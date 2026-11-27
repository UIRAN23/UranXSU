package org.uranxsu.manager.ui.util

import android.content.Context
import android.net.Uri
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class UranXsuThemeData(
    val version: Int = ThemePackageStorage.FORMAT_VERSION,
    val app: String = "UranXSU",
    // Colors & theme
    val themeMode: Int? = null,
    val useDynamicColor: Boolean? = null,
    val seedColor: Int? = null,
    val dynamicColorSpec: String? = null,
    val dynamicPaletteStyle: String? = null,
    val isUseBackgroundSeedColor: Boolean? = null,
    val isHighContrastMode: Boolean? = null,
    // Wallpaper & effects
    val hasBackground: Boolean? = null,
    val backgroundDim: Float? = null,
    val cardAlpha: Float? = null,
    val isEnableBlur: Boolean? = null,
    val isEnableBlurExp: Boolean? = null,
    val isFlashCardBackgroundEnabled: Boolean? = null,
    // Status card & module banners
    val hasCardBackground: Boolean? = null,
    val statusCardScrim: Float? = null,
    val statusCardHeight: Int? = null,
    val hideStatusCardText: Boolean? = null,
    val hideStatusCardDecoration: Boolean? = null,
    val showDonateCard: Boolean? = null,
    val showLearnMoreCard: Boolean? = null,
    val enableModuleBanner: Boolean? = null,
    // Layout, typography, navigation, gestures & icon
    val bottomBarStyle: String? = null,
    val showNavigationBarBadge: Boolean? = null,
    val showHomeCardIcons: Boolean? = null,
    val isSimpleMode: Boolean? = null,
    val showMoreModuleInfo: Boolean? = null,
    val useBuiltinMonoFont: Boolean? = null,
    val predictiveBackAnimation: String? = null,
    val predictiveBackExitDirection: String? = null,
    val enableSwipeDismiss: Boolean? = null,
    val pagerInterceptionMode: Int? = null,
    val useAltIcon: Boolean? = null,
    // Texts
    val customTexts: Map<String, String>? = null,
)

data class ImportedTheme(
    val data: UranXsuThemeData,
    val backgroundFile: File?,
    val backgroundSourceFile: File?,
    val cardBackgroundFile: File?,
    val cardBackgroundSourceFile: File?,
    val tempDir: File,
) {
    fun cleanup() {
        runCatching { tempDir.deleteRecursively() }
    }
}

data class ExportedThemePayload(
    val data: UranXsuThemeData,
    val backgroundFile: File? = null,
    val backgroundSourceFile: File? = null,
    val cardBackgroundFile: File? = null,
    val cardBackgroundSourceFile: File? = null,
)

object ThemePackageStorage {
    const val FORMAT_VERSION = 1
    const val THEME_EXTENSION = ".uxtheme"
    private const val THEME_JSON_ENTRY = "uranxsu-theme.json"
    private const val MEDIA_BACKGROUND = "media/background.img"
    private const val MEDIA_BACKGROUND_SOURCE = "media/background_source.img"
    private const val MEDIA_CARD_BACKGROUND = "media/card_background.img"
    private const val MEDIA_CARD_BACKGROUND_SOURCE = "media/card_background_source.img"
    private const val MAX_JSON_BYTES = 4L * 1024L * 1024L
    private const val MAX_MEDIA_BYTES = 64L * 1024L * 1024L

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun packageFileName(now: Date = Date()): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(now)
        return "UranXSU-theme-$stamp$THEME_EXTENSION"
    }

    fun exportToStream(out: OutputStream, payload: ExportedThemePayload) {
        ZipOutputStream(BufferedOutputStream(out)).use { zos ->
            zos.putNextEntry(ZipEntry(THEME_JSON_ENTRY))
            zos.write(json.encodeToString(payload.data).toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            writeFileEntry(zos, MEDIA_BACKGROUND, payload.backgroundFile)
            writeFileEntry(zos, MEDIA_BACKGROUND_SOURCE, payload.backgroundSourceFile)
            writeFileEntry(zos, MEDIA_CARD_BACKGROUND, payload.cardBackgroundFile)
            writeFileEntry(zos, MEDIA_CARD_BACKGROUND_SOURCE, payload.cardBackgroundSourceFile)
        }
    }

    suspend fun export(
        context: Context,
        uri: Uri,
        payload: ExportedThemePayload,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val out = context.contentResolver.openOutputStream(uri)
                ?: error("Cannot open output stream for $uri")
            out.use { exportToStream(it, payload) }
        }
    }

    fun importFromStream(
        context: Context,
        input: InputStream,
    ): Result<ImportedTheme> = runCatching {
        val tempDir = File(context.cacheDir, "theme_import_${UUID.randomUUID()}").apply {
            mkdirs()
        }
        try {
            var themeData: UranXsuThemeData? = null
            var backgroundFile: File? = null
            var backgroundSourceFile: File? = null
            var cardBackgroundFile: File? = null
            var cardBackgroundSourceFile: File? = null

            ZipInputStream(BufferedInputStream(input)).use { zis ->
                while (true) {
                    val entry = zis.nextEntry ?: break
                    val entryName = entry.name
                    if (!entry.isDirectory && isSafeZipEntry(entryName)) {
                        when (entryName) {
                            THEME_JSON_ENTRY -> {
                                val bytes = readEntryBounded(zis, MAX_JSON_BYTES)
                                themeData = json.decodeFromString<UranXsuThemeData>(
                                    bytes.toString(Charsets.UTF_8),
                                )
                            }

                            MEDIA_BACKGROUND -> {
                                val outFile = File(tempDir, "background.img")
                                copyEntryBounded(zis, outFile, MAX_MEDIA_BYTES)
                                backgroundFile = outFile
                            }

                            MEDIA_BACKGROUND_SOURCE -> {
                                val outFile = File(tempDir, "background_source.img")
                                copyEntryBounded(zis, outFile, MAX_MEDIA_BYTES)
                                backgroundSourceFile = outFile
                            }

                            MEDIA_CARD_BACKGROUND -> {
                                val outFile = File(tempDir, "card_background.img")
                                copyEntryBounded(zis, outFile, MAX_MEDIA_BYTES)
                                cardBackgroundFile = outFile
                            }

                            MEDIA_CARD_BACKGROUND_SOURCE -> {
                                val outFile = File(tempDir, "card_background_source.img")
                                copyEntryBounded(zis, outFile, MAX_MEDIA_BYTES)
                                cardBackgroundSourceFile = outFile
                            }
                        }
                    }
                    zis.closeEntry()
                }
            }

            val parsedData = themeData ?: error("Invalid .uxtheme archive: missing $THEME_JSON_ENTRY")
            ImportedTheme(
                data = parsedData,
                backgroundFile = backgroundFile?.takeIf { it.isFile && it.length() > 0L },
                backgroundSourceFile = backgroundSourceFile?.takeIf { it.isFile && it.length() > 0L },
                cardBackgroundFile = cardBackgroundFile?.takeIf { it.isFile && it.length() > 0L },
                cardBackgroundSourceFile = cardBackgroundSourceFile?.takeIf { it.isFile && it.length() > 0L },
                tempDir = tempDir,
            )
        } catch (t: Throwable) {
            runCatching { tempDir.deleteRecursively() }
            throw t
        }
    }

    suspend fun import(
        context: Context,
        uri: Uri,
    ): Result<ImportedTheme> = withContext(Dispatchers.IO) {
        runCatching {
            val input = context.contentResolver.openInputStream(uri)
                ?: error("Cannot open input stream for $uri")
            input.use { importFromStream(context, it).getOrThrow() }
        }
    }

    private fun writeFileEntry(
        zos: ZipOutputStream,
        entryName: String,
        file: File?,
    ) {
        if (file == null || !file.isFile || file.length() <= 0L) return
        zos.putNextEntry(ZipEntry(entryName))
        file.inputStream().use { it.copyTo(zos) }
        zos.closeEntry()
    }

    private fun isSafeZipEntry(name: String): Boolean = name.isNotBlank() &&
        !name.contains("..") &&
        !name.startsWith("/") &&
        !name.startsWith("\\")

    private fun readEntryBounded(input: InputStream, maxBytes: Long): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > maxBytes) error("Archive entry exceeds size limit ($maxBytes bytes)")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun copyEntryBounded(input: InputStream, dest: File, maxBytes: Long) {
        val buf = ByteArray(8192)
        var total = 0L
        FileOutputStream(dest).use { out ->
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > maxBytes) error("Media entry exceeds size limit ($maxBytes bytes)")
                out.write(buf, 0, n)
            }
            out.flush()
        }
    }
}
