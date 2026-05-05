package com.piashmsu.tvapk.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.piashmsu.tvapk.MainActivity
import com.piashmsu.tvapk.R
import com.piashmsu.tvapk.TvApkApp

/**
 * Broadcast receiver fired by [EpgNotificationScheduler] when a scheduled
 * favorite-channel programme is about to start. Posts a system notification
 * that, when tapped, opens MainActivity (which can route to that channel
 * via the standard nav graph).
 */
class EpgAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME).orEmpty()
        val programme = intent.getStringExtra(EXTRA_PROGRAMME).orEmpty()
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, channelName.hashCode())

        val openIntent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(
            context,
            notifId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val title = context.getString(R.string.notif_program_start_title, channelName)
        val text = if (programme.isNotBlank()) programme else channelName
        val notif = NotificationCompat.Builder(context, TvApkApp.CHANNEL_EPG)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { mgr.notify(notifId, notif) }
    }

    companion object {
        const val EXTRA_CHANNEL_NAME = "channel_name"
        const val EXTRA_PROGRAMME = "programme"
        const val EXTRA_NOTIF_ID = "notif_id"
    }
}
