package me.prince.launcher

import android.content.Context

data class LauncherOptions(
    val showWeather: Boolean = true,
    val showRecentApps: Boolean = true,
    val showRecommendations: Boolean = true,
    val oledCare: Boolean = true,
    val language: String = "es",
)

private const val OPTIONS_PREFS = "launcher"
private const val KEY_SHOW_WEATHER = "show_weather"
private const val KEY_SHOW_RECENT_APPS = "show_recent_apps"
private const val KEY_SHOW_RECOMMENDATIONS = "show_recommendations"
private const val KEY_OLED_CARE = "oled_care"
private const val KEY_LANGUAGE = "language"

fun loadLauncherOptions(context: Context): LauncherOptions {
    val prefs = context.getSharedPreferences(OPTIONS_PREFS, Context.MODE_PRIVATE)
    return LauncherOptions(
        showWeather = prefs.getBoolean(KEY_SHOW_WEATHER, true),
        showRecentApps = prefs.getBoolean(KEY_SHOW_RECENT_APPS, true),
        showRecommendations = prefs.getBoolean(KEY_SHOW_RECOMMENDATIONS, true),
        oledCare = prefs.getBoolean(KEY_OLED_CARE, true),
        language = prefs.getString(KEY_LANGUAGE, "es") ?: "es",
    )
}

fun saveLauncherOptions(context: Context, options: LauncherOptions) {
    context.getSharedPreferences(OPTIONS_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_SHOW_WEATHER, options.showWeather)
        .putBoolean(KEY_SHOW_RECENT_APPS, options.showRecentApps)
        .putBoolean(KEY_SHOW_RECOMMENDATIONS, options.showRecommendations)
        .putBoolean(KEY_OLED_CARE, options.oledCare)
        .putString(KEY_LANGUAGE, options.language)
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
