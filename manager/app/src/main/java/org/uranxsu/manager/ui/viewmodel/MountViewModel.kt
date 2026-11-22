package org.uranxsu.manager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.uranxsu.manager.domain.model.MountOverallStatus
import org.uranxsu.manager.domain.model.NoMountAppCandidate
import org.uranxsu.manager.domain.model.NoMountConfig
import org.uranxsu.manager.domain.model.NoMountStatus
import org.uranxsu.manager.domain.usecase.MountUseCase

data class MountSettingsUiState(
    val status: MountOverallStatus = MountOverallStatus(),
    val isRefreshing: Boolean = false,
)

class MountSettingsViewModel(
    private val mountUseCase: MountUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(MountSettingsUiState())
    val state: StateFlow<MountSettingsUiState> = mutableState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isRefreshing = true)
            val status = runCatching { mountUseCase.getOverallStatus() }
                .getOrDefault(MountOverallStatus())
            mutableState.value = MountSettingsUiState(status = status, isRefreshing = false)
        }
    }

    suspend fun setMode(mode: String): Boolean {
        val ok = runCatching { mountUseCase.setMountMode(mode) }.getOrDefault(false)
        refresh()
        return ok
    }
}

data class NoMountUiState(
    val status: NoMountStatus = NoMountStatus(),
    val apps: List<NoMountAppCandidate> = emptyList(),
    val isRefreshing: Boolean = false,
)

class NoMountViewModel(
    private val mountUseCase: MountUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(NoMountUiState())
    val state: StateFlow<NoMountUiState> = mutableState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isRefreshing = true)
            val status = runCatching { mountUseCase.getNoMountStatus() }
                .getOrDefault(NoMountStatus())
            mutableState.value = mutableState.value.copy(status = status, isRefreshing = false)
        }
    }

    fun loadApps() {
        viewModelScope.launch {
            val apps = runCatching { mountUseCase.loadInstalledAppsForExclusion() }
                .getOrDefault(emptyList())
            mutableState.value = mutableState.value.copy(apps = apps)
        }
    }

    suspend fun loadLkm(kmi: String? = null): Boolean {
        val ok = runCatching { mountUseCase.loadNoMountLkm(kmi) }.getOrDefault(false)
        refresh()
        return ok
    }

    suspend fun reloadAll(): Boolean {
        val ok = runCatching { mountUseCase.reloadNoMountAll() }.getOrDefault(false)
        refresh()
        return ok
    }

    suspend fun clearRules(): Boolean {
        val ok = runCatching { mountUseCase.clearNoMountRules() }.getOrDefault(false)
        refresh()
        return ok
    }

    suspend fun toggleModule(id: String, load: Boolean): Boolean {
        val ok = runCatching {
            if (load) mountUseCase.loadNoMountModule(id) else mountUseCase.unloadNoMountModule(id)
        }.getOrDefault(false)
        refresh()
        return ok
    }

    suspend fun addUid(uid: Int, pkg: String? = null): Boolean {
        val ok = runCatching { mountUseCase.addNoMountUid(uid, pkg) }.getOrDefault(false)
        refresh()
        return ok
    }

    suspend fun removeUid(uid: Int): Boolean {
        val ok = runCatching { mountUseCase.removeNoMountUid(uid) }.getOrDefault(false)
        refresh()
        return ok
    }

    suspend fun updateConfig(
        config: NoMountConfig,
        clearBootloopDisable: Boolean = false,
    ): Boolean {
        val ok = runCatching {
            mountUseCase.setNoMountConfig(config, clearBootloopDisable)
        }.getOrDefault(false)
        refresh()
        return ok
    }

    suspend fun loadRulesJson(): String = runCatching { mountUseCase.getNoMountRulesJson() }.getOrDefault("")
}
