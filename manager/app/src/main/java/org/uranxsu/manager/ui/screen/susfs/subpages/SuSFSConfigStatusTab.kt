package org.uranxsu.manager.ui.screen.susfs.subpages

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.Article
import androidx.compose.material.icons.twotone.AutoMode
import androidx.compose.material.icons.twotone.Bolt
import androidx.compose.material.icons.twotone.Build
import androidx.compose.material.icons.twotone.ErrorOutline
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.Memory
import androidx.compose.material.icons.twotone.PlayArrow
import androidx.compose.material.icons.twotone.Policy
import androidx.compose.material.icons.twotone.RestartAlt
import androidx.compose.material.icons.twotone.Restore
import androidx.compose.material.icons.twotone.Save
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material.icons.twotone.TaskAlt
import androidx.compose.material.icons.twotone.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.uranxsu.manager.R
import org.uranxsu.manager.domain.model.SuSFSLkmConfig
import org.uranxsu.manager.domain.model.SuSFSLkmStatus
import org.uranxsu.manager.domain.model.SuSFSStatusInfo
import org.uranxsu.manager.ui.component.WarningCard
import org.uranxsu.manager.ui.component.rememberLoadingDialog
import org.uranxsu.manager.ui.component.settings.SegmentedColumn
import org.uranxsu.manager.ui.component.settings.SettingsBaseWidget
import org.uranxsu.manager.ui.component.settings.SettingsDropdownWidget
import org.uranxsu.manager.ui.component.settings.SettingsJumpPageWidget
import org.uranxsu.manager.ui.component.settings.SettingsSwitchWidget
import org.uranxsu.manager.ui.screen.susfs.RegisterSuSFSRefresh
import org.uranxsu.manager.ui.screen.susfs.SuSFSRefreshRegistrar
import org.uranxsu.manager.ui.util.LocalSnackbarHost
import org.uranxsu.manager.ui.util.showReplacingSnackbar
import org.uranxsu.manager.ui.viewmodel.SuSFSUiAction
import org.uranxsu.manager.ui.viewmodel.SuSFSViewModel
import org.uranxsu.manager.ui.viewmodel.awaitSuSFSBoolean
import org.uranxsu.manager.ui.viewmodel.awaitSuSFSLkmStatus
import org.uranxsu.manager.ui.viewmodel.awaitSuSFSStatusInfo
import org.uranxsu.manager.ui.viewmodel.awaitSuSFSString

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StatusTab(
    nestedScrollConnection: NestedScrollConnection,
    innerPadding: PaddingValues,
    onRegisterRefresh: SuSFSRefreshRegistrar,
    configEnabled: Boolean,
    configEnabledLoaded: Boolean,
    onConfigEnabledChange: (Boolean) -> Unit,
    onConfigRestored: () -> Unit,
) {
    val configHelper = koinViewModel<SuSFSViewModel>()
    var statusInfo by remember { mutableStateOf(SuSFSStatusInfo("", "", "")) }
    var lkmStatus by remember { mutableStateOf(SuSFSLkmStatus()) }

    RegisterSuSFSRefresh(onRegisterRefresh) { _, forceRefresh ->
        statusInfo = awaitSuSFSStatusInfo(configHelper, forceRefresh)
            ?: SuSFSStatusInfo("", "", "")
        lkmStatus = awaitSuSFSLkmStatus(configHelper, forceRefresh)
            ?: SuSFSLkmStatus()
    }

    val uranxsuLkmBase = stringResource(R.string.susfs_backend_uranxsu_lkm)
    val backendLabel = when (lkmStatus.source) {
        "builtin" -> stringResource(R.string.susfs_backend_builtin)
        "uranxsu_lkm" -> if (lkmStatus.activeKmi.isNotBlank()) "$uranxsuLkmBase (${lkmStatus.activeKmi})" else uranxsuLkmBase
        "external_lkm" -> stringResource(R.string.susfs_backend_external_lkm)
        else -> stringResource(R.string.susfs_backend_inactive)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),
    ) {
        item {
            Spacer(Modifier.height(innerPadding.calculateTopPadding()))
        }

        item {
            AnimatedVisibility(
                visible = configEnabledLoaded && !configEnabled,
            ) {
                WarningCard(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    message = stringResource(R.string.susfs_config_disable_warning),
                )
            }
        }

        item {
            SegmentedColumn {
                item {
                    SettingsSwitchWidget(
                        icon = Icons.TwoTone.Save,
                        title = stringResource(R.string.susfs_enable_config),
                        description = stringResource(R.string.susfs_enable_config_summary),
                        checked = configEnabled,
                        enabled = configEnabledLoaded,
                        onCheckedChange = onConfigEnabledChange,
                    )
                }
                item {
                    SettingsBaseWidget(
                        icon = Icons.TwoTone.Memory,
                        title = stringResource(R.string.susfs_backend_source),
                        description = backendLabel,
                    )
                }
                item {
                    SettingsBaseWidget(
                        icon = Icons.TwoTone.Info,
                        title = stringResource(R.string.susfs_status_version),
                        description = statusInfo.version.ifBlank {
                            stringResource(R.string.susfs_status_no_data)
                        },
                    )
                }
                item {
                    SettingsBaseWidget(
                        icon = Icons.TwoTone.Settings,
                        title = stringResource(R.string.susfs_status_variant),
                        description = statusInfo.variant.ifBlank {
                            stringResource(R.string.susfs_status_no_data)
                        },
                    )
                }
                item {
                    SettingsBaseWidget(
                        icon = Icons.TwoTone.TaskAlt,
                        title = stringResource(R.string.susfs_status_enabled_features),
                        description = statusInfo.enabledFeatures.ifBlank {
                            stringResource(R.string.susfs_status_no_data)
                        },
                    )
                }
            }
        }

        item {
            BuiltinLkmSection(
                lkmStatus = lkmStatus,
                onStatusUpdated = { updated ->
                    lkmStatus = updated
                },
                onReloadAll = onConfigRestored,
            )
        }

        item {
            BackupRestoreSection(onConfigRestored = onConfigRestored)
        }

        item {
            Spacer(Modifier.height(innerPadding.calculateBottomPadding()))
        }
    }
}

@Composable
private fun BuiltinLkmSection(
    lkmStatus: SuSFSLkmStatus,
    onStatusUpdated: (SuSFSLkmStatus) -> Unit,
    onReloadAll: () -> Unit,
) {
    val configHelper = koinViewModel<SuSFSViewModel>()
    val snackbarHost = LocalSnackbarHost.current
    val loadingDialog = rememberLoadingDialog()
    val scope = rememberCoroutineScope()

    var showContextDialog by remember { mutableStateOf(false) }
    var suCtxInput by remember { mutableStateOf("") }
    var avcSuCtxInput by remember { mutableStateOf("") }
    var showLogDialog by remember { mutableStateOf(false) }
    var logContent by remember { mutableStateOf("") }

    val operationSuccessMsg = stringResource(R.string.susfs_operation_success)
    val operationFailedMsg = stringResource(R.string.susfs_operation_failed)
    val lkmLoadedMsg = stringResource(R.string.susfs_lkm_load_success)
    val lkmLoadFailedMsg = stringResource(R.string.susfs_lkm_load_failed)
    val autoKmiLabel = if (lkmStatus.detectedKmi.isNotBlank()) {
        stringResource(R.string.susfs_lkm_kmi_auto_detected, lkmStatus.detectedKmi)
    } else {
        stringResource(R.string.susfs_lkm_kmi_auto)
    }

    val kmiOptions = remember(lkmStatus.availableKmis, autoKmiLabel) {
        listOf(autoKmiLabel) + lkmStatus.availableKmis
    }
    val selectedKmiIndex = remember(lkmStatus.config.kmiOverride, lkmStatus.availableKmis) {
        val idx = lkmStatus.availableKmis.indexOf(lkmStatus.config.kmiOverride)
        if (idx >= 0) idx + 1 else 0
    }

    fun updateLkmConfig(transform: (SuSFSLkmConfig) -> SuSFSLkmConfig) {
        val updatedConfig = transform(lkmStatus.config)
        scope.launch {
            val ok = awaitSuSFSBoolean(configHelper) { reply ->
                SuSFSUiAction.SetLkmConfig(updatedConfig, reply)
            }
            if (ok) {
                onStatusUpdated(lkmStatus.copy(config = updatedConfig))
                snackbarHost.showReplacingSnackbar(operationSuccessMsg)
            } else {
                snackbarHost.showReplacingSnackbar(operationFailedMsg)
            }
        }
    }

    val contextSummary = remember(lkmStatus.config.suCtx, lkmStatus.config.avcSuCtx) {
        buildList {
            if (lkmStatus.config.suCtx.isNotBlank()) {
                add("su_ctx=${lkmStatus.config.suCtx}")
            }
            if (lkmStatus.config.avcSuCtx.isNotBlank()) {
                add("avc_su_ctx=${lkmStatus.config.avcSuCtx}")
            }
        }.joinToString(", ")
    }

    SegmentedColumn(
        title = stringResource(R.string.susfs_lkm_section_title),
    ) {
        item(visible = lkmStatus.lastError.isNotBlank()) {
            SettingsBaseWidget(
                icon = Icons.TwoTone.ErrorOutline,
                title = stringResource(R.string.susfs_lkm_last_error),
                description = lkmStatus.lastError,
                isError = true,
            )
        }

        item {
            SettingsSwitchWidget(
                icon = Icons.TwoTone.AutoMode,
                title = stringResource(R.string.susfs_lkm_auto_load),
                description = stringResource(R.string.susfs_lkm_auto_load_summary),
                checked = lkmStatus.config.autoLoad,
                onCheckedChange = { checked ->
                    updateLkmConfig { it.copy(autoLoad = checked) }
                },
            )
        }

        item {
            SettingsSwitchWidget(
                icon = Icons.TwoTone.Bolt,
                title = stringResource(R.string.susfs_lkm_early_load),
                description = stringResource(R.string.susfs_lkm_early_load_summary),
                checked = lkmStatus.config.earlyLoad,
                enabled = lkmStatus.config.autoLoad,
                onCheckedChange = { checked ->
                    updateLkmConfig { it.copy(earlyLoad = checked) }
                },
            )
        }

        item {
            SettingsDropdownWidget(
                icon = Icons.TwoTone.Build,
                title = stringResource(R.string.susfs_lkm_kmi_override),
                description = kmiOptions.getOrElse(selectedKmiIndex) { autoKmiLabel },
                choice = selectedKmiIndex,
                data = kmiOptions,
                onChoiceChange = { idx ->
                    val newOverride = if (idx == 0) "" else lkmStatus.availableKmis.getOrElse(idx - 1) { "" }
                    updateLkmConfig { it.copy(kmiOverride = newOverride) }
                },
            )
        }

        item {
            SettingsSwitchWidget(
                icon = Icons.TwoTone.Visibility,
                title = stringResource(R.string.susfs_lkm_expose_proc),
                description = stringResource(R.string.susfs_lkm_expose_proc_summary),
                checked = lkmStatus.config.exposeProc,
                onCheckedChange = { checked ->
                    updateLkmConfig { it.copy(exposeProc = checked) }
                },
            )
        }

        item {
            SettingsJumpPageWidget(
                icon = Icons.TwoTone.Policy,
                title = stringResource(R.string.susfs_lkm_selinux_params),
                description = contextSummary.ifBlank {
                    stringResource(R.string.susfs_lkm_selinux_params_summary)
                },
                onClick = {
                    suCtxInput = lkmStatus.config.suCtx
                    avcSuCtxInput = lkmStatus.config.avcSuCtx
                    showContextDialog = true
                },
            )
        }

        item(visible = lkmStatus.source == "inactive") {
            SettingsJumpPageWidget(
                icon = Icons.TwoTone.PlayArrow,
                title = stringResource(R.string.susfs_lkm_load_now),
                description = stringResource(R.string.susfs_lkm_load_now_summary),
                onClick = {
                    scope.launch {
                        val ok = loadingDialog.withLoading {
                            awaitSuSFSBoolean(configHelper) { reply ->
                                SuSFSUiAction.LoadLkmNow(
                                    kmi = lkmStatus.config.kmiOverride.ifBlank { null },
                                    applyConfig = true,
                                    reply = reply,
                                )
                            }
                        }
                        onReloadAll()
                        snackbarHost.showReplacingSnackbar(
                            if (ok) lkmLoadedMsg else lkmLoadFailedMsg,
                        )
                    }
                },
            )
        }

        item {
            SettingsJumpPageWidget(
                icon = Icons.AutoMirrored.TwoTone.Article,
                title = stringResource(R.string.susfs_lkm_view_log),
                description = stringResource(R.string.susfs_lkm_view_log_summary),
                onClick = {
                    scope.launch {
                        logContent = awaitSuSFSString(configHelper) { reply ->
                            SuSFSUiAction.LoadLkmLog(reply)
                        }
                        showLogDialog = true
                    }
                },
            )
        }
    }

    if (showContextDialog) {
        AlertDialog(
            onDismissRequest = { showContextDialog = false },
            title = { Text(stringResource(R.string.susfs_lkm_selinux_params)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = suCtxInput,
                        onValueChange = { suCtxInput = it },
                        label = { Text(stringResource(R.string.susfs_lkm_su_ctx_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = avcSuCtxInput,
                        onValueChange = { avcSuCtxInput = it },
                        label = { Text(stringResource(R.string.susfs_lkm_avc_su_ctx_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showContextDialog = false
                        updateLkmConfig {
                            it.copy(
                                suCtx = suCtxInput.trim(),
                                avcSuCtx = avcSuCtxInput.trim(),
                            )
                        }
                    },
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showContextDialog = false }) {
                    Text(stringResource(R.string.susfs_entry_cancel))
                }
            },
        )
    }

    if (showLogDialog) {
        AlertDialog(
            onDismissRequest = { showLogDialog = false },
            title = { Text(stringResource(R.string.susfs_lkm_view_log)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = logContent.ifBlank { stringResource(R.string.susfs_lkm_log_empty) },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLogDialog = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreSection(
    onConfigRestored: () -> Unit,
) {
    val configHelper = koinViewModel<SuSFSViewModel>()
    val snackbarHost = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()

    var showImportConfirm by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    val exportTitle = stringResource(R.string.susfs_backup_export)
    val exportDesc = stringResource(R.string.susfs_backup_description)
    val importTitle = stringResource(R.string.susfs_backup_import)
    val importDesc = stringResource(R.string.susfs_restore_description)
    val exportSuccessMsg = stringResource(R.string.susfs_backup_export_success)
    val exportFailedMsg = stringResource(R.string.susfs_backup_export_failed)
    val importSuccessMsg = stringResource(R.string.susfs_backup_import_success)
    val importFailedMsg = stringResource(R.string.susfs_backup_import_failed)
    val restoreDefaultTitle = stringResource(R.string.susfs_backup_restore_default)
    val restoreDefaultDesc = stringResource(R.string.susfs_backup_restore_default_desc)
    val confirmTitle = stringResource(R.string.susfs_backup_import_confirm_title)
    val confirmMsg = stringResource(R.string.susfs_backup_import_confirm_message)
    val importLabel = stringResource(R.string.susfs_backup_import_label)
    val cancelLabel = stringResource(R.string.susfs_entry_cancel)
    val operationSuccessMsg = stringResource(R.string.susfs_operation_success)
    val operationFailedMsg = stringResource(R.string.susfs_operation_failed)

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = awaitSuSFSBoolean(configHelper) { reply ->
                SuSFSUiAction.ExportConfig(uri.toString(), reply)
            }
            scope.launch {
                snackbarHost.showReplacingSnackbar(if (ok) exportSuccessMsg else exportFailedMsg)
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        pendingImportUri = uri
        showImportConfirm = true
    }

    SegmentedColumn {
        item {
            SettingsJumpPageWidget(
                icon = Icons.TwoTone.Save,
                title = exportTitle,
                description = exportDesc,
                onClick = {
                    exportLauncher.launch("susfs_backup.json")
                },
            )
        }
        item {
            SettingsJumpPageWidget(
                icon = Icons.TwoTone.Restore,
                title = importTitle,
                description = importDesc,
                onClick = {
                    importLauncher.launch(arrayOf("application/json"))
                },
            )
        }
        item {
            RestoreDefaultRow(
                title = restoreDefaultTitle,
                description = restoreDefaultDesc,
                snackbarHost = snackbarHost,
                operationSuccessMsg = operationSuccessMsg,
                operationFailedMsg = operationFailedMsg,
                onConfigRestored = onConfigRestored,
            )
        }
    }

    if (showImportConfirm) {
        AlertDialog(
            onDismissRequest = {
                showImportConfirm = false
                pendingImportUri = null
            },
            title = { Text(confirmTitle) },
            text = { Text(confirmMsg) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val uri = pendingImportUri
                        showImportConfirm = false
                        pendingImportUri = null
                        if (uri != null) {
                            scope.launch {
                                val ok = awaitSuSFSBoolean(configHelper) { reply ->
                                    SuSFSUiAction.ImportConfig(uri.toString(), reply)
                                }
                                if (ok) {
                                    onConfigRestored()
                                }
                                scope.launch {
                                    snackbarHost.showReplacingSnackbar(if (ok) importSuccessMsg else importFailedMsg)
                                }
                            }
                        }
                    },
                ) {
                    Text(importLabel)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImportConfirm = false
                    pendingImportUri = null
                }) {
                    Text(cancelLabel)
                }
            },
        )
    }
}

@Composable
private fun RestoreDefaultRow(
    title: String,
    description: String,
    snackbarHost: SnackbarHostState,
    operationSuccessMsg: String,
    operationFailedMsg: String,
    onConfigRestored: () -> Unit,
) {
    val configHelper = koinViewModel<SuSFSViewModel>()
    val scope = rememberCoroutineScope()
    SettingsJumpPageWidget(
        icon = Icons.TwoTone.RestartAlt,
        title = title,
        description = description,
        onClick = {
            scope.launch {
                val ok = awaitSuSFSBoolean(configHelper) { reply ->
                    SuSFSUiAction.RestoreDefault(reply)
                }
                if (ok) {
                    onConfigRestored()
                }
                snackbarHost.showReplacingSnackbar(if (ok) operationSuccessMsg else operationFailedMsg)
            }
        },
    )
}
