package com.example.pet.feature.pet.domain.usecase;

import com.example.pet.core.model.pet.Pet;
import com.example.pet.feature.pet.data.repository.PetRepository;

/** C 提供给 E 的状态更新入口。后台调用；satiety 越高越饱。 */
public final class ChangePetStatus {
    private final PetRepository repository;
    public ChangePetStatus(PetRepository repository) { this.repository = repository; }

    /** 两项基于同一份快照计算并一次保存，成功后才返回。 */
    public Pet execute(int moodDelta, int satietyDelta) {
        synchronized (repository) {
            Pet before = repository.getPet(PetRepository.LOCAL_USER_ID);
            Pet after = new Pet(before.getId(), before.getName(), before.getAppearanceId(),
                    before.getLevel(), before.getExp(),
                    clamp(before.getMood(), moodDelta), clamp(before.getSatiety(), satietyDelta));
            repository.updatePet(after);
            return after;
        }
    }
    public Pet changeMood(int delta) { return execute(delta, 0); }
    public Pet changeSatiety(int delta) { return execute(0, delta); }
    private static int clamp(int value, int delta) {
        return (int) Math.max(0L, Math.min(100L, (long) value + delta));
    }
}
