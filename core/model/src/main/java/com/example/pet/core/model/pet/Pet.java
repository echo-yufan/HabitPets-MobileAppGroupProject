package com.example.pet.core.model.pet;

/** 不可变的宠物信息；不依赖 Android 页面或存储。 */
public final class Pet {
    public static final int EXP_PER_LEVEL = 100;
    private final String id, name, appearanceId;
    private final int level, exp, mood, satiety;
    public Pet(String id, String name, String appearanceId, int level, int exp, int mood, int satiety) {
        if (id == null || id.trim().isEmpty() || name == null || name.trim().isEmpty()
                || !"cow_brown".equals(appearanceId) || level < 1 || exp < 0 || exp >= EXP_PER_LEVEL
                || mood < 0 || mood > 100 || satiety < 0 || satiety > 100) {
            throw new IllegalArgumentException("Invalid pet state");
        }
        this.id = id; this.name = name; this.appearanceId = appearanceId;
        this.level = level; this.exp = exp; this.mood = mood; this.satiety = satiety;
    }
    public String getId() { return id; }
    public String getName() { return name; }
    public String getAppearanceId() { return appearanceId; }
    public int getLevel() { return level; }
    public int getExp() { return exp; }
    public int getMood() { return mood; }
    /** 文档 hunger 的兼容读取名称；数值越高越饱。 */
    public int getHunger() { return satiety; }
    public int getSatiety() { return satiety; }
}