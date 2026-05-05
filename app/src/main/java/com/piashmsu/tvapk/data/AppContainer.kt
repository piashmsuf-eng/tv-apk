package com.piashmsu.tvapk.data

import android.content.Context
import com.piashmsu.tvapk.util.UpdateChecker
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.io.File
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {
    private val httpCache = Cache(
        directory = File(context.cacheDir, "http"),
        maxSize = 64L * 1024 * 1024,
    )

    val http: OkHttpClient = OkHttpClient.Builder()
        .cache(httpCache)
        // HTTP/2 multiplexes many requests over a single connection — big
        // win when fetching dozens of channel logos / EPG entries / TMDB
        // posters in parallel. Falls back to HTTP/1.1 automatically.
        .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
        // Larger pool keeps sockets warm across screens; idle keep-alive
        // avoids re-handshaking on every new image request.
        .connectionPool(ConnectionPool(maxIdleConnections = 16, keepAliveDuration = 5, TimeUnit.MINUTES))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        // No callTimeout: it would cap the entire Call lifecycle, killing
        // streaming XML/M3U downloads (EPG and large playlists) and any
        // long-lived response body read. The connect/read timeouts above
        // already guard against unresponsive servers; the player and
        // recorder additionally derive no-timeout clients for video.
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    val prefs: AppPrefs = AppPrefs(context)
    val channelRepo: ChannelRepository = ChannelRepository(context, http, prefs)
    val movieRepo: MovieRepository = MovieRepository(context, http, prefs)
    val epgRepo: EpgRepository = EpgRepository(http, prefs)
    val tmdb: TmdbClient = TmdbClient(http, prefs)
    val trakt: TraktScrobbler = TraktScrobbler(http, prefs)
    val updateChecker: UpdateChecker = UpdateChecker(http, prefs)
}
