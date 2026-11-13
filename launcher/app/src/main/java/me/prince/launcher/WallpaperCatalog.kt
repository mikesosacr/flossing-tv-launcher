package me.prince.launcher

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.IOException

data class WallpaperTheme(
    val id: String,
    val titleRes: Int,
    val query: String,
)

data class WallpaperResult(
    val title: String,
    val creator: String,
    val imageUrl: String,
    val thumbnailUrl: String,
    val sourceUrl: String,
    val license: String,
    val licenseUrl: String,
)

private const val API = "https://api.openverse.org/v1/images/"
private const val MAX_WALLPAPER_BYTES = 16 * 1024 * 1024
private const val WALLPAPER_FILE = "user-wallpaper"
private const val WALLPAPER_CREDIT = "wallpaper-credit"
private const val WALLPAPER_SOURCE = "wallpaper-source"

val WALLPAPER_THEMES = listOf(
    WallpaperTheme("nature", R.string.theme_nature, "cinematic nature landscape"),
    WallpaperTheme("coast", R.string.theme_coast, "ocean coast landscape"),
    WallpaperTheme("city", R.string.theme_city, "city skyline night"),
    WallpaperTheme("space", R.string.theme_space, "space nebula stars"),
    WallpaperTheme("architecture", R.string.theme_architecture, "modern architecture"),
)

fun searchWallpapers(theme: WallpaperTheme): List<WallpaperResult> {
    val query = java.net.URLEncoder.encode(theme.query, Charsets.UTF_8.name())
    val response = JSONObject(
        httpGet("$API?q=$query&license_type=commercial&mature=false&aspect_ratio=wide&page_size=8")
    )
    val results = response.optJSONArray("results") ?: return emptyList()
    return (0 until results.length()).mapNotNull { index ->
        val item = results.optJSONObject(index) ?: return@mapNotNull null
        val url = item.optString("url")
        val thumbnail = item.optString("thumbnail")
        val source = item.optString("foreign_landing_url")
        if (!url.startsWith("https://") || !thumbnail.startsWith("https://") || !source.startsWith("https://")) {
            return@mapNotNull null
        }
        WallpaperResult(
            title = item.optString("title").ifBlank { theme.query },
            creator = item.optString("creator").ifBlank { "Unknown creator" },
            imageUrl = url,
            thumbnailUrl = thumbnail,
            sourceUrl = source,
            license = item.optString("license").ifBlank { "Creative Commons" } +
                " " + item.optString("license_version"),
            licenseUrl = item.optString("license_url"),
        )
    }
}

fun downloadWallpaper(context: Context, result: WallpaperResult) {
    val connection = java.net.URL(result.imageUrl).openConnection() as java.net.HttpURLConnection
    connection.connectTimeout = 8_000
    connection.readTimeout = 15_000
    try {
        val bytes = connection.inputStream.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > MAX_WALLPAPER_BYTES) throw IOException("Wallpaper image exceeds size limit")
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth < 1280 || bounds.outHeight < 600) {
            throw IOException("Wallpaper image is too small")
        }
        if (bounds.outWidth > 20_000 || bounds.outHeight > 20_000) {
            throw IOException("Wallpaper dimensions exceed limit")
        }
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > 2560 || bounds.outHeight / sampleSize > 1440) {
            sampleSize *= 2
        }
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            android.graphics.BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
            },
        )
            ?: throw IOException("Wallpaper image could not be decoded")
        try {
            val file = File(context.filesDir, WALLPAPER_FILE)
            val temporary = File(context.filesDir, "$WALLPAPER_FILE.tmp")
            temporary.outputStream().use { output ->
                if (!bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, output)) {
                    throw IOException("Wallpaper image could not be saved")
                }
            }
            java.nio.file.Files.move(
                temporary.toPath(),
                file.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            )
            context.getSharedPreferences("launcher", Context.MODE_PRIVATE).edit()
                .putString(WALLPAPER_CREDIT, "${result.title} - ${result.creator} (${result.license}: ${result.licenseUrl})")
                .putString(WALLPAPER_SOURCE, result.sourceUrl)
                .apply()
        } finally {
            bitmap.recycle()
        }
    } finally {
        connection.disconnect()
    }
}

fun userWallpaperFile(context: Context): File? =
    File(context.filesDir, WALLPAPER_FILE).takeIf { it.isFile && it.length() > 0L }

fun wallpaperCredit(context: Context): String? =
    context.getSharedPreferences("launcher", Context.MODE_PRIVATE).getString(WALLPAPER_CREDIT, null)

fun wallpaperSource(context: Context): String? =
    context.getSharedPreferences("launcher", Context.MODE_PRIVATE).getString(WALLPAPER_SOURCE, null)

fun clearUserWallpaper(context: Context) {
    userWallpaperFile(context)?.let { file ->
        if (!file.delete()) throw IOException("Selected wallpaper could not be removed")
    }
    context.getSharedPreferences("launcher", Context.MODE_PRIVATE).edit()
        .remove(WALLPAPER_CREDIT)
        .remove(WALLPAPER_SOURCE)
        .apply()
}
