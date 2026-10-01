package com.example.myapplication.ui;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/** 日期展示工具（统一全组日期口径：今天 / 明天 / M月d日 周X） */
public final class DateUtil {

    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    private DateUtil() {
    }

    /** 截止日期展示：今天 / 明天 / 后天 / 12月30日 周三 */
    public static String formatDay(long ts) {
        long todayStart = startOfDay(System.currentTimeMillis());
        long dayStart = startOfDay(ts);
        long diffDays = (dayStart - todayStart) / DAY_MS;
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
            return "已逾期 " + new SimpleDateFormat("M月d日", Locale.CHINA).format(ts);
        }
        return new SimpleDateFormat("M月d日 EEE", Locale.CHINA).format(ts);
    }

    /** 提醒时间展示：12月30日 08:30 */
    public static String formatDateTime(long ts) {
        return new SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(ts);
    }

    /** 截止时间展示的时间部分：09:00 */
    public static String formatTime(long ts) {
        return new SimpleDateFormat("HH:mm", Locale.CHINA).format(ts);
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
