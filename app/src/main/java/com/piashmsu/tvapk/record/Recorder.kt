package com.piashmsu.tvapk.record

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Low-level recording primitive used by [RecordingService].
 *
 * Handles two stream shapes:
 *  - **HLS** (`.m3u8`): polls the playlist, downloads new TS segments, and
 *    appends them to a single output `.ts` file. Stops when the worker
 *    coroutine is cancelled.
 *  - **Progressive** (MP4/MKV/TS direct HTTP, RTSP, etc.): pipes the
 *    response body directly to disk with periodic flushes.
 *
 * Output is stored in shared storage under `Movies/TV-APK/` (via MediaStore
 * on Android Q+, raw [File] on older devices) so the user can access the
 * recording from any other app.
 */
class Recorder(
    private val context: Context,
    sharedHttp: OkHttpClient,
    private val streamUrl: String,
    private val title: String,
    private val userAgent: String?,
    private val referer: String?,
    private val extraHeaders: Map<String, String>,
) {
    /**
     * The shared client has a 60 s `callTimeout` which would kill any
     * recording longer than 1 minute (especially on progressive streams).
     * Derive a no-timeout client that still inherits the connection pool,
     * cache, and HTTP/2 protocol setup.
     */
    private val http: OkHttpClient = sharedHttp.newBuilder()
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    /** Returns absolute or content URI string of the file when finished, or null on failure. */
    fun run(shouldStop: () -> Boolean, onProgressBytes: (Long) -> Unit): String? {
        // Probe the URL once: if Content-Type is HLS or the body starts
        // with #EXTM3U, treat it as HLS regardless of file extension.
        // Many IPTV providers serve HLS without a `.m3u8` suffix.
        val isHls = detectHls()
        val ext = if (isHls) "ts" else inferExtension(streamUrl)
        val fileName = buildFileName(title, ext)

        val (outStream, displayPath) = openOutput(fileName, ext) ?: return null
        return try {
            BufferedOutputStream(outStream, 256 * 1024).use { os ->
                if (isHls) recordHls(os, shouldStop, onProgressBytes)
                else recordProgressive(os, shouldStop, onProgressBytes)
                os.flush()
            }
            displayPath
        } catch (t: Throwable) {
            null
        }
    }

    private fun detectHls(): Boolean {
        val urlMatch = streamUrl.contains(".m3u8", ignoreCase = true) ||
            streamUrl.contains("application/vnd.apple.mpegurl", ignoreCase = true) ||
            streamUrl.contains("application/x-mpegurl", ignoreCase = true)
        if (urlMatch) return true
        return runCatching {
            http.newCall(buildRequest(streamUrl)).execute().use { resp ->
                val contentType = resp.header("Content-Type").orEmpty().lowercase()
                if (contentType.contains("mpegurl") || contentType.contains("vnd.apple")) {
                    return@use true
                }
                // Peek first 64 bytes — HLS playlists begin with #EXTM3U
                val src = resp.body?.byteStream() ?: return@use false
                val head = ByteArray(64)
                var read = 0
                while (read < head.size) {
                    val n = src.read(head, read, head.size - read)
                    if (n <= 0) break
                    read += n
                }
                String(head, 0, read, Charsets.US_ASCII).startsWith("#EXTM3U")
            }
        }.getOrDefault(false)
    }

    private fun recordHls(
        out: OutputStream,
        shouldStop: () -> Boolean,
        onProgressBytes: (Long) -> Unit,
    ) {
        val seen = LinkedHashSet<String>()
        var totalBytes = 0L
        while (!shouldStop()) {
            val playlist = runCatching { fetchText(streamUrl) }.getOrNull() ?: break
            val baseUri = Uri.parse(streamUrl)
            val segments = parseHlsSegments(playlist, baseUri)

            for (seg in segments) {
                if (shouldStop()) break
                if (!seen.add(seg)) continue
                runCatching {
                    fetchBytes(seg) { chunk, len ->
                        out.write(chunk, 0, len)
                        totalBytes += len
                        onProgressBytes(totalBytes)
                    }
                }
            }
            if (shouldStop()) break

            // Pace: roughly the typical HLS target duration. We poll once per
            // 3 seconds so we never miss new segments on most encoders.
            Thread.sleep(3000)
        }
    }

    private fun recordProgressive(
        out: OutputStream,
        shouldStop: () -> Boolean,
        onProgressBytes: (Long) -> Unit,
    ) {
        var totalBytes = 0L
        val req = buildRequest(streamUrl)
        http.newCall(req).execute().use { resp ->
            val src = resp.body?.byteStream() ?: error("empty body")
            val buf = ByteArray(64 * 1024)
            while (!shouldStop()) {
                val n = src.read(buf)
                if (n <= 0) break
                out.write(buf, 0, n)
                totalBytes += n
                onProgressBytes(totalBytes)
            }
        }
    }

    private fun parseHlsSegments(playlist: String, baseUri: Uri): List<String> {
        val out = mutableListOf<String>()
        var nextIsUri = false
        for (raw in playlist.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#EXTINF")) {
                nextIsUri = true
                continue
            }
            if (line.startsWith("#")) continue
            if (nextIsUri) {
                out += resolveUri(line, baseUri)
                nextIsUri = false
            }
        }
        return out
    }

    private fun resolveUri(uri: String, base: Uri): String {
        if (uri.startsWith("http://") || uri.startsWith("https://")) return uri
        if (uri.startsWith("//")) return (base.scheme ?: "https") + ":" + uri
        if (uri.startsWith("/")) return "${base.scheme}://${base.host}${if (base.port > 0) ":${base.port}" else ""}$uri"
        // Relative path — replace the last segment of the base path.
        val basePath = base.path?.substringBeforeLast('/').orEmpty()
        return "${base.scheme}://${base.host}${if (base.port > 0) ":${base.port}" else ""}$basePath/$uri"
    }

    private fun fetchText(url: String): String {
        http.newCall(buildRequest(url)).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            return resp.body?.string().orEmpty()
        }
    }

    private fun fetchBytes(url: String, sink: (ByteArray, Int) -> Unit) {
        http.newCall(buildRequest(url)).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val src = resp.body?.byteStream() ?: return
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = src.read(buf)
                if (n <= 0) break
                sink(buf, n)
            }
        }
    }

    private fun buildRequest(url: String): Request {
        val b = Request.Builder().url(url)
        b.header("User-Agent", userAgent ?: "TVApk/1.0")
        if (!referer.isNullOrBlank()) b.header("Referer", referer)
        for ((k, v) in extraHeaders) b.header(k, v)
        return b.build()
    }

    private fun openOutput(fileName: String, ext: String): Pair<OutputStream, String>? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val mime = if (ext == "ts") "video/mp2t" else "video/mp4"
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/TV-APK")
            }
            val uri = context.contentResolver.insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values
            ) ?: return null
            val os = context.contentResolver.openOutputStream(uri) ?: return null
            os to (uri.toString())
        } else {
            @Suppress("DEPRECATION")
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                "TV-APK"
            )
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, fileName)
            file.outputStream() to file.absolutePath
        }
    }

    private fun buildFileName(title: String, ext: String): String {
        val safe = title.replace(Regex("[^A-Za-z0-9 _-]"), "_").take(60).ifBlank { "recording" }
        val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return "$safe-$ts.$ext"
    }

    private fun inferExtension(url: String): String {
        val lower = url.lowercase()
        return when {
            lower.contains(".mkv") -> "mkv"
            lower.contains(".webm") -> "webm"
            lower.contains(".ts") -> "ts"
            else -> "mp4"
        }
    }
}
