//! Zygisk module ABI.
//!
//! The module registers API v4 first, which provides the dev/inode based PLT
//! hook table and `hookJniNativeMethods`, and falls back to v3 where the host
//! only implements the older table.
const jni = @import("jni.zig");

pub const API_VERSION: c_long = 4;

pub const AppSpecializeArgs = extern struct {
    uid: *jni.jint,
    gid: *jni.jint,
    gids: *jni.jobject,
    runtime_flags: *jni.jint,
    rlimits: *jni.jobject,
    mount_external: *jni.jint,
    se_info: *jni.jstring,
    nice_name: *jni.jstring,
    instruction_set: *jni.jstring,
    app_data_dir: *jni.jstring,

    fds_to_ignore: ?*jni.jobject,
    is_child_zygote: ?*jni.jboolean,
    is_top_app: ?*jni.jboolean,
    pkg_data_info_list: ?*jni.jobject,
    whitelisted_data_info_list: ?*jni.jobject,
    mount_data_dirs: ?*jni.jboolean,
    mount_storage_dirs: ?*jni.jboolean,
};

pub const ServerSpecializeArgs = extern struct {
    uid: *jni.jint,
    gid: *jni.jint,
    gids: *jni.jobject,
    runtime_flags: *jni.jint,
    permitted_capabilities: *jni.jlong,
    effective_capabilities: *jni.jlong,
};

pub const ModuleAbi = extern struct {
    api_version: c_long,
    impl: ?*anyopaque,
    pre_app_specialize: *const fn (?*anyopaque, *AppSpecializeArgs) callconv(.c) void,
    post_app_specialize: *const fn (?*anyopaque, *const AppSpecializeArgs) callconv(.c) void,
    pre_server_specialize: *const fn (?*anyopaque, *ServerSpecializeArgs) callconv(.c) void,
    post_server_specialize: *const fn (?*anyopaque, *const ServerSpecializeArgs) callconv(.c) void,
};

pub const ApiTable = extern struct {
    impl: ?*anyopaque,
    register_module: *const fn (*ApiTable, *ModuleAbi) callconv(.c) bool,

    hook_jni_native_methods: ?*const fn (*jni.JNIEnv, [*:0]const u8, [*]jni.JNINativeMethod, c_int) callconv(.c) void,
    plt_hook_register: ?*const fn (usize, usize, [*:0]const u8, ?*anyopaque, ?*?*anyopaque) callconv(.c) void,
    exempt_fd: ?*const fn (c_int) callconv(.c) bool,
    plt_hook_commit: ?*const fn () callconv(.c) bool,
    connect_companion: ?*const fn (?*anyopaque) callconv(.c) c_int,
    set_option: ?*const fn (?*anyopaque, c_int) callconv(.c) void,
    get_module_dir: ?*const fn (?*anyopaque) callconv(.c) c_int,
    get_flags: ?*const fn (?*anyopaque) callconv(.c) u32,
};

pub const OPTION_DLCLOSE: c_int = 1;
