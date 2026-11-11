package com.wirdi.app;

import android.app.Application;
import android.content.Context;

public class WaqtakApplication extends Application {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppLocale.wrap(base));
    }
}
