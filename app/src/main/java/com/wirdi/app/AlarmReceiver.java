package com.wirdi.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import java.util.ArrayList;

public class AlarmReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID_PREFIX = "waqtak_daily_alarm_";
    private static final String CHANNEL_NAME = "تنبيهات وقتك";
    private static final String PREFS = "notification_sound_preferences";
    private static final String PREF_SOUND_URI = "sound_uri";
    private static final String PREF_SOUND_NAME = "sound_name";

    public static int notificationId(String id) { return 47000 + Math.abs(id.hashCode() % 20000); }

    public static Uri selectedSoundUri(Context context) {
        String saved = preferences(context).getString(PREF_SOUND_URI, null);
        if (saved != null && !saved.isEmpty()) {
            try { return Uri.parse(saved); } catch (Exception ignored) {}
        }
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
    }

    public static String selectedSoundName(Context context) {
        return preferences(context).getString(PREF_SOUND_NAME, "نغمة الإشعار الافتراضية");
    }

    public static void saveSelectedSound(Context context, Uri uri, String name) {
        if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        if (name == null || name.trim().isEmpty()) name = "نغمة الإشعار الافتراضية";
        preferences(context).edit()
                .putString(PREF_SOUND_URI, uri.toString())
                .putString(PREF_SOUND_NAME, name)
                .apply();
        ensureChannel(context);
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String channelId(Context context) {
        return CHANNEL_ID_PREFIX + Integer.toHexString(selectedSoundUri(context).toString().hashCode());
    }

    public static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        String id = channelId(context);
        if (nm.getNotificationChannel(id) != null) return;

        NotificationChannel ch = new NotificationChannel(id, CHANNEL_NAME + " · " + selectedSoundName(context), NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("منبه يومي لمواعيد المذاكرة");
        ch.enableVibration(true);
        ch.setVibrationPattern(new long[]{0, 500, 250, 500, 250, 900});
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        ch.setSound(null, null);
        nm.createNotificationChannel(ch);
    }

    @Override public void onReceive(Context context, Intent intent) {
        String id = intent.getStringExtra(AlarmScheduler.EXTRA_ID);
        String name = intent.getStringExtra(AlarmScheduler.EXTRA_NAME);
        boolean snooze = intent.getBooleanExtra(AlarmScheduler.EXTRA_SNOOZE, false);
        if (id == null) return;
        ArrayList<StudyCard> cards = StudyStore.load(context);
        StudyCard card = null;
        for (StudyCard candidate : cards) {
            if (candidate.id.equals(id)) { card = candidate; break; }
        }
        if (card == null) return;
        if (name == null || name.isEmpty()) name = card.name;
        if (!snooze) {
            card.triggeredDate = StudyCard.today();
            StudyStore.save(context, cards);
            AlarmScheduler.scheduleDaily(context, card);
        }
        ensureChannel(context);
        AlarmSoundService.start(context, id, name);
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        Intent full = new Intent(context, AlarmActivity.class).putExtra(AlarmScheduler.EXTRA_ID, id).putExtra(AlarmScheduler.EXTRA_NAME, name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent fullPi = PendingIntent.getActivity(context, notificationId(id), full, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        Intent content = new Intent(context, AlarmActivity.class).putExtra(AlarmScheduler.EXTRA_ID, id).putExtra(AlarmScheduler.EXTRA_NAME, name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentPi = PendingIntent.getActivity(context, notificationId(id) + 1, content, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, channelId(context))
                : new Notification.Builder(context).setPriority(Notification.PRIORITY_MAX).setSound(null);
        Notification notification = b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("حان وقت جلسة المذاكرة")
                .setContentText(name)
                .setStyle(new Notification.BigTextStyle().bigText(name + " — ابدأ جلسة المذاكرة أو أجّل التنبيه خمس دقائق."))
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setAutoCancel(false).setOngoing(true)
                .setFullScreenIntent(fullPi, true).setContentIntent(contentPi)
                .setVibrate(new long[]{0, 500, 250, 500, 250, 900}).build();
        nm.notify(notificationId(id), notification);
    }
}
