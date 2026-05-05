package com.piashmsu.tvapk

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.piashmsu.tvapk.cast.CastBridge
import com.piashmsu.tvapk.data.ThemePalette
import com.piashmsu.tvapk.ui.TvApkRoot
import com.piashmsu.tvapk.ui.theme.TvApkTheme

/**
 * Single-activity host. Picture-in-Picture is opt-in: pages that need it
 * (currently the Player) call [requestPip] which auto-computes a 16:9
 * aspect ratio and the OS handles the rest.
 *
 * The Cast SDK is initialized lazily on first onResume so a Cast-less
 * (e.g. FOSS / GMS-less) device never pays the CastContext init cost.
 */
class MainActivity : ComponentActivity() {

    private val isInPip = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Installs the AndroidX splash-screen compat shim before
        // super.onCreate so the system splash plays smoothly into the
        // Compose root.
        val splash = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Briefly hold the splash while DataStore warms up.
        var keep = true
        splash.setKeepOnScreenCondition { keep }
        window.decorView.postDelayed({ keep = false }, 350)

        setContent {
            val app = application as TvApkApp
            val palette by app.container.prefs.themePalette
                .collectAsState(initial = ThemePalette.DefaultVibe)
            TvApkTheme(palette = palette) {
                Surface(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    TvApkRoot(isInPipState = isInPip)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Lazy Cast SDK init — no-op if Play services unavailable.
        runCatching { CastBridge.castContext(this) }
    }

    fun requestPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .build()
        runCatching { enterPictureInPictureMode(params) }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPip.value = isInPictureInPictureMode
    }
}
