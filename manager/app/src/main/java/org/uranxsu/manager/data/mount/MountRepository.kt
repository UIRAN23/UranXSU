package org.uranxsu.manager.data.mount

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.uranxsu.manager.data.packageinfo.InstalledPackageRepository
import org.uranxsu.manager.data.shell.KsuCliRepository
import org.uranxsu.manager.domain.model.MountOverallStatus
import org.uranxsu.manager.domain.model.NoMountAppCandidate
import org.uranxsu.manager.domain.model.NoMountConfig
import org.uranxsu.manager.domain.model.NoMountExclusionEntry
import org.uranxsu.manager.domain.model.NoMountModuleInfo
import org.uranxsu.manager.domain.model.NoMountStatus

class MountRepository(
    private val ksuCliRepository: KsuCliRepository,
    private val installedPackages: InstalledPackageRepository,
) {
    companion object {
        private const val TAG = "MountRepository"
    }

    private val gson = Gson()

    private data class CommandResult(
        val success: Boolean,
        val stdout: String,
        val stderr: String,
    )

    suspend fun getOverallStatus(): MountOverallStatus = withContext(Dispatchers.IO) {
        val res = execMount("status")
        if (!res.success || res.stdout.isBlank()) {
            Log.e(TAG, "Failed to get mount status: ${res.stderr}")
            return@withContext MountOverallStatus()
        }
        runCatching {
            gson.fromJson(res.stdout, MountOverallStatusData::class.java)?.toDomain()
                ?: MountOverallStatus()
        }.getOrElse {
            Log.e(TAG, "Failed to parse mount status", it)
            MountOverallStatus()
        }
    }

    suspend fun setMountMode(mode: String): Boolean = execMount("set-mode ${shellQuote(mode)}").success

    suspend fun getNoMountStatus(): NoMountStatus = withContext(Dispatchers.IO) {
        val res = execMount("nomount status")
        if (!res.success || res.stdout.isBlank()) {
            Log.e(TAG, "Failed to get NoMount status: ${res.stderr}")
            return@withContext NoMountStatus()
        }
        runCatching {
            gson.fromJson(res.stdout, NoMountStatusData::class.java)?.toDomain()
                ?: NoMountStatus()
        }.getOrElse {
            Log.e(TAG, "Failed to parse NoMount status", it)
            NoMountStatus()
        }
    }

    suspend fun loadNoMountLkm(kmi: String? = null): Boolean {
        val arg = if (!kmi.isNullOrBlank()) " --kmi ${shellQuote(kmi)}" else ""
        return execMount("nomount load-lkm$arg").success
    }

    suspend fun reloadNoMountAll(): Boolean = execMount("nomount reload-all").success

    suspend fun clearNoMountRules(): Boolean = execMount("nomount clear-rules").success

    suspend fun clearNoMountAll(): Boolean = execMount("nomount clear-all").success

    suspend fun loadNoMountModule(id: String): Boolean = execMount("nomount module-load ${shellQuote(id)}").success

    suspend fun unloadNoMountModule(id: String): Boolean = execMount("nomount module-unload ${shellQuote(id)}").success

    suspend fun getNoMountRulesJson(): String = withContext(Dispatchers.IO) {
        val res = execMount("nomount rules")
        if (res.success) res.stdout else res.stderr
    }

    suspend fun addNoMountUid(uid: Int, pkg: String? = null): Boolean {
        val pkgArg = if (!pkg.isNullOrBlank()) " --pkg ${shellQuote(pkg)}" else ""
        return execMount("nomount uid-add $uid$pkgArg").success
    }

    suspend fun removeNoMountUid(uid: Int): Boolean = execMount("nomount uid-del $uid").success

    suspend fun setNoMountConfig(
        config: NoMountConfig,
        clearBootloopDisable: Boolean = false,
    ): Boolean {
        val args = buildList {
            add("nomount")
            add("set-config")
            add("--auto-load-lkm")
            add(config.autoLoadLkm.toString())
            if (config.kmiOverride.isNotBlank()) {
                add("--kmi-override")
                add(shellQuote(config.kmiOverride))
            }
            add("--block-isolated-uids")
            add(config.blockIsolatedUids.toString())
            add("--dump-rules-on-boot")
            add(config.dumpRulesOnBoot.toString())
            add("--bootloop-guard")
            add(config.bootloopGuard.toString())
            if (clearBootloopDisable) {
                add("--clear-bootloop-disable")
            }
        }.joinToString(" ")
        return execMount(args).success
    }

    suspend fun getInstalledAppCandidates(): List<NoMountAppCandidate> = withContext(Dispatchers.IO) {
        runCatching { installedPackages.refresh() }
        return@withContext installedPackages.packages.value
            .filter { it.uid >= 10000 }
            .distinctBy { it.uid }
            .map {
                NoMountAppCandidate(
                    uid = it.uid,
                    packageName = it.packageName,
                    label = it.appLabel.ifBlank { it.packageName },
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    private suspend fun execMount(subcommand: String): CommandResult = withContext(Dispatchers.IO) {
        try {
            val stdout = ArrayList<String>()
            val stderr = ArrayList<String>()
            val result = ksuCliRepository.withNewRootShell {
                newJob()
                    .add("${shellQuote(ksuCliRepository.getKsuDaemonPath())} mount $subcommand")
                    .to(stdout, stderr)
                    .exec()
            }
            CommandResult(
                success = result.isSuccess,
                stdout = stdout.joinToString("\n").trim(),
                stderr = stderr.joinToString("\n").trim(),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute ksud mount $subcommand", e)
            CommandResult(false, "", e.message.orEmpty())
        }
    }

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\"'\"'")}'"
}

private data class MountOverallStatusData(
    @SerializedName("mode")
    val mode: String = "no_mount",
    @SerializedName("external_metamodule_id")
    val externalMetamoduleId: String = "",
    @SerializedName("external_metamodule_name")
    val externalMetamoduleName: String = "",
    @SerializedName("external_metamodule_enabled")
    val externalMetamoduleEnabled: Boolean = false,
    @SerializedName("external_metamodule_is_nomount")
    val externalMetamoduleIsNomount: Boolean = false,
    @SerializedName("nomount_driver_ready")
    val nomountDriverReady: Boolean = false,
    @SerializedName("nomount_driver_version")
    val nomountDriverVersion: String = "",
    @SerializedName("nomount_rule_count")
    val nomountRuleCount: Int = 0,
) {
    fun toDomain() = MountOverallStatus(
        mode = mode,
        externalMetamoduleId = externalMetamoduleId,
        externalMetamoduleName = externalMetamoduleName,
        externalMetamoduleEnabled = externalMetamoduleEnabled,
        externalMetamoduleIsNomount = externalMetamoduleIsNomount,
        nomountDriverReady = nomountDriverReady,
        nomountDriverVersion = nomountDriverVersion,
        nomountRuleCount = nomountRuleCount,
    )
}

private data class NoMountConfigData(
    @SerializedName("auto_load_lkm")
    val autoLoadLkm: Boolean = true,
    @SerializedName("kmi_override")
    val kmiOverride: String = "",
    @SerializedName("block_isolated_uids")
    val blockIsolatedUids: Boolean = false,
    @SerializedName("dump_rules_on_boot")
    val dumpRulesOnBoot: Boolean = false,
    @SerializedName("bootloop_guard")
    val bootloopGuard: Boolean = true,
    @SerializedName("skipped_modules")
    val skippedModules: List<String> = emptyList(),
) {
    fun toDomain() = NoMountConfig(
        autoLoadLkm = autoLoadLkm,
        kmiOverride = kmiOverride,
        blockIsolatedUids = blockIsolatedUids,
        dumpRulesOnBoot = dumpRulesOnBoot,
        bootloopGuard = bootloopGuard,
        skippedModules = skippedModules,
    )
}

private data class NoMountExclusionData(
    @SerializedName("uid")
    val uid: String = "",
    @SerializedName("pkg")
    val pkg: String = "",
) {
    fun toDomain() = NoMountExclusionEntry(uid = uid, pkg = pkg)
}

private data class NoMountModuleData(
    @SerializedName("id")
    val id: String = "",
    @SerializedName("name")
    val name: String = "",
    @SerializedName("enabled")
    val enabled: Boolean = true,
    @SerializedName("skipped")
    val skipped: Boolean = false,
    @SerializedName("injected_files")
    val injectedFiles: Int = 0,
    @SerializedName("partitions")
    val partitions: List<String> = emptyList(),
) {
    fun toDomain() = NoMountModuleInfo(
        id = id,
        name = name,
        enabled = enabled,
        skipped = skipped,
        injectedFiles = injectedFiles,
        partitions = partitions,
    )
}

private data class NoMountStatusData(
    @SerializedName("driver_ready")
    val driverReady: Boolean = false,
    @SerializedName("driver_version")
    val driverVersion: String = "",
    @SerializedName("active_kmi")
    val activeKmi: String = "",
    @SerializedName("detected_kmi")
    val detectedKmi: String = "",
    @SerializedName("available_kmis")
    val availableKmis: List<String> = emptyList(),
    @SerializedName("rule_count")
    val ruleCount: Int = 0,
    @SerializedName("uid_count")
    val uidCount: Int = 0,
    @SerializedName("block_isolated")
    val blockIsolated: Boolean = false,
    @SerializedName("bootloop_disabled")
    val bootloopDisabled: Boolean = false,
    @SerializedName("config")
    val config: NoMountConfigData = NoMountConfigData(),
    @SerializedName("exclusions")
    val exclusions: List<NoMountExclusionData> = emptyList(),
    @SerializedName("modules")
    val modules: List<NoMountModuleData> = emptyList(),
) {
    fun toDomain() = NoMountStatus(
        driverReady = driverReady,
        driverVersion = driverVersion,
        activeKmi = activeKmi,
        detectedKmi = detectedKmi,
        availableKmis = availableKmis,
        ruleCount = ruleCount,
        uidCount = uidCount,
        blockIsolated = blockIsolated,
        bootloopDisabled = bootloopDisabled,
        config = config.toDomain(),
        exclusions = exclusions.map { it.toDomain() },
        modules = modules.map { it.toDomain() },
    )
}
