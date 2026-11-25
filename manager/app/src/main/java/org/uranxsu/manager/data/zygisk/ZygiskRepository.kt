package org.uranxsu.manager.data.zygisk

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.uranxsu.manager.data.shell.KsuCliRepository
import org.uranxsu.manager.domain.model.ZygiskConfig
import org.uranxsu.manager.domain.model.ZygiskImplInfo
import org.uranxsu.manager.domain.model.ZygiskInjectedModule
import org.uranxsu.manager.domain.model.ZygiskModuleProcess
import org.uranxsu.manager.domain.model.ZygiskProcessInfo
import org.uranxsu.manager.domain.model.ZygiskStatus
import org.uranxsu.manager.domain.model.ZygiskZygoteState

class ZygiskRepository(
    private val ksuCliRepository: KsuCliRepository,
) {
    companion object {
        private const val TAG = "ZygiskRepository"
    }

    private val gson = Gson()

    private data class CommandResult(
        val success: Boolean,
        val stdout: String,
        val stderr: String,
    )

    suspend fun getStatus(): ZygiskStatus = withContext(Dispatchers.IO) {
        val res = execZygisk("status")
        if (!res.success || res.stdout.isBlank()) {
            return@withContext ZygiskStatus()
        }
        runCatching {
            gson.fromJson(res.stdout, ZygiskStatusData::class.java)?.toDomain() ?: ZygiskStatus()
        }.getOrElse {
            Log.e(TAG, "Failed to parse zygisk status", it)
            ZygiskStatus()
        }
    }

    suspend fun getInfo(): ZygiskImplInfo = withContext(Dispatchers.IO) {
        val res = execZygisk("info")
        if (!res.success || res.stdout.isBlank()) {
            return@withContext ZygiskImplInfo()
        }
        runCatching {
            gson.fromJson(res.stdout, ZygiskImplInfoData::class.java)?.toDomain()
                ?: ZygiskImplInfo()
        }.getOrElse {
            Log.e(TAG, "Failed to parse zygisk info", it)
            ZygiskImplInfo()
        }
    }

    suspend fun getConfig(): ZygiskConfig = withContext(Dispatchers.IO) {
        val res = execZygisk("config get")
        if (!res.success || res.stdout.isBlank()) {
            return@withContext ZygiskConfig()
        }
        runCatching {
            gson.fromJson(res.stdout, ZygiskConfigData::class.java)?.toDomain() ?: ZygiskConfig()
        }.getOrElse {
            Log.e(TAG, "Failed to parse zygisk config", it)
            ZygiskConfig()
        }
    }

    suspend fun setConfig(key: String, value: String): Boolean = execZygisk("config set ${shellQuote(key)} ${shellQuote(value)}").success

    suspend fun getModules(): List<ZygiskInjectedModule> = withContext(Dispatchers.IO) {
        val res = execZygisk("modules")
        if (!res.success || res.stdout.isBlank()) {
            return@withContext emptyList()
        }
        runCatching {
            gson.fromJson(res.stdout, ZygiskModulesResponse::class.java)
                ?.modules
                ?.map { it.toDomain() }
                .orEmpty()
        }.getOrElse {
            Log.e(TAG, "Failed to parse zygisk modules", it)
            emptyList()
        }
    }

    suspend fun setEnabled(enabled: Boolean): Boolean = execZygisk(if (enabled) "on" else "off").success

    suspend fun switchProvider(target: String): Boolean = execZygisk("switch ${shellQuote(target)}").success

    suspend fun rearm(): Boolean = execZygisk("rearm").success

    private suspend fun execZygisk(subcommand: String): CommandResult = withContext(Dispatchers.IO) {
        try {
            val stdout = ArrayList<String>()
            val stderr = ArrayList<String>()
            val result = ksuCliRepository.withNewRootShell {
                newJob()
                    .add("${shellQuote(ksuCliRepository.getKsuDaemonPath())} zygisk $subcommand")
                    .to(stdout, stderr)
                    .exec()
            }
            CommandResult(
                success = result.isSuccess,
                stdout = stdout.joinToString("\n").trim(),
                stderr = stderr.joinToString("\n").trim(),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute ksud zygisk $subcommand", e)
            CommandResult(false, "", e.message.orEmpty())
        }
    }

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\"'\"'")}'"
}

private data class ZygiskProcessData(
    @SerializedName("name")
    val name: String = "",
    @SerializedName("pid")
    val pid: Int = 0,
) {
    fun toDomain() = ZygiskProcessInfo(name = name, pid = pid)
}

private data class ZygiskZygoteData(
    @SerializedName("abi")
    val abi: String = "",
    @SerializedName("status")
    val status: String = "",
) {
    fun toDomain() = ZygiskZygoteState(abi = abi, status = status)
}

private data class ZygiskStatusData(
    @SerializedName("enabled")
    val enabled: Boolean = false,
    @SerializedName("monitor_running")
    val monitorRunning: Boolean = false,
    @SerializedName("daemon_running")
    val daemonRunning: Boolean = false,
    @SerializedName("zygote_injected")
    val zygoteInjected: Boolean = false,
    @SerializedName("fully_active")
    val fullyActive: Boolean = false,
    @SerializedName("zygotes")
    val zygotes: List<ZygiskZygoteData> = emptyList(),
    @SerializedName("monitor")
    val monitor: ZygiskProcessData? = null,
    @SerializedName("daemons")
    val daemons: List<ZygiskProcessData> = emptyList(),
    @SerializedName("version")
    val version: String = "",
    @SerializedName("version_code")
    val versionCode: String = "",
) {
    fun toDomain() = ZygiskStatus(
        enabled = enabled,
        monitorRunning = monitorRunning,
        daemonRunning = daemonRunning,
        zygoteInjected = zygoteInjected,
        fullyActive = fullyActive,
        zygotes = zygotes.map { it.toDomain() },
        monitor = monitor?.toDomain(),
        daemons = daemons.map { it.toDomain() },
        version = version,
        versionCode = versionCode,
    )
}

private data class ZygiskImplInfoData(
    @SerializedName("state")
    val state: String = "none",
    @SerializedName("id")
    val id: String = "",
    @SerializedName("name")
    val name: String = "",
    @SerializedName("version")
    val version: String = "",
    @SerializedName("providers")
    val providers: List<String> = emptyList(),
) {
    fun toDomain() = ZygiskImplInfo(
        state = state,
        id = id,
        name = name,
        version = version,
        providers = providers,
    )
}

private data class ZygiskConfigData(
    @SerializedName("anonymous_memory")
    val anonymousMemory: String = "0",
    @SerializedName("custom_linker")
    val customLinker: String = "0",
    @SerializedName("plt_engine")
    val pltEngine: String = "yukiplt",
) {
    fun toDomain() = ZygiskConfig(
        anonymousMemory = anonymousMemory == "1",
        customLinker = customLinker == "1",
        pltEngine = when {
            pltEngine.isBlank() || pltEngine == "bytehook" -> "yukiplt"
            else -> pltEngine
        },
    )
}

private data class ZygiskModuleProcessData(
    @SerializedName("name")
    val name: String = "",
    @SerializedName("pid")
    val pid: Int = 0,
    @SerializedName("kind")
    val kind: String = "",
) {
    fun toDomain() = ZygiskModuleProcess(name = name, pid = pid, kind = kind)
}

private data class ZygiskInjectedModuleData(
    @SerializedName("id")
    val id: String = "",
    @SerializedName("name")
    val name: String = "",
    @SerializedName("version")
    val version: String = "",
    @SerializedName("type")
    val type: String = "zygisk",
    @SerializedName("abi")
    val abi: List<String> = emptyList(),
    @SerializedName("targets")
    val targets: List<String> = emptyList(),
    @SerializedName("companion")
    val companion: Boolean = false,
    @SerializedName("state")
    val state: String = "unknown",
    @SerializedName("processes")
    val processes: List<ZygiskModuleProcessData> = emptyList(),
) {
    fun toDomain() = ZygiskInjectedModule(
        id = id,
        name = name,
        version = version,
        type = type,
        abi = abi,
        targets = targets,
        companion = companion,
        state = state,
        processes = processes.map { it.toDomain() },
    )
}

private data class ZygiskModulesResponse(
    @SerializedName("modules")
    val modules: List<ZygiskInjectedModuleData> = emptyList(),
)
