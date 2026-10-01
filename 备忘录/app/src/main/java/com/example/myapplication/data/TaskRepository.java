package com.example.myapplication.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

/**
 * 统一任务数据入口（分工文档 4.3 接口约定的 B 侧本地实现）。
 * 集成阶段成员 A 的正式 TaskRepository 可直接替换本类，方法签名保持一致：
 * getTodayTasks(date) / createTask(task) / updateTask(task) / deleteTask(taskId)
 * B 额外提供 getUpcomingTasks(startDate, endDate)（分工文档 5.3）。
 */
public class TaskRepository {

    public static final int FILTER_ALL = 0;
    public static final int FILTER_ACTIVE = 1;
    public static final int FILTER_DONE = 2;
    public static final int FILTER_UPCOMING = 3;

    private static final long DAY_MS = 24L * 60 * 60 * 1000;
    private static volatile TaskRepository instance;

    private final TaskDbHelper helper;

    private TaskRepository(Context context) {
        helper = new TaskDbHelper(context.getApplicationContext());
    }

    public static TaskRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (TaskRepository.class) {
                if (instance == null) {
                    instance = new TaskRepository(context);
                }
            }
        }
        return instance;
    }

    // ---------- 查询 ----------

    /** 备忘录页面列表：全部（未完成在前，新创建的在前） */
    public List<Task> getTasks(int filter) {
        SQLiteDatabase db = helper.getReadableDatabase();
        String where = null;
        String order = "completed ASC, createdAt DESC";
        long now = System.currentTimeMillis();
        if (filter == FILTER_ACTIVE) {
            where = "completed = 0";
        } else if (filter == FILTER_DONE) {
            where = "completed = 1";
        } else if (filter == FILTER_UPCOMING) {
            // 近期视图：7 天内有截止（含已逾期）的未完成任务，按截止时间升序
            where = "completed = 0 AND deadline IS NOT NULL AND deadline <= " + (now + 7 * DAY_MS);
            order = "deadline ASC";
        }
        Cursor c = db.query(TaskDbHelper.TABLE_TASKS, null, where, null, null, null, order);
        List<Task> list = new ArrayList<>();
        while (c.moveToNext()) {
            list.add(fromCursor(c));
        }
        c.close();
        return list;
    }

    /** 契约方法：某天的今日任务（截止日在当天，或当天创建且未完成） */
    public List<Task> getTodayTasks(long dateStartMillis) {
        long dayEnd = dateStartMillis + DAY_MS - 1;
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor c = db.query(TaskDbHelper.TABLE_TASKS, null,
                "(deadline BETWEEN ? AND ?) OR (completed = 0 AND createdAt BETWEEN ? AND ?)",
                new String[]{String.valueOf(dateStartMillis), String.valueOf(dayEnd),
                        String.valueOf(dateStartMillis), String.valueOf(dayEnd)},
                null, null, "completed ASC, createdAt DESC");
        List<Task> list = new ArrayList<>();
        while (c.moveToNext()) {
            list.add(fromCursor(c));
        }
        c.close();
        return list;
    }

    /** 契约方法：区间内的近期任务（Upcoming 页面数据源） */
    public List<Task> getUpcomingTasks(long startMillis, long endMillis) {
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor c = db.query(TaskDbHelper.TABLE_TASKS, null,
                "deadline IS NOT NULL AND deadline BETWEEN ? AND ?",
                new String[]{String.valueOf(startMillis), String.valueOf(endMillis)},
                null, null, "deadline ASC");
        List<Task> list = new ArrayList<>();
        while (c.moveToNext()) {
            list.add(fromCursor(c));
        }
        c.close();
        return list;
    }

    public Task getTask(long taskId) {
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor c = db.query(TaskDbHelper.TABLE_TASKS, null, "id = ?",
                new String[]{String.valueOf(taskId)}, null, null, null);
        Task task = null;
        if (c.moveToFirst()) {
            task = fromCursor(c);
        }
        c.close();
        return task;
    }

    /** 开机后需要重新调度的提醒 */
    public List<Task> getTasksWithFutureReminders() {
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor c = db.query(TaskDbHelper.TABLE_TASKS, null,
                "reminderAt IS NOT NULL AND reminderAt > ? AND completed = 0",
                new String[]{String.valueOf(System.currentTimeMillis())},
                null, null, "reminderAt ASC");
        List<Task> list = new ArrayList<>();
        while (c.moveToNext()) {
            list.add(fromCursor(c));
        }
        c.close();
        return list;
    }

    // ---------- 写入 ----------

    /** 契约方法：创建任务，返回新 id */
    public long createTask(Task task) {
        SQLiteDatabase db = helper.getWritableDatabase();
        long id = db.insert(TaskDbHelper.TABLE_TASKS, null, toValues(task));
        task.id = id;
        return id;
    }

    /** 契约方法：更新任务 */
    public void updateTask(Task task) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.update(TaskDbHelper.TABLE_TASKS, toValues(task), "id = ?",
                new String[]{String.valueOf(task.id)});
    }

    /** 契约方法：删除任务 */
    public void deleteTask(long taskId) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.delete(TaskDbHelper.TABLE_TASKS, "id = ?", new String[]{String.valueOf(taskId)});
    }

    /** 完成打卡 / 取消打卡 */
    public void setCompleted(long taskId, boolean completed) {
        Task task = getTask(taskId);
        if (task == null) {
            return;
        }
        task.completed = completed;
        task.completedAt = completed ? System.currentTimeMillis() : null;
        updateTask(task);
    }

    /** 清空所有任务 */
    public int clearAll() {
        SQLiteDatabase db = helper.getWritableDatabase();
        return db.delete(TaskDbHelper.TABLE_TASKS, null, null);
    }

    /** 仅清空已完成的任务 */
    public int clearCompleted() {
        SQLiteDatabase db = helper.getWritableDatabase();
        return db.delete(TaskDbHelper.TABLE_TASKS, "completed = 1", null);
    }

    // ---------- 转换 ----------

    private Task fromCursor(Cursor c) {
        Task task = new Task();
        task.id = c.getLong(c.getColumnIndexOrThrow(TaskDbHelper.COL_ID));
        task.userId = c.getString(c.getColumnIndexOrThrow(TaskDbHelper.COL_USER_ID));
        task.name = c.getString(c.getColumnIndexOrThrow(TaskDbHelper.COL_NAME));
        task.type = c.getString(c.getColumnIndexOrThrow(TaskDbHelper.COL_TYPE));
        task.frequency = c.getString(c.getColumnIndexOrThrow(TaskDbHelper.COL_FREQUENCY));
        task.repeatDays = c.getString(c.getColumnIndexOrThrow(TaskDbHelper.COL_REPEAT_DAYS));
        task.deadline = getNullableLong(c, TaskDbHelper.COL_DEADLINE);
        task.rewardExp = c.getInt(c.getColumnIndexOrThrow(TaskDbHelper.COL_REWARD_EXP));
        task.rewardCoins = c.getInt(c.getColumnIndexOrThrow(TaskDbHelper.COL_REWARD_COINS));
        task.completed = c.getInt(c.getColumnIndexOrThrow(TaskDbHelper.COL_COMPLETED)) == 1;
        task.reminderAt = getNullableLong(c, TaskDbHelper.COL_REMINDER_AT);
        task.createdAt = c.getLong(c.getColumnIndexOrThrow(TaskDbHelper.COL_CREATED_AT));
        task.completedAt = getNullableLong(c, TaskDbHelper.COL_COMPLETED_AT);
        task.source = c.getString(c.getColumnIndexOrThrow(TaskDbHelper.COL_SOURCE));
        task.tag = c.getString(c.getColumnIndexOrThrow(TaskDbHelper.COL_TAG));
        return task;
    }

    private Long getNullableLong(Cursor c, String col) {
        int index = c.getColumnIndexOrThrow(col);
        return c.isNull(index) ? null : c.getLong(index);
    }

    private ContentValues toValues(Task task) {
        ContentValues values = new ContentValues();
        values.put(TaskDbHelper.COL_USER_ID, task.userId);
        values.put(TaskDbHelper.COL_NAME, task.name);
        values.put(TaskDbHelper.COL_TYPE, task.type);
        values.put(TaskDbHelper.COL_FREQUENCY, task.frequency);
        values.put(TaskDbHelper.COL_REPEAT_DAYS, task.repeatDays);
        values.put(TaskDbHelper.COL_DEADLINE, task.deadline);
        values.put(TaskDbHelper.COL_REWARD_EXP, task.rewardExp);
        values.put(TaskDbHelper.COL_REWARD_COINS, task.rewardCoins);
        values.put(TaskDbHelper.COL_COMPLETED, task.completed ? 1 : 0);
        values.put(TaskDbHelper.COL_REMINDER_AT, task.reminderAt);
        values.put(TaskDbHelper.COL_CREATED_AT, task.createdAt);
        values.put(TaskDbHelper.COL_COMPLETED_AT, task.completedAt);
        values.put(TaskDbHelper.COL_SOURCE, task.source);
        values.put(TaskDbHelper.COL_TAG, task.tag);
        return values;
    }
}
