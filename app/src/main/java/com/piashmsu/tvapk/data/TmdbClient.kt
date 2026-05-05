package com.piashmsu.tvapk.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Tiny TMDB API v3 client. Returns metadata enrichment for a movie when an
 * API key is configured; otherwise [enrich] returns null and the UI falls
 * back to the playlist-supplied data.
 *
 * We deliberately keep the request volume low — one search call per movie,
 * cached in-memory for the session. TMDB free tier allows ~50 req/s.
 */
class TmdbClient(private val http: OkHttpClient, private val prefs: AppPrefs) {

    private val cache = HashMap<String, TmdbMeta?>()

    data class TmdbMeta(
        val tmdbId: Int,
        val poster: String?,
        val backdrop: String?,
        val overview: String?,
        val voteAverage: Double?,
        val releaseYear: Int?,
    )

    suspend fun enrich(title: String, year: Int? = null): TmdbMeta? = withContext(Dispatchers.IO) {
        val key = title.lowercase().trim()
        if (cache.containsKey(key)) return@withContext cache[key]
        val apiKey = prefs.tmdbKey.first()
        if (apiKey.isBlank()) return@withContext null

        val urlBuilder = "https://api.themoviedb.org/3/search/movie".toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("api_key", apiKey)
            ?.addQueryParameter("query", title)
            ?: return@withContext null
        if (year != null) urlBuilder.addQueryParameter("year", year.toString())

        val req = Request.Builder().url(urlBuilder.build())
            .header("Accept", "application/json")
            .build()

        val meta = runCatching {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                val obj = JSONObject(resp.body?.string().orEmpty())
                val results = obj.optJSONArray("results") ?: return@runCatching null
                if (results.length() == 0) return@runCatching null
                val first = results.getJSONObject(0)
                TmdbMeta(
                    tmdbId = first.optInt("id"),
                    poster = first.optString("poster_path").takeIf { it.isNotBlank() }
                        ?.let { "https://image.tmdb.org/t/p/w500$it" },
                    backdrop = first.optString("backdrop_path").takeIf { it.isNotBlank() }
                        ?.let { "https://image.tmdb.org/t/p/w780$it" },
                    overview = first.optString("overview").takeIf { it.isNotBlank() },
                    voteAverage = first.optDouble("vote_average")
                        .takeIf { !it.isNaN() && it > 0.0 },
                    releaseYear = first.optString("release_date")
                        .takeIf { it.length >= 4 }?.substring(0, 4)?.toIntOrNull(),
                )
            }
        }.getOrNull()

        cache[key] = meta
        meta
    }
}
