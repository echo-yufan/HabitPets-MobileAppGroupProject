package com.example.virtualpetd.service;

/** Implement on the host Application to connect C without depending on its Activity. */
public interface PetLevelProviderOwner {
    PetLevelProvider getPetLevelProvider();
}
