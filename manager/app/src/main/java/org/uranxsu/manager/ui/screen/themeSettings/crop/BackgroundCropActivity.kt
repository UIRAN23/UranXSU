package org.uranxsu.manager.ui.screen.themeSettings.crop

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.RotateRight
import androidx.compose.material.icons.twotone.Check
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material.icons.twotone.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.PopupPositionProvider
import androidx.core.content.FileProvider
import com.yalantis.ucrop.UCrop
import com.yalantis.ucrop.callback.BitmapCropCallback
import com.yalantis.ucrop.view.CropImageView
import com.yalantis.ucrop.view.OverlayView
import com.yalantis.ucrop.view.TransformImageView
import com.yalantis.ucrop.view.UCropView
import java.io.File
import java.io.FileOutputStream
import java.io.Serializable
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.uranxsu.manager.R
import org.uranxsu.manager.ui.component.KeyPointSlider
import org.uranxsu.manager.ui.theme.KernelSUTheme
import org.uranxsu.manager.ui.util.adaptiveScaffoldWindowInsets

const val EXTRA_CROP_TITLE = "org.uranxsu.manager.extra.CROP_TITLE"

private enum class CropAspectOption {
    TARGET,
    FREE,
    RATIO_16_9,
    RATIO_4_3,
    RATIO_1_1,
    RATIO_9_16,
}

class BackgroundCropActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val inputUri = intent.getParcelableExtraCompat<Uri>(UCrop.EXTRA_INPUT_URI)
        val outputUri = intent.getParcelableExtraCompat<Uri>(UCrop.EXTRA_OUTPUT_URI)
        val aspectRatioX = intent.getFloatExtra(UCrop.EXTRA_ASPECT_RATIO_X, 0f)
        val aspectRatioY = intent.getFloatExtra(UCrop.EXTRA_ASPECT_RATIO_Y, 0f)
        val maxSizeX = intent.getIntExtra(UCrop.EXTRA_MAX_SIZE_X, 0)
        val maxSizeY = intent.getIntExtra(UCrop.EXTRA_MAX_SIZE_Y, 0)
        val customTitle = intent.getStringExtra(EXTRA_CROP_TITLE)?.takeIf { it.isNotBlank() }

        if (inputUri == null || outputUri == null) {
            finishWithError(IllegalArgumentException("Crop input or output Uri is missing."))
            return
        }

        setContent {
            KernelSUTheme {
                BackgroundCropScreen(
                    inputUri = inputUri,
                    outputUri = outputUri,
                    aspectRatioX = aspectRatioX,
                    aspectRatioY = aspectRatioY,
                    maxSizeX = maxSizeX,
                    maxSizeY = maxSizeY,
                    customTitle = customTitle,
                    onCancel = {
                        setResult(RESULT_CANCELED)
                        finish()
                    },
                    onCropped = { uri, width, height, offsetX, offsetY ->
                        finishWithResult(uri, width, height, offsetX, offsetY)
                    },
                    onError = ::finishWithError,
                )
            }
        }
    }

    private fun finishWithResult(
        uri: Uri,
        width: Int,
        height: Int,
        offsetX: Int,
        offsetY: Int,
    ) {
        val result = Intent()
            .putExtra(UCrop.EXTRA_OUTPUT_URI, uri)
            .putExtra(UCrop.EXTRA_OUTPUT_IMAGE_WIDTH, width)
            .putExtra(UCrop.EXTRA_OUTPUT_IMAGE_HEIGHT, height)
            .putExtra(UCrop.EXTRA_OUTPUT_OFFSET_X, offsetX)
            .putExtra(UCrop.EXTRA_OUTPUT_OFFSET_Y, offsetY)
        setResult(RESULT_OK, result)
        finish()
    }

    private fun finishWithError(error: Throwable) {
        val result = Intent().putExtra(UCrop.EXTRA_ERROR, error as Serializable)
        setResult(UCrop.RESULT_ERROR, result)
        finish()
    }
}

@SuppressLint("AutoboxingStateCreation")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BackgroundCropScreen(
    inputUri: Uri,
    outputUri: Uri,
    aspectRatioX: Float,
    aspectRatioY: Float,
    maxSizeX: Int,
    maxSizeY: Int,
    customTitle: String?,
    onCancel: () -> Unit,
    onCropped: (Uri, Int, Int, Int, Int) -> Unit,
    onError: (Throwable) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hasInitialTarget = aspectRatioX > 0f && aspectRatioY > 0f
    val initialAspectRatio = remember(aspectRatioX, aspectRatioY) {
        if (hasInitialTarget) aspectRatioX / aspectRatioY else null
    }
    val initialAspectOption = remember(hasInitialTarget) {
        if (hasInitialTarget) CropAspectOption.TARGET else CropAspectOption.FREE
    }
    val availableOptions = remember(hasInitialTarget) {
        buildList {
            if (hasInitialTarget) add(CropAspectOption.TARGET)
            add(CropAspectOption.FREE)
            add(CropAspectOption.RATIO_16_9)
            add(CropAspectOption.RATIO_4_3)
            add(CropAspectOption.RATIO_1_1)
            add(CropAspectOption.RATIO_9_16)
        }
    }

    var stagedInputFile by remember(inputUri) { mutableStateOf<File?>(null) }
    var stagingError by remember(inputUri) { mutableStateOf<Throwable?>(null) }
    var cropView by remember { mutableStateOf<UCropView?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isCropping by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }
    var rotationAngle by remember { mutableFloatStateOf(0f) }
    var selectedAspect by remember(initialAspectOption) { mutableStateOf(initialAspectOption) }
    var cropViewReloadToken by remember { mutableIntStateOf(0) }
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)

    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.58f).toArgb()

    // Stage content:// / picker URIs into an app-local file distinct from localOutputFile so
    // uCrop 2.2.11 never overwrites or 0-byte-truncates its own input when mOutputUri == mInputUri.
    LaunchedEffect(inputUri) {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val staged = File(context.cacheDir, "crop_src_${System.currentTimeMillis()}.img")
                context.contentResolver.openInputStream(inputUri)?.use { input ->
                    FileOutputStream(staged).use { output ->
                        input.copyTo(output)
                        output.flush()
                    }
                } ?: error("Cannot open selected image")
                check(staged.isFile && staged.length() > 0L) { "Selected image is empty" }
                staged
            }
        }
        result.onSuccess { file ->
            stagedInputFile = file
        }.onFailure { err ->
            stagingError = err
            isLoading = false
            loadFailed = true
            onError(err)
        }
    }

    val localOutputFile = remember(outputUri) {
        File(context.cacheDir, "crop_work_out_${System.currentTimeMillis()}.jpg")
    }
    val localOutputUri = remember(localOutputFile) { Uri.fromFile(localOutputFile) }

    DisposableEffect(inputUri) {
        onDispose {
            stagedInputFile?.let { file ->
                runCatching { file.delete() }
            }
            runCatching { localOutputFile.delete() }
        }
    }

    fun ratioForOption(option: CropAspectOption): Float? = when (option) {
        CropAspectOption.TARGET -> initialAspectRatio
        CropAspectOption.FREE -> null
        CropAspectOption.RATIO_16_9 -> 16f / 9f
        CropAspectOption.RATIO_4_3 -> 4f / 3f
        CropAspectOption.RATIO_1_1 -> 1f
        CropAspectOption.RATIO_9_16 -> 9f / 16f
    }

    fun applyAspectOption(view: UCropView, option: CropAspectOption) {
        val ratio = ratioForOption(option)
        if (ratio != null && ratio.isFinite() && ratio > 0f) {
            view.cropImageView.setTargetAspectRatio(ratio)
            view.overlayView.setTargetAspectRatio(ratio)
            view.overlayView.setFreestyleCropMode(OverlayView.FREESTYLE_CROP_MODE_ENABLE)
            view.overlayView.setAspectLockedCornerResize(ratio)
        } else {
            view.overlayView.setOnTouchListener(null)
            view.overlayView.setFreestyleCropMode(OverlayView.FREESTYLE_CROP_MODE_ENABLE)
            view.cropImageView.setTargetAspectRatio(CropImageView.SOURCE_IMAGE_ASPECT_RATIO)
        }
        view.cropImageView.setImageToWrapCropBounds(true)
    }

    fun syncCropControls() {
        cropView?.cropImageView?.let { imageView ->
            rotationAngle = imageView.currentAngle.normalizedCropAngle()
        }
    }

    fun updateRotation(targetAngle: Float) {
        cropView?.cropImageView?.let { imageView ->
            val normalizedTarget = targetAngle.normalizedCropAngle()
            val delta = (normalizedTarget - imageView.currentAngle.normalizedCropAngle())
                .normalizedCropAngle()
            imageView.cancelAllAnimations()
            imageView.postRotate(delta)
            rotationAngle = normalizedTarget
        }
    }

    fun finishRotationAdjustment() {
        cropView?.cropImageView?.setImageToWrapCropBounds()
    }

    SideEffect {
        topAppBarState.heightOffset = topAppBarState.heightOffsetLimit
        topAppBarState.contentOffset = 0f
    }

    suspend fun deliverOutputFile(croppedLocalUri: Uri, width: Int, height: Int, offsetX: Int, offsetY: Int) {
        withContext(Dispatchers.IO) {
            val croppedFile = croppedLocalUri.path?.let(::File)?.takeIf { it.isFile } ?: localOutputFile
            (
                context.contentResolver.openOutputStream(outputUri, "wt")
                    ?: context.contentResolver.openOutputStream(outputUri)
                )?.use { dest ->
                croppedFile.inputStream().use { src ->
                    src.copyTo(dest)
                    dest.flush()
                }
            } ?: error("Failed to write cropped output")
            runCatching { croppedFile.delete() }
        }
        onCropped(outputUri, width, height, offsetX, offsetY)
    }

    fun cropCurrentImage() {
        val imageView = cropView?.cropImageView ?: return
        isCropping = true
        imageView.setImageToWrapCropBounds()
        imageView.cropAndSaveImage(
            Bitmap.CompressFormat.JPEG,
            92,
            object : BitmapCropCallback {
                override fun onBitmapCropped(
                    resultUri: Uri,
                    offsetX: Int,
                    offsetY: Int,
                    imageWidth: Int,
                    imageHeight: Int,
                ) {
                    scope.launch {
                        runCatching {
                            deliverOutputFile(resultUri, imageWidth, imageHeight, offsetX, offsetY)
                        }.onFailure { err ->
                            isCropping = false
                            onError(err)
                        }
                    }
                }

                override fun onCropFailure(t: Throwable) {
                    (context as Activity).runOnUiThread {
                        isCropping = false
                        onError(t)
                    }
                }
            },
        )
    }

    fun useOriginalImage() {
        val src = stagedInputFile ?: return
        isCropping = true
        scope.launch {
            runCatching {
                val (w, h) = withContext(Dispatchers.IO) {
                    src.inputStream().use { input ->
                        FileOutputStream(localOutputFile).use { output ->
                            input.copyTo(output)
                            output.flush()
                        }
                    }
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(localOutputFile.absolutePath, opts)
                    opts.outWidth.coerceAtLeast(1) to opts.outHeight.coerceAtLeast(1)
                }
                deliverOutputFile(localOutputUri, w, h, 0, 0)
            }.onFailure { err ->
                isCropping = false
                onError(err)
            }
        }
    }

    Scaffold(
        contentWindowInsets = adaptiveScaffoldWindowInsets(),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(customTitle ?: stringResource(R.string.background_crop_title)) },
                navigationIcon = {
                    CropTooltipIconButton(
                        tooltip = stringResource(R.string.cancel),
                        enabled = !isCropping,
                        onClick = onCancel,
                    ) {
                        Icon(
                            Icons.TwoTone.Close,
                            contentDescription = stringResource(R.string.cancel),
                        )
                    }
                },
                actions = {
                    CropTooltipIconButton(
                        tooltip = stringResource(R.string.crop_rotate_90),
                        onClick = {
                            updateRotation(rotationAngle + 90f)
                            finishRotationAdjustment()
                        },
                        enabled = !isLoading && !isCropping && !loadFailed,
                    ) {
                        Icon(
                            Icons.AutoMirrored.TwoTone.RotateRight,
                            contentDescription = stringResource(R.string.crop_rotate_90),
                        )
                    }
                    CropTooltipIconButton(
                        tooltip = stringResource(R.string.background_crop_reset),
                        onClick = {
                            cropView = null
                            isLoading = true
                            loadFailed = false
                            rotationAngle = 0f
                            selectedAspect = initialAspectOption
                            cropViewReloadToken++
                        },
                        enabled = !isLoading && !isCropping && !loadFailed,
                    ) {
                        Icon(
                            Icons.TwoTone.RestartAlt,
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
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(availableOptions) { option ->
                            val label = when (option) {
                                CropAspectOption.TARGET -> stringResource(R.string.crop_ratio_target)
                                CropAspectOption.FREE -> stringResource(R.string.crop_ratio_free)
                                CropAspectOption.RATIO_16_9 -> "16:9"
                                CropAspectOption.RATIO_4_3 -> "4:3"
                                CropAspectOption.RATIO_1_1 -> "1:1"
                                CropAspectOption.RATIO_9_16 -> "9:16"
                            }
                            FilterChip(
                                selected = selectedAspect == option,
                                enabled = !isLoading && !isCropping && !loadFailed,
                                onClick = {
                                    selectedAspect = option
                                    cropView?.let { applyAspectOption(it, option) }
                                },
                                label = { Text(label) },
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.background_crop_rotation_angle),
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
                                value = rotationAngle.coerceIn(-180f, 180f),
                                onValueChange = ::updateRotation,
                                valueRange = -180f..180f,
                                keyPoints = listOf(-90f, 0f, 90f),
                                enabled = !isLoading && !isCropping && !loadFailed,
                                onValueChangeFinished = ::finishRotationAdjustment,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Text(
                            text = "${rotationAngle.toInt()}\u00B0",
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
                        OutlinedButton(
                            onClick = ::useOriginalImage,
                            enabled = stagedInputFile != null && !isLoading && !isCropping && !loadFailed,
                        ) {
                            Text(text = stringResource(R.string.crop_use_original))
                        }
                        Button(
                            onClick = ::cropCurrentImage,
                            enabled = !isLoading && !isCropping && !loadFailed,
                        ) {
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            val localInputUri = remember(stagedInputFile) { stagedInputFile?.let(Uri::fromFile) }
            if (localInputUri != null && stagingError == null) {
                key(localInputUri, cropViewReloadToken) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { viewContext ->
                            UCropView(viewContext, null).apply {
                                layoutParams = FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                )
                                cropImageView.isScaleEnabled = true
                                cropImageView.isRotateEnabled = false
                                cropImageView.isGestureEnabled = true
                                cropImageView.doubleTapScaleSteps = 5
                                cropImageView.setMaxScaleMultiplier(10f)
                                val cropAspectRatio = ratioForOption(selectedAspect)
                                if (cropAspectRatio != null && cropAspectRatio > 0f) {
                                    cropImageView.setTargetAspectRatio(cropAspectRatio)
                                    overlayView.setTargetAspectRatio(cropAspectRatio)
                                    overlayView.setAspectLockedCornerResize(cropAspectRatio)
                                } else {
                                    cropImageView.setTargetAspectRatio(CropImageView.SOURCE_IMAGE_ASPECT_RATIO)
                                }
                                if (maxSizeX > 0) cropImageView.setMaxResultImageSizeX(maxSizeX)
                                if (maxSizeY > 0) cropImageView.setMaxResultImageSizeY(maxSizeY)
                                overlayView.setShowCropFrame(true)
                                overlayView.setShowCropGrid(true)
                                overlayView.setFreestyleCropMode(OverlayView.FREESTYLE_CROP_MODE_ENABLE)
                                overlayView.setCropFrameColor(primaryColor)
                                overlayView.setCropGridColor(onSurfaceColor)
                                overlayView.setDimmedColor(scrimColor)
                                cropImageView.setTransformImageListener(
                                    object : TransformImageView.TransformImageListener {
                                        override fun onLoadComplete() {
                                            isLoading = false
                                            loadFailed = false
                                            syncCropControls()
                                        }

                                        override fun onLoadFailure(e: Exception) {
                                            isLoading = false
                                            loadFailed = true
                                            onError(e)
                                        }

                                        override fun onRotate(currentAngle: Float) {
                                            rotationAngle = currentAngle.normalizedCropAngle()
                                        }

                                        override fun onScale(currentScale: Float) = Unit
                                    },
                                )
                                cropView = this
                                runCatching {
                                    cropImageView.setImageUri(localInputUri, localOutputUri)
                                }.onFailure {
                                    visibility = View.INVISIBLE
                                    isLoading = false
                                    loadFailed = true
                                    onError(it)
                                }
                            }
                        },
                        update = {
                            it.visibility =
                                if (isLoading || loadFailed) View.INVISIBLE else View.VISIBLE
                            it.overlayView.setCropFrameColor(primaryColor)
                            it.overlayView.setCropGridColor(onSurfaceColor)
                            it.overlayView.setDimmedColor(scrimColor)
                        },
                    )
                }
            }

            if (isLoading || isCropping) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    LoadingIndicator()
                    Text(
                        text = stringResource(R.string.background_crop_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("DEPRECATION")
@Composable
private fun CropTooltipIconButton(
    tooltip: String,
    enabled: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    TooltipBox(
        positionProvider = remember { BelowAnchorTooltipPositionProvider() },
        tooltip = {
            PlainTooltip {
                Text(tooltip)
            }
        },
        state = rememberTooltipState(),
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
        ) {
            icon()
        }
    }
}

@SuppressLint("ClickableViewAccessibility")
private fun OverlayView.setAspectLockedCornerResize(aspectRatio: Float) {
    if (!aspectRatio.isFinite() || aspectRatio <= 0f) {
        return
    }

    val touchThreshold = 48f * resources.displayMetrics.density
    val minCropSize = resources
        .getDimensionPixelSize(com.yalantis.ucrop.R.dimen.ucrop_default_crop_rect_min_size)
        .toFloat()
    var activeCornerIndex = -1

    setOnTouchListener { view, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activeCornerIndex = cropViewRect.findTouchedCropCorner(
                    touchX = event.x,
                    touchY = event.y,
                    threshold = touchThreshold,
                )
                if (activeCornerIndex != -1) {
                    view.onTouchEvent(event)
                } else {
                    false
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (activeCornerIndex == -1 || event.pointerCount != 1) {
                    false
                } else {
                    val lockedPoint = cropViewRect.aspectLockedCornerPoint(
                        cornerIndex = activeCornerIndex,
                        touchX = event.x,
                        touchY = event.y,
                        aspectRatio = aspectRatio,
                        minX = paddingLeft.toFloat(),
                        minY = paddingTop.toFloat(),
                        maxX = (width - paddingRight).toFloat(),
                        maxY = (height - paddingBottom).toFloat(),
                        minCropSize = minCropSize,
                    )
                    val adjustedEvent = event.copyWithLocation(lockedPoint.x, lockedPoint.y)
                    try {
                        view.onTouchEvent(adjustedEvent)
                    } finally {
                        adjustedEvent.recycle()
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val handled = if (activeCornerIndex != -1) {
                    view.onTouchEvent(event)
                } else {
                    false
                }
                activeCornerIndex = -1
                handled
            }

            else -> false
        }
    }
}

private fun RectF.findTouchedCropCorner(
    touchX: Float,
    touchY: Float,
    threshold: Float,
): Int {
    val thresholdSquared = threshold * threshold
    val corners = floatArrayOf(
        left,
        top,
        right,
        top,
        right,
        bottom,
        left,
        bottom,
    )

    var closestCornerIndex = -1
    var closestDistance = thresholdSquared
    for (index in corners.indices step 2) {
        val deltaX = touchX - corners[index]
        val deltaY = touchY - corners[index + 1]
        val distance = deltaX * deltaX + deltaY * deltaY
        if (distance <= closestDistance) {
            closestDistance = distance
            closestCornerIndex = index / 2
        }
    }
    return closestCornerIndex
}

private fun RectF.aspectLockedCornerPoint(
    cornerIndex: Int,
    touchX: Float,
    touchY: Float,
    aspectRatio: Float,
    minX: Float,
    minY: Float,
    maxX: Float,
    maxY: Float,
    minCropSize: Float,
): CropTouchPoint {
    val anchorX: Float
    val anchorY: Float
    val directionX: Float
    val directionY: Float
    when (cornerIndex) {
        0 -> {
            anchorX = right
            anchorY = bottom
            directionX = -1f
            directionY = -1f
        }

        1 -> {
            anchorX = left
            anchorY = bottom
            directionX = 1f
            directionY = -1f
        }

        2 -> {
            anchorX = left
            anchorY = top
            directionX = 1f
            directionY = 1f
        }

        else -> {
            anchorX = right
            anchorY = top
            directionX = -1f
            directionY = 1f
        }
    }

    val projectedDistance = (
        (touchX - anchorX) * directionX * aspectRatio +
            (touchY - anchorY) * directionY
        ) / (aspectRatio * aspectRatio + 1f)
    val maxWidth = if (directionX > 0f) maxX - anchorX else anchorX - minX
    val maxHeight = if (directionY > 0f) maxY - anchorY else anchorY - minY
    val maxDistance = min(maxWidth / aspectRatio, maxHeight).coerceAtLeast(0f)
    val minDistance = max(minCropSize / aspectRatio, minCropSize)
    val safeDistance = if (maxDistance <= minDistance) {
        maxDistance
    } else {
        projectedDistance.coerceIn(minDistance, maxDistance)
    }

    return CropTouchPoint(
        x = anchorX + directionX * aspectRatio * safeDistance,
        y = anchorY + directionY * safeDistance,
    )
}

private fun MotionEvent.copyWithLocation(x: Float, y: Float): MotionEvent = MotionEvent.obtain(this).apply {
    setLocation(x, y)
}

private data class CropTouchPoint(
    val x: Float,
    val y: Float,
)

private class BelowAnchorTooltipPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val centeredX = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
        val x = centeredX.coerceIn(0, windowSize.width - popupContentSize.width)
        val preferredY = anchorBounds.bottom + 8
        val fallbackY = anchorBounds.top - popupContentSize.height - 8
        val y = if (preferredY + popupContentSize.height <= windowSize.height) {
            preferredY
        } else {
            fallbackY.coerceAtLeast(0)
        }
        return IntOffset(x, y)
    }
}

fun createFileProviderUri(context: Context, file: File): Uri = FileProvider.getUriForFile(
    context,
    "${context.packageName}.fileprovider",
    file,
)

fun createBackgroundCropOutputUri(context: Context, prefix: String = "crop"): Uri {
    val outputFile = File(context.cacheDir, "${prefix}_${System.currentTimeMillis()}.jpg").apply {
        parentFile?.mkdirs()
        delete()
        createNewFile()
        deleteOnExit()
    }

    return createFileProviderUri(context, outputFile)
}

private val CROP_CACHE_TEMP_PREFIXES = listOf(
    "crop_input_",
    "bg_crop_in_",
    "bg_crop_src_",
    "card_crop_in_",
    "card_crop_src_",
    "mod_banner_crop_in_",
    "crop_src_",
    "crop_work_out_",
    "background_crop_",
    "status_card_crop_",
    "module_banner_crop_",
)

private fun pruneOldCropCacheFiles(cacheDir: File) {
    val cutoff = System.currentTimeMillis() - 30L * 60L * 1000L
    cacheDir.listFiles()?.forEach { file ->
        if (file.isFile &&
            file.lastModified() < cutoff &&
            CROP_CACHE_TEMP_PREFIXES.any { file.name.startsWith(it) }
        ) {
            runCatching { file.delete() }
        }
    }
}

suspend fun stageCropInputUri(
    context: Context,
    sourceUri: Uri,
    prefix: String = "crop_input",
): Uri? = withContext(Dispatchers.IO) {
    val appContext = context.applicationContext
    val ownAuthority = "${appContext.packageName}.fileprovider"
    if (sourceUri.scheme == ContentResolver.SCHEME_CONTENT && sourceUri.authority == ownAuthority) {
        return@withContext sourceUri
    }
    if (sourceUri.scheme == ContentResolver.SCHEME_FILE && !sourceUri.path.isNullOrBlank()) {
        val file = File(sourceUri.path!!)
        if (file.isFile && file.length() > 0L) {
            return@withContext createFileProviderUri(appContext, file)
        }
    }
    runCatching {
        pruneOldCropCacheFiles(appContext.cacheDir)
        val stagedFile = File(appContext.cacheDir, "${prefix}_${System.currentTimeMillis()}.img").apply {
            deleteOnExit()
        }
        appContext.contentResolver.openInputStream(sourceUri)?.use { input ->
            FileOutputStream(stagedFile).use { output ->
                input.copyTo(output)
                output.flush()
            }
        } ?: error("Cannot open selected image")
        check(stagedFile.isFile && stagedFile.length() > 0L) { "Staged crop image is empty" }
        createFileProviderUri(appContext, stagedFile)
    }.onFailure { err ->
        Log.w("BackgroundCropActivity", "Failed to stage crop input URI: $sourceUri", err)
    }.getOrNull()
}

suspend fun copyUriToCropOutput(
    context: Context,
    sourceUri: Uri,
    outputUri: Uri,
): Boolean = withContext(Dispatchers.IO) {
    val appContext = context.applicationContext
    runCatching {
        appContext.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
            (
                appContext.contentResolver.openOutputStream(outputUri, "wt")
                    ?: appContext.contentResolver.openOutputStream(outputUri)
                )?.use { outputStream ->
                inputStream.copyTo(outputStream)
                outputStream.flush()
            } ?: error("Failed to open crop output stream")
        } ?: error("Failed to open selected image")
        true
    }.onFailure { err ->
        Log.w("BackgroundCropActivity", "Failed to copy crop URI $sourceUri -> $outputUri", err)
    }.getOrDefault(false)
}

fun createBackgroundCropIntent(
    context: Context,
    sourceUri: Uri,
    outputUri: Uri,
    aspectRatioX: Float,
    aspectRatioY: Float,
    maxSizeX: Int = 0,
    maxSizeY: Int = 0,
    title: String? = null,
): Intent {
    val safeSourceUri = if (sourceUri.scheme == "file" && !sourceUri.path.isNullOrBlank()) {
        createFileProviderUri(context, File(sourceUri.path!!))
    } else {
        sourceUri
    }
    val safeOutputUri = if (outputUri.scheme == "file" && !outputUri.path.isNullOrBlank()) {
        createFileProviderUri(context, File(outputUri.path!!))
    } else {
        outputUri
    }
    return Intent(context, BackgroundCropActivity::class.java).apply {
        // Photo Picker URIs grant read-only access; never add FLAG_GRANT_WRITE_URI_PERMISSION
        // when attaching a read-only input URI to clipData, otherwise ActivityTaskManager throws SecurityException.
        if (safeSourceUri.scheme == ContentResolver.SCHEME_CONTENT) {
            clipData = ClipData.newRawUri("crop_input", safeSourceUri)
        }
        putExtra(UCrop.EXTRA_INPUT_URI, safeSourceUri)
        putExtra(UCrop.EXTRA_OUTPUT_URI, safeOutputUri)
        putExtra(UCrop.EXTRA_ASPECT_RATIO_X, aspectRatioX)
        putExtra(UCrop.EXTRA_ASPECT_RATIO_Y, aspectRatioY)
        if (maxSizeX > 0) putExtra(UCrop.EXTRA_MAX_SIZE_X, maxSizeX)
        if (maxSizeY > 0) putExtra(UCrop.EXTRA_MAX_SIZE_Y, maxSizeY)
        if (!title.isNullOrBlank()) putExtra(EXTRA_CROP_TITLE, title)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

fun Context.backgroundCropSize(): Pair<Int, Int> {
    val displayMetrics = resources.displayMetrics
    return displayMetrics.widthPixels to displayMetrics.heightPixels
}

fun Context.statusCardCropDimensions(cardHeightDp: Int): Pair<Float, Float> {
    val dm = resources.displayMetrics
    val widthPx = (dm.widthPixels - 32f * dm.density).coerceAtLeast(100f)
    val heightPx = (cardHeightDp * dm.density).coerceAtLeast(80f)
    return widthPx to heightPx
}

inline fun <reified T> Intent.getParcelableExtraCompat(name: String): T? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
    getParcelableExtra(name, T::class.java)
} else {
    @Suppress("DEPRECATION")
    getParcelableExtra(name)
}

private fun Float.normalizedCropAngle(): Float {
    var angle = this % 360f
    if (angle > 180) angle -= 360f
    if (angle < -180) angle += 360f
    return angle
}
