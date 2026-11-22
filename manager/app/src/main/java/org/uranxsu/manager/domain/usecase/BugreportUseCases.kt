package org.uranxsu.manager.domain.usecase

import java.io.File
import org.uranxsu.manager.data.logging.BugreportRepository

class GenerateBugreportUseCase(
    private val repository: BugreportRepository,
) {
    operator fun invoke(): File = repository.create()
}
