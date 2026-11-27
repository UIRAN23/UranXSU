const std = @import("std");

const Target = struct {
    query: std.Target.Query,
    abi: []const u8,
};

const targets = [_]Target{
    .{ .query = .{ .cpu_arch = .aarch64, .os_tag = .linux, .abi = .android }, .abi = "arm64-v8a" },
    .{ .query = .{ .cpu_arch = .arm, .os_tag = .linux, .abi = .android, .cpu_model = .{ .explicit = &std.Target.arm.cpu.cortex_a7 } }, .abi = "armeabi-v7a" },
    .{ .query = .{ .cpu_arch = .x86_64, .os_tag = .linux, .abi = .android }, .abi = "x86_64" },
    .{ .query = .{ .cpu_arch = .x86, .os_tag = .linux, .abi = .android, .cpu_model = .{ .explicit = &std.Target.x86.cpu.i686 } }, .abi = "x86" },
};

pub fn build(b: *std.Build) void {
    const optimize: std.builtin.OptimizeMode = .ReleaseSmall;
    const install = b.getInstallStep();

    // One dynamic library per Android ABI, installed as zygisk/<abi>.so.
    for (targets) |t| {
        const resolved = b.resolveTargetQuery(t.query);
        const mod = b.createModule(.{
            .root_source_file = b.path("src/root.zig"),
            .target = resolved,
            .optimize = optimize,
            .strip = true,
        });
        const lib = b.addLibrary(.{
            .name = "noshot",
            .root_module = mod,
            .linkage = .dynamic,
        });
        const step = b.addInstallFile(
            lib.getEmittedBin(),
            b.fmt("zygisk/{s}.so", .{t.abi}),
        );
        install.dependOn(&step.step);
    }

    // Unit tests for the pure parsers (runs on the host).
    const tests = b.addTest(.{
        .root_module = b.createModule(.{
            .root_source_file = b.path("src/tests.zig"),
            .target = b.graph.host,
            .optimize = .Debug,
        }),
    });
    const run_tests = b.addRunArtifact(tests);
    const test_step = b.step("test", "Run parser unit tests");
    test_step.dependOn(&run_tests.step);

    // On-device selftest binary (push to /data/local/tmp and run as root).
    const selftest_mod = b.createModule(.{
        .root_source_file = b.path("src/selftest.zig"),
        .target = b.resolveTargetQuery(.{
            .cpu_arch = .aarch64,
            .os_tag = .linux,
            .abi = .android,
        }),
        .optimize = .ReleaseSmall,
        .strip = true,
    });
    const selftest = b.addExecutable(.{
        .name = "noshot-selftest",
        .root_module = selftest_mod,
    });
    const selftest_step = b.addInstallArtifact(selftest, .{});
    const selftest_named = b.step("selftest", "Build the on-device selftest binary");
    selftest_named.dependOn(&selftest_step.step);
}
