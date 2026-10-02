package com.example.pet.feature.chat.di;

import com.example.pet.feature.chat.data.repository.ChatRepository;

/** Implemented by the host Application; the feature does not depend on app. */
public interface ChatRepositoryOwner {
    ChatRepository getChatRepository();
}
