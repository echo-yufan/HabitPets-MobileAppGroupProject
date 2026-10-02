package com.example.pet.app;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import com.example.pet.core.ai.LLMClient;
import com.example.pet.core.common.concurrent.AppExecutors;
import com.example.pet.feature.chat.di.ChatRepositoryOwner;
import com.example.pet.feature.chat.data.repository.ChatRepository;
import com.example.pet.feature.chat.data.local.PreferencesChatHistoryStore;
import com.example.pet.feature.chat.data.ai.LlmChatGateway;

import com.example.pet.core.data.di.TaskRepositoryOwner;
import com.example.pet.core.data.repository.LocalTaskRepository;
import com.example.pet.core.data.repository.TaskRepository;

/** App-level composition root. Keeps the existing repository's lazy initialization. */
public final class PetApplication extends Application implements TaskRepositoryOwner, ChatRepositoryOwner {
    private ChatRepository chatRepository;

    @Override public ChatRepository getChatRepository() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new IllegalStateException("ChatRepository is owned by the main thread");
        }
        if (chatRepository == null) {
            Handler main = new Handler(Looper.getMainLooper());
            chatRepository = new ChatRepository(new PreferencesChatHistoryStore(
                    getSharedPreferences("virtual_pet_chat", MODE_PRIVATE), AppExecutors.disk()),
                    new LlmChatGateway(new LLMClient()), main::post);
        }
        return chatRepository;
    }
    @Override
    public TaskRepository getTaskRepository() {
        return LocalTaskRepository.getInstance(this);
    }
}
