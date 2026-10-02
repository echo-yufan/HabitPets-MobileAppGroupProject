package com.example.pet.app;

import androidx.lifecycle.ViewModelProvider;
import com.example.pet.feature.planning.presentation.MemoViewModel;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.ParcelFileDescriptor;
import android.view.View;
import android.widget.DatePicker;
import android.widget.TimePicker;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.pet.core.common.concurrent.AppExecutors;
import com.example.pet.core.data.di.AppDependencies;
import com.example.pet.core.data.repository.TaskRepository;
import com.example.pet.core.model.task.Task;
import com.example.pet.feature.planning.platform.reminder.ReminderReceiver;
import com.example.pet.feature.planning.platform.reminder.ReminderScheduler;
import com.example.pet.feature.planning.ui.MemoActivity;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

/** Real date/time dialogs, persistence, validation and system alarm registration. */
@RunWith(AndroidJUnit4.class)
public class ReminderTimeInstrumentedTest {
    private Context context;
    private TaskRepository repository;
    private final List<Long> created = new ArrayList<>();
    private String prefix;
    @Before public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        repository = AppDependencies.taskRepository(context);
        prefix = "ReminderDateCheck" + UUID.randomUUID().toString().substring(0, 8);
    }
    @After public void tearDown() throws Exception {
        disk(() -> {
            for (long id : created) { ReminderScheduler.cancel(context, id); repository.deleteTask(id); }
            return null;
        });
    }

    @Test public void reminderDateIsIndependentOfPastDeadlineAndDeleteCancelsAlarm() throws Exception {
        Task task = create(false);
        ZonedDateTime selected = ZonedDateTime.now().plusDays(2).withHour(18).withMinute(0).withSecond(0).withNano(0);
        try (ActivityScenario<MemoActivity> page = ActivityScenario.launch(MemoActivity.class)) {
            idle(); edit(task);
            choose(selected);
            onView(withId(com.example.pet.feature.planning.R.id.btn_save)).perform(click());
            idle();
            onView(withId(com.example.pet.feature.planning.R.id.et_name)).check(doesNotExist());
            Task saved = disk(() -> repository.getTask(task.id));
            assertEquals(task.deadline, saved.deadline);
            assertEquals(Long.valueOf(selected.toInstant().toEpochMilli()), saved.reminderAt);
            assertNotNull(pending(task.id));
            assertTrue(alarms().contains(ReminderReceiver.ACTION_REMIND + task.id));
            page.onActivity(activity -> new ViewModelProvider(activity).get(MemoViewModel.class).deleteTask(task.id));
            idle();
            assertNull(disk(() -> repository.getTask(task.id)));
        }
        assertNull(pending(task.id));
        assertFalse(alarms().contains(ReminderReceiver.ACTION_REMIND + task.id));
    }

    @Test public void pastSelectionKeepsPreviousReminderAndEditedName() throws Exception {
        Task task = create(false);
        try (ActivityScenario<MemoActivity> page = ActivityScenario.launch(MemoActivity.class)) {
            idle(); edit(task);
            onView(withId(com.example.pet.feature.planning.R.id.et_name)).perform(replaceText("kept input"), closeSoftKeyboard());
            choose(ZonedDateTime.now().minusDays(3));
            onView(withId(com.example.pet.feature.planning.R.id.tv_reminder_info))
                    .check(matches(withText("请选择未来的提醒时间")));
            onView(withId(com.example.pet.feature.planning.R.id.et_name)).check(matches(withText("kept input")));
            onView(withId(com.example.pet.feature.planning.R.id.btn_save)).check(matches(isEnabled()));
            assertEquals(task.reminderAt, disk(() -> repository.getTask(task.id).reminderAt));
        }
    }

    @Test public void legacyPastReminderCannotBeSavedUntilCorrected() throws Exception {
        Task task = create(true);
        try (ActivityScenario<MemoActivity> page = ActivityScenario.launch(MemoActivity.class)) {
            idle(); edit(task);
            onView(withId(com.example.pet.feature.planning.R.id.tv_reminder_info))
                    .check(matches(withText(containsString("提醒时间已过去"))));
            onView(withId(com.example.pet.feature.planning.R.id.et_name)).perform(replaceText("corrected name"), closeSoftKeyboard());
            onView(withId(com.example.pet.feature.planning.R.id.btn_save)).perform(click());
            idle();
            onView(withId(com.example.pet.feature.planning.R.id.et_name)).check(matches(withText("corrected name")));
            onView(withId(com.example.pet.feature.planning.R.id.btn_save)).check(matches(isEnabled()));
            assertEquals(task.name, disk(() -> repository.getTask(task.id).name));
            assertNull(pending(task.id));
            ZonedDateTime chosen = ZonedDateTime.now().plusDays(1).withSecond(0).withNano(0);
            choose(chosen);
            onView(withId(com.example.pet.feature.planning.R.id.btn_save)).perform(click()); idle();
            assertEquals("corrected name", disk(() -> repository.getTask(task.id).name));
            assertEquals(Long.valueOf(chosen.toInstant().toEpochMilli()), disk(() -> repository.getTask(task.id).reminderAt));
            assertNotNull(pending(task.id));
        }
    }

    @Test public void cancellingTimeDialogKeepsOriginalReminder() throws Exception {
        Task task = create(false);
        try (ActivityScenario<MemoActivity> page = ActivityScenario.launch(MemoActivity.class)) {
            idle(); edit(task);
            onView(withId(com.example.pet.feature.planning.R.id.btn_reminder_time)).perform(click());
            setDate(ZonedDateTime.now().plusDays(4));
            onView(withId(android.R.id.button1)).perform(click());
            onView(withId(android.R.id.button2)).perform(click());
            onView(withId(com.example.pet.feature.planning.R.id.btn_save)).perform(click()); idle();
            assertEquals(task.reminderAt, disk(() -> repository.getTask(task.id).reminderAt));
        }
    }

    @Test public void clearingDeadlineLeavesReminderUnchanged() throws Exception {
        Task task = create(false);
        try (ActivityScenario<MemoActivity> page = ActivityScenario.launch(MemoActivity.class)) {
            idle(); edit(task);
            onView(withId(com.example.pet.feature.planning.R.id.btn_deadline_clear)).perform(click());
            onView(withId(com.example.pet.feature.planning.R.id.btn_save)).perform(click()); idle();
            Task saved = disk(() -> repository.getTask(task.id));
            assertNull(saved.deadline);
            assertEquals(task.reminderAt, saved.reminderAt);
            assertNotNull(pending(task.id));
        }
    }

    @Test public void turningOffExpiredReminderAllowsSaveAndCancelsAlarm() throws Exception {
        Task task = create(true);
        try (ActivityScenario<MemoActivity> page = ActivityScenario.launch(MemoActivity.class)) {
            idle(); edit(task);
            onView(withId(com.example.pet.feature.planning.R.id.sw_reminder)).perform(click());
            onView(withId(com.example.pet.feature.planning.R.id.btn_save)).perform(click()); idle();
            assertNull(disk(() -> repository.getTask(task.id).reminderAt));
            assertNull(pending(task.id));
        }
    }

    private Task create(boolean expired) throws Exception {
        Task task = new Task(); task.name = prefix;
        task.deadline = ZonedDateTime.now().minusDays(3).toInstant().toEpochMilli();
        task.reminderAt = ZonedDateTime.now().plusDays(expired ? -2 : 1).withSecond(0).withNano(0).toInstant().toEpochMilli();
        disk(() -> { repository.createTask(task); return null; }); created.add(task.id); return task;
    }
    private void edit(Task task) { onView(withText(task.name)).perform(longClick()); }
    private void choose(ZonedDateTime selected) {
        onView(withId(com.example.pet.feature.planning.R.id.btn_reminder_time)).perform(click());
        setDate(selected);
        onView(withId(android.R.id.button1)).perform(click());
        onView(isAssignableFrom(TimePicker.class)).perform(new ViewAction() {
            @Override public Matcher<View> getConstraints() { return isAssignableFrom(TimePicker.class); }
            @Override public String getDescription() { return "select reminder hour and minute"; }
            @Override public void perform(UiController ui, View view) {
                ((TimePicker) view).setHour(selected.getHour()); ((TimePicker) view).setMinute(selected.getMinute());
            }
        });
        onView(withId(android.R.id.button1)).perform(click());
    }
    private void setDate(ZonedDateTime selected) {
        onView(isAssignableFrom(DatePicker.class)).perform(new ViewAction() {
            @Override public Matcher<View> getConstraints() { return isAssignableFrom(DatePicker.class); }
            @Override public String getDescription() { return "select independent reminder date"; }
            @Override public void perform(UiController ui, View view) {
                ((DatePicker) view).updateDate(selected.getYear(), selected.getMonthValue()-1, selected.getDayOfMonth());
            }
        });
    }
    private PendingIntent pending(long id) {
        return PendingIntent.getBroadcast(context, (int) id,
                new Intent(context, ReminderReceiver.class).setAction(ReminderReceiver.ACTION_REMIND + id),
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
    }
    private String alarms() throws Exception {
        try (ParcelFileDescriptor fd = InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand("dumpsys alarm");
             FileInputStream input = new FileInputStream(fd.getFileDescriptor()); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int length;
            while ((length = input.read(buffer)) != -1) output.write(buffer, 0, length);
            String dump = output.toString("UTF-8");
            int end = dump.indexOf("Past-due non-wakeup alarms");
            return end >= 0 ? dump.substring(0, end) : dump;
        }
    }
    private void idle() throws Exception { disk(() -> null); InstrumentationRegistry.getInstrumentation().waitForIdleSync(); }
    private <T> T disk(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action); AppExecutors.disk().execute(task);
        return task.get(15, TimeUnit.SECONDS);
    }
}
