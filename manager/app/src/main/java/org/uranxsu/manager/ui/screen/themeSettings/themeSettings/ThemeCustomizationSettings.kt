package org.uranxsu.manager.ui.screen.themeSettings

import android.app.Activity
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Android
import androidx.compose.material.icons.twotone.Brush
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Download
import androidx.compose.material.icons.twotone.Edit
import androidx.compose.material.icons.twotone.Height
import androidx.compose.material.icons.twotone.Image
import androidx.compose.material.icons.twotone.Opacity
import androidx.compose.material.icons.twotone.Upload
import androidx.compose.material.icons.twotone.Visibility
import androidx.compose.material.icons.twotone.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yalantis.ucrop.UCrop
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.uranxsu.manager.R
import org.uranxsu.manager.ui.component.KeyPointSlider
import org.uranxsu.manager.ui.component.media.isVideoFile
import org.uranxsu.manager.ui.component.media.isVideoUri
import org.uranxsu.manager.ui.component.settings.SegmentedColumn
import org.uranxsu.manager.ui.component.settings.SettingsBaseWidget
import org.uranxsu.manager.ui.component.settings.SettingsChooseWidget
import org.uranxsu.manager.ui.component.settings.SettingsJumpPageWidget
import org.uranxsu.manager.ui.component.settings.SettingsSwitchWidget
import org.uranxsu.manager.ui.navigation.LocalNavigator
import org.uranxsu.manager.ui.navigation.Route
import org.uranxsu.manager.ui.screen.themeSettings.crop.backgroundCropSize
import org.uranxsu.manager.ui.screen.themeSettings.crop.createBackgroundCropIntent
import org.uranxsu.manager.ui.screen.themeSettings.crop.createBackgroundCropOutputUri
import org.uranxsu.manager.ui.screen.themeSettings.crop.getParcelableExtraCompat
import org.uranxsu.manager.ui.theme.BackgroundManager
import org.uranxsu.manager.ui.theme.CardConfig
import org.uranxsu.manager.ui.theme.HomeCustomizationConfig
import org.uranxsu.manager.ui.theme.ThemeConfig
import org.uranxsu.manager.ui.util.CustomTextConfig
import org.uranxsu.manager.ui.util.ImportedTheme
import org.uranxsu.manager.ui.util.ThemeApplyOptions
import org.uranxsu.manager.ui.util.ThemePackageApplier
import org.uranxsu.manager.ui.util.ThemePackageStorage
import org.uranxsu.manager.ui.viewmodel.HomeViewModel
import org.uranxsu.manager.ui.viewmodel.SettingsUiAction
import org.uranxsu.manager.ui.viewmodel.SettingsUiState
import org.uranxsu.manager.ui.viewmodel.SettingsViewModel

private val STATUS_CARD_HEIGHT_PRESETS = listOf(88, 112, 160, 220, 280)

@Composable
fun AppIconSettings(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    SegmentedColumn(title = stringResource(R.string.app_icon_section)) {
        item {
            SettingsChooseWidget(
                icon = Icons.TwoTone.Android,
                title = stringResource(R.string.icon_switch_title),
                description = stringResource(R.string.icon_switch_summary),
                items = listOf(
                    stringResource(R.string.app_icon_default),
                    stringResource(R.string.app_icon_miku),
                ),
                selectedIndex = if (state.useAltIcon) 1 else 0,
                onSelectedIndexChange = { index ->
                    viewModel.dispatch(SettingsUiAction.SetAlternateIcon(index == 1))
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeCustomizationSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val home: HomeCustomizationConfig = koinInject()
    val failedMsg = stringResource(R.string.background_crop_failed)

    val cropCardLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val outputUri = result.data?.getParcelableExtraCompat<Uri>(UCrop.EXTRA_OUTPUT_URI)
            outputUri?.let { uri ->
                scope.launch {
                    if (!home.saveStatusCardImage(context, uri)) {
                        Toast.makeText(context, failedMsg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else if (result.resultCode == UCrop.RESULT_ERROR) {
            Toast.makeText(context, failedMsg, Toast.LENGTH_SHORT).show()
        }
    }

    val pickCardImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            if (isVideoUri(context, uri)) {
                scope.launch {
                    if (!home.saveStatusCardImage(context, uri)) {
                        Toast.makeText(context, failedMsg, Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                val outputUri = createBackgroundCropOutputUri(context, "status_card_crop")
                val (screenWidth, _) = context.backgroundCropSize()
                val cardAspectY = (home.statusCardHeight.toFloat() / 112f * (screenWidth / 3f)).coerceAtLeast(100f)
                val intent = createBackgroundCropIntent(
                    context = context,
                    sourceUri = uri,
                    outputUri = outputUri,
                    aspectRatioX = screenWidth.toFloat(),
                    aspectRatioY = cardAspectY,
                )
                cropCardLauncher.launch(intent)
            }
        }
    }

    val activeCardFile = remember(home.statusCardImagePath, home.statusCardImageVersion) {
        home.statusCardImagePath?.let(::File)?.takeIf { it.isFile }
    }
    val cardMediaDescription = when {
        activeCardFile == null -> stringResource(R.string.home_card_image_summary)
        isVideoFile(activeCardFile) -> stringResource(R.string.home_card_media_video_active)
        else -> stringResource(R.string.home_card_media_photo_active)
    }

    SegmentedColumn(title = stringResource(R.string.home_customization_section)) {
        item {
            SettingsBaseWidget(
                icon = Icons.TwoTone.Image,
                title = stringResource(R.string.home_card_image),
                description = cardMediaDescription,
                onClick = {
                    pickCardImage.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
                    )
                },
            )
        }

        item {
            SettingsBaseWidget(
                icon = Icons.TwoTone.Height,
                title = stringResource(R.string.home_status_card_height),
                description = stringResource(R.string.home_status_card_height_summary, home.statusCardHeight),
                descriptionColumnContent = {
                    KeyPointSlider(
                        value = home.statusCardHeight.toFloat(),
                        onValueChange = { home.updateStatusCardHeight(it.roundToInt()) },
                        valueRange = 80f..320f,
                        keyPoints = listOf(80f, 112f, 160f, 220f, 280f, 320f),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        STATUS_CARD_HEIGHT_PRESETS.forEach { preset ->
                            val selected = home.statusCardHeight == preset
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (selected) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHigh
                                        },
                                    )
                                    .clickable { home.updateStatusCardHeight(preset) }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "$preset",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    }
                },
            ) {
                Text(
                    text = "${home.statusCardHeight} dp",
                    style = MaterialTheme.typography.labelMediumEmphasized,
                )
            }
        }

        item(visible = home.statusCardImagePath != null) {
            SettingsBaseWidget(
                icon = Icons.TwoTone.Delete,
                title = stringResource(R.string.home_card_image_remove),
                onClick = { home.clearStatusCardImage(context) },
            )
        }

        item(visible = home.statusCardImagePath != null) {
            SettingsBaseWidget(
                icon = Icons.TwoTone.Opacity,
                title = stringResource(R.string.home_card_image_dim),
                descriptionColumnContent = {
                    KeyPointSlider(
                        value = home.statusCardScrim,
                        onValueChange = { home.updateStatusCardScrim(it) },
                        valueRange = 0f..0.85f,
                        keyPoints = listOf(0.25f, 0.5f, 0.75f),
                    )
                },
            ) {
                Text(
                    text = "${(home.statusCardScrim * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelMediumEmphasized,
                )
            }
        }

        item {
            SettingsSwitchWidget(
                icon = Icons.TwoTone.VisibilityOff,
                title = stringResource(R.string.home_hide_status_text),
                checked = home.hideStatusCardText,
                onCheckedChange = { home.updateHideStatusCardText(it) },
            )
        }

        item {
            SettingsSwitchWidget(
                icon = Icons.TwoTone.Brush,
                title = stringResource(R.string.home_hide_status_decoration),
                checked = home.hideStatusCardDecoration,
                onCheckedChange = { home.updateHideStatusCardDecoration(it) },
            )
        }

        item {
            SettingsSwitchWidget(
                icon = Icons.TwoTone.Visibility,
                title = stringResource(R.string.home_show_donate_card),
                checked = home.showDonateCard,
                onCheckedChange = { home.updateShowDonateCard(it) },
            )
        }

        item {
            SettingsSwitchWidget(
                icon = Icons.TwoTone.Visibility,
                title = stringResource(R.string.home_show_learn_more_card),
                checked = home.showLearnMoreCard,
                onCheckedChange = { home.updateShowLearnMoreCard(it) },
            )
        }

        item {
            SettingsSwitchWidget(
                icon = Icons.TwoTone.Image,
                title = stringResource(R.string.module_banner_setting),
                description = stringResource(R.string.module_banner_setting_summary),
                checked = home.enableModuleBanner,
                onCheckedChange = { home.updateEnableModuleBanner(it) },
            )
        }

        item {
            val navigator = LocalNavigator.current
            SettingsJumpPageWidget(
                icon = Icons.TwoTone.Edit,
                title = stringResource(R.string.custom_texts_title),
                description = stringResource(R.string.custom_texts_summary),
                onClick = { navigator.push(Route.CustomTexts) },
            )
        }
    }
}

@Composable
fun ThemePackageSettings(
    state: SettingsUiState,
    settingsViewModel: SettingsViewModel,
    homeViewModel: HomeViewModel,
    showBadge: Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val backgroundManager: BackgroundManager = koinInject()
    val home: HomeCustomizationConfig = koinInject()
    val customTexts: CustomTextConfig = koinInject()
    val isDark = isSystemInDarkTheme()
    val importOk = stringResource(R.string.theme_import_success)
    val importFail = stringResource(R.string.theme_import_failed)
    val exportOk = stringResource(R.string.theme_export_success)
    val exportFail = stringResource(R.string.theme_export_failed)

    var pendingImport by remember { mutableStateOf<ImportedTheme?>(null) }
    var isApplyingImport by remember { mutableStateOf(false) }

    DisposableEffect(pendingImport) {
        val current = pendingImport
        onDispose {
            // Cleanup only if replaced or disposed while not actively applying.
            if (!isApplyingImport && current !== pendingImport) {
                current?.cleanup()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null || isApplyingImport) return@rememberLauncherForActivityResult
        scope.launch {
            val imported = ThemePackageStorage.import(context, uri).getOrNull()
            if (imported == null) {
                Toast.makeText(context, importFail, Toast.LENGTH_LONG).show()
                return@launch
            }
            pendingImport?.cleanup()
            pendingImport = imported
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val (data, bg, cardBg) = ThemePackageApplier.buildExportPackage(
                context = context,
                themeConfig = themeConfig,
                cardConfig = cardConfig,
                homeCustomization = home,
                customTextConfig = customTexts,
                themeMode = state.themeMode,
                showNavigationBarBadge = showBadge,
                enableSwipeDismiss = state.enableSwipeDismiss,
                pagerInterceptionMode = state.pagerInterceptionMode,
            )
            val ok = ThemePackageStorage.export(context, uri, data, bg, cardBg).isSuccess
            Toast.makeText(context, if (ok) exportOk else exportFail, Toast.LENGTH_SHORT).show()
        }
    }

    pendingImport?.let { imported ->
        ThemeImportOptionsDialog(
            imported = imported,
            useDarkVariant = isDark,
            busy = isApplyingImport,
            onDismiss = {
                if (!isApplyingImport) {
                    imported.cleanup()
                    pendingImport = null
                }
            },
            onConfirm = { options ->
                if (isApplyingImport) return@ThemeImportOptionsDialog
                isApplyingImport = true
                scope.launch {
                    try {
                        runCatching {
                            ThemePackageApplier.apply(
                                context = context,
                                imported = imported,
                                useDarkVariant = isDark,
                                options = options,
                                settingsViewModel = settingsViewModel,
                                homeViewModel = homeViewModel,
                                backgroundManager = backgroundManager,
                                homeCustomization = home,
                                customTextConfig = customTexts,
                            )
                        }.onSuccess {
                            Toast.makeText(context, importOk, Toast.LENGTH_SHORT).show()
                        }.onFailure {
                            Toast.makeText(context, importFail, Toast.LENGTH_LONG).show()
                        }
                    } finally {
                        imported.cleanup()
                        pendingImport = null
                        isApplyingImport = false
                    }
                }
            },
        )
    }

    SegmentedColumn(title = stringResource(R.string.theme_package_section)) {
        item {
            SettingsJumpPageWidget(
                icon = Icons.TwoTone.Download,
                title = stringResource(R.string.theme_import_title),
                description = stringResource(R.string.theme_import_summary),
                onClick = { importLauncher.launch(arrayOf("*/*")) },
            )
        }

        item {
            SettingsJumpPageWidget(
                icon = Icons.TwoTone.Upload,
                title = stringResource(R.string.theme_export_title),
                description = stringResource(R.string.theme_export_summary),
                onClick = { exportLauncher.launch(ThemePackageStorage.packageFileName()) },
            )
        }
    }
}

private enum class ThemeImportPart {
    COLORS,
    BACKGROUND,
    CARD_BACKGROUND,
    EFFECTS,
    LAYOUT,
    TEXTS,
}

@Composable
private fun ThemeImportOptionsDialog(
    imported: ImportedTheme,
    useDarkVariant: Boolean,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (ThemeApplyOptions) -> Unit,
) {
    val variantData = if (imported.isAdaptive) {
        if (useDarkVariant) imported.dark?.data else imported.light?.data
    } else {
        null
    }
    val data = imported.data

    val availableParts = remember(imported, useDarkVariant) {
        buildList {
            if (variantData?.themeMode != null || data.themeMode != null ||
                variantData?.keyColor != null || data.keyColor != null ||
                variantData?.colorStyle != null || data.colorStyle != null ||
                variantData?.colorSpec != null || data.colorSpec != null
            ) {
                add(ThemeImportPart.COLORS)
            }
            val bgPath = if (imported.isAdaptive) {
                (if (useDarkVariant) imported.dark?.backgroundPath else imported.light?.backgroundPath)
                    ?: imported.backgroundPath
            } else {
                imported.backgroundPath
            }
            if ((bgPath != null && File(bgPath).isFile) || (variantData?.hasBackground ?: data.hasBackground) == false) {
                add(ThemeImportPart.BACKGROUND)
            }
            val cardBgPath = if (imported.isAdaptive) {
                (if (useDarkVariant) imported.dark?.cardBackgroundPath else imported.light?.cardBackgroundPath)
                    ?: imported.cardBackgroundPath
            } else {
                imported.cardBackgroundPath
            }
            if ((cardBgPath != null && File(cardBgPath).isFile) || (variantData?.hasCardBackground ?: data.hasCardBackground) == false) {
                add(ThemeImportPart.CARD_BACKGROUND)
            }
            if (variantData?.cardBackgroundAlphaPercent != null || data.cardBackgroundAlphaPercent != null ||
                variantData?.customBackgroundScrimPercent != null || data.customBackgroundScrimPercent != null ||
                variantData?.enableBlur != null || data.enableBlur != null
            ) {
                add(ThemeImportPart.EFFECTS)
            }
            if (variantData?.enableFloatingBottomBar != null || data.enableFloatingBottomBar != null ||
                variantData?.enableNavigationBadge != null || data.enableNavigationBadge != null ||
                variantData?.enableSwipeDismiss != null || data.enableSwipeDismiss != null ||
                variantData?.pagerInterceptionMode != null || data.pagerInterceptionMode != null ||
                variantData?.hideHomeCardStatusText != null || data.hideHomeCardStatusText != null ||
                variantData?.hideHomeCardDecoration != null || data.hideHomeCardDecoration != null
            ) {
                add(ThemeImportPart.LAYOUT)
            }
            if (!data.customTexts.isNullOrEmpty() || !data.customHomeTitle.isNullOrBlank()) {
                add(ThemeImportPart.TEXTS)
            }
        }.ifEmpty { ThemeImportPart.entries }
    }

    var selected by remember(imported) { mutableStateOf(availableParts.toSet()) }
    val allSelected = selected.size == availableParts.size

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.theme_import_dialog_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (imported.isAdaptive) {
                    Text(
                        text = stringResource(R.string.theme_import_adaptive_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.theme_options_header),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        enabled = !busy,
                        onClick = {
                            selected = if (allSelected) emptySet() else availableParts.toSet()
                        },
                    ) {
                        Text(
                            stringResource(
                                if (allSelected) R.string.theme_import_select_none else R.string.theme_import_select_all,
                            ),
                        )
                    }
                }
                availableParts.forEach { part ->
                    val labelRes = when (part) {
                        ThemeImportPart.COLORS -> R.string.theme_import_part_colors
                        ThemeImportPart.BACKGROUND -> R.string.theme_import_part_background
                        ThemeImportPart.CARD_BACKGROUND -> R.string.theme_import_part_card_background
                        ThemeImportPart.EFFECTS -> R.string.theme_import_part_effects
                        ThemeImportPart.LAYOUT -> R.string.theme_import_part_layout
                        ThemeImportPart.TEXTS -> R.string.theme_import_part_texts
                    }
                    val checked = part in selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = !busy) {
                                selected = if (checked) selected - part else selected + part
                            }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked,
                            enabled = !busy,
                            onCheckedChange = { isChecked ->
                                selected = if (isChecked) selected + part else selected - part
                            },
                        )
                        Text(
                            text = stringResource(labelRes),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && selected.isNotEmpty(),
                onClick = {
                    onConfirm(
                        ThemeApplyOptions(
                            applyColors = ThemeImportPart.COLORS in selected,
                            applyBackground = ThemeImportPart.BACKGROUND in selected,
                            applyCardBackground = ThemeImportPart.CARD_BACKGROUND in selected,
                            applyEffects = ThemeImportPart.EFFECTS in selected,
                            applyCustomTexts = ThemeImportPart.TEXTS in selected,
                            applyHomeLayout = ThemeImportPart.LAYOUT in selected,
                        ),
                    )
                },
            ) {
                Text(stringResource(R.string.theme_import_apply))
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
