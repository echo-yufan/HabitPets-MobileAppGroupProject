package com.example.virtualpetd.service;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.example.virtualpetd.data.EconomyDatabase;

/** Synchronous storage API: callers must run it off the UI thread. */
public class WalletService {
    private final EconomyDatabase helper;
    private final String userId;
    public WalletService(Context context) { this(EconomyDatabase.get(context), EconomyDatabase.LOCAL_USER); }
    public WalletService(EconomyDatabase helper, String userId) {
        if (userId == null || userId.trim().isEmpty()) throw new IllegalArgumentException("userId required");
        this.helper = helper; this.userId = userId;
    }
    public static int readBalance(SQLiteDatabase db, String userId) {
        try (Cursor c = db.rawQuery("SELECT balance FROM wallet WHERE user_id=?", new String[]{userId})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }
    public int getBalance() { return readBalance(helper.getReadableDatabase(), userId); }
    /** source must be the unique completion record ID, NOT a generic event type. */
    public boolean addCoins(int amount, String source) {
        if (amount <= 0 || source == null || source.trim().isEmpty()) return false;
        SQLiteDatabase db = helper.getWritableDatabase(); db.beginTransaction();
        try {
            try (Cursor c = db.rawQuery("SELECT 1 FROM rewards WHERE user_id=? AND source=?", new String[]{userId,source})) {
                if (c.moveToFirst()) return false;
            }
            int current = readBalance(db,userId);
            if ((long)current + amount > Integer.MAX_VALUE) return false;
            db.execSQL("INSERT OR IGNORE INTO wallet(user_id,balance) VALUES (?,0)", new Object[]{userId});
            db.execSQL("UPDATE wallet SET balance=balance+? WHERE user_id=?", new Object[]{amount,userId});
            db.execSQL("INSERT INTO rewards(user_id,source,amount) VALUES (?,?,?)", new Object[]{userId,source,amount});
            db.setTransactionSuccessful(); return true;
        } finally { db.endTransaction(); }
    }
    /** Standalone debit only; purchases must use ShopService's atomic transaction. */
    public boolean spendCoins(int amount) {
        if (amount <= 0) return false;
        SQLiteDatabase db = helper.getWritableDatabase(); db.beginTransaction();
        try {
            if (readBalance(db,userId) < amount) return false;
            db.execSQL("UPDATE wallet SET balance=balance-? WHERE user_id=?",new Object[]{amount,userId});
            db.setTransactionSuccessful(); return true;
        } finally { db.endTransaction(); }
    }
}
