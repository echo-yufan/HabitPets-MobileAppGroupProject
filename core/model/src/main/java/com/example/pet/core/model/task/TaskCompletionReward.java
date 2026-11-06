package com.example.pet.core.model.task;

/** Durable first-completion receipt. The source survives task deletion and UI retries. */
public final class TaskCompletionReward {
    public final String source;
    public final long taskId;
    public final int coins, exp;
    public TaskCompletionReward(String source, long taskId, int coins, int exp) {
        this.source = source; this.taskId = taskId; this.coins = coins; this.exp = exp;
    }
}
