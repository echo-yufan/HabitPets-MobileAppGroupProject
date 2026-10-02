package com.example.pet.feature.planning.presentation;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.pet.core.data.repository.TaskRepository;
import com.example.pet.core.model.task.Task;
import com.example.pet.feature.planning.domain.usecase.ReminderTimePolicy;
import java.time.Clock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

/** All commands enter on main, run on the shared disk queue, then publish on main. */
public final class MemoViewModel extends ViewModel {
    public interface Reminders {
        void schedule(Task task);
        void cancel(long id);
    }

    private final TaskRepository repository;
    private final Executor disk;
    private final Executor main;
    private final Reminders reminders;
    private final ReminderTimePolicy reminderTime;
    private final MutableLiveData<MemoUiState> state = new MutableLiveData<>(
            new MemoUiState(Collections.emptyList(), 0, TaskRepository.FILTER_ALL, false));
    private final MutableLiveData<MemoEvent> events = new MutableLiveData<>();
    private volatile boolean cleared;
    private long operationId;

    public MemoViewModel(TaskRepository repository, Executor disk, Executor main,
                         Runnable initializer, Reminders reminders) {
        this(repository, disk, main, initializer, reminders, Clock.systemDefaultZone());
    }

    public MemoViewModel(TaskRepository repository, Executor disk, Executor main,
                         Runnable initializer, Reminders reminders, Clock clock) {
        this.repository = repository;
        this.disk = disk;
        this.main = main;
        this.reminders = reminders;
        this.reminderTime = new ReminderTimePolicy(clock);
        submit(initializer, TaskRepository.FILTER_ALL, null);
    }

    public LiveData<MemoUiState> state() { return state; }
    public LiveData<MemoEvent> events() { return events; }
    public long operationId() { return operationId; }

    public boolean selectFilter(int filter) { return submit(() -> { }, filter, null); }

    public boolean createTasks(List<Task> tasks, String successMessage) {
        List<Task> pending = new ArrayList<>(tasks);
        return submit(() -> {
            for (Task task : pending) reminderTime.requireFuture(task.reminderAt);
            repository.createTasks(pending);
        }, TaskRepository.FILTER_ALL, successMessage);
    }

    public boolean deleteTask(long id) {
        return submit(() -> {
            repository.deleteTask(id);
            reminders.cancel(id);
        }, currentFilter(), null);
    }

    public boolean setCompleted(long id, boolean completed) {
        return submit(() -> {
            repository.setCompleted(id, completed);
            if (completed) reminders.cancel(id);
            else {
                Task latest = repository.getTask(id);
                if (latest != null && latest.reminderAt != null) reminders.schedule(latest);
            }
        }, currentFilter(), null);
    }

    public boolean editTask(long id, String name, Long deadline, Long reminderAt) {
        return submit(() -> {
            // Recheck when execution begins: time may have elapsed while queued.
            reminderTime.requireFuture(reminderAt);
            Task latest = repository.getTask(id);
            if (latest == null) throw new IllegalStateException("任务已不存在，请重新打开列表");
            latest.name = name;
            latest.deadline = deadline;
            latest.reminderAt = reminderAt;
            repository.updateTask(latest);
            if (latest.reminderAt != null && !latest.completed) reminders.schedule(latest);
            else reminders.cancel(id);
        }, currentFilter(), null);
    }

    public boolean clearTasks(boolean completedOnly) {
        return submit(() -> {
            List<Task> removed = repository.getTasks(completedOnly
                    ? TaskRepository.FILTER_DONE : TaskRepository.FILTER_ALL);
            if (completedOnly) repository.clearCompleted();
            else repository.clearAll();
            for (Task task : removed) reminders.cancel(task.id);
        }, currentFilter(), null);
    }

    private int currentFilter() { return state.getValue().filter; }

    private boolean submit(Runnable command, int filter, String successMessage) {
        MemoUiState previous = state.getValue();
        if (cleared || previous.busy) return false;
        long requestId = ++operationId;
        state.setValue(new MemoUiState(previous.tasks, previous.activeCount, previous.filter, true));
        try {
            disk.execute(() -> {
                MemoUiState result;
                String message = successMessage;
                boolean commandCompleted = false;
                try {
                    command.run();
                    commandCompleted = true;
                    // Read after the write completes, keeping list and count in one result.
                    List<Task> tasks = repository.getTasks(filter);
                    int count = repository.getTasks(TaskRepository.FILTER_ACTIVE).size();
                    result = new MemoUiState(tasks, count, filter, false);
                } catch (RuntimeException error) {
                    result = new MemoUiState(previous.tasks, previous.activeCount, previous.filter, false);
                    message = commandCompleted
                            ? "操作已完成，但列表刷新失败，请切换筛选刷新"
                            : error instanceof ReminderTimePolicy.InvalidReminderException
                                    ? error.getMessage() : "任务操作失败，请重试";
                }
                MemoUiState finalResult = result;
                String finalMessage = message;
                boolean finalSuccess = commandCompleted;
                main.execute(() -> {
                    if (cleared) return;
                    state.setValue(finalResult);
                    events.setValue(new MemoEvent(finalMessage, requestId, finalSuccess));
                });
            });
        } catch (RuntimeException error) {
            state.setValue(previous);
            events.setValue(new MemoEvent("暂时无法执行任务操作，请重试", requestId, false));
            return false;
        }
        return true;
    }

    @Override protected void onCleared() { cleared = true; }
}
