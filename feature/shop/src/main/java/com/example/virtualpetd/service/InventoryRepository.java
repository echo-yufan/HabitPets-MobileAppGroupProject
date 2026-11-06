package com.example.virtualpetd.service;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.example.virtualpetd.data.EconomyDatabase;
import com.example.virtualpetd.model.InventoryItem;
import com.example.virtualpetd.model.Item;
import java.util.ArrayList;
import java.util.List;

/** Inventory operations for E. Pet effects must be coordinated by E with C. */
public final class InventoryRepository {
    private final EconomyDatabase helper;
    private final ShopService catalog;
    private final String userId;
    public InventoryRepository(Context context) { this(context,EconomyDatabase.get(context),EconomyDatabase.LOCAL_USER); }
    public InventoryRepository(Context context,EconomyDatabase helper,String userId) {
        this.helper=helper; this.userId=userId; this.catalog=new ShopService(context,helper,userId);
    }
    public List<InventoryItem> getItems() { return getItems(userId); }
    public List<InventoryItem> getItems(String user) {
        List<InventoryItem> result=new ArrayList<>();
        try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT item_id,quantity,equipped FROM inventory WHERE user_id=? AND quantity>0 ORDER BY item_id",new String[]{user})) {
            while(c.moveToNext()) result.add(new InventoryItem(user,c.getString(0),c.getInt(1),c.getInt(2)!=0));
        }
        return result;
    }
    /** Controlled grant API, not the purchase path. Unique clothes cannot be granted twice. */
    public boolean addItem(String itemId) {
        Item item=catalog.find(itemId); if(item==null) return false;
        SQLiteDatabase db=helper.getWritableDatabase(); db.beginTransaction();
        try {
            int count=quantity(db,itemId);
            if(count==Integer.MAX_VALUE || ("Clothes".equals(item.getType()) && count>0)) return false;
            db.execSQL("INSERT OR IGNORE INTO inventory(user_id,item_id,quantity,equipped) VALUES (?,?,0,0)",new Object[]{userId,itemId});
            db.execSQL("UPDATE inventory SET quantity=quantity+1 WHERE user_id=? AND item_id=?",new Object[]{userId,itemId});
            db.setTransactionSuccessful(); return true;
        } finally { db.endTransaction(); }
    }
    public boolean consumeItem(String itemId) {
        Item item=catalog.find(itemId); if(item==null || !"Food".equals(item.getType())) return false;
        SQLiteDatabase db=helper.getWritableDatabase(); db.beginTransaction();
        try {
            if(quantity(db,itemId)<1) return false;
            db.execSQL("UPDATE inventory SET quantity=quantity-1 WHERE user_id=? AND item_id=?",new Object[]{userId,itemId});
            db.setTransactionSuccessful(); return true;
        } finally { db.endTransaction(); }
    }
    /** MVP has one outfit slot. This persists inventory state, not the pet's appearance. */
    public boolean setEquipped(String itemId) {
        Item item=catalog.find(itemId); if(item==null || !"Clothes".equals(item.getType())) return false;
        SQLiteDatabase db=helper.getWritableDatabase(); db.beginTransaction();
        try {
            if(quantity(db,itemId)<1) return false;
            db.execSQL("UPDATE inventory SET equipped=0 WHERE user_id=?",new Object[]{userId});
            db.execSQL("UPDATE inventory SET equipped=1 WHERE user_id=? AND item_id=?",new Object[]{userId,itemId});
            db.setTransactionSuccessful(); return true;
        } finally { db.endTransaction(); }
    }
    private int quantity(SQLiteDatabase db,String itemId) {
        try(Cursor c=db.rawQuery("SELECT quantity FROM inventory WHERE user_id=? AND item_id=?",new String[]{userId,itemId})) {
            return c.moveToFirst()?c.getInt(0):0;
        }
    }
}
