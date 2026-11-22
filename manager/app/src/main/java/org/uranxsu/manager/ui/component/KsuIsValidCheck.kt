package org.uranxsu.manager.ui.component

import androidx.compose.runtime.Composable
import org.uranxsu.manager.domain.model.KernelStatus

@Composable
inline fun KsuIsValid(
    status: KernelStatus,
    content: @Composable () -> Unit,
) {
    if (status.isFullFeatured) {
        content()
    }
}
