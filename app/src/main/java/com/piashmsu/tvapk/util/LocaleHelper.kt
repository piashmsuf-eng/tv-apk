package com.piashmsu.tvapk.util

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.piashmsu.tvapk.data.UiLanguage

/**
 * Applies the user's chosen UI language using Android's per-app locale API
 * (API 33+) with an AppCompat fallback for older devices. "Follow system"
 * is encoded as an empty list which restores OS-default behavior.
 *
 * Bangla numerals are formatted lazily through [bengaliNumerals]. We don't
 * override `Locale.getDefault()` for the whole VM — that would break OkHttp
 * date headers and other system code. Numerals are converted at render
 * time only.
 */
object LocaleHelper {

    fun apply(context: Context, language: UiLanguage) {
        val tag = language.tag
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val mgr = context.getSystemService(LocaleManager::class.java)
            mgr?.applicationLocales = if (tag.isBlank()) {
                LocaleList.getEmptyLocaleList()
            } else LocaleList.forLanguageTags(tag)
            return
        }
        val locales = if (tag.isBlank()) LocaleListCompat.getEmptyLocaleList()
        else LocaleListCompat.forLanguageTags(tag)
        AppCompatDelegate.setApplicationLocales(locales)
    }

    private val asciiToBengali = mapOf(
        '0' to '০', '1' to '১', '2' to '২', '3' to '৩', '4' to '৪',
        '5' to '৫', '6' to '৬', '7' to '৭', '8' to '৮', '9' to '৯',
    )

    /** Replace Latin digits with Bengali digits; non-digit chars passthrough. */
    fun bengaliNumerals(s: String): String = buildString(s.length) {
        for (c in s) append(asciiToBengali[c] ?: c)
    }
}
