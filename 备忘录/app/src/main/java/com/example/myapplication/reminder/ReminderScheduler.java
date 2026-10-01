package com.example.myapplication.reminder;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.example.myapplication.data.Task;

/**
 * 提醒调度入口（分工文档 5.3 输出接口）：
 * ReminderScheduler.schedule(task) / cancel(context, taskId)
 * 使用 AlarmManager 免安装精确度较宽松的窗口闹钟，兼容 targetSdk 33。
 */
public final class ReminderScheduler {

    private ReminderScheduler() {
    }

    public static void schedule(Context context, Task task) {
        cancel(context, task.id);
        if (task.reminderAt == null) {
            return;
        }
        long triggerAt = task.reminderAt;
        if (triggerAt <= System.currentTimeMillis()) {
            return; // 过去的时间不再调度
        }
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pending = buildPendingIntent(context, task.id, task.name);
        if (manager == null) {
            return;
        }
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending);
    }

    public static void cancel(Context context, long taskId) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pending = buildPendingIntent(context, taskId, "");
        if (manager != null) {
            manager.cancel(pending);
            pending.cancel();
        }
    }

    private static PendingIntent buildPendingIntent(Context context, long taskId, String taskName) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(ReminderReceiver.ACTION_REMIND + taskId);
        intent.putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId);
        intent.putExtra(ReminderReceiver.EXTRA_TASK_NAME, taskName);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(context, (int) taskId, intent, flags);
    }
}
