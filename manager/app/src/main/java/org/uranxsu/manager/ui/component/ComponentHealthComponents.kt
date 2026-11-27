package org.uranxsu.manager.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.uranxsu.manager.R
import org.uranxsu.manager.data.ksud.KsudSyncStatus
import org.uranxsu.manager.domain.model.ComponentHealthSummary
import org.uranxsu.manager.domain.model.ComponentRebootReason
import org.uranxsu.manager.domain.model.HealthComponentId
import org.uranxsu.manager.domain.model.requiresReboot

private val ksudVersionPattern = Regex(
    """^(?:ksud\s+)?([^\s(]+)(?:\s*\(uapi:\s*([^)]+)\))?""",
    RegexOption.IGNORE_CASE,
)

private fun formatKsudCompact(raw: String): String {
    val trimmed = raw.trim()
    val match = ksudVersionPattern.find(trimmed)
    val ver = match?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return trimmed
    return if (ver.firstOrNull()?.isDigit() == true) "v$ver" else ver
}

private fun formatKsudDetailed(raw: String): String {
    val trimmed = raw.trim()
    val match = ksudVersionPattern.find(trimmed) ?: return trimmed
    val verRaw = match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return trimmed
    val ver = if (verRaw.firstOrNull()?.isDigit() == true) "v$verRaw" else verRaw
    val uapi = match.groupValues.getOrNull(2)?.trim()?.takeIf { it.isNotBlank() }
    return if (uapi != null) "$ver · UAPI $uapi" else ver
}

@Composable
fun componentRebootLabel(reason: ComponentRebootReason): String = when (reason) {
    ComponentRebootReason.NONE -> stringResource(R.string.health_reboot_state_none)
    ComponentRebootReason.NOT_ROOTED_OR_UNKNOWN -> stringResource(R.string.health_reboot_state_unknown)
    ComponentRebootReason.LKM_UPDATE_REQUIRED -> stringResource(R.string.health_reboot_state_lkm_update)
    ComponentRebootReason.KSUD_NEEDS_SYNC -> stringResource(R.string.health_reboot_state_ksud_sync)
    ComponentRebootReason.MANAGER_UPDATE_REBOOT -> stringResource(R.string.health_reboot_state_manager_update)
    ComponentRebootReason.LEGACY_SESSION_REBOOT -> stringResource(R.string.health_reboot_state_legacy_session)
    ComponentRebootReason.MODE_CHANGED_REBOOT -> stringResource(R.string.health_reboot_state_mode_changed)
    ComponentRebootReason.NOT_RUNNING_REBOOT -> stringResource(R.string.health_reboot_state_not_running)
}

@Composable
fun componentLiveStateText(
    health: ComponentHealthSummary,
    component: HealthComponentId,
): String = when (component) {
    HealthComponentId.LKM -> when {
        health.lkmMode != true && health.isFullFeatured ->
            stringResource(R.string.health_live_lkm_builtin)

        health.lkmWorking && !health.runtimeLkmHash.isNullOrBlank() ->
            stringResource(R.string.health_live_lkm_active, health.runtimeLkmHash)

        health.lkmWorking ->
            stringResource(R.string.health_live_lkm_active_no_hash)

        else ->
            stringResource(R.string.health_live_lkm_inactive)
    }

    HealthComponentId.KSUD -> when (health.ksudIntegrity.status) {
        KsudSyncStatus.UP_TO_DATE -> stringResource(
            R.string.health_live_ksud_active,
            formatKsudDetailed(health.ksudIntegrity.installedVersion ?: health.ksudIntegrity.bundledVersion),
        )

        KsudSyncStatus.NEEDS_SYNC -> stringResource(
            R.string.health_live_ksud_needs_sync,
            formatKsudDetailed(health.ksudIntegrity.installedVersion ?: "?"),
        )

        KsudSyncStatus.MISSING -> stringResource(R.string.health_live_ksud_missing)

        KsudSyncStatus.UNAVAILABLE -> stringResource(R.string.health_live_ksud_unavailable)
    }

    HealthComponentId.SUSFS -> when {
        health.susfsLkmLoaded -> {
            val detail = listOfNotNull(
                health.susfsVersion.takeIf { it.isNotBlank() },
                health.susfsVariant.takeIf { it.isNotBlank() },
                health.susfsActiveKmi.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (detail.isNotBlank()) {
                stringResource(R.string.health_live_susfs_lkm, detail)
            } else {
                stringResource(R.string.health_live_susfs_lkm_simple)
            }
        }

        health.susfsWorking -> {
            val detail = listOfNotNull(
                health.susfsVersion.takeIf { it.isNotBlank() },
                health.susfsVariant.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (detail.isNotBlank()) {
                stringResource(R.string.health_live_susfs_builtin, detail)
            } else {
                stringResource(R.string.health_live_susfs_builtin_simple)
            }
        }

        else -> stringResource(R.string.health_live_susfs_inactive)
    }

    HealthComponentId.NOMOUNT -> when {
        health.mountMode == "no_mount" -> when {
            health.nomountReady -> stringResource(
                R.string.health_live_nomount_active,
                health.nomountVersion.ifBlank { "OK" },
                health.nomountRules,
            )

            health.nomountBootloopDisabled ->
                stringResource(R.string.health_live_nomount_bootloop)

            else ->
                stringResource(R.string.health_live_nomount_not_loaded)
        }

        health.nomountReady -> stringResource(
            R.string.health_live_nomount_active,
            health.nomountVersion.ifBlank { "OK" },
            health.nomountRules,
        )

        health.externalMetamoduleName.isNotBlank() -> stringResource(
            R.string.health_live_nomount_external,
            health.externalMetamoduleName,
        )

        else -> stringResource(R.string.health_live_nomount_external_none)
    }

    HealthComponentId.ZYGISK -> when {
        health.zygiskEnabled -> {
            val ver = health.zygiskVersion.ifBlank { "BuiltZygisk" }
            when {
                health.zygiskInjected -> stringResource(R.string.health_live_zygisk_injected, ver)
                health.zygiskRunning -> stringResource(R.string.health_live_zygisk_running, ver)
                else -> stringResource(R.string.health_live_zygisk_not_running)
            }
        }

        health.zygiskExternalName.isNotBlank() -> stringResource(
            R.string.health_live_zygisk_external,
            health.zygiskExternalName,
        )

        else -> stringResource(R.string.health_live_zygisk_disabled)
    }
}

@Composable
fun componentCompactStateText(
    health: ComponentHealthSummary,
    component: HealthComponentId,
): String = when (component) {
    HealthComponentId.LKM -> when {
        health.lkmMode != true && health.isFullFeatured ->
            stringResource(R.string.health_compact_lkm_builtin)

        health.lkmWorking && !health.runtimeLkmHash.isNullOrBlank() ->
            stringResource(R.string.health_compact_lkm_hash, health.runtimeLkmHash)

        health.lkmWorking ->
            stringResource(R.string.health_compact_lkm_active)

        else ->
            stringResource(R.string.health_compact_inactive)
    }

    HealthComponentId.KSUD -> when (health.ksudIntegrity.status) {
        KsudSyncStatus.UP_TO_DATE -> stringResource(
            R.string.health_compact_ksud_active,
            formatKsudCompact(health.ksudIntegrity.installedVersion ?: health.ksudIntegrity.bundledVersion),
        )

        KsudSyncStatus.NEEDS_SYNC -> stringResource(R.string.health_compact_ksud_needs_sync)

        KsudSyncStatus.MISSING -> stringResource(R.string.health_compact_ksud_missing)

        KsudSyncStatus.UNAVAILABLE -> stringResource(R.string.health_live_ksud_unavailable)
    }

    HealthComponentId.SUSFS -> when {
        health.susfsLkmLoaded -> {
            val ver = health.susfsVersion.takeIf { it.isNotBlank() }
            if (ver != null) {
                stringResource(R.string.health_compact_susfs_lkm, ver)
            } else {
                stringResource(R.string.health_compact_lkm_active)
            }
        }

        health.susfsWorking -> {
            val ver = health.susfsVersion.takeIf { it.isNotBlank() }
            if (ver != null) {
                stringResource(R.string.health_compact_susfs_builtin, ver)
            } else {
                stringResource(R.string.health_compact_lkm_builtin)
            }
        }

        else -> stringResource(R.string.health_compact_inactive)
    }

    HealthComponentId.NOMOUNT -> when {
        health.mountMode == "no_mount" -> when {
            health.nomountReady -> stringResource(
                R.string.health_compact_nomount_active,
                health.nomountVersion.ifBlank { "OK" },
                health.nomountRules,
            )

            health.nomountBootloopDisabled ->
                stringResource(R.string.health_live_nomount_bootloop)

            else ->
                stringResource(R.string.health_live_nomount_not_loaded)
        }

        health.nomountReady -> stringResource(
            R.string.health_compact_nomount_active,
            health.nomountVersion.ifBlank { "OK" },
            health.nomountRules,
        )

        health.externalMetamoduleName.isNotBlank() -> stringResource(
            R.string.health_live_nomount_external,
            health.externalMetamoduleName,
        )

        else -> stringResource(R.string.health_live_nomount_external_none)
    }

    HealthComponentId.ZYGISK -> when {
        health.zygiskEnabled -> {
            val ver = health.zygiskVersion.ifBlank { "BuiltZygisk" }
            when {
                health.zygiskInjected -> stringResource(R.string.health_compact_zygisk_injected, ver)
                health.zygiskRunning -> stringResource(R.string.health_compact_zygisk_running, ver)
                else -> stringResource(R.string.health_live_zygisk_not_running)
            }
        }

        health.zygiskExternalName.isNotBlank() -> stringResource(
            R.string.health_live_zygisk_external,
            health.zygiskExternalName,
        )

        else -> stringResource(R.string.health_live_zygisk_disabled)
    }
}

@Composable
fun componentRebootReason(
    health: ComponentHealthSummary,
    component: HealthComponentId,
): ComponentRebootReason = when (component) {
    HealthComponentId.LKM -> health.lkmRebootReason
    HealthComponentId.KSUD -> health.ksudRebootReason
    HealthComponentId.SUSFS -> health.susfsRebootReason
    HealthComponentId.NOMOUNT -> health.nomountRebootReason
    HealthComponentId.ZYGISK -> health.zygiskRebootReason
}

@Composable
fun componentDescriptionText(
    health: ComponentHealthSummary,
    component: HealthComponentId,
): String {
    val live = componentLiveStateText(health, component)
    val reason = componentRebootReason(health, component)
    return if (reason.requiresReboot || reason == ComponentRebootReason.KSUD_NEEDS_SYNC) {
        "$live\n${componentRebootLabel(reason)}"
    } else {
        live
    }
}

@Composable
fun componentHomeDescriptionText(
    health: ComponentHealthSummary,
    component: HealthComponentId,
): String {
    val live = componentCompactStateText(health, component)
    val reason = componentRebootReason(health, component)
    return if (reason.requiresReboot || reason == ComponentRebootReason.KSUD_NEEDS_SYNC) {
        "$live\n${componentRebootLabel(reason)}"
    } else {
        live
    }
}

@Composable
fun ComponentHealthSummary.rebootRequiredNames(): String {
    val lkmLabel = stringResource(R.string.health_short_lkm)
    val ksudLabel = stringResource(R.string.health_short_ksud)
    val susfsLabel = stringResource(R.string.health_short_susfs)
    val nomountLabel = stringResource(R.string.health_short_nomount)
    val zygiskLabel = stringResource(R.string.health_short_zygisk)
    return componentsNeedingReboot.joinToString(", ") { id ->
        when (id) {
            HealthComponentId.LKM -> lkmLabel
            HealthComponentId.KSUD -> ksudLabel
            HealthComponentId.SUSFS -> susfsLabel
            HealthComponentId.NOMOUNT -> nomountLabel
            HealthComponentId.ZYGISK -> zygiskLabel
        }
    }
}

@Composable
fun KsudDaemonDialog(
    health: ComponentHealthSummary,
    onDismiss: () -> Unit,
    onSync: () -> Unit,
    onReboot: () -> Unit,
) {
    val snapshot = health.ksudIntegrity
    val statusText = when (snapshot.status) {
        KsudSyncStatus.UP_TO_DATE -> stringResource(R.string.ksud_status_up_to_date)
        KsudSyncStatus.NEEDS_SYNC -> stringResource(R.string.ksud_status_needs_sync)
        KsudSyncStatus.MISSING -> stringResource(R.string.ksud_status_missing)
        KsudSyncStatus.UNAVAILABLE -> stringResource(R.string.ksud_status_unavailable)
    }
    val bootSessionText = if (health.hasBootSession && health.ksudBootVersion.isNotBlank()) {
        val hashPart = health.ksudBootHash.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
        "${health.ksudBootVersion}$hashPart"
    } else {
        stringResource(R.string.health_boot_session_legacy)
    }
    val rebootText = componentRebootLabel(health.ksudRebootReason)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_ksud_daemon)) },
        text = {
            Text(
                stringResource(
                    R.string.health_ksud_dialog_detail,
                    statusText,
                    snapshot.bundledVersion.ifBlank { "?" },
                    health.ksudBundledHash.ifBlank { "—" },
                    snapshot.installedVersion ?: stringResource(R.string.ksud_not_installed),
                    bootSessionText,
                    rebootText,
                ),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    onSync()
                },
            ) {
                Text(stringResource(R.string.ksud_sync_action))
            }
        },
        dismissButton = {
            if (health.ksudRebootReason.requiresReboot) {
                TextButton(
                    onClick = {
                        onDismiss()
                        onReboot()
                    },
                ) {
                    Text(stringResource(R.string.health_reboot_action))
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        },
    )
}

@Composable
fun KernelLkmDialog(
    health: ComponentHealthSummary,
    onDismiss: () -> Unit,
    onOpenInstall: () -> Unit,
    onReboot: () -> Unit,
) {
    val modeLabel = when {
        health.isLateLoadMode -> stringResource(R.string.health_lkm_mode_late)
        health.lkmMode == true -> stringResource(R.string.health_lkm_mode_lkm)
        else -> stringResource(R.string.health_lkm_mode_gki)
    }
    val rebootLabel = componentRebootLabel(health.lkmRebootReason)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.health_lkm_title)) },
        text = {
            Text(
                stringResource(
                    R.string.health_lkm_dialog_detail,
                    modeLabel,
                    health.runtimeLkmHash ?: "—",
                    health.bundledLkmHash.ifBlank { "—" },
                    health.kernelUapiVersion?.toString() ?: "—",
                    health.managerUapiVersion,
                    rebootLabel,
                ),
            )
        },
        confirmButton = {
            when {
                health.lkmMode == true && !health.isLateLoadMode -> {
                    TextButton(
                        onClick = {
                            onDismiss()
                            onOpenInstall()
                        },
                    ) {
                        Text(stringResource(R.string.health_lkm_update_action))
                    }
                }

                health.lkmRebootReason.requiresReboot -> {
                    TextButton(
                        onClick = {
                            onDismiss()
                            onReboot()
                        },
                    ) {
                        Text(stringResource(R.string.health_reboot_action))
                    }
                }

                else -> {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(android.R.string.ok))
                    }
                }
            }
        },
        dismissButton = {
            if (health.lkmMode == true && !health.isLateLoadMode && health.lkmRebootReason.requiresReboot) {
                TextButton(
                    onClick = {
                        onDismiss()
                        onReboot()
                    },
                ) {
                    Text(stringResource(R.string.health_reboot_action))
                }
            } else if (health.lkmMode == true || health.lkmRebootReason.requiresReboot) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        },
    )
}
