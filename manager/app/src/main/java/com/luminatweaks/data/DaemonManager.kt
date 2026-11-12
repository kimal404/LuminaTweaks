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

import android.annotation.SuppressLint
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

object DaemonManager {
    private const val MODULE_DAEMON_PATH = "/data/adb/modules/luminatweaks/luminad"
    private const val MODULE_BINDER_PATH = "/data/adb/modules/luminatweaks/lumina-binder"
    private const val MODULE_UPDATE_DIR = "/data/adb/modules_update/luminatweaks"
    private const val PID_FILE_PATH = "/data/local/tmp/lumina.pid"
    private const val HEARTBEAT_FILE_PATH = "/data/local/tmp/lumina.alive"
    private const val HEARTBEAT_MAX_AGE_MS = 10_000L
    private const val PUBLIC_FPS_PATH = "/data/local/tmp/lumina.fps"
    private const val LEGACY_FPS_PATH = "/data/adb/.config/lumina/fps.txt"

    data class DaemonStatus(
        val pid: String?,
        val profile: String,
        val fps: String
    )

    private fun runSuCmd(cmd: String, timeoutSeconds: Long = 5): String {
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

    private fun readSystemProperty(key: String): String? {
        return try {
            @SuppressLint("PrivateApi")
            val spClass = Class.forName("android.os.SystemProperties")
            val getMethod = spClass.getMethod("get", String::class.java, String::class.java)
            val value = getMethod.invoke(null, key, "") as? String
            value?.trim()?.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    private fun readHeartbeatAgeMs(): Long? {
        return try {
            val file = File(HEARTBEAT_FILE_PATH)
            if (!file.exists() || !file.canRead()) return null
            val timestamp = file.readText().trim().toLongOrNull() ?: return null
            (System.currentTimeMillis() - timestamp).coerceAtLeast(0L)
        } catch (_: Exception) {
            null
        }
    }

    private fun readPidFromFile(): String? {
        return try {
            val pidFile = File(PID_FILE_PATH)
            if (!pidFile.exists() || !pidFile.canRead()) return null
            val pid = pidFile.readText().trim()
            if (pid.isEmpty() || !pid.all { it.isDigit() }) return null
            val commFile = File("/proc/$pid/comm")
            if (commFile.exists() && commFile.canRead()) {
                return if (commFile.readText().trim() == "luminad") pid else null
            }
            val heartbeatAge = readHeartbeatAgeMs()
            if (heartbeatAge == null || heartbeatAge <= HEARTBEAT_MAX_AGE_MS) pid else null
        } catch (_: Exception) {
            null
        }
    }

    private fun readFpsFromFile(): String {
        for (path in listOf(PUBLIC_FPS_PATH, LEGACY_FPS_PATH)) {
            try {
                val file = File(path)
                if (file.exists() && file.canRead()) {
                    val value = file.readText().trim()
                    if (value.isNotEmpty()) return value
                }
            } catch (_: Exception) {}
        }
        return ""
    }

    suspend fun isRebootRequired(): Boolean = withContext(Dispatchers.IO) {
        runSuCmd("[ -d $MODULE_UPDATE_DIR ] && echo 'YES'", 3).contains("YES")
    }

    suspend fun isModuleInstalled(): Boolean = withContext(Dispatchers.IO) {
        runSuCmd("[ -f $MODULE_DAEMON_PATH ] && echo 'OK'", 3).contains("OK")
    }

    suspend fun getDaemonPid(): String? = withContext(Dispatchers.IO) {
        readPidFromFile()?.let { return@withContext it }

        val pid = runSuCmd("pidof luminad", 3)
        if (pid.isNotBlank() && pid.all { it.isDigit() || it.isWhitespace() }) {
            pid.split("\\s+".toRegex()).firstOrNull { it.isNotBlank() }
        } else {
            null
        }
    }

    suspend fun isDaemonRunning(): Boolean = getDaemonPid() != null

    suspend fun getStatus(): DaemonStatus = withContext(Dispatchers.IO) {
        val pid = readPidFromFile()
            ?: return@withContext DaemonStatus(null, "-", "-")

        val profile = readSystemProperty("sys.lumina.profile") ?: "Balanced"
        DaemonStatus(
            pid = pid,
            profile = profile,
            fps = parseFps(readFpsFromFile())
        )
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
            "killall -15 luminad 2>/dev/null || pkill -15 -x luminad 2>/dev/null || true; " +
                "i=0; while [ \$i -lt 5 ]; do pidof luminad >/dev/null 2>&1 || break; sleep 0.5; i=\$((i+1)); done; " +
                "killall -9 luminad 2>/dev/null || pkill -9 -x luminad 2>/dev/null || true; " +
                "rm -f $PID_FILE_PATH $HEARTBEAT_FILE_PATH",
            6
        )
        !isDaemonRunning()
    }

    suspend fun startDaemon(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (isDaemonRunning()) return@withContext true

        if (isRebootRequired()) {
            return@withContext false
        }

        if (!isModuleInstalled()) {
            return@withContext false
        }

        runSuCmd(
            "if [ -f $MODULE_BINDER_PATH ] && ! pidof lumina-binder >/dev/null 2>&1; then " +
                "chmod 755 $MODULE_BINDER_PATH && nohup $MODULE_BINDER_PATH >/dev/null 2>&1 & sleep 1; " +
                "fi; " +
                "chmod 755 $MODULE_DAEMON_PATH && nohup $MODULE_DAEMON_PATH >/dev/null 2>&1 &",
            5
        )

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
