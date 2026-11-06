package com.example.pet.core.model.pet;

/** Implemented by the host, so pet and shop do not depend on one another. */
public interface PetInteractionOwner {
    PetInteractions getPetInteractions();
}
