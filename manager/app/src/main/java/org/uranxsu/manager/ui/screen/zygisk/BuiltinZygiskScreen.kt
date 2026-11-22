package org.uranxsu.manager.ui.screen.zygisk

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Build
import androidx.compose.material.icons.twotone.Extension
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.Link
import androidx.compose.material.icons.twotone.Memory
import androidx.compose.material.icons.twotone.PowerSettingsNew
import androidx.compose.material.icons.twotone.Refresh
import androidx.compose.material.icons.twotone.RestartAlt
import androidx.compose.material.icons.twotone.Science
import androidx.compose.material.icons.twotone.SwapHoriz
import androidx.compose.material.icons.twotone.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.uranxsu.manager.R
import org.uranxsu.manager.ui.component.ConfirmResult
import org.uranxsu.manager.ui.component.SwipeableSnackbarHost
import org.uranxsu.manager.ui.component.rememberConfirmDialog
import org.uranxsu.manager.ui.component.rememberLoadingDialog
import org.uranxsu.manager.ui.component.settings.AppBackButton
import org.uranxsu.manager.ui.component.settings.SegmentedColumn
import org.uranxsu.manager.ui.component.settings.SettingsBaseWidget
import org.uranxsu.manager.ui.component.settings.SettingsChooseWidget
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
import org.uranxsu.manager.ui.viewmodel.BuiltinZygiskViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BuiltinZygiskScreen() {
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val viewModel = koinViewModel<BuiltinZygiskViewModel>()
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val snackbarHost = LocalSnackbarHost.current
    val loadingDialog = rememberLoadingDialog()
    val confirmDialog = rememberConfirmDialog()
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val pullRefreshState = rememberPullToRefreshState()

    ActivityResumeEffect(viewModel) {
        viewModel.refresh()
    }

    val status = uiState.status
    val info = uiState.info
    val config = uiState.config
    val opSuccessMsg = stringResource(R.string.mount_operation_success)
    val opFailedMsg = stringResource(R.string.mount_operation_failed)
    val softRebootTitle = stringResource(R.string.reboot_soft)
    val softRebootConfirm = stringResource(R.string.zygisk_soft_reboot_confirm)
    val builtinLabel = stringResource(R.string.zygisk_provider_builtin)
    val modulesSectionTitle = stringResource(R.string.zygisk_modules_section)

    val stateLabel = when {
        status.fullyActive -> stringResource(R.string.zygisk_state_fully_active)
        status.monitorRunning || status.daemonRunning -> stringResource(R.string.zygisk_state_partial)
        status.enabled -> stringResource(R.string.zygisk_state_reboot_required)
        info.state == "running" -> "${info.name} ${info.version}".trim()
        else -> stringResource(R.string.zygisk_state_disabled)
    }

    val zygoteSummary = remember(status.zygotes) {
        status.zygotes.joinToString(", ") { "zygote${it.abi}: ${it.status}" }
    }

    val processSummary = remember(status.monitor, status.daemons) {
        buildList {
            status.monitor?.let { add("${it.name} (PID ${it.pid})") }
            status.daemons.forEach { add("${it.name} (PID ${it.pid})") }
        }.joinToString(", ")
    }

    val providerTargets = remember(info.providers) {
        (listOf("builtin") + info.providers).distinct()
    }
    val providerLabels = remember(providerTargets, builtinLabel) {
        providerTargets.map { if (it == "builtin") builtinLabel else it }
    }
    val selectedProviderIdx = remember(info.id, providerTargets) {
        providerTargets.indexOf(info.id).coerceAtLeast(0)
    }

    val pltEngines = remember { listOf("bytehook", "lsplt") }
    val pltLabels = remember { listOf("ByteHook", "LSPlt") }
    val selectedPltIdx = pltEngines.indexOf(config.pltEngine).coerceAtLeast(0)

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                modifier = Modifier.blurEffect(),
                title = { Text(stringResource(R.string.builtin_zygisk_title)) },
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
                        title = stringResource(R.string.zygisk_status_section),
                    ) {
                        item {
                            SettingsSwitchWidget(
                                icon = Icons.TwoTone.PowerSettingsNew,
                                title = stringResource(R.string.zygisk_enable_builtin),
                                description = stringResource(R.string.zygisk_enable_builtin_summary),
                                checked = status.enabled,
                                onCheckedChange = { checked ->
                                    scope.launch {
                                        val ok = loadingDialog.withLoading {
                                            viewModel.setEnabled(checked)
                                        }
                                        snackbarHost.showReplacingSnackbar(
                                            if (ok) opSuccessMsg else opFailedMsg,
                                        )
                                    }
                                },
                            )
                        }

                        item {
                            SettingsBaseWidget(
                                icon = Icons.TwoTone.Science,
                                title = stringResource(R.string.zygisk_runtime_state),
                                description = if (status.version.isNotBlank()) {
                                    "$stateLabel · NyaZygisk ${status.version}"
                                } else {
                                    stateLabel
                                },
                            )
                        }

                        item(visible = zygoteSummary.isNotBlank()) {
                            SettingsBaseWidget(
                                icon = Icons.TwoTone.Memory,
                                title = stringResource(R.string.zygisk_zygotes_status),
                                description = zygoteSummary,
                            )
                        }

                        item(visible = processSummary.isNotBlank()) {
                            SettingsBaseWidget(
                                icon = Icons.TwoTone.Info,
                                title = stringResource(R.string.zygisk_processes_title),
                                description = processSummary,
                            )
                        }

                        item(visible = info.providers.isNotEmpty()) {
                            SettingsDropdownWidget(
                                icon = Icons.TwoTone.SwapHoriz,
                                title = stringResource(R.string.zygisk_switch_provider),
                                description = providerLabels.getOrElse(selectedProviderIdx) { builtinLabel },
                                choice = selectedProviderIdx,
                                data = providerLabels,
                                onChoiceChange = { idx ->
                                    val target = providerTargets.getOrElse(idx) { "builtin" }
                                    scope.launch {
                                        val ok = loadingDialog.withLoading {
                                            viewModel.switchProvider(target)
                                        }
                                        snackbarHost.showReplacingSnackbar(
                                            if (ok) opSuccessMsg else opFailedMsg,
                                        )
                                    }
                                },
                            )
                        }
                    }
                }

                item {
                    SegmentedColumn(
                        title = stringResource(R.string.zygisk_settings_section),
                    ) {
                        item {
                            SettingsSwitchWidget(
                                icon = Icons.TwoTone.VisibilityOff,
                                title = stringResource(R.string.zygisk_anonymous_memory),
                                description = stringResource(R.string.zygisk_anonymous_memory_summary),
                                checked = config.anonymousMemory,
                                onCheckedChange = { checked ->
                                    scope.launch {
                                        val ok = viewModel.setConfig(
                                            "anonymous_memory",
                                            if (checked) "1" else "0",
                                        )
                                        snackbarHost.showReplacingSnackbar(
                                            if (ok) opSuccessMsg else opFailedMsg,
                                        )
                                    }
                                },
                            )
                        }

                        item {
                            SettingsSwitchWidget(
                                icon = Icons.TwoTone.Link,
                                title = stringResource(R.string.zygisk_custom_linker),
                                description = stringResource(R.string.zygisk_custom_linker_summary),
                                checked = config.customLinker,
                                onCheckedChange = { checked ->
                                    scope.launch {
                                        val ok = viewModel.setConfig(
                                            "custom_linker",
                                            if (checked) "1" else "0",
                                        )
                                        snackbarHost.showReplacingSnackbar(
                                            if (ok) opSuccessMsg else opFailedMsg,
                                        )
                                    }
                                },
                            )
                        }

                        item {
                            SettingsChooseWidget(
                                icon = Icons.TwoTone.Build,
                                title = stringResource(R.string.zygisk_plt_engine),
                                description = stringResource(R.string.zygisk_plt_engine_summary),
                                items = pltLabels,
                                selectedIndex = selectedPltIdx,
                                onSelectedIndexChange = { idx ->
                                    val engine = pltEngines.getOrElse(idx) { "bytehook" }
                                    scope.launch {
                                        val ok = viewModel.setConfig("plt_engine", engine)
                                        snackbarHost.showReplacingSnackbar(
                                            if (ok) opSuccessMsg else opFailedMsg,
                                        )
                                    }
                                },
                            )
                        }
                    }
                }

                item {
                    SegmentedColumn(
                        title = stringResource(R.string.nomount_actions_section),
                    ) {
                        item(visible = status.enabled) {
                            SettingsJumpPageWidget(
                                icon = Icons.TwoTone.Refresh,
                                title = stringResource(R.string.zygisk_rearm_title),
                                description = stringResource(R.string.zygisk_rearm_summary),
                                onClick = {
                                    scope.launch {
                                        val ok = loadingDialog.withLoading {
                                            viewModel.rearm()
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
                                icon = Icons.TwoTone.RestartAlt,
                                title = softRebootTitle,
                                description = stringResource(R.string.zygisk_soft_reboot_summary),
                                onClick = {
                                    scope.launch {
                                        val res = confirmDialog.awaitConfirm(
                                            title = softRebootTitle,
                                            content = softRebootConfirm,
                                        )
                                        if (res == ConfirmResult.Confirmed) {
                                            viewModel.softReboot()
                                        }
                                    }
                                },
                            )
                        }
                    }
                }

                if (uiState.modules.isNotEmpty()) {
                    lazySegmentColumn(
                        items = uiState.modules,
                        title = modulesSectionTitle,
                        key = { _, item -> "zygisk_mod_${item.id}" },
                    ) { _, mod ->
                        val abis = if (mod.abi.isNotEmpty()) mod.abi.joinToString("/") { "${it}bit" } else "-"
                        val procs = if (mod.processes.isNotEmpty()) {
                            " · " + mod.processes.joinToString(", ") { "${it.name} (${it.pid})" }
                        } else {
                            ""
                        }
                        SettingsBaseWidget(
                            icon = Icons.TwoTone.Extension,
                            title = if (mod.version.isNotBlank()) "${mod.name} ${mod.version}" else mod.name,
                            description = "${mod.state} · ${mod.type} · $abis$procs",
                        )
                    }
                }
            }
        }
    }
}
