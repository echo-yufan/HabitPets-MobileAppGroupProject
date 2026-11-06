package com.example.pet.feature.pet.data.repository;
import com.example.pet.core.model.pet.Pet;
/** 同步磁盘接口，必须后台调用。当前仅支持项目已有的本地用户。 */
public interface PetRepository {
    String LOCAL_USER_ID = "local_user";

    /** 文档对外入口。首次读取会初始化；不得把不同用户静默映射到同一宠物。 */
    default Pet getPet(String userId) {
        if (!LOCAL_USER_ID.equals(userId))
            throw new IllegalArgumentException("Only local_user is supported");
        return getOrCreate();
    }
    /** 文档对外保存入口，保留仓库已有身份校验及失败恢复。 */
    default void updatePet(Pet pet) { save(pet); }

    /** 旧代码兼容入口；组员新接入请使用 getPet(userId)。 */
    Pet getOrCreate();
    /** 旧代码兼容入口；业务状态变更优先使用 PetService。 */
    void save(Pet pet);
}
