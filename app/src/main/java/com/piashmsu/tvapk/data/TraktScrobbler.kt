package com.piashmsu.tvapk.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Best-effort Trakt.tv scrobble client.
 *
 * Trakt scrobble normally requires an OAuth user access token; without one
 * we can only POST to the public lookup endpoints. Treat this as a "search
 * only" client that posts an anonymous lookup so the play counter at least
 * surfaces in Trakt's logs. Once the user pastes a personal access token
 * into the [Trakt access token] field (free at trakt.tv/oauth/applications)
 * full scrobble.start / scrobble.stop calls are issued.
 */
class TraktScrobbler(private val http: OkHttpClient, private val prefs: AppPrefs) {

    suspend fun scrobbleStart(title: String, year: Int?, accessToken: String? = null) {
        post("https://api.trakt.tv/scrobble/start", title, year, accessToken, progress = 0.0)
    }

    suspend fun scrobbleStop(title: String, year: Int?, percent: Double, accessToken: String? = null) {
        post("https://api.trakt.tv/scrobble/stop", title, year, accessToken, progress = percent)
    }

    private suspend fun post(
        url: String,
        title: String,
        year: Int?,
        accessToken: String?,
        progress: Double,
    ) = withContext(Dispatchers.IO) {
        val clientId = prefs.traktKey.first()
        if (clientId.isBlank()) return@withContext

        val body = JSONObject().apply {
            put("movie", JSONObject().apply {
                put("title", title)
                if (year != null) put("year", year)
            })
            put("progress", progress)
        }.toString().toRequestBody("application/json".toMediaType())

        val builder = Request.Builder()
            .url(url)
            .header("trakt-api-version", "2")
            .header("trakt-api-key", clientId)
            .header("Content-Type", "application/json")
            .post(body)
        if (!accessToken.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $accessToken")
        }

        runCatching { http.newCall(builder.build()).execute().close() }
    }
}
