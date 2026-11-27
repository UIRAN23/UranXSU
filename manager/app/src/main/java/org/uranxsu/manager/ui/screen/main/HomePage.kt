package org.uranxsu.manager.ui.screen.main

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.system.Os
import android.widget.Toast
import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.MenuBook
import androidx.compose.material.icons.twotone.Android
import androidx.compose.material.icons.twotone.Block
import androidx.compose.material.icons.twotone.DeveloperBoard
import androidx.compose.material.icons.twotone.Error
import androidx.compose.material.icons.twotone.Extension
import androidx.compose.material.icons.twotone.FilterList
import androidx.compose.material.icons.twotone.Group
import androidx.compose.material.icons.twotone.HealthAndSafety
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.Layers
import androidx.compose.material.icons.twotone.Memory
import androidx.compose.material.icons.twotone.PowerSettingsNew
import androidx.compose.material.icons.twotone.RestartAlt
import androidx.compose.material.icons.twotone.Science
import androidx.compose.material.icons.twotone.Security
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material.icons.twotone.Smartphone
import androidx.compose.material.icons.twotone.Sync
import androidx.compose.material.icons.twotone.Tag
import androidx.compose.material.icons.twotone.TaskAlt
import androidx.compose.material.icons.twotone.Tune
import androidx.compose.material.icons.twotone.VolunteerActivism
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.uranxsu.manager.BuildConfig
import org.uranxsu.manager.Natives.KernelPatchImplementation
import org.uranxsu.manager.R
import org.uranxsu.manager.data.ksud.KsudSyncStatus
import org.uranxsu.manager.domain.model.ComponentHealthSummary
import org.uranxsu.manager.domain.model.ComponentRebootReason
import org.uranxsu.manager.domain.model.HealthComponentId
import org.uranxsu.manager.domain.model.HomeSystemInfo
import org.uranxsu.manager.domain.model.KernelStatus
import org.uranxsu.manager.domain.model.ManagerUpdateChannel
import org.uranxsu.manager.domain.model.ManagerUpdateInfo
import org.uranxsu.manager.domain.model.UpdateImpact
import org.uranxsu.manager.domain.model.requiresReboot
import org.uranxsu.manager.domain.usecase.EnqueueManagerUpdateUseCase
import org.uranxsu.manager.magica.MagicaService
import org.uranxsu.manager.ui.component.ConfirmResult
import org.uranxsu.manager.ui.component.KernelLkmDialog
import org.uranxsu.manager.ui.component.KsuIsValid
import org.uranxsu.manager.ui.component.KsudDaemonDialog
import org.uranxsu.manager.ui.component.SwipeableSnackbarHost
import org.uranxsu.manager.ui.component.WarningCard
import org.uranxsu.manager.ui.component.componentHomeDescriptionText
import org.uranxsu.manager.ui.component.componentRebootReason
import org.uranxsu.manager.ui.component.rebootRequiredNames
import org.uranxsu.manager.ui.component.rememberConfirmDialog
import org.uranxsu.manager.ui.component.rememberLoadingDialog
import org.uranxsu.manager.ui.component.settings.SegmentedColumn
import org.uranxsu.manager.ui.component.settings.SettingsBaseWidget
import org.uranxsu.manager.ui.component.settings.SettingsJumpPageWidget
import org.uranxsu.manager.ui.navigation.LocalNavigator
import org.uranxsu.manager.ui.navigation.Route
import org.uranxsu.manager.ui.screen.LabelText
import org.uranxsu.manager.ui.theme.CardConfig
import org.uranxsu.manager.ui.theme.HomeCustomizationConfig
import org.uranxsu.manager.ui.theme.ThemeConfig
import org.uranxsu.manager.ui.theme.blurEffect
import org.uranxsu.manager.ui.theme.blurSource
import org.uranxsu.manager.ui.util.CustomTextKey
import org.uranxsu.manager.ui.util.LocalPermissionRequestInterface
import org.uranxsu.manager.ui.util.LocalSnackbarHost
import org.uranxsu.manager.ui.util.adaptiveScaffoldWindowInsets
import org.uranxsu.manager.ui.util.customText
import org.uranxsu.manager.ui.util.downloader.downloadManagerUpdate
import org.uranxsu.manager.ui.viewmodel.HomeUiAction
import org.uranxsu.manager.ui.viewmodel.HomeUiEvent
import org.uranxsu.manager.ui.viewmodel.HomeUiState
import org.uranxsu.manager.ui.viewmodel.HomeViewModel

/**
 * @author ShirkNeko
 * @date 2025/9/29.
 */

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomePage(
    bottomPadding: Dp,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val viewModel = koinViewModel<HomeViewModel>()
    val homeCustomization = koinInject<HomeCustomizationConfig>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.dispatch(HomeUiAction.AwaitInitialData)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeUiEvent.Error -> if (event.message.isNotBlank()) {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }

                is HomeUiEvent.Info -> {
                    Toast.makeText(context, resources.getString(event.stringRes), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    ReportDrawnWhen { uiState.isInitialDataLoaded }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        rememberTopAppBarState(
            initialHeightOffset = -154f,
            initialHeightOffsetLimit = -154f, // from debugger
        ),
    )
    val scrollState = rememberScrollState()
    val navigator = LocalNavigator.current
    val loadingDialog = rememberLoadingDialog()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopBar(
                uiState = uiState,
                onReboot = { viewModel.dispatch(HomeUiAction.Reboot(it)) },
                scrollBehavior = scrollBehavior,
            )
        },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentWindowInsets = adaptiveScaffoldWindowInsets(includeBottom = false),
        snackbarHost = {
            SwipeableSnackbarHost(
                modifier = Modifier.padding(bottom = bottomPadding),
                hostState = LocalSnackbarHost.current,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .blurSource()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(scrollState)
                .padding(
                    top = innerPadding.calculateTopPadding() + 2.dp,
                    start = 16.dp,
                    end = 16.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            // 状态卡片
            if (uiState.isCoreDataLoaded) {
                if (uiState.systemStatus.isManager && !uiState.systemStatus.isFullFeatured) {
                    if ((
                            uiState.systemStatus.kernelUAPIVersion
                                ?: 1
                            ) > uiState.systemStatus.managerUAPIVersion
                    ) {
                        WarningCard(
                            message = stringResource(R.string.require_manager_version),
                            icon = {
                                Icon(
                                    imageVector = Icons.TwoTone.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            onClick = {
                                if (uiState.systemStatus.isLateLoadMode) return@WarningCard
                                navigator.push(Route.Install(preselectedKernelUri = null))
                            },
                        )
                    } else {
                        WarningCard(
                            message = if (uiState.systemStatus.lkmMode == true) {
                                stringResource(R.string.require_kernel_version)
                            } else {
                                stringResource(R.string.require_kernel_version_gki)
                            },
                            icon = {
                                Icon(
                                    imageVector = Icons.TwoTone.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            onClick = {
                                if (uiState.systemStatus.isLateLoadMode) return@WarningCard
                                navigator.push(Route.Install(preselectedKernelUri = null))
                            },
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // 警告信息
                if (BuildConfig.DEBUG) {
                    WarningCard(
                        message = stringResource(R.string.debug_version_notice),
                        icon = {
                            Icon(
                                imageVector = Icons.TwoTone.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                if (!uiState.systemStatus.isOfficialSignature) {
                    WarningCard(
                        message = stringResource(
                            R.string.unofficial_version_notice,
                            stringResource(R.string.app_name),
                        ),
                        icon = {
                            Icon(
                                imageVector = Icons.TwoTone.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                if (BuildConfig.IS_PR_BUILD || uiState.systemStatus.isPrBuild) {
                    WarningCard(
                        message = stringResource(
                            id = R.string.home_pr_build_warning,
                        ),
                        icon = {
                            Icon(
                                imageVector = Icons.TwoTone.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                if (uiState.systemStatus.kernelPatchImplementation == KernelPatchImplementation.OFFICIAL) {
                    WarningCard(
                        message = stringResource(
                            R.string.conflict_with_apatch,
                        ),
                        icon = {
                            Icon(
                                imageVector = Icons.TwoTone.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                if (uiState.systemStatus.ksuVersion != null && !uiState.systemStatus.isRootAvailable) {
                    WarningCard(
                        message = stringResource(id = R.string.grant_root_failed),
                        icon = {
                            Icon(
                                imageVector = Icons.TwoTone.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                StatusCard(
                    uiState = uiState,
                    onClickInstall = {
                        if (uiState.systemStatus.isLateLoadMode) return@StatusCard
                        navigator.push(Route.Install(preselectedKernelUri = null))
                    },
                    onClickJailbreak = {
                        loadingDialog.showLoading()
                        context.startService(Intent(context, MagicaService::class.java))
                        // Manager will be force-stopped and restarted by late-load on success.
                        // If that doesn't happen within timeout, jailbreak likely failed.
                        scope.launch(Dispatchers.IO) {
                            delay(30_000.milliseconds)
                            withContext(Dispatchers.Main) {
                                loadingDialog.hide()
                                Toast.makeText(
                                    context,
                                    R.string.jailbreak_timeout,
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                    },
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
            val rebootConfirmDialog = rememberConfirmDialog()
            if (uiState.systemStatus.needsLkmUpdate) {
                WarningCard(
                    message = stringResource(
                        R.string.home_lkm_update_warning,
                        uiState.systemStatus.runtimeLkmHash.orEmpty(),
                        BuildConfig.BUNDLED_LKM_HASH,
                    ),
                    icon = {
                        Icon(
                            imageVector = Icons.TwoTone.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    onClick = {
                        if (!uiState.systemStatus.isLateLoadMode) {
                            navigator.push(Route.Install(preselectedKernelUri = null))
                        }
                    },
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
            val ksudState = uiState.systemInfo.ksudIntegrity
            if (uiState.systemStatus.isManager &&
                (
                    ksudState?.status == KsudSyncStatus.NEEDS_SYNC ||
                        ksudState?.status == KsudSyncStatus.MISSING
                    )
            ) {
                WarningCard(
                    message = if (ksudState.status == KsudSyncStatus.MISSING) {
                        stringResource(R.string.home_ksud_missing_warning)
                    } else {
                        customText(CustomTextKey.HOME_STATUS_KSUD_SYNC)
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.TwoTone.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    onClick = {
                        viewModel.dispatch(HomeUiAction.SyncKsud)
                    },
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
            val componentHealth = uiState.systemInfo.componentHealth
            val nonLkmRebootIds = componentHealth?.componentsNeedingReboot.orEmpty().filter {
                !(uiState.systemStatus.needsLkmUpdate && it == HealthComponentId.LKM)
            }
            if (uiState.systemStatus.isManager &&
                componentHealth != null &&
                nonLkmRebootIds.isNotEmpty()
            ) {
                val rebootNames = componentHealth.rebootRequiredNames()
                val rebootConfirmTitle = stringResource(R.string.health_reboot_confirm_title)
                val rebootConfirmBody = stringResource(R.string.health_reboot_confirm_body, rebootNames)
                val rebootActionLabel = stringResource(R.string.health_reboot_action)
                val cancelLabel = stringResource(android.R.string.cancel)
                WarningCard(
                    message = stringResource(R.string.health_reboot_banner, rebootNames),
                    icon = {
                        Icon(
                            imageVector = Icons.TwoTone.RestartAlt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    onClick = {
                        scope.launch {
                            if (rebootConfirmDialog.awaitConfirm(
                                    title = rebootConfirmTitle,
                                    content = rebootConfirmBody,
                                    confirm = rebootActionLabel,
                                    dismiss = cancelLabel,
                                ) == ConfirmResult.Confirmed
                            ) {
                                viewModel.dispatch(HomeUiAction.Reboot(""))
                            }
                        }
                    },
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
            ManagerUpdateCard(uiState.stableManagerUpdate)
            ManagerUpdateCard(uiState.betaManagerUpdate)
            if (uiState.isBetaManagerUpdateCheckFailed) {
                WarningCard(
                    message = stringResource(R.string.beta_update_check_failed),
                    icon = {
                        Icon(
                            imageVector = Icons.TwoTone.Error,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            if (uiState.isExtendedDataLoaded) {
                InfoCard(
                    systemStatus = uiState.systemStatus,
                    systemInfo = uiState.systemInfo,
                    isSimpleMode = uiState.isSimpleMode,
                    showHomeCardIcons = uiState.showHomeCardIcons,
                    onSyncKsud = { viewModel.dispatch(HomeUiAction.SyncKsud) },
                    onReboot = { viewModel.dispatch(HomeUiAction.Reboot("")) },
                    onOpenInstall = {
                        if (!uiState.systemStatus.isLateLoadMode) {
                            navigator.push(Route.Install(preselectedKernelUri = null))
                        }
                    },
                    onOpenNoMount = {
                        if (uiState.systemInfo.componentHealth?.mountMode == "no_mount") {
                            navigator.push(Route.NoMountManager)
                        } else {
                            navigator.push(Route.MountSettings)
                        }
                    },
                    onOpenSuSFS = { navigator.push(Route.SuSFSConfig) },
                    onOpenZygisk = { navigator.push(Route.BuiltinZygisk) },
                )
            }

            // 链接卡片
            if (!uiState.isSimpleMode) {
                if (homeCustomization.showDonateCard) {
                    DonateCard(uiState.showHomeCardIcons)
                }
                if (homeCustomization.showLearnMoreCard) {
                    LearnMoreCard(uiState.showHomeCardIcons)
                }
            }

            Spacer(Modifier.height(bottomPadding))
        }
    }
}

@Composable
private fun ManagerUpdateCard(update: ManagerUpdateInfo?) {
    val visibilityState = remember { MutableTransitionState(false) }
    var displayedUpdate by remember { mutableStateOf<ManagerUpdateInfo?>(null) }

    LaunchedEffect(update) {
        if (update != null) displayedUpdate = update
        visibilityState.targetState = update != null
    }

    AnimatedVisibility(
        visibleState = visibilityState,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        displayedUpdate?.let { updateInfo ->
            ManagerUpdateCardContent(updateInfo)
        }
    }
}

@Composable
private fun ManagerUpdateCardContent(updateInfo: ManagerUpdateInfo) {
    val context = LocalContext.current
    val permissionRequestInterface = LocalPermissionRequestInterface.current
    val enqueueManagerUpdate = koinInject<EnqueueManagerUpdateUseCase>()
    val channelTitle = stringResource(
        if (updateInfo.channel == ManagerUpdateChannel.STABLE) {
            R.string.manager_update_stable
        } else {
            R.string.manager_update_beta
        },
    )
    val message = if (updateInfo.channel == ManagerUpdateChannel.STABLE) {
        stringResource(R.string.new_version_available, updateInfo.versionCode)
    } else {
        stringResource(R.string.beta_version_available, updateInfo.versionCode)
    }
    val impactLabel = when (updateInfo.updateImpact) {
        UpdateImpact.UNKNOWN -> null
        UpdateImpact.APK_ONLY -> stringResource(R.string.update_impact_apk_only)
        UpdateImpact.KSUD_SYNC -> stringResource(R.string.update_impact_ksud_sync)
        UpdateImpact.LKM_UPDATE -> stringResource(R.string.update_impact_lkm_update)
    }
    val updateText = customText(CustomTextKey.HOME_UPDATE_BUTTON)
    val baseDetails = stringResource(
        R.string.manager_update_details,
        updateInfo.versionName,
        updateInfo.versionCode,
        updateInfo.abi,
    )
    val details = if (impactLabel != null) "$baseDetails\n$impactLabel" else baseDetails
    val dialogContent = if (updateInfo.changelog.isBlank()) {
        details
    } else {
        "$details\n\n${updateInfo.changelog}"
    }
    val updateDialog = rememberConfirmDialog(
        onConfirm = {
            downloadManagerUpdate(
                context,
                permissionRequestInterface,
                updateInfo,
                enqueueManagerUpdate,
            )
        },
    )

    WarningCard(
        message = if (impactLabel != null) "$message • $impactLabel" else message,
        color = MaterialTheme.colorScheme.outlineVariant,
        icon = {
            Icon(
                imageVector = Icons.TwoTone.Info,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        },
        onClick = {
            updateDialog.showConfirm(
                title = channelTitle,
                content = dialogContent,
                markdown = updateInfo.changelog.isNotBlank(),
                confirm = updateText,
            )
        },
    )

    Spacer(modifier = Modifier.height(10.dp))
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RebootDropdownItems(
    items: Map<Int, String>,
    onReboot: (String) -> Unit,
) {
    items.onEachIndexed { index, (id, reason) ->
        val label = when (id) {
            R.string.reboot -> customText(CustomTextKey.COMMON_REBOOT)
            R.string.reboot_soft -> customText(CustomTextKey.COMMON_REBOOT_SOFT)
            R.string.reboot_userspace -> customText(CustomTextKey.COMMON_REBOOT_USERSPACE)
            R.string.reboot_recovery -> customText(CustomTextKey.COMMON_REBOOT_RECOVERY)
            R.string.reboot_bootloader -> customText(CustomTextKey.COMMON_REBOOT_BOOTLOADER)
            R.string.reboot_download -> customText(CustomTextKey.COMMON_REBOOT_DOWNLOAD)
            R.string.reboot_edl -> customText(CustomTextKey.COMMON_REBOOT_EDL)
            else -> stringResource(id)
        }
        DropdownMenuItem(
            shape = MenuDefaults.itemShape(index, items.size).shape,
            text = { Text(label) },
            onClick = { onReboot(reason) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TopBar(
    uiState: HomeUiState,
    onReboot: (String) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()

    LargeFlexibleTopAppBar(
        modifier = Modifier.blurEffect(),
        title = {
            Text(
                text = customText(CustomTextKey.HOME_TITLE),
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor =
                if (themeConfig.isEnableBlur) {
                    Color.Transparent
                } else {
                    MaterialTheme.colorScheme.surfaceContainer.copy(cardConfig.cardAlpha)
                },
            scrolledContainerColor =
                if (themeConfig.isEnableBlur) {
                    Color.Transparent
                } else {
                    MaterialTheme.colorScheme.surfaceContainer.copy(cardConfig.cardAlpha)
                },
        ),
        actions = {
            if (uiState.isCoreDataLoaded) {
                // 重启按钮
                var showDropdown by remember { mutableStateOf(false) }
                KsuIsValid(uiState.systemStatus) {
                    if (uiState.systemStatus.isRootAvailable) {
                        IconButton(onClick = {
                            showDropdown = true
                        }) {
                            Icon(
                                imageVector = Icons.TwoTone.PowerSettingsNew,
                                contentDescription = stringResource(id = R.string.reboot),
                            )

                            DropdownMenuPopup(expanded = showDropdown, onDismissRequest = {
                                showDropdown = false
                            }) {
                                DropdownMenuGroup(
                                    shapes = MenuDefaults.groupShapes(),
                                ) {
                                    val pm =
                                        LocalContext.current.getSystemService(Context.POWER_SERVICE) as PowerManager?
                                    var methods = mapOf(
                                        R.string.reboot to "",
                                        R.string.reboot_soft to "soft_reboot",
                                        R.string.reboot_recovery to "recovery",
                                        R.string.reboot_bootloader to "bootloader",
                                        R.string.reboot_download to "download",
                                        R.string.reboot_edl to "edl",
                                    )

                                    @Suppress("DEPRECATION")
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && pm?.isRebootingUserspaceSupported == true) {
                                        methods = methods + (R.string.reboot_userspace to "userspace")
                                    }

                                    RebootDropdownItems(methods, onReboot)
                                }
                            }
                        }
                    }
                }
            }
        },
        windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
        scrollBehavior = scrollBehavior,
    )
}

@Composable
private fun StatusCard(
    uiState: HomeUiState,
    onClickInstall: () -> Unit = {},
    onClickJailbreak: () -> Unit = {},
) {
    val context = LocalContext.current
    val homeCustomization = koinInject<HomeCustomizationConfig>()
    val systemStatus = uiState.systemStatus
    val onClick = { _: Offset ->
        if (systemStatus.isRootAvailable || systemStatus.kernelVersion.isGKI()) {
            onClickInstall()
        }
    }

    val statusCardImageFile = remember(
        homeCustomization.statusCardImagePath,
        homeCustomization.statusCardImageVersion,
    ) {
        homeCustomization.statusCardImagePath?.let(::File)?.takeIf { it.isFile }
    }

    if (statusCardImageFile != null && systemStatus.ksuVersion != null) {
        val workingModeText = when {
            systemStatus.isSafeMode -> customText(CustomTextKey.HOME_TAG_SAFEMODE)
            else -> customText(CustomTextKey.HOME_STATUS_WORKING)
        }
        val workingModeSurfaceText = if (systemStatus.lkmMode == true) "LKM" else "Built-in"
        val painter = rememberAsyncImagePainter(
            model = ImageRequest.Builder(context)
                .data(statusCardImageFile)
                .setParameter("v", homeCustomization.statusCardImageVersion)
                .crossfade(true)
                .build(),
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .clickable {
                    if (systemStatus.isRootAvailable || systemStatus.kernelVersion.isGKI()) {
                        onClickInstall()
                    }
                },
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = homeCustomization.statusCardHeight.dp),
            ) {
                Image(
                    painter = painter,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = homeCustomization.statusCardScrim)),
                )
                if (!homeCustomization.hideStatusCardText || !homeCustomization.hideStatusCardDecoration) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.CenterStart)
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (!homeCustomization.hideStatusCardDecoration) {
                                Icon(
                                    imageVector = Icons.TwoTone.TaskAlt,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            if (!homeCustomization.hideStatusCardText) {
                                Text(
                                    text = workingModeText,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                )
                            }
                            if (!homeCustomization.hideStatusCardDecoration) {
                                LabelText(
                                    label = workingModeSurfaceText,
                                    containerColor = MaterialTheme.colorScheme.primary,
                                )
                                if (systemStatus.isLateLoadMode) {
                                    LabelText(
                                        label = stringResource(id = R.string.jailbreak_mode),
                                        containerColor = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                if (Os.uname().machine != "aarch64") {
                                    LabelText(
                                        label = Os.uname().machine,
                                        containerColor = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                        if (!homeCustomization.hideStatusCardText) {
                            Text(
                                text = stringResource(
                                    R.string.home_short_info,
                                    uiState.systemInfo.superuserCount,
                                    uiState.systemInfo.moduleCount,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.88f),
                            )
                        }
                    }
                }
            }
        }
        return
    }

    when {
        systemStatus.ksuVersion != null -> {
            val workingModeText = when {
                systemStatus.isSafeMode -> customText(CustomTextKey.HOME_TAG_SAFEMODE)
                else -> customText(CustomTextKey.HOME_STATUS_WORKING)
            }

            val workingModeSurfaceText = when {
                systemStatus.lkmMode == true -> "LKM"
                else -> "Built-in"
            }

            SettingsBaseWidget(
                modifier = Modifier.defaultMinSize(minHeight = homeCustomization.statusCardHeight.dp),
                icon = Icons.TwoTone.TaskAlt.takeIf { !homeCustomization.hideStatusCardDecoration },
                iconPlaceholder = false,
                iconSize = 18.dp,
                title = if (homeCustomization.hideStatusCardText) "" else workingModeText,
                description = if (homeCustomization.hideStatusCardText) {
                    ""
                } else {
                    stringResource(
                        R.string.home_short_info,
                        uiState.systemInfo.superuserCount,
                        uiState.systemInfo.moduleCount,
                    )
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                foreContent = if (homeCustomization.hideStatusCardDecoration) {
                    null
                } else {
                    {
                        Spacer(Modifier.width(8.dp))

                        // 工作模式标签
                        LabelText(
                            label = workingModeSurfaceText,
                            containerColor = MaterialTheme.colorScheme.primary,
                        )

                        if (systemStatus.isLateLoadMode) {
                            Spacer(Modifier.width(6.dp))
                            LabelText(
                                label = stringResource(id = R.string.jailbreak_mode),
                                containerColor = MaterialTheme.colorScheme.primary,
                            )
                        }

                        // 架构标签
                        if (Os.uname().machine != "aarch64") {
                            Spacer(Modifier.width(6.dp))
                            LabelText(
                                label = Os.uname().machine,
                                containerColor = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                onClick = onClick,
            )
        }

        systemStatus.kernelVersion.isGKI() -> {
            SettingsBaseWidget(
                modifier = Modifier.defaultMinSize(minHeight = homeCustomization.statusCardHeight.dp),
                icon = Icons.TwoTone.Warning,
                iconSize = 18.dp,
                isError = true,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                title = customText(CustomTextKey.HOME_STATUS_NOT_INSTALLED),
                description = customText(CustomTextKey.HOME_STATUS_INSTALL_HINT),
                onClick = onClick,
                trailingContent = if (systemStatus.isSELinuxPermissive) {
                    {
                        Button(
                            onClick = onClickJailbreak,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                        ) {
                            Text(stringResource(R.string.home_jailbreak))
                        }
                    }
                } else {
                    null
                },
            )
        }

        else -> {
            SettingsBaseWidget(
                modifier = Modifier.defaultMinSize(minHeight = homeCustomization.statusCardHeight.dp),
                icon = Icons.TwoTone.Block,
                iconSize = 18.dp,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                isError = true,
                title = customText(CustomTextKey.HOME_STATUS_UNSUPPORTED),
                description = stringResource(R.string.home_unsupported_reason),
            )
        }
    }
}

@Composable
fun LearnMoreCard(
    showIcon: Boolean,
) {
    val uriHandler = LocalUriHandler.current
    val url = stringResource(R.string.home_learn_kernelsu_url)
    val learnTitle = customText(CustomTextKey.HOME_LEARN_TITLE)

    SegmentedColumn(
        modifier = Modifier.fillMaxWidth(),
        title = stringResource(R.string.learn_more),
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
    ) {
        item {
            SettingsBaseWidget(
                icon = Icons.AutoMirrored.TwoTone.MenuBook.takeIf { showIcon },
                iconPlaceholder = false,
                title = learnTitle,
                description = stringResource(R.string.home_click_to_learn_kernelsu),
                onClick = {
                    uriHandler.openUri(url)
                },
            )
        }
    }
}

@Composable
fun DonateCard(
    showIcon: Boolean,
) {
    val uriHandler = LocalUriHandler.current
    val supportTitle = customText(CustomTextKey.HOME_SUPPORT_TITLE)
    SegmentedColumn(
        modifier = Modifier.fillMaxWidth(),
        title = supportTitle,
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
    ) {
        item {
            SettingsBaseWidget(
                icon = Icons.TwoTone.VolunteerActivism.takeIf { showIcon },
                iconPlaceholder = false,
                title = supportTitle,
                description = stringResource(R.string.home_support_content),
                onClick = {
                    uriHandler.openUri("https://patreon.com/weishu")
                },
            )
        }
    }
}

@Composable
private fun InfoCard(
    systemStatus: KernelStatus,
    systemInfo: HomeSystemInfo,
    isSimpleMode: Boolean,
    showHomeCardIcons: Boolean,
    onSyncKsud: () -> Unit = {},
    onReboot: () -> Unit = {},
    onOpenInstall: () -> Unit = {},
    onOpenNoMount: () -> Unit = {},
    onOpenSuSFS: () -> Unit = {},
    onOpenZygisk: () -> Unit = {},
) {
    val managersList = systemInfo.managersList
    var showKsudDialog by remember { mutableStateOf(false) }
    var showLkmDialog by remember { mutableStateOf(false) }

    val ksudSnapshot = systemInfo.ksudIntegrity
    val componentHealth = systemInfo.componentHealth ?: ComponentHealthSummary(
        isFullFeatured = systemStatus.isFullFeatured,
        lkmMode = systemStatus.lkmMode,
        isLateLoadMode = systemStatus.isLateLoadMode,
        runtimeLkmHash = systemStatus.runtimeLkmHash,
        bundledLkmHash = BuildConfig.BUNDLED_LKM_HASH,
        kernelUapiVersion = systemStatus.kernelUAPIVersion,
        managerUapiVersion = systemStatus.managerUAPIVersion,
        ksudIntegrity = ksudSnapshot ?: org.uranxsu.manager.data.ksud.KsudIntegritySnapshot(),
    )

    if (showKsudDialog) {
        KsudDaemonDialog(
            health = componentHealth,
            onDismiss = { showKsudDialog = false },
            onSync = onSyncKsud,
            onReboot = onReboot,
        )
    }

    if (showLkmDialog) {
        KernelLkmDialog(
            health = componentHealth,
            onDismiss = { showLkmDialog = false },
            onOpenInstall = onOpenInstall,
            onReboot = onReboot,
        )
    }

    SegmentedColumn(
        title = stringResource(R.string.home_version_info),
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
    ) {
        item {
            SettingsBaseWidget(
                icon = Icons.TwoTone.Smartphone.takeIf { showHomeCardIcons },
                iconPlaceholder = false,
                title = customText(CustomTextKey.HOME_INFO_DEVICE_MODEL),
                description = systemInfo.deviceModel,
            )
        }

        item {
            SettingsBaseWidget(
                icon = Icons.TwoTone.DeveloperBoard.takeIf { showHomeCardIcons },
                iconPlaceholder = false,
                title = customText(CustomTextKey.HOME_INFO_KERNEL),
                description = systemInfo.kernelRelease,
            )
        }

        item(
            visible = !isSimpleMode,
        ) {
            SettingsBaseWidget(
                icon = Icons.TwoTone.Android.takeIf { showHomeCardIcons },
                iconPlaceholder = false,
                title = customText(CustomTextKey.HOME_INFO_ANDROID),
                description = systemInfo.androidVersion,
            )
        }

        item(
            visible = systemStatus.isManager,
        ) {
            SettingsBaseWidget(
                icon = Icons.TwoTone.Memory.takeIf { showHomeCardIcons },
                iconPlaceholder = false,
                title = customText(CustomTextKey.HOME_INFO_KERNEL_FULL),
                description = systemStatus.ksuFullVersion.orEmpty(),
            )
        }

        item {
            SettingsBaseWidget(
                icon = Icons.TwoTone.Tag.takeIf { showHomeCardIcons },
                iconPlaceholder = false,
                title = customText(CustomTextKey.HOME_INFO_MANAGER_VERSION),
                description = "${systemInfo.managerVersion.first} (${systemInfo.managerVersion.second}/${systemInfo.managerVersion.third})",
            )
        }
    }

    if (systemStatus.isManager) {
        SegmentedColumn(
            title = stringResource(R.string.health_components_section),
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
        ) {
            item {
                val reason = componentRebootReason(componentHealth, HealthComponentId.LKM)
                SettingsJumpPageWidget(
                    icon = Icons.TwoTone.HealthAndSafety.takeIf { showHomeCardIcons },
                    iconPlaceholder = false,
                    title = stringResource(R.string.health_lkm_title),
                    description = componentHomeDescriptionText(componentHealth, HealthComponentId.LKM),
                    isError = reason.requiresReboot,
                    onClick = { showLkmDialog = true },
                )
            }

            item {
                val reason = componentRebootReason(componentHealth, HealthComponentId.KSUD)
                SettingsJumpPageWidget(
                    icon = Icons.TwoTone.Sync.takeIf { showHomeCardIcons },
                    iconPlaceholder = false,
                    title = customText(CustomTextKey.HOME_INFO_KSUD),
                    description = componentHomeDescriptionText(componentHealth, HealthComponentId.KSUD),
                    isError = reason.requiresReboot || reason == ComponentRebootReason.KSUD_NEEDS_SYNC,
                    onClick = { showKsudDialog = true },
                )
            }

            item(visible = systemStatus.isFullFeatured) {
                val reason = componentRebootReason(componentHealth, HealthComponentId.SUSFS)
                SettingsJumpPageWidget(
                    icon = Icons.TwoTone.Tune.takeIf { showHomeCardIcons },
                    iconPlaceholder = false,
                    title = customText(CustomTextKey.HOME_INFO_SUSFS),
                    description = componentHomeDescriptionText(componentHealth, HealthComponentId.SUSFS),
                    isError = reason.requiresReboot,
                    onClick = { onOpenSuSFS() },
                )
            }

            item(visible = systemStatus.isFullFeatured) {
                val reason = componentRebootReason(componentHealth, HealthComponentId.NOMOUNT)
                SettingsJumpPageWidget(
                    icon = Icons.TwoTone.Layers.takeIf { showHomeCardIcons },
                    iconPlaceholder = false,
                    title = customText(CustomTextKey.TOOL_NOMOUNT),
                    description = componentHomeDescriptionText(componentHealth, HealthComponentId.NOMOUNT),
                    isError = reason.requiresReboot || componentHealth.nomountBootloopDisabled,
                    onClick = { onOpenNoMount() },
                )
            }

            item(visible = systemStatus.isFullFeatured) {
                val reason = componentRebootReason(componentHealth, HealthComponentId.ZYGISK)
                SettingsJumpPageWidget(
                    icon = Icons.TwoTone.Science.takeIf { showHomeCardIcons },
                    iconPlaceholder = false,
                    title = customText(CustomTextKey.HOME_INFO_ZYGISK),
                    description = componentHomeDescriptionText(componentHealth, HealthComponentId.ZYGISK),
                    isError = reason.requiresReboot,
                    onClick = { onOpenZygisk() },
                )
            }
        }
    }

    SegmentedColumn(
        title = stringResource(R.string.home_status_info),
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
    ) {
        item {
            SettingsBaseWidget(
                icon = Icons.TwoTone.Security.takeIf { showHomeCardIcons },
                iconPlaceholder = false,
                title = customText(CustomTextKey.HOME_INFO_SELINUX),
                description = systemInfo.selinuxStatus,
            )
        }

        item {
            val seccompDisplay = when (systemInfo.seccompStatus) {
                -1 -> stringResource(R.string.seccomp_status_not_supported)
                0 -> stringResource(R.string.seccomp_status_disabled)
                1 -> stringResource(R.string.seccomp_status_strict)
                2 -> stringResource(R.string.seccomp_status_filter)
                else -> stringResource(R.string.seccomp_status_unknown)
            }

            SettingsBaseWidget(
                icon = Icons.TwoTone.FilterList.takeIf { showHomeCardIcons },
                iconPlaceholder = false,
                title = customText(CustomTextKey.HOME_INFO_SECCOMP),
                description = seccompDisplay,
            )
        }

        item(
            visible = !isSimpleMode && managersList != null,
        ) {
            val signatureMap =
                managersList?.managers.orEmpty().groupBy { it.signatureIndex }
            val managersText = buildString {
                signatureMap.toSortedMap().forEach { (signatureIndex, managers) ->
                    append(managers.joinToString(", ") { "UID: ${it.uid}" })
                    append(" ")
                    append(
                        when (signatureIndex) {
                            0 -> "(${stringResource(R.string.app_name)})"

                            255 -> "(${stringResource(R.string.dynamic_managerature)})"

                            else -> if (signatureIndex >= 1) {
                                "(${
                                    stringResource(
                                        R.string.signature_index,
                                        signatureIndex,
                                    )
                                })"
                            } else {
                                "(${stringResource(R.string.unknown_signature)})"
                            }
                        },
                    )
                    append(" | ")
                }
            }.trimEnd(' ', '|')

            SettingsBaseWidget(
                icon = Icons.TwoTone.Group.takeIf { showHomeCardIcons },
                iconPlaceholder = false,
                title = customText(CustomTextKey.HOME_INFO_MULTI_MANAGER),
                description = managersText.ifEmpty { stringResource(R.string.no_active_manager) },
            )
        }

        item(
            visible = !isSimpleMode && systemStatus.isFullFeatured,
        ) {
            SettingsBaseWidget(
                icon = Icons.TwoTone.Tune.takeIf { showHomeCardIcons },
                iconPlaceholder = false,
                title = customText(CustomTextKey.HOME_INFO_HOOK_TYPE),
                description = systemStatus.hookType,
            )
        }

        item(
            visible = !isSimpleMode && systemInfo.metaModuleImplement.isNotEmpty() && systemInfo.metaModuleImplement != "None",
        ) {
            SettingsBaseWidget(
                icon = Icons.TwoTone.Extension.takeIf { showHomeCardIcons },
                iconPlaceholder = false,
                title = customText(CustomTextKey.HOME_INFO_METAMODULE),
                description = systemInfo.metaModuleImplement,
            )
        }
    }
}
