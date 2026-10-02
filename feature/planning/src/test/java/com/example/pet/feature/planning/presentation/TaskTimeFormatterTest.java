package com.example.pet.feature.planning.presentation;

import com.example.pet.core.common.time.DateUtil;
import com.example.pet.core.model.task.Task;
import org.junit.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import static org.junit.Assert.*;

public class TaskTimeFormatterTest {
    private final ZoneId zone = ZoneId.of("Asia/Shanghai");
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-02T10:00:00Z"), zone);
    private final TaskTimeFormatter formatter = new TaskTimeFormatter(clock);
    private Task added(long timestamp) { Task task = new Task(); task.createdAt = timestamp; return task; }
    private Task completed(Long timestamp) { Task task = added(0); task.completed = true; task.completedAt = timestamp; return task; }

    @Test public void additionChangesFromJustNowAtExactlyTwoMinutes() {
        assertEquals("刚刚添加", formatter.format(added(clock.millis() - 119999)));
        assertEquals("今天添加", formatter.format(added(clock.millis() - 120000)));
    }
    @Test public void completionChangesFromJustNowAtExactlyTwoMinutes() {
        assertEquals("刚刚完成", formatter.format(completed(clock.millis() - 119999)));
        assertEquals("今天完成", formatter.format(completed(clock.millis() - 120000)));
    }
    @Test public void earlierTodayIsNotJustNow() {
        long morning = Instant.parse("2026-10-02T00:00:00Z").toEpochMilli();
        assertEquals("今天添加", formatter.format(added(morning)));
        assertEquals("今天完成", formatter.format(completed(morning)));
    }
    @Test public void yesterdayHasItsOwnLabel() {
        long yesterday = clock.instant().minusSeconds(86400).toEpochMilli();
        assertEquals("昨天添加", formatter.format(added(yesterday)));
        assertEquals("昨天完成", formatter.format(completed(yesterday)));
    }
    @Test public void olderEventsUseDateWithoutOverdueWording() {
        long old = Instant.parse("2026-09-25T01:00:00Z").toEpochMilli();
        assertEquals("9月25日添加", formatter.format(added(old)));
        assertEquals("9月25日完成", formatter.format(completed(old)));
    }
    @Test public void previousYearIsExplicit() {
        assertEquals("2025年9月25日完成", formatter.format(completed(Instant.parse("2025-09-25T01:00:00Z").toEpochMilli())));
    }
    @Test public void missingCompletionTimeDoesNotInventNow() {
        assertEquals("已完成", formatter.format(completed(null)));
    }
    @Test public void deadlineTakesPriorityOverCreationTime() {
        Task task = added(0);
        task.deadline = Instant.parse("2026-10-03T10:00:00Z").toEpochMilli();
        assertEquals("明天 18:00", formatter.format(task));
        task.completed = true;
        task.completedAt = clock.millis();
        assertEquals("刚刚完成", formatter.format(task));
    }
    @Test public void pastDeadlinePreservesExistingDeadlineLabel() {
        Task task = added(clock.millis());
        task.deadline = Instant.parse("2026-09-25T01:00:00Z").toEpochMilli();
        assertEquals("已逾期 9月25日 09:00", formatter.format(task));
    }
    @Test public void futureTimestampIsNotJustNow() {
        assertEquals("今天添加", formatter.format(added(clock.millis() + 60000)));
    }
    @Test public void justNowCanSpanMidnightWhileOlderEventsBecomeYesterday() {
        Clock midnight = Clock.fixed(Instant.parse("2026-10-02T16:00:30Z"), zone);
        TaskTimeFormatter atMidnight = new TaskTimeFormatter(midnight);
        assertEquals("刚刚添加", atMidnight.format(added(midnight.millis() - 60000)));
        assertEquals("昨天添加", atMidnight.format(added(midnight.millis() - 120000)));
    }
    @Test public void calendarDaysWorkAcrossTwentyThreeHourDay() {
        ZoneId ny = ZoneId.of("America/New_York");
        Clock before = Clock.fixed(Instant.parse("2026-03-08T05:00:00Z"), ny);
        long next = Instant.parse("2026-03-09T04:00:00Z").toEpochMilli();
        assertEquals("明天", DateUtil.formatDay(next, before));
        Clock after = Clock.fixed(Instant.parse("2026-03-09T04:00:00Z"), ny);
        assertEquals("昨天完成", new TaskTimeFormatter(after).format(completed(before.millis())));
    }
    @Test public void calendarDaysWorkAcrossTwentyFiveHourDay() {
        ZoneId ny = ZoneId.of("America/New_York");
        Clock before = Clock.fixed(Instant.parse("2026-11-01T04:00:00Z"), ny);
        long next = Instant.parse("2026-11-02T05:00:00Z").toEpochMilli();
        assertEquals("明天", DateUtil.formatDay(next, before));
        Clock after = Clock.fixed(Instant.parse("2026-11-02T05:00:00Z"), ny);
        assertEquals("昨天添加", new TaskTimeFormatter(after).format(added(before.millis())));
    }
}
