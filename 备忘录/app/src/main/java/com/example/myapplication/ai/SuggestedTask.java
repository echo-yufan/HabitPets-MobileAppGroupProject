package com.example.myapplication.ai;

/** AI 拆解产出的单个建议子任务 */
public class SuggestedTask {

    public String name;
    public Long deadline;       // epoch 毫秒，可为 null
    public int rewardExp;
    public int rewardCoins;

    public SuggestedTask(String name, Long deadline, int rewardExp, int rewardCoins) {
        this.name = name;
        this.deadline = deadline;
        this.rewardExp = rewardExp;
        this.rewardCoins = rewardCoins;
    }
}
