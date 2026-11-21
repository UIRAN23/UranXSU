package org.uranxsu.manager.domain.model

data class WebUiModuleInfo(
    val name: String,
    val enabled: Boolean,
    val remove: Boolean,
    val hasWebUi: Boolean,
)
