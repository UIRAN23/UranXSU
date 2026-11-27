package org.uranxsu.manager.ui.screen.health

import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Backup
import androidx.compose.material.icons.twotone.CleaningServices
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.DeleteSweep
import androidx.compose.material.icons.twotone.HealthAndSafety
import androidx.compose.material.icons.twotone.Layers
import androidx.compose.material.icons.twotone.Memory
import androidx.compose.material.icons.twotone.RestartAlt
import androidx.compose.material.icons.twotone.Restore
import androidx.compose.material.icons.twotone.Science
import androidx.compose.material.icons.twotone.Sync
import androidx.compose.material.icons.twotone.TaskAlt
import androidx.compose.material.icons.twotone.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.uranxsu.manager.R
import org.uranxsu.manager.data.backup.BackupInspection
import org.uranxsu.manager.data.backup.BackupSection
import org.uranxsu.manager.data.backup.ConfigBackupManager
import org.uranxsu.manager.domain.model.HealthComponentId
import org.uranxsu.manager.domain.model.requiresReboot
import org.uranxsu.manager.ui.component.ConfirmResult
import org.uranxsu.manager.ui.component.KernelLkmDialog
import org.uranxsu.manager.ui.component.KsudDaemonDialog
import org.uranxsu.manager.ui.component.SwipeableSnackbarHost
import org.uranxsu.manager.ui.component.WarningCard
import org.uranxsu.manager.ui.component.componentDescriptionText
import org.uranxsu.manager.ui.component.componentRebootReason
import org.uranxsu.manager.ui.component.rebootRequiredNames
import org.uranxsu.manager.ui.component.rememberConfirmDialog
import org.uranxsu.manager.ui.component.rememberLoadingDialog
import org.uranxsu.manager.ui.component.settings.AppBackButton
import org.uranxsu.manager.ui.component.settings.SegmentedColumn
import org.uranxsu.manager.ui.component.settings.SettingsBaseWidget
import org.uranxsu.manager.ui.component.settings.SettingsJumpPageWidget
import org.uranxsu.manager.ui.navigation.LocalNavigator
import org.uranxsu.manager.ui.navigation.Route
import org.uranxsu.manager.ui.theme.CardConfig
import org.uranxsu.manager.ui.theme.ThemeConfig
import org.uranxsu.manager.ui.theme.blurEffect
import org.uranxsu.manager.ui.theme.blurSource
import org.uranxsu.manager.ui.util.ActivityResumeEffect
import org.uranxsu.manager.ui.util.CustomTextKey
import org.uranxsu.manager.ui.util.LocalSnackbarHost
import org.uranxsu.manager.ui.util.adaptiveScaffoldWindowInsets
import org.uranxsu.manager.ui.util.customText
import org.uranxsu.manager.ui.util.showReplacingSnackbar
import org.uranxsu.manager.ui.viewmodel.CleanupItemId
import org.uranxsu.manager.ui.viewmodel.SystemHealthViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SystemHealthScreen() {
    val context = LocalContext.current
    val resources = LocalResources.current
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val viewModel = koinViewModel<SystemHealthViewModel>()
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val snackbarHost = LocalSnackbarHost.current
    val loadingDialog = rememberLoadingDialog()
    val confirmDialog = rememberConfirmDialog()
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val pullRefreshState = rememberPullToRefreshState()

    var showExportDialog by remember { mutableStateOf(false) }
    var selectedExportSections by remember { mutableStateOf(BackupSection.entries.toSet()) }
    var pendingRestore by remember { mutableStateOf<Pair<Uri, BackupInspection>?>(null) }
    var selectedRestoreSections by remember { mutableStateOf<Set<BackupSection>>(emptySet()) }
    var showKsudDialog by remember { mutableStateOf(false) }
    var showLkmDialog by remember { mutableStateOf(false) }

    val ksudSyncedMsg = stringResource(R.string.ksud_sync_success)
    val opFailMsg = stringResource(R.string.mount_operation_failed)
    val rebootConfirmTitle = stringResource(R.string.health_reboot_confirm_title)
    val rebootActionLabel = stringResource(R.string.health_reboot_action)
    val cancelLabel = stringResource(android.R.string.cancel)
    val backupExportOk = stringResource(R.string.backup_export_success)
    val backupExportFail = stringResource(R.string.backup_export_failed)
    val backupInspectFail = stringResource(R.string.backup_inspect_failed)

    val health = uiState.health
    val rebootRequiredNames = health.rebootRequiredNames()
    val rebootConfirmBody = stringResource(R.string.health_reboot_confirm_body, rebootRequiredNames)

    fun triggerReboot() {
        scope.launch {
            val confirm = confirmDialog.awaitConfirm(
                title = rebootConfirmTitle,
                content = rebootConfirmBody,
                confirm = rebootActionLabel,
                dismiss = cancelLabel,
            )
            if (confirm == ConfirmResult.Confirmed) {
                viewModel.reboot("").onFailure {
                    snackbarHost.showReplacingSnackbar(opFailMsg)
                }
            }
        }
    }

    if (showKsudDialog) {
        KsudDaemonDialog(
            health = health,
            onDismiss = { showKsudDialog = false },
            onSync = {
                scope.launch {
                    val ok = loadingDialog.withLoading { viewModel.syncKsud().isSuccess }
                    snackbarHost.showReplacingSnackbar(if (ok) ksudSyncedMsg else opFailMsg)
                }
            },
            onReboot = ::triggerReboot,
        )
    }

    if (showLkmDialog) {
        KernelLkmDialog(
            health = health,
            onDismiss = { showLkmDialog = false },
            onOpenInstall = { navigator.push(Route.Install(null)) },
            onReboot = ::triggerReboot,
        )
    }

    ActivityResumeEffect(viewModel) {
        viewModel.refresh()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val res = loadingDialog.withLoading {
                viewModel.exportBackup(uri, selectedExportSections)
            }
            snackbarHost.showReplacingSnackbar(
                if (res.isSuccess) backupExportOk else backupExportFail,
            )
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val inspection = loadingDialog.withLoading {
                viewModel.inspectBackup(uri).getOrNull()
            }
            if (inspection == null || inspection.availableSections.isEmpty()) {
                snackbarHost.showReplacingSnackbar(backupInspectFail)
            } else {
                selectedRestoreSections = inspection.availableSections
                pendingRestore = uri to inspection
            }
        }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                modifier = Modifier.blurEffect(),
                title = { Text(customText(CustomTextKey.TOOL_SYSTEM_HEALTH)) },
                navigationIcon = {
                    AppBackButton(onClick = { navigator.pop() })
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (themeConfig.isEnableBlur) {
                        Color.Transparent
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer.copy(cardConfig.cardAlpha)
                    },
                    scrolledContainerColor = if (themeConfig.isEnableBlur) {
                        Color.Transparent
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer.copy(cardConfig.cardAlpha)
                    },
                ),
                windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SwipeableSnackbarHost(hostState = snackbarHost) },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentWindowInsets = adaptiveScaffoldWindowInsets(),
    ) { innerPadding ->
        PullToRefreshBox(
            state = pullRefreshState,
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .blurSource(),
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    modifier = Modifier
                        .padding(top = innerPadding.calculateTopPadding())
                        .align(Alignment.TopCenter),
                    state = pullRefreshState,
                    isRefreshing = uiState.isRefreshing,
                )
            },
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 5.dp,
                    bottom = innerPadding.calculateBottomPadding() + 16.dp,
                ),
            ) {
                if (health.anyRebootRequired) {
                    item(key = "reboot_banner") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                        ) {
                            WarningCard(
                                message = stringResource(R.string.health_reboot_banner, rebootRequiredNames),
                                icon = {
                                    Icon(
                                        imageVector = Icons.TwoTone.RestartAlt,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                                onClick = ::triggerReboot,
                            )
                        }
                    }
                }

                item(key = "components_section") {
                    SegmentedColumn(title = stringResource(R.string.health_components_section)) {
                        item {
                            val reason = componentRebootReason(health, HealthComponentId.LKM)
                            SettingsJumpPageWidget(
                                icon = Icons.TwoTone.HealthAndSafety,
                                title = stringResource(R.string.health_lkm_title),
                                description = componentDescriptionText(health, HealthComponentId.LKM),
                                isError = reason.requiresReboot,
                                onClick = { showLkmDialog = true },
                            )
                        }

                        item {
                            val reason = componentRebootReason(health, HealthComponentId.KSUD)
                            SettingsJumpPageWidget(
                                icon = Icons.TwoTone.Sync,
                                title = customText(CustomTextKey.HOME_INFO_KSUD),
                                description = componentDescriptionText(health, HealthComponentId.KSUD),
                                isError = reason.requiresReboot ||
                                    reason == org.uranxsu.manager.domain.model.ComponentRebootReason.KSUD_NEEDS_SYNC,
                                onClick = { showKsudDialog = true },
                            )
                        }

                        item(visible = health.isFullFeatured) {
                            val reason = componentRebootReason(health, HealthComponentId.NOMOUNT)
                            SettingsJumpPageWidget(
                                icon = Icons.TwoTone.Layers,
                                title = customText(CustomTextKey.TOOL_NOMOUNT),
                                description = componentDescriptionText(health, HealthComponentId.NOMOUNT),
                                isError = reason.requiresReboot || health.nomountBootloopDisabled,
                                onClick = {
                                    if (health.mountMode == "no_mount") {
                                        navigator.push(Route.NoMountManager)
                                    } else {
                                        navigator.push(Route.MountSettings)
                                    }
                                },
                            )
                        }

                        item(visible = health.isFullFeatured) {
                            val reason = componentRebootReason(health, HealthComponentId.SUSFS)
                            SettingsJumpPageWidget(
                                icon = Icons.TwoTone.Tune,
                                title = customText(CustomTextKey.EXTENSION_SUSFS),
                                description = componentDescriptionText(health, HealthComponentId.SUSFS),
                                isError = reason.requiresReboot,
                                onClick = { navigator.push(Route.SuSFSConfig) },
                            )
                        }

                        item(visible = health.isFullFeatured) {
                            val reason = componentRebootReason(health, HealthComponentId.ZYGISK)
                            SettingsJumpPageWidget(
                                icon = Icons.TwoTone.Science,
                                title = customText(CustomTextKey.ZYGISK_SETTINGS_TITLE),
                                description = componentDescriptionText(health, HealthComponentId.ZYGISK),
                                isError = reason.requiresReboot,
                                onClick = { navigator.push(Route.BuiltinZygisk) },
                            )
                        }
                    }
                }

                item(key = "cleanup_section") {
                    SegmentedColumn(title = stringResource(R.string.cleanup_section_title)) {
                        if (uiState.cleanupEntries.isEmpty()) {
                            item {
                                SettingsBaseWidget(
                                    icon = Icons.TwoTone.TaskAlt,
                                    title = stringResource(R.string.cleanup_system_clean),
                                )
                            }
                        } else {
                            uiState.cleanupEntries.forEach { entry ->
                                item(key = entry.id.name) {
                                    val titleRes = when (entry.id) {
                                        CleanupItemId.OVERLAYIMG -> R.string.cleanup_item_overlayimg
                                        CleanupItemId.OLD_LOGS -> R.string.cleanup_item_old_logs
                                        CleanupItemId.TMP_APKS -> R.string.cleanup_item_tmp_apks
                                        CleanupItemId.APP_CACHE -> R.string.cleanup_item_app_cache
                                    }
                                    val sizeLabel = Formatter.formatShortFileSize(context, entry.sizeBytes)
                                    SettingsBaseWidget(
                                        icon = Icons.TwoTone.CleaningServices,
                                        title = stringResource(titleRes),
                                        description = "${entry.path} ($sizeLabel)",
                                        trailingContent = {
                                            IconButton(
                                                onClick = {
                                                    scope.launch {
                                                        val freed = loadingDialog.withLoading { viewModel.cleanItem(entry.id) }
                                                        val msg = resources.getString(
                                                            R.string.cleanup_done,
                                                            Formatter.formatShortFileSize(context, freed),
                                                        )
                                                        snackbarHost.showReplacingSnackbar(msg)
                                                    }
                                                },
                                            ) {
                                                Icon(
                                                    imageVector = Icons.TwoTone.Delete,
                                                    contentDescription = stringResource(titleRes),
                                                )
                                            }
                                        },
                                    )
                                }
                            }

                            item {
                                val totalSizeLabel = Formatter.formatShortFileSize(context, uiState.totalCleanableBytes)
                                SettingsJumpPageWidget(
                                    icon = Icons.TwoTone.DeleteSweep,
                                    title = stringResource(R.string.cleanup_clean_all, totalSizeLabel),
                                    onClick = {
                                        scope.launch {
                                            val freed = loadingDialog.withLoading { viewModel.cleanAll() }
                                            val msg = resources.getString(
                                                R.string.cleanup_done,
                                                Formatter.formatShortFileSize(context, freed),
                                            )
                                            snackbarHost.showReplacingSnackbar(msg)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                item(key = "backup_section") {
                    SegmentedColumn(title = customText(CustomTextKey.TOOL_CONFIG_BACKUP)) {
                        item {
                            SettingsJumpPageWidget(
                                icon = Icons.TwoTone.Backup,
                                title = stringResource(R.string.backup_export_title),
                                description = stringResource(R.string.backup_export_summary),
                                onClick = {
                                    selectedExportSections = BackupSection.entries.toSet()
                                    showExportDialog = true
                                },
                            )
                        }

                        item {
                            SettingsJumpPageWidget(
                                icon = Icons.TwoTone.Restore,
                                title = stringResource(R.string.backup_restore_title),
                                description = stringResource(R.string.backup_restore_summary),
                                onClick = { importLauncher.launch(arrayOf("*/*")) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showExportDialog) {
        BackupSectionsDialog(
            title = stringResource(R.string.backup_export_dialog_title),
            subtitle = null,
            availableSections = BackupSection.entries,
            selected = selectedExportSections,
            confirmLabel = stringResource(R.string.backup_confirm_export),
            onToggle = { section, checked ->
                selectedExportSections = if (checked) {
                    selectedExportSections + section
                } else {
                    selectedExportSections - section
                }
            },
            onDismiss = { showExportDialog = false },
            onConfirm = {
                showExportDialog = false
                exportLauncher.launch(ConfigBackupManager.backupFileName())
            },
        )
    }

    pendingRestore?.let { (uri, inspection) ->
        val dateText = if (inspection.manifest.createdAtEpochMs > 0L) {
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(Date(inspection.manifest.createdAtEpochMs))
        } else {
            "-"
        }
        val verText = inspection.manifest.appVersionName.ifBlank { "UranXSU" }
        BackupSectionsDialog(
            title = stringResource(R.string.backup_restore_dialog_title),
            subtitle = stringResource(R.string.backup_restore_meta, dateText, verText),
            availableSections = BackupSection.entries.filter { it in inspection.availableSections },
            selected = selectedRestoreSections,
            confirmLabel = stringResource(R.string.confirm),
            onToggle = { section, checked ->
                selectedRestoreSections = if (checked) {
                    selectedRestoreSections + section
                } else {
                    selectedRestoreSections - section
                }
            },
            onDismiss = { pendingRestore = null },
            onConfirm = {
                val chosen = selectedRestoreSections
                pendingRestore = null
                scope.launch {
                    val res = loadingDialog.withLoading { viewModel.restoreBackup(uri, chosen) }
                    val msg = res.fold(
                        onSuccess = { summary ->
                            if (summary.failed.isEmpty()) {
                                resources.getString(R.string.backup_restore_success, summary.restored.size)
                            } else {
                                resources.getString(
                                    R.string.backup_restore_partial,
                                    summary.restored.size,
                                    summary.failed.keys.joinToString(", ") { it.id },
                                )
                            }
                        },
                        onFailure = { backupInspectFail },
                    )
                    snackbarHost.showReplacingSnackbar(msg)
                }
            },
        )
    }
}

@Composable
private fun BackupSectionsDialog(
    title: String,
    subtitle: String?,
    availableSections: List<BackupSection>,
    selected: Set<BackupSection>,
    confirmLabel: String,
    onToggle: (BackupSection, Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                availableSections.forEach { section ->
                    val labelRes = when (section) {
                        BackupSection.ALLOWLIST -> R.string.backup_section_allowlist
                        BackupSection.SUSFS -> R.string.backup_section_susfs
                        BackupSection.NOMOUNT -> R.string.backup_section_nomount
                        BackupSection.APPEARANCE -> R.string.backup_section_appearance
                    }
                    val checked = section in selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onToggle(section, !checked) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { isChecked -> onToggle(section, isChecked) },
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
                enabled = selected.isNotEmpty(),
                onClick = onConfirm,
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
