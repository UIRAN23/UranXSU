package org.uranxsu.manager.ui.screen.noshot

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.Article
import androidx.compose.material.icons.twotone.AdminPanelSettings
import androidx.compose.material.icons.twotone.Apps
import androidx.compose.material.icons.twotone.Clear
import androidx.compose.material.icons.twotone.LayersClear
import androidx.compose.material.icons.twotone.ScreenshotMonitor
import androidx.compose.material.icons.twotone.Search
import androidx.compose.material.icons.twotone.Security
import androidx.compose.material.icons.twotone.Shield
import androidx.compose.material.icons.twotone.Sync
import androidx.compose.material.icons.twotone.VerifiedUser
import androidx.compose.material.icons.twotone.VisibilityOff
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.uranxsu.manager.R
import org.uranxsu.manager.ui.component.PackageIcon
import org.uranxsu.manager.ui.component.SwipeableSnackbarHost
import org.uranxsu.manager.ui.component.rememberLoadingDialog
import org.uranxsu.manager.ui.component.settings.AppBackButton
import org.uranxsu.manager.ui.component.settings.SegmentedColumn
import org.uranxsu.manager.ui.component.settings.SettingsBaseWidget
import org.uranxsu.manager.ui.component.settings.SettingsChooseWidget
import org.uranxsu.manager.ui.component.settings.SettingsJumpPageWidget
import org.uranxsu.manager.ui.component.settings.SettingsSwitchWidget
import org.uranxsu.manager.ui.component.settings.lazySegmentColumn
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
import org.uranxsu.manager.ui.viewmodel.NoShotViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NoShotScreen() {
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val viewModel = koinViewModel<NoShotViewModel>()
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val snackbarHost = LocalSnackbarHost.current
    val loadingDialog = rememberLoadingDialog()
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val pullRefreshState = rememberPullToRefreshState()

    var showStateDialog by remember { mutableStateOf(false) }

    ActivityResumeEffect(viewModel) {
        viewModel.refresh()
    }

    val status = uiState.status
    val settings = status.settings
    val okMsg = stringResource(R.string.noshot_op_success)
    val failMsg = stringResource(R.string.noshot_op_failed)

    val captureModes = remember { listOf("off", "observe", "on") }
    val captureModeLabels = listOf(
        stringResource(R.string.noshot_capture_guard_off),
        stringResource(R.string.noshot_capture_guard_observe),
        stringResource(R.string.noshot_capture_guard_on),
    )
    val selectedCaptureIdx = captureModes.indexOf(settings.captureGuard.lowercase()).coerceAtLeast(0)

    val enabledCount = remember(status.targets) {
        status.targets.distinctBy { it.packageName }.size
    }

    val filteredApps = remember(uiState.apps, uiState.searchQuery, uiState.showSystemApps) {
        val q = uiState.searchQuery.trim()
        uiState.apps.filter { candidate ->
            val visibleByType = uiState.showSystemApps || !candidate.isSystem || candidate.enabled
            val matchesQuery = q.isEmpty() ||
                candidate.appLabel.contains(q, ignoreCase = true) ||
                candidate.packageName.contains(q, ignoreCase = true)
            visibleByType && matchesQuery
        }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                modifier = Modifier.blurEffect(),
                title = { Text(customText(CustomTextKey.TOOL_NOSHOT)) },
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
                item(key = "status_section") {
                    SegmentedColumn(title = stringResource(R.string.noshot_status_section)) {
                        item {
                            SettingsSwitchWidget(
                                icon = Icons.TwoTone.ScreenshotMonitor,
                                title = stringResource(R.string.noshot_enable_title),
                                description = stringResource(R.string.noshot_enable_summary),
                                enabled = !status.standaloneConflict,
                                checked = status.enabled,
                                onCheckedChange = { checked ->
                                    scope.launch {
                                        val ok = loadingDialog.withLoading { viewModel.toggleEnabled(checked) }
                                        snackbarHost.showReplacingSnackbar(if (ok) okMsg else failMsg)
                                    }
                                },
                            )
                        }

                        item(visible = status.standaloneConflict) {
                            SettingsBaseWidget(
                                icon = Icons.TwoTone.Warning,
                                title = stringResource(R.string.noshot_standalone_conflict),
                                isError = true,
                            )
                        }

                        item(visible = !status.zygiskEnabled) {
                            SettingsJumpPageWidget(
                                icon = Icons.TwoTone.Warning,
                                title = stringResource(R.string.noshot_zygisk_required),
                                isError = true,
                                onClick = { navigator.push(Route.BuiltinZygisk) },
                            )
                        }

                        item(visible = status.bootloopDisabled) {
                            SettingsJumpPageWidget(
                                icon = Icons.TwoTone.Warning,
                                title = stringResource(R.string.noshot_bootloop_warning, status.attemptCount),
                                isError = true,
                                onClick = {
                                    scope.launch {
                                        val ok = loadingDialog.withLoading { viewModel.resetGuard() }
                                        snackbarHost.showReplacingSnackbar(if (ok) okMsg else failMsg)
                                    }
                                },
                            )
                        }

                        item {
                            SettingsBaseWidget(
                                icon = Icons.TwoTone.AdminPanelSettings,
                                title = stringResource(R.string.noshot_runtime_mode),
                                description = if (status.runtimeMode == "system") {
                                    stringResource(R.string.noshot_mode_system)
                                } else {
                                    stringResource(R.string.noshot_mode_client)
                                },
                            )
                        }

                        item {
                            SettingsBaseWidget(
                                icon = Icons.TwoTone.VerifiedUser,
                                title = stringResource(R.string.noshot_zygisk_loaded),
                                description = stringResource(
                                    if (status.loadedInZygisk) {
                                        R.string.noshot_zygisk_loaded_yes
                                    } else {
                                        R.string.noshot_zygisk_loaded_no
                                    },
                                ),
                            )
                        }

                        item {
                            SettingsBaseWidget(
                                icon = Icons.TwoTone.Security,
                                title = stringResource(R.string.noshot_capture_guard_live),
                                description = status.captureGuardState,
                            )
                        }

                        item(visible = status.enabled) {
                            SettingsJumpPageWidget(
                                icon = Icons.TwoTone.Sync,
                                title = stringResource(R.string.noshot_sync_plan),
                                description = stringResource(R.string.noshot_sync_plan_summary),
                                onClick = {
                                    scope.launch {
                                        val ok = viewModel.syncPlan()
                                        snackbarHost.showReplacingSnackbar(if (ok) okMsg else failMsg)
                                    }
                                },
                            )
                        }

                        item {
                            SettingsJumpPageWidget(
                                icon = Icons.AutoMirrored.TwoTone.Article,
                                title = stringResource(R.string.noshot_view_state),
                                description = stringResource(R.string.noshot_view_state_summary),
                                onClick = { showStateDialog = true },
                            )
                        }
                    }
                }

                item(key = "hooks_section") {
                    SegmentedColumn(title = stringResource(R.string.noshot_hooks_section)) {
                        item {
                            SettingsSwitchWidget(
                                icon = Icons.TwoTone.VisibilityOff,
                                title = stringResource(R.string.noshot_flag_secure),
                                description = stringResource(R.string.noshot_flag_secure_summary),
                                checked = settings.flagSecure,
                                onCheckedChange = { checked ->
                                    scope.launch {
                                        val ok = viewModel.updateSettings { it.copy(flagSecure = checked) }
                                        snackbarHost.showReplacingSnackbar(if (ok) okMsg else failMsg)
                                    }
                                },
                            )
                        }

                        item {
                            SettingsSwitchWidget(
                                icon = Icons.TwoTone.LayersClear,
                                title = stringResource(R.string.noshot_surface_secure),
                                description = stringResource(R.string.noshot_surface_secure_summary),
                                checked = settings.surfaceSecure,
                                onCheckedChange = { checked ->
                                    scope.launch {
                                        val ok = viewModel.updateSettings { it.copy(surfaceSecure = checked) }
                                        snackbarHost.showReplacingSnackbar(if (ok) okMsg else failMsg)
                                    }
                                },
                            )
                        }

                        item {
                            SettingsSwitchWidget(
                                icon = Icons.TwoTone.Shield,
                                title = stringResource(R.string.noshot_detect_guard),
                                description = stringResource(R.string.noshot_detect_guard_summary),
                                checked = settings.detectGuard,
                                onCheckedChange = { checked ->
                                    scope.launch {
                                        val ok = viewModel.updateSettings { it.copy(detectGuard = checked) }
                                        snackbarHost.showReplacingSnackbar(if (ok) okMsg else failMsg)
                                    }
                                },
                            )
                        }

                        item {
                            SettingsSwitchWidget(
                                icon = Icons.TwoTone.AdminPanelSettings,
                                title = stringResource(R.string.noshot_system_hook),
                                description = stringResource(R.string.noshot_system_hook_summary),
                                checked = settings.systemHook,
                                onCheckedChange = { checked ->
                                    scope.launch {
                                        val ok = viewModel.updateSettings { it.copy(systemHook = checked) }
                                        snackbarHost.showReplacingSnackbar(if (ok) okMsg else failMsg)
                                    }
                                },
                            )
                        }

                        item {
                            SettingsChooseWidget(
                                icon = Icons.TwoTone.Security,
                                title = stringResource(R.string.noshot_capture_guard),
                                items = captureModeLabels,
                                selectedIndex = selectedCaptureIdx,
                                onSelectedIndexChange = { idx ->
                                    val chosen = captureModes.getOrElse(idx) { "off" }
                                    scope.launch {
                                        val ok = viewModel.updateSettings { it.copy(captureGuard = chosen) }
                                        snackbarHost.showReplacingSnackbar(if (ok) okMsg else failMsg)
                                    }
                                },
                            )
                        }

                        item {
                            SettingsSwitchWidget(
                                icon = Icons.AutoMirrored.TwoTone.Article,
                                title = stringResource(R.string.noshot_log),
                                checked = settings.log,
                                onCheckedChange = { checked ->
                                    scope.launch {
                                        val ok = viewModel.updateSettings { it.copy(log = checked) }
                                        snackbarHost.showReplacingSnackbar(if (ok) okMsg else failMsg)
                                    }
                                },
                            )
                        }
                    }
                }

                item(key = "apps_filter_section") {
                    SegmentedColumn(title = stringResource(R.string.noshot_apps_section, enabledCount)) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                            ) {
                                OutlinedTextField(
                                    value = uiState.searchQuery,
                                    onValueChange = { viewModel.setSearch(it) },
                                    placeholder = { Text(stringResource(R.string.noshot_search_apps)) },
                                    leadingIcon = {
                                        Icon(imageVector = Icons.TwoTone.Search, contentDescription = null)
                                    },
                                    trailingIcon = {
                                        if (uiState.searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { viewModel.setSearch("") }) {
                                                Icon(imageVector = Icons.TwoTone.Clear, contentDescription = null)
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }

                        item {
                            SettingsSwitchWidget(
                                icon = Icons.TwoTone.Apps,
                                title = stringResource(R.string.noshot_show_system),
                                checked = uiState.showSystemApps,
                                onCheckedChange = { viewModel.setShowSystemApps(it) },
                            )
                        }
                    }
                }

                if (filteredApps.isEmpty() && !uiState.isLoadingApps) {
                    item(key = "apps_empty") {
                        Text(
                            text = stringResource(R.string.noshot_app_no_matches),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        )
                    }
                } else {
                    lazySegmentColumn(
                        items = filteredApps,
                        key = { _, app -> app.packageName },
                    ) { _, app ->
                        SettingsBaseWidget(
                            leadingContent = {
                                PackageIcon(
                                    packageName = app.packageName,
                                    contentDescription = app.appLabel,
                                    modifier = Modifier.size(36.dp),
                                )
                            },
                            title = app.appLabel,
                            description = app.packageName,
                            trailingContent = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    if (app.enabled) {
                                        val isSystemMode = app.mode == "system"
                                        val nextMode = if (isSystemMode) "client" else "system"
                                        Surface(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    scope.launch {
                                                        val ok = viewModel.setAppMode(app.packageName, nextMode)
                                                        snackbarHost.showReplacingSnackbar(if (ok) okMsg else failMsg)
                                                    }
                                                },
                                            color = if (isSystemMode) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.secondaryContainer
                                            },
                                        ) {
                                            Text(
                                                text = stringResource(
                                                    if (isSystemMode) {
                                                        R.string.noshot_app_mode_system
                                                    } else {
                                                        R.string.noshot_app_mode_client
                                                    },
                                                ),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = if (isSystemMode) {
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.onSecondaryContainer
                                                },
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Switch(
                                        checked = app.enabled,
                                        onCheckedChange = { checked ->
                                            scope.launch {
                                                val ok = viewModel.setAppEnabled(app.packageName, checked, app.mode)
                                                snackbarHost.showReplacingSnackbar(if (ok) okMsg else failMsg)
                                            }
                                        },
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (showStateDialog) {
        AlertDialog(
            onDismissRequest = { showStateDialog = false },
            title = { Text(stringResource(R.string.noshot_view_state)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = status.stateLog.ifBlank { stringResource(R.string.noshot_state_empty) },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showStateDialog = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
    }
}
