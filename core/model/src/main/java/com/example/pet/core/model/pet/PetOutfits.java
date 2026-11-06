package com.example.pet.core.model.pet;

/** Stable inventory IDs grouped by wearable body part. */
public final class PetOutfits {
    private PetOutfits() {}
    public static boolean contains(String id) {
        return slotOf(id) != null;
    }
    public static String slotOf(String id) {
        if ("hat".equals(id) || "beret".equals(id) || "ribbon".equals(id) || "wizard".equals(id) || "crown".equals(id)) return "head";
        if ("bow".equals(id) || "scarf".equals(id)) return "neck";
        if ("raincoat".equals(id) || "vest".equals(id)) return "body";
        return null;
    }
    public static String[] idsForSlot(String slot) {
        if ("head".equals(slot)) return new String[]{"hat","beret","ribbon","wizard","crown"};
        if ("neck".equals(slot)) return new String[]{"bow","scarf"};
        if ("body".equals(slot)) return new String[]{"raincoat","vest"};
        throw new IllegalArgumentException("Unknown outfit slot");
    }
}
