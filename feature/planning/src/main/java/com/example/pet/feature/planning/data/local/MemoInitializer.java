package com.example.pet.feature.planning.data.local;

import android.content.Context;
import com.example.pet.core.common.time.DateUtil;
import com.example.pet.core.data.repository.TaskRepository;
import com.example.pet.core.model.task.Task;
import java.util.Arrays;

/** Existing first-run samples, executed on the shared disk queue. */
public final class MemoInitializer implements Runnable {
    private final Context application;
    private final TaskRepository repo;

    public MemoInitializer(Context context, TaskRepository repo) {
        this.application = context.getApplicationContext();
        this.repo = repo;
    }

    @Override
    public void run() {
        android.content.SharedPreferences prefs =
                application.getSharedPreferences("b_planner", Context.MODE_PRIVATE);
        if (prefs.getBoolean("seeded_v1", false) || !repo.getTasks(TaskRepository.FILTER_ALL).isEmpty()) {
            return;
        }
        long today = DateUtil.startOfDay(System.currentTimeMillis());

        Task feed = new Task();
        feed.name = "去养成商城补充饲料";
        feed.deadline = today + 14 * 60 * 60 * 1000L + 30 * 60 * 1000L;
        feed.tag = "采购";

        Task breakfast = new Task();
        breakfast.name = "给小牛准备早餐";
        breakfast.deadline = today + 9 * 60 * 60 * 1000L;
        breakfast.tag = "日常照顾";

        Task record = new Task();
        record.name = "整理宠物成长记录";
        record.completed = true;
        record.createdAt = today - 26 * 60 * 60 * 1000L;
        record.completedAt = today - 25 * 60 * 60 * 1000L;
        repo.createTasks(Arrays.asList(feed, breakfast, record));

        prefs.edit().putBoolean("seeded_v1", true).apply();
    }

}
