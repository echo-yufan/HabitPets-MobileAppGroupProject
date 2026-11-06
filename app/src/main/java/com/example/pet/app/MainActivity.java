package com.example.pet.app;

/** Home hosts the same real pet scene as the secondary pet page; navigation stays in the app. */
public final class MainActivity extends com.example.pet.feature.pet.ui.PetActivity {
    @Override protected boolean isHomeScreen() { return true; }
}
