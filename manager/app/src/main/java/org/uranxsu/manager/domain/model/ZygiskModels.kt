package org.uranxsu.manager.domain.model

data class ZygiskProcessInfo(
    val name: String = "",
    val pid: Int = 0,
)

data class ZygiskZygoteState(
    val abi: String = "",
    val status: String = "",
)

data class ZygiskStatus(
    val enabled: Boolean = false,
    val monitorRunning: Boolean = false,
    val daemonRunning: Boolean = false,
    val zygoteInjected: Boolean = false,
    val fullyActive: Boolean = false,
    val zygotes: List<ZygiskZygoteState> = emptyList(),
    val monitor: ZygiskProcessInfo? = null,
    val daemons: List<ZygiskProcessInfo> = emptyList(),
    val version: String = "",
    val versionCode: String = "",
)

data class ZygiskImplInfo(
    val state: String = "none",
    val id: String = "",
    val name: String = "",
    val version: String = "",
    val providers: List<String> = emptyList(),
)

data class ZygiskConfig(
    val anonymousMemory: Boolean = false,
    val customLinker: Boolean = false,
    val pltEngine: String = "yukiplt",
)

data class ZygiskModuleProcess(
    val name: String = "",
    val pid: Int = 0,
    val kind: String = "",
)

data class ZygiskInjectedModule(
    val id: String = "",
    val name: String = "",
    val version: String = "",
    val type: String = "zygisk",
    val abi: List<String> = emptyList(),
    val targets: List<String> = emptyList(),
    val companion: Boolean = false,
    val state: String = "unknown",
    val processes: List<ZygiskModuleProcess> = emptyList(),
)
