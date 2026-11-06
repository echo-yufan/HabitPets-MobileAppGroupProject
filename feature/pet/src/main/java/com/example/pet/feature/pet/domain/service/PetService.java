package com.example.pet.feature.pet.domain.service;

import com.example.pet.core.model.pet.Pet;
import com.example.pet.feature.pet.data.repository.PetRepository;
import com.example.pet.feature.pet.domain.model.LevelUpResult;
import com.example.pet.feature.pet.domain.usecase.AddPetExp;
import com.example.pet.feature.pet.domain.usecase.ChangePetStatus;

/**
 * 交付说明第 6.3 节对外业务入口。所有方法均同步，调用方使用后台队列。
 * hunger 按文档“喂食后增加”的含义：越高越饱，与存储 satiety 一致。
 */
public final class PetService {
    private final AddPetExp growth;
    private final ChangePetStatus status;
    private final PetRepository repository;
    public PetService(PetRepository repository) {
        this.repository = repository;
        growth = new AddPetExp(repository);
        status = new ChangePetStatus(repository);
    }
    public LevelUpResult addExp(int amount) { return growth.execute(amount); }
    public Pet changeMood(int delta) { return status.changeMood(delta); }
    public Pet changeHunger(int delta) { return status.changeSatiety(delta); }
    /** 扩展入口：一次互动影响两项时必须合并保存。 */
    public Pet changeStatus(int moodDelta, int hungerDelta) {
        return status.execute(moodDelta, hungerDelta);
    }
    /**
     * E's host coordinator validates ownership and persists the outfit in inventory.
     * The cow base appearance remains unchanged. Standalone C hosts must provide E first.
     */
    public Pet equipAppearance(String itemId) {
        if (itemId == null || itemId.trim().isEmpty())
            throw new IllegalArgumentException("Item ID required");
        if (repository instanceof com.example.pet.core.model.pet.PetInteractions) {
            if (!com.example.pet.core.model.pet.PetOutfits.contains(itemId))
                throw new IllegalArgumentException("Expected clothing");
            com.example.pet.core.model.pet.PetInteractions.Result result =
                    ((com.example.pet.core.model.pet.PetInteractions) repository).useItem(itemId, java.util.UUID.randomUUID().toString());
            if (result.food) throw new IllegalArgumentException("Expected clothing");
            if (result.status != com.example.pet.core.model.pet.PetInteractions.Status.SUCCESS
                    && result.status != com.example.pet.core.model.pet.PetInteractions.Status.ALREADY_EQUIPPED)
                throw new IllegalStateException("Equipment failed: " + result.status);
            return repository.getPet(PetRepository.LOCAL_USER_ID);
        }
        throw new UnsupportedOperationException("Host must provide PetInteractions for equipment");
    }
}
