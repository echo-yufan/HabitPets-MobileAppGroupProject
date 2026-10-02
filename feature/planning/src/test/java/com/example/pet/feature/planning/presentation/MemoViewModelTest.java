package com.example.pet.feature.planning.presentation;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.example.pet.core.data.repository.TaskRepository;
import com.example.pet.core.model.task.Task;
import org.junit.Rule;
import org.junit.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;
import static org.junit.Assert.*;

public class MemoViewModelTest {
    @Rule public InstantTaskExecutorRule liveData = new InstantTaskExecutorRule();
    private final ManualExecutor disk = new ManualExecutor();
    private final ManualExecutor main = new ManualExecutor();
    private final FakeRepository repo = new FakeRepository();
    private final List<String> reminders = new ArrayList<>();

    private MemoViewModel model() {
        return new MemoViewModel(repo, disk, main, () -> repo.calls.add("seed"),
                new MemoViewModel.Reminders() {
                    @Override public void schedule(Task task) { reminders.add("schedule:" + task.id); }
                    @Override public void cancel(long id) { reminders.add("cancel:" + id); }
                }, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    }

    private void drain() { disk.next(); main.next(); }

    @Test public void initializationWaitsForDiskAndPublishesOnlyOnMain() {
        MemoViewModel vm = model();
        assertTrue(repo.calls.isEmpty());
        assertTrue(vm.state().getValue().busy);
        disk.next();
        assertEquals(Arrays.asList("seed", "read:0", "read:1"), repo.calls);
        assertTrue(vm.state().getValue().busy);
        main.next();
        assertFalse(vm.state().getValue().busy);
    }

    @Test public void writeCompletesBeforeRefreshAndDuplicateSubmissionIsRejected() {
        MemoViewModel vm = model();
        drain();
        repo.calls.clear();
        Task task = task("one");
        assertTrue(vm.createTasks(Collections.singletonList(task), "added"));
        assertFalse(vm.createTasks(Collections.singletonList(task), "added"));
        assertTrue(repo.calls.isEmpty());
        drain();
        assertEquals(Arrays.asList("create", "read:0", "read:1"), repo.calls);
        assertEquals(1, vm.state().getValue().tasks.size());
        assertEquals(1, vm.state().getValue().activeCount);
        assertEquals("added", vm.events().getValue().consume());
        assertNull(vm.events().getValue().consume());
    }

    @Test public void databaseFailureAllowsRetryAndDoesNotSendSuccess() {
        MemoViewModel vm = model();
        drain();
        repo.failWrite = true;
        vm.createTasks(Collections.singletonList(task("one")), "added");
        drain();
        assertFalse(vm.state().getValue().busy);
        assertEquals("任务操作失败，请重试", vm.events().getValue().consume());
        repo.failWrite = false;
        assertTrue(vm.createTasks(Collections.singletonList(task("retry")), null));
        drain();
        assertEquals(1, vm.state().getValue().tasks.size());
    }

    @Test public void rejectedQueueDoesNotAcceptCommandOrLeaveScreenBusy() {
        MemoViewModel vm = model();
        drain();
        disk.reject = true;
        assertFalse(vm.createTasks(Collections.singletonList(task("unsaved")), "added"));
        assertFalse(vm.state().getValue().busy);
        assertTrue(repo.tasks.isEmpty());
        assertNotNull(vm.events().getValue().consume());
        disk.reject = false;
        assertTrue(vm.createTasks(Collections.singletonList(task("retry")), null));
        drain();
        assertEquals(1, repo.tasks.size());
    }

    @Test public void destroyedModelDoesNotPublishQueuedResult() {
        MemoViewModel vm = model();
        drain();
        MemoEvent before = vm.events().getValue();
        vm.createTasks(Collections.singletonList(task("saved")), "added");
        disk.next();
        vm.onCleared();
        main.next();
        assertSame(before, vm.events().getValue());
        assertTrue(vm.state().getValue().tasks.isEmpty());
        assertEquals(1, repo.tasks.size());
    }

    @Test public void filteringKeepsCountOfAllActiveTasks() {
        Task active = task("active");
        Task done = task("done");
        done.completed = true;
        repo.tasks.add(active);
        repo.tasks.add(done);
        MemoViewModel vm = model();
        drain();
        vm.selectFilter(TaskRepository.FILTER_DONE);
        drain();
        assertEquals(TaskRepository.FILTER_DONE, vm.state().getValue().filter);
        assertEquals(1, vm.state().getValue().tasks.size());
        assertEquals("done", vm.state().getValue().tasks.get(0).name);
        assertEquals(1, vm.state().getValue().activeCount);
    }

    @Test public void completionAndUndoKeepExistingReminderBehavior() {
        Task task = task("reminder");
        task.id = 8;
        task.reminderAt = 100L;
        repo.tasks.add(task);
        MemoViewModel vm = model();
        drain();
        vm.setCompleted(8, true);
        drain();
        vm.setCompleted(8, false);
        drain();
        assertEquals(Arrays.asList("cancel:8", "schedule:8"), reminders);
        assertEquals(1, vm.state().getValue().activeCount);
    }

    @Test public void editingUsesRepositoryObjectAndDeleteCancelsReminder() {
        Task task = task("old");
        task.id = 9;
        repo.tasks.add(task);
        MemoViewModel vm = model();
        drain();
        vm.editTask(9, "new", 20L, 30L);
        assertEquals("old", task.name);
        drain();
        assertEquals("new", vm.state().getValue().tasks.get(0).name);
        assertEquals(Long.valueOf(20), task.deadline);
        vm.deleteTask(9);
        drain();
        assertTrue(vm.state().getValue().tasks.isEmpty());
        assertEquals(Arrays.asList("schedule:9", "cancel:9"), reminders);
    }

    @Test public void clearCompletedRetainsActiveTasks() {
        Task done = task("done");
        done.id = 10;
        done.completed = true;
        repo.tasks.add(done);
        Task active = task("active");
        active.id = 11;
        repo.tasks.add(active);
        MemoViewModel vm = model();
        drain();
        vm.clearTasks(true);
        drain();
        assertEquals(1, vm.state().getValue().tasks.size());
        assertEquals(Collections.singletonList("cancel:10"), reminders);
        vm.clearTasks(false);
        drain();
        assertTrue(vm.state().getValue().tasks.isEmpty());
        assertEquals(Arrays.asList("cancel:10", "cancel:11"), reminders);
    }

    @Test public void savedCommandIsSuccessfulEvenIfRefreshingListFails() {
        MemoViewModel vm = model();
        drain();
        repo.failRead = true;
        vm.createTasks(Collections.singletonList(task("saved")), "added");
        long operation = vm.operationId();
        drain();
        MemoEvent result = vm.events().getValue().consumeResult();
        assertTrue(result.successful);
        assertEquals(operation, result.operationId);
        assertEquals("操作已完成，但列表刷新失败，请切换筛选刷新", result.message());
        assertEquals(1, repo.tasks.size());
        assertFalse(vm.state().getValue().busy);
        repo.failRead = false;
        vm.selectFilter(TaskRepository.FILTER_ALL);
        drain();
        assertEquals(1, vm.state().getValue().tasks.size());
    }

    @Test public void writeFailureProducesAnUnsuccessfulMatchingResult() {
        MemoViewModel vm = model();
        drain();
        repo.failWrite = true;
        vm.createTasks(Collections.singletonList(task("unsaved")), "added");
        long operation = vm.operationId();
        drain();
        MemoEvent result = vm.events().getValue().consumeResult();
        assertFalse(result.successful);
        assertEquals(operation, result.operationId);
        assertEquals("任务操作失败，请重试", result.message());
        assertTrue(repo.tasks.isEmpty());
    }

    private static Task task(String name) { Task t = new Task(); t.name = name; return t; }

    @Test public void pastReminderRejectsEditBeforeMutatingTaskAndKeepsExistingAlarm() {
        Task task = task("old"); task.id = 99; task.reminderAt = 5000L;
        repo.tasks.add(task);
        MemoViewModel vm = model(); drain();
        vm.editTask(99, "new", 1000L, -1L); drain();
        assertEquals("old", task.name);
        assertEquals(Long.valueOf(5000), task.reminderAt);
        assertTrue(reminders.isEmpty());
        assertFalse(vm.events().getValue().successful);
        assertEquals("请选择未来的提醒时间", vm.events().getValue().message());
    }

    @Test public void expiredWhileWaitingForDiskRejectsEditAndCanRetry() {
        Task task = task("old"); task.id = 99; repo.tasks.add(task);
        final long[] now = {0};
        Clock mutable = new Clock() {
            @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return Instant.ofEpochMilli(now[0]); }
        };
        MemoViewModel vm = new MemoViewModel(repo, disk, main, () -> {},
                new MemoViewModel.Reminders() {
                    @Override public void schedule(Task task) { reminders.add("schedule"); }
                    @Override public void cancel(long id) { reminders.add("cancel"); }
                }, mutable);
        drain();
        vm.editTask(99, "new", null, 1000L);
        now[0] = 1000;
        drain();
        assertEquals("old", task.name);
        assertFalse(vm.state().getValue().busy);
        assertTrue(reminders.isEmpty());
        assertFalse(vm.events().getValue().successful);
        assertTrue(vm.editTask(99, "retry", null, 2000L)); drain();
        assertEquals("retry", task.name);
        assertEquals(Arrays.asList("schedule"), reminders);
    }

    @Test public void batchWithPastReminderIsRejectedBeforeAnyInsert() {
        MemoViewModel vm = model(); drain();
        Task bad = task("bad"); bad.reminderAt = -100L;
        vm.createTasks(Arrays.asList(task("good"), bad), null); drain();
        assertTrue(repo.tasks.isEmpty());
        assertFalse(vm.events().getValue().successful);
        assertEquals("请选择未来的提醒时间", vm.events().getValue().message());
    }

    private static final class ManualExecutor implements Executor {
        final Queue<Runnable> queue = new ArrayDeque<>();
        boolean reject;
        @Override public void execute(Runnable command) {
            if (reject) throw new java.util.concurrent.RejectedExecutionException("Test rejection");
            queue.add(command);
        }
        void next() { assertFalse("No queued work", queue.isEmpty()); queue.remove().run(); }
    }

    private static final class FakeRepository implements TaskRepository {
        final List<Task> tasks = new ArrayList<>();
        final List<String> calls = new ArrayList<>();
        boolean failWrite;
        boolean failRead;
        @Override public List<Task> getTasks(int filter) {
            if (failRead) throw new IllegalStateException("Test read failure");
            calls.add("read:" + filter);
            List<Task> result = new ArrayList<>();
            for (Task t : tasks) {
                if (filter == FILTER_ALL || (filter == FILTER_ACTIVE && !t.completed)
                        || (filter == FILTER_DONE && t.completed)) result.add(t);
            }
            return result;
        }
        @Override public long createTask(Task task) {
            if (failWrite) throw new IllegalStateException("Test failure");
            calls.add("create"); task.id = tasks.size() + 1; tasks.add(task); return task.id;
        }
        @Override public void createTasks(List<Task> batch) {
            if (failWrite) throw new IllegalStateException("Test failure");
            for (Task task : batch) createTask(task);
        }
        @Override public Task getTask(long id) {
            for (Task t : tasks) if (t.id == id) return t;
            return null;
        }
        @Override public void updateTask(Task task) { }
        @Override public void deleteTask(long id) { tasks.removeIf(t -> t.id == id); }
        @Override public void setCompleted(long id, boolean completed) { getTask(id).completed = completed; }
        @Override public int clearAll() { int n = tasks.size(); tasks.clear(); return n; }
        @Override public int clearCompleted() {
            int n = tasks.size(); tasks.removeIf(t -> t.completed); return n - tasks.size();
        }
        @Override public List<Task> getTodayTasks(long date) { throw new UnsupportedOperationException(); }
        @Override public List<Task> getUpcomingTasks(long start, long end) { throw new UnsupportedOperationException(); }
        @Override public List<Task> getTasksWithFutureReminders() { throw new UnsupportedOperationException(); }
    }
}
