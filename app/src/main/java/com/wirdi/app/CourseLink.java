package com.wirdi.app;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

public final class CourseLink {
    private CourseLink() { }

    public static boolean isValidHttpUrl(String value) {
        if (value == null || value.isEmpty() || !value.equals(value.trim())) return false;
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) return false;
        }
        Uri uri = Uri.parse(value);
        String scheme = uri.getScheme();
        String host = uri.getHost();
        return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                && host != null && !host.isEmpty();
    }

    public static void open(Context context, String value) {
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(value)));
        } catch (ActivityNotFoundException error) {
            Toast.makeText(context, R.string.resource_link_open_error, Toast.LENGTH_LONG).show();
        }
    }
}
