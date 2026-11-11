package com.wirdi.app;

import android.app.Activity;
import android.content.Context;

public abstract class LocalizedActivity extends Activity {
    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(AppLocale.wrap(newBase));
    }
}
