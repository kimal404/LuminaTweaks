package com.luminatweaks.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

data class AppProfileConfig(
    val pkg: String,
    val saklarUtama: Boolean = false,
    val liteMode: String = "default", // "default", "on", "off"
    val dnd: Boolean = false
)

data class DisplayRrMode(val id: Int, val hz: Int)

object ConfigRepository {
    private const val CONFIG_DIR = "/data/adb/luminatweaks"
    private const val GLOBAL_CONFIG_PATH = "$CONFIG_DIR/config.json"
    private const val APP_CONFIG_PATH = "$CONFIG_DIR/app_config.json"

    private val fileMutex = Mutex()

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

    private fun ensureDir() {
        runSuCmd("mkdir -p $CONFIG_DIR")
    }

    suspend fun getGlobalLiteMode(): Boolean = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            val content = runSuCmd("cat $GLOBAL_CONFIG_PATH 2>/dev/null")
            if (content.isBlank()) return@withLock false
            try {
                JSONObject(content).optBoolean("lite_mode_global", false)
            } catch (_: Exception) {
                false
            }
        }
    }

    suspend fun setGlobalLiteMode(enabled: Boolean) = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            ensureDir()
            val json = JSONObject().apply {
                put("lite_mode_global", enabled)
            }.toString()
            runSuCmd("echo '$json' > $GLOBAL_CONFIG_PATH")
            val propVal = if (enabled) "1" else "0"
            runSuCmd("setprop persist.lumina.litemode $propVal")
        }
    }

    private fun loadAppConfigsInternal(): Map<String, AppProfileConfig> {
        val content = runSuCmd("cat $APP_CONFIG_PATH 2>/dev/null")
        if (content.isBlank()) return emptyMap()

        val result = mutableMapOf<String, AppProfileConfig>()
        try {
            val arr = JSONArray(content)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val pkg = obj.optString("pkg")
                if (pkg.isNotEmpty()) {
                    result[pkg] = AppProfileConfig(
                        pkg = pkg,
                        saklarUtama = obj.optBoolean("saklar_utama", false),
                        liteMode = obj.optString("lite_mode", "default"),
                        dnd = obj.optBoolean("dnd", false)
                    )
                }
            }
        } catch (_: Exception) {}
        return result
    }

    suspend fun getAppConfigs(): Map<String, AppProfileConfig> = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            loadAppConfigsInternal()
        }
    }

    suspend fun saveAppConfig(profile: AppProfileConfig) = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            ensureDir()
            val currentConfigs = loadAppConfigsInternal().toMutableMap()
            currentConfigs[profile.pkg] = profile

            val arr = JSONArray()
            for (item in currentConfigs.values) {
                val obj = JSONObject().apply {
                    put("pkg", item.pkg)
                    put("saklar_utama", item.saklarUtama)
                    put("lite_mode", item.liteMode)
                    put("dnd", item.dnd)
                }
                arr.put(obj)
            }
            val rawJson = arr.toString()
            runSuCmd("echo '$rawJson' > $APP_CONFIG_PATH")
        }
    }
    suspend fun getRefreshRateModes(): List<DisplayRrMode> = withContext(Dispatchers.IO) {
        val content = runSuCmd("cat /data/adb/luminatweaks/rr_modes.json 2>/dev/null")
        if (content.isBlank()) return@withContext emptyList()
        val list = mutableListOf<DisplayRrMode>()
        try {
            val arr = JSONArray(content)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(DisplayRrMode(obj.getInt("id"), obj.getInt("hz")))
            }
        } catch (_: Exception) {}
        list.sortedBy { it.hz }
    }

    suspend fun getCurrentRefreshRateId(): Int? = withContext(Dispatchers.IO) {
        val content = runSuCmd("cat /data/adb/luminatweaks/current_rr.txt 2>/dev/null").trim()
        content.toIntOrNull()
    }

    suspend fun setRefreshRate(id: Int?) = withContext(Dispatchers.IO) {
        if (id != null) {
            runSuCmd("echo $id > /data/adb/luminatweaks/current_rr.txt")
            runSuCmd("[ -d /data/adb/modules/luminatweaks ] && echo $id > /data/adb/modules/luminatweaks/current_rr.txt")
            runSuCmd("/system/bin/service call SurfaceFlinger 1035 i32 $id")
        } else {
            runSuCmd("rm -f /data/adb/luminatweaks/current_rr.txt /data/adb/modules/luminatweaks/current_rr.txt")
        }
    }
}