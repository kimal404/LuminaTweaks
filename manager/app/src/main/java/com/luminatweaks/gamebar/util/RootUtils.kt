/*
 * Copyright (C) 2026-2027 Zexshia
 * Copyright (C) 2026 kimal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.luminatweaks.gamebar.util

import com.topjohnwu.superuser.io.SuFile

object RootUtils {

    fun readRootFile(path: String): String? {
        return try {
            val file = SuFile(path)
            if (!file.exists()) return null
            file.newInputStream().bufferedReader().use { it.readText().trim() }
        } catch (_: Exception) {
            null
        }
    }

    fun writeRootFile(path: String, content: String) {
        try {
            val file = SuFile(path)
            val dir = SuFile(path.substringBeforeLast('/', ""))
            if (!dir.exists()) dir.mkdirs()
            val tmp = SuFile(dir, file.name + ".tmp_" + System.nanoTime())
            tmp.newOutputStream().use { it.write(content.toByteArray()) }
            tmp.renameTo(file)
        } catch (_: Exception) {
        }
    }
}
