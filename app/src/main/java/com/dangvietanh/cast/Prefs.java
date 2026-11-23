package com.dangvietanh.cast;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {

    private static final String PREF_NAME = "dva_cast_prefs";
    private static final String KEY_AUTO_START = "auto_start_on_boot";
    private static final String KEY_UPDATE_HISTORY_JSON = "update_history_json";

    private static final String KEY_AUTH_TOKEN = "auth_token";
    private static final String KEY_USER_JSON = "auth_user_json";
    private static final String KEY_REACTION_PREFIX = "movie_reaction_";

    // Player General Settings
    private static final String KEY_AUTO_PLAY_NEXT = "player_auto_next";
    private static final String KEY_AUTO_SKIP_INTRO = "player_auto_skip_intro";
    private static final String KEY_AUTO_OPTIMAL_SERVER = "player_auto_optimal_server";
    private static final String KEY_AUTO_DUB_NEXT = "player_auto_dub_next";
    private static final String KEY_SEAMLESS_PRELOAD = "player_seamless_preload";
    private static final String KEY_TRANSITION_EFFECT = "player_transition_effect";
    private static final String KEY_NEXT_EP_DELAY = "player_next_ep_delay";
    private static final String KEY_VIDEO_FILL_COVER = "player_video_fill_cover";
    private static final String KEY_CONTRAST_LEVEL = "player_contrast_level";
    private static final String KEY_PLAYBACK_RATE = "player_playback_rate";

    // Subtitle & AI Dubbing Settings
    private static final String KEY_SUB_SHOW_VI = "sub_show_vi";
    private static final String KEY_SUB_SHOW_EN = "sub_show_en";
    private static final String KEY_SUB_REVERSE = "sub_reverse";
    private static final String KEY_SUB_FONT = "sub_font";
    private static final String KEY_SUB_COLOR = "sub_color";
    private static final String KEY_SUB_AUTO_LAYOUT = "sub_auto_layout";
    private static final String KEY_SUB_FONT_STEP = "sub_font_step";
    private static final String KEY_SUB_POS_PCT = "sub_pos_pct";
    private static final String KEY_SUB_OUTLINE = "sub_outline";
    private static final String KEY_SUB_DELAY_SEC = "sub_delay_sec";
    private static final String KEY_SUB_EXTEND_SEC = "sub_extend_sec";
    private static final String KEY_SUB_TRANSLATE_LANG = "sub_translate_lang";
    private static final String KEY_SUB_AI_VOICE_ENABLED = "sub_ai_voice_enabled";
    private static final String KEY_SUB_AI_VOICE_MODE = "sub_ai_voice_mode";
    private static final String KEY_SUB_AI_VOICE_VOLUME = "sub_ai_voice_volume";

    private Prefs() {
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // ---- Boot & OTA Update ----

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

    // ---- Authentication ----

    public static String getAuthToken(Context context) {
        return getPrefs(context).getString(KEY_AUTH_TOKEN, "");
    }

    public static void setAuthToken(Context context, String token) {
        getPrefs(context).edit().putString(KEY_AUTH_TOKEN, token != null ? token : "").apply();
    }

    public static String getUserJson(Context context) {
        return getPrefs(context).getString(KEY_USER_JSON, "");
    }

    public static void setUserJson(Context context, String json) {
        getPrefs(context).edit().putString(KEY_USER_JSON, json != null ? json : "").apply();
    }

    public static void clearAuth(Context context) {
        getPrefs(context).edit()
                .remove(KEY_AUTH_TOKEN)
                .remove(KEY_USER_JSON)
                .apply();
    }

    // ---- Movie Reactions (like / dislike) ----

    public static String getReaction(Context context, String movieKey) {
        if (movieKey == null || movieKey.trim().isEmpty()) return "";
        return getPrefs(context).getString(KEY_REACTION_PREFIX + movieKey.trim(), "");
    }

    public static void setReaction(Context context, String movieKey, String val) {
        if (movieKey == null || movieKey.trim().isEmpty()) return;
        SharedPreferences.Editor ed = getPrefs(context).edit();
        if (val == null || val.trim().isEmpty()) {
            ed.remove(KEY_REACTION_PREFIX + movieKey.trim());
        } else {
            ed.putString(KEY_REACTION_PREFIX + movieKey.trim(), val.trim());
        }
        ed.apply();
    }

    // ---- Player Settings ----

    public static boolean isAutoPlayNext(Context context) {
        return getPrefs(context).getBoolean(KEY_AUTO_PLAY_NEXT, true);
    }

    public static void setAutoPlayNext(Context context, boolean v) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_PLAY_NEXT, v).apply();
    }

    public static boolean isAutoSkipIntro(Context context) {
        return getPrefs(context).getBoolean(KEY_AUTO_SKIP_INTRO, false);
    }

    public static void setAutoSkipIntro(Context context, boolean v) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_SKIP_INTRO, v).apply();
    }

    public static boolean isAutoOptimalServer(Context context) {
        return getPrefs(context).getBoolean(KEY_AUTO_OPTIMAL_SERVER, true);
    }

    public static void setAutoOptimalServer(Context context, boolean v) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_OPTIMAL_SERVER, v).apply();
    }

    public static boolean isAutoDubNext(Context context) {
        return getPrefs(context).getBoolean(KEY_AUTO_DUB_NEXT, false);
    }

    public static void setAutoDubNext(Context context, boolean v) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_DUB_NEXT, v).apply();
    }

    public static boolean isSeamlessPreload(Context context) {
        return getPrefs(context).getBoolean(KEY_SEAMLESS_PRELOAD, true);
    }

    public static void setSeamlessPreload(Context context, boolean v) {
        getPrefs(context).edit().putBoolean(KEY_SEAMLESS_PRELOAD, v).apply();
    }

    public static boolean isTransitionEffect(Context context) {
        return getPrefs(context).getBoolean(KEY_TRANSITION_EFFECT, true);
    }

    public static void setTransitionEffect(Context context, boolean v) {
        getPrefs(context).edit().putBoolean(KEY_TRANSITION_EFFECT, v).apply();
    }

    public static int getNextEpisodeDelaySec(Context context) {
        return Math.max(0, Math.min(30, getPrefs(context).getInt(KEY_NEXT_EP_DELAY, 10)));
    }

    public static void setNextEpisodeDelaySec(Context context, int sec) {
        getPrefs(context).edit().putInt(KEY_NEXT_EP_DELAY, Math.max(0, Math.min(30, sec))).apply();
    }

    public static boolean isVideoFillCover(Context context) {
        return getPrefs(context).getBoolean(KEY_VIDEO_FILL_COVER, false);
    }

    public static void setVideoFillCover(Context context, boolean v) {
        getPrefs(context).edit().putBoolean(KEY_VIDEO_FILL_COVER, v).apply();
    }

    public static String getContrastLevel(Context context) {
        return getPrefs(context).getString(KEY_CONTRAST_LEVEL, "Mặc định");
    }

    public static void setContrastLevel(Context context, String level) {
        getPrefs(context).edit().putString(KEY_CONTRAST_LEVEL, level != null ? level : "Mặc định").apply();
    }

    public static float getPlaybackRate(Context context) {
        return getPrefs(context).getFloat(KEY_PLAYBACK_RATE, 1.0f);
    }

    public static void setPlaybackRate(Context context, float rate) {
        getPrefs(context).edit().putFloat(KEY_PLAYBACK_RATE, rate).apply();
    }

    // ---- Subtitle & AI Voice Dubbing ----

    public static boolean isSubShowVi(Context context) {
        return getPrefs(context).getBoolean(KEY_SUB_SHOW_VI, true);
    }

    public static void setSubShowVi(Context context, boolean v) {
        getPrefs(context).edit().putBoolean(KEY_SUB_SHOW_VI, v).apply();
    }

    public static boolean isSubShowEn(Context context) {
        return getPrefs(context).getBoolean(KEY_SUB_SHOW_EN, false);
    }

    public static void setSubShowEn(Context context, boolean v) {
        getPrefs(context).edit().putBoolean(KEY_SUB_SHOW_EN, v).apply();
    }

    public static boolean isSubReverse(Context context) {
        return getPrefs(context).getBoolean(KEY_SUB_REVERSE, false);
    }

    public static void setSubReverse(Context context, boolean v) {
        getPrefs(context).edit().putBoolean(KEY_SUB_REVERSE, v).apply();
    }

    public static String getSubFont(Context context) {
        return getPrefs(context).getString(KEY_SUB_FONT, "Arial,Helvetica,sans-serif");
    }

    public static void setSubFont(Context context, String font) {
        getPrefs(context).edit().putString(KEY_SUB_FONT, font != null ? font : "Arial,Helvetica,sans-serif").apply();
    }

    public static String getSubColor(Context context) {
        return getPrefs(context).getString(KEY_SUB_COLOR, "white");
    }

    public static void setSubColor(Context context, String color) {
        getPrefs(context).edit().putString(KEY_SUB_COLOR, color != null ? color : "white").apply();
    }

    public static boolean isSubAutoLayout(Context context) {
        return getPrefs(context).getBoolean(KEY_SUB_AUTO_LAYOUT, true);
    }

    public static void setSubAutoLayout(Context context, boolean v) {
        getPrefs(context).edit().putBoolean(KEY_SUB_AUTO_LAYOUT, v).apply();
    }

    public static int getSubFontStep(Context context) {
        return Math.max(1, Math.min(20, getPrefs(context).getInt(KEY_SUB_FONT_STEP, 9)));
    }

    public static void setSubFontStep(Context context, int step) {
        getPrefs(context).edit().putInt(KEY_SUB_FONT_STEP, Math.max(1, Math.min(20, step))).apply();
    }

    public static int getSubPositionPct(Context context) {
        return Math.max(-10, Math.min(40, getPrefs(context).getInt(KEY_SUB_POS_PCT, 10)));
    }

    public static void setSubPositionPct(Context context, int pct) {
        getPrefs(context).edit().putInt(KEY_SUB_POS_PCT, Math.max(-10, Math.min(40, pct))).apply();
    }

    public static String getSubOutline(Context context) {
        return getPrefs(context).getString(KEY_SUB_OUTLINE, "uniform");
    }

    public static void setSubOutline(Context context, String outline) {
        getPrefs(context).edit().putString(KEY_SUB_OUTLINE, outline != null ? outline : "uniform").apply();
    }

    public static float getSubDelaySec(Context context) {
        return getPrefs(context).getFloat(KEY_SUB_DELAY_SEC, 0.0f);
    }

    public static void setSubDelaySec(Context context, float sec) {
        getPrefs(context).edit().putFloat(KEY_SUB_DELAY_SEC, sec).apply();
    }

    public static float getSubExtendSec(Context context) {
        return getPrefs(context).getFloat(KEY_SUB_EXTEND_SEC, 0.0f);
    }

    public static void setSubExtendSec(Context context, float sec) {
        getPrefs(context).edit().putFloat(KEY_SUB_EXTEND_SEC, Math.max(0f, sec)).apply();
    }

    public static String getSubTranslateLang(Context context) {
        String v = getPrefs(context).getString(KEY_SUB_TRANSLATE_LANG, "");
        return "off".equalsIgnoreCase(v) ? "" : v;
    }

    public static void setSubTranslateLang(Context context, String lang) {
        String v = (lang == null || "off".equalsIgnoreCase(lang)) ? "" : lang;
        getPrefs(context).edit().putString(KEY_SUB_TRANSLATE_LANG, v).apply();
    }

    public static boolean isSubAiVoiceEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_SUB_AI_VOICE_ENABLED, false);
    }

    public static void setSubAiVoiceEnabled(Context context, boolean v) {
        getPrefs(context).edit().putBoolean(KEY_SUB_AI_VOICE_ENABLED, v).apply();
    }

    public static String getSubAiVoiceMode(Context context) {
        return getPrefs(context).getString(KEY_SUB_AI_VOICE_MODE, "adaptive");
    }

    public static void setSubAiVoiceMode(Context context, String mode) {
        getPrefs(context).edit().putString(KEY_SUB_AI_VOICE_MODE, mode != null ? mode : "adaptive").apply();
    }

    public static int getSubAiVoiceVolume(Context context) {
        return Math.max(0, Math.min(100, getPrefs(context).getInt(KEY_SUB_AI_VOICE_VOLUME, 100)));
    }

    public static void setSubAiVoiceVolume(Context context, int vol) {
        getPrefs(context).edit().putInt(KEY_SUB_AI_VOICE_VOLUME, Math.max(0, Math.min(100, vol))).apply();
    }

    public static void resetSubtitlePrefs(Context context) {
        getPrefs(context).edit()
                .putBoolean(KEY_SUB_SHOW_VI, true)
                .putBoolean(KEY_SUB_SHOW_EN, false)
                .putBoolean(KEY_SUB_REVERSE, false)
                .putString(KEY_SUB_FONT, "Arial,Helvetica,sans-serif")
                .putString(KEY_SUB_COLOR, "white")
                .putBoolean(KEY_SUB_AUTO_LAYOUT, true)
                .putInt(KEY_SUB_FONT_STEP, 9)
                .putInt(KEY_SUB_POS_PCT, 10)
                .putString(KEY_SUB_OUTLINE, "uniform")
                .putFloat(KEY_SUB_DELAY_SEC, 0.0f)
                .putFloat(KEY_SUB_EXTEND_SEC, 0.0f)
                .putString(KEY_SUB_TRANSLATE_LANG, "")
                .putBoolean(KEY_SUB_AI_VOICE_ENABLED, false)
                .putString(KEY_SUB_AI_VOICE_MODE, "adaptive")
                .putInt(KEY_SUB_AI_VOICE_VOLUME, 100)
                .apply();
    }
}
