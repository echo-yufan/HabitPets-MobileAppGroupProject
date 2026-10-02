package com.example.pet.core.data.di;

import com.example.pet.core.data.repository.TaskRepository;

/** Dependency contract implemented by the host application. */
public interface TaskRepositoryOwner {
    TaskRepository getTaskRepository();
}
