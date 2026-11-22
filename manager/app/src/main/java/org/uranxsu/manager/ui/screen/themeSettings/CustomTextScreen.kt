package org.uranxsu.manager.ui.screen.themeSettings

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Clear
import androidx.compose.material.icons.twotone.Edit
import androidx.compose.material.icons.twotone.RestartAlt
import androidx.compose.material.icons.twotone.Search
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.uranxsu.manager.R
import org.uranxsu.manager.ui.component.ConfirmResult
import org.uranxsu.manager.ui.component.SwipeableSnackbarHost
import org.uranxsu.manager.ui.component.rememberConfirmDialog
import org.uranxsu.manager.ui.component.settings.AppBackButton
import org.uranxsu.manager.ui.component.settings.SegmentedColumn
import org.uranxsu.manager.ui.component.settings.SettingsBaseWidget
import org.uranxsu.manager.ui.navigation.LocalNavigator
import org.uranxsu.manager.ui.theme.CardConfig
import org.uranxsu.manager.ui.theme.ThemeConfig
import org.uranxsu.manager.ui.theme.blurEffect
import org.uranxsu.manager.ui.theme.blurSource
import org.uranxsu.manager.ui.util.CustomTextConfig
import org.uranxsu.manager.ui.util.CustomTextGroup
import org.uranxsu.manager.ui.util.CustomTextKey
import org.uranxsu.manager.ui.util.LocalCustomTexts
import org.uranxsu.manager.ui.util.LocalSnackbarHost
import org.uranxsu.manager.ui.util.adaptiveScaffoldWindowInsets
import org.uranxsu.manager.ui.util.customText
import org.uranxsu.manager.ui.util.customTextSummary
import org.uranxsu.manager.ui.util.showReplacingSnackbar

@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CustomTextScreen() {
    val context = LocalContext.current
    val themeConfig: ThemeConfig = koinInject()
    val cardConfig: CardConfig = koinInject()
    val customTextConfig: CustomTextConfig = koinInject()
    val customTexts = LocalCustomTexts.current
    val navigator = LocalNavigator.current
    val snackbarHost = LocalSnackbarHost.current
    val confirmDialog = rememberConfirmDialog()
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    var editingKey by remember { mutableStateOf<CustomTextKey?>(null) }
    var editingValue by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }

    val resetAllTitle = stringResource(R.string.custom_texts_reset_all)
    val resetAllMessage = stringResource(R.string.custom_texts_reset_all_confirm)
    val resetAllDone = stringResource(R.string.custom_texts_reset_done)
    val confirmLabel = stringResource(R.string.confirm)
    val cancelLabel = stringResource(android.R.string.cancel)

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                modifier = Modifier.blurEffect(),
                title = { Text(customText(CustomTextKey.PAGE_CUSTOM_TEXTS)) },
                navigationIcon = { AppBackButton(onClick = { navigator.pop() }) },
                actions = {
                    if (customTexts.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    val result = confirmDialog.awaitConfirm(
                                        title = resetAllTitle,
                                        content = resetAllMessage,
                                        confirm = confirmLabel,
                                        dismiss = cancelLabel,
                                    )
                                    if (result == ConfirmResult.Confirmed) {
                                        customTextConfig.resetAll()
                                        snackbarHost.showReplacingSnackbar(resetAllDone)
                                    }
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.TwoTone.RestartAlt,
                                contentDescription = resetAllTitle,
                            )
                        }
                    }
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .blurSource(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 5.dp,
                bottom = innerPadding.calculateBottomPadding() + 16.dp,
            ),
        ) {
            item(key = "search_box") {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.custom_texts_search_placeholder)) },
                    leadingIcon = {
                        Icon(imageVector = Icons.TwoTone.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(imageVector = Icons.TwoTone.Clear, contentDescription = null)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }

            val query = searchQuery.trim().lowercase()

            CustomTextGroup.entries.forEach { group ->
                val allGroupKeys = CustomTextKey.entries.filter { it.group == group }
                val keys = if (query.isEmpty()) {
                    allGroupKeys
                } else {
                    allGroupKeys.filter { textKey ->
                        val name = runCatching { context.getString(textKey.nameRes) }.getOrNull().orEmpty().lowercase()
                        val defaultVal = runCatching { context.getString(textKey.defaultRes) }.getOrNull().orEmpty().lowercase()
                        val customVal = customTexts[textKey.key].orEmpty().lowercase()
                        textKey.key.lowercase().contains(query) ||
                            name.contains(query) ||
                            defaultVal.contains(query) ||
                            customVal.contains(query)
                    }
                }
                if (keys.isNotEmpty()) {
                    item(key = "group_${group.name}") {
                        SegmentedColumn(title = stringResource(group.titleRes)) {
                            keys.forEach { textKey ->
                                item {
                                    val currentVal = customTexts[textKey.key]
                                    SettingsBaseWidget(
                                        icon = Icons.TwoTone.Edit,
                                        title = stringResource(textKey.nameRes),
                                        description = customTextSummary(textKey, currentVal),
                                        onClick = {
                                            editingKey = textKey
                                            editingValue = currentVal.orEmpty()
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editingKey?.let { key ->
        val defaultText = stringResource(key.defaultRes)
        AlertDialog(
            onDismissRequest = { editingKey = null },
            title = { Text(stringResource(key.nameRes)) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.custom_text_default_value, defaultText),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = editingValue,
                        onValueChange = { editingValue = it },
                        placeholder = { Text(defaultText) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        customTextConfig.set(key.key, editingValue)
                        editingKey = null
                    },
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                if (!customTexts[key.key].isNullOrBlank()) {
                    TextButton(
                        onClick = {
                            customTextConfig.reset(key.key)
                            editingKey = null
                        },
                    ) {
                        Text(stringResource(R.string.custom_text_reset_single))
                    }
                } else {
                    TextButton(onClick = { editingKey = null }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            },
        )
    }
}
