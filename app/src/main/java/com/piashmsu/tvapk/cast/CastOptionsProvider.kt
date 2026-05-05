package com.piashmsu.tvapk.cast

import android.content.Context
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions

/**
 * Exposes the Google Cast SDK configuration. The receiver app id below is
 * the well-known "default media receiver" — works for any vanilla
 * H.264/AAC stream without needing a registered Cast Developer account.
 *
 * Returning `false` from `getAdditionalSessionProviders` and an empty
 * default media-options means the SDK will silently no-op on devices with
 * no Cast hardware nearby, which is what we want.
 */
class CastOptionsProvider : OptionsProvider {

    override fun getCastOptions(context: Context): CastOptions {
        return CastOptions.Builder()
            .setReceiverApplicationId("CC1AD845") // Default Media Receiver
            .setCastMediaOptions(
                CastMediaOptions.Builder()
                    .setMediaSessionEnabled(true)
                    .setNotificationOptions(null)
                    .build()
            )
            .build()
    }

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}
