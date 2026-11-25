package org.uranxsu.manager.ui.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

object LauncherIconUtils {
    private const val TAG = "LauncherIconUtils"

    fun apply(context: Context, useAlt: Boolean) {
        val packageName = context.packageName
        val main = ComponentName(packageName, "org.uranxsu.manager.ui.MainActivity")
        val alias = ComponentName(packageName, "org.uranxsu.manager.ui.MainActivityAlias")
        val target = if (useAlt) alias else main
        val other = if (useAlt) main else alias
        val pm = context.packageManager

        // Skip unchanged aliases so ROMs don't restart or kill the process unnecessarily.
        fun setEnabled(component: ComponentName, enabled: Boolean) {
            val wanted = if (enabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            if (pm.getComponentEnabledSetting(component) == wanted) return
            pm.setComponentEnabledSetting(component, wanted, PackageManager.DONT_KILL_APP)
        }

        runCatching {
            setEnabled(target, true)
            setEnabled(other, false)
        }.onFailure {
            Log.e(TAG, "Failed to update launcher icon state", it)
        }
    }
}
