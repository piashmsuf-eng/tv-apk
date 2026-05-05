package com.piashmsu.tvapk.cast

import android.content.Context
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.images.WebImage
import android.net.Uri

/**
 * Thin wrapper around the Cast SDK that fails gracefully when Google Play
 * services are unavailable (FOSS builds, GMS-less devices). Call
 * [available] first; if it returns false, the rest of the API is a no-op
 * — keeping the player UI functional even on Cast-less devices.
 */
object CastBridge {

    fun available(context: Context): Boolean {
        val gms = GoogleApiAvailability.getInstance()
        val status = gms.isGooglePlayServicesAvailable(context)
        return status == com.google.android.gms.common.ConnectionResult.SUCCESS
    }

    fun castContext(context: Context): CastContext? = runCatching {
        CastContext.getSharedInstance(context.applicationContext)
    }.getOrNull()

    fun isConnected(context: Context): Boolean =
        castContext(context)?.castState == CastState.CONNECTED

    fun loadMedia(
        context: Context,
        streamUrl: String,
        title: String,
        contentType: String? = null,
        artUrl: String? = null,
    ): Boolean {
        val ctx = castContext(context) ?: return false
        val session: CastSession = ctx.sessionManager.currentCastSession ?: return false
        val client = session.remoteMediaClient ?: return false
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_GENERIC).apply {
            putString(MediaMetadata.KEY_TITLE, title)
            if (!artUrl.isNullOrBlank()) {
                addImage(WebImage(Uri.parse(artUrl)))
            }
        }
        val mime = contentType ?: when {
            streamUrl.lowercase().contains(".m3u8") -> "application/x-mpegURL"
            streamUrl.lowercase().contains(".mpd") -> "application/dash+xml"
            streamUrl.lowercase().endsWith(".mp4") -> "video/mp4"
            else -> "video/mp4"
        }
        val info = MediaInfo.Builder(streamUrl)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(mime)
            .setMetadata(metadata)
            .build()
        return runCatching {
            client.load(MediaLoadRequestData.Builder().setMediaInfo(info).build())
            true
        }.getOrDefault(false)
    }
}
