package org.uranxsu.manager.domain.model

enum class ManagerUpdateChannel {
    STABLE,
    BETA,
}

enum class UpdateImpact {
    APK_ONLY,
    KSUD_SYNC,
    LKM_UPDATE,
}

sealed interface ManagerApkSource {
    val url: String

    data class DirectApk(override val url: String) : ManagerApkSource

    data class NightlyArtifact(
        override val url: String,
        val preferredAbi: String,
        val expectedVersionCode: Int,
    ) : ManagerApkSource
}

data class ManagerUpdateInfo(
    val channel: ManagerUpdateChannel,
    val versionCode: Int,
    val versionName: String,
    val abi: String,
    val fileName: String,
    val source: ManagerApkSource,
    val changelog: String = "",
    val updateImpact: UpdateImpact = UpdateImpact.APK_ONLY,
    val remoteLkmHash: String? = null,
    val remoteUserspaceHash: String? = null,
)
