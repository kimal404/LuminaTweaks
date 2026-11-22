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

import android.app.ActivityManager
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.view.Display
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
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
import com.luminatweaks.data.ConfigRepository
import com.luminatweaks.data.DaemonManager
import com.luminatweaks.data.GameBarConfig
import com.luminatweaks.data.GameBarPrefs
import com.luminatweaks.data.GameBarQuickApp
import com.luminatweaks.data.GameBarSide
import com.luminatweaks.data.GameBarUiState
import com.luminatweaks.data.LiveFeedSample
import com.luminatweaks.data.LiveSessionFeed
import com.luminatweaks.data.SysfsTelemetry
import com.luminatweaks.ui.overlay.GameBarRoot
import com.luminatweaks.ui.screen.FloatingFpsPrefs
import com.luminatweaks.ui.theme.LuminaTheme
import com.luminatweaks.util.DeviceUtil
import com.luminatweaks.util.RootUtil
import java.util.Locale
import kotlin.math.min
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

class GameBarService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    companion object {
        private const val CHANNEL_ID = "lumina_game_bar_channel"
        private const val NOTIFICATION_ID = 4042
        private const val GAME_STALE_GRACE_MS = 10_000L
        private const val PREVIEW_SECONDS = 3600
        private const val PREVIEW_RENEW_MS = 30 * 60 * 1000L

        const val ACTION_SYNC = "com.luminatweaks.action.GAME_BAR_SYNC"
        const val ACTION_SHOW_NOW = "com.luminatweaks.action.GAME_BAR_SHOW_NOW"
        const val ACTION_HIDE_NOW = "com.luminatweaks.action.GAME_BAR_HIDE_NOW"
        const val ACTION_STOP = "com.luminatweaks.action.GAME_BAR_STOP"

        suspend fun ensureOverlayPermission(context: Context): Boolean =
            FloatingFpsService.ensureOverlayPermission(context)

        fun sync(context: Context, config: GameBarConfig = GameBarPrefs.load(context)) {
            if (!config.enabled) {
                context.stopService(Intent(context, GameBarService::class.java))
                return
            }
            startCompat(context, Intent(context, GameBarService::class.java).apply {
                action = ACTION_SYNC
            })
        }

        fun showNow(context: Context) {
            startCompat(context, Intent(context, GameBarService::class.java).apply {
                action = ACTION_SHOW_NOW
            })
        }

        fun hideNow(context: Context) {
            startCompat(context, Intent(context, GameBarService::class.java).apply {
                action = ACTION_HIDE_NOW
            })
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

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private val uiState = mutableStateOf(GameBarUiState())
    private val sysfs = SysfsTelemetry()

    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var telemetryJob: Job? = null
    private var brightnessJob: Job? = null
    private var profileJob: Job? = null

    private var activeConfig = GameBarConfig()
    private var isAttached = false
    private var isExpanded = false
    private var manualOverride: Boolean? = null
    private var wasGameActive = false
    private var lastGameSeenMs = 0L
    private var previewLease = false
    private var lastLeaseRequestMs = 0L
    private var windowConfigKey = ""
    private var lastMetricsMs = 0L
    private var lastFpsValue = 0
    private var lastFpsAtMs = 0L
    private var cachedGamePackage: String? = null
    private var cachedGameLabel: String? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        startForegroundCompat()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        activeConfig = GameBarPrefs.load(this)
        when (intent?.action) {
            ACTION_SHOW_NOW -> {
                manualOverride = true
                startForegroundCompat()
                startTelemetryLoop()
                refreshSoon()
                return START_STICKY
            }

            ACTION_HIDE_NOW -> {
                manualOverride = false
                if (!activeConfig.enabled) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                startTelemetryLoop()
                return START_STICKY
            }

            ACTION_STOP -> {
                GameBarPrefs.save(this, activeConfig.copy(enabled = false))
                manualOverride = false
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_SYNC -> {
                manualOverride = null
            }
        }

        if (!activeConfig.enabled && manualOverride != true) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundCompat()
        startTelemetryLoop()
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateWindowLayout(force = true)
    }

    override fun onDestroy() {
        telemetryJob?.cancel()
        brightnessJob?.cancel()
        profileJob?.cancel()
        detachOverlay()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startTelemetryLoop() {
        if (telemetryJob?.isActive == true) return
        telemetryJob = serviceScope.launch {
            while (isActive) {
                val config = GameBarPrefs.load(this@GameBarService)
                activeConfig = config
                uiState.value = uiState.value.copy(
                    side = config.side,
                    opacity = config.opacity,
                    handleOpacity = config.handleOpacity,
                    autoDimHandle = config.autoDimHandle,
                    autoGame = config.autoGame,
                    manual = manualOverride == true
                )
                val now = System.currentTimeMillis()
                val feed = withContext(Dispatchers.IO) { LiveSessionFeed.read(this@GameBarService) }
                val gameActive = feed?.gameActive == true
                if (gameActive) {
                    lastGameSeenMs = now
                    updateGameLabel(feed?.packageName)
                } else if (feed?.packageName == null) {
                    cachedGamePackage = null
                    cachedGameLabel = null
                }
                if (gameActive && !wasGameActive) {
                    manualOverride = null
                }
                wasGameActive = gameActive

                if (!config.enabled && manualOverride != true) {
                    stopSelf()
                    break
                }

                val autoVisible = config.enabled && (
                    !config.autoGame ||
                        gameActive ||
                        (lastGameSeenMs > 0L && now - lastGameSeenMs <= GAME_STALE_GRACE_MS)
                    )
                val visible = (manualOverride ?: autoVisible) && !isKeyguardLocked()

                if (!visible) {
                    if (isAttached) {
                        detachOverlay()
                        updateNotification(false)
                    }
                    delay(500L)
                    continue
                }

                refreshMetrics(config, feed, now)
                if (!isAttached) {
                    attachOverlay()
                    updateNotification(true)
                }
                val key = windowKey(config)
                if (key != windowConfigKey) {
                    windowConfigKey = key
                    updateWindowLayout(force = true)
                }
                applyPreviewLease(config)
                delay(500L)
            }
        }
    }

    private fun refreshSoon() {
        serviceScope.launch {
            val config = GameBarPrefs.load(this@GameBarService)
            activeConfig = config
            refreshMetrics(config, null, System.currentTimeMillis())
            if (!isAttached) {
                attachOverlay()
                updateNotification(true)
            }
        }
    }

    private fun refreshMetrics(
        config: GameBarConfig,
        feed: LiveFeedSample?,
        now: Long
    ) {
        val collect = now - lastMetricsMs >= 1_000L || uiState.value.quickApps.size != config.quickApps.size
        if (!collect) return
        lastMetricsMs = now

        serviceScope.launch {
            val metrics = withContext(Dispatchers.IO) { readMetrics(config, feed) }
            val nowMs = System.currentTimeMillis()
            val fps = if (metrics.fps > 0) {
                lastFpsValue = metrics.fps
                lastFpsAtMs = nowMs
                metrics.fps
            } else if (nowMs - lastFpsAtMs <= 4_000L) {
                lastFpsValue
            } else {
                0
            }
            val frametime = if (metrics.frametimeMs > 0f) {
                metrics.frametimeMs
            } else if (fps > 0) {
                1_000f / fps
            } else {
                0f
            }
            uiState.value = uiState.value.copy(
                expanded = isExpanded,
                liteMode = metrics.liteMode,
                rrModes = metrics.rrModes,
                currentRrId = metrics.currentRrId,
                fps = fps,
                frametimeMs = frametime,
                targetHz = metrics.targetHz,
                profile = metrics.profile,
                autoProfile = metrics.autoProfile,
                gameLabel = cachedGameLabel,
                gamePackage = cachedGamePackage,
                cpuPercent = metrics.cpuPercent,
                gpuPercent = metrics.gpuPercent,
                tempLabel = metrics.tempLabel,
                batteryPercent = metrics.batteryPercent,
                ramPercent = metrics.ramPercent,
                volumePercent = metrics.volumePercent,
                brightnessPercent = metrics.brightnessPercent,
                side = config.side,
                opacity = config.opacity,
                handleOpacity = config.handleOpacity,
                autoDimHandle = config.autoDimHandle,
                autoGame = config.autoGame,
                manual = manualOverride == true,
                fpsOverlay = metrics.fpsOverlay,
                dndEnabled = metrics.dndEnabled,
                quickApps = metrics.quickApps
            )
        }
    }

    private data class BarMetrics(
        val fps: Int,
        val frametimeMs: Float,
        val targetHz: Int,
        val profile: String,
        val autoProfile: Boolean,
        val cpuPercent: Int?,
        val gpuPercent: Int?,
        val tempLabel: String,
        val batteryPercent: Int,
        val ramPercent: Int?,
        val volumePercent: Int,
        val brightnessPercent: Int,
        val fpsOverlay: Boolean,
        val dndEnabled: Boolean,
        val liteMode: Boolean,
        val rrModes: List<Int>,
        val currentRrId: Int?,
        val quickApps: List<GameBarQuickApp>
    )

    private fun readMetrics(config: GameBarConfig, feed: LiveFeedSample?): BarMetrics {
        val hz = readDisplayHz().coerceAtLeast(60)
        val rawFps = feed?.fps?.takeIf { it > 0f } ?: 0f
        val fps = if (rawFps > 0f) rawFps.roundToInt().coerceIn(1, hz) else 0
        val frametime = when {
            feed != null && feed.frametimeMs > 0f -> feed.frametimeMs
            fps > 0 -> 1000f / fps
            else -> 0f
        }

        val audio = getSystemService(AUDIO_SERVICE) as? AudioManager
        val maxVolume = audio?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 1
        val volume = audio?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
        val brightness = Settings.System.getInt(
            contentResolver,
            Settings.System.SCREEN_BRIGHTNESS,
            128
        ).coerceIn(1, 255)
        val battery = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.let { intent ->
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                if (level >= 0) (level * 100f / scale).roundToInt() else 0
            } ?: 0

        val activityManager = getSystemService(ACTIVITY_SERVICE) as? ActivityManager
        val memory = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memory)
        val ram = if (memory.totalMem > 0L) {
            (((memory.totalMem - memory.availMem).toDouble() / memory.totalMem.toDouble()) * 100.0)
                .roundToInt()
                .coerceIn(1, 99)
        } else {
            null
        }

        val quickApps = config.quickApps.map { pkg ->
            val label = runCatching {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
            }.getOrDefault(pkg)
            GameBarQuickApp(pkg, label)
        }.sortedBy { it.label.lowercase(Locale.ROOT) }

        return BarMetrics(
            fps = fps,
            frametimeMs = frametime,
            targetHz = hz,
            profile = DeviceUtil.readSystemProperty("sys.lumina.profile") ?: "Balanced",
            autoProfile = DeviceUtil.readSystemProperty("persist.lumina.autoprofile")
                .let { it == null || (!it.equals("0", true) && !it.equals("false", true)) },
            cpuPercent = sysfs.cpuPercent()?.roundToInt()?.coerceIn(0, 100) ?: 0,
            gpuPercent = sysfs.gpuPercent()?.roundToInt()?.coerceIn(0, 100) ?: 0,
            tempLabel = DeviceUtil.getBatteryTemperature(this),
            batteryPercent = battery,
            ramPercent = ram,
            volumePercent = (volume * 100f / maxVolume).roundToInt().coerceIn(0, 100),
            brightnessPercent = (brightness * 100f / 255f).roundToInt().coerceIn(0, 100),
            fpsOverlay = FloatingFpsPrefs.load(this).enabled,
            dndEnabled = (getSystemService(NotificationManager::class.java)
                ?.currentInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_ALL) !=
                NotificationManager.INTERRUPTION_FILTER_ALL,
            liteMode = ConfigRepository.cachedTweaksState?.isGlobalLiteMode ?: false,
            rrModes = supportedRefreshRates(),
            currentRrId = GameBarPrefs.refreshRateHint(this),
            quickApps = quickApps
        )
    }

    private fun isKeyguardLocked(): Boolean {
        val keyguard = getSystemService(KeyguardManager::class.java) ?: return false
        return runCatching { keyguard.isKeyguardLocked }.getOrDefault(false)
    }

    private fun updateGameLabel(packageName: String?) {
        if (packageName.isNullOrBlank()) {
            cachedGamePackage = null
            cachedGameLabel = null
            return
        }
        if (packageName == cachedGamePackage) return
        cachedGamePackage = packageName
        cachedGameLabel = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        }.getOrNull()
    }

    private fun attachOverlay() {
        if (isAttached) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) return
        uiState.value = uiState.value.copy(
            side = activeConfig.side,
            opacity = activeConfig.opacity,
            handleOpacity = activeConfig.handleOpacity,
            autoDimHandle = activeConfig.autoDimHandle,
            autoGame = activeConfig.autoGame,
            manual = manualOverride == true
        )
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@GameBarService)
            setViewTreeViewModelStoreOwner(this@GameBarService)
            setViewTreeSavedStateRegistryOwner(this@GameBarService)
            setContent {
                LuminaTheme {
                    GameBarRoot(
                        state = uiState,
                        onExpand = { setExpanded(true) },
                        onCollapse = { setExpanded(false) },
                        onProfileSelected = ::selectProfile,
                        onVolumeChanged = ::setVolume,
                        onBrightnessChanged = ::setBrightness,
                        onQuickAppClick = ::openApp,
                        onToggleFpsOverlay = ::toggleFpsOverlay,
                        onToggleDnd = ::toggleDnd,
                        onToggleLiteMode = ::toggleLiteMode,
                        onOpenManager = ::openManager,
                        onRefreshRateSelected = ::selectRefreshRate
                    )
                }
            }
        }
        val params = createLayoutParams()
        configureLayoutParams(params, view)
        runCatching {
            windowManager.addView(view, params)
            composeView = view
            layoutParams = params
            isAttached = true
            windowConfigKey = windowKey(activeConfig)
            view.post { updateGestureExclusion() }
        }
    }

    private fun detachOverlay() {
        if (!isAttached) return
        val view = composeView
        if (view != null) {
            runCatching { windowManager.removeViewImmediate(view) }
        }
        composeView = null
        layoutParams = null
        isAttached = false
        isExpanded = false
        uiState.value = uiState.value.copy(expanded = false)
    }

    private fun setExpanded(expanded: Boolean) {
        if (isExpanded == expanded) return
        isExpanded = expanded
        uiState.value = uiState.value.copy(expanded = expanded)
        updateWindowLayout(force = true)
    }

    private fun updateWindowLayout(force: Boolean = false) {
        if (!isAttached) return
        val view = composeView ?: return
        val params = layoutParams ?: return
        configureLayoutParams(params, view)
        if (force) {
            windowConfigKey = windowKey(activeConfig)
        }
        runCatching { windowManager.updateViewLayout(view, params) }
        view.post { updateGestureExclusion() }
    }

    private fun windowKey(config: GameBarConfig): String =
        "${config.side.key}|${config.panelWidthDp}|${isExpanded}"

    private fun configureLayoutParams(params: WindowManager.LayoutParams, view: ComposeView) {
        val metrics = resources.displayMetrics
        val density = metrics.density
        val isLeft = activeConfig.side == GameBarSide.LEFT
        val edgeWidth = (14f * density).roundToInt().coerceAtLeast(1)
        val panelWidth = min(
            (activeConfig.panelWidthDp * density).roundToInt(),
            (metrics.widthPixels * 0.92f).roundToInt()
        ).coerceAtLeast((240f * density).roundToInt())
        val margin = (14f * density).roundToInt()

        if (isExpanded) {
            params.gravity = Gravity.CENTER_VERTICAL or (if (isLeft) Gravity.LEFT else Gravity.RIGHT)
            params.x = margin
            params.y = 0
            params.width = panelWidth
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
        } else {
            params.gravity = Gravity.TOP or (if (isLeft) Gravity.LEFT else Gravity.RIGHT)
            params.x = 0
            params.y = 0
            params.width = edgeWidth
            params.height = WindowManager.LayoutParams.MATCH_PARENT
        }
        view.requestLayout()
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun updateGestureExclusion() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val view = composeView ?: return
        if (view.width <= 0 || view.height <= 0) return
        view.systemGestureExclusionRects = if (isExpanded) {
            mutableListOf()
        } else {
            mutableListOf(Rect(0, 0, view.width, view.height))
        }
    }

    private fun applyPreviewLease(config: GameBarConfig) {
        val need = manualOverride == true || (config.enabled && !config.autoGame && manualOverride != false)
        if (!need) {
            previewLease = false
            return
        }
        val now = System.currentTimeMillis()
        if (previewLease && now - lastLeaseRequestMs < PREVIEW_RENEW_MS) return
        previewLease = true
        lastLeaseRequestMs = now
        serviceScope.launch(Dispatchers.IO) {
            runCatching { DaemonManager.requestFpsPreview(PREVIEW_SECONDS) }
        }
    }

    private fun selectProfile(name: String) {
        profileJob?.cancel()
        profileJob = serviceScope.launch {
            val safe = name.trim().takeIf { it.isNotBlank() } ?: return@launch
            if (uiState.value.autoProfile) {
                withContext(Dispatchers.IO) {
                    runCatching { ConfigRepository.setAutoProfile(false) }
                }
                Toast.makeText(
                    this@GameBarService,
                    localizedText(
                        "Auto profile dimatikan, profil manual aktif",
                        "Auto profile disabled, manual profile active"
                    ),
                    Toast.LENGTH_SHORT
                ).show()
                delay(180L)
            }
            val ok = withContext(Dispatchers.IO) { DaemonManager.setProfile(safe) }
            if (ok) {
                uiState.value = uiState.value.copy(profile = safe, autoProfile = false)
            }
        }
    }

    private fun setVolume(percent: Int) {
        val audio = getSystemService(AUDIO_SERVICE) as? AudioManager ?: return
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val value = (percent.coerceIn(0, 100) * max / 100f).roundToInt()
        runCatching {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0)
        }
        uiState.value = uiState.value.copy(volumePercent = percent.coerceIn(0, 100))
    }

    private fun setBrightness(percent: Int) {
        val safe = percent.coerceIn(1, 100)
        uiState.value = uiState.value.copy(brightnessPercent = safe)
        brightnessJob?.cancel()
        brightnessJob = serviceScope.launch {
            delay(120L)
            val value = (safe * 255f / 100f).roundToInt().coerceIn(1, 255)
            val wrote = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.System.canWrite(this@GameBarService)) {
                runCatching {
                    Settings.System.putInt(
                        contentResolver,
                        Settings.System.SCREEN_BRIGHTNESS,
                        value
                    )
                }.getOrDefault(false)
            } else {
                false
            }
            if (!wrote) {
                withContext(Dispatchers.IO) {
                    runCatching { RootUtil.runAsRoot("settings put system screen_brightness $value") }
                }
            }
        }
    }

    private fun toggleFpsOverlay() {
        val current = FloatingFpsPrefs.load(this)
        if (current.enabled) {
            val updated = current.copy(enabled = false)
            FloatingFpsPrefs.save(this, updated)
            FloatingFpsService.sync(this, updated)
            uiState.value = uiState.value.copy(fpsOverlay = false)
            return
        }
        serviceScope.launch {
            val granted = FloatingFpsService.ensureOverlayPermission(this@GameBarService)
            if (!granted) {
                Toast.makeText(
                    this@GameBarService,
                    localizedText(
                        "Izin overlay diperlukan untuk FPS Meter",
                        "Overlay permission is required for FPS Meter"
                    ),
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            val updated = FloatingFpsPrefs.load(this@GameBarService).copy(enabled = true)
            FloatingFpsPrefs.save(this@GameBarService, updated)
            FloatingFpsService.sync(this@GameBarService, updated)
            uiState.value = uiState.value.copy(fpsOverlay = true)
        }
    }

    private fun toggleDnd() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (!manager.isNotificationPolicyAccessGranted) {
            Toast.makeText(
                this,
                localizedText(
                    "Izinkan akses Jangan Ganggu untuk fitur ini",
                    "Allow Do Not Disturb access for this feature"
                ),
                Toast.LENGTH_SHORT
            ).show()
            runCatching {
                startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            return
        }
        val enable = manager.currentInterruptionFilter ==
            NotificationManager.INTERRUPTION_FILTER_ALL
        val target = if (enable) {
            NotificationManager.INTERRUPTION_FILTER_PRIORITY
        } else {
            NotificationManager.INTERRUPTION_FILTER_ALL
        }
        val ok = runCatching {
            manager.setInterruptionFilter(target)
            true
        }.getOrDefault(false)
        if (ok) {
            uiState.value = uiState.value.copy(dndEnabled = enable)
        }
    }

    private fun toggleLiteMode() {
        val target = !uiState.value.liteMode
        serviceScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { ConfigRepository.setGlobalLiteMode(target) }.isSuccess
            }
            if (ok) {
                uiState.value = uiState.value.copy(liteMode = target)
                Toast.makeText(
                    this@GameBarService,
                    if (target) {
                        localizedText("Mode Lite aktif", "Lite mode enabled")
                    } else {
                        localizedText("Mode Lite nonaktif", "Lite mode disabled")
                    },
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun supportedRefreshRates(): List<Int> {
        val display = currentDisplay() ?: return emptyList()
        val modes = runCatching {
            display.supportedModes.map { (it.refreshRate + 0.5f).toInt() }
        }.getOrDefault(emptyList())
        return modes.filter { it > 0 }.distinct().sortedDescending()
    }

    @Suppress("DEPRECATION")
    private fun currentDisplay(): Display? {
        val viaApi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                createDisplayContext(display)
                    .getSystemService(WindowManager::class.java)
                    ?.defaultDisplay
            }.getOrNull()
        } else {
            null
        }
        return viaApi ?: runCatching { windowManager.defaultDisplay }.getOrNull()
    }

    private fun selectRefreshRate(hz: Int?) {
        serviceScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { applyRefreshRate(hz) }.getOrDefault(false)
            }
            if (ok) {
                GameBarPrefs.setRefreshRateHint(this@GameBarService, hz)
                uiState.value = uiState.value.copy(
                    currentRrId = hz,
                    targetHz = hz ?: uiState.value.targetHz
                )
            } else {
                Toast.makeText(
                    this@GameBarService,
                    localizedText("Gagal mengubah refresh rate", "Failed to change refresh rate"),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private suspend fun applyRefreshRate(hz: Int?): Boolean {
        if (hz == null) {
            ConfigRepository.setRefreshRate(null)
            return true
        }
        var id = ConfigRepository.cachedTweaksState
            ?.rrModes
            ?.firstOrNull { it.hz == hz }
            ?.id
        if (id == null) {
            id = runCatching {
                currentDisplay()?.supportedModes
                    ?.filter { (it.refreshRate + 0.5f).toInt() == hz }
                    ?.minByOrNull { it.modeId }
                    ?.modeId
            }.getOrNull()
        }
        ConfigRepository.setRefreshRate(id)
        return true
    }

    private fun openApp(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(intent) }
        setExpanded(false)
    }

    private fun openManager() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        runCatching { startActivity(intent) }
        setExpanded(false)
    }

    private fun readDisplayHz(): Int {
        @Suppress("DEPRECATION")
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { display }.getOrNull() ?: windowManager.defaultDisplay
        } else {
            windowManager.defaultDisplay
        }
        return display?.refreshRate?.roundToInt() ?: 60
    }

    private fun startForegroundCompat() {
        val notification = buildNotification(isAttached)
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

    private fun updateNotification(visible: Boolean) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, buildNotification(visible))
    }

    private fun buildNotification(visible: Boolean): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val showIntent = PendingIntent.getService(
            this,
            10,
            Intent(this, GameBarService::class.java).apply { action = ACTION_SHOW_NOW },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val hideIntent = PendingIntent.getService(
            this,
            11,
            Intent(this, GameBarService::class.java).apply { action = ACTION_HIDE_NOW },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            12,
            Intent(this, GameBarService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val status = when {
            visible -> localizedText("Game Bar aktif", "Game Bar active")
            activeConfig.autoGame -> localizedText(
                "Menunggu game berjalan",
                "Waiting for a game session"
            )
            else -> localizedText("Game Bar siap", "Game Bar ready")
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(localizedText("Lumina Game Bar", "Lumina Game Bar"))
            .setContentText(status)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, localizedText("Tampilkan", "Show"), showIntent)
            .addAction(0, localizedText("Sembunyikan", "Hide"), hideIntent)
            .addAction(0, localizedText("Matikan", "Stop"), stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            localizedText("Lumina Game Bar", "Lumina Game Bar"),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = localizedText(
                "Sidepanel kontrol dan telemetri saat bermain game",
                "Control and telemetry sidepanel while gaming"
            )
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun localizedText(id: String, en: String): String {
        val lang = Locale.getDefault().language.lowercase(Locale.ROOT)
        return if (lang == "in" || lang == "id") id else en
    }
}
