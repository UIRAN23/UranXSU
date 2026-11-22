package org.uranxsu.manager.domain.usecase

import org.uranxsu.manager.data.zygisk.ZygiskRepository

class ZygiskUseCase(
    private val repository: ZygiskRepository,
) {
    suspend fun getStatus() = repository.getStatus()
    suspend fun getInfo() = repository.getInfo()
    suspend fun getConfig() = repository.getConfig()
    suspend fun setConfig(key: String, value: String) = repository.setConfig(key, value)
    suspend fun getModules() = repository.getModules()
    suspend fun setEnabled(enabled: Boolean) = repository.setEnabled(enabled)
    suspend fun switchProvider(target: String) = repository.switchProvider(target)
    suspend fun rearm() = repository.rearm()
}
