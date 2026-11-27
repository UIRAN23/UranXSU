package org.uranxsu.manager.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.topjohnwu.superuser.ShellUtils
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.uranxsu.manager.BuildConfig
import org.uranxsu.manager.Natives
import org.uranxsu.manager.data.backup.BackupInspection
import org.uranxsu.manager.data.backup.BackupManifest
import org.uranxsu.manager.data.backup.BackupRestoreSummary
import org.uranxsu.manager.data.backup.BackupSection
import org.uranxsu.manager.data.backup.ConfigBackupManager
import org.uranxsu.manager.data.kernel.KernelRepository
import org.uranxsu.manager.data.ksud.KsudIntegrityRepository
import org.uranxsu.manager.data.ksud.KsudIntegritySnapshot
import org.uranxsu.manager.data.mount.MountRepository
import org.uranxsu.manager.data.noshot.NoShotRepository
import org.uranxsu.manager.data.shell.KsuCliRepository
import org.uranxsu.manager.data.susfs.SuSFSRepository
import org.uranxsu.manager.data.zygisk.ZygiskRepository

enum class CleanupItemId {
    OVERLAYIMG,
    OLD_LOGS,
    TMP_APKS,
    APP_CACHE,
}

data class CleanupEntry(
    val id: CleanupItemId,
    val path: String,
    val sizeBytes: Long,
    val exists: Boolean,
)

data class SystemHealthUiState(
    val isRefreshing: Boolean = false,
    val isBusy: Boolean = false,
    val isFullFeatured: Boolean = false,
    val lkmMode: Boolean? = null,
    val runtimeLkmHash: String? = null,
    val bundledLkmHash: String = BuildConfig.BUNDLED_LKM_HASH,
    val needsLkmUpdate: Boolean = false,
    val ksudIntegrity: KsudIntegritySnapshot = KsudIntegritySnapshot(),
    val nomountReady: Boolean = false,
    val nomountVersion: String = "",
    val nomountRules: Int = 0,
    val susfsVersion: String = "",
    val susfsVariant: String = "",
    val susfsLkmLoaded: Boolean = false,
    val zygiskEnabled: Boolean = false,
    val zygiskRunning: Boolean = false,
    val noshotEnabled: Boolean = false,
    val noshotMode: String = "client",
    val noshotBootloopDisabled: Boolean = false,
    val cleanupEntries: List<CleanupEntry> = emptyList(),
    val totalCleanableBytes: Long = 0L,
)

class SystemHealthViewModel(
    private val application: Application,
    private val kernelRepository: KernelRepository,
    private val ksudIntegrityRepository: KsudIntegrityRepository,
    private val mountRepository: MountRepository,
    private val suSFSRepository: SuSFSRepository,
    private val zygiskRepository: ZygiskRepository,
    private val noShotRepository: NoShotRepository,
    private val ksuCliRepository: KsuCliRepository,
    private val configBackupManager: ConfigBackupManager,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SystemHealthUiState())
    val state: StateFlow<SystemHealthUiState> = mutableState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            mutableState.update { it.copy(isRefreshing = true) }
            val loaded = coroutineScope {
                val kernelDeferred = async { runCatching { kernelRepository.getStatus() }.getOrNull() }
                val ksudDeferred = async { runCatching { ksudIntegrityRepository.checkStatus() }.getOrDefault(KsudIntegritySnapshot()) }
                val mountDeferred = async { runCatching { mountRepository.getNoMountStatus() }.getOrNull() }
                val susfsDeferred = async { runCatching { suSFSRepository.loadStatusInfo(true) }.getOrNull() }
                val susfsLkmDeferred = async { runCatching { suSFSRepository.loadLkmStatus(true) }.getOrNull() }
                val zygiskDeferred = async { runCatching { zygiskRepository.getStatus() }.getOrNull() }
                val noshotDeferred = async { runCatching { noShotRepository.getStatus() }.getOrNull() }
                val cleanupDeferred = async { scanLeftovers() }

                val kernel = kernelDeferred.await()
                val ksud = ksudDeferred.await()
                val nomount = mountDeferred.await()
                val susfs = susfsDeferred.await()
                val susfsLkm = susfsLkmDeferred.await()
                val zygisk = zygiskDeferred.await()
                val noshot = noshotDeferred.await()
                val cleanup = cleanupDeferred.await()

                SystemHealthUiState(
                    isRefreshing = false,
                    isBusy = mutableState.value.isBusy,
                    isFullFeatured = kernel?.isFullFeatured == true,
                    lkmMode = kernel?.lkmMode,
                    runtimeLkmHash = kernel?.runtimeLkmHash,
                    bundledLkmHash = BuildConfig.BUNDLED_LKM_HASH,
                    needsLkmUpdate = kernel?.needsLkmUpdate == true,
                    ksudIntegrity = ksud,
                    nomountReady = nomount?.driverReady == true,
                    nomountVersion = nomount?.driverVersion.orEmpty(),
                    nomountRules = nomount?.ruleCount ?: 0,
                    susfsVersion = susfs?.version.orEmpty(),
                    susfsVariant = susfs?.variant.orEmpty(),
                    susfsLkmLoaded = susfsLkm?.lkmLoaded == true,
                    zygiskEnabled = zygisk?.enabled == true,
                    zygiskRunning = zygisk?.fullyActive == true || zygisk?.daemonRunning == true,
                    noshotEnabled = noshot?.enabled == true,
                    noshotMode = noshot?.runtimeMode.orEmpty().ifBlank { "client" },
                    noshotBootloopDisabled = noshot?.bootloopDisabled == true,
                    cleanupEntries = cleanup,
                    totalCleanableBytes = cleanup.sumOf { it.sizeBytes },
                )
            }
            mutableState.value = loaded
        }
    }

    suspend fun syncKsud(): Result<KsudIntegritySnapshot> {
        mutableState.update { it.copy(isBusy = true) }
        return try {
            val result = ksudIntegrityRepository.repair()
            result.onSuccess { snapshot ->
                mutableState.update { it.copy(ksudIntegrity = snapshot) }
            }
            result
        } finally {
            mutableState.update { it.copy(isBusy = false) }
        }
    }

    suspend fun cleanItem(id: CleanupItemId): Long = withContext(Dispatchers.IO) {
        mutableState.update { it.copy(isBusy = true) }
        try {
            val before = mutableState.value.cleanupEntries.firstOrNull { it.id == id }?.sizeBytes ?: 0L
            performCleanup(id)
            val refreshed = scanLeftovers()
            mutableState.update {
                it.copy(
                    cleanupEntries = refreshed,
                    totalCleanableBytes = refreshed.sumOf { entry -> entry.sizeBytes },
                )
            }
            before
        } finally {
            mutableState.update { it.copy(isBusy = false) }
        }
    }

    suspend fun cleanAll(): Long = withContext(Dispatchers.IO) {
        mutableState.update { it.copy(isBusy = true) }
        try {
            val before = mutableState.value.totalCleanableBytes
            CleanupItemId.entries.forEach { performCleanup(it) }
            val refreshed = scanLeftovers()
            mutableState.update {
                it.copy(
                    cleanupEntries = refreshed,
                    totalCleanableBytes = refreshed.sumOf { entry -> entry.sizeBytes },
                )
            }
            before
        } finally {
            mutableState.update { it.copy(isBusy = false) }
        }
    }

    suspend fun exportBackup(
        uri: Uri,
        sections: Set<BackupSection>,
    ): Result<BackupManifest> {
        mutableState.update { it.copy(isBusy = true) }
        return try {
            configBackupManager.exportBackup(uri, sections)
        } finally {
            mutableState.update { it.copy(isBusy = false) }
        }
    }

    suspend fun inspectBackup(uri: Uri): Result<BackupInspection> {
        mutableState.update { it.copy(isBusy = true) }
        return try {
            configBackupManager.inspectBackup(uri)
        } finally {
            mutableState.update { it.copy(isBusy = false) }
        }
    }

    suspend fun restoreBackup(
        uri: Uri,
        sections: Set<BackupSection>,
    ): Result<BackupRestoreSummary> {
        mutableState.update { it.copy(isBusy = true) }
        return try {
            val result = configBackupManager.restoreBackup(uri, sections)
            refresh()
            result
        } finally {
            mutableState.update { it.copy(isBusy = false) }
        }
    }

    private fun scanLeftovers(): List<CleanupEntry> {
        val entries = mutableListOf<CleanupEntry>()
        val hasRoot = runCatching { Natives.isManager && ksuCliRepository.rootAvailable() }.getOrDefault(false)

        if (hasRoot) {
            val shell = ksuCliRepository.getRootShell()
            val overlayExists = ShellUtils.fastCmdResult(shell, "[ -d /data/adb/ksu/overlayimg ]")
            if (overlayExists) {
                val kb = ShellUtils.fastCmd(shell, "du -sk /data/adb/ksu/overlayimg 2>/dev/null")
                    .trim().split(Regex("\\s+")).firstOrNull()?.toLongOrNull() ?: 0L
                entries += CleanupEntry(
                    id = CleanupItemId.OVERLAYIMG,
                    path = "/data/adb/ksu/overlayimg",
                    sizeBytes = (kb * 1024L).coerceAtLeast(1024L),
                    exists = true,
                )
            }

            val oldLogFiles = ShellUtils.fastCmd(
                shell,
                "ls -1 /data/adb/ksu/log/*.old /data/adb/ksu/log/*.bak 2>/dev/null",
            ).trim()
            if (oldLogFiles.isNotBlank()) {
                val kb = ShellUtils.fastCmd(
                    shell,
                    "du -sk /data/adb/ksu/log/*.old /data/adb/ksu/log/*.bak 2>/dev/null | tail -n 1",
                ).trim().split(Regex("\\s+")).firstOrNull()?.toLongOrNull() ?: 1L
                entries += CleanupEntry(
                    id = CleanupItemId.OLD_LOGS,
                    path = "/data/adb/ksu/log/*.{old,bak}",
                    sizeBytes = (kb * 1024L).coerceAtLeast(1024L),
                    exists = true,
                )
            }

            val tmpFiles = ShellUtils.fastCmd(shell, "ls -1 /data/local/tmp/uranxsu_* 2>/dev/null").trim()
            if (tmpFiles.isNotBlank()) {
                val kb = ShellUtils.fastCmd(shell, "du -sk /data/local/tmp/uranxsu_* 2>/dev/null | tail -n 1")
                    .trim().split(Regex("\\s+")).firstOrNull()?.toLongOrNull() ?: 1L
                entries += CleanupEntry(
                    id = CleanupItemId.TMP_APKS,
                    path = "/data/local/tmp/uranxsu_*",
                    sizeBytes = (kb * 1024L).coerceAtLeast(1024L),
                    exists = true,
                )
            }
        }

        val appCacheBytes = computeAppCacheSize(application.cacheDir)
        if (appCacheBytes > 0L) {
            entries += CleanupEntry(
                id = CleanupItemId.APP_CACHE,
                path = application.cacheDir.absolutePath,
                sizeBytes = appCacheBytes,
                exists = true,
            )
        }

        return entries
    }

    private fun performCleanup(id: CleanupItemId) {
        val hasRoot = runCatching { Natives.isManager && ksuCliRepository.rootAvailable() }.getOrDefault(false)
        when (id) {
            CleanupItemId.OVERLAYIMG -> if (hasRoot) {
                ShellUtils.fastCmdResult(ksuCliRepository.getRootShell(), "rm -rf /data/adb/ksu/overlayimg")
            }

            CleanupItemId.OLD_LOGS -> if (hasRoot) {
                ShellUtils.fastCmdResult(
                    ksuCliRepository.getRootShell(),
                    "rm -f /data/adb/ksu/log/*.old /data/adb/ksu/log/*.bak",
                )
            }

            CleanupItemId.TMP_APKS -> if (hasRoot) {
                ShellUtils.fastCmdResult(ksuCliRepository.getRootShell(), "rm -rf /data/local/tmp/uranxsu_*")
            }

            CleanupItemId.APP_CACHE -> cleanAppCache(application.cacheDir)
        }
    }

    private fun computeAppCacheSize(cacheDir: File): Long {
        if (!cacheDir.isDirectory) return 0L
        var total = 0L
        cacheDir.listFiles()?.forEach { child ->
            if (isCleanableCacheChild(child.name)) {
                total += child.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            }
        }
        return total
    }

    private fun cleanAppCache(cacheDir: File) {
        if (!cacheDir.isDirectory) return
        cacheDir.listFiles()?.forEach { child ->
            if (isCleanableCacheChild(child.name)) {
                runCatching { child.deleteRecursively() }
            }
        }
    }

    private fun isCleanableCacheChild(name: String): Boolean = name == "image_cache" ||
        name.startsWith("crop_") ||
        name.startsWith("bg_crop_") ||
        name.startsWith("card_crop_") ||
        name.startsWith("mod_banner_") ||
        name.startsWith("theme_import_") ||
        name.startsWith("backup_restore_") ||
        name.startsWith("bk_read_")
}
