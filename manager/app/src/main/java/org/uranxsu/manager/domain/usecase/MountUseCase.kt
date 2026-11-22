package org.uranxsu.manager.domain.usecase

import org.uranxsu.manager.data.mount.MountRepository
import org.uranxsu.manager.domain.model.NoMountConfig

class MountUseCase(
    private val repository: MountRepository,
) {
    suspend fun getOverallStatus() = repository.getOverallStatus()
    suspend fun setMountMode(mode: String) = repository.setMountMode(mode)

    suspend fun getNoMountStatus() = repository.getNoMountStatus()
    suspend fun loadNoMountLkm(kmi: String? = null) = repository.loadNoMountLkm(kmi)
    suspend fun reloadNoMountAll() = repository.reloadNoMountAll()
    suspend fun clearNoMountRules() = repository.clearNoMountRules()
    suspend fun clearNoMountAll() = repository.clearNoMountAll()
    suspend fun loadNoMountModule(id: String) = repository.loadNoMountModule(id)
    suspend fun unloadNoMountModule(id: String) = repository.unloadNoMountModule(id)
    suspend fun getNoMountRulesJson() = repository.getNoMountRulesJson()
    suspend fun addNoMountUid(uid: Int, pkg: String? = null) = repository.addNoMountUid(uid, pkg)
    suspend fun removeNoMountUid(uid: Int) = repository.removeNoMountUid(uid)
    suspend fun setNoMountConfig(
        config: NoMountConfig,
        clearBootloopDisable: Boolean = false,
    ) = repository.setNoMountConfig(config, clearBootloopDisable)
    suspend fun loadInstalledAppsForExclusion(): List<org.uranxsu.manager.domain.model.NoMountAppCandidate> = repository.getInstalledAppCandidates()
}
