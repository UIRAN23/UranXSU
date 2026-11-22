//! Metamodule management
//!
//! This module handles all metamodule-related functionality.
//! Metamodules are special modules that manage how regular modules are mounted
//! and provide hooks for module installation/uninstallation.

use std::{
    collections::HashMap,
    fs::{self, File},
    path::{Path, PathBuf},
    process::Command,
};

use anyhow::{Context, Result, ensure};
use log::{info, warn};

use crate::{
    android::{
        module::{self, ModuleType::All, ScriptWait},
        mount::config::{MountConfig, MountMode},
    },
    assets, defs,
};

/// Metadata about an external (user-installed) metamodule in `/data/adb/modules/`.
#[derive(Debug, Clone)]
pub struct ExternalMetamoduleInfo {
    pub id: String,
    pub name: String,
    pub path: PathBuf,
    pub enabled: bool,
}

/// Returns true when UranXSU's built-in mount engine (`NoMount`)
/// owns mounting and external metamodule boot scripts should be suppressed.
pub fn is_external_metamodule_suppressed() -> bool {
    matches!(MountConfig::load_or_default().mode, MountMode::NoMount)
}

/// Scan `/data/adb/modules/*` directly for a user-installed metamodule (`metamodule=true/1`),
/// ignoring UranXSU's internal `/data/adb/ksu/nomount` runtime directory.
pub fn find_external_metamodule() -> Option<ExternalMetamoduleInfo> {
    let mut found = None;
    let _ = module::foreach_module(All, |module_path| {
        if let Ok(props) = module::read_module_prop(module_path)
            && is_metamodule(&props)
        {
            let dir_id = module_path
                .file_name()
                .and_then(|s| s.to_str())
                .unwrap_or_default()
                .to_string();
            let id = props
                .get("id")
                .map(|s| s.trim().to_string())
                .filter(|s| !s.is_empty())
                .unwrap_or(dir_id);
            let name = props
                .get("name")
                .map(|s| s.trim().to_string())
                .filter(|s| !s.is_empty())
                .unwrap_or_else(|| id.clone());
            let enabled = !module_path.join(defs::DISABLE_FILE_NAME).exists()
                && !module_path.join(defs::REMOVE_FILE_NAME).exists();
            found = Some(ExternalMetamoduleInfo {
                id,
                name,
                path: module_path.to_path_buf(),
                enabled,
            });
        }
        Ok(())
    });
    found
}

/// Determine whether the provided module properties mark it as a metamodule
pub fn is_metamodule(props: &HashMap<String, String>) -> bool {
    props.get("metamodule").is_some_and(|s| {
        let trimmed = s.trim();
        trimmed == "1" || trimmed.eq_ignore_ascii_case("true")
    })
}

/// Get metamodule path if it exists.
/// Respects UranXSU's configured mount engine so built-in NoMount
/// never accidentally invokes an external metamodule's scripts.
pub fn get_metamodule_path() -> Option<PathBuf> {
    let mode = MountConfig::load_or_default().mode;
    if mode == MountMode::NoMount {
        let runtime = PathBuf::from(defs::NOMOUNT_RUNTIME_DIR.trim_end_matches('/'));
        if runtime.exists() && runtime.is_dir() {
            return Some(runtime);
        }
    }

    // IMPORTANT: trim trailing slash so `lstat` inspects the symlink itself instead of dereferencing it.
    let path = Path::new(defs::METAMODULE_DIR.trim_end_matches('/'));

    // Check if symlink exists and resolve it
    if path.is_symlink()
        && let Ok(target) = std::fs::read_link(path)
    {
        // If target is relative, resolve it
        let resolved = if target.is_absolute() {
            target
        } else {
            path.parent()?.join(target)
        };

        if resolved.exists() && resolved.is_dir() {
            if mode == MountMode::Metamodule
                && crate::android::mount::nomount::is_runtime_symlink_target(&resolved)
            {
                // Ignore stale built-in NoMount symlink when in external Metamodule mode.
            } else {
                return Some(resolved);
            }
        } else {
            warn!(
                "Metamodule symlink points to non-existent path: {}",
                resolved.display()
            );
        }
    }

    if mode != MountMode::Metamodule {
        return None;
    }

    // Fallback: search for metamodule=1 in modules directory
    if let Some(ext) = find_external_metamodule() {
        info!(
            "Found metamodule in modules directory: {}",
            ext.path.display()
        );
        return Some(ext.path);
    }

    None
}

/// Get Metamodule Id
pub fn get_metamodule_id() -> Option<String> {
    get_metamodule_path().and_then(|path| {
        path.file_name()
            .and_then(|os_str| os_str.to_str())
            .map(ToString::to_string)
    })
}

/// Check if metamodule exists
pub fn has_metamodule() -> bool {
    get_metamodule_path().is_some()
}

/// Check if it's safe to install a regular module
/// Returns Ok(()) if safe, Err(is_disabled) if blocked
/// - Err(true) means metamodule is disabled
/// - Err(false) means metamodule is in other unstable state
pub fn check_install_safety() -> Result<(), bool> {
    // No metamodule → safe
    let Some(metamodule_path) = get_metamodule_path() else {
        return Ok(());
    };

    // No metainstall.sh → safe (uses default installer)
    // The staged update directory may contain the latest scripts, so check both locations
    let has_metainstall = metamodule_path
        .join(defs::METAMODULE_METAINSTALL_SCRIPT)
        .exists()
        || metamodule_path.file_name().is_some_and(|module_id| {
            Path::new(defs::MODULE_UPDATE_DIR)
                .join(module_id)
                .join(defs::METAMODULE_METAINSTALL_SCRIPT)
                .exists()
        });
    if !has_metainstall {
        return Ok(());
    }

    // Check for marker files
    let has_update = metamodule_path.join(defs::UPDATE_FILE_NAME).exists();
    let has_remove = metamodule_path.join(defs::REMOVE_FILE_NAME).exists();
    let has_disable = metamodule_path.join(defs::DISABLE_FILE_NAME).exists();

    // Stable state (no markers) → safe
    if !has_update && !has_remove && !has_disable {
        return Ok(());
    }

    // Return true if disabled, false for other unstable states
    Err(has_disable && !has_update && !has_remove)
}

/// Create or update the metamodule symlink
/// Points /data/adb/metamodule -> /data/adb/modules/{module_id}
pub fn ensure_symlink(module_path: &Path) -> Result<()> {
    // METAMODULE_DIR might have trailing slash, so we need to trim it
    let symlink_path = Path::new(defs::METAMODULE_DIR.trim_end_matches('/'));

    info!(
        "Creating metamodule symlink: {} -> {}",
        symlink_path.display(),
        module_path.display()
    );

    // Remove existing symlink if it exists
    if symlink_path.exists() || symlink_path.is_symlink() {
        info!("Removing old metamodule symlink/path");
        if symlink_path.is_symlink() {
            std::fs::remove_file(symlink_path).with_context(|| "Failed to remove old symlink")?;
        } else {
            // Could be a directory, remove it
            std::fs::remove_dir_all(symlink_path)
                .with_context(|| "Failed to remove old directory")?;
        }
    }

    // Create symlink
    std::os::unix::fs::symlink(module_path, symlink_path)
        .with_context(|| format!("Failed to create symlink to {}", module_path.display()))?;

    info!("Metamodule symlink created successfully");
    Ok(())
}

/// Remove the metamodule symlink
pub fn remove_symlink() -> Result<()> {
    let symlink_path = Path::new(defs::METAMODULE_DIR.trim_end_matches('/'));

    if symlink_path.is_symlink() {
        std::fs::remove_file(symlink_path)
            .with_context(|| "Failed to remove metamodule symlink")?;
        info!("Metamodule symlink removed");
    }

    Ok(())
}

/// Get the install script content, using metainstall.sh from metamodule if available
/// Returns the script content to be executed
pub fn get_install_script(
    is_metamodule: bool,
    installer_content: &str,
    install_module_script: &str,
) -> Result<String> {
    // Check if there's a metamodule with metainstall.sh
    // Only apply this logic for regular modules (not when installing metamodule itself)
    let install_script = if is_metamodule {
        info!("Installing metamodule, using default installer");
        install_module_script.to_string()
    } else if let Some(metamodule_path) = get_metamodule_path() {
        if metamodule_path.join(defs::DISABLE_FILE_NAME).exists() {
            info!("Metamodule is disabled, using default installer");
            install_module_script.to_string()
        } else {
            let metainstall_path = metamodule_path.join(defs::METAMODULE_METAINSTALL_SCRIPT);

            if metainstall_path.exists() {
                info!("Using metainstall.sh from metamodule");
                let metamodule_content = std::fs::read_to_string(&metainstall_path)
                    .with_context(|| "Failed to read metamodule metainstall.sh")?;
                format!("{installer_content}\n{metamodule_content}\nexit 0\n")
            } else {
                info!("Metamodule exists but has no metainstall.sh, using default installer");
                install_module_script.to_string()
            }
        }
    } else {
        info!("No metamodule found, using default installer");
        install_module_script.to_string()
    };

    Ok(install_script)
}

/// Check if metamodule script exists and is ready to execute
/// Returns None if metamodule doesn't exist, is disabled, or script is missing
/// Returns Some(script_path) if script is ready to execute
fn check_metamodule_script(script_name: &str) -> Option<PathBuf> {
    // Check if metamodule exists
    let metamodule_path = get_metamodule_path()?;

    // Check if metamodule is disabled
    if metamodule_path.join(defs::DISABLE_FILE_NAME).exists() {
        info!("Metamodule is disabled, skipping {script_name}");
        return None;
    }

    // Check if script exists
    let script_path = metamodule_path.join(script_name);
    if !script_path.exists() {
        return None;
    }

    Some(script_path)
}

/// Execute metamodule's metauninstall.sh for a specific module
pub fn exec_metauninstall_script(module_id: &str) -> Result<()> {
    let Some(metauninstall_path) = check_metamodule_script(defs::METAMODULE_METAUNINSTALL_SCRIPT)
    else {
        return Ok(());
    };

    info!("Executing metamodule metauninstall.sh for module: {module_id}");

    let mut command = Command::new(assets::BUSYBOX_PATH);
    command
        .args(["sh", metauninstall_path.to_str().unwrap()])
        .current_dir(metauninstall_path.parent().unwrap())
        .envs(crate::android::module::get_common_script_envs(
            get_metamodule_id().as_deref(),
        ))
        .env("MODULE_ID", module_id);
    if fs::exists(defs::METAMODULE_DEBUG)? {
        command
            .stdout(File::open(format!(
                "{}.out.log",
                defs::METAMODULE_METAUNINSTALL_SCRIPT_LOG
            ))?)
            .stderr(File::open(format!(
                "{}.err.log",
                defs::METAMODULE_METAUNINSTALL_SCRIPT_LOG
            ))?);
    }
    let result = command.status()?;

    ensure!(
        result.success(),
        "Metamodule metauninstall.sh failed for module {module_id}",
    );

    info!("Metamodule metauninstall.sh executed successfully for {module_id}");
    Ok(())
}

/// Execute metamodule mount script
pub fn exec_mount_script(module_dir: &str) -> Result<()> {
    let Some(mount_script) = check_metamodule_script(defs::METAMODULE_MOUNT_SCRIPT) else {
        return Ok(());
    };

    info!("Executing mount script for metamodule");

    let mut command = Command::new(assets::BUSYBOX_PATH);
    command
        .args(["sh", mount_script.to_str().unwrap()])
        .envs(crate::android::module::get_common_script_envs(
            get_metamodule_id().as_deref(),
        ))
        .env("MODULE_DIR", module_dir);

    if fs::exists(defs::METAMODULE_DEBUG)? {
        command
            .stdout(File::open(format!(
                "{}.out.log",
                defs::METAMODULE_MOUNT_SCRIPT_LOG
            ))?)
            .stderr(File::open(format!(
                "{}.err.log",
                defs::METAMODULE_MOUNT_SCRIPT_LOG
            ))?);
    }
    let result = command.status()?;

    ensure!(result.success(), "Metamodule mount script failed");

    info!("Metamodule mount script executed successfully");
    Ok(())
}

/// Execute metamodule script for a specific stage
pub fn exec_stage_script(stage: &str, wait: ScriptWait) -> Result<()> {
    if is_external_metamodule_suppressed() {
        info!("Built-in mount engine active; skipping external metamodule {stage}.sh");
        return Ok(());
    }

    let Some(script_path) = check_metamodule_script(&format!("{stage}.sh")) else {
        return Ok(());
    };

    info!("Executing metamodule {stage}.sh");
    module::exec_script(&script_path, wait)?;
    info!("Metamodule {stage}.sh executed successfully");
    Ok(())
}
