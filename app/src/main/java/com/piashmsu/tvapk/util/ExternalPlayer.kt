package com.piashmsu.tvapk.util

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Hands off a stream URL to an external video player such as VLC or
 * MX Player. If [packageName] is blank the user gets the system chooser;
 * otherwise we target that package directly.
 */
object ExternalPlayer {

    fun open(context: Context, streamUrl: String, title: String?, packageName: String): Boolean {
        val uri = Uri.parse(streamUrl)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeFor(streamUrl))
            putExtra("title", title.orEmpty())
            // VLC-specific extras
            putExtra("from_start", false)
            // MX Player-specific extras
            putExtra("decode_mode", 2 /* hardware+ */)
            putExtra("video_zoom", 0)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (packageName.isNotBlank()) intent.setPackage(packageName)
        return runCatching {
            if (packageName.isBlank()) {
                val chooser = Intent.createChooser(intent, "Open in…")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
            } else {
                context.startActivity(intent)
            }
            true
        }.getOrElse { false }
    }

    private fun mimeFor(url: String): String {
        val lower = url.lowercase()
        return when {
            lower.contains(".m3u8") -> "application/vnd.apple.mpegurl"
            lower.contains(".mpd") -> "application/dash+xml"
            lower.endsWith(".mkv") -> "video/x-matroska"
            lower.endsWith(".mp4") -> "video/mp4"
            lower.endsWith(".ts") -> "video/mp2t"
            else -> "video/*"
        }
    }
}
