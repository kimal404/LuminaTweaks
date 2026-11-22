package com.luminatweaks.gamedeck

import android.app.ActivityOptions
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
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Toast
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.pm.ApplicationInfo
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
    private val sysfs = SysfsTelemetry()

    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var telemetryJob: Job? = null
    private var transitionJob: Job? = null
    private var profileJob: Job? = null
    private var recomposer: Recomposer? = null
    private var recomposerJob: Job? = null
    private var lastForegroundPkg: String? = null
    private var lastForegroundTs: Long = 0L

    private var activeConfig = GameDeckConfig()
    private var isAttached = false
    private var isExpanded = false
    private var manualOverride: Boolean? = null
    private var wasGameActive = false
    private var lastGameSeenMs = 0L
    private var lastMetricsMs = 0L
    private var windowKey = ""
    private var freeformUnlocked = false

    private var manualAutoGraceUntilMs = 0L
    private var manualProfileGraceUntilMs = 0L
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
        startForegroundCompat()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        activeConfig = GameDeckPrefs.load(this)
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
                GameDeckPrefs.save(this, activeConfig.copy(enabled = false))
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
                val config = GameDeckPrefs.load(this@GameDeckService)
                activeConfig = config
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
                    val afterMs = System.currentTimeMillis()
                    val resolvedAuto = if (afterMs < manualAutoGraceUntilMs) {
                        uiState.value.autoProfile
                    } else {
                        snapshot.autoProfile
                    }
                    val resolvedProfile = if (afterMs < manualProfileGraceUntilMs) {
                        uiState.value.profile
                    } else {
                        snapshot.profile
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
                        profile = resolvedProfile,
                        autoProfile = resolvedAuto,
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
        val autoProfile: Boolean,
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
        val autoProp = DeviceUtil.readSystemProperty("persist.lumina.autoprofile")
            .let { it == null || (!it.equals("0", true) && !it.equals("false", true)) }

        return DeckSnapshot(
            fps = fps,
            frametimeMs = frametime,
            targetHz = hz,
            cpuPercent = cpu,
            gpuPercent = gpu,
            tempLabel = DeviceUtil.getBatteryTemperature(this),
            batteryPercent = battery,
            profile = profileProp,
            autoProfile = autoProp,
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
            setOnTouchListener { _, event ->
                if (isExpanded && event.action == MotionEvent.ACTION_OUTSIDE) {
                    setExpanded(false)
                    true
                } else {
                    false
                }
            }
            setContent {
                LuminaTheme {
                    GameDeckRoot(
                        state = uiState,
                        isWindowExpanded = isWindowExpandedState.value,
                        onExpand = { setExpanded(true) },
                        onCollapse = { setExpanded(false) },
                        onToggleAutoProfile = ::toggleAutoProfile,
                        onProfileSelected = ::selectProfile,
                        onRefreshRateSelected = ::selectRefreshRate,
                        onToggleDnd = ::toggleDnd,
                        onQuickAppClick = ::launchFreeformApp,
                        onOpenManager = ::openManager
                    )
                }
            }
        }
        val params = createLayoutParams()
        configureLayoutParams(params, view, expandedWindow = false)
        runCatching {
            windowManager.addView(view, params)
            composeView = view
            layoutParams = params
            isAttached = true
            windowKey = "${activeConfig.side.key}|false"
            view.post { updateGestureExclusion() }
        }
    }

    private fun detachOverlay() {
        if (!isAttached) return
        transitionJob?.cancel()
        recomposerJob?.cancel()
        runCatching { recomposer?.cancel() }
        recomposer = null
        val view = composeView
        if (view != null) {
            runCatching { windowManager.removeViewImmediate(view) }
        }
        composeView = null
        layoutParams = null
        isAttached = false
        isExpanded = false
        isWindowExpandedState.value = false
        uiState.value = uiState.value.copy(expanded = false)
    }

    private fun setExpanded(expand: Boolean) {
        if (isExpanded == expand) return
        transitionJob?.cancel()
        isExpanded = expand
        if (expand) {
            isWindowExpandedState.value = true
            updateWindowLayout()
            lastMetricsMs = 0L
            transitionJob = serviceScope.launch {
                delay(16L)
                uiState.value = uiState.value.copy(expanded = true)
            }
        } else {
            uiState.value = uiState.value.copy(expanded = false)
            transitionJob = serviceScope.launch {
                delay(185L)
                if (!isExpanded) {
                    isWindowExpandedState.value = false
                    updateWindowLayout()
                }
            }
        }
    }

    private fun updateWindowLayout() {
        if (!isAttached) return
        val view = composeView ?: return
        val params = layoutParams ?: return
        configureLayoutParams(params, view, isWindowExpandedState.value)
        windowKey = "${activeConfig.side.key}|${isWindowExpandedState.value}"
        runCatching { windowManager.updateViewLayout(view, params) }
        view.post { updateGestureExclusion() }
    }

    private fun configureLayoutParams(
        params: WindowManager.LayoutParams,
        view: ComposeView,
        expandedWindow: Boolean
    ) {
        val density = resources.displayMetrics.density
        val isLeft = activeConfig.side == GameDeckSide.LEFT
        if (expandedWindow) {
            params.gravity = Gravity.CENTER_VERTICAL or (if (isLeft) Gravity.LEFT else Gravity.RIGHT)
            params.x = (12f * density).roundToInt()
            params.y = 0
            params.width = WindowManager.LayoutParams.WRAP_CONTENT
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
        } else {
            params.gravity = Gravity.CENTER_VERTICAL or (if (isLeft) Gravity.LEFT else Gravity.RIGHT)
            params.x = 0
            params.y = 0
            params.width = (22f * density).roundToInt()
            params.height = (56f * density).roundToInt()
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
        val view = composeView ?: return
        if (view.width <= 0 || view.height <= 0) return
        view.systemGestureExclusionRects = if (isWindowExpandedState.value) {
            mutableListOf()
        } else {
            mutableListOf(Rect(0, 0, view.width, view.height))
        }
    }

    private fun toggleAutoProfile() {
        val nextAuto = !uiState.value.autoProfile
        manualAutoGraceUntilMs = System.currentTimeMillis() + 5_000L
        uiState.value = uiState.value.copy(autoProfile = nextAuto)
        serviceScope.launch(Dispatchers.IO) {
            val synced = runCatching { DaemonManager.setAutoProfile(nextAuto) }.getOrDefault(false)
            if (!synced) {
                runCatching { ConfigRepository.setAutoProfile(nextAuto) }
            }
        }
    }

    private fun selectProfile(name: String) {
        profileJob?.cancel()
        val now = System.currentTimeMillis()
        manualAutoGraceUntilMs = now + 5_000L
        manualProfileGraceUntilMs = now + 5_000L
        uiState.value = uiState.value.copy(profile = name, autoProfile = false)

        profileJob = serviceScope.launch {
            val ok = withContext(Dispatchers.IO) {
                val synced = runCatching { DaemonManager.setAutoProfile(false) }.getOrDefault(false)
                if (!synced) {
                    runCatching { ConfigRepository.setAutoProfile(false) }
                }
                DaemonManager.setProfile(name)
            }
            if (!ok) {
                Toast.makeText(
                    this@GameDeckService,
                    localizedText("Daemon Lumina tidak aktif", "Lumina daemon is offline"),
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
