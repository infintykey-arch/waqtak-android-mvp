package com.wirdi.app;

import android.app.Activity;
import android.app.LocaleManager;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.LocaleList;
import android.text.TextUtils;
import android.view.View;

import java.util.Locale;

final class AppLocale {
    private static final String PREFS = "waqtak_language_preferences";
    private static final String LANGUAGE_TAG = "language_tag";

    private AppLocale() {}

    static Locale getLocale(Context context) {
        Configuration configuration = context.getResources().getConfiguration();
        if (Build.VERSION.SDK_INT >= 24 && !configuration.getLocales().isEmpty()) {
            return configuration.getLocales().get(0);
        }
        return configuration.locale != null ? configuration.locale : Locale.getDefault();
    }

    static Context wrap(Context base) {
        if (Build.VERSION.SDK_INT >= 33) return base;
        String languageTag = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(LANGUAGE_TAG, "");
        if (languageTag == null || languageTag.isEmpty()) return base;

        Locale locale = Locale.forLanguageTag(languageTag);
        Configuration configuration = new Configuration(base.getResources().getConfiguration());
        configuration.setLocale(locale);
        configuration.setLayoutDirection(locale);
        return base.createConfigurationContext(configuration);
    }

    static void setLanguage(Activity activity, String languageTag) {
        if (Build.VERSION.SDK_INT >= 33) {
            LocaleManager localeManager = activity.getSystemService(LocaleManager.class);
            if (localeManager != null) {
                localeManager.setApplicationLocales(LocaleList.forLanguageTags(languageTag));
            }
            return;
        }

        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(LANGUAGE_TAG, languageTag)
                .apply();
        activity.recreate();
    }

    static void applyLayoutDirection(Context context, View view) {
        view.setLayoutDirection(TextUtils.getLayoutDirectionFromLocale(getLocale(context)));
    }
}
