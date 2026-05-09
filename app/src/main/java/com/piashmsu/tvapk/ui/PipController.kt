package com.piashmsu.tvapk.ui

import com.piashmsu.tvapk.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Bridges the Compose player UI to the host [MainActivity] for
 * Picture-in-Picture entry. The activity sets itself when [onCreate] runs;
 * Compose code reads [shouldEnterOnLeave] in `onUserLeaveHint` and calls
 * [enterNow] from a button.
 */
object PipController {
    @Volatile var activity: MainActivity? = null

    /** True while the player screen is active. Toggled from PlayerScreen. */
    @Volatile var shouldEnterOnLeave: Boolean = false

    fun enterNow() {
        activity?.tryEnterPip()
    }
}

object PipState {
    val isInPip = MutableStateFlow(false)
}
