package org.uranxsu.manager.ui.screen.mount

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Album
import androidx.compose.material.icons.twotone.Extension
import androidx.compose.material.icons.twotone.FolderSpecial
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.Layers
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
import org.uranxsu.manager.ui.component.SwipeableSnackbarHost
import org.uranxsu.manager.ui.component.settings.AppBackButton
import org.uranxsu.manager.ui.component.settings.SegmentedColumn
import org.uranxsu.manager.ui.component.settings.SettingsBaseWidget
import org.uranxsu.manager.ui.component.settings.SettingsChooseWidget
import org.uranxsu.manager.ui.component.settings.SettingsJumpPageWidget
import org.uranxsu.manager.ui.navigation.LocalNavigator
import org.uranxsu.manager.ui.navigation.Route
import org.uranxsu.manager.ui.theme.CardConfig
import org.uranxsu.manager.ui.theme.ThemeConfig
import org.uranxsu.manager.ui.theme.blurEffect
import org.uranxsu.manager.ui.theme.blurSource
import org.uranxsu.manager.ui.util.ActivityResumeEffect
import org.uranxsu.manager.ui.util.LocalSnackbarHost
import org.uranxsu.manager.ui.util.adaptiveScaffoldWindowInsets
import org.uranxsu.manager.ui.util.showReplacingSnackbar
import org.uranxsu.manager.ui.viewmodel.MountSettingsViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MountSettingsScreen() {
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val viewModel = koinViewModel<MountSettingsViewModel>()
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val snackbarHost = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val pullRefreshState = rememberPullToRefreshState()

    ActivityResumeEffect(viewModel) {
        viewModel.refresh()
    }

    val modeKeys = remember {
        listOf("no_mount", "metamodule", "disabled")
    }
    val modeLabels = listOf(
        stringResource(R.string.mount_mode_nomount),
        stringResource(R.string.mount_mode_metamodule),
        stringResource(R.string.mount_mode_disabled),
    )
    val modeDescriptions = listOf(
        stringResource(R.string.mount_mode_nomount_desc),
        stringResource(R.string.mount_mode_metamodule_desc),
        stringResource(R.string.mount_mode_disabled_desc),
    )
    val selectedModeIndex = modeKeys.indexOf(uiState.status.mode).coerceAtLeast(0)
    val modeSavedMsg = stringResource(R.string.mount_mode_saved)
    val opFailedMsg = stringResource(R.string.mount_operation_failed)

    val nomountSummary = if (uiState.status.nomountDriverReady) {
        stringResource(
            R.string.mount_nomount_summary_active,
            uiState.status.nomountDriverVersion,
            uiState.status.nomountRuleCount,
        )
    } else {
        stringResource(R.string.mount_nomount_summary_inactive)
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                modifier = Modifier.blurEffect(),
                title = { Text(stringResource(R.string.mount_engine_title)) },
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
                        title = stringResource(R.string.mount_mode_section_title),
                    ) {
                        item {
                            SettingsChooseWidget(
                                icon = Icons.TwoTone.Layers,
                                title = stringResource(R.string.mount_mode_active_title),
                                description = modeLabels[selectedModeIndex],
                                items = modeLabels,
                                itemDescriptions = modeDescriptions,
                                selectedIndex = selectedModeIndex,
                                onSelectedIndexChange = { index ->
                                    val newMode = modeKeys.getOrElse(index) { "no_mount" }
                                    scope.launch {
                                        val ok = viewModel.setMode(newMode)
                                        snackbarHost.showReplacingSnackbar(
                                            if (ok) modeSavedMsg else opFailedMsg,
                                        )
                                    }
                                },
                            )
                        }

                        item(visible = uiState.status.externalMetamoduleId.isNotBlank()) {
                            SettingsBaseWidget(
                                icon = Icons.TwoTone.Extension,
                                title = stringResource(R.string.mount_external_metamodule),
                                description = uiState.status.externalMetamoduleId,
                            )
                        }

                        item {
                            SettingsBaseWidget(
                                icon = Icons.TwoTone.Info,
                                title = stringResource(R.string.mount_mode_reboot_hint_title),
                                description = stringResource(R.string.mount_mode_reboot_hint),
                            )
                        }
                    }
                }

                item {
                    SegmentedColumn(
                        title = stringResource(R.string.mount_engines_section_title),
                    ) {
                        item {
                            SettingsJumpPageWidget(
                                icon = Icons.TwoTone.FolderSpecial,
                                title = stringResource(R.string.nomount_manager_title),
                                description = nomountSummary,
                                onClick = {
                                    navigator.push(Route.NoMountManager)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
