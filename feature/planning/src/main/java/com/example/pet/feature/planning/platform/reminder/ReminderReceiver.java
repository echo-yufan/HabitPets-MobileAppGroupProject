package com.example.pet.feature.planning.platform.reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.pet.core.common.concurrent.AppExecutors;
import com.example.pet.core.data.di.AppDependencies;
import com.example.pet.feature.planning.domain.usecase.DeliverTaskReminder;

import com.example.pet.feature.planning.platform.notification.NotificationHelper;

/** 闹钟到点后发出通知 */
public class ReminderReceiver extends BroadcastReceiver {

    public static final String ACTION_REMIND = "com.example.pet.action.REMIND_";
    public static final String EXTRA_TASK_ID = "taskId";
    public static final String EXTRA_TASK_NAME = "taskName";
    public static final String EXTRA_REMINDER_AT = "reminderAt";

    @Override
    public void onReceive(Context context, Intent intent) {
        long taskId = intent.getLongExtra(EXTRA_TASK_ID, -1);
        long expectedAt = intent.getLongExtra(EXTRA_REMINDER_AT, -1);
        if (taskId < 0) {
            return;
        }
        Context application = context.getApplicationContext();
        PendingResult pending = goAsync();
        try {
            AppExecutors.disk().execute(() -> {
                try {
                    new DeliverTaskReminder(AppDependencies.taskRepository(application),
                            System::currentTimeMillis,
                            task -> NotificationHelper.showTaskReminder(application, task.id, task.name))
                            .execute(taskId, expectedAt);
                } catch (RuntimeException error) {
                    Log.e("ReminderReceiver", "Unable to deliver task reminder", error);
                } finally {
                    pending.finish();
                }
            });
        } catch (RuntimeException error) {
            pending.finish();
            Log.e("ReminderReceiver", "Unable to queue task reminder", error);
        }
    }
}
