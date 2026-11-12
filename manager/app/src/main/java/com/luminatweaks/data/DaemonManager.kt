/*
 * Copyright (C) 2026 kimal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.luminatweaks.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object DaemonManager {
    private const val MODULE_DAEMON_PATH = "/data/adb/modules/luminatweaks/luminad"
    private const val MODULE_UPDATE_DIR = "/data/adb/modules_update/luminatweaks"

    data class DaemonStatus(
        val pid: String?,
        val profile: String,
        val fps: String
    )

    private fun runSuCmd(cmd: String, timeoutSeconds: Long = 8): String {
        return try {
            val process = ProcessBuilder("su", "-c", cmd)
                .redirectErrorStream(true)
                .start()
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return ""
            }
            process.inputStream.bufferedReader().use { it.readText() }.trim()
        } catch (_: Exception) {
            ""
        }
    }

    suspend fun autoGrantPermissions(): Boolean = withContext(Dispatchers.IO) {
        val cmds = """
            pm grant com.luminatweaks android.permission.POST_NOTIFICATIONS 2>/dev/null
            cmd appops set com.luminatweaks POST_NOTIFICATION allow 2>/dev/null
        """.trimIndent()
        runSuCmd(cmds)
        true
    }

    suspend fun isRebootRequired(): Boolean = withContext(Dispatchers.IO) {
        runSuCmd("[ -d $MODULE_UPDATE_DIR ] && echo 'YES'").contains("YES")
    }

    suspend fun isModuleInstalled(): Boolean = withContext(Dispatchers.IO) {
        runSuCmd("[ -f $MODULE_DAEMON_PATH ] && echo 'OK'").contains("OK")
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

    suspend fun getStatus(): DaemonStatus = withContext(Dispatchers.IO) {
        val output = runSuCmd(
            "pid=\$(pidof luminad | awk '{print \$1}'); " +
                "echo \"PID=\$pid\"; " +
                "echo \"PROFILE=\$(getprop sys.lumina.profile)\"; " +
                "echo \"FPS=\$(cat /data/adb/.config/lumina/fps.txt 2>/dev/null)\""
        )

        var pid: String? = null
        var profile = ""
        var fpsRaw = ""
        output.lineSequence().forEach { line ->
            when {
                line.startsWith("PID=") -> pid = line.removePrefix("PID=").trim().ifBlank { null }
                line.startsWith("PROFILE=") -> profile = line.removePrefix("PROFILE=").trim()
                line.startsWith("FPS=") -> fpsRaw = line.removePrefix("FPS=").trim()
            }
        }

        val resolvedPid = pid
        if (resolvedPid == null) {
            DaemonStatus(null, "-", "-")
        } else {
            DaemonStatus(
                pid = resolvedPid,
                profile = profile.ifBlank { "Balanced" },
                fps = parseFps(fpsRaw)
            )
        }
    }

    private fun parseFps(raw: String): String {
        if (raw.isBlank()) return "-"
        val parts = raw.split(Regex("\\s+"))
        val fps = parts.getOrNull(0)?.toFloatOrNull() ?: return "-"
        val timestamp = parts.getOrNull(1)?.toLongOrNull() ?: 0L
        val age = System.currentTimeMillis() - timestamp
        return if (fps <= 0f || (timestamp > 0L && age > 5000L)) "-" else fps.toInt().toString()
    }

    suspend fun stopDaemon(): Boolean = withContext(Dispatchers.IO) {
        runSuCmd(
            "pkill -TERM luminad 2>/dev/null || killall -TERM luminad 2>/dev/null; " +
                "sleep 1; " +
                "pkill -9 luminad 2>/dev/null || killall -9 luminad 2>/dev/null"
        )
        !isDaemonRunning()
    }

    suspend fun startDaemon(context: Context): Boolean = withContext(Dispatchers.IO) {
        autoGrantPermissions()

        if (isDaemonRunning()) return@withContext true

        // Jangan jalankan daemon jika modul baru di-flash dan menunggu reboot
        if (isRebootRequired()) {
            return@withContext false
        }

        // Hanya boleh jalan jika modul terpasang resmi di KernelSU/Magisk
        if (!isModuleInstalled()) {
            return@withContext false
        }

        runSuCmd("$MODULE_DAEMON_PATH </dev/null >/dev/null 2>&1 &")

        var retries = 5
        while (retries > 0) {
            delay(300)
            if (isDaemonRunning()) return@withContext true
            retries--
        }
        false
    }

    suspend fun restartDaemon(context: Context): Boolean = withContext(Dispatchers.IO) {
        stopDaemon()
        startDaemon(context)
    }
}
