package com.example.pet.app;

import android.app.Activity;
import android.app.Application;
import com.example.pet.app.di.AppContainer;
import com.example.pet.app.navigation.AppNavigator;
import com.example.pet.core.data.di.TaskRepositoryOwner;
import com.example.pet.core.data.repository.TaskRepository;
import com.example.pet.core.model.pet.PetInteractionOwner;
import com.example.pet.core.model.pet.PetInteractions;
import com.example.pet.feature.chat.data.repository.ChatRepository;
import com.example.pet.feature.chat.di.ChatRepositoryOwner;
import com.example.pet.feature.pet.data.repository.PetRepository;
import com.example.pet.feature.pet.di.PetNavigationOwner;
import com.example.pet.feature.pet.di.PetRepositoryOwner;
import com.example.virtualpetd.service.PetInteractionNavigation;
import com.example.virtualpetd.service.PetLevelProvider;
import com.example.virtualpetd.service.PetLevelProviderOwner;

/**
 * Feature-facing composition root. Dependency lifetimes belong to AppContainer and routing to
 * AppNavigator; keeping these owner interfaces preserves existing feature and test integrations.
 */
public final class PetApplication extends Application implements TaskRepositoryOwner,
        ChatRepositoryOwner, PetRepositoryOwner, PetLevelProviderOwner, PetNavigationOwner,
        PetInteractionOwner, PetInteractionNavigation {
    // Constructors only retain references; repositories and database access remain lazy.
    private final AppContainer container = new AppContainer(this);
    private final AppNavigator navigator = new AppNavigator();

    @Override public PetRepository getPetRepository() { return container.petRepository(); }
    @Override public PetInteractions getPetInteractions() { return container.petInteractions(); }
    @Override public TaskRepository getTaskRepository() { return container.taskRepository(); }
    @Override public ChatRepository getChatRepository() { return container.chatRepository(); }

    @Override public PetLevelProvider getPetLevelProvider() {
        return new RepositoryPetLevelProvider(getPetRepository());
    }

    @Override public void openTasks(Activity activity) { navigator.openTasks(activity); }
    @Override public void openChat(Activity activity) { navigator.openChat(activity); }
    @Override public int itemArt(String itemId) { return navigator.itemArt(itemId); }
    @Override public void openPet(Activity activity) { navigator.openPet(activity, null, false); }
    @Override public void openPet(Activity activity, String itemId, boolean food) {
        navigator.openPet(activity, itemId, food);
    }
    @Override public void openInventory(Activity activity, String category) {
        navigator.openInventory(activity, category);
    }
    @Override public void openShop(Activity activity, boolean inventory) {
        navigator.openShop(activity, inventory);
    }
}
