package com.luminatweaks.data

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.luminatweaks.model.AppItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

data class DisplayRrMode(val id: Int, val hz: Int)

data class TweaksUiState(
    val isGlobalLiteMode: Boolean = false,
    val isDisableThermal: Boolean = false,
    val rrModes: List<DisplayRrMode> = emptyList(),
    val currentRrId: Int? = null,
    val currentRendererId: String = "default",
    val isMtkDevice: Boolean = false,
    val isBypassFpsgo: Boolean = false,
    val isBoostGed: Boolean = true,
    val isLoaded: Boolean = false
)

object ConfigRepository {
    private const val CONFIG_DIR = "/data/adb/luminatweaks"
    private const val GLOBAL_CONFIG_PATH = "$CONFIG_DIR/config.json"
    private const val APP_CONFIG_PATH = "$CONFIG_DIR/app_config.json"
    private const val BATCH_SEP = "___LUMINA_SEP___"

    private val fileMutex = Mutex()

    @Volatile
    var cachedTweaksState: TweaksUiState? = null

    @Volatile
    var cachedApps: List<AppItem>? = null

    @Volatile
    var cachedAppConfigs: Map<String, AppProfileConfig>? = null

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

    fun killApp(packageName: String) {
        runSuCmd("am force-stop $packageName")
    }

    private fun ensureDir() {
        runSuCmd("mkdir -p $CONFIG_DIR")
    }

    suspend fun getTweaksState(forceRefresh: Boolean = false): TweaksUiState = withContext(Dispatchers.IO) {
        if (!forceRefresh && cachedTweaksState != null && cachedTweaksState!!.isLoaded) {
            return@withContext cachedTweaksState!!
        }

        val batchScript = listOf(
            "cat $GLOBAL_CONFIG_PATH 2>/dev/null",
            "echo '$BATCH_SEP'",
            "dt=\$(cat $CONFIG_DIR/disable_thermal.txt 2>/dev/null); [ -z \"\$dt\" ] && dt=\$(getprop persist.lumina.disable_thermal); [ -z \"\$dt\" ] && dt=\"0\"; echo \"\$dt\"",
            "echo '$BATCH_SEP'",
            "cat $CONFIG_DIR/rr_modes.json 2>/dev/null",
            "echo '$BATCH_SEP'",
            "cat $CONFIG_DIR/current_rr.txt 2>/dev/null",
            "echo '$BATCH_SEP'",
            "ren=\$(cat $CONFIG_DIR/current_renderer.txt 2>/dev/null); [ -z \"\$ren\" ] && ren=\$(getprop persist.lumina.renderer); [ -z \"\$ren\" ] && ren=\$(getprop debug.hwui.renderer); [ -z \"\$ren\" ] && ren=\"default\"; echo \"\$ren\"",
            "echo '$BATCH_SEP'",
            "[ -e /sys/kernel/fpsgo/common/force_onoff ] && echo 1 || echo 0",
            "echo '$BATCH_SEP'",
            "fgo=\$(cat $CONFIG_DIR/fpsgo_bypass.txt 2>/dev/null); [ -z \"\$fgo\" ] && fgo=\$(getprop persist.lumina.fpsgo_bypass); [ -z \"\$fgo\" ] && fgo=\"0\"; echo \"\$fgo\"",
            "echo '$BATCH_SEP'",
            "ged=\$(cat $CONFIG_DIR/ged_boost.txt 2>/dev/null); [ -z \"\$ged\" ] && ged=\$(getprop persist.lumina.ged_boost); [ -z \"\$ged\" ] && ged=\"1\"; echo \"\$ged\""
        ).joinToString(" ; ")

        val output = runSuCmd(batchScript)
        val parts = output.split(BATCH_SEP).map { it.trim() }

        val isLite = if (parts.isNotEmpty() && parts[0].isNotBlank()) {
            try {
                JSONObject(parts[0]).optBoolean("lite_mode_global", false)
            } catch (_: Exception) {
                false
            }
        } else false

        val isDisableThermal = if (parts.size > 1) parts[1] == "1" else false

        val rrModes = if (parts.size > 2 && parts[2].isNotBlank()) {
            try {
                val arr = JSONArray(parts[2])
                val list = mutableListOf<DisplayRrMode>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(DisplayRrMode(obj.getInt("id"), obj.getInt("hz")))
                }
                list.sortedBy { it.hz }
            } catch (_: Exception) {
                emptyList()
            }
        } else emptyList()

        val currentRr = if (parts.size > 3) parts[3].toIntOrNull() else null
        val currentRenderer = if (parts.size > 4 && parts[4].isNotBlank()) parts[4] else "default"
        val isMtk = if (parts.size > 5) parts[5] == "1" else false
        val isBypassFpsgo = if (parts.size > 6) parts[6] == "1" else false
        val isBoostGed = if (parts.size > 7) parts[7] == "1" else true

        val newState = TweaksUiState(
            isGlobalLiteMode = isLite,
            isDisableThermal = isDisableThermal,
            rrModes = rrModes,
            currentRrId = currentRr,
            currentRendererId = currentRenderer,
            isMtkDevice = isMtk,
            isBypassFpsgo = isBypassFpsgo,
            isBoostGed = isBoostGed,
            isLoaded = true
        )
        cachedTweaksState = newState
        newState
    }

    suspend fun loadInstalledApps(packageManager: PackageManager, forceRefresh: Boolean = false): List<AppItem> = withContext(Dispatchers.IO) {
        if (!forceRefresh && cachedApps != null) {
            return@withContext cachedApps!!
        }

        val installed = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        val parsedList = installed.map { appInfo ->
            val label = appInfo.loadLabel(packageManager).toString()
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isGame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appInfo.category == ApplicationInfo.CATEGORY_GAME
            } else {
                @Suppress("DEPRECATION")
                (appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0
            }

            AppItem(
                name = label,
                packageName = appInfo.packageName,
                isSystemApp = isSystem,
                isGame = isGame,
                isEnabled = false
            )
        }
        cachedApps = parsedList
        parsedList
    }

    suspend fun getCurrentProfile(): String = withContext(Dispatchers.IO) {
        val prop = runSuCmd("getprop sys.lumina.profile").trim()
        if (prop.isNotBlank()) prop else "Balanced"
    }

    suspend fun getGlobalLiteMode(): Boolean = withContext(Dispatchers.IO) {
        cachedTweaksState?.let { return@withContext it.isGlobalLiteMode }
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
        cachedTweaksState = cachedTweaksState?.copy(isGlobalLiteMode = enabled)
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

    suspend fun setDisableThermal(enabled: Boolean) = withContext(Dispatchers.IO) {
        cachedTweaksState = cachedTweaksState?.copy(isDisableThermal = enabled)
        ensureDir()
        val valStr = if (enabled) "1" else "0"
        runSuCmd("echo '$valStr' > $CONFIG_DIR/disable_thermal.txt && setprop persist.lumina.disable_thermal '$valStr'")
    }

    suspend fun setFpsgoBypass(enabled: Boolean) = withContext(Dispatchers.IO) {
        cachedTweaksState = cachedTweaksState?.copy(isBypassFpsgo = enabled)
        ensureDir()
        val valStr = if (enabled) "1" else "0"
        runSuCmd("echo '$valStr' > $CONFIG_DIR/fpsgo_bypass.txt && setprop persist.lumina.fpsgo_bypass '$valStr'")
    }

    suspend fun setGedBoost(enabled: Boolean) = withContext(Dispatchers.IO) {
        cachedTweaksState = cachedTweaksState?.copy(isBoostGed = enabled)
        ensureDir()
        val valStr = if (enabled) "1" else "0"
        runSuCmd("echo '$valStr' > $CONFIG_DIR/ged_boost.txt && setprop persist.lumina.ged_boost '$valStr'")
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
                        refreshRate = obj.optString("refresh_rate", "default"),
                        renderer = obj.optString("renderer", "default"),
                        dnd = obj.optString("dnd", "default"),
                        textureEnabled = obj.optBoolean("texture_enabled", false),
                        textureDetail = obj.optInt("texture_detail", 0),
                        pixelSharp = obj.optBoolean("pixel_sharp", false),
                        hardDistance = obj.optBoolean("hard_distance", false)
                    )
                }
            }
        } catch (_: Exception) {}
        return result
    }

    suspend fun getAppConfigs(forceRefresh: Boolean = false): Map<String, AppProfileConfig> = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            if (!forceRefresh && cachedAppConfigs != null) {
                return@withLock cachedAppConfigs!!
            }
            val loaded = loadAppConfigsInternal()
            cachedAppConfigs = loaded
            loaded
        }
    }

    suspend fun saveAppConfig(profile: AppProfileConfig) = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            ensureDir()
            val currentConfigs = (cachedAppConfigs ?: loadAppConfigsInternal()).toMutableMap()
            currentConfigs[profile.pkg] = profile
            cachedAppConfigs = currentConfigs

            val arr = JSONArray()
            for (item in currentConfigs.values) {
                val obj = JSONObject().apply {
                    put("pkg", item.pkg)
                    put("saklar_utama", item.saklarUtama)
                    put("lite_mode", item.liteMode)
                    put("refresh_rate", item.refreshRate)
                    put("renderer", item.renderer)
                    put("dnd", item.dnd)
                    put("texture_enabled", item.textureEnabled)
                    put("texture_detail", item.textureDetail)
                    put("pixel_sharp", item.pixelSharp)
                    put("hard_distance", item.hardDistance)
                }
                arr.put(obj)
            }
            val rawJson = arr.toString()
            runSuCmd("echo '$rawJson' > $APP_CONFIG_PATH")
        }
    }

    suspend fun getRefreshRateModes(): List<DisplayRrMode> = withContext(Dispatchers.IO) {
        cachedTweaksState?.let { return@withContext it.rrModes }
        val content = runSuCmd("cat $CONFIG_DIR/rr_modes.json 2>/dev/null")
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
        cachedTweaksState?.let { return@withContext it.currentRrId }
        val content = runSuCmd("cat $CONFIG_DIR/current_rr.txt 2>/dev/null").trim()
        content.toIntOrNull()
    }

    suspend fun setRefreshRate(id: Int?) = withContext(Dispatchers.IO) {
        cachedTweaksState = cachedTweaksState?.copy(currentRrId = id)
        if (id != null) {
            runSuCmd("echo $id > $CONFIG_DIR/current_rr.txt")
            runSuCmd("[ -d /data/adb/modules/luminatweaks ] && echo $id > /data/adb/modules/luminatweaks/current_rr.txt")
            runSuCmd("/system/bin/service call SurfaceFlinger 1035 i32 $id")
        } else {
            runSuCmd("rm -f $CONFIG_DIR/current_rr.txt /data/adb/modules/luminatweaks/current_rr.txt")
        }
    }

    suspend fun setRenderer(id: String) = withContext(Dispatchers.IO) {
        cachedTweaksState = cachedTweaksState?.copy(currentRendererId = id)
        ensureDir()
        val cmds = mutableListOf<String>()
        cmds.add("echo '$id' > $CONFIG_DIR/current_renderer.txt")
        cmds.add("[ -d /data/adb/modules/luminatweaks ] && echo '$id' > /data/adb/modules/luminatweaks/current_renderer.txt")
        cmds.add("setprop persist.lumina.renderer '$id'")

        if (id == "default") {
            cmds.add("setprop debug.hwui.renderer ''")
            cmds.add("setprop debug.renderengine.backend ''")
            cmds.add("setprop debug.hwui.render_thread ''")
            cmds.add("setprop debug.skia.threaded_mode ''")
            cmds.add("resetprop --delete ro.hwui.use_vulkan 2>/dev/null || resetprop ro.hwui.use_vulkan ''")
        } else {
            cmds.add("setprop debug.hwui.renderer '$id'")
            if (id.contains("threaded")) {
                cmds.add("setprop debug.hwui.render_thread true")
                if (id.contains("skia")) {
                    cmds.add("setprop debug.skia.threaded_mode true")
                } else {
                    cmds.add("setprop debug.skia.threaded_mode false")
                }
            } else {
                cmds.add("setprop debug.hwui.render_thread false")
                cmds.add("setprop debug.skia.threaded_mode false")
            }

            when (id) {
                "skiavk", "skiavkthreaded", "vulkan" -> {
                    cmds.add("setprop debug.renderengine.backend vulkan")
                    cmds.add("resetprop ro.hwui.use_vulkan true")
                }
                "skiagl", "skiaglthreaded", "opengl", "openglthreaded" -> {
                    cmds.add("setprop debug.renderengine.backend gles")
                    cmds.add("resetprop ro.hwui.use_vulkan false")
                }
            }
        }
        runSuCmd(cmds.joinToString(" && "))
    }
}
