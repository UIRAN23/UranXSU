use std::{
    collections::HashMap,
    fs::{self, Permissions, create_dir_all, set_permissions},
    os::unix::fs::PermissionsExt,
    path::{Path, PathBuf},
    process::Command,
};

use anyhow::{Context, Result, bail};
use log::{info, warn};
use serde::{Deserialize, Serialize};

use crate::{
    android::{
        debug,
        module::{self, metamodule},
        mount::{
            self,
            config::{MountConfig, MountMode, NoMountConfig},
        },
        utils,
    },
    assets, defs,
};

const NOMOUNT_MODULE_PROP: &str = "\
id=nomount
metamodule=true
name=NoMount
version=v2.3.1
versionCode=231
author=maxsteeel
description=A metamodule that replaces OverlayFS/MagicMount with VFS path redirection.
";

const NOMOUNT_METAINSTALL_SH: &str = r#"ui_print "- Using NoMount metainstall"
install_module
ui_print "- Installation complete"

metamodule_hot_install() {
	if [ ! "$KSU" = true ]; then
		return
	fi
	if [ -z "$MODID" ]; then
		return
	fi
	MODDIR_INTERNAL="/data/adb/modules/$MODID"
	MODPATH_INTERNAL="/data/adb/modules_update/$MODID"
	if [ ! -d "$MODDIR_INTERNAL" ] || [ ! -d "$MODPATH_INTERNAL" ]; then
		return
	fi
	busybox rm -rf "$MODDIR_INTERNAL"
	busybox mv "$MODPATH_INTERNAL" "$MODDIR_INTERNAL"
	if [ -n "$MODULE_HOT_RUN_SCRIPT" ]; then
		[ -f "$MODDIR_INTERNAL/$MODULE_HOT_RUN_SCRIPT" ] && sh "$MODDIR_INTERNAL/$MODULE_HOT_RUN_SCRIPT"
	fi
	mkdir -p "$MODPATH_INTERNAL"
	cat "$MODDIR_INTERNAL/module.prop" > "$MODPATH_INTERNAL/module.prop"
	( sleep 3 ; rm -rf "$MODDIR_INTERNAL/update" "$MODPATH_INTERNAL" ) &
	ui_print "- Module hot install requested!"
	ui_print "- Refresh module page after installation!"
	ui_print "- No need to reboot!"
}

if [ "$KSU_HAS_HOT_INSTALL" != true ] && [ "$MODULE_HOT_INSTALL_REQUEST" = true ]; then
	metamodule_hot_install
fi
"#;

const NOMOUNT_METAMOUNT_SH: &str = "\
#!/system/bin/sh
exec /data/adb/ksud mount nomount mount-all
";

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct NoMountExclusionEntry {
    pub uid: String,
    #[serde(default)]
    pub pkg: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct NoMountModuleInfo {
    pub id: String,
    pub name: String,
    pub enabled: bool,
    pub skipped: bool,
    pub injected_files: usize,
    #[serde(default)]
    pub partitions: Vec<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct NoMountStatus {
    pub driver_ready: bool,
    pub driver_version: String,
    pub active_kmi: String,
    pub detected_kmi: String,
    pub available_kmis: Vec<String>,
    pub rule_count: usize,
    pub uid_count: usize,
    pub block_isolated: bool,
    pub bootloop_disabled: bool,
    pub config: NoMountConfig,
    pub exclusions: Vec<NoMountExclusionEntry>,
    pub modules: Vec<NoMountModuleInfo>,
}

fn nm_bin_path() -> PathBuf {
    Path::new(defs::NOMOUNT_RUNTIME_DIR).join("bin/nm")
}

fn boot_semaphore_path() -> PathBuf {
    Path::new(defs::NOMOUNT_DATA_DIR).join(".booting")
}

fn disable_flag_path() -> PathBuf {
    Path::new(defs::NOMOUNT_DATA_DIR).join("disable")
}

fn dump_rules_flag_path() -> PathBuf {
    Path::new(defs::NOMOUNT_DATA_DIR).join("dump_rules")
}

fn exclusion_list_path() -> PathBuf {
    Path::new(defs::NOMOUNT_DATA_DIR).join(".exclusion_list.json")
}

fn block_isolated_flag_path() -> PathBuf {
    Path::new(defs::NOMOUNT_DATA_DIR).join(".block_isolated_uids")
}

fn active_kmi_marker_path() -> PathBuf {
    Path::new(defs::NOMOUNT_RUNTIME_DIR).join(".active_kmi")
}

pub fn is_runtime_symlink_target(path: &Path) -> bool {
    let runtime = defs::NOMOUNT_RUNTIME_DIR.trim_end_matches('/');
    path.to_string_lossy().trim_end_matches('/') == runtime
}

pub fn ensure_runtime_staged() -> Result<()> {
    let runtime_dir = Path::new(defs::NOMOUNT_RUNTIME_DIR);
    let bin_dir = runtime_dir.join("bin");
    create_dir_all(&bin_dir).context("create NoMount runtime dir")?;
    create_dir_all(defs::NOMOUNT_DATA_DIR).context("create NoMount data dir")?;
    let _ = set_permissions(runtime_dir, Permissions::from_mode(0o755));
    let _ = set_permissions(&bin_dir, Permissions::from_mode(0o755));

    let prop_path = runtime_dir.join("module.prop");
    fs::write(&prop_path, NOMOUNT_MODULE_PROP).context("write NoMount module.prop")?;
    let _ = set_permissions(&prop_path, Permissions::from_mode(0o644));

    let metainstall_path = runtime_dir.join(defs::METAMODULE_METAINSTALL_SCRIPT);
    fs::write(&metainstall_path, NOMOUNT_METAINSTALL_SH).context("write NoMount metainstall.sh")?;
    let _ = set_permissions(&metainstall_path, Permissions::from_mode(0o755));

    let metamount_path = runtime_dir.join(defs::METAMODULE_MOUNT_SCRIPT);
    fs::write(&metamount_path, NOMOUNT_METAMOUNT_SH).context("write NoMount metamount.sh")?;
    let _ = set_permissions(&metamount_path, Permissions::from_mode(0o755));

    if let Ok(nm_bytes) = assets::get_asset("nm") {
        let nm_path = nm_bin_path();
        let needs_write =
            fs::metadata(&nm_path).map_or(true, |m| m.len() as usize != nm_bytes.len());
        if needs_write {
            let _ = fs::remove_file(&nm_path);
            fs::write(&nm_path, &nm_bytes).context("extract embedded nm binary")?;
        }
        set_permissions(&nm_path, Permissions::from_mode(0o755))?;

        let bin_link = Path::new(defs::BINARY_DIR).join("nm");
        let _ = create_dir_all(defs::BINARY_DIR);
        let _ = fs::remove_file(&bin_link);
        let _ = std::os::unix::fs::symlink(&nm_path, &bin_link);
    }

    Ok(())
}

pub fn reconcile_metamodule_symlink(mode: MountMode) -> Result<()> {
    let symlink_path = Path::new(defs::METAMODULE_DIR.trim_end_matches('/'));
    let runtime_dir = Path::new(defs::NOMOUNT_RUNTIME_DIR.trim_end_matches('/'));
    let current_target = fs::read_link(symlink_path).ok();
    let points_to_uranxsu_nomount = current_target
        .as_deref()
        .is_some_and(is_runtime_symlink_target);

    match mode {
        MountMode::NoMount => {
            ensure_runtime_staged()?;
            // Point /data/adb/metamodule -> /data/adb/ksu/nomount when no symlink exists,
            // when it is broken, or when switching explicitly to built-in NoMount.
            if !points_to_uranxsu_nomount {
                if symlink_path.is_symlink() {
                    let _ = fs::remove_file(symlink_path);
                } else if symlink_path.exists() {
                    // Never delete a real user directory; only replace symlinks.
                    warn!(
                        "{} is not a symlink; refusing to replace",
                        symlink_path.display()
                    );
                    return Ok(());
                }
                std::os::unix::fs::symlink(runtime_dir, symlink_path).with_context(|| {
                    format!(
                        "symlink {} -> {}",
                        symlink_path.display(),
                        runtime_dir.display()
                    )
                })?;
            }
        }
        MountMode::Metamodule => {
            if points_to_uranxsu_nomount {
                let _ = fs::remove_file(symlink_path);
            }
            if !symlink_path.is_symlink()
                && !symlink_path.exists()
                && let Some(ext) = metamodule::find_external_metamodule()
            {
                let _ = std::os::unix::fs::symlink(&ext.path, symlink_path);
            }
        }
        MountMode::Disabled => {
            if points_to_uranxsu_nomount {
                let _ = fs::remove_file(symlink_path);
            }
        }
    }

    Ok(())
}

fn run_nm(args: &[&str]) -> Result<String> {
    let nm = nm_bin_path();
    if !nm.exists() {
        ensure_runtime_staged()?;
    }
    let output = Command::new(&nm)
        .args(args)
        .output()
        .with_context(|| format!("execute {} {}", nm.display(), args.join(" ")))?;
    let stdout = String::from_utf8_lossy(&output.stdout).trim().to_string();
    let stderr = String::from_utf8_lossy(&output.stderr).trim().to_string();
    if !output.status.success() {
        bail!(
            "nm {} failed (code {:?}): {} {}",
            args.join(" "),
            output.status.code(),
            stdout,
            stderr
        );
    }
    Ok(stdout)
}

pub fn query_driver_version() -> Option<String> {
    run_nm(&["version"]).ok().filter(|s| !s.is_empty())
}

fn match_release_to_kmi(rel: &str, available: &[String]) -> Option<String> {
    for kmi in available {
        if let Some((os, kver)) = kmi.split_once('-') {
            let prefix = format!("{kver}.");
            let needle = format!("-{os}-");
            if rel.starts_with(&prefix) && rel.contains(&needle) {
                return Some(kmi.clone());
            }
        }
    }
    if rel.contains("android") {
        return None;
    }
    let mut matched: Option<String> = None;
    let mut count = 0usize;
    for kmi in available {
        if let Some((_, kver)) = kmi.split_once('-') {
            let prefix = format!("{kver}.");
            if let Some(rest) = rel.strip_prefix(&prefix) {
                let patch = rest.split('-').next().unwrap_or("");
                if !patch.is_empty() && patch.bytes().all(|b| b.is_ascii_digit()) {
                    matched = Some(kmi.clone());
                    count += 1;
                }
            }
        }
    }
    if count == 1 { matched } else { None }
}

fn resolve_nomount_kmi(override_kmi: &str, available: &[String]) -> Result<String> {
    let trimmed = override_kmi.trim();
    if !trimmed.is_empty() && available.iter().any(|k| k == trimmed) {
        return Ok(trimmed.to_string());
    }
    let uname = rustix::system::uname();
    let rel = uname.release().to_string_lossy();
    if let Some(kmi) = match_release_to_kmi(&rel, available) {
        return Ok(kmi);
    }
    if let Ok(kmi) = crate::boot_patch::get_current_kmi()
        && available.iter().any(|k| k == &kmi)
    {
        return Ok(kmi);
    }
    bail!("No matching embedded nomount.ko for kernel '{rel}'")
}

fn load_ko_via_susfs_insmod(ko_path: &Path) -> Result<()> {
    let insmod_bytes = assets::get_asset("susfs_insmod")?;
    let insmod_path = Path::new(defs::NOMOUNT_RUNTIME_DIR).join("susfs_insmod");
    let _ = fs::remove_file(&insmod_path);
    fs::write(&insmod_path, &insmod_bytes)?;
    set_permissions(&insmod_path, Permissions::from_mode(0o700))?;

    let prev_kptr = fs::read_to_string("/proc/sys/kernel/kptr_restrict")
        .ok()
        .map(|s| s.trim().to_string());
    if prev_kptr.as_deref() != Some("0") {
        let _ = fs::write("/proc/sys/kernel/kptr_restrict", "0\n");
    }

    let status = Command::new(&insmod_path).arg(ko_path).status();

    if let Some(prev) = prev_kptr
        && prev != "0"
    {
        let _ = fs::write("/proc/sys/kernel/kptr_restrict", format!("{prev}\n"));
    }
    let _ = fs::remove_file(&insmod_path);

    let status = status.context("run susfs_insmod for nomount.ko")?;
    if !status.success() {
        bail!("susfs_insmod exited with status {:?}", status.code());
    }
    Ok(())
}

pub fn ensure_driver_loaded(kmi_override: Option<&str>) -> Result<()> {
    ensure_runtime_staged()?;
    if query_driver_version().is_some() {
        return Ok(());
    }

    let config = MountConfig::load_or_default();
    let available = assets::list_supported_nomount_kmi();
    if available.is_empty() {
        bail!("No embedded nomount.ko available for this architecture");
    }

    let effective_override = kmi_override.unwrap_or(&config.nomount.kmi_override);
    let kmi = resolve_nomount_kmi(effective_override, &available)?;
    let ko_asset = format!("{kmi}_nomount.ko");
    let ko_bytes = assets::get_asset(&ko_asset)
        .with_context(|| format!("missing embedded asset {ko_asset}"))?;

    let ko_path = Path::new(defs::NOMOUNT_RUNTIME_DIR).join("nomount.ko");
    let _ = fs::remove_file(&ko_path);
    fs::write(&ko_path, &ko_bytes).context("extract nomount.ko")?;
    set_permissions(&ko_path, Permissions::from_mode(0o600))?;

    let insmod_res = debug::insmod(&ko_path, &[]);
    if let Err(e) = insmod_res {
        warn!("ksud insmod for nomount.ko failed ({e:#}); trying fallback loader");
        let _ = Command::new("rmmod").arg("nomount").status();
        load_ko_via_susfs_insmod(&ko_path)?;
    }

    let _ = fs::remove_file(&ko_path);

    let Some(driver_version) = query_driver_version() else {
        bail!("nomount.ko loaded but NoMount driver API did not respond");
    };

    let _ = fs::write(active_kmi_marker_path(), format!("{kmi}\n"));
    crate::android::init_event::write_component_marker(
        defs::NOMOUNT_LOADED_HASH_PATH,
        defs::NOMOUNT_HASH,
        &driver_version.to_string(),
    );
    info!("Built-in NoMount driver loaded ({kmi})");
    Ok(())
}

fn bootloop_check() -> bool {
    let config = MountConfig::load_or_default();
    if !config.nomount.bootloop_guard {
        return true;
    }

    let _ = create_dir_all(defs::NOMOUNT_DATA_DIR);
    let sem_path = boot_semaphore_path();
    let current = fs::read_to_string("/proc/sys/kernel/random/boot_id")
        .ok()
        .map(|s| s.trim().to_string())
        .filter(|s| !s.is_empty())
        .unwrap_or_else(|| "fallback".to_string());

    if let Ok(prev) = fs::read_to_string(&sem_path) {
        let prev = prev.trim();
        if !prev.is_empty() && prev != current {
            warn!("NoMount bootloop detected (prev={prev}, current={current}); disabling NoMount");
            let _ = fs::write(disable_flag_path(), "");
            let _ = fs::remove_file(&sem_path);
            return false;
        }
    } else {
        let _ = fs::write(&sem_path, format!("{current}\n"));
    }

    true
}

pub fn on_post_fs_data_early() {
    if utils::is_safe_mode() {
        return;
    }
    let config = MountConfig::load_or_default();
    if let Err(e) = reconcile_metamodule_symlink(config.mode) {
        warn!("reconcile metamodule symlink failed: {e:#}");
    }
    if config.mode != MountMode::NoMount {
        return;
    }
    if disable_flag_path().exists() {
        warn!("NoMount disabled via /data/adb/nomount/disable");
        return;
    }
    if !bootloop_check() {
        return;
    }
    if config.nomount.auto_load_lkm
        && let Err(e) = ensure_driver_loaded(None)
    {
        warn!("NoMount early driver load failed: {e:#}");
    }
}

pub fn mount_all_modules() -> Result<()> {
    if utils::is_safe_mode() {
        return Ok(());
    }
    let config = MountConfig::load_or_default();
    ensure_runtime_staged()?;
    if disable_flag_path().exists() {
        warn!("NoMount is disabled by bootloop guard; skipping mount");
        return Ok(());
    }
    if !bootloop_check() {
        return Ok(());
    }
    if config.nomount.auto_load_lkm {
        ensure_driver_loaded(None)?;
    } else if query_driver_version().is_none() {
        bail!("NoMount driver is not loaded and auto_load_lkm is disabled");
    }

    let modules_dir = Path::new(defs::MODULE_DIR);
    if !modules_dir.is_dir() {
        return Ok(());
    }

    let mut entries: Vec<PathBuf> = fs::read_dir(modules_dir)?
        .flatten()
        .map(|e| e.path())
        .filter(|p| p.is_dir())
        .collect();
    entries.sort();

    let mut injected = 0usize;
    for mod_path in entries {
        let Some(mod_id) = mod_path.file_name().and_then(|s| s.to_str()) else {
            continue;
        };
        if mod_id == "nomount" || mod_id.contains("tricky") || mod_id.contains("playintegrity") {
            continue;
        }
        if mod_path.join(defs::DISABLE_FILE_NAME).exists()
            || mod_path.join(defs::REMOVE_FILE_NAME).exists()
            || mod_path.join("skip_mount").exists()
            || config.nomount.skipped_modules.iter().any(|s| s == mod_id)
        {
            continue;
        }
        let props = module::read_module_prop(&mod_path).unwrap_or_default();
        if metamodule::is_metamodule(&props) {
            continue;
        }
        if !mount::has_mountable_partitions(&mod_path) {
            continue;
        }
        let path_str = mod_path.to_string_lossy();
        match run_nm(&["module", "load", &path_str]) {
            Ok(_) => {
                injected += 1;
            }
            Err(e) => {
                warn!("NoMount failed loading module {mod_id}: {e:#}");
            }
        }
    }

    // Do NOT call ksucalls::report_module_mounted() here: matching upstream maxsteeel/nomount,
    // NoMount creates zero kernel mounts and must not arm unnecessary mount-cleanup paths.
    info!("NoMount injected {injected} mountable modules");
    Ok(())
}

pub fn read_exclusions() -> Vec<NoMountExclusionEntry> {
    let Ok(raw) = fs::read_to_string(exclusion_list_path()) else {
        return Vec::new();
    };
    serde_json::from_str::<Vec<NoMountExclusionEntry>>(&raw).unwrap_or_default()
}

fn write_exclusions(entries: &[NoMountExclusionEntry]) -> Result<()> {
    create_dir_all(defs::NOMOUNT_DATA_DIR)?;
    let json = serde_json::to_string_pretty(entries)?;
    fs::write(exclusion_list_path(), json)?;
    Ok(())
}

pub fn apply_uid_settings() {
    if query_driver_version().is_none() {
        return;
    }
    let exclusions = read_exclusions();
    for entry in &exclusions {
        let uid = entry.uid.trim();
        if !uid.is_empty() && uid.bytes().all(|b| b.is_ascii_digit()) {
            let _ = run_nm(&["uid", "add", uid]);
        }
    }

    let config = MountConfig::load_or_default();
    let block = config.nomount.block_isolated_uids || block_isolated_flag_path().exists();
    if block {
        let _ = run_nm(&["uid", "block_isolated", "on"]);
    }
}

pub fn on_services() {
    let config = MountConfig::load_or_default();
    if config.mode != MountMode::NoMount || disable_flag_path().exists() {
        return;
    }
    apply_uid_settings();
}

pub fn on_boot_completed() {
    let sem_path = boot_semaphore_path();
    if sem_path.exists() {
        let _ = fs::remove_file(&sem_path);
        info!("NoMount boot completed safely");
    }
    let config = MountConfig::load_or_default();
    if config.mode == MountMode::NoMount
        && (config.nomount.dump_rules_on_boot || dump_rules_flag_path().exists())
        && let Ok(rules) = run_nm(&["rule", "list"])
    {
        info!(
            "NoMount rules dump on boot ({} lines)",
            rules.lines().count()
        );
    }
}

pub fn reload_all() -> Result<()> {
    ensure_driver_loaded(None)?;
    let _ = run_nm(&["rule", "clear"]);
    mount_all_modules()?;
    apply_uid_settings();
    Ok(())
}

pub fn clear_rules() -> Result<()> {
    run_nm(&["rule", "clear"])?;
    Ok(())
}

pub fn clear_all() -> Result<()> {
    run_nm(&["clear", "all"])?;
    Ok(())
}

pub fn module_load(id: &str) -> Result<()> {
    module::validate_module_id(id)?;
    ensure_driver_loaded(None)?;
    let mod_path = Path::new(defs::MODULE_DIR).join(id);
    if !mod_path.is_dir() {
        bail!("Module {id} not found");
    }
    let path_str = mod_path.to_string_lossy();
    run_nm(&["module", "load", &path_str])?;
    let mut cfg = MountConfig::load_or_default();
    cfg.nomount.skipped_modules.retain(|m| m != id);
    cfg.save()?;
    Ok(())
}

pub fn module_unload(id: &str) -> Result<()> {
    module::validate_module_id(id)?;
    let mod_path = Path::new(defs::MODULE_DIR).join(id);
    let path_str = mod_path.to_string_lossy();
    if query_driver_version().is_some() {
        let _ = run_nm(&["module", "unload", &path_str]);
    }
    let mut cfg = MountConfig::load_or_default();
    if !cfg.nomount.skipped_modules.iter().any(|m| m == id) {
        cfg.nomount.skipped_modules.push(id.to_string());
    }
    cfg.save()?;
    Ok(())
}

pub fn uid_add(uid: u32, pkg: Option<&str>) -> Result<()> {
    let uid_str = uid.to_string();
    if query_driver_version().is_some() {
        run_nm(&["uid", "add", &uid_str])?;
    }
    let mut entries = read_exclusions();
    if let Some(existing) = entries.iter_mut().find(|e| e.uid == uid_str) {
        if let Some(p) = pkg
            && !p.is_empty()
        {
            existing.pkg = p.to_string();
        }
    } else {
        entries.push(NoMountExclusionEntry {
            uid: uid_str,
            pkg: pkg.unwrap_or_default().to_string(),
        });
    }
    write_exclusions(&entries)?;
    Ok(())
}

pub fn uid_del(uid: u32) -> Result<()> {
    let uid_str = uid.to_string();
    if query_driver_version().is_some() {
        let _ = run_nm(&["uid", "del", &uid_str]);
    }
    let mut entries = read_exclusions();
    entries.retain(|e| e.uid != uid_str);
    write_exclusions(&entries)?;
    Ok(())
}

pub fn rules_json() -> Result<String> {
    if query_driver_version().is_none() {
        return Ok("[]".to_string());
    }
    run_nm(&["rule", "list", "--json"])
}

pub fn set_config(
    auto_load_lkm: Option<bool>,
    kmi_override: Option<String>,
    block_isolated_uids: Option<bool>,
    dump_rules_on_boot: Option<bool>,
    bootloop_guard: Option<bool>,
    clear_bootloop_disable: bool,
) -> Result<NoMountConfig> {
    let mut cfg = MountConfig::load_or_default();
    if let Some(v) = auto_load_lkm {
        cfg.nomount.auto_load_lkm = v;
    }
    if let Some(v) = kmi_override {
        cfg.nomount.kmi_override = v.trim().to_string();
    }
    if let Some(v) = block_isolated_uids {
        cfg.nomount.block_isolated_uids = v;
        let _ = create_dir_all(defs::NOMOUNT_DATA_DIR);
        if v {
            let _ = fs::write(block_isolated_flag_path(), "1\n");
        } else {
            let _ = fs::remove_file(block_isolated_flag_path());
        }
        if query_driver_version().is_some() {
            let _ = run_nm(&["uid", "block_isolated", if v { "on" } else { "off" }]);
        }
    }
    if let Some(v) = dump_rules_on_boot {
        cfg.nomount.dump_rules_on_boot = v;
        let _ = create_dir_all(defs::NOMOUNT_DATA_DIR);
        if v {
            let _ = fs::write(dump_rules_flag_path(), "1\n");
        } else {
            let _ = fs::remove_file(dump_rules_flag_path());
        }
    }
    if let Some(v) = bootloop_guard {
        cfg.nomount.bootloop_guard = v;
    }
    if clear_bootloop_disable {
        let _ = fs::remove_file(disable_flag_path());
        let _ = fs::remove_file(boot_semaphore_path());
    }
    cfg.save()?;
    Ok(cfg.nomount)
}

fn query_injected_module_counts() -> HashMap<String, usize> {
    let mut map = HashMap::new();
    let Ok(raw) = run_nm(&["module", "list", "--json"]) else {
        return map;
    };
    if let Ok(serde_json::Value::Array(items)) = serde_json::from_str::<serde_json::Value>(&raw) {
        for item in items {
            let id = item
                .get("id")
                .or_else(|| item.get("module"))
                .or_else(|| item.get("name"))
                .and_then(|v| v.as_str());
            let count = item
                .get("count")
                .or_else(|| item.get("files"))
                .or_else(|| item.get("rules"))
                .and_then(serde_json::Value::as_u64);
            if let (Some(id), Some(count)) = (id, count) {
                map.insert(id.to_string(), count as usize);
            }
        }
    }
    map
}

pub fn get_status() -> NoMountStatus {
    let _ = ensure_runtime_staged();
    let cfg = MountConfig::load_or_default();
    let available_kmis = assets::list_supported_nomount_kmi();
    let detected_kmi = resolve_nomount_kmi("", &available_kmis).unwrap_or_default();
    let driver_version = query_driver_version().unwrap_or_default();
    let driver_ready = !driver_version.is_empty();

    let active_kmi = if driver_ready {
        fs::read_to_string(active_kmi_marker_path())
            .ok()
            .map(|s| s.trim().to_string())
            .unwrap_or_default()
    } else {
        String::new()
    };

    let rule_count = if driver_ready {
        run_nm(&["rule", "list"])
            .ok()
            .map(|s| s.lines().filter(|l| !l.trim().is_empty()).count())
            .unwrap_or(0)
    } else {
        0
    };

    let live_uids: Vec<u32> = if driver_ready {
        run_nm(&["uid", "list"])
            .ok()
            .and_then(|s| serde_json::from_str::<Vec<u32>>(&s).ok())
            .unwrap_or_default()
    } else {
        Vec::new()
    };

    let block_isolated = if driver_ready {
        run_nm(&["uid", "block_isolated"])
            .ok()
            .is_some_and(|s| s.contains('1') || s.eq_ignore_ascii_case("on"))
    } else {
        cfg.nomount.block_isolated_uids || block_isolated_flag_path().exists()
    };

    let injected_counts = if driver_ready {
        query_injected_module_counts()
    } else {
        HashMap::new()
    };

    let mut modules = Vec::new();
    if let Ok(read_dir) = fs::read_dir(defs::MODULE_DIR) {
        let mut dirs: Vec<PathBuf> = read_dir
            .flatten()
            .map(|e| e.path())
            .filter(|p| p.is_dir())
            .collect();
        dirs.sort();
        for dir in dirs {
            let Some(id) = dir.file_name().and_then(|s| s.to_str()) else {
                continue;
            };
            if id == "nomount" {
                continue;
            }
            let props = module::read_module_prop(&dir).unwrap_or_default();
            if metamodule::is_metamodule(&props) {
                continue;
            }
            let mut parts: Vec<String> = mount::detect_module_partitions(&dir)
                .into_iter()
                .map(|(p, _)| p)
                .collect();
            parts.sort();
            parts.dedup();
            if parts.is_empty() {
                continue;
            }
            let name = props.get("name").cloned().unwrap_or_else(|| id.to_string());
            let enabled = !dir.join(defs::DISABLE_FILE_NAME).exists()
                && !dir.join(defs::REMOVE_FILE_NAME).exists()
                && !dir.join("skip_mount").exists();
            let skipped = cfg.nomount.skipped_modules.iter().any(|s| s == id);
            let injected_files = injected_counts.get(id).copied().unwrap_or(0);
            modules.push(NoMountModuleInfo {
                id: id.to_string(),
                name,
                enabled,
                skipped,
                injected_files,
                partitions: parts,
            });
        }
    }

    let exclusions = read_exclusions();
    let uid_count = if !live_uids.is_empty() {
        live_uids.len()
    } else {
        exclusions.len()
    };

    NoMountStatus {
        driver_ready,
        driver_version,
        active_kmi,
        detected_kmi,
        available_kmis,
        rule_count,
        uid_count,
        block_isolated,
        bootloop_disabled: disable_flag_path().exists(),
        config: cfg.nomount,
        exclusions,
        modules,
    }
}
