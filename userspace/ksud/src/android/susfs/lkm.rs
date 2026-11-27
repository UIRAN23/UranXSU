use std::{
    fs::{self, File, OpenOptions, Permissions, create_dir_all, set_permissions},
    io::Write,
    os::unix::fs::PermissionsExt,
    path::Path,
    process::{Command, Stdio},
    thread,
    time::{Duration, Instant},
};

use anyhow::{Context, Result, bail};
use clap::Subcommand;
use log::{info, warn};
use serde::{Deserialize, Serialize};
use tempfile::NamedTempFile;

use crate::{
    android::{
        susfs::{api::prelude as api, config::model::Config},
        utils,
    },
    assets, defs,
};

const LOAD_TIMEOUT: Duration = Duration::from_secs(30);
const POLL_INTERVAL: Duration = Duration::from_millis(50);

const fn default_true() -> bool {
    true
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SusfsLkmConfig {
    #[serde(default = "default_true")]
    pub auto_load: bool,
    #[serde(default = "default_true")]
    pub early_load: bool,
    #[serde(default)]
    pub kmi_override: String,
    #[serde(default = "default_true")]
    pub expose_proc: bool,
    #[serde(default)]
    pub su_ctx: String,
    #[serde(default)]
    pub avc_su_ctx: String,
}

impl Default for SusfsLkmConfig {
    fn default() -> Self {
        Self {
            auto_load: true,
            early_load: true,
            kmi_override: String::new(),
            expose_proc: true,
            su_ctx: String::new(),
            avc_su_ctx: String::new(),
        }
    }
}

impl SusfsLkmConfig {
    pub fn load_or_default() -> Self {
        fs::read_to_string(defs::SUSFS_LKM_CONFIG)
            .ok()
            .and_then(|s| serde_json::from_str(&s).ok())
            .unwrap_or_default()
    }

    pub fn save(&self) -> Result<()> {
        let target_path = Path::new(defs::SUSFS_LKM_CONFIG);
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

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SusfsLkmStatus {
    pub source: String,
    pub version: String,
    pub variant: String,
    pub active_kmi: String,
    pub detected_kmi: String,
    pub available_kmis: Vec<String>,
    pub lkm_loaded: bool,
    pub config: SusfsLkmConfig,
    pub last_error: String,
}

#[derive(Debug, Subcommand)]
pub enum LkmCommand {
    /// Print SUSFS backend and built-in LKM status as JSON.
    Status,

    /// Load UranXSU built-in SUSFS LKM now.
    Load {
        /// Optional KMI override for this load attempt.
        #[arg(long)]
        kmi: Option<String>,
        /// Apply persisted SUSFS configuration immediately after loading.
        #[arg(long)]
        apply_config: bool,
    },

    /// Update built-in SUSFS LKM loader configuration.
    SetConfig {
        #[arg(long)]
        auto_load: Option<bool>,
        #[arg(long)]
        early_load: Option<bool>,
        #[arg(long)]
        kmi_override: Option<String>,
        #[arg(long)]
        expose_proc: Option<bool>,
        #[arg(long)]
        su_ctx: Option<String>,
        #[arg(long)]
        avc_su_ctx: Option<String>,
    },

    /// Print the last SUSFS LKM load log.
    Log,
}

struct KptrRestrictGuard {
    previous: Option<String>,
}

impl KptrRestrictGuard {
    fn allow_kallsyms() -> Self {
        const PATH: &str = "/proc/sys/kernel/kptr_restrict";
        let previous = fs::read_to_string(PATH).ok().map(|s| s.trim().to_string());
        if let Some(prev) = &previous
            && prev != "0"
            && fs::write(PATH, "0\n").is_ok()
        {
            return Self {
                previous: Some(prev.clone()),
            };
        }
        Self { previous: None }
    }
}

impl Drop for KptrRestrictGuard {
    fn drop(&mut self) {
        if let Some(prev) = &self.previous {
            let _ = fs::write("/proc/sys/kernel/kptr_restrict", format!("{prev}\n"));
        }
    }
}

fn is_susfs_active() -> bool {
    api::version().is_ok()
}

fn match_release_to_kmi(rel: &str, available: &[String]) -> Option<String> {
    // Pass 1: exact GKI release match "<kver>.*-<os>-*"
    for kmi in available {
        if let Some((os, kver)) = kmi.split_once('-') {
            let prefix = format!("{kver}.");
            let needle = format!("-{os}-");
            if rel.starts_with(&prefix) && rel.contains(&needle) {
                return Some(kmi.clone());
            }
        }
    }

    // If an android tag is present in uname release, do not guess across mismatched Android versions.
    if rel.contains("android") {
        return None;
    }

    // Pass 2: tag-less release, match by kernel version only when unique.
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

fn resolve_kmi(override_kmi: &str, available: &[String]) -> Result<(String, &'static str)> {
    let trimmed = override_kmi.trim();
    if !trimmed.is_empty() {
        if available.iter().any(|k| k == trimmed) {
            return Ok((trimmed.to_string(), "custom"));
        }
        warn!("kmi_override '{trimmed}' is not embedded; falling back to auto detection");
    }

    let uname = rustix::system::uname();
    let rel = uname.release().to_string_lossy();
    if let Some(kmi) = match_release_to_kmi(&rel, available) {
        return Ok((kmi, "auto"));
    }

    if let Ok(kmi) = crate::boot_patch::get_current_kmi()
        && available.iter().any(|k| k == &kmi)
    {
        return Ok((kmi, "ksud"));
    }

    bail!("No matching embedded SUSFS LKM for kernel '{rel}'")
}

fn write_last_error(msg: &str) {
    let _ = create_dir_all(defs::SUSFS_LKM_DIR);
    if fs::write(defs::SUSFS_LKM_LAST_ERROR, format!("{msg}\n")).is_ok() {
        let _ = set_permissions(defs::SUSFS_LKM_LAST_ERROR, Permissions::from_mode(0o600));
    }
}

fn clear_last_error() {
    let _ = fs::remove_file(defs::SUSFS_LKM_LAST_ERROR);
}

fn validate_param_value(name: &str, value: &str) -> Result<()> {
    if value.bytes().any(|b| b.is_ascii_whitespace() || b == b'\0') {
        bail!("Invalid whitespace in SUSFS LKM parameter '{name}'");
    }
    Ok(())
}

pub fn apply_persisted_susfs_config() {
    let config = Config::read_or_default();
    if !config.is_enabled() {
        return;
    }
    let _ = config.apply_avc_log_spoofing();
    let _ = config.init_sus_kstat();
    let _ = config.apply_logging();
    let _ = config.apply_hide_sus_mnts_for_non_su_procs();
    let _ = config.apply_uname();
    let _ = config.apply_cmdline_or_bootconfig();
    let _ = config.final_sus_kstat();
    let _ = config.apply_sus_map();
    let _ = config.apply_priority_uid_filter();
    let _ = config.apply_open_redirect();
    let _ = config.apply_sus_path_uid_filter();
    let _ = config.apply_sus_path();
}

pub fn detect_backend() -> SusfsLkmStatus {
    let config = SusfsLkmConfig::load_or_default();
    let available_kmis = assets::list_supported_susfs_kmi();
    let detected_kmi = resolve_kmi("", &available_kmis)
        .map(|(k, _)| k)
        .unwrap_or_default();
    let version = api::version().unwrap_or_default();
    let variant = api::variant().unwrap_or_default();
    let enabled_features = api::enabled_features().unwrap_or_default();
    let sysfs_present = Path::new("/sys/module/susfs_guard_lkm").exists();
    let marker_kmi = fs::read_to_string(defs::SUSFS_LKM_MARKER)
        .ok()
        .map(|s| s.trim().to_string())
        .unwrap_or_default();

    let (source, active_kmi, lkm_loaded) = if !version.is_empty() {
        let is_lkm = sysfs_present
            || enabled_features
                .lines()
                .any(|line| line.trim() == "SUSFS_LKM_MODULES");
        if is_lkm {
            if !marker_kmi.is_empty() || Path::new(defs::SUSFS_LKM_MARKER).exists() {
                (
                    "uranxsu_lkm".to_string(),
                    if marker_kmi.is_empty() {
                        detected_kmi.clone()
                    } else {
                        marker_kmi
                    },
                    true,
                )
            } else {
                ("external_lkm".to_string(), detected_kmi.clone(), true)
            }
        } else {
            ("builtin".to_string(), String::new(), false)
        }
    } else {
        ("inactive".to_string(), String::new(), false)
    };

    let last_error = fs::read_to_string(defs::SUSFS_LKM_LAST_ERROR)
        .ok()
        .map(|s| s.trim().to_string())
        .unwrap_or_default();

    SusfsLkmStatus {
        source,
        version,
        variant,
        active_kmi,
        detected_kmi,
        available_kmis,
        lkm_loaded,
        config,
        last_error,
    }
}

pub fn load_now(kmi_override: Option<&str>, apply_config: bool) -> Result<()> {
    if utils::is_safe_mode() {
        bail!("Safe mode is active; refusing to load SUSFS LKM");
    }

    if is_susfs_active() || Path::new("/sys/module/susfs_guard_lkm").exists() {
        info!("SUSFS is already active; skipping built-in SUSFS LKM load");
        clear_last_error();
        let _ = assets::reconcile_susfs_link();
        return Ok(());
    }

    let config = SusfsLkmConfig::load_or_default();
    let available = assets::list_supported_susfs_kmi();
    if available.is_empty() {
        let msg = "No embedded SUSFS LKM modules available for this architecture";
        write_last_error(msg);
        bail!("{msg}");
    }

    let effective_override = kmi_override.unwrap_or(&config.kmi_override);
    let (kmi, mode) = match resolve_kmi(effective_override, &available) {
        Ok(res) => res,
        Err(e) => {
            write_last_error(&format!("{e:#}"));
            return Err(e);
        }
    };

    let insmod_bytes = match assets::get_asset("susfs_insmod") {
        Ok(bytes) => bytes,
        Err(e) => {
            let msg = format!("Embedded susfs_insmod binary not available: {e:#}");
            write_last_error(&msg);
            bail!("{msg}");
        }
    };
    let ko_asset_name = format!("{kmi}_susfs.ko");
    let ko_bytes = match assets::get_asset(&ko_asset_name) {
        Ok(bytes) => bytes,
        Err(e) => {
            let msg = format!("Embedded SUSFS module '{ko_asset_name}' missing: {e:#}");
            write_last_error(&msg);
            bail!("{msg}");
        }
    };

    create_dir_all(defs::SUSFS_LKM_DIR).context("create SUSFS LKM runtime directory")?;
    let _ = set_permissions(defs::SUSFS_LKM_DIR, Permissions::from_mode(0o700));

    let insmod_path = Path::new(defs::SUSFS_LKM_DIR).join("susfs_insmod");
    let ko_path = Path::new(defs::SUSFS_LKM_DIR).join("susfs_guard_lkm.ko");

    let _ = fs::remove_file(&insmod_path);
    fs::write(&insmod_path, &insmod_bytes).context("write susfs_insmod")?;
    set_permissions(&insmod_path, Permissions::from_mode(0o700))?;

    let _ = fs::remove_file(&ko_path);
    fs::write(&ko_path, &ko_bytes).context("write susfs_guard_lkm.ko")?;
    set_permissions(&ko_path, Permissions::from_mode(0o600))?;

    let mut params = vec![format!(
        "expose_proc={}",
        if config.expose_proc { "1" } else { "0" }
    )];
    let su_ctx = config.su_ctx.trim();
    if !su_ctx.is_empty() {
        validate_param_value("su_ctx", su_ctx)?;
        params.push(format!("su_ctx={su_ctx}"));
    }
    let avc_su_ctx = config.avc_su_ctx.trim();
    if !avc_su_ctx.is_empty() {
        validate_param_value("avc_su_ctx", avc_su_ctx)?;
        params.push(format!("avc_su_ctx={avc_su_ctx}"));
    }

    let mut log_file = OpenOptions::new()
        .create(true)
        .write(true)
        .truncate(true)
        .open(defs::SUSFS_LKM_LOG)
        .context("open SUSFS LKM load log")?;
    let _ = set_permissions(defs::SUSFS_LKM_LOG, Permissions::from_mode(0o600));

    let uname = rustix::system::uname();
    let rel = uname.release().to_string_lossy();
    let _ = writeln!(log_file, "UranXSU built-in SUSFS LKM loader");
    let _ = writeln!(log_file, "kernel: {rel}");
    let _ = writeln!(log_file, "kmi: {kmi} ({mode})");
    let _ = writeln!(log_file, "params: {}", params.join(" "));
    let _ = log_file.flush();

    let stdout_file: File = log_file.try_clone().context("clone log file for stdout")?;
    let stderr_file: File = log_file.try_clone().context("clone log file for stderr")?;

    let _kptr_guard = KptrRestrictGuard::allow_kallsyms();

    let mut cmd = Command::new(&insmod_path);
    cmd.arg("-v")
        .arg(&ko_path)
        .args(&params)
        .stdout(Stdio::from(stdout_file))
        .stderr(Stdio::from(stderr_file));

    let mut child = cmd.spawn().context("spawn susfs_insmod")?;
    let deadline = Instant::now() + LOAD_TIMEOUT;
    let status = loop {
        if let Some(status) = child.try_wait().context("poll susfs_insmod")? {
            break Some(status);
        }
        if Instant::now() >= deadline {
            let _ = child.kill();
            let _ = child.wait();
            break None;
        }
        thread::sleep(POLL_INTERVAL);
    };

    let _ = fs::remove_file(&ko_path);

    match status {
        Some(s) if s.success() => {
            let _ = writeln!(log_file, "status: loaded ({kmi})");
            fs::write(defs::SUSFS_LKM_MARKER, format!("{kmi}\n"))
                .context("write SUSFS LKM marker")?;
            let _ = set_permissions(defs::SUSFS_LKM_MARKER, Permissions::from_mode(0o600));
            crate::android::init_event::write_component_marker(
                defs::SUSFS_LOADED_HASH_PATH,
                defs::SUSFS_HASH,
                &format!("{kmi}:{mode}"),
            );
            clear_last_error();
            let _ = assets::reconcile_susfs_link();
            if apply_config {
                apply_persisted_susfs_config();
            }
            info!("Built-in SUSFS LKM loaded successfully ({kmi}, {mode})");
            Ok(())
        }
        Some(s) => {
            let code = s.code().unwrap_or(-1);
            let msg = format!("susfs_insmod exited with status {code} for {kmi}");
            let _ = writeln!(log_file, "status: failed ({msg})");
            write_last_error(&msg);
            bail!("{msg}");
        }
        None => {
            let msg = format!(
                "susfs_insmod timed out after {}s for {kmi}",
                LOAD_TIMEOUT.as_secs()
            );
            let _ = writeln!(log_file, "status: timeout ({msg})");
            write_last_error(&msg);
            bail!("{msg}");
        }
    }
}

fn ensure_lkm_loaded(stage: &str) {
    if utils::is_safe_mode() {
        warn!("Safe mode active; skip SUSFS LKM auto-load at {stage}");
        return;
    }

    let config = SusfsLkmConfig::load_or_default();
    if !config.auto_load {
        info!("Built-in SUSFS LKM auto-load disabled; skipping at {stage}");
        return;
    }

    if stage == "post-fs-data" && !config.early_load {
        info!("Built-in SUSFS LKM early-load disabled; deferring to boot-completed");
        return;
    }

    if is_susfs_active() {
        info!("SUSFS already active at {stage}; skipping built-in SUSFS LKM load");
        return;
    }

    if let Err(e) = load_now(None, false) {
        warn!("Built-in SUSFS LKM load at {stage} failed: {e:#}");
    }
}

pub fn on_post_fs_data() {
    ensure_lkm_loaded("post-fs-data");
}

pub fn on_boot_completed() {
    ensure_lkm_loaded("boot-completed");
}

pub fn on_late_load() {
    ensure_lkm_loaded("late-load");
}

pub fn run_cli(command: LkmCommand) -> Result<()> {
    match command {
        LkmCommand::Status => {
            let status = detect_backend();
            println!("{}", serde_json::to_string_pretty(&status)?);
        }
        LkmCommand::Load { kmi, apply_config } => {
            load_now(kmi.as_deref(), apply_config)?;
            println!("SUSFS LKM loaded successfully");
        }
        LkmCommand::SetConfig {
            auto_load,
            early_load,
            kmi_override,
            expose_proc,
            su_ctx,
            avc_su_ctx,
        } => {
            let mut config = SusfsLkmConfig::load_or_default();
            if let Some(v) = auto_load {
                config.auto_load = v;
            }
            if let Some(v) = early_load {
                config.early_load = v;
            }
            if let Some(v) = kmi_override {
                config.kmi_override = v.trim().to_string();
            }
            if let Some(v) = expose_proc {
                config.expose_proc = v;
            }
            if let Some(v) = su_ctx {
                validate_param_value("su_ctx", v.trim())?;
                config.su_ctx = v.trim().to_string();
            }
            if let Some(v) = avc_su_ctx {
                validate_param_value("avc_su_ctx", v.trim())?;
                config.avc_su_ctx = v.trim().to_string();
            }
            config.save()?;
            println!("{}", serde_json::to_string_pretty(&config)?);
        }
        LkmCommand::Log => {
            if let Ok(content) = fs::read_to_string(defs::SUSFS_LKM_LOG) {
                print!("{content}");
            }
        }
    }
    Ok(())
}
