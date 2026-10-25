package com.example.pet.feature.pet.data.local;

import android.content.SharedPreferences;
import com.example.pet.core.model.pet.Pet;
import com.example.pet.feature.pet.data.repository.PetRepository;
import java.util.UUID;

/** 唯一宠物记录。Application 持有一个实例；所有字段一次提交。 */
public final class LocalPetRepository implements PetRepository {
    public static final String PREFERENCES_NAME = "habitpets_pet";
    private final SharedPreferences preferences;
    // commit 失败也可能已经更新内存。恢复确认前，不把它当作已保存的数据。
    private boolean needsRecovery;
    private Pet recoveryPet;
    public LocalPetRepository(SharedPreferences preferences) { this.preferences = preferences; }

    @Override public synchronized Pet getOrCreate() {
        recoverIfNeeded();
        if (!preferences.contains("schema")) {
            // 有残缺记录时报告失败，避免静默覆盖用户数据。
            if (!preferences.getAll().isEmpty()) throw new IllegalStateException("Incomplete pet record");
            Pet pet = new Pet(UUID.randomUUID().toString(), "牛来", "cow_brown", 1, 0, 80, 80);
            write(pet, null);
            return pet;
        }
        if (preferences.getInt("schema", -1) != 1) throw new IllegalStateException("Unsupported pet schema");
        for (String key : new String[]{"id", "name", "appearance", "level", "exp", "mood", "satiety"}) {
            if (!preferences.contains(key)) throw new IllegalStateException("Incomplete pet record");
        }
        return new Pet(preferences.getString("id", null), preferences.getString("name", null),
                preferences.getString("appearance", null), preferences.getInt("level", -1),
                preferences.getInt("exp", -1), preferences.getInt("mood", -1),
                preferences.getInt("satiety", -1));
    }

    @Override public synchronized void save(Pet pet) {
        if (pet == null) throw new IllegalArgumentException("Pet required");
        Pet current = getOrCreate();
        if (!current.getId().equals(pet.getId())) throw new IllegalArgumentException("Different pet identity");
        write(pet, current);
    }

    private void write(Pet pet, Pet previous) {
        // 在提交前记住最后确认的记录，异常和 false 都进入恢复状态。
        recoveryPet = previous;
        needsRecovery = true;
        if (!editRecord(pet).commit()) throw new IllegalStateException("Pet could not be saved to disk");
        needsRecovery = false;
        recoveryPet = null;
    }

    private void recoverIfNeeded() {
        if (!needsRecovery) return;
        if (!editRecord(recoveryPet).commit())
            throw new IllegalStateException("Pet storage recovery failed");
        needsRecovery = false;
        recoveryPet = null;
    }

    private SharedPreferences.Editor editRecord(Pet pet) {
        SharedPreferences.Editor editor = preferences.edit();
        if (pet == null) {
            // 首次初始化失败：恢复到没有宠物记录的状态，再允许正常初始化。
            for (String key : new String[]{"schema", "id", "name", "appearance", "level", "exp", "mood", "satiety"})
                editor.remove(key);
            return editor;
        }
        return editor.putInt("schema", 1)
                .putString("id", pet.getId()).putString("name", pet.getName())
                .putString("appearance", pet.getAppearanceId()).putInt("level", pet.getLevel())
                .putInt("exp", pet.getExp()).putInt("mood", pet.getMood())
                .putInt("satiety", pet.getSatiety());
    }
}