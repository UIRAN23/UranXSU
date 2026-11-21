package org.uranxsu.manager.domain.usecase

import org.uranxsu.manager.data.update.ManagerUpdateRepository
import org.uranxsu.manager.domain.model.ManagerUpdateChannel
import org.uranxsu.manager.domain.model.ManagerUpdateInfo

class CheckManagerUpdateUseCase(
    private val repository: ManagerUpdateRepository,
) {
    suspend operator fun invoke(channel: ManagerUpdateChannel): ManagerUpdateInfo? = when (channel) {
        ManagerUpdateChannel.STABLE -> repository.checkStableUpdate()
        ManagerUpdateChannel.BETA -> repository.checkBetaUpdate()
    }
}
