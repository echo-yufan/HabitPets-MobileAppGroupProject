package com.example.pet.core.model.task;

/**
 * 统一数据契约中的 Task/Habit 实体（分工文档第 10 节）。
 * 字段名称与含义全组约定，不要单独修改：
 * id, userId, name, type, frequency, repeatDays, deadline,
 * rewardExp, rewardCoins, completed
 * B 模块额外维护 reminderAt（提醒时间）与 source（来源标记，如 "ai"）。
 */
public class Task {

    public static final String TYPE_TASK = "TASK";
    public static final String TYPE_HABIT = "HABIT";

    public static final String SOURCE_AI = "ai";

    public long id;
    public String userId;
    public String name;
    public String type;
    public String frequency;      // DAILY / WEEKLY / null
    public String repeatDays;     // 形如 "1,3,5"，可为 null
    public Long deadline;         // 截止时间，epoch 毫秒，可为 null
    public int rewardExp;
    public int rewardCoins;
    public boolean completed;
    public Long reminderAt;       // 提醒时间，epoch 毫秒，可为 null
    public long createdAt;
    public Long completedAt;      // 完成时间，可为 null
    public String source;         // 来源：null=手动 / "ai"=AI 拆解
    public String tag;            // 展示标签（如 日常照顾 / 采购），可为 null

    public Task() {
        this.userId = "local_user";
        this.type = TYPE_TASK;
        this.rewardExp = 10;
        this.rewardCoins = 5;
        this.createdAt = System.currentTimeMillis();
    }

    public boolean isOverdue() {
        return !completed && deadline != null && deadline < System.currentTimeMillis();
    }
}
