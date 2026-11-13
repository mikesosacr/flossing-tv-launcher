package me.prince.launcher

import android.content.Context
import android.location.LocationManager
import android.util.Log
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import kotlin.math.roundToInt

private const val TAG = "Weather"

data class Weather(val temperature: Int, val summary: String)

// Uses the TV's network location (coarse location is already granted for the Wi-Fi name) and Open-Meteo, which needs no key
fun fetchWeather(context: Context): Weather? {
    val location = try {
        context.getSystemService(LocationManager::class.java).getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
    } catch (e: SecurityException) {
        Log.w(TAG, "No location permission", e)
        null
    } ?: return null
    val url = String.format(
        Locale.US,
        "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f&current=temperature_2m,weather_code",
        location.latitude,
        location.longitude,
    )
    return try {
        val current = JSONObject(httpGet(url)).getJSONObject("current")
        Weather(current.getDouble("temperature_2m").roundToInt(), describe(context, current.getInt("weather_code")))
    } catch (e: IOException) {
        Log.w(TAG, "Weather fetch failed", e)
        null
    } catch (e: JSONException) {
        Log.w(TAG, "Unexpected weather response", e)
        null
    }
}

// WMO weather codes
private fun describe(context: Context, code: Int): String = when (code) {
    0 -> context.getString(R.string.weather_clear)
    1, 2 -> context.getString(R.string.weather_partly_cloudy)
    3 -> context.getString(R.string.weather_cloudy)
    45, 48 -> context.getString(R.string.weather_fog)
    in 51..57 -> context.getString(R.string.weather_drizzle)
    in 61..67, in 80..82 -> context.getString(R.string.weather_rain)
    in 71..77, 85, 86 -> context.getString(R.string.weather_snow)
    in 95..99 -> context.getString(R.string.weather_thunderstorm)
    else -> ""
}
