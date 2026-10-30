package com.luminatweaks.util

import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogUtil {

    private fun runSu(cmd: String): Boolean {
        return try {
            val process = ProcessBuilder("su", "-c", cmd)
                .redirectErrorStream(true)
                .start()
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    suspend fun dumpDiagnosticLogs(context: Context, saveToDownloads: Boolean): File? = withContext(Dispatchers.IO) {
        val cacheDir = context.cacheDir
        cacheDir.listFiles()?.forEach { file ->
            if (file.name.startsWith("Lumina_Logs_") && file.name.endsWith(".tar.gz")) {
                file.delete()
            }
        }

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "Lumina_Logs_$timeStamp.tar.gz"
        val appUid = context.applicationInfo.uid

        val targetPath = if (saveToDownloads) {
            "/storage/emulated/0/Download/$fileName"
        } else {
            "${context.cacheDir.absolutePath}/$fileName"
        }

        val appVersion = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: Exception) {
            "2.0.1"
        }

        val d = "$"

        val script = """
            TMP_DIR="/data/local/tmp/lumina_logs_tmp"
            rm -rf ${d}TMP_DIR

            mkdir -p ${d}TMP_DIR/LuminaConfig
            mkdir -p ${d}TMP_DIR/log

            cp -f /data/adb/luminatweaks/*.json ${d}TMP_DIR/LuminaConfig/ 2>/dev/null

            INFO_FILE="${d}TMP_DIR/log/Lumina.log"

            MODULE_VER=${d}(grep '^version=' /data/adb/modules/luminatweaks/module.prop 2>/dev/null | cut -d= -f2)
            [ -z "${d}MODULE_VER" ] && MODULE_VER="2.0.0 (Remastered)"

            KERNEL_INFO=${d}(uname -r -m)
            ANDROID_VER=${d}(getprop ro.build.version.release)
            API_LEVEL=${d}(getprop ro.build.version.sdk)
            DEVICE_MODEL=${d}(getprop ro.product.model)
            FINGERPRINT=${d}(getprop ro.build.fingerprint)
            SELINUX_STATUS=${d}(getenforce 2>/dev/null)
            DAEMON_PID=${d}(pidof luminad 2>/dev/null)
            ACTIVE_PROFILE=${d}(getprop sys.lumina.profile 2>/dev/null)
            [ -z "${d}ACTIVE_PROFILE" ] && ACTIVE_PROFILE="Balanced"

            echo "################################################################" > "${d}INFO_FILE"
            echo "                     LuminaTweaks Diagnostics                   " >> "${d}INFO_FILE"
            echo "################################################################" >> "${d}INFO_FILE"
            echo "" >> "${d}INFO_FILE"
            echo "  [Device Information]" >> "${d}INFO_FILE"
            echo "    Device Model   : ${d}DEVICE_MODEL" >> "${d}INFO_FILE"
            echo "    Android Ver    : ${d}ANDROID_VER (API ${d}API_LEVEL)" >> "${d}INFO_FILE"
            echo "    Kernel         : ${d}KERNEL_INFO" >> "${d}INFO_FILE"
            echo "    SELinux Status : ${d}SELINUX_STATUS" >> "${d}INFO_FILE"
            echo "    Fingerprint    : ${d}FINGERPRINT" >> "${d}INFO_FILE"
            echo "" >> "${d}INFO_FILE"
            echo "  [Lumina Subsystem]" >> "${d}INFO_FILE"
            echo "    Manager Version: $appVersion" >> "${d}INFO_FILE"
            echo "    Module Version : ${d}MODULE_VER" >> "${d}INFO_FILE"
            echo "    Daemon PID     : ${d}DAEMON_PID" >> "${d}INFO_FILE"
            echo "    Active Profile : ${d}ACTIVE_PROFILE" >> "${d}INFO_FILE"
            echo "" >> "${d}INFO_FILE"
            echo "################################################################" >> "${d}INFO_FILE"
            echo "" >> "${d}INFO_FILE"

            if [ -f "/data/adb/luminatweaks/luminad.log" ]; then
                echo "--- START OF LUMINAD SERVICE LOG ---" >> "${d}INFO_FILE"
                cat /data/adb/luminatweaks/luminad.log >> "${d}INFO_FILE"
                echo "" >> "${d}INFO_FILE"
                echo "--- END OF LUMINAD SERVICE LOG ---" >> "${d}INFO_FILE"
                echo "" >> "${d}INFO_FILE"
            fi

            if [ -f "/data/adb/luminatweaks/luminad.log.old" ]; then
                echo "--- PREVIOUS LUMINAD SERVICE LOG (.old) ---" >> "${d}INFO_FILE"
                cat /data/adb/luminatweaks/luminad.log.old >> "${d}INFO_FILE"
                echo "" >> "${d}INFO_FILE"
            fi

            cat /data/adb/ksu/log/logcat.log > ${d}TMP_DIR/log/logcat.log 2>/dev/null
            cat /data/adb/ap/log/logcat.log >> ${d}TMP_DIR/log/logcat.log 2>/dev/null
            cat /data/adb/ksu/log/dmesg.log > ${d}TMP_DIR/log/dmesg.log 2>/dev/null
            cat /data/adb/ap/log/dmesg.log >> ${d}TMP_DIR/log/dmesg.log 2>/dev/null
            cat /cache/magisk.log > ${d}TMP_DIR/log/magisk.log 2>/dev/null
            cat /data/adb/magisk.log >> ${d}TMP_DIR/log/magisk.log 2>/dev/null
            cat /data/adb/apatch/log/* >> ${d}TMP_DIR/log/apatch.log 2>/dev/null

            logcat -d -t 350 > ${d}TMP_DIR/log/logcat_recent.txt 2>/dev/null
            dmesg | tail -n 250 > ${d}TMP_DIR/log/dmesg_recent.txt 2>/dev/null

            cd /data/local/tmp
            tar -czf "$fileName" -C lumina_logs_tmp .

            mkdir -p "${d}(dirname "$targetPath")"
            cp -f "$fileName" "$targetPath"

            if [ "$saveToDownloads" = "true" ]; then
                chmod 666 "$targetPath"
            else
                chown $appUid:$appUid "$targetPath"
                chmod 600 "$targetPath"
                restorecon "$targetPath" 2>/dev/null
            fi

            rm -rf lumina_logs_tmp
            rm -f "$fileName"
        """.trimIndent()

        val success = runSu(script)
        val finalFile = File(targetPath)

        if (success && finalFile.exists()) {
            if (saveToDownloads) {
                MediaScannerConnection.scanFile(context, arrayOf(finalFile.absolutePath), null, null)
            }
            finalFile
        } else {
            null
        }
    }

    fun getShareLogIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/gzip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(intent, "Bagikan Berkas Log")
    }
}
