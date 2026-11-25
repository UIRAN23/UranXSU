package org.uranxsu.manager.ui.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import org.uranxsu.manager.data.AppSettingsRepository

/**
 * Restores the saved alternate launcher icon after an app update resets component states
 * back to the manifest defaults.
 */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = GlobalContext.getOrNull()?.get<AppSettingsRepository>()
                if (settings != null) {
                    settings.preload()
                    LauncherIconUtils.apply(appContext, settings.getBoolean("use_alt_icon", false))
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
