//! Tiny syscall-only file helpers.
//!
//! The module is built without libc: every syscall is issued through
//! `std.os.linux`, so the resulting `.so` has no DT_NEEDED entries and can be
//! injected into any Android process (32/64 bit, bionic).

const std = @import("std");
const linux = std.os.linux;

pub const max_path = 512;

pub const Error = error{
    NameTooLong,
    OpenFailed,
    ReadFailed,
    WriteFailed,
    TooBig,
    OutOfMemory,
};

fn toZ(path: []const u8, buf: *[max_path]u8) Error![*:0]const u8 {
    if (path.len >= buf.len) return error.NameTooLong;
    @memcpy(buf[0..path.len], path);
    buf[path.len] = 0;
    return @ptrCast(buf);
}

pub fn openRead(path: []const u8) Error!linux.fd_t {
    var zbuf: [max_path]u8 = undefined;
    const z = try toZ(path, &zbuf);
    const rc = linux.openat(linux.AT.FDCWD, z, .{ .ACCMODE = .RDONLY, .CLOEXEC = true }, 0);
    if (linux.errno(rc) != .SUCCESS) return error.OpenFailed;
    return @intCast(rc);
}

pub fn close(fd: linux.fd_t) void {
    _ = linux.close(fd);
}

/// Create or truncate `path` and write `bytes`. Used for the small state files
/// the module keeps for itself.
pub fn writeFile(path: []const u8, bytes: []const u8) bool {
    var zbuf: [max_path]u8 = undefined;
    const z = toZ(path, &zbuf) catch return false;
    const rc = linux.openat(linux.AT.FDCWD, z, .{ .ACCMODE = .WRONLY, .CREAT = true, .TRUNC = true, .CLOEXEC = true }, 0o644);
    if (linux.errno(rc) != .SUCCESS) return false;
    const fd: linux.fd_t = @intCast(rc);
    defer close(fd);
    return writeAllFd(fd, bytes);
}

pub fn exists(path: []const u8) bool {
    const fd = openRead(path) catch return false;
    close(fd);
    return true;
}

/// Append diagnostics without disturbing existing content.
pub fn appendFile(path: []const u8, bytes: []const u8) bool {
    var zbuf: [max_path]u8 = undefined;
    const z = toZ(path, &zbuf) catch return false;
    const rc = linux.openat(linux.AT.FDCWD, z, .{ .ACCMODE = .WRONLY, .CREAT = true, .APPEND = true, .CLOEXEC = true }, 0o644);
    if (linux.errno(rc) != .SUCCESS) return false;
    const fd: linux.fd_t = @intCast(rc);
    defer close(fd);
    return writeAllFd(fd, bytes);
}

/// Read up to `max` bytes into a newly allocated slice.
/// Returns null when the file cannot be opened.
pub fn readAlloc(alloc: std.mem.Allocator, path: []const u8, max: usize) (Error || std.mem.Allocator.Error)!?[]u8 {
    const fd = openRead(path) catch |e| switch (e) {
        error.OpenFailed => return null,
        else => return e,
    };
    defer close(fd);

    var list: std.ArrayListUnmanaged(u8) = .empty;
    errdefer list.deinit(alloc);
    var buf: [8192]u8 = undefined;
    while (true) {
        const rc = linux.read(fd, &buf, buf.len);
        const e = linux.errno(rc);
        if (e == .INTR) continue;
        if (e != .SUCCESS) return error.ReadFailed;
        const n: usize = rc;
        if (n == 0) break;
        if (list.items.len + n > max) return error.TooBig;
        try list.appendSlice(alloc, buf[0..n]);
    }
    return try list.toOwnedSlice(alloc);
}

/// Read a small file into `out`; returns the valid slice or null.
pub fn readFixed(path: []const u8, out: []u8) ?[]u8 {
    const fd = openRead(path) catch return null;
    defer close(fd);
    var n: usize = 0;
    while (n < out.len) {
        const rc = linux.read(fd, out.ptr + n, out.len - n);
        const e = linux.errno(rc);
        if (e == .INTR) continue;
        if (e != .SUCCESS) return null;
        const got: usize = rc;
        if (got == 0) break;
        n += got;
    }
    return out[0..n];
}

pub fn writeAllFd(fd: linux.fd_t, bytes: []const u8) bool {
    var off: usize = 0;
    while (off < bytes.len) {
        const rc = linux.write(fd, bytes.ptr + off, bytes.len - off);
        const e = linux.errno(rc);
        if (e == .INTR) continue;
        if (e != .SUCCESS) return false;
        const n: usize = rc;
        if (n == 0) return false;
        off += n;
    }
    return true;
}

/// Read exactly `buf.len` bytes unless EOF/error; returns bytes read.
pub fn readSome(fd: linux.fd_t, buf: []u8) usize {
    var n: usize = 0;
    while (n < buf.len) {
        const rc = linux.read(fd, buf.ptr + n, buf.len - n);
        const e = linux.errno(rc);
        if (e == .INTR) continue;
        if (e != .SUCCESS) break;
        const got: usize = rc;
        if (got == 0) break;
        n += got;
    }
    return n;
}

/// Modification time in nanoseconds, or null if statx fails.
pub fn mtimeNanos(path: []const u8) ?i128 {
    var zbuf: [max_path]u8 = undefined;
    const z = toZ(path, &zbuf) catch return null;
    var st: linux.Statx = undefined;
    var mask = linux.STATX{};
    mask.MTIME = true;
    const rc = linux.statx(linux.AT.FDCWD, z, 0, mask, &st);
    if (linux.errno(rc) != .SUCCESS) return null;
    return @as(i128, st.mtime.sec) * std.time.ns_per_s + @as(i128, st.mtime.nsec);
}
