//! `allow.list` / `settings.conf` parsing.
//!
//! The on-disk shape is line based so both sides can share a file:
//!
//!     # noshot:1:2
//!     # ui:com.example.app|u0
//!     com.example.app
//!     com.example.bank
//!
//! Lines starting with '#' are comments. `# noshot:<ver>` marks a deliberately
//! written file (an empty one included), and `# noshot:<ver>:<n>` additionally
//! declares how many entries were written so a truncated write can be
//! detected. `# ui:` lines carry the WebUI's per-user selection and are
//! ignored by the native side.
//!
//! Besides plain package names we accept `pkg|u999` for a per-user rule and
//! `pkg|client` / `pkg|u999|client` to keep that app on the in-process hook.
//! The React UI writes plain names by default.

const std = @import("std");

pub const max_config_bytes = 1 << 20;
pub const max_system_uids = 512;
pub const max_system_pkgs = 64;
pub const max_pkg_bytes = 96;

pub const target_paths = [_][]const u8{
    "/data/adb/ksu/noshot/allow.list",
    "/data/adb/noshot_zygisk/allow.list",
    "/data/adb/modules/noshot_zygisk/allow.list",
};

pub const settings_paths = [_][]const u8{
    "/data/adb/ksu/noshot/settings.conf",
    "/data/adb/noshot_zygisk/settings.conf",
    "/data/adb/modules/noshot_zygisk/settings.conf",
};

/// State files system_server can read and write on its own.
pub const mode_path = "/data/system/noshot_mode";
/// Selection resolved by the root side (uid list), the only copy system_server
/// is able to read.
pub const plan_path = "/data/system/noshot_plan";
/// system_server's state copied where app processes can read it. Same fallback
/// order as the config files: some ROMs deny zygote access to /data/adb.
pub const app_mode_paths = [_][]const u8{
    "/data/system/noshot_mode",
    "/data/adb/ksu/noshot/mode",
    "/data/adb/noshot_zygisk/mode",
    "/data/adb/modules/noshot_zygisk/mode",
};
pub const attempt_path = "/data/system/noshot_attempt";
pub const disable_path = "/data/system/noshot_disable";

/// User-visible switch; system_server cannot read /data/adb, the companion
/// folds it into the `hook` flag it reports.
pub const user_disable_path = "/data/adb/ksu/noshot/disable_system";

pub const FLAG_SECURE: i32 = 0x2000;

pub const Target = struct {
    pkg: []const u8,
    /// null = all users, otherwise the Android user id (e.g. 0 or 999)
    user: ?u32 = null,
    mode: Mode = .system,
};

pub const Mode = enum { system, client };

pub const Settings = struct {
    flag_secure: bool = true,
    surface_secure: bool = true,
    detect_guard: bool = true,
    system_hook: bool = true,
    log: bool = true,
    /// Capture/recording callback handling on the system side.
    capture_guard: CaptureGuard = .off,
};

/// 0/off, `observe` (count only), 1/on (drop the notifications).
pub const CaptureGuard = enum {
    off,
    observe,
    on,

    pub fn parse(v: []const u8) CaptureGuard {
        if (std.ascii.eqlIgnoreCase(v, "observe")) return .observe;
        const on = std.mem.eql(u8, v, "1") or std.ascii.eqlIgnoreCase(v, "true") or std.ascii.eqlIgnoreCase(v, "on");
        return if (on) .on else .off;
    }
};

/// `SYSCONF` reply from the root companion: the resolved inputs of the
/// system_server side. Parsed into fixed storage, never allocated.
pub const SystemConf = struct {
    hook: bool = true,
    log: bool = true,
    gen: i64 = 0,
    capture: CaptureGuard = .off,
    uid_count: usize = 0,
    pkg_count: usize = 0,
    uids: [max_system_uids]u32 = undefined,
    pkgs: [max_system_pkgs][max_pkg_bytes]u8 = undefined,
    pkg_lens: [max_system_pkgs]u8 = @splat(0),

    pub fn pkgAt(self: *const SystemConf, i: usize) []const u8 {
        return self.pkgs[i][0..self.pkg_lens[i]];
    }

    pub fn addUid(self: *SystemConf, uid: u32) void {
        if (self.uid_count == self.uids.len) return;
        for (self.uids[0..self.uid_count]) |u| if (u == uid) return;
        self.uids[self.uid_count] = uid;
        self.uid_count += 1;
    }

    pub fn addPkg(self: *SystemConf, pkg: []const u8) void {
        if (self.pkg_count == self.pkgs.len or pkg.len == 0 or pkg.len > max_pkg_bytes) return;
        for (0..self.pkg_count) |i| {
            if (std.mem.eql(u8, self.pkgAt(i), pkg)) return;
        }
        @memcpy(self.pkgs[self.pkg_count][0..pkg.len], pkg);
        self.pkg_lens[self.pkg_count] = @intCast(pkg.len);
        self.pkg_count += 1;
    }

    pub fn hasUid(self: *const SystemConf, uid: u32) bool {
        for (self.uids[0..self.uid_count]) |u| if (u == uid) return true;
        return false;
    }

    /// Window layer names carry "<pkg>/<activity>"; used when the metadata
    /// owner is unavailable.
    pub fn nameMatches(self: *const SystemConf, name: []const u8) bool {
        for (0..self.pkg_count) |i| {
            const p = self.pkgAt(i);
            if (std.mem.indexOf(u8, name, p)) |at| {
                const end = at + p.len;
                if (end == name.len or name[end] == '/' or name[end] == ' ' or name[end] == ':') return true;
            }
        }
        return false;
    }
};

pub fn parseSystemConf(out: *SystemConf, text: []const u8) void {
    out.hook = true;
    out.log = true;
    out.gen = 0;
    out.capture = .off;
    out.uid_count = 0;
    out.pkg_count = 0;
    var lines = std.mem.splitScalar(u8, text, '\n');
    while (lines.next()) |raw| {
        const line = std.mem.trim(u8, raw, " \t\r");
        const eq = std.mem.indexOfScalar(u8, line, '=') orelse continue;
        const key = line[0..eq];
        const val = line[eq + 1 ..];
        if (std.mem.eql(u8, key, "hook")) {
            out.hook = std.mem.eql(u8, val, "1");
        } else if (std.mem.eql(u8, key, "log")) {
            out.log = std.mem.eql(u8, val, "1");
        } else if (std.mem.eql(u8, key, "capture")) {
            out.capture = CaptureGuard.parse(val);
        } else if (std.mem.eql(u8, key, "gen")) {
            out.gen = std.fmt.parseInt(i64, val, 10) catch 0;
        } else if (std.mem.eql(u8, key, "uid")) {
            if (std.fmt.parseInt(u32, val, 10) catch null) |uid| out.addUid(uid);
        } else if (std.mem.eql(u8, key, "pkg")) {
            if (isPackageName(val)) out.addPkg(val);
        }
    }
}

pub const ParseResult = struct {
    targets: []Target,
    valid: bool,
    magic: bool,
    declared: ?usize = null,
    counted: usize = 0,
};

pub fn isPackageName(s: []const u8) bool {
    if (s.len == 0 or s.len > 255) return false;
    var dots: usize = 0;
    var prev_dot = true; // disallow leading '.'
    for (s) |c| {
        if (c == '.') {
            if (prev_dot) return false;
            prev_dot = true;
            dots += 1;
        } else if (c == '_' or (c >= '0' and c <= '9') or (c >= 'a' and c <= 'z') or (c >= 'A' and c <= 'Z')) {
            prev_dot = false;
        } else {
            return false;
        }
    }
    return dots >= 1 and !prev_dot;
}

fn parseUserSuffix(rest: []const u8) ?u32 {
    // rest is the part after '|', e.g. "u999"
    if (rest.len < 2 or rest[0] != 'u') return null;
    return std.fmt.parseInt(u32, rest[1..], 10) catch null;
}

/// Parse an `allow.list` body. Slices in the result point into `text`.
pub fn parseTargets(alloc: std.mem.Allocator, text: []const u8) !ParseResult {
    var list: std.ArrayListUnmanaged(Target) = .empty;
    errdefer list.deinit(alloc);

    var magic = false;
    var declared: ?usize = null;
    var counted: usize = 0;

    var lines = std.mem.splitScalar(u8, text, '\n');
    while (lines.next()) |raw| {
        const line = std.mem.trim(u8, raw, " \t\r");
        if (line.len == 0) continue;

        if (std.mem.startsWith(u8, line, "#")) {
            if (std.mem.startsWith(u8, line, "# ui:")) {
                // The WebUI writes one "# ui:<pkg>|u<id>" line per selection
                // and includes those lines in the magic counter.
                counted += 1;
            } else if (std.mem.startsWith(u8, line, "# noshot:")) {
                magic = true;
                const rest = line["# noshot:".len..];
                if (std.mem.indexOfScalar(u8, rest, ':')) |ci| {
                    declared = std.fmt.parseInt(usize, rest[ci + 1 ..], 10) catch null;
                }
            }
            continue;
        }

        var pkg = line;
        var user: ?u32 = null;
        var mode: Mode = .system;
        if (std.mem.indexOfScalar(u8, line, '|')) |bar| {
            pkg = std.mem.trim(u8, line[0..bar], " \t");
            var rest = line[bar + 1 ..];
            while (rest.len > 0) {
                const next = std.mem.indexOfScalar(u8, rest, '|');
                const field = std.mem.trim(u8, if (next) |n| rest[0..n] else rest, " \t");
                rest = if (next) |n| rest[n + 1 ..] else "";
                if (field.len == 0) continue;
                if (std.ascii.eqlIgnoreCase(field, "client")) {
                    mode = .client;
                } else if (parseUserSuffix(field)) |u| {
                    user = u;
                } else {
                    pkg = "";
                    break;
                }
            }
        }
        if (!isPackageName(pkg)) continue;

        try list.append(alloc, .{ .pkg = pkg, .user = user, .mode = mode });
        counted += 1;
    }

    const count_ok = if (declared) |d| d == counted else true;
    const valid = (magic or list.items.len > 0) and count_ok;
    return .{
        .targets = try list.toOwnedSlice(alloc),
        .valid = valid,
        .magic = magic,
        .declared = declared,
        .counted = counted,
    };
}

pub fn parseSettings(text: []const u8) Settings {
    var s = Settings{};
    var lines = std.mem.splitScalar(u8, text, '\n');
    while (lines.next()) |raw| {
        const line = std.mem.trim(u8, raw, " \t\r");
        if (line.len == 0 or line[0] == '#') continue;
        const eq = std.mem.indexOfScalar(u8, line, '=') orelse continue;
        const key = std.mem.trim(u8, line[0..eq], " \t");
        const val = std.mem.trim(u8, line[eq + 1 ..], " \t");
        const on = std.mem.eql(u8, val, "1") or std.ascii.eqlIgnoreCase(val, "true") or std.ascii.eqlIgnoreCase(val, "on");
        if (std.mem.eql(u8, key, "flag_secure")) {
            s.flag_secure = on;
        } else if (std.mem.eql(u8, key, "surface_secure")) {
            s.surface_secure = on;
        } else if (std.mem.eql(u8, key, "detect_guard")) {
            s.detect_guard = on;
        } else if (std.mem.eql(u8, key, "system_hook")) {
            s.system_hook = on;
        } else if (std.mem.eql(u8, key, "log")) {
            s.log = on;
        } else if (std.mem.eql(u8, key, "capture_guard")) {
            s.capture_guard = CaptureGuard.parse(val);
        }
    }
    return s;
}

/// Package part of a zygote process name: "com.foo:remote" -> "com.foo".
pub fn packageOf(process_name: []const u8) []const u8 {
    if (std.mem.indexOfScalar(u8, process_name, ':')) |i| return process_name[0..i];
    return process_name;
}

pub fn userIdOf(uid: i32) u32 {
    if (uid < 0) return 0;
    const u: u32 = @intCast(uid);
    return u / 100000;
}

pub fn matches(targets: []const Target, pkg: []const u8, uid: i32) bool {
    return modeOf(targets, pkg, uid) != null;
}

/// Patch mode of the first matching rule; null when the app is not selected.
pub fn modeOf(targets: []const Target, pkg: []const u8, uid: i32) ?Mode {
    const user = userIdOf(uid);
    for (targets) |t| {
        if (!std.mem.eql(u8, t.pkg, pkg)) continue;
        if (t.user) |tu| {
            if (tu != user) continue;
        }
        return t.mode;
    }
    return null;
}

// ---------------------------------------------------------------------------
// tests (run with: zig test src/config.zig)
// ---------------------------------------------------------------------------

test "parses packages, comments and magic" {
    const text =
        \\# noshot:1:4
        \\# ui:com.a|u0
        \\com.a
        \\com.b.c
        \\# random comment
        \\com.d_e.f1
    ;
    const r = try parseTargets(std.testing.allocator, text);
    defer std.testing.allocator.free(r.targets);
    try std.testing.expect(r.valid);
    try std.testing.expectEqual(@as(usize, 3), r.targets.len);
    try std.testing.expectEqualStrings("com.b.c", r.targets[1].pkg);
    try std.testing.expect(r.targets[1].user == null);
}

test "rejects truncated write (declared count mismatch)" {
    const text =
        \\# noshot:1:4
        \\com.a
        \\com.b
    ;
    const r = try parseTargets(std.testing.allocator, text);
    defer std.testing.allocator.free(r.targets);
    try std.testing.expect(!r.valid);
}

test "empty but deliberate file is valid" {
    const r = try parseTargets(std.testing.allocator, "# noshot:1:0\n");
    defer std.testing.allocator.free(r.targets);
    try std.testing.expect(r.valid);
    try std.testing.expectEqual(@as(usize, 0), r.targets.len);
}

test "per-user rule" {
    const r = try parseTargets(std.testing.allocator, "# noshot:1:1\ncom.bank|u999\n");
    defer std.testing.allocator.free(r.targets);
    try std.testing.expect(r.valid);
    try std.testing.expectEqual(@as(?u32, 999), r.targets[0].user);
    try std.testing.expect(matches(r.targets, "com.bank", 999 * 100000 + 10123));
    try std.testing.expect(!matches(r.targets, "com.bank", 0 + 10123));
}

test "real world config from the original WebUI" {
    const text =
        \\# noshot:1:2
        \\# ui:com.google.android.apps.authenticator2|u0
        \\com.google.android.apps.authenticator2
    ;
    const r = try parseTargets(std.testing.allocator, text);
    defer std.testing.allocator.free(r.targets);
    try std.testing.expect(r.valid);
    try std.testing.expectEqual(@as(usize, 1), r.targets.len);
}

test "invalid package names are ignored" {
    try std.testing.expect(!isPackageName(""));
    try std.testing.expect(!isPackageName("nodots"));
    try std.testing.expect(!isPackageName("com..a"));
    try std.testing.expect(!isPackageName("com.a b"));
    try std.testing.expect(isPackageName("com.example.app"));
}

test "process name package extraction" {
    try std.testing.expectEqualStrings("com.foo", packageOf("com.foo:remote"));
    try std.testing.expectEqualStrings("com.foo", packageOf("com.foo"));
}

test "settings" {
    const s = parseSettings("# cfg\nflag_secure=0\nsurface_secure=true\ndetect_guard=off\nlog=1\n");
    try std.testing.expect(!s.flag_secure);
    try std.testing.expect(s.surface_secure);
    try std.testing.expect(!s.detect_guard);
    try std.testing.expect(s.log);
    try std.testing.expect(s.system_hook);
    try std.testing.expect(!parseSettings("system_hook=0\n").system_hook);
}

test "capture guard off by default" {
    try std.testing.expectEqual(CaptureGuard.off, parseSettings("").capture_guard);
    try std.testing.expectEqual(CaptureGuard.observe, parseSettings("capture_guard=observe\n").capture_guard);
    try std.testing.expectEqual(CaptureGuard.on, parseSettings("capture_guard=1\n").capture_guard);
    try std.testing.expectEqual(CaptureGuard.on, parseSettings("capture_guard=on\n").capture_guard);
    try std.testing.expectEqual(CaptureGuard.off, parseSettings("capture_guard=0\n").capture_guard);
    try std.testing.expectEqual(CaptureGuard.off, parseSettings("capture_guard=bogus\n").capture_guard);
}

test "plan carries the capture guard" {
    var c: SystemConf = undefined;
    parseSystemConf(&c, "hook=1\ncapture=observe\ngen=7\n");
    try std.testing.expectEqual(CaptureGuard.observe, c.capture);
    parseSystemConf(&c, "hook=1\n");
    try std.testing.expectEqual(CaptureGuard.off, c.capture);
}

test "per-app patch mode" {
    const text =
        \\# noshot:2:3
        \\com.plain
        \\com.bank|u999|client
        \\com.player|client
    ;
    const r = try parseTargets(std.testing.allocator, text);
    defer std.testing.allocator.free(r.targets);
    try std.testing.expect(r.valid);
    try std.testing.expectEqual(@as(usize, 3), r.targets.len);
    try std.testing.expectEqual(Mode.system, r.targets[0].mode);
    try std.testing.expectEqual(Mode.client, r.targets[1].mode);
    try std.testing.expectEqual(@as(?u32, 999), r.targets[1].user);
    try std.testing.expectEqual(Mode.client, r.targets[2].mode);
    try std.testing.expectEqual(@as(?Mode, .client), modeOf(r.targets, "com.player", 10123));
    try std.testing.expectEqual(@as(?Mode, .system), modeOf(r.targets, "com.plain", 10123));
    try std.testing.expectEqual(@as(?Mode, null), modeOf(r.targets, "com.other", 10123));
}

test "unknown rule suffix drops the line" {
    const r = try parseTargets(std.testing.allocator, "# noshot:1:0\ncom.a|bogus\n");
    defer std.testing.allocator.free(r.targets);
    try std.testing.expectEqual(@as(usize, 0), r.targets.len);
    try std.testing.expect(r.valid);
}

test "companion system config" {
    var c: SystemConf = undefined;
    parseSystemConf(&c, "hook=1\ngen=42\nuid=10123\nuid=10456\nuid=10123\npkg=com.bank\n");
    try std.testing.expect(c.hook);
    try std.testing.expectEqual(@as(i64, 42), c.gen);
    try std.testing.expectEqual(@as(usize, 2), c.uid_count);
    try std.testing.expect(c.hasUid(10456));
    try std.testing.expect(!c.hasUid(999));
    try std.testing.expectEqual(@as(usize, 1), c.pkg_count);
    try std.testing.expect(c.nameMatches("bank:com.bank/com.bank.Main"));
    try std.testing.expect(!c.nameMatches("com.banking.fake"));
    try std.testing.expect(!parseSystemConfHookOff());
}

fn parseSystemConfHookOff() bool {
    var c: SystemConf = undefined;
    parseSystemConf(&c, "hook=0\n");
    return c.hook;
}
