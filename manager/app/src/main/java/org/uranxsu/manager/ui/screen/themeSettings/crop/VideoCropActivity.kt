package org.uranxsu.manager.ui.screen.themeSettings.crop

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Check
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material.icons.twotone.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.util.Locale
import org.uranxsu.manager.R
import org.uranxsu.manager.ui.component.KeyPointSlider
import org.uranxsu.manager.ui.component.media.LoopingVideoBackground
import org.uranxsu.manager.ui.component.media.VideoCropTransform
import org.uranxsu.manager.ui.component.media.computeVideoOverflow
import org.uranxsu.manager.ui.theme.KernelSUTheme
import org.uranxsu.manager.ui.util.adaptiveScaffoldWindowInsets

const val EXTRA_VIDEO_URI = "org.uranxsu.manager.extra.VIDEO_URI"
const val EXTRA_VIDEO_ASPECT_X = "org.uranxsu.manager.extra.VIDEO_ASPECT_X"
const val EXTRA_VIDEO_ASPECT_Y = "org.uranxsu.manager.extra.VIDEO_ASPECT_Y"
const val EXTRA_VIDEO_CORNER_DP = "org.uranxsu.manager.extra.VIDEO_CORNER_DP"
const val EXTRA_VIDEO_TITLE = "org.uranxsu.manager.extra.VIDEO_TITLE"
const val EXTRA_VIDEO_SCALE = "org.uranxsu.manager.extra.VIDEO_SCALE"
const val EXTRA_VIDEO_OFFSET_X = "org.uranxsu.manager.extra.VIDEO_OFFSET_X"
const val EXTRA_VIDEO_OFFSET_Y = "org.uranxsu.manager.extra.VIDEO_OFFSET_Y"

class VideoCropActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val videoUri = intent.getParcelableExtraCompat<Uri>(EXTRA_VIDEO_URI)
        if (videoUri == null) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        val aspectX = intent.getFloatExtra(EXTRA_VIDEO_ASPECT_X, 9f).coerceAtLeast(1f)
        val aspectY = intent.getFloatExtra(EXTRA_VIDEO_ASPECT_Y, 16f).coerceAtLeast(1f)
        val cornerRadiusDp = intent.getFloatExtra(EXTRA_VIDEO_CORNER_DP, 20f).coerceIn(0f, 36f)
        val customTitle = intent.getStringExtra(EXTRA_VIDEO_TITLE)?.takeIf { it.isNotBlank() }
        val initialTransform = VideoCropTransform(
            scale = intent.getFloatExtra(EXTRA_VIDEO_SCALE, 1f),
            offsetX = intent.getFloatExtra(EXTRA_VIDEO_OFFSET_X, 0f),
            offsetY = intent.getFloatExtra(EXTRA_VIDEO_OFFSET_Y, 0f),
        ).sanitized()

        setContent {
            KernelSUTheme {
                VideoCropScreen(
                    videoUri = videoUri,
                    aspectX = aspectX,
                    aspectY = aspectY,
                    cornerRadiusDp = cornerRadiusDp,
                    customTitle = customTitle,
                    initialTransform = initialTransform,
                    onCancel = {
                        setResult(RESULT_CANCELED)
                        finish()
                    },
                    onApply = { transform ->
                        val sanitized = transform.sanitized()
                        val data = Intent().apply {
                            putExtra(EXTRA_VIDEO_URI, videoUri)
                            putExtra(EXTRA_VIDEO_SCALE, sanitized.scale)
                            putExtra(EXTRA_VIDEO_OFFSET_X, sanitized.offsetX)
                            putExtra(EXTRA_VIDEO_OFFSET_Y, sanitized.offsetY)
                        }
                        setResult(RESULT_OK, data)
                        finish()
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VideoCropScreen(
    videoUri: Uri,
    aspectX: Float,
    aspectY: Float,
    cornerRadiusDp: Float,
    customTitle: String?,
    initialTransform: VideoCropTransform,
    onCancel: () -> Unit,
    onApply: (VideoCropTransform) -> Unit,
) {
    var transform by remember(initialTransform) { mutableStateOf(initialTransform) }
    var videoSize by remember { mutableStateOf(0 to 0) }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }
    var previewError by remember { mutableStateOf(false) }

    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)
    val density = LocalDensity.current

    SideEffect {
        topAppBarState.heightOffset = topAppBarState.heightOffsetLimit
        topAppBarState.contentOffset = 0f
    }

    val targetAspect = (aspectX / aspectY).coerceIn(0.25f, 5f)
    val frameShape = RoundedCornerShape(cornerRadiusDp.dp)

    Scaffold(
        contentWindowInsets = adaptiveScaffoldWindowInsets(),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(customTitle ?: stringResource(R.string.video_crop_title)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.TwoTone.Close,
                            contentDescription = stringResource(R.string.cancel),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { transform = VideoCropTransform.Default }) {
                        Icon(
                            imageVector = Icons.TwoTone.RestartAlt,
                            contentDescription = stringResource(R.string.background_crop_reset),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.wrapContentHeight(),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.video_crop_zoom),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(84.dp),
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                        ) {
                            KeyPointSlider(
                                value = transform.scale.coerceIn(
                                    VideoCropTransform.MIN_SCALE,
                                    VideoCropTransform.MAX_SCALE,
                                ),
                                onValueChange = { newScale ->
                                    transform = transform.copy(scale = newScale).sanitized()
                                },
                                valueRange = VideoCropTransform.MIN_SCALE..VideoCropTransform.MAX_SCALE,
                                keyPoints = listOf(1f, 1.5f, 2f, 3f, 4f),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Text(
                            text = String.format(Locale.US, "%.1fx", transform.scale),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .width(48.dp)
                                .padding(start = 8.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(onClick = { transform = VideoCropTransform.Default }) {
                            Text(text = stringResource(R.string.background_crop_reset))
                        }
                        Button(onClick = { onApply(transform.sanitized()) }) {
                            Icon(
                                imageVector = Icons.TwoTone.Check,
                                contentDescription = null,
                            )
                            Text(
                                text = stringResource(R.string.background_crop_apply),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val maxW = with(density) { maxWidth.toPx() }
                val maxH = with(density) { maxHeight.toPx() }
                val (frameWpx, frameHpx) = if (maxW > 0f && maxH > 0f) {
                    val containerAspect = maxW / maxH
                    if (containerAspect > targetAspect) {
                        (maxH * targetAspect) to maxH
                    } else {
                        maxW to (maxW / targetAspect)
                    }
                } else {
                    1f to 1f
                }
                val frameWdp = with(density) { frameWpx.toDp() }
                val frameHdp = with(density) { frameHpx.toDp() }

                Box(
                    modifier = Modifier
                        .size(width = frameWdp, height = frameHdp)
                        .clip(frameShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .onSizeChanged { previewSize = it }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val vw = previewSize.width.toFloat()
                                val vh = previewSize.height.toFloat()
                                val (vidW, vidH) = videoSize
                                val current = transform
                                val newScale = (current.scale * zoom).coerceIn(
                                    VideoCropTransform.MIN_SCALE,
                                    VideoCropTransform.MAX_SCALE,
                                )
                                val (maxDx, maxDy) = computeVideoOverflow(
                                    viewWidth = vw,
                                    viewHeight = vh,
                                    mediaWidth = vidW.toFloat(),
                                    mediaHeight = vidH.toFloat(),
                                    scale = newScale,
                                )
                                val newOffsetX = if (maxDx > 0.5f) {
                                    (current.offsetX + pan.x / maxDx).coerceIn(-1f, 1f)
                                } else {
                                    0f
                                }
                                val newOffsetY = if (maxDy > 0.5f) {
                                    (current.offsetY + pan.y / maxDy).coerceIn(-1f, 1f)
                                } else {
                                    0f
                                }
                                transform = VideoCropTransform(
                                    scale = newScale,
                                    offsetX = newOffsetX,
                                    offsetY = newOffsetY,
                                ).sanitized()
                            }
                        }
                        .border(2.dp, MaterialTheme.colorScheme.primary, frameShape),
                ) {
                    LoopingVideoBackground(
                        videoUri = videoUri,
                        modifier = Modifier.fillMaxSize(),
                        isMuted = true,
                        isActive = true,
                        transform = transform,
                        onVideoSizeChanged = { w, h ->
                            videoSize = w to h
                            previewError = false
                        },
                        onError = {
                            previewError = true
                        },
                    )

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val gridColor = Color.White.copy(alpha = 0.28f)
                        val stroke = 1.dp.toPx()
                        val w = size.width
                        val h = size.height
                        drawLine(gridColor, Offset(w / 3f, 0f), Offset(w / 3f, h), strokeWidth = stroke)
                        drawLine(gridColor, Offset(w * 2f / 3f, 0f), Offset(w * 2f / 3f, h), strokeWidth = stroke)
                        drawLine(gridColor, Offset(0f, h / 3f), Offset(w, h / 3f), strokeWidth = stroke)
                        drawLine(gridColor, Offset(0f, h * 2f / 3f), Offset(w, h * 2f / 3f), strokeWidth = stroke)
                    }

                    if (previewError) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.8f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.video_crop_load_failed),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.video_crop_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

fun createVideoCropIntent(
    context: Context,
    videoUri: Uri,
    aspectRatioX: Float,
    aspectRatioY: Float,
    cornerRadiusDp: Float = 20f,
    title: String? = null,
    initialTransform: VideoCropTransform = VideoCropTransform.Default,
): Intent = Intent(context, VideoCropActivity::class.java).apply {
    clipData = ClipData.newRawUri("video_crop_input", videoUri)
    putExtra(EXTRA_VIDEO_URI, videoUri)
    putExtra(EXTRA_VIDEO_ASPECT_X, aspectRatioX)
    putExtra(EXTRA_VIDEO_ASPECT_Y, aspectRatioY)
    putExtra(EXTRA_VIDEO_CORNER_DP, cornerRadiusDp)
    if (!title.isNullOrBlank()) putExtra(EXTRA_VIDEO_TITLE, title)
    val sanitized = initialTransform.sanitized()
    putExtra(EXTRA_VIDEO_SCALE, sanitized.scale)
    putExtra(EXTRA_VIDEO_OFFSET_X, sanitized.offsetX)
    putExtra(EXTRA_VIDEO_OFFSET_Y, sanitized.offsetY)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}

fun parseVideoCropResult(data: Intent?): Pair<Uri?, VideoCropTransform>? {
    if (data == null) return null
    val uri = data.getParcelableExtraCompat<Uri>(EXTRA_VIDEO_URI)
    val transform = VideoCropTransform(
        scale = data.getFloatExtra(EXTRA_VIDEO_SCALE, 1f),
        offsetX = data.getFloatExtra(EXTRA_VIDEO_OFFSET_X, 0f),
        offsetY = data.getFloatExtra(EXTRA_VIDEO_OFFSET_Y, 0f),
    ).sanitized()
    return uri to transform
}
