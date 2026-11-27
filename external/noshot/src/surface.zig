//! SurfaceControl secure-flag suppression (in-process half).
//!
//! Some apps (video players, DRM clients) do not rely on the window's
//! FLAG_SECURE only: they call `SurfaceControl.Transaction.setSecure(true)`
//! which ends up in the JNI native method `SurfaceControl.nativeSetFlags`, and
//! `SurfaceControl.Builder.setSecure(true)` which puts the same bit straight
//! into the `nativeCreate` flags. Clearing SECURE (0x80) from those arguments
//! keeps every other flag intact and stops the surface from becoming
//! unreadable.

const std = @import("std");
const jni = @import("jni.zig");
const api = @import("api.zig");
const log = @import("log.zig");

const SECURE: i32 = 0x00000080;

const SetFlagsFn = *const fn (*jni.JNIEnv, jni.jclass, jni.jlong, jni.jlong, jni.jint, jni.jint) callconv(.c) void;
/// @CriticalNative call shape: no JNIEnv, no jclass.
const SetFlagsCriticalFn = *const fn (jni.jlong, jni.jlong, jni.jint, jni.jint) callconv(.c) void;

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

const SetFlagsAbi = enum { jni, critical };

var orig_set_flags: ?*anyopaque = null;
var orig_create: ?*anyopaque = null;
var installed: bool = false;
var cleared: usize = 0;

var set_flags_abi: SetFlagsAbi = .jni;
var probe_args: [4]i64 = @splat(0);
var probe_seen: bool = false;

fn hookPtr(comptime f: anytype) ?*anyopaque {
    return @ptrCast(@constCast(f));
}

pub fn install(api_table: *api.ApiTable, env: *jni.JNIEnv) bool {
    if (installed) return true;
    const hook = api_table.hook_jni_native_methods orelse return false;

    orig_set_flags = detectSetFlagsAbi(env, hook);

    const set_flags_hook = if (set_flags_abi == .critical) hookPtr(&hookNativeSetFlagsCritical) else hookPtr(&hookNativeSetFlags);
    var methods = [_]jni.JNINativeMethod{
        .{
            .name = "nativeSetFlags",
            .signature = "(JJII)V",
            .fn_ptr = set_flags_hook,
        },
        .{
            .name = "nativeCreate",
            .signature = "(Landroid/view/SurfaceSession;Ljava/lang/String;IIIIJLandroid/os/Parcel;)J",
            .fn_ptr = @ptrCast(@constCast(&hookNativeCreate)),
        },
    };
    hook(env, "android/view/SurfaceControl", &methods, 2);
    orig_create = methods[1].fn_ptr;
    installed = orig_set_flags != null and orig_create != null;

    if (installed) {
        log.info("hook installed: SurfaceControl.nativeSetFlags ({s}) + nativeCreate", .{@tagName(set_flags_abi)});
    } else {
        log.warn("hook skipped: SurfaceControl (setFlags={d} create={d})", .{
            @intFromBool(orig_set_flags != null), @intFromBool(orig_create != null),
        });
    }
    return installed;
}

/// `nativeSetFlags` is `@CriticalNative` on some ROMs, in which case a
/// JNI-shaped hook is never entered. Probe once with all-zero arguments (the
/// probe swallows them) and pick the matching shape.
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
    return original;
}

fn probeSetFlags(a0: jni.jlong, a1: jni.jlong, a2: jni.jlong, a3: jni.jlong) callconv(.c) void {
    probe_args = .{ a0, a1, a2, a3 };
    probe_seen = true;
}

fn hookNativeSetFlags(
    env: *jni.JNIEnv,
    cls: jni.jclass,
    transaction: jni.jlong,
    surface: jni.jlong,
    flags: jni.jint,
    mask: jni.jint,
) callconv(.c) void {
    var f = flags;
    if ((mask & SECURE) != 0) {
        f &= ~SECURE;
        cleared += 1;
        if (cleared <= 4) log.info("SurfaceControl SECURE suppressed (flags=0x{x} mask=0x{x})", .{ flags, mask });
    }
    const orig_fn: SetFlagsFn = @ptrCast(@alignCast(orig_set_flags orelse return));
    orig_fn(env, cls, transaction, surface, f, mask);
}

fn hookNativeSetFlagsCritical(
    transaction: jni.jlong,
    surface: jni.jlong,
    flags: jni.jint,
    mask: jni.jint,
) callconv(.c) void {
    var f = flags;
    if ((mask & SECURE) != 0) {
        f &= ~SECURE;
        cleared += 1;
        if (cleared <= 4) log.info("SurfaceControl SECURE suppressed (flags=0x{x} mask=0x{x})", .{ flags, mask });
    }
    const orig_fn: SetFlagsCriticalFn = @ptrCast(@alignCast(orig_set_flags orelse return));
    orig_fn(transaction, surface, f, mask);
}

fn hookNativeCreate(
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
    const orig_fn: CreateFn = @ptrCast(@alignCast(orig_create orelse return 0));
    var f = flags;
    if ((f & SECURE) != 0) {
        f &= ~SECURE;
        cleared += 1;
        if (cleared <= 4) log.info("SurfaceControl create flag SECURE suppressed (flags=0x{x})", .{flags});
    }
    return orig_fn(env, cls, session, name, w, h, format, f, parent, metadata);
}
