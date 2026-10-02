package com.example.pet.core.common.time;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/** 日期展示工具（统一全组日期口径：今天 / 明天 / M月d日 周X） */
public final class DateUtil {


    private DateUtil() {
    }

    /** 截止日期展示：今天 / 明天 / 后天 / 12月30日 周三 */
    public static String formatDay(long ts) {
        return formatDay(ts, Clock.systemDefaultZone());
    }

    public static String formatDay(long ts, Clock clock) {
        LocalDate today = LocalDate.now(clock);
        LocalDate date = Instant.ofEpochMilli(ts).atZone(clock.getZone()).toLocalDate();
        long diffDays = ChronoUnit.DAYS.between(today, date);
        if (diffDays == 0) {
            return "今天";
        }
        if (diffDays == 1) {
            return "明天";
        }
        if (diffDays == 2) {
            return "后天";
        }
        if (diffDays < 0) {
            return "已逾期 " + date.format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINA));
        }
        return date.format(DateTimeFormatter.ofPattern("M月d日 EEE", Locale.CHINA));
    }

    /** 提醒时间展示：12月30日 08:30 */
    public static String formatDateTime(long ts) {
        return new SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(ts);
    }

    /** 截止时间展示的时间部分：09:00 */
    public static String formatTime(long ts) {
        return new SimpleDateFormat("HH:mm", Locale.CHINA).format(ts);
    }

    public static String formatTime(long ts, Clock clock) {
        return Instant.ofEpochMilli(ts).atZone(clock.getZone())
                .format(DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA));
    }

    public static long startOfDay(long ts) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(ts);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    /** 由年月日 + 时分构造时间戳 */
    public static long of(int year, int month, int day, int hour, int minute) {
        Calendar cal = Calendar.getInstance();
        cal.set(year, month, day, hour, minute, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }
}
