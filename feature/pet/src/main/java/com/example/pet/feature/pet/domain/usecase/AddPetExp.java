package com.example.pet.feature.pet.domain.usecase;
import com.example.pet.core.model.pet.Pet;
import com.example.pet.feature.pet.data.repository.PetRepository;
import com.example.pet.feature.pet.domain.model.LevelUpResult;

/** 后台调用；同一个仓库上的读取、计算、保存串行完成。尚未处理任务奖励去重。 */
public final class AddPetExp {
    private final PetRepository repository;
    public AddPetExp(PetRepository repository) { this.repository = repository; }
    public LevelUpResult execute(int amount) {
        if (amount < 0) throw new IllegalArgumentException("Experience reward must not be negative");
        synchronized (repository) {
            Pet before = repository.getPet(PetRepository.LOCAL_USER_ID);
            long total = (long) before.getExp() + amount;
            long level = (long) before.getLevel() + total / Pet.EXP_PER_LEVEL;
            if (level > Integer.MAX_VALUE) throw new IllegalArgumentException("Level limit exceeded");
            if (amount == 0) return new LevelUpResult(before, 0);
            Pet after = new Pet(before.getId(), before.getName(), before.getAppearanceId(),
                    (int) level, (int) (total % Pet.EXP_PER_LEVEL), before.getMood(), before.getSatiety());
            repository.updatePet(after);
            return new LevelUpResult(after, after.getLevel() - before.getLevel());
        }
    }
}