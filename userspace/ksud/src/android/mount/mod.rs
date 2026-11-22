pub mod cli;
pub mod config;
pub mod nomount;

use std::{
    fs,
    path::{Path, PathBuf},
};

use anyhow::Result;
use log::{info, warn};
use serde::{Deserialize, Serialize};

use crate::android::{
    module::metamodule,
    mount::config::{MountConfig, MountMode},
};

pub const KNOWN_PARTITIONS: &[&str] = &[
    "system",
    "vendor",
    "product",
    "system_ext",
    "odm",
    "apex",
    "oem",
    "optics",
    "prism",
    "mi_ext",
    "my_bigball",
    "my_carrier",
    "my_company",
    "my_engineering",
    "my_heytap",
    "my_manifest",
    "my_preload",
    "my_product",
    "my_region",
    "my_reserve",
    "my_stock",
];

/// Returns true if `/<part>` exists as a real separate partition directory on the device
/// (or `/system/<part>` is a symlink to `/<part>`).
pub fn is_separate_partition(part: &str) -> bool {
    if part == "system" {
        return false;
    }
    let root_part = Path::new("/").join(part);
    if let Ok(meta) = fs::symlink_metadata(&root_part)
        && meta.is_dir()
    {
        return true;
    }
    Path::new("/system").join(part).is_symlink() && root_part.is_dir()
}

fn dir_has_any_entry(dir: &Path) -> bool {
    fs::read_dir(dir).is_ok_and(|mut it| it.next().is_some())
}

fn system_dir_has_non_partition_entries(sys_dir: &Path) -> bool {
    let Ok(entries) = fs::read_dir(sys_dir) else {
        return false;
    };
    for entry in entries.flatten() {
        let name = entry.file_name();
        let name_str = name.to_string_lossy();
        if KNOWN_PARTITIONS.contains(&name_str.as_ref()) && is_separate_partition(&name_str) {
            continue;
        }
        return true;
    }
    false
}

/// Detect all mountable partition roots inside a module directory.
/// Recognizes both `<mod>/<part>` and `<mod>/system/<part>` for every separate partition
/// (such as `odm`, `vendor`, `product`, `system_ext`, `my_*`, `mi_ext`), even when
/// `/system/<part>` is NOT a symlink (e.g. Android 14-17 SAR OnePlus/Oplus devices).
pub fn detect_module_partitions(mod_dir: &Path) -> Vec<(String, PathBuf)> {
    let mut result = Vec::new();

    for &part in KNOWN_PARTITIONS {
        if part == "system" {
            let sys_dir = mod_dir.join("system");
            if sys_dir.is_dir() && system_dir_has_non_partition_entries(&sys_dir) {
                result.push(("system".to_string(), sys_dir));
            }
            continue;
        }

        // Root-level partition directory (e.g. <mod>/odm, <mod>/vendor)
        let root_part = mod_dir.join(part);
        if root_part.is_dir() && dir_has_any_entry(&root_part) {
            result.push((part.to_string(), root_part));
        }

        // System-nested partition directory (e.g. <mod>/system/odm when /odm is a separate mount)
        let nested_part = mod_dir.join("system").join(part);
        if nested_part.is_dir() && is_separate_partition(part) && dir_has_any_entry(&nested_part) {
            result.push((part.to_string(), nested_part));
        }
    }

    result
}

/// Returns true if a module directory actually contains mountable partition files
/// (distinguishing filesystem-modifying modules from Zygisk/LKM/service-only modules).
pub fn has_mountable_partitions(mod_dir: &Path) -> bool {
    !detect_module_partitions(mod_dir).is_empty()
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MountOverallStatus {
    pub mode: String,
    pub external_metamodule_id: String,
    pub external_metamodule_name: String,
    pub external_metamodule_enabled: bool,
    pub external_metamodule_is_nomount: bool,
    pub nomount_driver_ready: bool,
    pub nomount_driver_version: String,
    pub nomount_rule_count: usize,
}

pub fn active_metamodule_env() -> Option<String> {
    match MountConfig::load_or_default().mode {
        MountMode::NoMount => Some("nomount".to_string()),
        MountMode::Metamodule => metamodule::find_external_metamodule()
            .filter(|m| m.enabled)
            .map(|m| m.id),
        MountMode::Disabled => None,
    }
}

pub fn get_overall_status() -> MountOverallStatus {
    let cfg = MountConfig::load_or_default();
    let nm = nomount::get_status();
    let ext = metamodule::find_external_metamodule();
    let external_metamodule_id = ext.as_ref().map(|m| m.id.clone()).unwrap_or_default();
    let external_metamodule_name = ext.as_ref().map(|m| m.name.clone()).unwrap_or_default();
    let external_metamodule_enabled = ext.as_ref().is_some_and(|m| m.enabled);
    let external_metamodule_is_nomount = ext.as_ref().is_some_and(|m| {
        m.id.eq_ignore_ascii_case("nomount") || m.name.eq_ignore_ascii_case("nomount")
    });

    MountOverallStatus {
        mode: cfg.mode.as_str().to_string(),
        external_metamodule_id,
        external_metamodule_name,
        external_metamodule_enabled,
        external_metamodule_is_nomount,
        nomount_driver_ready: nm.driver_ready,
        nomount_driver_version: nm.driver_version,
        nomount_rule_count: nm.rule_count,
    }
}

pub fn on_post_fs_data_early() {
    nomount::on_post_fs_data_early();
}

pub fn exec_mount(module_dir: &str) -> Result<()> {
    let cfg = MountConfig::load_or_default();
    info!("UranXSU mount engine executing mode: {}", cfg.mode.as_str());
    match cfg.mode {
        MountMode::NoMount => nomount::mount_all_modules(),
        MountMode::Metamodule => {
            if metamodule::get_metamodule_path()
                .as_deref()
                .is_some_and(nomount::is_runtime_symlink_target)
            {
                warn!("Metamodule symlink points to built-in NoMount; skipping external mount");
                return Ok(());
            }
            metamodule::exec_mount_script(module_dir)
        }
        MountMode::Disabled => {
            info!("Module mounting is disabled in UranXSU mount configuration");
            Ok(())
        }
    }
}

pub fn on_services() {
    nomount::on_services();
}

pub fn on_boot_completed() {
    nomount::on_boot_completed();
}
