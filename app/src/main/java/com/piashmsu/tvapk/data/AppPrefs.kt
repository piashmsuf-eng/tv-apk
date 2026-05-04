package com.piashmsu.tvapk.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "tv_apk_prefs")

/**
 * User-controlled application preferences.
 *
 * The app intentionally ships with no preconfigured channels or movies.
 * The user must supply their own legal IPTV M3U URL(s) and/or movie catalog
 * JSON URL through the Settings screen. Multiple playlist sources are
 * supported — the live-TV list merges channels from every enabled source.
 */
class AppPrefs(private val context: Context) {

    private object Keys {
        // Legacy single-URL key kept around for one-off migration.
        val LEGACY_PLAYLIST_URL = stringPreferencesKey("playlist_url")

        val PLAYLIST_SOURCES = stringPreferencesKey("playlist_sources_json")
        val MOVIE_CATALOG_URL = stringPreferencesKey("movie_catalog_url")
        val LAST_PLAYED = stringPreferencesKey("last_played")
        val FAVORITES = stringPreferencesKey("favorite_channel_ids")
        val RECENTS = stringPreferencesKey("recent_channels_json")
        val REFRESH_INTERVAL_HOURS = intPreferencesKey("refresh_interval_hours")
        val LAST_AUTO_REFRESH = stringPreferencesKey("last_auto_refresh")
        val DEFAULTS_SEEDED = booleanPreferencesKey("defaults_seeded")
        val MOVIE_FAVORITES = stringPreferencesKey("favorite_movie_ids")
        val MOVIE_PROGRESS = stringPreferencesKey("movie_progress_json")
        val APP_THEME = stringPreferencesKey("app_theme")
    }

    val playlistSources: Flow<List<PlaylistSource>> = context.dataStore.data.map { prefs ->
        val json = prefs[Keys.PLAYLIST_SOURCES]
        if (json.isNullOrBlank()) {
            // Migrate from legacy single-URL preference.
            val legacy = prefs[Keys.LEGACY_PLAYLIST_URL]
            if (!legacy.isNullOrBlank()) {
                listOf(
                    PlaylistSource(
                        id = "legacy",
                        name = "My playlist",
                        url = legacy,
                        enabled = true,
                    )
                )
            } else emptyList()
        } else {
            decodeSources(json)
        }
    }

    val movieCatalogUrl: Flow<String> = context.dataStore.data.map {
        it[Keys.MOVIE_CATALOG_URL].orEmpty()
    }

    val lastPlayed: Flow<String> = context.dataStore.data.map {
        it[Keys.LAST_PLAYED].orEmpty()
    }

    val favorites: Flow<Set<String>> = context.dataStore.data.map {
        it[Keys.FAVORITES].orEmpty()
            .split('\n')
            .map { id -> id.trim() }
            .filter { id -> id.isNotEmpty() }
            .toSet()
    }

    val recents: Flow<List<RecentChannel>> = context.dataStore.data.map {
        decodeRecents(it[Keys.RECENTS].orEmpty())
    }

    val refreshInterval: Flow<RefreshInterval> = context.dataStore.data.map {
        RefreshInterval.fromHours(it[Keys.REFRESH_INTERVAL_HOURS] ?: 0)
    }

    val lastAutoRefresh: Flow<String> = context.dataStore.data.map {
        it[Keys.LAST_AUTO_REFRESH].orEmpty()
    }

    val movieFavorites: Flow<Set<String>> = context.dataStore.data.map {
        it[Keys.MOVIE_FAVORITES].orEmpty()
            .split('\n')
            .map { id -> id.trim() }
            .filter { id -> id.isNotEmpty() }
            .toSet()
    }

    val movieProgress: Flow<Map<String, MovieProgress>> = context.dataStore.data.map {
        decodeMovieProgress(it[Keys.MOVIE_PROGRESS].orEmpty())
    }

    val appTheme: Flow<AppTheme> = context.dataStore.data.map {
        AppTheme.fromKey(it[Keys.APP_THEME])
    }

    suspend fun setMovieCatalogUrl(url: String) =
        update(Keys.MOVIE_CATALOG_URL, url.trim())

    suspend fun setLastPlayed(title: String) =
        update(Keys.LAST_PLAYED, title.take(120))

    suspend fun setRefreshInterval(value: RefreshInterval) {
        context.dataStore.edit { it[Keys.REFRESH_INTERVAL_HOURS] = value.hours }
    }

    suspend fun setLastAutoRefresh(stamp: String) =
        update(Keys.LAST_AUTO_REFRESH, stamp)

    /**
     * Seed a default world-TV playlist source on first launch so the app
     * has channels out of the box. Also enables a 12-hour auto-refresh by
     * default. Does nothing on subsequent launches — the user is free to
     * disable, edit, or remove the seeded source without it coming back.
     */
    suspend fun seedDefaultsIfNeeded() {
        val seeded = context.dataStore.data.first()[Keys.DEFAULTS_SEEDED] == true
        if (seeded) return
        val sources = playlistSources.first()
        if (sources.isEmpty()) {
            writeSources(
                listOf(
                    PlaylistSource(
                        id = "world-tv-default",
                        name = "World TV (auto-updated)",
                        url = "https://iptv-org.github.io/iptv/index.m3u",
                        enabled = true,
                        epgUrl = "https://iptv-org.github.io/epg/guides/us.xml",
                    )
                )
            )
        }
        context.dataStore.edit { prefs ->
            if ((prefs[Keys.REFRESH_INTERVAL_HOURS] ?: 0) == 0) {
                prefs[Keys.REFRESH_INTERVAL_HOURS] = RefreshInterval.Every12h.hours
            }
            prefs[Keys.DEFAULTS_SEEDED] = true
        }
    }

    suspend fun upsertPlaylistSource(source: PlaylistSource) {
        val current = playlistSources.first().toMutableList()
        val idx = current.indexOfFirst { it.id == source.id }
        if (idx >= 0) current[idx] = source else current += source
        writeSources(current)
    }

    suspend fun removePlaylistSource(id: String) {
        val current = playlistSources.first().filter { it.id != id }
        writeSources(current)
    }

    suspend fun setPlaylistSources(sources: List<PlaylistSource>) {
        writeSources(sources)
    }

    suspend fun toggleFavorite(channelId: String): Boolean {
        var resulting = false
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.FAVORITES].orEmpty()
                .split('\n')
                .filter { it.isNotEmpty() }
                .toMutableSet()
            if (channelId in current) current -= channelId
            else { current += channelId; resulting = true }
            prefs[Keys.FAVORITES] = current.joinToString("\n")
        }
        return resulting
    }

    suspend fun pushRecent(entry: RecentChannel) {
        context.dataStore.edit { prefs ->
            val items = decodeRecents(prefs[Keys.RECENTS].orEmpty()).toMutableList()
            items.removeAll { it.channelId == entry.channelId }
            items.add(0, entry)
            while (items.size > 30) items.removeAt(items.lastIndex)
            prefs[Keys.RECENTS] = encodeRecents(items)
        }
    }

    private suspend fun writeSources(sources: List<PlaylistSource>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PLAYLIST_SOURCES] = encodeSources(sources)
        }
    }

    private suspend fun update(key: Preferences.Key<String>, value: String) {
        context.dataStore.edit { it[key] = value }
    }

    private fun encodeSources(sources: List<PlaylistSource>): String {
        val arr = JSONArray()
        for (s in sources) {
            arr.put(
                JSONObject().apply {
                    put("id", s.id)
                    put("name", s.name)
                    put("url", s.url)
                    put("enabled", s.enabled)
                    if (!s.epgUrl.isNullOrBlank()) put("epgUrl", s.epgUrl)
                    if (!s.userAgent.isNullOrBlank()) put("userAgent", s.userAgent)
                    if (!s.referer.isNullOrBlank()) put("referer", s.referer)
                }
            )
        }
        return arr.toString()
    }

    private fun decodeSources(json: String): List<PlaylistSource> {
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                PlaylistSource(
                    id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                    name = o.optString("name").ifBlank { "Playlist" },
                    url = o.optString("url"),
                    enabled = o.optBoolean("enabled", true),
                    epgUrl = o.optString("epgUrl").ifBlank { null },
                    userAgent = o.optString("userAgent").ifBlank { null },
                    referer = o.optString("referer").ifBlank { null },
                )
            }
        }.getOrDefault(emptyList())
    }

    suspend fun toggleMovieFavorite(movieId: String): Boolean {
        var resulting = false
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.MOVIE_FAVORITES].orEmpty()
                .split('\n')
                .filter { it.isNotEmpty() }
                .toMutableSet()
            if (movieId in current) current -= movieId
            else { current += movieId; resulting = true }
            prefs[Keys.MOVIE_FAVORITES] = current.joinToString("\n")
        }
        return resulting
    }

    suspend fun saveMovieProgress(progress: MovieProgress) {
        context.dataStore.edit { prefs ->
            val map = decodeMovieProgress(prefs[Keys.MOVIE_PROGRESS].orEmpty()).toMutableMap()
            map[progress.movieId] = progress
            // Drop entries older than 60 days so the JSON blob doesn't grow
            // forever on heavy users.
            val cutoff = System.currentTimeMillis() - 60L * 24 * 3600 * 1000
            val pruned = map.filterValues { it.updatedAt >= cutoff }
            prefs[Keys.MOVIE_PROGRESS] = encodeMovieProgress(pruned)
        }
    }

    suspend fun clearMovieProgress(movieId: String) {
        context.dataStore.edit { prefs ->
            val map = decodeMovieProgress(prefs[Keys.MOVIE_PROGRESS].orEmpty()).toMutableMap()
            map.remove(movieId)
            prefs[Keys.MOVIE_PROGRESS] = encodeMovieProgress(map)
        }
    }

    suspend fun setAppTheme(theme: AppTheme) {
        context.dataStore.edit { it[Keys.APP_THEME] = theme.name }
    }

    private fun encodeMovieProgress(map: Map<String, MovieProgress>): String {
        val arr = JSONArray()
        for ((id, p) in map) {
            arr.put(
                JSONObject().apply {
                    put("id", id)
                    put("position", p.positionMs)
                    put("duration", p.durationMs)
                    put("updated", p.updatedAt)
                }
            )
        }
        return arr.toString()
    }

    private fun decodeMovieProgress(json: String): Map<String, MovieProgress> {
        if (json.isBlank()) return emptyMap()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id").ifBlank { return@mapNotNull null }
                id to MovieProgress(
                    movieId = id,
                    positionMs = o.optLong("position"),
                    durationMs = o.optLong("duration"),
                    updatedAt = o.optLong("updated"),
                )
            }.toMap()
        }.getOrDefault(emptyMap())
    }

    private fun encodeRecents(items: List<RecentChannel>): String {
        val arr = JSONArray()
        for (r in items) {
            arr.put(
                JSONObject().apply {
                    put("channelId", r.channelId)
                    put("name", r.name)
                    put("logo", r.logo.orEmpty())
                    put("streamUrl", r.streamUrl)
                    put("timestamp", r.timestamp)
                }
            )
        }
        return arr.toString()
    }

    private fun decodeRecents(json: String): List<RecentChannel> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                RecentChannel(
                    channelId = o.optString("channelId"),
                    name = o.optString("name"),
                    logo = o.optString("logo").ifBlank { null },
                    streamUrl = o.optString("streamUrl"),
                    timestamp = o.optLong("timestamp"),
                )
            }
        }.getOrDefault(emptyList())
    }
}
