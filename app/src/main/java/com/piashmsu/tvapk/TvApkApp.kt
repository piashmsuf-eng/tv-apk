package com.piashmsu.tvapk

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.piashmsu.tvapk.data.AppContainer
import com.piashmsu.tvapk.util.CrashReporter
import com.piashmsu.tvapk.util.LocaleHelper
import com.piashmsu.tvapk.work.PlaylistRefreshWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TvApkApp : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = AppContainer(this)

        createNotificationChannels()

        appScope.launch {
            container.prefs.seedDefaultsIfNeeded()
            val interval = container.prefs.refreshInterval.first()
            PlaylistRefreshWorker.configure(this@TvApkApp, interval)

            // Re-apply user locale on every cold start so per-app language
            // survives process death.
            LocaleHelper.apply(this@TvApkApp, container.prefs.uiLanguage.first())

            if (container.prefs.crashReporter.first()) {
                CrashReporter.install(this@TvApkApp)
            }

            // Pre-warm channel logos: after a short delay, ask Coil to
            // download (in parallel) the first ~30 channel logos. This
            // means the Live TV grid renders immediately on first open,
            // logos already cached, no per-tile network round-trip.
            delay(2_000)
            val warmList = container.channelRepo.channels.first()
                .asSequence()
                .mapNotNull { it.logo }
                .filter { it.isNotBlank() }
                .distinct()
                .take(30)
                .toList()
            val loader = newImageLoader()
            warmList.forEach { url ->
                loader.enqueue(
                    ImageRequest.Builder(this@TvApkApp)
                        .data(url)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build()
                )
            }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_EPG,
                "EPG programmes",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Notifies when a favorite channel's programme starts."
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_RECORDING,
                "Recording",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Active stream recording."
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_UPDATE,
                "Update available",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "When a newer TV APK release is available."
            }
        )
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .crossfade(120)
        .okHttpClient { container.http }
        .memoryCache {
            MemoryCache.Builder(this)
                // Use up to 25% of available app heap for image bitmaps —
                // bigger memory cache = far fewer disk reads when scrolling
                // long channel lists.
                .maxSizePercent(0.25)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                // 128 MB persistent disk cache for logos + posters; survives
                // process death so subsequent launches feel instant.
                .maxSizeBytes(128L * 1024 * 1024)
                .build()
        }
        .respectCacheHeaders(false)
        // Decoders run in parallel (default is 4 — bumped to 8 for faster
        // poster grid + channel logo decode).
        .bitmapFactoryMaxParallelism(8)
        .build()

    companion object {
        lateinit var instance: TvApkApp
            private set

        const val CHANNEL_EPG = "epg-programmes"
        const val CHANNEL_RECORDING = "recording"
        const val CHANNEL_UPDATE = "update-available"
    }
}
