//! Minimal JNI bindings for Zig.
//!
//! We intentionally do not use `@cImport` (no NDK headers are required to
//! build this module). The JNI function table is a plain array of function
//! pointers; the indices below were extracted from AOSP's
//! `libnativehelper/include_jni/jni.h` (the 4 reserved slots included).
//!
//! `JNIEnv` is a `JNINativeInterface*`, and a native method receives a
//! `JNIEnv*` -- i.e. a pointer to that pointer.

const std = @import("std");

pub const jint = c_int;
pub const jboolean = u8;
pub const jlong = i64;
pub const jobject = ?*anyopaque;
pub const jclass = ?*anyopaque;
pub const jstring = ?*anyopaque;
pub const jmethodID = ?*anyopaque;

pub const JNINativeInterface = opaque {};
pub const JNIEnv = *const JNINativeInterface;

pub const JNI_FALSE: jboolean = 0;
pub const JNI_TRUE: jboolean = 1;

// --- JNINativeInterface slot numbers (AOSP jni.h) -------------------------
pub const idx = struct {
    pub const FindClass = 6;
    pub const ExceptionClear = 17;
    pub const NewGlobalRef = 21;
    pub const DeleteLocalRef = 23;
    pub const GetObjectClass = 31;
    pub const GetMethodID = 33;
    pub const CallObjectMethod = 34;
    pub const CallBooleanMethod = 37;
    pub const CallIntMethod = 49;
    pub const CallVoidMethod = 61;
    pub const CallStaticIntMethod = 129;
    pub const CallStaticVoidMethod = 141;
    pub const GetStaticMethodID = 113;
    pub const GetFieldID = 94;
    pub const GetLongField = 101;
    pub const NewStringUTF = 167;
    pub const GetStringUTFChars = 169;
    pub const ReleaseStringUTFChars = 170;
    pub const GetArrayLength = 171;
    pub const RegisterNatives = 211;
    pub const GetJavaVM = 215;
    pub const ExceptionCheck = 228;
};

pub const FindClassFn = *const fn (*JNIEnv, [*:0]const u8) callconv(.c) jclass;
pub const ExceptionClearFn = *const fn (*JNIEnv) callconv(.c) void;
pub const ExceptionCheckFn = *const fn (*JNIEnv) callconv(.c) jboolean;
pub const NewGlobalRefFn = *const fn (*JNIEnv, jobject) callconv(.c) jobject;
pub const DeleteLocalRefFn = *const fn (*JNIEnv, jobject) callconv(.c) void;
pub const GetObjectClassFn = *const fn (*JNIEnv, jobject) callconv(.c) jclass;
pub const GetMethodIDFn = *const fn (*JNIEnv, jclass, [*:0]const u8, [*:0]const u8) callconv(.c) jmethodID;
pub const GetStaticMethodIDFn = *const fn (*JNIEnv, jclass, [*:0]const u8, [*:0]const u8) callconv(.c) jmethodID;
pub const CallObjectMethodFn = *const fn (*JNIEnv, jobject, jmethodID, ...) callconv(.c) jobject;
pub const CallBooleanMethodFn = *const fn (*JNIEnv, jobject, jmethodID, ...) callconv(.c) jboolean;
pub const CallIntMethodFn = *const fn (*JNIEnv, jobject, jmethodID, ...) callconv(.c) jint;
pub const CallVoidMethodFn = *const fn (*JNIEnv, jobject, jmethodID, ...) callconv(.c) void;
pub const CallStaticVoidMethodFn = *const fn (*JNIEnv, jclass, jmethodID, ...) callconv(.c) void;
pub const NewStringUTFfn = *const fn (*JNIEnv, [*:0]const u8) callconv(.c) jstring;
pub const GetStringUTFCharsFn = *const fn (*JNIEnv, jstring, ?*jboolean) callconv(.c) ?[*:0]const u8;
pub const ReleaseStringUTFCharsFn = *const fn (*JNIEnv, jstring, [*:0]const u8) callconv(.c) void;
pub const GetArrayLengthFn = *const fn (*JNIEnv, jobject) callconv(.c) jint;

inline fn slot(env: *JNIEnv, comptime index: usize) ?*const anyopaque {
    const table: [*]const ?*const anyopaque = @ptrCast(@alignCast(env.*));
    return table[index];
}

/// Fetch a JNI function by table slot. Returns null if the host has no such
/// slot (never happens for the slots used here on Android).
pub inline fn get(env: *JNIEnv, comptime index: usize, comptime F: type) ?F {
    const p = slot(env, index) orelse return null;
    return @ptrCast(@alignCast(p));
}

pub fn findClass(env: *JNIEnv, name: [*:0]const u8) jclass {
    const f = get(env, idx.FindClass, FindClassFn) orelse return null;
    return f(env, name);
}

pub fn getObjectClass(env: *JNIEnv, obj: jobject) jclass {
    const f = get(env, idx.GetObjectClass, GetObjectClassFn) orelse return null;
    return f(env, obj);
}

pub fn getMethodID(env: *JNIEnv, cls: jclass, name: [*:0]const u8, sig: [*:0]const u8) jmethodID {
    const f = get(env, idx.GetMethodID, GetMethodIDFn) orelse return null;
    return f(env, cls, name, sig);
}

pub fn getStaticMethodID(env: *JNIEnv, cls: jclass, name: [*:0]const u8, sig: [*:0]const u8) jmethodID {
    const f = get(env, idx.GetStaticMethodID, GetStaticMethodIDFn) orelse return null;
    return f(env, cls, name, sig);
}

pub fn callIntMethod(env: *JNIEnv, obj: jobject, mid: jmethodID, args: anytype) jint {
    const f = get(env, idx.CallIntMethod, CallIntMethodFn) orelse return 0;
    return @call(.auto, f, .{ env, obj, mid } ++ args);
}

pub fn callObjectMethod(env: *JNIEnv, obj: jobject, mid: jmethodID, args: anytype) jobject {
    const f = get(env, idx.CallObjectMethod, CallObjectMethodFn) orelse return null;
    return @call(.auto, f, .{ env, obj, mid } ++ args);
}

pub fn callBooleanMethod(env: *JNIEnv, obj: jobject, mid: jmethodID, args: anytype) jboolean {
    const f = get(env, idx.CallBooleanMethod, CallBooleanMethodFn) orelse return 0;
    return @call(.auto, f, .{ env, obj, mid } ++ args);
}

pub fn callVoidMethod(env: *JNIEnv, obj: jobject, mid: jmethodID, args: anytype) void {
    const f = get(env, idx.CallVoidMethod, CallVoidMethodFn) orelse return;
    @call(.auto, f, .{ env, obj, mid } ++ args);
}

pub fn callStaticVoidMethod(env: *JNIEnv, cls: jclass, mid: jmethodID, args: anytype) void {
    const f = get(env, idx.CallStaticVoidMethod, CallStaticVoidMethodFn) orelse return;
    @call(.auto, f, .{ env, cls, mid } ++ args);
}

pub fn newGlobalRef(env: *JNIEnv, obj: jobject) jobject {
    const f = get(env, idx.NewGlobalRef, NewGlobalRefFn) orelse return null;
    return f(env, obj);
}

pub fn deleteLocalRef(env: *JNIEnv, obj: jobject) void {
    const f = get(env, idx.DeleteLocalRef, DeleteLocalRefFn) orelse return;
    f(env, obj);
}

pub fn exceptionCheck(env: *JNIEnv) bool {
    const f = get(env, idx.ExceptionCheck, ExceptionCheckFn) orelse return false;
    return f(env) != 0;
}

pub fn exceptionClear(env: *JNIEnv) void {
    const f = get(env, idx.ExceptionClear, ExceptionClearFn) orelse return;
    f(env);
}

pub fn getStringUTFChars(env: *JNIEnv, str: jstring) ?[*:0]const u8 {
    const f = get(env, idx.GetStringUTFChars, GetStringUTFCharsFn) orelse return null;
    return f(env, str, null);
}

pub fn releaseStringUTFChars(env: *JNIEnv, str: jstring, chars: [*:0]const u8) void {
    const f = get(env, idx.ReleaseStringUTFChars, ReleaseStringUTFCharsFn) orelse return;
    f(env, str, chars);
}

/// A string copied into `buf`. NUL terminated when it fits.
pub fn copyJavaString(env: *JNIEnv, str: jstring, buf: []u8) ?[]u8 {
    if (str == null or buf.len == 0) return null;
    const chars = getStringUTFChars(env, str) orelse return null;
    defer releaseStringUTFChars(env, str, chars);
    const s = std.mem.span(chars);
    const n = @min(s.len, buf.len - 1);
    @memcpy(buf[0..n], s[0..n]);
    buf[n] = 0;
    return buf[0..n];
}

/// JNINativeMethod, matching the C layout.
pub const JNINativeMethod = extern struct {
    name: [*:0]const u8,
    signature: [*:0]const u8,
    fn_ptr: ?*anyopaque,
};
