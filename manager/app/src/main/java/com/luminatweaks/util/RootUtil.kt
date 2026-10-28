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
}
