package com.piashmsu.tvapk.player

import android.content.Context
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Builds an ExoPlayer instance configured to handle the dominant IPTV stream
 * formats (HLS, MPEG-DASH, SmoothStreaming, RTSP, and progressive MP4/MKV).
 *
 * `userAgent`, `referer`, and `extraHeaders` are forwarded to the HTTP data
 * source so DRM-free streams that require a specific User-Agent / Referer
 * (a common protection against hot-linking) play correctly.
 *
 * `bufferSeconds` is the user-configurable target buffer in seconds.
 *
 * `httpClient` is an optional shared OkHttp client. Reusing the app-wide
 * client gives us HTTP/2 multiplexing, persistent connections, and the
 * shared 64 MB disk cache configured in [com.piashmsu.tvapk.data.AppContainer].
 *
 * Channel start latency is tuned for IPTV / live HLS:
 *   - bufferForPlaybackMs = 800 ms (default 2500 ms) — start playing as
 *     soon as ~800 ms is buffered instead of waiting 2.5 s of black.
 *   - bufferForPlaybackAfterRebufferMs = 2000 ms — quicker resume after
 *     a stall.
 *   - minBufferMs = 5 s, maxBufferMs scales with user setting (5–120 s).
 */
fun buildPlayerForUrl(
    context: Context,
    url: String,
    userAgent: String? = null,
    referer: String? = null,
    extraHeaders: Map<String, String> = emptyMap(),
    bufferSeconds: Int = 30,
    httpClient: OkHttpClient? = null,
    fastStart: Boolean = true,
): ExoPlayer {
    @Suppress("UNUSED_VARIABLE")
    val targetUrl = url

    val httpFactory: HttpDataSource.Factory = if (httpClient != null) {
        // Derive a player-specific client from the shared one with no
        // call timeout. ExoPlayer keeps a single OkHttp Call open for the
        // entire playback duration on progressive (MP4/MKV) streams; the
        // shared client's 60 s callTimeout would hard-cancel that.
        // ConnectionPool, dispatcher, cache, and protocols are all
        // inherited via newBuilder() so we keep HTTP/2 multiplexing.
        val playerClient = httpClient.newBuilder()
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
        OkHttpDataSource.Factory(playerClient)
            .setUserAgent(userAgent ?: "TVApk/1.0 (Android)")
    } else {
        androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent ?: "TVApk/1.0 (Android)")
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(20_000)
            .setAllowCrossProtocolRedirects(true)
    }

    val headers = buildMap {
        if (!referer.isNullOrBlank()) put("Referer", referer)
        putAll(extraHeaders)
    }
    if (headers.isNotEmpty()) httpFactory.setDefaultRequestProperties(headers)

    val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
    val factory = DefaultMediaSourceFactory(dataSourceFactory)

    val targetMaxMs = bufferSeconds.coerceIn(5, 120) * 1_000
    // We want a low minimum buffer (5s) for fast channel start. Media3's
    // DEFAULT_MIN_BUFFER_MS is 50 s, so we deliberately do NOT inherit it.
    // We just clamp to a sane floor so really low user settings don't break.
    val minBufferMs = 5_000
    val maxBufferMs = targetMaxMs.coerceAtLeast(minBufferMs)
    // Fast start: kick off as soon as ~800 ms is buffered; otherwise the
    // Media3 default of 2500 ms applies.
    val playbackStartMs = if (fastStart) 800 else DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS
    val playbackResumeMs = if (fastStart) 2_000 else DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
    val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(minBufferMs, maxBufferMs, playbackStartMs, playbackResumeMs)
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()

    val trackSelector = DefaultTrackSelector(context).apply {
        setParameters(
            buildUponParameters()
                .setPreferredAudioLanguage(null)
                .setPreferredTextLanguage(null)
                .setSelectUndeterminedTextLanguage(false)
        )
    }

    return ExoPlayer.Builder(context)
        .setMediaSourceFactory(factory)
        .setLoadControl(loadControl)
        .setTrackSelector(trackSelector)
        .build()
}
