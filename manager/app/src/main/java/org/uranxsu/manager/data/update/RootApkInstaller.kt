package org.uranxsu.manager.data.update

import android.app.Application
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.uranxsu.manager.Natives
import org.uranxsu.manager.data.shell.KsuCliRepository

class RootApkInstaller(
    private val application: Application,
    private val ksuCliRepository: KsuCliRepository,
) {
    private companion object {
        const val TEMP_APK_PATH = "/data/local/tmp/uranxsu_update.apk"
    }

    suspend fun install(apkFile: File): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (!Natives.isManager || !ksuCliRepository.rootAvailable()) {
                throw IOException("Root unavailable for silent APK install")
            }
            if (!apkFile.isFile || apkFile.length() <= 0L) {
                throw IOException("Invalid APK file: ${apkFile.absolutePath}")
            }

            val srcQuoted = KsuCliRepository.shellQuote(apkFile.absolutePath)
            val installerQuoted = KsuCliRepository.shellQuote(application.packageName)
            val cmd = "cp $srcQuoted $TEMP_APK_PATH && chmod 0644 $TEMP_APK_PATH && " +
                "pm install -r -i $installerQuoted $TEMP_APK_PATH; " +
                "rc=\$?; rm -f $TEMP_APK_PATH; exit \$rc"

            val output = mutableListOf<String>()
            val result = ksuCliRepository.getRootShell().newJob().add(cmd).to(output).exec()
            if (!result.isSuccess) {
                val msg = output.joinToString("\n").ifBlank { "pm install failed (${result.code})" }
                throw IOException(msg)
            }
        }
    }
}
