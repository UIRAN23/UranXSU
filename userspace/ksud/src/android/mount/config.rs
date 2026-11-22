use std::{
    fs::{self, Permissions, create_dir_all, set_permissions},
    io::Write,
    os::unix::fs::PermissionsExt,
    path::Path,
};

use anyhow::Result;
use serde::{Deserialize, Serialize};
use tempfile::NamedTempFile;

use crate::defs;

const fn default_true() -> bool {
    true
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, Default)]
#[serde(rename_all = "snake_case")]
pub enum MountMode {
    #[default]
    NoMount,
    Metamodule,
    Disabled,
}

impl MountMode {
    pub const fn as_str(self) -> &'static str {
        match self {
            Self::NoMount => "no_mount",
            Self::Metamodule => "metamodule",
            Self::Disabled => "disabled",
        }
    }

    pub fn parse(value: &str) -> Option<Self> {
        match value.trim().to_ascii_lowercase().as_str() {
            "nomount" | "no_mount" => Some(Self::NoMount),
            "metamodule" => Some(Self::Metamodule),
            "disabled" | "none" | "off" => Some(Self::Disabled),
            _ => None,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct NoMountConfig {
    #[serde(default = "default_true")]
    pub auto_load_lkm: bool,
    #[serde(default)]
    pub kmi_override: String,
    #[serde(default)]
    pub block_isolated_uids: bool,
    #[serde(default)]
    pub dump_rules_on_boot: bool,
    #[serde(default = "default_true")]
    pub bootloop_guard: bool,
    #[serde(default)]
    pub skipped_modules: Vec<String>,
}

impl Default for NoMountConfig {
    fn default() -> Self {
        Self {
            auto_load_lkm: true,
            kmi_override: String::new(),
            block_isolated_uids: false,
            dump_rules_on_boot: false,
            bootloop_guard: true,
            skipped_modules: Vec::new(),
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct MountConfig {
    #[serde(default)]
    pub mode: MountMode,
    #[serde(default)]
    pub nomount: NoMountConfig,
}

impl MountConfig {
    pub fn load_or_default() -> Self {
        fs::read_to_string(defs::MOUNT_CONFIG_PATH)
            .ok()
            .and_then(|s| serde_json::from_str(&s).ok())
            .unwrap_or_default()
    }

    pub fn save(&self) -> Result<()> {
        let target_path = Path::new(defs::MOUNT_CONFIG_PATH);
        if let Some(parent) = target_path.parent() {
            create_dir_all(parent)?;
        }
        let target_dir = target_path.parent().unwrap_or_else(|| Path::new("."));
        let mut temp_file = NamedTempFile::new_in(target_dir)?;
        serde_json::to_writer_pretty(&mut temp_file, self)?;
        temp_file.flush()?;
        set_permissions(temp_file.path(), Permissions::from_mode(0o600))?;
        temp_file.as_file().sync_all()?;
        temp_file.persist(target_path)?;
        Ok(())
    }
}
