package org.uranxsu.manager.domain.usecase

import org.uranxsu.manager.data.flash.FlashRepository
import org.uranxsu.manager.domain.model.FlashOperation

class ExecuteFlashOperationUseCase(private val repository: FlashRepository) {
    operator fun invoke(operation: FlashOperation) = repository.execute(operation)
}
