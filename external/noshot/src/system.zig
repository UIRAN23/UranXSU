//! system_server side of the module: make the SECURE flag ineffective for the
//! surfaces of selected apps without touching those apps.
//!
//! WindowManager builds the layer tree, so both places that can put SECURE on
//! a layer are JNI calls into `android.view.SurfaceControl`:
//!
//!     nativeCreate(session, name, w, h, format, flags, parent, metadata)
//!     nativeSetFlags(transaction, surface, flags, mask)
//!
//! Ownership is resolved once, when the layer is created: the metadata parcel
//! carries the owning uid (written by WindowStateAnimator from the app's
//! Session), a child inherits from its parent layer, and the layer name
//! contains the package as a last resort. Everything else is left alone.

const std = @import("std");
const linux = std.os.linux;

const api = @import("api.zig");
const cfg = @import("config.zig");
const fs = @import("fs.zig");
const jni = @import("jni.zig");
const log = @import("log.zig");
const outbound = @import("outbound.zig");
const state = @import("state.zig");

/// SurfaceControl.SECURE, used both as a builder flag and in setSecure().
const SECURE: i32 = 0x80;

/// Marker uid for a layer whose name matched a selected package.
const SEL_ALL: u32 = std.math.maxInt(u32);

const CreateFn = *const fn (
    *jni.JNIEnv,
    jni.jclass,
    jni.jobject,
    jni.jstring,
    jni.jint,
    jni.jint,
    jni.jint,
    jni.jint,
    jni.jlong,
    jni.jobject,
) callconv(.c) jni.jlong;

const SetFlagsFn = *const fn (*jni.JNIEnv, jni.jclass, jni.jlong, jni.jlong, jni.jint, jni.jint) callconv(.c) void;
/// @CriticalNative call shape: no JNIEnv, no jclass.
const SetFlagsCriticalFn = *const fn (jni.jlong, jni.jlong, jni.jint, jni.jint) callconv(.c) void;

const SetFlagsAbi = enum { jni, critical };

var set_flags_abi: SetFlagsAbi = .jni;
var probe_args: [4]i64 = @splat(0);
var probe_seen: bool = false;

const MAP_CAP = 8192;
const CHECK_SPAN_NS: i128 = 2 * std.time.ns_per_s;
const RX_MAX = 16 * 1024;

var orig_create: ?*anyopaque = null;
var orig_set_flags: ?*anyopaque = null;
var installed: bool = false;

var conf: cfg.SystemConf = .{};
var conf_gen: i64 = -1;
var plan_ok: bool = false;
var mode_written: i8 = -1;
var last_check_ns: i128 = 0;
var rx: [RX_MAX]u8 = undefined;

/// Last published capture-guard word; the file is rewritten only on change.
const guard_path = "/data/system/noshot_guard";
var guard_written: []const u8 = "";

var map_key: [MAP_CAP]i64 = @splat(0);
var map_uid: [MAP_CAP]u32 = @splat(0);
var map_used: [MAP_CAP]bool = @splat(false);
var map_lock = std.atomic.Value(bool).init(false);

var creates_logged: usize = 0;
var flags_logged: usize = 0;
var clears_logged: usize = 0;
var clears_total: usize = 0;

var cls_parcel: jni.jclass = null;
var mid_pos: jni.jmethodID = null;
var mid_set_pos: jni.jmethodID = null;
var mid_read_int: jni.jmethodID = null;

fn lock() void {
    while (map_lock.swap(true, .acquire)) std.atomic.spinLoopHint();
}

fn unlock() void {
    map_lock.store(false, .release);
}

fn slotOf(key: i64) usize {
    var x: u64 = @bitCast(key);
    x *%= 0x9E3779B97F4A7C15;
    x ^= x >> 31;
    return @intCast(x & (MAP_CAP - 1));
}

/// Only layers with a known owner are stored; a create without an owner
/// poisons the slot instead, so a reused address cannot inherit a stale uid.
fn record(key: i64, uid: u32) void {
    if (key == 0) return;
    lock();
    defer unlock();
    var i = slotOf(key);
    var n: usize = 0;
    while (n < MAP_CAP) : (n += 1) {
        if (!map_used[i]) {
            if (uid == 0) return;
            map_used[i] = true;
            map_key[i] = key;
            map_uid[i] = uid;
            return;
        }
        if (map_key[i] == key) {
            map_uid[i] = uid;
            return;
        }
        i = (i + 1) & (MAP_CAP - 1);
    }
}

fn lookup(key: i64) ?u32 {
    if (key == 0) return null;
    lock();
    defer unlock();
    var i = slotOf(key);
    var n: usize = 0;
    while (n < MAP_CAP) : (n += 1) {
        if (!map_used[i]) return null;
        if (map_key[i] == key) return map_uid[i];
        i = (i + 1) & (MAP_CAP - 1);
    }
    return null;
}

fn selected(uid: u32) bool {
    if (!conf.hook) return false;
    if (uid == SEL_ALL) return true;
    return conf.hasUid(uid);
}

fn nowNanos() i128 {
    var ts: linux.timespec = undefined;
    if (linux.errno(linux.clock_gettime(.MONOTONIC, &ts)) != .SUCCESS) return 0;
    return @as(i128, ts.sec) * std.time.ns_per_s + @as(i128, ts.nsec);
}

/// Re-reads the resolved selection the root side keeps in /data/system.
fn refresh(force: bool) void {
    const now = nowNanos();
    if (!force and last_check_ns != 0 and now - last_check_ns < CHECK_SPAN_NS) return;
    last_check_ns = now;

    const text = fs.readFixed(cfg.plan_path, &rx) orelse {
        if (force) log.warn("selection plan unreadable: {s}", .{cfg.plan_path});
        state.note("refresh failed: {s} unreadable", .{cfg.plan_path});
        plan_ok = false;
        publishMode();
        return;
    };
    var fresh: cfg.SystemConf = undefined;
    cfg.parseSystemConf(&fresh, text);
    plan_ok = fresh.uid_count > 0;
    const changed = fresh.gen != conf_gen or fresh.hook != conf.hook or
        fresh.uid_count != conf.uid_count or fresh.capture != conf.capture;
    conf_gen = fresh.gen;
    conf = fresh;
    // Kept in sync even when the hook is not installed: the published word
    // then says "restart" instead of silently claiming the level is active.
    outbound.setMode(guardMode(conf.capture));
    publishGuard();
    if (!changed and !force) {
        publishMode();
        return;
    }
    publishMode();
    log.setEnabled(conf.log);
    log.info("system config: {d} uids, {d} names, hook={d}", .{
        conf.uid_count, conf.pkg_count, @intFromBool(conf.hook),
    });
    state.note("refresh gen={d} hook={d} uids={d} pkgs={d} first={d}", .{
        conf.gen, @intFromBool(conf.hook), conf.uid_count, conf.pkg_count,
        if (conf.uid_count > 0) conf.uids[0] else 0,
    });
}

fn metadataUid(env: *jni.JNIEnv, parcel: jni.jobject) u32 {
    if (parcel == null or mid_read_int == null or mid_pos == null or mid_set_pos == null) return 0;

    const saved = jni.callIntMethod(env, parcel, mid_pos, .{});
    if (jni.exceptionCheck(env)) {
        jni.exceptionClear(env);
        return 0;
    }
    if (saved != 0) jni.callVoidMethod(env, parcel, mid_set_pos, .{@as(jni.jint, 0)});
    defer jni.callVoidMethod(env, parcel, mid_set_pos, .{saved});

    const count = jni.callIntMethod(env, parcel, mid_read_int, .{});
    if (jni.exceptionCheck(env)) {
        jni.exceptionClear(env);
        return 0;
    }
    if (count <= 0 or count > 16) return 0;

    var uid: u32 = 0;
    var i: i32 = 0;
    while (i < count) : (i += 1) {
        const key = jni.callIntMethod(env, parcel, mid_read_int, .{});
        const len = jni.callIntMethod(env, parcel, mid_read_int, .{});
        if (len != 4) {
            if (jni.exceptionCheck(env)) jni.exceptionClear(env);
            return 0;
        }
        const val = jni.callIntMethod(env, parcel, mid_read_int, .{});
        if (jni.exceptionCheck(env)) {
            jni.exceptionClear(env);
            return 0;
        }
        // METADATA_OWNER_UID == 1
        if (key == 1 and uid == 0) uid = @bitCast(val);
    }
    return uid;
}

fn resolveParcel(env: *jni.JNIEnv) bool {
    const cls = jni.findClass(env, "android/os/Parcel");
    if (cls == null) {
        if (jni.exceptionCheck(env)) jni.exceptionClear(env);
        return false;
    }
    cls_parcel = jni.newGlobalRef(env, cls);
    jni.deleteLocalRef(env, cls);
    if (cls_parcel == null) return false;
    mid_pos = jni.getMethodID(env, cls_parcel, "dataPosition", "()I");
    mid_set_pos = jni.getMethodID(env, cls_parcel, "setDataPosition", "(I)V");
    mid_read_int = jni.getMethodID(env, cls_parcel, "readInt", "()I");
    if (jni.exceptionCheck(env)) jni.exceptionClear(env);
    return mid_pos != null and mid_set_pos != null and mid_read_int != null;
}

fn writeMode(system_side: bool) bool {
    return fs.writeFile(cfg.mode_path, if (system_side) "system\n" else "client\n");
}

/// Publishes the side app processes should use. Only claims `system` while the
/// hooks are installed *and* a selection has been resolved, so a first boot
/// without a plan keeps working through the in-process hook.
fn publishMode() void {
    const active = installed and plan_ok and conf.hook;
    const value: i8 = if (active) 1 else 0;
    if (mode_written == value) return;
    if (writeMode(active)) mode_written = value;
}

/// Capture-callback status, read back by the WebUI. Separate from the mode file
/// so existing one-word parsers keep working.
fn publishGuard() void {
    const word = outbound.stateWord();
    if (std.mem.eql(u8, guard_written, word)) return;
    var buf: [32]u8 = undefined;
    const text = std.fmt.bufPrint(&buf, "{s}\n", .{word}) catch return;
    if (fs.writeFile(guard_path, text)) guard_written = word;
}

/// Counts hook installations per boot. A system_server that dies right after
/// the hook is installed comes back in the same boot, so three quick attempts
/// disable the system side instead of risking a loop.
///
/// Only quick retries count: an install that follows a stretch of healthy
/// uptime is a deliberate restart (`ctl.restart zygote`), not a crash loop, and
/// must not burn the fuse — that restart is the documented way to turn the
/// capture guard on.
const stable_gap_s: u64 = 120;

fn uptimeSeconds() u64 {
    return @intCast(@divTrunc(nowNanos(), std.time.ns_per_s));
}

fn attemptAllowed() bool {
    if (fs.exists(cfg.disable_path)) return false;

    var boot_buf: [64]u8 = undefined;
    const boot_raw = fs.readFixed("/proc/sys/kernel/random/boot_id", &boot_buf) orelse return true;
    const boot_id = std.mem.trim(u8, boot_raw, " \t\r\n");
    if (boot_id.len == 0) return true;

    const now = uptimeSeconds();
    var stored: [128]u8 = undefined;
    var count: u32 = 0;
    if (fs.readFixed(cfg.attempt_path, &stored)) |text| {
        const saved = std.mem.trim(u8, text, " \t\r\n");
        if (std.mem.startsWith(u8, saved, boot_id)) {
            var fields = std.mem.tokenizeAny(u8, saved[boot_id.len..], " ");
            count = std.fmt.parseInt(u32, fields.next() orelse "", 10) catch 0;
            const last = std.fmt.parseInt(u64, fields.next() orelse "", 10) catch 0;
            if (now > last and now - last > stable_gap_s) count = 0;
        }
    }

    if (count >= 3) {
        _ = fs.writeFile(cfg.disable_path, "system_server hook failed three times in one boot\n");
        log.err("system hook disabled after {d} attempts in this boot", .{count});
        return false;
    }

    var out: [160]u8 = undefined;
    if (std.fmt.bufPrint(&out, "{s} {d} {d}\n", .{ boot_id, count + 1, now })) |text| {
        _ = fs.writeFile(cfg.attempt_path, text);
    } else |_| {}
    return true;
}

/// `SurfaceControl.nativeSetFlags` is annotated `@CriticalNative` on some
/// vendor ROMs: it is then called without JNIEnv/jclass and a JNI-shaped hook
/// is never entered. Probe it once with all-zero arguments, which the probe
/// swallows, and pick the matching hook shape.
fn detectSetFlagsAbi(env: *jni.JNIEnv, hook: *const fn (*jni.JNIEnv, [*:0]const u8, [*]jni.JNINativeMethod, c_int) callconv(.c) void) ?*anyopaque {
    const cls = jni.findClass(env, "android/view/SurfaceControl");
    if (cls == null) {
        if (jni.exceptionCheck(env)) jni.exceptionClear(env);
        return null;
    }
    defer jni.deleteLocalRef(env, cls);

    const mid = jni.getStaticMethodID(env, cls, "nativeSetFlags", "(JJII)V");
    if (mid == null) {
        if (jni.exceptionCheck(env)) jni.exceptionClear(env);
        return null;
    }

    var methods = [_]jni.JNINativeMethod{.{
        .name = "nativeSetFlags",
        .signature = "(JJII)V",
        .fn_ptr = @ptrCast(@constCast(&probeSetFlags)),
    }};
    hook(env, "android/view/SurfaceControl", &methods, 1);
    const original = methods[0].fn_ptr;

    probe_seen = false;
    jni.callStaticVoidMethod(env, cls, mid, .{
        @as(jni.jlong, 0), @as(jni.jlong, 0), @as(jni.jint, 0), @as(jni.jint, 0),
    });
    if (jni.exceptionCheck(env)) jni.exceptionClear(env);

    set_flags_abi = if (!probe_seen or (probe_args[0] == 0 and probe_args[1] == 0)) .critical else .jni;
    log.info("nativeSetFlags abi: {s} (seen={d})", .{ @tagName(set_flags_abi), @intFromBool(probe_seen) });
    state.note("abi={s} seen={d} args={d},{d},{d},{d}", .{
        @tagName(set_flags_abi), @intFromBool(probe_seen),
        probe_args[0], probe_args[1], probe_args[2], probe_args[3],
    });
    return original;
}

fn probeSetFlags(a0: jni.jlong, a1: jni.jlong, a2: jni.jlong, a3: jni.jlong) callconv(.c) void {
    probe_args = .{ a0, a1, a2, a3 };
    probe_seen = true;
}

/// Turns a requested level into the hook's own mode.
pub fn guardMode(requested: cfg.CaptureGuard) outbound.Mode {
    return switch (requested) {
        .off => .off,
        .observe => .observe,
        .on => .suppress,
    };
}

fn hookPtr(comptime f: anytype) ?*anyopaque {
    return @ptrCast(@constCast(f));
}

pub fn install(table: *api.ApiTable, env: *jni.JNIEnv) bool {
    if (installed) return true;

    // Read the plan first: even when the hooks are skipped this boot, the
    // published guard word then says "restart" instead of a stale level.
    refresh(true);

    if (!attemptAllowed()) {
        log.warn("system hook skipped ({s} or too many attempts)", .{cfg.disable_path});
        plan_ok = false;
        publishMode();
        publishGuard();
        return false;
    }

    const hook = table.hook_jni_native_methods orelse {
        log.err("host has no hookJniNativeMethods", .{});
        publishMode();
        publishGuard();
        return false;
    };

    // Independent of the surface hooks: detection callbacks are a separate
    // concern and stay available while `system_hook=0`.
    _ = outbound.install(table, env, guardMode(conf.capture));
    publishGuard();

    if (!conf.hook) {
        log.info("system hook off (settings or {s})", .{cfg.user_disable_path});
        publishMode();
        return false;
    }
    if (!resolveParcel(env)) log.warn("Parcel bindings unavailable, owner uid falls back to names", .{});

    orig_set_flags = detectSetFlagsAbi(env, hook);

    const set_flags_hook = if (set_flags_abi == .critical) hookPtr(&hookSetFlagsCritical) else hookPtr(&hookSetFlags);
    var methods = [_]jni.JNINativeMethod{
        .{
            .name = "nativeCreate",
            .signature = "(Landroid/view/SurfaceSession;Ljava/lang/String;IIIIJLandroid/os/Parcel;)J",
            .fn_ptr = @ptrCast(@constCast(&hookCreate)),
        },
        .{
            .name = "nativeSetFlags",
            .signature = "(JJII)V",
            .fn_ptr = set_flags_hook,
        },
    };
    hook(env, "android/view/SurfaceControl", &methods, 2);

    orig_create = methods[0].fn_ptr;
    installed = orig_create != null and orig_set_flags != null;
    publishMode();

    if (installed) {
        log.info("system hook installed: create={x} setFlags={x} selected={d}", .{
            @intFromPtr(orig_create.?), @intFromPtr(orig_set_flags.?), conf.uid_count,
        });
    } else {
        log.err("system hook failed: create={d} setFlags={d}", .{
            @intFromBool(orig_create != null), @intFromBool(orig_set_flags != null),
        });
    }
    state.note("install hook={d} uids={d} pkgs={d} create={d} setFlags={d}", .{
        @intFromBool(conf.hook), conf.uid_count, conf.pkg_count,
        @intFromBool(orig_create != null), @intFromBool(orig_set_flags != null),
    });
    return installed;
}

fn hookCreate(
    env: *jni.JNIEnv,
    cls: jni.jclass,
    session: jni.jobject,
    name: jni.jstring,
    w: jni.jint,
    h: jni.jint,
    format: jni.jint,
    flags: jni.jint,
    parent: jni.jlong,
    metadata: jni.jobject,
) callconv(.c) jni.jlong {
    const orig: CreateFn = @ptrCast(@alignCast(orig_create orelse return 0));
    refresh(false);

    var uid = metadataUid(env, metadata);
    var buf: [160]u8 = undefined;
    var label: []const u8 = "";
    if (uid == 0 and parent != 0) {
        if (lookup(parent)) |inherited| uid = inherited;
    }
    if (uid == 0 or creates_logged < 8) {
        if (jni.copyJavaString(env, name, &buf)) |s| label = s;
    }
    if (uid == 0 and label.len > 0 and conf.nameMatches(label)) uid = SEL_ALL;
    if (uid != 0 and parent != 0) record(parent, uid);

    var f = flags;
    const hit = uid != 0 and selected(uid);
    if (hit) f &= ~SECURE;

    const handle = orig(env, cls, session, name, w, h, format, f, parent, metadata);
    if (handle != 0) record(handle, uid);

    if (creates_logged < 8) {
        creates_logged += 1;
        log.info("layer create name={s} uid={d} surface={x} parent={x} flags=0x{x}{s}", .{
            label, uid, handle, parent, flags, if (hit) " secure-cleared" else "",
        });
    }
    return handle;
}

fn hookSetFlags(
    env: *jni.JNIEnv,
    cls: jni.jclass,
    transaction: jni.jlong,
    surface: jni.jlong,
    flags: jni.jint,
    mask: jni.jint,
) callconv(.c) void {
    const orig: SetFlagsFn = @ptrCast(@alignCast(orig_set_flags orelse return));
    orig(env, cls, transaction, surface, adjust(surface, flags, mask), mask);
}

fn hookSetFlagsCritical(
    transaction: jni.jlong,
    surface: jni.jlong,
    flags: jni.jint,
    mask: jni.jint,
) callconv(.c) void {
    const orig: SetFlagsCriticalFn = @ptrCast(@alignCast(orig_set_flags orelse return));
    orig(transaction, surface, adjust(surface, flags, mask), mask);
}

fn adjust(surface: jni.jlong, flags: jni.jint, mask: jni.jint) jni.jint {
    if ((mask & SECURE) == 0) return flags;

    const owner = lookup(surface);
    if (flags_logged < 8) {
        flags_logged += 1;
        log.info("secure set surface={x} owner={any} sel={d}", .{
            surface, owner, @intFromBool(owner != null and selected(owner.?)),
        });
    }
    const uid = owner orelse return flags;
    if (!selected(uid)) return flags;

    clears_total += 1;
    if (clears_logged < 8) {
        clears_logged += 1;
        log.info("secure cleared uid={d} surface={x} flags=0x{x} mask=0x{x}", .{
            uid, surface, flags, mask,
        });
    }
    return flags & ~SECURE;
}

pub fn isInstalled() bool {
    return installed;
}

pub fn selectedCount() usize {
    return conf.uid_count;
}

pub fn clearedCount() usize {
    return clears_total;
}
