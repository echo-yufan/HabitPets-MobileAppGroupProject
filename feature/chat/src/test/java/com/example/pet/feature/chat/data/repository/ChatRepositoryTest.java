package com.example.pet.feature.chat.data.repository;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.ViewModelStore;
import androidx.lifecycle.ViewModelProvider;
import com.example.pet.core.ai.ChatRequest;
import com.example.pet.core.common.callback.ChatCallback;
import com.example.pet.feature.chat.data.ai.ChatGateway;
import com.example.pet.feature.chat.data.local.ChatHistoryStore;
import com.example.pet.feature.chat.domain.model.ChatMessage;
import com.example.pet.feature.chat.domain.model.ChatState;
import com.example.pet.feature.chat.presentation.ChatViewModel;
import com.example.pet.feature.chat.presentation.ChatViewModelFactory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class ChatRepositoryTest {
    @Rule public InstantTaskExecutorRule main = new InstantTaskExecutorRule();
    private MemoryStore store;
    private FakeGateway gateway;
    private ChatRepository repository;
    @Before public void setUp() {
        store = new MemoryStore();
        gateway = new FakeGateway();
        repository = new ChatRepository(store, gateway, Runnable::run);
    }

    @Test public void leavingAndReenteringRetainsBusyStateAndReply() {
        ViewModelStore models = new ViewModelStore();
        ChatViewModel first = new ViewModelProvider(models, new ChatViewModelFactory(repository))
                .get(ChatViewModel.class);
        assertTrue(first.send("hello"));
        models.clear();
        ChatViewModel second = new ChatViewModel(repository);
        assertTrue(second.getState().getValue().requesting);
        assertFalse(second.send("duplicate"));
        assertEquals(1, gateway.requests.size());
        gateway.requests.get(0).callback.onSuccess("reply");
        assertFalse(second.getState().getValue().requesting);
        assertEquals(2, second.getState().getValue().messages.size());
        assertTrue(first.getState().getValue().requesting); // detached observer never receives updates
    }

    @Test public void secondObserverSeesSameSnapshotAndCannotStartParallelRequest() {
        List<ChatState> pageA = new ArrayList<>(), pageB = new ArrayList<>();
        repository.addListener(pageA::add);
        repository.sendMessage("A");
        repository.addListener(pageB::add);
        assertTrue(pageB.get(0).requesting);
        assertFalse(repository.sendMessage("B"));
        gateway.requests.get(0).callback.onError("timeout");
        assertEquals(pageA.get(pageA.size()-1).messages, pageB.get(pageB.size()-1).messages);
    }

    @Test public void resetCancelsRequestAndIgnoresLateReplyAfterNewSend() {
        repository.sendMessage("old");
        Request old = gateway.requests.get(0);
        String oldSession = repository.snapshot().sessionId;
        repository.reset();
        assertTrue(old.cancelled);
        assertNotEquals(oldSession, repository.snapshot().sessionId);
        assertTrue(repository.sendMessage("new"));
        old.callback.onSuccess("stale reply");
        old.callback.onError("stale error");
        assertTrue(repository.snapshot().requesting);
        assertEquals(1, repository.snapshot().messages.size());
        assertEquals("new", repository.snapshot().messages.get(0).content);
        gateway.requests.get(1).callback.onSuccess("fresh reply");
        assertEquals("fresh reply", repository.snapshot().messages.get(1).content);
    }

    @Test public void failurePreservesUserAndRetryUsesSameMessageIdExactlyOnce() {
        repository.sendMessage("question");
        String id = repository.snapshot().messages.get(0).id;
        gateway.requests.get(0).callback.onError("请求超时，请重试");
        assertEquals(ChatMessage.Status.FAILED, repository.snapshot().messages.get(0).status);
        assertEquals("question", store.state.messages.get(0).content);
        assertTrue(repository.retry(id));
        assertFalse(repository.retry(id));
        gateway.requests.get(0).callback.onSuccess("old attempt reply");
        assertTrue(repository.snapshot().requesting);
        gateway.requests.get(1).callback.onSuccess("answer");
        assertEquals(2, repository.snapshot().messages.size());
        assertEquals(id, repository.snapshot().messages.get(0).id);
        assertEquals(ChatMessage.Status.SENT, repository.snapshot().messages.get(0).status);
    }

    @Test public void staleFailureCannotRemoveNewerMessage() {
        repository.sendMessage("A");
        Request old = gateway.requests.get(0);
        old.callback.onError("failed");
        assertTrue(repository.sendMessage("B"));
        old.callback.onError("late failure");
        assertEquals(2, repository.snapshot().messages.size());
        assertEquals("B", repository.snapshot().messages.get(1).content);
        assertTrue(repository.snapshot().requesting);
        assertFalse(repository.retry(repository.snapshot().messages.get(0).id));
    }

    @Test public void failedMessagesStayVisibleButAreExcludedFromApiContext() {
        repository.sendMessage("failed question");
        gateway.requests.get(0).callback.onError("offline");
        repository.sendMessage("new question");
        assertEquals(1, gateway.requests.get(1).context.size());
        assertEquals("new question", gateway.requests.get(1).context.get(0).content);
        gateway.requests.get(1).callback.onSuccess("new answer");
        assertEquals(3, repository.snapshot().messages.size());
        assertNull(repository.snapshot().retryMessageId());
    }

    @Test public void duplicateCallbacksProduceOnlyOneAssistantReply() {
        repository.sendMessage("hello");
        ChatCallback callback = gateway.requests.get(0).callback;
        callback.onSuccess("one");
        callback.onSuccess("two");
        callback.onError("late");
        assertEquals(2, repository.snapshot().messages.size());
        assertEquals("one", repository.snapshot().messages.get(1).content);
    }

    @Test public void processRecoveryMarksSendingFailedWithoutAutomaticallyResending() {
        repository.sendMessage("in flight");
        String id = store.state.messages.get(0).id;
        FakeGateway newProcess = new FakeGateway();
        ChatRepository recovered = new ChatRepository(store, newProcess, Runnable::run);
        assertFalse(recovered.snapshot().requesting);
        assertEquals(ChatMessage.Status.FAILED, recovered.snapshot().messages.get(0).status);
        assertEquals(id, recovered.snapshot().retryMessageId());
        assertEquals(0, newProcess.requests.size());
        assertTrue(recovered.retry(id));
    }

    @Test public void blankMessageDoesNotWriteOrRequest() {
        assertFalse(repository.sendMessage("  "));
        assertFalse(repository.sendMessage(null));
        assertTrue(repository.snapshot().messages.isEmpty());
        assertTrue(gateway.requests.isEmpty());
    }

    @Test public void emptyReplyIsFailureAndAllowsRetry() {
        repository.sendMessage("hi");
        gateway.requests.get(0).callback.onSuccess("  ");
        assertFalse(repository.snapshot().requesting);
        assertNotNull(repository.snapshot().retryMessageId());
    }

    @Test public void synchronousCallbackDoesNotLeaveBusyOrLoseHandle() {
        ChatRepository sync = new ChatRepository(new MemoryStore(), (prompt, context, callback) -> {
            callback.onSuccess("sync reply");
            return () -> {};
        }, Runnable::run);
        assertTrue(sync.sendMessage("hello"));
        assertFalse(sync.snapshot().requesting);
        assertEquals(2, sync.snapshot().messages.size());
    }

    @Test public void throwingTransportEndsBusyAndPreservesFailedInput() {
        ChatRepository throwing = new ChatRepository(new MemoryStore(), (p, c, cb) -> {
            throw new IllegalStateException("unavailable");
        }, Runnable::run);
        throwing.sendMessage("hello");
        assertFalse(throwing.snapshot().requesting);
        assertNotNull(throwing.snapshot().retryMessageId());
    }

    @Test public void historyAndApiContextRemainBoundedAcrossManyTurns() {
        for (int i = 0; i < 110; i++) {
            repository.sendMessage("question " + i);
            Request request = gateway.requests.get(i);
            assertTrue(request.context.size() <= 10);
            assertEquals("user", request.context.get(0).role);
            request.callback.onSuccess("answer " + i);
        }
        assertEquals(200, repository.snapshot().messages.size());
        assertEquals("question 10", repository.snapshot().messages.get(0).content);
    }

    @Test public void snapshotsCannotBeMutatedAndRetainOriginalContent() {
        repository.sendMessage("hello");
        ChatState old = repository.snapshot();
        try { old.messages.clear(); fail(); } catch (UnsupportedOperationException expected) { }
        gateway.requests.get(0).callback.onSuccess("reply");
        assertEquals(1, old.messages.size());
        assertEquals(ChatMessage.Status.SENDING, old.messages.get(0).status);
    }

    static final class MemoryStore implements ChatHistoryStore {
        ChatState state;
        @Override public ChatState load() { return state; }
        @Override public void save(ChatState state) { this.state = state; }
    }
    static final class Request implements ChatRequest {
        final ChatCallback callback;
        final List<ChatMessage> context;
        boolean cancelled;
        Request(List<ChatMessage> context, ChatCallback callback) { this.context = context; this.callback = callback; }
        @Override public void cancel() { cancelled = true; }
    }
    static final class FakeGateway implements ChatGateway {
        final List<Request> requests = new ArrayList<>();
        @Override public ChatRequest send(String prompt, List<ChatMessage> context, ChatCallback callback) {
            Request request = new Request(context, callback);
            requests.add(request);
            return request;
        }
    }
}
