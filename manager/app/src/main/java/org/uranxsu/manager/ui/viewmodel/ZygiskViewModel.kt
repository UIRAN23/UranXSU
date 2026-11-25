package org.uranxsu.manager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.uranxsu.manager.domain.model.ZygiskConfig
import org.uranxsu.manager.domain.model.ZygiskImplInfo
import org.uranxsu.manager.domain.model.ZygiskInjectedModule
import org.uranxsu.manager.domain.model.ZygiskStatus
import org.uranxsu.manager.domain.usecase.RebootUseCase
import org.uranxsu.manager.domain.usecase.ZygiskUseCase

private val zygiskSwitchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

data class BuiltinZygiskUiState(
    val status: ZygiskStatus = ZygiskStatus(),
    val info: ZygiskImplInfo = ZygiskImplInfo(),
    val config: ZygiskConfig = ZygiskConfig(),
    val modules: List<ZygiskInjectedModule> = emptyList(),
    val isRefreshing: Boolean = false,
)

class BuiltinZygiskViewModel(
    private val zygiskUseCase: ZygiskUseCase,
    private val rebootUseCase: RebootUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(BuiltinZygiskUiState())
    val state: StateFlow<BuiltinZygiskUiState> = mutableState.asStateFlow()
    private var pendingSwitch: Deferred<Boolean>? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isRefreshing = true)
            val statusDeferred = async { runCatching { zygiskUseCase.getStatus() }.getOrDefault(ZygiskStatus()) }
            val infoDeferred = async { runCatching { zygiskUseCase.getInfo() }.getOrDefault(ZygiskImplInfo()) }
            val configDeferred = async { runCatching { zygiskUseCase.getConfig() }.getOrDefault(ZygiskConfig()) }
            val modulesDeferred = async { runCatching { zygiskUseCase.getModules() }.getOrDefault(emptyList()) }
            mutableState.value = BuiltinZygiskUiState(
                status = statusDeferred.await(),
                info = infoDeferred.await(),
                config = configDeferred.await(),
                modules = modulesDeferred.await(),
                isRefreshing = false,
            )
        }
    }

    suspend fun setEnabled(enabled: Boolean): Boolean {
        val ok = runCatching { zygiskUseCase.setEnabled(enabled) }.getOrDefault(false)
        refresh()
        return ok
    }

    suspend fun switchProvider(target: String): Boolean {
        val active = pendingSwitch
        if (active != null && active.isActive) {
            return active.await()
        }
        val deferred = zygiskSwitchScope.async {
            runCatching { zygiskUseCase.switchProvider(target) }.getOrDefault(false)
        }
        pendingSwitch = deferred
        return try {
            val ok = deferred.await()
            refresh()
            ok
        } finally {
            if (pendingSwitch === deferred) {
                pendingSwitch = null
            }
        }
    }

    suspend fun awaitPendingSwitch(): Boolean = pendingSwitch?.let { deferred ->
        runCatching { deferred.await() }.getOrDefault(false)
    } ?: true

    suspend fun rearm(): Boolean {
        val ok = runCatching { zygiskUseCase.rearm() }.getOrDefault(false)
        refresh()
        return ok
    }

    suspend fun setConfig(key: String, value: String): Boolean {
        val ok = runCatching { zygiskUseCase.setConfig(key, value) }.getOrDefault(false)
        refresh()
        return ok
    }

    fun softReboot() {
        viewModelScope.launch {
            if (awaitPendingSwitch()) {
                runCatching { rebootUseCase("soft_reboot") }
            }
        }
    }
}
