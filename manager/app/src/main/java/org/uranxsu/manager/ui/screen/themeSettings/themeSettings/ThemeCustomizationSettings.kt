package org.uranxsu.manager.ui.screen.themeSettings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Android
import androidx.compose.material.icons.twotone.Brush
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Download
import androidx.compose.material.icons.twotone.Edit
import androidx.compose.material.icons.twotone.Height
import androidx.compose.material.icons.twotone.Image
import androidx.compose.material.icons.twotone.Opacity
import androidx.compose.material.icons.twotone.Palette
import androidx.compose.material.icons.twotone.Upload
import androidx.compose.material.icons.twotone.Visibility
import androidx.compose.material.icons.twotone.VisibilityOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.yalantis.ucrop.UCrop
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.uranxsu.manager.R
import org.uranxsu.manager.ui.component.KeyPointSlider
import org.uranxsu.manager.ui.component.media.isVideoUri
import org.uranxsu.manager.ui.component.settings.SegmentedColumn
import org.uranxsu.manager.ui.component.settings.SettingsBaseWidget
import org.uranxsu.manager.ui.component.settings.SettingsChooseWidget
import org.uranxsu.manager.ui.component.settings.SettingsJumpPageWidget
import org.uranxsu.manager.ui.component.settings.SettingsSwitchWidget
import org.uranxsu.manager.ui.navigation.LocalNavigator
import org.uranxsu.manager.ui.navigation.Route
import org.uranxsu.manager.ui.screen.themeSettings.crop.BackgroundCropActivity
import org.uranxsu.manager.ui.screen.themeSettings.crop.backgroundCropSize
import org.uranxsu.manager.ui.screen.themeSettings.crop.createBackgroundCropOutputUri
import org.uranxsu.manager.ui.screen.themeSettings.crop.getParcelableExtraCompat
import org.uranxsu.manager.ui.theme.BackgroundManager
import org.uranxsu.manager.ui.theme.CardConfig
import org.uranxsu.manager.ui.theme.HomeCustomizationConfig
import org.uranxsu.manager.ui.theme.ThemeConfig
import org.uranxsu.manager.ui.util.CustomTextConfig
import org.uranxsu.manager.ui.util.ThemeApplyOptions
import org.uranxsu.manager.ui.util.ThemePackageApplier
import org.uranxsu.manager.ui.util.ThemePackageStorage
import org.uranxsu.manager.ui.viewmodel.HomeViewModel
import org.uranxsu.manager.ui.viewmodel.SettingsUiAction
import org.uranxsu.manager.ui.viewmodel.SettingsUiState
import org.uranxsu.manager.ui.viewmodel.SettingsViewModel

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
        }
    }

    val pickCardImage = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
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
                val intent = Intent(context, BackgroundCropActivity::class.java).apply {
                    putExtra(UCrop.EXTRA_INPUT_URI, uri)
                    putExtra(UCrop.EXTRA_OUTPUT_URI, outputUri)
                    putExtra(UCrop.EXTRA_ASPECT_RATIO_X, screenWidth.toFloat())
                    putExtra(UCrop.EXTRA_ASPECT_RATIO_Y, cardAspectY)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }
                cropCardLauncher.launch(intent)
            }
        }
    }

    SegmentedColumn(title = stringResource(R.string.home_customization_section)) {
        item {
            SettingsBaseWidget(
                icon = Icons.TwoTone.Image,
                title = stringResource(R.string.home_card_image),
                description = stringResource(R.string.home_card_image_summary),
                onClick = { pickCardImage.launch("*/*") },
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

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = ThemePackageStorage.import(context, uri)
            val imported = result.getOrNull()
            if (imported == null) {
                Toast.makeText(context, importFail, Toast.LENGTH_LONG).show()
                return@launch
            }
            runCatching {
                ThemePackageApplier.apply(
                    context = context,
                    imported = imported,
                    useDarkVariant = isDark,
                    options = ThemeApplyOptions.ALL,
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
            imported.cleanup()
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
