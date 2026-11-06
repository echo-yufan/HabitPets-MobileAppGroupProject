package com.example.virtualpetd.service;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import com.example.virtualpetd.R;
import com.example.virtualpetd.data.EconomyDatabase;
import com.example.virtualpetd.model.Item;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class ShopService {
    public enum PurchaseResult { SUCCESS, INSUFFICIENT_COINS, ALREADY_OWNED, ITEM_NOT_FOUND, INVALID_REQUEST, INVALID_QUANTITY, LEVEL_LOCKED, STORAGE_ERROR }
    private final EconomyDatabase helper;
    private final String userId;
    public static final int MAX_FOOD_QUANTITY = 99;
    private final List<Item> items;
    private final PetLevelProvider levels;
    public ShopService(Context context) { this(context,EconomyDatabase.get(context),EconomyDatabase.LOCAL_USER); }
    public ShopService(Context context, EconomyDatabase helper, String userId) {
        this(context, helper, userId, PetLevels.resolve(context));
    }
    public ShopService(Context context, EconomyDatabase helper, String userId, PetLevelProvider levels) {
        if (levels == null) throw new IllegalArgumentException("PetLevelProvider required");
        this.levels = levels;
        if (userId == null || userId.trim().isEmpty()) throw new IllegalArgumentException("userId required");
        this.helper=helper; this.userId=userId;
        items=Collections.unmodifiableList(Arrays.asList(
            new Item("apple",context.getString(R.string.shop_apple),"Food",20,context.getString(R.string.shop_desc_apple),"shop_item_apple",1),
            new Item("carrot",context.getString(R.string.shop_carrot),"Food",10,context.getString(R.string.shop_desc_carrot),"shop_item_carrot",1),
            new Item("milk",context.getString(R.string.shop_milk),"Food",30,context.getString(R.string.shop_desc_milk),"shop_item_milk",1),
            new Item("hat",context.getString(R.string.shop_hat),"Clothes",50,context.getString(R.string.shop_desc_hat),"shop_item_hat",1),
            new Item("bow",context.getString(R.string.shop_bow),"Clothes",60,context.getString(R.string.shop_desc_bow),"shop_item_bow",1),
            new Item("scarf",context.getString(R.string.shop_scarf),"Clothes",80,context.getString(R.string.shop_desc_scarf),"shop_item_scarf",1),
            new Item("cookie",context.getString(R.string.shop_cookie),"Food",35,context.getString(R.string.shop_desc_cookie),"shop_item_cookie",2),
            new Item("beret",context.getString(R.string.shop_beret),"Clothes",95,context.getString(R.string.shop_desc_beret),"shop_item_beret",2),
            new Item("strawberry",context.getString(R.string.shop_strawberry),"Food",45,context.getString(R.string.shop_desc_strawberry),"shop_item_strawberry",3),
            new Item("ribbon",context.getString(R.string.shop_ribbon),"Clothes",110,context.getString(R.string.shop_desc_ribbon),"shop_item_ribbon",3),
            new Item("bread",context.getString(R.string.shop_bread),"Food",55,context.getString(R.string.shop_desc_bread),"shop_item_bread",3),
            new Item("cake",context.getString(R.string.shop_cake),"Food",75,context.getString(R.string.shop_desc_cake),"shop_item_cake",5),
            new Item("raincoat",context.getString(R.string.shop_raincoat),"Clothes",150,context.getString(R.string.shop_desc_raincoat),"shop_item_raincoat",5),
            new Item("vest",context.getString(R.string.shop_vest),"Clothes",180,context.getString(R.string.shop_desc_vest),"shop_item_vest",5),
            new Item("bento",context.getString(R.string.shop_bento),"Food",95,context.getString(R.string.shop_desc_bento),"shop_item_bento",7),
            new Item("wizard",context.getString(R.string.shop_wizard),"Clothes",240,context.getString(R.string.shop_desc_wizard),"shop_item_wizard",7),
            new Item("feast",context.getString(R.string.shop_feast),"Food",130,context.getString(R.string.shop_desc_feast),"shop_item_feast",10),
            new Item("crown",context.getString(R.string.shop_crown),"Clothes",320,context.getString(R.string.shop_desc_crown),"shop_item_crown",10)
        ));
    }

    public int getPetLevel() { return Math.max(1, levels.getLevel(userId)); }
    public List<Item> getItems() { return items; }
    public Item find(String id) { for(Item item:items) if(item.getId().equals(id)) return item; return null; }
    public PurchaseResult purchase(String itemId) { return purchase(itemId,UUID.randomUUID().toString()); }
    public PurchaseResult purchase(String itemId,String requestId) {
        return purchase(itemId, 1, requestId);
    }
    /** Same request ID must refer to the same item AND quantity, including after restart. */
    public PurchaseResult purchase(String itemId, int count, String requestId) {
        Item item=find(itemId);
        if(item==null) return PurchaseResult.ITEM_NOT_FOUND;
        if (count < 1 || count > MAX_FOOD_QUANTITY
                || ("Clothes".equals(item.getType()) && count != 1)) return PurchaseResult.INVALID_QUANTITY;
        long total = (long) item.getPrice() * count;
        if (total > Integer.MAX_VALUE) return PurchaseResult.INVALID_QUANTITY;
        if(requestId==null || requestId.trim().isEmpty()) return PurchaseResult.INVALID_REQUEST;
        try {
            SQLiteDatabase db=helper.getWritableDatabase(); db.beginTransaction();
            try {
                try(Cursor c=db.rawQuery("SELECT item_id,quantity FROM purchases WHERE user_id=? AND request_id=?",new String[]{userId,requestId})) {
                    if(c.moveToFirst()) return (itemId.equals(c.getString(0)) && c.getInt(1)==count) ? PurchaseResult.SUCCESS : PurchaseResult.INVALID_REQUEST;
                }
                // Never trust the level used by the UI when the detail sheet was opened.
                if (getPetLevel() < item.getRequiredLevel()) return PurchaseResult.LEVEL_LOCKED;
                int quantity=0;
                try(Cursor c=db.rawQuery("SELECT quantity FROM inventory WHERE user_id=? AND item_id=?",new String[]{userId,itemId})) {
                    if(c.moveToFirst()) quantity=c.getInt(0);
                }
                if("Clothes".equals(item.getType()) && quantity>0) return PurchaseResult.ALREADY_OWNED;
                if((long)quantity+count>Integer.MAX_VALUE) return PurchaseResult.INVALID_REQUEST;
                if(WalletService.readBalance(db,userId)<total) return PurchaseResult.INSUFFICIENT_COINS;
                db.execSQL("UPDATE wallet SET balance=balance-? WHERE user_id=?",new Object[]{total,userId});
                db.execSQL("INSERT OR IGNORE INTO inventory(user_id,item_id,quantity,equipped) VALUES (?,?,0,0)",new Object[]{userId,itemId});
                db.execSQL("UPDATE inventory SET quantity=quantity+? WHERE user_id=? AND item_id=?",new Object[]{count,userId,itemId});
                db.execSQL("INSERT INTO purchases(user_id,request_id,item_id,quantity) VALUES (?,?,?,?)",new Object[]{userId,requestId,itemId,count});
                db.setTransactionSuccessful(); return PurchaseResult.SUCCESS;
            } finally { db.endTransaction(); }
        } catch(SQLiteException e) { return PurchaseResult.STORAGE_ERROR; }
    }
}
