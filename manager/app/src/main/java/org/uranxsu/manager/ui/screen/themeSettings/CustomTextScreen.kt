package org.uranxsu.manager.ui.screen.themeSettings

import android.annotation.SuppressLint
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Check
import androidx.compose.material.icons.twotone.Clear
import androidx.compose.material.icons.twotone.Edit
import androidx.compose.material.icons.twotone.RestartAlt
import androidx.compose.material.icons.twotone.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import org.uranxsu.manager.ui.theme.renderBackgroundBlur
import org.uranxsu.manager.ui.util.CustomTextConfig
import org.uranxsu.manager.ui.util.CustomTextGroup
import org.uranxsu.manager.ui.util.CustomTextKey
import org.uranxsu.manager.ui.util.LocalCustomTexts
import org.uranxsu.manager.ui.util.LocalSnackbarHost
import org.uranxsu.manager.ui.util.adaptiveScaffoldWindowInsets
import org.uranxsu.manager.ui.util.customText
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
    var selectedGroup by remember { mutableStateOf<CustomTextGroup?>(null) }
    var onlyModified by remember { mutableStateOf(false) }

    val resetAllTitle = stringResource(R.string.custom_texts_reset_all)
    val resetAllMessage = stringResource(R.string.custom_texts_reset_all_confirm)
    val resetDoneMsg = stringResource(R.string.custom_texts_reset_done)
    val resetGroupLabel = stringResource(R.string.custom_texts_reset_group)
    val confirmLabel = stringResource(R.string.confirm)
    val cancelLabel = stringResource(android.R.string.cancel)

    val totalCount = CustomTextKey.entries.size
    val modifiedCount = remember(customTexts) {
        CustomTextKey.entries.count { !customTexts[it.key].isNullOrBlank() }
    }
    val modifiedByGroup = remember(customTexts) {
        CustomTextGroup.entries.associateWith { group ->
            CustomTextKey.entries.count { it.group == group && !customTexts[it.key].isNullOrBlank() }
        }
    }

    fun confirmResetAll() {
        scope.launch {
            val result = confirmDialog.awaitConfirm(
                title = resetAllTitle,
                content = resetAllMessage,
                confirm = confirmLabel,
                dismiss = cancelLabel,
            )
            if (result == ConfirmResult.Confirmed) {
                customTextConfig.resetAll()
                snackbarHost.showReplacingSnackbar(resetDoneMsg)
            }
        }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                modifier = Modifier.blurEffect(),
                title = { Text(customText(CustomTextKey.PAGE_CUSTOM_TEXTS)) },
                navigationIcon = { AppBackButton(onClick = { navigator.pop() }) },
                actions = {
                    if (modifiedCount > 0) {
                        IconButton(onClick = ::confirmResetAll) {
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
        val query = searchQuery.trim().lowercase()
        val visibleGroups = remember(selectedGroup, onlyModified, query, customTexts) {
            val groupsToScan = selectedGroup?.let { listOf(it) } ?: CustomTextGroup.entries
            groupsToScan.mapNotNull { group ->
                val keys = CustomTextKey.entries.filter { textKey ->
                    if (textKey.group != group) return@filter false
                    val customVal = customTexts[textKey.key]
                    if (onlyModified && customVal.isNullOrBlank()) return@filter false
                    if (query.isEmpty()) return@filter true
                    val name = runCatching { context.getString(textKey.nameRes) }.getOrNull().orEmpty().lowercase()
                    val defaultVal = runCatching { context.getString(textKey.defaultRes) }.getOrNull().orEmpty().lowercase()
                    textKey.key.lowercase().contains(query) ||
                        name.contains(query) ||
                        defaultVal.contains(query) ||
                        customVal.orEmpty().lowercase().contains(query)
                }
                if (keys.isEmpty()) null else group to keys
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .blurSource(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 6.dp,
                bottom = innerPadding.calculateBottomPadding() + 16.dp,
            ),
        ) {
            item(key = "summary_card") {
                val progress = if (totalCount > 0) modifiedCount.toFloat() / totalCount.toFloat() else 0f
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .renderBackgroundBlur(),
                    shape = RoundedCornerShape(20.dp),
                    color = if (themeConfig.isEnableBlurExp) {
                        Color.Transparent
                    } else {
                        MaterialTheme.colorScheme.surfaceBright.copy(alpha = cardConfig.cardAlpha)
                    },
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.custom_texts_progress, modifiedCount, totalCount),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            if (modifiedCount > 0) {
                                TextButton(onClick = ::confirmResetAll) {
                                    Text(stringResource(R.string.custom_text_reset_single))
                                }
                            }
                        }
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

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

            item(key = "filter_chips") {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "chip_only_modified") {
                        FilterChip(
                            selected = onlyModified,
                            onClick = { onlyModified = !onlyModified },
                            label = {
                                Text(stringResource(R.string.custom_texts_filter_modified, modifiedCount))
                            },
                            leadingIcon = if (onlyModified) {
                                {
                                    Icon(
                                        imageVector = Icons.TwoTone.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            } else {
                                null
                            },
                        )
                    }
                    item(key = "chip_all_groups") {
                        FilterChip(
                            selected = selectedGroup == null,
                            onClick = { selectedGroup = null },
                            label = { Text(stringResource(R.string.custom_texts_filter_all, totalCount)) },
                        )
                    }
                    items(CustomTextGroup.entries, key = { it.name }) { group ->
                        val groupModified = modifiedByGroup[group] ?: 0
                        val groupLabel = stringResource(group.titleRes)
                        FilterChip(
                            selected = selectedGroup == group,
                            onClick = {
                                selectedGroup = if (selectedGroup == group) null else group
                            },
                            label = {
                                Text(
                                    if (groupModified > 0) "$groupLabel ($groupModified)" else groupLabel,
                                )
                            },
                        )
                    }
                }
            }

            if (visibleGroups.isEmpty()) {
                item(key = "empty_filter") {
                    Text(
                        text = stringResource(R.string.custom_texts_empty_filter),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                    )
                }
            } else {
                visibleGroups.forEach { (group, keys) ->
                    item(key = "group_${group.name}") {
                        val groupTitle = stringResource(group.titleRes)
                        val groupModified = modifiedByGroup[group] ?: 0
                        val groupResetConfirmMsg = stringResource(R.string.custom_texts_reset_group_confirm, groupTitle)
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = if (groupModified > 0) "$groupTitle ($groupModified)" else groupTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f),
                                )
                                if (groupModified > 0) {
                                    TextButton(
                                        onClick = {
                                            scope.launch {
                                                val res = confirmDialog.awaitConfirm(
                                                    title = resetGroupLabel,
                                                    content = groupResetConfirmMsg,
                                                    confirm = confirmLabel,
                                                    dismiss = cancelLabel,
                                                )
                                                if (res == ConfirmResult.Confirmed) {
                                                    customTextConfig.resetGroup(group)
                                                    snackbarHost.showReplacingSnackbar(resetDoneMsg)
                                                }
                                            }
                                        },
                                    ) {
                                        Text(resetGroupLabel)
                                    }
                                }
                            }
                            SegmentedColumn {
                                keys.forEach { textKey ->
                                    item(key = textKey.key) {
                                        val customVal = customTexts[textKey.key]?.takeIf { it.isNotBlank() }
                                        val defaultVal = stringResource(textKey.defaultRes)
                                        val summary = if (customVal != null) {
                                            stringResource(R.string.custom_text_changed_format, defaultVal, customVal)
                                        } else {
                                            stringResource(R.string.custom_text_default_value, defaultVal)
                                        }
                                        SettingsBaseWidget(
                                            icon = Icons.TwoTone.Edit,
                                            title = stringResource(textKey.nameRes),
                                            description = summary,
                                            onClick = {
                                                editingKey = textKey
                                                editingValue = customVal.orEmpty()
                                            },
                                        ) {
                                            if (customVal != null) {
                                                IconButton(
                                                    onClick = {
                                                        customTextConfig.reset(textKey.key)
                                                    },
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.TwoTone.RestartAlt,
                                                        contentDescription = stringResource(R.string.custom_text_reset_single),
                                                    )
                                                }
                                            }
                                        }
                                    }
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
