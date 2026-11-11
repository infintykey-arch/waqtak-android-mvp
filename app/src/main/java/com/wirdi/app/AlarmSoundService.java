package com.wirdi.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

public class AlarmSoundService extends Service {
    private static final String CHANNEL_ID = "waqtak_alarm_playback";
    private static final int FOREGROUND_NOTIFICATION_ID = 47800;
    private static final String EXTRA_CARD_ID = "sound_card_id";
    private static final String EXTRA_CARD_NAME = "sound_card_name";

    private MediaPlayer player;

    public static void start(Context context, String cardId, String cardName) {
        Intent intent = new Intent(context, AlarmSoundService.class)
                .putExtra(EXTRA_CARD_ID, cardId)
                .putExtra(EXTRA_CARD_NAME, cardName);
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
            else context.startService(intent);
        } catch (Exception e) {
            android.util.Log.e("MidadAlarmSound", "Could not start alarm sound service", e);
        }
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, AlarmSoundService.class));
    }

    @Override public void onCreate() {
        super.onCreate();
        createForegroundChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String cardId = intent == null ? null : intent.getStringExtra(EXTRA_CARD_ID);
        String cardName = intent == null ? null : intent.getStringExtra(EXTRA_CARD_NAME);
        startInForeground(cardId, cardName);
        startPlayback(AlarmReceiver.selectedSoundUri(this), true);
        return START_NOT_STICKY;
    }

    private void startInForeground(String cardId, String cardName) {
        Intent openAlarm = new Intent(this, AlarmActivity.class)
                .putExtra(AlarmScheduler.EXTRA_ID, cardId)
                .putExtra(AlarmScheduler.EXTRA_NAME, cardName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int requestCode = cardId == null ? FOREGROUND_NOTIFICATION_ID : AlarmReceiver.notificationId(cardId) + 2;
        PendingIntent contentIntent = PendingIntent.getActivity(this, requestCode, openAlarm,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this).setPriority(Notification.PRIORITY_LOW);
        Notification notification = builder
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(getString(R.string.service_notification_title))
                .setContentText(getString(R.string.service_notification_text))
                .setCategory(Notification.CATEGORY_SERVICE)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setOngoing(true)
                .setContentIntent(contentIntent)
                .build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(FOREGROUND_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, notification);
        }
    }

    private void createForegroundChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;
        NotificationChannel channel = manager.getNotificationChannel(CHANNEL_ID);
        if (channel == null) {
            channel = new NotificationChannel(CHANNEL_ID, getString(R.string.playback_channel_name), NotificationManager.IMPORTANCE_LOW);
            channel.setSound(null, null);
            channel.enableVibration(false);
        }
        channel.setName(getString(R.string.playback_channel_name));
        channel.setDescription(getString(R.string.playback_channel_description));
        manager.createNotificationChannel(channel);
    }

    private void startPlayback(Uri uri, boolean allowFallback) {
        releasePlayer();
        MediaPlayer next = new MediaPlayer();
        player = next;
        try {
            next.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            next.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
            next.setLooping(true);
            next.setOnPreparedListener(mediaPlayer -> {
                if (player == mediaPlayer) {
                    try { mediaPlayer.start(); }
                    catch (IllegalStateException e) { android.util.Log.e("MidadAlarmSound", "Alarm tone could not start", e); }
                }
            });
            next.setOnErrorListener((mediaPlayer, what, extra) -> {
                if (player == mediaPlayer) {
                    releasePlayer();
                    if (allowFallback) startPlayback(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), false);
                    else stopSelf();
                }
                return true;
            });
            Uri tone = uri == null ? RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) : uri;
            next.setDataSource(this, tone);
            next.prepareAsync();
        } catch (Exception e) {
            android.util.Log.e("MidadAlarmSound", "Alarm tone could not be prepared", e);
            releasePlayer();
            if (allowFallback) {
                Uri fallback = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                if (fallback != null && !fallback.equals(uri)) startPlayback(fallback, false);
                else stopSelf();
            } else {
                stopSelf();
            }
        }
    }

    private void releasePlayer() {
        if (player == null) return;
        try { if (player.isPlaying()) player.stop(); } catch (Exception ignored) {}
        try { player.reset(); } catch (Exception ignored) {}
        try { player.release(); } catch (Exception ignored) {}
        player = null;
    }

    @Override public void onDestroy() {
        releasePlayer();
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE);
        else stopForeground(true);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
