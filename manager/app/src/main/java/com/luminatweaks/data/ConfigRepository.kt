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

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import com.luminatweaks.model.AppItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

data class DisplayRrMode(val id: Int, val hz: Int)

data class TweaksUiState(
    val isGlobalLiteMode: Boolean = false,
    val rrModes: List<DisplayRrMode> = emptyList(),
    val currentRrId: Int? = null,
    val currentRendererId: String = "default",
    val isAutoDnd: Boolean = false,
    val currentIoScheduler: String = "mq-deadline",
    val zramSizeGb: Int = 8,
    val zramAlgorithm: String = "zstd",
    val swappiness: Int = 100,
    val isLoaded: Boolean = false
)

object ConfigRepository {
    private const val CONFIG_DIR = "/data/adb/.config/lumina"
    private const val GLOBAL_CONFIG_PATH = "$CONFIG_DIR/config.json"
    private const val APP_CONFIG_PATH = "$CONFIG_DIR/app_config.json"
    private const val IPC_SOCKET_PRIMARY = "/dev/socket/lumina_ipc.sock"
    private const val IPC_SOCKET_FALLBACK = "/data/local/tmp/lumina_ipc.sock"
    private const val BATCH_SEP = "___LUMINA_SEP___"

    private val GAMELIST_JSON_CANDIDATES = listOf(
        "/data/adb/.config/lumina/gamelist.json",
        "/data/adb/modules/luminatweaks/gamelist.json",
        "/data/adb/modules_update/luminatweaks/gamelist.json"
    )

    private val GAMELIST_TXT_CANDIDATES = listOf(
        "/data/adb/modules/luminatweaks/gamelist.txt",
        "/data/adb/modules_update/luminatweaks/gamelist.txt",
        "/data/adb/.config/lumina/gamelist.txt",
        "/data/adb/modules/luminatweaks/games.txt",
        "/data/adb/modules_update/luminatweaks/games.txt",
        "/data/adb/.config/lumina/games.txt"
    )

    private val fileMutex = Mutex()

    @Volatile
    var cachedTweaksState: TweaksUiState? = null

    @Volatile
    var cachedApps: List<AppItem>? = null

    @Volatile
    var cachedAppConfigs: Map<String, AppProfileConfig>? = null

    @Volatile
    var defaultGames: Set<String> = emptySet()

    @Volatile
    var disabledGames: Set<String> = emptySet()

    @Volatile
    var customGames: Set<String> = emptySet()

    private fun runSuCmd(cmd: String, timeoutSeconds: Long = 6): String {
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

    private fun writeFileAtomically(targetPath: String, content: String) {
        val encoded = Base64.encodeToString(content.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
        val tmpPath = "$targetPath.tmp"
        runSuCmd(
            "mkdir -p $CONFIG_DIR && " +
                "printf '%s' '$encoded' | base64 -d > '$tmpPath' && " +
                "mv -f '$tmpPath' '$targetPath'",
            5
        )
    }

    private fun notifyDaemonReload() {
        runSuCmd(
            "(printf 'RELOAD\\n' | nc -U $IPC_SOCKET_PRIMARY 2>/dev/null || " +
                "printf 'RELOAD\\n' | toybox nc -U $IPC_SOCKET_PRIMARY 2>/dev/null || " +
                "printf 'RELOAD\\n' | nc -U $IPC_SOCKET_FALLBACK 2>/dev/null || " +
                "printf 'RELOAD\\n' | toybox nc -U $IPC_SOCKET_FALLBACK 2>/dev/null) >/dev/null 2>&1 &",
            3
        )
    }

    fun killApp(packageName: String) {
        val safePkg = packageName.replace(Regex("[^a-zA-Z0-9._]"), "")
        if (safePkg.isNotEmpty()) {
            runSuCmd("am force-stop $safePkg", 4)
        }
    }

    private fun ensureDir() {
        runSuCmd("mkdir -p $CONFIG_DIR", 3)
    }

    private fun updateGlobalConfigInternal(updater: (JSONObject) -> Unit) {
        ensureDir()
        val currentContent = runSuCmd("cat $GLOBAL_CONFIG_PATH 2>/dev/null", 4)
        val json = try {
            if (currentContent.isNotBlank()) JSONObject(currentContent) else JSONObject()
        } catch (_: Exception) {
            JSONObject()
        }
        updater(json)
        writeFileAtomically(GLOBAL_CONFIG_PATH, json.toString())
        notifyDaemonReload()
    }

    suspend fun reapplyGlobalTweaks() = withContext(Dispatchers.IO) {
        notifyDaemonReload()
    }

    suspend fun getTweaksState(forceRefresh: Boolean = false): TweaksUiState = withContext(Dispatchers.IO) {
        if (!forceRefresh && cachedTweaksState != null && cachedTweaksState!!.isLoaded) {
            return@withContext cachedTweaksState!!
        }

        val batchScript = listOf(
            "cat $GLOBAL_CONFIG_PATH 2>/dev/null",
            "echo '$BATCH_SEP'",
            "cat $CONFIG_DIR/rr_modes.json 2>/dev/null",
            "echo '$BATCH_SEP'",
            "cat $CONFIG_DIR/current_rr.txt 2>/dev/null",
            "echo '$BATCH_SEP'",
            "ren=\$(cat $CONFIG_DIR/current_renderer.txt 2>/dev/null); [ -z \"\$ren\" ] && ren=\$(getprop persist.lumina.renderer); [ -z \"\$ren\" ] && ren=\$(getprop debug.hwui.renderer); [ -z \"\$ren\" ] && ren=\"default\"; echo \"\$ren\""
        ).joinToString(" ; ")

        val output = runSuCmd(batchScript, 5)
        val parts = output.split(BATCH_SEP).map { it.trim() }

        val globalJson = if (parts.isNotEmpty() && parts[0].isNotBlank()) {
            try {
                JSONObject(parts[0])
            } catch (_: Exception) {
                null
            }
        } else null

        val isLite = globalJson?.optBoolean("lite_mode_global", false) ?: false
        val isAutoDnd = globalJson?.optBoolean("auto_dnd", false) ?: false
        val currentIoSched = globalJson?.optString("io_scheduler", "mq-deadline")?.takeIf { it.isNotBlank() } ?: "mq-deadline"
        val zramSize = globalJson?.optInt("zram_size_gb", 8) ?: 8
        val zramAlgo = globalJson?.optString("zram_algorithm", "zstd")?.takeIf { it.isNotBlank() } ?: "zstd"
        val swappiness = globalJson?.optInt("swappiness", 100) ?: 100

        val rrModes = if (parts.size > 1 && parts[1].isNotBlank()) {
            try {
                val arr = JSONArray(parts[1])
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

        val currentRr = if (parts.size > 2) parts[2].toIntOrNull() else null
        val currentRenderer = if (parts.size > 3 && parts[3].isNotBlank()) parts[3] else "default"

        val newState = TweaksUiState(
            isGlobalLiteMode = isLite,
            rrModes = rrModes,
            currentRrId = currentRr,
            currentRendererId = currentRenderer,
            isAutoDnd = isAutoDnd,
            currentIoScheduler = currentIoSched,
            zramSizeGb = zramSize,
            zramAlgorithm = zramAlgo,
            swappiness = swappiness,
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

    suspend fun setGlobalLiteMode(enabled: Boolean) = withContext(Dispatchers.IO) {
        cachedTweaksState = cachedTweaksState?.copy(isGlobalLiteMode = enabled)
        fileMutex.withLock {
            updateGlobalConfigInternal { it.put("lite_mode_global", enabled) }
            val propVal = if (enabled) "1" else "0"
            runSuCmd("setprop persist.lumina.litemode $propVal && echo '$propVal' > $CONFIG_DIR/lite_mode.conf", 4)
        }
    }

    suspend fun setAutoDnd(enabled: Boolean) = withContext(Dispatchers.IO) {
        cachedTweaksState = cachedTweaksState?.copy(isAutoDnd = enabled)
        fileMutex.withLock {
            updateGlobalConfigInternal { it.put("auto_dnd", enabled) }
            val propVal = if (enabled) "1" else "0"
            runSuCmd("setprop persist.lumina.autodnd $propVal", 4)
        }
    }

    suspend fun setIoScheduler(scheduler: String) = withContext(Dispatchers.IO) {
        cachedTweaksState = cachedTweaksState?.copy(currentIoScheduler = scheduler)
        fileMutex.withLock {
            updateGlobalConfigInternal { it.put("io_scheduler", scheduler) }
        }
    }

    suspend fun setZramConfig(
        sizeGb: Int? = null,
        algorithm: String? = null,
        swappiness: Int? = null
    ) = withContext(Dispatchers.IO) {
        if (sizeGb == null && algorithm == null && swappiness == null) {
            return@withContext
        }

        val cached = cachedTweaksState
        if (cached != null) {
            cachedTweaksState = cached.copy(
                zramSizeGb = sizeGb ?: cached.zramSizeGb,
                zramAlgorithm = algorithm ?: cached.zramAlgorithm,
                swappiness = swappiness ?: cached.swappiness
            )
        }

        fileMutex.withLock {
            updateGlobalConfigInternal { json ->
                sizeGb?.let { json.put("zram_size_gb", it) }
                algorithm?.let { json.put("zram_algorithm", it) }
                swappiness?.let { json.put("swappiness", it) }
            }
        }
    }

    private fun loadAppConfigsInternal(): Map<String, AppProfileConfig> {
        val content = runSuCmd("cat $APP_CONFIG_PATH 2>/dev/null", 4)
        if (content.isBlank()) return emptyMap()

        val result = mutableMapOf<String, AppProfileConfig>()
        try {
            val arr = JSONArray(content)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val pkg = obj.optString("pkg")
                if (pkg.isNotEmpty()) {
                    val rawDetail = obj.optInt("texture_detail", 0).coerceIn(0, 4)
                    val resolvedLod = (if (obj.has("texture_lod")) obj.optInt("texture_lod", rawDetail) else rawDetail).coerceIn(0, 4)

                    val rawDriver = obj.optString("texture_driver", "").trim().lowercase()
                    val legacyEnabled = obj.optBoolean("texture_enabled", false)
                    val resolvedDriver = when {
                        rawDriver == "angle" || rawDriver == "native" -> rawDriver
                        legacyEnabled || resolvedLod > 0 -> "angle"
                        else -> "native"
                    }
                    val resolvedTextureEnabled = resolvedDriver == "angle" || legacyEnabled

                    result[pkg] = AppProfileConfig(
                        pkg = pkg,
                        saklarUtama = obj.optBoolean("saklar_utama", false),
                        disabled = obj.optBoolean("disabled", false),
                        custom = if (obj.has("custom")) {
                            obj.optBoolean("custom", false)
                        } else {
                            obj.optBoolean("saklar_utama", false)
                        },
                        liteMode = obj.optString("lite_mode", "default"),
                        refreshRate = obj.optString("refresh_rate", "default"),
                        renderer = obj.optString("renderer", "default"),
                        dnd = obj.optString("dnd", "default"),
                        textureDriver = resolvedDriver,
                        textureLod = resolvedLod,
                        textureEnabled = resolvedTextureEnabled,
                        textureDetail = resolvedLod,
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
            refreshGameSets(loaded)
            loaded
        }
    }

    private fun persistAppConfigsInternal(configs: Map<String, AppProfileConfig>) {
        ensureDir()
        val arr = JSONArray()
        for (item in configs.values) {
            val normalizedLod = (if (item.textureLod > 0) item.textureLod else item.textureDetail).coerceIn(0, 4)
            val normalizedDriver = when {
                item.textureDriver.equals("angle", ignoreCase = true) -> "angle"
                item.textureDriver.equals("native", ignoreCase = true) -> "native"
                item.textureEnabled || normalizedLod > 0 -> "angle"
                else -> "native"
            }
            val normalizedEnabled = normalizedDriver == "angle" || item.textureEnabled

            val obj = JSONObject().apply {
                put("pkg", item.pkg)
                put("saklar_utama", item.saklarUtama)
                put("disabled", item.disabled)
                put("custom", item.custom)
                put("lite_mode", item.liteMode)
                put("refresh_rate", item.refreshRate)
                put("renderer", item.renderer)
                put("dnd", item.dnd)
                put("texture_driver", normalizedDriver)
                put("texture_lod", normalizedLod)
                put("texture_enabled", normalizedEnabled)
                put("texture_detail", normalizedLod)
                put("pixel_sharp", item.pixelSharp)
                put("hard_distance", item.hardDistance)
            }
            arr.put(obj)
        }
        writeFileAtomically(APP_CONFIG_PATH, arr.toString())

        val globalContent = runSuCmd("cat $GLOBAL_CONFIG_PATH 2>/dev/null", 4)
        val globalJson = try {
            if (globalContent.isNotBlank()) JSONObject(globalContent) else JSONObject()
        } catch (_: Exception) {
            JSONObject()
        }
        val disabledSet = JSONArray()
        configs.values.filter { it.disabled }.forEach { disabledSet.put(it.pkg) }
        globalJson.put("disabled_games", disabledSet)
        writeFileAtomically(GLOBAL_CONFIG_PATH, globalJson.toString())
        notifyDaemonReload()
    }

    suspend fun saveAppConfig(profile: AppProfileConfig) = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            val normalizedLod = (if (profile.textureLod > 0) profile.textureLod else profile.textureDetail).coerceIn(0, 4)
            val normalizedDriver = when {
                profile.textureDriver.equals("angle", ignoreCase = true) -> "angle"
                profile.textureDriver.equals("native", ignoreCase = true) -> "native"
                profile.textureEnabled || normalizedLod > 0 -> "angle"
                else -> "native"
            }
            val normalizedProfile = profile.copy(
                textureDriver = normalizedDriver,
                textureLod = normalizedLod,
                textureEnabled = normalizedDriver == "angle" || profile.textureEnabled,
                textureDetail = normalizedLod
            )

            val currentConfigs = (cachedAppConfigs ?: loadAppConfigsInternal()).toMutableMap()
            currentConfigs[normalizedProfile.pkg] = normalizedProfile
            cachedAppConfigs = currentConfigs
            refreshGameSets(currentConfigs)
            persistAppConfigsInternal(currentConfigs)
        }
    }

    private fun readFirstExisting(paths: List<String>): String {
        if (paths.isEmpty()) return ""
        val args = paths.joinToString(" ")
        val script = "for f in $args; do if [ -f \"\$f\" ]; then cat \"\$f\" 2>/dev/null; break; fi; done"
        return runSuCmd(script, 4)
    }

    private data class GameListMeta(
        val liteMode: String = "default",
        val dnd: String = "default"
    )

    private fun normalizeOption(value: String?): String = when (value?.trim()?.lowercase()) {
        "on", "true", "1" -> "on"
        "off", "false", "0" -> "off"
        else -> "default"
    }

    private fun loadGameWhitelist(): Map<String, GameListMeta> {
        val jsonContent = readFirstExisting(GAMELIST_JSON_CANDIDATES)
        if (jsonContent.isNotBlank()) {
            try {
                val obj = JSONObject(jsonContent)
                val keys = obj.keys()
                val map = mutableMapOf<String, GameListMeta>()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val pkg = key.substringBefore(':').trim()
                    if (pkg.isEmpty()) continue
                    val item = obj.optJSONObject(key)
                    val liteRaw = item?.optString("lite_mode")?.takeIf { it.isNotBlank() }
                        ?: item?.optString("liteMode")
                    map[pkg] = GameListMeta(
                        liteMode = normalizeOption(liteRaw),
                        dnd = normalizeOption(item?.optString("dnd"))
                    )
                }
                if (map.isNotEmpty()) {
                    defaultGames = map.keys.toSet()
                    return map
                }
            } catch (_: Exception) {}
        }

        val output = readFirstExisting(GAMELIST_TXT_CANDIDATES)
        if (output.isBlank()) return emptyMap()

        val map = mutableMapOf<String, GameListMeta>()
        output.lineSequence()
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }
            .map { it.substringBefore(' ').trim().substringBefore(':').trim() }
            .filter { it.isNotEmpty() }
            .forEach { map[it] = GameListMeta() }
        defaultGames = map.keys.toSet()
        return map
    }

    suspend fun syncInstalledGameWhitelist(installedPackages: Collection<String>): Map<String, AppProfileConfig> =
        withContext(Dispatchers.IO) {
            fileMutex.withLock {
                val whitelist = loadGameWhitelist()
                val targets = whitelist.keys.intersect(installedPackages.toSet())
                val currentConfigs = (cachedAppConfigs ?: loadAppConfigsInternal()).toMutableMap()

                if (targets.isEmpty()) {
                    cachedAppConfigs = currentConfigs
                    refreshGameSets(currentConfigs)
                    return@withLock currentConfigs
                }

                var changed = false
                for (pkg in targets) {
                    val existing = currentConfigs[pkg]
                    if (existing == null) {
                        val meta = whitelist[pkg] ?: GameListMeta()
                        currentConfigs[pkg] = AppProfileConfig(
                            pkg = pkg,
                            saklarUtama = true,
                            disabled = false,
                            custom = false,
                            liteMode = meta.liteMode,
                            dnd = meta.dnd
                        )
                        changed = true
                    } else if (!existing.disabled) {
                        val normalized = existing.copy(
                            saklarUtama = true,
                            custom = if (whitelist.containsKey(pkg)) false else existing.custom
                        )
                        if (normalized != existing) {
                            currentConfigs[pkg] = normalized
                            changed = true
                        }
                    }
                }

                if (changed) {
                    persistAppConfigsInternal(currentConfigs)
                }
                cachedAppConfigs = currentConfigs
                refreshGameSets(currentConfigs)
                currentConfigs
            }
        }

    private fun refreshGameSets(configs: Map<String, AppProfileConfig>) {
        disabledGames = configs.values.filter { it.disabled }.map { it.pkg }.toSet()
        customGames = configs.values.filter { it.custom && !it.disabled }.map { it.pkg }.toSet()
    }

    fun isAppActive(pkg: String): Boolean {
        val isDefault = defaultGames.contains(pkg)
        val isCustom = customGames.contains(pkg)
        return !disabledGames.contains(pkg) && (isDefault || isCustom)
    }

    suspend fun getRefreshRateModes(): List<DisplayRrMode> = withContext(Dispatchers.IO) {
        cachedTweaksState?.let {
            if (it.rrModes.isNotEmpty()) return@withContext it.rrModes
        }
        val content = runSuCmd("cat $CONFIG_DIR/rr_modes.json 2>/dev/null", 4)
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
        val content = runSuCmd("cat $CONFIG_DIR/current_rr.txt 2>/dev/null", 3).trim()
        content.toIntOrNull()
    }

    suspend fun setRefreshRate(id: Int?) = withContext(Dispatchers.IO) {
        cachedTweaksState = cachedTweaksState?.copy(currentRrId = id)
        ensureDir()
        if (id != null) {
            runSuCmd("echo $id > $CONFIG_DIR/current_rr.txt && /system/bin/service call SurfaceFlinger 1035 i32 $id", 4)
        } else {
            runSuCmd("rm -f $CONFIG_DIR/current_rr.txt", 3)
        }
        notifyDaemonReload()
    }

    suspend fun setRenderer(id: String) = withContext(Dispatchers.IO) {
        cachedTweaksState = cachedTweaksState?.copy(currentRendererId = id)
        ensureDir()
        val cmds = mutableListOf<String>()
        cmds.add("echo '$id' > $CONFIG_DIR/current_renderer.txt")
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
        runSuCmd(cmds.joinToString(" && "), 5)
        notifyDaemonReload()
    }
}
