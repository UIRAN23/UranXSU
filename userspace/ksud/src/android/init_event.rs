use std::{path::Path, time::Instant};

use anyhow::{Context, Result};
use log::{error, info, warn};

use crate::{
    android::{
        dynamic_manager, feature, ksucalls,
        module::{self, ScriptWait, handle_updated_modules, metamodule, prune_modules},
        restorecon,
        utils::{self, is_safe_mode},
    },
    assets, defs,
};

pub fn on_post_fs_data() -> Result<()> {
    if let Err(e) = ksucalls::ensure_uapi_version_matched() {
        error!("{e:#}, skip on_post_fs_data");
        return Ok(());
    }

    ksucalls::report_post_fs_data();

    utils::umask(0);

    // Clear all temporary module configs early
    if let Err(e) = crate::android::module::module_config::clear_all_temp_configs() {
        warn!("clear temp configs failed: {e}");
    }

    let _ = catch_bootlog("logcat", &["logcat", "-b", "all"]);
    let _ = catch_bootlog("dmesg", &["dmesg", "-w", "-r"]);

    if utils::has_magisk() {
        warn!("Magisk detected, skip post-fs-data!");
        return Ok(());
    }

    let safe_mode = crate::android::utils::is_safe_mode();
    let wait = ScriptWait::Until(Instant::now() + defs::BOOT_STAGE_TIMEOUT);

    if safe_mode {
        // we should still ensure module directory exists in safe mode
        // because we may need to operate the module dir in safe mode
        warn!("safe mode, skip common post-fs-data.d scripts");
    } else {
        // Then exec common post-fs-data scripts
        if let Err(e) = crate::android::module::exec_common_scripts("post-fs-data.d", wait) {
            warn!("exec common post-fs-data scripts failed: {e}");
        }
        if let Err(e) = dynamic_manager::booted_load() {
            warn!("set dynamic manager failed: {e}");
        }
    }

    let module_dir = defs::MODULE_DIR;

    assets::ensure_binaries(true).with_context(|| "Failed to extract bin assets")?;

    crate::android::hot_install::clean_trash();

    // if we are in safe mode, we should disable all modules
    if safe_mode {
        warn!("safe mode, skip post-fs-data scripts and disable all modules!");
        if let Err(e) = module::disable_all_modules() {
            warn!("disable all modules failed: {e}");
        }
        return Ok(());
    }

    if let Err(e) = handle_updated_modules() {
        warn!("handle updated modules failed: {e}");
    }

    if let Err(e) = prune_modules() {
        warn!("prune modules failed: {e}");
    }

    // Refresh /metadata/watchdog/ksu/{modules.rc,preinit/} so the next boot's kernel hook sees the
    // current module set. Acts as a safety net when state was changed outside
    // of ksud's normal mutation commands.
    if let Err(e) = crate::android::module::regenerate_preinit() {
        warn!("regenerate preinit failed: {e}");
    }

    if let Err(e) = restorecon::restorecon() {
        warn!("restorecon failed: {e}");
    }

    // load sepolicy.rule
    if crate::android::module::load_sepolicy_rule().is_err() {
        warn!("load sepolicy.rule failed");
    }

    if let Err(e) = crate::android::builtin_zygisk::apply_sepolicy() {
        warn!("apply built-in zygisk sepolicy failed: {e}");
    }

    if let Err(e) = crate::android::profile::apply_sepolies() {
        warn!("apply root profile sepolicy failed: {e}");
    }

    feature::init_forced_features();

    // load feature config
    if is_safe_mode() {
        warn!("safe mode, skip load feature config");
    } else if let Err(e) = crate::android::feature::init_features() {
        warn!("init features failed: {e}");
    }

    // Load built-in SUSFS LKM early if configured and no built-in SUSFS is active.
    crate::android::susfs::lkm::on_post_fs_data();

    // Load susfs config entries that must capture metadata before mounts/overlays.
    crate::android::susfs::init_event::on_post_fs_data();

    record_boot_session("post-fs-data");

    // Stage built-in mount engine and load NoMount driver before module scripts run.
    crate::android::mount::on_post_fs_data_early();

    // Clean up or bootstrap built-in Zygisk before module post-fs-data scripts run.
    if let Err(e) = crate::android::builtin_zygisk::cleanup_disabled() {
        warn!("built-in zygisk cleanup failed: {e}");
    }
    if let Err(e) = crate::android::builtin_zygisk::bootstrap() {
        warn!("built-in zygisk bootstrap failed: {e}");
    }

    // execute metamodule post-fs-data script first (priority)
    if let Err(e) = metamodule::exec_stage_script("post-fs-data", wait) {
        warn!("exec metamodule post-fs-data script failed: {e}");
    }

    // exec modules post-fs-data scripts
    if let Err(e) = module::exec_stage_script("post-fs-data", wait) {
        warn!("exec post-fs-data scripts failed: {e}");
    }

    // load system.prop
    if let Err(e) = module::load_system_prop() {
        warn!("load system.prop failed: {e}");
    }

    // execute built-in mount engine or external metamodule mount script
    if let Err(e) = crate::android::mount::exec_mount(module_dir) {
        warn!("execute mount engine failed: {e}");
    }

    // Load umount config and apply to kernel
    if let Err(e) = crate::android::umount_config::load_umount_config() {
        warn!("load umount config failed: {e}");
    }

    run_stage("post-mount", wait);

    std::env::set_current_dir("/").with_context(|| "failed to chdir to /")?;

    Ok(())
}

pub fn run_stage(stage: &str, wait: ScriptWait) {
    utils::umask(0);

    if utils::has_magisk() {
        warn!("Magisk detected, skip {stage}");
        return;
    }

    if is_safe_mode() {
        warn!("safe mode, skip {stage} scripts");
        return;
    }

    if let Err(e) = module::exec_common_scripts(&format!("{stage}.d"), wait) {
        warn!("Failed to exec common {stage} scripts: {e}");
    }

    // execute metamodule stage script first (priority)
    if let Err(e) = metamodule::exec_stage_script(stage, wait) {
        warn!("Failed to exec metamodule {stage} script: {e}");
    }

    // execute regular modules stage scripts
    if let Err(e) = module::exec_stage_script(stage, wait) {
        warn!("Failed to exec {stage} scripts: {e}");
    }
}

pub fn on_services() {
    if let Err(e) = ksucalls::ensure_uapi_version_matched() {
        error!("{e:#}, skip on_services");
        return;
    }

    match ksucalls::report_services() {
        Ok(true) => {}
        Ok(false) => {
            info!("services already started, skipping");
            return;
        }
        Err(e) => {
            error!("Failed to report services: {e:#}");
            return;
        }
    }

    info!("on_services triggered!");
    if !is_safe_mode() {
        crate::android::mount::on_services();
        if let Err(e) = crate::android::builtin_zygisk::ensure_monitor() {
            warn!("built-in zygisk ensure_monitor failed: {e}");
        }
    }
    run_stage("service", ScriptWait::NoWait);
}

pub fn on_boot_completed() {
    if let Err(e) = ksucalls::ensure_uapi_version_matched() {
        error!("{e:#}, skip on_boot_completed");
        return;
    }

    ksucalls::report_boot_complete();
    info!("on_boot_completed triggered!");

    crate::android::preinit::on_boot_completed();

    run_stage("boot-completed", ScriptWait::NoWait);
    // Load susfs boot-completed and finalize mount boot state
    if !is_safe_mode() {
        crate::android::mount::on_boot_completed();
        if let Err(e) = crate::android::builtin_zygisk::ensure_armed() {
            warn!("built-in zygisk ensure_armed failed: {e}");
        }
        crate::android::susfs::lkm::on_boot_completed();
        crate::android::susfs::init_event::on_boot_completed();
    }
}

#[derive(serde::Serialize, serde::Deserialize, Default)]
#[serde(default)]
pub struct BootSessionInfo {
    pub boot_id: String,
    pub stage: String,
    pub ksud_version_code: String,
    pub ksud_version_name: String,
    pub ksud_hash: String,
    pub userspace_hash: String,
    pub susfs_hash: String,
    pub nomount_hash: String,
    pub zygisk_hash: String,
    pub mount_mode: String,
    pub zygisk_builtin_enabled: bool,
}

#[derive(serde::Serialize, serde::Deserialize, Default)]
#[serde(default)]
pub struct ComponentMarker {
    pub boot_id: String,
    pub hash: String,
    pub version: String,
}

fn read_boot_id() -> String {
    std::fs::read_to_string("/proc/sys/kernel/random/boot_id")
        .map(|s| s.trim().to_string())
        .unwrap_or_default()
}

fn write_json_file<T: serde::Serialize>(path: &str, value: &T) {
    let target = Path::new(path);
    if let Some(parent) = target.parent()
        && let Err(e) = utils::ensure_dir_exists(parent)
    {
        warn!("Failed to create dir for {path}: {e}");
        return;
    }
    let Ok(payload) = serde_json::to_string_pretty(value) else {
        return;
    };
    let tmp = target.with_extension("tmp");
    if let Err(e) = std::fs::write(&tmp, payload) {
        warn!("Failed to write {path}: {e}");
        return;
    }
    {
        use std::os::unix::fs::PermissionsExt;
        let _ = std::fs::set_permissions(&tmp, std::fs::Permissions::from_mode(0o644));
    }
    if let Err(e) = std::fs::rename(&tmp, target) {
        warn!("Failed to rename temp file to {path}: {e}");
        let _ = std::fs::remove_file(&tmp);
    }
}

pub fn write_component_marker(path: &str, hash: &str, version: &str) {
    let marker = ComponentMarker {
        boot_id: read_boot_id(),
        hash: hash.to_string(),
        version: version.to_string(),
    };
    write_json_file(path, &marker);
}

pub fn record_boot_session(stage: &str) {
    let mount_cfg = crate::android::mount::config::MountConfig::load_or_default();
    let info = BootSessionInfo {
        boot_id: read_boot_id(),
        stage: stage.to_string(),
        ksud_version_code: defs::VERSION_CODE.to_string(),
        ksud_version_name: defs::VERSION_NAME.to_string(),
        ksud_hash: defs::KSUD_HASH.to_string(),
        userspace_hash: defs::USERSPACE_HASH.to_string(),
        susfs_hash: defs::SUSFS_HASH.to_string(),
        nomount_hash: defs::NOMOUNT_HASH.to_string(),
        zygisk_hash: defs::ZYGISK_HASH.to_string(),
        mount_mode: mount_cfg.mode.as_str().to_string(),
        zygisk_builtin_enabled: crate::android::builtin_zygisk::is_enabled(),
    };
    write_json_file(defs::BOOT_SESSION_PATH, &info);
}

pub fn print_boot_session_json() -> Result<()> {
    fn read_json<T: serde::de::DeserializeOwned>(path: &str) -> Option<T> {
        let content = std::fs::read_to_string(path).ok()?;
        serde_json::from_str::<T>(&content).ok()
    }

    let boot_id = read_boot_id();
    let boot_session: Option<BootSessionInfo> = read_json(defs::BOOT_SESSION_PATH);
    let susfs_marker: Option<ComponentMarker> = read_json(defs::SUSFS_LOADED_HASH_PATH);
    let nomount_marker: Option<ComponentMarker> = read_json(defs::NOMOUNT_LOADED_HASH_PATH);
    let zygisk_marker: Option<ComponentMarker> = read_json(defs::ZYGISK_RUNNING_HASH_PATH);
    let report = serde_json::json!({
        "boot_id": boot_id,
        "bundled": {
            "version_code": defs::VERSION_CODE,
            "version_name": defs::VERSION_NAME,
            "ksud_hash": defs::KSUD_HASH,
            "userspace_hash": defs::USERSPACE_HASH,
            "susfs_hash": defs::SUSFS_HASH,
            "nomount_hash": defs::NOMOUNT_HASH,
            "zygisk_hash": defs::ZYGISK_HASH,
        },
        "boot_session": boot_session,
        "susfs_loaded_marker": susfs_marker,
        "nomount_loaded_marker": nomount_marker,
        "zygisk_running_marker": zygisk_marker,
    });
    println!("{}", serde_json::to_string(&report)?);
    Ok(())
}

fn catch_bootlog(logname: &str, command: &[&str]) -> Result<()> {
    use std::{os::unix::process::CommandExt, process::Stdio};

    let logdir = Path::new(defs::LOG_DIR);
    utils::ensure_dir_exists(logdir)?;
    let bootlog = logdir.join(format!("{logname}.log"));
    let oldbootlog = logdir.join(format!("{logname}.old.log"));

    if bootlog.exists() {
        std::fs::rename(&bootlog, oldbootlog)?;
    }

    let bootlog = std::fs::File::create(bootlog)?;

    let mut args = vec!["-s", "9", defs::BOOTLOG_TIMEOUT];
    args.extend_from_slice(command);
    // timeout -s 9 30s logcat > boot.log
    let result = unsafe {
        std::process::Command::new("timeout")
            .process_group(0)
            .pre_exec(|| {
                utils::switch_cgroups();
                Ok(())
            })
            .args(args)
            .stdout(Stdio::from(bootlog))
            .spawn()
    };

    if let Err(e) = result {
        warn!("Failed to start logcat: {e:#}");
    }

    Ok(())
}
