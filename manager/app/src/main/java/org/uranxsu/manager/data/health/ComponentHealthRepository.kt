package org.uranxsu.manager.data.health

import com.topjohnwu.superuser.ShellUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.uranxsu.manager.BuildConfig
import org.uranxsu.manager.data.kernel.KernelRepository
import org.uranxsu.manager.data.ksud.KsudIntegrityRepository
import org.uranxsu.manager.data.ksud.KsudIntegritySnapshot
import org.uranxsu.manager.data.ksud.KsudSyncStatus
import org.uranxsu.manager.data.mount.MountRepository
import org.uranxsu.manager.data.shell.KsuCliRepository
import org.uranxsu.manager.data.susfs.SuSFSRepository
import org.uranxsu.manager.data.zygisk.ZygiskRepository
import org.uranxsu.manager.domain.model.BootSessionReport
import org.uranxsu.manager.domain.model.ComponentHealthSummary
import org.uranxsu.manager.domain.model.ComponentLoadedMarker
import org.uranxsu.manager.domain.model.ComponentRebootReason
import org.uranxsu.manager.domain.model.KernelStatus
import org.uranxsu.manager.domain.model.SuSFSStatus

/**
 * Aggregates live operational state and post-update reboot status for Kernel/LKM,
 * ksud daemon, SUSFS, NoMount, and BuiltZygisk across Home and System Health screens.
 */
class ComponentHealthRepository(
    private val kernelRepository: KernelRepository,
    private val ksudIntegrityRepository: KsudIntegrityRepository,
    private val mountRepository: MountRepository,
    private val suSFSRepository: SuSFSRepository,
    private val zygiskRepository: ZygiskRepository,
    private val ksuCliRepository: KsuCliRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun inspect(
        preloadedKernel: KernelStatus? = null,
        preloadedKsud: KsudIntegritySnapshot? = null,
        preloadedSusfs: SuSFSStatus? = null,
        preloadedZygiskImpl: String? = null,
    ): ComponentHealthSummary = coroutineScope {
        val kernelDeferred = async {
            preloadedKernel ?: runCatching { kernelRepository.getStatus() }.getOrNull()
        }
        val ksudDeferred = async {
            preloadedKsud ?: runCatching { ksudIntegrityRepository.checkStatus() }
                .getOrDefault(KsudIntegritySnapshot())
        }

        val kernel = kernelDeferred.await()
        val ksud = ksudDeferred.await()
        val hasFullRoot = kernel?.isFullFeatured == true || kernel?.isRootAvailable == true

        val bootSessionDeferred = async {
            if (hasFullRoot) readBootSessionReport() else null
        }
        val mountOverallDeferred = async {
            if (hasFullRoot) runCatching { mountRepository.getOverallStatus() }.getOrNull() else null
        }
        val nomountDeferred = async {
            if (hasFullRoot) runCatching { mountRepository.getNoMountStatus() }.getOrNull() else null
        }
        val susfsDeferred = async {
            if (hasFullRoot) {
                runCatching { suSFSRepository.loadStatusInfo(true) }.getOrNull()
            } else {
                null
            }
        }
        val susfsLkmDeferred = async {
            if (hasFullRoot) runCatching { suSFSRepository.loadLkmStatus(true) }.getOrNull() else null
        }
        val zygiskDeferred = async {
            if (hasFullRoot) runCatching { zygiskRepository.getStatus() }.getOrNull() else null
        }
        val zygiskImplDeferred = async {
            preloadedZygiskImpl ?: if (hasFullRoot) {
                runCatching { ksuCliRepository.getZygiskImplement() }.getOrDefault("None")
            } else {
                "None"
            }
        }

        val report = bootSessionDeferred.await()
        val mountOverall = mountOverallDeferred.await()
        val nomount = nomountDeferred.await()
        val susfs = susfsDeferred.await()
        val susfsLkm = susfsLkmDeferred.await()
        val zygisk = zygiskDeferred.await()
        val zygiskImpl = zygiskImplDeferred.await()

        val currentBootId = report?.bootId.orEmpty()
        val session = report?.bootSession
        val sessionMatchesBoot = session != null &&
            (currentBootId.isBlank() || session.bootId == currentBootId)

        // 1. Kernel / LKM
        val lkmMode = kernel?.lkmMode
        val lkmWorking = kernel?.isManager == true && kernel.isFullFeatured
        val lkmRebootReason = when {
            kernel == null || !kernel.isManager -> ComponentRebootReason.NOT_ROOTED_OR_UNKNOWN
            lkmMode != true -> ComponentRebootReason.NONE
            kernel.needsLkmUpdate -> ComponentRebootReason.LKM_UPDATE_REQUIRED
            else -> ComponentRebootReason.NONE
        }

        // 2. ksud daemon
        val bundledKsudHash = BuildConfig.BUNDLED_KSUD_HASH
        val ksudWorking = ksud.status == KsudSyncStatus.UP_TO_DATE
        val ksudRebootReason = when {
            kernel?.isManager != true -> ComponentRebootReason.NOT_ROOTED_OR_UNKNOWN

            ksud.status == KsudSyncStatus.UNAVAILABLE -> ComponentRebootReason.NOT_ROOTED_OR_UNKNOWN

            ksud.status == KsudSyncStatus.MISSING || ksud.status == KsudSyncStatus.NEEDS_SYNC ->
                ComponentRebootReason.KSUD_NEEDS_SYNC

            !hasFullRoot -> ComponentRebootReason.NONE

            !sessionMatchesBoot -> ComponentRebootReason.LEGACY_SESSION_REBOOT

            isHashKnown(bundledKsudHash) &&
                isHashKnown(session.ksudHash) &&
                session.ksudHash != bundledKsudHash -> ComponentRebootReason.MANAGER_UPDATE_REBOOT

            else -> ComponentRebootReason.NONE
        }

        // 3. SUSFS
        val susfsVersion = susfs?.version.orEmpty()
            .ifBlank { preloadedSusfs?.version.orEmpty() }
            .ifBlank { susfsLkm?.version.orEmpty() }
        val susfsVariant = susfs?.variant.orEmpty().ifBlank { susfsLkm?.variant.orEmpty() }
        val susfsLkmLoaded = susfsLkm?.lkmLoaded == true
        val susfsWorking = susfsLkmLoaded ||
            (susfsVersion.isNotBlank() && !susfsVersion.equals("unavailable", ignoreCase = true))
        val bundledSusfsHash = BuildConfig.BUNDLED_SUSFS_HASH
        val susfsMarker = report?.susfsLoadedMarker
        val susfsRebootReason = when {
            !hasFullRoot -> ComponentRebootReason.NOT_ROOTED_OR_UNKNOWN

            !susfsLkmLoaded -> ComponentRebootReason.NONE

            !markerMatchesBoot(currentBootId, susfsMarker) -> ComponentRebootReason.LEGACY_SESSION_REBOOT

            isHashKnown(bundledSusfsHash) &&
                susfsMarker?.hash != bundledSusfsHash -> ComponentRebootReason.MANAGER_UPDATE_REBOOT

            else -> ComponentRebootReason.NONE
        }

        // 4. NoMount / Mount Engine
        val mountMode = mountOverall?.mode?.ifBlank { "no_mount" } ?: "no_mount"
        val nomountReady = nomount?.driverReady == true || mountOverall?.nomountDriverReady == true
        val nomountVersion = nomount?.driverVersion.orEmpty().ifBlank {
            mountOverall?.nomountDriverVersion.orEmpty()
        }
        val nomountRules = nomount?.ruleCount ?: mountOverall?.nomountRuleCount ?: 0
        val extMetaName = mountOverall?.externalMetamoduleName.orEmpty()
            .ifBlank { mountOverall?.externalMetamoduleId.orEmpty() }
        val extMetaEnabled = mountOverall?.externalMetamoduleEnabled == true
        val extMetaIsNomount = mountOverall?.externalMetamoduleIsNomount == true
        val nomountWorking = if (mountMode == "no_mount") {
            nomountReady
        } else {
            nomountReady || extMetaEnabled
        }
        val bundledNomountHash = BuildConfig.BUNDLED_NOMOUNT_HASH
        val nomountMarker = report?.nomountLoadedMarker
        val bootMountMode = session?.takeIf { sessionMatchesBoot }?.mountMode.orEmpty()
        val internalNomountInCharge = mountMode == "no_mount" || !extMetaIsNomount
        val nomountRebootReason = when {
            !hasFullRoot -> ComponentRebootReason.NOT_ROOTED_OR_UNKNOWN

            bootMountMode.isNotBlank() && bootMountMode != mountMode ->
                ComponentRebootReason.MODE_CHANGED_REBOOT

            nomountReady && internalNomountInCharge -> when {
                !markerMatchesBoot(currentBootId, nomountMarker) ->
                    ComponentRebootReason.LEGACY_SESSION_REBOOT

                isHashKnown(bundledNomountHash) && nomountMarker?.hash != bundledNomountHash ->
                    ComponentRebootReason.MANAGER_UPDATE_REBOOT

                else -> ComponentRebootReason.NONE
            }

            mountMode == "no_mount" && !nomountReady &&
                (nomount?.availableKmis?.isNotEmpty() == true || nomount?.bootloopDisabled == true) ->
                ComponentRebootReason.NOT_RUNNING_REBOOT

            else -> ComponentRebootReason.NONE
        }

        // 5. Zygisk (BuiltZygisk or External)
        val zygiskEnabled = zygisk?.enabled == true
        val zygiskRunning = zygisk?.fullyActive == true ||
            zygisk?.daemonRunning == true ||
            zygisk?.monitorRunning == true
        val zygiskExternalName = if (!zygiskEnabled &&
            zygiskImpl.isNotBlank() &&
            !zygiskImpl.equals("None", ignoreCase = true)
        ) {
            zygiskImpl
        } else {
            ""
        }
        val zygiskWorking = if (zygiskEnabled) {
            zygiskRunning
        } else {
            zygiskExternalName.isNotBlank()
        }
        val bundledZygiskHash = BuildConfig.BUNDLED_ZYGISK_HASH
        val zygiskMarker = report?.zygiskRunningMarker
        val bootZygiskEnabled = session?.takeIf { sessionMatchesBoot }?.zygiskBuiltinEnabled
        val zygiskRebootReason = when {
            !hasFullRoot -> ComponentRebootReason.NOT_ROOTED_OR_UNKNOWN

            bootZygiskEnabled != null && bootZygiskEnabled != zygiskEnabled ->
                ComponentRebootReason.MODE_CHANGED_REBOOT

            zygiskEnabled -> when {
                !zygiskRunning -> ComponentRebootReason.NOT_RUNNING_REBOOT

                !markerMatchesBoot(currentBootId, zygiskMarker) ->
                    ComponentRebootReason.LEGACY_SESSION_REBOOT

                isHashKnown(bundledZygiskHash) && zygiskMarker?.hash != bundledZygiskHash ->
                    ComponentRebootReason.MANAGER_UPDATE_REBOOT

                else -> ComponentRebootReason.NONE
            }

            zygiskRunning -> ComponentRebootReason.MODE_CHANGED_REBOOT

            else -> ComponentRebootReason.NONE
        }

        ComponentHealthSummary(
            isFullFeatured = kernel?.isFullFeatured == true,
            lkmMode = lkmMode,
            isLateLoadMode = kernel?.isLateLoadMode == true,
            runtimeLkmHash = kernel?.runtimeLkmHash,
            bundledLkmHash = BuildConfig.BUNDLED_LKM_HASH,
            kernelUapiVersion = kernel?.kernelUAPIVersion,
            managerUapiVersion = kernel?.managerUAPIVersion ?: 0,
            lkmWorking = lkmWorking,
            lkmRebootReason = lkmRebootReason,
            ksudIntegrity = ksud,
            ksudWorking = ksudWorking,
            ksudBootVersion = session?.takeIf { sessionMatchesBoot }?.ksudVersionName.orEmpty(),
            ksudBootHash = session?.takeIf { sessionMatchesBoot }?.ksudHash.orEmpty(),
            ksudBundledHash = bundledKsudHash,
            ksudRebootReason = ksudRebootReason,
            susfsVersion = susfsVersion,
            susfsVariant = susfsVariant,
            susfsLkmLoaded = susfsLkmLoaded,
            susfsSource = susfsLkm?.source.orEmpty(),
            susfsActiveKmi = susfsLkm?.activeKmi.orEmpty().ifBlank { susfsLkm?.detectedKmi.orEmpty() },
            susfsLastError = susfsLkm?.lastError.orEmpty(),
            susfsWorking = susfsWorking,
            susfsBootHash = susfsMarker?.takeIf { markerMatchesBoot(currentBootId, it) }?.hash.orEmpty(),
            susfsBundledHash = bundledSusfsHash,
            susfsRebootReason = susfsRebootReason,
            mountMode = mountMode,
            bootMountMode = bootMountMode,
            nomountReady = nomountReady,
            nomountVersion = nomountVersion,
            nomountRules = nomountRules,
            nomountBootloopDisabled = nomount?.bootloopDisabled == true,
            externalMetamoduleName = extMetaName,
            externalMetamoduleEnabled = extMetaEnabled,
            externalMetamoduleIsNomount = extMetaIsNomount,
            nomountWorking = nomountWorking,
            nomountBootHash = nomountMarker?.takeIf { markerMatchesBoot(currentBootId, it) }?.hash.orEmpty(),
            nomountBundledHash = bundledNomountHash,
            nomountRebootReason = nomountRebootReason,
            zygiskEnabled = zygiskEnabled,
            zygiskRunning = zygiskRunning,
            zygiskInjected = zygisk?.zygoteInjected == true,
            zygiskVersion = zygisk?.version.orEmpty(),
            zygiskExternalName = zygiskExternalName,
            zygiskWorking = zygiskWorking,
            zygiskBootHash = zygiskMarker?.takeIf { markerMatchesBoot(currentBootId, it) }?.hash.orEmpty(),
            zygiskBundledHash = bundledZygiskHash,
            zygiskRebootReason = zygiskRebootReason,
            currentBootId = currentBootId,
            hasBootSession = sessionMatchesBoot,
        )
    }

    private suspend fun readBootSessionReport(): BootSessionReport? = withContext(Dispatchers.IO) {
        runCatching {
            val shell = ksuCliRepository.getRootShell()
            val ksud = KsuCliRepository.shellQuote(ksuCliRepository.getKsuDaemonPath())
            val output = ShellUtils.fastCmd(shell, "$ksud debug boot-session 2>/dev/null").trim()
            if (output.isBlank() || !output.startsWith("{")) return@runCatching null
            json.decodeFromString<BootSessionReport>(output)
        }.getOrNull()
    }

    private fun isHashKnown(hash: String?): Boolean = !hash.isNullOrBlank() && hash != "unknown"

    private fun markerMatchesBoot(currentBootId: String, marker: ComponentLoadedMarker?): Boolean {
        if (marker == null || !isHashKnown(marker.hash)) return false
        return currentBootId.isBlank() || marker.bootId == currentBootId
    }
}
