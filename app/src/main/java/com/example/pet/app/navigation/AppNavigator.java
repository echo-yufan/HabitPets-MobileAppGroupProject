package com.example.pet.app.navigation;

import android.app.Activity;
import android.content.Intent;
import com.example.pet.app.MainActivity;
import com.example.pet.feature.chat.ui.ChatActivity;
import com.example.pet.feature.pet.ui.PetActivity;
import com.example.pet.feature.planning.ui.MemoActivity;
import com.example.virtualpetd.InventoryActivity;
import com.example.virtualpetd.ShopActivity;
import com.example.virtualpetd.ui.ProductArt;

/** Host-only cross-feature routing. Preserve Activity targets, extras and task-stack flags. */
public final class AppNavigator {
    public void openTasks(Activity activity) {
        activity.startActivity(new Intent(activity, MemoActivity.class));
    }

    public void openChat(Activity activity) {
        activity.startActivity(new Intent(activity, ChatActivity.class));
    }

    public int itemArt(String itemId) {
        return ProductArt.resource(itemId);
    }

    public void openPet(Activity activity, String itemId, boolean food) {
        Intent intent = new Intent(activity, MainActivity.class);
        if (itemId != null) {
            // Feedback describes a committed operation; opening the screen must not repeat it.
            intent.putExtra(PetActivity.EXTRA_FEEDBACK_ITEM, itemId);
            intent.putExtra(PetActivity.EXTRA_FEEDBACK_FOOD, food);
            if (food) intent.putExtra(PetActivity.EXTRA_FEEDBACK_ART, itemArt(itemId));
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        activity.startActivity(intent);
    }

    public void openInventory(Activity activity, String category) {
        Intent intent = new Intent(activity, InventoryActivity.class);
        intent.putExtra("inventory_category", category);
        activity.startActivity(intent);
    }

    public void openShop(Activity activity, boolean inventory) {
        activity.startActivity(new Intent(activity, inventory ? InventoryActivity.class : ShopActivity.class));
    }
}
