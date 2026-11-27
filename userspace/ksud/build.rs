use std::{
    env,
    ffi::OsString,
    fs, io,
    path::{Path, PathBuf},
    process::Command,
};

const BOOTSTRAP_SOURCE: &str = "src/lkm_image_bootstrap.S";
const BOOTSTRAP_OBJECT: &str = "lkm_image_bootstrap.o";
const PREPARED_BOOTSTRAP_OBJECT: &str = ".lkm_image_bootstrap.o";

fn get_git_version() -> Result<(u32, String), std::io::Error> {
    let output = Command::new("git")
        .args(["rev-list", "--count", "HEAD"])
        .output()?;

    let output = output.stdout;
    let version_code = String::from_utf8(output).expect("Failed to read git count stdout");
    let version_code: u32 = version_code
        .trim()
        .parse()
        .map_err(|_| std::io::Error::other("Failed to parse git count"))?;
    let version_code = 30000 + 700 + version_code; // For historical reasons

    let described = String::from_utf8(
        Command::new("git")
            .args([
                "describe",
                "--tags",
                "--always",
                "--abbrev=0",
                "--match",
                "v[0-9]*",
            ])
            .output()?
            .stdout,
    )
    .map_err(|_| std::io::Error::other("Failed to parse git describe"))?;
    let described = described.trim();
    let is_plain_sha = !described.is_empty()
        && described.len() >= 7
        && described.len() <= 40
        && described.bytes().all(|b| b.is_ascii_hexdigit());
    let raw_ver = if described.is_empty() || is_plain_sha {
        "v4.0.0"
    } else {
        described
    };
    let version_name = raw_ver.trim_start_matches('v').to_string();
    Ok((version_code, version_name))
}

fn configure_bindgen() {
    // The bindgen::Builder is the main entry point
    // to bindgen, and lets you build up options for
    // the resulting bindings.
    let mut builder = bindgen::Builder::default()
        // The input header we would like to generate
        // bindings for.
        .header("src/android/uapi/ksu_uapi.h")
        .clang_args(["-x", "c++", "-I../../"])
        // Tell cargo to invalidate the built crate whenever any of the
        // included header files changed.
        .parse_callbacks(Box::new(bindgen::CargoCallbacks::new()));
    if env::var("CARGO_CFG_TARGET_ARCH").as_deref() == Ok("riscv64") {
        // libc does not yet expose Android's RISC-V signal context. Generate
        // it from the target NDK rather than assuming another libc's layout.
        builder = builder.header_contents("ksu_signal_context.h", "#include <sys/ucontext.h>");
    }
    let bindings = builder
        // Finish the builder and generate the bindings.
        .generate()
        // Unwrap the Result and panic on failure.
        .expect("Unable to generate bindings");

    // Write the bindings to the $OUT_DIR/bindings.rs file.
    let out_path = std::path::PathBuf::from(env::var("OUT_DIR").unwrap());
    // for debug, uncomment below
    // let out_path = std::path::PathBuf::from(env::var("CARGO_MANIFEST_DIR").unwrap());
    bindings
        .write_to_file(out_path.join("bindings.rs"))
        .expect("Couldn't write bindings!");
}

fn validate_bootstrap_object(path: &Path) -> io::Result<()> {
    let object = fs::read(path)?;
    let valid = object.len() >= 64
        && object.starts_with(b"\x7fELF")
        && object[4] == 2
        && object[5] == 1
        && u16::from_le_bytes([object[16], object[17]]) == 1
        && u16::from_le_bytes([object[18], object[19]]) == 183;
    if valid {
        Ok(())
    } else {
        Err(io::Error::other(
            "bootstrap object must be a little-endian AArch64 ELF64 ET_REL",
        ))
    }
}

fn copy_bootstrap_object(source: &Path, output: &Path) -> io::Result<()> {
    validate_bootstrap_object(source)?;
    fs::copy(source, output)?;
    Ok(())
}

fn ndk_clang() -> Option<PathBuf> {
    let ndk = env::var_os("ANDROID_NDK_HOME")
        .or_else(|| env::var_os("ANDROID_NDK_ROOT"))
        .map(PathBuf::from)?;
    let prebuilt = ndk.join("toolchains/llvm/prebuilt");
    let mut hosts = fs::read_dir(prebuilt)
        .ok()?
        .filter_map(Result::ok)
        .map(|entry| entry.path())
        .collect::<Vec<_>>();
    hosts.sort();
    hosts.into_iter().find_map(|host| {
        ["clang", "clang.exe"]
            .into_iter()
            .map(|name| host.join("bin").join(name))
            .find(|path| path.is_file())
    })
}

fn run_assembler(program: &Path, arguments: &[OsString]) -> Result<(), String> {
    let output = Command::new(program)
        .args(arguments)
        .output()
        .map_err(|error| format!("{}: {error}", program.display()))?;
    if output.status.success() {
        return Ok(());
    }
    let details = if output.stderr.is_empty() {
        &output.stdout
    } else {
        &output.stderr
    };
    Err(format!(
        "{}: {}",
        program.display(),
        String::from_utf8_lossy(details).trim()
    ))
}

fn assemble_bootstrap() {
    println!("cargo:rerun-if-changed={BOOTSTRAP_SOURCE}");
    println!("cargo:rerun-if-env-changed=KSU_LKM_BOOTSTRAP_OBJECT");
    println!("cargo:rerun-if-env-changed=KSU_LKM_BOOTSTRAP_CC");
    println!("cargo:rerun-if-env-changed=ANDROID_NDK_HOME");
    println!("cargo:rerun-if-env-changed=ANDROID_NDK_ROOT");

    let manifest = PathBuf::from(env::var_os("CARGO_MANIFEST_DIR").unwrap());
    let source = manifest.join(BOOTSTRAP_SOURCE);
    let output = PathBuf::from(env::var_os("OUT_DIR").unwrap()).join(BOOTSTRAP_OBJECT);

    if let Some(prebuilt) = env::var_os("KSU_LKM_BOOTSTRAP_OBJECT") {
        let prebuilt = PathBuf::from(prebuilt);
        copy_bootstrap_object(&prebuilt, &output).unwrap_or_else(|error| {
            panic!(
                "cannot use KSU_LKM_BOOTSTRAP_OBJECT {}: {error}",
                prebuilt.display()
            )
        });
        return;
    }

    // cross builds prepare this file on the host before entering the container.
    let prepared = manifest.join(PREPARED_BOOTSTRAP_OBJECT);
    println!("cargo:rerun-if-changed={}", prepared.display());
    if prepared.is_file() {
        copy_bootstrap_object(&prepared, &output).unwrap_or_else(|error| {
            panic!(
                "cannot use prepared bootstrap object {}: {error}",
                prepared.display()
            )
        });
        return;
    }

    let mut errors = Vec::new();
    let mut drivers = Vec::<PathBuf>::new();
    if let Some(compiler) = env::var_os("KSU_LKM_BOOTSTRAP_CC") {
        drivers.push(PathBuf::from(compiler));
    }
    drivers.push(PathBuf::from("aarch64-linux-gnu-gcc"));
    if let Some(clang) = ndk_clang() {
        drivers.push(clang);
    }
    drivers.push(PathBuf::from("clang"));

    for driver in drivers {
        let mut arguments = Vec::<OsString>::new();
        if driver
            .file_name()
            .and_then(|name| name.to_str())
            .is_some_and(|name| name.contains("clang"))
        {
            arguments.push("--target=aarch64-linux-gnu".into());
        }
        arguments.extend([
            "-c".into(),
            "-nostdlib".into(),
            "-o".into(),
            output.as_os_str().to_owned(),
            source.as_os_str().to_owned(),
        ]);
        match run_assembler(&driver, &arguments) {
            Ok(()) => {
                validate_bootstrap_object(&output)
                    .expect("assembler produced an invalid bootstrap object");
                return;
            }
            Err(error) => errors.push(error),
        }
    }

    let llvm_mc = PathBuf::from("llvm-mc");
    let llvm_arguments = [
        "-triple=aarch64-linux-gnu".into(),
        "-filetype=obj".into(),
        "-o".into(),
        output.as_os_str().to_owned(),
        source.as_os_str().to_owned(),
    ];
    match run_assembler(&llvm_mc, &llvm_arguments) {
        Ok(()) => {
            validate_bootstrap_object(&output)
                .expect("llvm-mc produced an invalid bootstrap object");
        }
        Err(error) => {
            errors.push(error);
            panic!(
                "cannot assemble the AArch64 LKM bootstrap; install an AArch64 GNU compiler, clang, or llvm-mc, or set KSU_LKM_BOOTSTRAP_OBJECT:\n{}",
                errors.join("\n")
            );
        }
    }
}

fn build_mkbootfs(out_directory: &Path) {
    const API_LEVEL: u32 = 26;
    const LIBRARY_NAME: &str = "mkbootfs";

    let target = env::var("TARGET").expect("TARGET not set");
    let manifest_directory =
        PathBuf::from(env::var_os("CARGO_MANIFEST_DIR").expect("CARGO_MANIFEST_DIR not set"));
    let (clang_target, asset_subdirectory) = match target.as_str() {
        "aarch64-linux-android" => (
            format!("aarch64-linux-android{API_LEVEL}"),
            Path::new("bin/aarch64"),
        ),
        "armv7-linux-androideabi" => (
            format!("armv7a-linux-androideabi{API_LEVEL}"),
            Path::new("bin/arm"),
        ),
        "x86_64-linux-android" => (
            format!("x86_64-linux-android{API_LEVEL}"),
            Path::new("bin/x86_64"),
        ),
        "riscv64-linux-android" => (
            "riscv64-linux-android35".to_string(),
            Path::new("bin/riscv"),
        ),
        _ => panic!("mkbootfs is not configured for Android target {target}"),
    };

    let source = manifest_directory.join("src/mkbootfs.cpp");
    let asset_directory = manifest_directory.join(asset_subdirectory);
    let output = asset_directory.join("mkbootfs");
    println!("cargo:rerun-if-changed={}", source.display());
    println!("cargo:rerun-if-changed={}", output.display());
    println!("cargo:rerun-if-env-changed=ANDROID_NDK_HOME");
    println!("cargo:rerun-if-env-changed=ANDROID_NDK_ROOT");

    let mut build = cc::Build::new();
    build
        .cpp(true)
        .target(&target)
        .cargo_metadata(false)
        .out_dir(out_directory)
        .file(&source)
        .std("c++20")
        .opt_level_str("z")
        .define("_FILE_OFFSET_BITS", "64")
        .define("_FORTIFY_SOURCE", "2")
        .flag(format!("--target={clang_target}"))
        .flags([
            "-fPIE",
            "-fstack-protector-strong",
            "-ffunction-sections",
            "-fdata-sections",
            "-fvisibility=hidden",
            "-fno-exceptions",
            "-fno-rtti",
        ])
        .warnings(true)
        .extra_warnings(true)
        .warnings_into_errors(true);
    let compiler = build.get_compiler();
    if !compiler.is_like_clang() {
        panic!(
            "mkbootfs requires the Android NDK clang++ compiler, got {:?}",
            compiler.path()
        );
    }

    fs::create_dir_all(&asset_directory).unwrap_or_else(|error| {
        panic!(
            "failed to create mkbootfs asset directory {}: {error}",
            asset_directory.display()
        )
    });

    build.compile(LIBRARY_NAME);
    let archive = out_directory.join(format!("lib{LIBRARY_NAME}.a"));
    if !archive.is_file() {
        panic!(
            "cc did not produce the expected mkbootfs archive {}",
            archive.display()
        );
    }

    let temporary_output = out_directory.join("mkbootfs");
    let mut linker = cc::Build::new();
    linker
        .cpp(true)
        .target(&target)
        .cargo_metadata(false)
        .no_default_flags(true)
        .flag(format!("--target={clang_target}"));
    let mut command = linker.get_compiler().to_command();
    command
        .arg(&archive)
        .args([
            "-pie",
            "-static-libstdc++",
            "-Wl,--gc-sections",
            "-Wl,--build-id=none",
            "-Wl,--exclude-libs,ALL",
            "-Wl,-z,relro,-z,now",
            "-Wl,--strip-all",
        ])
        .arg("-o")
        .arg(&temporary_output);

    let status = command
        .status()
        .unwrap_or_else(|error| panic!("failed to execute Android clang++ for mkbootfs: {error}"));
    if !status.success() {
        panic!("failed to build mkbootfs for {target}: {status}");
    }

    let built = fs::read(&temporary_output).unwrap_or_else(|error| {
        panic!(
            "failed to read built mkbootfs {}: {error}",
            temporary_output.display()
        )
    });
    if fs::read(&output).ok().as_deref() != Some(built.as_slice()) {
        fs::write(&output, built).unwrap_or_else(|error| {
            panic!(
                "failed to install mkbootfs asset {}: {error}",
                output.display()
            )
        });
    }
}

fn parse_hex_words<const N: usize>(hex: &str) -> [u32; N] {
    let mut out = [0u32; N];
    for (slot, chunk) in out.iter_mut().zip(hex.as_bytes().chunks_exact(8)) {
        let s = std::str::from_utf8(chunk).expect("valid utf8 hex");
        *slot = u32::from_str_radix(s, 16).expect("valid u32 hex");
    }
    out
}

fn sha256_hex(data: &[u8]) -> String {
    const H0_HEX: &str = "6a09e667bb67ae853c6ef372a54ff53a510e527f9b05688c1f83d9ab5be0cd19";
    const K_HEX: &str = concat!(
        "428a2f9871374491b5c0fbcfe9b5dba53956c25b59f111f1923f82a4ab1c5ed5",
        "d807aa9812835b01243185be550c7dc372be5d7480deb1fe9bdc06a7c19bf174",
        "e49b69c1efbe47860fc19dc6240ca1cc2de92c6f4a7484aa5cb0a9dc76f988da",
        "983e5152a831c66db00327c8bf597fc7c6e00bf3d5a7914706ca635114292967",
        "27b70a852e1b21384d2c6dfc53380d13650a7354766a0abb81c2c92e92722c85",
        "a2bfe8a1a81a664bc24b8b70c76c51a3d192e819d6990624f40e3585106aa070",
        "19a4c1161e376c082748774c34b0bcb5391c0cb34ed8aa4a5b9cca4f682e6ff3",
        "748f82ee78a5636f84c878148cc7020890befffaa4506cebbef9a3f7c67178f2",
    );
    let k_words: [u32; 64] = parse_hex_words(K_HEX);
    let mut h: [u32; 8] = parse_hex_words(H0_HEX);
    let bit_len = (data.len() as u64).wrapping_mul(8);
    let mut padded = data.to_vec();
    padded.push(0x80);
    while (padded.len() % 64) != 56 {
        padded.push(0);
    }
    padded.extend_from_slice(&bit_len.to_be_bytes());

    for chunk in padded.chunks_exact(64) {
        let mut w = [0u32; 64];
        for (dst, word) in w.iter_mut().zip(chunk.chunks_exact(4)) {
            *dst = u32::from_be_bytes([word[0], word[1], word[2], word[3]]);
        }
        for i in 16..64 {
            let s0 = w[i - 15].rotate_right(7) ^ w[i - 15].rotate_right(18) ^ (w[i - 15] >> 3);
            let s1 = w[i - 2].rotate_right(17) ^ w[i - 2].rotate_right(19) ^ (w[i - 2] >> 10);
            w[i] = w[i - 16]
                .wrapping_add(s0)
                .wrapping_add(w[i - 7])
                .wrapping_add(s1);
        }
        let [mut a, mut b, mut c, mut d, mut e, mut f, mut g, mut hh] = h;
        for (&k, &w_i) in k_words.iter().zip(w.iter()) {
            let s1 = e.rotate_right(6) ^ e.rotate_right(11) ^ e.rotate_right(25);
            let ch = (e & f) ^ ((!e) & g);
            let temp1 = hh
                .wrapping_add(s1)
                .wrapping_add(ch)
                .wrapping_add(k)
                .wrapping_add(w_i);
            let s0 = a.rotate_right(2) ^ a.rotate_right(13) ^ a.rotate_right(22);
            let maj = (a & b) ^ (a & c) ^ (b & c);
            let temp2 = s0.wrapping_add(maj);

            hh = g;
            g = f;
            f = e;
            e = d.wrapping_add(temp1);
            d = c;
            c = b;
            b = a;
            a = temp1.wrapping_add(temp2);
        }
        for (slot, delta) in h.iter_mut().zip([a, b, c, d, e, f, g, hh]) {
            *slot = slot.wrapping_add(delta);
        }
    }
    let mut out = String::with_capacity(64);
    for word in h {
        use std::fmt::Write;
        let _ = write!(out, "{word:08x}");
    }
    out
}

fn git_tree_hash(paths: &[&str]) -> String {
    let mut resolved = Vec::with_capacity(paths.len());
    for path in paths {
        let spec = format!("HEAD:{path}");
        let Ok(output) = Command::new("git")
            .args(["rev-parse", "--verify", "--quiet", &spec])
            .output()
        else {
            continue;
        };
        if output.status.success() {
            let oid = String::from_utf8_lossy(&output.stdout).trim().to_string();
            if !oid.is_empty() {
                resolved.push(oid);
            }
        }
    }
    if resolved.is_empty() {
        "unknown".to_string()
    } else {
        sha256_hex(resolved.join(":").as_bytes())[..8].to_string()
    }
}

fn emit_hash_env(env_key: &str, paths: &[&str]) {
    let value = env::var(env_key).unwrap_or_else(|_| git_tree_hash(paths));
    println!("cargo:rustc-env={env_key}={value}");
}

fn main() {
    assemble_bootstrap();

    let (code, name) = match get_git_version() {
        Ok((code, name)) => (code, name),
        Err(_) => {
            // show warning if git is not installed
            println!("cargo:warning=Failed to get git version, using 0.0.0");
            (0, "0.0.0".to_string())
        }
    };

    let out_dir = env::var("OUT_DIR").expect("Failed to get $OUT_DIR");
    let out_dir = Path::new(&out_dir);
    if env::var("KSU_PACKAGE_NAME").is_err() {
        println!("cargo:rustc-env=KSU_PACKAGE_NAME=org.uranxsu.manager");
    }
    println!("cargo:rustc-env=VERSION_CODE={code}");
    println!("cargo:rustc-env=VERSION_NAME={name}");
    emit_hash_env("KSU_KSUD_HASH", &["userspace/ksud"]);
    emit_hash_env(
        "KSU_USERSPACE_HASH",
        &[
            "userspace/ksud",
            "susfs-lkm",
            "external/nomount",
            "external/BuiltZygisk",
        ],
    );
    emit_hash_env(
        "KSU_SUSFS_HASH",
        &["susfs-lkm", "userspace/ksud/src/android/susfs"],
    );
    emit_hash_env(
        "KSU_NOMOUNT_HASH",
        &["external/nomount", "userspace/ksud/src/android/mount"],
    );
    emit_hash_env(
        "KSU_ZYGISK_HASH",
        &[
            "external/BuiltZygisk",
            "userspace/ksud/src/android/builtin_zygisk.rs",
        ],
    );

    let target_os = env::var("CARGO_CFG_TARGET_OS").expect("CARGO_CFG_TARGET_OS not set");
    if target_os == "android" {
        build_mkbootfs(out_dir);
        configure_bindgen();
    }
}
