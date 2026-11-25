package org.uranxsu.manager.ui.util

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import kotlinx.serialization.json.Json
import org.uranxsu.manager.R
import org.uranxsu.manager.data.AppSettingsRepository

val LocalCustomTexts = compositionLocalOf<Map<String, String>> { emptyMap() }

enum class CustomTextGroup(@param:StringRes val titleRes: Int) {
    HOME(R.string.custom_text_group_home),
    PAGE_MAIN(R.string.custom_text_group_page_main),
    PAGE_FEATURE(R.string.custom_text_group_page_feature),
    SETTINGS(R.string.custom_text_group_settings),
}

enum class CustomTextKey(
    val key: String,
    @param:StringRes val nameRes: Int,
    @param:StringRes val defaultRes: Int,
    val group: CustomTextGroup,
) {
    // Home screen
    HOME_TITLE("home.title", R.string.custom_home_title_label, R.string.app_name, CustomTextGroup.HOME),
    HOME_STATUS_WORKING("home.status_working", R.string.home_working, R.string.home_working, CustomTextGroup.HOME),
    HOME_STATUS_NOT_INSTALLED("home.status_not_installed", R.string.home_not_installed, R.string.home_not_installed, CustomTextGroup.HOME),
    HOME_STATUS_INSTALL_HINT("home.status_install_hint", R.string.home_click_to_install, R.string.home_click_to_install, CustomTextGroup.HOME),
    HOME_TAG_SAFEMODE("home.tag_safemode", R.string.safe_mode, R.string.safe_mode, CustomTextGroup.HOME),
    HOME_INFO_KERNEL("home.info_kernel", R.string.home_kernel, R.string.home_kernel, CustomTextGroup.HOME),
    HOME_INFO_ANDROID("home.info_android", R.string.home_android_version, R.string.home_android_version, CustomTextGroup.HOME),
    HOME_INFO_KERNEL_FULL("home.info_kernel_full", R.string.home_kernel_version, R.string.home_kernel_version, CustomTextGroup.HOME),
    HOME_INFO_MANAGER_VERSION("home.info_manager_version", R.string.home_manager_version, R.string.home_manager_version, CustomTextGroup.HOME),
    HOME_INFO_SELINUX("home.info_selinux", R.string.home_selinux_status, R.string.home_selinux_status, CustomTextGroup.HOME),
    HOME_INFO_SECCOMP("home.info_seccomp", R.string.home_seccomp_status, R.string.home_seccomp_status, CustomTextGroup.HOME),
    HOME_INFO_DEVICE_MODEL("home.info_device_model", R.string.home_device_model, R.string.home_device_model, CustomTextGroup.HOME),
    HOME_INFO_HOOK_TYPE("home.info_hook_type", R.string.home_hook_type, R.string.home_hook_type, CustomTextGroup.HOME),
    HOME_INFO_ZYGISK("home.info_zygisk", R.string.home_zygisk_implement, R.string.home_zygisk_implement, CustomTextGroup.HOME),
    HOME_INFO_METAMODULE("home.info_metamodule", R.string.home_meta_module_implement, R.string.home_meta_module_implement, CustomTextGroup.HOME),
    HOME_INFO_SUSFS("home.info_susfs", R.string.home_susfs_version, R.string.home_susfs_version, CustomTextGroup.HOME),
    HOME_STATUS_UNSUPPORTED("home.status_unsupported", R.string.home_unsupported, R.string.home_unsupported, CustomTextGroup.HOME),
    HOME_SUPPORT_TITLE("home.support_title", R.string.home_support_title, R.string.home_support_title, CustomTextGroup.HOME),
    HOME_LEARN_TITLE("home.learn_title", R.string.home_learn_kernelsu, R.string.home_learn_kernelsu, CustomTextGroup.HOME),

    // Main navigation & page headers
    PAGE_HOME("page.home", R.string.home, R.string.home, CustomTextGroup.PAGE_MAIN),
    PAGE_SUPERUSER("page.superuser", R.string.superuser, R.string.superuser, CustomTextGroup.PAGE_MAIN),
    PAGE_MODULES("page.modules", R.string.module, R.string.module, CustomTextGroup.PAGE_MAIN),
    PAGE_SETTINGS("page.settings", R.string.settings, R.string.settings, CustomTextGroup.PAGE_MAIN),
    PAGE_LOG("page.log", R.string.sulog, R.string.sulog, CustomTextGroup.PAGE_MAIN),

    // Feature pages
    PAGE_APPEARANCE("page.appearance", R.string.theme_settings, R.string.theme_settings, CustomTextGroup.PAGE_FEATURE),
    PAGE_CUSTOM_TEXTS("page.custom_texts", R.string.custom_texts_title, R.string.custom_texts_title, CustomTextGroup.PAGE_FEATURE),
    PAGE_TEMPLATE("page.template", R.string.settings_profile_template, R.string.settings_profile_template, CustomTextGroup.PAGE_FEATURE),
    PAGE_ABOUT("page.about", R.string.about, R.string.about, CustomTextGroup.PAGE_FEATURE),
    PAGE_UMOUNT("page.umount", R.string.umount_path_manager, R.string.umount_path_manager, CustomTextGroup.PAGE_FEATURE),
    PAGE_TOOLS("page.tools", R.string.tools, R.string.tools, CustomTextGroup.PAGE_FEATURE),
    PAGE_DYNAMIC_MANAGERS("page.dynamic_managers", R.string.dynamic_manager_title, R.string.dynamic_manager_title, CustomTextGroup.PAGE_FEATURE),
    EXTENSION_SUSFS("extension.susfs", R.string.susfs_config_setting_title, R.string.susfs_config_setting_title, CustomTextGroup.PAGE_FEATURE),
    PAGE_MOUNT_ENGINE("page.mount_engine", R.string.mount_engine_title, R.string.mount_engine_title, CustomTextGroup.PAGE_FEATURE),
    ZYGISK_SETTINGS_TITLE("zygisk.settings_title", R.string.builtin_zygisk_title, R.string.builtin_zygisk_title, CustomTextGroup.PAGE_FEATURE),
    MODULE_EMPTY("module.empty", R.string.module_empty, R.string.module_empty, CustomTextGroup.PAGE_FEATURE),
    SUPERUSER_EMPTY("superuser.empty", R.string.no_apps_found, R.string.no_apps_found, CustomTextGroup.PAGE_FEATURE),

    // Settings items
    SETTING_SUCOMPAT("settings.sucompat", R.string.settings_sucompat, R.string.settings_sucompat, CustomTextGroup.SETTINGS),
    SETTING_KERNEL_UMOUNT("settings.kernel_umount", R.string.settings_kernel_umount, R.string.settings_kernel_umount, CustomTextGroup.SETTINGS),
    SETTING_SELINUX_HIDE("settings.selinux_hide", R.string.settings_selinux_hide, R.string.settings_selinux_hide, CustomTextGroup.SETTINGS),
    SETTING_SULOG("settings.sulog", R.string.settings_sulog, R.string.settings_sulog, CustomTextGroup.SETTINGS),
    SETTING_ADB_ROOT("settings.adb_root", R.string.settings_adb_root, R.string.settings_adb_root, CustomTextGroup.SETTINGS),
    SETTING_SHELL_SU("settings.shell_su", R.string.settings_shell_su_adb_only, R.string.settings_shell_su_adb_only, CustomTextGroup.SETTINGS),
    SETTING_SOFT_REBOOT("settings.soft_reboot", R.string.settings_soft_reboot, R.string.settings_soft_reboot, CustomTextGroup.SETTINGS),
    SETTING_UMOUNT_MODULES_DEFAULT("settings.umount_modules_default", R.string.settings_umount_modules_default, R.string.settings_umount_modules_default, CustomTextGroup.SETTINGS),
    SETTING_SEND_LOG("settings.send_log", R.string.send_log, R.string.send_log, CustomTextGroup.SETTINGS),
    COMMON_REBOOT("common.reboot", R.string.reboot, R.string.reboot, CustomTextGroup.SETTINGS),
    COMMON_REBOOT_RECOVERY("common.reboot_recovery", R.string.reboot_recovery, R.string.reboot_recovery, CustomTextGroup.SETTINGS),
    COMMON_REBOOT_BOOTLOADER("common.reboot_bootloader", R.string.reboot_bootloader, R.string.reboot_bootloader, CustomTextGroup.SETTINGS),
    ;

    companion object {
        fun fromKey(key: String): CustomTextKey? = entries.find { it.key == key }
    }
}

@Stable
class CustomTextConfig(
    private val settings: AppSettingsRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }

    var customTexts by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    init {
        load()
    }

    fun load() {
        val raw = settings.getString(KEY_CUSTOM_TEXTS, null)
        val parsed = if (raw.isNullOrBlank()) {
            emptyMap()
        } else {
            runCatching { json.decodeFromString<Map<String, String>>(raw) }.getOrDefault(emptyMap())
        }
        customTexts = parsed.filterValues { it.isNotBlank() }
    }

    fun set(key: String, value: String?) {
        val trimmed = value?.trim().orEmpty()
        val updated = if (trimmed.isEmpty()) {
            customTexts - key
        } else {
            customTexts + (key to trimmed)
        }
        saveMap(updated)
    }

    fun setAll(values: Map<String, String>, merge: Boolean = false) {
        val cleaned = values.mapValues { it.value.trim() }.filterValues { it.isNotEmpty() }
        val updated = if (merge) customTexts + cleaned else cleaned
        saveMap(updated)
    }

    fun reset(key: String) {
        saveMap(customTexts - key)
    }

    fun resetAll() {
        saveMap(emptyMap())
    }

    fun get(key: String): String? = customTexts[key]?.takeIf { it.isNotBlank() }

    private fun saveMap(map: Map<String, String>) {
        customTexts = map
        if (map.isEmpty()) {
            settings.remove(KEY_CUSTOM_TEXTS)
        } else {
            settings.putString(KEY_CUSTOM_TEXTS, json.encodeToString(map))
        }
    }

    private companion object {
        const val KEY_CUSTOM_TEXTS = "custom_texts_json"
    }
}

@Composable
fun customText(key: CustomTextKey): String {
    val custom = LocalCustomTexts.current[key.key]
    return if (!custom.isNullOrBlank()) custom else stringResource(key.defaultRes)
}

@Composable
fun customTextSummary(key: CustomTextKey, custom: String?): String {
    val customValue = custom?.takeIf { it.isNotBlank() }
    if (customValue != null) return customValue
    return stringResource(R.string.custom_text_default_value, stringResource(key.defaultRes))
}
