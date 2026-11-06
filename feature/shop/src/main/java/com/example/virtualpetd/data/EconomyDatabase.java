package com.example.virtualpetd.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/** Shared transaction boundary for wallet, inventory and idempotency records. */
public final class EconomyDatabase extends SQLiteOpenHelper {
    public static final String LOCAL_USER = "local_user";
    private static volatile EconomyDatabase instance;
    private final Context context;
    public static EconomyDatabase get(Context context) {
        if (instance == null) synchronized (EconomyDatabase.class) {
            if (instance == null) instance = new EconomyDatabase(context.getApplicationContext(), "economy.db");
        }
        return instance;
    }
    public EconomyDatabase(Context context, String name) {
        super(context.getApplicationContext(), name, null, 2);
        this.context = context.getApplicationContext();
    }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE wallet (user_id TEXT PRIMARY KEY, balance INTEGER NOT NULL CHECK(balance >= 0 AND balance <= 2147483647))");
        db.execSQL("CREATE TABLE inventory (user_id TEXT NOT NULL, item_id TEXT NOT NULL, quantity INTEGER NOT NULL CHECK(quantity >= 0), equipped INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(user_id,item_id))");
        db.execSQL("CREATE TABLE rewards (user_id TEXT NOT NULL, source TEXT NOT NULL, amount INTEGER NOT NULL, PRIMARY KEY(user_id,source))");
        db.execSQL("CREATE TABLE purchases (user_id TEXT NOT NULL, request_id TEXT NOT NULL, item_id TEXT NOT NULL, quantity INTEGER NOT NULL DEFAULT 1, PRIMARY KEY(user_id,request_id))");
        SharedPreferences old = context.getSharedPreferences("wallet_prefs", Context.MODE_PRIVATE);
        // Only an actually saved legacy balance is migrated; clean installations start at zero.
        int balance = Math.max(0, old.getInt("coin_balance", 0));
        db.execSQL("INSERT INTO wallet(user_id,balance) VALUES (?,?)", new Object[]{LOCAL_USER,balance});
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            // Every v1 order was exactly one item. Keep historical request IDs intact.
            db.execSQL("ALTER TABLE purchases ADD COLUMN quantity INTEGER NOT NULL DEFAULT 1");
        }
    }
}
