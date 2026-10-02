package com.example.pet.feature.chat.data.local;

import com.example.pet.feature.chat.domain.model.ChatState;

public interface ChatHistoryStore {
    ChatState load();
    void save(ChatState state);
}
