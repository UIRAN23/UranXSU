//! Capture/recording detection callbacks that leave `system_server`.
//!
//! Android 14+ detection callbacks are ordinary oneway binder transactions
//! sent from system_server to the app, so they can be seen from inside
//! system_server: `BinderProxy.transactNative` is still a JNI method there
//! (unlike `Binder.execTransact`, which is Java on recent releases).
//!
//!   android.app.IScreenCaptureObserver.onScreenCaptured()              (API 34+)
//!   android.window.IScreenRecordingCallback.onScreenRecordingStateChanged(bool)
//!
//! Two levels, both off by default:
//!   observe — count and record only, nothing is altered;
//!   on      — drop the "capture happened" notice, and drop the recording
//!             notice only while it reports "visible".
//!
//! The parcel is matched on its bytes, not by calling back into Java: a
//! callback parcel is tiny, so the whole buffer is copied out once and the
//! offsets are validated before anything is touched.

const std = @import("std");

const api = @import("api.zig");
const jni = @import("jni.zig");
const log = @import("log.zig");
const state = @import("state.zig");

const capture_token = "android.app.IScreenCaptureObserver";
const recording_token = "android.window.IScreenRecordingCallback";

const FLAG_ONEWAY: jni.jint = 1;

/// Sanity bound on a callback parcel: with the kernel header the capture
/// callback is 88 bytes and the recording one 96..100, without it 80..92.
/// Anything outside this window is forwarded untouched.
pub const min_size: i32 = 64;
pub const max_size: i32 = 160;

/// Interface-token slots: kernel-binder parcel (strictMode, workSource,
/// header, token) and the legacy one (strictMode, token).
pub const token_slot_new: i32 = 12;
pub const token_slot_old: i32 = 4;

pub const Kind = enum { capture, recording, other };
pub const Mode = enum { off, observe, suppress };

var cls_parcel: jni.jclass = null;
var mid_pos: jni.jmethodID = null;
var mid_set_pos: jni.jmethodID = null;
var mid_size: jni.jmethodID = null;
var mid_read_int: jni.jmethodID = null;

var orig_transact: ?*anyopaque = null;
var installed: bool = false;
/// The hook was asked for but this process refused it (no host API, no
/// Parcel bindings). Distinct from "not installed yet", which a restart fixes.
var rejected: bool = false;

var mode: Mode = .off;
var capture_seen: usize = 0;
var capture_dropped: usize = 0;
var recording_seen: usize = 0;
var recording_dropped: usize = 0;
var last_code: i32 = 0;

const OrigTransactFn = *const fn (*jni.JNIEnv, jni.jobject, jni.jint, jni.jobject, jni.jobject, jni.jint) callconv(.c) jni.jboolean;

pub fn kindOf(descriptor: []const u8) Kind {
    if (std.mem.eql(u8, descriptor, capture_token)) return .capture;
    if (std.mem.eql(u8, descriptor, recording_token)) return .recording;
    return .other;
}

/// Bytes a `string16` of `len` characters occupies, length prefix and padding
/// included. -1 is the null string.
pub fn string16Bytes(len: i32) i32 {
    if (len < 0) return 4;
    return 4 + @divTrunc((len + 1) * 2 + 3, 4) * 4;
}

pub const Found = struct {
    kind: Kind,
    /// Offset of the payload int (recording state).
    value_at: usize,
    value: i32,
};

fn readInt(bytes: []const u8, off: usize) ?i32 {
    if (off + 4 > bytes.len) return null;
    return std.mem.readInt(i32, bytes[off..][0..4], .little);
}

fn tokenAt(bytes: []const u8, slot: usize, expected: []const u8) bool {
    const len = readInt(bytes, slot) orelse return false;
    if (len != @as(i32, @intCast(expected.len))) return false;
    const chars = slot + 4;
    if (chars + expected.len * 2 > bytes.len) return false;
    for (expected, 0..) |c, i| {
        const unit = std.mem.readInt(u16, bytes[chars + i * 2 ..][0..2], .little);
        if (unit != c) return false;
    }
    return true;
}

/// Pure matcher over a callback-sized parcel buffer.
pub fn locateIn(bytes: []const u8) ?Found {
    for ([_]usize{ token_slot_new, token_slot_old }) |slot| {
        if (tokenAt(bytes, slot, capture_token)) {
            return .{ .kind = .capture, .value_at = 0, .value = 0 };
        }
        if (tokenAt(bytes, slot, recording_token)) {
            const at = slot + @as(usize, @intCast(string16Bytes(recording_token.len)));
            return .{ .kind = .recording, .value_at = at, .value = readInt(bytes, at) orelse 0 };
        }
    }
    return null;
}

pub fn install(table: *api.ApiTable, env: *jni.JNIEnv, requested: Mode) bool {
    mode = requested;
    if (installed) return true;
    if (requested == .off) return false;

    const cls = jni.findClass(env, "android/os/Parcel");
    if (cls == null) {
        if (jni.exceptionCheck(env)) jni.exceptionClear(env);
        log.warn("callback guard: Parcel not found", .{});
        state.note("callback guard off: no Parcel", .{});
        rejected = true;
        return false;
    }
    const gcls = jni.newGlobalRef(env, cls);
    jni.deleteLocalRef(env, cls);
    if (gcls == null) return false;
    cls_parcel = gcls;

    mid_pos = jni.getMethodID(env, cls_parcel, "dataPosition", "()I");
    mid_set_pos = jni.getMethodID(env, cls_parcel, "setDataPosition", "(I)V");
    mid_size = jni.getMethodID(env, cls_parcel, "dataSize", "()I");
    mid_read_int = jni.getMethodID(env, cls_parcel, "readInt", "()I");
    if (jni.exceptionCheck(env)) jni.exceptionClear(env);
    if (mid_pos == null or mid_set_pos == null or mid_size == null or mid_read_int == null) {
        log.warn("callback guard: Parcel bindings unavailable", .{});
        state.note("callback guard off: no Parcel bindings", .{});
        rejected = true;
        return false;
    }

    const hook = table.hook_jni_native_methods orelse {
        log.warn("callback guard: host has no hookJniNativeMethods", .{});
        state.note("callback guard off: no hook API", .{});
        rejected = true;
        return false;
    };

    var methods = [_]jni.JNINativeMethod{.{
        .name = "transactNative",
        .signature = "(ILandroid/os/Parcel;Landroid/os/Parcel;I)Z",
        .fn_ptr = @ptrCast(@constCast(&hookTransact)),
    }};
    hook(env, "android/os/BinderProxy", &methods, 1);
    // A rejected hook can leave a pending FindClass exception behind.
    if (jni.exceptionCheck(env)) jni.exceptionClear(env);
    orig_transact = methods[0].fn_ptr;
    installed = orig_transact != null;

    if (installed) {
        log.info("callback guard installed ({s})", .{@tagName(mode)});
        state.note("callback guard {s}", .{@tagName(mode)});
    } else {
        log.err("callback guard failed: BinderProxy.transactNative", .{});
        state.note("callback guard off: hook rejected", .{});
        rejected = true;
    }
    return installed;
}

/// Switches an already installed hook between observe and suppress.
pub fn setMode(requested: Mode) void {
    mode = requested;
}

fn hookTransact(
    env: *jni.JNIEnv,
    this: jni.jobject,
    code: jni.jint,
    data: jni.jobject,
    reply: jni.jobject,
    flags: jni.jint,
) callconv(.c) jni.jboolean {
    const orig: OrigTransactFn = @ptrCast(@alignCast(orig_transact orelse return jni.JNI_FALSE));
    if (mode == .off or data == null or (flags & FLAG_ONEWAY) == 0) {
        return orig(env, this, code, data, reply, flags);
    }
    return switch (classify(env, data, code)) {
        .drop => jni.JNI_TRUE,
        .pass => orig(env, this, code, data, reply, flags),
    };
}

const Verdict = enum { drop, pass };

/// Copies the parcel out, then decides. Any failure falls through to the
/// original method, so a layout this build does not know simply passes.
fn classify(env: *jni.JNIEnv, parcel: jni.jobject, code: i32) Verdict {
    const size = jni.callIntMethod(env, parcel, mid_size, .{});
    if (size < min_size or size > max_size or @rem(size, 4) != 0) return .pass;

    // Reading moves the parcel's own position, which the native side uses.
    const saved = jni.callIntMethod(env, parcel, mid_pos, .{});
    if (jni.exceptionCheck(env)) {
        jni.exceptionClear(env);
        return .pass;
    }
    defer jni.callVoidMethod(env, parcel, mid_set_pos, .{saved});
    // Cheap prefilter: two ints. Only a parcel whose token length at a known
    // slot is exactly one of the two descriptors gets copied out.
    if (!plausible(env, parcel, size)) return .pass;

    var buf: [max_size]u8 = undefined;
    const bytes = buf[0..@intCast(size)];
    if (!copyOut(env, parcel, bytes)) return .pass;

    const found = locateIn(bytes) orelse return .pass;
    switch (found.kind) {
        .capture => {
            capture_seen += 1;
            if (mode == .suppress) {
                capture_dropped += 1;
                if (capture_dropped <= 4 or capture_dropped % 100 == 0) {
                    record("capture", code, capture_dropped, -1);
                }
                return .drop;
            }
            if (capture_seen <= 4 or capture_seen % 100 == 0) {
                record("capture", code, capture_seen, -1);
            }
            return .pass;
        },
        .recording => {
            recording_seen += 1;
            const visible = found.value > 0;
            if (mode == .suppress and visible) {
                recording_dropped += 1;
                if (recording_dropped <= 4 or recording_dropped % 100 == 0) {
                    record("recording", code, recording_dropped, 1);
                }
                return .drop;
            }
            if (recording_seen <= 4 or recording_seen % 100 == 0) {
                record("recording", code, recording_seen, @intFromBool(visible));
            }
            return .pass;
        },
        .other => return .pass,
    }
}

fn plausible(env: *jni.JNIEnv, parcel: jni.jobject, size: i32) bool {
    for ([_]i32{ token_slot_new, token_slot_old }) |slot| {
        if (slot + 4 > size) continue;
        const len = readIntAt(env, parcel, slot) orelse continue;
        if (len == capture_token.len or len == recording_token.len) return true;
    }
    return false;
}

fn readIntAt(env: *jni.JNIEnv, parcel: jni.jobject, off: i32) ?i32 {
    jni.callVoidMethod(env, parcel, mid_set_pos, .{off});
    const v = jni.callIntMethod(env, parcel, mid_read_int, .{});
    if (jni.exceptionCheck(env)) {
        jni.exceptionClear(env);
        return null;
    }
    return v;
}

/// Reads the parcel into `out` (as ints). The caller restores the position.
fn copyOut(env: *jni.JNIEnv, parcel: jni.jobject, out: []u8) bool {
    const words = out.len / 4;
    jni.callVoidMethod(env, parcel, mid_set_pos, .{@as(jni.jint, 0)});

    var i: usize = 0;
    while (i < words) : (i += 1) {
        const v = jni.callIntMethod(env, parcel, mid_read_int, .{});
        if (jni.exceptionCheck(env)) {
            jni.exceptionClear(env);
            return false;
        }
        std.mem.writeInt(i32, out[i * 4 ..][0..4], v, .little);
    }
    return true;
}

fn record(what: []const u8, code: i32, n: usize, visible: i32) void {
    last_code = code;
    if (visible < 0) {
        log.info("{s} callback (transact={d} n={d})", .{ what, code, n });
        state.note("{s} n={d} code={d}", .{ what, n, code });
    } else {
        log.info("{s} callback (transact={d} n={d} visible={d})", .{ what, code, n, visible });
        state.note("{s} n={d} code={d} visible={d}", .{ what, n, code, visible });
    }
}

/// `on|observe|off|restart|unsupported`, published for the WebUI. `restart`
/// means the level is saved but the hook was not installed this boot.
pub fn stateWord() []const u8 {
    if (mode == .off) return "off";
    if (installed) return if (mode == .observe) "observe" else "on";
    return if (rejected) "unsupported" else "restart";
}

// ---------------------------------------------------------------------------
// tests
// ---------------------------------------------------------------------------

/// Builds the parcel a Java proxy writes for `descriptor` plus one payload int.
fn buildParcel(alloc: std.mem.Allocator, descriptor: []const u8, payload: i32) ![]u8 {
    const token = string16Bytes(@intCast(descriptor.len));
    const total: usize = @intCast(token_slot_new + token + 4);
    const buf = try alloc.alloc(u8, total);
    @memset(buf, 0);

    std.mem.writeInt(i32, buf[8..12], 0x53595354, .little); // kernel header
    const slot: usize = @intCast(token_slot_new);
    std.mem.writeInt(i32, buf[slot..][0..4], @intCast(descriptor.len), .little);
    for (descriptor, 0..) |c, i| {
        std.mem.writeInt(u16, buf[slot + 4 + i * 2 ..][0..2], c, .little);
    }
    const at: usize = @intCast(token_slot_new + token);
    std.mem.writeInt(i32, buf[at..][0..4], payload, .little);
    return buf;
}

test "callback descriptors" {
    try std.testing.expectEqual(Kind.capture, kindOf("android.app.IScreenCaptureObserver"));
    try std.testing.expectEqual(Kind.recording, kindOf("android.window.IScreenRecordingCallback"));
    try std.testing.expectEqual(Kind.other, kindOf("android.app.IActivityManager"));
    try std.testing.expectEqual(Kind.other, kindOf(""));
    try std.testing.expectEqual(Kind.other, kindOf("android.app.IScreenCaptureObserverX"));
}

test "string16 sizes" {
    try std.testing.expectEqual(@as(i32, 4 + 72), string16Bytes(34));
    try std.testing.expectEqual(@as(i32, 4 + 80), string16Bytes(39));
    try std.testing.expectEqual(@as(i32, 4), string16Bytes(-1));
}

test "capture parcel is recognised" {
    const buf = try buildParcel(std.testing.allocator, capture_token, 0);
    defer std.testing.allocator.free(buf);
    const found = locateIn(buf) orelse return error.NotFound;
    try std.testing.expectEqual(Kind.capture, found.kind);
    try std.testing.expect(@as(i32, @intCast(buf.len)) >= min_size);
    try std.testing.expect(@as(i32, @intCast(buf.len)) <= max_size);
}

test "recording parcel reports its state" {
    const on = try buildParcel(std.testing.allocator, recording_token, 1);
    defer std.testing.allocator.free(on);
    const visible = locateIn(on) orelse return error.NotFound;
    try std.testing.expectEqual(Kind.recording, visible.kind);
    try std.testing.expectEqual(@as(i32, 1), visible.value);

    const off = try buildParcel(std.testing.allocator, recording_token, 0);
    defer std.testing.allocator.free(off);
    const hidden = locateIn(off) orelse return error.NotFound;
    try std.testing.expectEqual(@as(i32, 0), hidden.value);
}

test "payload offset lands right after the token" {
    const buf = try buildParcel(std.testing.allocator, recording_token, 1);
    defer std.testing.allocator.free(buf);
    const found = locateIn(buf) orelse return error.NotFound;
    try std.testing.expectEqual(@as(usize, @intCast(token_slot_new + string16Bytes(39))), found.value_at);
    try std.testing.expectEqual(found.value_at + 4, buf.len);
}

test "legacy parcel without a work source is recognised" {
    const token = string16Bytes(34);
    const buf = try std.testing.allocator.alloc(u8, @intCast(token_slot_old + token));
    defer std.testing.allocator.free(buf);
    @memset(buf, 0);
    std.mem.writeInt(i32, buf[@intCast(token_slot_old)..][0..4], 34, .little);
    for (capture_token, 0..) |c, i| {
        const at: usize = @intCast(token_slot_old + 4 + i * 2);
        std.mem.writeInt(u16, buf[at..][0..2], c, .little);
    }
    const found = locateIn(buf) orelse return error.NotFound;
    try std.testing.expectEqual(Kind.capture, found.kind);
}

test "other interfaces and truncated buffers are ignored" {
    const buf = try buildParcel(std.testing.allocator, "android.app.IActivityManagerX", 0);
    defer std.testing.allocator.free(buf);
    try std.testing.expect(locateIn(buf) == null);

    // A token that does not fit the buffer must not be trusted.
    const short = try buildParcel(std.testing.allocator, capture_token, 0);
    defer std.testing.allocator.free(short);
    try std.testing.expect(locateIn(short[0..56]) == null);
}
