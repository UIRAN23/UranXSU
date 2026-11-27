package org.uranxsu.manager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.uranxsu.manager.data.noshot.NoShotRepository
import org.uranxsu.manager.domain.model.NoShotAppCandidate
import org.uranxsu.manager.domain.model.NoShotSettings
import org.uranxsu.manager.domain.model.NoShotStatus

data class NoShotUiState(
    val status: NoShotStatus = NoShotStatus(),
    val apps: List<NoShotAppCandidate> = emptyList(),
    val searchQuery: String = "",
    val showSystemApps: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingApps: Boolean = false,
)

class NoShotViewModel(
    private val repository: NoShotRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(NoShotUiState())
    val state: StateFlow<NoShotUiState> = mutableState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            mutableState.update { it.copy(isRefreshing = true) }
            val status = repository.getStatus()
            mutableState.update { it.copy(status = status, isRefreshing = false, isLoadingApps = true) }
            val apps = repository.loadAppCandidates(status.targets)
            mutableState.update { it.copy(apps = apps, isLoadingApps = false) }
        }
    }

    fun setSearch(query: String) {
        mutableState.update { it.copy(searchQuery = query) }
    }

    fun setShowSystemApps(show: Boolean) {
        mutableState.update { it.copy(showSystemApps = show) }
    }

    suspend fun toggleEnabled(enabled: Boolean): Boolean {
        val (ok, updatedStatus) = repository.setEnabled(enabled)
        mutableState.update { it.copy(status = updatedStatus) }
        return ok
    }

    suspend fun updateSettings(transform: (NoShotSettings) -> NoShotSettings): Boolean {
        val updated = transform(mutableState.value.status.settings)
        val ok = repository.updateSettings(updated)
        val latest = repository.getStatus()
        mutableState.update { it.copy(status = latest) }
        return ok
    }

    suspend fun setAppEnabled(packageName: String, enabled: Boolean, mode: String = "system"): Boolean {
        val (ok, targets) = if (enabled) {
            repository.addTarget(packageName, mode)
        } else {
            repository.removeTarget(packageName)
        }
        val latestStatus = if (targets.isNotEmpty() || ok) {
            mutableState.value.status.copy(targets = targets)
        } else {
            repository.getStatus()
        }
        val apps = repository.loadAppCandidates(latestStatus.targets)
        mutableState.update { it.copy(status = latestStatus, apps = apps) }
        return ok
    }

    suspend fun setAppMode(packageName: String, mode: String): Boolean {
        val (ok, targets) = repository.addTarget(packageName, mode)
        val latestStatus = mutableState.value.status.copy(targets = targets)
        val apps = repository.loadAppCandidates(latestStatus.targets)
        mutableState.update { it.copy(status = latestStatus, apps = apps) }
        return ok
    }

    suspend fun resetGuard(): Boolean {
        val (ok, updatedStatus) = repository.resetGuard()
        mutableState.update { it.copy(status = updatedStatus) }
        return ok
    }

    suspend fun syncPlan(): Boolean {
        val (ok, updatedStatus) = repository.syncPlan()
        mutableState.update { it.copy(status = updatedStatus) }
        return ok
    }
}
