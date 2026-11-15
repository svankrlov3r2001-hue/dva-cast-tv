package com.dangvietanh.cast;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {

    private static final String PREF_NAME = "dva_cast_prefs";
    private static final String KEY_AUTO_START = "auto_start_on_boot";
    private static final String KEY_UPDATE_HISTORY_JSON = "update_history_json";

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

    public static String getCachedUpdateHistoryJson(Context context) {
        return getPrefs(context).getString(KEY_UPDATE_HISTORY_JSON, "");
    }

    public static void setCachedUpdateHistoryJson(Context context, String json) {
        getPrefs(context).edit().putString(KEY_UPDATE_HISTORY_JSON, json != null ? json : "").apply();
    }
}
