package com.example.pet.feature.interaction;

import com.example.pet.core.data.repository.TaskRepository;
import com.example.pet.core.model.task.Task;
import com.example.pet.core.model.task.TaskCompletionReward;
import java.util.List;

/** App composition adapter. Completion is persisted before rewards; reads reconcile unfinished delivery. */
public final class RewardingTaskRepository implements TaskRepository {
    private final TaskRepository tasks;
    private final PetInteractionCoordinator pets;
    public RewardingTaskRepository(TaskRepository tasks, PetInteractionCoordinator pets) {
        this.tasks = tasks; this.pets = pets;
    }
    private void recover() { pets.recover(); }
    @Override public List<Task> getTasks(int filter) { recover(); return tasks.getTasks(filter); }
    @Override public List<Task> getTodayTasks(long date) { recover(); return tasks.getTodayTasks(date); }
    @Override public List<Task> getUpcomingTasks(long from, long to) { recover(); return tasks.getUpcomingTasks(from, to); }
    @Override public Task getTask(long id) { recover(); return tasks.getTask(id); }
    @Override public List<Task> getTasksWithFutureReminders() { recover(); return tasks.getTasksWithFutureReminders(); }
    @Override public long createTask(Task task) { recover(); return tasks.createTask(task); }
    @Override public void createTasks(List<Task> batch) { recover(); tasks.createTasks(batch); }
    @Override public void updateTask(Task task) { recover(); tasks.updateTask(task); recover(); }
    @Override public void deleteTask(long id) { recover(); tasks.deleteTask(id); }
    @Override public void setCompleted(long id, boolean completed) { setCompletedWithReward(id, completed); }
    @Override public TaskCompletionReward setCompletedWithReward(long id, boolean completed) {
        recover();
        TaskCompletionReward reward = tasks.setCompletedWithReward(id, completed);
        recover();
        return reward;
    }
    @Override public int clearAll() { recover(); return tasks.clearAll(); }
    @Override public int clearCompleted() { recover(); return tasks.clearCompleted(); }
}
