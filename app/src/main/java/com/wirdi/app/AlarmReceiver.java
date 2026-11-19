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
    private static final String PREFS = "notification_sound_preferences";
    private static final String PREF_SOUND_URI = "sound_uri";
    private static final String PREF_SOUND_NAME = "sound_name";
    private static final String LEGACY_DEFAULT_SOUND_NAME = "\u0646\u063a\u0645\u0629 \u0627\u0644\u0625\u0634\u0639\u0627\u0631 \u0627\u0644\u0627\u0641\u062a\u0631\u0627\u0636\u064a\u0629";

    public static int notificationId(String id) { return 47000 + Math.abs(id.hashCode() % 20000); }
    public static int reminderNotificationId(String id) { return notificationId(id) + 20_000; }

    public static void cancelReminderNotification(Context context, String id) {
        if (context == null || id == null || id.isEmpty()) return;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.cancel(reminderNotificationId(id));
    }

    public static Uri selectedSoundUri(Context context) {
        String saved = preferences(context).getString(PREF_SOUND_URI, null);
        if (saved != null && !saved.isEmpty()) {
            try { return Uri.parse(saved); } catch (Exception ignored) {}
        }
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
    }

    public static String selectedSoundName(Context context) {
        String saved = preferences(context).getString(PREF_SOUND_NAME, null);
        if (saved == null || saved.trim().isEmpty() || context.getString(R.string.default_ringtone).equals(saved) || LEGACY_DEFAULT_SOUND_NAME.equals(saved)) {
            return context.getString(R.string.default_ringtone);
        }
        return saved;
    }

    public static void saveSelectedSound(Context context, Uri uri, String name) {
        if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        if (name == null || name.trim().isEmpty()) name = context.getString(R.string.default_ringtone);
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
        context = AppLocale.wrap(context);
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        String id = channelId(context);
        NotificationChannel ch = nm.getNotificationChannel(id);
        if (ch == null) {
            ch = new NotificationChannel(id, context.getString(R.string.alarm_channel_name) + " · " + selectedSoundName(context), NotificationManager.IMPORTANCE_HIGH);
            ch.enableVibration(true);
            ch.setVibrationPattern(new long[]{0, 500, 250, 500, 250, 900});
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            ch.setSound(null, null);
        }
        ch.setName(context.getString(R.string.alarm_channel_name) + " · " + selectedSoundName(context));
        ch.setDescription(context.getString(R.string.alarm_channel_description));
        nm.createNotificationChannel(ch);
    }

    @Override public void onReceive(Context context, Intent intent) {
        context = AppLocale.wrap(context);
        if (intent != null && AlarmScheduler.ACTION_REMINDER_NOT_NOW.equals(intent.getAction())) {
            cancelReminderNotification(context, intent.getStringExtra(AlarmScheduler.EXTRA_ID));
            return;
        }
        if (intent == null) return;
        String id = intent.getStringExtra(AlarmScheduler.EXTRA_ID);
        String name = intent.getStringExtra(AlarmScheduler.EXTRA_NAME);
        boolean snooze = intent.getBooleanExtra(AlarmScheduler.EXTRA_SNOOZE, false);
        boolean autoReminder = AlarmScheduler.ACTION_REMINDER.equals(intent.getAction())
                || intent.getBooleanExtra(AlarmScheduler.EXTRA_AUTO_REMINDER, false);
        if (id == null) return;
        ArrayList<StudyCard> cards = StudyStore.load(context);
        StudyCard card = null;
        for (StudyCard candidate : cards) {
            if (candidate.id.equals(id)) { card = candidate; break; }
        }
        if (card == null) return;
        if (autoReminder && (card.doneToday() || card.autoReminderAtMillis <= 0)) {
            if (card.autoReminderAtMillis > 0) {
                card.autoReminderAtMillis = 0;
                StudyStore.save(context, cards);
            }
            return;
        }
        if (name == null || name.isEmpty()) name = card.name;
        if (autoReminder) {
            card.autoReminderAtMillis = 0;
            StudyStore.save(context, cards);
        } else if (!snooze) {
            card.triggeredDate = StudyCard.today();
            card.autoReminderDate = "";
            StudyStore.save(context, cards);
            AlarmScheduler.scheduleDaily(context, card);
        }
        if (autoReminder) {
            showIncompleteReminder(context, card, name);
            return;
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
                .setContentTitle(context.getString(R.string.alarm_screen_title))
                .setContentText(name)
                .setStyle(new Notification.BigTextStyle().bigText(context.getString(R.string.alarm_name_for_notification, name)))
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setAutoCancel(false).setOngoing(true)
                .setFullScreenIntent(fullPi, true).setContentIntent(contentPi)
                .setVibrate(new long[]{0, 500, 250, 500, 250, 900}).build();
        nm.notify(notificationId(id), notification);
    }

    private static void showIncompleteReminder(Context context, StudyCard card, String name) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = manager.getNotificationChannel("waqtak_incomplete_course_reminders");
            if (channel == null) {
                channel = new NotificationChannel("waqtak_incomplete_course_reminders",
                        context.getString(R.string.incomplete_reminder_channel_name), NotificationManager.IMPORTANCE_DEFAULT);
            }
            channel.setName(context.getString(R.string.incomplete_reminder_channel_name));
            channel.setDescription(context.getString(R.string.incomplete_reminder_channel_description));
            channel.setSound(null, null);
            channel.enableVibration(false);
            channel.enableLights(false);
            manager.createNotificationChannel(channel);
        }

        Intent continueIntent = new Intent(context, StudyActivity.class)
                .setAction(AlarmScheduler.ACTION_REMINDER)
                .setData(Uri.parse("wirdi://reminder/" + card.id + "/continue"))
                .putExtra(AlarmScheduler.EXTRA_ID, card.id)
                .putExtra(AlarmScheduler.EXTRA_NAME, name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent continuePending = PendingIntent.getActivity(context, reminderNotificationId(card.id), continueIntent, flags);

        Intent notNowIntent = new Intent(context, AlarmReceiver.class)
                .setAction(AlarmScheduler.ACTION_REMINDER_NOT_NOW)
                .setData(Uri.parse("wirdi://reminder/" + card.id + "/not-now"))
                .putExtra(AlarmScheduler.EXTRA_ID, card.id);
        PendingIntent notNowPending = PendingIntent.getBroadcast(context, reminderNotificationId(card.id) + 1, notNowIntent, flags);

        String message = context.getString(R.string.incomplete_reminder_text, name);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, "waqtak_incomplete_course_reminders")
                : new Notification.Builder(context).setPriority(Notification.PRIORITY_DEFAULT).setSound(null).setVibrate(new long[0]);
        Notification notification = builder.setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(context.getString(R.string.incomplete_reminder_title))
                .setContentText(message)
                .setStyle(new Notification.BigTextStyle().bigText(message))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setAutoCancel(true)
                .setContentIntent(continuePending)
                .addAction(android.R.drawable.ic_media_play, context.getString(R.string.continue_course_now), continuePending)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, context.getString(R.string.not_now), notNowPending)
                .build();
        manager.notify(reminderNotificationId(card.id), notification);
    }
}
