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

package com.luminatweaks.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RootUtil {
    suspend fun checkRootAccess(): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder("su", "-c", "id").start()
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (_: Exception) {
            false
        }
    }

    suspend fun rebootDevice(reason: String = ""): Boolean = withContext(Dispatchers.IO) {
        val cmd = when (reason) {
            "" -> "svc power reboot || reboot"
            "recovery" -> "/system/bin/input keyevent 26 && svc power reboot recovery || reboot recovery"
            else -> "svc power reboot $reason || reboot $reason"
        }
        try {
            ProcessBuilder("su", "-c", cmd).start()
            true
        } catch (_: Exception) {
            false
        }
    }
}
