package com.dangvietanh.cast;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {

    private static final String PREF_NAME = "dva_cast_prefs";
    private static final String KEY_AUTO_START = "auto_start_on_boot";

    private Prefs() {
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isAutoStartEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_AUTO_START, false);
    }

    public static void setAutoStartEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_START, enabled).apply();
    }
}
