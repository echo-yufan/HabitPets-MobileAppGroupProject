package com.example.pet.app;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.pet.core.common.concurrent.AppExecutors;
import com.example.pet.core.data.di.AppDependencies;
import com.example.pet.core.data.repository.TaskRepository;
import com.example.pet.core.model.task.Task;
import com.example.pet.feature.planning.platform.notification.NotificationHelper;
import com.example.pet.feature.planning.platform.reminder.ReminderReceiver;
import com.example.pet.feature.planning.platform.reminder.ReminderScheduler;
import com.example.pet.feature.planning.presentation.MemoViewModel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** Real SQLite, AlarmManager, manifest receiver and system notifications on API 33.
 * Run only on a test device with an empty task list: bulk-clear tests reject existing data.
 */
@RunWith(AndroidJUnit4.class)
public class TaskReminderInstrumentedTest {
    private Context context;
    private TaskRepository repo;
    private MemoViewModel model;
    private final List<Long> created = new ArrayList<>();

    @Before public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        repo = AppDependencies.taskRepository(context);
        assertTrue("Use an empty test device; refusing to clear existing tasks",
                disk(() -> repo.getTasks(TaskRepository.FILTER_ALL).isEmpty()));
        assertTrue("Notification permission must be enabled for this test", NotificationHelper.isGranted(context));
        NotificationHelper.ensureChannel(context);
        Handler main = new Handler(Looper.getMainLooper());
        onMain(() -> model = new MemoViewModel(repo, AppExecutors.disk(), r -> main.post(r),
                () -> { }, new MemoViewModel.Reminders() {
                    @Override public void schedule(Task task) { ReminderScheduler.schedule(context, task); }
                    @Override public void cancel(long id) { ReminderScheduler.cancel(context, id); }
                }));
        idle();
    }

    @After public void tearDown() throws Exception {
        if (repo == null) return;
        disk(() -> {
            for (long id : created) { repo.deleteTask(id); ReminderScheduler.cancel(context, id); }
            return null;
        });
    }

    @Test public void realAlarmDisplaysNotificationWhileAppIsInBackground() throws Exception {
        Task task = create("ReminderLiveCheck");
        long due = System.currentTimeMillis() + 5000;
        edit(task, due);
        assertAlarmPresent(task.id);
        shell("input keyevent HOME");
        long timeout = SystemClock.elapsedRealtime() + 120000;
        StatusBarNotification notification;
        while ((notification = notification(task.id)) == null && SystemClock.elapsedRealtime() < timeout) {
            SystemClock.sleep(500);
        }
        assertNotNull("Real AlarmManager did not deliver a notification within 120 seconds", notification);
        assertEquals(task.name, notification.getNotification().extras.getString(Notification.EXTRA_TITLE));
        Log.i("HabitPetsReminderTest", "REAL_NOTIFICATION id=" + task.id
                + " delayMs=" + (System.currentTimeMillis() - due));
        onMain(() -> assertTrue(model.deleteTask(task.id)));
        idle();
        assertNull("Deleting task must dismiss delivered notification", notification(task.id));
    }

    @Test public void singleDeleteRemovesSystemAlarmAndRejectsAlreadyQueuedBroadcast() throws Exception {
        Task task = create("ReminderDeleteCheck");
        edit(task, System.currentTimeMillis() + 600000);
        assertAlarmPresent(task.id);
        onMain(() -> assertTrue(model.deleteTask(task.id)));
        idle();
        assertAlarmAbsent(task.id);
        staleBroadcast(task.id, task.reminderAt);
        assertNull(notification(task.id));
        Log.i("HabitPetsReminderTest", "DELETE_CANCELLED id=" + task.id);
    }

    @Test public void clearAllRemovesEverySystemAlarmAndRejectsStaleBroadcasts() throws Exception {
        Task one = create("ReminderClearA");
        Task two = create("ReminderClearB");
        edit(one, System.currentTimeMillis() + 600000);
        edit(two, System.currentTimeMillis() + 660000);
        assertAlarmPresent(one.id); assertAlarmPresent(two.id);
        onMain(() -> assertTrue(model.clearTasks(false)));
        idle();
        assertAlarmAbsent(one.id); assertAlarmAbsent(two.id);
        staleBroadcast(one.id, one.reminderAt); staleBroadcast(two.id, two.reminderAt);
        assertNull(notification(one.id)); assertNull(notification(two.id));
        Log.i("HabitPetsReminderTest", "CLEAR_ALL_CANCELLED ids=" + one.id + "," + two.id);
    }

    @Test public void clearCompletedRemovesResidualAlarmAndKeepsActiveAlarm() throws Exception {
        Task done = create("ReminderDoneCheck");
        Task active = create("ReminderActiveCheck");
        edit(done, System.currentTimeMillis() + 600000);
        edit(active, System.currentTimeMillis() + 660000);
        // Simulate a residual alarm from older code: mark completed without cancelling it.
        disk(() -> { repo.setCompleted(done.id, true); return null; });
        assertAlarmPresent(done.id); assertAlarmPresent(active.id);
        onMain(() -> assertTrue(model.clearTasks(true)));
        idle();
        assertAlarmAbsent(done.id); assertAlarmPresent(active.id);
        assertNotNull(disk(() -> repo.getTask(active.id)));
        staleBroadcast(done.id, done.reminderAt);
        assertNull(notification(done.id));
        Log.i("HabitPetsReminderTest", "CLEAR_DONE_CANCELLED id=" + done.id + " retained=" + active.id);
    }

    private Task create(String name) throws Exception {
        Task task = new Task(); task.name = name;
        disk(() -> { repo.createTask(task); return null; });
        created.add(task.id); return task;
    }

    private void edit(Task task, long due) throws Exception {
        onMain(() -> assertTrue(model.editTask(task.id, task.name, null, due)));
        idle(); task.reminderAt = due;
    }

    private PendingIntent pending(long id) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(ReminderReceiver.ACTION_REMIND + id);
        return PendingIntent.getBroadcast(context, (int) id, intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
    }

    private void assertAlarmPresent(long id) throws Exception {
        assertNotNull("Reminder PendingIntent must exist", pending(id));
        assertTrue("AlarmManager must contain task alarm", scheduledAlarms().contains(ReminderReceiver.ACTION_REMIND + id));
    }

    private void assertAlarmAbsent(long id) throws Exception {
        assertNull("Reminder PendingIntent must be cancelled", pending(id));
        assertFalse("AlarmManager must no longer contain task alarm", scheduledAlarms().contains(ReminderReceiver.ACTION_REMIND + id));
    }

    private String scheduledAlarms() throws Exception {
        String dump = shell("dumpsys alarm");
        // Exclude statistics/history that retain names of alarms that have already fired.
        int end = dump.indexOf("Past-due non-wakeup alarms");
        if (end < 0) end = dump.indexOf("  Next wake from idle");
        if (end < 0) end = dump.indexOf("  Pending user blocked background alarms");
        return end >= 0 ? dump.substring(0, end) : dump;
    }

    private void staleBroadcast(long id, long due) throws Exception {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(ReminderReceiver.ACTION_REMIND + id);
        intent.putExtra(ReminderReceiver.EXTRA_TASK_ID, id);
        intent.putExtra(ReminderReceiver.EXTRA_TASK_NAME, "stale name");
        intent.putExtra(ReminderReceiver.EXTRA_REMINDER_AT, due);
        context.sendBroadcast(intent);
        // Receiver dispatch can follow the initial disk barrier; allow it to enter the queue.
        SystemClock.sleep(1000);
        idle();
    }

    private StatusBarNotification notification(long id) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        for (StatusBarNotification n : manager.getActiveNotifications()) if (n.getId() == (int) id) return n;
        return null;
    }

    private <T> T disk(Callable<T> work) throws Exception {
        FutureTask<T> future = new FutureTask<>(work);
        AppExecutors.disk().execute(future);
        return future.get(15, TimeUnit.SECONDS);
    }

    private void idle() throws Exception {
        disk(() -> null);
        onMain(() -> { });
        assertFalse("Task command failed to finish", model.state().getValue().busy);
    }

    private void onMain(Runnable action) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action);
    }

    private String shell(String command) throws Exception {
        try (ParcelFileDescriptor fd = InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .executeShellCommand(command); FileInputStream input = new FileInputStream(fd.getFileDescriptor());
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toString("UTF-8");
        }
    }
}
