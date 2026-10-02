package com.example.pet.feature.planning.presentation;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.example.pet.core.common.concurrent.AppExecutors;
import com.example.pet.core.data.di.AppDependencies;
import com.example.pet.core.data.repository.TaskRepository;
import com.example.pet.core.model.task.Task;
import com.example.pet.feature.planning.data.local.MemoInitializer;
import com.example.pet.feature.planning.platform.reminder.ReminderScheduler;

/** Keeps only application context; no Activity or View survives with the model. */
public final class MemoViewModelFactory implements ViewModelProvider.Factory {
    private final Context application;

    public MemoViewModelFactory(Context context) {
        application = context.getApplicationContext();
    }

    @NonNull @Override public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (!modelClass.equals(MemoViewModel.class)) throw new IllegalArgumentException("Unknown ViewModel");
        TaskRepository repository = AppDependencies.taskRepository(application);
        Handler handler = new Handler(Looper.getMainLooper());
        MemoViewModel model = new MemoViewModel(repository, AppExecutors.disk(),
                command -> handler.post(command), new MemoInitializer(application, repository),
                new MemoViewModel.Reminders() {
                    @Override public void schedule(Task task) { ReminderScheduler.schedule(application, task); }
                    @Override public void cancel(long id) { ReminderScheduler.cancel(application, id); }
                });
        return modelClass.cast(model);
    }
}
