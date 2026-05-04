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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.piashmsu.tvapk.data.AppTheme
import com.piashmsu.tvapk.ui.AppViewModel
import com.piashmsu.tvapk.ui.PipController
import com.piashmsu.tvapk.ui.PipState
import com.piashmsu.tvapk.ui.TvApkRoot
import com.piashmsu.tvapk.ui.theme.TvApkTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        PipController.activity = this
        setContent {
            val vm: AppViewModel = viewModel(factory = AppViewModel.Factory)
            val theme by vm.appTheme.collectAsState()
            val isDark = when (theme) {
                AppTheme.Light -> false
                AppTheme.Dark -> true
                AppTheme.System -> isSystemInDarkTheme()
            }
            TvApkTheme(darkTheme = isDark) {
                Surface(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    TvApkRoot()
                }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (PipController.shouldEnterOnLeave && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            tryEnterPip()
        }
    }

    fun tryEnterPip() {
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
        PipState.isInPip.value = isInPictureInPictureMode
    }

    override fun onDestroy() {
        if (PipController.activity === this) PipController.activity = null
        super.onDestroy()
    }
}
