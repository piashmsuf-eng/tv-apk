package com.piashmsu.tvapk.ui

import android.content.Context
import android.os.Build
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.piashmsu.tvapk.BuildConfig
import com.piashmsu.tvapk.TvApkApp
import com.piashmsu.tvapk.data.AppContainer
import com.piashmsu.tvapk.data.AppPrefs
import com.piashmsu.tvapk.data.ChannelRepository
import com.piashmsu.tvapk.data.EpgRepository
import com.piashmsu.tvapk.data.LauncherVariant
import com.piashmsu.tvapk.data.MovieRepository
import com.piashmsu.tvapk.data.PlaylistSource
import com.piashmsu.tvapk.data.RecentChannel
import com.piashmsu.tvapk.data.RefreshInterval
import com.piashmsu.tvapk.data.SortMode
import com.piashmsu.tvapk.data.ThemePalette
import com.piashmsu.tvapk.data.UiLanguage
import com.piashmsu.tvapk.security.PinManager
import com.piashmsu.tvapk.util.LauncherIconManager
import com.piashmsu.tvapk.util.LocaleHelper
import com.piashmsu.tvapk.work.PlaylistRefreshWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppViewModel(
    private val container: AppContainer,
    private val app: TvApkApp,
) : ViewModel() {

    val prefs: AppPrefs get() = container.prefs
    val channelRepo: ChannelRepository get() = container.channelRepo
    val movieRepo: MovieRepository get() = container.movieRepo
    val epgRepo: EpgRepository get() = container.epgRepo

    fun container(): AppContainer = container

    val playlistSources = prefs.playlistSources
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val movieCatalogUrl = prefs.movieCatalogUrl
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val refreshInterval = prefs.refreshInterval
        .stateIn(viewModelScope, SharingStarted.Eagerly, RefreshInterval.Off)
    val lastAutoRefresh = prefs.lastAutoRefresh
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val favorites = prefs.favorites
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val recents = prefs.recents
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val watchlist = prefs.watchlist
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val watchPositions = prefs.watchPositions
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val watchCounter = prefs.watchCounter
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val themePalette = prefs.themePalette
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemePalette.DefaultVibe)
    val launcherVariant = prefs.launcherVariant
        .stateIn(viewModelScope, SharingStarted.Eagerly, LauncherVariant.Default)
    val uiLanguage = prefs.uiLanguage
        .stateIn(viewModelScope, SharingStarted.Eagerly, UiLanguage.System)

    val sortMode = prefs.sortMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, SortMode.Default)
    val filterCountry = prefs.filterCountry
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val filterLanguage = prefs.filterLanguage
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val pinHash = prefs.pinHash
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val lockedGroups = prefs.lockedGroups
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    private val _unlockedGroups = MutableStateFlow<Set<String>>(emptySet())
    val unlockedGroups = _unlockedGroups.asStateFlow()

    val externalPlayerPkg = prefs.externalPlayerPkg
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val playerBufferSeconds = prefs.playerBufferSeconds
        .stateIn(viewModelScope, SharingStarted.Eagerly, 30)
    val autoSkipOffline = prefs.autoSkipOffline
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val playbackSpeed = prefs.playbackSpeed
        .stateIn(viewModelScope, SharingStarted.Eagerly, 1f)
    val audioBoostPercent = prefs.audioBoostPercent
        .stateIn(viewModelScope, SharingStarted.Eagerly, 100)
    val subtitleScalePercent = prefs.subtitleScalePercent
        .stateIn(viewModelScope, SharingStarted.Eagerly, 100)
    val fastStart = prefs.fastStart
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val tmdbKey = prefs.tmdbKey
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val traktKey = prefs.traktKey
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val notificationsEpg = prefs.notificationsEpg
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val crashReporter = prefs.crashReporter
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val updateLatestTag = prefs.updateLatestTag
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val onboarded = prefs.onboarded
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val channelState = channelRepo.state
    val movieState = movieRepo.state
    val epgState = epgRepo.state
    val channels = channelRepo.channels
    val movies = movieRepo.movies
    val epg = epgRepo.byChannel
    val channelStatuses = channelRepo.statuses
    val probeProgress = channelRepo.probeProgress

    init {
        viewModelScope.launch {
            channelRepo.refresh()
            // First-load auto-probe in the background. We only auto-probe
            // if the user hasn't probed in the last 6 h, to avoid hammering
            // the network on every cold start.
            val last = prefs.probeLastRunMs.first()
            val stale = System.currentTimeMillis() - last > 6 * 60 * 60 * 1000L
            if (stale) channelRepo.probeReachability()
        }
        viewModelScope.launch { movieRepo.refresh() }
        viewModelScope.launch { epgRepo.refresh() }
    }

    fun saveMovieCatalogUrl(url: String) {
        viewModelScope.launch {
            prefs.setMovieCatalogUrl(url)
            movieRepo.refresh()
        }
    }

    fun upsertPlaylistSource(source: PlaylistSource) {
        viewModelScope.launch {
            prefs.upsertPlaylistSource(source)
            channelRepo.refresh()
            epgRepo.refresh()
        }
    }

    fun togglePlaylistEnabled(source: PlaylistSource) {
        viewModelScope.launch {
            prefs.upsertPlaylistSource(source.copy(enabled = !source.enabled))
            channelRepo.refresh()
            epgRepo.refresh()
        }
    }

    fun removePlaylistSource(id: String) {
        viewModelScope.launch {
            prefs.removePlaylistSource(id)
            channelRepo.refresh()
            epgRepo.refresh()
        }
    }

    fun setRefreshInterval(value: RefreshInterval) {
        viewModelScope.launch {
            prefs.setRefreshInterval(value)
            PlaylistRefreshWorker.configure(app, value)
        }
    }

    fun toggleFavorite(channelId: String) {
        viewModelScope.launch { prefs.toggleFavorite(channelId) }
    }

    fun pushRecent(entry: RecentChannel) {
        viewModelScope.launch { prefs.pushRecent(entry) }
    }

    fun refreshChannels() {
        viewModelScope.launch { runCatching { channelRepo.refresh() } }
    }
    fun refreshMovies() {
        viewModelScope.launch { runCatching { movieRepo.refresh() } }
    }
    fun refreshEpg() {
        viewModelScope.launch { runCatching { epgRepo.refresh() } }
    }
    fun probeChannels() {
        viewModelScope.launch { runCatching { channelRepo.probeReachability() } }
    }

    fun setThemePalette(palette: ThemePalette) {
        viewModelScope.launch { prefs.setThemePalette(palette) }
    }
    fun setLauncherVariant(variant: LauncherVariant) {
        viewModelScope.launch {
            prefs.setLauncherVariant(variant)
            LauncherIconManager.apply(app, variant)
        }
    }
    fun setUiLanguage(lang: UiLanguage) {
        viewModelScope.launch {
            prefs.setUiLanguage(lang)
            LocaleHelper.apply(app, lang)
        }
    }

    fun setSortMode(mode: SortMode) {
        viewModelScope.launch { prefs.setSortMode(mode) }
    }
    fun setFilterCountry(value: String) {
        viewModelScope.launch { prefs.setFilterCountry(value) }
    }
    fun setFilterLanguage(value: String) {
        viewModelScope.launch { prefs.setFilterLanguage(value) }
    }

    fun setPin(pin: String) {
        viewModelScope.launch { prefs.setPinHash(if (pin.isBlank()) "" else PinManager.hash(pin)) }
    }
    fun setLockedGroups(groups: Set<String>) {
        viewModelScope.launch { prefs.setLockedGroups(groups) }
    }
    fun unlockGroup(group: String, pin: String): Boolean {
        val ok = PinManager.verify(pin, pinHash.value)
        if (ok) _unlockedGroups.value = _unlockedGroups.value + group
        return ok
    }
    fun relockAll() {
        _unlockedGroups.value = emptySet()
    }

    fun setExternalPlayerPkg(pkg: String) {
        viewModelScope.launch { prefs.setExternalPlayerPkg(pkg) }
    }
    fun setPlayerBufferSeconds(seconds: Int) {
        viewModelScope.launch { prefs.setPlayerBufferSeconds(seconds) }
    }
    fun setAutoSkipOffline(value: Boolean) {
        viewModelScope.launch { prefs.setAutoSkipOffline(value) }
    }
    fun setPlaybackSpeed(speed: Float) {
        viewModelScope.launch { prefs.setPlaybackSpeed(speed) }
    }
    fun setAudioBoostPercent(value: Int) {
        viewModelScope.launch { prefs.setAudioBoostPercent(value) }
    }
    fun setSubtitleScalePercent(value: Int) {
        viewModelScope.launch { prefs.setSubtitleScalePercent(value) }
    }
    fun setFastStart(value: Boolean) {
        viewModelScope.launch { prefs.setFastStart(value) }
    }

    fun saveWatchPosition(streamUrl: String, positionMs: Long, durationMs: Long) {
        viewModelScope.launch { prefs.setWatchPosition(streamUrl, positionMs, durationMs) }
    }
    fun toggleWatchlist(movieId: String) {
        viewModelScope.launch { prefs.toggleWatchlist(movieId) }
    }
    fun bumpWatchCounter(itemId: String) {
        viewModelScope.launch { prefs.bumpWatchCounter(itemId) }
    }

    fun setTmdbKey(key: String) {
        viewModelScope.launch { prefs.setTmdbKey(key) }
    }
    fun setTraktKey(key: String) {
        viewModelScope.launch { prefs.setTraktKey(key) }
    }

    fun setNotificationsEpg(value: Boolean) {
        viewModelScope.launch { prefs.setNotificationsEpg(value) }
    }
    fun setCrashReporter(value: Boolean) {
        viewModelScope.launch { prefs.setCrashReporter(value) }
    }

    fun checkForUpdates(onResult: (com.piashmsu.tvapk.util.UpdateChecker.Result?) -> Unit) {
        viewModelScope.launch {
            val r = container.updateChecker.check(BuildConfig.VERSION_NAME)
            onResult(r)
        }
    }

    fun markOnboarded() {
        viewModelScope.launch { prefs.setOnboarded(true) }
    }
    fun resetOnboarding() {
        viewModelScope.launch { prefs.setOnboarded(false) }
    }

    /**
     * Export every persisted preference (settings, favorites, sources,
     * watchlist) to a JSON file inside the app's external "Downloads"
     * directory and return a content:// URI suitable for ACTION_SEND. Runs
     * off the main thread.
     */
    suspend fun exportSettings(context: Context): android.net.Uri? = withContext(Dispatchers.IO) {
        val json = prefs.exportToJson()
        val dir = File(context.filesDir, "exports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val out = File(dir, "tvapk-backup-$stamp.json")
        out.writeText(json)
        runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", out)
        }.getOrNull()
    }

    suspend fun importSettings(context: Context, uri: android.net.Uri): Boolean =
        withContext(Dispatchers.IO) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.use {
                    it.readBytes().toString(Charsets.UTF_8)
                }
            }.getOrNull() ?: return@withContext false
            val ok = prefs.importFromJson(text)
            if (ok) {
                channelRepo.refresh()
                movieRepo.refresh()
                epgRepo.refresh()
            }
            ok
        }

    fun appVersion(): String = BuildConfig.VERSION_NAME

    @Suppress("unused")
    fun appAndroidSdk(): Int = Build.VERSION.SDK_INT

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = TvApkApp.instance
                return AppViewModel(app.container, app) as T
            }
        }
    }
}
