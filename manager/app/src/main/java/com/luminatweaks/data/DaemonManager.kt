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
    private const val MODULE_DIR = "/data/adb/modules/luminatweaks"
    private const val MODULE_BIN_DIR = "$MODULE_DIR/bin"
    private const val MODULE_UPDATE_DIR = "/data/adb/modules_update/luminatweaks"
    private const val CONFIG_DIR = "/data/adb/.config/lumina"
    private const val PID_FILE_PATH = "$CONFIG_DIR/luminad.pid"
    private const val HEARTBEAT_FILE_PATH = "$CONFIG_DIR/luminad.alive"
    private const val LEGACY_PID_FILE_PATH = "/data/local/tmp/lumina.pid"
    private const val LEGACY_HEARTBEAT_FILE_PATH = "/data/local/tmp/lumina.alive"
    private const val HEARTBEAT_MAX_AGE_MS = 30_000L
    private const val IPC_SOCKET_PRIMARY = "$CONFIG_DIR/lumina.sock"
    private const val IPC_SOCKET_FALLBACK = "/data/local/tmp/lumina_ipc.sock"
    private const val IPC_SENTINEL_PID = "ipc"
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
        var freshest: Long? = null
        for (path in listOf(HEARTBEAT_FILE_PATH, LEGACY_HEARTBEAT_FILE_PATH)) {
            try {
                val file = File(path)
                if (!file.exists() || !file.canRead()) continue
                val timestamp = file.readText().trim().toLongOrNull() ?: continue
                val age = (System.currentTimeMillis() - timestamp).coerceAtLeast(0L)
                val current = freshest
                if (current == null || age < current) freshest = age
            } catch (_: Exception) {}
        }
        return freshest
    }

    private fun isHeartbeatFresh(): Boolean {
        val age = readHeartbeatAgeMs() ?: return false
        return age <= HEARTBEAT_MAX_AGE_MS
    }

    private fun readPidFromFile(): String? {
        for (path in listOf(PID_FILE_PATH, LEGACY_PID_FILE_PATH)) {
            try {
                val pidFile = File(path)
                if (!pidFile.exists() || !pidFile.canRead()) continue
                val pid = pidFile.readText().trim()
                if (pid.isEmpty() || !pid.all { it.isDigit() }) continue
                if (!File("/proc/$pid").exists()) continue

                val comm = try {
                    File("/proc/$pid/comm")
                        .takeIf { it.exists() && it.canRead() }
                        ?.readText()
                        ?.trim()
                } catch (_: Exception) {
                    null
                }
                if (comm != null) {
                    if (comm == "luminad") return pid
                    // A stale PID file must not mask a real daemon or report
                    // an unrelated process; try the next candidate.
                    continue
                }

                val cmdline = try {
                    File("/proc/$pid/cmdline")
                        .takeIf { it.exists() && it.canRead() }
                        ?.readText()
                        ?.replace('\u0000', ' ')
                } catch (_: Exception) {
                    null
                }
                if (cmdline?.contains("luminad") == true) return pid

                // SELinux may hide /proc/<pid>/comm from the app domain. In
                // that case a recent heartbeat is the best available proof.
                if (isHeartbeatFresh()) return pid
            } catch (_: Exception) {}
        }
        return null
    }

    private fun probeDaemon(): String? {
        // One su call performs every fallback in order: pidof, a /proc scan,
        // then an IPC ping on the new socket and the legacy fallback. This
        // keeps the polling loop cheap when the daemon is stopped.
        // timeout(1) keeps a stuck connection from blocking the caller.
        val cmd = "P=\$(pidof luminad 2>/dev/null | awk '{print \$1}'); " +
            "if [ -z \"\$P\" ]; then for d in /proc/[0-9]*; do " +
            "c=\$(cat \"\$d/comm\" 2>/dev/null); " +
            "[ \"\$c\" = luminad ] && { P=\${d#/proc/}; break; }; done; fi; " +
            "if [ -n \"\$P\" ]; then echo \"PID \$P\"; exit 0; fi; " +
            "N=\$(command -v timeout >/dev/null 2>&1 && echo 'timeout 1'); " +
            "for s in $IPC_SOCKET_PRIMARY $IPC_SOCKET_FALLBACK; do " +
            "[ -S \"\$s\" ] || continue; " +
            "printf 'PING\\n' | \$N toybox nc -w 1 -U \"\$s\" 2>/dev/null | grep -q PONG && " +
            "{ echo ALIVE; exit 0; }; " +
            "printf 'PING\\n' | \$N nc -w 1 -U \"\$s\" 2>/dev/null | grep -q PONG && " +
            "{ echo ALIVE; exit 0; }; " +
            "done; echo OFFLINE"
        val output = runSuCmd(cmd, 5)
        return when {
            output.startsWith("PID ") -> output.removePrefix("PID ")
                .trim()
                .takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
            output.contains("ALIVE") -> IPC_SENTINEL_PID
            else -> null
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
        runSuCmd(
            "[ -f $MODULE_BIN_DIR/luminad ] || [ -f $MODULE_DIR/luminad ] && echo 'OK'",
            3
        ).contains("OK")
    }

    suspend fun getDaemonPid(): String? = withContext(Dispatchers.IO) {
        readPidFromFile() ?: probeDaemon()
    }

    suspend fun isDaemonRunning(): Boolean = getDaemonPid() != null

    suspend fun getStatus(): DaemonStatus = withContext(Dispatchers.IO) {
        val pid = getDaemonPid()
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
                "rm -f $PID_FILE_PATH $HEARTBEAT_FILE_PATH " +
                "$LEGACY_PID_FILE_PATH $LEGACY_HEARTBEAT_FILE_PATH " +
                "$IPC_SOCKET_PRIMARY $IPC_SOCKET_FALLBACK",
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
            "MODDIR=$MODULE_DIR; " +
                "BINDER=\$MODDIR/bin/lumina-binder; [ -f \$BINDER ] || BINDER=\$MODDIR/lumina-binder; " +
                "DAEMON=\$MODDIR/bin/luminad; [ -f \$DAEMON ] || DAEMON=\$MODDIR/luminad; " +
                "if [ -f \$BINDER ] && ! pidof lumina-binder >/dev/null 2>&1; then " +
                "chmod 755 \$BINDER && nohup \$BINDER >/dev/null 2>&1 & sleep 1; " +
                "fi; " +
                "chmod 755 \$DAEMON && nohup \$DAEMON >/dev/null 2>&1 &",
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
