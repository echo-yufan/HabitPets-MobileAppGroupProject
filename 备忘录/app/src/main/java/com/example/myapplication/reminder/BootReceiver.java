package com.example.myapplication.reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.example.myapplication.data.Task;
import com.example.myapplication.data.TaskRepository;

import java.util.List;

/** 设备重启后恢复所有未触发的任务提醒 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }
        List<Task> tasks = TaskRepository.getInstance(context).getTasksWithFutureReminders();
        for (Task task : tasks) {
            ReminderScheduler.schedule(context, task);
        }
    }
}
