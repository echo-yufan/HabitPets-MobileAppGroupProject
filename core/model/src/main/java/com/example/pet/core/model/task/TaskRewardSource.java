package com.example.pet.core.model.task;

import java.util.List;

/** Background-only outbox stored in the same transaction as task completion. */
public interface TaskRewardSource {
    List<TaskCompletionReward> pendingRewards();
    void acknowledgeReward(String source);
}
