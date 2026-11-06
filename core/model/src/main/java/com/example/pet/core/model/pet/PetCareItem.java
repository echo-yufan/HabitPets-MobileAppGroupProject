package com.example.pet.core.model.pet;

/** Read-only owned-item snapshot shared with the care panel, independent of storage and Android UI. */
public final class PetCareItem {
    public final String id, name, type, slot;
    public final int quantity, moodGain, satietyGain;
    public final boolean equipped;
    public PetCareItem(String id, String name, String type, int quantity, boolean equipped, int moodGain, int satietyGain) {
        this.id=id; this.name=name; this.type=type; this.quantity=quantity; this.equipped=equipped;
        this.slot=PetOutfits.slotOf(id); this.moodGain=moodGain; this.satietyGain=satietyGain;
    }
}
