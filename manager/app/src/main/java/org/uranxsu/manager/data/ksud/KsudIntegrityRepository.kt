package org.uranxsu.manager.data.ksud

import com.topjohnwu.superuser.ShellUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.uranxsu.manager.BuildConfig
import org.uranxsu.manager.Natives
import org.uranxsu.manager.data.shell.KsuCliRepository

enum class KsudSyncStatus {
    UP_TO_DATE,
    NEEDS_SYNC,
    MISSING,
    UNAVAILABLE,
}

data class KsudIntegritySnapshot(
    val status: KsudSyncStatus = KsudSyncStatus.UNAVAILABLE,
    val bundledVersion: String = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
    val installedVersion: String? = null,
    val error: String? = null,
)

class KsudIntegrityRepository(
    private val ksuCliRepository: KsuCliRepository,
) {
    private companion object {
        const val KSUD_SYSTEM_PATH = "/data/adb/ksud"
        const val KSUD_TMP_PATH = "/data/adb/ksud.tmp"
        const val KSU_BIN_DIR = "/data/adb/ksu/bin"
    }

    suspend fun checkStatus(): KsudIntegritySnapshot = withContext(Dispatchers.IO) {
        if (!Natives.isManager) {
            return@withContext KsudIntegritySnapshot(status = KsudSyncStatus.UNAVAILABLE)
        }
        val bundledPath = runCatching { ksuCliRepository.getKsuDaemonPath() }.getOrNull()
            ?: return@withContext KsudIntegritySnapshot(
                status = KsudSyncStatus.UNAVAILABLE,
                error = "Bundled libksud.so not found",
            )

        val nativeRc = runCatching { Natives.checkKsudIntegrity(bundledPath) }.getOrDefault(-1)
        val resolvedStatus = when (nativeRc) {
            0 -> KsudSyncStatus.UP_TO_DATE
            1 -> KsudSyncStatus.NEEDS_SYNC
            2 -> KsudSyncStatus.MISSING
            else -> checkViaRootShell(bundledPath)
        }
        val installedVersion = if (resolvedStatus != KsudSyncStatus.MISSING) {
            readInstalledVersion()
        } else {
            null
        }
        KsudIntegritySnapshot(
            status = resolvedStatus,
            installedVersion = installedVersion,
        )
    }

    suspend fun repair(): Result<KsudIntegritySnapshot> = withContext(Dispatchers.IO) {
        if (!Natives.isManager) {
            return@withContext Result.failure(IllegalStateException("KernelSU manager unavailable"))
        }
        val bundledPath = runCatching { ksuCliRepository.getKsuDaemonPath() }
            .getOrElse { return@withContext Result.failure(it) }

        val nativeRc = runCatching { Natives.repairKsud(bundledPath) }.getOrDefault(-1)
        if (nativeRc != 0) {
            val shellOk = repairViaRootShell(bundledPath)
            if (!shellOk) {
                return@withContext Result.failure(
                    IllegalStateException("Failed to sync ksud (native rc=$nativeRc)"),
                )
            }
        }
        Result.success(checkStatus())
    }

    suspend fun syncIfNeeded(): Result<KsudIntegritySnapshot> {
        val current = checkStatus()
        return if (current.status == KsudSyncStatus.NEEDS_SYNC ||
            current.status == KsudSyncStatus.MISSING
        ) {
            repair()
        } else {
            Result.success(current)
        }
    }

    private fun checkViaRootShell(bundledPath: String): KsudSyncStatus {
        val shell = ksuCliRepository.getRootShell()
        val exists = ShellUtils.fastCmdResult(shell, "[ -f $KSUD_SYSTEM_PATH ]")
        if (!exists) return KsudSyncStatus.MISSING
        val quoted = KsuCliRepository.shellQuote(bundledPath)
        val same = ShellUtils.fastCmdResult(shell, "cmp -s $quoted $KSUD_SYSTEM_PATH")
        return if (same) KsudSyncStatus.UP_TO_DATE else KsudSyncStatus.NEEDS_SYNC
    }

    private fun repairViaRootShell(bundledPath: String): Boolean {
        val shell = ksuCliRepository.getRootShell()
        val quoted = KsuCliRepository.shellQuote(bundledPath)
        val script = "mkdir -p /data/adb $KSU_BIN_DIR && " +
            "cp $quoted $KSUD_TMP_PATH && chmod 0755 $KSUD_TMP_PATH && " +
            "mv -f $KSUD_TMP_PATH $KSUD_SYSTEM_PATH && " +
            "ln -sf $KSUD_SYSTEM_PATH $KSU_BIN_DIR/ksud && " +
            "ln -sf $KSUD_SYSTEM_PATH $KSU_BIN_DIR/resetprop"
        return ShellUtils.fastCmdResult(shell, script)
    }

    private fun readInstalledVersion(): String? = runCatching {
        val shell = ksuCliRepository.getRootShell()
        ShellUtils.fastCmd(shell, "$KSUD_SYSTEM_PATH -V 2>/dev/null")
            .lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?.trim()
    }.getOrNull()
}
