package org.uranxsu.manager.ui.screen.mount

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.Article
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material.icons.twotone.AutoMode
import androidx.compose.material.icons.twotone.Build
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.DeleteSweep
import androidx.compose.material.icons.twotone.Extension
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.Memory
import androidx.compose.material.icons.twotone.PersonOff
import androidx.compose.material.icons.twotone.PlayArrow
import androidx.compose.material.icons.twotone.Refresh
import androidx.compose.material.icons.twotone.Security
import androidx.compose.material.icons.twotone.Shield
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.uranxsu.manager.R
import org.uranxsu.manager.domain.model.NoMountAppCandidate
import org.uranxsu.manager.domain.model.NoMountConfig
import org.uranxsu.manager.ui.component.ConfirmResult
import org.uranxsu.manager.ui.component.SwipeableSnackbarHost
import org.uranxsu.manager.ui.component.rememberConfirmDialog
import org.uranxsu.manager.ui.component.rememberLoadingDialog
import org.uranxsu.manager.ui.component.settings.AppBackButton
import org.uranxsu.manager.ui.component.settings.SegmentedColumn
import org.uranxsu.manager.ui.component.settings.SettingsBaseWidget
import org.uranxsu.manager.ui.component.settings.SettingsDropdownWidget
import org.uranxsu.manager.ui.component.settings.SettingsJumpPageWidget
import org.uranxsu.manager.ui.component.settings.SettingsSwitchWidget
import org.uranxsu.manager.ui.component.settings.lazySegmentColumn
import org.uranxsu.manager.ui.navigation.LocalNavigator
import org.uranxsu.manager.ui.theme.CardConfig
import org.uranxsu.manager.ui.theme.ThemeConfig
import org.uranxsu.manager.ui.theme.blurEffect
import org.uranxsu.manager.ui.theme.blurSource
import org.uranxsu.manager.ui.util.ActivityResumeEffect
import org.uranxsu.manager.ui.util.LocalSnackbarHost
import org.uranxsu.manager.ui.util.adaptiveScaffoldWindowInsets
import org.uranxsu.manager.ui.util.showReplacingSnackbar
import org.uranxsu.manager.ui.viewmodel.NoMountViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NoMountManagerScreen() {
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val viewModel = koinViewModel<NoMountViewModel>()
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val snackbarHost = LocalSnackbarHost.current
    val loadingDialog = rememberLoadingDialog()
    val confirmDialog = rememberConfirmDialog()
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val pullRefreshState = rememberPullToRefreshState()

    var showRulesDialog by remember { mutableStateOf(false) }
    var rulesText by remember { mutableStateOf("") }
    var showAddUidDialog by remember { mutableStateOf(false) }
    var appSearchQuery by remember { mutableStateOf("") }

    ActivityResumeEffect(viewModel) {
        viewModel.refresh()
    }

    val status = uiState.status
    val opSuccessMsg = stringResource(R.string.mount_operation_success)
    val opFailedMsg = stringResource(R.string.mount_operation_failed)
    val clearConfirmTitle = stringResource(R.string.nomount_clear_rules)
    val clearConfirmBody = stringResource(R.string.nomount_clear_rules_confirm)
    val modulesSectionTitle = stringResource(R.string.nomount_modules_section)

    val autoKmiLabel = if (status.detectedKmi.isNotBlank()) {
        stringResource(R.string.susfs_lkm_kmi_auto_detected, status.detectedKmi)
    } else {
        stringResource(R.string.susfs_lkm_kmi_auto)
    }
    val kmiOptions = remember(status.availableKmis, autoKmiLabel) {
        listOf(autoKmiLabel) + status.availableKmis
    }
    val selectedKmiIndex = remember(status.config.kmiOverride, status.availableKmis) {
        val idx = status.availableKmis.indexOf(status.config.kmiOverride)
        if (idx >= 0) idx + 1 else 0
    }

    fun updateNoMountConfig(
        clearBootloop: Boolean = false,
        transform: (NoMountConfig) -> NoMountConfig,
    ) {
        val updated = transform(status.config)
        scope.launch {
            val ok = viewModel.updateConfig(updated, clearBootloop)
            snackbarHost.showReplacingSnackbar(if (ok) opSuccessMsg else opFailedMsg)
        }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                modifier = Modifier.blurEffect(),
                title = { Text(stringResource(R.string.nomount_manager_title)) },
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
                item {
                    SegmentedColumn(
                        title = stringResource(R.string.nomount_status_section),
                    ) {
                        item {
                            SettingsBaseWidget(
                                icon = Icons.TwoTone.Memory,
                                title = stringResource(R.string.nomount_driver_status),
                                description = if (status.driverReady) {
                                    val kmiSuffix = if (status.activeKmi.isNotBlank()) " (${status.activeKmi})" else ""
                                    "${status.driverVersion}$kmiSuffix"
                                } else {
                                    stringResource(R.string.nomount_driver_inactive)
                                },
                            )
                        }

                        item {
                            SettingsBaseWidget(
                                icon = Icons.TwoTone.Info,
                                title = stringResource(R.string.nomount_active_stats),
                                description = stringResource(
                                    R.string.nomount_active_stats_value,
                                    status.ruleCount,
                                    status.uidCount,
                                ),
                            )
                        }

                        item(visible = status.bootloopDisabled) {
                            SettingsJumpPageWidget(
                                icon = Icons.TwoTone.Warning,
                                title = stringResource(R.string.nomount_bootloop_triggered),
                                description = stringResource(R.string.nomount_bootloop_clear_hint),
                                isError = true,
                                onClick = {
                                    updateNoMountConfig(clearBootloop = true) { it }
                                },
                            )
                        }
                    }
                }

                item {
                    SegmentedColumn(
                        title = stringResource(R.string.nomount_settings_section),
                    ) {
                        item {
                            SettingsSwitchWidget(
                                icon = Icons.TwoTone.AutoMode,
                                title = stringResource(R.string.nomount_auto_load_lkm),
                                description = stringResource(R.string.nomount_auto_load_lkm_summary),
                                checked = status.config.autoLoadLkm,
                                onCheckedChange = { checked ->
                                    updateNoMountConfig { it.copy(autoLoadLkm = checked) }
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
                                    val override = if (idx == 0) "" else status.availableKmis.getOrElse(idx - 1) { "" }
                                    updateNoMountConfig { it.copy(kmiOverride = override) }
                                },
                            )
                        }

                        item {
                            SettingsSwitchWidget(
                                icon = Icons.TwoTone.Shield,
                                title = stringResource(R.string.nomount_block_isolated),
                                description = stringResource(R.string.nomount_block_isolated_summary),
                                checked = status.blockIsolated,
                                onCheckedChange = { checked ->
                                    updateNoMountConfig { it.copy(blockIsolatedUids = checked) }
                                },
                            )
                        }

                        item {
                            SettingsSwitchWidget(
                                icon = Icons.TwoTone.Security,
                                title = stringResource(R.string.nomount_bootloop_guard),
                                description = stringResource(R.string.nomount_bootloop_guard_summary),
                                checked = status.config.bootloopGuard,
                                onCheckedChange = { checked ->
                                    updateNoMountConfig { it.copy(bootloopGuard = checked) }
                                },
                            )
                        }

                        item {
                            SettingsSwitchWidget(
                                icon = Icons.AutoMirrored.TwoTone.Article,
                                title = stringResource(R.string.nomount_dump_rules_boot),
                                description = stringResource(R.string.nomount_dump_rules_boot_summary),
                                checked = status.config.dumpRulesOnBoot,
                                onCheckedChange = { checked ->
                                    updateNoMountConfig { it.copy(dumpRulesOnBoot = checked) }
                                },
                            )
                        }
                    }
                }

                item {
                    SegmentedColumn(
                        title = stringResource(R.string.nomount_actions_section),
                    ) {
                        item(visible = !status.driverReady) {
                            SettingsJumpPageWidget(
                                icon = Icons.TwoTone.PlayArrow,
                                title = stringResource(R.string.nomount_load_driver_now),
                                description = stringResource(R.string.nomount_load_driver_now_summary),
                                onClick = {
                                    scope.launch {
                                        val ok = loadingDialog.withLoading {
                                            viewModel.loadLkm(status.config.kmiOverride.ifBlank { null })
                                        }
                                        snackbarHost.showReplacingSnackbar(
                                            if (ok) opSuccessMsg else opFailedMsg,
                                        )
                                    }
                                },
                            )
                        }

                        item {
                            SettingsJumpPageWidget(
                                icon = Icons.TwoTone.Refresh,
                                title = stringResource(R.string.nomount_reload_all),
                                description = stringResource(R.string.nomount_reload_all_summary),
                                onClick = {
                                    scope.launch {
                                        val ok = loadingDialog.withLoading {
                                            viewModel.reloadAll()
                                        }
                                        snackbarHost.showReplacingSnackbar(
                                            if (ok) opSuccessMsg else opFailedMsg,
                                        )
                                    }
                                },
                            )
                        }

                        item {
                            SettingsJumpPageWidget(
                                icon = Icons.AutoMirrored.TwoTone.Article,
                                title = stringResource(R.string.nomount_view_rules),
                                description = stringResource(R.string.nomount_view_rules_summary),
                                onClick = {
                                    scope.launch {
                                        rulesText = loadingDialog.withLoading {
                                            viewModel.loadRulesJson()
                                        }
                                        showRulesDialog = true
                                    }
                                },
                            )
                        }

                        item {
                            SettingsJumpPageWidget(
                                icon = Icons.TwoTone.DeleteSweep,
                                title = stringResource(R.string.nomount_clear_rules),
                                description = stringResource(R.string.nomount_clear_rules_summary),
                                onClick = {
                                    scope.launch {
                                        val res = confirmDialog.awaitConfirm(
                                            title = clearConfirmTitle,
                                            content = clearConfirmBody,
                                        )
                                        if (res == ConfirmResult.Confirmed) {
                                            val ok = viewModel.clearRules()
                                            snackbarHost.showReplacingSnackbar(
                                                if (ok) opSuccessMsg else opFailedMsg,
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    }
                }

                if (status.modules.isNotEmpty()) {
                    lazySegmentColumn(
                        items = status.modules,
                        title = modulesSectionTitle,
                        key = { _, item -> "nm_mod_${item.id}" },
                    ) { _, mod ->
                        val active = !mod.skipped && mod.enabled
                        val desc = buildString {
                            append(mod.id)
                            if (mod.partitions.isNotEmpty()) {
                                append(" · ")
                                append(mod.partitions.joinToString(", "))
                            }
                            if (mod.injectedFiles > 0) {
                                append(" (")
                                append(mod.injectedFiles)
                                append(")")
                            }
                        }
                        SettingsSwitchWidget(
                            icon = Icons.TwoTone.Extension,
                            title = mod.name,
                            description = desc,
                            checked = active,
                            enabled = mod.enabled,
                            onCheckedChange = { checked ->
                                scope.launch {
                                    val ok = loadingDialog.withLoading {
                                        viewModel.toggleModule(mod.id, checked)
                                    }
                                    snackbarHost.showReplacingSnackbar(
                                        if (ok) opSuccessMsg else opFailedMsg,
                                    )
                                }
                            },
                        )
                    }
                } else {
                    item {
                        SegmentedColumn(title = modulesSectionTitle) {
                            item {
                                SettingsBaseWidget(
                                    icon = Icons.TwoTone.Extension,
                                    title = stringResource(R.string.mount_no_mountable_modules),
                                    description = stringResource(R.string.mount_no_mountable_modules_summary),
                                )
                            }
                        }
                    }
                }

                item {
                    SegmentedColumn(
                        title = stringResource(R.string.nomount_exclusions_section),
                    ) {
                        item {
                            SettingsJumpPageWidget(
                                icon = Icons.TwoTone.Add,
                                title = stringResource(R.string.nomount_add_exclusion),
                                description = stringResource(R.string.nomount_add_exclusion_summary),
                                onClick = {
                                    appSearchQuery = ""
                                    viewModel.loadApps()
                                    showAddUidDialog = true
                                },
                            )
                        }
                    }
                }

                if (status.exclusions.isNotEmpty()) {
                    lazySegmentColumn(
                        items = status.exclusions,
                        key = { _, item -> "nm_uid_${item.uid}" },
                    ) { _, entry ->
                        SettingsBaseWidget(
                            icon = Icons.TwoTone.PersonOff,
                            title = entry.pkg.ifBlank { "UID ${entry.uid}" },
                            description = "UID ${entry.uid}",
                            trailingContent = {
                                IconButton(
                                    onClick = {
                                        val uid = entry.uid.toIntOrNull() ?: return@IconButton
                                        scope.launch {
                                            val ok = viewModel.removeUid(uid)
                                            snackbarHost.showReplacingSnackbar(
                                                if (ok) opSuccessMsg else opFailedMsg,
                                            )
                                        }
                                    },
                                ) {
                                    Icon(
                                        imageVector = Icons.TwoTone.Delete,
                                        contentDescription = stringResource(R.string.nomount_remove_exclusion),
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (showRulesDialog) {
        AlertDialog(
            onDismissRequest = { showRulesDialog = false },
            title = { Text(stringResource(R.string.nomount_view_rules)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = rulesText.ifBlank { "[]" },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showRulesDialog = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
    }

    if (showAddUidDialog) {
        val excludedUids = remember(status.exclusions) {
            status.exclusions.map { it.uid }.toSet()
        }
        val filteredApps = remember(uiState.apps, excludedUids, appSearchQuery) {
            val q = appSearchQuery.trim()
            val base = if (q.isEmpty()) {
                uiState.apps
            } else {
                uiState.apps.filter {
                    it.label.contains(q, ignoreCase = true) ||
                        it.packageName.contains(q, ignoreCase = true) ||
                        it.uid.toString().contains(q)
                }
            }
            base.sortedWith(
                compareByDescending<NoMountAppCandidate> {
                    excludedUids.contains(it.uid.toString())
                }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.label },
            )
        }
        AlertDialog(
            onDismissRequest = { showAddUidDialog = false },
            title = { Text(stringResource(R.string.nomount_add_exclusion)) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = appSearchQuery,
                        onValueChange = { appSearchQuery = it },
                        label = { Text(stringResource(R.string.susfs_uid_filter_search)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                    ) {
                        items(filteredApps, key = { it.uid }) { app ->
                            val isExcluded = excludedUids.contains(app.uid.toString())
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        scope.launch {
                                            val ok = if (isExcluded) {
                                                viewModel.removeUid(app.uid)
                                            } else {
                                                viewModel.addUid(app.uid, app.packageName)
                                            }
                                            snackbarHost.showReplacingSnackbar(
                                                if (ok) opSuccessMsg else opFailedMsg,
                                            )
                                        }
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = isExcluded,
                                    onCheckedChange = null,
                                )
                                Column(modifier = Modifier.padding(start = 8.dp)) {
                                    Text(
                                        text = app.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        text = "${app.packageName} (UID ${app.uid})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddUidDialog = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
    }
}
