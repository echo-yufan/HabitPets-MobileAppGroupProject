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
    public PetService(PetRepository repository) {
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
     * 文档约定的预留签名。尚无 D/E 商品到外观的映射或装备模型，
     * 不写入数据、不返回成功；调用方必须处理此异常，不能据此扣物品。
     */
    public Pet equipAppearance(String itemId) {
        if (itemId == null || itemId.trim().isEmpty())
            throw new IllegalArgumentException("Item ID required");
        throw new UnsupportedOperationException("Equipment integration is not implemented yet");
    }
}
