package com.example.pet.feature.chat.data.ai;

import com.example.pet.core.ai.ChatRequest;
import com.example.pet.core.common.callback.ChatCallback;
import com.example.pet.feature.chat.domain.model.ChatMessage;
import java.util.List;

public interface ChatGateway {
    ChatRequest send(String systemPrompt, List<ChatMessage> context, ChatCallback callback);
}
