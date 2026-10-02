package com.example.pet.feature.planning.platform.reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.pet.core.common.concurrent.AppExecutors;
import com.example.pet.core.data.di.AppDependencies;
import com.example.pet.core.model.task.Task;

import java.util.List;

/** 设备重启后恢复所有未触发的任务提醒 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }
        Context application = context.getApplicationContext();
        PendingResult pending = goAsync();
        try {
            AppExecutors.disk().execute(() -> {
                try {
                    List<Task> tasks = AppDependencies.taskRepository(application).getTasksWithFutureReminders();
                    for (Task task : tasks) ReminderScheduler.schedule(application, task);
                } catch (RuntimeException error) {
                    Log.e("BootReceiver", "Unable to restore task reminders", error);
                } finally {
                    pending.finish();
                }
            });
        } catch (RuntimeException error) {
            pending.finish();
            Log.e("BootReceiver", "Unable to queue reminder restore", error);
        }
    }
}
