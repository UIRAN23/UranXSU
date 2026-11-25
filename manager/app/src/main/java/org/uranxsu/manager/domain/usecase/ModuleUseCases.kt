package org.uranxsu.manager.domain.usecase

import org.uranxsu.manager.data.module.ModuleRepository
import org.uranxsu.manager.data.network.NetworkRequestRepository

class FetchRemoteTextUseCase(private val repository: NetworkRequestRepository) {
    suspend operator fun invoke(url: String) = repository.fetch(url)
}

class ObserveInstalledModulesUseCase(private val repository: ModuleRepository) {
    operator fun invoke() = repository.installedModules
}

class RefreshInstalledModulesUseCase(private val repository: ModuleRepository) {
    suspend operator fun invoke(
        manual: Boolean,
        checkUpdates: Boolean,
    ): Result<Boolean> = repository.refreshInstalledModules(manual, checkUpdates)
}

class CalculateInstalledModuleSizeUseCase(private val repository: ModuleRepository) {
    suspend operator fun invoke(moduleId: String) = repository.calculateInstalledModuleSize(moduleId)
}

class UpdateCachedModuleEnabledUseCase(private val repository: ModuleRepository) {
    operator fun invoke(moduleId: String, enabled: Boolean) = repository.updateCachedEnabled(moduleId, enabled)
}
