package com.piashmsu.tvapk.data

import android.content.Context
import com.piashmsu.tvapk.util.UpdateChecker
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {
    private val httpCache = Cache(
        directory = File(context.cacheDir, "http"),
        maxSize = 32L * 1024 * 1024,
    )

    val http: OkHttpClient = OkHttpClient.Builder()
        .cache(httpCache)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
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
