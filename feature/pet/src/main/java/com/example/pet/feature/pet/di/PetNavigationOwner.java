package com.example.pet.feature.pet.di;
import android.app.Activity;
/** Host owns navigation, keeping C independent of D. */
public interface PetNavigationOwner { void openShop(Activity activity, boolean inventory); }
