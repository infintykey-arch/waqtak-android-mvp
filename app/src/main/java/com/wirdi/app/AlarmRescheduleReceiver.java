package com.wirdi.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmRescheduleReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent != null && Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) AlarmReceiver.ensureChannel(context);
        AlarmScheduler.scheduleAll(context);
    }
}
