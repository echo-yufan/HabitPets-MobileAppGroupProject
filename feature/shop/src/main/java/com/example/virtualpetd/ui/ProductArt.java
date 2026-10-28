package com.example.virtualpetd.ui;
import com.example.virtualpetd.R;
public final class ProductArt {
    private ProductArt() {}
    public static int resource(String id) {
        switch(id) {
            case "apple": return R.drawable.shop_item_apple;
            case "carrot": return R.drawable.shop_item_carrot;
            case "milk": return R.drawable.shop_item_milk;
            case "hat": return R.drawable.shop_item_hat;
            case "bow": return R.drawable.shop_item_bow;
            case "scarf": return R.drawable.shop_item_scarf;
            case "cookie": return R.drawable.shop_item_cookie;
            case "beret": return R.drawable.shop_item_beret;
            case "strawberry": return R.drawable.shop_item_strawberry;
            case "ribbon": return R.drawable.shop_item_ribbon;
            case "bread": return R.drawable.shop_item_bread;
            case "cake": return R.drawable.shop_item_cake;
            case "raincoat": return R.drawable.shop_item_raincoat;
            case "vest": return R.drawable.shop_item_vest;
            case "bento": return R.drawable.shop_item_bento;
            case "wizard": return R.drawable.shop_item_wizard;
            case "feast": return R.drawable.shop_item_feast;
            case "crown": return R.drawable.shop_item_crown;
            default: return R.drawable.shop_bag;
        }
    }
}
