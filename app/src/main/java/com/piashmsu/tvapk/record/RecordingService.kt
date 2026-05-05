package com.piashmsu.tvapk.record

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.piashmsu.tvapk.MainActivity
import com.piashmsu.tvapk.R
import com.piashmsu.tvapk.TvApkApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Foreground service that performs background recording of a live stream.
 *
 * Exactly one recording can run at a time. While active a persistent
 * notification with a "Stop" action is shown — the user can stop the
 * recording from anywhere on the device.
 */
class RecordingService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var job: Job? = null
    @Volatile private var stopRequested = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopRequested = true
                stopRecording(success = true)
                return START_NOT_STICKY
            }
            ACTION_START -> {
                if (job?.isActive == true) return START_NOT_STICKY
                val args = RecordingArgs.fromIntent(intent) ?: run {
                    stopSelf(); return START_NOT_STICKY
                }
                startRecording(args)
            }
        }
        return START_NOT_STICKY
    }

    private fun startRecording(args: RecordingArgs) {
        ensureChannel()
        val notif = buildNotification(args.title, "Starting recording…")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID, notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIF_ID, notif)
        }
        _state.value = RecordingState.Active(args.title, 0L)

        job = scope.launch {
            val container = TvApkApp.instance.container
            val recorder = Recorder(
                context = applicationContext,
                sharedHttp = container.http,
                streamUrl = args.streamUrl,
                title = args.title,
                userAgent = args.userAgent,
                referer = args.referer,
                extraHeaders = args.extraHeaders,
            )
            val result = recorder.run(
                shouldStop = { stopRequested },
                onProgressBytes = { bytes ->
                    val mb = bytes / 1024 / 1024
                    _state.value = RecordingState.Active(args.title, bytes)
                    notify("${"%,d".format(mb)} MB written")
                },
            )
            _state.value =
                if (result != null) RecordingState.Finished(args.title, result)
                else RecordingState.Failed(args.title, "Recording failed")
            stopRecording(success = result != null)
        }
    }

    private fun stopRecording(success: Boolean) {
        job?.cancel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun notify(progressText: String) {
        val title = (state.value as? RecordingState.Active)?.title ?: "Recording"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(title, progressText))
    }

    private fun buildNotification(title: String, body: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val contentPi = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPi = PendingIntent.getService(
            this, 1,
            Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Recording: $title")
            .setContentText(body)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentPi)
            .addAction(0, "Stop", stopPi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Live recording",
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply { description = "Shows ongoing live-TV recordings." }
                )
            }
        }
    }

    companion object {
        const val ACTION_START = "com.piashmsu.tvapk.RECORD_START"
        const val ACTION_STOP = "com.piashmsu.tvapk.RECORD_STOP"
        private const val CHANNEL_ID = "tv_apk_recording"
        private const val NOTIF_ID = 1001

        private val _state = MutableStateFlow<RecordingState>(RecordingState.Idle)
        val state: StateFlow<RecordingState> = _state.asStateFlow()

        fun start(context: Context, args: RecordingArgs) {
            val intent = Intent(context, RecordingService::class.java)
                .setAction(ACTION_START)
            args.applyTo(intent)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, RecordingService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}

sealed interface RecordingState {
    data object Idle : RecordingState
    data class Active(val title: String, val bytesWritten: Long) : RecordingState
    data class Finished(val title: String, val output: String) : RecordingState
    data class Failed(val title: String, val message: String) : RecordingState
}

data class RecordingArgs(
    val streamUrl: String,
    val title: String,
    val userAgent: String?,
    val referer: String?,
    val extraHeaders: Map<String, String>,
) {
    fun applyTo(intent: Intent) {
        intent.putExtra("streamUrl", streamUrl)
        intent.putExtra("title", title)
        intent.putExtra("userAgent", userAgent)
        intent.putExtra("referer", referer)
        intent.putExtra("headersKeys", extraHeaders.keys.toTypedArray())
        intent.putExtra("headersValues", extraHeaders.values.toTypedArray())
    }

    companion object {
        fun fromIntent(intent: Intent): RecordingArgs? {
            val url = intent.getStringExtra("streamUrl") ?: return null
            val title = intent.getStringExtra("title") ?: "Recording"
            val ks = intent.getStringArrayExtra("headersKeys").orEmpty()
            val vs = intent.getStringArrayExtra("headersValues").orEmpty()
            val map = ks.zip(vs).toMap()
            return RecordingArgs(
                streamUrl = url,
                title = title,
                userAgent = intent.getStringExtra("userAgent"),
                referer = intent.getStringExtra("referer"),
                extraHeaders = map,
            )
        }
    }
}
