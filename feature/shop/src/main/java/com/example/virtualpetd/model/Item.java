package com.example.virtualpetd.model;

public class Item {

    private String id;
    private String name;
    private String type;
    private int price;
    private String effect;
    private String assetKey;
    private final int requiredLevel;

    public Item(String id, String name, String type, int price,
                String effect, String assetKey) {
        this(id, name, type, price, effect, assetKey, 1);
    }

    public Item(String id, String name, String type, int price,
                String effect, String assetKey, int requiredLevel) {
        if (requiredLevel < 1 || price < 1) throw new IllegalArgumentException("Invalid item configuration");
        this.requiredLevel = requiredLevel;
        this.id = id;
        this.name = name;
        this.type = type;
        this.price = price;
        this.effect = effect;
        this.assetKey = assetKey;
    }

    public int getRequiredLevel() { return requiredLevel; }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public int getPrice() {
        return price;
    }

    public String getEffect() {
        return effect;
    }

    public String getAssetKey() {
        return assetKey;
    }
}