package me.prince.launcher

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

data class CustomShelf(
    val id: String,
    val name: String,
    val packages: List<String>,
)

private const val SHELVES_PREFS = "launcher"
private const val KEY_CUSTOM_SHELVES = "custom_shelves"
private const val TAG = "CustomShelves"

fun loadCustomShelves(context: Context): List<CustomShelf> {
    val json = context.getSharedPreferences(SHELVES_PREFS, Context.MODE_PRIVATE)
        .getString(KEY_CUSTOM_SHELVES, null) ?: return emptyList()
    return try {
        val rows = JSONArray(json)
        (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            CustomShelf(
                id = row.getString("id"),
                name = row.getString("name"),
                packages = row.getJSONArray("packages").let { packages ->
                    (0 until packages.length()).map(packages::getString)
                },
            )
        }
    } catch (e: JSONException) {
        Log.w(TAG, "Ignoring malformed saved app rows", e)
        emptyList()
    }
}

fun saveCustomShelves(context: Context, shelves: List<CustomShelf>) {
    val rows = JSONArray()
    shelves.forEach { shelf ->
        rows.put(JSONObject()
            .put("id", shelf.id)
            .put("name", shelf.name)
            .put("packages", JSONArray(shelf.packages)))
    }
    context.getSharedPreferences(SHELVES_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_CUSTOM_SHELVES, rows.toString())
        .apply()
}
