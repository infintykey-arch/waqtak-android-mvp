package com.wirdi.app;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

public class AlarmActivity extends LocalizedActivity {
    private String cardId = "";
    private String cardName = "";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true); }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        getWindow().setStatusBarColor(Ui.BG); getWindow().setNavigationBarColor(Ui.BG);
        cardId = getIntent().getStringExtra(AlarmScheduler.EXTRA_ID);
        cardName = getIntent().getStringExtra(AlarmScheduler.EXTRA_NAME);
        if (cardName == null) cardName = getString(R.string.default_alarm_name);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setGravity(Gravity.CENTER);
        AppLocale.applyLayoutDirection(this, root);
        root.setPadding(Ui.dp(this, 26), Ui.dp(this, 28), Ui.dp(this, 26), Ui.dp(this, 28));
        root.setBackground(Ui.gradient(Color.rgb(8,14,25), Color.rgb(3,6,11), Ui.LINE, 0, this));
        TextView bell = Ui.text(this, "◉", 62, Ui.BLUE, true); bell.setGravity(Gravity.CENTER);
        root.addView(bell, new LinearLayout.LayoutParams(-1, Ui.dp(this, 120)));
        TextView label = Ui.text(this, getString(R.string.alarm_screen_title), 16, Ui.SILVER, false); label.setGravity(Gravity.CENTER);
        root.addView(label, new LinearLayout.LayoutParams(-1, Ui.dp(this, 38)));
        TextView title = Ui.text(this, cardName, 27, Ui.WHITE, true); title.setGravity(Gravity.CENTER); title.setMaxLines(3);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, Ui.dp(this, 110)); titleLp.bottomMargin = Ui.dp(this, 18); root.addView(title, titleLp);
        TextView begin = Ui.text(this, getString(R.string.start_now), 17, Color.WHITE, true); begin.setGravity(Gravity.CENTER); begin.setBackground(Ui.gradient(Ui.BLUE, Color.rgb(32,95,190), Color.TRANSPARENT, 16, this));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-1, Ui.dp(this, 56)); blp.bottomMargin = Ui.dp(this, 13); root.addView(begin, blp);
        begin.setOnClickListener(v -> beginStudy());
        TextView snooze = Ui.text(this, getString(R.string.snooze_five_minutes), 15, Ui.SILVER, true); snooze.setGravity(Gravity.CENTER);
        snooze.setBackground(Ui.shape(Ui.SURFACE2, Ui.LINE, 16, this)); root.addView(snooze, new LinearLayout.LayoutParams(-1, Ui.dp(this, 52)));
        snooze.setOnClickListener(v -> snooze());
        setContentView(root);
    }

    private void beginStudy() {
        AlarmSoundService.stop(this);
        cancelNotification();
        Intent i = new Intent(this, MainActivity.class).putExtra("start_card_id", cardId);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i); finish();
    }
    private void snooze() {
        AlarmSoundService.stop(this);
        StudyCard c = StudyStore.find(this, cardId);
        if (c != null) AlarmScheduler.scheduleSnooze(this, c);
        cancelNotification(); finish();
    }
    private void cancelNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(AlarmReceiver.notificationId(cardId));
    }
    @Override public void onBackPressed() { /* Keep the ringing screen actionable until snooze or start is chosen. */ }
}
