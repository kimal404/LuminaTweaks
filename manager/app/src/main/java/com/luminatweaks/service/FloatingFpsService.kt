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
    val tempLabel: String = "37.0°C",
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
    private var cachedTemp = "37.0°C"
    private var cachedProfile = "Balanced"
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

                if (now - cacheStampMs >= 2_500L) {
                    cacheStampMs = now
                    cachedHz = readDisplayHz().coerceAtLeast(30)
                    if (cfg.showTemp) cachedTemp = DeviceUtil.getBatteryTemperature(this@FloatingFpsService)
                    if (cfg.showProfile) {
                        cachedProfile = readSystemProperty("sys.lumina.profile") ?: "Balanced"
                    }
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
                    tempLabel = cachedTemp,
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
            onDrag = drag@{ dx, dy ->
                val lp = (layoutParams as? WindowManager.LayoutParams)
                if (lp == null) return@drag
                val metrics = resources.displayMetrics
                hasCustomDragPosition = true
                lp.x = (lp.x + dx.roundToInt()).coerceIn(0, (metrics.widthPixels - 80).coerceAtLeast(0))
                lp.y = (lp.y + dy.roundToInt()).coerceIn(0, (metrics.heightPixels - 60).coerceAtLeast(0))
                runCatching { wm.updateViewLayout(this, lp) }
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
                        "Sentuhan overlay dikunci (buka lewat notifikasi)",
                        "Overlay touch locked (unlock from notification)"
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
                "Overlay aktif (sentuhan dikunci)",
                "Overlay active (touch locked)"
            )
            isShowing && liveTelemetry.fps <= 0 -> localizedText(
                "Menunggu frame game...",
                "Waiting for game frames..."
            )
            isShowing -> localizedText(
                "Menampilkan FPS real-time di layar",
                "Showing real-time FPS on screen"
            )
            else -> localizedText(
                "Mode Auto-Game aktif · Menunggu game dibuka",
                "Auto-Game active · Waiting for game launch"
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

    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false

    var onDrag: ((Float, Float) -> Unit)? = null
    var onDoubleTapToggleStyle: (() -> Unit)? = null
    var onLongPressLockTouch: (() -> Unit)? = null

    fun setPalette(next: OverlayPalette) {
        palette = next
        invalidate()
    }

    fun applyState(config: FloatingFpsConfig, telemetry: LiveFpsTelemetry) {
        val relayout = this.config != config
        this.config = config
        this.telemetry = telemetry
        val scale = config.scale.coerceIn(0.8f, 1.3f)
        fpsPaint.textSize = 15f * scale * scaledDensity
        labelPaint.textSize = 10f * scale * scaledDensity
        extraPaint.textSize = 10f * scale * scaledDensity
        linePaint.strokeWidth = 2f * scale * density
        contentDescription = if (telemetry.fps > 0) {
            "FPS ${telemetry.fps}, ${String.format(Locale.US, "%.1f", telemetry.frametimeMs)} ms"
        } else {
            "FPS --, waiting for game frames"
        }
        if (relayout) requestLayout()
        invalidate()
    }

    private fun scale(): Float = config.scale.coerceIn(0.8f, 1.3f)

    private fun extraParts(): List<String> {
        val parts = ArrayList<String>(3)
        if (config.showFrametime && telemetry.fps > 0) {
            parts.add(String.format(Locale.US, "%.1f ms", telemetry.frametimeMs))
        }
        if (config.showTemp) parts.add(telemetry.tempLabel)
        if (config.showProfile) parts.add(telemetry.profileLabel)
        return parts
    }

    private fun textHeight(paint: Paint): Float {
        val metrics = paint.fontMetrics
        return metrics.descent - metrics.ascent
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val s = scale()
        val dp = density * s
        val dot = 8f * dp
        val width: Float
        val height: Float

        when (config.style) {
            FpsOverlayStyle.PILL -> {
                val extra = extraParts().joinToString(" · ")
                var content = dot + 7f * dp + fpsPaint.measureText("120") + 4f * dp +
                    labelPaint.measureText("FPS")
                if (extra.isNotEmpty()) content += 8f * dp + extraPaint.measureText(extra)
                width = content + 24f * dp
                height = max(textHeight(fpsPaint), dot) + 12f * dp
            }
            FpsOverlayStyle.GRAPH -> {
                width = 158f * dp
                height = 8f * dp + max(textHeight(fpsPaint), extraPaint.textSize) + 5f * dp +
                    22f * dp + 8f * dp
            }
            FpsOverlayStyle.MINIMAL -> {
                val extra = extraParts().firstOrNull().orEmpty()
                var content = fpsPaint.measureText("120")
                if (extra.isNotEmpty()) content += 6f * dp + extraPaint.measureText(extra)
                width = content + 20f * dp
                height = max(textHeight(fpsPaint), extraPaint.textSize) + 8f * dp
            }
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
        val alpha = (config.bgAlpha * (if (config.style == FpsOverlayStyle.MINIMAL) 0.68f else 1f))
            .coerceIn(0.15f, 1f)

        backgroundPaint.color = palette.background
        backgroundPaint.alpha = (alpha * 255f).roundToInt().coerceIn(0, 255)
        val radius = if (config.style == FpsOverlayStyle.GRAPH) 16f * dp else h / 2f
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, radius, radius, backgroundPaint)

        when (config.style) {
            FpsOverlayStyle.PILL -> drawPill(canvas, s, dp, h, status)
            FpsOverlayStyle.GRAPH -> drawGraph(canvas, s, dp, w, h, status)
            FpsOverlayStyle.MINIMAL -> drawMinimal(canvas, s, dp, h, status)
        }
    }

    private fun fpsLabel(): String = if (telemetry.fps > 0) telemetry.fps.toString() else "--"

    private fun drawPill(canvas: Canvas, s: Float, dp: Float, h: Float, status: Int) {
        val dot = 8f * dp
        val cy = h / 2f
        var x = 12f * dp
        dotPaint.color = status
        canvas.drawCircle(x + dot / 2f, cy, dot / 2f, dotPaint)
        x += dot + 7f * dp

        fpsPaint.color = status
        drawCentered(canvas, fpsLabel(), fpsPaint, x, cy)
        x += fpsPaint.measureText("120") + 4f * dp

        labelPaint.color = palette.onSurface
        drawCentered(canvas, "FPS", labelPaint, x, cy)
        x += labelPaint.measureText("FPS")

        val extra = extraParts().joinToString(" · ")
        if (extra.isNotEmpty()) {
            extraPaint.color = palette.onSurfaceVariant
            drawCentered(canvas, extra, extraPaint, x + 8f * dp, cy)
        }
    }

    private fun drawGraph(canvas: Canvas, s: Float, dp: Float, w: Float, h: Float, status: Int) {
        val pad = 11f * dp
        val top = 8f * dp
        var x = pad
        val headerCenter = top + max(textHeight(fpsPaint), extraPaint.textSize) / 2f
        val dot = 7f * dp

        dotPaint.color = status
        canvas.drawCircle(x + dot / 2f, headerCenter, dot / 2f, dotPaint)
        x += dot + 6f * dp

        fpsPaint.color = status
        val fpsText = if (telemetry.fps > 0) "${telemetry.fps} FPS" else "-- FPS"
        drawCentered(canvas, fpsText, fpsPaint, x, headerCenter)

        val firstExtra = extraParts().firstOrNull()
        if (firstExtra != null) {
            extraPaint.color = palette.onSurfaceVariant
            val textWidth = extraPaint.measureText(firstExtra)
            drawCentered(canvas, firstExtra, extraPaint, w - pad - textWidth, headerCenter)
        }

        val graphTop = top + max(textHeight(fpsPaint), extraPaint.textSize) + 5f * dp
        val graphBottom = h - 8f * dp
        val graphLeft = pad
        val graphRight = w - pad
        if (telemetry.fps > 0) {
            drawSparkline(canvas, graphLeft, graphTop, graphRight, graphBottom, status, dp)
        }
    }

    private fun drawMinimal(canvas: Canvas, s: Float, dp: Float, h: Float, status: Int) {
        val cy = h / 2f
        var x = 10f * dp
        fpsPaint.color = status
        drawCentered(canvas, fpsLabel(), fpsPaint, x, cy)
        x += fpsPaint.measureText("120")

        val firstExtra = extraParts().firstOrNull()
        if (firstExtra != null) {
            extraPaint.color = palette.onSurfaceVariant
            drawCentered(canvas, firstExtra, extraPaint, x + 6f * dp, cy)
        }
    }

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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (config.touchLock) return false
        gestureDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                lastX = event.x
                lastY = event.y
                dragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastX
                val dy = event.y - lastY
                if (!dragging && hypot(event.x - downX, event.y - downY) > touchSlop) {
                    dragging = true
                }
                if (dragging) {
                    onDrag?.invoke(dx, dy)
                    lastX = event.x
                    lastY = event.y
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
