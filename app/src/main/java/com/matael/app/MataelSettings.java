package com.matael.app;

import android.content.Context;
import android.content.SharedPreferences;

public final class MataelSettings {

private static final String PREFS = "matael_settings";

public static final String THEME_SYSTEM = "system";
public static final String THEME_LIGHT = "light";
public static final String THEME_DARK = "dark";

public static final String VIEW_CARDS = "cards";
public static final String VIEW_LIST = "list";

public static final String SORT_UPDATED = "updated";
public static final String SORT_CREATED = "created";
public static final String SORT_TITLE = "title";
public static final String SORT_MANUAL = "manual";

public static final String START_NOTES = "notes";
public static final String START_MOMENTS = "moments";

private final SharedPreferences preferences;

public MataelSettings(Context context) {
    preferences = context.getApplicationContext()
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
}

public String getTheme() {
    return preferences.getString("theme", THEME_SYSTEM);
}

public boolean setTheme(String theme) {
    if (!THEME_SYSTEM.equals(theme)
            && !THEME_LIGHT.equals(theme)
            && !THEME_DARK.equals(theme)) {
        return false;
    }

    return preferences.edit().putString("theme", theme).commit();
}

public int getAccentColor() {
    return preferences.getInt("accent_color", 0xFF6488FF);
}

public boolean setAccentColor(int color) {
    return preferences.edit()
            .putInt("accent_color", color).commit();
}

public long getAutoLockMillis() {
    return preferences.getLong("auto_lock_ms", 60_000L);
}

public boolean setAutoLockMillis(long millis) {
    if (millis != 0L
            && millis != 30_000L
            && millis != 60_000L
            && millis != 300_000L
            && millis != 600_000L) {
        return false;
    }

    return preferences.edit()
            .putLong("auto_lock_ms", millis).commit();
}

public boolean isLockOnBackground() {
    return preferences.getBoolean("lock_on_background", true);
}

public boolean setLockOnBackground(boolean enabled) {
    return preferences.edit()
            .putBoolean("lock_on_background", enabled)
            .commit();
}

public boolean isGlassEnabled() {
    return preferences.getBoolean("glass_enabled", true);
}

public boolean setGlassEnabled(boolean enabled) {
    return preferences.edit().putBoolean("glass_enabled", enabled).commit();
}

public boolean isReducedMotion() {
    return preferences.getBoolean("reduced_motion", false);
}

public boolean setReducedMotion(boolean enabled) {
    return preferences.edit().putBoolean("reduced_motion", enabled).commit();
}

public String getNotesView() {
    return preferences.getString("notes_view", VIEW_CARDS);
}

public boolean setNotesView(String view) {
    if (!VIEW_CARDS.equals(view) && !VIEW_LIST.equals(view)) {
        return false;
    }

    return preferences.edit().putString("notes_view", view).commit();
}

public String getSortOrder() {
    return preferences.getString("sort_order", SORT_UPDATED);
}

public boolean setSortOrder(String order) {
    if (!SORT_UPDATED.equals(order)
            && !SORT_CREATED.equals(order)
            && !SORT_TITLE.equals(order)
            && !SORT_MANUAL.equals(order)) {
        return false;
    }

    return preferences.edit().putString("sort_order", order).commit();
}

public String getStartScreen() {
    return preferences.getString("start_screen", START_NOTES);
}

public boolean setStartScreen(String screen) {
    if (!START_NOTES.equals(screen)
            && !START_MOMENTS.equals(screen)) {
        return false;
    }

    return preferences.edit().putString("start_screen", screen)
            .commit();
}

public long getLastBackupTime() {
    return preferences.getLong("last_backup_time", 0L);
}

public boolean setLastBackupTime(long time) {
    return preferences.edit()
            .putLong("last_backup_time", time).commit();
}

}