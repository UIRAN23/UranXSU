package org.uranxsu.manager.data.noshot

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.uranxsu.manager.data.packageinfo.InstalledPackageRepository
import org.uranxsu.manager.data.shell.KsuCliRepository
import org.uranxsu.manager.domain.model.NoShotAppCandidate
import org.uranxsu.manager.domain.model.NoShotSettings
import org.uranxsu.manager.domain.model.NoShotStatus
import org.uranxsu.manager.domain.model.NoShotTarget

class NoShotRepository(
    private val ksuCliRepository: KsuCliRepository,
    private val installedPackages: InstalledPackageRepository,
) {
    private companion object {
        const val TAG = "NoShotRepository"
    }

    private val gson = Gson()

    suspend fun getStatus(): NoShotStatus = withContext(Dispatchers.IO) {
        val res = execNoshot("status")
        if (!res.success || res.stdout.isBlank()) {
            Log.w(TAG, "Failed to read noshot status: ${res.stderr}")
            return@withContext NoShotStatus()
        }
        parseStatus(res.stdout) ?: NoShotStatus()
    }

    suspend fun setEnabled(enabled: Boolean): Pair<Boolean, NoShotStatus> = withContext(Dispatchers.IO) {
        val sub = if (enabled) "enable" else "disable"
        val res = execNoshot(sub)
        val parsed = if (res.success && res.stdout.isNotBlank()) parseStatus(res.stdout) else null
        res.success to (parsed ?: getStatus())
    }

    suspend fun updateSettings(settings: NoShotSettings): Boolean = withContext(Dispatchers.IO) {
        val cg = when (settings.captureGuard.lowercase()) {
            "observe", "on" -> settings.captureGuard.lowercase()
            else -> "off"
        }
        val cmd = buildString {
            append("set-settings")
            append(" --flag-secure ").append(settings.flagSecure)
            append(" --surface-secure ").append(settings.surfaceSecure)
            append(" --detect-guard ").append(settings.detectGuard)
            append(" --system-hook ").append(settings.systemHook)
            append(" --capture-guard ").append(cg)
            append(" --log ").append(settings.log)
        }
        execNoshot(cmd).success
    }

    suspend fun addTarget(packageName: String, mode: String = "system"): Pair<Boolean, List<NoShotTarget>> = withContext(Dispatchers.IO) {
        val cleanMode = if (mode == "client") "client" else "system"
        val quotedPkg = KsuCliRepository.shellQuote(packageName)
        val res = execNoshot("add $quotedPkg --mode $cleanMode")
        val targets = parseTargetsPayload(res.stdout)
        res.success to targets
    }

    suspend fun removeTarget(packageName: String): Pair<Boolean, List<NoShotTarget>> = withContext(Dispatchers.IO) {
        val quotedPkg = KsuCliRepository.shellQuote(packageName)
        val res = execNoshot("remove $quotedPkg")
        val targets = parseTargetsPayload(res.stdout)
        res.success to targets
    }

    suspend fun resetGuard(): Pair<Boolean, NoShotStatus> = withContext(Dispatchers.IO) {
        val res = execNoshot("reset-guard")
        val parsed = if (res.success && res.stdout.isNotBlank()) parseStatus(res.stdout) else null
        res.success to (parsed ?: getStatus())
    }

    suspend fun syncPlan(): Pair<Boolean, NoShotStatus> = withContext(Dispatchers.IO) {
        val res = execNoshot("sync-plan")
        val parsed = if (res.success && res.stdout.isNotBlank()) parseStatus(res.stdout) else null
        res.success to (parsed ?: getStatus())
    }

    suspend fun loadAppCandidates(targets: List<NoShotTarget>): List<NoShotAppCandidate> = withContext(Dispatchers.IO) {
        if (installedPackages.packages.value.isEmpty()) {
            runCatching { installedPackages.refresh() }
        }
        val targetMap = targets
            .groupBy { it.packageName }
            .mapValues { (_, list) -> list.firstOrNull { it.user == null } ?: list.first() }

        val installedByPkg = installedPackages.packages.value
            .distinctBy { it.packageName }
            .associateBy { it.packageName }

        val list = installedByPkg.values.map { info ->
            val activeTarget = targetMap[info.packageName]
            NoShotAppCandidate(
                packageName = info.packageName,
                appLabel = info.appLabel.ifBlank { info.packageName },
                uid = info.uid,
                isSystem = info.isSystem,
                enabled = activeTarget != null,
                mode = activeTarget?.mode ?: "system",
            )
        }.toMutableList()

        targetMap.forEach { (pkg, target) ->
            if (!installedByPkg.containsKey(pkg)) {
                list += NoShotAppCandidate(
                    packageName = pkg,
                    appLabel = pkg,
                    uid = -1,
                    isSystem = false,
                    enabled = true,
                    mode = target.mode,
                )
            }
        }

        list.sortedWith(
            compareByDescending<NoShotAppCandidate> { it.enabled }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.appLabel },
        )
    }

    private fun parseStatus(raw: String): NoShotStatus? = runCatching {
        gson.fromJson(raw, NoShotStatusJson::class.java)?.toDomain()
    }.getOrNull()

    private fun parseTargetsPayload(raw: String): List<NoShotTarget> = runCatching {
        gson.fromJson(raw, NoShotTargetsPayloadJson::class.java)
            ?.targets
            .orEmpty()
            .map { it.toDomain() }
    }.getOrDefault(emptyList())

    private fun execNoshot(subcommand: String): CommandResult = try {
        val stdout = ArrayList<String>()
        val stderr = ArrayList<String>()
        val ksud = KsuCliRepository.shellQuote(ksuCliRepository.getKsuDaemonPath())
        val result = ksuCliRepository.getRootShell()
            .newJob()
            .add("$ksud noshot $subcommand")
            .to(stdout, stderr)
            .exec()
        CommandResult(
            success = result.isSuccess,
            stdout = stdout.joinToString("\n").trim(),
            stderr = stderr.joinToString("\n").trim(),
        )
    } catch (t: Throwable) {
        Log.e(TAG, "Failed to run ksud noshot $subcommand", t)
        CommandResult(false, "", t.message.orEmpty())
    }

    private data class CommandResult(
        val success: Boolean,
        val stdout: String,
        val stderr: String,
    )
}

private data class NoShotSettingsJson(
    @SerializedName("flag_secure")
    val flagSecure: Boolean = true,
    @SerializedName("surface_secure")
    val surfaceSecure: Boolean = true,
    @SerializedName("detect_guard")
    val detectGuard: Boolean = true,
    @SerializedName("system_hook")
    val systemHook: Boolean = true,
    @SerializedName("capture_guard")
    val captureGuard: String = "off",
    @SerializedName("log")
    val log: Boolean = true,
) {
    fun toDomain() = NoShotSettings(
        flagSecure = flagSecure,
        surfaceSecure = surfaceSecure,
        detectGuard = detectGuard,
        systemHook = systemHook,
        captureGuard = captureGuard,
        log = log,
    )
}

private data class NoShotTargetJson(
    @SerializedName("package")
    val packageName: String = "",
    @SerializedName("user")
    val user: Int? = null,
    @SerializedName("mode")
    val mode: String = "system",
) {
    fun toDomain() = NoShotTarget(
        packageName = packageName,
        user = user,
        mode = mode,
    )
}

private data class NoShotTargetsPayloadJson(
    @SerializedName("targets")
    val targets: List<NoShotTargetJson> = emptyList(),
)

private data class NoShotStatusJson(
    @SerializedName("enabled")
    val enabled: Boolean = false,
    @SerializedName("standalone_conflict")
    val standaloneConflict: Boolean = false,
    @SerializedName("zygisk_enabled")
    val zygiskEnabled: Boolean = false,
    @SerializedName("zygisk_running")
    val zygiskRunning: Boolean = false,
    @SerializedName("loaded_in_zygisk")
    val loadedInZygisk: Boolean = false,
    @SerializedName("runtime_mode")
    val runtimeMode: String = "client",
    @SerializedName("bootloop_disabled")
    val bootloopDisabled: Boolean = false,
    @SerializedName("attempt_count")
    val attemptCount: Int = 0,
    @SerializedName("disable_system")
    val disableSystem: Boolean = false,
    @SerializedName("capture_guard_state")
    val captureGuardState: String = "unknown",
    @SerializedName("state_log")
    val stateLog: String = "",
    @SerializedName("settings")
    val settings: NoShotSettingsJson = NoShotSettingsJson(),
    @SerializedName("targets")
    val targets: List<NoShotTargetJson> = emptyList(),
) {
    fun toDomain() = NoShotStatus(
        enabled = enabled,
        standaloneConflict = standaloneConflict,
        zygiskEnabled = zygiskEnabled,
        zygiskRunning = zygiskRunning,
        loadedInZygisk = loadedInZygisk,
        runtimeMode = runtimeMode,
        bootloopDisabled = bootloopDisabled,
        attemptCount = attemptCount,
        disableSystem = disableSystem,
        captureGuardState = captureGuardState,
        stateLog = stateLog,
        settings = settings.toDomain(),
        targets = targets.map { it.toDomain() },
    )
}
