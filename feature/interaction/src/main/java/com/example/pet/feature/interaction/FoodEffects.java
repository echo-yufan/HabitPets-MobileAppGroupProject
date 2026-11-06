package com.example.pet.feature.interaction;

/** Explicit values, independent of translated shop descriptions. Values are capped at 100. */
public final class FoodEffects {
    private FoodEffects() {}
    public static int[] forItem(String id) {
        switch (id) {
            case "apple": return new int[]{5, 15};
            case "carrot": return new int[]{3, 10};
            case "milk": return new int[]{5, 20};
            case "cookie": return new int[]{12, 12};
            case "strawberry": return new int[]{12, 18};
            case "bread": return new int[]{8, 30};
            case "cake": return new int[]{25, 25};
            case "bento": return new int[]{20, 45};
            case "feast": return new int[]{40, 60};
            default: throw new IllegalArgumentException("Unknown food");
        }
    }
}
