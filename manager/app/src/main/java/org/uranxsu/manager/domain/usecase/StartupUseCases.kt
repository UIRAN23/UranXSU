package org.uranxsu.manager.domain.usecase

import org.uranxsu.manager.data.startup.StartupRepository

class ObserveStartupStateUseCase(
    private val repository: StartupRepository,
) {
    operator fun invoke() = repository.state
}
