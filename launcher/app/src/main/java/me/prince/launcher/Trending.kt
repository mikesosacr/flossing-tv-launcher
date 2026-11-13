package me.prince.launcher

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.Locale

private const val TAG = "Trending"
private const val API = "https://api.themoviedb.org/3"
private const val IMAGES = "https://image.tmdb.org/t/p"
private const val MAX_TITLES = 8
private const val STREMIO = "com.stremio.one"

// Which country's streaming catalogue to match (tmdb.region in local.properties), else the TV's own country
private val REGION = BuildConfig.TMDB_REGION.ifBlank { Locale.getDefault().country.ifBlank { "US" } }.uppercase(Locale.US)
// all, movie or tv (tmdb.feed in local.properties)
private val FEED = BuildConfig.TMDB_FEED.ifBlank { "all" }

// TMDB watch-provider id → the Android TV app that streams it. The button opens the first installed one.
// Add your own: provider ids are listed at https://api.themoviedb.org/3/watch/providers/tv (needs your key).
private val PROVIDER_APPS = mapOf(
    8 to "com.netflix.ninja",                       // Netflix
    9 to "com.amazon.amazonvideo.livingroom",       // Amazon Prime Video
    119 to "com.amazon.amazonvideo.livingroom",     // Amazon Prime Video
    337 to "com.disney.disneyplus",                 // Disney+
    122 to "in.startv.hotstar",                     // Hotstar
    2336 to "in.startv.hotstar",                    // JioHotstar
    350 to "com.apple.atve.androidtv.appletv",      // Apple TV+
    15 to "com.hulu.livingroomplus",                // Hulu
    531 to "com.cbs.ott",                           // Paramount+
)

data class TrendingTitle(
    val title: String,
    val meta: String,
    val overview: String,
    // Kept compressed (~200KB); decoded on demand, since 8 decoded 1280px backdrops cost ~30MB
    val backdropJpeg: ByteArray,
    // Blurred copy for glass surfaces drawn over it
    val frosted: ImageBitmap,
    // Null when TMDB has no logo or it's too dark to read on the backdrop. Compressed like the backdrop.
    val logoPng: ByteArray?,
    val action: Intent?,
    val actionLabel: String?,
)

// This week's trending titles from TMDB. The home screen shows setup/retry guidance when this feed is unavailable.
// The key can come from local.properties, BuildConfig, or the launcher's on-device settings.
fun fetchTrending(
    context: Context,
    key: String = loadTmdbKey(context),
    language: String = "es-ES",
): List<TrendingTitle> {
    if (key.isBlank()) return emptyList()
    return try {
        val results = JSONObject(tmdbGet("/trending/$FEED/week", key, language)).getJSONArray("results")
        results.objects()
            .filter { it.optString("media_type") in setOf("movie", "tv") && !it.isNull("backdrop_path") }
            .take(MAX_TITLES)
            .mapNotNull { item ->
                // One bad title (missing image, odd JSON) shouldn't drop the whole row
                try {
                    toTitle(context, key, language, item)
                } catch (e: IOException) {
                    Log.w(TAG, "Skipping ${item.optInt("id")}", e)
                    null
                } catch (e: JSONException) {
                    Log.w(TAG, "Skipping ${item.optInt("id")}", e)
                    null
                }
            }
    } catch (e: IOException) {
        Log.w(TAG, "Trending fetch failed", e)
        emptyList()
    } catch (e: JSONException) {
        Log.w(TAG, "Unexpected TMDB response", e)
        emptyList()
    }
}

// [opens]: the button, when it's already known (the app whose top titles these are); else looked up per title
private fun toTitle(
    context: Context,
    key: String,
    language: String,
    item: JSONObject,
    opens: Pair<Intent?, String?>? = null,
    youtubeSearchIfUnavailable: Boolean = false,
): TrendingTitle {
    val type = item.getString("media_type")
    val append = if (opens == null) "external_ids,watch/providers,images" else "images"
    val details = JSONObject(tmdbGet(
        "/$type/${item.getInt("id")}?append_to_response=$append&include_image_language=en,null",
        key,
        language,
    ))
    val title = item.optString("title").ifBlank { item.optString("name") }
    val year = item.optString("release_date").ifBlank { item.optString("first_air_date") }.take(4)
    val genre = details.optJSONArray("genres")?.optJSONObject(0)?.optString("name").orEmpty()
    val rating = item.optDouble("vote_average", 0.0)
    val meta = listOf(
        context.getString(if (type == "tv") R.string.series else R.string.movie),
        year,
        genre,
        if (rating > 0) String.format(Locale.US, "★ %.1f", rating) else "",
    ).filter { it.isNotBlank() }.joinToString("  ·  ")

    val logoPath = details.optJSONObject("images")?.optJSONArray("logos")?.objects()
        ?.firstOrNull { it.optString("iso_639_1") == "en" }
        ?.optString("file_path")
    val logoPng = logoPath?.let { httpBytes("$IMAGES/w500$it") }?.takeIf { isLightEnough(decode(it)) }

    val (action, label) = opens ?: watchAction(context, type, details, title, youtubeSearchIfUnavailable)
    val backdropJpeg = httpBytes("$IMAGES/w1280${item.getString("backdrop_path")}")
    return TrendingTitle(
        title = title,
        meta = meta,
        overview = item.optString("overview"),
        backdropJpeg = backdropJpeg,
        frosted = frostedBackdrop(decode(backdropJpeg), BACKDROP_HEIGHT_FRACTION),
        logoPng = logoPng,
        action = action,
        actionLabel = label,
    )
}

// Prefer a matching in-region provider; the free feed may search YouTube when no provider app is identifiable.
private fun watchAction(
    context: Context,
    type: String,
    details: JSONObject,
    title: String,
    youtubeSearchIfUnavailable: Boolean,
): Pair<Intent?, String?> {
    val pm = context.packageManager
    val regionProviders = details.optJSONObject("watch/providers")?.optJSONObject("results")
        ?.optJSONObject(REGION)
    val providers = listOf("free", "ads", "flatrate")
        .flatMap { regionProviders?.optJSONArray(it)?.objects().orEmpty() }
    for (provider in providers) {
        val name = provider.optString("provider_name")
        val pkg = PROVIDER_APPS[provider.optInt("provider_id")]
        val launch = pkg?.let { pm.getLeanbackLaunchIntentForPackage(it) }
            ?: providerLaunchIntent(context, name)
        if (launch != null) {
            return launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) to context.getString(R.string.watch_on, name)
        }
    }
    if (youtubeSearchIfUnavailable) {
        youtubeSearchIntent(context, title)?.let { (intent, label) -> return intent to label }
    }
    val imdb = details.optJSONObject("external_ids")?.optString("imdb_id").orEmpty()
    if (imdb.isBlank() || pm.getLeanbackLaunchIntentForPackage(STREMIO) == null) return null to null
    val stremioType = if (type == "tv") "series" else "movie"
    return Intent(Intent.ACTION_VIEW, Uri.parse("stremio:///detail/$stremioType/$imdb"))
        .setPackage(STREMIO)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) to context.getString(R.string.open_in_stremio)
}

private fun providerLaunchIntent(context: Context, providerName: String): Intent? {
    val provider = providerName.normalizedServiceName()
    if (provider.length < 4) return null
    val pm = context.packageManager
    val leanback = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
    val app = pm.queryIntentActivities(leanback, 0)
        .distinctBy { it.activityInfo.packageName }
        .firstOrNull { info ->
            val label = info.loadLabel(pm).toString().normalizedServiceName()
            label.length >= 4 && (label == provider || label.startsWith(provider) || provider.startsWith(label))
        } ?: return null
    return pm.getLeanbackLaunchIntentForPackage(app.activityInfo.packageName)
}

private fun youtubeSearchIntent(context: Context, title: String): Pair<Intent, String>? {
    val pm = context.packageManager
    val leanback = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
    val apps = pm.queryIntentActivities(leanback, 0).distinctBy { it.activityInfo.packageName }
    val app = apps.firstOrNull {
        it.loadLabel(pm).toString().contains("YouTube", ignoreCase = true)
    } ?: apps.firstOrNull {
        val label = it.loadLabel(pm).toString()
        label.contains("Tizen", ignoreCase = true) && label.contains("Tube", ignoreCase = true)
    } ?: return null
    val packageName = app.activityInfo.packageName
    val webSearch = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(title)}"),
    ).setPackage(packageName)
    val search = Intent(Intent.ACTION_SEARCH)
        .setPackage(packageName)
        .putExtra(android.app.SearchManager.QUERY, title)
    val intent = when {
        webSearch.resolveActivity(pm) != null -> webSearch
        search.resolveActivity(pm) != null -> search
        else -> return null
    }
    return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) to context.getString(
        R.string.search_on_app,
        app.loadLabel(pm).toString(),
    )
}

private fun String.normalizedServiceName(): String =
    lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

fun TrendingTitle.decodeBackdrop(): ImageBitmap = decode(backdropJpeg).asImageBitmap()
fun TrendingTitle.decodeLogo(): ImageBitmap? = logoPng?.let { decode(it).asImageBitmap() }

// Streaming apps whose tile fills the top of the home screen with their most popular titles, like tvOS's top shelf:
// package → TMDB watch-provider ids ("|" = either). YouTube has no TMDB catalogue. Ids: /watch/providers/tv?watch_region=IN
val TOP_SHELF_PROVIDERS = mapOf(
    "com.netflix.ninja" to "8",
    "com.amazon.amazonvideo.livingroom" to "119",
    "com.disney.disneyplus" to "337",
    "in.startv.hotstar" to "2336",                    // JioHotstar
    "com.sonyliv" to "237",                           // Sony LIV
    "com.apple.atve.androidtv.appletv" to "350",      // Apple TV app (Apple TV+)
)
private const val TOP_SHELF_TITLES = 6
// "Popular now", not "popular ever": shows that started in the last two years (with any recent episode, 25-year-old
// procedurals and wrestling win), movies released in the last year
private const val TOP_SHELF_SHOW_DAYS = 730L
private const val TOP_SHELF_MOVIE_DAYS = 365L
// News, reality, soap, talk
private const val TOP_SHELF_SKIP_TV_GENRES = "10763,10764,10766,10767"

// What's popular on one streaming app in REGION right now. TMDB has no per-service charts, so this is TMDB
// popularity among titles that app streams. The button opens the app (TMDB doesn't know Netflix/Prime title ids).
fun fetchTopShelf(
    context: Context,
    packageName: String,
    appLabel: String,
    key: String = loadTmdbKey(context),
    language: String = "es-ES",
): List<TrendingTitle> {
    val providers = TOP_SHELF_PROVIDERS[packageName]
    if (key.isBlank() || providers == null) return emptyList()
    val open = context.packageManager.getLeanbackLaunchIntentForPackage(packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val today = java.time.LocalDate.now()
    val common = "watch_region=$REGION&with_watch_providers=${Uri.encode(providers)}&with_watch_monetization_types=flatrate" +
        "&sort_by=popularity.desc&include_adult=false"
    val opens = open to context.getString(R.string.open_in_app, appLabel)
    val shows = discoverTitles(
        context,
        key,
        language,
        "tv",
        "$common&first_air_date.gte=${today.minusDays(TOP_SHELF_SHOW_DAYS)}&without_genres=$TOP_SHELF_SKIP_TV_GENRES",
        opens,
    )
    val movies = discoverTitles(
        context,
        key,
        language,
        "movie",
        "$common&primary_release_date.gte=${today.minusDays(TOP_SHELF_MOVIE_DAYS)}",
        opens,
    )
    // Alternate show, movie, show...: movie popularity runs far higher, so a merged sort would be all movies
    return (0 until maxOf(shows.size, movies.size)).flatMap { listOfNotNull(shows.getOrNull(it), movies.getOrNull(it)) }
        .take(TOP_SHELF_TITLES)
}

fun fetchFreeRecommendations(
    context: Context,
    key: String = loadTmdbKey(context),
    language: String = "es-ES",
): List<TrendingTitle> {
    if (key.isBlank()) return emptyList()
    val common = "watch_region=$REGION&with_watch_monetization_types=${Uri.encode("free|ads")}" +
        "&sort_by=popularity.desc&include_adult=false"
    val shows = discoverTitles(
        context,
        key,
        language,
        "tv",
        "$common&without_genres=$TOP_SHELF_SKIP_TV_GENRES",
        youtubeSearchIfUnavailable = true,
    )
    val movies = discoverTitles(
        context,
        key,
        language,
        "movie",
        common,
        youtubeSearchIfUnavailable = true,
    )
    // Alternate shows and movies so the feed isn't dominated by the more popular media type.
    return (0 until maxOf(shows.size, movies.size)).flatMap { listOfNotNull(shows.getOrNull(it), movies.getOrNull(it)) }
        .take(TOP_SHELF_TITLES)
}

private fun discoverTitles(
    context: Context,
    key: String,
    language: String,
    mediaType: String,
    query: String,
    opens: Pair<Intent?, String?>? = null,
    youtubeSearchIfUnavailable: Boolean = false,
): List<TrendingTitle> {
    val items = try {
        JSONObject(tmdbGet("/discover/$mediaType?$query", key, language))
            .getJSONArray("results")
            .objects()
    } catch (e: IOException) {
        Log.w(TAG, "Discovery fetch failed for $mediaType", e)
        return emptyList()
    } catch (e: JSONException) {
        Log.w(TAG, "Unexpected discovery response for $mediaType", e)
        return emptyList()
    }
    return items.filter { !it.isNull("backdrop_path") }
        .take(TOP_SHELF_TITLES / 2)
        .onEach { it.put("media_type", mediaType) }
        .mapNotNull { item ->
            try {
                toTitle(context, key, language, item, opens, youtubeSearchIfUnavailable)
            } catch (e: IOException) {
                Log.w(TAG, "Skipping ${item.optInt("id")}", e)
                null
            } catch (e: JSONException) {
                Log.w(TAG, "Skipping ${item.optInt("id")}", e)
                null
            }
        }
}

// Accepts both the v3 API key and the v4 read access token (a JWT, starts with "eyJ")
private fun tmdbGet(path: String, key: String, language: String): String {
    val separator = if ('?' in path) '&' else '?'
    val url = "$API$path${separator}language=$language"
    if (key.startsWith("eyJ")) return httpGet(url, mapOf("Authorization" to "Bearer $key"))
    return httpGet("$url&api_key=$key")
}

private fun decode(bytes: ByteArray): Bitmap =
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: throw IOException("Undecodable image")

// Many TMDB logos are black text, invisible on a dark backdrop; sample opaque pixels and reject dark ones
private fun isLightEnough(logo: Bitmap): Boolean {
    var total = 0f
    var count = 0
    for (x in 0 until logo.width step 8) {
        for (y in 0 until logo.height step 8) {
            val pixel = logo.getPixel(x, y)
            if (pixel ushr 24 < 128) continue
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            total += (0.299f * r + 0.587f * g + 0.114f * b) / 255f
            count++
        }
    }
    return count > 0 && total / count > 0.45f
}

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
