package com.example.pet.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.pet.core.ai.ChatRequest;
import com.example.pet.core.common.callback.ChatCallback;
import com.example.pet.core.common.concurrent.AppExecutors;
import com.example.pet.feature.chat.data.ai.ChatGateway;
import com.example.pet.feature.chat.data.local.ChatHistoryStore;
import com.example.pet.feature.chat.data.local.PreferencesChatHistoryStore;
import com.example.pet.feature.chat.data.repository.ChatRepository;
import com.example.pet.feature.chat.domain.model.ChatMessage;
import com.example.pet.feature.chat.domain.model.ChatState;
import com.example.pet.feature.chat.ui.ChatActivity;
import org.json.JSONArray;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

/** Isolated app dependency and temporary preferences, never the user's chat history. */
@RunWith(AndroidJUnit4.class)
public class ChatStateInstrumentedTest {
    private static final int INPUT = com.example.pet.feature.chat.R.id.etInput;
    private static final int SEND = com.example.pet.feature.chat.R.id.btnSend;
    private static final int RETRY = com.example.pet.feature.chat.R.id.btnRetry;
    private static final int HISTORY = com.example.pet.feature.chat.R.id.tvChatHistory;
    private PetApplication application;
    private Field dependency;
    private ChatRepository original;
    private ChatRepository repository;
    private FakeGateway gateway;
    private String preferenceName;
    private SharedPreferences preferences;

    @Before public void setUp() throws Exception {
        application = (PetApplication) InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getApplicationContext();
        dependency = PetApplication.class.getDeclaredField("chatRepository");
        dependency.setAccessible(true);
        gateway = new FakeGateway();
        preferenceName = "chat_test_" + UUID.randomUUID();
        preferences = application.getSharedPreferences(preferenceName, Context.MODE_PRIVATE);
        main(() -> {
            original = (ChatRepository) dependency.get(application);
            repository = newRepository(new MemoryStore());
            dependency.set(application, repository);
            return null;
        });
    }
    @After public void tearDown() throws Exception {
        main(() -> {
            if (repository != null) repository.reset();
            dependency.set(application, original);
            return null;
        });
        flushDisk();
        application.deleteSharedPreferences(preferenceName);
    }

    @Test public void leaveAndReenterShowsBusyThenReplyWithoutDuplicateRequest() throws Exception {
        try (ActivityScenario<ChatActivity> first = ActivityScenario.launch(ChatActivity.class)) {
            send("leave question");
            assertBusy();
        }
        try (ActivityScenario<ChatActivity> second = ActivityScenario.launch(ChatActivity.class)) {
            assertBusy();
            assertEquals(1, gateway.requests.size());
            onView(withId(HISTORY)).check(matches(withText(containsString("leave question"))));
            gateway.requests.get(0).callback.onSuccess("return answer");
            idle();
            onView(withId(SEND)).check(matches(isEnabled()));
            second.onActivity(activity -> {
                String text = ((TextView) activity.findViewById(HISTORY)).getText().toString();
                assertEquals(1, occurrences(text, "return answer"));
                assertEquals(1, occurrences(text, "leave question"));
            });
        }
    }

    @Test public void recreationRetainsViewModelAndSingleRequest() throws Exception {
        try (ActivityScenario<ChatActivity> page = ActivityScenario.launch(ChatActivity.class)) {
            send("rotation question");
            page.recreate();
            assertBusy();
            assertEquals(1, gateway.requests.size());
            gateway.requests.get(0).callback.onSuccess("rotation answer");
            idle();
            onView(withId(SEND)).check(matches(isEnabled()));
            onView(withId(HISTORY)).check(matches(withText(containsString("rotation answer"))));
        }
    }

    @Test public void confirmedResetCancelsOldRequestAndLateCallbacksCannotPolluteNewConversation() throws Exception {
        try (ActivityScenario<ChatActivity> page = ActivityScenario.launch(ChatActivity.class)) {
            send("old question");
            Request old = gateway.requests.get(0);
            onView(withId(com.example.pet.feature.chat.R.id.btnNewChat)).perform(click());
            onView(withText("确定")).perform(click());
            assertTrue(old.cancelled);
            onView(withId(SEND)).check(matches(isEnabled()));
            send("fresh question");
            old.callback.onSuccess("stale answer");
            old.callback.onError("stale failure");
            idle();
            assertBusy();
            onView(withId(HISTORY)).check(matches(withText(not(containsString("stale")))));
            gateway.requests.get(1).callback.onSuccess("fresh answer");
            idle();
            onView(withId(HISTORY)).check(matches(withText(containsString("fresh answer"))));
            onView(withId(HISTORY)).check(matches(withText(not(containsString("old question")))));
        }
    }

    @Test public void failureKeepsMessageShowsRetryAndRetryDoesNotDuplicateUserMessage() throws Exception {
        try (ActivityScenario<ChatActivity> page = ActivityScenario.launch(ChatActivity.class)) {
            send("retry question");
            String id = main(() -> repository.snapshot().messages.get(0).id);
            gateway.requests.get(0).callback.onError("请求超时，请重试");
            idle();
            onView(withId(SEND)).check(matches(isEnabled()));
            onView(withId(RETRY)).check(matches(isDisplayed())).perform(click());
            assertBusy();
            gateway.requests.get(0).callback.onSuccess("late previous attempt");
            gateway.requests.get(1).callback.onSuccess("retry answer");
            idle();
            assertEquals(id, main(() -> repository.snapshot().messages.get(0).id));
            page.onActivity(activity -> {
                String text = ((TextView) activity.findViewById(HISTORY)).getText().toString();
                assertEquals(1, occurrences(text, "retry question"));
                assertEquals(1, occurrences(text, "retry answer"));
                assertFalse(text.contains("late previous attempt"));
                assertEquals(View.GONE, activity.findViewById(RETRY).getVisibility());
            });
        }
    }

    @Test public void rejectedBlankInputIsNotClearedAndDoesNotCreateRequest() throws Exception {
        try (ActivityScenario<ChatActivity> page = ActivityScenario.launch(ChatActivity.class)) {
            onView(withId(INPUT)).perform(replaceText("   "), closeSoftKeyboard());
            onView(withId(SEND)).perform(click());
            onView(withId(INPUT)).check(matches(withText("   ")));
            assertTrue(gateway.requests.isEmpty());
        }
    }

    @Test public void legacyHistoryAndPromptMigrateWithoutLosingContentOrIdsOnReload() throws Exception {
        preferences.edit().putString("display_history", "[{\"role\":\"user\",\"content\":\"legacy question\"},"
                + "{\"role\":\"assistant\",\"content\":\"legacy answer\"}]")
                .putString("system_prompt", "legacy prompt").commit();
        installPersistentRepository();
        String id = main(() -> repository.snapshot().messages.get(0).id);
        try (ActivityScenario<ChatActivity> page = ActivityScenario.launch(ChatActivity.class)) {
            onView(withId(HISTORY)).check(matches(withText(containsString("legacy question"))));
            onView(withId(HISTORY)).check(matches(withText(containsString("legacy answer"))));
        }
        flushDisk();
        ChatState loaded = new PreferencesChatHistoryStore(preferences, AppExecutors.disk()).load();
        assertEquals(2, loaded.messages.size());
        assertEquals(id, loaded.messages.get(0).id);
        assertEquals("legacy prompt", loaded.systemPrompt);
        assertEquals(ChatMessage.Status.SENT, loaded.messages.get(0).status);
        assertEquals(2, new JSONArray(preferences.getString("display_history", "[]")).length());
    }

    @Test public void restoredPendingRequestBecomesFailedAndCanRetryAfterProcessRecovery() throws Exception {
        preferences.edit().putString("session_id", "previous-session")
                .putString("display_history", "[{\"id\":\"previous-id\",\"role\":\"user\","
                        + "\"content\":\"interrupted question\",\"status\":\"SENDING\"}]").commit();
        installPersistentRepository();
        try (ActivityScenario<ChatActivity> page = ActivityScenario.launch(ChatActivity.class)) {
            onView(withId(SEND)).check(matches(isEnabled()));
            onView(withId(HISTORY)).check(matches(withText(containsString("上次请求已中断"))));
            assertEquals(0, gateway.requests.size());
            onView(withId(RETRY)).perform(click());
            assertBusy();
            gateway.requests.get(0).callback.onSuccess("recovered answer");
            idle();
            assertEquals("previous-id", main(() -> repository.snapshot().messages.get(0).id));
        }
    }

    @Test public void queuedPersistenceRetainsResetAndCannotSaveStaleReply() throws Exception {
        installPersistentRepository();
        main(() -> { repository.sendMessage("discarded"); repository.reset(); return null; });
        gateway.requests.get(0).callback.onSuccess("discarded reply");
        idle();
        flushDisk();
        ChatState saved = new PreferencesChatHistoryStore(preferences, AppExecutors.disk()).load();
        assertTrue(saved.messages.isEmpty());
        assertEquals(main(() -> repository.snapshot().sessionId), saved.sessionId);
    }

    private ChatRepository newRepository(ChatHistoryStore store) {
        return new ChatRepository(store, gateway, new Handler(Looper.getMainLooper())::post);
    }
    private void installPersistentRepository() throws Exception {
        main(() -> {
            repository = newRepository(new PreferencesChatHistoryStore(preferences, AppExecutors.disk()));
            dependency.set(application, repository);
            return null;
        });
    }
    private void send(String text) {
        onView(withId(INPUT)).perform(replaceText(text), closeSoftKeyboard());
        onView(withId(SEND)).perform(click());
    }
    private void assertBusy() {
        onView(withId(SEND)).check(matches(not(isEnabled())));
        onView(withId(com.example.pet.feature.chat.R.id.progressBar)).check(matches(isDisplayed()));
    }
    private void idle() { InstrumentationRegistry.getInstrumentation().waitForIdleSync(); }
    private void flushDisk() throws Exception {
        FutureTask<Boolean> barrier = new FutureTask<>(() -> preferences.edit().commit());
        AppExecutors.disk().execute(barrier);
        assertTrue(barrier.get(5, TimeUnit.SECONDS));
    }
    private static <T> T main(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(task);
        return task.get(5, TimeUnit.SECONDS);
    }
    private int occurrences(String text, String needle) {
        return (text.length() - text.replace(needle, "").length()) / needle.length();
    }
    private static final class MemoryStore implements ChatHistoryStore {
        @Override public ChatState load() { return null; }
        @Override public void save(ChatState state) { }
    }
    private static final class Request implements ChatRequest {
        final ChatCallback callback;
        boolean cancelled;
        Request(ChatCallback callback) { this.callback = callback; }
        @Override public void cancel() { cancelled = true; }
    }
    private static final class FakeGateway implements ChatGateway {
        final List<Request> requests = new ArrayList<>();
        @Override public ChatRequest send(String prompt, List<ChatMessage> context, ChatCallback callback) {
            Request request = new Request(callback);
            requests.add(request);
            return request;
        }
    }
}
