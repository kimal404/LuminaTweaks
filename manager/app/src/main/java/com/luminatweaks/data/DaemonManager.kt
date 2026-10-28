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

    suspend fun deployBinary(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            // Salin dari assets ke internal cache
            val tempFile = File(context.cacheDir, "luminad_tmp")
            context.assets.open("bin/luminad").use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            // Pindahkan ke target root dengan chmod 755
            val cmds = """
                mkdir -p $DAEMON_DIR
                cp ${tempFile.absolutePath} $DAEMON_PATH
                chmod 755 $DAEMON_PATH
                rm -f ${tempFile.absolutePath}
            """.trimIndent()

            runSuCmd(cmds)
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun startDaemon(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (isDaemonRunning()) return@withContext true

        // Pastikan biner sudah ada di target
        val exists = runSuCmd("[ -f $DAEMON_PATH ] && echo 'OK'").contains("OK")
        if (!exists) {
            deployBinary(context)
        }

        // Jalankan detached di background tanpa blocking IO
        runSuCmd("$DAEMON_PATH </dev/null >/dev/null 2>&1 &")

        // Tunggu maksimal 1.5 detik untuk memastikan proses spawn
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
