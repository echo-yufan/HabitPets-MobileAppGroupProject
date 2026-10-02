package com.example.pet.core.data.repository;

/** A requested update cannot succeed because its task no longer exists. */
public final class TaskNotFoundException extends IllegalStateException {
    public TaskNotFoundException(long taskId) {
        super("Task no longer exists: " + taskId);
    }
}
