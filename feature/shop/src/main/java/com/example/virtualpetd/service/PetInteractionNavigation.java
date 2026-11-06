package com.example.virtualpetd.service;
import android.app.Activity;
/** Cross-feature navigation supplied by the app; D does not depend on C. */
public interface PetInteractionNavigation {
    void openPet(Activity activity);
    default void openPet(Activity activity, String itemId, boolean food) { openPet(activity); }
}
