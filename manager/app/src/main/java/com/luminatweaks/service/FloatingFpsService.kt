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
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.ServiceInfo
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.luminatweaks.MainActivity
import com.luminatweaks.R
import com.luminatweaks.data.DaemonManager
import com.luminatweaks.ui.screen.FloatingFpsConfig
import com.luminatweaks.ui.screen.FloatingFpsPrefs
import com.luminatweaks.ui.screen.FpsOverlayCorner
import com.luminatweaks.ui.screen.FpsOverlayStyle
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

data class LiveFpsTelemetry(
    val fps: Int = 0,
    val targetHz: Int = 120,
    val frametimeMs: Float = 8.3f,
    val cpuClusterLabel: String = "-- GHz",
    val tempLabel: String = "37.0°C",
    val ramLabel: String = "RAM --%",
    val profileLabel: String = "Balanced",
    val history: FloatArray = FloatArray(18) { 8.3f },
    val gameActive: Boolean = false
)

/**
 * Floating FPS overlay.
 *
 * Rendering is a single plain [View] (Paints + Canvas) and telemetry comes from
 * the tiny file `luminad` writes, so the polling loop never starts `su`, never
 * invokes `dumpsys`, and never constructs a Compose tree.
 */
class FloatingFpsService : Service() {

    companion object {
        private const val CHANNEL_ID = "lumina_floating_fps_channel"
        private const val NOTIFICATION_ID = 4041
        private const val PUBLIC_FPS_PATH = "/data/local/tmp/lumina.fps"
        private const val FEED_MAX_AGE_MS = 1_500L
        private const val GAME_STALE_GRACE_MS = 12_000L
        private const val ALWAYS_ON_PREVIEW_SECONDS = 3600
        private const val PREVIEW_RENEW_MS = 30 * 60 * 1000L

        const val ACTION_SYNC = "com.luminatweaks.action.FPS_OVERLAY_SYNC"
        const val ACTION_PREVIEW_BURST = "com.luminatweaks.action.FPS_OVERLAY_PREVIEW_BURST"
        const val ACTION_UNLOCK_TOUCH = "com.luminatweaks.action.FPS_OVERLAY_UNLOCK_TOUCH"
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

            ACTION_UNLOCK_TOUCH -> {
                val updated = FloatingFpsPrefs.load(this).copy(touchLock = false)
                FloatingFpsPrefs.save(this, updated)
                activeConfig = updated
                overlayView?.applyState(updated, liveTelemetry)
                applyLayoutParamsUpdate(resetCorner = false)
                updateNotification(isShowing = isOverlayAttached)
                Toast.makeText(
                    this,
                    localizedText("Sentuhan overlay dibuka", "Overlay touch unlocked"),
                    Toast.LENGTH_SHORT
                ).show()
                return START_STICKY
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
            lp.y = lp.y.coerceIn(0, (metrics.heightPixels - 60).coerceAtLeast(0))
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
            val history = FloatArray(18) { 8.3f }
            var lastGameSeenMs = 0L

            while (isActive) {
                val cfg = activeConfig
                val now = System.currentTimeMillis()
                val inBurst = now < burstUntilMs

                if (!cfg.enabled && !inBurst) {
                    stopSelf()
                    break
                }

                val feed = withContext(Dispatchers.IO) { readFeed() }
                val fresh = feed != null && now - feed.epochMs <= FEED_MAX_AGE_MS
                if (fresh && feed != null && feed.gameActive) {
                    lastGameSeenMs = now
                }
                val show = inBurst || (cfg.enabled && (
                    !cfg.autoGame ||
                        (fresh && feed?.gameActive == true) ||
                        (lastGameSeenMs > 0L && now - lastGameSeenMs <= GAME_STALE_GRACE_MS)
                    ))

                if (!show) {
                    if (isOverlayAttached) {
                        detachOverlay()
                        updateNotification(isShowing = false)
                    }
                    delay(cfg.intervalMs.toLong().coerceIn(250L, 2_000L))
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
                    // No real game frame is available. Never fall back to the
                    // display refresh rate; the view renders `--` instead.
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

                if (fps > 0f) {
                    System.arraycopy(history, 1, history, 0, history.size - 1)
                    history[history.size - 1] = frametime.coerceIn(0f, 250f)
                }

                liveTelemetry = LiveFpsTelemetry(
                    fps = if (fps > 0f) fps.roundToInt().coerceIn(1, max(cachedHz, 60)) else 0,
                    targetHz = cachedHz,
                    frametimeMs = if (fps > 0f) frametime.coerceIn(0f, 250f) else 0f,
                    cpuClusterLabel = cachedCpuCluster,
                    tempLabel = cachedTemp,
                    ramLabel = cachedRam,
                    profileLabel = cachedProfile,
                    history = history,
                    gameActive = feed?.gameActive == true || inBurst
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

    private data class Feed(
        val fps: Float,
        val frametimeMs: Float,
        val epochMs: Long,
        val gameActive: Boolean
    )

    private fun readFeed(): Feed? {
        // The property survives the untrusted-app SELinux wall that often
        // blocks /data/local/tmp; files remain as a second channel.
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

    /**
     * Manual mode keeps the overlay visible outside games. Ask the daemon for a
     * long preview once (and again when the service restarts) so sampling still
     * runs while the Manager or another app is in front.
     */
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
                val lp = (layoutParams as? WindowManager.LayoutParams)
                if (lp != null) {
                    dragStartX = lp.x
                    dragStartY = lp.y
                }
            }
            onDrag = drag@{ totalDx, totalDy ->
                val lp = (layoutParams as? WindowManager.LayoutParams) ?: return@drag
                val metrics = resources.displayMetrics
                val maxX = (metrics.widthPixels - width.coerceAtLeast(80)).coerceAtLeast(0)
                val maxY = (metrics.heightPixels - height.coerceAtLeast(44)).coerceAtLeast(0)
                val targetX = (dragStartX + totalDx.roundToInt()).coerceIn(0, maxX)
                val targetY = (dragStartY + totalDy.roundToInt()).coerceIn(0, maxY)
                hasCustomDragPosition = true
                if (targetX != lp.x || targetY != lp.y) {
                    lp.x = targetX
                    lp.y = targetY
                    runCatching { wm.updateViewLayout(this, lp) }
                }
            }
            onDoubleTapToggleStyle = toggle@{
                val updated = activeConfig.copy(style = nextStyle(activeConfig.style))
                activeConfig = updated
                FloatingFpsPrefs.save(this@FloatingFpsService, updated)
                applyState(updated, liveTelemetry)
                post { applyLayoutParamsUpdate(resetCorner = !hasCustomDragPosition) }
            }
            onLongPressLockTouch = lock@{
                val updated = activeConfig.copy(touchLock = true)
                activeConfig = updated
                FloatingFpsPrefs.save(this@FloatingFpsService, updated)
                applyState(updated, liveTelemetry)
                applyLayoutParamsUpdate(resetCorner = false)
                updateNotification(isShowing = true)
                Toast.makeText(
                    this@FloatingFpsService,
                    localizedText(
                        "Sentuhan overlay dikunci",
                        "Overlay touch locked"
                    ),
                    Toast.LENGTH_SHORT
                ).show()
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

    private fun nextStyle(current: FpsOverlayStyle): FpsOverlayStyle = when (current) {
        FpsOverlayStyle.PILL -> FpsOverlayStyle.GRAPH
        FpsOverlayStyle.GRAPH -> FpsOverlayStyle.MINIMAL
        FpsOverlayStyle.MINIMAL -> FpsOverlayStyle.PILL
    }

    private fun createLayoutParams(config: FloatingFpsConfig): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        if (config.touchLock) {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }

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

        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        if (activeConfig.touchLock) {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        lp.flags = flags

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
        val viewWidth = width.takeIf { it > 0 }
            ?: ((if (activeConfig.style == FpsOverlayStyle.GRAPH) 158f else 138f) * density * scale).roundToInt()
        val viewHeight = height.takeIf { it > 0 } ?: (44f * density * scale).roundToInt()

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
                localizedText("Lumina FPS Monitor", "Lumina FPS Monitor"),
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
            isShowing && activeConfig.touchLock -> localizedText(
                "Sentuhan overlay dikunci",
                "Overlay touch locked"
            )
            isShowing && liveTelemetry.fps <= 0 -> localizedText(
                "Menunggu sesi game",
                "Waiting for game session"
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

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(localizedText("Lumina FPS Monitor", "Lumina FPS Monitor"))
            .setContentText(statusLine)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (activeConfig.touchLock) {
            val unlockIntent = PendingIntent.getService(
                this,
                2,
                Intent(this, FloatingFpsService::class.java).apply { action = ACTION_UNLOCK_TOUCH },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                0,
                localizedText("Buka Kunci", "Unlock Touch"),
                unlockIntent
            )
        }

        builder.addAction(
            0,
            localizedText("Matikan", "Stop"),
            stopIntent
        )

        return builder.build()
    }

    private fun localizedText(id: String, en: String): String {
        val lang = Locale.getDefault().language.lowercase(Locale.ROOT)
        return if (lang == "in" || lang == "id") id else en
    }
}

// ---------------------------------------------------------------------------
// Native overlay view: Canvas + Paint only (no Compose, no per-frame allocs)
// ---------------------------------------------------------------------------

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
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fpsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val extraPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sparkPath = Path()
    private val sparkFillPath = Path()
    private val rect = RectF()

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onDoubleTap(e: MotionEvent): Boolean {
            onDoubleTapToggleStyle?.invoke()
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            onLongPressLockTouch?.invoke()
        }
    })
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private var dragging = false

    var onDragStart: (() -> Unit)? = null
    var onDrag: ((Float, Float) -> Unit)? = null
    var onDoubleTapToggleStyle: (() -> Unit)? = null
    var onLongPressLockTouch: (() -> Unit)? = null

    fun setPalette(next: OverlayPalette) {
        palette = next
        invalidate()
    }

    private val heroPillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var lastLayoutSignature = ""

    fun applyState(config: FloatingFpsConfig, telemetry: LiveFpsTelemetry) {
        val relayout = this.config != config
        this.config = config
        this.telemetry = telemetry
        val scale = config.scale.coerceIn(0.8f, 1.3f)
        val isMinimal = config.style == FpsOverlayStyle.MINIMAL
        fpsPaint.textSize = (if (isMinimal) 17f else 20f) * scale * scaledDensity
        extraPaint.textSize = (if (isMinimal) 11.5f else 12.5f) * scale * scaledDensity
        extraPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        linePaint.strokeWidth = 2.2f * scale * density
        val lines = stackedLines()
        val sig = "${config.style}|$scale|${config.showFps}|${fpsLabel()}|${lines.joinToString("|")}"
        contentDescription = buildList {
            if (config.showFps) add("FPS ${fpsLabel()}")
            addAll(lines)
        }.joinToString(", ")
        if (relayout || sig != lastLayoutSignature) {
            lastLayoutSignature = sig
            requestLayout()
        }
        invalidate()
    }

    private fun scale(): Float = config.scale.coerceIn(0.8f, 1.3f)

    private fun stackedLines(): List<String> {
        val lines = ArrayList<String>(5)
        if (config.showFrametime) {
            val msText = if (telemetry.fps > 0) String.format(Locale.US, "%.1f ms", telemetry.frametimeMs) else "-- ms"
            lines.add(msText)
        }
        if (config.showProfile) {
            lines.add(telemetry.profileLabel)
        }
        if (config.showCpuCluster) {
            lines.add(telemetry.cpuClusterLabel)
        }
        if (config.showTemp) {
            lines.add(telemetry.tempLabel)
        }
        if (config.showRam) {
            lines.add(telemetry.ramLabel)
        }
        return lines
    }

    private fun textHeight(paint: Paint): Float {
        val metrics = paint.fontMetrics
        return metrics.descent - metrics.ascent
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val s = scale()
        val dp = density * s
        val isMinimal = config.style == FpsOverlayStyle.MINIMAL
        val padX = (if (isMinimal) 11f else 14f) * dp
        val padY = (if (isMinimal) 9f else 12f) * dp
        val rowGap = (if (isMinimal) 4f else 6f) * dp
        val lines = stackedLines()

        var maxInnerWidth = if (isMinimal) 56f * dp else 66f * dp
        if (config.style == FpsOverlayStyle.GRAPH) {
            maxInnerWidth = max(maxInnerWidth, 84f * dp)
        }
        if (config.showFps) {
            val dot = (if (isMinimal) 7f else 9f) * dp
            val pillPadH = if (isMinimal) 0f else 20f * dp
            val heroW = pillPadH + dot + 7f * dp + fpsPaint.measureText("120")
            maxInnerWidth = max(maxInnerWidth, heroW)
        }
        for (line in lines) {
            maxInnerWidth = max(maxInnerWidth, extraPaint.measureText(line))
        }

        val width = maxInnerWidth + padX * 2f
        var height = padY * 2f
        var blockCount = 0

        if (config.showFps) {
            val pillPadV = if (isMinimal) 0f else 8f * dp
            height += max(textHeight(fpsPaint), 9f * dp) + pillPadV
            blockCount++
        }

        if (config.style == FpsOverlayStyle.GRAPH) {
            if (blockCount > 0) height += rowGap
            height += 22f * dp
            blockCount++
        }

        if (lines.isNotEmpty()) {
            val lineH = textHeight(extraPaint)
            if (blockCount > 0) height += rowGap
            height += lines.size * lineH + (lines.size - 1) * rowGap
        } else if (blockCount == 0) {
            height += textHeight(fpsPaint)
        }

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
        val isMinimal = config.style == FpsOverlayStyle.MINIMAL
        val alpha = (config.bgAlpha * (if (isMinimal) 0.80f else 1f)).coerceIn(0.20f, 1f)

        backgroundPaint.color = palette.background
        backgroundPaint.alpha = (alpha * 255f).roundToInt().coerceIn(0, 255)
        val radius = (if (isMinimal) 16f else 22f) * dp
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, radius, radius, backgroundPaint)

        val padX = (if (isMinimal) 11f else 14f) * dp
        val padY = (if (isMinimal) 9f else 12f) * dp
        val rowGap = (if (isMinimal) 4f else 6f) * dp
        var cursorY = padY
        var hasDrawnBlock = false

        if (config.showFps) {
            val dot = (if (isMinimal) 7f else 9f) * dp
            val pillPadV = if (isMinimal) 0f else 4f * dp
            val pillPadH = if (isMinimal) 0f else 10f * dp
            val heroH = max(textHeight(fpsPaint), dot) + pillPadV * 2f

            if (!isMinimal) {
                heroPillPaint.color = status
                heroPillPaint.alpha = 38
                rect.set(padX, cursorY, w - padX, cursorY + heroH)
                canvas.drawRoundRect(rect, 13f * dp, 13f * dp, heroPillPaint)
            }

            val cy = cursorY + heroH / 2f
            val contentStart = padX + pillPadH
            dotPaint.color = status
            canvas.drawCircle(contentStart + dot / 2f, cy, dot / 2f, dotPaint)

            fpsPaint.color = status
            drawCentered(canvas, fpsLabel(), fpsPaint, contentStart + dot + 7f * dp, cy)
            cursorY += heroH
            hasDrawnBlock = true
        }

        if (config.style == FpsOverlayStyle.GRAPH) {
            if (hasDrawnBlock) cursorY += rowGap
            val graphH = 22f * dp
            if (telemetry.fps > 0) {
                drawSparkline(canvas, padX, cursorY, w - padX, cursorY + graphH, status, dp)
            }
            cursorY += graphH
            hasDrawnBlock = true
        }

        val lines = stackedLines()
        if (lines.isNotEmpty()) {
            val lineH = textHeight(extraPaint)
            extraPaint.color = palette.onSurface
            lines.forEachIndexed { index, itemText ->
                if (hasDrawnBlock || index > 0) cursorY += rowGap
                val cy = cursorY + lineH / 2f
                drawCentered(canvas, itemText, extraPaint, padX, cy)
                cursorY += lineH
            }
        }
    }

    private fun fpsLabel(): String = if (telemetry.fps > 0) telemetry.fps.toString() else "--"

    private fun drawCentered(canvas: Canvas, text: String, paint: Paint, x: Float, centerY: Float) {
        val metrics = paint.fontMetrics
        val baseline = centerY - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(text, x, baseline, paint)
    }

    private fun drawSparkline(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        color: Int,
        dp: Float
    ) {
        val samples = telemetry.history
        if (samples.size < 2 || right <= left || bottom <= top) return

        var minValue = Float.MAX_VALUE
        var maxValue = -Float.MAX_VALUE
        for (value in samples) {
            minValue = minOf(minValue, value)
            maxValue = maxOf(maxValue, value)
        }
        if (!minValue.isFinite() || !maxValue.isFinite()) return
        minValue -= 1.5f
        maxValue += 1.5f
        val span = (maxValue - minValue).coerceAtLeast(1f)
        val stepX = (right - left) / (samples.size - 1)

        sparkPath.reset()
        sparkFillPath.reset()
        val height = bottom - top
        samples.forEachIndexed { index, value ->
            val x = left + index * stepX
            val normalized = ((value - minValue) / span).coerceIn(0f, 1f)
            val y = top + height * (1f - normalized * 0.82f - 0.09f)
            if (index == 0) {
                sparkPath.moveTo(x, y)
                sparkFillPath.moveTo(x, bottom)
                sparkFillPath.lineTo(x, y)
            } else {
                sparkPath.lineTo(x, y)
                sparkFillPath.lineTo(x, y)
            }
        }
        sparkFillPath.lineTo(right, bottom)
        sparkFillPath.close()

        fillPaint.shader = LinearGradient(
            0f, top, 0f, bottom,
            (color and 0x00FFFFFF) or 0x47000000,
            0x00000000,
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(sparkFillPath, fillPaint)

        linePaint.color = color
        canvas.drawPath(sparkPath, linePaint)
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
        if (config.touchLock) return false
        val screenEvent = MotionEvent.obtain(event).apply {
            setLocation(event.rawX, event.rawY)
        }
        gestureDetector.onTouchEvent(screenEvent)
        screenEvent.recycle()
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
