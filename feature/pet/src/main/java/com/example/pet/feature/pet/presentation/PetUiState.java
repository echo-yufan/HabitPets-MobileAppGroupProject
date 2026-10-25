package com.example.pet.feature.pet.presentation;
import com.example.pet.core.model.pet.Pet;
/** 页面状态快照，区分加载、成功、失败。 */
public final class PetUiState {
    public final Pet pet;
    public final boolean loading;
    public final boolean failed;
    private PetUiState(Pet pet, boolean loading, boolean failed) {
        this.pet = pet; this.loading = loading; this.failed = failed;
    }
    public static PetUiState loading() { return loading(null); }
    public static PetUiState loading(Pet previous) { return new PetUiState(previous, true, false); }
    public static PetUiState ready(Pet pet) { return new PetUiState(pet, false, false); }
    public static PetUiState error() { return error(null); }
    public static PetUiState error(Pet previous) { return new PetUiState(previous, false, true); }
}