package me.prince.launcher

import android.content.Context

data class LauncherOptions(
    val showWeather: Boolean = true,
    val showRecentApps: Boolean = true,
    val recentLimit: Int = 8,
    val excludedRecentApps: Set<String> = emptySet(),
    val prioritizedRecentApps: List<String> = emptyList(),
    val showRecommendations: Boolean = true,
    val oledCare: Boolean = true,
    val language: String = "es",
    val layout: String = "tvos",
    val theme: String = "midnight",
    val ambientWallpaper: Boolean = false,
)

private const val OPTIONS_PREFS = "launcher"
private const val KEY_SHOW_WEATHER = "show_weather"
private const val KEY_SHOW_RECENT_APPS = "show_recent_apps"
private const val KEY_RECENT_LIMIT = "recent_limit"
private const val KEY_EXCLUDED_RECENT_APPS = "excluded_recent_apps"
private const val KEY_PRIORITIZED_RECENT_APPS = "prioritized_recent_apps"
private const val KEY_SHOW_RECOMMENDATIONS = "show_recommendations"
private const val KEY_OLED_CARE = "oled_care"
private const val KEY_LANGUAGE = "language"
private const val KEY_LAYOUT = "layout"
private const val KEY_THEME = "theme"
private const val KEY_AMBIENT_WALLPAPER = "ambient_wallpaper"

fun loadLauncherOptions(context: Context): LauncherOptions {
    val prefs = context.getSharedPreferences(OPTIONS_PREFS, Context.MODE_PRIVATE)
    return LauncherOptions(
        showWeather = prefs.getBoolean(KEY_SHOW_WEATHER, true),
        showRecentApps = prefs.getBoolean(KEY_SHOW_RECENT_APPS, true),
        recentLimit = prefs.getInt(KEY_RECENT_LIMIT, 8).coerceIn(1, 20),
        excludedRecentApps = prefs.getStringSet(KEY_EXCLUDED_RECENT_APPS, emptySet()).orEmpty(),
        prioritizedRecentApps = prefs.getString(KEY_PRIORITIZED_RECENT_APPS, null)
            ?.split(',')?.filter { it.isNotBlank() }.orEmpty(),
        showRecommendations = prefs.getBoolean(KEY_SHOW_RECOMMENDATIONS, true),
        oledCare = prefs.getBoolean(KEY_OLED_CARE, true),
        language = prefs.getString(KEY_LANGUAGE, "es") ?: "es",
        layout = prefs.getString(KEY_LAYOUT, "tvos") ?: "tvos",
        theme = prefs.getString(KEY_THEME, "midnight") ?: "midnight",
        ambientWallpaper = prefs.getBoolean(KEY_AMBIENT_WALLPAPER, false),
    )
}

fun saveLauncherOptions(context: Context, options: LauncherOptions) {
    context.getSharedPreferences(OPTIONS_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_SHOW_WEATHER, options.showWeather)
        .putBoolean(KEY_SHOW_RECENT_APPS, options.showRecentApps)
        .putInt(KEY_RECENT_LIMIT, options.recentLimit.coerceIn(1, 20))
        .putStringSet(KEY_EXCLUDED_RECENT_APPS, options.excludedRecentApps.toSet())
        .putString(KEY_PRIORITIZED_RECENT_APPS, options.prioritizedRecentApps.joinToString(","))
        .putBoolean(KEY_SHOW_RECOMMENDATIONS, options.showRecommendations)
        .putBoolean(KEY_OLED_CARE, options.oledCare)
        .putString(KEY_LANGUAGE, options.language)
        .putString(KEY_LAYOUT, options.layout)
        .putString(KEY_THEME, options.theme)
        .putBoolean(KEY_AMBIENT_WALLPAPER, options.ambientWallpaper)
        .apply()
}

private const val TMDB_PREFS = "tmdb"
private const val KEY_TMDB_KEY = "api_key"

fun loadTmdbKey(context: Context): String =
    context.getSharedPreferences(TMDB_PREFS, Context.MODE_PRIVATE)
        .getString(KEY_TMDB_KEY, null)
        ?: BuildConfig.TMDB_KEY

fun saveTmdbKey(context: Context, key: String) {
    context.getSharedPreferences(TMDB_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_TMDB_KEY, key.trim())
        .apply()
}
