package org.uranxsu.manager.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.uranxsu.manager.data.ksud.KsudIntegritySnapshot

@Serializable
data class BootSessionBundled(
    @SerialName("version_code") val versionCode: String = "",
    @SerialName("version_name") val versionName: String = "",
    @SerialName("ksud_hash") val ksudHash: String = "",
    @SerialName("userspace_hash") val userspaceHash: String = "",
    @SerialName("susfs_hash") val susfsHash: String = "",
    @SerialName("nomount_hash") val nomountHash: String = "",
    @SerialName("zygisk_hash") val zygiskHash: String = "",
)

@Serializable
data class BootSessionRecord(
    @SerialName("boot_id") val bootId: String = "",
    val stage: String = "",
    @SerialName("ksud_version_code") val ksudVersionCode: String = "",
    @SerialName("ksud_version_name") val ksudVersionName: String = "",
    @SerialName("ksud_hash") val ksudHash: String = "",
    @SerialName("userspace_hash") val userspaceHash: String = "",
    @SerialName("susfs_hash") val susfsHash: String = "",
    @SerialName("nomount_hash") val nomountHash: String = "",
    @SerialName("zygisk_hash") val zygiskHash: String = "",
    @SerialName("mount_mode") val mountMode: String = "",
    @SerialName("zygisk_builtin_enabled") val zygiskBuiltinEnabled: Boolean = false,
)

@Serializable
data class ComponentLoadedMarker(
    @SerialName("boot_id") val bootId: String = "",
    val hash: String = "",
    val version: String = "",
)

@Serializable
data class BootSessionReport(
    @SerialName("boot_id") val bootId: String = "",
    val bundled: BootSessionBundled = BootSessionBundled(),
    @SerialName("boot_session") val bootSession: BootSessionRecord? = null,
    @SerialName("susfs_loaded_marker") val susfsLoadedMarker: ComponentLoadedMarker? = null,
    @SerialName("nomount_loaded_marker") val nomountLoadedMarker: ComponentLoadedMarker? = null,
    @SerialName("zygisk_running_marker") val zygiskRunningMarker: ComponentLoadedMarker? = null,
)

enum class HealthComponentId {
    LKM,
    KSUD,
    SUSFS,
    NOMOUNT,
    ZYGISK,
}

enum class ComponentRebootReason {
    NONE,
    NOT_ROOTED_OR_UNKNOWN,
    LKM_UPDATE_REQUIRED,
    KSUD_NEEDS_SYNC,
    MANAGER_UPDATE_REBOOT,
    LEGACY_SESSION_REBOOT,
    MODE_CHANGED_REBOOT,
    NOT_RUNNING_REBOOT,
}

val ComponentRebootReason.requiresReboot: Boolean
    get() = when (this) {
        ComponentRebootReason.LKM_UPDATE_REQUIRED,
        ComponentRebootReason.MANAGER_UPDATE_REBOOT,
        ComponentRebootReason.LEGACY_SESSION_REBOOT,
        ComponentRebootReason.MODE_CHANGED_REBOOT,
        ComponentRebootReason.NOT_RUNNING_REBOOT,
        -> true

        ComponentRebootReason.NONE,
        ComponentRebootReason.NOT_ROOTED_OR_UNKNOWN,
        ComponentRebootReason.KSUD_NEEDS_SYNC,
        -> false
    }

data class ComponentHealthSummary(
    val isFullFeatured: Boolean = false,
    val lkmMode: Boolean? = null,
    val isLateLoadMode: Boolean = false,
    val runtimeLkmHash: String? = null,
    val bundledLkmHash: String = "",
    val kernelUapiVersion: Int? = null,
    val managerUapiVersion: Int = 0,
    val lkmWorking: Boolean = false,
    val lkmRebootReason: ComponentRebootReason = ComponentRebootReason.NONE,
    val ksudIntegrity: KsudIntegritySnapshot = KsudIntegritySnapshot(),
    val ksudWorking: Boolean = false,
    val ksudBootVersion: String = "",
    val ksudBootHash: String = "",
    val ksudBundledHash: String = "",
    val ksudRebootReason: ComponentRebootReason = ComponentRebootReason.NONE,
    val susfsVersion: String = "",
    val susfsVariant: String = "",
    val susfsLkmLoaded: Boolean = false,
    val susfsSource: String = "",
    val susfsActiveKmi: String = "",
    val susfsLastError: String = "",
    val susfsWorking: Boolean = false,
    val susfsBootHash: String = "",
    val susfsBundledHash: String = "",
    val susfsRebootReason: ComponentRebootReason = ComponentRebootReason.NONE,
    val mountMode: String = "no_mount",
    val bootMountMode: String = "",
    val nomountReady: Boolean = false,
    val nomountVersion: String = "",
    val nomountRules: Int = 0,
    val nomountBootloopDisabled: Boolean = false,
    val externalMetamoduleName: String = "",
    val externalMetamoduleEnabled: Boolean = false,
    val externalMetamoduleIsNomount: Boolean = false,
    val nomountWorking: Boolean = false,
    val nomountBootHash: String = "",
    val nomountBundledHash: String = "",
    val nomountRebootReason: ComponentRebootReason = ComponentRebootReason.NONE,
    val zygiskEnabled: Boolean = false,
    val zygiskRunning: Boolean = false,
    val zygiskInjected: Boolean = false,
    val zygiskVersion: String = "",
    val zygiskExternalName: String = "",
    val zygiskWorking: Boolean = false,
    val zygiskBootHash: String = "",
    val zygiskBundledHash: String = "",
    val zygiskRebootReason: ComponentRebootReason = ComponentRebootReason.NONE,
    val currentBootId: String = "",
    val hasBootSession: Boolean = false,
) {
    val componentsNeedingReboot: List<HealthComponentId>
        get() = buildList {
            if (lkmRebootReason.requiresReboot) add(HealthComponentId.LKM)
            if (ksudRebootReason.requiresReboot) add(HealthComponentId.KSUD)
            if (susfsRebootReason.requiresReboot) add(HealthComponentId.SUSFS)
            if (nomountRebootReason.requiresReboot) add(HealthComponentId.NOMOUNT)
            if (zygiskRebootReason.requiresReboot) add(HealthComponentId.ZYGISK)
        }

    val anyRebootRequired: Boolean
        get() = componentsNeedingReboot.isNotEmpty()
}
