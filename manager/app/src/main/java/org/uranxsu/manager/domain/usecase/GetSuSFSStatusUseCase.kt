package org.uranxsu.manager.domain.usecase

import org.uranxsu.manager.data.susfs.SuSFSRepository

class GetSuSFSStatusUseCase(private val repository: SuSFSRepository) {
    suspend operator fun invoke() = repository.getStatus()
}
