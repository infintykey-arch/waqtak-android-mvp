package com.wirdi.app;

import android.content.Context;
import android.content.SharedPreferences;

/** Shared frequency cap for interstitials shown at user-initiated content breaks. */
final class AdFrequencyGate {
    private static final String PREFS = "waqtak_ad_frequency";
    private static final String ACTION_COUNT = "eligible_user_actions";
    private static final String LAST_SHOWN_AT = "last_interstitial_shown_at";
    private static final long MIN_INTERVAL_MS = 120_000L;

    private AdFrequencyGate() {}

    static synchronized boolean recordUserActionAndShouldShow(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int actionCount = prefs.getInt(ACTION_COUNT, 0) + 1;
        prefs.edit().putInt(ACTION_COUNT, actionCount).apply();

        if (actionCount % 2 != 0) return false;
        long lastShownAt = prefs.getLong(LAST_SHOWN_AT, 0L);
        return System.currentTimeMillis() - lastShownAt >= MIN_INTERVAL_MS;
    }

    static synchronized void markShown(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putLong(LAST_SHOWN_AT, System.currentTimeMillis())
                .apply();
    }
}
