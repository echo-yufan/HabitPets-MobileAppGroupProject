package com.example.pet.feature.planning.domain.usecase;

import org.junit.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import static org.junit.Assert.*;

public class ReminderTimePolicyTest {
    private final ZoneId zone = ZoneId.of("Asia/Shanghai");
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-02T02:00:00Z"), zone);
    private final ReminderTimePolicy policy = new ReminderTimePolicy(clock);

    @Test public void rejectsPastAndExactlyNow() {
        for (long value : new long[]{clock.millis() - 1, clock.millis()}) {
            try { policy.requireFuture(value); fail(); }
            catch (ReminderTimePolicy.InvalidReminderException e) {
                assertEquals("请选择未来的提醒时间", e.getMessage());
            }
        }
    }
    @Test public void acceptsFutureAndDisabledReminder() {
        policy.requireFuture(null);
        policy.requireFuture(clock.millis() + 1);
    }
    @Test public void explicitDateAndTimeArePreservedExactly() {
        assertEquals(Instant.parse("2026-10-03T10:00:00Z").toEpochMilli(),
                policy.resolve(LocalDate.of(2026, 10, 3), LocalTime.of(18, 0), zone));
    }
    @Test public void pastDateIsRejectedInsteadOfAutomaticallyAddingOneDay() {
        try { policy.resolve(LocalDate.of(2026, 9, 30), LocalTime.of(18, 0), zone); fail(); }
        catch (ReminderTimePolicy.InvalidReminderException expected) { }
    }
    @Test public void daylightSavingGapIsRejectedInsteadOfMovingChosenHour() {
        ReminderTimePolicy early = new ReminderTimePolicy(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), zone));
        try { early.resolve(LocalDate.of(2026, 3, 8), LocalTime.of(2, 30), ZoneId.of("America/New_York")); fail(); }
        catch (ReminderTimePolicy.InvalidReminderException e) { assertTrue(e.getMessage().contains("不存在")); }
    }
    @Test public void daylightSavingOverlapUsesFirstOccurrence() {
        assertEquals(Instant.parse("2026-11-01T05:30:00Z").toEpochMilli(),
                policy.resolve(LocalDate.of(2026, 11, 1), LocalTime.of(1, 30), ZoneId.of("America/New_York")));
    }
}
