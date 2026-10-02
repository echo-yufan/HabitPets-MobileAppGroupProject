package com.example.pet.feature.planning.domain.usecase;

import com.example.pet.core.data.repository.TaskRepository;
import com.example.pet.core.model.task.Task;
import org.junit.Test;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class DeliverTaskReminderTest {
    private Task stored;
    private final List<String> notifications = new ArrayList<>();
    private final TaskRepository repository = (TaskRepository) Proxy.newProxyInstance(
            TaskRepository.class.getClassLoader(), new Class<?>[]{TaskRepository.class},
            (proxy, method, args) -> {
                if (!method.getName().equals("getTask")) throw new AssertionError("Unexpected call");
                return stored;
            });

    private Task task() {
        stored = new Task(); stored.id = 1; stored.name = "current name"; stored.reminderAt = 100L;
        return stored;
    }

    private boolean deliver(long expected) {
        return new DeliverTaskReminder(repository, () -> 200L,
                task -> notifications.add(task.name)).execute(1, expected);
    }

    @Test public void missingTaskDoesNotNotify() { assertFalse(deliver(100)); assertTrue(notifications.isEmpty()); }
    @Test public void completedTaskDoesNotNotify() { task().completed = true; assertFalse(deliver(100)); }
    @Test public void disabledReminderDoesNotNotify() { task().reminderAt = null; assertFalse(deliver(100)); }
    @Test public void futureReminderDoesNotNotifyEarly() { task().reminderAt = 300L; assertFalse(deliver(300)); }
    @Test public void oldBroadcastCannotTriggerChangedReminder() { task().reminderAt = 150L; assertFalse(deliver(100)); }
    @Test public void validLateReminderUsesCurrentDatabaseName() {
        task(); assertTrue(deliver(100)); assertEquals("current name", notifications.get(0));
    }
    @Test public void olderInstalledAlarmWithoutTimestampStillChecksDatabase() {
        task(); assertTrue(deliver(-1)); stored = null; assertFalse(deliver(-1));
    }
}
