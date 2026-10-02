package com.example.pet.feature.planning.domain.usecase;

import com.example.pet.core.data.repository.TaskRepository;
import com.example.pet.core.model.task.Task;

/** Runs on the same disk queue as task mutations, including the final notification. */
public final class DeliverTaskReminder {
    public interface Clock { long now(); }
    public interface Notifier { void show(Task task); }
    private final TaskRepository repository;
    private final Clock clock;
    private final Notifier notifyTask;

    public DeliverTaskReminder(TaskRepository repository, Clock clock, Notifier notifyTask) {
        this.repository = repository;
        this.clock = clock;
        this.notifyTask = notifyTask;
    }

    public boolean execute(long taskId, long expectedReminderAt) {
        Task task = repository.getTask(taskId);
        if (task == null || task.completed || task.reminderAt == null
                || task.reminderAt > clock.now()) return false;
        // Older installed alarms have no timestamp extra; still validate their database state.
        if (expectedReminderAt >= 0 && task.reminderAt.longValue() != expectedReminderAt) return false;
        notifyTask.show(task);
        return true;
    }
}
