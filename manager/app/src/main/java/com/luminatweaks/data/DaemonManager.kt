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
import java.io.BufferedReader
import java.io.InputStreamReader

object DaemonManager {
    private const val MODULE_DAEMON_PATH = "/data/adb/modules/luminatweaks/luminad"

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

    suspend fun stopDaemon(): Boolean = withContext(Dispatchers.IO) {
        runSuCmd("pkill -9 luminad || killall -9 luminad 2>/dev/null")
        !isDaemonRunning()
    }

    suspend fun startDaemon(context: Context): Boolean = withContext(Dispatchers.IO) {
        autoGrantPermissions()

        if (isDaemonRunning()) return@withContext true

        // Hanya boleh jalan jika modul terpasang di KernelSU/Magisk
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
