package com.example.myapplication.reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 闹钟到点后发出通知 */
public class ReminderReceiver extends BroadcastReceiver {

    public static final String ACTION_REMIND = "com.example.myapplication.action.REMIND_";
    public static final String EXTRA_TASK_ID = "taskId";
    public static final String EXTRA_TASK_NAME = "taskName";

    @Override
    public void onReceive(Context context, Intent intent) {
        long taskId = intent.getLongExtra(EXTRA_TASK_ID, -1);
        String name = intent.getStringExtra(EXTRA_TASK_NAME);
        if (taskId < 0 || name == null) {
            return;
        }
        NotificationHelper.showTaskReminder(context, taskId, name);
    }
}
