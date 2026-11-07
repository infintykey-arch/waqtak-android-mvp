package com.wirdi.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

public class SplashActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Ui.BG); getWindow().setNavigationBarColor(Ui.BG);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER); root.setBackgroundColor(Ui.BG);
        ImageView logo = new ImageView(this); logo.setImageResource(com.wirdi.app.R.drawable.wirdi_splash);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 250), Ui.dp(this, 250));
        root.addView(logo, lp);
        android.widget.TextView title = Ui.text(this, "وقتك", 30, Ui.WHITE, true); title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, Ui.dp(this, 48));
        tp.topMargin = Ui.dp(this, 10); root.addView(title, tp);
        android.widget.TextView sub = Ui.text(this, "نظّم وقت المذاكرة", 15, Ui.SILVER, false); sub.setGravity(Gravity.CENTER);
        root.addView(sub, new LinearLayout.LayoutParams(-1, Ui.dp(this, 34)));
        setContentView(root);
        root.setAlpha(0f); root.animate().alpha(1f).setDuration(250).start();
        root.postDelayed(() -> { startActivity(new Intent(this, MainActivity.class)); finish(); }, 350);
    }
}
