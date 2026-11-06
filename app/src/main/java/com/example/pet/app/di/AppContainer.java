package com.example.pet.app.di;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import com.example.pet.core.ai.LLMClient;
import com.example.pet.core.common.concurrent.AppExecutors;
import com.example.pet.core.data.repository.LocalTaskRepository;
import com.example.pet.core.data.repository.TaskRepository;
import com.example.pet.core.model.pet.PetInteractions;
import com.example.pet.feature.chat.data.ai.LlmChatGateway;
import com.example.pet.feature.chat.data.local.PreferencesChatHistoryStore;
import com.example.pet.feature.chat.data.repository.ChatRepository;
import com.example.pet.feature.interaction.PetInteractionCoordinator;
import com.example.pet.feature.interaction.RewardingTaskRepository;
import com.example.pet.feature.pet.data.local.LocalPetRepository;
import com.example.pet.feature.pet.data.repository.PetRepository;
import com.example.virtualpetd.data.EconomyDatabase;

/**
 * Application-scoped dependency assembly; feature modules receive only their existing owner contracts.
 * Construction stays lazy. Opening an Activity must not open a database on the main thread.
 */
public final class AppContainer {
    private final Application application;
    private PetInteractionCoordinator petCoordinator;
    private TaskRepository taskRepository;
    // ChatRepository retains its original main-thread ownership; disk work uses AppExecutors.
    private ChatRepository chatRepository;

    public AppContainer(Application application) {
        this.application = application;
    }

    private synchronized PetInteractionCoordinator coordinator() {
        if (petCoordinator == null) {
            // The guarded repository must be shared by tasks, the shop and the pet screen.
            // Creating a second raw repository would bypass pending interaction/reward recovery.
            petCoordinator = new PetInteractionCoordinator(application,
                    EconomyDatabase.get(application),
                    new LocalPetRepository(application.getSharedPreferences(
                            LocalPetRepository.PREFERENCES_NAME, Application.MODE_PRIVATE)),
                    LocalTaskRepository.getInstance(application));
        }
        return petCoordinator;
    }

    public PetRepository petRepository() {
        return coordinator();
    }

    public PetInteractions petInteractions() {
        return coordinator();
    }

    public synchronized TaskRepository taskRepository() {
        if (taskRepository == null) {
            taskRepository = new RewardingTaskRepository(
                    LocalTaskRepository.getInstance(application), coordinator());
        }
        return taskRepository;
    }

    public ChatRepository chatRepository() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new IllegalStateException("ChatRepository is owned by the main thread");
        }
        if (chatRepository == null) {
            Handler main = new Handler(Looper.getMainLooper());
            chatRepository = new ChatRepository(new PreferencesChatHistoryStore(
                    application.getSharedPreferences("virtual_pet_chat", Application.MODE_PRIVATE),
                    AppExecutors.disk()), new LlmChatGateway(new LLMClient()), main::post);
        }
        return chatRepository;
    }
}
