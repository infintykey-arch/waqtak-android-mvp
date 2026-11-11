package com.wirdi.app;

import android.content.Context;
import android.content.SharedPreferences;

/** One persistent daily snooze limit shared by every course in the app. */
public final class DailySnoozeLimit {
    private static final int MAX_SNOOZES_PER_DAY = 3;
    private static final String PREFERENCES = "waqtak_daily_snooze_limit";
    private static final String KEY_DATE = "local_date";
    private static final String KEY_COUNT = "count";
    private static final Object LOCK = new Object();

    public enum Result {
        SCHEDULED,
        DAILY_LIMIT_REACHED,
        FAILED
    }

    private DailySnoozeLimit() {}

    public static Result postpone(Context context, StudyCard card) {
        if (context == null || card == null) return Result.FAILED;
        synchronized (LOCK) {
            Context appContext = context.getApplicationContext();
            if (appContext == null) appContext = context;
            SharedPreferences preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
            String today = StudyCard.today();
            String savedDate = preferences.getString(KEY_DATE, "");
            int count = today.equals(savedDate) ? preferences.getInt(KEY_COUNT, 0) : 0;
            if (count >= MAX_SNOOZES_PER_DAY) return Result.DAILY_LIMIT_REACHED;

            if (!AlarmScheduler.scheduleSnooze(context, card)) return Result.FAILED;
            boolean saved = preferences.edit()
                    .putString(KEY_DATE, today)
                    .putInt(KEY_COUNT, count + 1)
                    .commit();
            if (!saved) {
                AlarmScheduler.cancelSnooze(context, card);
                return Result.FAILED;
            }
            return Result.SCHEDULED;
        }
    }
}
