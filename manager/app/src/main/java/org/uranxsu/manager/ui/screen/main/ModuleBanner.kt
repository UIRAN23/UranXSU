package org.uranxsu.manager.ui.screen.main

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.scale
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import com.topjohnwu.superuser.io.SuFileOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koin.compose.koinInject
import org.uranxsu.manager.data.shell.KsuCliRepository
import org.uranxsu.manager.domain.model.InstalledModule
import org.uranxsu.manager.ui.component.media.isVideoPath
import org.uranxsu.manager.ui.component.media.isVideoUri

private const val TAG = "ModuleBanner"
private const val MODULE_DIR = "/data/adb/modules/"
private const val MODULE_UPDATE_DIR = "/data/adb/modules_update/"
private const val MAX_BANNER_BYTES = 4L * 1024 * 1024
private const val BANNER_DECODE_PX = 720
private const val CUSTOM_BANNER_PREFIX = "banner_custom"
private const val BANNER_BACKUP_FILE = ".uranxsu_banner_backup"
private const val MAX_SOURCE_BYTES = 32L * 1024 * 1024
private const val MAX_BANNER_DIMENSION = 2048

private val MODULE_ROOTS = listOf("/data/adb/modules", "/data/adb/modules_update")
private val bannerCache = LruCache<String, ImageBitmap>(32)
private val bannerRevision = mutableIntStateOf(0)

fun invalidateModuleBanner(moduleId: String) {
    Log.d(TAG, "invalidate banner cache for $moduleId")
    bannerCache.evictAll()
    bannerRevision.intValue++
}

private fun readBanner(
    moduleId: String,
    banner: String,
    shell: Shell,
    okHttpClient: OkHttpClient,
): ByteArray? {
    if (banner.startsWith("http://", true) || banner.startsWith("https://", true)) {
        return okHttpClient.newCall(Request.Builder().url(banner).build()).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body ?: return null
            val source = body.source()
            if (source.request(MAX_BANNER_BYTES + 1)) return null
            source.readByteArray()
        }
    }
    if (banner.split('/').contains("..")) return null
    val candidates = if (banner.startsWith("/")) {
        listOf(banner).filter { it.startsWith(MODULE_DIR) || it.startsWith(MODULE_UPDATE_DIR) }
    } else {
        listOf("$MODULE_DIR$moduleId/$banner", "$MODULE_UPDATE_DIR$moduleId/$banner")
    }
    val file = candidates
        .map { SuFile(it).apply { this.shell = shell } }
        .firstOrNull { it.isFile } ?: return null
    if (file.length() > MAX_BANNER_BYTES) return null
    return SuFileInputStream.open(file).use { it.readBytes() }
}

private fun loadModuleBanner(
    moduleId: String,
    banner: String,
    shell: Shell,
    okHttpClient: OkHttpClient,
): ImageBitmap? = runCatching {
    val bytes = readBanner(moduleId, banner, shell, okHttpClient) ?: return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= BANNER_DECODE_PX) sample *= 2
    BitmapFactory.decodeByteArray(
        bytes,
        0,
        bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sample },
    )?.asImageBitmap()
}.onFailure { Log.w(TAG, "load banner for $moduleId failed: $banner", it) }
    .getOrNull()
    ?.also { bannerCache.put("$moduleId:$banner", it) }

@Composable
fun rememberModuleBanner(moduleId: String, banner: String?, enabled: Boolean = true): ImageBitmap? {
    if (!enabled || banner.isNullOrBlank() || isVideoPath(banner)) return null
    val ksuCli = koinInject<KsuCliRepository>()
    val okHttpClient = koinInject<OkHttpClient>()
    val key = "$moduleId:$banner"
    val revision = bannerRevision.intValue
    val bitmap by produceState(bannerCache.get(key), key, revision) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                loadModuleBanner(moduleId, banner, ksuCli.getRootShell(), okHttpClient)
            }
        }
    }
    return bitmap
}

@Composable
fun rememberModuleBannerVideo(moduleId: String, banner: String?, enabled: Boolean = true): File? {
    if (!enabled || banner.isNullOrBlank() || !isVideoPath(banner)) return null
    val context = LocalContext.current
    val ksuCli = koinInject<KsuCliRepository>()
    val revision = bannerRevision.intValue
    val file by produceState<File?>(null, moduleId, banner, revision) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val shell = ksuCli.getRootShell()
                val targetFile = File(context.cacheDir, "banner_${moduleId}_${banner.substringAfterLast('/')}")
                val srcFile = SuFile("/data/adb/modules/$moduleId/$banner").apply { this.shell = shell }
                val updateSrcFile = SuFile("/data/adb/modules_update/$moduleId/$banner").apply { this.shell = shell }
                val chosen = when {
                    srcFile.isFile -> srcFile
                    updateSrcFile.isFile -> updateSrcFile
                    else -> null
                } ?: return@runCatching null

                if (!targetFile.exists() || targetFile.length() != chosen.length()) {
                    SuFileInputStream.open(chosen).use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                targetFile
            }.getOrNull()
        }
    }
    return file
}

fun Modifier.moduleBanner(banner: ImageBitmap?, scrim: Color): Modifier {
    if (banner == null) return this
    return drawWithCache {
        val scale = maxOf(size.width / banner.width, size.height / banner.height)
        val srcW = (size.width / scale).roundToInt().coerceIn(1, banner.width)
        val srcH = (size.height / scale).roundToInt().coerceIn(1, banner.height)
        val srcOffset = IntOffset((banner.width - srcW) / 2, (banner.height - srcH) / 2)
        val srcSize = IntSize(srcW, srcH)
        val dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
        val gradient = Brush.horizontalGradient(
            listOf(
                scrim.copy(alpha = 0.90f),
                scrim.copy(alpha = 0.74f),
                scrim.copy(alpha = 0.42f),
            ),
        )
        onDrawBehind {
            drawImage(
                image = banner,
                srcOffset = srcOffset,
                srcSize = srcSize,
                dstSize = dstSize,
            )
            drawRect(gradient)
        }
    }
}

object ModuleBannerEditor {
    fun isCustomBanner(module: InstalledModule): Boolean = module.bannerPath?.substringAfterLast('/')?.startsWith(CUSTOM_BANNER_PREFIX) == true

    fun import(
        context: Context,
        shell: Shell,
        module: InstalledModule,
        uri: Uri,
    ): Result<Unit> = runCatching {
        val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Unable to read selected media")
        require(raw.isNotEmpty() && raw.size <= MAX_SOURCE_BYTES) { "Media is empty or too large" }

        val isVideo = isVideoUri(context, uri)
        val dirs = moduleDirectories(shell, module.dirId)
        require(dirs.isNotEmpty()) { "Module directory not found" }

        if (isVideo) {
            val mime = context.contentResolver.getType(uri)
            val ext = when {
                mime?.contains("webm", ignoreCase = true) == true -> "webm"
                mime?.contains("mkv", ignoreCase = true) == true -> "mkv"
                else -> "mp4"
            }
            val fileName = "$CUSTOM_BANNER_PREFIX.$ext"
            dirs.forEach { dir ->
                backupOriginalBanner(shell, dir, readText(shell, "$dir/module.prop"))
                removeCustomBannerFiles(shell, dir, keep = fileName)
                writeBytes(shell, "$dir/$fileName", raw)
                check(setBannerValue(shell, dir, fileName)) { "Failed to update module.prop" }
            }
            return@runCatching
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Selected file is not a valid image" }

        val normalized = normalizeBanner(raw, bounds)
        val fileName = "$CUSTOM_BANNER_PREFIX.${normalized.extension}"
        dirs.forEach { dir ->
            backupOriginalBanner(shell, dir, readText(shell, "$dir/module.prop"))
            removeCustomBannerFiles(shell, dir, keep = fileName)
            writeBytes(shell, "$dir/$fileName", normalized.bytes)
            check(setBannerValue(shell, dir, fileName)) { "Failed to update module.prop" }
        }
    }.onFailure { Log.w(TAG, "import banner for ${module.dirId} failed", it) }

    fun remove(shell: Shell, module: InstalledModule): Result<Unit> = runCatching {
        val dirs = moduleDirectories(shell, module.dirId)
        require(dirs.isNotEmpty()) { "Module directory not found" }
        dirs.forEach { dir ->
            check(restoreOriginalBanner(shell, dir)) { "Failed to restore module.prop" }
            removeCustomBannerFiles(shell, dir, keep = null)
            deleteFile(shell, "$dir/$BANNER_BACKUP_FILE")
        }
    }.onFailure { Log.w(TAG, "remove banner for ${module.dirId} failed", it) }

    private class NormalizedBanner(val bytes: ByteArray, val extension: String)

    private fun normalizeBanner(raw: ByteArray, bounds: BitmapFactory.Options): NormalizedBanner {
        val longestSide = max(bounds.outWidth, bounds.outHeight)
        val orientation = runCatching {
            ExifInterface(ByteArrayInputStream(raw)).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        if (raw.size <= MAX_BANNER_BYTES &&
            longestSide <= MAX_BANNER_DIMENSION &&
            orientation == ExifInterface.ORIENTATION_NORMAL
        ) {
            return NormalizedBanner(raw, extensionFor(bounds.outMimeType))
        }

        var sample = 1
        while (longestSide / (sample * 2) >= MAX_BANNER_DIMENSION) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(
            raw,
            0,
            raw.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: error("Unable to decode image")
        val oriented = applyExifOrientation(decoded, orientation)
        val scaled = scaleDown(oriented)
        val bytes = encodeWebp(scaled)
        if (!scaled.isRecycled) scaled.recycle()
        return NormalizedBanner(bytes, "webp")
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        if (orientation == ExifInterface.ORIENTATION_NORMAL ||
            orientation == ExifInterface.ORIENTATION_UNDEFINED
        ) {
            return bitmap
        }
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)

                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)

                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)

                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    postRotate(270f)
                    postScale(-1f, 1f)
                }

                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)

                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    postRotate(90f)
                    postScale(-1f, 1f)
                }

                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)

                else -> return bitmap
            }
        }
        return runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                .also { if (it !== bitmap) bitmap.recycle() }
        }.getOrDefault(bitmap)
    }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= MAX_BANNER_DIMENSION) return bitmap
        val scale = MAX_BANNER_DIMENSION.toFloat() / longest
        val width = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        return bitmap.scale(width, height).also { if (it !== bitmap) bitmap.recycle() }
    }

    private fun encodeWebp(bitmap: Bitmap): ByteArray {
        var quality = 90
        var bytes = compressWebp(bitmap, quality)
        while (bytes.size > MAX_BANNER_BYTES && quality > 40) {
            quality -= 10
            bytes = compressWebp(bitmap, quality)
        }
        var scale = 0.8f
        while (bytes.size > MAX_BANNER_BYTES && scale >= 0.4f) {
            val width = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
            val height = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
            val scaled = bitmap.scale(width, height)
            bytes = compressWebp(scaled, 80)
            if (scaled !== bitmap) scaled.recycle()
            scale *= 0.8f
        }
        return bytes
    }

    private fun compressWebp(bitmap: Bitmap, quality: Int): ByteArray = ByteArrayOutputStream().use { output ->
        @Suppress("DEPRECATION")
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            Bitmap.CompressFormat.WEBP
        }
        bitmap.compress(format, quality, output)
        output.toByteArray()
    }

    private fun extensionFor(mime: String?): String = when (mime?.lowercase()) {
        "image/jpeg", "image/jpg" -> "jpg"
        "image/webp" -> "webp"
        "image/heic", "image/heif" -> "heic"
        else -> "png"
    }

    private fun moduleDirectories(shell: Shell, moduleId: String): List<String> {
        if (moduleId.isBlank() ||
            moduleId.contains('/') ||
            moduleId.contains('\\') ||
            moduleId == "." ||
            moduleId == ".."
        ) {
            return emptyList()
        }
        return MODULE_ROOTS.mapNotNull { root ->
            val dir = "$root/$moduleId"
            val prop = SuFile("$dir/module.prop").apply { this.shell = shell }
            dir.takeIf { prop.isFile }
        }
    }

    private fun suFile(shell: Shell, path: String): SuFile = SuFile(path).apply { this.shell = shell }

    private fun readText(shell: Shell, path: String): String? = runCatching {
        val file = suFile(shell, path)
        if (!file.isFile) return null
        SuFileInputStream.open(file).use { it.readBytes().toString(Charsets.UTF_8) }
    }.getOrNull()

    private fun writeBytes(shell: Shell, path: String, bytes: ByteArray) {
        SuFileOutputStream.open(suFile(shell, path), false).use { it.write(bytes) }
    }

    private fun deleteFile(shell: Shell, path: String) {
        runCatching { suFile(shell, path).delete() }
    }

    private fun removeCustomBannerFiles(shell: Shell, dir: String, keep: String?) {
        val files = runCatching { suFile(shell, dir).listFiles() }.getOrNull() ?: return
        files.forEach { file ->
            if (file.name.startsWith(CUSTOM_BANNER_PREFIX) && file.name != keep) {
                runCatching { file.delete() }
            }
        }
    }

    private fun backupOriginalBanner(shell: Shell, dir: String, propText: String?) {
        val value = propText?.let(::bannerValueOf)?.takeIf { it.isNotBlank() } ?: return
        if (value.startsWith(CUSTOM_BANNER_PREFIX)) return
        val backup = "$dir/$BANNER_BACKUP_FILE"
        if (suFile(shell, backup).exists()) return
        runCatching { writeBytes(shell, backup, value.toByteArray(Charsets.UTF_8)) }
    }

    private fun restoreOriginalBanner(shell: Shell, dir: String): Boolean {
        val backup = readText(shell, "$dir/$BANNER_BACKUP_FILE")
        return if (backup != null) {
            setBannerValue(shell, dir, backup.ifBlank { null })
        } else {
            setBannerValue(shell, dir, null)
        }
    }

    private fun bannerValueOf(propText: String): String? = propText.lineSequence()
        .firstOrNull { it.substringBefore('=', "").trim() == "banner" }
        ?.substringAfter('=', "")
        ?.trim()

    private fun setBannerValue(shell: Shell, dir: String, value: String?): Boolean {
        val propPath = "$dir/module.prop"
        val original = readText(shell, propPath) ?: return false
        val newline = if (original.contains("\r\n")) "\r\n" else "\n"
        val endsWithNewline = original.endsWith("\n")
        val body = original.removeSuffix("\n").removeSuffix("\r")
        val lines = if (body.isEmpty()) {
            mutableListOf()
        } else {
            body.split(Regex("\r?\n")).toMutableList()
        }
        val index = lines.indexOfFirst { it.substringBefore('=', "").trim() == "banner" }
        when {
            value == null -> if (index >= 0) lines.removeAt(index)
            index >= 0 -> lines[index] = "banner=$value"
            else -> lines += "banner=$value"
        }
        val text = lines.joinToString(newline) + if (endsWithNewline && lines.isNotEmpty()) newline else ""
        return runCatching {
            writeBytes(shell, propPath, text.toByteArray(Charsets.UTF_8))
        }.isSuccess
    }
}
