package com.example.pet.feature.pet.domain.model;
import com.example.pet.core.model.pet.Pet;
/** 只有保存成功才返回的成长结果。 */
public final class LevelUpResult {
    private final Pet pet;
    private final int levelsGained;
    public LevelUpResult(Pet pet, int levelsGained) { this.pet = pet; this.levelsGained = levelsGained; }
    public Pet getPet() { return pet; }
    public int getLevelsGained() { return levelsGained; }
}