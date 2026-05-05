package com.piashmsu.tvapk.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.piashmsu.tvapk.data.LauncherVariant

/**
 * Toggles the manifest `<activity-alias>` entries to switch the launcher
 * icon. Only one alias is enabled at a time; switching disables the others
 * so duplicate launcher entries don't appear in the home screen.
 *
 * Note: Android may take up to ~30 s to refresh the launcher cache after a
 * change. Some launchers require a re-login or device reboot. We can't fix
 * that from a 3rd-party app — it's a system limitation.
 */
object LauncherIconManager {

    fun apply(context: Context, variant: LauncherVariant) {
        val pm = context.packageManager
        val pkg = context.packageName
        for (v in LauncherVariant.values()) {
            val component = ComponentName(pkg, v.aliasName)
            val targetState = when {
                v == variant && v == LauncherVariant.Default ->
                    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                v == variant ->
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                v == LauncherVariant.Default ->
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                else ->
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            runCatching {
                pm.setComponentEnabledSetting(
                    component,
                    targetState,
                    PackageManager.DONT_KILL_APP,
                )
            }
        }
    }
}
