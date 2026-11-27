//! logcat output written straight to logd's `/dev/socket/logdw` socket.
//!
//! The module links against nothing (`DT_NEEDED` is empty), so it cannot import
//! `__android_log_write`. The wire format below is what liblog itself sends:
//! an 11-byte packed header, the priority byte, then `tag\0` and `message\0`.
const std = @import("std");
const linux = std.os.linux;

const socket_path = "/dev/socket/logdw";
const tag = "NoShot";

pub const PRIO_DEBUG: u8 = 3;
pub const PRIO_INFO: u8 = 4;
pub const PRIO_WARN: u8 = 5;
pub const PRIO_ERROR: u8 = 6;

var enabled: bool = true;

pub fn setEnabled(on: bool) void {
    enabled = on;
}

fn put(comptime T: type, buf: []u8, off: usize, v: T) void {
    std.mem.writeInt(T, buf[off..][0..@sizeOf(T)], v, .little);
}

fn send(prio: u8, text: []const u8) void {
    var path: [108]u8 = @splat(0);
    @memcpy(path[0..socket_path.len], socket_path);
    const addr: linux.sockaddr.un = .{ .family = linux.AF.UNIX, .path = path };

    const rc = linux.socket(linux.AF.UNIX, linux.SOCK.DGRAM | linux.SOCK.CLOEXEC | linux.SOCK.NONBLOCK, 0);
    if (linux.errno(rc) != .SUCCESS) return;
    const fd: linux.fd_t = @intCast(rc);
    defer _ = linux.close(fd);
    if (linux.errno(linux.connect(fd, @ptrCast(&addr), @sizeOf(linux.sockaddr.un))) != .SUCCESS) return;

    var ts: linux.timespec = undefined;
    _ = linux.clock_gettime(.REALTIME, &ts);

    var buf: [768 + 64]u8 = @splat(0);
    buf[0] = 0;
    put(u16, &buf, 1, @intCast(linux.gettid()));
    put(u32, &buf, 3, @intCast(ts.sec));
    put(u32, &buf, 7, @intCast(ts.nsec));
    buf[11] = prio;
    @memcpy(buf[12 .. 12 + tag.len], tag);
    const body = 13 + tag.len;
    const len = @min(text.len, buf.len - body - 1);
    @memcpy(buf[body .. body + len], text[0..len]);
    _ = linux.sendto(fd, &buf, body + len + 1, 0, null, 0);
}

fn emit(prio: u8, comptime fmt: []const u8, args: anytype) void {
    if (!enabled) return;
    var buf: [768]u8 = undefined;
    const msg = std.fmt.bufPrint(&buf, fmt, args) catch return;
    send(prio, msg);
}

pub fn debug(comptime fmt: []const u8, args: anytype) void {
    emit(PRIO_DEBUG, fmt, args);
}

pub fn info(comptime fmt: []const u8, args: anytype) void {
    emit(PRIO_INFO, fmt, args);
}

pub fn warn(comptime fmt: []const u8, args: anytype) void {
    emit(PRIO_WARN, fmt, args);
}

pub fn err(comptime fmt: []const u8, args: anytype) void {
    emit(PRIO_ERROR, fmt, args);
}
