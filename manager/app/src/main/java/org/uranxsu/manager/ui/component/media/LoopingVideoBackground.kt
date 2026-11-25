package org.uranxsu.manager.ui.component.media

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File

private const val TAG = "LoopingVideoBackground"

@Immutable
data class VideoCropTransform(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
) {
    fun sanitized(): VideoCropTransform {
        val safeScale = if (scale.isFinite()) scale.coerceIn(MIN_SCALE, MAX_SCALE) else MIN_SCALE
        val safeX = if (offsetX.isFinite()) offsetX.coerceIn(-1f, 1f) else 0f
        val safeY = if (offsetY.isFinite()) offsetY.coerceIn(-1f, 1f) else 0f
        return VideoCropTransform(scale = safeScale, offsetX = safeX, offsetY = safeY)
    }

    companion object {
        const val MIN_SCALE = 1f
        const val MAX_SCALE = 4f
        val Default = VideoCropTransform()
    }
}

fun computeVideoOverflow(
    viewWidth: Float,
    viewHeight: Float,
    mediaWidth: Float,
    mediaHeight: Float,
    scale: Float,
): Pair<Float, Float> {
    if (viewWidth <= 0f || viewHeight <= 0f || mediaWidth <= 0f || mediaHeight <= 0f) {
        return 0f to 0f
    }
    val safeScale = if (scale.isFinite()) scale.coerceIn(VideoCropTransform.MIN_SCALE, VideoCropTransform.MAX_SCALE) else 1f
    val viewAspect = viewWidth / viewHeight
    val mediaAspect = mediaWidth / mediaHeight
    val baseScaleX = if (mediaAspect > viewAspect) mediaAspect / viewAspect else 1f
    val baseScaleY = if (mediaAspect > viewAspect) 1f else viewAspect / mediaAspect
    val finalScaleX = baseScaleX * safeScale
    val finalScaleY = baseScaleY * safeScale
    val maxDx = (viewWidth * (finalScaleX - 1f) / 2f).coerceAtLeast(0f)
    val maxDy = (viewHeight * (finalScaleY - 1f) / 2f).coerceAtLeast(0f)
    return maxDx to maxDy
}

private val VIDEO_EXTENSIONS = setOf("mp4", "webm", "mkv", "mov", "avi", "3gp", "flv", "m4v")
private val IMAGE_FTYP_BRANDS = setOf(
    "heic",
    "heix",
    "hevc",
    "hevx",
    "heim",
    "heis",
    "mif1",
    "msf1",
    "avif",
    "avis",
)

fun isVideoPath(path: String?): Boolean {
    if (path.isNullOrBlank()) return false
    val ext = path.substringBefore('?').substringBefore('#').substringAfterLast('.', "").lowercase()
    return ext in VIDEO_EXTENSIONS
}

fun isVideoFile(file: File?): Boolean {
    if (file == null || !file.isFile) return false
    return isVideoPath(file.name)
}

private fun queryDisplayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIdx >= 0) cursor.getString(nameIdx) else null
    }
}.getOrNull()

private fun queryMediaStoreMime(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(
        uri,
        arrayOf(MediaStore.MediaColumns.MIME_TYPE),
        null,
        null,
        null,
    )?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val mimeIdx = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
        if (mimeIdx >= 0) cursor.getString(mimeIdx) else null
    }
}.getOrNull()

private fun sniffVideoFormat(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.openInputStream(uri)?.use { stream ->
        val header = ByteArray(16)
        var readTotal = 0
        while (readTotal < header.size) {
            val read = stream.read(header, readTotal, header.size - readTotal)
            if (read <= 0) break
            readTotal += read
        }
        if (readTotal < 4) return@use null

        // EBML (WebM / Matroska)
        if (header[0] == 0x1A.toByte() &&
            header[1] == 0x45.toByte() &&
            header[2] == 0xDF.toByte() &&
            header[3] == 0xA3.toByte()
        ) {
            return@use "webm"
        }

        // RIFF .... AVI
        if (readTotal >= 12 &&
            header[0] == 'R'.code.toByte() &&
            header[1] == 'I'.code.toByte() &&
            header[2] == 'F'.code.toByte() &&
            header[3] == 'F'.code.toByte() &&
            header[8] == 'A'.code.toByte() &&
            header[9] == 'V'.code.toByte() &&
            header[10] == 'I'.code.toByte()
        ) {
            return@use "avi"
        }

        // ISO Base Media File Format: "ftyp"
        if (readTotal >= 12 &&
            header[4] == 'f'.code.toByte() &&
            header[5] == 't'.code.toByte() &&
            header[6] == 'y'.code.toByte() &&
            header[7] == 'p'.code.toByte()
        ) {
            val brand = String(header, 8, 4, Charsets.US_ASCII).lowercase()
            if (brand in IMAGE_FTYP_BRANDS) {
                return@use "image"
            }
            return@use if (brand == "qt  ") "mov" else "mp4"
        }
        null
    }
}.getOrNull()

fun isVideoUri(context: Context, uri: Uri?): Boolean {
    if (uri == null) return false

    val mimeType = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        ?: queryMediaStoreMime(context, uri)
    if (mimeType != null) {
        if (mimeType.startsWith("video/", ignoreCase = true)) return true
        if (mimeType.startsWith("image/", ignoreCase = true)) return false
    }

    val displayName = queryDisplayName(context, uri)
    if (isVideoPath(displayName)) return true

    val path = uri.path ?: uri.toString()
    if (isVideoPath(path)) return true

    return when (sniffVideoFormat(context, uri)) {
        "webm", "avi", "mp4", "mov" -> true
        "image" -> false
        else -> false
    }
}

fun resolveVideoExtension(context: Context, uri: Uri): String {
    val mimeType = (
        runCatching { context.contentResolver.getType(uri) }.getOrNull()
            ?: queryMediaStoreMime(context, uri)
        )?.lowercase()
    when {
        mimeType == null -> Unit
        mimeType.contains("webm") -> return "webm"
        mimeType.contains("matroska") || mimeType.contains("mkv") -> return "mkv"
        mimeType.contains("quicktime") || mimeType.contains("mov") -> return "mov"
        mimeType.contains("3gpp") || mimeType.contains("3gp") -> return "3gp"
        mimeType.contains("x-msvideo") || mimeType.contains("avi") -> return "avi"
        mimeType.contains("mp4") || mimeType.contains("m4v") -> return "mp4"
    }

    val extFromName = queryDisplayName(context, uri)
        ?.substringAfterLast('.', "")
        ?.lowercase()
        ?.takeIf { it in VIDEO_EXTENSIONS }
    if (extFromName != null) return extFromName

    val extFromPath = (uri.path ?: uri.toString())
        .substringBefore('?')
        .substringBefore('#')
        .substringAfterLast('.', "")
        .lowercase()
        .takeIf { it in VIDEO_EXTENSIONS }
    if (extFromPath != null) return extFromPath

    return when (sniffVideoFormat(context, uri)) {
        "webm" -> "webm"
        "avi" -> "avi"
        "mov" -> "mov"
        else -> "mp4"
    }
}

private class VideoPlayerHolder {
    var mediaPlayer: MediaPlayer? = null
    var surface: Surface? = null
    var textureView: TextureView? = null
    var isPrepared: Boolean = false
    var isReleased: Boolean = false
    var videoWidth: Int = 0
    var videoHeight: Int = 0
    var viewWidth: Int = 0
    var viewHeight: Int = 0
    var transform: VideoCropTransform = VideoCropTransform.Default

    fun updateTransform(newTransform: VideoCropTransform) {
        val sanitized = newTransform.sanitized()
        if (transform != sanitized) {
            transform = sanitized
            applyTransform()
        }
    }

    fun applyTransform() {
        val tv = textureView ?: return
        if (viewWidth <= 0 || viewHeight <= 0 || videoWidth <= 0 || videoHeight <= 0) return

        val t = transform.sanitized()
        val viewAspect = viewWidth.toFloat() / viewHeight.toFloat()
        val videoAspect = videoWidth.toFloat() / videoHeight.toFloat()
        var baseScaleX = 1f
        var baseScaleY = 1f
        if (videoAspect > viewAspect) {
            baseScaleX = videoAspect / viewAspect
        } else {
            baseScaleY = viewAspect / videoAspect
        }

        val finalScaleX = baseScaleX * t.scale
        val finalScaleY = baseScaleY * t.scale
        val (maxDx, maxDy) = computeVideoOverflow(
            viewWidth = viewWidth.toFloat(),
            viewHeight = viewHeight.toFloat(),
            mediaWidth = videoWidth.toFloat(),
            mediaHeight = videoHeight.toFloat(),
            scale = t.scale,
        )

        val matrix = Matrix().apply {
            setScale(finalScaleX, finalScaleY, viewWidth / 2f, viewHeight / 2f)
            postTranslate(t.offsetX * maxDx, t.offsetY * maxDy)
        }
        tv.setTransform(matrix)
    }

    fun startIfReady() {
        if (!isPrepared || isReleased) return
        runCatching {
            val mp = mediaPlayer ?: return
            if (!mp.isPlaying) {
                mp.start()
            }
        }
    }

    fun pauseIfReady() {
        if (!isPrepared || isReleased) return
        runCatching {
            val mp = mediaPlayer ?: return
            if (mp.isPlaying) {
                mp.pause()
            }
        }
    }

    fun release() {
        isReleased = true
        isPrepared = false
        runCatching {
            mediaPlayer?.setSurface(null)
            mediaPlayer?.release()
        }
        mediaPlayer = null
        runCatching {
            surface?.release()
        }
        surface = null
    }
}

@Composable
fun LoopingVideoBackground(
    videoUri: Uri,
    modifier: Modifier = Modifier,
    isMuted: Boolean = true,
    isActive: Boolean = true,
    transform: VideoCropTransform = VideoCropTransform.Default,
    onVideoSizeChanged: ((Int, Int) -> Unit)? = null,
    onError: (() -> Unit)? = null,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentIsActive by rememberUpdatedState(isActive)
    val currentIsMuted by rememberUpdatedState(isMuted)
    val currentTransform by rememberUpdatedState(transform.sanitized())
    val currentOnVideoSizeChanged by rememberUpdatedState(onVideoSizeChanged)
    val currentOnError by rememberUpdatedState(onError)

    key(videoUri) {
        val holder = remember(videoUri) { VideoPlayerHolder() }

        LaunchedEffect(isActive) {
            if (isActive) {
                holder.startIfReady()
            } else {
                holder.pauseIfReady()
            }
        }

        LaunchedEffect(currentTransform) {
            holder.updateTransform(currentTransform)
        }

        DisposableEffect(lifecycleOwner, holder) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> holder.pauseIfReady()
                    Lifecycle.Event.ON_RESUME -> if (currentIsActive) holder.startIfReady()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                holder.release()
            }
        }

        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    holder.textureView = this
                    holder.transform = currentTransform
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(
                            surfaceTexture: SurfaceTexture,
                            width: Int,
                            height: Int,
                        ) {
                            holder.release()
                            holder.isReleased = false
                            holder.viewWidth = width
                            holder.viewHeight = height
                            holder.transform = currentTransform
                            val surface = Surface(surfaceTexture)
                            holder.surface = surface
                            try {
                                val mp = MediaPlayer().apply {
                                    setSurface(surface)
                                    setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT)
                                    val filePath = videoUri.path?.takeIf { videoUri.scheme == "file" }
                                    if (filePath != null) {
                                        setDataSource(filePath)
                                    } else {
                                        setDataSource(ctx, videoUri)
                                    }
                                    isLooping = true
                                    if (currentIsMuted) {
                                        setVolume(0f, 0f)
                                    }
                                    setOnVideoSizeChangedListener { _, vw, vh ->
                                        holder.videoWidth = vw
                                        holder.videoHeight = vh
                                        holder.applyTransform()
                                        if (vw > 0 && vh > 0) {
                                            currentOnVideoSizeChanged?.invoke(vw, vh)
                                        }
                                    }
                                    setOnPreparedListener { player ->
                                        if (holder.isReleased) return@setOnPreparedListener
                                        holder.isPrepared = true
                                        holder.videoWidth = player.videoWidth
                                        holder.videoHeight = player.videoHeight
                                        holder.applyTransform()
                                        if (player.videoWidth > 0 && player.videoHeight > 0) {
                                            currentOnVideoSizeChanged?.invoke(player.videoWidth, player.videoHeight)
                                        }
                                        if (currentIsMuted) {
                                            player.setVolume(0f, 0f)
                                        }
                                        if (currentIsActive &&
                                            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                                        ) {
                                            player.start()
                                        }
                                    }
                                    setOnCompletionListener { player ->
                                        if (holder.isPrepared && !holder.isReleased && currentIsActive) {
                                            runCatching {
                                                player.seekTo(0)
                                                player.start()
                                            }
                                        }
                                    }
                                    setOnErrorListener { _, what, extra ->
                                        Log.w(TAG, "MediaPlayer error: what=$what, extra=$extra")
                                        currentOnError?.invoke()
                                        true
                                    }
                                    prepareAsync()
                                }
                                holder.mediaPlayer = mp
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to initialize MediaPlayer for $videoUri", e)
                                holder.release()
                                currentOnError?.invoke()
                            }
                        }

                        override fun onSurfaceTextureSizeChanged(
                            surfaceTexture: SurfaceTexture,
                            width: Int,
                            height: Int,
                        ) {
                            holder.viewWidth = width
                            holder.viewHeight = height
                            holder.applyTransform()
                        }

                        override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                            holder.release()
                            return true
                        }

                        override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit
                    }
                }
            },
            modifier = modifier,
            update = {
                holder.updateTransform(currentTransform)
            },
            onRelease = {
                holder.textureView = null
                holder.release()
            },
        )
    }
}
