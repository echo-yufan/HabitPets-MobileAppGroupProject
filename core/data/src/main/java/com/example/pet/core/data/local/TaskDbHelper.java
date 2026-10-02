package com.example.pet.core.data.local;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class TaskDbHelper extends SQLiteOpenHelper {

    public static final String DB_NAME = "pet_planner.db";
    public static final int DB_VERSION = 3;
    public static final String TABLE_TASKS = "tasks";

    public static final String COL_ID = "id";
    public static final String COL_USER_ID = "userId";
    public static final String COL_NAME = "name";
    public static final String COL_TYPE = "type";
    public static final String COL_FREQUENCY = "frequency";
    public static final String COL_REPEAT_DAYS = "repeatDays";
    public static final String COL_DEADLINE = "deadline";
    public static final String COL_REWARD_EXP = "rewardExp";
    public static final String COL_REWARD_COINS = "rewardCoins";
    public static final String COL_COMPLETED = "completed";
    public static final String COL_REMINDER_AT = "reminderAt";
    public static final String COL_CREATED_AT = "createdAt";
    public static final String COL_COMPLETED_AT = "completedAt";
    public static final String COL_SOURCE = "source";
    public static final String COL_TAG = "tag";

    public TaskDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_TASKS + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_USER_ID + " TEXT NOT NULL, "
                + COL_NAME + " TEXT NOT NULL, "
                + COL_TYPE + " TEXT, "
                + COL_FREQUENCY + " TEXT, "
                + COL_REPEAT_DAYS + " TEXT, "
                + COL_DEADLINE + " INTEGER, "
                + COL_REWARD_EXP + " INTEGER DEFAULT 10, "
                + COL_REWARD_COINS + " INTEGER DEFAULT 5, "
                + COL_COMPLETED + " INTEGER DEFAULT 0, "
                + COL_REMINDER_AT + " INTEGER, "
                + COL_CREATED_AT + " INTEGER NOT NULL, "
                + COL_COMPLETED_AT + " INTEGER, "
                + COL_SOURCE + " TEXT, "
                + COL_TAG + " TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE " + TABLE_TASKS + " ADD COLUMN " + COL_SOURCE + " TEXT");
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE " + TABLE_TASKS + " ADD COLUMN " + COL_TAG + " TEXT");
        }
    }
}
