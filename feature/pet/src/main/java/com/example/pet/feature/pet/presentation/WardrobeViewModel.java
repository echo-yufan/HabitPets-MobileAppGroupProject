package com.example.pet.feature.pet.presentation;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.example.pet.core.model.pet.PetInteractions;
import java.util.UUID;
import java.util.concurrent.Executor;

/** Loads the canonical inventory outfit; never caches a second persistent outfit on the pet. */
public final class WardrobeViewModel extends ViewModel {
    public static final class State {
        public final String outfit;
        public final boolean busy, failed;
        State(String outfit, boolean busy, boolean failed) { this.outfit = outfit; this.busy = busy; this.failed = failed; }
    }
    private final PetInteractions interactions;
    private final Executor disk, main;
    private final MutableLiveData<State> state = new MutableLiveData<>(new State(null, true, false));
    private final MutableLiveData<PetInteractions.Result> events = new MutableLiveData<>();
    private boolean running;
    private volatile boolean cleared;
    private String removeRequest;
    public WardrobeViewModel(PetInteractions interactions, Executor disk, Executor main) {
        this.interactions = interactions; this.disk = disk; this.main = main;
    }
    public LiveData<State> state() { return state; }
    public LiveData<PetInteractions.Result> events() { return events; }
    public void consumeEvent() { events.setValue(null); }
    public void load() { execute(false); }
    public void remove() {
        if (removeRequest == null) removeRequest = UUID.randomUUID().toString();
        execute(true);
    }
    private void execute(boolean remove) {
        if (running || cleared) return;
        running = true;
        State old = state.getValue(); state.setValue(new State(old.outfit, true, false));
        final String request = removeRequest;
        disk.execute(() -> {
            PetInteractions.Result result = null;
            String outfit = old.outfit; boolean failed = false;
            try {
                if (remove) result = interactions.unequip(request);
                outfit = interactions.getEquippedItemId();
            } catch (RuntimeException error) { failed = true; }
            final State next = new State(outfit, false, failed);
            final PetInteractions.Result outcome = result;
            main.execute(() -> {
                running = false;
                if (cleared) return;
                state.setValue(next);
                if (remove) {
                    if (outcome != null && outcome.status == PetInteractions.Status.SUCCESS) removeRequest = null;
                    events.setValue(outcome == null ? new PetInteractions.Result(PetInteractions.Status.STORAGE_ERROR, "", false) : outcome);
                }
            });
        });
    }
    @Override protected void onCleared() { cleared = true; }
}
