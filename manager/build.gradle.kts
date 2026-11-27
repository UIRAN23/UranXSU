import com.diffplug.spotless.LineEnding

plugins {
    alias(libs.plugins.agp.app) apply false
    alias(libs.plugins.agp.test) apply false
    alias(libs.plugins.androidx.baselineprofile) apply false
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.spotless)
}

spotless {
    lineEndings = LineEnding.UNIX

    kotlin {
        target("**/src/**/*.kt")
        targetExclude("**/build/**", "**/generated/**")
        ktlint(libs.versions.ktlint.get())
            .customRuleSets(listOf("io.nlopez.compose.rules:ktlint:${libs.versions.compose.rules.get()}"))
            .editorConfigOverride(
                mapOf(
                    "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
                    "ktlint_compose_modifier-missing-check" to "disabled",
                    "ktlint_compose_compositionlocal-allowlist" to "disabled",
                    "ktlint_compose_mutable-state-param-check" to "disabled",
                    "ktlint_compose_parameter-naming" to "disabled",
                    "ktlint_compose_modifier-naming" to "disabled",
                ),
            )
    }

    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**", "**/.gradle/**")
        ktlint(libs.versions.ktlint.get())
    }
}

extra["androidMinSdkVersion"] = 26
extra["androidTargetSdkVersion"] = 37
extra["androidCompileSdkVersion"] = 37
extra["androidBuildToolsVersion"] = "36.1.0"
extra["androidCompileNdkVersion"] = libs.versions.ndk.get()
extra["androidSourceCompatibility"] = JavaVersion.VERSION_21
extra["androidTargetCompatibility"] = JavaVersion.VERSION_21
extra["managerVersionCode"] = 30000 + getGitCommitCount() + 700
extra["managerVersionName"] = getGitDescribe()
extra["bundledLkmHash"] = gitTreeHash("kernel", "uapi")
extra["bundledUserspaceHash"] = gitTreeHash(
    "userspace/ksud",
    "susfs-lkm",
    "external/nomount",
    "external/BuiltZygisk",
)
extra["bundledKsudHash"] = gitTreeHash("userspace/ksud")
extra["bundledSusfsHash"] = gitTreeHash("susfs-lkm", "userspace/ksud/src/android/susfs")
extra["bundledNomountHash"] = gitTreeHash("external/nomount", "userspace/ksud/src/android/mount")
extra["bundledZygiskHash"] = gitTreeHash("external/BuiltZygisk", "userspace/ksud/src/android/builtin_zygisk.rs")
extra["isPrBuild"] = project.findProperty("IS_PR_BUILD")?.toString()?.toBoolean() ?: false
extra["defaultManagerPackageName"] = "org.uranxsu.manager"
extra["managerPackageName"] = project.findProperty("KSU_PACKAGE_NAME")?.toString() ?: extra["defaultManagerPackageName"]
extra["defaultManagerAppName"] = if (extra["isPrBuild"] == true) "UranXSU PR" else "UranXSU"
extra["managerName"] = project.findProperty("KSU_NAME")?.toString() ?: extra["defaultManagerAppName"]

val isSpoofedBuild = project.findProperty("IS_SPOOFED_BUILD")?.toString()?.toBoolean() ?: false

fun getGitCommitCount(): Int = providers.exec {
    commandLine("git", "rev-list", "--count", "HEAD")
}.standardOutput.asText.get().trim().toInt()

fun gitTreeHash(vararg paths: String): String {
    val oids = paths.map { path ->
        val execResult = providers.exec {
            commandLine("git", "rev-parse", "--verify", "--quiet", "HEAD:$path")
            isIgnoreExitValue = true
        }
        if (execResult.result.get().exitValue != 0) {
            return@map ""
        }
        execResult.standardOutput.asText.get().trim()
    }
    if (oids.any { it.isEmpty() }) return "unknown"
    val digest = java.security.MessageDigest.getInstance("SHA-256")
        .digest(oids.joinToString(":").toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }.take(8)
}

fun getGitDescribe(): String {
    val described = providers.exec {
        commandLine("git", "describe", "--tags", "--always", "--abbrev=0", "--match", "v[0-9]*")
    }.standardOutput.asText.get().trim()
    val base = if (described.isNotEmpty() && !described.matches(Regex("^[0-9a-f]{7,40}$"))) {
        described
    } else {
        "v4.0.0"
    }
    return if (isSpoofedBuild) {
        "$base-spoofed"
    } else {
        base
    }
}
