package com.example.pet.feature.planning.presentation;

import com.example.pet.core.model.task.Task;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One snapshot for the list, count, filter and loading indicator. */
public final class MemoUiState {
    public final List<Task> tasks;
    public final int activeCount;
    public final int filter;
    public final boolean busy;

    public MemoUiState(List<Task> tasks, int activeCount, int filter, boolean busy) {
        this.tasks = Collections.unmodifiableList(new ArrayList<>(tasks));
        this.activeCount = activeCount;
        this.filter = filter;
        this.busy = busy;
    }
}
