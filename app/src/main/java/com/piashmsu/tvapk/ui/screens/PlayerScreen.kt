package com.piashmsu.tvapk.ui.screens

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.piashmsu.tvapk.MainActivity
import com.piashmsu.tvapk.cast.CastBridge
import com.piashmsu.tvapk.data.PlaybackTarget
import com.piashmsu.tvapk.data.PlaybackTargetHolder
import com.piashmsu.tvapk.data.RecentChannel
import com.piashmsu.tvapk.player.buildPlayerForUrl
import com.piashmsu.tvapk.record.RecordingArgs
import com.piashmsu.tvapk.record.RecordingService
import com.piashmsu.tvapk.record.RecordingState
import com.piashmsu.tvapk.ui.AppViewModel
import com.piashmsu.tvapk.util.ExternalPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val CONTROL_HIDE_DELAY_MS = 3500L

private enum class AspectMode(val label: String, val mode: Int) {
    Fit("Fit", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    Fill("Fill", AspectRatioFrameLayout.RESIZE_MODE_FILL),
    Zoom("Zoom", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    Width("16:9", AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH),
    Height("4:3", AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT),
}

private data class SleepOption(val label: String, val minutes: Int)

private val SLEEP_OPTIONS = listOf(
    SleepOption("Off", 0),
    SleepOption("15 min", 15),
    SleepOption("30 min", 30),
    SleepOption("60 min", 60),
    SleepOption("90 min", 90),
)

private val SPEED_OPTIONS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

private data class TrackEntry(val groupIndex: Int, val trackIndex: Int, val label: String)

@Composable
fun PlayerScreen(onBack: () -> Unit, isInPip: Boolean = false) {
    val vm: AppViewModel = viewModel(factory = AppViewModel.Factory)
    val target by PlaybackTargetHolder.current.collectAsState()

    if (target == null) {
        Column(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("No stream selected", color = Color.White)
            Spacer(Modifier.height(8.dp))
            FilledTonalIconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
            }
        }
        return
    }

    val current = target!!
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val ua = (current as? PlaybackTarget.LiveChannel)?.channel?.httpUserAgent
    val referer = (current as? PlaybackTarget.LiveChannel)?.channel?.httpReferer
    val headers = (current as? PlaybackTarget.LiveChannel)?.channel?.httpHeaders.orEmpty()

    val bufferSeconds by vm.playerBufferSeconds.collectAsState()
    val savedSpeed by vm.playbackSpeed.collectAsState()
    val autoSkipOffline by vm.autoSkipOffline.collectAsState()
    val externalPkg by vm.externalPlayerPkg.collectAsState()
    val watchPositions by vm.watchPositions.collectAsState()
    val tmdb = vm.container().tmdb

    val player = remember(current) {
        buildPlayerForUrl(context, current.streamUrl, ua, referer, headers, bufferSeconds).apply {
            setMediaItem(MediaItem.fromUri(current.streamUrl))
            playbackParameters = PlaybackParameters(savedSpeed)
            prepare()
            playWhenReady = true
        }
    }
    var isPlaying by remember { mutableStateOf(true) }
    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteractionAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var aspectMode by remember { mutableStateOf(AspectMode.Fit) }
    var sleepMenuOpen by remember { mutableStateOf(false) }
    var speedMenuOpen by remember { mutableStateOf(false) }
    var sleepDeadlineMs by remember { mutableLongStateOf(0L) }
    var sleepRemaining by remember { mutableStateOf("") }
    var seekIndicator by remember { mutableStateOf<Pair<Boolean, Long>?>(null) }
    var trackPanel by remember { mutableStateOf<TrackPanelKind?>(null) }
    var pinchZoom by remember { mutableFloatStateOf(1f) }
    var brightnessOverlay by remember { mutableFloatStateOf(-1f) }
    var volumeOverlay by remember { mutableFloatStateOf(-1f) }
    var resumePromptShown by remember { mutableStateOf(false) }
    var currentSpeed by remember { mutableFloatStateOf(savedSpeed) }
    var tracks by remember { mutableStateOf<Tracks?>(null) }

    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    LaunchedEffect(current) {
        vm.prefs.setLastPlayed(current.title)
        if (current is PlaybackTarget.LiveChannel) {
            val ch = current.channel
            vm.prefs.setLastPlayedChannel(ch.id)
            vm.pushRecent(
                RecentChannel(
                    channelId = ch.id,
                    name = ch.name,
                    logo = ch.logo,
                    streamUrl = ch.streamUrl,
                    timestamp = System.currentTimeMillis(),
                )
            )
        }

        if (current is PlaybackTarget.VideoOnDemand) {
            val saved = watchPositions[current.streamUrl] ?: 0L
            if (saved > 0) {
                resumePromptShown = true
                player.pause()
            }
            // TMDB enrich (best-effort, logged into in-memory cache)
            scope.launch {
                runCatching { tmdb.enrich(current.movie.title, current.movie.year) }
            }
        }
    }

    // Continue Watching: persist position every ~5 s while playing.
    LaunchedEffect(player, current) {
        while (true) {
            delay(5_000)
            if (current is PlaybackTarget.VideoOnDemand && player.isPlaying) {
                val pos = player.currentPosition.coerceAtLeast(0)
                val dur = player.duration.takeIf { it > 0 } ?: 0
                vm.saveWatchPosition(current.streamUrl, pos, dur)
            }
        }
    }

    LaunchedEffect(controlsVisible, isPlaying, lastInteractionAt) {
        if (controlsVisible && isPlaying && !isInPip) {
            delay(CONTROL_HIDE_DELAY_MS)
            if (System.currentTimeMillis() - lastInteractionAt >= CONTROL_HIDE_DELAY_MS - 50) {
                controlsVisible = false
            }
        }
    }

    LaunchedEffect(sleepDeadlineMs) {
        if (sleepDeadlineMs <= 0L) {
            sleepRemaining = ""
            return@LaunchedEffect
        }
        while (true) {
            val remaining = sleepDeadlineMs - System.currentTimeMillis()
            if (remaining <= 0L) {
                player.pause()
                sleepDeadlineMs = 0L
                sleepRemaining = ""
                break
            }
            val mins = (remaining / 60000L).toInt()
            val secs = ((remaining / 1000L) % 60L).toInt()
            sleepRemaining = "%d:%02d".format(mins, secs)
            delay(500)
        }
    }

    LaunchedEffect(seekIndicator) {
        if (seekIndicator != null) {
            delay(700)
            seekIndicator = null
        }
    }

    LaunchedEffect(brightnessOverlay) {
        if (brightnessOverlay >= 0f) {
            delay(900)
            brightnessOverlay = -1f
        }
    }
    LaunchedEffect(volumeOverlay) {
        if (volumeOverlay >= 0f) {
            delay(900)
            volumeOverlay = -1f
        }
    }

    if (!isInPip) EnterImmersive()

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (!playing) {
                    controlsVisible = true
                    lastInteractionAt = System.currentTimeMillis()
                }
            }

            override fun onTracksChanged(t: Tracks) {
                tracks = t
            }

            override fun onPlayerError(error: PlaybackException) {
                if (autoSkipOffline && current is PlaybackTarget.LiveChannel) {
                    val all = vm.channels.value
                    val idx = all.indexOfFirst { it.id == current.channel.id }
                    val statuses = vm.channelStatuses.value
                    val nextOnline = (idx + 1 until all.size).firstOrNull {
                        statuses[all[it].id] == com.piashmsu.tvapk.data.ChannelStatus.Online
                    }
                    if (nextOnline != null) {
                        val skip = all[nextOnline]
                        PlaybackTargetHolder.current.value = PlaybackTarget.LiveChannel(skip)
                    }
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            // Final save for VOD on dispose.
            if (current is PlaybackTarget.VideoOnDemand) {
                val pos = player.currentPosition.coerceAtLeast(0)
                val dur = player.duration.takeIf { it > 0 } ?: 0
                vm.saveWatchPosition(current.streamUrl, pos, dur)
            }
            player.release()
        }
    }

    val nowPlaying = (current as? PlaybackTarget.LiveChannel)?.let { lc ->
        val tvgId = lc.channel.tvgId
        vm.epgRepo.nowPlaying(tvgId)
    }
    val upNext = (current as? PlaybackTarget.LiveChannel)?.let { lc ->
        vm.epgRepo.upNext(lc.channel.tvgId)
    }

    val recordingState by RecordingService.state.collectAsState()

    fun touch() {
        controlsVisible = true
        lastInteractionAt = System.currentTimeMillis()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = pinchZoom.coerceIn(0.85f, 2.5f)
                    scaleY = pinchZoom.coerceIn(0.85f, 2.5f)
                }
                .pointerInput(player) {
                    detectTapGestures(
                        onTap = {
                            if (isInPip) return@detectTapGestures
                            controlsVisible = !controlsVisible
                            if (controlsVisible) lastInteractionAt = System.currentTimeMillis()
                        },
                        onDoubleTap = { offset ->
                            if (isInPip) return@detectTapGestures
                            val half = size.width / 2f
                            if (offset.x < half) {
                                player.seekBack()
                                seekIndicator = false to System.currentTimeMillis()
                            } else {
                                player.seekForward()
                                seekIndicator = true to System.currentTimeMillis()
                            }
                            touch()
                        },
                    )
                }
                .pointerInput(player, isInPip) {
                    if (isInPip) return@pointerInput
                    detectTransformGestures { _, _, zoom, _ ->
                        if (zoom != 1f) {
                            pinchZoom = (pinchZoom * zoom).coerceIn(0.85f, 2.5f)
                            touch()
                        }
                    }
                }
                .pointerInput(player, isInPip, maxVolume) {
                    if (isInPip) return@pointerInput
                    detectVerticalDragGestures(
                        onDragStart = {},
                        onDragEnd = {},
                    ) { change, dragAmount ->
                        val w = size.width.coerceAtLeast(1)
                        val isRight = change.position.x > w / 2f
                        // dragAmount up = negative; we want up to *increase*.
                        val delta = -dragAmount / size.height.coerceAtLeast(1).toFloat()
                        if (isRight) {
                            val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            val target = (current + delta * maxVolume).toInt()
                                .coerceIn(0, maxVolume)
                            audioManager.setStreamVolume(
                                AudioManager.STREAM_MUSIC,
                                target,
                                0,
                            )
                            volumeOverlay = target.toFloat() / maxVolume
                        } else {
                            val activity = context as? Activity
                            if (activity != null) {
                                val lp = activity.window.attributes
                                val cur = if (lp.screenBrightness in 0f..1f) lp.screenBrightness
                                else getSystemBrightness(context)
                                val target = (cur + delta).coerceIn(0.05f, 1f)
                                lp.screenBrightness = target
                                activity.window.attributes = lp
                                brightnessOverlay = target
                            }
                        }
                        touch()
                    }
                },
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    resizeMode = aspectMode.mode
                }
            },
            update = { view ->
                view.resizeMode = aspectMode.mode
            },
        )

        seekIndicator?.let { (forward, _) ->
            Box(
                modifier = Modifier
                    .align(if (forward) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(horizontal = 36.dp)
                    .clip(CircleShape)
                    .background(Color(0xCC0B0E22))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (forward) Icons.Outlined.FastForward else Icons.Outlined.FastRewind,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (forward) "+10s" else "-10s",
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
        }

        // Volume / brightness overlays — fade after ~900 ms.
        if (volumeOverlay >= 0f) {
            GestureOverlay(
                icon = Icons.Outlined.VolumeUp,
                value = volumeOverlay,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
        if (brightnessOverlay >= 0f) {
            GestureOverlay(
                icon = Icons.Outlined.BrightnessMedium,
                value = brightnessOverlay,
                modifier = Modifier.align(Alignment.CenterStart),
            )
        }

        AnimatedVisibility(
            visible = controlsVisible && !isInPip,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x66000000)),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalIconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(Modifier.size(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            current.title,
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (nowPlaying != null) {
                            Text(
                                "● ${nowPlaying.title}",
                                color = MaterialTheme.colorScheme.secondary,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        } else {
                            Text(
                                "Streaming via ExoPlayer",
                                color = Color(0xCCBFC4D6),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }

                    // Picture-in-Picture
                    FilledTonalIconButton(
                        onClick = {
                            (context as? MainActivity)?.requestPip()
                            touch()
                        },
                        colors = darkButtonColors(),
                    ) {
                        Icon(Icons.Outlined.PictureInPictureAlt, contentDescription = "Picture-in-Picture")
                    }
                    Spacer(Modifier.size(6.dp))

                    // Speed
                    Box {
                        FilledTonalIconButton(
                            onClick = { speedMenuOpen = true; touch() },
                            colors = darkButtonColors(
                                containerColor = if (currentSpeed != 1f) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                else Color(0x99000000),
                            ),
                        ) {
                            Icon(Icons.Outlined.Speed, contentDescription = "Playback speed")
                        }
                        DropdownMenu(
                            expanded = speedMenuOpen,
                            onDismissRequest = { speedMenuOpen = false },
                        ) {
                            SPEED_OPTIONS.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text("${s}x") },
                                    onClick = {
                                        currentSpeed = s
                                        player.playbackParameters = PlaybackParameters(s)
                                        vm.setPlaybackSpeed(s)
                                        speedMenuOpen = false
                                        touch()
                                    },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.size(6.dp))

                    // Tracks (audio + subs)
                    FilledTonalIconButton(
                        onClick = { trackPanel = TrackPanelKind.Audio; touch() },
                        colors = darkButtonColors(),
                    ) {
                        Icon(Icons.Outlined.MusicNote, contentDescription = "Audio tracks")
                    }
                    Spacer(Modifier.size(6.dp))
                    FilledTonalIconButton(
                        onClick = { trackPanel = TrackPanelKind.Subtitle; touch() },
                        colors = darkButtonColors(),
                    ) {
                        Icon(Icons.Outlined.ClosedCaption, contentDescription = "Subtitles")
                    }
                    Spacer(Modifier.size(6.dp))
                    FilledTonalIconButton(
                        onClick = { trackPanel = TrackPanelKind.Quality; touch() },
                        colors = darkButtonColors(),
                    ) {
                        Icon(Icons.Outlined.HighQuality, contentDescription = "Quality")
                    }
                    Spacer(Modifier.size(6.dp))

                    // Cast
                    FilledTonalIconButton(
                        onClick = {
                            val ok = CastBridge.loadMedia(
                                context = context,
                                streamUrl = current.streamUrl,
                                title = current.title,
                                artUrl = (current as? PlaybackTarget.VideoOnDemand)?.movie?.poster,
                            )
                            if (!ok) {
                                // No active cast session — try to launch the
                                // device picker; the SDK no-ops if Play
                                // services are missing.
                                runCatching { CastBridge.castContext(context) }
                            }
                            touch()
                        },
                        colors = darkButtonColors(),
                    ) {
                        Icon(Icons.Outlined.Cast, contentDescription = "Cast")
                    }
                    Spacer(Modifier.size(6.dp))

                    // External player
                    FilledTonalIconButton(
                        onClick = {
                            ExternalPlayer.open(
                                context = context,
                                streamUrl = current.streamUrl,
                                title = current.title,
                                packageName = externalPkg,
                            )
                            touch()
                        },
                        colors = darkButtonColors(),
                    ) {
                        Icon(Icons.Outlined.OpenInNew, contentDescription = "Open externally")
                    }
                    Spacer(Modifier.size(6.dp))

                    Box {
                        FilledTonalIconButton(
                            onClick = { sleepMenuOpen = true; touch() },
                            colors = darkButtonColors(
                                containerColor = if (sleepDeadlineMs > 0L) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                else Color(0x99000000),
                            ),
                        ) {
                            Icon(Icons.Outlined.Bedtime, contentDescription = "Sleep timer")
                        }
                        DropdownMenu(
                            expanded = sleepMenuOpen,
                            onDismissRequest = { sleepMenuOpen = false },
                        ) {
                            SLEEP_OPTIONS.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt.label) },
                                    onClick = {
                                        sleepDeadlineMs = if (opt.minutes <= 0) 0L
                                            else System.currentTimeMillis() + opt.minutes * 60_000L
                                        sleepMenuOpen = false
                                        touch()
                                    },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.size(6.dp))
                    FilledTonalIconButton(
                        onClick = {
                            aspectMode = AspectMode.values()[(aspectMode.ordinal + 1) % AspectMode.values().size]
                            touch()
                        },
                        colors = darkButtonColors(),
                    ) {
                        Icon(Icons.Outlined.AspectRatio, contentDescription = "Aspect ratio")
                    }
                    Spacer(Modifier.size(6.dp))

                    if (current is PlaybackTarget.LiveChannel) {
                        val recordingActive = recordingState is RecordingState.Active
                        FilledTonalIconButton(
                            onClick = {
                                touch()
                                if (recordingActive) {
                                    RecordingService.stop(context)
                                } else {
                                    RecordingService.start(
                                        context,
                                        RecordingArgs(
                                            streamUrl = current.streamUrl,
                                            title = current.title,
                                            userAgent = ua,
                                            referer = referer,
                                            extraHeaders = headers,
                                        ),
                                    )
                                }
                            },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = if (recordingActive) Color(0xFFFF3E5C) else Color(0x99000000),
                                contentColor = Color.White,
                            ),
                        ) {
                            Icon(
                                if (recordingActive) Icons.Outlined.Stop else Icons.Outlined.FiberManualRecord,
                                contentDescription = if (recordingActive) "Stop recording" else "Record",
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalIconButton(onClick = { player.seekBack(); touch() }) {
                        Icon(Icons.Outlined.FastRewind, contentDescription = "Rewind 10s")
                    }
                    FilledTonalIconButton(
                        onClick = {
                            if (isPlaying) player.pause() else player.play()
                            touch()
                        },
                        modifier = Modifier.size(72.dp),
                    ) {
                        Icon(
                            if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                            contentDescription = "Play/pause",
                            modifier = Modifier.size(36.dp),
                        )
                    }
                    FilledTonalIconButton(onClick = { player.seekForward(); touch() }) {
                        Icon(Icons.Outlined.FastForward, contentDescription = "Fast-forward 10s")
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (sleepRemaining.isNotBlank()) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color(0x99000000))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.Bedtime,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.size(6.dp))
                            Text(
                                "Sleep in $sleepRemaining",
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                    AssistChip(
                        onClick = { touch() },
                        enabled = false,
                        label = {
                            Text(
                                "Aspect: ${aspectMode.label} • ${currentSpeed}x",
                                color = Color.White,
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Color(0x66000000),
                            disabledContainerColor = Color(0x66000000),
                            disabledLabelColor = Color.White,
                        ),
                    )
                    (current as? PlaybackTarget.LiveChannel)?.let { lc ->
                        if (lc.channel.hasCatchup) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0x99000000))
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Outlined.Schedule,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.size(6.dp))
                                Text(
                                    "Catch-up: rewind up to ${lc.channel.catchupDays} days",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                    if (upNext != null) {
                        Text(
                            "Up next: ${upNext.title} • ${formatTime(upNext.start)}",
                            color = Color(0xCCBFC4D6),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    (recordingState as? RecordingState.Active)?.let {
                        Text(
                            "● Recording: ${it.bytesWritten / 1024 / 1024} MB written",
                            color = Color(0xFFFF3E5C),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    (recordingState as? RecordingState.Finished)?.let {
                        Text(
                            "Recording saved → ${it.output}",
                            color = Color(0xFF8AE070),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    Text(
                        "Tap to toggle controls • Pinch to zoom • Swipe up/down: brightness (left) / volume (right)",
                        color = Color(0x99BFC4D6),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }

        // Track panel (audio / subtitle / quality)
        trackPanel?.let { kind ->
            TrackSelectionPanel(
                kind = kind,
                player = player,
                tracks = tracks,
                onClose = { trackPanel = null },
            )
        }

        // Resume prompt for VOD
        if (resumePromptShown && current is PlaybackTarget.VideoOnDemand) {
            val saved = watchPositions[current.streamUrl] ?: 0L
            AlertDialog(
                onDismissRequest = { resumePromptShown = false; player.play() },
                title = { Text("Resume?") },
                text = { Text("Continue from ${formatPosition(saved)}?") },
                confirmButton = {
                    TextButton(onClick = {
                        player.seekTo(saved)
                        player.play()
                        resumePromptShown = false
                    }) { Text("Resume") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        player.seekTo(0)
                        player.play()
                        resumePromptShown = false
                    }) { Text("Start over") }
                },
            )
        }
    }
}

/** Audio / subtitle / quality picker rendered as a side sheet. */
@Composable
private fun TrackSelectionPanel(
    kind: TrackPanelKind,
    player: ExoPlayer,
    tracks: Tracks?,
    onClose: () -> Unit,
) {
    val type = when (kind) {
        TrackPanelKind.Audio -> C.TRACK_TYPE_AUDIO
        TrackPanelKind.Subtitle -> C.TRACK_TYPE_TEXT
        TrackPanelKind.Quality -> C.TRACK_TYPE_VIDEO
    }
    val groups = tracks?.groups?.filter { it.type == type }.orEmpty()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .pointerInput(Unit) { detectTapGestures(onTap = { onClose() }) },
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(320.dp)
                .background(Color(0xEE0B0E22))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                when (kind) {
                    TrackPanelKind.Audio -> "Audio track"
                    TrackPanelKind.Subtitle -> "Subtitle"
                    TrackPanelKind.Quality -> "Quality"
                },
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
            // Auto / off entry
            DropdownMenuItem(
                text = {
                    Text(
                        if (kind == TrackPanelKind.Subtitle) "Off"
                        else "Auto",
                        color = Color.White,
                    )
                },
                onClick = {
                    val params = player.trackSelectionParameters
                    val builder = params.buildUpon()
                        .clearOverridesOfType(type)
                    if (kind == TrackPanelKind.Subtitle) {
                        builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    } else if (kind == TrackPanelKind.Quality) {
                        builder.setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
                    }
                    player.trackSelectionParameters = builder.build()
                    onClose()
                },
            )
            if (groups.isEmpty()) {
                Text("No tracks reported", color = Color.White.copy(alpha = 0.7f))
            } else {
                groups.forEachIndexed { gi, group ->
                    for (ti in 0 until group.length) {
                        val format = group.getTrackFormat(ti)
                        val label = formatLabel(kind, format)
                        DropdownMenuItem(
                            text = {
                                Text(
                                    label,
                                    color = if (group.isTrackSelected(ti)) MaterialTheme.colorScheme.primary
                                    else Color.White,
                                )
                            },
                            onClick = {
                                val override = TrackSelectionOverride(group.mediaTrackGroup, ti)
                                val newParams: TrackSelectionParameters = player.trackSelectionParameters
                                    .buildUpon()
                                    .setOverrideForType(override)
                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, kind != TrackPanelKind.Subtitle)
                                    .build()
                                player.trackSelectionParameters = newParams
                                onClose()
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun formatLabel(kind: TrackPanelKind, format: androidx.media3.common.Format): String {
    return when (kind) {
        TrackPanelKind.Quality -> {
            val height = format.height.takeIf { it > 0 }
            val bitrate = format.bitrate.takeIf { it > 0 }?.let { "${it / 1000} kbps" }
            buildList {
                if (height != null) add("${height}p")
                if (bitrate != null) add(bitrate)
            }.joinToString(" • ").ifBlank { "Variant" }
        }
        TrackPanelKind.Audio -> {
            val lang = format.language ?: "und"
            val codec = format.codecs ?: format.sampleMimeType ?: ""
            "$lang • $codec".trim('•', ' ')
        }
        TrackPanelKind.Subtitle -> {
            val lang = format.language ?: "und"
            val label = format.label ?: ""
            if (label.isNotBlank()) "$lang • $label" else lang
        }
    }
}

private enum class TrackPanelKind { Audio, Subtitle, Quality }

@Composable
private fun darkButtonColors(
    containerColor: Color = Color(0x99000000),
) = IconButtonDefaults.filledTonalIconButtonColors(
    containerColor = containerColor,
    contentColor = Color.White,
)

@Composable
private fun GestureOverlay(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(horizontal = 36.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xCC0B0E22))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(6.dp))
        Text("${(value * 100).toInt()}%", color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
}

private fun getSystemBrightness(context: Context): Float {
    return runCatching {
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
    }.getOrDefault(0.5f)
}

@Composable
private fun EnterImmersive() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = context as? Activity ?: return@DisposableEffect onDispose { }
        val window = activity.window
        val previousOrientation = activity.requestedOrientation
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        activity.requestedOrientation =
            ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
        // Keep the screen on while playing — without this Android can dim the
        // display after a few minutes of inactivity (no touches).
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            WindowCompat.setDecorFitsSystemWindows(window, true)
            activity.requestedOrientation = previousOrientation
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            // Reset the per-window brightness override.
            val lp = window.attributes
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            window.attributes = lp
        }
    }
}

private fun formatTime(epoch: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epoch))

private fun formatPosition(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total / 60) % 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
