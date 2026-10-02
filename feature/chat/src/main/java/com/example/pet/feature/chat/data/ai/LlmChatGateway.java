package com.example.pet.feature.chat.data.ai;

import com.example.pet.core.ai.ChatRequest;
import com.example.pet.core.ai.LLMClient;
import com.example.pet.core.common.callback.ChatCallback;
import com.example.pet.feature.chat.domain.model.ChatMessage;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;

public final class LlmChatGateway implements ChatGateway {
    private final LLMClient client;
    public LlmChatGateway(LLMClient client) { this.client = client; }

    @Override public ChatRequest send(String prompt, List<ChatMessage> context, ChatCallback callback) {
        try {
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "system").put("content", prompt));
            for (ChatMessage message : context) {
                messages.put(new JSONObject().put("role", message.role).put("content", message.content));
            }
            return client.sendChat(messages, callback);
        } catch (Exception e) {
            callback.onError("构建消息失败，请重试");
            return () -> {};
        }
    }
}
