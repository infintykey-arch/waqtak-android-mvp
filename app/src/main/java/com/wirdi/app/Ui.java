package com.wirdi.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public final class Ui {
    public static final int BG = Color.rgb(7, 10, 16);
    public static final int SURFACE = Color.rgb(17, 23, 33);
    public static final int SURFACE2 = Color.rgb(26, 35, 48);
    public static final int LINE = Color.rgb(39, 53, 70);
    public static final int BLUE = Color.rgb(62, 142, 255);
    public static final int SILVER = Color.rgb(202, 213, 225);
    public static final int MUTED = Color.rgb(148, 163, 181);
    public static final int WHITE = Color.rgb(240, 245, 250);
    public static final int GREEN = Color.rgb(82, 203, 166);
    public static final int RED = Color.rgb(239, 104, 110);

    private Ui() {}
    public static int dp(Context c, float n) { return Math.round(n * c.getResources().getDisplayMetrics().density); }

    public static TextView text(Context c, String value, float size, int color, boolean bold) {
        TextView v = new TextView(c);
        v.setText(value); v.setTextSize(size); v.setTextColor(color);
        v.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        v.setGravity(Gravity.CENTER_VERTICAL); v.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        v.setIncludeFontPadding(true); return v;
    }

    public static GradientDrawable shape(int color, int strokeColor, float radiusDp, Context c) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(c, radiusDp));
        if (strokeColor != Color.TRANSPARENT) d.setStroke(dp(c, 1), strokeColor);
        return d;
    }

    public static GradientDrawable gradient(int top, int bottom, int stroke, float radius, Context c) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{top, bottom});
        d.setCornerRadius(dp(c, radius)); if (stroke != Color.TRANSPARENT) d.setStroke(dp(c, 1), stroke); return d;
    }

    public static Button button(Context c, String label, int fill, int foreground) {
        Button b = new Button(c); b.setText(label); b.setAllCaps(false); b.setTextSize(14);
        b.setTextColor(foreground); b.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        b.setBackground(shape(fill, Color.TRANSPARENT, 14, c));
        b.setPadding(dp(c, 12), 0, dp(c, 12), 0); b.setMinHeight(dp(c, 46));
        b.setStateListAnimator(null); b.setElevation(dp(c, 1));
        return b;
    }

    public static TextView badge(Context c, String label) {
        TextView t = text(c, label, 11, MUTED, false);
        t.setGravity(Gravity.CENTER); t.setPadding(dp(c, 8), dp(c, 4), dp(c, 8), dp(c, 4));
        t.setBackground(shape(SURFACE2, LINE, 30, c)); return t;
    }
}
