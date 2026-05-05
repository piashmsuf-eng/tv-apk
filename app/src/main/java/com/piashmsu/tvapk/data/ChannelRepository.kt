package com.piashmsu.tvapk.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Loads and merges channels from every enabled [PlaylistSource]. Sources are
 * fetched in parallel; if one source fails the others are still kept.
 *
 * Supports three URL schemes:
 *  - `http://` / `https://` — fetched over the network (the common case).
 *  - `content://` — read through Android's ContentResolver. Used by the
 *    "Pick M3U from device" file picker so users can pull a playlist out
 *    of any file-manager-accessible location.
 *  - `file://` and bare absolute paths — read directly off disk.
 */
class ChannelRepository(
    private val context: Context,
    private val http: OkHttpClient,
    private val prefs: AppPrefs,
) {
    private val _state = MutableStateFlow<LoadState>(LoadState.Idle)
    val state: StateFlow<LoadState> = _state.asStateFlow()

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels.asStateFlow()

    private val _statuses = MutableStateFlow<Map<String, ChannelStatus>>(emptyMap())
    val statuses: StateFlow<Map<String, ChannelStatus>> = _statuses.asStateFlow()

    private val _probeProgress = MutableStateFlow<ProbeProgress>(ProbeProgress.Idle)
    val probeProgress: StateFlow<ProbeProgress> = _probeProgress.asStateFlow()

    private val probeClient: OkHttpClient = http.newBuilder()
        .cache(null)
        .dispatcher(
            Dispatcher().apply {
                maxRequests = 16
                maxRequestsPerHost = 4
            },
        )
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .callTimeout(6, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(false)
        .build()

    suspend fun refresh(): Result<Int> = withContext(Dispatchers.IO) {
        val sources = prefs.playlistSources.first().filter { it.enabled && it.url.isNotBlank() }
        if (sources.isEmpty()) {
            _state.value = LoadState.Idle
            _channels.value = emptyList()
            _statuses.value = emptyMap()
            return@withContext Result.failure(IllegalStateException("No playlist sources configured."))
        }
        _state.value = LoadState.Loading
        val errors = mutableListOf<String>()
        val merged = coroutineScope {
            sources.map { src ->
                async { runCatching { fetchSource(src) }.onFailure { errors += "${src.name}: ${it.message}" } }
            }.awaitAll().mapNotNull { it.getOrNull() }
        }.flatten()

        if (merged.isEmpty() && errors.isNotEmpty()) {
            _state.value = LoadState.Error(errors.joinToString(" • "))
            return@withContext Result.failure(IllegalStateException(errors.joinToString(" • ")))
        }

        _channels.value = merged
        // Re-hydrate cached probe results so users don't see "Unknown" until
        // they manually re-probe. Stale (>24 h) entries are discarded.
        val cached = prefs.probeResults.first()
        val lastRun = prefs.probeLastRunMs.first()
        val fresh = System.currentTimeMillis() - lastRun < 24 * 60 * 60 * 1000L
        _statuses.value = if (cached.isNotEmpty() && fresh) {
            cached.mapValues { (_, online) ->
                if (online) ChannelStatus.Online else ChannelStatus.Offline
            }
        } else emptyMap()
        _state.value = LoadState.Success(merged.size)
        Result.success(merged.size)
    }

    /**
     * Probe the reachability of every loaded channel. Each channel is checked
     * with a short HEAD (falling back to a 1-byte ranged GET) — outcomes are
     * recorded on a [ConcurrentHashMap] so concurrent writes are safe, and
     * progress / partial results are pushed to [_statuses] / [_probeProgress]
     * in batches.
     *
     * Concurrency is capped both at the coroutine layer ([flatMapMerge]) and
     * at the OkHttp dispatcher to stop the device's network stack from being
     * flooded — earlier versions used an unbounded HashMap + 24 parallel
     * coroutines and could crash on large playlists.
     */
    @Suppress("OPT_IN_USAGE")
    suspend fun probeReachability() = withContext(Dispatchers.IO) {
        val list = _channels.value
        if (list.isEmpty()) {
            _probeProgress.value = ProbeProgress.Idle
            return@withContext
        }
        val total = list.size
        _probeProgress.value = ProbeProgress.Running(0, total)
        val results = ConcurrentHashMap<String, ChannelStatus>(total)
        val done = AtomicInteger(0)

        list.asFlow()
            .flatMapMerge(concurrency = 12) { ch ->
                flow {
                    val status = runCatching { probeOne(ch) }
                        .getOrDefault(ChannelStatus.Offline)
                    emit(ch.id to status)
                }.flowOn(Dispatchers.IO)
            }
            .collect { (id, status) ->
                results[id] = status
                val n = done.incrementAndGet()
                _probeProgress.value = ProbeProgress.Running(n, total)
                if (n % 64 == 0 || n == total) {
                    _statuses.value = HashMap(results)
                }
            }

        _statuses.value = HashMap(results)
        val online = results.count { it.value == ChannelStatus.Online }
        val offline = results.count { it.value == ChannelStatus.Offline }
        _probeProgress.value = ProbeProgress.Finished(online, offline, total)
        // Persist results so the next launch shows the same Online/Offline
        // state without the user having to re-run the probe.
        runCatching {
            prefs.setProbeResults(results.mapValues { it.value == ChannelStatus.Online })
        }
    }

    /**
     * Group channels by category respecting the user's [SortMode] +
     * country/language filter chips. Favorites can optionally be
     * surfaced first.
     */
    fun groupedByCategory(
        query: String,
        hideOffline: Boolean,
        onlyOffline: Boolean,
        sortMode: SortMode,
        countryFilter: String,
        languageFilter: String,
        favoriteIds: Set<String>,
        lockedGroups: Set<String>,
        unlockedGroups: Set<String>,
    ): List<Category<Channel>> {
        val items = _channels.value
        val statuses = _statuses.value
        val filtered = items.asSequence()
            .filter { ch ->
                val matches = query.isBlank() ||
                    ch.name.contains(query, true) ||
                    ch.group.contains(query, true) ||
                    ch.sourceName.contains(query, true)
                if (!matches) return@filter false
                if (countryFilter.isNotBlank() && !ch.country.equals(countryFilter, true)) return@filter false
                if (languageFilter.isNotBlank() && !ch.language.equals(languageFilter, true)) return@filter false
                if (ch.group in lockedGroups && ch.group !in unlockedGroups) return@filter false
                val s = statuses[ch.id]
                if (onlyOffline) s == ChannelStatus.Offline
                else if (hideOffline) s != ChannelStatus.Offline
                else true
            }
            .toList()

        val sorted = filtered.sortedWith(channelComparator(sortMode, statuses, favoriteIds))
        val grouped = sorted.groupBy { it.group }.toList()

        val ordered = when (sortMode) {
            SortMode.ByCountry -> grouped.sortedBy { (g, list) ->
                (list.firstOrNull()?.country ?: g).lowercase()
            }
            SortMode.FavoritesFirst -> {
                val (fav, rest) = grouped.partition { (_, list) ->
                    list.any { favoriteIds.contains(it.id) }
                }
                fav + rest
            }
            SortMode.Alphabetical -> grouped.sortedBy { it.first.lowercase() }
            SortMode.OnlineFirst -> grouped.sortedByDescending { (_, list) ->
                list.count { statuses[it.id] == ChannelStatus.Online }
            }
            else -> grouped.sortedBy { it.first }
        }

        return ordered.map { (g, list) -> Category(g, list) }
    }

    fun availableCountries(): List<String> =
        _channels.value.mapNotNull { it.country?.takeIf { c -> c.isNotBlank() } }
            .toSet().sorted()

    fun availableLanguages(): List<String> =
        _channels.value.mapNotNull { it.language?.takeIf { l -> l.isNotBlank() } }
            .toSet().sorted()

    private fun channelComparator(
        sortMode: SortMode,
        statuses: Map<String, ChannelStatus>,
        favoriteIds: Set<String>,
    ): Comparator<Channel> {
        val name = compareBy<Channel> { it.name.lowercase() }
        return when (sortMode) {
            SortMode.Alphabetical -> name
            SortMode.ByCountry ->
                compareBy<Channel> { (it.country ?: "").lowercase() }.then(name)
            SortMode.OnlineFirst ->
                compareBy<Channel> { statuses[it.id] != ChannelStatus.Online }.then(name)
            SortMode.FavoritesFirst ->
                compareBy<Channel> { it.id !in favoriteIds }.then(name)
            else -> compareBy<Channel> { it.group.lowercase() }.then(name)
        }
    }

    private fun probeOne(channel: Channel): ChannelStatus {
        val url = channel.streamUrl
        if (url.isBlank()) return ChannelStatus.Offline
        val raw = url.trim()
        if (raw.startsWith("content://", true) || raw.startsWith("file://", true) || raw.startsWith("/")) {
            return ChannelStatus.Online
        }
        return try {
            val builder = Request.Builder().url(raw).head()
            builder.header("User-Agent", channel.httpUserAgent ?: "TVApk/1.0")
            channel.httpReferer?.let { builder.header("Referer", it) }
            for ((k, v) in channel.httpHeaders) builder.header(k, v)
            probeClient.newCall(builder.build()).execute().use { resp ->
                when {
                    resp.isSuccessful -> ChannelStatus.Online
                    resp.code == 405 || resp.code == 501 -> probeRange(channel)
                    else -> ChannelStatus.Offline
                }
            }
        } catch (_: IllegalArgumentException) {
            ChannelStatus.Offline
        } catch (_: Throwable) {
            try { probeRange(channel) } catch (_: Throwable) { ChannelStatus.Offline }
        }
    }

    private fun probeRange(channel: Channel): ChannelStatus {
        return try {
            val builder = Request.Builder().url(channel.streamUrl)
                .header("Range", "bytes=0-0")
                .header("User-Agent", channel.httpUserAgent ?: "TVApk/1.0")
            channel.httpReferer?.let { builder.header("Referer", it) }
            for ((k, v) in channel.httpHeaders) builder.header(k, v)
            probeClient.newCall(builder.build()).execute().use { resp ->
                if (resp.isSuccessful) ChannelStatus.Online else ChannelStatus.Offline
            }
        } catch (_: Throwable) {
            ChannelStatus.Offline
        }
    }

    private fun fetchSource(source: PlaylistSource): List<Channel> {
        val body = readSource(source)
        return M3UParser.parse(body, source)
    }

    private fun readSource(source: PlaylistSource): String {
        val raw = source.url.trim()
        return when {
            raw.startsWith("content://", ignoreCase = true) -> {
                val uri = Uri.parse(raw)
                context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                    ?: error("Empty file (${source.name})")
            }
            raw.startsWith("file://", ignoreCase = true) -> {
                File(Uri.parse(raw).path ?: error("Bad file URI")).readText(Charsets.UTF_8)
            }
            raw.startsWith("/") -> File(raw).readText(Charsets.UTF_8)
            else -> {
                val builder = Request.Builder().url(raw)
                builder.header("User-Agent", source.userAgent ?: "TVApk/1.0")
                if (!source.referer.isNullOrBlank()) builder.header("Referer", source.referer)
                http.newCall(builder.build()).execute().use { resp ->
                    if (!resp.isSuccessful) error("HTTP ${resp.code}")
                    resp.body?.string().orEmpty()
                }
            }
        }
    }

}

enum class ChannelStatus { Online, Offline }

sealed interface ProbeProgress {
    data object Idle : ProbeProgress
    data class Running(val done: Int, val total: Int) : ProbeProgress
    data class Finished(val online: Int, val offline: Int, val total: Int) : ProbeProgress
}

sealed interface LoadState {
    data object Idle : LoadState
    data object Loading : LoadState
    data class Success(val count: Int) : LoadState
    data class Error(val message: String) : LoadState
}
