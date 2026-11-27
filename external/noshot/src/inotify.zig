//! Screenshot-detection guard.
//!
//! On Android 9..13 the common detection trick is a FileObserver on the
//! screenshots directory (WhatsApp, Snapchat, ...). The watch is created via
//! `inotify_add_watch` inside libandroid_runtime's FileObserver native code.
//! We PLT-hook that one import and refuse watches whose path mentions
//! "screenshot". Everything else passes through untouched.
//!
//! Android 14+ added the official screen-capture callback APIs; those are
//! backed by binder callbacks and are not covered here.

const std = @import("std");
const linux = std.os.linux;
const fs = @import("fs.zig");
const api = @import("api.zig");
const log = @import("log.zig");

const OrigFn = *const fn ([*:0]const u8, u32) callconv(.c) c_int;

var orig: ?*anyopaque = null;
var installed: bool = false;
var denied: usize = 0;

fn makedev(major: u64, minor: u64) usize {
    const d: u64 = ((major & 0xfffff000) << 32) |
        ((major & 0x0fff) << 8) |
        ((minor & 0xffffff00) << 12) |
        (minor & 0x000000ff);
    return @intCast(d);
}

const DevIno = struct { dev: usize, ino: usize };

/// Find the dev/inode pair of a mapped ELF in /proc/self/maps.
fn findMapped(alloc: std.mem.Allocator, needle: []const u8) ?DevIno {
    const maps = fs.readAlloc(alloc, "/proc/self/maps", 4 << 20) catch return null;
    if (maps == null) return null;
    defer alloc.free(maps.?);

    var lines = std.mem.splitScalar(u8, maps.?, '\n');
    while (lines.next()) |line| {
        const end = std.mem.indexOfScalar(u8, line, '/') orelse continue;
        const path = line[end..];
        if (!std.mem.endsWith(u8, path, needle)) continue;

        // start-end perms offset dev inode path
        var it = std.mem.tokenizeScalar(u8, line[0..end], ' ');
        _ = it.next() orelse continue; // addr
        _ = it.next() orelse continue; // perms
        _ = it.next() orelse continue; // offset
        const dev_s = it.next() orelse continue;
        const ino_s = it.next() orelse continue;

        const colon = std.mem.indexOfScalar(u8, dev_s, ':') orelse continue;
        const major = std.fmt.parseInt(u64, dev_s[0..colon], 16) catch continue;
        const minor = std.fmt.parseInt(u64, dev_s[colon + 1 ..], 16) catch continue;
        const ino = std.fmt.parseInt(u64, ino_s, 10) catch continue;
        return .{ .dev = makedev(major, minor), .ino = @intCast(ino) };
    }
    return null;
}

pub fn install(api_table: *api.ApiTable, alloc: std.mem.Allocator) bool {
    if (installed) return true;
    const register = api_table.plt_hook_register orelse {
        log.warn("PLT hook API unavailable (api<4 host), detect guard off", .{});
        return false;
    };

    const target = findMapped(alloc, "/libandroid_runtime.so") orelse {
        log.warn("libandroid_runtime.so not mapped, detect guard off", .{});
        return false;
    };

    register(target.dev, target.ino, "inotify_add_watch", @ptrCast(@constCast(&hookInotifyAddWatch)), &orig);
    installed = true;
    log.info("PLT hook registered: inotify_add_watch (dev={x} ino={d})", .{ target.dev, target.ino });
    return true;
}

fn containsScreenshot(path: []const u8) bool {
    const needle = "screenshot";
    if (path.len < needle.len) return false;
    var i: usize = 0;
    while (i + needle.len <= path.len) : (i += 1) {
        var ok = true;
        for (needle, 0..) |c, j| {
            if (std.ascii.toLower(path[i + j]) != c) {
                ok = false;
                break;
            }
        }
        if (ok) return true;
    }
    return false;
}

fn hookInotifyAddWatch(path: [*:0]const u8, mask: u32) callconv(.c) c_int {
    const p = std.mem.span(path);
    if (containsScreenshot(p)) {
        denied += 1;
        if (denied <= 8) log.info("screenshot watch denied: {s}", .{p});
        return -1;
    }
    const orig_fn: OrigFn = @ptrCast(@alignCast(orig orelse return -1));
    return orig_fn(path, mask);
}

pub fn isInstalled() bool {
    return installed;
}

/// exported for the on-device selftest binary
pub fn selftestContains(path: []const u8) bool {
    return containsScreenshot(path);
}
