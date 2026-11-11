package com.wirdi.app;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.text.TextUtils;
import android.view.View;

import java.util.Locale;

final class AppLocale {
    private AppLocale() {}

    static Locale getLocale(Context context) {
        Configuration configuration = context.getResources().getConfiguration();
        if (Build.VERSION.SDK_INT >= 24 && !configuration.getLocales().isEmpty()) {
            return configuration.getLocales().get(0);
        }
        return configuration.locale != null ? configuration.locale : Locale.getDefault();
    }

    static void applyLayoutDirection(Context context, View view) {
        view.setLayoutDirection(TextUtils.getLayoutDirectionFromLocale(getLocale(context)));
    }
}
