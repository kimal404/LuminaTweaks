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

package com.luminatweaks.service

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.luminatweaks.MainActivity
import com.luminatweaks.R
import com.luminatweaks.data.ConfigRepository
import com.luminatweaks.data.DaemonManager
import com.luminatweaks.ui.screen.FloatingFpsConfig
import com.luminatweaks.ui.screen.FloatingFpsPrefs
import com.luminatweaks.ui.screen.FpsOverlayCorner
import com.luminatweaks.ui.theme.OverlayPalette
import com.luminatweaks.ui.theme.ThemeController
import com.luminatweaks.ui.theme.overlayPalette
import com.luminatweaks.util.DeviceUtil
import com.luminatweaks.util.RootUtil
import java.io.File
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class LiveFpsTelemetry(
    val fps: Int = 0,
    val targetHz: Int = 120,
    val frametimeMs: Float = 8.3f,
    val cpuClusterLabel: String = "-- GHz",
    val tempLabel: String = "37.0°C",
    val ramLabel: String = "RAM --%",
    val profileLabel: String = "Balanced",
    val gameActive: Boolean = false
)

class FloatingFpsService : Service() {

    companion object {
        private const val CHANNEL_ID = "lumina_floating_fps_channel"
        private const val NOTIFICATION_ID = 4041
        private const val PUBLIC_FPS_PATH = "/data/local/tmp/lumina.fps"
        private const val FEED_MAX_AGE_MS = 2_000L
        private const val GAME_STALE_GRACE_MS = 10_000L
        private const val ALWAYS_ON_PREVIEW_SECONDS = 3600
        private const val PREVIEW_RENEW_MS = 30 * 60 * 1000L

        const val ACTION_SYNC = "com.luminatweaks.action.FPS_OVERLAY_SYNC"
        const val ACTION_PREVIEW_BURST = "com.luminatweaks.action.FPS_OVERLAY_PREVIEW_BURST"
        const val ACTION_STOP = "com.luminatweaks.action.FPS_OVERLAY_STOP"

        suspend fun ensureOverlayPermission(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)) {
                return true
            }
            val pkg = context.packageName
            RootUtil.runAsRoot(
                "appops set $pkg SYSTEM_ALERT_WINDOW allow 2>/dev/null || " +
                    "cmd appops set $pkg SYSTEM_ALERT_WINDOW allow 2>/dev/null"
            )
            delay(150L)
            if (Settings.canDrawOverlays(context)) return true

            withContext(Dispatchers.Main) {
                runCatching {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$pkg")
                    ).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            }
            return false
        }

        fun sync(context: Context, config: FloatingFpsConfig = FloatingFpsPrefs.load(context)) {
            if (!config.enabled) {
                runCatching {
                    context.stopService(Intent(context, FloatingFpsService::class.java))
                }
                return
            }
            val intent = Intent(context, FloatingFpsService::class.java).apply {
                action = ACTION_SYNC
            }
            startCompat(context, intent)
        }

        fun triggerPreviewBurst(context: Context) {
            val intent = Intent(context, FloatingFpsService::class.java).apply {
                action = ACTION_PREVIEW_BURST
            }
            startCompat(context, intent)
        }

        private fun startCompat(context: Context, intent: Intent) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var windowManager: WindowManager? = null
    private var overlayView: FpsOverlayView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var activeConfig = FloatingFpsConfig()
    private var liveTelemetry = LiveFpsTelemetry()

    private var telemetryJob: Job? = null
    private var previewBurstJob: Job? = null
    private var burstUntilMs = 0L
    private var isOverlayAttached = false
    private var lastAppliedCorner: FpsOverlayCorner? = null
    private var hasCustomDragPosition = false
    private var previewLease = false
    private var lastLeaseRequestMs = 0L

    private var lastForegroundPkg: String? = null
    private var lastForegroundTs = 0L
    private var cachedGameList: Set<String> = emptySet()
    private var cachedGameListStampMs = 0L

    private var cachedHz = 60
    private var cachedCpuCluster = "-- GHz"
    private var cachedTemp = "37.0°C"
    private var cachedRam = "RAM --%"
    private var cachedProfile = "Balanced"
    private var sensorStampMs = 0L
    private var cacheStampMs = 0L

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                val updated = FloatingFpsPrefs.load(this).copy(enabled = false)
                FloatingFpsPrefs.save(this, updated)
                startForegroundCompat()
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_PREVIEW_BURST -> {
                activeConfig = FloatingFpsPrefs.load(this)
                burstUntilMs = System.currentTimeMillis() + 5_500L
                startForegroundCompat()
                requestDaemonPreview(8)
                startTelemetryLoop()
                previewBurstJob?.cancel()
                previewBurstJob = serviceScope.launch {
                    delay(5_600L)
                    burstUntilMs = 0L
                    if (!activeConfig.enabled) {
                        stopSelf()
                    } else {
                        applyPreviewLease(force = true)
                    }
                }
                return START_STICKY
            }
        }

        val loaded = FloatingFpsPrefs.load(this)
        val cornerChanged = lastAppliedCorner != null && lastAppliedCorner != loaded.corner
        if (cornerChanged) {
            hasCustomDragPosition = false
        }
        activeConfig = loaded

        if (!loaded.enabled && System.currentTimeMillis() >= burstUntilMs) {
            startForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundCompat()
        if (isOverlayAttached) {
            overlayView?.applyState(loaded, liveTelemetry)
            applyLayoutParamsUpdate(resetCorner = cornerChanged)
        }
        startTelemetryLoop()
        applyPreviewLease()
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlayView?.setPalette(ThemeController.overlayPalette(this))
        val lp = layoutParams
        if (lp != null) {
            val metrics = resources.displayMetrics
            lp.x = lp.x.coerceIn(0, (metrics.widthPixels - 60).coerceAtLeast(0))
            lp.y = lp.y.coerceIn(0, (metrics.heightPixels - 44).coerceAtLeast(0))
        }
        overlayView?.post { applyLayoutParamsUpdate(resetCorner = !hasCustomDragPosition) }
    }

    override fun onDestroy() {
        previewBurstJob?.cancel()
        telemetryJob?.cancel()
        detachOverlay()
        if (previewLease || System.currentTimeMillis() < burstUntilMs) {
            previewLease = false
            Thread {
                runBlocking { runCatching { DaemonManager.stopFpsPreview() } }
            }.start()
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startForegroundCompat() {
        val notification = buildNotification(isShowing = isOverlayAttached)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startTelemetryLoop() {
        if (telemetryJob?.isActive == true) return
        telemetryJob = serviceScope.launch {
            var lastGameSeenMs = 0L

            while (isActive) {
                val cfg = activeConfig
                val now = System.currentTimeMillis()
                val inBurst = now < burstUntilMs

                if (!cfg.enabled && !inBurst) {
                    stopSelf()
                    break
                }

                val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
                val screenAwake = pm?.isInteractive ?: true

                val (feed, fgGame) = withContext(Dispatchers.IO) {
                    val f = readFeed()
                    val g = if (cfg.autoGame && screenAwake) isForegroundGameActive(now) else false
                    f to g
                }

                val fresh = feed != null && now - feed.epochMs <= FEED_MAX_AGE_MS
                val detectedGame = fgGame || (fresh && feed?.gameActive == true)
                if (detectedGame) {
                    lastGameSeenMs = now
                }

                val show = screenAwake && (inBurst || (cfg.enabled && (
                    !cfg.autoGame ||
                        detectedGame ||
                        (lastGameSeenMs > 0L && now - lastGameSeenMs <= GAME_STALE_GRACE_MS)
                    )))

                if (!show) {
                    if (isOverlayAttached) {
                        detachOverlay()
                        updateNotification(isShowing = false)
                    }
                    delay(350L)
                    continue
                }

                val daemonReading = fresh && feed != null && feed.fps > 0f
                val fps: Float
                val frametime: Float
                if (daemonReading) {
                    fps = (feed?.fps ?: 0f).coerceAtLeast(0f)
                    frametime = (feed?.frametimeMs ?: 0f).takeIf { it > 0f }
                        ?: if (fps > 0f) 1000f / fps else 0f
                } else {
                    fps = 0f
                    frametime = 0f
                }

                if (!cfg.autoGame && lastLeaseRequestMs > 0L && now - lastLeaseRequestMs >= PREVIEW_RENEW_MS) {
                    lastLeaseRequestMs = now
                    requestDaemonPreview(ALWAYS_ON_PREVIEW_SECONDS)
                }

                if (now - sensorStampMs >= 1_000L) {
                    sensorStampMs = now
                    withContext(Dispatchers.IO) {
                        if (cfg.showCpuCluster) cachedCpuCluster = readCpuClusterLabel()
                        if (cfg.showTemp) cachedTemp = readBatteryTempAndPowerLabel()
                        if (cfg.showRam) cachedRam = readRamUsageLabel()
                        if (cfg.showProfile) {
                            cachedProfile = readSystemProperty("sys.lumina.profile") ?: "Balanced"
                        }
                    }
                }

                if (now - cacheStampMs >= 2_500L) {
                    cacheStampMs = now
                    cachedHz = readDisplayHz().coerceAtLeast(30)
                    overlayView?.setPalette(ThemeController.overlayPalette(this@FloatingFpsService))
                }

                liveTelemetry = LiveFpsTelemetry(
                    fps = if (fps > 0f) fps.roundToInt().coerceIn(1, max(cachedHz, 60)) else 0,
                    targetHz = cachedHz,
                    frametimeMs = if (fps > 0f) frametime.coerceIn(0f, 250f) else 0f,
                    cpuClusterLabel = cachedCpuCluster,
                    tempLabel = cachedTemp,
                    ramLabel = cachedRam,
                    profileLabel = cachedProfile,
                    gameActive = detectedGame || inBurst
                )

                if (!isOverlayAttached) {
                    attachOverlay()
                    updateNotification(isShowing = isOverlayAttached)
                }
                overlayView?.applyState(cfg, liveTelemetry)

                delay(cfg.intervalMs.toLong().coerceIn(250L, 2_000L))
            }
        }
    }

    private fun isForegroundGameActive(nowMs: Long): Boolean {
        val usm = getSystemService(UsageStatsManager::class.java) ?: return false
        val lookback = if (lastForegroundPkg == null) 300_000L else 12_000L
        val events = runCatching { usm.queryEvents(nowMs - lookback, nowMs) }.getOrNull()
        if (events != null) {
            val ev = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(ev)
                if (ev.eventType == UsageEvents.Event.ACTIVITY_RESUMED && ev.timeStamp >= lastForegroundTs) {
                    val pkg = ev.packageName
                    if (!pkg.isNullOrBlank() &&
                        pkg != "android" &&
                        pkg != "com.android.systemui" &&
                        !pkg.contains("permissioncontroller", ignoreCase = true)
                    ) {
                        lastForegroundTs = ev.timeStamp
                        lastForegroundPkg = pkg
                    }
                }
            }
        }

        val fg = lastForegroundPkg ?: return false
        if (fg == packageName || isLauncherPackage(fg)) return false

        val configured = ConfigRepository.cachedAppConfigs?.get(fg)
        if (configured != null && !configured.disabled && (configured.saklarUtama || configured.custom)) {
            return true
        }

        if (nowMs - cachedGameListStampMs >= 4_000L) {
            cachedGameListStampMs = nowMs
            cachedGameList = readLuminaGameList()
        }
        if (cachedGameList.contains(fg)) {
            return true
        }

        return runCatching {
            val info = packageManager.getApplicationInfo(fg, 0)
            @Suppress("DEPRECATION")
            val legacyGame = (info.flags and ApplicationInfo.FLAG_IS_GAME) != 0
            val modernGame = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                info.category == ApplicationInfo.CATEGORY_GAME
            legacyGame || modernGame
        }.getOrDefault(false)
    }

    private fun isLauncherPackage(pkg: String): Boolean {
        return runCatching {
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolved = packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            resolved?.activityInfo?.packageName == pkg
        }.getOrDefault(false)
    }

    private fun readLuminaGameList(): Set<String> {
        val jsonPaths = listOf(
            "/data/adb/.config/lumina/gamelist.json",
            "/data/adb/modules/luminatweaks/config/gamelist.json",
            "/data/adb/modules/luminatweaks/gamelist.json"
        )
        for (path in jsonPaths) {
            val raw = runCatching { File(path).takeIf { it.canRead() }?.readText() }.getOrNull()
                ?.takeIf { it.isNotBlank() } ?: continue
            val parsed = runCatching {
                val json = JSONObject(raw)
                buildSet {
                    val keys = json.keys()
                    while (keys.hasNext()) {
                        val name = keys.next().substringBefore(':').trim()
                        if (name.isNotEmpty()) add(name)
                    }
                }
            }.getOrNull()
            if (!parsed.isNullOrEmpty()) return parsed
        }

        val txtPaths = listOf(
            "/data/adb/.config/lumina/gamelist.txt",
            "/data/adb/.config/lumina/games.txt",
            "/data/adb/modules/luminatweaks/config/gamelist.txt",
            "/data/adb/modules/luminatweaks/gamelist.txt"
        )
        for (path in txtPaths) {
            val raw = runCatching { File(path).takeIf { it.canRead() }?.readText() }.getOrNull()
                ?.takeIf { it.isNotBlank() } ?: continue
            val parsed = raw.lineSequence()
                .map { it.substringBefore('#').trim().substringBefore(' ').substringBefore(':').trim() }
                .filter { it.isNotEmpty() }
                .toSet()
            if (parsed.isNotEmpty()) return parsed
        }
        return emptySet()
    }

    private data class Feed(
        val fps: Float,
        val frametimeMs: Float,
        val epochMs: Long,
        val gameActive: Boolean
    )

    private fun readFeed(): Feed? {
        val candidates = ArrayList<Feed>(3)
        parseFeed(readSystemProperty("sys.lumina.fps"))?.let { candidates.add(it) }
        val appFile = File(filesDir, "lumina.fps").absolutePath
        for (path in listOf(appFile, PUBLIC_FPS_PATH, "/data/adb/.config/lumina/fps.txt")) {
            try {
                val file = File(path)
                if (!file.exists() || !file.canRead()) continue
                parseFeed(file.readText())?.let { candidates.add(it) }
            } catch (_: Exception) {}
        }
        return candidates.maxByOrNull { it.epochMs }
    }

    private fun readCpuClusterLabel(): String {
        return try {
            val base = File("/sys/devices/system/cpu/cpufreq")
            val policies = base.listFiles { file ->
                file.isDirectory && file.name.startsWith("policy")
            }.orEmpty()
            val peakKhz = policies.mapNotNull { policyDir ->
                val raw = runCatching {
                    File(policyDir, "scaling_cur_freq").takeIf { it.canRead() }?.readText()?.trim()
                        ?: File(policyDir, "cpuinfo_cur_freq").takeIf { it.canRead() }?.readText()?.trim()
                }.getOrNull()
                raw?.toLongOrNull()?.takeIf { it > 0L }
            }.maxOrNull()
            if (peakKhz == null) "-- GHz" else String.format(Locale.US, "%.1f GHz", peakKhz / 1_000_000f)
        } catch (_: Exception) {
            "-- GHz"
        }
    }

    private fun readBatteryTempAndPowerLabel(): String {
        return DeviceUtil.getBatteryTemperature(this)
    }

    private fun readRamUsageLabel(): String {
        return try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return "RAM --%"
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            if (info.totalMem <= 0L) return "RAM --%"
            val usedPct = (((info.totalMem - info.availMem).toDouble() / info.totalMem.toDouble()) * 100.0)
                .roundToInt()
                .coerceIn(1, 99)
            "$usedPct%"
        } catch (_: Exception) {
            "RAM --%"
        }
    }

    private fun parseFeed(raw: String?): Feed? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.trim().split(Regex("\\s+"))
        if (parts.size < 2) return null
        val fps = parts[0].toFloatOrNull() ?: return null
        val epochMs = parts[1].toLongOrNull()?.takeIf { it > 0L } ?: return null
        val frametime = parts.getOrNull(2)?.toFloatOrNull()
            ?: if (fps > 0f) 1000f / fps else 0f
        val gameActive = parts.getOrNull(3)?.let { it == "1" || it.equals("true", true) }
            ?: (fps > 0f)
        return Feed(fps, frametime, epochMs, gameActive)
    }

    @Suppress("DEPRECATION")
    private fun readDisplayHz(): Int {
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { display }.getOrNull() ?: windowManager?.defaultDisplay
        } else {
            windowManager?.defaultDisplay
        }
        return display?.refreshRate?.roundToInt() ?: 60
    }

    @SuppressLint("PrivateApi")
    private fun readSystemProperty(key: String): String? {
        return try {
            val spClass = Class.forName("android.os.SystemProperties")
            val getMethod = spClass.getMethod("get", String::class.java, String::class.java)
            val value = getMethod.invoke(null, key, "") as? String
            value?.trim()?.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    private fun requestDaemonPreview(seconds: Int) {
        serviceScope.launch(Dispatchers.IO) {
            runCatching { DaemonManager.requestFpsPreview(seconds) }
        }
    }

    private fun applyPreviewLease(force: Boolean = false) {
        if (!force && System.currentTimeMillis() < burstUntilMs) return
        val need = activeConfig.enabled && !activeConfig.autoGame
        if (!force && need == previewLease) return
        previewLease = need
        if (need) lastLeaseRequestMs = System.currentTimeMillis()
        serviceScope.launch(Dispatchers.IO) {
            runCatching {
                if (need) {
                    DaemonManager.requestFpsPreview(ALWAYS_ON_PREVIEW_SECONDS)
                } else {
                    DaemonManager.stopFpsPreview()
                }
            }
        }
    }

    private fun attachOverlay() {
        if (isOverlayAttached) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            return
        }
        val wm = windowManager ?: return
        val cfg = activeConfig
        val params = createLayoutParams(cfg)
        val view = FpsOverlayView(this).apply {
            setPalette(ThemeController.overlayPalette(this@FloatingFpsService))
            applyState(cfg, liveTelemetry)
            var dragStartX = 0
            var dragStartY = 0
            onDragStart = {
                val lp = layoutParams
                if (lp != null) {
                    dragStartX = lp.x
                    dragStartY = lp.y
                }
            }
            onDrag = drag@{ totalDx, totalDy ->
                val lp = layoutParams ?: return@drag
                val metrics = resources.displayMetrics
                val maxX = (metrics.widthPixels - width.coerceAtLeast(80)).coerceAtLeast(0)
                val maxY = (metrics.heightPixels - height.coerceAtLeast(36)).coerceAtLeast(0)
                val targetX = (dragStartX + totalDx.roundToInt()).coerceIn(0, maxX)
                val targetY = (dragStartY + totalDy.roundToInt()).coerceIn(0, maxY)
                hasCustomDragPosition = true
                if (targetX != lp.x || targetY != lp.y) {
                    lp.x = targetX
                    lp.y = targetY
                    runCatching { wm.updateViewLayout(this, lp) }
                }
            }
        }

        runCatching {
            wm.addView(view, params)
            overlayView = view
            layoutParams = params
            lastAppliedCorner = cfg.corner
            isOverlayAttached = true
            view.post { applyLayoutParamsUpdate(resetCorner = !hasCustomDragPosition) }
        }
    }

    private fun detachOverlay() {
        if (!isOverlayAttached) return
        val wm = windowManager
        val view = overlayView
        if (wm != null && view != null) {
            runCatching { wm.removeViewImmediate(view) }
        }
        overlayView = null
        layoutParams = null
        isOverlayAttached = false
    }

    private fun createLayoutParams(config: FloatingFpsConfig): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL

        val (initX, initY) = computeCornerCoordinates(config.corner, 0, 0)
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initX
            y = initY
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun applyLayoutParamsUpdate(resetCorner: Boolean) {
        val wm = windowManager ?: return
        val view = overlayView ?: return
        val lp = layoutParams ?: return

        if (resetCorner || !hasCustomDragPosition) {
            val (cx, cy) = computeCornerCoordinates(
                activeConfig.corner,
                view.measuredWidth,
                view.measuredHeight
            )
            lp.x = cx
            lp.y = cy
            lastAppliedCorner = activeConfig.corner
        }
        runCatching { wm.updateViewLayout(view, lp) }
    }

    private fun computeCornerCoordinates(corner: FpsOverlayCorner, width: Int, height: Int): Pair<Int, Int> {
        val metrics = resources.displayMetrics
        val density = metrics.density
        val marginX = (14f * density).roundToInt()
        val topMargin = (38f * density).roundToInt()
        val bottomMargin = (72f * density).roundToInt()
        val scale = activeConfig.scale.coerceIn(0.8f, 1.3f)
        val viewWidth = width.takeIf { it > 0 } ?: (168f * density * scale).roundToInt()
        val viewHeight = height.takeIf { it > 0 } ?: (34f * density * scale).roundToInt()

        return when (corner) {
            FpsOverlayCorner.TOP_LEFT -> marginX to topMargin
            FpsOverlayCorner.TOP_RIGHT ->
                (metrics.widthPixels - viewWidth - marginX).coerceAtLeast(marginX) to topMargin
            FpsOverlayCorner.BOTTOM_LEFT ->
                marginX to (metrics.heightPixels - viewHeight - bottomMargin).coerceAtLeast(topMargin)
            FpsOverlayCorner.BOTTOM_RIGHT ->
                (metrics.widthPixels - viewWidth - marginX).coerceAtLeast(marginX) to
                    (metrics.heightPixels - viewHeight - bottomMargin).coerceAtLeast(topMargin)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Lumina FPS Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = localizedText(
                    "Layanan overlay FPS real-time saat bermain game",
                    "Real-time FPS overlay service while gaming"
                )
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(isShowing: Boolean) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, buildNotification(isShowing))
    }

    private fun buildNotification(isShowing: Boolean): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, FloatingFpsService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusLine = when {
            isShowing && liveTelemetry.fps <= 0 -> localizedText(
                "Menunggu frame game",
                "Waiting for game frames"
            )
            isShowing -> localizedText(
                "Floating HUD aktif",
                "Floating HUD active"
            )
            else -> localizedText(
                "Menunggu sesi game",
                "Waiting for game session"
            )
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Lumina FPS Monitor")
            .setContentText(statusLine)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, localizedText("Matikan", "Stop"), stopIntent)
            .build()
    }

    private fun localizedText(id: String, en: String): String {
        val lang = Locale.getDefault().language.lowercase(Locale.ROOT)
        return if (lang == "in" || lang == "id") id else en
    }
}

private class FpsOverlayView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity

    private var config = FloatingFpsConfig()
    private var telemetry = LiveFpsTelemetry()
    private var palette = OverlayPalette(
        background = 0xFF161C22.toInt(),
        onSurface = 0xFFFFFFFF.toInt(),
        onSurfaceVariant = 0xFFB8C0C8.toInt(),
        primary = 0xFF80D8A8.toInt(),
        tertiary = 0xFFFFD27F.toInt(),
        error = 0xFFFFB4AB.toInt()
    )

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val heroPillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fpsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val extraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val rect = RectF()
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private var dragging = false
    private var lastLayoutSignature = ""

    var onDragStart: (() -> Unit)? = null
    var onDrag: ((Float, Float) -> Unit)? = null

    fun setPalette(next: OverlayPalette) {
        palette = next
        invalidate()
    }

    fun applyState(config: FloatingFpsConfig, telemetry: LiveFpsTelemetry) {
        val relayout = this.config != config
        this.config = config
        this.telemetry = telemetry
        val scale = config.scale.coerceIn(0.8f, 1.3f)
        fpsPaint.textSize = 14f * scale * scaledDensity
        labelPaint.textSize = 9f * scale * scaledDensity
        extraPaint.textSize = 11f * scale * scaledDensity
        val items = metricItems()
        val sig = "$scale|${config.showFps}|${fpsLabel()}|${items.joinToString("|")}"
        contentDescription = buildList {
            if (config.showFps) add("FPS ${fpsLabel()}")
            addAll(items)
        }.joinToString(", ")
        if (relayout || sig != lastLayoutSignature) {
            lastLayoutSignature = sig
            requestLayout()
        }
        invalidate()
    }

    private fun scale(): Float = config.scale.coerceIn(0.8f, 1.3f)

    private fun metricItems(): List<String> {
        val items = ArrayList<String>(5)
        if (config.showFrametime) {
            val msText = if (telemetry.fps > 0) String.format(Locale.US, "%.1f ms", telemetry.frametimeMs) else "-- ms"
            items.add(msText)
        }
        if (config.showProfile) {
            items.add(telemetry.profileLabel)
        }
        if (config.showCpuCluster) {
            items.add(telemetry.cpuClusterLabel)
        }
        if (config.showTemp) {
            items.add(telemetry.tempLabel)
        }
        if (config.showRam) {
            items.add(telemetry.ramLabel)
        }
        return items
    }

    private fun textHeight(paint: Paint): Float {
        val metrics = paint.fontMetrics
        return metrics.descent - metrics.ascent
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val s = scale()
        val dp = density * s
        val padX = 10f * dp
        val padY = 6f * dp
        val itemGap = 8f * dp
        val divW = 1f * dp
        val items = metricItems()

        var width = padX * 2f
        var contentH = 0f
        var blockCount = 0

        if (config.showFps) {
            val dot = 7f * dp
            val pillPadH = 8f * dp
            val pillPadV = 3f * dp
            val numW = max(fpsPaint.measureText("120"), fpsPaint.measureText(fpsLabel()))
            val unitW = labelPaint.measureText("FPS")
            val heroW = pillPadH * 2f + dot + 5f * dp + numW + 3f * dp + unitW
            val heroH = max(textHeight(fpsPaint), dot) + pillPadV * 2f
            width += heroW
            contentH = max(contentH, heroH)
            blockCount++
        }

        items.forEach { itemText ->
            if (blockCount > 0) width += itemGap + divW + itemGap
            width += extraPaint.measureText(itemText)
            contentH = max(contentH, textHeight(extraPaint))
            blockCount++
        }

        if (blockCount == 0) {
            width += 48f * dp
            contentH = textHeight(fpsPaint)
        }

        val height = contentH + padY * 2f
        setMeasuredDimension(
            resolveSize(width.roundToInt().coerceAtLeast(1), widthMeasureSpec),
            resolveSize(height.roundToInt().coerceAtLeast(1), heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val s = scale()
        val dp = density * s
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val status = statusColor()
        val alpha = config.bgAlpha.coerceIn(0.20f, 1f)

        backgroundPaint.color = palette.background
        backgroundPaint.alpha = (alpha * 255f).roundToInt().coerceIn(0, 255)
        val radius = h / 2f
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, radius, radius, backgroundPaint)

        val padX = 10f * dp
        val itemGap = 8f * dp
        val divW = 1f * dp
        val cy = h / 2f
        var cursorX = padX
        var hasDrawnBlock = false

        if (config.showFps) {
            val dot = 7f * dp
            val pillPadH = 8f * dp
            val pillPadV = 3f * dp
            val numW = max(fpsPaint.measureText("120"), fpsPaint.measureText(fpsLabel()))
            val unitW = labelPaint.measureText("FPS")
            val heroW = pillPadH * 2f + dot + 5f * dp + numW + 3f * dp + unitW
            val heroH = max(textHeight(fpsPaint), dot) + pillPadV * 2f

            heroPillPaint.color = status
            heroPillPaint.alpha = 40
            rect.set(cursorX, cy - heroH / 2f, cursorX + heroW, cy + heroH / 2f)
            canvas.drawRoundRect(rect, heroH / 2f, heroH / 2f, heroPillPaint)

            var x = cursorX + pillPadH
            dotPaint.color = status
            canvas.drawCircle(x + dot / 2f, cy, dot / 2f, dotPaint)
            x += dot + 5f * dp

            fpsPaint.color = status
            drawCentered(canvas, fpsLabel(), fpsPaint, x, cy)
            x += numW + 3f * dp

            labelPaint.color = status
            labelPaint.alpha = 215
            drawCentered(canvas, "FPS", labelPaint, x, cy)

            cursorX += heroW
            hasDrawnBlock = true
        }

        val items = metricItems()
        if (items.isNotEmpty()) {
            extraPaint.color = palette.onSurface
            dividerPaint.color = palette.onSurface
            dividerPaint.alpha = 56
            val divH = 10f * dp
            items.forEach { itemText ->
                if (hasDrawnBlock) {
                    cursorX += itemGap
                    canvas.drawRect(cursorX, cy - divH / 2f, cursorX + divW, cy + divH / 2f, dividerPaint)
                    cursorX += divW + itemGap
                }
                drawCentered(canvas, itemText, extraPaint, cursorX, cy)
                cursorX += extraPaint.measureText(itemText)
                hasDrawnBlock = true
            }
        }
    }

    private fun fpsLabel(): String = if (telemetry.fps > 0) telemetry.fps.toString() else "--"

    private fun drawCentered(canvas: Canvas, text: String, paint: Paint, x: Float, centerY: Float) {
        val metrics = paint.fontMetrics
        val baseline = centerY - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(text, x, baseline, paint)
    }

    private fun statusColor(): Int {
        if (telemetry.fps <= 0) return palette.onSurfaceVariant
        val ratio = telemetry.fps.toFloat() / telemetry.targetHz.coerceAtLeast(1)
        return when {
            ratio >= 0.90f -> palette.primary
            ratio >= 0.65f -> palette.tertiary
            else -> palette.error
        }
    }

    private var downRawX = 0f
    private var downRawY = 0f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                dragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val totalDx = event.rawX - downRawX
                val totalDy = event.rawY - downRawY
                if (!dragging && hypot(totalDx, totalDy) > touchSlop) {
                    dragging = true
                    onDragStart?.invoke()
                }
                if (dragging) {
                    onDrag?.invoke(totalDx, totalDy)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!dragging) performClick()
                dragging = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
