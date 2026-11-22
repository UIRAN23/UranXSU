use anyhow::{Result, bail};
use clap::{Args, Subcommand};

use crate::android::mount::{
    config::{MountConfig, MountMode},
    get_overall_status, nomount,
};

#[derive(Debug, Args)]
pub struct MountArgs {
    #[command(subcommand)]
    pub command: MountCommand,
}

#[derive(Debug, Subcommand)]
pub enum MountCommand {
    /// Print overall built-in mount engine status as JSON.
    Status,

    /// Switch active mount mode (no_mount, metamodule, disabled).
    SetMode { mode: String },

    /// Manage built-in original NoMount v2.3.1 engine.
    Nomount {
        #[command(subcommand)]
        command: NoMountCommand,
    },
}

#[derive(Debug, Subcommand)]
pub enum NoMountCommand {
    Status,
    LoadLkm {
        #[arg(long)]
        kmi: Option<String>,
    },
    MountAll,
    ReloadAll,
    ClearRules,
    ClearAll,
    ModuleLoad {
        id: String,
    },
    ModuleUnload {
        id: String,
    },
    Rules,
    UidAdd {
        uid: u32,
        #[arg(long)]
        pkg: Option<String>,
    },
    UidDel {
        uid: u32,
    },
    SetConfig {
        #[arg(long)]
        auto_load_lkm: Option<bool>,
        #[arg(long)]
        kmi_override: Option<String>,
        #[arg(long)]
        block_isolated_uids: Option<bool>,
        #[arg(long)]
        dump_rules_on_boot: Option<bool>,
        #[arg(long)]
        bootloop_guard: Option<bool>,
        #[arg(long)]
        clear_bootloop_disable: bool,
    },
}

pub fn run_main(args: MountArgs) -> Result<()> {
    match args.command {
        MountCommand::Status => {
            let status = get_overall_status();
            println!("{}", serde_json::to_string_pretty(&status)?);
        }
        MountCommand::SetMode { mode } => {
            let Some(parsed) = MountMode::parse(&mode) else {
                bail!("Invalid mount mode '{mode}'; expected no_mount, metamodule, or disabled");
            };
            let mut cfg = MountConfig::load_or_default();
            cfg.mode = parsed;
            cfg.save()?;
            nomount::reconcile_metamodule_symlink(parsed)?;
            println!("{}", serde_json::to_string_pretty(&get_overall_status())?);
        }
        MountCommand::Nomount { command } => match command {
            NoMountCommand::Status => {
                println!("{}", serde_json::to_string_pretty(&nomount::get_status())?);
            }
            NoMountCommand::LoadLkm { kmi } => {
                nomount::ensure_driver_loaded(kmi.as_deref())?;
                println!("NoMount driver loaded");
            }
            NoMountCommand::MountAll => {
                nomount::mount_all_modules()?;
            }
            NoMountCommand::ReloadAll => {
                nomount::reload_all()?;
                println!("NoMount rules reloaded");
            }
            NoMountCommand::ClearRules => {
                nomount::clear_rules()?;
                println!("NoMount rules cleared");
            }
            NoMountCommand::ClearAll => {
                nomount::clear_all()?;
                println!("NoMount rules and UIDs cleared");
            }
            NoMountCommand::ModuleLoad { id } => {
                nomount::module_load(&id)?;
                println!("Loaded module {id}");
            }
            NoMountCommand::ModuleUnload { id } => {
                nomount::module_unload(&id)?;
                println!("Unloaded module {id}");
            }
            NoMountCommand::Rules => {
                println!("{}", nomount::rules_json()?);
            }
            NoMountCommand::UidAdd { uid, pkg } => {
                nomount::uid_add(uid, pkg.as_deref())?;
                println!("Added UID {uid}");
            }
            NoMountCommand::UidDel { uid } => {
                nomount::uid_del(uid)?;
                println!("Removed UID {uid}");
            }
            NoMountCommand::SetConfig {
                auto_load_lkm,
                kmi_override,
                block_isolated_uids,
                dump_rules_on_boot,
                bootloop_guard,
                clear_bootloop_disable,
            } => {
                let updated = nomount::set_config(
                    auto_load_lkm,
                    kmi_override,
                    block_isolated_uids,
                    dump_rules_on_boot,
                    bootloop_guard,
                    clear_bootloop_disable,
                )?;
                println!("{}", serde_json::to_string_pretty(&updated)?);
            }
        },
    }
    Ok(())
}
