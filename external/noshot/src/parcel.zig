//! FLAG_SECURE removal at the binder boundary.
//!
//! We intercept `android.os.BinderProxy.transactNative` and patch the
//! outgoing `IWindowSession` transaction: the `LayoutParams` parcel
//! carries `flags`; clearing bit 13 (FLAG_SECURE) there means WindowManager
//! never learns that the window is secure, so screenshots and screen
//! recording work while every other window flag is preserved.
//!
//! The parcel is written by AIDL as:
//!
//!     int32  strict-mode policy
//!     string16 "android.view.IWindowSession"
//!     flat_binder_object window
//!     int32  1 (attrs != null)
//!     int32  width
//!     int32  height
//!     int32  x
//!     int32  y
//!     int32  type
//!     int32  flags          <-- we clear FLAG_SECURE here
//!     ...
//!
//! `LayoutParams.writeToParcel()` has kept this prefix stable for many
//! releases; we additionally validate every field before touching anything,
//! so a format change simply makes the hook a no-op instead of corrupting a
//! transaction.

const std = @import("std");
const jni = @import("jni.zig");
const api = @import("api.zig");
const cfg = @import("config.zig");
const log = @import("log.zig");

const token = "android.view.IWindowSession";

var cls_parcel: jni.jclass = null;
var mid_data_position: jni.jmethodID = null;
var mid_set_position: jni.jmethodID = null;
var mid_data_size: jni.jmethodID = null;
var mid_read_int: jni.jmethodID = null;
var mid_write_int: jni.jmethodID = null;
var mid_read_string: jni.jmethodID = null;

var orig_transact: ?*anyopaque = null;
var installed: bool = false;
var stripped: usize = 0;

const OrigTransactFn = *const fn (*jni.JNIEnv, jni.jobject, jni.jint, jni.jobject, jni.jobject, jni.jint) callconv(.c) jni.jboolean;

fn inDim(n: i32) bool {
    return n == -1 or n == -2 or (n >= 0 and n <= 20000);
}

/// width, height, x, y, type, flags. Window flags legitimately use the sign
/// bit, so only the SECURE bit is required there.
fn plausible(w: i32, h: i32, x: i32, y: i32, ty: i32, flags: i32) bool {
    if (!inDim(w) or !inDim(h)) return false;
    if (x < -20000 or x > 20000) return false;
    if (y < -20000 or y > 20000) return false;
    if (ty < 1 or ty > 3000) return false;
    return (flags & cfg.FLAG_SECURE) != 0;
}

/// Transactions that carry a LayoutParams: addToDisplay*, relayout*.
fn carriesParams(code: i32) bool {
    return code >= 1 and code <= 7 and code != 3;
}

pub fn install(api_table: *api.ApiTable, env: *jni.JNIEnv) bool {
    if (installed) return true;

    const cls = jni.findClass(env, "android/os/Parcel");
    if (cls == null) {
        if (jni.exceptionCheck(env)) jni.exceptionClear(env);
        log.err("Parcel class not found", .{});
        return false;
    }
    const gcls = jni.newGlobalRef(env, cls);
    jni.deleteLocalRef(env, cls);
    if (gcls == null) return false;
    cls_parcel = gcls;

    mid_data_position = jni.getMethodID(env, cls_parcel, "dataPosition", "()I");
    mid_set_position = jni.getMethodID(env, cls_parcel, "setDataPosition", "(I)V");
    mid_data_size = jni.getMethodID(env, cls_parcel, "dataSize", "()I");
    mid_read_int = jni.getMethodID(env, cls_parcel, "readInt", "()I");
    mid_write_int = jni.getMethodID(env, cls_parcel, "writeInt", "(I)V");
    mid_read_string = jni.getMethodID(env, cls_parcel, "readString", "()Ljava/lang/String;");
    if (mid_data_position == null or mid_set_position == null or mid_data_size == null or
        mid_read_int == null or mid_write_int == null or mid_read_string == null)
    {
        if (jni.exceptionCheck(env)) jni.exceptionClear(env);
        log.err("Parcel method lookup failed", .{});
        return false;
    }

    const hook = api_table.hook_jni_native_methods orelse {
        log.err("host has no hookJniNativeMethods", .{});
        return false;
    };

    var methods = [_]jni.JNINativeMethod{
        .{
            .name = "transactNative",
            .signature = "(ILandroid/os/Parcel;Landroid/os/Parcel;I)Z",
            .fn_ptr = @ptrCast(@constCast(&hookTransactNative)),
        },
    };
    hook(env, "android/os/BinderProxy", &methods, 1);
    orig_transact = methods[0].fn_ptr;
    installed = orig_transact != null;

    if (installed) {
        log.info("hook installed: BinderProxy.transactNative", .{});
    } else {
        log.err("hook failed: BinderProxy.transactNative", .{});
    }
    return installed;
}

fn hookTransactNative(
    env: *jni.JNIEnv,
    this: jni.jobject,
    code: jni.jint,
    data: jni.jobject,
    reply: jni.jobject,
    flags: jni.jint,
) callconv(.c) jni.jboolean {
    if (data != null) patchParcel(env, data, code);

    const orig: OrigTransactFn = @ptrCast(@alignCast(orig_transact orelse return jni.JNI_FALSE));
    return orig(env, this, code, data, reply, flags);
}

fn readInt(env: *jni.JNIEnv, parcel: jni.jobject) i32 {
    return jni.callIntMethod(env, parcel, mid_read_int, .{});
}

fn setPos(env: *jni.JNIEnv, parcel: jni.jobject, pos: i32) void {
    jni.callVoidMethod(env, parcel, mid_set_position, .{pos});
}

fn tokenMatchesAt(env: *jni.JNIEnv, parcel: jni.jobject, off: i32, out_pos: *i32) bool {
    setPos(env, parcel, off);
    const name = jni.callObjectMethod(env, parcel, mid_read_string, .{});
    if (name == null) {
        if (jni.exceptionCheck(env)) jni.exceptionClear(env);
        return false;
    }
    var ok = false;
    if (jni.getStringUTFChars(env, name)) |chars| {
        const s = std.mem.span(chars);
        ok = std.mem.startsWith(u8, s, token);
        jni.releaseStringUTFChars(env, name, chars);
    }
    jni.deleteLocalRef(env, name);
    if (jni.exceptionCheck(env)) jni.exceptionClear(env);
    if (ok) out_pos.* = jni.callIntMethod(env, parcel, mid_data_position, .{});
    return ok;
}

fn patchParcel(env: *jni.JNIEnv, parcel: jni.jobject, code: i32) void {
    if (!carriesParams(code)) return;

    const saved = jni.callIntMethod(env, parcel, mid_data_position, .{});
    defer setPos(env, parcel, saved);

    // Legacy binder parcel: [strictMode][string16 token]  (token at 4)
    // Android 17 kernel-binder parcel: [strictMode][workSource][header][token]
    // (token at 12). Try both; readString() rejects the wrong slot safely.
    var start: i32 = -1;
    if (!tokenMatchesAt(env, parcel, 4, &start)) {
        if (!tokenMatchesAt(env, parcel, 12, &start)) return;
    }

    const size = jni.callIntMethod(env, parcel, mid_data_size, .{});
    if (size < 32 or size > (16 << 20) or start < 0 or start >= size) return;

    // LayoutParams block, verified on this ROM (Android 17):
    // start+24 binder handle, start+28 presence, start+32 width,
    // start+52 flags. A different layout simply fails validation.
    const lp = start + 32;
    if (lp + 24 > size) return;
    setPos(env, parcel, start + 28);
    if (readInt(env, parcel) != 1) return;

    setPos(env, parcel, lp);
    var v: [6]i32 = undefined;
    for (&v) |*slot| slot.* = readInt(env, parcel);
    if (jni.exceptionCheck(env)) {
        jni.exceptionClear(env);
        return;
    }
    if (!plausible(v[0], v[1], v[2], v[3], v[4], v[5])) return;

    setPos(env, parcel, lp + 20);
    jni.callVoidMethod(env, parcel, mid_write_int, .{v[5] & ~cfg.FLAG_SECURE});
    stripped += 1;
    if (stripped <= 4) {
        log.info("FLAG_SECURE stripped (transact={d} off={d} w={d} h={d} type={d} flags=0x{x})", .{ code, lp, v[0], v[1], v[4], v[5] });
    }
}

pub fn isInstalled() bool {
    return installed;
}
