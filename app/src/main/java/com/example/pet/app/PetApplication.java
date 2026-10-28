package com.example.pet.app;

import android.app.Application;
import com.example.pet.feature.pet.di.PetRepositoryOwner;
import com.example.pet.feature.pet.data.repository.PetRepository;
import com.example.pet.feature.pet.data.local.LocalPetRepository;
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
public final class PetApplication extends Application implements TaskRepositoryOwner, ChatRepositoryOwner, PetRepositoryOwner, com.example.virtualpetd.service.PetLevelProviderOwner, com.example.pet.feature.pet.di.PetNavigationOwner {
    @Override public com.example.virtualpetd.service.PetLevelProvider getPetLevelProvider() {
        return new RepositoryPetLevelProvider(getPetRepository());
    }
    @Override public void openShop(android.app.Activity activity, boolean inventory) {
        activity.startActivity(new android.content.Intent(activity, inventory ? com.example.virtualpetd.InventoryActivity.class : com.example.virtualpetd.ShopActivity.class));
    }
    private PetRepository petRepository;
    @Override public synchronized PetRepository getPetRepository() {
        if (petRepository == null) petRepository = new LocalPetRepository(getSharedPreferences(LocalPetRepository.PREFERENCES_NAME, MODE_PRIVATE));
        return petRepository;
    }
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
