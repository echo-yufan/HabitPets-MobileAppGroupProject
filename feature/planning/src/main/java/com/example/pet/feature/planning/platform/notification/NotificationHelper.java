package com.example.pet.feature.planning.platform.notification;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.TaskStackBuilder;

import com.example.pet.feature.planning.R;
import com.example.pet.feature.planning.ui.MemoActivity;

/**
 * 任务提醒通知（成员 B：本地 Notification）。
 */
public final class NotificationHelper {

    public static final String CHANNEL_ID = "task_reminders";
    private static final String CHANNEL_NAME = "任务提醒";
    private static final String CHANNEL_DESC = "任务截止与计划提醒通知";

    private NotificationHelper() {
    }

    public static void ensureChannel(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(CHANNEL_DESC);
        manager.createNotificationChannel(channel);
    }

    public static boolean isGranted(Context context) {
        if (Build.VERSION.SDK_INT < 33) {
            return true;
        }
        return context.checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    public static void showTaskReminder(Context context, long taskId, String taskName) {
        if (!isGranted(context)) return;
        ensureChannel(context);
        Intent open = new Intent(context, MemoActivity.class);
        PendingIntent contentIntent = TaskStackBuilder.create(context)
                .addNextIntentWithParentStack(open)
                .getPendingIntent((int) taskId,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_bell)
                .setContentTitle(taskName)
                .setContentText("到时间啦，别忘了完成它")
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setColor(0xFF2E9E63);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify((int) taskId, builder.build());
        }
    }

    public static void cancelTaskReminder(Context context, long taskId) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.cancel((int) taskId);
    }
}
