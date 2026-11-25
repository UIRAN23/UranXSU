package org.uranxsu.manager.ui.theme

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.uranxsu.manager.data.AppSettingsRepository

@Stable
class HomeCustomizationConfig(
    private val settings: AppSettingsRepository,
) {
    var statusCardImagePath by mutableStateOf<String?>(null)
        private set
    var statusCardImageVersion by mutableIntStateOf(0)
        private set
    var statusCardScrim by mutableFloatStateOf(DEFAULT_CARD_SCRIM)
        private set
    var statusCardHeight by mutableIntStateOf(DEFAULT_STATUS_CARD_HEIGHT)
        private set
    var hideStatusCardText by mutableStateOf(false)
        private set
    var hideStatusCardDecoration by mutableStateOf(false)
        private set
    var showDonateCard by mutableStateOf(true)
        private set
    var showLearnMoreCard by mutableStateOf(true)
        private set
    var enableModuleBanner by mutableStateOf(true)
        private set

    fun load() {
        val path = settings.getString(KEY_STATUS_CARD_IMAGE, null)
        statusCardImagePath = path?.takeIf { File(it).isFile }
        statusCardScrim = settings.getFloat(KEY_STATUS_CARD_SCRIM, DEFAULT_CARD_SCRIM).coerceIn(0f, 0.85f)
        statusCardHeight = settings.getInt(KEY_STATUS_CARD_HEIGHT, DEFAULT_STATUS_CARD_HEIGHT).coerceIn(80, 360)
        hideStatusCardText = settings.getBoolean(KEY_HIDE_STATUS_CARD_TEXT, false)
        hideStatusCardDecoration = settings.getBoolean(KEY_HIDE_STATUS_CARD_DECORATION, false)
        showDonateCard = settings.getBoolean(KEY_SHOW_DONATE_CARD, true)
        showLearnMoreCard = settings.getBoolean(KEY_SHOW_LEARN_MORE_CARD, true)
        enableModuleBanner = settings.getBoolean(KEY_ENABLE_MODULE_BANNER, true)
    }

    suspend fun saveStatusCardImage(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val isVideo = org.uranxsu.manager.ui.component.media.isVideoUri(context, uri)
            val ext = if (isVideo) {
                org.uranxsu.manager.ui.component.media.resolveVideoExtension(context, uri)
            } else {
                "jpg"
            }

            val filesDir = context.applicationContext.filesDir
            val fileName = "home_status_card_bg_${System.currentTimeMillis()}.$ext"
            val tmpDest = File(filesDir, "$fileName.tmp")
            val dest = File(filesDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tmpDest).use { output ->
                    input.copyTo(output)
                    output.flush()
                }
            } ?: error("Cannot open selected media")
            check(tmpDest.isFile && tmpDest.length() > 0L) { "Copied card media is empty" }
            if (!tmpDest.renameTo(dest)) {
                tmpDest.copyTo(dest, overwrite = true)
                tmpDest.delete()
            }

            // Clean up older status card media files only after the new file is safely stored.
            filesDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("home_status_card_bg") && file.name != dest.name) {
                    runCatching { file.delete() }
                }
            }

            settings.putString(KEY_STATUS_CARD_IMAGE, dest.absolutePath)
            statusCardImagePath = dest.absolutePath
            statusCardImageVersion++
            true
        }.onFailure {
            Log.e(TAG, "Failed to save status card media", it)
        }.getOrDefault(false)
    }

    suspend fun saveStatusCardImageFile(context: Context, sourceFile: File): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            require(sourceFile.isFile) { "Source file does not exist" }
            val ext = sourceFile.extension.takeIf { it.isNotBlank() } ?: "jpg"
            val filesDir = context.applicationContext.filesDir
            val dest = File(filesDir, "home_status_card_bg_${System.currentTimeMillis()}.$ext")
            sourceFile.copyTo(dest, overwrite = true)
            check(dest.isFile && dest.length() > 0L) { "Copied card media is empty" }
            filesDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("home_status_card_bg") && file.name != dest.name) {
                    runCatching { file.delete() }
                }
            }
            settings.putString(KEY_STATUS_CARD_IMAGE, dest.absolutePath)
            statusCardImagePath = dest.absolutePath
            statusCardImageVersion++
            true
        }.onFailure {
            Log.e(TAG, "Failed to copy status card media", it)
        }.getOrDefault(false)
    }

    fun clearStatusCardImage(context: Context) {
        runCatching {
            context.applicationContext.filesDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("home_status_card_bg")) {
                    runCatching { file.delete() }
                }
            }
        }
        settings.remove(KEY_STATUS_CARD_IMAGE)
        statusCardImagePath = null
        statusCardImageVersion++
    }

    fun updateStatusCardHeight(height: Int) {
        val coerced = height.coerceIn(80, 360)
        statusCardHeight = coerced
        settings.putInt(KEY_STATUS_CARD_HEIGHT, coerced)
    }

    fun updateStatusCardScrim(value: Float) {
        val coerced = value.coerceIn(0f, 0.85f)
        statusCardScrim = coerced
        settings.putFloat(KEY_STATUS_CARD_SCRIM, coerced)
    }

    fun updateHideStatusCardText(hide: Boolean) {
        hideStatusCardText = hide
        settings.putBoolean(KEY_HIDE_STATUS_CARD_TEXT, hide)
    }

    fun updateHideStatusCardDecoration(hide: Boolean) {
        hideStatusCardDecoration = hide
        settings.putBoolean(KEY_HIDE_STATUS_CARD_DECORATION, hide)
    }

    fun updateShowDonateCard(show: Boolean) {
        showDonateCard = show
        settings.putBoolean(KEY_SHOW_DONATE_CARD, show)
    }

    fun updateShowLearnMoreCard(show: Boolean) {
        showLearnMoreCard = show
        settings.putBoolean(KEY_SHOW_LEARN_MORE_CARD, show)
    }

    fun updateEnableModuleBanner(enabled: Boolean) {
        enableModuleBanner = enabled
        settings.putBoolean(KEY_ENABLE_MODULE_BANNER, enabled)
    }

    private companion object {
        const val TAG = "HomeCustomizationConfig"
        const val DEFAULT_CARD_SCRIM = 0.35f
        const val DEFAULT_STATUS_CARD_HEIGHT = 112
        const val KEY_STATUS_CARD_IMAGE = "home_status_card_image_path"
        const val KEY_STATUS_CARD_SCRIM = "home_status_card_scrim"
        const val KEY_STATUS_CARD_HEIGHT = "home_status_card_height"
        const val KEY_HIDE_STATUS_CARD_TEXT = "hide_home_card_status_text"
        const val KEY_HIDE_STATUS_CARD_DECORATION = "hide_home_card_decoration"
        const val KEY_SHOW_DONATE_CARD = "show_donate_card"
        const val KEY_SHOW_LEARN_MORE_CARD = "show_learn_more_card"
        const val KEY_ENABLE_MODULE_BANNER = "enable_module_banner"
    }
}
