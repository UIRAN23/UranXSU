package org.uranxsu.manager.ui.screen.main

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity.CLIPBOARD_SERVICE
import android.app.Activity.RESULT_OK
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.Undo
import androidx.compose.material.icons.automirrored.twotone.Wysiwyg
import androidx.compose.material.icons.twotone.Check
import androidx.compose.material.icons.twotone.ChevronRight
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material.icons.twotone.Cloud
import androidx.compose.material.icons.twotone.Crop
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material.icons.twotone.Download
import androidx.compose.material.icons.twotone.Extension
import androidx.compose.material.icons.twotone.Image
import androidx.compose.material.icons.twotone.MoreVert
import androidx.compose.material.icons.twotone.PlayArrow
import androidx.compose.material.icons.twotone.Refresh
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CheckableDropdownMenuItem
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.FixedScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.capsule.ContinuousRoundedRectangle
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.uranxsu.manager.R
import org.uranxsu.manager.data.settings.SettingsPlatformRepository
import org.uranxsu.manager.data.shell.KsuCliRepository
import org.uranxsu.manager.domain.model.DocumentFormat
import org.uranxsu.manager.domain.model.DocumentPolicy
import org.uranxsu.manager.domain.model.InstalledModule
import org.uranxsu.manager.domain.model.MetaModuleStatus
import org.uranxsu.manager.domain.model.ModuleReadme
import org.uranxsu.manager.domain.model.ModuleUpdateVerification
import org.uranxsu.manager.domain.usecase.EnqueueDownloadUseCase
import org.uranxsu.manager.domain.usecase.ExtractModuleNameUseCase
import org.uranxsu.manager.domain.usecase.IsModuleUriAccessibleUseCase
import org.uranxsu.manager.domain.usecase.ObserveDownloadUseCase
import org.uranxsu.manager.domain.usecase.PeekOneshotScriptUseCase
import org.uranxsu.manager.domain.usecase.TakeModuleUriPermissionUseCase
import org.uranxsu.manager.ui.component.ConfirmResult
import org.uranxsu.manager.ui.component.InstallConfirmationDialog
import org.uranxsu.manager.ui.component.SearchAppBar
import org.uranxsu.manager.ui.component.SwipeableSnackbarHost
import org.uranxsu.manager.ui.component.WarningCard
import org.uranxsu.manager.ui.component.ZipFileDetector
import org.uranxsu.manager.ui.component.ZipFileInfo
import org.uranxsu.manager.ui.component.ZipType
import org.uranxsu.manager.ui.component.rememberConfirmDialog
import org.uranxsu.manager.ui.component.rememberLoadingDialog
import org.uranxsu.manager.ui.component.rememberSearchAppBarScrollBehavior
import org.uranxsu.manager.ui.component.settings.SegmentedColumn
import org.uranxsu.manager.ui.component.settings.SettingsBaseWidget
import org.uranxsu.manager.ui.component.settings.SettingsChooseDialog
import org.uranxsu.manager.ui.component.settings.SettingsJumpPageWidget
import org.uranxsu.manager.ui.component.settings.SettingsTextFieldWidget
import org.uranxsu.manager.ui.navigation.LocalNavigator
import org.uranxsu.manager.ui.navigation.Route
import org.uranxsu.manager.ui.screen.LabelText
import org.uranxsu.manager.ui.screen.themeSettings.crop.copyUriToCropOutput
import org.uranxsu.manager.ui.screen.themeSettings.crop.createBackgroundCropIntent
import org.uranxsu.manager.ui.screen.themeSettings.crop.createBackgroundCropOutputUri
import org.uranxsu.manager.ui.screen.themeSettings.crop.createFileProviderUri
import org.uranxsu.manager.ui.screen.themeSettings.crop.getParcelableExtraCompat
import org.uranxsu.manager.ui.screen.themeSettings.crop.stageCropInputUri
import org.uranxsu.manager.ui.theme.CardConfig
import org.uranxsu.manager.ui.theme.HomeCustomizationConfig
import org.uranxsu.manager.ui.theme.ThemeConfig
import org.uranxsu.manager.ui.theme.blurSource
import org.uranxsu.manager.ui.theme.renderBackgroundBlur
import org.uranxsu.manager.ui.util.CustomTextKey
import org.uranxsu.manager.ui.util.LocalPermissionRequestInterface
import org.uranxsu.manager.ui.util.LocalSnackbarHost
import org.uranxsu.manager.ui.util.Shortcut
import org.uranxsu.manager.ui.util.adaptiveScaffoldWindowInsets
import org.uranxsu.manager.ui.util.customText
import org.uranxsu.manager.ui.util.downloader.download
import org.uranxsu.manager.ui.util.downloader.downloadFailureMessage
import org.uranxsu.manager.ui.util.showReplacingSnackbar
import org.uranxsu.manager.ui.viewmodel.HomeViewModel
import org.uranxsu.manager.ui.viewmodel.ModuleUiAction
import org.uranxsu.manager.ui.viewmodel.ModuleUiEvent
import org.uranxsu.manager.ui.viewmodel.ModuleUiState
import org.uranxsu.manager.ui.viewmodel.ModuleViewModel
import org.uranxsu.manager.ui.webui.WebUIActivity

enum class ShortcutType {
    Action,
    WebUI,
}

/**
 * @author ShirkNeko
 * @date 2025/9/29.
 */
@SuppressLint("ResourceType", "AutoboxingStateCreation")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ModulePage(bottomPadding: Dp) {
    val isModuleUriAccessible = koinInject<IsModuleUriAccessibleUseCase>()
    val takeModuleUriPermission = koinInject<TakeModuleUriPermissionUseCase>()
    val extractModuleName = koinInject<ExtractModuleNameUseCase>()
    val zipFileDetector = koinInject<ZipFileDetector>()
    val peekScript = koinInject<PeekOneshotScriptUseCase>()
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val viewModel = koinViewModel<ModuleViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val homeState by koinViewModel<HomeViewModel>().uiState.collectAsStateWithLifecycle()
    val snackBarHost = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()
    var lastClickTime by remember { mutableStateOf(0L) }

    var showDropdown by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    var showConfirmationDialog by remember { mutableStateOf(false) }
    var pendingZipFiles by remember { mutableStateOf<List<ZipFileInfo>>(emptyList()) }
    InstallConfirmationDialog(
        show = showConfirmationDialog,
        zipFiles = pendingZipFiles,
        onConfirm = { info ->
            showConfirmationDialog = false
            navigator.push(
                Route.Flash.modules(
                    info.filter { it.type == ZipType.MODULE }
                        .map { it.uri.toString() },
                ),
            )
            viewModel.dispatch(ModuleUiAction.MarkNeedRefresh)
        },
        onDismiss = {
            showConfirmationDialog = false
            pendingZipFiles = emptyList()
        },
    )

    val selectZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        if (it.resultCode != RESULT_OK) {
            return@rememberLauncherForActivityResult
        }
        val data = it.data ?: return@rememberLauncherForActivityResult

        scope.launch {
            val zipFiles = mutableListOf<ZipFileInfo>()
            val clipData = data.clipData
            if (clipData != null) {
                val selectedModules = mutableListOf<Uri>()
                val selectedModuleNames = mutableMapOf<Uri, String>()

                suspend fun processUri(uri: Uri) {
                    try {
                        val uriString = uri.toString()
                        if (!isModuleUriAccessible(uriString)) {
                            return
                        }
                        takeModuleUriPermission(uriString)
                        val moduleName =
                            withContext(Dispatchers.IO) { extractModuleName(uriString) }
                        selectedModules.add(uri)
                        selectedModuleNames[uri] = moduleName
                    } catch (e: Exception) {
                        Log.e("ModuleScreen", "Error while processing URI: $uri, Error: ${e.message}")
                    }
                }

                for (i in 0 until clipData.itemCount) {
                    val uri = clipData.getItemAt(i).uri
                    processUri(uri)
                }

                if (selectedModules.isEmpty()) {
                    snackBarHost.showReplacingSnackbar("Unable to access selected module files")
                    return@launch
                }
                selectedModules.singleOrNull()?.let { uri ->
                    peekScript(uri.toString())?.let { manifest ->
                        navigator.push(Route.ScriptRun(uri.toString(), offerInstall = true, displayName = manifest.name))
                        return@launch
                    }
                }
                selectedModules.forEach { it ->
                    val info =
                        withContext(Dispatchers.IO) { zipFileDetector.parseModuleInfo(context, it) }
                    zipFiles.add(info)
                }
                pendingZipFiles = zipFiles

                showConfirmationDialog = true
            } else {
                val uri = data.data ?: return@launch
                // 单个安装模块
                try {
                    val uriString = uri.toString()
                    if (!isModuleUriAccessible(uriString)) {
                        snackBarHost.showReplacingSnackbar("Unable to access selected module files")
                        return@launch
                    }

                    takeModuleUriPermission(uriString)

                    peekScript(uriString)?.let { manifest ->
                        navigator.push(Route.ScriptRun(uriString, offerInstall = true, displayName = manifest.name))
                        return@launch
                    }

                    val info = withContext(Dispatchers.IO) {
                        zipFileDetector.parseModuleInfo(
                            context,
                            uri,
                        )
                    }
                    zipFiles.add(info)
                    pendingZipFiles = zipFiles

                    showConfirmationDialog = true
                } catch (e: Exception) {
                    Log.e("ModuleScreen", "Error processing a single URI: $uri, Error: ${e.message}")
                    snackBarHost.showReplacingSnackbar("Error processing module file: ${e.message}")
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.dispatch(ModuleUiAction.Search(""))
        if (uiState.moduleList.isEmpty() || uiState.isNeedRefresh) {
            viewModel.dispatch(ModuleUiAction.Refresh())
        }
    }

    val isSafeMode = homeState.systemStatus.isSafeMode
    val hideInstallButton = isSafeMode || uiState.hasMagisk

    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = rememberSearchAppBarScrollBehavior(
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState),
    )

    Scaffold(
        topBar = {
            SearchAppBar(
                title = customText(CustomTextKey.PAGE_MODULES),
                searchText = uiState.search,
                onSearchTextChange = { query ->
                    viewModel.dispatch(ModuleUiAction.Search(query))
                },
                dropdownContent = {
                    IconButton(
                        onClick = { showDropdown = true },
                    ) {
                        Icon(
                            imageVector = Icons.TwoTone.MoreVert,
                            contentDescription = stringResource(id = R.string.settings),
                        )

                        ModuleDropdown(
                            expanded = showDropdown,
                            onDismissRequest = { showDropdown = false },
                            viewModel = viewModel,
                            uiState = uiState,
                        )
                    }
                },
                navigationContent = {
                    IconButton(
                        onClick = {
                            navigator.push(Route.ModuleRepo)
                        },
                    ) {
                        Icon(
                            imageVector = Icons.TwoTone.Cloud,
                            contentDescription = customText(CustomTextKey.MODULE_REPO_TITLE),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                searchBarPlaceHolderText = customText(CustomTextKey.MODULE_SEARCH_PLACEHOLDER),
            )
        },
        floatingActionButton = {
            if (hideInstallButton) return@Scaffold

            FloatingActionButton(
                modifier = Modifier.padding(bottom = bottomPadding + 5.dp),
                contentColor = MaterialTheme.colorScheme.onPrimary,
                containerColor = MaterialTheme.colorScheme.primary,
                onClick = {
                    selectZipLauncher.launch(
                        Intent(Intent.ACTION_GET_CONTENT).apply {
                            type = "application/zip"
                            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                        },
                    )
                },
                content = {
                    Icon(
                        painter = painterResource(id = R.drawable.package_import),
                        contentDescription = null,
                    )
                },
            )
        },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentWindowInsets = adaptiveScaffoldWindowInsets(includeBottom = false),
        snackbarHost = {
            SwipeableSnackbarHost(
                hostState = snackBarHost,
            )
        },
    ) { innerPadding ->
        when {
            uiState.hasMagisk -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.TwoTone.Warning,
                            contentDescription = null,
                            modifier = Modifier
                                .size(64.dp)
                                .padding(bottom = 16.dp),
                        )
                        Text(
                            stringResource(R.string.module_magisk_conflict),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }

            uiState.moduleList.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.TwoTone.Extension,
                            contentDescription = null,
                            modifier = Modifier
                                .size(96.dp)
                                .padding(bottom = 16.dp),
                        )
                        Text(
                            text =
                                if (uiState.search.isNotEmpty()) {
                                    stringResource(R.string.search_no_any_match)
                                } else {
                                    customText(CustomTextKey.MODULE_EMPTY)
                                },
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }

            else -> {
                ModuleList(
                    viewModel = viewModel,
                    uiState = uiState,
                    listState = listState,
                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                    onUpdateModule = {
                        navigator.push(Route.Flash.moduleUpdate(it.toString()))
                    },
                    onClickModule = { id, hasWebUi ->
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastClickTime < 600) {
                            Log.d("ModuleScreen", "Click too fast, ignoring")
                            return@ModuleList
                        }
                        lastClickTime = currentTime

                        if (hasWebUi) {
                            try {
                                context.startActivity(
                                    Intent(context, WebUIActivity::class.java)
                                        .setData(
                                            Uri.Builder()
                                                .scheme("kernelsu")
                                                .authority("webui")
                                                .appendQueryParameter("id", id)
                                                .build(),
                                        ),
                                )
                            } catch (e: Exception) {
                                Log.e("ModuleScreen", "Error launching WebUI: ${e.message}", e)
                                scope.launch {
                                    snackBarHost.showReplacingSnackbar("Error launching WebUI: ${e.message}")
                                }
                            }
                            return@ModuleList
                        }
                    },
                    context = context,
                    snackBarHost = snackBarHost,
                    bottomPadding = bottomPadding + innerPadding.calculateBottomPadding(),
                    topPadding = innerPadding.calculateTopPadding(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ModuleDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    viewModel: ModuleViewModel,
    uiState: ModuleUiState,
) {
    DropdownMenuPopup(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
    ) {
        DropdownMenuGroup(
            shapes = MenuDefaults.groupShapes(),
        ) {
            CheckableDropdownMenuItem(
                checked = uiState.sortActionFirst,
                onCheckedChange = {
                    viewModel.dispatch(
                        ModuleUiAction.Sort(uiState.sortEnabledFirst, it),
                    )
                },
                text = { Text(customText(CustomTextKey.MODULE_SORT_ACTION)) },
                shapes = MenuDefaults.itemShape(
                    index = 0,
                    count = 2,
                ),
            )
            CheckableDropdownMenuItem(
                checked = uiState.sortEnabledFirst,
                onCheckedChange = {
                    viewModel.dispatch(
                        ModuleUiAction.Sort(it, uiState.sortActionFirst),
                    )
                },
                text = { Text(customText(CustomTextKey.MODULE_SORT_ENABLED)) },
                shapes = MenuDefaults.itemShape(
                    index = 1,
                    count = 2,
                ),
            )
        }
    }
}

private fun getMetaModuleWarningText(
    hasModuleRequireMount: Boolean,
    showWarning: Boolean,
    context: Context,
    status: MetaModuleStatus,
): String? {
    if (!showWarning) return null
    if (!hasModuleRequireMount) return null

    return status.resId?.let { context.getString(it) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MetaModuleWarningCard(
    text: String,
    visible: Boolean,
    onClose: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        WarningCard(
            shape = CardDefaults.elevatedShape,
            message = text,
            onClose = onClose,
        )

        Spacer(Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ModuleList(
    viewModel: ModuleViewModel,
    uiState: ModuleUiState,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    boxModifier: Modifier = Modifier,
    onUpdateModule: (Uri) -> Unit,
    onClickModule: (id: String, hasWebUi: Boolean) -> Unit,
    context: Context,
    snackBarHost: SnackbarHostState,
    bottomPadding: Dp,
    topPadding: Dp,
) {
    val shortcut = koinInject<Shortcut>()
    val settings = koinInject<SettingsPlatformRepository>()
    var showMetaModuleWarning by rememberSaveable { mutableStateOf(true) }
    val enqueueDownload = koinInject<EnqueueDownloadUseCase>()
    val observeDownload = koinInject<ObserveDownloadUseCase>()
    val permissionRequestInterface = LocalPermissionRequestInterface.current
    var requestingLocalNetworkPermission by remember { mutableStateOf(false) }
    val localNetworkPermissionDescription = stringResource(R.string.local_network_permission_description)
    val scope = rememberCoroutineScope()
    val pullRefreshState = rememberPullToRefreshState()
    val failedEnable = stringResource(R.string.module_failed_to_enable)
    val failedDisable = stringResource(R.string.module_failed_to_disable)
    val failedUninstall = stringResource(R.string.module_uninstall_failed)
    val successUninstall = stringResource(R.string.module_uninstall_success)
    val reboot = customText(CustomTextKey.COMMON_REBOOT)
    val rebootToApply = stringResource(R.string.reboot_to_apply)
    val moduleStr = stringResource(R.string.module)
    val uninstall = stringResource(R.string.uninstall)
    val cancel = stringResource(android.R.string.cancel)
    val moduleUninstallConfirm = stringResource(R.string.module_uninstall_confirm)
    val metaModuleUninstallConfirm = stringResource(R.string.metamodule_uninstall_confirm)
    val updateText = stringResource(R.string.module_update)
    val changelogText = stringResource(R.string.module_changelog)
    val downloadingText = stringResource(R.string.module_downloading)
    val startDownloadingText = stringResource(R.string.module_start_downloading)

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is ModuleUiEvent.EnabledChanged -> {
                    val moduleName = uiState.moduleList
                        .find { it.dirId == event.moduleId }
                        ?.name ?: event.moduleId
                    if (event.successful) {
                        val result = snackBarHost.showReplacingSnackbar(
                            message = rebootToApply,
                            actionLabel = reboot,
                            duration = SnackbarDuration.Long,
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            viewModel.dispatch(ModuleUiAction.Reboot)
                        }
                    } else {
                        val message = if (event.enabled) failedEnable else failedDisable
                        snackBarHost.showReplacingSnackbar(message.format(moduleName))
                    }
                }

                is ModuleUiEvent.RemovedChanged -> {
                    val moduleName = uiState.moduleList
                        .find { it.dirId == event.moduleId }
                        ?.name ?: event.moduleId
                    if (event.successful) {
                        viewModel.dispatch(ModuleUiAction.MarkNeedRefresh)
                        viewModel.dispatch(ModuleUiAction.Refresh())
                    }
                    if (event.removed) {
                        val message = if (event.successful) successUninstall else failedUninstall
                        val result = snackBarHost.showReplacingSnackbar(
                            message = message.format(moduleName),
                            actionLabel = reboot.takeIf { event.successful },
                            duration = SnackbarDuration.Long,
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            viewModel.dispatch(ModuleUiAction.Reboot)
                        }
                    }
                }

                is ModuleUiEvent.Error -> if (event.message.isNotBlank()) {
                    snackBarHost.showReplacingSnackbar(event.message)
                }

                ModuleUiEvent.RefreshCompleted -> Unit

                ModuleUiEvent.LocalNetworkPermissionRequired -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN &&
                        context.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED &&
                        !requestingLocalNetworkPermission
                    ) {
                        requestingLocalNetworkPermission = true
                        permissionRequestInterface.requestPermission(
                            permission = Manifest.permission.ACCESS_LOCAL_NETWORK,
                            callback = { granted ->
                                requestingLocalNetworkPermission = false
                                if (granted) viewModel.dispatch(ModuleUiAction.Refresh(manual = true))
                            },
                            requestDescription = localNetworkPermissionDescription,
                        )
                    }
                }
            }
        }
    }

    val loadingDialog = rememberLoadingDialog()
    val confirmDialog = rememberConfirmDialog()
    val ksuCli = koinInject<KsuCliRepository>()
    val bannerSavedMsg = stringResource(R.string.module_banner_saved)
    val bannerResetMsg = stringResource(R.string.module_banner_reset_done)
    val bannerFailedMsg = stringResource(R.string.module_banner_failed)
    val externalCropUnavailable = stringResource(R.string.background_external_crop_unavailable)
    val customBannerCropTitle = stringResource(R.string.custom_banner_crop_title)

    var bannerTargetModule by remember { mutableStateOf<InstalledModule?>(null) }
    var bannerActionModule by remember { mutableStateOf<InstalledModule?>(null) }
    var pendingBannerPhotoModule by remember { mutableStateOf<InstalledModule?>(null) }
    var pendingBannerPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var pendingBannerExternalCropUri by remember { mutableStateOf<Uri?>(null) }
    var showBannerCropMethodDialog by remember { mutableStateOf(false) }

    fun importCroppedBannerPhoto(target: InstalledModule, outputUri: Uri) {
        scope.launch {
            val opResult = withContext(Dispatchers.IO) {
                ModuleBannerEditor.import(context, ksuCli.getRootShell(), target, outputUri)
            }
            if (opResult.isSuccess) {
                invalidateModuleBanner(target.dirId)
                viewModel.dispatch(ModuleUiAction.Refresh(manual = true))
                snackBarHost.showReplacingSnackbar(bannerSavedMsg)
            } else {
                snackBarHost.showReplacingSnackbar(bannerFailedMsg)
            }
        }
    }

    val cropModuleBannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val target = bannerTargetModule ?: return@rememberLauncherForActivityResult
        bannerTargetModule = null
        if (result.resultCode == RESULT_OK) {
            val outputUri = result.data?.getParcelableExtraCompat<Uri>(UCrop.EXTRA_OUTPUT_URI)
            if (outputUri != null) {
                importCroppedBannerPhoto(target, outputUri)
            }
        } else if (result.resultCode == UCrop.RESULT_ERROR) {
            scope.launch { snackBarHost.showReplacingSnackbar(bannerFailedMsg) }
        }
    }

    val externalCropModuleBannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val target = bannerTargetModule
        bannerTargetModule = null
        val outputUri = pendingBannerExternalCropUri ?: result.data?.data
        pendingBannerExternalCropUri = null
        if (result.resultCode == RESULT_OK && target != null && outputUri != null) {
            importCroppedBannerPhoto(target, outputUri)
        }
    }

    val launchModuleBannerInAppCrop: (InstalledModule, Uri) -> Unit = { target, sourceUri ->
        scope.launch {
            val stagedUri = stageCropInputUri(context, sourceUri, "mod_banner_crop_in") ?: sourceUri
            bannerTargetModule = target
            val outputUri = createBackgroundCropOutputUri(context, "module_banner_crop")
            val intent = createBackgroundCropIntent(
                context = context,
                sourceUri = stagedUri,
                outputUri = outputUri,
                aspectRatioX = 16f,
                aspectRatioY = 7f,
                maxSizeX = 2048,
                maxSizeY = 2048,
                title = customBannerCropTitle,
            )
            try {
                cropModuleBannerLauncher.launch(intent)
            } catch (t: Throwable) {
                Log.e("ModulePage", "In-app banner crop failed", t)
                bannerTargetModule = null
                snackBarHost.showReplacingSnackbar(bannerFailedMsg)
            }
        }
    }

    val launchModuleBannerExternalCrop: (InstalledModule, Uri) -> Unit = { target, sourceUri ->
        scope.launch {
            val uri = createBackgroundCropOutputUri(context, "module_banner_crop_ext")
            val copied = copyUriToCropOutput(context, sourceUri, uri)

            if (!copied) {
                Toast.makeText(context, externalCropUnavailable, Toast.LENGTH_SHORT).show()
                launchModuleBannerInAppCrop(target, sourceUri)
            } else {
                val intent = Intent("com.android.camera.action.CROP").apply {
                    clipData = ClipData.newRawUri("crop_output", uri)
                    setDataAndType(uri, "image/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    putExtra("crop", "true")
                    putExtra("aspectX", 16)
                    putExtra("aspectY", 7)
                    putExtra("outputX", 1600)
                    putExtra("outputY", 700)
                    putExtra("return-data", false)
                    putExtra(MediaStore.EXTRA_OUTPUT, uri)
                }
                try {
                    bannerTargetModule = target
                    pendingBannerExternalCropUri = uri
                    externalCropModuleBannerLauncher.launch(intent)
                } catch (t: Throwable) {
                    Log.e("ModulePage", "External banner crop failed, fallback to in-app crop", t)
                    pendingBannerExternalCropUri = null
                    Toast.makeText(context, externalCropUnavailable, Toast.LENGTH_SHORT).show()
                    launchModuleBannerInAppCrop(target, sourceUri)
                }
            }
        }
    }

    val pickModuleBannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val target = bannerTargetModule ?: return@rememberLauncherForActivityResult
        bannerTargetModule = null
        if (uri == null) return@rememberLauncherForActivityResult
        pendingBannerPhotoModule = target
        pendingBannerPhotoUri = uri
        showBannerCropMethodDialog = true
    }

    SettingsChooseDialog(
        show = showBannerCropMethodDialog,
        title = stringResource(R.string.background_crop_method_title),
        items = listOf(
            stringResource(R.string.background_crop_method_in_app),
            stringResource(R.string.background_crop_method_external),
        ),
        selectedIndex = 0,
        onDismiss = {
            showBannerCropMethodDialog = false
            pendingBannerPhotoUri = null
            pendingBannerPhotoModule = null
        },
        onSelectedIndexChange = { index ->
            val target = pendingBannerPhotoModule ?: return@SettingsChooseDialog
            val sourceUri = pendingBannerPhotoUri ?: return@SettingsChooseDialog
            when (index) {
                0 -> launchModuleBannerInAppCrop(target, sourceUri)
                1 -> launchModuleBannerExternalCrop(target, sourceUri)
            }
        },
    )

    var shortcutModuleId by rememberSaveable { mutableStateOf<String?>(null) }
    val textFieldState = rememberTextFieldState()
    var shortcutIconUri by rememberSaveable { mutableStateOf<String?>(null) }
    var defaultShortcutIconUri by rememberSaveable { mutableStateOf<String?>(null) }
    var defaultActionShortcutIconUri by rememberSaveable { mutableStateOf<String?>(null) }
    var defaultWebUiShortcutIconUri by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedShortcutType by rememberSaveable { mutableStateOf<ShortcutType?>(null) }
    val showShortcutDialog = remember { mutableStateOf(false) }

    fun openShortcutDialogForType(type: ShortcutType) {
        selectedShortcutType = type
        val defaultIcon = when (type) {
            ShortcutType.Action -> defaultActionShortcutIconUri ?: defaultWebUiShortcutIconUri
            ShortcutType.WebUI -> defaultWebUiShortcutIconUri ?: defaultActionShortcutIconUri
        }
        defaultShortcutIconUri = defaultIcon
        shortcutIconUri = defaultIcon
        showShortcutDialog.value = true
    }

    fun hasModuleShortcut(context: Context, moduleId: String, type: ShortcutType): Boolean = when (type) {
        ShortcutType.Action -> shortcut.hasModuleActionShortcut(context, moduleId)
        ShortcutType.WebUI -> shortcut.hasModuleWebUiShortcut(context, moduleId)
    }

    fun deleteModuleShortcut(context: Context, moduleId: String, type: ShortcutType) {
        when (type) {
            ShortcutType.Action -> shortcut.deleteModuleActionShortcut(context, moduleId)
            ShortcutType.WebUI -> shortcut.deleteModuleWebUiShortcut(context, moduleId)
        }
    }

    fun createModuleShortcut(
        context: Context,
        moduleId: String,
        name: String,
        iconUri: String?,
        type: ShortcutType,
        intentToken: String,
    ) {
        when (type) {
            ShortcutType.Action -> {
                shortcut.createModuleActionShortcut(
                    context = context,
                    moduleId = moduleId,
                    name = name,
                    iconUri = iconUri,
                    intentToken = intentToken,
                )
            }

            ShortcutType.WebUI -> {
                shortcut.createModuleWebUiShortcut(
                    context = context,
                    moduleId = moduleId,
                    name = name,
                    iconUri = iconUri,
                    intentToken = intentToken,
                )
            }
        }
    }

    val pickShortcutIconLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        shortcutIconUri = uri?.toString()
    }

    val shortcutPreviewIcon = remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(shortcutIconUri) {
        val uriStr = shortcutIconUri
        if (uriStr.isNullOrBlank()) {
            shortcutPreviewIcon.value = null
            return@LaunchedEffect
        }
        val bitmap = withContext(Dispatchers.IO) {
            shortcut.loadShortcutBitmap(context, uriStr)
        }
        shortcutPreviewIcon.value = bitmap?.asImageBitmap()
    }

    var hasExistingShortcut by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(shortcutModuleId, selectedShortcutType, showShortcutDialog.value) {
        val moduleId = shortcutModuleId
        val type = selectedShortcutType
        if (!showShortcutDialog.value || moduleId.isNullOrBlank() || type == null) {
            hasExistingShortcut = false
            return@LaunchedEffect
        }
        val exists = withContext(Dispatchers.IO) {
            hasModuleShortcut(context, moduleId, type)
        }
        hasExistingShortcut = exists
    }

    suspend fun onModuleUpdate(
        module: InstalledModule,
        changelogUrl: String,
        downloadUrl: String,
        fileName: String,
    ) {
        val showToast: suspend (String) -> Unit = { msg ->
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    msg,
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }

        val verification = loadingDialog.withLoading { viewModel.resolveUpdate(module) }
        if (verification == ModuleUpdateVerification.Rejected) {
            showToast(context.getString(R.string.repo_update_unverifiable))
            return
        }
        val registry = verification as? ModuleUpdateVerification.Registry
        val canInstall = { registry == null || viewModel.isUpdateCurrent(module, registry) }
        val changelogResult = if (registry != null) {
            Result.success(
                ModuleReadme(
                    registry.release.descriptionHTML.ifBlank { module.description },
                    registry.repositoryUrl,
                    if (registry.release.descriptionHTML.isNotBlank()) DocumentFormat.HTML else DocumentFormat.MARKDOWN,
                    DocumentPolicy.SameOrigin(registry.repositoryUrl),
                ),
            )
        } else {
            loadingDialog.withLoading { viewModel.loadUpdateChangelog(changelogUrl, module.updateJson) }
        }
        val changelog = changelogResult.getOrElse {
            showToast(context.getString(R.string.repo_document_error))
            return
        }

        val confirmResult = confirmDialog.awaitConfirm(
            changelogText,
            content = changelog.content,
            document = changelog,
            confirm = updateText,
        )

        if (confirmResult != ConfirmResult.Confirmed) {
            return
        }
        if (!canInstall()) {
            showToast(context.getString(R.string.repo_update_unverifiable))
            return
        }

        showToast(startDownloadingText.format(module.name))

        val downloading = downloadingText.format(module.name)
        withContext(Dispatchers.IO) {
            download(
                context,
                permissionRequestInterface,
                registry?.zipUrl ?: downloadUrl,
                fileName,
                enqueueDownload,
                observeDownload,
                expected = registry?.expected,
                canEnqueue = canInstall,
                onFailed = { failed -> Toast.makeText(context, downloadFailureMessage(context, failed), Toast.LENGTH_LONG).show() },
                onDownloaded = { uri ->
                    if (canInstall()) onUpdateModule(uri)
                },
                onDownloading = {
                    launch(Dispatchers.Main) {
                        Toast.makeText(context, downloading, Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }
    }

    suspend fun onModuleUninstallClicked(module: InstalledModule) {
        val isUninstall = !module.remove
        if (isUninstall) {
            val formatter = if (module.metamodule) metaModuleUninstallConfirm else moduleUninstallConfirm
            val confirmResult = confirmDialog.awaitConfirm(
                moduleStr,
                content = formatter.format(module.name),
                confirm = uninstall,
                dismiss = cancel,
            )
            if (confirmResult != ConfirmResult.Confirmed) {
                return
            }
        }

        if (isUninstall) {
            withContext(Dispatchers.IO) {
                shortcut.deleteModuleActionShortcut(context, module.id)
                shortcut.deleteModuleWebUiShortcut(context, module.id)
            }
        }
        viewModel.dispatch(ModuleUiAction.SetRemoved(module.dirId, isUninstall))
    }

    fun onModuleAddShortcut(module: InstalledModule, shortcutType: ShortcutType) {
        shortcutModuleId = module.id
        textFieldState.edit {
            replace(0, length, module.name)
        }
        shortcutIconUri = null
        defaultShortcutIconUri = null
        defaultActionShortcutIconUri = module.actionIconPath
            ?.takeIf { it.isNotBlank() }
            ?.let { "su:$it" }
        defaultWebUiShortcutIconUri = module.webUiIconPath
            ?.takeIf { it.isNotBlank() }
            ?.let { "su:$it" }
        openShortcutDialogForType(shortcutType)
    }

    PullToRefreshBox(
        state = pullRefreshState,
        onRefresh = {
            viewModel.dispatch(ModuleUiAction.Refresh(manual = true))
        },
        modifier = boxModifier
            .fillMaxSize()
            .blurSource(),
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                modifier = Modifier
                    .padding(top = topPadding)
                    .align(Alignment.TopCenter),
                state = pullRefreshState,
                isRefreshing = uiState.isRefreshing,
            )
        },
        isRefreshing = uiState.isRefreshing,
    ) {
        val metaModuleWarningText by produceState<String?>(
            initialValue = null,
            uiState.hasModuleRequireMount,
            showMetaModuleWarning,
            uiState.metaModuleStatus,
        ) {
            value = withContext(Dispatchers.IO) {
                getMetaModuleWarningText(
                    uiState.hasModuleRequireMount,
                    showMetaModuleWarning,
                    context,
                    uiState.metaModuleStatus,
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = modifier,
            contentPadding = remember {
                PaddingValues(
                    start = 16.dp,
                    top = 0.dp,
                    end = 16.dp,
                    bottom = 72.dp + 5.dp + 5.dp, // FAB + bottom padding of FAB
                )
            },
        ) {
            item {
                Spacer(modifier = Modifier.height(topPadding))
            }

            if (metaModuleWarningText != null) {
                item(
                    key = "warning",
                ) {
                    MetaModuleWarningCard(
                        text = metaModuleWarningText!!,
                        visible = showMetaModuleWarning,
                        onClose = { showMetaModuleWarning = false },
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            items(
                items = uiState.moduleList,
                key = { "module-$it.id" },
            ) { module ->
                ModuleItem(
                    viewModel = viewModel,
                    module = module,
                    moduleSizes = uiState.moduleSizes,
                    updateUrl = module.moduleUpdate?.zipUrl.orEmpty(),
                    onUninstallClicked = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                onModuleUninstallClicked(module)
                            }
                        }
                    },
                    onCheckChanged = { enabled ->
                        viewModel.dispatch(ModuleUiAction.SetEnabled(module.dirId, enabled))
                        true
                    },
                    onUpdate = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                onModuleUpdate(
                                    module,
                                    module.moduleUpdate!!.changelog,
                                    module.moduleUpdate.zipUrl,
                                    "${module.name}-${module.moduleUpdate.version}.zip",
                                )
                            }
                        }
                    },
                    onClick = {
                        onClickModule(it.dirId, it.hasWebUi)
                    },
                    onModuleAddShortcut = { module, type ->
                        onModuleAddShortcut(module, type)
                    },
                    onEditBanner = { mod ->
                        if (ModuleBannerEditor.isCustomBanner(mod)) {
                            bannerActionModule = mod
                        } else {
                            bannerTargetModule = mod
                            pickModuleBannerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        }
                    },
                    showMoreModuleInfo = uiState.showMoreModuleInfo,
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                Spacer(modifier = Modifier.height(bottomPadding))
            }
        }
    }

    bannerActionModule?.let { mod ->
        AlertDialog(
            onDismissRequest = { bannerActionModule = null },
            title = { Text(stringResource(R.string.module_banner_title)) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.module_banner_custom_actions_desc, mod.name),
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                bannerActionModule = null
                                scope.launch {
                                    val cached = withContext(Dispatchers.IO) {
                                        ModuleBannerEditor.extractBannerToCache(
                                            context = context,
                                            shell = ksuCli.getRootShell(),
                                            module = mod,
                                        )
                                    }
                                    if (cached == null) {
                                        snackBarHost.showReplacingSnackbar(bannerFailedMsg)
                                    } else {
                                        pendingBannerPhotoModule = mod
                                        pendingBannerPhotoUri = createFileProviderUri(context, cached)
                                        showBannerCropMethodDialog = true
                                    }
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(imageVector = Icons.TwoTone.Crop, contentDescription = null)
                        Text(text = stringResource(R.string.module_banner_crop))
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                bannerActionModule = null
                                bannerTargetModule = mod
                                pickModuleBannerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(imageVector = Icons.TwoTone.Image, contentDescription = null)
                        Text(text = stringResource(R.string.module_banner_replace))
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                bannerActionModule = null
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        ModuleBannerEditor.remove(ksuCli.getRootShell(), mod)
                                    }
                                    if (result.isSuccess) {
                                        invalidateModuleBanner(mod.dirId)
                                        viewModel.dispatch(ModuleUiAction.Refresh(manual = true))
                                        snackBarHost.showReplacingSnackbar(bannerResetMsg)
                                    } else {
                                        snackBarHost.showReplacingSnackbar(bannerFailedMsg)
                                    }
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(imageVector = Icons.TwoTone.Delete, contentDescription = null)
                        Text(text = stringResource(R.string.module_banner_reset))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { bannerActionModule = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showShortcutDialog.value) {
        ModalBottomSheet(
            sheetState = rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            ),
            onDismissRequest = {
                showShortcutDialog.value = false
            },
        ) {
            var error by remember { mutableStateOf("") }
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.module_shortcut_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(horizontal = 24.dp),
                )

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .padding(vertical = 16.dp)
                        .size(100.dp)
                        .clip(ContinuousRoundedRectangle(25.dp)),
                ) {
                    val preview = shortcutPreviewIcon.value
                    if (preview != null) {
                        Image(
                            bitmap = preview,
                            modifier = Modifier.size(100.dp),
                            contentDescription = null,
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .background(Color.White),
                        )
                        Image(
                            painter = painterResource(id = R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            contentScale = FixedScale(1.5f),
                        )
                    }
                }
                SegmentedColumn {
                    if (shortcutIconUri == defaultShortcutIconUri) {
                        item {
                            SettingsBaseWidget(
                                iconPlaceholder = false,
                                isOnBackground = false,
                                title = stringResource(id = R.string.module_shortcut_icon_pick),
                                onClick = {
                                    pickShortcutIconLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                    )
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.TwoTone.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                    } else {
                        item {
                            SettingsBaseWidget(
                                iconPlaceholder = false,
                                isOnBackground = false,
                                title = stringResource(id = R.string.restore),
                                onClick = {
                                    shortcutIconUri = defaultShortcutIconUri
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.TwoTone.Undo,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                    }

                    item {
                        val shouldNotEmpty =
                            stringResource(R.string.module_shortcut_should_not_empty)
                        SettingsTextFieldWidget(
                            state = textFieldState,
                            title = stringResource(id = R.string.module_shortcut_name_label),
                            error = error,
                            renderBackgroundBlur = false,
                        )

                        LaunchedEffect(textFieldState.text) {
                            error = if (textFieldState.text.isBlank()) {
                                shouldNotEmpty
                            } else {
                                ""
                            }
                        }
                    }

                    if (hasExistingShortcut) {
                        item {
                            SettingsJumpPageWidget(
                                iconPlaceholder = false,
                                renderBackgroundBlur = false,
                                title = stringResource(id = R.string.module_shortcut_delete),
                                onClick = {
                                    val moduleId = shortcutModuleId
                                    val type = selectedShortcutType
                                    if (!moduleId.isNullOrBlank() && type != null) {
                                        deleteModuleShortcut(context, moduleId, type)
                                    }
                                    showShortcutDialog.value = false
                                },
                            )
                        }
                    }

                    item {
                        val settings = koinInject<SettingsPlatformRepository>()
                        val moduleShortcutSchemeCopied = stringResource(R.string.module_shortcut_scheme_copied)

                        SettingsJumpPageWidget(
                            iconPlaceholder = false,
                            renderBackgroundBlur = false,
                            title = stringResource(R.string.module_shortcut_copy_scheme),
                            onClick = {
                                val currentModuleId = shortcutModuleId
                                val type = selectedShortcutType
                                if (currentModuleId.isNullOrBlank() || type == null) {
                                    return@SettingsJumpPageWidget
                                }
                                val url = shortcut.buildShortcutUri(currentModuleId, type, settings.intentToken).toString()
                                val clipboard = context.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("KernelSU deep link", url))
                                Toast.makeText(context, moduleShortcutSchemeCopied, Toast.LENGTH_SHORT).show()
                            },
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { showShortcutDialog.value = false },
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp),
                    ) {
                        Text(
                            text = stringResource(id = android.R.string.cancel),
                        )
                    }

                    Button(
                        onClick = {
                            val moduleId = shortcutModuleId
                            val type = selectedShortcutType
                            if (!moduleId.isNullOrBlank() && textFieldState.text.isNotBlank() && type != null) {
                                createModuleShortcut(
                                    context = context,
                                    moduleId = moduleId,
                                    name = textFieldState.text.toString(),
                                    iconUri = shortcutIconUri,
                                    type = type,
                                    intentToken = settings.intentToken,
                                )
                            }
                            showShortcutDialog.value = false
                        },
                        enabled = error.isBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 16.dp),
                    ) {
                        Text(
                            text = if (hasExistingShortcut) {
                                stringResource(id = R.string.module_update)
                            } else {
                                stringResource(id = android.R.string.ok)
                            },
                        )
                    }
                }
            }
        }
    }
}

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun ModuleItem(
    viewModel: ModuleViewModel,
    module: InstalledModule,
    moduleSizes: Map<String, String>,
    updateUrl: String,
    onUninstallClicked: (InstalledModule) -> Unit,
    onCheckChanged: suspend (Boolean) -> Boolean,
    onUpdate: (InstalledModule) -> Unit,
    onClick: (InstalledModule) -> Unit,
    onModuleAddShortcut: (InstalledModule, ShortcutType) -> Unit,
    onEditBanner: (InstalledModule) -> Unit = {},
    showMoreModuleInfo: Boolean,
) {
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val homeCustomization: HomeCustomizationConfig = koinInject()
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
    val hapticFeedback = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var isEnabled by remember(module.dirId) { mutableStateOf(module.enabled) }
    var isChangingEnabled by remember(module.dirId) { mutableStateOf(false) }

    LaunchedEffect(module.enabled) {
        isEnabled = module.enabled
    }

    val banner = rememberModuleBanner(
        moduleId = module.dirId,
        banner = module.bannerPath,
        enabled = homeCustomization.enableModuleBanner,
    )
    val bannerScrim = MaterialTheme.colorScheme.surfaceBright

    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .renderBackgroundBlur(),
        color =
            if (themeConfig.isEnableBlurExp) {
                Color.Transparent
            } else {
                MaterialTheme.colorScheme.surfaceBright.copy(cardConfig.cardAlpha)
            },
        shape = RoundedCornerShape(16.dp),
    ) {
        val textDecoration = if (!module.remove) null else TextDecoration.LineThrough
        val interactionSource = remember { MutableInteractionSource() }

        LaunchedEffect(module.dirId) {
            viewModel.dispatch(ModuleUiAction.LoadSize(module.dirId))
        }

        val sizeStr = moduleSizes[module.dirId]

        Box(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .moduleBanner(banner, bannerScrim)
                    .run {
                        if (module.hasActionScript || module.hasWebUi) {
                            combinedClickable(
                                onClick = {
                                    if (module.hasWebUi) {
                                        onClick(module)
                                    }
                                },
                            )
                        } else {
                            this
                        }
                    }
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val moduleVersion = customText(CustomTextKey.MODULE_VERSION_LABEL)
                    val moduleAuthor = customText(CustomTextKey.MODULE_AUTHOR_LABEL)

                    Column(
                        modifier = Modifier.fillMaxWidth(0.8f),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = module.name,
                                fontSize = MaterialTheme.typography.titleMedium.fontSize,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                                fontFamily = MaterialTheme.typography.titleMedium.fontFamily,
                                textDecoration = textDecoration,
                                modifier = Modifier.weight(1f, false),
                            )
                        }

                        Text(
                            text = "$moduleVersion: ${module.version}",
                            fontSize = MaterialTheme.typography.bodySmall.fontSize,
                            lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                            fontFamily = MaterialTheme.typography.bodySmall.fontFamily,
                            textDecoration = textDecoration,
                        )

                        Text(
                            text = "$moduleAuthor: ${module.author}",
                            fontSize = MaterialTheme.typography.bodySmall.fontSize,
                            lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                            fontFamily = MaterialTheme.typography.bodySmall.fontFamily,
                            textDecoration = textDecoration,
                        )

                        // 显示更多模块信息时添加updateJson
                        if (showMoreModuleInfo && module.updateJson.isNotEmpty()) {
                            val updateJsonLabel = customText(CustomTextKey.MODULE_UPDATE_JSON)
                            Text(
                                text = "$updateJsonLabel: ${module.updateJson}",
                                fontSize = MaterialTheme.typography.bodySmall.fontSize,
                                lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                                fontFamily = MaterialTheme.typography.bodySmall.fontFamily,
                                textDecoration = textDecoration,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 5,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = { },
                                        onLongClick = {
                                            val clipData = ClipData.newPlainText(
                                                "Update JSON URL",
                                                module.updateJson,
                                            )
                                            clipboardManager.setPrimaryClip(clipData)
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)

                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.module_update_json_copied),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        },
                                    ),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Switch(
                            enabled = !module.update && !isChangingEnabled,
                            checked = isEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    isChangingEnabled = true
                                    try {
                                        if (onCheckChanged(enabled)) {
                                            isEnabled = enabled
                                        }
                                    } finally {
                                        isChangingEnabled = false
                                    }
                                }
                            },
                            interactionSource = if (!module.hasWebUi) interactionSource else null,
                            thumbContent = {
                                if (isEnabled) {
                                    Icon(
                                        imageVector = Icons.TwoTone.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(SwitchDefaults.IconSize),
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.TwoTone.Close,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.surfaceBright,
                                        modifier = Modifier.size(SwitchDefaults.IconSize),
                                    )
                                }
                            },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = module.description,
                    fontSize = MaterialTheme.typography.bodySmall.fontSize,
                    fontFamily = MaterialTheme.typography.bodySmall.fontFamily,
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                    fontWeight = MaterialTheme.typography.bodySmall.fontWeight,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 4,
                    textDecoration = textDecoration,
                )

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    LabelText(
                        label = module.dirId,
                        containerColor = MaterialTheme.colorScheme.primary,
                    )
                    if (module.metamodule) {
                        LabelText(
                            label = "META",
                            containerColor = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    LabelText(
                        label = sizeStr ?: "0 KB",
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider(thickness = Dp.Hairline)

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (module.hasActionScript) {
                        Surface(
                            modifier = Modifier
                                .padding(vertical = 5.dp)
                                .defaultMinSize(minWidth = 52.dp, minHeight = 32.dp)
                                .clip(ButtonDefaults.filledTonalShape)
                                .combinedClickable(
                                    onClick = {
                                        navigator.push(Route.ExecuteModuleAction(module.dirId, false))
                                        viewModel.dispatch(ModuleUiAction.MarkNeedRefresh)
                                    },
                                    onLongClick = {
                                        onModuleAddShortcut(module, ShortcutType.Action)
                                    },
                                ),
                            color =
                                if (!module.remove && isEnabled) {
                                    ButtonDefaults.filledTonalButtonColors().containerColor
                                } else {
                                    ButtonDefaults.filledTonalButtonColors().disabledContainerColor
                                },
                            contentColor = if (!module.remove && isEnabled) {
                                ButtonDefaults.filledTonalButtonColors().contentColor
                            } else {
                                ButtonDefaults.filledTonalButtonColors().disabledContentColor
                            },
                        ) {
                            Icon(
                                modifier = Modifier
                                    .size(20.dp)
                                    .padding(12.dp, 7.dp, 12.dp, 7.dp),
                                imageVector = Icons.TwoTone.PlayArrow,
                                contentDescription = null,
                            )
                        }
                    }

                    if (module.hasWebUi) {
                        Surface(
                            modifier = Modifier
                                .padding(vertical = 5.dp)
                                .defaultMinSize(minWidth = 52.dp, minHeight = 32.dp)
                                .clip(ButtonDefaults.filledTonalShape)
                                .combinedClickable(
                                    onClick = { onClick(module) },
                                    onLongClick = {
                                        onModuleAddShortcut(module, ShortcutType.WebUI)
                                    },
                                ),
                            color =
                                if (!module.remove && isEnabled) {
                                    ButtonDefaults.filledTonalButtonColors().containerColor
                                } else {
                                    ButtonDefaults.filledTonalButtonColors().disabledContainerColor
                                },
                            contentColor = if (!module.remove && isEnabled) {
                                ButtonDefaults.filledTonalButtonColors().contentColor
                            } else {
                                ButtonDefaults.filledTonalButtonColors().disabledContentColor
                            },
                        ) {
                            Icon(
                                modifier = Modifier
                                    .size(20.dp)
                                    .padding(12.dp, 7.dp, 12.dp, 7.dp),
                                imageVector = Icons.AutoMirrored.TwoTone.Wysiwyg,
                                contentDescription = null,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f, true))

                    if (homeCustomization.enableModuleBanner) {
                        Surface(
                            modifier = Modifier
                                .padding(vertical = 5.dp)
                                .defaultMinSize(minWidth = 52.dp, minHeight = 32.dp)
                                .clip(ButtonDefaults.filledTonalShape)
                                .clickable {
                                    onEditBanner(module)
                                },
                            color = ButtonDefaults.filledTonalButtonColors().containerColor,
                            contentColor = ButtonDefaults.filledTonalButtonColors().contentColor,
                        ) {
                            Icon(
                                modifier = Modifier
                                    .size(20.dp)
                                    .padding(12.dp, 7.dp, 12.dp, 7.dp),
                                imageVector = Icons.TwoTone.Image,
                                contentDescription = customText(CustomTextKey.MODULE_BANNER_TITLE),
                            )
                        }
                    }

                    if (updateUrl.isNotEmpty()) {
                        Surface(
                            modifier = Modifier
                                .padding(vertical = 5.dp)
                                .defaultMinSize(minWidth = 52.dp, minHeight = 32.dp)
                                .clip(ButtonDefaults.filledTonalShape)
                                .clickable {
                                    onUpdate(module)
                                },
                            color =
                                if (!module.remove) {
                                    ButtonDefaults.filledTonalButtonColors().containerColor
                                } else {
                                    ButtonDefaults.filledTonalButtonColors().disabledContainerColor
                                },
                            contentColor = if (!module.remove) {
                                ButtonDefaults.filledTonalButtonColors().contentColor
                            } else {
                                ButtonDefaults.filledTonalButtonColors().disabledContentColor
                            },
                        ) {
                            Icon(
                                modifier = Modifier
                                    .size(20.dp)
                                    .padding(12.dp, 7.dp, 12.dp, 7.dp),
                                imageVector = Icons.TwoTone.Download,
                                contentDescription = null,
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .padding(vertical = 5.dp)
                            .defaultMinSize(minWidth = 52.dp, minHeight = 32.dp)
                            .clip(ButtonDefaults.filledTonalShape)
                            .clickable {
                                onUninstallClicked(module)
                            },
                        color =
                            if (!module.remove) {
                                ButtonDefaults.filledTonalButtonColors().containerColor
                            } else {
                                ButtonDefaults.filledTonalButtonColors().disabledContainerColor
                            },
                        contentColor = if (!module.remove) {
                            ButtonDefaults.filledTonalButtonColors().contentColor
                        } else {
                            ButtonDefaults.filledTonalButtonColors().disabledContentColor
                        },
                    ) {
                        if (!module.remove) {
                            Icon(
                                modifier = Modifier
                                    .size(20.dp)
                                    .padding(12.dp, 7.dp, 12.dp, 7.dp),
                                imageVector = Icons.TwoTone.Delete,
                                contentDescription = null,
                            )
                        } else {
                            Icon(
                                modifier = Modifier
                                    .size(20.dp)
                                    .rotate(180f)
                                    .padding(12.dp, 7.dp, 12.dp, 7.dp),
                                imageVector = Icons.TwoTone.Refresh,
                                contentDescription = null,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun ModuleItemPreview() {
    val module = InstalledModule(
        id = "id",
        name = "name",
        version = "version",
        versionCode = 1,
        author = "author",
        description = "I am a test module and i do nothing but show a very long description",
        enabled = true,
        update = true,
        remove = false,
        updateJson = "",
        hasWebUi = true,
        hasActionScript = true,
        metamodule = true,
        actionIconPath = null,
        webUiIconPath = null,
        dirId = "dirId",
        moduleUpdate = null,
    )
    ModuleItem(
        koinViewModel<ModuleViewModel>(),
        module,
        emptyMap(),
        "",
        {},
        { true },
        {},
        {},
        { _, _ -> },
        {},
        false,
    )
}
