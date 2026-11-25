package org.uranxsu.manager.ui.util

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

@Serializable
data class ThemeVariantData(
    val themeMode: Int? = null,
    val miuixMonet: Boolean? = null,
    val keyColor: Int? = null,
    val colorStyle: String? = null,
    val colorSpec: String? = null,
    val cardBackgroundAlphaPercent: Int? = null,
    val customBackgroundBlurPercent: Int? = null,
    val customBackgroundScrimPercent: Int? = null,
    val hasBackground: Boolean? = null,
    val hasCardBackground: Boolean? = null,
    val hideHomeCardStatusText: Boolean? = null,
    val hideHomeCardDecoration: Boolean? = null,
    val homeStatusScale: Float? = null,
    val enableFloatingBottomBar: Boolean? = null,
    val enableNavigationBadge: Boolean? = null,
    val enableSwipeDismiss: Boolean? = null,
    val enableBlur: Boolean? = null,
    val pagerInterceptionMode: Int? = null,
)

@Serializable
data class ThemePackageData(
    val version: Int = ThemePackageStorage.BASE_VERSION,
    val themeMode: Int? = null,
    val miuixMonet: Boolean? = null,
    val keyColor: Int? = null,
    val colorStyle: String? = null,
    val colorSpec: String? = null,
    val cardBackgroundAlphaPercent: Int? = null,
    val customBackgroundBlurPercent: Int? = null,
    val customBackgroundScrimPercent: Int? = null,
    val hasBackground: Boolean? = null,
    val hasCardBackground: Boolean? = null,
    val hideHomeCardStatusText: Boolean? = null,
    val hideHomeCardDecoration: Boolean? = null,
    val homeStatusScale: Float? = null,
    val enableFloatingBottomBar: Boolean? = null,
    val enableNavigationBadge: Boolean? = null,
    val enableSwipeDismiss: Boolean? = null,
    val enableBlur: Boolean? = null,
    val pagerInterceptionMode: Int? = null,
    val customHomeTitle: String? = null,
    val customTexts: Map<String, String>? = null,
    val appearance: JsonElement? = null,
    val adaptive: Boolean? = null,
    val light: ThemeVariantData? = null,
    val dark: ThemeVariantData? = null,
)

data class ImportedThemeVariant(
    val data: ThemeVariantData,
    val backgroundPath: String? = null,
    val cardBackgroundPath: String? = null,
)

data class ImportedTheme(
    val data: ThemePackageData,
    val backgroundPath: String? = null,
    val cardBackgroundPath: String? = null,
    val light: ImportedThemeVariant? = null,
    val dark: ImportedThemeVariant? = null,
) {
    val isAdaptive: Boolean
        get() = data.adaptive == true && light != null && dark != null

    fun cleanup() {
        listOfNotNull(
            backgroundPath,
            cardBackgroundPath,
            light?.backgroundPath,
            light?.cardBackgroundPath,
            dark?.backgroundPath,
            dark?.cardBackgroundPath,
        ).forEach { runCatching { File(it).delete() } }
    }
}

object ThemePackageStorage {
    const val BASE_VERSION = 2
    const val MIN_SUPPORTED_VERSION = 1
    const val MAX_SUPPORTED_VERSION = 6

    private const val THEME_ENTRY = "theme.json"
    private const val BACKGROUND_ENTRY = "background"
    private const val CARD_BACKGROUND_ENTRY = "card_background"
    private const val BACKGROUND_LIGHT_ENTRY = "background_light"
    private const val BACKGROUND_DARK_ENTRY = "background_dark"
    private const val CARD_BACKGROUND_LIGHT_ENTRY = "card_background_light"
    private const val CARD_BACKGROUND_DARK_ENTRY = "card_background_dark"
    private const val MAX_JSON_BYTES = 128L * 1024
    private const val MAX_IMAGE_BYTES = 64L * 1024 * 1024
    private const val STREAM_BUFFER_SIZE = 256 * 1024

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun packageFileName(): String {
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
        return "UranXSU-theme-$stamp.sukisu"
    }

    suspend fun import(context: Context, uri: Uri): Result<ImportedTheme> = withContext(Dispatchers.IO) {
        runCatching {
            val rawInput = context.contentResolver.openInputStream(uri)
                ?: error("Cannot open selected theme file")
            val buffered = BufferedInputStream(rawInput, STREAM_BUFFER_SIZE)
            buffered.mark(ThemePackageCrypto.HEADER_SIZE + 4)
            val probe = ByteArray(ThemePackageCrypto.HEADER_SIZE)
            val probeLen = buffered.read(probe)
            buffered.reset()

            val zipStream = if (probeLen == ThemePackageCrypto.HEADER_SIZE && ThemePackageCrypto.hasValidHeader(probe)) {
                ThemePackageCrypto.decryptingStream(buffered)
            } else {
                buffered
            }
            zipStream.use { stream ->
                readZipPackage(context, stream)
            }
        }
    }

    suspend fun export(
        context: Context,
        uri: Uri,
        data: ThemePackageData,
        backgroundFile: File?,
        cardBackgroundFile: File?,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val output = context.contentResolver.openOutputStream(uri)
                ?: error("Cannot open destination file")
            val payload = data.copy(
                version = BASE_VERSION,
                hasBackground = backgroundFile?.isFile == true,
                hasCardBackground = cardBackgroundFile?.isFile == true,
            )
            BufferedOutputStream(output, STREAM_BUFFER_SIZE).use { buffered ->
                ThemePackageCrypto.encryptingStream(buffered).use { cipherOut ->
                    ZipOutputStream(cipherOut).use { zip ->
                        zip.setLevel(Deflater.NO_COMPRESSION)
                        zip.putNextEntry(ZipEntry(THEME_ENTRY))
                        zip.write(json.encodeToString(payload).toByteArray(Charsets.UTF_8))
                        zip.closeEntry()

                        if (backgroundFile?.isFile == true) {
                            writeFileEntry(zip, BACKGROUND_ENTRY, backgroundFile)
                        }
                        if (cardBackgroundFile?.isFile == true) {
                            writeFileEntry(zip, CARD_BACKGROUND_ENTRY, cardBackgroundFile)
                        }
                    }
                }
            }
        }
    }

    private fun writeFileEntry(zip: ZipOutputStream, name: String, file: File) {
        zip.putNextEntry(ZipEntry(name))
        file.inputStream().use { input -> input.copyTo(zip, STREAM_BUFFER_SIZE) }
        zip.closeEntry()
    }

    private fun readZipPackage(context: Context, input: InputStream): ImportedTheme {
        val tempDir = File(context.cacheDir, "theme_import_${System.currentTimeMillis()}").apply {
            mkdirs()
        }
        val extracted = mutableListOf<File>()
        try {
            var packageData: ThemePackageData? = null
            var bgPath: String? = null
            var cardBgPath: String? = null
            var lightBgPath: String? = null
            var darkBgPath: String? = null
            var lightCardBgPath: String? = null
            var darkCardBgPath: String? = null

            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        when (entry.name) {
                            THEME_ENTRY -> {
                                val bytes = readBoundedBytes(zip, MAX_JSON_BYTES)
                                packageData = json.decodeFromString<ThemePackageData>(
                                    bytes.toString(Charsets.UTF_8),
                                )
                            }

                            BACKGROUND_ENTRY -> {
                                bgPath = extractImage(zip, File(tempDir, "bg.img"), extracted)
                            }

                            CARD_BACKGROUND_ENTRY -> {
                                cardBgPath = extractImage(zip, File(tempDir, "card_bg.img"), extracted)
                            }

                            BACKGROUND_LIGHT_ENTRY -> {
                                lightBgPath = extractImage(zip, File(tempDir, "bg_light.img"), extracted)
                            }

                            BACKGROUND_DARK_ENTRY -> {
                                darkBgPath = extractImage(zip, File(tempDir, "bg_dark.img"), extracted)
                            }

                            CARD_BACKGROUND_LIGHT_ENTRY -> {
                                lightCardBgPath = extractImage(zip, File(tempDir, "card_bg_light.img"), extracted)
                            }

                            CARD_BACKGROUND_DARK_ENTRY -> {
                                darkCardBgPath = extractImage(zip, File(tempDir, "card_bg_dark.img"), extracted)
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            val data = packageData ?: error("Theme package is missing theme.json")
            require(data.version in MIN_SUPPORTED_VERSION..MAX_SUPPORTED_VERSION) {
                "Unsupported theme version: ${data.version}"
            }

            val lightVariant = data.light?.let {
                ImportedThemeVariant(
                    data = it,
                    backgroundPath = lightBgPath,
                    cardBackgroundPath = lightCardBgPath,
                )
            }
            val darkVariant = data.dark?.let {
                ImportedThemeVariant(
                    data = it,
                    backgroundPath = darkBgPath,
                    cardBackgroundPath = darkCardBgPath,
                )
            }
            return ImportedTheme(
                data = data,
                backgroundPath = bgPath,
                cardBackgroundPath = cardBgPath,
                light = lightVariant,
                dark = darkVariant,
            )
        } catch (t: Throwable) {
            extracted.forEach { runCatching { it.delete() } }
            runCatching { tempDir.deleteRecursively() }
            throw t
        }
    }

    private fun readBoundedBytes(input: InputStream, limit: Long): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buf)
            if (read < 0) break
            total += read
            require(total <= limit) { "Theme metadata too large" }
            out.write(buf, 0, read)
        }
        return out.toByteArray()
    }

    private fun extractImage(
        input: InputStream,
        dest: File,
        tracked: MutableList<File>,
    ): String {
        var total = 0L
        val buf = ByteArray(64 * 1024)
        dest.outputStream().use { out ->
            while (true) {
                val read = input.read(buf)
                if (read < 0) break
                total += read
                require(total <= MAX_IMAGE_BYTES) { "Theme image exceeds size limit" }
                out.write(buf, 0, read)
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(dest.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Invalid image in theme package" }
        tracked += dest
        return dest.absolutePath
    }

    fun mapSukiSuThemeMode(mode: Int?): Int? = when (mode) {
        0, 3 -> 0
        1, 4 -> 1
        2, 5 -> 2
        else -> null
    }

    fun mapColorStyle(raw: String?): PaletteStyle? {
        if (raw.isNullOrBlank()) return null
        val normalized = raw.replace("_", "").replace("-", "").replace(" ", "")
        return PaletteStyle.entries.find { it.name.equals(normalized, ignoreCase = true) }
    }

    fun mapColorSpec(raw: String?): ColorSpec.SpecVersion? {
        if (raw.isNullOrBlank()) return null
        return when {
            raw.contains("2025") -> ColorSpec.SpecVersion.SPEC_2025
            raw.contains("2021") -> ColorSpec.SpecVersion.SPEC_2021
            else -> null
        }
    }
}
