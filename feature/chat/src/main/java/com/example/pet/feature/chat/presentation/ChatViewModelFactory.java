package com.example.pet.feature.chat.presentation;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.example.pet.feature.chat.data.repository.ChatRepository;

public final class ChatViewModelFactory implements ViewModelProvider.Factory {
    private final ChatRepository repository;
    public ChatViewModelFactory(ChatRepository repository) { this.repository = repository; }
    @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (!modelClass.isAssignableFrom(ChatViewModel.class)) throw new IllegalArgumentException("Unknown ViewModel");
        return modelClass.cast(new ChatViewModel(repository));
    }
}
