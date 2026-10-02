package com.example.pet.feature.planning.presentation;

import com.example.pet.core.common.time.DateUtil;
import com.example.pet.core.model.task.Task;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Display-only rules; historical timestamps are never rewritten. */
public final class TaskTimeFormatter {
    private static final long RECENT_MS = 2 * 60 * 1000L;
    private final Clock clock;
    public TaskTimeFormatter(Clock clock) { this.clock = clock; }

    public String format(Task task) {
        if (task.completed) {
            return task.completedAt == null ? "已完成" : event(task.completedAt, "完成");
        }
        if (task.deadline != null) {
            return DateUtil.formatDay(task.deadline, clock) + " " + DateUtil.formatTime(task.deadline, clock);
        }
        return event(task.createdAt, "添加");
    }

    private String event(long timestamp, String action) {
        long age = clock.millis() - timestamp;
        if (age >= 0 && age < RECENT_MS) return "刚刚" + action;
        LocalDate today = LocalDate.now(clock);
        LocalDate date = Instant.ofEpochMilli(timestamp).atZone(clock.getZone()).toLocalDate();
        if (date.equals(today)) return "今天" + action;
        if (date.equals(today.minusDays(1))) return "昨天" + action;
        String pattern = date.getYear() == today.getYear() ? "M月d日" : "yyyy年M月d日";
        return date.format(DateTimeFormatter.ofPattern(pattern, Locale.CHINA)) + action;
    }
}
