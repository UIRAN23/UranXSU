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
    NAVIGATION(R.string.custom_text_group_navigation),
    MODULES(R.string.custom_text_group_modules),
    SUPERUSER(R.string.custom_text_group_superuser),
    SETTINGS(R.string.custom_text_group_settings),
    TOOLS(R.string.custom_text_group_tools),
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
    HOME_STATUS_UNSUPPORTED("home.status_unsupported", R.string.home_unsupported, R.string.home_unsupported, CustomTextGroup.HOME),
    HOME_TAG_SAFEMODE("home.tag_safemode", R.string.safe_mode, R.string.safe_mode, CustomTextGroup.HOME),
    HOME_STATUS_KSUD_SYNC("home.status_ksud_sync", R.string.home_ksud_mismatch_warning, R.string.home_ksud_mismatch_warning, CustomTextGroup.HOME),
    HOME_UPDATE_BUTTON("home.update_button", R.string.module_update, R.string.module_update, CustomTextGroup.HOME),
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
    HOME_INFO_KSUD("home.info_ksud", R.string.home_ksud_daemon, R.string.home_ksud_daemon, CustomTextGroup.HOME),
    HOME_INFO_MULTI_MANAGER("home.info_multi_manager", R.string.multi_manager_list, R.string.multi_manager_list, CustomTextGroup.HOME),
    HOME_SUPPORT_TITLE("home.support_title", R.string.home_support_title, R.string.home_support_title, CustomTextGroup.HOME),
    HOME_LEARN_TITLE("home.learn_title", R.string.home_learn_kernelsu, R.string.home_learn_kernelsu, CustomTextGroup.HOME),

    // Navigation & reboot
    PAGE_HOME("page.home", R.string.home, R.string.home, CustomTextGroup.NAVIGATION),
    PAGE_SUPERUSER("page.superuser", R.string.superuser, R.string.superuser, CustomTextGroup.NAVIGATION),
    PAGE_MODULES("page.modules", R.string.module, R.string.module, CustomTextGroup.NAVIGATION),
    PAGE_SETTINGS("page.settings", R.string.settings, R.string.settings, CustomTextGroup.NAVIGATION),
    PAGE_LOG("page.log", R.string.sulog, R.string.sulog, CustomTextGroup.NAVIGATION),
    COMMON_REBOOT("common.reboot", R.string.reboot, R.string.reboot, CustomTextGroup.NAVIGATION),
    COMMON_REBOOT_SOFT("common.reboot_soft", R.string.reboot_soft, R.string.reboot_soft, CustomTextGroup.NAVIGATION),
    COMMON_REBOOT_USERSPACE("common.reboot_userspace", R.string.reboot_userspace, R.string.reboot_userspace, CustomTextGroup.NAVIGATION),
    COMMON_REBOOT_RECOVERY("common.reboot_recovery", R.string.reboot_recovery, R.string.reboot_recovery, CustomTextGroup.NAVIGATION),
    COMMON_REBOOT_BOOTLOADER("common.reboot_bootloader", R.string.reboot_bootloader, R.string.reboot_bootloader, CustomTextGroup.NAVIGATION),
    COMMON_REBOOT_DOWNLOAD("common.reboot_download", R.string.reboot_download, R.string.reboot_download, CustomTextGroup.NAVIGATION),
    COMMON_REBOOT_EDL("common.reboot_edl", R.string.reboot_edl, R.string.reboot_edl, CustomTextGroup.NAVIGATION),

    // Modules
    MODULE_EMPTY("module.empty", R.string.module_empty, R.string.module_empty, CustomTextGroup.MODULES),
    MODULE_SEARCH_PLACEHOLDER("module.search_placeholder", R.string.search_modules, R.string.search_modules, CustomTextGroup.MODULES),
    MODULE_REPO_TITLE("module.repo_title", R.string.module_repo, R.string.module_repo, CustomTextGroup.MODULES),
    MODULE_SORT_ACTION("module.sort_action", R.string.module_sort_action_first, R.string.module_sort_action_first, CustomTextGroup.MODULES),
    MODULE_SORT_ENABLED("module.sort_enabled", R.string.module_sort_enabled_first, R.string.module_sort_enabled_first, CustomTextGroup.MODULES),
    MODULE_VERSION_LABEL("module.version_label", R.string.module_version, R.string.module_version, CustomTextGroup.MODULES),
    MODULE_AUTHOR_LABEL("module.author_label", R.string.module_author, R.string.module_author, CustomTextGroup.MODULES),
    MODULE_UPDATE_JSON("module.update_json", R.string.module_update_json, R.string.module_update_json, CustomTextGroup.MODULES),
    MODULE_SHOW_MORE_INFO("module.show_more_info", R.string.show_more_module_info, R.string.show_more_module_info, CustomTextGroup.MODULES),
    MODULE_BANNER_TITLE("module.banner_title", R.string.module_banner_title, R.string.module_banner_title, CustomTextGroup.MODULES),

    // SuperUser
    SUPERUSER_EMPTY("superuser.empty", R.string.no_apps_found, R.string.no_apps_found, CustomTextGroup.SUPERUSER),
    SUPERUSER_SEARCH_PLACEHOLDER("superuser.search_placeholder", R.string.search_apps, R.string.search_apps, CustomTextGroup.SUPERUSER),
    SUPERUSER_REVERSE_ORDER("superuser.reverse_order", R.string.reverse_order, R.string.reverse_order, CustomTextGroup.SUPERUSER),
    SUPERUSER_SHOW_SYSTEM("superuser.show_system", R.string.show_system_apps, R.string.show_system_apps, CustomTextGroup.SUPERUSER),
    SUPERUSER_BACKUP_ALLOWLIST("superuser.backup_allowlist", R.string.backup_allowlist, R.string.backup_allowlist, CustomTextGroup.SUPERUSER),
    SUPERUSER_RESTORE_ALLOWLIST("superuser.restore_allowlist", R.string.restore_allowlist, R.string.restore_allowlist, CustomTextGroup.SUPERUSER),
    SUPERUSER_RECENTLY_INSTALLED("superuser.recently_installed", R.string.recently_installed, R.string.recently_installed, CustomTextGroup.SUPERUSER),
    SUPERUSER_LAUNCH_APP("superuser.launch_app", R.string.launch_app, R.string.launch_app, CustomTextGroup.SUPERUSER),
    SUPERUSER_FORCE_STOP("superuser.force_stop", R.string.force_stop_app, R.string.force_stop_app, CustomTextGroup.SUPERUSER),
    SUPERUSER_UMOUNT_MODULES("superuser.umount_modules", R.string.profile_umount_modules, R.string.profile_umount_modules, CustomTextGroup.SUPERUSER),

    // Settings
    PAGE_APPEARANCE("page.appearance", R.string.theme_settings, R.string.theme_settings, CustomTextGroup.SETTINGS),
    PAGE_CUSTOM_TEXTS("page.custom_texts", R.string.custom_texts_title, R.string.custom_texts_title, CustomTextGroup.SETTINGS),
    PAGE_TEMPLATE("page.template", R.string.settings_profile_template, R.string.settings_profile_template, CustomTextGroup.SETTINGS),
    PAGE_ABOUT("page.about", R.string.about, R.string.about, CustomTextGroup.SETTINGS),
    SETTING_SUCOMPAT("settings.sucompat", R.string.settings_sucompat, R.string.settings_sucompat, CustomTextGroup.SETTINGS),
    SETTING_KERNEL_UMOUNT("settings.kernel_umount", R.string.settings_kernel_umount, R.string.settings_kernel_umount, CustomTextGroup.SETTINGS),
    SETTING_SELINUX_HIDE("settings.selinux_hide", R.string.settings_selinux_hide, R.string.settings_selinux_hide, CustomTextGroup.SETTINGS),
    SETTING_SULOG("settings.sulog", R.string.settings_sulog, R.string.settings_sulog, CustomTextGroup.SETTINGS),
    SETTING_ADB_ROOT("settings.adb_root", R.string.settings_adb_root, R.string.settings_adb_root, CustomTextGroup.SETTINGS),
    SETTING_SHELL_SU("settings.shell_su", R.string.settings_shell_su_adb_only, R.string.settings_shell_su_adb_only, CustomTextGroup.SETTINGS),
    SETTING_SOFT_REBOOT("settings.soft_reboot", R.string.settings_soft_reboot, R.string.settings_soft_reboot, CustomTextGroup.SETTINGS),
    SETTING_UMOUNT_MODULES_DEFAULT("settings.umount_modules_default", R.string.settings_umount_modules_default, R.string.settings_umount_modules_default, CustomTextGroup.SETTINGS),
    SETTING_AUTO_UPDATE_KSUD("settings.auto_update_ksud", R.string.settings_auto_update_ksud, R.string.settings_auto_update_ksud, CustomTextGroup.SETTINGS),
    SETTING_CHECK_BETA_UPDATE("settings.check_beta_update", R.string.settings_check_beta_update, R.string.settings_check_beta_update, CustomTextGroup.SETTINGS),
    SETTING_CHECK_MODULE_UPDATE("settings.check_module_update", R.string.settings_check_module_update, R.string.settings_check_module_update, CustomTextGroup.SETTINGS),
    SETTING_THEME_MODE("settings.theme_mode", R.string.theme_mode, R.string.theme_mode, CustomTextGroup.SETTINGS),
    SETTING_DYNAMIC_COLOR("settings.dynamic_color", R.string.dynamic_color_title, R.string.dynamic_color_title, CustomTextGroup.SETTINGS),
    SETTING_WALLPAPER("settings.wallpaper", R.string.wallpaper_section, R.string.wallpaper_section, CustomTextGroup.SETTINGS),
    SETTING_APP_ICON("settings.app_icon", R.string.app_icon_section, R.string.app_icon_section, CustomTextGroup.SETTINGS),
    SETTING_THEME_PACKAGES("settings.theme_packages", R.string.theme_package_section, R.string.theme_package_section, CustomTextGroup.SETTINGS),
    SETTING_SIMPLE_MODE("settings.simple_mode", R.string.simple_mode, R.string.simple_mode, CustomTextGroup.SETTINGS),

    // Tools
    PAGE_TOOLS("page.tools", R.string.tools, R.string.tools, CustomTextGroup.TOOLS),
    SETTING_SEND_LOG("settings.send_log", R.string.send_log, R.string.send_log, CustomTextGroup.TOOLS),
    EXTENSION_SUSFS("extension.susfs", R.string.susfs_config_setting_title, R.string.susfs_config_setting_title, CustomTextGroup.TOOLS),
    PAGE_MOUNT_ENGINE("page.mount_engine", R.string.mount_engine_title, R.string.mount_engine_title, CustomTextGroup.TOOLS),
    TOOL_NOMOUNT("tool.nomount", R.string.nomount_manager_title, R.string.nomount_manager_title, CustomTextGroup.TOOLS),
    ZYGISK_SETTINGS_TITLE("zygisk.settings_title", R.string.builtin_zygisk_title, R.string.builtin_zygisk_title, CustomTextGroup.TOOLS),
    PAGE_DYNAMIC_MANAGERS("page.dynamic_managers", R.string.dynamic_manager_title, R.string.dynamic_manager_title, CustomTextGroup.TOOLS),
    PAGE_UMOUNT("page.umount", R.string.umount_path_manager, R.string.umount_path_manager, CustomTextGroup.TOOLS),
    TOOL_NOSHOT("tool.noshot", R.string.noshot_title, R.string.noshot_title, CustomTextGroup.TOOLS),
    TOOL_SYSTEM_HEALTH("tool.system_health", R.string.system_health_title, R.string.system_health_title, CustomTextGroup.TOOLS),
    TOOL_CONFIG_BACKUP("tool.config_backup", R.string.backup_section_title, R.string.backup_section_title, CustomTextGroup.TOOLS),
    TOOL_UNINSTALL("tool.uninstall", R.string.settings_uninstall, R.string.settings_uninstall, CustomTextGroup.TOOLS),
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

    fun resetGroup(group: CustomTextGroup) {
        val keysToRemove = CustomTextKey.entries
            .filter { it.group == group }
            .map { it.key }
            .toSet()
        saveMap(customTexts - keysToRemove)
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
