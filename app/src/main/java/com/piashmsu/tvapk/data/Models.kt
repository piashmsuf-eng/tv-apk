package com.piashmsu.tvapk.data

/**
 * A single live-TV channel parsed from an M3U playlist.
 *
 * `httpUserAgent`, `httpReferer`, and `httpHeaders` are optional per-channel
 * HTTP overrides extracted from `#EXTVLCOPT:` / `#KODIPROP:` directives or
 * inherited from the parent [PlaylistSource]. They are passed to the
 * ExoPlayer DataSource so DRM-free streams that require a specific
 * User-Agent / Referer accept the request.
 *
 * `catchupSource`, `catchupDays`, and `catchupType` represent the standard
 * IPTV catch-up / time-shift attributes — when present, the player shows a
 * timeline that lets the user rewind into the last N days of programming.
 */
data class Channel(
    val id: String,
    val name: String,
    val logo: String?,
    val group: String,
    val streamUrl: String,
    val tvgId: String? = null,
    val language: String? = null,
    val country: String? = null,
    val sourceId: String = "",
    val sourceName: String = "",
    val httpUserAgent: String? = null,
    val httpReferer: String? = null,
    val httpHeaders: Map<String, String> = emptyMap(),
    val catchupSource: String? = null,
    val catchupDays: Int? = null,
    val catchupType: String? = null,
) {
    val hasCatchup: Boolean get() = !catchupSource.isNullOrBlank() && (catchupDays ?: 0) > 0
}

/** A movie or video-on-demand entry. */
data class Movie(
    val id: String,
    val title: String,
    val poster: String?,
    val streamUrl: String,
    val genre: String,
    val language: String,
    val year: Int?,
    val description: String?,
    val durationMinutes: Int?,
    val rating: Double?,
    val backdrop: String?,
)

/** UI-friendly grouping of items by category title. */
data class Category<T>(
    val title: String,
    val items: List<T>,
)

/**
 * One configured IPTV playlist source. Multiple sources can be added —
 * channels from all enabled sources are merged into the live-TV list.
 *
 * `epgUrl` is an XMLTV URL associated with this source. When set, EPG
 * "now playing" / "up next" data appears on channel tiles.
 *
 * `userAgent` / `referer` apply to every channel from this source unless
 * the channel itself specifies an override via `#EXTVLCOPT:` / `#KODIPROP:`.
 */
data class PlaylistSource(
    val id: String,
    val name: String,
    val url: String,
    val enabled: Boolean = true,
    val epgUrl: String? = null,
    val userAgent: String? = null,
    val referer: String? = null,
)

/** A single EPG programme entry parsed from XMLTV. */
data class EpgProgramme(
    val channelId: String,
    val title: String,
    val description: String?,
    val start: Long,
    val end: Long,
) {
    val durationMs: Long get() = (end - start).coerceAtLeast(0)
    fun isLive(now: Long = System.currentTimeMillis()): Boolean = now in start..end
}

/** A persisted "recently watched" entry. */
data class RecentChannel(
    val channelId: String,
    val name: String,
    val logo: String?,
    val streamUrl: String,
    val timestamp: Long,
)

/** Resume position + duration for a single movie. */
data class MovieProgress(
    val movieId: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
) {
    val fraction: Float
        get() = if (durationMs <= 0L) 0f
        else (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

    val isInProgress: Boolean
        get() = positionMs > 5_000L && (durationMs <= 0L || positionMs < durationMs - 5_000L)
}

/** Persisted user choice for the app theme. */
enum class AppTheme(val label: String) {
    System("Follow system"),
    Light("Light"),
    Dark("Dark");

    companion object {
        fun fromKey(key: String?): AppTheme = values().firstOrNull { it.name == key } ?: System
    }
}

/** Persistent setting for the periodic playlist refresh. */
enum class RefreshInterval(val hours: Int, val label: String) {
    Off(0, "Off"),
    Every6h(6, "Every 6 hours"),
    Every12h(12, "Every 12 hours"),
    Daily(24, "Daily");

    companion object {
        fun fromHours(h: Int): RefreshInterval = values().firstOrNull { it.hours == h } ?: Off
    }
}
