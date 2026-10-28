package com.example.virtualpetd.model;

public class InventoryItem {

    private String userId;
    private String itemId;
    private int quantity;
    private boolean equipped;

    public InventoryItem(String userId, String itemId, int quantity, boolean equipped) {
        this.userId = userId;
        this.itemId = itemId;
        this.quantity = quantity;
        this.equipped = equipped;
    }

    public String getUserId() {
        return userId;
    }

    public String getItemId() {
        return itemId;
    }

    public int getQuantity() {
        return quantity;
    }

    public boolean isEquipped() {
        return equipped;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public void setEquipped(boolean equipped) {
        this.equipped = equipped;
    }
}