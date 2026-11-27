package org.uranxsu.manager.data.backup

import android.app.Application
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.topjohnwu.superuser.ShellUtils
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.uranxsu.manager.BuildConfig
import org.uranxsu.manager.Natives
import org.uranxsu.manager.data.AppSettingsRepository
import org.uranxsu.manager.data.profile.ProfileRepository
import org.uranxsu.manager.data.settings.SettingsPlatformRepository
import org.uranxsu.manager.data.shell.KsuCliRepository
import org.uranxsu.manager.data.susfs.SuSFSConfigHelper
import org.uranxsu.manager.domain.model.AllowlistRestoreResult
import org.uranxsu.manager.ui.theme.BackgroundManager
import org.uranxsu.manager.ui.theme.CardConfig
import org.uranxsu.manager.ui.theme.HomeCustomizationConfig
import org.uranxsu.manager.ui.theme.ThemeConfig
import org.uranxsu.manager.ui.util.CustomTextConfig
import org.uranxsu.manager.ui.util.ThemeApplyOptions
import org.uranxsu.manager.ui.util.ThemePackageApplier
import org.uranxsu.manager.ui.util.ThemePackageStorage

enum class BackupSection(val id: String) {
    ALLOWLIST("allowlist"),
    SUSFS("susfs"),
    NOMOUNT("nomount"),
    NOSHOT("noshot"),
    APPEARANCE("appearance"),
    ;

    companion object {
        fun fromId(id: String): BackupSection? = entries.firstOrNull { it.id == id }
    }
}

@Serializable
data class BackupManifest(
    val version: Int = 1,
    val app: String = "UranXSU",
    val createdAtEpochMs: Long = 0L,
    val appVersionName: String = "",
    val sections: List<String> = emptyList(),
)

data class BackupInspection(
    val manifest: BackupManifest,
    val availableSections: Set<BackupSection>,
)

data class BackupRestoreSummary(
    val restored: Set<BackupSection>,
    val failed: Map<BackupSection, String>,
)

class ConfigBackupManager(
    private val application: Application,
    private val ksuCliRepository: KsuCliRepository,
    private val profileRepository: ProfileRepository,
    private val suSFSConfigHelper: SuSFSConfigHelper,
    private val appSettingsRepository: AppSettingsRepository,
    private val settingsPlatformRepository: SettingsPlatformRepository,
    private val themeConfig: ThemeConfig,
    private val cardConfig: CardConfig,
    private val backgroundManager: BackgroundManager,
    private val homeCustomizationConfig: HomeCustomizationConfig,
    private val customTextConfig: CustomTextConfig,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    companion object {
        const val BACKUP_EXTENSION = ".uranxsu-backup"
        private const val MANIFEST_ENTRY = "manifest.json"
        private const val ALLOWLIST_PATH = "/data/adb/ksu/.allowlist"
        private const val ALLOWLIST_ENTRY = "allowlist/allowlist.bin"
        private const val APPEARANCE_ENTRY = "appearance/theme.uxtheme"
        private const val MAX_ENTRY_BYTES = 64L * 1024L * 1024L

        private val SUSFS_FILES = listOf(
            "susfs/susfs.json" to "/data/adb/ksu/.susfs.json",
            "susfs/susfs_lkm.json" to "/data/adb/ksu/.susfs_lkm.json",
        )

        private val NOMOUNT_FILES = listOf(
            "nomount/.mount_config.json" to "/data/adb/ksu/.mount_config.json",
            "nomount/.umount" to "/data/adb/ksu/.umount",
            "nomount/.exclusion_list.json" to "/data/adb/nomount/.exclusion_list.json",
            "nomount/.block_isolated_uids" to "/data/adb/nomount/.block_isolated_uids",
            "nomount/disable" to "/data/adb/nomount/disable",
            "nomount/dump_rules" to "/data/adb/nomount/dump_rules",
        )

        private val NOSHOT_FILES = listOf(
            "noshot/allow.list" to "/data/adb/ksu/noshot/allow.list",
            "noshot/settings.conf" to "/data/adb/ksu/noshot/settings.conf",
            "noshot/enabled" to "/data/adb/ksu/noshot/enabled",
            "noshot/disable_system" to "/data/adb/ksu/noshot/disable_system",
        )

        fun backupFileName(now: Date = Date()): String {
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(now)
            return "UranXSU-backup-$stamp$BACKUP_EXTENSION"
        }
    }

    suspend fun exportBackup(
        uri: Uri,
        sections: Set<BackupSection> = BackupSection.entries.toSet(),
    ): Result<BackupManifest> = withContext(Dispatchers.IO) {
        runCatching {
            val stagedEntries = linkedMapOf<String, ByteArray>()
            val includedSections = linkedSetOf<BackupSection>()
            val canUseRoot = runCatching { Natives.isManager && ksuCliRepository.rootAvailable() }.getOrDefault(false)

            if (BackupSection.ALLOWLIST in sections && canUseRoot) {
                readRootFileBytes(ALLOWLIST_PATH)?.let { bytes ->
                    stagedEntries[ALLOWLIST_ENTRY] = bytes
                    includedSections += BackupSection.ALLOWLIST
                }
            }

            if (BackupSection.SUSFS in sections && canUseRoot) {
                var added = false
                SUSFS_FILES.forEach { (entry, path) ->
                    readRootFileBytes(path)?.let { bytes ->
                        stagedEntries[entry] = bytes
                        added = true
                    }
                }
                if (added) includedSections += BackupSection.SUSFS
            }

            if (BackupSection.NOMOUNT in sections && canUseRoot) {
                var added = false
                NOMOUNT_FILES.forEach { (entry, path) ->
                    readRootFileBytes(path)?.let { bytes ->
                        stagedEntries[entry] = bytes
                        added = true
                    }
                }
                if (added) includedSections += BackupSection.NOMOUNT
            }

            if (BackupSection.NOSHOT in sections && canUseRoot) {
                var added = false
                NOSHOT_FILES.forEach { (entry, path) ->
                    readRootFileBytes(path)?.let { bytes ->
                        stagedEntries[entry] = bytes
                        added = true
                    }
                }
                if (added) includedSections += BackupSection.NOSHOT
            }

            if (BackupSection.APPEARANCE in sections) {
                val payload = ThemePackageApplier.buildExportPackage(
                    context = application,
                    settings = appSettingsRepository,
                    settingsPlatformRepository = settingsPlatformRepository,
                    themeConfig = themeConfig,
                    cardConfig = cardConfig,
                    backgroundManager = backgroundManager,
                    homeCustomization = homeCustomizationConfig,
                    customTextConfig = customTextConfig,
                )
                val uxBytes = ByteArrayOutputStream().apply {
                    ThemePackageStorage.exportToStream(this, payload)
                }.toByteArray()
                stagedEntries[APPEARANCE_ENTRY] = uxBytes
                includedSections += BackupSection.APPEARANCE
            }

            val manifest = BackupManifest(
                version = 1,
                app = "UranXSU",
                createdAtEpochMs = System.currentTimeMillis(),
                appVersionName = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                sections = includedSections.map { it.id },
            )

            val output = application.contentResolver.openOutputStream(uri)
                ?: error("Cannot open output stream for backup")
            output.use { out ->
                ZipOutputStream(BufferedOutputStream(out)).use { zos ->
                    zos.putNextEntry(ZipEntry(MANIFEST_ENTRY))
                    zos.write(json.encodeToString(manifest).toByteArray(Charsets.UTF_8))
                    zos.closeEntry()

                    stagedEntries.forEach { (entryName, bytes) ->
                        zos.putNextEntry(ZipEntry(entryName))
                        zos.write(bytes)
                        zos.closeEntry()
                    }
                }
            }
            manifest
        }
    }

    suspend fun inspectBackup(uri: Uri): Result<BackupInspection> = withContext(Dispatchers.IO) {
        runCatching {
            val input = application.contentResolver.openInputStream(uri)
                ?: error("Cannot open backup file")
            var manifest: BackupManifest? = null
            val detected = linkedSetOf<BackupSection>()

            input.use { raw ->
                ZipInputStream(BufferedInputStream(raw)).use { zis ->
                    while (true) {
                        val entry = zis.nextEntry ?: break
                        val name = entry.name
                        if (!entry.isDirectory && isSafeEntryName(name)) {
                            when {
                                name == MANIFEST_ENTRY -> {
                                    val bytes = readBounded(zis, 1024L * 1024L)
                                    manifest = runCatching {
                                        json.decodeFromString<BackupManifest>(bytes.toString(Charsets.UTF_8))
                                    }.getOrNull()
                                }

                                name == ALLOWLIST_ENTRY -> detected += BackupSection.ALLOWLIST

                                name.startsWith("susfs/") -> detected += BackupSection.SUSFS

                                name.startsWith("nomount/") -> detected += BackupSection.NOMOUNT

                                name.startsWith("noshot/") -> detected += BackupSection.NOSHOT

                                name == APPEARANCE_ENTRY -> detected += BackupSection.APPEARANCE
                            }
                        }
                        zis.closeEntry()
                    }
                }
            }

            val finalManifest = manifest ?: BackupManifest(sections = detected.map { it.id })
            if (detected.isEmpty()) {
                finalManifest.sections.mapNotNull { BackupSection.fromId(it) }.forEach { detected += it }
            }
            BackupInspection(
                manifest = finalManifest,
                availableSections = detected,
            )
        }
    }

    suspend fun restoreBackup(
        uri: Uri,
        sections: Set<BackupSection>,
    ): Result<BackupRestoreSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val tempDir = File(application.cacheDir, "backup_restore_${UUID.randomUUID()}").apply {
                mkdirs()
            }
            try {
                val extracted = linkedMapOf<String, File>()
                val input = application.contentResolver.openInputStream(uri)
                    ?: error("Cannot open backup file")
                input.use { raw ->
                    ZipInputStream(BufferedInputStream(raw)).use { zis ->
                        while (true) {
                            val entry = zis.nextEntry ?: break
                            val name = entry.name
                            if (!entry.isDirectory && isSafeEntryName(name) && shouldExtractEntry(name, sections)) {
                                val safeLocalName = name.replace('/', '_')
                                val outFile = File(tempDir, safeLocalName)
                                copyBounded(zis, outFile, MAX_ENTRY_BYTES)
                                extracted[name] = outFile
                            }
                            zis.closeEntry()
                        }
                    }
                }

                val restored = linkedSetOf<BackupSection>()
                val failed = linkedMapOf<BackupSection, String>()
                val canUseRoot = runCatching { Natives.isManager && ksuCliRepository.rootAvailable() }.getOrDefault(false)

                if (BackupSection.ALLOWLIST in sections) {
                    val binFile = extracted[ALLOWLIST_ENTRY]
                    if (binFile != null) {
                        runCatching {
                            check(canUseRoot) { "Root unavailable" }
                            val result = ParcelFileDescriptor.open(
                                binFile,
                                ParcelFileDescriptor.MODE_READ_ONLY,
                            ).use { pfd ->
                                profileRepository.restoreAllowlist(pfd.fd)
                            }
                            check(result == AllowlistRestoreResult.Success) {
                                "Allowlist restore returned $result"
                            }
                        }.onSuccess {
                            restored += BackupSection.ALLOWLIST
                        }.onFailure {
                            failed[BackupSection.ALLOWLIST] = it.message ?: "Restore failed"
                        }
                    }
                }

                if (BackupSection.SUSFS in sections) {
                    val mainJson = extracted["susfs/susfs.json"]
                    val lkmJson = extracted["susfs/susfs_lkm.json"]
                    if (mainJson != null || lkmJson != null) {
                        runCatching {
                            check(canUseRoot) { "Root unavailable" }
                            if (mainJson != null) {
                                val ksud = KsuCliRepository.shellQuote(ksuCliRepository.getKsuDaemonPath())
                                val src = KsuCliRepository.shellQuote(mainJson.absolutePath)
                                val ok = ShellUtils.fastCmdResult(
                                    ksuCliRepository.getRootShell(),
                                    "$ksud susfs config restore $src",
                                )
                                if (!ok) {
                                    writeRootFileFromLocal(mainJson, "/data/adb/ksu/.susfs.json", "0644")
                                }
                            }
                            if (lkmJson != null) {
                                writeRootFileFromLocal(lkmJson, "/data/adb/ksu/.susfs_lkm.json", "0644")
                            }
                            suSFSConfigHelper.refreshConfig()
                        }.onSuccess {
                            restored += BackupSection.SUSFS
                        }.onFailure {
                            failed[BackupSection.SUSFS] = it.message ?: "Restore failed"
                        }
                    }
                }

                if (BackupSection.NOMOUNT in sections) {
                    val present = NOMOUNT_FILES.mapNotNull { (entry, path) ->
                        extracted[entry]?.let { it to path }
                    }
                    if (present.isNotEmpty()) {
                        runCatching {
                            check(canUseRoot) { "Root unavailable" }
                            val shell = ksuCliRepository.getRootShell()
                            ShellUtils.fastCmdResult(shell, "mkdir -p /data/adb/ksu /data/adb/nomount")
                            present.forEach { (local, dst) ->
                                check(writeRootFileFromLocal(local, dst, "0644")) {
                                    "Failed writing $dst"
                                }
                            }
                        }.onSuccess {
                            restored += BackupSection.NOMOUNT
                        }.onFailure {
                            failed[BackupSection.NOMOUNT] = it.message ?: "Restore failed"
                        }
                    }
                }

                if (BackupSection.NOSHOT in sections) {
                    val present = NOSHOT_FILES.mapNotNull { (entry, path) ->
                        extracted[entry]?.let { it to path }
                    }
                    if (present.isNotEmpty()) {
                        runCatching {
                            check(canUseRoot) { "Root unavailable" }
                            val shell = ksuCliRepository.getRootShell()
                            ShellUtils.fastCmdResult(shell, "mkdir -p /data/adb/ksu/noshot && chmod 0755 /data/adb/ksu/noshot")
                            present.forEach { (local, dst) ->
                                check(writeRootFileFromLocal(local, dst, "0644")) {
                                    "Failed writing $dst"
                                }
                            }
                            val ksud = KsuCliRepository.shellQuote(ksuCliRepository.getKsuDaemonPath())
                            if (extracted.containsKey("noshot/enabled")) {
                                ShellUtils.fastCmdResult(shell, "$ksud noshot enable")
                            } else {
                                ShellUtils.fastCmdResult(shell, "[ ! -f /data/adb/ksu/noshot/enabled ] || $ksud noshot sync-plan")
                            }
                        }.onSuccess {
                            restored += BackupSection.NOSHOT
                        }.onFailure {
                            failed[BackupSection.NOSHOT] = it.message ?: "Restore failed"
                        }
                    }
                }

                if (BackupSection.APPEARANCE in sections) {
                    val uxFile = extracted[APPEARANCE_ENTRY]
                    if (uxFile != null) {
                        runCatching {
                            val imported = uxFile.inputStream().use { stream ->
                                ThemePackageStorage.importFromStream(application, stream).getOrThrow()
                            }
                            try {
                                ThemePackageApplier.applyWithoutViewModels(
                                    context = application,
                                    imported = imported,
                                    options = ThemeApplyOptions(),
                                    settings = appSettingsRepository,
                                    settingsPlatformRepository = settingsPlatformRepository,
                                    backgroundManager = backgroundManager,
                                    homeCustomization = homeCustomizationConfig,
                                    customTextConfig = customTextConfig,
                                )
                            } finally {
                                imported.cleanup()
                            }
                        }.onSuccess {
                            restored += BackupSection.APPEARANCE
                        }.onFailure {
                            failed[BackupSection.APPEARANCE] = it.message ?: "Restore failed"
                        }
                    }
                }

                BackupRestoreSummary(restored = restored, failed = failed)
            } finally {
                runCatching { tempDir.deleteRecursively() }
            }
        }
    }

    private fun readRootFileBytes(rootPath: String): ByteArray? {
        val shell = ksuCliRepository.getRootShell()
        val quotedSrc = KsuCliRepository.shellQuote(rootPath)
        val tmpFile = File(application.cacheDir, "bk_read_${UUID.randomUUID()}.bin")
        val quotedTmp = KsuCliRepository.shellQuote(tmpFile.absolutePath)
        return try {
            val ok = ShellUtils.fastCmdResult(
                shell,
                "[ -f $quotedSrc ] && cp $quotedSrc $quotedTmp && chmod 0644 $quotedTmp",
            )
            if (!ok || !tmpFile.isFile) {
                null
            } else {
                tmpFile.readBytes()
            }
        } finally {
            runCatching { tmpFile.delete() }
        }
    }

    private fun writeRootFileFromLocal(
        localFile: File,
        rootDestPath: String,
        mode: String,
    ): Boolean {
        val shell = ksuCliRepository.getRootShell()
        val src = KsuCliRepository.shellQuote(localFile.absolutePath)
        val dst = KsuCliRepository.shellQuote(rootDestPath)
        val dstTmp = KsuCliRepository.shellQuote("$rootDestPath.tmp")
        val cmd = "cp $src $dstTmp && chmod $mode $dstTmp && mv -f $dstTmp $dst"
        return ShellUtils.fastCmdResult(shell, cmd)
    }

    private fun shouldExtractEntry(entryName: String, sections: Set<BackupSection>): Boolean = when {
        entryName == ALLOWLIST_ENTRY -> BackupSection.ALLOWLIST in sections
        entryName.startsWith("susfs/") -> BackupSection.SUSFS in sections
        entryName.startsWith("nomount/") -> BackupSection.NOMOUNT in sections
        entryName.startsWith("noshot/") -> BackupSection.NOSHOT in sections
        entryName == APPEARANCE_ENTRY -> BackupSection.APPEARANCE in sections
        else -> false
    }

    private fun isSafeEntryName(name: String): Boolean = name.isNotBlank() &&
        !name.contains("..") &&
        !name.startsWith("/") &&
        !name.startsWith("\\")

    private fun readBounded(input: java.io.InputStream, maxBytes: Long): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > maxBytes) error("Entry exceeds size cap")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun copyBounded(input: java.io.InputStream, dest: File, maxBytes: Long) {
        val buf = ByteArray(8192)
        var total = 0L
        FileOutputStream(dest).use { out ->
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > maxBytes) error("Entry exceeds size cap")
                out.write(buf, 0, n)
            }
            out.flush()
        }
    }
}
