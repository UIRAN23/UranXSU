use std::{
    collections::{BTreeSet, HashMap},
    fs::{self, Permissions},
    os::unix::fs::PermissionsExt,
    path::Path,
    time::UNIX_EPOCH,
};

use anyhow::{Context, Result, bail};
use clap::{Args, Subcommand};
use log::{info, warn};
use serde::Serialize;

use crate::{assets, defs};

const DEFAULT_ALLOW_LIST: &str = "# noshot:2:0\n";
const DEFAULT_SETTINGS_CONF: &str = "# noshot settings v2\nflag_secure=1\nsurface_secure=1\ndetect_guard=1\nsystem_hook=1\ncapture_guard=0\nlog=1\n";

#[derive(Debug, Args)]
pub struct NoshotArgs {
    #[command(subcommand)]
    pub command: NoshotCommand,
}

#[derive(Debug, Subcommand)]
pub enum NoshotCommand {
    /// Print built-in NoShot runtime status as JSON.
    Status,

    /// Enable built-in NoShot and stage Zygisk libraries.
    Enable,

    /// Disable built-in NoShot and remove staged Zygisk libraries.
    Disable,

    /// List configured target packages as JSON.
    List,

    /// Add or update an app package in NoShot allow.list.
    Add {
        package: String,
        #[arg(long, default_value = "system")]
        mode: String,
        #[arg(long)]
        user: Option<u32>,
    },

    /// Remove an app package (or a per-user rule) from NoShot allow.list.
    Remove {
        package: String,
        #[arg(long)]
        user: Option<u32>,
    },

    /// Update NoShot global hook settings and regenerate the system_server plan.
    SetSettings {
        #[arg(long)]
        flag_secure: Option<bool>,
        #[arg(long)]
        surface_secure: Option<bool>,
        #[arg(long)]
        detect_guard: Option<bool>,
        #[arg(long)]
        system_hook: Option<bool>,
        #[arg(long)]
        capture_guard: Option<String>,
        #[arg(long)]
        log: Option<bool>,
    },

    /// Reset system_server bootloop protection counters and regenerate the plan.
    ResetGuard,

    /// Regenerate /data/system/noshot_plan from allow.list and settings.conf.
    SyncPlan,
}

#[derive(Debug, Clone, Serialize)]
pub struct NoshotTarget {
    pub package: String,
    pub user: Option<u32>,
    pub mode: String,
}

#[derive(Debug, Clone, Serialize)]
pub struct NoshotSettings {
    pub flag_secure: bool,
    pub surface_secure: bool,
    pub detect_guard: bool,
    pub system_hook: bool,
    pub capture_guard: String,
    pub log: bool,
}

impl Default for NoshotSettings {
    fn default() -> Self {
        Self {
            flag_secure: true,
            surface_secure: true,
            detect_guard: true,
            system_hook: true,
            capture_guard: "off".to_owned(),
            log: true,
        }
    }
}

#[derive(Debug, Clone, Serialize)]
pub struct NoshotStatus {
    pub enabled: bool,
    pub standalone_conflict: bool,
    pub zygisk_enabled: bool,
    pub zygisk_running: bool,
    pub loaded_in_zygisk: bool,
    pub runtime_mode: String,
    pub bootloop_disabled: bool,
    pub attempt_count: u32,
    pub disable_system: bool,
    pub capture_guard_state: String,
    pub state_log: String,
    pub settings: NoshotSettings,
    pub targets: Vec<NoshotTarget>,
}

pub fn run_main(args: NoshotArgs) -> Result<()> {
    match args.command {
        NoshotCommand::Status => {
            println!("{}", serde_json::to_string(&get_status())?);
            Ok(())
        }
        NoshotCommand::Enable => {
            enable()?;
            println!("{}", serde_json::to_string(&get_status())?);
            Ok(())
        }
        NoshotCommand::Disable => {
            disable()?;
            println!("{}", serde_json::to_string(&get_status())?);
            Ok(())
        }
        NoshotCommand::List => {
            let targets = read_targets();
            let payload = serde_json::json!({ "targets": targets });
            println!("{}", serde_json::to_string(&payload)?);
            Ok(())
        }
        NoshotCommand::Add {
            package,
            mode,
            user,
        } => {
            add_target(&package, user, &mode)?;
            let payload = serde_json::json!({ "targets": read_targets() });
            println!("{}", serde_json::to_string(&payload)?);
            Ok(())
        }
        NoshotCommand::Remove { package, user } => {
            remove_target(&package, user)?;
            let payload = serde_json::json!({ "targets": read_targets() });
            println!("{}", serde_json::to_string(&payload)?);
            Ok(())
        }
        NoshotCommand::SetSettings {
            flag_secure,
            surface_secure,
            detect_guard,
            system_hook,
            capture_guard,
            log,
        } => {
            update_settings(
                flag_secure,
                surface_secure,
                detect_guard,
                system_hook,
                capture_guard.as_deref(),
                log,
            )?;
            println!("{}", serde_json::to_string(&read_settings())?);
            Ok(())
        }
        NoshotCommand::ResetGuard => {
            reset_guard()?;
            println!("{}", serde_json::to_string(&get_status())?);
            Ok(())
        }
        NoshotCommand::SyncPlan => {
            generate_plan()?;
            println!("{}", serde_json::to_string(&get_status())?);
            Ok(())
        }
    }
}

pub fn is_enabled() -> bool {
    Path::new(defs::NOSHOT_ENABLED_FLAG).exists()
}

pub fn standalone_active() -> bool {
    let dir = Path::new(defs::NOSHOT_STANDALONE_DIR);
    dir.is_dir() && !dir.join("disable").exists() && !dir.join("remove").exists()
}

pub fn on_post_fs_data() {
    if standalone_active() {
        info!("Standalone noshot_zygisk module is active; skipping built-in NoShot");
        let _ = remove_staged_libs();
        return;
    }

    if !is_enabled() {
        let _ = remove_staged_libs();
        return;
    }

    info!("Initializing built-in NoShot in post-fs-data");
    if let Err(e) = ensure_config_files() {
        warn!("NoShot ensure_config_files failed: {e}");
    }
    if let Err(e) = stage_zygisk_libs() {
        warn!("NoShot stage_zygisk_libs failed: {e}");
    }

    // Reset boot-scoped state files until system_server runs again.
    let _ = fs::remove_file(defs::NOSHOT_SYSTEM_STATE);
    let _ = fs::remove_file(defs::NOSHOT_SYSTEM_GUARD);
    let _ = write_file_mode(defs::NOSHOT_SYSTEM_MODE, "client\n", 0o644);

    if let Err(e) = generate_plan() {
        warn!("NoShot generate_plan in post-fs-data failed: {e}");
    }
}

pub fn on_boot_completed() {
    if standalone_active() || !is_enabled() {
        return;
    }

    let _ = fs::remove_file(defs::NOSHOT_SYSTEM_ATTEMPT);
    if let Err(e) = generate_plan() {
        warn!("NoShot generate_plan in boot_completed failed: {e}");
    }
}

pub fn enable() -> Result<()> {
    if standalone_active() {
        bail!("Standalone noshot_zygisk module is active; disable or remove it first");
    }

    ensure_config_files()?;
    stage_zygisk_libs()?;
    write_file_mode(defs::NOSHOT_ENABLED_FLAG, "1\n", 0o644)?;
    if !Path::new(defs::NOSHOT_SYSTEM_MODE).exists() {
        let _ = write_file_mode(defs::NOSHOT_SYSTEM_MODE, "client\n", 0o644);
    }
    generate_plan()?;
    crate::android::builtin_zygisk::refresh_daemon_modules();
    Ok(())
}

pub fn disable() -> Result<()> {
    if Path::new(defs::NOSHOT_ENABLED_FLAG).exists() {
        fs::remove_file(defs::NOSHOT_ENABLED_FLAG)?;
    }
    remove_staged_libs()?;
    let _ = fs::remove_file(defs::NOSHOT_SYSTEM_PLAN);
    crate::android::builtin_zygisk::refresh_daemon_modules();
    Ok(())
}

pub fn reset_guard() -> Result<()> {
    let _ = fs::remove_file(defs::NOSHOT_SYSTEM_DISABLE);
    let _ = fs::remove_file(defs::NOSHOT_SYSTEM_ATTEMPT);
    if is_enabled() && !standalone_active() {
        generate_plan()?;
    }
    Ok(())
}

pub fn add_target(package: &str, user: Option<u32>, mode: &str) -> Result<()> {
    let pkg = package.trim();
    if !is_valid_package_name(pkg) {
        bail!("Invalid Android package name: {package}");
    }
    let normalized_mode = match mode.trim().to_ascii_lowercase().as_str() {
        "system" => "system".to_owned(),
        "client" => "client".to_owned(),
        other => bail!("Invalid NoShot mode '{other}'; expected system or client"),
    };

    ensure_config_files()?;
    let mut targets = read_targets();
    if let Some(existing) = targets
        .iter_mut()
        .find(|t| t.package == pkg && t.user == user)
    {
        existing.mode = normalized_mode;
    } else {
        targets.push(NoshotTarget {
            package: pkg.to_owned(),
            user,
            mode: normalized_mode,
        });
    }
    targets.sort_by(|a, b| a.package.cmp(&b.package).then_with(|| a.user.cmp(&b.user)));
    write_targets(&targets)?;
    if is_enabled() && !standalone_active() {
        generate_plan()?;
    }
    Ok(())
}

pub fn remove_target(package: &str, user: Option<u32>) -> Result<()> {
    let pkg = package.trim();
    ensure_config_files()?;
    let mut targets = read_targets();
    targets.retain(|t| !(t.package == pkg && (user.is_none() || t.user == user)));
    write_targets(&targets)?;
    if is_enabled() && !standalone_active() {
        generate_plan()?;
    }
    Ok(())
}

pub fn update_settings(
    flag_secure: Option<bool>,
    surface_secure: Option<bool>,
    detect_guard: Option<bool>,
    system_hook: Option<bool>,
    capture_guard: Option<&str>,
    log: Option<bool>,
) -> Result<()> {
    ensure_config_files()?;
    let mut current = read_settings();
    if let Some(v) = flag_secure {
        current.flag_secure = v;
    }
    if let Some(v) = surface_secure {
        current.surface_secure = v;
    }
    if let Some(v) = detect_guard {
        current.detect_guard = v;
    }
    if let Some(v) = system_hook {
        current.system_hook = v;
    }
    if let Some(v) = capture_guard {
        current.capture_guard = parse_capture_guard_strict(v)?;
    }
    if let Some(v) = log {
        current.log = v;
    }
    write_settings(&current)?;
    if is_enabled() && !standalone_active() {
        generate_plan()?;
    }
    Ok(())
}

pub fn get_status() -> NoshotStatus {
    let enabled = is_enabled();
    let standalone_conflict = standalone_active();
    let zygisk_enabled = crate::android::builtin_zygisk::is_enabled();
    let zygisk_running = crate::android::builtin_zygisk::is_daemon_active();
    let loaded_in_zygisk = crate::android::builtin_zygisk::injected_modules()
        .iter()
        .any(|m| {
            (m.id == "noshot" || m.id == "noshot_zygisk")
                && (m.state == "injected" || m.state == "loaded")
        });

    let runtime_mode = fs::read_to_string(defs::NOSHOT_SYSTEM_MODE)
        .or_else(|_| fs::read_to_string(defs::NOSHOT_MODE_COPY))
        .ok()
        .map(|s| {
            if s.trim_start().starts_with("system") {
                "system".to_owned()
            } else {
                "client".to_owned()
            }
        })
        .unwrap_or_else(|| "client".to_owned());

    let bootloop_disabled = Path::new(defs::NOSHOT_SYSTEM_DISABLE).exists();
    let attempt_count = fs::read_to_string(defs::NOSHOT_SYSTEM_ATTEMPT)
        .ok()
        .and_then(|s| s.trim().parse::<u32>().ok())
        .unwrap_or(0);
    let disable_system = Path::new(defs::NOSHOT_DISABLE_SYSTEM).exists();
    let capture_guard_state = fs::read_to_string(defs::NOSHOT_SYSTEM_GUARD)
        .map(|s| s.trim().to_owned())
        .unwrap_or_else(|_| "unknown".to_owned());
    let state_log = fs::read_to_string(defs::NOSHOT_SYSTEM_STATE).unwrap_or_default();

    NoshotStatus {
        enabled,
        standalone_conflict,
        zygisk_enabled,
        zygisk_running,
        loaded_in_zygisk,
        runtime_mode,
        bootloop_disabled,
        attempt_count,
        disable_system,
        capture_guard_state,
        state_log,
        settings: read_settings(),
        targets: read_targets(),
    }
}

pub fn generate_plan() -> Result<()> {
    ensure_config_files()?;

    let settings = read_settings();
    let hook = settings.system_hook && !Path::new(defs::NOSHOT_DISABLE_SYSTEM).exists();
    let plan_gen = file_mtime_secs(defs::NOSHOT_ALLOW_LIST)
        .max(file_mtime_secs(defs::NOSHOT_SETTINGS_CONF));

    let users = collect_user_ids();
    let pkg_uids = read_package_uids();
    let targets = read_targets();

    let mut lines = Vec::new();
    lines.push(format!("hook={}", u8::from(hook)));
    lines.push(format!("log={}", u8::from(settings.log)));
    lines.push(format!("capture={}", settings.capture_guard));
    lines.push(format!("gen={plan_gen}"));

    let mut seen_pkgs = BTreeSet::new();
    let mut seen_uids = BTreeSet::new();
    for t in &targets {
        if t.mode == "client" {
            continue;
        }
        let Some(&base_uid) = pkg_uids.get(&t.package) else {
            continue;
        };
        if seen_pkgs.insert(t.package.clone()) {
            lines.push(format!("pkg={}", t.package));
        }
        if let Some(u) = t.user {
            let uid = base_uid.saturating_add(u.saturating_mul(100_000));
            if seen_uids.insert(uid) {
                lines.push(format!("uid={uid}"));
            }
        } else {
            for &u in &users {
                let uid = base_uid.saturating_add(u.saturating_mul(100_000));
                if seen_uids.insert(uid) {
                    lines.push(format!("uid={uid}"));
                }
            }
        }
    }

    let mut body = lines.join("\n");
    body.push('\n');
    write_file_mode(defs::NOSHOT_SYSTEM_PLAN, &body, 0o644)?;

    if let Ok(mode_raw) = fs::read_to_string(defs::NOSHOT_SYSTEM_MODE) {
        let side = if mode_raw.trim_start().starts_with("system") {
            "system\n"
        } else {
            "client\n"
        };
        let _ = write_file_mode(defs::NOSHOT_MODE_COPY, side, 0o644);
    }

    Ok(())
}

fn ensure_config_files() -> Result<()> {
    let dir = Path::new(defs::NOSHOT_DIR);
    if !dir.exists() {
        fs::create_dir_all(dir)?;
    }
    let _ = fs::set_permissions(dir, Permissions::from_mode(0o755));

    let allow = Path::new(defs::NOSHOT_ALLOW_LIST);
    if !allow.exists() {
        let standalone_allow = Path::new("/data/adb/noshot_zygisk/allow.list");
        if standalone_allow.is_file()
            && let Ok(existing) = fs::read_to_string(standalone_allow)
        {
            write_file_mode(defs::NOSHOT_ALLOW_LIST, &existing, 0o644)?;
        }
        if !allow.exists() {
            write_file_mode(defs::NOSHOT_ALLOW_LIST, DEFAULT_ALLOW_LIST, 0o644)?;
        }
    }

    let settings = Path::new(defs::NOSHOT_SETTINGS_CONF);
    if !settings.exists() {
        let standalone_settings = Path::new("/data/adb/noshot_zygisk/settings.conf");
        if standalone_settings.is_file()
            && let Ok(existing) = fs::read_to_string(standalone_settings)
        {
            write_file_mode(defs::NOSHOT_SETTINGS_CONF, &existing, 0o644)?;
        }
        if !settings.exists() {
            write_file_mode(defs::NOSHOT_SETTINGS_CONF, DEFAULT_SETTINGS_CONF, 0o644)?;
        }
    }

    let _ = fs::set_permissions(allow, Permissions::from_mode(0o644));
    let _ = fs::set_permissions(settings, Permissions::from_mode(0o644));
    Ok(())
}

fn stage_zygisk_libs() -> Result<()> {
    let zygisk_dir = Path::new(defs::NOSHOT_ZYGISK_DIR);
    if !zygisk_dir.exists() {
        fs::create_dir_all(zygisk_dir)?;
    }
    let _ = fs::set_permissions(zygisk_dir, Permissions::from_mode(0o755));

    #[cfg(target_arch = "aarch64")]
    let mapping: &[(&str, &str)] = &[
        ("noshot_64.so", "arm64-v8a.so"),
        ("noshot_32.so", "armeabi-v7a.so"),
    ];
    #[cfg(target_arch = "arm")]
    let mapping: &[(&str, &str)] = &[("noshot_32.so", "armeabi-v7a.so")];
    #[cfg(target_arch = "x86_64")]
    let mapping: &[(&str, &str)] = &[
        ("noshot_64.so", "x86_64.so"),
        ("noshot_32.so", "x86.so"),
    ];
    #[cfg(not(any(target_arch = "aarch64", target_arch = "arm", target_arch = "x86_64")))]
    let mapping: &[(&str, &str)] = &[];

    for (asset_name, abi_file) in mapping {
        let Ok(bytes) = assets::get_asset(asset_name) else {
            warn!("NoShot asset {asset_name} not embedded for this build");
            continue;
        };
        let dst = zygisk_dir.join(abi_file);
        if fs::read(&dst).is_ok_and(|existing| existing == bytes.as_ref()) {
            let _ = fs::set_permissions(&dst, Permissions::from_mode(0o644));
            continue;
        }
        write_bytes_mode(&dst, bytes.as_ref(), 0o644)?;
    }

    Ok(())
}

fn remove_staged_libs() -> Result<()> {
    let dir = Path::new(defs::NOSHOT_ZYGISK_DIR);
    if dir.exists() {
        fs::remove_dir_all(dir)?;
    }
    Ok(())
}

fn read_targets() -> Vec<NoshotTarget> {
    let Ok(content) = fs::read_to_string(defs::NOSHOT_ALLOW_LIST) else {
        return Vec::new();
    };
    let mut out = Vec::new();
    for raw in content.lines() {
        let line = raw.trim();
        if line.is_empty() || line.starts_with('#') {
            continue;
        }
        let mut parts = line.split('|');
        let Some(pkg) = parts.next().map(str::trim) else {
            continue;
        };
        if !is_valid_package_name(pkg) {
            continue;
        }
        let mut user = None;
        let mut mode = "system".to_owned();
        let mut valid = true;
        for part in parts {
            let token = part.trim();
            if token.is_empty() {
                continue;
            }
            if let Some(u_str) = token.strip_prefix('u') {
                if let Ok(u) = u_str.parse::<u32>() {
                    user = Some(u);
                } else {
                    valid = false;
                    break;
                }
            } else if token == "client" {
                mode = "client".to_owned();
            } else if token == "system" {
                mode = "system".to_owned();
            } else {
                valid = false;
                break;
            }
        }
        if !valid {
            continue;
        }
        if !out
            .iter()
            .any(|t: &NoshotTarget| t.package == pkg && t.user == user)
        {
            out.push(NoshotTarget {
                package: pkg.to_owned(),
                user,
                mode,
            });
        }
    }
    out
}

fn write_targets(targets: &[NoshotTarget]) -> Result<()> {
    let mut buf = format!("# noshot:2:{}\n", targets.len());
    for t in targets {
        buf.push_str(&t.package);
        if let Some(u) = t.user {
            buf.push_str(&format!("|u{u}"));
        }
        if t.mode == "client" {
            buf.push_str("|client");
        }
        buf.push('\n');
    }
    write_file_mode(defs::NOSHOT_ALLOW_LIST, &buf, 0o644)
}

fn read_settings() -> NoshotSettings {
    let mut s = NoshotSettings::default();
    let Ok(content) = fs::read_to_string(defs::NOSHOT_SETTINGS_CONF) else {
        return s;
    };
    for raw in content.lines() {
        let line = raw.trim();
        if line.is_empty() || line.starts_with('#') {
            continue;
        }
        let Some((k, v)) = line.split_once('=') else {
            continue;
        };
        let key = k.trim();
        let val = v.trim();
        match key {
            "flag_secure" => s.flag_secure = parse_bool_val(val),
            "surface_secure" => s.surface_secure = parse_bool_val(val),
            "detect_guard" => s.detect_guard = parse_bool_val(val),
            "system_hook" => s.system_hook = parse_bool_val(val),
            "capture_guard" => s.capture_guard = parse_capture_guard(val),
            "log" => s.log = parse_bool_val(val),
            _ => {}
        }
    }
    s
}

fn write_settings(s: &NoshotSettings) -> Result<()> {
    let cg = match s.capture_guard.as_str() {
        "on" => "1",
        "observe" => "observe",
        _ => "0",
    };
    let text = format!(
        "# noshot settings v2\nflag_secure={}\nsurface_secure={}\ndetect_guard={}\nsystem_hook={}\ncapture_guard={cg}\nlog={}\n",
        u8::from(s.flag_secure),
        u8::from(s.surface_secure),
        u8::from(s.detect_guard),
        u8::from(s.system_hook),
        u8::from(s.log),
    );
    write_file_mode(defs::NOSHOT_SETTINGS_CONF, &text, 0o644)
}

fn parse_bool_val(v: &str) -> bool {
    v == "1" || v.eq_ignore_ascii_case("true") || v.eq_ignore_ascii_case("on")
}

fn parse_capture_guard(v: &str) -> String {
    if v.eq_ignore_ascii_case("observe") {
        "observe".to_owned()
    } else if parse_bool_val(v) {
        "on".to_owned()
    } else {
        "off".to_owned()
    }
}

fn parse_capture_guard_strict(v: &str) -> Result<String> {
    let lower = v.trim().to_ascii_lowercase();
    match lower.as_str() {
        "0" | "off" | "false" => Ok("off".to_owned()),
        "observe" => Ok("observe".to_owned()),
        "1" | "on" | "true" => Ok("on".to_owned()),
        _ => bail!("Invalid capture_guard '{v}'; expected off, observe, or on"),
    }
}

fn is_valid_package_name(pkg: &str) -> bool {
    if pkg.is_empty() || pkg.len() > 96 {
        return false;
    }
    let mut has_dot = false;
    let mut prev_dot = true;
    for b in pkg.bytes() {
        if b == b'.' {
            if prev_dot {
                return false;
            }
            has_dot = true;
            prev_dot = true;
        } else if b.is_ascii_alphanumeric() || b == b'_' {
            prev_dot = false;
        } else {
            return false;
        }
    }
    has_dot && !prev_dot
}

fn collect_user_ids() -> Vec<u32> {
    let mut ids = BTreeSet::new();
    if let Ok(entries) = fs::read_dir("/data/user") {
        for entry in entries.flatten() {
            if let Some(name) = entry.file_name().to_str()
                && let Ok(uid) = name.parse::<u32>()
            {
                ids.insert(uid);
            }
        }
    }
    if ids.is_empty() {
        vec![0]
    } else {
        ids.into_iter().collect()
    }
}

fn read_package_uids() -> HashMap<String, u32> {
    let mut map = HashMap::new();
    let Ok(content) = fs::read_to_string("/data/system/packages.list") else {
        return map;
    };
    for line in content.lines() {
        let mut parts = line.split_whitespace();
        let Some(pkg) = parts.next() else {
            continue;
        };
        let Some(uid_str) = parts.next() else {
            continue;
        };
        if let Ok(uid) = uid_str.parse::<u32>() {
            map.entry(pkg.to_owned()).or_insert(uid);
        }
    }
    map
}

fn file_mtime_secs(path: &str) -> u64 {
    fs::metadata(path)
        .ok()
        .and_then(|m| m.modified().ok())
        .and_then(|t| t.duration_since(UNIX_EPOCH).ok())
        .map_or(0, |d| d.as_secs())
}

fn write_file_mode(path: &str, content: &str, mode: u32) -> Result<()> {
    write_bytes_mode(Path::new(path), content.as_bytes(), mode)
}

fn write_bytes_mode(path: &Path, bytes: &[u8], mode: u32) -> Result<()> {
    if let Some(parent) = path.parent()
        && !parent.exists()
    {
        fs::create_dir_all(parent)?;
    }
    let tmp = path.with_extension("tmp");
    fs::write(&tmp, bytes).with_context(|| format!("Failed to write {}", tmp.display()))?;
    fs::set_permissions(&tmp, Permissions::from_mode(mode))
        .with_context(|| format!("Failed to chmod {}", tmp.display()))?;
    fs::rename(&tmp, path).with_context(|| format!("Failed to rename {}", path.display()))?;
    Ok(())
}
