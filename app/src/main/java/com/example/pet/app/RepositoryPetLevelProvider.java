package com.example.pet.app;
import com.example.pet.feature.pet.data.repository.PetRepository;
import com.example.virtualpetd.service.PetLevelProvider;
/** Reads C's persisted level for each D refresh and purchase validation. */
public final class RepositoryPetLevelProvider implements PetLevelProvider {
 private final PetRepository repository;
 public RepositoryPetLevelProvider(PetRepository repository) { this.repository = repository; }
 @Override public int getLevel(String userId) {
  synchronized (repository) { return repository.getPet(userId).getLevel(); }
 }
}
