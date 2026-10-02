package com.example.pet.core.data.di;

import android.content.Context;

import com.example.pet.core.data.repository.TaskRepository;

/** Resolves dependencies supplied by the host application without depending on app. */
public final class AppDependencies {
    private AppDependencies() {
    }

    public static TaskRepository taskRepository(Context context) {
        TaskRepositoryOwner owner = (TaskRepositoryOwner) context.getApplicationContext();
        return owner.getTaskRepository();
    }
}
