package com.example.pet.core.data.repository;

import com.example.pet.core.model.task.Task;

import java.util.List;

/** Synchronous task persistence contract. All methods must run on the shared disk executor, never on the UI thread. */
public interface TaskRepository {
    int FILTER_ALL = 0;
    int FILTER_ACTIVE = 1;
    int FILTER_DONE = 2;
    int FILTER_UPCOMING = 3;

    List<Task> getTasks(int filter);
    List<Task> getTodayTasks(long dateStartMillis);
    List<Task> getUpcomingTasks(long startMillis, long endMillis);
    Task getTask(long taskId);
    List<Task> getTasksWithFutureReminders();
    long createTask(Task task);
    /** Commits the whole batch or rolls it back; task IDs are assigned only after commit. */
    void createTasks(List<Task> tasks);
    void updateTask(Task task);
    void deleteTask(long taskId);
    void setCompleted(long taskId, boolean completed);
    /** Returns a receipt only for the first rewarded completion; older hosts may return null. */
    default com.example.pet.core.model.task.TaskCompletionReward setCompletedWithReward(long taskId, boolean completed) {
        setCompleted(taskId, completed);
        return null;
    }
    int clearAll();
    int clearCompleted();
}
