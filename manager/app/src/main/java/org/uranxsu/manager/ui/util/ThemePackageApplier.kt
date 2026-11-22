package org.uranxsu.manager.ui.util

import android.content.Context
import android.net.Uri
import java.io.File
import org.uranxsu.manager.ui.theme.BackgroundManager
import org.uranxsu.manager.ui.theme.BottomBarStyle
import org.uranxsu.manager.ui.theme.CardConfig
import org.uranxsu.manager.ui.theme.HomeCustomizationConfig
import org.uranxsu.manager.ui.theme.ThemeConfig
import org.uranxsu.manager.ui.viewmodel.HomeUiAction
import org.uranxsu.manager.ui.viewmodel.HomeViewModel
import org.uranxsu.manager.ui.viewmodel.SettingsUiAction
import org.uranxsu.manager.ui.viewmodel.SettingsViewModel

data class ThemeApplyOptions(
    val applyColors: Boolean = true,
    val applyBackground: Boolean = true,
    val applyCardBackground: Boolean = true,
    val applyEffects: Boolean = true,
    val applyCustomTexts: Boolean = true,
    val applyHomeLayout: Boolean = true,
) {
    companion object {
        val ALL = ThemeApplyOptions()
    }
}

object ThemePackageApplier {
    suspend fun apply(
        context: Context,
        imported: ImportedTheme,
        useDarkVariant: Boolean,
        options: ThemeApplyOptions = ThemeApplyOptions(),
        settingsViewModel: SettingsViewModel,
        homeViewModel: HomeViewModel,
        backgroundManager: BackgroundManager,
        homeCustomization: HomeCustomizationConfig,
        customTextConfig: CustomTextConfig,
    ) {
        val data = imported.data
        val selectedVariant = if (imported.isAdaptive) {
            if (useDarkVariant) imported.dark else imported.light
        } else {
            null
        }
        val variantData = selectedVariant?.data

        if (options.applyColors) {
            val themeMode = ThemePackageStorage.mapSukiSuThemeMode(variantData?.themeMode ?: data.themeMode)
            if (themeMode != null) {
                settingsViewModel.dispatch(SettingsUiAction.SetThemeMode(themeMode))
            }

            val keyColor = variantData?.keyColor ?: data.keyColor
            if (keyColor != null) {
                if (keyColor == 0) {
                    settingsViewModel.dispatch(SettingsUiAction.SetDynamicColor(true))
                } else {
                    settingsViewModel.dispatch(SettingsUiAction.SetDynamicColor(false))
                    settingsViewModel.dispatch(SettingsUiAction.SetThemeColor(keyColor))
                }
            }

            ThemePackageStorage.mapColorSpec(variantData?.colorSpec ?: data.colorSpec)?.let { spec ->
                settingsViewModel.dispatch(SettingsUiAction.SetDynamicColorSpec(spec))
            }
            ThemePackageStorage.mapColorStyle(variantData?.colorStyle ?: data.colorStyle)?.let { style ->
                settingsViewModel.dispatch(SettingsUiAction.SetDynamicPaletteStyle(style))
            }
        }

        if (options.applyBackground) {
            val bgPath = selectedVariant?.backgroundPath ?: imported.backgroundPath
            val hasBg = variantData?.hasBackground ?: data.hasBackground
            if (bgPath != null && File(bgPath).isFile) {
                backgroundManager.saveAndApplyCustomBackground(context, Uri.fromFile(File(bgPath)))
                settingsViewModel.dispatch(SettingsUiAction.Initialize)
            } else if (hasBg == false) {
                settingsViewModel.dispatch(SettingsUiAction.RemoveCustomBackground)
            }
        }

        if (options.applyCardBackground) {
            val cardBgPath = selectedVariant?.cardBackgroundPath ?: imported.cardBackgroundPath
            val hasCardBg = variantData?.hasCardBackground ?: data.hasCardBackground
            if (cardBgPath != null && File(cardBgPath).isFile) {
                homeCustomization.saveStatusCardImageFile(context, File(cardBgPath))
            } else if (hasCardBg == false) {
                homeCustomization.clearStatusCardImage(context)
            }
        }

        if (options.applyEffects) {
            (variantData?.cardBackgroundAlphaPercent ?: data.cardBackgroundAlphaPercent)?.let { pct ->
                settingsViewModel.dispatch(SettingsUiAction.SetCardAlpha((pct.coerceIn(0, 100) / 100f)))
            }
            (variantData?.customBackgroundScrimPercent ?: data.customBackgroundScrimPercent)?.let { pct ->
                settingsViewModel.dispatch(SettingsUiAction.SetBackgroundDim((pct.coerceIn(0, 100) / 100f)))
            }
            (variantData?.enableBlur ?: data.enableBlur)?.let { blur ->
                backgroundManager.saveEnableBlur(blur)
            }
        }

        if (options.applyHomeLayout) {
            (variantData?.enableFloatingBottomBar ?: data.enableFloatingBottomBar)?.let { floating ->
                backgroundManager.saveBottomBarStyle(
                    if (floating) BottomBarStyle.FLOATING else BottomBarStyle.MATERIAL3_EXPRESSIVE,
                )
            }
            (variantData?.enableNavigationBadge ?: data.enableNavigationBadge)?.let { badge ->
                homeViewModel.dispatch(HomeUiAction.SetNavigationBarBadge(badge))
            }
            (variantData?.enableSwipeDismiss ?: data.enableSwipeDismiss)?.let { swipe ->
                settingsViewModel.dispatch(SettingsUiAction.SetSwipeDismiss(swipe))
            }
            (variantData?.pagerInterceptionMode ?: data.pagerInterceptionMode)?.let { mode ->
                settingsViewModel.dispatch(SettingsUiAction.SetPagerInterceptionMode(mode.coerceIn(0, 2)))
            }
            (variantData?.hideHomeCardStatusText ?: data.hideHomeCardStatusText)?.let { hide ->
                homeCustomization.updateHideStatusCardText(hide)
            }
            (variantData?.hideHomeCardDecoration ?: data.hideHomeCardDecoration)?.let { hide ->
                homeCustomization.updateHideStatusCardDecoration(hide)
            }
        }

        if (options.applyCustomTexts) {
            val mergedTexts = buildMap {
                data.customTexts?.forEach { (k, v) ->
                    if (v.isNotBlank()) put(k, v)
                }
                data.customHomeTitle?.takeIf { it.isNotBlank() }?.let { title ->
                    put(CustomTextKey.HOME_TITLE.key, title)
                }
            }
            if (mergedTexts.isNotEmpty()) {
                customTextConfig.setAll(mergedTexts, merge = true)
            }
        }
    }

    fun buildExportPackage(
        context: Context,
        themeConfig: ThemeConfig,
        cardConfig: CardConfig,
        homeCustomization: HomeCustomizationConfig,
        customTextConfig: CustomTextConfig,
        themeMode: Int,
        showNavigationBarBadge: Boolean,
        enableSwipeDismiss: Boolean,
        pagerInterceptionMode: Int,
    ): Triple<ThemePackageData, File?, File?> {
        val bgFile = File(context.applicationContext.filesDir, "custom_background.jpg")
            .takeIf { cardConfig.isCustomBackgroundEnabled && it.isFile }
        val cardBgFile = homeCustomization.statusCardImagePath
            ?.let(::File)
            ?.takeIf { it.isFile }
        val texts = customTextConfig.customTexts
        val data = ThemePackageData(
            version = ThemePackageStorage.BASE_VERSION,
            themeMode = themeMode,
            keyColor = if (themeConfig.useDynamicColor) 0 else themeConfig.seedColor,
            colorStyle = themeConfig.dynamicPaletteStyle.name,
            colorSpec = themeConfig.dynamicColorSpec.name,
            cardBackgroundAlphaPercent = (cardConfig.cardAlpha * 100f).toInt().coerceIn(0, 100),
            customBackgroundScrimPercent = (themeConfig.backgroundDim * 100f).toInt().coerceIn(0, 100),
            hasBackground = bgFile != null,
            hasCardBackground = cardBgFile != null,
            hideHomeCardStatusText = homeCustomization.hideStatusCardText,
            hideHomeCardDecoration = homeCustomization.hideStatusCardDecoration,
            enableFloatingBottomBar = themeConfig.bottomBarStyle == BottomBarStyle.FLOATING,
            enableNavigationBadge = showNavigationBarBadge,
            enableSwipeDismiss = enableSwipeDismiss,
            enableBlur = themeConfig.isEnableBlur,
            pagerInterceptionMode = pagerInterceptionMode,
            customHomeTitle = texts[CustomTextKey.HOME_TITLE.key],
            customTexts = texts.takeIf { it.isNotEmpty() },
        )
        return Triple(data, bgFile, cardBgFile)
    }
}
