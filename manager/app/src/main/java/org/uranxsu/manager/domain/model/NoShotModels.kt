package org.uranxsu.manager.domain.model

data class NoShotSettings(
    val flagSecure: Boolean = true,
    val surfaceSecure: Boolean = true,
    val detectGuard: Boolean = true,
    val systemHook: Boolean = true,
    val captureGuard: String = "off",
    val log: Boolean = true,
)

data class NoShotTarget(
    val packageName: String,
    val user: Int? = null,
    val mode: String = "system",
)

data class NoShotStatus(
    val enabled: Boolean = false,
    val standaloneConflict: Boolean = false,
    val zygiskEnabled: Boolean = false,
    val zygiskRunning: Boolean = false,
    val loadedInZygisk: Boolean = false,
    val runtimeMode: String = "client",
    val bootloopDisabled: Boolean = false,
    val attemptCount: Int = 0,
    val disableSystem: Boolean = false,
    val captureGuardState: String = "unknown",
    val stateLog: String = "",
    val settings: NoShotSettings = NoShotSettings(),
    val targets: List<NoShotTarget> = emptyList(),
)

data class NoShotAppCandidate(
    val packageName: String,
    val appLabel: String,
    val uid: Int,
    val isSystem: Boolean,
    val enabled: Boolean,
    val mode: String,
)
