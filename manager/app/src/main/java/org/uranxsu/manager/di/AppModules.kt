package org.uranxsu.manager.di

import coil.ImageLoader
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import me.zhanghai.android.appiconloader.coil.AppIconFetcher
import me.zhanghai.android.appiconloader.coil.AppIconKeyer
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import org.uranxsu.manager.BuildConfig
import org.uranxsu.manager.data.AppSettingsRepository
import org.uranxsu.manager.data.application.ApplicationControlRepository
import org.uranxsu.manager.data.application.DynamicManagerRepository
import org.uranxsu.manager.data.count.CountRepository
import org.uranxsu.manager.data.download.DownloadRepository
import org.uranxsu.manager.data.file.ModuleFileRepository
import org.uranxsu.manager.data.flash.FlashRepository
import org.uranxsu.manager.data.kernel.KernelRepository
import org.uranxsu.manager.data.kernel.UmountRepository
import org.uranxsu.manager.data.logging.BugreportRepository
import org.uranxsu.manager.data.logging.SulogRepository
import org.uranxsu.manager.data.module.ModuleActionRepository
import org.uranxsu.manager.data.module.ModuleCatalogRepository
import org.uranxsu.manager.data.module.ModuleIconRepository
import org.uranxsu.manager.data.module.ModulePreferencesRepository
import org.uranxsu.manager.data.module.ModuleReleaseRepository
import org.uranxsu.manager.data.module.ModuleRepository
import org.uranxsu.manager.data.module.RepositorySourceStore
import org.uranxsu.manager.data.module.registry.KernelSuRegistryRepository
import org.uranxsu.manager.data.mount.MountRepository
import org.uranxsu.manager.data.network.NetworkRequestRepository
import org.uranxsu.manager.data.network.NetworkStatusRepository
import org.uranxsu.manager.data.network.WebResourceRepository
import org.uranxsu.manager.data.packageinfo.AppIconDataSource
import org.uranxsu.manager.data.packageinfo.InstalledPackageCache
import org.uranxsu.manager.data.packageinfo.InstalledPackageRepository
import org.uranxsu.manager.data.packageinfo.RootServiceRepository
import org.uranxsu.manager.data.packageinfo.SuperUserRepository
import org.uranxsu.manager.data.profile.ProfileRepository
import org.uranxsu.manager.data.profile.ProfileTemplateRepository
import org.uranxsu.manager.data.script.ScriptRepository
import org.uranxsu.manager.data.settings.LocaleHelper
import org.uranxsu.manager.data.settings.LocaleRepository
import org.uranxsu.manager.data.settings.SettingsPlatformRepository
import org.uranxsu.manager.data.shell.KsuCliRepository
import org.uranxsu.manager.data.shell.ShortcutRepository
import org.uranxsu.manager.data.startup.ApplicationInitializationRepository
import org.uranxsu.manager.data.startup.StartupRepository
import org.uranxsu.manager.data.susfs.SuSFSConfigHelper
import org.uranxsu.manager.data.susfs.SuSFSRepository
import org.uranxsu.manager.data.system.HomeRuntimeRepository
import org.uranxsu.manager.data.system.HomeStateRepository
import org.uranxsu.manager.data.text.HanziToPinyin
import org.uranxsu.manager.data.theme.MonetCompatColorSource
import org.uranxsu.manager.data.theme.ThemeRepository
import org.uranxsu.manager.data.update.ManagerUpdateRepository
import org.uranxsu.manager.data.webui.WebUiRepository
import org.uranxsu.manager.data.zygisk.ZygiskRepository
import org.uranxsu.manager.domain.text.TextTransliterator
import org.uranxsu.manager.domain.usecase.AddModuleRepositoryUseCase
import org.uranxsu.manager.domain.usecase.AddUmountPathUseCase
import org.uranxsu.manager.domain.usecase.ApplyLanguageUseCase
import org.uranxsu.manager.domain.usecase.BackupAllowlistUseCase
import org.uranxsu.manager.domain.usecase.CalculateInstalledModuleSizeUseCase
import org.uranxsu.manager.domain.usecase.CheckFlashModuleMountUseCase
import org.uranxsu.manager.domain.usecase.CheckManagerUpdateUseCase
import org.uranxsu.manager.domain.usecase.CleanSulogUseCase
import org.uranxsu.manager.domain.usecase.ClearDynamicManagerUseCase
import org.uranxsu.manager.domain.usecase.ConfigureSuLogUseCase
import org.uranxsu.manager.domain.usecase.ControlAppUseCase
import org.uranxsu.manager.domain.usecase.DeleteProfileTemplateUseCase
import org.uranxsu.manager.domain.usecase.EnableSulogUseCase
import org.uranxsu.manager.domain.usecase.EnqueueDownloadUseCase
import org.uranxsu.manager.domain.usecase.EnqueueManagerUpdateUseCase
import org.uranxsu.manager.domain.usecase.EnsureManagerInstalledUseCase
import org.uranxsu.manager.domain.usecase.ExecuteFlashOperationUseCase
import org.uranxsu.manager.domain.usecase.ExecuteModuleActionUseCase
import org.uranxsu.manager.domain.usecase.ExportProfileTemplatesUseCase
import org.uranxsu.manager.domain.usecase.ExtractModuleIdUseCase
import org.uranxsu.manager.domain.usecase.ExtractModuleNameUseCase
import org.uranxsu.manager.domain.usecase.FetchRemoteTextUseCase
import org.uranxsu.manager.domain.usecase.GenerateBugreportUseCase
import org.uranxsu.manager.domain.usecase.GetAppProfileUseCase
import org.uranxsu.manager.domain.usecase.GetAppSepolicyUseCase
import org.uranxsu.manager.domain.usecase.GetBooleanPreferenceUseCase
import org.uranxsu.manager.domain.usecase.GetCatalogModuleUseCase
import org.uranxsu.manager.domain.usecase.GetDefaultUmountModulesUseCase
import org.uranxsu.manager.domain.usecase.GetHomeBasicInfoUseCase
import org.uranxsu.manager.domain.usecase.GetInstallEnvironmentUseCase
import org.uranxsu.manager.domain.usecase.GetKernelFeatureSettingsUseCase
import org.uranxsu.manager.domain.usecase.GetKernelStatusUseCase
import org.uranxsu.manager.domain.usecase.GetLastScriptRunUseCase
import org.uranxsu.manager.domain.usecase.GetManagerRuntimeInfoUseCase
import org.uranxsu.manager.domain.usecase.GetModuleReadmeUseCase
import org.uranxsu.manager.domain.usecase.GetModuleReleaseDetailsUseCase
import org.uranxsu.manager.domain.usecase.GetModuleUpdateChangelogUseCase
import org.uranxsu.manager.domain.usecase.GetPlatformFeatureStatusUseCase
import org.uranxsu.manager.domain.usecase.GetProfileTemplateUseCase
import org.uranxsu.manager.domain.usecase.GetStringPreferenceUseCase
import org.uranxsu.manager.domain.usecase.GetStringSetPreferenceUseCase
import org.uranxsu.manager.domain.usecase.GetSuSFSStatusUseCase
import org.uranxsu.manager.domain.usecase.GetSuperUserAppGroupUseCase
import org.uranxsu.manager.domain.usecase.ImportAllowlistUseCase
import org.uranxsu.manager.domain.usecase.ImportProfileTemplatesUseCase
import org.uranxsu.manager.domain.usecase.InitializeApplicationUseCase
import org.uranxsu.manager.domain.usecase.IsCatalogModuleCurrentUseCase
import org.uranxsu.manager.domain.usecase.IsLateLoadModeUseCase
import org.uranxsu.manager.domain.usecase.IsModuleUriAccessibleUseCase
import org.uranxsu.manager.domain.usecase.IsNetworkAvailableUseCase
import org.uranxsu.manager.domain.usecase.IsSoftRebootPreferredUseCase
import org.uranxsu.manager.domain.usecase.IsSystemLanguageSettingsUseCase
import org.uranxsu.manager.domain.usecase.LaunchSystemLanguageSettingsUseCase
import org.uranxsu.manager.domain.usecase.LoadModuleIconUseCase
import org.uranxsu.manager.domain.usecase.LoadSettingsPlatformUseCase
import org.uranxsu.manager.domain.usecase.MountUseCase
import org.uranxsu.manager.domain.usecase.ObserveAssetDownloadUseCase
import org.uranxsu.manager.domain.usecase.ObserveCatalogModulesUseCase
import org.uranxsu.manager.domain.usecase.ObserveDownloadUseCase
import org.uranxsu.manager.domain.usecase.ObserveDynamicManagerStateUseCase
import org.uranxsu.manager.domain.usecase.ObserveInstalledModulesUseCase
import org.uranxsu.manager.domain.usecase.ObserveKernelFlashUseCase
import org.uranxsu.manager.domain.usecase.ObserveModuleCatalogRefreshingUseCase
import org.uranxsu.manager.domain.usecase.ObserveProfileTemplateOfflineUseCase
import org.uranxsu.manager.domain.usecase.ObserveProfileTemplateRefreshingUseCase
import org.uranxsu.manager.domain.usecase.ObserveProfileTemplatesUseCase
import org.uranxsu.manager.domain.usecase.ObserveRepositorySourcesUseCase
import org.uranxsu.manager.domain.usecase.ObserveStartupStateUseCase
import org.uranxsu.manager.domain.usecase.ObserveSulogStateUseCase
import org.uranxsu.manager.domain.usecase.ObserveSuperUserStateUseCase
import org.uranxsu.manager.domain.usecase.ObserveUmountStateUseCase
import org.uranxsu.manager.domain.usecase.PeekOneshotScriptUseCase
import org.uranxsu.manager.domain.usecase.PrepareScriptUseCase
import org.uranxsu.manager.domain.usecase.RebootUseCase
import org.uranxsu.manager.domain.usecase.RefreshDynamicManagerUseCase
import org.uranxsu.manager.domain.usecase.RefreshInstalledModulesUseCase
import org.uranxsu.manager.domain.usecase.RefreshModuleCatalogUseCase
import org.uranxsu.manager.domain.usecase.RefreshProfileTemplatesUseCase
import org.uranxsu.manager.domain.usecase.RefreshSulogUseCase
import org.uranxsu.manager.domain.usecase.RefreshSuperUsersUseCase
import org.uranxsu.manager.domain.usecase.RefreshUmountPathsUseCase
import org.uranxsu.manager.domain.usecase.ReleaseScriptUseCase
import org.uranxsu.manager.domain.usecase.RemoveModuleRepositoryUseCase
import org.uranxsu.manager.domain.usecase.RemovePreferenceUseCase
import org.uranxsu.manager.domain.usecase.RemoveUmountPathUseCase
import org.uranxsu.manager.domain.usecase.RenameModuleRepositoryUseCase
import org.uranxsu.manager.domain.usecase.ResolveModuleUpdateUseCase
import org.uranxsu.manager.domain.usecase.RunScriptUseCase
import org.uranxsu.manager.domain.usecase.SaveModuleActionLogUseCase
import org.uranxsu.manager.domain.usecase.SaveProfileTemplateUseCase
import org.uranxsu.manager.domain.usecase.SelectDynamicManagerUseCase
import org.uranxsu.manager.domain.usecase.SetAppProfileUseCase
import org.uranxsu.manager.domain.usecase.SetAppSepolicyUseCase
import org.uranxsu.manager.domain.usecase.SetBooleanPreferenceUseCase
import org.uranxsu.manager.domain.usecase.SetDefaultUmountModulesUseCase
import org.uranxsu.manager.domain.usecase.SetKernelUmountEnabledUseCase
import org.uranxsu.manager.domain.usecase.SetManualDynamicManagerUseCase
import org.uranxsu.manager.domain.usecase.SetModuleEnabledUseCase
import org.uranxsu.manager.domain.usecase.SetModuleRemovedUseCase
import org.uranxsu.manager.domain.usecase.SetSelinuxHideEnabledUseCase
import org.uranxsu.manager.domain.usecase.SetShellSuAdbOnlyEnabledUseCase
import org.uranxsu.manager.domain.usecase.SetStringPreferenceUseCase
import org.uranxsu.manager.domain.usecase.SetStringSetPreferenceUseCase
import org.uranxsu.manager.domain.usecase.SetSuEnabledUseCase
import org.uranxsu.manager.domain.usecase.StartKernelFlashUseCase
import org.uranxsu.manager.domain.usecase.SuSFSConfigUseCase
import org.uranxsu.manager.domain.usecase.TakeModuleUriPermissionUseCase
import org.uranxsu.manager.domain.usecase.TransliterateTextUseCase
import org.uranxsu.manager.domain.usecase.UpdateAppearanceUseCase
import org.uranxsu.manager.domain.usecase.UpdateCachedModuleEnabledUseCase
import org.uranxsu.manager.domain.usecase.UpdatePlatformSettingUseCase
import org.uranxsu.manager.domain.usecase.ValidateSepolicyUseCase
import org.uranxsu.manager.domain.usecase.ZygiskUseCase
import org.uranxsu.manager.ui.activity.util.ThemeUtils
import org.uranxsu.manager.ui.component.ZipFileDetector
import org.uranxsu.manager.ui.theme.BackgroundManager
import org.uranxsu.manager.ui.theme.CardConfig
import org.uranxsu.manager.ui.theme.HomeCustomizationConfig
import org.uranxsu.manager.ui.theme.ThemeConfig
import org.uranxsu.manager.ui.util.CustomTextConfig
import org.uranxsu.manager.ui.util.Shortcut
import org.uranxsu.manager.ui.viewmodel.AppProfileViewModel
import org.uranxsu.manager.ui.viewmodel.BuiltinZygiskViewModel
import org.uranxsu.manager.ui.viewmodel.DynamicManagerViewModel
import org.uranxsu.manager.ui.viewmodel.ExecuteModuleActionViewModel
import org.uranxsu.manager.ui.viewmodel.FlashViewModel
import org.uranxsu.manager.ui.viewmodel.HomeViewModel
import org.uranxsu.manager.ui.viewmodel.InstallViewModel
import org.uranxsu.manager.ui.viewmodel.KernelFlashViewModel
import org.uranxsu.manager.ui.viewmodel.MainIntentViewModel
import org.uranxsu.manager.ui.viewmodel.ModuleDetailViewModel
import org.uranxsu.manager.ui.viewmodel.ModuleRepoViewModel
import org.uranxsu.manager.ui.viewmodel.ModuleViewModel
import org.uranxsu.manager.ui.viewmodel.MountSettingsViewModel
import org.uranxsu.manager.ui.viewmodel.NoMountViewModel
import org.uranxsu.manager.ui.viewmodel.RepositorySourcesViewModel
import org.uranxsu.manager.ui.viewmodel.ScriptRunViewModel
import org.uranxsu.manager.ui.viewmodel.SettingsViewModel
import org.uranxsu.manager.ui.viewmodel.SuSFSViewModel
import org.uranxsu.manager.ui.viewmodel.SulogViewModel
import org.uranxsu.manager.ui.viewmodel.SuperUserViewModel
import org.uranxsu.manager.ui.viewmodel.TemplateEditorViewModel
import org.uranxsu.manager.ui.viewmodel.TemplateViewModel
import org.uranxsu.manager.ui.viewmodel.UmountManagerScreenViewModel
import org.uranxsu.manager.ui.webui.MonetColorsProvider

val applicationScopeQualifier = named("applicationScope")

val coreModule = module {
    single<CoroutineScope>(applicationScopeQualifier) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
    single {
        OkHttpClient.Builder()
            .cache(Cache(File(androidApplication().cacheDir, "okhttp"), 10L * 1024L * 1024L))
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "UranXSU/${BuildConfig.VERSION_CODE}")
                        .header("Accept-Language", Locale.getDefault().toLanguageTag())
                        .build(),
                )
            }
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()
    }
    single {
        val application = androidApplication()
        val iconSize = application.resources.getDimensionPixelSize(android.R.dimen.app_icon_size)
        ImageLoader.Builder(application)
            .components {
                add(AppIconKeyer())
                add(AppIconFetcher.Factory(iconSize, false, application))
            }
            .build()
    }
}

val repositoryModule = module {
    single { KsuCliRepository(androidApplication()) }
    singleOf(::CountRepository)
    singleOf(::InstalledPackageCache)
    singleOf(::AppIconDataSource)
    singleOf(::RootServiceRepository)
    singleOf(::InstalledPackageRepository)
    single {
        SuperUserRepository(
            application = get(),
            cache = get(),
            installedPackageRepository = get(),
            profileRepository = get(),
            applicationScope = get(applicationScopeQualifier),
        )
    }
    single {
        AppSettingsRepository(
            context = androidApplication(),
            applicationScope = get(applicationScopeQualifier),
        )
    }
    singleOf(::StartupRepository)
    single {
        ApplicationInitializationRepository(
            application = get(),
            imageLoader = get(),
            applicationScope = get(applicationScopeQualifier),
            flashRepository = get(),
            ksuCliRepository = get(),
        )
    }
    singleOf(::ManagerUpdateRepository)
    singleOf(::ApplicationControlRepository)
    singleOf(::DownloadRepository)
    single { FlashRepository(get(), get(applicationScopeQualifier), get(), get()) }
    singleOf(::KernelRepository)
    singleOf(::HomeRuntimeRepository)
    singleOf(::HomeStateRepository)
    singleOf(::NetworkStatusRepository)
    singleOf(::NetworkRequestRepository)
    singleOf(::DynamicManagerRepository)
    singleOf(::SulogRepository)
    singleOf(::BugreportRepository)
    singleOf(::UmountRepository)
    singleOf(::ModuleCatalogRepository)
    single { ModuleReleaseRepository(get(), get(applicationScopeQualifier)) }
    singleOf(::ModuleIconRepository)
    single { KernelSuRegistryRepository(get(), get(), get(), get()) }
    singleOf(::RepositorySourceStore)
    singleOf(::ModuleRepository)
    singleOf(::ModulePreferencesRepository)
    singleOf(::ModuleActionRepository)
    single { ScriptRepository(androidApplication(), get(), get(applicationScopeQualifier)) }
    singleOf(::WebResourceRepository)
    singleOf(::WebUiRepository)
    singleOf(::ModuleFileRepository)
    singleOf(::ProfileRepository)
    singleOf(::ProfileTemplateRepository)
    singleOf(::SuSFSConfigHelper)
    singleOf(::SuSFSRepository)
    singleOf(::MountRepository)
    singleOf(::ZygiskRepository)
    singleOf(::MonetCompatColorSource)
    singleOf(::ThemeRepository)
    single {
        val themeRepository = get<ThemeRepository>()
        ThemeConfig(themeRepository::defaultSeedColor)
    }
    singleOf(::CardConfig)
    singleOf(::HomeCustomizationConfig)
    singleOf(::CustomTextConfig)
    singleOf(::BackgroundManager)
    singleOf(::ThemeUtils)
    singleOf(::LocaleHelper)
    singleOf(::LocaleRepository)
    singleOf(::SettingsPlatformRepository)
    singleOf(::ShortcutRepository)
    singleOf(::Shortcut)
    singleOf(::MonetColorsProvider)
    singleOf(::ZipFileDetector)
    single { HanziToPinyin.create() } bind TextTransliterator::class
}

val useCaseModule = module {
    factoryOf(::InitializeApplicationUseCase)
    factoryOf(::GetHomeBasicInfoUseCase)
    factoryOf(::IsNetworkAvailableUseCase)
    factoryOf(::LoadSettingsPlatformUseCase)
    factoryOf(::UpdateAppearanceUseCase)
    factoryOf(::UpdatePlatformSettingUseCase)
    factoryOf(::GetPlatformFeatureStatusUseCase)
    factoryOf(::IsSoftRebootPreferredUseCase)
    factoryOf(::CheckManagerUpdateUseCase)
    factoryOf(::EnsureManagerInstalledUseCase)
    factoryOf(::RebootUseCase)
    factoryOf(::EnqueueDownloadUseCase)
    factoryOf(::EnqueueManagerUpdateUseCase)
    factoryOf(::ObserveDownloadUseCase)
    factoryOf(::ObserveAssetDownloadUseCase)
    factoryOf(::GetKernelStatusUseCase)
    factoryOf(::GetInstallEnvironmentUseCase)
    factoryOf(::ExecuteFlashOperationUseCase)
    factoryOf(::CheckFlashModuleMountUseCase)
    factoryOf(::GetManagerRuntimeInfoUseCase)
    factoryOf(::GetKernelFeatureSettingsUseCase)
    factoryOf(::SetSuEnabledUseCase)
    factoryOf(::SetKernelUmountEnabledUseCase)
    factoryOf(::SetShellSuAdbOnlyEnabledUseCase)
    factoryOf(::ConfigureSuLogUseCase)
    factoryOf(::SetSelinuxHideEnabledUseCase)
    factoryOf(::SetDefaultUmountModulesUseCase)
    factoryOf(::IsLateLoadModeUseCase)
    factoryOf(::GetAppProfileUseCase)
    factoryOf(::SetAppProfileUseCase)
    factoryOf(::GetAppSepolicyUseCase)
    factoryOf(::SetAppSepolicyUseCase)
    factoryOf(::ControlAppUseCase)
    factoryOf(::ValidateSepolicyUseCase)
    factoryOf(::GetDefaultUmountModulesUseCase)
    factoryOf(::GetSuSFSStatusUseCase)
    factoryOf(::SuSFSConfigUseCase)
    factoryOf(::MountUseCase)
    factoryOf(::ZygiskUseCase)
    factoryOf(::ApplyLanguageUseCase)
    factoryOf(::IsSystemLanguageSettingsUseCase)
    factoryOf(::LaunchSystemLanguageSettingsUseCase)
    factoryOf(::GenerateBugreportUseCase)
    factoryOf(::ObserveStartupStateUseCase)
    factoryOf(::GetSuperUserAppGroupUseCase)
    factoryOf(::ObserveCatalogModulesUseCase)
    factoryOf(::ObserveModuleCatalogRefreshingUseCase)
    factoryOf(::RefreshModuleCatalogUseCase)
    factoryOf(::GetCatalogModuleUseCase)
    factoryOf(::IsCatalogModuleCurrentUseCase)
    factoryOf(::GetModuleReadmeUseCase)
    factoryOf(::GetModuleReleaseDetailsUseCase)
    factoryOf(::LoadModuleIconUseCase)
    factoryOf(::ResolveModuleUpdateUseCase)
    factoryOf(::GetModuleUpdateChangelogUseCase)
    factoryOf(::ObserveRepositorySourcesUseCase)
    factoryOf(::AddModuleRepositoryUseCase)
    factoryOf(::RenameModuleRepositoryUseCase)
    factoryOf(::RemoveModuleRepositoryUseCase)
    factoryOf(::ObserveProfileTemplatesUseCase)
    factoryOf(::ObserveProfileTemplateRefreshingUseCase)
    factoryOf(::ObserveProfileTemplateOfflineUseCase)
    factoryOf(::RefreshProfileTemplatesUseCase)
    factoryOf(::GetProfileTemplateUseCase)
    factoryOf(::SaveProfileTemplateUseCase)
    factoryOf(::DeleteProfileTemplateUseCase)
    factoryOf(::ImportProfileTemplatesUseCase)
    factoryOf(::ExportProfileTemplatesUseCase)
    factoryOf(::GetBooleanPreferenceUseCase)
    factoryOf(::SetBooleanPreferenceUseCase)
    factoryOf(::GetStringPreferenceUseCase)
    factoryOf(::SetStringPreferenceUseCase)
    factoryOf(::GetStringSetPreferenceUseCase)
    factoryOf(::SetStringSetPreferenceUseCase)
    factoryOf(::ObserveDynamicManagerStateUseCase)
    factoryOf(::RefreshDynamicManagerUseCase)
    factoryOf(::SelectDynamicManagerUseCase)
    factoryOf(::SetManualDynamicManagerUseCase)
    factoryOf(::ClearDynamicManagerUseCase)
    factoryOf(::ObserveSulogStateUseCase)
    factoryOf(::RefreshSulogUseCase)
    factoryOf(::EnableSulogUseCase)
    factoryOf(::CleanSulogUseCase)
    factoryOf(::ObserveUmountStateUseCase)
    factoryOf(::RefreshUmountPathsUseCase)
    factoryOf(::AddUmountPathUseCase)
    factoryOf(::RemoveUmountPathUseCase)
    factoryOf(::ObserveKernelFlashUseCase)
    factoryOf(::StartKernelFlashUseCase)
    factoryOf(::RemovePreferenceUseCase)
    factoryOf(::ObserveSuperUserStateUseCase)
    factoryOf(::RefreshSuperUsersUseCase)
    factoryOf(::BackupAllowlistUseCase)
    factoryOf(::ImportAllowlistUseCase)
    factoryOf(::FetchRemoteTextUseCase)
    factoryOf(::IsModuleUriAccessibleUseCase)
    factoryOf(::TakeModuleUriPermissionUseCase)
    factoryOf(::ExtractModuleNameUseCase)
    factoryOf(::ExtractModuleIdUseCase)
    factoryOf(::ObserveInstalledModulesUseCase)
    factoryOf(::RefreshInstalledModulesUseCase)
    factoryOf(::CalculateInstalledModuleSizeUseCase)
    factoryOf(::UpdateCachedModuleEnabledUseCase)
    factoryOf(::ExecuteModuleActionUseCase)
    factoryOf(::PeekOneshotScriptUseCase)
    factoryOf(::PrepareScriptUseCase)
    factoryOf(::ReleaseScriptUseCase)
    factoryOf(::RunScriptUseCase)
    factoryOf(::GetLastScriptRunUseCase)
    factoryOf(::SaveModuleActionLogUseCase)
    factoryOf(::SetModuleEnabledUseCase)
    factoryOf(::SetModuleRemovedUseCase)
    factoryOf(::TransliterateTextUseCase)
}

val viewModelModule = module {
    viewModel { parameters -> ScriptRunViewModel(parameters[0], get(), get(), get(), get(), get()) }
    viewModel { parameters ->
        AppProfileViewModel(
            uid = parameters[0],
            packageName = parameters[1],
            getAppGroup = get(),
            getProfile = get(),
            getDefaultUmountModules = get(),
            setProfile = get(),
            getSepolicy = get(),
            setSepolicy = get(),
            controlApp = get(),
            validateSepolicy = get(),
        )
    }
    viewModelOf(::HomeViewModel)
    viewModelOf(::InstallViewModel)
    viewModelOf(::MainIntentViewModel)
    viewModelOf(::KernelFlashViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::ModuleViewModel)
    viewModelOf(::SuperUserViewModel)
    viewModelOf(::SuSFSViewModel)
    viewModelOf(::MountSettingsViewModel)
    viewModelOf(::NoMountViewModel)
    viewModelOf(::BuiltinZygiskViewModel)
    viewModel { parameters -> ModuleRepoViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), parameters.getOrNull<String>()) }
    viewModel { parameters -> ModuleDetailViewModel(parameters[0], parameters[1], get(), get(), get(), get(), get(), get(), get()) }
    viewModelOf(::RepositorySourcesViewModel)
    viewModelOf(::TemplateViewModel)
    viewModel { parameters ->
        TemplateEditorViewModel(
            templateId = parameters[0],
            readOnly = parameters[1],
            isCreation = parameters[2],
            getTemplate = get(),
            saveTemplate = get(),
            deleteTemplate = get(),
        )
    }
    viewModelOf(::SulogViewModel)
    viewModelOf(::DynamicManagerViewModel)
    viewModelOf(::FlashViewModel)
    viewModelOf(::UmountManagerScreenViewModel)
    viewModel { parameters ->
        ExecuteModuleActionViewModel(
            moduleId = parameters[0],
            executeModuleAction = get(),
            saveModuleActionLog = get(),
        )
    }
}

val appModules = listOf(coreModule, repositoryModule, useCaseModule, viewModelModule)
