package com.example.pet.feature.chat.data.repository;

import com.example.pet.core.ai.ChatRequest;
import com.example.pet.core.common.callback.ChatCallback;
import com.example.pet.feature.chat.data.ai.ChatGateway;
import com.example.pet.feature.chat.data.local.ChatHistoryStore;
import com.example.pet.feature.chat.domain.model.ChatMessage;
import com.example.pet.feature.chat.domain.model.ChatState;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * App-scoped conversation owner. Commands/listeners run on the main thread;
 * transport callbacks are marshalled to that same executor before touching state.
 */
public final class ChatRepository {
    public static final String DEFAULT_SYSTEM_PROMPT =
            "你是一只可爱的虚拟宠物，名叫小星。你性格活泼、好奇，说话简短可爱，"
                    + "偶尔会卖萌。用中文回答，每次回复不超过50个字。";
    private static final int MAX_HISTORY = 200;
    private static final int MAX_CONTEXT = 10;
    private final ChatHistoryStore store;
    private final ChatGateway gateway;
    private final Executor callbackExecutor;
    private final List<Consumer<ChatState>> listeners = new ArrayList<>();
    private final List<ChatMessage> messages = new ArrayList<>();
    private String sessionId;
    private String systemPrompt;
    private PendingRequest active;
    private final Thread ownerThread = Thread.currentThread();

    private static final class PendingRequest {
        final String sessionId;
        final String messageId;
        final String requestId = UUID.randomUUID().toString();
        ChatRequest handle;
        PendingRequest(String sessionId, String messageId) {
            this.sessionId = sessionId;
            this.messageId = messageId;
        }
    }

    public ChatRepository(ChatHistoryStore store, ChatGateway gateway, Executor callbackExecutor) {
        this.store = store;
        this.gateway = gateway;
        this.callbackExecutor = callbackExecutor;
        ChatState restored = store.load();
        sessionId = restored == null ? UUID.randomUUID().toString() : restored.sessionId;
        systemPrompt = restored == null ? DEFAULT_SYSTEM_PROMPT : restored.systemPrompt;
        if (restored != null) {
            for (ChatMessage message : restored.messages) {
                messages.add(message.status == ChatMessage.Status.SENDING
                        ? message.withStatus(ChatMessage.Status.FAILED, "上次请求已中断，请重试") : message);
            }
        }
        trimHistory();
        store.save(snapshot());
    }

    public ChatState snapshot() {
        checkThread();
        return new ChatState(sessionId, systemPrompt, messages, active != null);
    }
    public void addListener(Consumer<ChatState> listener) {
        checkThread();
        listeners.add(listener);
        listener.accept(snapshot());
    }
    public void removeListener(Consumer<ChatState> listener) { checkThread(); listeners.remove(listener); }

    public boolean sendMessage(String input) {
        checkThread();
        if (active != null || input == null || input.trim().isEmpty()) return false;
        ChatMessage message = new ChatMessage(UUID.randomUUID().toString(), sessionId, "user",
                input.trim(), ChatMessage.Status.SENDING, null);
        messages.add(message);
        trimHistory();
        startRequest(message);
        return true;
    }

    public boolean retry(String messageId) {
        checkThread();
        if (messageId == null || !messageId.equals(snapshot().retryMessageId())) return false;
        int index = messages.size() - 1;
        ChatMessage message = messages.get(index).withStatus(ChatMessage.Status.SENDING, null);
        messages.set(index, message);
        startRequest(message);
        return true;
    }

    public void reset() {
        checkThread();
        PendingRequest previous = active;
        active = null;
        sessionId = UUID.randomUUID().toString();
        messages.clear();
        publish();
        if (previous != null && previous.handle != null) previous.handle.cancel();
    }

    private void startRequest(ChatMessage message) {
        PendingRequest request = new PendingRequest(sessionId, message.id);
        active = request;
        publish();
        if (active != request) return;
        try {
            ChatRequest handle = gateway.send(systemPrompt, buildContext(), new ChatCallback() {
                @Override public void onSuccess(String reply) {
                    callbackExecutor.execute(() -> complete(request, reply, null));
                }
                @Override public void onError(String error) {
                    callbackExecutor.execute(() -> complete(request, null, error));
                }
            });
            // A synchronous callback can finish or reset this request before send returns.
            if (active == request) request.handle = handle;
            else if (handle != null) handle.cancel();
        } catch (Exception e) {
            complete(request, null, "请求发送失败，请重试");
        }
    }

    private void complete(PendingRequest request, String reply, String error) {
        checkThread();
        if (active != request || !sessionId.equals(request.sessionId)
                || !active.requestId.equals(request.requestId)) return;
        int index = -1;
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).id.equals(request.messageId)) { index = i; break; }
        }
        if (index < 0) return;
        boolean success = error == null && reply != null && !reply.trim().isEmpty();
        messages.set(index, messages.get(index).withStatus(success ? ChatMessage.Status.SENT
                : ChatMessage.Status.FAILED, success ? null
                : (error == null || error.isEmpty() ? "没有收到有效回复，请重试" : error)));
        if (success) messages.add(new ChatMessage(UUID.randomUUID().toString(), sessionId,
                "assistant", reply, ChatMessage.Status.SENT, null));
        active = null;
        trimHistory();
        publish();
    }

    private List<ChatMessage> buildContext() {
        List<ChatMessage> context = new ArrayList<>();
        for (ChatMessage message : messages) {
            if (message.status != ChatMessage.Status.FAILED) context.add(message);
        }
        while (context.size() > MAX_CONTEXT) context.remove(0);
        while (!context.isEmpty() && "assistant".equals(context.get(0).role)) context.remove(0);
        return context;
    }

    private void trimHistory() {
        while (messages.size() > MAX_HISTORY) messages.remove(0);
        while (!messages.isEmpty() && "assistant".equals(messages.get(0).role)) messages.remove(0);
    }

    private void publish() {
        ChatState state = snapshot();
        store.save(state);
        for (Consumer<ChatState> listener : new ArrayList<>(listeners)) listener.accept(state);
    }

    private void checkThread() {
        if (Thread.currentThread() != ownerThread) {
            throw new IllegalStateException("Chat state must be accessed on its owner thread");
        }
    }
}
