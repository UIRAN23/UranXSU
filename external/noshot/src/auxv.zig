//! A local `getauxval` so the module can use `std.heap.page_allocator` without
//! importing libc: `DT_NEEDED` stays empty, and the symbol is hidden so it
//! cannot shadow the process' own libc.
const std = @import("std");
const linux = std.os.linux;

const at_pagesz = 6;
const default_page = 4096;

pub fn impl(index: usize) callconv(.c) usize {
    var buf: [512]u8 = undefined;
    const rc = linux.open("/proc/self/auxv", .{ .ACCMODE = .RDONLY, .CLOEXEC = true }, 0);
    if (linux.errno(rc) == .SUCCESS) {
        const fd: linux.fd_t = @intCast(rc);
        defer _ = linux.close(fd);
        var off: usize = 0;
        while (off + 16 <= buf.len) {
            const n = linux.read(fd, buf[off..].ptr, buf.len - off);
            if (linux.errno(n) != .SUCCESS) break;
            if (n == 0) break;
            off += n;
        }
        var i: usize = 0;
        while (i + 16 <= off) : (i += 16) {
            const key = std.mem.readInt(usize, buf[i..][0..@sizeOf(usize)], .little);
            if (key == 0) break;
            if (key == index) return std.mem.readInt(usize, buf[i + @sizeOf(usize) ..][0..@sizeOf(usize)], .little);
        }
    }
    return if (index == at_pagesz) default_page else 0;
}
