package com.piashmsu.tvapk.util

import com.piashmsu.tvapk.data.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

/**
 * Polls GitHub Releases for the latest tag and decides whether the running
 * build is stale. Tag names are compared loosely so prefixes like `v` or
 * `release-` don't trip the comparison up.
 */
class UpdateChecker(
    private val http: OkHttpClient,
    private val prefs: AppPrefs,
    private val owner: String = "piashmsuf-eng",
    private val repo: String = "tv-apk",
) {

    data class Result(
        val latestTag: String,
        val current: String,
        val updateAvailable: Boolean,
        val htmlUrl: String,
    )

    suspend fun check(currentVersion: String): Result? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo/releases")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "TVApk-UpdateCheck/1.0")
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                val body = resp.body?.string().orEmpty()
                val arr = JSONArray(body)
                if (arr.length() == 0) return@runCatching null
                val first = arr.getJSONObject(0)
                val tag = first.optString("tag_name").ifBlank {
                    first.optString("name")
                }
                val html = first.optString("html_url")
                    .ifBlank { "https://github.com/$owner/$repo/releases" }
                val latest = tag.ifBlank { return@runCatching null }
                prefs.setUpdateChecked(latest)
                Result(
                    latestTag = latest,
                    current = currentVersion,
                    updateAvailable = compareLoose(latest, currentVersion) > 0,
                    htmlUrl = html,
                )
            }
        }.getOrNull()
    }

    /**
     * Compare two version-ish strings ignoring leading non-digits. Returns
     * +1 / 0 / -1 like Comparable.
     */
    private fun compareLoose(a: String, b: String): Int {
        val ax = stripPrefix(a).split('.', '-').mapNotNull { it.toIntOrNull() }
        val bx = stripPrefix(b).split('.', '-').mapNotNull { it.toIntOrNull() }
        val len = maxOf(ax.size, bx.size)
        for (i in 0 until len) {
            val ai = ax.getOrNull(i) ?: 0
            val bi = bx.getOrNull(i) ?: 0
            if (ai != bi) return if (ai > bi) 1 else -1
        }
        return 0
    }

    private fun stripPrefix(s: String): String {
        var i = 0
        while (i < s.length && !s[i].isDigit()) i++
        return s.substring(i)
    }
}
