package com.piashmsu.tvapk.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
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
        val ONBOARDED = booleanPreferencesKey("onboarded_v5")

        val THEME_PALETTE = stringPreferencesKey("theme_palette")
        val LAUNCHER_VARIANT = stringPreferencesKey("launcher_variant")
        val UI_LANGUAGE = stringPreferencesKey("ui_language")

        val PIN_HASH = stringPreferencesKey("pin_hash")
        val LOCKED_GROUPS = stringPreferencesKey("locked_groups")

        val EXTERNAL_PLAYER_PKG = stringPreferencesKey("external_player_pkg")
        val PLAYER_BUFFER_SECONDS = intPreferencesKey("player_buffer_seconds")
        val AUTO_SKIP_OFFLINE = booleanPreferencesKey("auto_skip_offline")
        val PLAYBACK_SPEED = stringPreferencesKey("playback_speed_default")

        val SORT_MODE = stringPreferencesKey("live_sort_mode")
        val FILTER_COUNTRY = stringPreferencesKey("live_filter_country")
        val FILTER_LANGUAGE = stringPreferencesKey("live_filter_language")

        val PROBE_RESULTS = stringPreferencesKey("probe_results_json")
        val PROBE_LAST_RUN = longPreferencesKey("probe_last_run_ms")

        val WATCH_POSITIONS = stringPreferencesKey("watch_positions_json")
        val WATCHLIST = stringPreferencesKey("watchlist_ids")
        val WATCH_COUNTER = stringPreferencesKey("watch_counter_json")

        val TMDB_KEY = stringPreferencesKey("tmdb_api_key")
        val TRAKT_KEY = stringPreferencesKey("trakt_client_id")

        val NOTIFICATIONS_EPG = booleanPreferencesKey("notifications_epg")
        val CRASH_REPORTER = booleanPreferencesKey("crash_reporter_enabled")
        val UPDATE_LAST_CHECKED = longPreferencesKey("update_last_checked_ms")
        val UPDATE_LATEST_TAG = stringPreferencesKey("update_latest_tag")

        val LAST_PLAYED_CHANNEL_ID = stringPreferencesKey("last_played_channel_id")
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

    val onboarded: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.ONBOARDED] == true
    }

    val themePalette: Flow<ThemePalette> = context.dataStore.data.map {
        ThemePalette.fromKey(it[Keys.THEME_PALETTE])
    }

    val launcherVariant: Flow<LauncherVariant> = context.dataStore.data.map {
        LauncherVariant.fromKey(it[Keys.LAUNCHER_VARIANT])
    }

    val uiLanguage: Flow<UiLanguage> = context.dataStore.data.map {
        UiLanguage.fromKey(it[Keys.UI_LANGUAGE])
    }

    val pinHash: Flow<String> = context.dataStore.data.map {
        it[Keys.PIN_HASH].orEmpty()
    }

    val lockedGroups: Flow<Set<String>> = context.dataStore.data.map {
        it[Keys.LOCKED_GROUPS].orEmpty()
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    val externalPlayerPkg: Flow<String> = context.dataStore.data.map {
        it[Keys.EXTERNAL_PLAYER_PKG].orEmpty()
    }

    val playerBufferSeconds: Flow<Int> = context.dataStore.data.map {
        (it[Keys.PLAYER_BUFFER_SECONDS] ?: 30).coerceIn(5, 120)
    }

    val autoSkipOffline: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.AUTO_SKIP_OFFLINE] != false
    }

    val playbackSpeed: Flow<Float> = context.dataStore.data.map {
        it[Keys.PLAYBACK_SPEED]?.toFloatOrNull() ?: 1f
    }

    val sortMode: Flow<SortMode> = context.dataStore.data.map {
        SortMode.fromKey(it[Keys.SORT_MODE])
    }

    val filterCountry: Flow<String> = context.dataStore.data.map {
        it[Keys.FILTER_COUNTRY].orEmpty()
    }

    val filterLanguage: Flow<String> = context.dataStore.data.map {
        it[Keys.FILTER_LANGUAGE].orEmpty()
    }

    val probeResults: Flow<Map<String, Boolean>> = context.dataStore.data.map {
        decodeProbeResults(it[Keys.PROBE_RESULTS].orEmpty())
    }

    val probeLastRunMs: Flow<Long> = context.dataStore.data.map {
        it[Keys.PROBE_LAST_RUN] ?: 0L
    }

    val watchPositions: Flow<Map<String, Long>> = context.dataStore.data.map {
        decodeWatchPositions(it[Keys.WATCH_POSITIONS].orEmpty())
    }

    val watchlist: Flow<Set<String>> = context.dataStore.data.map {
        it[Keys.WATCHLIST].orEmpty()
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    val watchCounter: Flow<Map<String, Int>> = context.dataStore.data.map {
        decodeWatchCounter(it[Keys.WATCH_COUNTER].orEmpty())
    }

    val tmdbKey: Flow<String> = context.dataStore.data.map { it[Keys.TMDB_KEY].orEmpty() }
    val traktKey: Flow<String> = context.dataStore.data.map { it[Keys.TRAKT_KEY].orEmpty() }

    val notificationsEpg: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.NOTIFICATIONS_EPG] == true
    }

    val crashReporter: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.CRASH_REPORTER] == true
    }

    val updateLastCheckedMs: Flow<Long> = context.dataStore.data.map {
        it[Keys.UPDATE_LAST_CHECKED] ?: 0L
    }
    val updateLatestTag: Flow<String> = context.dataStore.data.map {
        it[Keys.UPDATE_LATEST_TAG].orEmpty()
    }

    val lastPlayedChannelId: Flow<String> = context.dataStore.data.map {
        it[Keys.LAST_PLAYED_CHANNEL_ID].orEmpty()
    }

    suspend fun setMovieCatalogUrl(url: String) =
        update(Keys.MOVIE_CATALOG_URL, url.trim())

    suspend fun setLastPlayed(title: String) =
        update(Keys.LAST_PLAYED, title.take(120))

    suspend fun setLastPlayedChannel(id: String) =
        update(Keys.LAST_PLAYED_CHANNEL_ID, id)

    suspend fun setRefreshInterval(value: RefreshInterval) {
        context.dataStore.edit { it[Keys.REFRESH_INTERVAL_HOURS] = value.hours }
    }

    suspend fun setLastAutoRefresh(stamp: String) =
        update(Keys.LAST_AUTO_REFRESH, stamp)

    suspend fun setOnboarded(value: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDED] = value }
    }

    suspend fun setThemePalette(palette: ThemePalette) =
        update(Keys.THEME_PALETTE, palette.key)

    suspend fun setLauncherVariant(variant: LauncherVariant) =
        update(Keys.LAUNCHER_VARIANT, variant.key)

    suspend fun setUiLanguage(lang: UiLanguage) =
        update(Keys.UI_LANGUAGE, lang.key)

    suspend fun setPinHash(hash: String) =
        update(Keys.PIN_HASH, hash)

    suspend fun setLockedGroups(groups: Set<String>) =
        update(Keys.LOCKED_GROUPS, groups.joinToString("\n"))

    suspend fun setExternalPlayerPkg(pkg: String) =
        update(Keys.EXTERNAL_PLAYER_PKG, pkg.trim())

    suspend fun setPlayerBufferSeconds(seconds: Int) {
        context.dataStore.edit { it[Keys.PLAYER_BUFFER_SECONDS] = seconds.coerceIn(5, 120) }
    }

    suspend fun setAutoSkipOffline(value: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_SKIP_OFFLINE] = value }
    }

    suspend fun setPlaybackSpeed(speed: Float) =
        update(Keys.PLAYBACK_SPEED, speed.toString())

    suspend fun setSortMode(mode: SortMode) =
        update(Keys.SORT_MODE, mode.key)

    suspend fun setFilterCountry(value: String) =
        update(Keys.FILTER_COUNTRY, value)

    suspend fun setFilterLanguage(value: String) =
        update(Keys.FILTER_LANGUAGE, value)

    suspend fun setProbeResults(results: Map<String, Boolean>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PROBE_RESULTS] = encodeProbeResults(results)
            prefs[Keys.PROBE_LAST_RUN] = System.currentTimeMillis()
        }
    }

    suspend fun setWatchPosition(streamUrl: String, positionMs: Long, durationMs: Long) {
        if (streamUrl.isBlank()) return
        context.dataStore.edit { prefs ->
            val map = decodeWatchPositions(prefs[Keys.WATCH_POSITIONS].orEmpty()).toMutableMap()
            // If we're within 30 s of the end OR within 5 s of the start, remove rather than save.
            val nearEnd = durationMs > 0 && positionMs >= durationMs - 30_000L
            val nearStart = positionMs < 5_000L
            if (nearEnd || nearStart) map.remove(streamUrl) else map[streamUrl] = positionMs
            prefs[Keys.WATCH_POSITIONS] = encodeWatchPositions(map)
        }
    }

    suspend fun toggleWatchlist(movieId: String) {
        context.dataStore.edit { prefs ->
            val set = prefs[Keys.WATCHLIST].orEmpty()
                .split('\n')
                .filter { it.isNotEmpty() }
                .toMutableSet()
            if (movieId in set) set -= movieId else set += movieId
            prefs[Keys.WATCHLIST] = set.joinToString("\n")
        }
    }

    suspend fun bumpWatchCounter(itemId: String) {
        if (itemId.isBlank()) return
        context.dataStore.edit { prefs ->
            val map = decodeWatchCounter(prefs[Keys.WATCH_COUNTER].orEmpty()).toMutableMap()
            map[itemId] = (map[itemId] ?: 0) + 1
            prefs[Keys.WATCH_COUNTER] = encodeWatchCounter(map)
        }
    }

    suspend fun setTmdbKey(key: String) = update(Keys.TMDB_KEY, key.trim())
    suspend fun setTraktKey(key: String) = update(Keys.TRAKT_KEY, key.trim())

    suspend fun setNotificationsEpg(value: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATIONS_EPG] = value }
    }

    suspend fun setCrashReporter(value: Boolean) {
        context.dataStore.edit { it[Keys.CRASH_REPORTER] = value }
    }

    suspend fun setUpdateChecked(latestTag: String) {
        context.dataStore.edit {
            it[Keys.UPDATE_LAST_CHECKED] = System.currentTimeMillis()
            it[Keys.UPDATE_LATEST_TAG] = latestTag
        }
    }

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

    /** Snapshot prefs to a single JSON document for backup / restore. */
    suspend fun exportToJson(): String {
        val prefs = context.dataStore.data.first()
        val obj = JSONObject()
        obj.put("schema", 1)
        obj.put("playlistSources", encodeSources(playlistSources.first()))
        obj.put("movieCatalogUrl", prefs[Keys.MOVIE_CATALOG_URL].orEmpty())
        obj.put("favorites", prefs[Keys.FAVORITES].orEmpty())
        obj.put("recents", prefs[Keys.RECENTS].orEmpty())
        obj.put("refreshIntervalHours", prefs[Keys.REFRESH_INTERVAL_HOURS] ?: 0)
        obj.put("themePalette", prefs[Keys.THEME_PALETTE].orEmpty())
        obj.put("launcherVariant", prefs[Keys.LAUNCHER_VARIANT].orEmpty())
        obj.put("uiLanguage", prefs[Keys.UI_LANGUAGE].orEmpty())
        obj.put("lockedGroups", prefs[Keys.LOCKED_GROUPS].orEmpty())
        obj.put("externalPlayerPkg", prefs[Keys.EXTERNAL_PLAYER_PKG].orEmpty())
        obj.put("playerBufferSeconds", prefs[Keys.PLAYER_BUFFER_SECONDS] ?: 30)
        obj.put("autoSkipOffline", prefs[Keys.AUTO_SKIP_OFFLINE] != false)
        obj.put("playbackSpeed", prefs[Keys.PLAYBACK_SPEED].orEmpty())
        obj.put("sortMode", prefs[Keys.SORT_MODE].orEmpty())
        obj.put("filterCountry", prefs[Keys.FILTER_COUNTRY].orEmpty())
        obj.put("filterLanguage", prefs[Keys.FILTER_LANGUAGE].orEmpty())
        obj.put("watchlist", prefs[Keys.WATCHLIST].orEmpty())
        obj.put("watchPositions", prefs[Keys.WATCH_POSITIONS].orEmpty())
        obj.put("watchCounter", prefs[Keys.WATCH_COUNTER].orEmpty())
        obj.put("tmdbKey", prefs[Keys.TMDB_KEY].orEmpty())
        obj.put("traktKey", prefs[Keys.TRAKT_KEY].orEmpty())
        obj.put("notificationsEpg", prefs[Keys.NOTIFICATIONS_EPG] == true)
        obj.put("crashReporter", prefs[Keys.CRASH_REPORTER] == true)
        // Don't include PIN hash in the backup: it would defeat the purpose.
        return obj.toString(2)
    }

    suspend fun importFromJson(json: String): Boolean {
        return runCatching {
            val obj = JSONObject(json)
            context.dataStore.edit { prefs ->
                obj.optString("playlistSources").takeIf { it.isNotBlank() }?.let {
                    prefs[Keys.PLAYLIST_SOURCES] = it
                }
                obj.optString("movieCatalogUrl").takeIf { it.isNotBlank() }?.let {
                    prefs[Keys.MOVIE_CATALOG_URL] = it
                }
                obj.optString("favorites").let { prefs[Keys.FAVORITES] = it }
                obj.optString("recents").takeIf { it.isNotBlank() }?.let {
                    prefs[Keys.RECENTS] = it
                }
                obj.optInt("refreshIntervalHours", -1).takeIf { it >= 0 }?.let {
                    prefs[Keys.REFRESH_INTERVAL_HOURS] = it
                }
                obj.optString("themePalette").takeIf { it.isNotBlank() }?.let {
                    prefs[Keys.THEME_PALETTE] = it
                }
                obj.optString("launcherVariant").takeIf { it.isNotBlank() }?.let {
                    prefs[Keys.LAUNCHER_VARIANT] = it
                }
                obj.optString("uiLanguage").takeIf { it.isNotBlank() }?.let {
                    prefs[Keys.UI_LANGUAGE] = it
                }
                obj.optString("lockedGroups").let { prefs[Keys.LOCKED_GROUPS] = it }
                obj.optString("externalPlayerPkg").let { prefs[Keys.EXTERNAL_PLAYER_PKG] = it }
                obj.optInt("playerBufferSeconds", -1).takeIf { it >= 0 }?.let {
                    prefs[Keys.PLAYER_BUFFER_SECONDS] = it
                }
                if (obj.has("autoSkipOffline")) {
                    prefs[Keys.AUTO_SKIP_OFFLINE] = obj.optBoolean("autoSkipOffline", true)
                }
                obj.optString("playbackSpeed").takeIf { it.isNotBlank() }?.let {
                    prefs[Keys.PLAYBACK_SPEED] = it
                }
                obj.optString("sortMode").takeIf { it.isNotBlank() }?.let {
                    prefs[Keys.SORT_MODE] = it
                }
                obj.optString("filterCountry").let { prefs[Keys.FILTER_COUNTRY] = it }
                obj.optString("filterLanguage").let { prefs[Keys.FILTER_LANGUAGE] = it }
                obj.optString("watchlist").let { prefs[Keys.WATCHLIST] = it }
                obj.optString("watchPositions").takeIf { it.isNotBlank() }?.let {
                    prefs[Keys.WATCH_POSITIONS] = it
                }
                obj.optString("watchCounter").takeIf { it.isNotBlank() }?.let {
                    prefs[Keys.WATCH_COUNTER] = it
                }
                obj.optString("tmdbKey").let { prefs[Keys.TMDB_KEY] = it }
                obj.optString("traktKey").let { prefs[Keys.TRAKT_KEY] = it }
                if (obj.has("notificationsEpg")) {
                    prefs[Keys.NOTIFICATIONS_EPG] = obj.optBoolean("notificationsEpg", false)
                }
                if (obj.has("crashReporter")) {
                    prefs[Keys.CRASH_REPORTER] = obj.optBoolean("crashReporter", false)
                }
            }
            true
        }.getOrDefault(false)
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

    private fun encodeProbeResults(results: Map<String, Boolean>): String {
        val obj = JSONObject()
        for ((id, online) in results) obj.put(id, online)
        return obj.toString()
    }

    private fun decodeProbeResults(json: String): Map<String, Boolean> {
        if (json.isBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(json)
            buildMap {
                obj.keys().forEach { k -> put(k, obj.optBoolean(k)) }
            }
        }.getOrDefault(emptyMap())
    }

    private fun encodeWatchPositions(map: Map<String, Long>): String {
        val obj = JSONObject()
        for ((url, pos) in map) obj.put(url, pos)
        return obj.toString()
    }

    private fun decodeWatchPositions(json: String): Map<String, Long> {
        if (json.isBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(json)
            buildMap {
                obj.keys().forEach { k -> put(k, obj.optLong(k)) }
            }
        }.getOrDefault(emptyMap())
    }

    private fun encodeWatchCounter(map: Map<String, Int>): String {
        val obj = JSONObject()
        for ((id, count) in map) obj.put(id, count)
        return obj.toString()
    }

    private fun decodeWatchCounter(json: String): Map<String, Int> {
        if (json.isBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(json)
            buildMap {
                obj.keys().forEach { k -> put(k, obj.optInt(k)) }
            }
        }.getOrDefault(emptyMap())
    }
}
