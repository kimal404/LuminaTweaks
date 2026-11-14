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
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Choreographer
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.luminatweaks.MainActivity
import com.luminatweaks.R
import com.luminatweaks.ui.screen.FloatingFpsConfig
import com.luminatweaks.ui.screen.FloatingFpsPrefs
import com.luminatweaks.ui.screen.FpsOverlayCorner
import com.luminatweaks.ui.screen.FpsOverlayStyle
import com.luminatweaks.ui.theme.LuminaTheme
import com.luminatweaks.util.DeviceUtil
import com.luminatweaks.util.RootUtil
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class LiveFpsTelemetry(
    val fps: Int = 0,
    val targetHz: Int = 120,
    val frametimeMs: Float = 8.3f,
    val tempLabel: String = "37.0°C",
    val profileLabel: String = "Balanced",
    val history: List<Float> = List(18) { 8.3f }
)

class FloatingFpsService : Service() {

    companion object {
        private const val CHANNEL_ID = "lumina_floating_fps_channel"
        private const val NOTIFICATION_ID = 4041

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
    private var overlayView: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    private var activeConfig by mutableStateOf(FloatingFpsConfig())
    private var liveTelemetry by mutableStateOf(LiveFpsTelemetry())

    private var telemetryJob: Job? = null
    private var previewBurstJob: Job? = null
    private var burstUntilMs: Long = 0L
    private var isOverlayAttached = false
    private var lastAppliedCorner: FpsOverlayCorner? = null
    private var hasCustomDragPosition = false

    private val rootShell = PersistentRootShell()
    private val sfSampler = SurfaceFlingerSampler()
    private val fallbackChoreographer = ChoreographerFpsTracker()

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
                startTelemetryLoop()
                previewBurstJob?.cancel()
                previewBurstJob = serviceScope.launch {
                    delay(5_600L)
                    burstUntilMs = 0L
                    if (!activeConfig.enabled) {
                        stopSelf()
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
            applyLayoutParamsUpdate(resetCorner = cornerChanged)
        }
        startTelemetryLoop()
        return START_STICKY
    }

    override fun onDestroy() {
        previewBurstJob?.cancel()
        telemetryJob?.cancel()
        fallbackChoreographer.stop()
        detachOverlay()
        rootShell.close()
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
            var gamePackages: Set<String> = emptySet()
            var lastGameListLoadMs = 0L
            var hasRoot = false
            var lastRootProbeMs = 0L
            var cachedLayerPackage = ""
            var cachedLayerName: String? = null
            var history = List(18) { 8.3f }

            while (isActive) {
                val cfg = activeConfig
                val now = System.currentTimeMillis()
                val inBurst = now < burstUntilMs

                if (!cfg.enabled && !inBurst) {
                    stopSelf()
                    break
                }

                if (now - lastRootProbeMs > 10_000L) {
                    lastRootProbeMs = now
                    hasRoot = withContext(Dispatchers.IO) { RootUtil.checkRootAccess() }
                    if (hasRoot && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this@FloatingFpsService)) {
                        withContext(Dispatchers.IO) {
                            RootUtil.runAsRoot("appops set $packageName SYSTEM_ALERT_WINDOW allow 2>/dev/null")
                        }
                    }
                }

                if (cfg.autoGame && (now - lastGameListLoadMs > 8_000L || gamePackages.isEmpty())) {
                    lastGameListLoadMs = now
                    gamePackages = withContext(Dispatchers.IO) { loadGameListPackages(hasRoot) }
                }

                val targetHz = readDisplayHz().coerceAtLeast(60)
                val tempStr = DeviceUtil.getBatteryTemperature(this@FloatingFpsService)
                val profileStr = readSystemProperty("sys.lumina.profile") ?: "Balanced"

                val sample = withContext(Dispatchers.IO) {
                    val fgPkg = if (hasRoot) detectForegroundPackage() else null
                    val shouldDisplay = when {
                        inBurst -> true
                        !cfg.enabled -> false
                        !cfg.autoGame -> true
                        fgPkg.isNullOrBlank() -> false
                        gamePackages.isEmpty() -> false
                        else -> fgPkg in gamePackages
                    }

                    if (!shouldDisplay) {
                        return@withContext PollOutcome(show = false, fps = 0, frametimeMs = 1000f / targetHz)
                    }

                    // 1) Fast direct file feed from luminad (/data/local/tmp/lumina.fps)
                    val daemonFps = readDaemonFpsDirect()
                    if (daemonFps != null && daemonFps > 0f) {
                        val ms = (1000f / daemonFps).coerceIn(1f, 250f)
                        return@withContext PollOutcome(
                            show = true,
                            fps = daemonFps.roundToInt().coerceAtMost(targetHz),
                            frametimeMs = ms
                        )
                    }

                    // 2) Real-time SurfaceFlinger layer frame timestamps via persistent root shell
                    if (hasRoot) {
                        val topPkg = fgPkg ?: packageName
                        if (topPkg != cachedLayerPackage || cachedLayerName == null) {
                            cachedLayerPackage = topPkg
                            cachedLayerName = resolveSurfaceLayerForPackage(topPkg)
                        }
                        val sfResult = cachedLayerName?.let { layer ->
                            sfSampler.sampleLayer(rootShell, layer)
                        }
                        if (sfResult != null) {
                            return@withContext PollOutcome(
                                show = true,
                                fps = sfResult.fps.coerceIn(0, targetHz),
                                frametimeMs = sfResult.frametimeMs.coerceIn(1f, 250f)
                            )
                        } else {
                            // Layer may have recreated (e.g., Unity/UE4 surface switch); re-resolve next tick
                            cachedLayerName = null
                        }
                    }

                    // 3) Fallback Choreographer frame tracker
                    fallbackChoreographer.start()
                    val fallbackFps = fallbackChoreographer.consumeFps()
                    val ms = if (fallbackFps > 0) (1000f / fallbackFps) else (1000f / targetHz)
                    PollOutcome(
                        show = true,
                        fps = fallbackFps.coerceIn(0, targetHz),
                        frametimeMs = ms
                    )
                }

                if (sample.show) {
                    if (!isOverlayAttached) {
                        attachOverlay()
                        updateNotification(isShowing = true)
                    }
                    history = (history.drop(1) + sample.frametimeMs)
                    liveTelemetry = LiveFpsTelemetry(
                        fps = sample.fps,
                        targetHz = targetHz,
                        frametimeMs = sample.frametimeMs,
                        tempLabel = tempStr,
                        profileLabel = profileStr,
                        history = history
                    )
                } else if (isOverlayAttached) {
                    detachOverlay()
                    updateNotification(isShowing = false)
                }

                delay(cfg.intervalMs.toLong().coerceIn(200L, 2000L))
            }
        }
    }

    private data class PollOutcome(
        val show: Boolean,
        val fps: Int,
        val frametimeMs: Float
    )

    private fun readDaemonFpsDirect(): Float? {
        for (path in listOf("/data/local/tmp/lumina.fps", "/data/adb/.config/lumina/fps.txt")) {
            val value = runCatching {
                val file = File(path)
                if (!file.exists() || !file.canRead()) return@runCatching null
                val parts = file.readText().trim().split(Regex("\\s+"))
                val fps = parts.getOrNull(0)?.toFloatOrNull() ?: return@runCatching null
                val ts = parts.getOrNull(1)?.toLongOrNull() ?: 0L
                val age = System.currentTimeMillis() - ts
                if (fps <= 0f || (ts > 0L && age > 2500L)) null else fps
            }.getOrNull()
            if (value != null) return value
        }
        return null
    }

    private fun detectForegroundPackage(): String? {
        val out = rootShell.exec(
            "dumpsys activity activities 2>/dev/null | grep -m1 -E 'topResumedActivity=|mResumedActivity=' || " +
                "dumpsys window 2>/dev/null | grep -m1 'mCurrentFocus='"
        )
        val match = Regex("""([a-zA-Z0-9_]+(?:\.[a-zA-Z0-9_]+)+)/""").find(out)
        return match?.groupValues?.getOrNull(1)
    }

    private fun resolveSurfaceLayerForPackage(pkg: String): String? {
        val listOut = rootShell.exec("dumpsys SurfaceFlinger --list 2>/dev/null")
        if (listOut.isBlank()) return null
        val lines = listOut.lineSequence()
            .map { it.trim() }
            .filter { it.contains(pkg) }
            .filterNot {
                it.startsWith("Bounds for") ||
                    it.startsWith("Background for") ||
                    it.startsWith("DimLayer") ||
                    it.startsWith("Screenshot")
            }
            .toList()
        if (lines.isEmpty()) return null
        return lines.lastOrNull { it.contains("SurfaceView") } ?: lines.last()
    }

    private fun loadGameListPackages(hasRoot: Boolean): Set<String> {
        val raw = runCatching {
            val file = File("/data/adb/.config/lumina/gamelist.json")
            if (file.exists() && file.canRead()) {
                file.readText()
            } else if (hasRoot) {
                rootShell.exec("cat /data/adb/.config/lumina/gamelist.json 2>/dev/null")
            } else {
                ""
            }
        }.getOrDefault("")

        if (raw.isBlank()) return emptySet()
        val result = LinkedHashSet<String>()
        val trimmed = raw.trim()
        runCatching {
            when {
                trimmed.startsWith("[") -> {
                    val arr = JSONArray(trimmed)
                    for (i in 0 until arr.length()) {
                        val item = arr.opt(i)
                        when (item) {
                            is String -> item.trim().takeIf { it.contains('.') }?.let(result::add)
                            is JSONObject -> {
                                val pkg = item.optString("packageName")
                                    .ifBlank { item.optString("package") }
                                    .ifBlank { item.optString("pkg") }
                                    .trim()
                                if (pkg.contains('.')) result.add(pkg)
                            }
                        }
                    }
                }

                trimmed.startsWith("{") -> {
                    val obj = JSONObject(trimmed)
                    val arr = obj.optJSONArray("games")
                        ?: obj.optJSONArray("packages")
                        ?: obj.optJSONArray("list")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val pkg = arr.optString(i).trim()
                            if (pkg.contains('.')) result.add(pkg)
                        }
                    } else {
                        val keys = obj.keys()
                        while (keys.hasNext()) {
                            val key = keys.next().trim()
                            if (key.contains('.')) result.add(key)
                        }
                    }
                }

                else -> {
                    trimmed.lineSequence()
                        .map { it.trim() }
                        .filter { it.isNotBlank() && !it.startsWith("#") && it.contains('.') }
                        .forEach(result::add)
                }
            }
        }
        return result
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

    private fun attachOverlay() {
        if (isOverlayAttached) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            return
        }
        val wm = windowManager ?: return

        val owner = OverlayLifecycleOwner().also {
            it.performRestore()
            it.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            it.handleLifecycleEvent(Lifecycle.Event.ON_START)
            it.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
        lifecycleOwner = owner

        val params = createLayoutParams(activeConfig)
        layoutParams = params
        lastAppliedCorner = activeConfig.corner

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                LuminaTheme {
                    LiveFpsOverlayContent(
                        config = activeConfig,
                        telemetry = liveTelemetry,
                        onDragDelta = { dx, dy ->
                            val lp = this@FloatingFpsService.layoutParams ?: return@LiveFpsOverlayContent
                            val metrics = resources.displayMetrics
                            hasCustomDragPosition = true
                            lp.x = (lp.x + dx.roundToInt()).coerceIn(0, (metrics.widthPixels - 80).coerceAtLeast(0))
                            lp.y = (lp.y + dy.roundToInt()).coerceIn(0, (metrics.heightPixels - 60).coerceAtLeast(0))
                            runCatching { wm.updateViewLayout(this, lp) }
                        },
                        onDoubleTapToggleStyle = {
                            val nextStyle = if (activeConfig.style == FpsOverlayStyle.PILL) {
                                FpsOverlayStyle.GRAPH
                            } else {
                                FpsOverlayStyle.PILL
                            }
                            val updated = activeConfig.copy(style = nextStyle)
                            activeConfig = updated
                            FloatingFpsPrefs.save(this@FloatingFpsService, updated)
                        },
                        onLongPressLockTouch = {
                            val updated = activeConfig.copy(touchLock = true)
                            activeConfig = updated
                            FloatingFpsPrefs.save(this@FloatingFpsService, updated)
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
                    )
                }
            }
        }

        runCatching {
            wm.addView(view, params)
            overlayView = view
            isOverlayAttached = true
        }
    }

    private fun detachOverlay() {
        if (!isOverlayAttached) return
        val wm = windowManager
        val view = overlayView
        if (wm != null && view != null) {
            runCatching { wm.removeViewImmediate(view) }
        }
        lifecycleOwner?.apply {
            handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
        lifecycleOwner = null
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

        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        if (config.touchLock) {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }

        val (initX, initY) = computeCornerCoordinates(config.corner)
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
            val (cx, cy) = computeCornerCoordinates(activeConfig.corner)
            lp.x = cx
            lp.y = cy
            lastAppliedCorner = activeConfig.corner
        }
        runCatching { wm.updateViewLayout(view, lp) }
    }

    private fun computeCornerCoordinates(corner: FpsOverlayCorner): Pair<Int, Int> {
        val metrics = resources.displayMetrics
        val density = metrics.density
        val marginX = (14f * density).roundToInt()
        val topMargin = (38f * density).roundToInt()
        val bottomMargin = (72f * density).roundToInt()
        val estWidth = (132f * density).roundToInt()
        val estHeight = (44f * density).roundToInt()

        return when (corner) {
            FpsOverlayCorner.TOP_LEFT -> marginX to topMargin
            FpsOverlayCorner.TOP_RIGHT -> (metrics.widthPixels - estWidth - marginX).coerceAtLeast(marginX) to topMargin
            FpsOverlayCorner.BOTTOM_LEFT -> marginX to (metrics.heightPixels - estHeight - bottomMargin).coerceAtLeast(topMargin)
            FpsOverlayCorner.BOTTOM_RIGHT ->
                (metrics.widthPixels - estWidth - marginX).coerceAtLeast(marginX) to
                    (metrics.heightPixels - estHeight - bottomMargin).coerceAtLeast(topMargin)
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
// Live Overlay Compose UI (MD3E)
// ---------------------------------------------------------------------------

@Composable
private fun LiveFpsOverlayContent(
    config: FloatingFpsConfig,
    telemetry: LiveFpsTelemetry,
    onDragDelta: (Float, Float) -> Unit,
    onDoubleTapToggleStyle: () -> Unit,
    onLongPressLockTouch: () -> Unit
) {
    val ratio = telemetry.fps.toFloat() / telemetry.targetHz.coerceAtLeast(1).toFloat()
    val targetStatusColor = when {
        telemetry.fps <= 0 -> MaterialTheme.colorScheme.onSurfaceVariant
        ratio >= 0.90f -> MaterialTheme.colorScheme.primary
        ratio >= 0.65f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    val statusColor by animateColorAsState(
        targetValue = targetStatusColor,
        label = "liveOverlayStatusColor"
    )
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = config.bgAlpha)
    val textColor = MaterialTheme.colorScheme.onSurface
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    val extraParts = buildList {
        if (config.showFrametime) add(String.format(Locale.US, "%.1f ms", telemetry.frametimeMs))
        if (config.showTemp) add(telemetry.tempLabel)
        if (config.showProfile) add(telemetry.profileLabel)
    }

    val gestureModifier = if (config.touchLock) {
        Modifier
    } else {
        Modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { onDoubleTapToggleStyle() },
                    onLongPress = { onLongPressLockTouch() }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDragDelta(dragAmount.x, dragAmount.y)
                }
            }
    }

    Box(
        modifier = gestureModifier.graphicsLayer {
            scaleX = config.scale
            scaleY = config.scale
        }
    ) {
        when (config.style) {
            FpsOverlayStyle.PILL -> {
                Surface(
                    shape = CircleShape,
                    color = containerColor
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            text = "${telemetry.fps}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = statusColor
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "FPS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        if (extraParts.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = extraParts.joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = subTextColor,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            FpsOverlayStyle.GRAPH -> {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = containerColor
                ) {
                    Column(
                        modifier = Modifier
                            .width(158.dp)
                            .padding(horizontal = 11.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(statusColor)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${telemetry.fps} FPS",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = statusColor
                                )
                            }
                            if (extraParts.isNotEmpty()) {
                                Text(
                                    text = extraParts.first(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = subTextColor,
                                    maxLines = 1
                                )
                            }
                        }

                        OverlaySparkline(
                            samples = telemetry.history,
                            lineColor = statusColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(22.dp)
                        )
                    }
                }
            }

            FpsOverlayStyle.MINIMAL -> {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.background.copy(
                        alpha = (config.bgAlpha * 0.65f).coerceIn(0.15f, 0.75f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${telemetry.fps}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = statusColor
                        )
                        if (extraParts.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = extraParts.first(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = textColor.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlaySparkline(
    samples: List<Float>,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (samples.size < 2 || size.width <= 0f || size.height <= 0f) return@Canvas
        val minVal = (samples.minOrNull() ?: 8f) - 1.5f
        val maxVal = (samples.maxOrNull() ?: 16f) + 1.5f
        val span = (maxVal - minVal).coerceAtLeast(1f)
        val stepX = size.width / (samples.size - 1).toFloat()

        val linePath = Path()
        val fillPath = Path()
        samples.forEachIndexed { index, value ->
            val x = index * stepX
            val normalized = ((value - minVal) / span).coerceIn(0f, 1f)
            val y = size.height * (1f - normalized * 0.82f - 0.09f)
            if (index == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, size.height)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo(size.width, size.height)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.28f), Color.Transparent)
            )
        )
        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

// ---------------------------------------------------------------------------
// Real-Time SurfaceFlinger Frame Sampler & Persistent Root Shell
// ---------------------------------------------------------------------------

private class PersistentRootShell {
    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null
    private var counter = 0

    @Synchronized
    fun exec(command: String): String {
        ensureStarted()
        val w = writer ?: return ""
        val r = reader ?: return ""
        counter += 1
        val marker = "__LUMINA_END_${counter}__"
        return try {
            w.write(command)
            w.newLine()
            w.write("echo $marker")
            w.newLine()
            w.flush()

            val sb = StringBuilder()
            while (true) {
                val line = r.readLine() ?: break
                if (line == marker) break
                sb.append(line).append('\n')
            }
            sb.toString().trim()
        } catch (_: Exception) {
            close()
            ""
        }
    }

    private fun ensureStarted() {
        val current = process
        if (current != null) {
            val alive = runCatching {
                current.exitValue()
                false
            }.getOrDefault(true)
            if (alive) return
        }
        close()
        runCatching {
            val p = ProcessBuilder("su")
                .redirectErrorStream(true)
                .start()
            process = p
            writer = BufferedWriter(OutputStreamWriter(p.outputStream))
            reader = BufferedReader(InputStreamReader(p.inputStream))
        }
    }

    @Synchronized
    fun close() {
        runCatching { writer?.close() }
        runCatching { reader?.close() }
        runCatching { process?.destroy() }
        writer = null
        reader = null
        process = null
    }
}

private class SurfaceFlingerSampler {
    data class Sample(val fps: Int, val frametimeMs: Float)

    private var lastLayer: String = ""
    private var lastMaxPresentNs: Long = 0L
    private var lastWallNs: Long = 0L

    fun sampleLayer(shell: PersistentRootShell, layerName: String): Sample? {
        if (layerName != lastLayer) {
            lastLayer = layerName
            lastMaxPresentNs = 0L
            lastWallNs = 0L
        }
        val escaped = layerName.replace("\"", "\\\"")
        val raw = shell.exec("dumpsys SurfaceFlinger --latency \"$escaped\" 2>/dev/null")
        if (raw.isBlank()) return null

        val lines = raw.lines()
        if (lines.size < 2) return null

        val validPresentNs = ArrayList<Long>(lines.size)
        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) continue
            val parts = line.split(Regex("\\s+"))
            val present = parts.getOrNull(1)?.toLongOrNull() ?: continue
            if (present <= 0L || present == Long.MAX_VALUE) continue
            validPresentNs.add(present)
        }
        if (validPresentNs.isEmpty()) return null
        validPresentNs.sort()

        val nowWall = System.nanoTime()
        val newest = validPresentNs.last()

        if (lastMaxPresentNs == 0L) {
            lastMaxPresentNs = newest
            lastWallNs = nowWall
            val cutoff = newest - 1_000_000_000L
            val windowFrames = validPresentNs.filter { it >= cutoff }
            if (windowFrames.size < 2) return Sample(fps = 0, frametimeMs = 16.6f)
            val spanNs = (windowFrames.last() - windowFrames.first()).coerceAtLeast(1_000_000L)
            val fps = ((windowFrames.size - 1) * 1_000_000_000.0 / spanNs).roundToInt()
            val lastDeltaMs = ((windowFrames.last() - windowFrames[windowFrames.size - 2]) / 1_000_000.0).toFloat()
            return Sample(fps = fps, frametimeMs = lastDeltaMs)
        }

        val newFrames = validPresentNs.filter { it > lastMaxPresentNs }
        if (newFrames.isEmpty()) {
            val idleNs = (nowWall - lastWallNs).coerceAtLeast(0L)
            return if (idleNs > 1_200_000_000L) {
                Sample(fps = 0, frametimeMs = 16.6f)
            } else {
                null
            }
        }

        val spanNs = (newFrames.last() - lastMaxPresentNs).coerceAtLeast(1_000_000L)
        val fps = (newFrames.size * 1_000_000_000.0 / spanNs).roundToInt()
        val frametimeMs = if (newFrames.size >= 2) {
            ((newFrames.last() - newFrames[newFrames.size - 2]) / 1_000_000.0).toFloat()
        } else {
            ((newFrames.last() - lastMaxPresentNs) / 1_000_000.0).toFloat()
        }

        lastMaxPresentNs = newFrames.last()
        lastWallNs = nowWall
        return Sample(fps = fps, frametimeMs = frametimeMs)
    }
}

private class ChoreographerFpsTracker : Choreographer.FrameCallback {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var running = false
    private var frameCount = 0
    private var lastSampleNs = 0L

    fun start() {
        if (running) return
        running = true
        frameCount = 0
        lastSampleNs = System.nanoTime()
        mainHandler.post {
            if (running) Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun stop() {
        running = false
    }

    fun consumeFps(): Int {
        val now = System.nanoTime()
        val elapsedNs = (now - lastSampleNs).coerceAtLeast(1L)
        val count = frameCount
        frameCount = 0
        lastSampleNs = now
        return (count * 1_000_000_000.0 / elapsedNs).roundToInt()
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        frameCount += 1
        Choreographer.getInstance().postFrameCallback(this)
    }
}

private class OverlayLifecycleOwner :
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val viewModelStore: ViewModelStore
        get() = store

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    fun performRestore() {
        savedStateController.performRestore(null)
    }

    fun handleLifecycleEvent(event: Lifecycle.Event) {
        lifecycleRegistry.handleLifecycleEvent(event)
    }
}
