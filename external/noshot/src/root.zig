//! NoShot Zygisk: per-app FLAG_SECURE remover.
//!
//! Native side: Zygisk entry points, per-process gating, JNI hooks and a root
//! companion. Selected apps are handled inside system_server (no code change in
//! the app at all); the in-process binder hook remains for the apps that opt
//! into it.

const std = @import("std");
const linux = std.os.linux;

const aux = @import("auxv.zig");
const api = @import("api.zig");
const jni = @import("jni.zig");
const cfg = @import("config.zig");
const fs = @import("fs.zig");
const log = @import("log.zig");
const parcel = @import("parcel.zig");
const surface = @import("surface.zig");
const inotify = @import("inotify.zig");
const system = @import("system.zig");

const COMPANION_CONF = "CONF";
const COMPANION_SETTINGS = "SETTINGS";

var g_table: ?*api.ApiTable = null;
var g_env: ?*jni.JNIEnv = null;

var g_settings: cfg.Settings = .{};
var g_is_target: bool = false;
var g_app_mode: cfg.Mode = .system;
var g_client_hooks: bool = true;
/// True only when the host accepted API v4 (dev/inode PLT table).
var g_plt_available: bool = false;

const MAX_PROCESS = 256;
var g_process: [MAX_PROCESS]u8 = undefined;
var g_process_len: usize = 0;

/// Hosts reject a module whose `impl` is null (Magisk's `valid()` checks it),
/// silently: registration succeeds and the specialize callbacks never run.
var module_impl: u8 = 0;

var module_abi_v4: api.ModuleAbi = .{
    .api_version = 4,
    .impl = @ptrCast(&module_impl),
    .pre_app_specialize = &preAppSpecialize,
    .post_app_specialize = &postAppSpecialize,
    .pre_server_specialize = &preServerSpecialize,
    .post_server_specialize = &postServerSpecialize,
};

// Hosts that only implement Zygisk API v3 get the JNI half of the module
// but not the dev/inode PLT guard.
var module_abi_v3: api.ModuleAbi = .{
    .api_version = 3,
    .impl = @ptrCast(&module_impl),
    .pre_app_specialize = &preAppSpecialize,
    .post_app_specialize = &postAppSpecialize,
    .pre_server_specialize = &preServerSpecialize,
    .post_server_specialize = &postServerSpecialize,
};

// Satisfies std' getauxval reference locally so the module imports nothing.
comptime {
    @export(&aux.impl, .{ .name = "getauxval", .linkage = .strong, .visibility = .hidden });
}

// ---------------------------------------------------------------------------
// Zygisk entry points
// ---------------------------------------------------------------------------

export fn zygisk_module_entry(table: *api.ApiTable, env: *jni.JNIEnv) callconv(.c) void {
    g_table = table;
    g_env = env;

    if (table.register_module(table, &module_abi_v4)) {
        g_plt_available = true;
        log.info("module loaded pid={d} api=4", .{linux.getpid()});
        return;
    }
    if (table.register_module(table, &module_abi_v3)) {
        g_plt_available = false;
        log.warn("module loaded pid={d} api=3 (inotify PLT guard unavailable)", .{linux.getpid()});
        return;
    }
    log.err("register_module rejected (need Zygisk API v3 or v4)", .{});
}

export fn zygisk_companion_entry(client: c_int) callconv(.c) void {
    const fd: linux.fd_t = client;
    var lenbuf: [4]u8 = undefined;
    if (fs.readSome(fd, &lenbuf) != 4) return;
    const len = std.mem.readInt(u32, &lenbuf, .little);
    if (len == 0 or len > 64) return;

    var reqbuf: [64]u8 = undefined;
    if (fs.readSome(fd, reqbuf[0..len]) != len) return;
    const req = reqbuf[0..len];

    var reply: []const u8 = "";
    var owned: ?[]u8 = null;
    defer if (owned) |o| std.heap.page_allocator.free(o);

    if (std.mem.eql(u8, req, COMPANION_CONF)) {
        if (loadValidConfigText(std.heap.page_allocator)) |t| {
            owned = t;
            reply = t;
        }
    } else if (std.mem.eql(u8, req, COMPANION_SETTINGS)) {
        if (loadNewestText(std.heap.page_allocator, &cfg.settings_paths)) |t| {
            owned = t;
            reply = t;
        }
    } else if (std.mem.eql(u8, req, "PING")) {
        reply = "PONG";
    }

    std.mem.writeInt(u32, &lenbuf, @intCast(reply.len), .little);
    if (!fs.writeAllFd(fd, &lenbuf)) return;
    if (reply.len > 0) _ = fs.writeAllFd(fd, reply);
}

// ---------------------------------------------------------------------------
// Specialization
// ---------------------------------------------------------------------------

fn preAppSpecialize(_: ?*anyopaque, args: *api.AppSpecializeArgs) callconv(.c) void {
    const env = g_env orelse return;
    const table = g_table orelse return;

    var name_buf: [MAX_PROCESS]u8 = undefined;
    var pkg: []const u8 = "";
    if (args.nice_name.* != null) {
        if (jni.copyJavaString(env, args.nice_name.*, &name_buf)) |s| {
            pkg = cfg.packageOf(s);
        }
    }
    if (pkg.len == 0 or pkg.len >= g_process.len) {
        log.debug("unnamed process, skipped", .{});
        return;
    }
    @memcpy(g_process[0..pkg.len], pkg);
    g_process_len = pkg.len;

    const uid: i32 = args.uid.*;

    var arena = std.heap.ArenaAllocator.init(std.heap.page_allocator);
    defer arena.deinit();
    const alloc = arena.allocator();

    g_settings = loadSettings(alloc);
    log.setEnabled(g_settings.log);

    const targets = loadTargets(alloc) orelse &.{};
    g_is_target = cfg.matches(targets, pkg, uid);
    g_app_mode = cfg.modeOf(targets, pkg, uid) orelse .system;
    // The app's credentials are still privileged here; postAppSpecialize can no
    // longer read /data/adb, so the decision is taken now.
    g_client_hooks = clientHooksNeeded();

    log.info("pre proc={s} uid={d} target={d} mode={s} client={d} cfg=(flag={d} surface={d} detect={d} system={d} log={d})", .{
        pkg,
        uid,
        @intFromBool(g_is_target),
        @tagName(g_app_mode),
        @intFromBool(g_client_hooks),
        @intFromBool(g_settings.flag_secure),
        @intFromBool(g_settings.surface_secure),
        @intFromBool(g_settings.detect_guard),
        @intFromBool(g_settings.system_hook),
        @intFromBool(g_settings.log),
    });

    if (!g_is_target) {
        if (table.set_option) |set| set(table.impl, api.OPTION_DLCLOSE);
    }
}

fn postAppSpecialize(_: ?*anyopaque, _: *const api.AppSpecializeArgs) callconv(.c) void {
    const table = g_table orelse return;
    const env = g_env orelse return;
    if (!g_is_target) return;

    if (!g_client_hooks) {
        log.info("post proc={s} handled by system_server", .{g_process[0..g_process_len]});
        return;
    }

    var hooks: usize = 0;
    if (g_settings.flag_secure) {
        if (parcel.install(table, env)) hooks += 1;
    }

    var arena = std.heap.ArenaAllocator.init(std.heap.page_allocator);
    defer arena.deinit();
    const alloc = arena.allocator();

    if (g_settings.surface_secure) {
        if (surface.install(table, env)) hooks += 1;
    }

    if (g_settings.detect_guard and g_plt_available) {
        if (inotify.install(table, alloc)) {
            hooks += 1;
            if (table.plt_hook_commit) |commit| {
                if (!commit()) log.warn("pltHookCommit failed", .{});
            }
        }
    }

    log.info("post proc={s} hooks={d}", .{ g_process[0..g_process_len], hooks });
}

fn preServerSpecialize(_: ?*anyopaque, _: *api.ServerSpecializeArgs) callconv(.c) void {}

fn postServerSpecialize(_: ?*anyopaque, _: *const api.ServerSpecializeArgs) callconv(.c) void {
    const table = g_table orelse return;
    const env = g_env orelse return;
    _ = system.install(table, env);
}

/// `pkg|client` apps, and every app when the system side is unavailable, keep
/// the in-process hook. The system side publishes its state to
/// `/data/adb/noshot_zygisk/mode` through the root scripts.
fn clientHooksNeeded() bool {
    if (g_app_mode == .client) return true;
    if (!g_settings.system_hook) {
        log.warn("system_hook=0, falling back to the in-process hook", .{});
        return true;
    }
    var buf: [16]u8 = undefined;
    for (cfg.app_mode_paths) |path| {
        const text = fs.readFixed(path, &buf) orelse continue;
        if (std.mem.eql(u8, std.mem.trim(u8, text, " \t\r\n"), "system")) return false;
        break;
    }
    log.warn("system_server side inactive or unknown, falling back to the in-process hook", .{});
    return true;
}

// ---------------------------------------------------------------------------
// Configuration loading
// ---------------------------------------------------------------------------

/// First non-empty settings file (mtime is only used between candidates).
fn loadNewestText(alloc: std.mem.Allocator, paths: []const []const u8) ?[]u8 {
    var best: ?[]u8 = null;
    var best_mtime: i128 = std.math.minInt(i128);
    for (paths) |p| {
        const text = (fs.readAlloc(alloc, p, cfg.max_config_bytes) catch continue) orelse continue;
        if (text.len == 0) {
            alloc.free(text);
            continue;
        }
        const mt = fs.mtimeNanos(p) orelse 0;
        if (best == null or mt > best_mtime) {
            if (best) |old| alloc.free(old);
            best = text;
            best_mtime = mt;
        } else {
            alloc.free(text);
        }
    }
    return best;
}

/// Newest file that parses as a complete `allow.list`. Used by the companion,
/// which hands the whole text back to the injected process.
fn loadValidConfigText(alloc: std.mem.Allocator) ?[]u8 {
    var best: ?[]u8 = null;
    var best_mtime: i128 = std.math.minInt(i128);
    for (cfg.target_paths) |p| {
        const text = (fs.readAlloc(alloc, p, cfg.max_config_bytes) catch continue) orelse continue;
        const r = cfg.parseTargets(alloc, text) catch {
            alloc.free(text);
            continue;
        };
        alloc.free(r.targets);
        if (!r.valid) {
            alloc.free(text);
            continue;
        }
        const mt = fs.mtimeNanos(p) orelse 0;
        if (best == null or mt > best_mtime) {
            if (best) |old| alloc.free(old);
            best = text;
            best_mtime = mt;
        } else {
            alloc.free(text);
        }
    }
    return best;
}

fn loadTargets(alloc: std.mem.Allocator) ?[]cfg.Target {
    var best: ?[]cfg.Target = null;
    var best_mtime: i128 = std.math.minInt(i128);
    var best_path: []const u8 = "";

    for (cfg.target_paths) |p| {
        const text = (fs.readAlloc(alloc, p, cfg.max_config_bytes) catch continue) orelse continue;
        const r = cfg.parseTargets(alloc, text) catch continue;
        if (!r.valid) {
            log.debug("config ignored (invalid/truncated): {s}", .{p});
            continue;
        }
        const mt = fs.mtimeNanos(p) orelse 0;
        if (best == null or mt > best_mtime) {
            best = r.targets;
            best_mtime = mt;
            best_path = p;
        }
    }
    if (best) |t| {
        log.debug("config: {s} ({d} entries)", .{ best_path, t.len });
        return t;
    }

    // Root companion fallback: some ROMs deny zygote access to /data/adb.
    if (readCompanion(alloc, COMPANION_CONF)) |text| {
        const r = cfg.parseTargets(alloc, text) catch return null;
        if (r.valid) {
            log.info("config via root companion ({d} entries)", .{r.targets.len});
            return r.targets;
        }
    }
    return null;
}

fn loadSettings(alloc: std.mem.Allocator) cfg.Settings {
    for (cfg.settings_paths) |p| {
        const text = (fs.readAlloc(alloc, p, 1 << 16) catch continue) orelse continue;
        if (text.len > 0) return cfg.parseSettings(text);
    }
    if (readCompanion(alloc, COMPANION_SETTINGS)) |text| {
        return cfg.parseSettings(text);
    }
    return .{};
}

fn readCompanion(alloc: std.mem.Allocator, request: []const u8) ?[]u8 {
    const table = g_table orelse return null;
    const connect = table.connect_companion orelse {
        log.warn("companion {s}: no connect_companion", .{request});
        return null;
    };
    const fd = connect(table.impl);
    if (fd < 0) {
        log.warn("companion {s}: connect failed ({d})", .{ request, fd });
        return null;
    }
    defer fs.close(@intCast(fd));

    var lenbuf: [4]u8 = undefined;
    std.mem.writeInt(u32, &lenbuf, @intCast(request.len), .little);
    if (!fs.writeAllFd(@intCast(fd), &lenbuf)) return null;
    if (!fs.writeAllFd(@intCast(fd), request)) return null;

    if (fs.readSome(@intCast(fd), &lenbuf) != 4) {
        log.warn("companion {s}: no reply length", .{request});
        return null;
    }
    const len = std.mem.readInt(u32, &lenbuf, .little);
    if (len == 0 or len > cfg.max_config_bytes) {
        log.warn("companion {s}: reply length {d}", .{ request, len });
        return null;
    }

    const buf = alloc.alloc(u8, len) catch return null;
    if (fs.readSome(@intCast(fd), buf) != len) return null;
    return buf;
}
