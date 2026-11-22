package org.uranxsu.manager.domain.model

data class MountOverallStatus(
    val mode: String = "no_mount",
    val externalMetamoduleId: String = "",
    val externalMetamoduleName: String = "",
    val externalMetamoduleEnabled: Boolean = false,
    val externalMetamoduleIsNomount: Boolean = false,
    val nomountDriverReady: Boolean = false,
    val nomountDriverVersion: String = "",
    val nomountRuleCount: Int = 0,
)

data class NoMountConfig(
    val autoLoadLkm: Boolean = true,
    val kmiOverride: String = "",
    val blockIsolatedUids: Boolean = false,
    val dumpRulesOnBoot: Boolean = false,
    val bootloopGuard: Boolean = true,
    val skippedModules: List<String> = emptyList(),
)

data class NoMountExclusionEntry(
    val uid: String = "",
    val pkg: String = "",
)

data class NoMountModuleInfo(
    val id: String = "",
    val name: String = "",
    val enabled: Boolean = true,
    val skipped: Boolean = false,
    val injectedFiles: Int = 0,
    val partitions: List<String> = emptyList(),
)

data class NoMountStatus(
    val driverReady: Boolean = false,
    val driverVersion: String = "",
    val activeKmi: String = "",
    val detectedKmi: String = "",
    val availableKmis: List<String> = emptyList(),
    val ruleCount: Int = 0,
    val uidCount: Int = 0,
    val blockIsolated: Boolean = false,
    val bootloopDisabled: Boolean = false,
    val config: NoMountConfig = NoMountConfig(),
    val exclusions: List<NoMountExclusionEntry> = emptyList(),
    val modules: List<NoMountModuleInfo> = emptyList(),
)

data class NoMountAppCandidate(
    val uid: Int,
    val packageName: String,
    val label: String,
)
