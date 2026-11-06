package com.example.pet.feature.pet.di;
import android.app.Activity;
/** Host owns navigation, keeping C independent of D. */
public interface PetNavigationOwner {
    void openShop(Activity activity, boolean inventory);
    default void openInventory(Activity activity, String category) { openShop(activity, true); }
    default void openTasks(Activity activity) { }
    default void openChat(Activity activity) { }
    default int itemArt(String itemId) { return 0; }
}
