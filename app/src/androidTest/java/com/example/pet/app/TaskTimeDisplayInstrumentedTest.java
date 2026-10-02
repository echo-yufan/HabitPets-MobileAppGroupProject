package com.example.pet.app;

import android.content.Context;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.pet.core.common.concurrent.AppExecutors;
import com.example.pet.core.data.di.AppDependencies;
import com.example.pet.core.data.repository.TaskRepository;
import com.example.pet.core.model.task.Task;
import com.example.pet.feature.planning.ui.MemoActivity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class TaskTimeDisplayInstrumentedTest {
    private TaskRepository repository;
    private String prefix;
    private final List<Long> created = new ArrayList<>();
    @Before public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        repository = AppDependencies.taskRepository(context);
        prefix = "TimeDisplay" + UUID.randomUUID().toString().substring(0, 8);
    }
    @After public void tearDown() throws Exception {
        disk(() -> { for (long id : created) repository.deleteTask(id); return null; });
    }

    @Test public void pageShowsHistoricalDatesAndMissingCompletionWithoutChangingStorage() throws Exception {
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime week = now.minusDays(7);
        Task old = create("Old", week.toInstant().toEpochMilli(), false, null, null);
        Task done = create("Done", now.toInstant().toEpochMilli(), true, now.minusDays(1).toInstant().toEpochMilli(), null);
        Task missing = create("Missing", week.toInstant().toEpochMilli(), true, null, null);
        Task deadline = create("Deadline", week.toInstant().toEpochMilli(), false, null,
                now.plusDays(1).withHour(18).withMinute(0).withSecond(0).withNano(0).toInstant().toEpochMilli());
        try (ActivityScenario<MemoActivity> page = ActivityScenario.launch(MemoActivity.class)) {
            idle();
            String date = week.format(DateTimeFormatter.ofPattern(week.getYear() == now.getYear() ? "M月d日" : "yyyy年M月d日"));
            assertEquals(date + "添加 ·", metadata(page, old.id));
            assertEquals("昨天完成 ·", metadata(page, done.id));
            assertEquals("已完成 ·", metadata(page, missing.id));
            assertEquals("明天 18:00 ·", metadata(page, deadline.id));
            assertEquals(old.createdAt, disk(() -> repository.getTask(old.id)).createdAt);
            assertEquals(done.completedAt, disk(() -> repository.getTask(done.id)).completedAt);
            assertNull(disk(() -> repository.getTask(missing.id)).completedAt);
        }
    }

    @Test public void stayingOnPageUpdatesJustNowAfterTimePassesWithoutDatabaseOperation() throws Exception {
        Task recent = create("Recent", System.currentTimeMillis() - 95000, false, null, null);
        try (ActivityScenario<MemoActivity> page = ActivityScenario.launch(MemoActivity.class)) {
            idle();
            assertEquals("刚刚添加 ·", metadata(page, recent.id));
            long until = SystemClock.elapsedRealtime() + 45000;
            while (SystemClock.elapsedRealtime() < until && !"今天添加 ·".equals(metadata(page, recent.id))) {
                SystemClock.sleep(500);
            }
            assertEquals("今天添加 ·", metadata(page, recent.id));
            assertEquals(recent.createdAt, disk(() -> repository.getTask(recent.id)).createdAt);
        }
    }

    private Task create(String suffix, long at, boolean completed, Long completedAt, Long deadline) throws Exception {
        Task task = new Task(); task.name = prefix + suffix; task.createdAt = at;
        task.completed = completed; task.completedAt = completedAt; task.deadline = deadline;
        disk(() -> { repository.createTask(task); return null; }); created.add(task.id); return task;
    }
    private String metadata(ActivityScenario<MemoActivity> page, long id) {
        String[] result = {null};
        page.onActivity(activity -> {
            ViewGroup container = activity.findViewById(com.example.pet.feature.planning.R.id.content_container);
            for (int i = 0; i < container.getChildCount(); i++) {
                View card = container.getChildAt(i);
                if (card.getTag() instanceof Task && ((Task) card.getTag()).id == id) {
                    result[0] = ((TextView) card.findViewById(com.example.pet.feature.planning.R.id.tv_meta)).getText().toString();
                }
            }
        });
        assertNotNull("Task card not rendered: " + id, result[0]);
        return result[0];
    }
    private void idle() throws Exception { disk(() -> null); InstrumentationRegistry.getInstrumentation().waitForIdleSync(); }
    private <T> T disk(Callable<T> work) throws Exception {
        FutureTask<T> task = new FutureTask<>(work); AppExecutors.disk().execute(task);
        return task.get(15, TimeUnit.SECONDS);
    }
}
