package com.luminatweaks.gamedeck

import android.app.ActivityOptions
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.compositionContext
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
import com.luminatweaks.data.LiveSessionFeed
import com.luminatweaks.data.SysfsTelemetry
import com.luminatweaks.service.FloatingFpsService
import com.luminatweaks.ui.theme.LuminaTheme
import com.luminatweaks.util.DeviceUtil
import com.luminatweaks.util.RootUtil
import java.util.Locale
import kotlin.math.abs
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

class GameDeckService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    companion object {
        private const val CHANNEL_ID = "lumina_game_deck_channel"
        private const val NOTIFICATION_ID = 4046
        private const val GAME_STALE_GRACE_MS = 10_000L
        private const val REOPEN_COOLDOWN_MS = 320L
        private const val EXPANDED_HEIGHT_FRACTION = 0.76f
        private const val EXPANDED_MAX_HEIGHT_DP = 336f
        private const val COLLAPSED_WIDTH_DP = 24f
        private const val COLLAPSED_HEIGHT_DP = 72f
        private const val EDGE_MARGIN_DP = 12f

        const val ACTION_SYNC = "com.luminatweaks.action.GAME_DECK_SYNC"
        const val ACTION_SHOW_NOW = "com.luminatweaks.action.GAME_DECK_SHOW_NOW"
        const val ACTION_HIDE_NOW = "com.luminatweaks.action.GAME_DECK_HIDE_NOW"
        const val ACTION_STOP = "com.luminatweaks.action.GAME_DECK_STOP"

        suspend fun ensureOverlayPermission(context: Context): Boolean =
            FloatingFpsService.ensureOverlayPermission(context)

        fun sync(context: Context, config: GameDeckConfig = GameDeckPrefs.load(context)) {
            if (!config.enabled) {
                context.stopService(Intent(context, GameDeckService::class.java))
                return
            }
            startCompat(context, Intent(context, GameDeckService::class.java).apply {
                action = ACTION_SYNC
            })
        }

        fun showNow(context: Context) {
            startCompat(context, Intent(context, GameDeckService::class.java).apply {
                action = ACTION_SHOW_NOW
            })
        }

        fun hideNow(context: Context) {
            startCompat(context, Intent(context, GameDeckService::class.java).apply {
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

    private val uiState = mutableStateOf(GameDeckUiState())
    private val isWindowExpandedState = mutableStateOf(false)
    private val isHandlePressedState = mutableStateOf(false)
    private val sysfs = SysfsTelemetry()

    private lateinit var windowManager: WindowManager
    private var hostView: FrameLayout? = null
    private var composeView: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var telemetryJob: Job? = null
    private var transitionJob: Job? = null
    private var recomposer: Recomposer? = null
    private var recomposerJob: Job? = null
    private var lastForegroundPkg: String? = null
    private var lastForegroundTs: Long = 0L

    private var activeConfig = GameDeckConfig()
    private var isAttached = false
    private var isExpanded = false
    private var isCollapsing = false
    private var edgeYOffsetPx = 0
    private var reopenBlockedUntilMs = 0L
    private var manualOverride: Boolean? = null
    private var wasGameActive = false
    private var lastGameSeenMs = 0L
    private var lastMetricsMs = 0L
    private var windowKey = ""
    private var freeformUnlocked = false

    private var pendingOverrideLabel: String? = null
    private var pendingOverrideUntilMs = 0L
    private var profileAutoMode = true
    private var prevRootCpuTotal = 0L
    private var prevRootCpuIdle = 0L
    private var lastCpuPercent = 0
    private var lastGpuPercent = 0

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        val currentOverride = DeviceUtil.readSystemProperty("sys.lumina.profile_override") ?: "Auto"
        profileAutoMode = currentOverride.equals("Auto", ignoreCase = true)
        uiState.value = uiState.value.copy(
            profileAutoMode = profileAutoMode,
            profileOverride = currentOverride
        )
        startForegroundCompat()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        activeConfig = GameDeckPrefs.load(this)
        edgeYOffsetPx = activeConfig.edgeYOffsetPx
        when (intent?.action) {
            ACTION_SHOW_NOW -> {
                manualOverride = true
                startForegroundCompat()
                startTelemetryLoop()
                lastMetricsMs = 0L
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
                GameDeckPrefs.save(
                    this,
                    activeConfig.copy(enabled = false, edgeYOffsetPx = edgeYOffsetPx)
                )
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
        updateWindowLayout()
    }

    override fun onDestroy() {
        telemetryJob?.cancel()
        transitionJob?.cancel()
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
                val config = GameDeckPrefs.load(this@GameDeckService)
                activeConfig = config.copy(edgeYOffsetPx = edgeYOffsetPx)
                val now = System.currentTimeMillis()
                val feed = withContext(Dispatchers.IO) { LiveSessionFeed.read(this@GameDeckService) }
                val gameActive = (feed?.gameActive == true) ||
                    withContext(Dispatchers.IO) { isForegroundGameActive(now) }
                if (gameActive) {
                    lastGameSeenMs = now
                }
                if (gameActive && !wasGameActive) {
                    manualOverride = null
                }
                if (!gameActive && wasGameActive && manualOverride != true && !profileAutoMode) {
                    profileAutoMode = true
                    withContext(Dispatchers.IO) { DaemonManager.clearProfileOverride() }
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

                if (!isAttached) {
                    attachOverlay()
                    updateNotification(true)
                }

                val nextKey = "${config.side.key}|${isWindowExpandedState.value}"
                if (nextKey != windowKey) {
                    windowKey = nextKey
                    updateWindowLayout()
                }

                val interval = if (isExpanded) 850L else 2_500L
                if (now - lastMetricsMs >= interval || uiState.value.quickApps.size != config.quickApps.size) {
                    lastMetricsMs = now
                    val snapshot = withContext(Dispatchers.IO) { collectSnapshot(config, isExpanded, feed) }
                    if (pendingOverrideLabel != null &&
                        (snapshot.profileOverride == pendingOverrideLabel ||
                            now >= pendingOverrideUntilMs)
                    ) {
                        pendingOverrideLabel = null
                    }
                    uiState.value = uiState.value.copy(
                        expanded = isExpanded,
                        side = config.side,
                        opacity = config.opacity,
                        fps = snapshot.fps,
                        frametimeMs = snapshot.frametimeMs,
                        targetHz = snapshot.targetHz,
                        cpuPercent = snapshot.cpuPercent,
                        gpuPercent = snapshot.gpuPercent,
                        tempLabel = snapshot.tempLabel,
                        batteryPercent = snapshot.batteryPercent,
                        profile = snapshot.profile,
                        profileOverride = pendingOverrideLabel ?: snapshot.profileOverride,
                        profileAutoMode = profileAutoMode,
                        rrModes = snapshot.rrModes,
                        selectedRrHz = snapshot.selectedRrHz,
                        dndEnabled = snapshot.dndEnabled,
                        quickApps = snapshot.quickApps
                    )
                }
                delay(450L)
            }
        }
    }

    private data class DeckSnapshot(
        val fps: Int,
        val frametimeMs: Float,
        val targetHz: Int,
        val cpuPercent: Int,
        val gpuPercent: Int,
        val tempLabel: String,
        val batteryPercent: Int,
        val profile: String,
        val profileOverride: String,
        val rrModes: List<Int>,
        val selectedRrHz: Int?,
        val dndEnabled: Boolean,
        val quickApps: List<GameDeckQuickApp>
    )

    private fun collectSnapshot(
        config: GameDeckConfig,
        collectHeavy: Boolean,
        feed: com.luminatweaks.data.LiveFeedSample?
    ): DeckSnapshot {
        val hz = readDisplayHz().coerceAtLeast(30)
        val rawFps = if (collectHeavy) (feed?.fps?.takeIf { it > 0f } ?: 0f) else 0f
        val fps = if (rawFps > 0f) rawFps.roundToInt().coerceIn(1, hz) else 0
        val frametime = when {
            !collectHeavy -> 0f
            feed != null && feed.frametimeMs > 0f -> feed.frametimeMs
            fps > 0 -> 1000f / fps
            else -> 0f
        }

        val battery = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.let { intent ->
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                if (level >= 0) (level * 100f / scale).roundToInt() else 0
            } ?: 0

        val quickApps = config.quickApps.map { pkg ->
            val label = runCatching {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
            }.getOrDefault(pkg)
            GameDeckQuickApp(pkg, label)
        }.sortedBy { it.label.lowercase(Locale.ROOT) }

        val cpu = if (collectHeavy) readCpuLoad() else lastCpuPercent
        val gpu = if (collectHeavy) readGpuLoad() else lastGpuPercent

        val profileProp = DeviceUtil.readSystemProperty("sys.lumina.profile") ?: "Balanced"
        val overrideProp = DeviceUtil.readSystemProperty("sys.lumina.profile_override") ?: "Auto"

        return DeckSnapshot(
            fps = fps,
            frametimeMs = frametime,
            targetHz = hz,
            cpuPercent = cpu,
            gpuPercent = gpu,
            tempLabel = DeviceUtil.getBatteryTemperature(this),
            batteryPercent = battery,
            profile = profileProp,
            profileOverride = overrideProp,
            rrModes = supportedRefreshRates(),
            selectedRrHz = GameDeckPrefs.refreshRateHint(this),
            dndEnabled = (getSystemService(NotificationManager::class.java)
                ?.currentInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_ALL) !=
                NotificationManager.INTERRUPTION_FILTER_ALL,
            quickApps = quickApps
        )
    }

    private fun runRootRead(cmd: String): String {
        return runCatching {
            val process = ProcessBuilder("su", "-c", cmd)
                .redirectErrorStream(true)
                .start()
            if (!process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return ""
            }
            process.inputStream.bufferedReader().use { it.readText() }.trim()
        }.getOrDefault("")
    }

    private fun readCpuLoad(): Int {
        val direct = sysfs.cpuPercent()?.roundToInt()?.coerceIn(0, 100)
        if (direct != null) {
            lastCpuPercent = direct
            return direct
        }
        val line = runRootRead("head -n 1 /proc/stat 2>/dev/null")
        if (!line.startsWith("cpu ")) return lastCpuPercent
        val parts = line.split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
        if (parts.size < 5) return lastCpuPercent
        val idle = parts[3] + parts[4]
        val total = parts.sum()
        val dTotal = total - prevRootCpuTotal
        val dIdle = idle - prevRootCpuIdle
        prevRootCpuTotal = total
        prevRootCpuIdle = idle
        if (dTotal <= 0L || dTotal == total) return lastCpuPercent
        val pct = (((dTotal - dIdle).toDouble() / dTotal.toDouble()) * 100.0)
            .roundToInt()
            .coerceIn(0, 100)
        lastCpuPercent = pct
        return pct
    }

    private fun readGpuLoad(): Int {
        val direct = sysfs.gpuPercent()?.roundToInt()?.coerceIn(0, 100)
        if (direct != null) {
            lastGpuPercent = direct
            return direct
        }
        val cmd = "for p in " +
            "/sys/module/ged/parameters/gpu_loading " +
            "/sys/kernel/ged/hal/gpu_utilization " +
            "/sys/class/misc/mali0/device/utilization " +
            "/sys/class/devfreq/gpufreq/gpu_busy_percentage " +
            "/sys/kernel/gpu/gpu_busy; do " +
            "[ -r \"\$p\" ] && { cat \"\$p\"; break; }; done; " +
            "[ -r /sys/class/kgsl/kgsl-3d0/gpubusy ] && cat /sys/class/kgsl/kgsl-3d0/gpubusy"
        val raw = runRootRead(cmd)
        if (raw.isEmpty()) return lastGpuPercent
        val tokens = raw.split(Regex("\\s+"))
        val pct = if (tokens.size >= 2) {
            val busy = tokens[0].toFloatOrNull()
            val total = tokens[1].toFloatOrNull()
            if (busy != null && total != null && total > 0f) {
                (busy / total * 100f).roundToInt().coerceIn(0, 100)
            } else {
                tokens[0].replace("%", "").toFloatOrNull()?.roundToInt()?.coerceIn(0, 100)
            }
        } else {
            tokens[0].replace("%", "").toFloatOrNull()?.roundToInt()?.coerceIn(0, 100)
        }
        if (pct != null) {
            lastGpuPercent = pct
        }
        return lastGpuPercent
    }

    private fun isForegroundGameActive(nowMs: Long): Boolean {
        val usm = getSystemService(UsageStatsManager::class.java) ?: return false
        val lookback = if (lastForegroundPkg == null) 120_000L else 10_000L
        val events = runCatching { usm.queryEvents(nowMs - lookback, nowMs) }.getOrNull()
        if (events != null) {
            val ev = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(ev)
                if (ev.eventType == UsageEvents.Event.ACTIVITY_RESUMED && ev.timeStamp >= lastForegroundTs) {
                    val pkg = ev.packageName
                    if (!pkg.isNullOrBlank() && pkg != "android" && pkg != "com.android.systemui") {
                        lastForegroundTs = ev.timeStamp
                        lastForegroundPkg = pkg
                    }
                }
            }
        }
        val fg = lastForegroundPkg ?: return false
        if (fg == packageName) return false
        val configured = ConfigRepository.cachedAppConfigs?.get(fg)
        if (configured != null && !configured.disabled && (configured.saklarUtama || configured.custom)) {
            return true
        }
        return runCatching {
            val info = packageManager.getApplicationInfo(fg, 0)
            (info.flags and ApplicationInfo.FLAG_IS_GAME) != 0 ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && info.category == ApplicationInfo.CATEGORY_GAME)
        }.getOrDefault(false)
    }

    private fun isKeyguardLocked(): Boolean {
        val keyguard = getSystemService(KeyguardManager::class.java) ?: return false
        return runCatching { keyguard.isKeyguardLocked }.getOrDefault(false)
    }

    private fun attachOverlay() {
        if (isAttached) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) return
        uiState.value = uiState.value.copy(
            side = activeConfig.side,
            opacity = activeConfig.opacity
        )
        recomposerJob?.cancel()
        runCatching { recomposer?.cancel() }
        val uiContext = AndroidUiDispatcher.CurrentThread
        val activeRecomposer = Recomposer(uiContext)
        recomposer = activeRecomposer
        recomposerJob = serviceScope.launch(uiContext) {
            activeRecomposer.runRecomposeAndApplyChanges()
        }

        val view = ComposeView(this).apply {
            compositionContext = activeRecomposer
            setViewTreeLifecycleOwner(this@GameDeckService)
            setViewTreeViewModelStoreOwner(this@GameDeckService)
            setViewTreeSavedStateRegistryOwner(this@GameDeckService)
            setContent {
                LuminaTheme {
                    GameDeckRoot(
                        state = uiState,
                        isWindowExpanded = isWindowExpandedState.value,
                        isHandlePressed = isHandlePressedState.value,
                        onCollapse = { setExpanded(false) },
                        onProfileSelected = ::selectProfileOverride,
                        onAutoModeToggle = ::toggleProfileAutoMode,
                        onRefreshRateSelected = ::selectRefreshRate,
                        onToggleDnd = ::toggleDnd,
                        onQuickAppClick = ::launchFreeformApp,
                        onOpenManager = ::openManager,
                        onCollapseSettled = ::handleCollapseSettled
                    )
                }
            }
        }

        val container = object : FrameLayout(this) {
            private var downRawX = 0f
            private var downRawY = 0f
            private var startEdgeY = 0
            private var gestureMode = 0 // 0 = undecided, 1 = expand, 2 = vertical drag

            override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
                if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                    if (isExpanded && !isCollapsing) {
                        setExpanded(false)
                    }
                    return true
                }

                if (!isWindowExpandedState.value && !isCollapsing) {
                    val density = resources.displayMetrics.density
                    val touchSlopPx = 8f * density
                    val swipeThresholdPx = 12f * density

                    when (ev.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            downRawX = ev.rawX
                            downRawY = ev.rawY
                            startEdgeY = edgeYOffsetPx
                            gestureMode = 0
                            isHandlePressedState.value = true
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = ev.rawX - downRawX
                            val dy = ev.rawY - downRawY
                            val inwardDx = if (activeConfig.side == GameDeckSide.LEFT) dx else -dx

                            if (gestureMode == 0) {
                                if (inwardDx >= swipeThresholdPx && inwardDx >= abs(dy) * 0.55f) {
                                    gestureMode = 1
                                    isHandlePressedState.value = false
                                    setExpanded(true)
                                    return true
                                } else if (abs(dy) >= touchSlopPx && abs(dy) > abs(dx) * 1.15f) {
                                    gestureMode = 2
                                }
                            }

                            if (gestureMode == 2) {
                                val maxOffset = collapsedMaxOffsetPx()
                                val nextY = (startEdgeY + dy.roundToInt()).coerceIn(-maxOffset, maxOffset)
                                if (nextY != edgeYOffsetPx) {
                                    edgeYOffsetPx = nextY
                                    activeConfig = activeConfig.copy(edgeYOffsetPx = nextY)
                                    this@GameDeckService.layoutParams?.let { lp ->
                                        lp.y = nextY
                                        runCatching { windowManager.updateViewLayout(this, lp) }
                                    }
                                }
                            }
                            return true
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            isHandlePressedState.value = false
                            if (gestureMode == 2) {
                                commitEdgeHandlePosition()
                            } else if (gestureMode == 0 && ev.actionMasked == MotionEvent.ACTION_UP) {
                                if (abs(ev.rawX - downRawX) < touchSlopPx &&
                                    abs(ev.rawY - downRawY) < touchSlopPx
                                ) {
                                    setExpanded(true)
                                }
                            }
                            gestureMode = 0
                            return true
                        }
                    }
                }
                return super.dispatchTouchEvent(ev)
            }
        }.apply {
            setViewTreeLifecycleOwner(this@GameDeckService)
            setViewTreeViewModelStoreOwner(this@GameDeckService)
            setViewTreeSavedStateRegistryOwner(this@GameDeckService)
            addView(
                view,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }

        val params = createLayoutParams()
        configureLayoutParams(params, container, expandedWindow = false)
        runCatching {
            windowManager.addView(container, params)
            hostView = container
            composeView = view
            layoutParams = params
            isAttached = true
            isCollapsing = false
            windowKey = "${activeConfig.side.key}|false"
            container.post { updateGestureExclusion() }
        }
    }

    private fun detachOverlay() {
        if (!isAttached) return
        transitionJob?.cancel()
        recomposerJob?.cancel()
        runCatching { recomposer?.cancel() }
        recomposer = null
        val host = hostView
        if (host != null) {
            runCatching { windowManager.removeViewImmediate(host) }
        }
        hostView = null
        composeView = null
        layoutParams = null
        isAttached = false
        isExpanded = false
        isCollapsing = false
        isWindowExpandedState.value = false
        isHandlePressedState.value = false
        uiState.value = uiState.value.copy(expanded = false)
    }

    private fun setExpanded(expand: Boolean) {
        if (isExpanded == expand) return
        if (expand && (isCollapsing || System.currentTimeMillis() < reopenBlockedUntilMs)) return
        transitionJob?.cancel()
        isExpanded = expand
        if (expand) {
            isCollapsing = false
            isWindowExpandedState.value = true
            updateWindowLayout()
            lastMetricsMs = 0L
            transitionJob = serviceScope.launch {
                delay(16L)
                if (isExpanded) {
                    uiState.value = uiState.value.copy(expanded = true)
                }
            }
        } else {
            isCollapsing = true
            reopenBlockedUntilMs = System.currentTimeMillis() + REOPEN_COOLDOWN_MS
            val wasVisible = uiState.value.expanded
            uiState.value = uiState.value.copy(expanded = false)
            if (!wasVisible || !isAttached || !isWindowExpandedState.value) {
                finishCollapse()
            }
        }
    }

    private fun handleCollapseSettled() {
        finishCollapse()
    }

    private fun finishCollapse() {
        isCollapsing = false
        if (isExpanded) return
        if (isWindowExpandedState.value) {
            isWindowExpandedState.value = false
            uiState.value = uiState.value.copy(expanded = false)
            updateWindowLayout()
        }
    }

    private fun updateWindowLayout() {
        if (!isAttached) return
        val host = hostView ?: return
        val params = layoutParams ?: return
        configureLayoutParams(params, host, isWindowExpandedState.value)
        windowKey = "${activeConfig.side.key}|${isWindowExpandedState.value}"
        runCatching { windowManager.updateViewLayout(host, params) }
        host.post { updateGestureExclusion() }
    }

    private fun collapsedMaxOffsetPx(): Int {
        val displayMetrics = resources.displayMetrics
        val density = displayMetrics.density
        val margin = (EDGE_MARGIN_DP * density).roundToInt()
        val halfScreen = displayMetrics.heightPixels / 2f
        return (halfScreen - (COLLAPSED_HEIGHT_DP * density / 2f) - margin)
            .roundToInt()
            .coerceAtLeast(0)
    }

    private fun configureLayoutParams(
        params: WindowManager.LayoutParams,
        host: FrameLayout,
        expandedWindow: Boolean
    ) {
        val displayMetrics = resources.displayMetrics
        val density = displayMetrics.density
        val isLeft = activeConfig.side == GameDeckSide.LEFT
        val margin = (EDGE_MARGIN_DP * density).roundToInt()
        val halfScreen = displayMetrics.heightPixels / 2f
        val panelHeightPx = (displayMetrics.heightPixels * EXPANDED_HEIGHT_FRACTION)
            .coerceIn(288f * density, EXPANDED_MAX_HEIGHT_DP * density)
            .coerceAtMost((displayMetrics.heightPixels - 28f * density).coerceAtLeast(260f * density))
        val panelWidthPx = (DECK_TOTAL_WIDTH_DP * density)
            .coerceAtMost((displayMetrics.widthPixels - 24f * density).coerceAtLeast(320f * density))
        val maxOffset = if (expandedWindow) {
            (halfScreen - (panelHeightPx / 2f) - (8f * density))
                .roundToInt()
                .coerceAtLeast(0)
        } else {
            collapsedMaxOffsetPx()
        }
        params.gravity = Gravity.CENTER_VERTICAL or (if (isLeft) Gravity.LEFT else Gravity.RIGHT)
        params.y = edgeYOffsetPx.coerceIn(-maxOffset, maxOffset)
        if (expandedWindow) {
            params.x = (12f * density).roundToInt()
            params.width = panelWidthPx.roundToInt()
            params.height = panelHeightPx.roundToInt()
        } else {
            params.x = 0
            params.width = (COLLAPSED_WIDTH_DP * density).roundToInt()
            params.height = (COLLAPSED_HEIGHT_DP * density).roundToInt()
        }
        host.requestLayout()
    }

    private fun commitEdgeHandlePosition() {
        activeConfig = activeConfig.copy(edgeYOffsetPx = edgeYOffsetPx)
        GameDeckPrefs.save(this, activeConfig)
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
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.LEFT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun updateGestureExclusion() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val host = hostView ?: return
        if (host.width <= 0 || host.height <= 0) return
        host.systemGestureExclusionRects = if (isWindowExpandedState.value) {
            mutableListOf()
        } else {
            mutableListOf(Rect(0, 0, host.width, host.height))
        }
    }

    private fun selectProfileOverride(name: String?) {
        if (profileAutoMode || name == null) {
            Toast.makeText(
                this@GameDeckService,
                localizedText(
                    "Matikan Auto Mode dulu untuk memilih profil",
                    "Turn off Auto Mode to choose a profile"
                ),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val label = when (name) {
            "eco" -> "Eco"
            "performance" -> "Performance"
            "performance_lite" -> "Performance Lite"
            else -> "Balanced"
        }
        pendingOverrideLabel = label
        pendingOverrideUntilMs = System.currentTimeMillis() + 4_000L
        uiState.value = uiState.value.copy(profileOverride = label)

        serviceScope.launch {
            val ok = withContext(Dispatchers.IO) {
                DaemonManager.setProfileOverride(name)
            }
            if (!ok) {
                pendingOverrideLabel = null
                Toast.makeText(
                    this@GameDeckService,
                    localizedText("Gagal menerapkan profil", "Failed to apply profile"),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun toggleProfileAutoMode() {
        val nextAuto = !profileAutoMode
        profileAutoMode = nextAuto
        if (!nextAuto) {
            uiState.value = uiState.value.copy(
                profileAutoMode = false,
                profileOverride = "Auto"
            )
            Toast.makeText(
                this@GameDeckService,
                localizedText("Mode manual — pilih profil", "Manual mode — select a profile"),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        pendingOverrideLabel = "Auto"
        pendingOverrideUntilMs = System.currentTimeMillis() + 4_000L
        uiState.value = uiState.value.copy(
            profileAutoMode = true,
            profileOverride = "Auto"
        )
        serviceScope.launch {
            val ok = withContext(Dispatchers.IO) {
                DaemonManager.clearProfileOverride()
            }
            if (!ok) {
                Toast.makeText(
                    this@GameDeckService,
                    localizedText("Gagal kembali ke Auto Mode", "Failed to restore Auto Mode"),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun supportedRefreshRates(): List<Int> {
        val display = currentDisplay() ?: return emptyList()
        return runCatching {
            display.supportedModes
                .map { (it.refreshRate + 0.5f).toInt() }
                .filter { it >= 30 }
                .distinct()
                .sorted()
        }.getOrDefault(emptyList())
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

    private fun readDisplayHz(): Int {
        return currentDisplay()?.refreshRate?.roundToInt() ?: 60
    }

    private fun selectRefreshRate(hz: Int?) {
        GameDeckPrefs.setRefreshRateHint(this, hz)
        uiState.value = uiState.value.copy(
            selectedRrHz = hz,
            targetHz = hz ?: readDisplayHz()
        )
        serviceScope.launch(Dispatchers.IO) {
            runCatching {
                if (hz == null) {
                    ConfigRepository.setRefreshRate(null)
                } else {
                    var modeId = ConfigRepository.cachedTweaksState
                        ?.rrModes
                        ?.firstOrNull { it.hz == hz }
                        ?.id
                    if (modeId == null) {
                        modeId = currentDisplay()?.supportedModes
                            ?.filter { (it.refreshRate + 0.5f).toInt() == hz }
                            ?.minByOrNull { it.modeId }
                            ?.modeId
                    }
                    ConfigRepository.setRefreshRate(modeId)
                }
            }
        }
    }

    private fun toggleDnd() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (!manager.isNotificationPolicyAccessGranted) {
            Toast.makeText(
                this,
                localizedText(
                    "Izinkan akses Jangan Ganggu terlebih dahulu",
                    "Allow Do Not Disturb access first"
                ),
                Toast.LENGTH_SHORT
            ).show()
            runCatching {
                startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            setExpanded(false)
            return
        }
        val enable = manager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL
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

    private fun launchFreeformApp(packageName: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return
        launchIntent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
        )
        setExpanded(false)

        serviceScope.launch {
            if (!freeformUnlocked) {
                freeformUnlocked = true
                withContext(Dispatchers.IO) {
                    runCatching {
                        RootUtil.runAsRoot(
                            "settings put global enable_freeform_support 1 2>/dev/null; " +
                                "settings put global force_resizable_activities 1 2>/dev/null"
                        )
                    }
                }
            }
            val dm = resources.displayMetrics
            val boxW = (320f * dm.density).roundToInt().coerceAtMost((dm.widthPixels * 0.65f).roundToInt())
            val boxH = (520f * dm.density).roundToInt().coerceAtMost((dm.heightPixels * 0.85f).roundToInt())
            val left = ((dm.widthPixels - boxW) / 2).coerceAtLeast(16)
            val top = ((dm.heightPixels - boxH) / 2).coerceAtLeast(16)
            val bounds = Rect(left, top, left + boxW, top + boxH)

            val launched = runCatching {
                val options = ActivityOptions.makeBasic().apply {
                    setLaunchBounds(bounds)
                }
                runCatching {
                    val m = ActivityOptions::class.java.getMethod(
                        "setLaunchWindowingMode",
                        Int::class.javaPrimitiveType
                    )
                    m.invoke(options, 5)
                }
                startActivity(launchIntent, options.toBundle())
                true
            }.getOrDefault(false)

            if (!launched) {
                runCatching { startActivity(launchIntent) }
            }
        }
    }

    private fun openManager() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        runCatching { startActivity(intent) }
        setExpanded(false)
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
        val stopIntent = PendingIntent.getService(
            this,
            12,
            Intent(this, GameDeckService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val status = if (visible) {
            localizedText("Lumina Game Deck aktif", "Lumina Game Deck active")
        } else {
            localizedText("Menunggu sesi game", "Waiting for game session")
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Lumina Game Deck")
            .setContentText(status)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, localizedText("Matikan", "Stop"), stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Lumina Game Deck",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun localizedText(id: String, en: String): String {
        val lang = Locale.getDefault().language.lowercase(Locale.ROOT)
        return if (lang == "in" || lang == "id") id else en
    }
}
