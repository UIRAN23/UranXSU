//! On-device selftest. Reads /data/local/tmp/noshot_selftest.list first, then
//! the live config, and validates the parcel heuristic.
const std = @import("std");
const linux = std.os.linux;
const cfg = @import("config.zig");
const outbound = @import("outbound.zig");

fn readFile(alloc: std.mem.Allocator, path: []const u8) ![]u8 {
    var zbuf: [512]u8 = undefined;
    if (path.len >= zbuf.len) return error.NameTooLong;
    @memcpy(zbuf[0..path.len], path);
    zbuf[path.len] = 0;
    const rc = linux.openat(linux.AT.FDCWD, @ptrCast(&zbuf), .{ .ACCMODE = .RDONLY, .CLOEXEC = true }, 0);
    if (linux.errno(rc) != .SUCCESS) return error.OpenFailed;
    const fd: linux.fd_t = @intCast(rc);
    defer _ = linux.close(fd);
    var list: std.ArrayListUnmanaged(u8) = .empty;
    defer list.deinit(alloc);
    var buf: [4096]u8 = undefined;
    while (true) {
        const r = linux.read(fd, &buf, buf.len);
        if (linux.errno(r) != .SUCCESS) return error.ReadFailed;
        if (r == 0) break;
        try list.appendSlice(alloc, buf[0..@intCast(r)]);
    }
    return alloc.dupe(u8, list.items);
}

fn looksLikeLayoutParams(v: [7]i32) bool {
    if (v[0] != 1) return false;
    const ok_dim = struct {
        fn f(n: i32) bool {
            return n == -1 or n == -2 or (n >= 0 and n <= 32768);
        }
    }.f;
    if (!ok_dim(v[1]) or !ok_dim(v[2])) return false;
    if (v[3] < -1_000_000 or v[3] > 1_000_000) return false;
    if (v[4] < -1_000_000 or v[4] > 1_000_000) return false;
    if (v[5] < 1 or v[5] > 9999) return false;
    if ((v[6] & 0x2000) == 0) return false;
    return true;
}

pub fn main() !void {
    const alloc = std.heap.page_allocator;
    const paths = [_][]const u8{
        "/data/local/tmp/noshot_selftest.list",
        "/data/adb/noshot_zygisk/allow.list",
    };
    for (paths) |path| {
        if (readFile(alloc, path)) |data| {
            defer alloc.free(data);
            const r = try cfg.parseTargets(alloc, data);
            defer alloc.free(r.targets);
            std.debug.print("file={s} bytes={d} valid={} targets={d} declared={any}\n", .{ path, data.len, r.valid, r.targets.len, r.declared });
            for (r.targets, 0..) |t, i| {
                if (i >= 12) break;
                std.debug.print("  [{d}] {s} user={any}\n", .{ i, t.pkg, t.user });
            }
        } else |e| {
            std.debug.print("read {s} failed: {any}\n", .{ path, e });
        }
    }
    const good = [7]i32{ 1, -1, -1, 0, 0, 1, 0x2000 | 0x400 };
    const bad = [7]i32{ 1, -1, -1, 0, 0, 1, 0x400 };
    std.debug.print("parcel good={} bad={}\n", .{ looksLikeLayoutParams(good), looksLikeLayoutParams(bad) });

    // The callback matcher is pure, so it can be checked without a capture.
    var parcel: [160]u8 = @splat(0);
    writeToken(&parcel, 12, "android.window.IScreenRecordingCallback");
    std.mem.writeInt(i32, parcel[96..100], 1, .little);
    const found = outbound.locateIn(&parcel);
    std.debug.print("callback matcher recording={} value={d}\n", .{
        found != null and found.?.kind == .recording,
        if (found) |f| f.value else -1,
    });
    std.debug.print("selftest done\n", .{});
}

fn writeToken(buf: []u8, slot: usize, text: []const u8) void {
    std.mem.writeInt(i32, buf[slot..][0..4], @intCast(text.len), .little);
    for (text, 0..) |c, i| {
        std.mem.writeInt(u16, buf[slot + 4 + i * 2 ..][0..2], c, .little);
    }
}
