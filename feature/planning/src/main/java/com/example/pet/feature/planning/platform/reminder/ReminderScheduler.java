package com.example.pet.feature.planning.platform.reminder;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.example.pet.core.model.task.Task;
import com.example.pet.feature.planning.platform.notification.NotificationHelper;

/**
 * 提醒调度入口（分工文档 5.3 输出接口）：
 * ReminderScheduler.schedule(task) / cancel(context, taskId)
 * 使用 AlarmManager 安排一次性非精确提醒。
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
        PendingIntent pending = buildPendingIntent(context, task.id, task.name, triggerAt);
        if (manager == null) {
            return;
        }
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending);
    }

    public static void cancel(Context context, long taskId) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pending = findPendingIntent(context, taskId);
        if (manager != null && pending != null) {
            manager.cancel(pending);
        }
        if (pending != null) pending.cancel();
        NotificationHelper.cancelTaskReminder(context, taskId);
    }

    private static Intent reminderIntent(Context context, long taskId) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(ReminderReceiver.ACTION_REMIND + taskId);
        return intent;
    }

    private static PendingIntent findPendingIntent(Context context, long taskId) {
        int flags = PendingIntent.FLAG_NO_CREATE;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(context, (int) taskId, reminderIntent(context, taskId), flags);
    }

    private static PendingIntent buildPendingIntent(Context context, long taskId, String taskName, long reminderAt) {
        Intent intent = reminderIntent(context, taskId);
        intent.putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId);
        intent.putExtra(ReminderReceiver.EXTRA_TASK_NAME, taskName);
        intent.putExtra(ReminderReceiver.EXTRA_REMINDER_AT, reminderAt);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(context, (int) taskId, intent, flags);
    }
}
