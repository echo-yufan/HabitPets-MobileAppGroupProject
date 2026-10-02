package com.example.pet.core.common.concurrent;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Process-wide ordered disk queue. Do not shut it down when a screen closes. */
public final class AppExecutors {
    private static final Executor DISK = Executors.newSingleThreadExecutor(runnable ->
            new Thread(runnable, "habitpets-disk"));

    private AppExecutors() { }

    public static Executor disk() {
        return DISK;
    }
}
