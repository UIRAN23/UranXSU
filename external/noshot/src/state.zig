//! Boot-time diagnostics shared by the system_server hooks.
//!
//! logcat is not reachable from every context, so decisions and probe hits are
//! also appended here (bounded, first lines only).

const std = @import("std");
const fs = @import("fs.zig");

pub const path = "/data/system/noshot_state";
const limit = 8 * 1024;

var written: usize = 0;

pub fn note(comptime fmt: []const u8, args: anytype) void {
    if (written >= limit) return;
    var buf: [256]u8 = undefined;
    const text = std.fmt.bufPrint(&buf, fmt ++ "\n", args) catch return;
    if (fs.appendFile(path, text)) written += text.len;
}
