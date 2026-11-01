package com.p3win.core.prefs;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

/**
 * Настройки P3WIN.
 *
 * <p>Используется тот же {@code SharedPreferences}, что и у ядра
 * ({@code PreferenceManager.getDefaultSharedPreferences}), но все ключи
 * имеют префикс {@code p3_} — так настройки оболочки и настройки ядра
 * физически не пересекаются, а пользователь видит один экран настроек.
 */
public final class P3Prefs {

    private static SharedPreferences prefs;

    public static final String KEY_ACCENT = "p3_accent_color";
    public static final String KEY_THEME = "p3_theme";
    public static final String KEY_CARD_RADIUS = "p3_card_radius";
    public static final String KEY_GRID_COLUMNS = "p3_grid_columns";
    public static final String KEY_SHOW_CLOCK = "p3_show_clock";
    public static final String KEY_ENGINE_HUD = "p3_engine_hud";
    public static final String KEY_ENGINE_HUD_POSITION = "p3_engine_hud_position";
    public static final String KEY_ENGINE_SAMPLE_MS = "p3_engine_sample_ms";
    public static final String KEY_ENGINE_TEMP = "p3_engine_temperature";
    public static final String KEY_CONTROL_CENTER_GESTURE = "p3_control_center_gesture";
    public static final String KEY_KEEP_SESSIONS_DB = "p3_keep_sessions";
    public static final String KEY_DEFAULT_CONTAINER_PRESET = "p3_default_container_preset";
    public static final String KEY_SAFE_MODE = "p3_safe_mode";
    public static final String KEY_FIRST_RUN_DONE = "p3_first_run_done";
    public static final String KEY_LAST_TAB = "p3_last_tab";

    public static final int THEME_DARK = 0;
    public static final int THEME_AMOLED = 1;
    public static final int THEME_LIGHT = 2;

    public static final int ACCENT_P3_CYAN = 0xFF00E5FF;
    public static final int ACCENT_P3_MAGENTA = 0xFFFF2D95;
    public static final int ACCENT_P3_LIME = 0xFFB2FF59;
    public static final int ACCENT_P3_AMBER = 0xFFFFC400;
    public static final int ACCENT_P3_VIOLET = 0xFF9D7BFF;

    public static final int DEFAULT_GRID_COLUMNS = 3;
    public static final int DEFAULT_SAMPLE_MS = 500;

    public static final int HUD_TOP_LEFT = 0;
    public static final int HUD_TOP_RIGHT = 1;
    public static final int HUD_BOTTOM_LEFT = 2;
    public static final int HUD_BOTTOM_RIGHT = 3;

    private P3Prefs() {
    }

    public static void init(Context context) {
        prefs = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
    }

    public static SharedPreferences raw() {
        return prefs;
    }

    // --- Оформление ------------------------------------------------------

    public static int accentColor() {
        return prefs.getInt(KEY_ACCENT, ACCENT_P3_CYAN);
    }

    public static void setAccentColor(int color) {
        prefs.edit().putInt(KEY_ACCENT, color).apply();
    }

    public static int theme() {
        return prefs.getInt(KEY_THEME, THEME_DARK);
    }

    public static void setTheme(int theme) {
        prefs.edit().putInt(KEY_THEME, theme).apply();
    }

    public static int cardCornerRadiusPx(Context context) {
        int dp = prefs.getInt(KEY_CARD_RADIUS, 14);
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }

    public static int gridColumns() {
        return prefs.getInt(KEY_GRID_COLUMNS, DEFAULT_GRID_COLUMNS);
    }

    public static void setGridColumns(int columns) {
        prefs.edit().putInt(KEY_GRID_COLUMNS, Math.max(2, Math.min(6, columns))).apply();
    }

    public static boolean showClock() {
        return prefs.getBoolean(KEY_SHOW_CLOCK, true);
    }

    public static void setShowClock(boolean show) {
        prefs.edit().putBoolean(KEY_SHOW_CLOCK, show).apply();
    }

    // --- P3 Engine -------------------------------------------------------

    public static boolean engineHudEnabled() {
        return prefs.getBoolean(KEY_ENGINE_HUD, true);
    }

    public static void setEngineHudEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ENGINE_HUD, enabled).apply();
    }

    public static int engineHudPosition() {
        return prefs.getInt(KEY_ENGINE_HUD_POSITION, HUD_TOP_RIGHT);
    }

    public static void setEngineHudPosition(int position) {
        prefs.edit().putInt(KEY_ENGINE_HUD_POSITION, position).apply();
    }

    public static int engineSampleMs() {
        return prefs.getInt(KEY_ENGINE_SAMPLE_MS, DEFAULT_SAMPLE_MS);
    }

    public static void setEngineSampleMs(int ms) {
        prefs.edit().putInt(KEY_ENGINE_SAMPLE_MS, Math.max(200, ms)).apply();
    }

    public static boolean engineTemperatureEnabled() {
        return prefs.getBoolean(KEY_ENGINE_TEMP, true);
    }

    public static void setEngineTemperatureEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ENGINE_TEMP, enabled).apply();
    }

    public static int controlCenterGesture() {
        return prefs.getInt(KEY_CONTROL_CENTER_GESTURE, 1);
    }

    // --- Прочее ----------------------------------------------------------

    public static boolean keepSessions() {
        return prefs.getBoolean(KEY_KEEP_SESSIONS_DB, true);
    }

    public static String defaultContainerPreset() {
        return prefs.getString(KEY_DEFAULT_CONTAINER_PRESET, "balanced");
    }

    public static void setDefaultContainerPreset(String presetId) {
        prefs.edit().putString(KEY_DEFAULT_CONTAINER_PRESET, presetId).apply();
    }

    public static boolean firstRunDone() {
        return prefs.getBoolean(KEY_FIRST_RUN_DONE, false);
    }

    public static void setFirstRunDone(boolean done) {
        prefs.edit().putBoolean(KEY_FIRST_RUN_DONE, done).apply();
    }

    public static int lastTab() {
        return prefs.getInt(KEY_LAST_TAB, 0);
    }

    public static void setLastTab(int tab) {
        prefs.edit().putInt(KEY_LAST_TAB, tab).apply();
    }

    /**
     * Безопасный режим: следующий запуск без GPU-оверрайдов.
     * Включается автоматически после серии падений (см. CrashGuard).
     */
    public static boolean safeMode() {
        return prefs.getBoolean(KEY_SAFE_MODE, false);
    }

    public static void setSafeMode(boolean enabled) {
        prefs.edit().putBoolean(KEY_SAFE_MODE, enabled).apply();
    }
}
