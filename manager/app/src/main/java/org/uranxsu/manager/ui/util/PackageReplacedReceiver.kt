package org.uranxsu.manager.ui.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import org.uranxsu.manager.Natives
import org.uranxsu.manager.data.AppSettingsRepository
import org.uranxsu.manager.data.ksud.KsudIntegrityRepository

/**
 * Restores the saved alternate launcher icon after an app update resets component states
 * back to the manifest defaults, and auto-syncs the bundled `ksud` binary when enabled.
 */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val koin = GlobalContext.getOrNull()
                val settings = koin?.getOrNull<AppSettingsRepository>()
                if (settings != null) {
                    settings.preload()
                    LauncherIconUtils.apply(appContext, settings.getBoolean("use_alt_icon", false))
                    if (settings.getBoolean("auto_update_ksud", true) &&
                        runCatching { Natives.isManager }.getOrDefault(false)
                    ) {
                        koin.getOrNull<KsudIntegrityRepository>()?.syncIfNeeded()
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
