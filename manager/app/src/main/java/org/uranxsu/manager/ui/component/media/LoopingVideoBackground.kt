package org.uranxsu.manager.ui.component.media

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File

private const val TAG = "LoopingVideoBackground"

private val VIDEO_EXTENSIONS = setOf("mp4", "webm", "mkv", "mov", "avi", "3gp", "flv", "m4v")

fun isVideoPath(path: String?): Boolean {
    if (path.isNullOrBlank()) return false
    val ext = path.substringAfterLast('.', "").lowercase()
    return ext in VIDEO_EXTENSIONS
}

fun isVideoFile(file: File?): Boolean {
    if (file == null || !file.isFile) return false
    return isVideoPath(file.name)
}

fun isVideoUri(context: Context, uri: Uri?): Boolean {
    if (uri == null) return false
    val mimeType = runCatching { context.contentResolver.getType(uri) }.getOrNull()
    if (mimeType?.startsWith("video/", ignoreCase = true) == true) {
        return true
    }
    val path = uri.path ?: uri.toString()
    return isVideoPath(path)
}

@Composable
fun LoopingVideoBackground(
    videoUri: Uri,
    modifier: Modifier = Modifier,
    isMuted: Boolean = true,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var mediaPlayerInstance by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    runCatching {
                        if (mediaPlayerInstance?.isPlaying == true) {
                            mediaPlayerInstance?.pause()
                        }
                    }
                }

                Lifecycle.Event.ON_RESUME -> {
                    runCatching {
                        mediaPlayerInstance?.start()
                    }
                }

                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    AndroidView(
        factory = { ctx ->
            TextureView(ctx).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                        try {
                            mediaPlayerInstance?.release()
                            val mp = MediaPlayer().apply {
                                setSurface(Surface(surface))
                                setDataSource(ctx, videoUri)
                                isLooping = true
                                if (isMuted) {
                                    setVolume(0f, 0f)
                                }
                                setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                                setOnPreparedListener { player ->
                                    player.start()
                                }
                                setOnErrorListener { _, what, extra ->
                                    Log.w(TAG, "MediaPlayer error: what=$what, extra=$extra")
                                    true
                                }
                                prepareAsync()
                            }
                            mediaPlayerInstance = mp
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to initialize MediaPlayer for $videoUri", e)
                        }
                    }

                    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                        runCatching {
                            mediaPlayerInstance?.release()
                            mediaPlayerInstance = null
                        }
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                }
            }
        },
        modifier = modifier,
        onRelease = {
            runCatching {
                mediaPlayerInstance?.release()
                mediaPlayerInstance = null
            }
        },
    )
}
