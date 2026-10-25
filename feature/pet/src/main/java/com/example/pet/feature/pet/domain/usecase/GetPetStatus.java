package com.example.pet.feature.pet.domain.usecase;
import com.example.pet.core.model.pet.Pet;
import com.example.pet.feature.pet.data.repository.PetRepository;
public final class GetPetStatus {
    private final PetRepository repository;
    public GetPetStatus(PetRepository repository) { this.repository = repository; }
    public Pet execute() { return repository.getPet(PetRepository.LOCAL_USER_ID); }
}