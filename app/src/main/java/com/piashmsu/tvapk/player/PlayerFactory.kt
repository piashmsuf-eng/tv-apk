package com.piashmsu.tvapk.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector

/**
 * Builds an ExoPlayer instance configured to handle the dominant IPTV stream
 * formats (HLS, MPEG-DASH, SmoothStreaming, RTSP, and progressive MP4/MKV).
 *
 * `userAgent`, `referer`, and `extraHeaders` are forwarded to the HTTP data
 * source so DRM-free streams that require a specific User-Agent / Referer
 * (a common protection against hot-linking) play correctly.
 *
 * `bufferSeconds` is the user-configurable target buffer in seconds. The
 * minimum playback buffer is bounded by Media3 to ~500 ms so we just clamp
 * the input to a sensible 5–120 s range.
 */
fun buildPlayerForUrl(
    context: Context,
    url: String,
    userAgent: String? = null,
    referer: String? = null,
    extraHeaders: Map<String, String> = emptyMap(),
    bufferSeconds: Int = 30,
): ExoPlayer {
    @Suppress("UNUSED_VARIABLE")
    val targetUrl = url

    val httpFactory = DefaultHttpDataSource.Factory()
        .setUserAgent(userAgent ?: "TVApk/1.0 (Android)")
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(30_000)
        .setAllowCrossProtocolRedirects(true)

    val headers = buildMap {
        if (!referer.isNullOrBlank()) put("Referer", referer)
        putAll(extraHeaders)
    }
    if (headers.isNotEmpty()) httpFactory.setDefaultRequestProperties(headers)

    val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
    val factory = DefaultMediaSourceFactory(dataSourceFactory)

    val target = bufferSeconds.coerceIn(5, 120) * 1_000
    val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            (target / 2).coerceAtLeast(DefaultLoadControl.DEFAULT_MIN_BUFFER_MS),
            target.coerceAtLeast(DefaultLoadControl.DEFAULT_MAX_BUFFER_MS),
            DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
            DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
        )
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()

    val trackSelector = DefaultTrackSelector(context).apply {
        setParameters(
            buildUponParameters()
                .setPreferredAudioLanguage(null)
                .setPreferredTextLanguage(null)
                .setSelectUndeterminedTextLanguage(false)
                .setRendererDisabled(C.TRACK_TYPE_TEXT, true)
        )
    }

    return ExoPlayer.Builder(context)
        .setMediaSourceFactory(factory)
        .setLoadControl(loadControl)
        .setTrackSelector(trackSelector)
        .build()
}
