package com.example.pet.feature.pet.presentation;
/** 一次性提示；页面重建后不重复消费旧的升级消息。 */
public final class PetEvent {
    public final int reachedLevel;
    public final boolean failed;
    public final boolean invalidReward;
    public final boolean statusUpdateFailed;
    private boolean consumed;
    public PetEvent(int reachedLevel, boolean failed, boolean invalidReward) {
        this(reachedLevel, failed, invalidReward, false);
    }
    private PetEvent(int reachedLevel, boolean failed, boolean invalidReward, boolean statusUpdateFailed) {
        this.statusUpdateFailed = statusUpdateFailed;
        this.reachedLevel = reachedLevel; this.failed = failed; this.invalidReward = invalidReward;
    }
    public static PetEvent statusSaveFailed() { return new PetEvent(0, true, false, true); }
    public boolean consume() {
        if (consumed) return false;
        consumed = true; return true;
    }
}