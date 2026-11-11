package com.wirdi.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import java.util.ArrayList;
import java.util.Calendar;

public final class AlarmScheduler {
    public static final String ACTION_ALARM = "com.wirdi.app.DAILY_ALARM";
    public static final String ACTION_SNOOZE = "com.wirdi.app.SNOOZE_ALARM";
    public static final String EXTRA_ID = "card_id";
    public static final String EXTRA_NAME = "card_name";
    public static final String EXTRA_SNOOZE = "is_snooze";
    private AlarmScheduler() {}

    public static void scheduleAll(Context context) {
        scheduleAll(context, StudyStore.load(context));
    }

    public static void scheduleAll(Context context, ArrayList<StudyCard> cards) {
        if (cards == null) return;
        for (StudyCard card : cards) scheduleDaily(context, card);
    }

    public static void scheduleDaily(Context context, StudyCard card) {
        if (card == null || card.id.isEmpty()) return;
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        PendingIntent pending = alarmPendingIntent(context, card.id, card.name, false);
        long nextAt = nextAlarmTimeMillis(card);
        if (nextAt == Long.MAX_VALUE) {
            manager.cancel(pending);
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= 31 && manager.canScheduleExactAlarms()) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextAt, pending);
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextAt, pending);
            }
        } catch (SecurityException ignored) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextAt, pending);
        }
    }

    /** Returns the next eligible alarm time, or Long.MAX_VALUE if no weekdays are selected. */
    public static long nextAlarmTimeMillis(StudyCard card) {
        if (card == null || card.weekdaysMask == 0) return Long.MAX_VALUE;
        String[] hm = card.scheduledTime.split(":");
        int hour = hm.length > 0 ? safeInt(hm[0], 19) : 19;
        int minute = hm.length > 1 ? safeInt(hm[1], 0) : 0;
        Calendar now = Calendar.getInstance();
        long nowMillis = now.getTimeInMillis();

        for (int offset = 0; offset <= 7; offset++) {
            Calendar candidate = (Calendar) now.clone();
            candidate.add(Calendar.DAY_OF_YEAR, offset);
            candidate.set(Calendar.HOUR_OF_DAY, hour);
            candidate.set(Calendar.MINUTE, minute);
            candidate.set(Calendar.SECOND, 0);
            candidate.set(Calendar.MILLISECOND, 0);

            int weekdayBit = 1 << (candidate.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY);
            if ((card.weekdaysMask & weekdayBit) == 0) continue;
            if (candidate.getTimeInMillis() <= nowMillis) {
                long lateBy = nowMillis - candidate.getTimeInMillis();
                if (offset == 0 && !StudyCard.today().equals(card.triggeredDate)
                        && lateBy < 60_000L) {
                    return nowMillis + 1_000L;
                }
                continue;
            }
            return candidate.getTimeInMillis();
        }
        return Long.MAX_VALUE;
    }

    public static boolean scheduleSnooze(Context context, StudyCard card) {
        if (card == null) return false;
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return false;
        PendingIntent pending = alarmPendingIntent(context, card.id, card.name, true);
        long at = System.currentTimeMillis() + 5 * 60 * 1000L;
        try {
            if (Build.VERSION.SDK_INT >= 31 && manager.canScheduleExactAlarms()) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending);
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending);
            }
            return true;
        } catch (SecurityException ignored) {
            try {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending);
                return true;
            } catch (RuntimeException fallbackError) {
                return false;
            }
        } catch (RuntimeException error) {
            return false;
        }
    }

    public static void cancelSnooze(Context context, StudyCard card) {
        if (card == null) return;
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager != null) manager.cancel(alarmPendingIntent(context, card.id, card.name, true));
    }

    public static void cancel(Context context, String id) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        manager.cancel(alarmPendingIntent(context, id, "", false));
        manager.cancel(alarmPendingIntent(context, id, "", true));
    }

    private static PendingIntent alarmPendingIntent(Context context, String id, String name, boolean snooze) {
        String action = snooze ? ACTION_SNOOZE : ACTION_ALARM;
        Intent i = new Intent(context, AlarmReceiver.class).setAction(action).setData(Uri.parse("wirdi://alarm/" + id + (snooze ? "/snooze" : "")));
        i.putExtra(EXTRA_ID, id); i.putExtra(EXTRA_NAME, name); i.putExtra(EXTRA_SNOOZE, snooze);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(context, id.hashCode() + (snooze ? 1 : 0), i, flags);
    }
    private static int safeInt(String s, int fallback) { try { return Integer.parseInt(s); } catch (Exception e) { return fallback; } }
}
