package org.uranxsu.manager.ui.util

import android.content.Context
import android.net.Uri
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import java.io.File
import org.uranxsu.manager.data.AppSettingsRepository
import org.uranxsu.manager.data.module.ModulePreferencesRepository
import org.uranxsu.manager.data.settings.SettingsPlatformRepository
import org.uranxsu.manager.domain.model.AppearanceSetting
import org.uranxsu.manager.domain.model.PlatformSetting
import org.uranxsu.manager.ui.theme.BackgroundManager
import org.uranxsu.manager.ui.theme.BottomBarStyle
import org.uranxsu.manager.ui.theme.CardConfig
import org.uranxsu.manager.ui.theme.HomeCustomizationConfig
import org.uranxsu.manager.ui.theme.ThemeConfig
import org.uranxsu.manager.ui.viewmodel.HomeUiAction
import org.uranxsu.manager.ui.viewmodel.HomeUiState
import org.uranxsu.manager.ui.viewmodel.HomeViewModel
import org.uranxsu.manager.ui.viewmodel.ModuleUiAction
import org.uranxsu.manager.ui.viewmodel.ModuleUiState
import org.uranxsu.manager.ui.viewmodel.ModuleViewModel
import org.uranxsu.manager.ui.viewmodel.PredictiveBackAnimation
import org.uranxsu.manager.ui.viewmodel.PredictiveBackExitDirection
import org.uranxsu.manager.ui.viewmodel.SettingsUiAction
import org.uranxsu.manager.ui.viewmodel.SettingsUiState
import org.uranxsu.manager.ui.viewmodel.SettingsViewModel

data class ThemeApplyOptions(
    val applyColors: Boolean = true,
    val applyBackground: Boolean = true,
    val applyCardBackground: Boolean = true,
    val applyEffects: Boolean = true,
    val applyLayout: Boolean = true,
    val applyCustomTexts: Boolean = true,
)

object ThemePackageApplier {
    fun buildExportPackage(
        context: Context,
        themeConfig: ThemeConfig,
        cardConfig: CardConfig,
        backgroundManager: BackgroundManager,
        homeCustomization: HomeCustomizationConfig,
        customTextConfig: CustomTextConfig,
        settingsState: SettingsUiState,
        homeState: HomeUiState,
        moduleState: ModuleUiState,
    ): ExportedThemePayload {
        val bgFile = themeConfig.customBackgroundUri?.path?.let(::File)?.takeIf { it.isFile }
        val bgSourceFile = backgroundManager.getBackgroundSourceFile(context)
            ?.takeIf { it.isFile && it.absolutePath != bgFile?.absolutePath }
        val cardFile = homeCustomization.statusCardImagePath?.let(::File)?.takeIf { it.isFile }
        val cardSourceFile = homeCustomization.getStatusCardSourceFile(context)
            ?.takeIf { it.isFile && it.absolutePath != cardFile?.absolutePath }

        val data = UranXsuThemeData(
            version = ThemePackageStorage.FORMAT_VERSION,
            app = "UranXSU",
            themeMode = settingsState.themeMode,
            useDynamicColor = settingsState.useDynamicColor,
            seedColor = themeConfig.seedColor,
            dynamicColorSpec = settingsState.dynamicColorSpec.name,
            dynamicPaletteStyle = settingsState.dynamicPaletteStyle.name,
            isUseBackgroundSeedColor = themeConfig.isUseBackgroundSeedColor,
            isHighContrastMode = themeConfig.isHighContrastMode,
            hasBackground = bgFile != null,
            backgroundDim = settingsState.backgroundDim,
            cardAlpha = settingsState.cardAlpha,
            isEnableBlur = themeConfig.isEnableBlur,
            isEnableBlurExp = themeConfig.isEnableBlurExp,
            isFlashCardBackgroundEnabled = settingsState.isFlashCardBackgroundEnabled,
            hasCardBackground = cardFile != null,
            statusCardScrim = homeCustomization.statusCardScrim,
            statusCardHeight = homeCustomization.statusCardHeight,
            hideStatusCardText = homeCustomization.hideStatusCardText,
            hideStatusCardDecoration = homeCustomization.hideStatusCardDecoration,
            showDonateCard = homeCustomization.showDonateCard,
            showLearnMoreCard = homeCustomization.showLearnMoreCard,
            enableModuleBanner = homeCustomization.enableModuleBanner,
            bottomBarStyle = themeConfig.bottomBarStyle.name,
            showNavigationBarBadge = homeState.showNavigationBarBadge,
            showHomeCardIcons = homeState.showHomeCardIcons,
            isSimpleMode = homeState.isSimpleMode,
            showMoreModuleInfo = moduleState.showMoreModuleInfo,
            useBuiltinMonoFont = settingsState.useBuiltinMonoFont,
            predictiveBackAnimation = settingsState.predictiveBackAnimation.value,
            predictiveBackExitDirection = settingsState.predictiveBackExitDirection.value,
            enableSwipeDismiss = settingsState.enableSwipeDismiss,
            pagerInterceptionMode = settingsState.pagerInterceptionMode,
            useAltIcon = settingsState.useAltIcon,
            customTexts = customTextConfig.customTexts,
        )

        return ExportedThemePayload(
            data = data,
            backgroundFile = bgFile,
            backgroundSourceFile = bgSourceFile,
            cardBackgroundFile = cardFile,
            cardBackgroundSourceFile = cardSourceFile,
        )
    }

    fun buildExportPackage(
        context: Context,
        settings: AppSettingsRepository,
        settingsPlatformRepository: SettingsPlatformRepository,
        themeConfig: ThemeConfig,
        cardConfig: CardConfig,
        backgroundManager: BackgroundManager,
        homeCustomization: HomeCustomizationConfig,
        customTextConfig: CustomTextConfig,
    ): ExportedThemePayload {
        val snapshot = settingsPlatformRepository.load()
        val bgFile = themeConfig.customBackgroundUri?.path?.let(::File)?.takeIf { it.isFile }
        val bgSourceFile = backgroundManager.getBackgroundSourceFile(context)
            ?.takeIf { it.isFile && it.absolutePath != bgFile?.absolutePath }
        val cardFile = homeCustomization.statusCardImagePath?.let(::File)?.takeIf { it.isFile }
        val cardSourceFile = homeCustomization.getStatusCardSourceFile(context)
            ?.takeIf { it.isFile && it.absolutePath != cardFile?.absolutePath }

        val data = UranXsuThemeData(
            version = ThemePackageStorage.FORMAT_VERSION,
            app = "UranXSU",
            themeMode = snapshot.themeMode,
            useDynamicColor = snapshot.useDynamicColor,
            seedColor = themeConfig.seedColor,
            dynamicColorSpec = snapshot.dynamicColorSpec.name,
            dynamicPaletteStyle = snapshot.dynamicPaletteStyle.name,
            isUseBackgroundSeedColor = themeConfig.isUseBackgroundSeedColor,
            isHighContrastMode = themeConfig.isHighContrastMode,
            hasBackground = bgFile != null || cardConfig.isCustomBackgroundEnabled,
            backgroundDim = snapshot.backgroundDim,
            cardAlpha = snapshot.cardAlpha,
            isEnableBlur = themeConfig.isEnableBlur,
            isEnableBlurExp = themeConfig.isEnableBlurExp,
            isFlashCardBackgroundEnabled = snapshot.flashCardBackgroundEnabled,
            hasCardBackground = cardFile != null,
            statusCardScrim = homeCustomization.statusCardScrim,
            statusCardHeight = homeCustomization.statusCardHeight,
            hideStatusCardText = homeCustomization.hideStatusCardText,
            hideStatusCardDecoration = homeCustomization.hideStatusCardDecoration,
            showDonateCard = homeCustomization.showDonateCard,
            showLearnMoreCard = homeCustomization.showLearnMoreCard,
            enableModuleBanner = homeCustomization.enableModuleBanner,
            bottomBarStyle = themeConfig.bottomBarStyle.name,
            showNavigationBarBadge = settings.getBoolean(HomeViewModel.PREF_SHOW_NAVIGATION_BAR_BADGE, true),
            showHomeCardIcons = settings.getBoolean(HomeViewModel.PREF_SHOW_HOME_CARD_ICONS, true),
            isSimpleMode = settings.getBoolean(HomeViewModel.PREF_SIMPLE_MODE, false),
            showMoreModuleInfo = settings.getBoolean(ModulePreferencesRepository.PREF_SHOW_MORE_INFO, false),
            useBuiltinMonoFont = snapshot.useBuiltinMonoFont,
            predictiveBackAnimation = snapshot.predictiveBackAnimation,
            predictiveBackExitDirection = snapshot.predictiveBackExitDirection,
            enableSwipeDismiss = snapshot.enableSwipeDismiss,
            pagerInterceptionMode = snapshot.pagerInterceptionMode,
            useAltIcon = snapshot.useAltIcon,
            customTexts = customTextConfig.customTexts,
        )

        return ExportedThemePayload(
            data = data,
            backgroundFile = bgFile,
            backgroundSourceFile = bgSourceFile,
            cardBackgroundFile = cardFile,
            cardBackgroundSourceFile = cardSourceFile,
        )
    }

    suspend fun apply(
        context: Context,
        imported: ImportedTheme,
        options: ThemeApplyOptions,
        settingsViewModel: SettingsViewModel,
        homeViewModel: HomeViewModel,
        moduleViewModel: ModuleViewModel,
        backgroundManager: BackgroundManager,
        homeCustomization: HomeCustomizationConfig,
        customTextConfig: CustomTextConfig,
    ) {
        val data = imported.data

        if (options.applyColors) {
            data.themeMode?.let { mode ->
                settingsViewModel.dispatch(SettingsUiAction.SetThemeMode(mode.coerceIn(0, 2)))
            }
            data.useDynamicColor?.let { dynamic ->
                settingsViewModel.dispatch(SettingsUiAction.SetDynamicColor(dynamic))
            }
            data.seedColor?.let { seed ->
                settingsViewModel.dispatch(SettingsUiAction.SetThemeColor(seed))
            }
            parseColorSpec(data.dynamicColorSpec)?.let { spec ->
                settingsViewModel.dispatch(SettingsUiAction.SetDynamicColorSpec(spec))
            }
            parsePaletteStyle(data.dynamicPaletteStyle)?.let { style ->
                settingsViewModel.dispatch(SettingsUiAction.SetDynamicPaletteStyle(style))
            }
            data.isUseBackgroundSeedColor?.let { backgroundManager.saveUseBackgroundSeedColor(it) }
            data.isHighContrastMode?.let { backgroundManager.saveEnableHighContrastMode(it) }
        }

        if (options.applyBackground) {
            val bgFile = imported.backgroundFile
            if (bgFile != null && bgFile.isFile) {
                val srcFile = imported.backgroundSourceFile?.takeIf { it.isFile } ?: bgFile
                backgroundManager.saveBackgroundSourceImage(context, Uri.fromFile(srcFile))
                backgroundManager.saveAndApplyCustomBackground(context, Uri.fromFile(bgFile))
                settingsViewModel.dispatch(SettingsUiAction.SaveCardConfig)
            } else if (data.hasBackground == false) {
                settingsViewModel.dispatch(SettingsUiAction.RemoveCustomBackground)
            }
        }

        if (options.applyCardBackground) {
            val cardFile = imported.cardBackgroundFile
            if (cardFile != null && cardFile.isFile) {
                val srcFile = imported.cardBackgroundSourceFile?.takeIf { it.isFile } ?: cardFile
                homeCustomization.saveStatusCardSourceImage(context, Uri.fromFile(srcFile))
                homeCustomization.saveStatusCardImageFile(context, cardFile)
            } else if (data.hasCardBackground == false) {
                homeCustomization.clearStatusCardImage(context)
            }
        }

        if (options.applyEffects) {
            data.cardAlpha?.let { settingsViewModel.dispatch(SettingsUiAction.SetCardAlpha(it.coerceIn(0f, 1f))) }
            data.backgroundDim?.let { settingsViewModel.dispatch(SettingsUiAction.SetBackgroundDim(it.coerceIn(0f, 1f))) }
            settingsViewModel.dispatch(SettingsUiAction.SaveCardConfig)
            data.isFlashCardBackgroundEnabled?.let {
                settingsViewModel.dispatch(SettingsUiAction.SetFlashCardBackground(it))
            }
            data.isEnableBlur?.let { backgroundManager.saveEnableBlur(it) }
            data.isEnableBlurExp?.let { backgroundManager.saveEnableBlurExp(it) }
            data.statusCardScrim?.let { homeCustomization.updateStatusCardScrim(it) }
        }

        if (options.applyLayout) {
            parseBottomBarStyle(data.bottomBarStyle)?.let { backgroundManager.saveBottomBarStyle(it) }
            data.showNavigationBarBadge?.let { homeViewModel.dispatch(HomeUiAction.SetNavigationBarBadge(it)) }
            data.showHomeCardIcons?.let { homeViewModel.dispatch(HomeUiAction.SetHomeCardIcons(it)) }
            data.isSimpleMode?.let { homeViewModel.dispatch(HomeUiAction.SetSimpleMode(it)) }
            data.showMoreModuleInfo?.let { moduleViewModel.dispatch(ModuleUiAction.SetShowMoreInfo(it)) }
            data.useBuiltinMonoFont?.let { settingsViewModel.handleBuiltinMonospaceFontChange(it) }
            parsePredictiveBackAnimation(data.predictiveBackAnimation)?.let {
                settingsViewModel.dispatch(SettingsUiAction.SetPredictiveBackAnimation(it))
            }
            parsePredictiveBackDirection(data.predictiveBackExitDirection)?.let {
                settingsViewModel.dispatch(SettingsUiAction.SetPredictiveBackExitDirection(it))
            }
            data.enableSwipeDismiss?.let { settingsViewModel.dispatch(SettingsUiAction.SetSwipeDismiss(it)) }
            data.pagerInterceptionMode?.let { settingsViewModel.dispatch(SettingsUiAction.SetPagerInterceptionMode(it)) }
            data.useAltIcon?.let { settingsViewModel.dispatch(SettingsUiAction.SetAlternateIcon(it)) }
            data.statusCardHeight?.let { homeCustomization.updateStatusCardHeight(it) }
            data.hideStatusCardText?.let { homeCustomization.updateHideStatusCardText(it) }
            data.hideStatusCardDecoration?.let { homeCustomization.updateHideStatusCardDecoration(it) }
            data.showDonateCard?.let { homeCustomization.updateShowDonateCard(it) }
            data.showLearnMoreCard?.let { homeCustomization.updateShowLearnMoreCard(it) }
            data.enableModuleBanner?.let { homeCustomization.updateEnableModuleBanner(it) }
        }

        if (options.applyCustomTexts && data.customTexts != null) {
            customTextConfig.setAll(data.customTexts, merge = false)
        }
    }

    suspend fun applyWithoutViewModels(
        context: Context,
        imported: ImportedTheme,
        options: ThemeApplyOptions = ThemeApplyOptions(),
        settings: AppSettingsRepository,
        settingsPlatformRepository: SettingsPlatformRepository,
        backgroundManager: BackgroundManager,
        homeCustomization: HomeCustomizationConfig,
        customTextConfig: CustomTextConfig,
    ) {
        val data = imported.data

        if (options.applyColors) {
            data.themeMode?.let { mode ->
                settingsPlatformRepository.updateAppearance(AppearanceSetting.ThemeMode(mode.coerceIn(0, 2)))
            }
            data.useDynamicColor?.let { dynamic ->
                settingsPlatformRepository.updateAppearance(AppearanceSetting.DynamicColor(dynamic))
            }
            data.seedColor?.let { seed ->
                settingsPlatformRepository.updateAppearance(AppearanceSetting.SeedColor(seed))
            }
            parseColorSpec(data.dynamicColorSpec)?.let { spec ->
                settingsPlatformRepository.updateAppearance(AppearanceSetting.DynamicColorSpec(spec))
            }
            parsePaletteStyle(data.dynamicPaletteStyle)?.let { style ->
                settingsPlatformRepository.updateAppearance(AppearanceSetting.DynamicPaletteStyle(style))
            }
            data.isUseBackgroundSeedColor?.let { backgroundManager.saveUseBackgroundSeedColor(it) }
            data.isHighContrastMode?.let { backgroundManager.saveEnableHighContrastMode(it) }
        }

        if (options.applyBackground) {
            val bgFile = imported.backgroundFile
            if (bgFile != null && bgFile.isFile) {
                val srcFile = imported.backgroundSourceFile?.takeIf { it.isFile } ?: bgFile
                backgroundManager.saveBackgroundSourceImage(context, Uri.fromFile(srcFile))
                settingsPlatformRepository.updateAppearance(
                    AppearanceSetting.CustomBackground(Uri.fromFile(bgFile).toString()),
                )
            } else if (data.hasBackground == false) {
                settingsPlatformRepository.updateAppearance(AppearanceSetting.RemoveCustomBackground)
            }
        }

        if (options.applyCardBackground) {
            val cardFile = imported.cardBackgroundFile
            if (cardFile != null && cardFile.isFile) {
                val srcFile = imported.cardBackgroundSourceFile?.takeIf { it.isFile } ?: cardFile
                homeCustomization.saveStatusCardSourceImage(context, Uri.fromFile(srcFile))
                homeCustomization.saveStatusCardImageFile(context, cardFile)
            } else if (data.hasCardBackground == false) {
                homeCustomization.clearStatusCardImage(context)
            }
        }

        if (options.applyEffects) {
            data.cardAlpha?.let {
                settingsPlatformRepository.updateAppearance(AppearanceSetting.CardAlpha(it.coerceIn(0f, 1f)))
            }
            data.backgroundDim?.let {
                settingsPlatformRepository.updateAppearance(AppearanceSetting.BackgroundDim(it.coerceIn(0f, 1f)))
            }
            settingsPlatformRepository.updateAppearance(AppearanceSetting.SaveCardConfig)
            data.isFlashCardBackgroundEnabled?.let {
                settingsPlatformRepository.updateAppearance(AppearanceSetting.FlashCardBackground(it))
            }
            data.isEnableBlur?.let { backgroundManager.saveEnableBlur(it) }
            data.isEnableBlurExp?.let { backgroundManager.saveEnableBlurExp(it) }
            data.statusCardScrim?.let { homeCustomization.updateStatusCardScrim(it) }
        }

        if (options.applyLayout) {
            parseBottomBarStyle(data.bottomBarStyle)?.let { backgroundManager.saveBottomBarStyle(it) }
            data.showNavigationBarBadge?.let {
                settings.putBoolean(HomeViewModel.PREF_SHOW_NAVIGATION_BAR_BADGE, it)
            }
            data.showHomeCardIcons?.let {
                settings.putBoolean(HomeViewModel.PREF_SHOW_HOME_CARD_ICONS, it)
            }
            data.isSimpleMode?.let {
                settings.putBoolean(HomeViewModel.PREF_SIMPLE_MODE, it)
            }
            data.showMoreModuleInfo?.let {
                settings.putBoolean(ModulePreferencesRepository.PREF_SHOW_MORE_INFO, it)
            }
            data.useBuiltinMonoFont?.let {
                settingsPlatformRepository.updatePlatform(PlatformSetting.BuiltinMonospaceFont(it))
            }
            parsePredictiveBackAnimation(data.predictiveBackAnimation)?.let {
                settingsPlatformRepository.updatePlatform(PlatformSetting.PredictiveBackAnimation(it.value))
            }
            parsePredictiveBackDirection(data.predictiveBackExitDirection)?.let {
                settingsPlatformRepository.updatePlatform(PlatformSetting.PredictiveBackExitDirection(it.value))
            }
            data.enableSwipeDismiss?.let {
                settingsPlatformRepository.updatePlatform(PlatformSetting.SwipeDismiss(it))
            }
            data.pagerInterceptionMode?.let {
                settingsPlatformRepository.updatePlatform(PlatformSetting.PagerInterceptionMode(it))
            }
            data.useAltIcon?.let {
                settingsPlatformRepository.updatePlatform(PlatformSetting.AlternateIcon(it))
            }
            data.statusCardHeight?.let { homeCustomization.updateStatusCardHeight(it) }
            data.hideStatusCardText?.let { homeCustomization.updateHideStatusCardText(it) }
            data.hideStatusCardDecoration?.let { homeCustomization.updateHideStatusCardDecoration(it) }
            data.showDonateCard?.let { homeCustomization.updateShowDonateCard(it) }
            data.showLearnMoreCard?.let { homeCustomization.updateShowLearnMoreCard(it) }
            data.enableModuleBanner?.let { homeCustomization.updateEnableModuleBanner(it) }
        }

        if (options.applyCustomTexts && data.customTexts != null) {
            customTextConfig.setAll(data.customTexts, merge = false)
        }
    }

    private fun parseColorSpec(raw: String?): ColorSpec.SpecVersion? = raw?.let { name -> ColorSpec.SpecVersion.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } }

    private fun parsePaletteStyle(raw: String?): PaletteStyle? = raw?.let { name -> PaletteStyle.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } }

    private fun parseBottomBarStyle(raw: String?): BottomBarStyle? = raw?.let { name -> BottomBarStyle.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } }

    private fun parsePredictiveBackAnimation(raw: String?): PredictiveBackAnimation? = raw?.let { value ->
        PredictiveBackAnimation.entries.firstOrNull {
            it.value.equals(value, ignoreCase = true) || it.name.equals(value, ignoreCase = true)
        }
    }

    private fun parsePredictiveBackDirection(raw: String?): PredictiveBackExitDirection? = raw?.let { value ->
        PredictiveBackExitDirection.entries.firstOrNull {
            it.value.equals(value, ignoreCase = true) || it.name.equals(value, ignoreCase = true)
        }
    }
}
