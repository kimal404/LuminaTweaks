package com.luminatweaks.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader

object DaemonManager {
    private const val DAEMON_DIR = "/data/adb/luminatweaks"
    private const val DAEMON_PATH = "$DAEMON_DIR/luminad"
    private const val BOOT_SERVICE_DIR = "/data/adb/service.d"
    private const val BOOT_SCRIPT_PATH = "$BOOT_SERVICE_DIR/luminad_boot.sh"

    private fun runSuCmd(cmd: String): String {
        return try {
            val process = ProcessBuilder("su", "-c", cmd)
                .redirectErrorStream(true)
                .start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            process.waitFor()
            output.toString().trim()
        } catch (e: Exception) {
            ""
        }
    }

    suspend fun autoGrantPermissions(): Boolean = withContext(Dispatchers.IO) {
        val cmds = """
            cmd appops set com.luminatweaks GET_INSTALLED_APPS allow 2>/dev/null
            cmd appops set com.luminatweaks QUERY_ALL_PACKAGES allow 2>/dev/null
            appops set com.luminatweaks GET_INSTALLED_APPS allow 2>/dev/null
            pm grant com.luminatweaks android.permission.POST_NOTIFICATIONS 2>/dev/null
        """.trimIndent()
        runSuCmd(cmds)
        true
    }

    suspend fun getDaemonPid(): String? = withContext(Dispatchers.IO) {
        val pid = runSuCmd("pidof luminad")
        if (pid.isNotBlank() && pid.all { it.isDigit() || it.isWhitespace() }) {
            pid.split("\\s+".toRegex()).firstOrNull { it.isNotBlank() }
        } else {
            null
        }
    }

    suspend fun isDaemonRunning(): Boolean = getDaemonPid() != null

    suspend fun stopDaemon(): Boolean = withContext(Dispatchers.IO) {
        runSuCmd("pkill -9 luminad || killall -9 luminad 2>/dev/null")
        !isDaemonRunning()
    }

    suspend fun setupBootScript(): Boolean = withContext(Dispatchers.IO) {
        val bootScriptContent = """
            #!/system/bin/sh
            while [ "${'$'}(getprop sys.boot_completed)" != "1" ]; do
                sleep 2
            done
            sleep 3

            DAEMON_BIN="$DAEMON_PATH"
            if [ -f "${'$'}DAEMON_BIN" ]; then
                chmod 755 "${'$'}DAEMON_BIN"
                "${'$'}DAEMON_BIN" </dev/null >/dev/null 2>&1 &
            fi
        """.trimIndent()

        val cmds = """
            mkdir -p $BOOT_SERVICE_DIR
            echo '$bootScriptContent' > $BOOT_SCRIPT_PATH
            chmod 755 $BOOT_SCRIPT_PATH
        """.trimIndent()

        runSuCmd(cmds)
        true
    }

    suspend fun deployBinary(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            autoGrantPermissions()
            val tempFile = File(context.cacheDir, "luminad_tmp")
            context.assets.open("bin/luminad").use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            val cmds = """
                mkdir -p $DAEMON_DIR
                cp ${tempFile.absolutePath} $DAEMON_PATH
                chmod 755 $DAEMON_PATH
                rm -f ${tempFile.absolutePath}
            """.trimIndent()

            runSuCmd(cmds)
            setupBootScript()
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun startDaemon(context: Context): Boolean = withContext(Dispatchers.IO) {
        autoGrantPermissions()

        if (isDaemonRunning()) return@withContext true

        val exists = runSuCmd("[ -f $DAEMON_PATH ] && echo 'OK'").contains("OK")
        if (!exists) {
            deployBinary(context)
        } else {
            setupBootScript()
        }

        runSuCmd("$DAEMON_PATH </dev/null >/dev/null 2>&1 &")

        var retries = 5
        while (retries > 0) {
            kotlinx.coroutines.delay(300)
            if (isDaemonRunning()) return@withContext true
            retries--
        }
        false
    }

    suspend fun restartDaemon(context: Context): Boolean = withContext(Dispatchers.IO) {
        stopDaemon()
        deployBinary(context)
        startDaemon(context)
    }
}
