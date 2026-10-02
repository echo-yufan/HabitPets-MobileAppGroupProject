package com.example.pet.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import com.example.pet.core.common.concurrent.AppExecutors;
import com.example.pet.core.data.di.AppDependencies;
import com.example.pet.core.data.local.TaskDbHelper;
import com.example.pet.core.data.repository.TaskNotFoundException;
import com.example.pet.core.data.repository.TaskRepository;
import com.example.pet.core.model.task.Task;
import com.example.pet.feature.planning.ui.MemoActivity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;

/** Real SQLite failures injected with a narrowly scoped trigger, removed in teardown.
 * Existing task data is never cleared; each test cleans up only its unique named rows.
 */
@RunWith(AndroidJUnit4.class)
public class TaskStorageInstrumentedTest {
    private Context context;
    private TaskRepository repo;
    private TaskDbHelper helper;
    private String prefix;
    private String trigger;

    @Before public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        repo = AppDependencies.taskRepository(context);
        helper = new TaskDbHelper(context);
        prefix = "StorageCheck" + UUID.randomUUID().toString().replace("-", "");
        trigger = prefix + "Trigger";
    }

    @After public void tearDown() throws Exception {
        disk(() -> {
            helper.getWritableDatabase().execSQL("DROP TRIGGER IF EXISTS " + trigger);
            for (Task task : repo.getTasks(TaskRepository.FILTER_ALL)) {
                if (task.name != null && task.name.startsWith(prefix)) repo.deleteTask(task.id);
            }
            helper.close();
            return null;
        });
        onMain(() -> {
            List<Activity> activities = new ArrayList<>();
            for (Stage stage : new Stage[]{Stage.CREATED, Stage.STARTED, Stage.RESUMED, Stage.PAUSED, Stage.STOPPED}) {
                activities.addAll(ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(stage));
            }
            for (Activity activity : activities) activity.finish();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    @Test public void thirdInsertFailureRollsBackEntireBatchAndRetryInsertsExactlyOnce() throws Exception {
        List<Task> batch = Arrays.asList(task("One"), task("Two"), task("Three"), task("Four"), task("Five"));
        reject("INSERT", batch.get(2).name, false);
        disk(() -> {
            try { repo.createTasks(batch); fail("Batch must report third-row failure"); }
            catch (android.database.sqlite.SQLiteException expected) { }
            for (Task task : batch) assertEquals("Rolled-back IDs must not escape", 0, task.id);
            assertEquals(0, ownTasks().size());
            return null;
        });
        dropTrigger();
        disk(() -> {
            repo.createTasks(batch);
            assertEquals(5, ownTasks().size());
            for (Task task : batch) assertTrue(task.id > 0);
            return null;
        });
    }

    @Test public void ignoredInsertIsRejectedInsteadOfReturningMinusOneAsSuccess() throws Exception {
        Task task = task("Ignored");
        reject("INSERT", task.name, true);
        disk(() -> {
            try { repo.createTask(task); fail("Ignored insert must be reported as failure"); }
            catch (android.database.sqlite.SQLiteException expected) { }
            assertEquals(0, task.id);
            assertEquals(0, ownTasks().size());
            return null;
        });
    }

    @Test public void constraintFailureDoesNotAssignAnInvalidTaskId() throws Exception {
        Task task = task("Invalid"); task.name = null;
        disk(() -> {
            try { repo.createTask(task); fail("NOT NULL failure must be reported"); }
            catch (android.database.sqlite.SQLiteException expected) { }
            assertEquals(0, task.id);
            return null;
        });
    }

    @Test public void updatingMissingTaskReportsFailure() throws Exception {
        Task task = task("Missing"); task.id = Long.MAX_VALUE;
        disk(() -> {
            try { repo.updateTask(task); fail("Missing update must not report success"); }
            catch (TaskNotFoundException expected) { }
            return null;
        });
    }

    @Test public void completingMissingTaskReportsFailure() throws Exception {
        disk(() -> {
            try { repo.setCompleted(Long.MAX_VALUE, true); fail("Missing completion must not report success"); }
            catch (TaskNotFoundException expected) { }
            return null;
        });
    }

    @Test public void repeatedDeleteIsAnIdempotentSuccess() throws Exception {
        Task task = task("Delete");
        disk(() -> {
            repo.createTask(task);
            repo.deleteTask(task.id);
            repo.deleteTask(task.id);
            assertNull(repo.getTask(task.id));
            return null;
        });
    }

    @Test public void failedAddKeepsInputAndRetrySavesOnlyOneTask() throws Exception {
        String name = prefix + "UiAdd";
        reject("INSERT", name, false);
        openMemo();
        onView(withId(com.example.pet.feature.planning.R.id.btn_add)).perform(click());
        onView(withId(com.example.pet.feature.planning.R.id.et_content)).perform(replaceText(name), closeSoftKeyboard());
        onView(withId(com.example.pet.feature.planning.R.id.btn_direct)).perform(click());
        idle();
        onView(withId(com.example.pet.feature.planning.R.id.et_content)).check(matches(withText(name)));
        onView(withId(com.example.pet.feature.planning.R.id.btn_direct)).check(matches(isEnabled()));
        assertEquals(0, disk(() -> ownTasks().size()).intValue());
        dropTrigger();
        onView(withId(com.example.pet.feature.planning.R.id.btn_direct)).perform(click());
        idle();
        onView(withId(com.example.pet.feature.planning.R.id.et_content)).check(doesNotExist());
        assertEquals(1, disk(() -> ownTasks().size()).intValue());
    }

    @Test public void failedEditKeepsEditedInputAndExistingRecordUntilRetry() throws Exception {
        Task task = task("UiOld");
        disk(() -> { repo.createTask(task); return null; });
        String edited = prefix + "UiNew";
        reject("UPDATE", edited, false);
        openMemo();
        onView(withText(task.name)).perform(longClick());
        onView(withId(com.example.pet.feature.planning.R.id.et_name)).perform(replaceText(edited), closeSoftKeyboard());
        onView(withId(com.example.pet.feature.planning.R.id.btn_save)).perform(click());
        idle();
        onView(withId(com.example.pet.feature.planning.R.id.et_name)).check(matches(withText(edited)));
        onView(withId(com.example.pet.feature.planning.R.id.btn_save)).check(matches(isEnabled()));
        assertEquals(task.name, disk(() -> repo.getTask(task.id).name));
        dropTrigger();
        onView(withId(com.example.pet.feature.planning.R.id.btn_save)).perform(click());
        idle();
        onView(withId(com.example.pet.feature.planning.R.id.et_name)).check(doesNotExist());
        assertEquals(edited, disk(() -> repo.getTask(task.id).name));
        assertEquals(1, disk(() -> ownTasks().size()).intValue());
    }

    private void reject(String operation, String name, boolean ignore) throws Exception {
        disk(() -> {
            SQLiteDatabase db = helper.getWritableDatabase();
            // Names are generated from UUID + fixed alphanumeric suffixes, never user input.
            db.execSQL("CREATE TRIGGER " + trigger + " BEFORE " + operation
                    + " ON tasks WHEN NEW.name = '" + name + "' BEGIN SELECT RAISE("
                    + (ignore ? "IGNORE" : "ABORT, 'injected storage failure'") + "); END");
            return null;
        });
    }

    private void dropTrigger() throws Exception {
        disk(() -> { helper.getWritableDatabase().execSQL("DROP TRIGGER IF EXISTS " + trigger); return null; });
    }

    private Task task(String suffix) { Task task = new Task(); task.name = prefix + suffix; return task; }

    private List<Task> ownTasks() {
        List<Task> result = new ArrayList<>();
        for (Task task : repo.getTasks(TaskRepository.FILTER_ALL)) {
            if (task.name != null && task.name.startsWith(prefix)) result.add(task);
        }
        return result;
    }

    private void openMemo() {
        context.startActivity(new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        awaitResumed(MainActivity.class);
        onMain(() -> {
            for (Activity a : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) {
                if (a instanceof MainActivity) a.findViewById(com.example.pet.R.id.imageButton5).performClick();
            }
        });
        awaitResumed(MemoActivity.class);
    }

    private void awaitResumed(Class<? extends Activity> type) {
        long until = SystemClock.elapsedRealtime() + 10000;
        while (SystemClock.elapsedRealtime() < until) {
            boolean[] found = {false};
            onMain(() -> {
                for (Activity a : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) {
                    if (type.isInstance(a)) found[0] = true;
                }
            });
            if (found[0]) { idleUnchecked(); SystemClock.sleep(1000); return; }
            SystemClock.sleep(100);
        }
        fail("Page not resumed: " + type.getSimpleName());
    }

    private void idleUnchecked() { try { idle(); } catch (Exception error) { throw new AssertionError(error); } }
    private void idle() throws Exception { disk(() -> null); onMain(() -> { }); }
    private void onMain(Runnable action) { InstrumentationRegistry.getInstrumentation().runOnMainSync(action); }
    private <T> T disk(Callable<T> work) throws Exception {
        FutureTask<T> future = new FutureTask<>(work);
        AppExecutors.disk().execute(future);
        return future.get(15, TimeUnit.SECONDS);
    }
}
