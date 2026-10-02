package com.example.pet.feature.planning.domain.usecase;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

/** Reminder dates are independent of deadlines. Invalid times are never silently moved. */
public final class ReminderTimePolicy {
    private final Clock clock;
    public ReminderTimePolicy(Clock clock) { this.clock = clock; }

    public void requireFuture(Long timestamp) {
        if (timestamp != null && timestamp <= clock.millis()) {
            throw new InvalidReminderException("请选择未来的提醒时间");
        }
    }

    public long resolve(LocalDate date, LocalTime time, ZoneId zone) {
        LocalDateTime local = LocalDateTime.of(date, time);
        List<ZoneOffset> offsets = zone.getRules().getValidOffsets(local);
        if (offsets.isEmpty()) {
            throw new InvalidReminderException("该时间因夏令时调整不存在，请选择其他时间");
        }
        // For repeated local times, use the first occurrence consistently.
        long timestamp = local.toInstant(offsets.get(0)).toEpochMilli();
        requireFuture(timestamp);
        return timestamp;
    }

    public static final class InvalidReminderException extends IllegalArgumentException {
        public InvalidReminderException(String message) { super(message); }
    }
}
