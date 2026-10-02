package com.example.pet.feature.chat.data.local;

import android.content.SharedPreferences;
import com.example.pet.feature.chat.data.repository.ChatRepository;
import com.example.pet.feature.chat.domain.model.ChatMessage;
import com.example.pet.feature.chat.domain.model.ChatState;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/** Keeps the original preference keys; adds ids/status/session metadata to each message. */
public final class PreferencesChatHistoryStore implements ChatHistoryStore {
    private final SharedPreferences preferences;
    private final Executor diskExecutor;
    public PreferencesChatHistoryStore(SharedPreferences preferences, Executor diskExecutor) {
        this.preferences = preferences;
        this.diskExecutor = diskExecutor;
    }

    @Override public ChatState load() {
        String sessionId = preferences.getString("session_id", UUID.randomUUID().toString());
        String prompt = preferences.getString("system_prompt", ChatRepository.DEFAULT_SYSTEM_PROMPT);
        if (prompt == null || prompt.isEmpty()) prompt = ChatRepository.DEFAULT_SYSTEM_PROMPT;
        List<ChatMessage> messages = new ArrayList<>();
        String saved = preferences.getString("display_history", "[]");
        try {
            JSONArray array = new JSONArray(saved);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                String role = item.optString("role");
                if (!"user".equals(role) && !"assistant".equals(role)) continue;
                ChatMessage.Status status;
                try { status = ChatMessage.Status.valueOf(item.optString("status", "SENT")); }
                catch (IllegalArgumentException e) { status = ChatMessage.Status.FAILED; }
                String id = item.optString("id");
                if (id.isEmpty()) id = UUID.randomUUID().toString();
                messages.add(new ChatMessage(id, sessionId, role, item.optString("content"),
                        status, item.isNull("error") ? null : item.optString("error")));
            }
        } catch (Exception e) {
            // Preserve a malformed old value for recovery rather than silently erasing it.
            final String backup = saved;
            diskExecutor.execute(() -> preferences.edit().putString("display_history_corrupt", backup).apply());
        }
        return new ChatState(sessionId, prompt, messages, false);
    }

    @Override public void save(ChatState state) {
        // The shared serial disk queue preserves snapshot order even across reset and callbacks.
        diskExecutor.execute(() -> {
            JSONArray array = new JSONArray();
            for (ChatMessage message : state.messages) {
                JSONObject item = new JSONObject();
                try {
                    item.put("id", message.id).put("session_id", message.sessionId)
                            .put("role", message.role).put("content", message.content)
                            .put("status", message.status.name()).put("error", message.error);
                } catch (org.json.JSONException e) { throw new IllegalStateException(e); }
                array.put(item);
            }
            preferences.edit().putString("display_history", array.toString())
                    .putString("session_id", state.sessionId)
                    .putString("system_prompt", state.systemPrompt).apply();
        });
    }
}
