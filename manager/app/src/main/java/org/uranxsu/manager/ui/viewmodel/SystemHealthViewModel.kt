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
import org.uranxsu.manager.Natives
import org.uranxsu.manager.data.backup.BackupInspection
import org.uranxsu.manager.data.backup.BackupManifest
import org.uranxsu.manager.data.backup.BackupRestoreSummary
import org.uranxsu.manager.data.backup.BackupSection
import org.uranxsu.manager.data.backup.ConfigBackupManager
import org.uranxsu.manager.data.health.ComponentHealthRepository
import org.uranxsu.manager.data.ksud.KsudIntegrityRepository
import org.uranxsu.manager.data.ksud.KsudIntegritySnapshot
import org.uranxsu.manager.data.shell.KsuCliRepository
import org.uranxsu.manager.domain.model.ComponentHealthSummary
import org.uranxsu.manager.domain.usecase.RebootUseCase

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
    val health: ComponentHealthSummary = ComponentHealthSummary(),
    val cleanupEntries: List<CleanupEntry> = emptyList(),
    val totalCleanableBytes: Long = 0L,
)

class SystemHealthViewModel(
    private val application: Application,
    private val componentHealthRepository: ComponentHealthRepository,
    private val ksudIntegrityRepository: KsudIntegrityRepository,
    private val ksuCliRepository: KsuCliRepository,
    private val configBackupManager: ConfigBackupManager,
    private val rebootUseCase: RebootUseCase,
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
                val healthDeferred = async {
                    runCatching { componentHealthRepository.inspect() }.getOrDefault(ComponentHealthSummary())
                }
                val cleanupDeferred = async { scanLeftovers() }

                val health = healthDeferred.await()
                val cleanup = cleanupDeferred.await()

                SystemHealthUiState(
                    isRefreshing = false,
                    isBusy = mutableState.value.isBusy,
                    health = health,
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
                val updatedHealth = runCatching {
                    componentHealthRepository.inspect(preloadedKsud = snapshot)
                }.getOrNull()
                mutableState.update { cur ->
                    cur.copy(
                        health = updatedHealth ?: cur.health.copy(ksudIntegrity = snapshot),
                    )
                }
            }
            result
        } finally {
            mutableState.update { it.copy(isBusy = false) }
        }
    }

    suspend fun reboot(reason: String = ""): Result<Unit> = rebootUseCase(reason)

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
