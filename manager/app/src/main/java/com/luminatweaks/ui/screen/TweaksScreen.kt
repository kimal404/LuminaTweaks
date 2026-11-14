// Copyright (C) 2026 kimal
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package com.luminatweaks.ui.screen

import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luminatweaks.R
import com.luminatweaks.data.ConfigRepository
import com.luminatweaks.data.DisplayRrMode
import com.luminatweaks.service.FloatingFpsService
import com.luminatweaks.ui.theme.LocalCornerRadius
import com.luminatweaks.ui.theme.LocalFloatingNavBar
import com.luminatweaks.util.DeviceUtil
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------
// Renderer catalog
// ---------------------------------------------------------------------------

data class RendererItem(
    val id: String,
    val labelRes: Int
)

val RENDERER_OPTIONS = listOf(
    RendererItem("default", R.string.tweaks_renderer_system_default),
    RendererItem("skiavk", R.string.opt_default),
    RendererItem("skiavkthreaded", R.string.opt_default),
    RendererItem("skiagl", R.string.opt_default),
    RendererItem("skiaglthreaded", R.string.opt_default),
    RendererItem("opengl", R.string.opt_default),
    RendererItem("openglthreaded", R.string.opt_default),
    RendererItem("vulkan", R.string.opt_default)
)

fun getRendererDisplayLabel(id: String, defaultLabel: String): String {
    return when (id.trim().lowercase()) {
        "default" -> defaultLabel
        "skiavk" -> "Skia Vulkan"
        "skiavkthreaded" -> "Skia Vulkan (Threaded)"
        "skiagl" -> "Skia OpenGL"
        "skiaglthreaded" -> "Skia OpenGL (Threaded)"
        "opengl" -> "OpenGL ES"
        "openglthreaded" -> "OpenGL ES (Threaded)"
        "vulkan" -> "Vulkan"
        else -> id
    }
}

fun getRendererShortLabel(id: String, defaultLabel: String): String {
    return when (id.trim().lowercase()) {
        "default" -> defaultLabel
        "skiavk" -> "Skia Vulkan"
        "skiavkthreaded" -> "Vulkan MT"
        "skiagl" -> "Skia GL"
        "skiaglthreaded" -> "Skia GL MT"
        "opengl" -> "OpenGL"
        "openglthreaded" -> "OpenGL MT"
        "vulkan" -> "Vulkan"
        else -> id
    }
}

private data class TweakOption<T>(
    val value: T,
    val label: String
)

// ---------------------------------------------------------------------------
// Floating FPS Monitor configuration state (ready for live service binding)
// ---------------------------------------------------------------------------

enum class FpsOverlayStyle(val key: String) {
    PILL("pill"),
    GRAPH("graph"),
    MINIMAL("minimal");

    companion object {
        fun fromKey(raw: String?): FpsOverlayStyle =
            entries.firstOrNull { it.key.equals(raw, ignoreCase = true) } ?: PILL
    }
}

enum class FpsOverlayCorner(val key: String, val alignment: Alignment) {
    TOP_LEFT("top_left", Alignment.TopStart),
    TOP_RIGHT("top_right", Alignment.TopEnd),
    BOTTOM_LEFT("bottom_left", Alignment.BottomStart),
    BOTTOM_RIGHT("bottom_right", Alignment.BottomEnd);

    companion object {
        fun fromKey(raw: String?): FpsOverlayCorner =
            entries.firstOrNull { it.key.equals(raw, ignoreCase = true) } ?: TOP_RIGHT
    }
}

data class FloatingFpsConfig(
    val enabled: Boolean = false,
    val style: FpsOverlayStyle = FpsOverlayStyle.PILL,
    val corner: FpsOverlayCorner = FpsOverlayCorner.TOP_RIGHT,
    val autoGame: Boolean = true,
    val touchLock: Boolean = false,
    val showFrametime: Boolean = true,
    val showTemp: Boolean = false,
    val showProfile: Boolean = false,
    val scale: Float = 1.0f,
    val bgAlpha: Float = 0.88f,
    val intervalMs: Int = 500
)

object FloatingFpsPrefs {
    private const val PREF_NAME = "floating_fps_prefs"

    fun load(context: Context): FloatingFpsConfig {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return FloatingFpsConfig(
            enabled = sp.getBoolean("enabled", false),
            style = FpsOverlayStyle.fromKey(sp.getString("style", FpsOverlayStyle.PILL.key)),
            corner = FpsOverlayCorner.fromKey(sp.getString("corner", FpsOverlayCorner.TOP_RIGHT.key)),
            autoGame = sp.getBoolean("auto_game", true),
            touchLock = sp.getBoolean("touch_lock", false),
            showFrametime = sp.getBoolean("show_frametime", true),
            showTemp = sp.getBoolean("show_temp", false),
            showProfile = sp.getBoolean("show_profile", false),
            scale = sp.getFloat("scale", 1.0f).coerceIn(0.8f, 1.3f),
            bgAlpha = sp.getFloat("bg_alpha", 0.88f).coerceIn(0.25f, 1.0f),
            intervalMs = sp.getInt("interval_ms", 500).takeIf { it in listOf(250, 500, 1000) } ?: 500
        )
    }

    fun save(context: Context, config: FloatingFpsConfig) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", config.enabled)
            .putString("style", config.style.key)
            .putString("corner", config.corner.key)
            .putBoolean("auto_game", config.autoGame)
            .putBoolean("touch_lock", config.touchLock)
            .putBoolean("show_frametime", config.showFrametime)
            .putBoolean("show_temp", config.showTemp)
            .putBoolean("show_profile", config.showProfile)
            .putFloat("scale", config.scale)
            .putFloat("bg_alpha", config.bgAlpha)
            .putInt("interval_ms", config.intervalMs)
            .apply()
    }
}

private data class SimulatedFpsTelemetry(
    val fps: Int = 120,
    val targetHz: Int = 120,
    val frametimeMs: Float = 8.3f,
    val tempLabel: String = "37.2°C",
    val profileLabel: String = "Balanced",
    val history: List<Float> = listOf(8.3f, 8.4f, 8.2f, 8.3f, 8.5f, 8.3f, 8.2f, 8.3f, 8.4f, 8.3f, 8.2f, 8.3f)
)

@Composable
private fun rememberSimulatedFpsTelemetry(
    targetHz: Int,
    intervalMs: Int,
    active: Boolean = true
): SimulatedFpsTelemetry {
    val safeTarget = targetHz.coerceAtLeast(60)
    val baseFrameMs = 1000f / safeTarget.toFloat()
    var tick by remember { mutableIntStateOf(0) }
    var history by remember(safeTarget) {
        mutableStateOf(List(16) { baseFrameMs })
    }

    LaunchedEffect(active, intervalMs, safeTarget) {
        if (!active) return@LaunchedEffect
        val pattern = intArrayOf(0, 0, -1, 0, 0, -2, 0, 0, -1, 0, 0, 0)
        while (isActive) {
            delay(intervalMs.toLong())
            val delta = pattern[tick % pattern.size]
            val currentFps = (safeTarget + delta).coerceAtLeast(1)
            val currentMs = (1000f / currentFps.toFloat())
            history = (history.drop(1) + currentMs)
            tick += 1
        }
    }

    val latestMs = history.lastOrNull() ?: baseFrameMs
    val latestFps = (1000f / latestMs).roundToInt().coerceIn(1, safeTarget)
    return SimulatedFpsTelemetry(
        fps = latestFps,
        targetHz = safeTarget,
        frametimeMs = latestMs,
        history = history
    )
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

@Composable
private fun localizedLabel(id: String, en: String): String {
    val isIndonesian = remember {
        val lang = Locale.getDefault().language.lowercase(Locale.ROOT)
        lang == "in" || lang == "id"
    }
    return if (isIndonesian) id else en
}

@Suppress("DEPRECATION")
private fun currentDisplayHz(context: Context, windowManager: WindowManager?): Int? {
    val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        runCatching { context.display }.getOrNull() ?: windowManager?.defaultDisplay
    } else {
        windowManager?.defaultDisplay
    }
    return display?.refreshRate?.roundToInt()
}

private suspend fun applyTweakChange(
    snackbarHostState: SnackbarHostState,
    errorMessage: String,
    revert: () -> Unit,
    change: suspend () -> Any?
) {
    val result = runCatching { change() }
    val succeeded = result.isSuccess && ((result.getOrNull() as? Boolean) ?: true)
    if (!succeeded) {
        revert()
        snackbarHostState.showSnackbar(errorMessage)
    }
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

private suspend fun awaitPagerSettled(pagerState: PagerState?) {
    pagerState ?: return
    snapshotFlow { pagerState.isScrollInProgress }.first { !it }
}

@Composable
fun TweaksScreen(pagerState: PagerState? = null) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val cached = ConfigRepository.cachedTweaksState

    var isGlobalLiteMode by remember { mutableStateOf(cached?.isGlobalLiteMode ?: false) }
    var isGlobalTextureEnabled by remember { mutableStateOf(cached?.isGlobalTextureEnabled ?: false) }
    var rrModes by remember { mutableStateOf<List<DisplayRrMode>>(cached?.rrModes ?: emptyList()) }
    var currentRrId by remember { mutableStateOf<Int?>(cached?.currentRrId) }
    var liveRefreshRate by remember { mutableIntStateOf(60) }
    var currentRendererId by remember { mutableStateOf(cached?.currentRendererId ?: "default") }
    var currentIoScheduler by remember { mutableStateOf(cached?.currentIoScheduler ?: "mq-deadline") }
    var availableIoSchedulers by remember { mutableStateOf(emptyList<String>()) }

    var fpsConfig by remember { mutableStateOf(FloatingFpsPrefs.load(context)) }
    val updateFpsConfig: (FloatingFpsConfig) -> Unit = { updated ->
        val wasEnabled = fpsConfig.enabled
        fpsConfig = updated
        FloatingFpsPrefs.save(context, updated)
        if (updated.enabled && !wasEnabled) {
            coroutineScope.launch {
                val granted = FloatingFpsService.ensureOverlayPermission(context)
                if (granted) {
                    FloatingFpsService.sync(context, updated)
                } else {
                    val reverted = updated.copy(enabled = false)
                    fpsConfig = reverted
                    FloatingFpsPrefs.save(context, reverted)
                }
            }
        } else {
            FloatingFpsService.sync(context, updated)
        }
    }

    LaunchedEffect(Unit) {
        if (fpsConfig.enabled) {
            val granted = FloatingFpsService.ensureOverlayPermission(context)
            if (granted) {
                FloatingFpsService.sync(context, fpsConfig)
            }
        }
    }

    var showRefreshRateSheet by rememberSaveable { mutableStateOf(false) }
    var showRenderSheet by rememberSaveable { mutableStateOf(false) }
    var showIoSchedulerSheet by rememberSaveable { mutableStateOf(false) }
    var inFloatingFpsSubpage by rememberSaveable { mutableStateOf(false) }
    var inMemoryZramSubpage by rememberSaveable { mutableStateOf(false) }
    var inHardwareDiagnosticSubpage by rememberSaveable { mutableStateOf(false) }
    var inUtilitySubpage by rememberSaveable { mutableStateOf(false) }

    val defaultDynamicLabel = stringResource(R.string.tweaks_rr_default_dynamic)
    val defaultRendererLabel = stringResource(R.string.tweaks_renderer_system_default)
    val applyFailedText = localizedLabel(
        "Gagal menerapkan perubahan",
        "Failed to apply change"
    )

    LaunchedEffect(Unit) {
        awaitPagerSettled(pagerState)
        val state = ConfigRepository.getTweaksState(forceRefresh = true)
        isGlobalLiteMode = state.isGlobalLiteMode
        isGlobalTextureEnabled = state.isGlobalTextureEnabled
        rrModes = state.rrModes
        currentRrId = state.currentRrId
        currentRendererId = state.currentRendererId
        currentIoScheduler = state.currentIoScheduler
        availableIoSchedulers = withContext(Dispatchers.IO) {
            DeviceUtil.getAvailableIoSchedulers()
        }
    }

    LaunchedEffect(Unit) {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        while (isActive) {
            if (pagerState?.isScrollInProgress != true) {
                val hz = currentDisplayHz(context, windowManager)
                if (hz != null && hz > 0) {
                    liveRefreshRate = hz
                }
            }
            delay(1200L)
        }
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            delay(3000L)
            if (pagerState?.isScrollInProgress == true) continue
            val fresh = withContext(Dispatchers.IO) {
                ConfigRepository.getTweaksState(forceRefresh = true)
            }
            isGlobalTextureEnabled = fresh.isGlobalTextureEnabled
        }
    }

    val currentRrText = remember(liveRefreshRate, currentRrId, rrModes, defaultDynamicLabel) {
        val targetMode = rrModes.find { it.id == currentRrId }
        when {
            targetMode != null && targetMode.hz != liveRefreshRate ->
                "${liveRefreshRate} Hz (${targetMode.hz} Hz)"
            currentRrId == null ->
                "${liveRefreshRate} Hz · $defaultDynamicLabel"
            else ->
                "${liveRefreshRate} Hz"
        }
    }

    val currentRendererShortText = remember(currentRendererId, defaultRendererLabel) {
        getRendererShortLabel(currentRendererId, defaultRendererLabel)
    }

    val effectiveRendererText = if (isGlobalTextureEnabled) {
        stringResource(R.string.tweaks_renderer_locked_note)
    } else {
        currentRendererShortText
    }

    val rendererOptions = remember(defaultRendererLabel) {
        RENDERER_OPTIONS.map {
            TweakOption(it.id, getRendererDisplayLabel(it.id, defaultRendererLabel))
        }
    }

    val ioSchedulerOptions = remember(availableIoSchedulers, currentIoScheduler) {
        (availableIoSchedulers + currentIoScheduler)
            .filter { it.isNotBlank() }
            .distinct()
    }

    // ---- Subpages ----------------------------------------------------------

    if (inFloatingFpsSubpage) {
        BackHandler { inFloatingFpsSubpage = false }
        FloatingFpsConfigScreen(
            config = fpsConfig,
            targetHz = liveRefreshRate,
            onConfigChange = updateFpsConfig,
            onBack = { inFloatingFpsSubpage = false }
        )
        return
    }

    if (inHardwareDiagnosticSubpage) {
        BackHandler { inHardwareDiagnosticSubpage = false }
        HardwareDiagnosticScreen(onBack = { inHardwareDiagnosticSubpage = false })
        return
    }

    if (inUtilitySubpage) {
        BackHandler { inUtilitySubpage = false }
        UtilityScreen(onBack = { inUtilitySubpage = false })
        return
    }

    if (inMemoryZramSubpage) {
        BackHandler { inMemoryZramSubpage = false }
        MemoryZramScreen(onBack = { inMemoryZramSubpage = false })
        return
    }

    // ---- Bottom Sheets -----------------------------------------------------

    if (showRefreshRateSheet) {
        val rrSubtitle = if (currentRrId == null) {
            "$liveRefreshRate Hz · $defaultDynamicLabel"
        } else {
            localizedLabel(
                "Layar saat ini: $liveRefreshRate Hz",
                "Current display: $liveRefreshRate Hz"
            )
        }
        TweakOptionBottomSheet(
            title = stringResource(R.string.tweaks_dialog_select_rr),
            subtitle = rrSubtitle,
            options = rrModes.map { TweakOption(it.id, "${it.hz} Hz") },
            isSelected = { it == currentRrId },
            onSelect = { id ->
                val previous = currentRrId
                currentRrId = id
                showRefreshRateSheet = false
                coroutineScope.launch {
                    applyTweakChange(
                        snackbarHostState = snackbarHostState,
                        errorMessage = applyFailedText,
                        revert = { currentRrId = previous }
                    ) { ConfigRepository.setRefreshRate(id) }
                }
            },
            onDismiss = { showRefreshRateSheet = false }
        )
    }

    if (showRenderSheet) {
        TweakOptionBottomSheet(
            title = stringResource(R.string.tweaks_dialog_select_renderer),
            options = rendererOptions,
            isSelected = { it.equals(currentRendererId, ignoreCase = true) },
            onSelect = { rawId ->
                val previous = currentRendererId
                currentRendererId = rawId
                showRenderSheet = false
                coroutineScope.launch {
                    applyTweakChange(
                        snackbarHostState = snackbarHostState,
                        errorMessage = applyFailedText,
                        revert = { currentRendererId = previous }
                    ) { ConfigRepository.setRenderer(rawId) }
                }
            },
            onDismiss = { showRenderSheet = false }
        )
    }

    if (showIoSchedulerSheet) {
        TweakOptionBottomSheet(
            title = stringResource(R.string.dialog_select_io_scheduler),
            options = ioSchedulerOptions.map { TweakOption(it, it) },
            isSelected = { it.equals(currentIoScheduler, ignoreCase = true) },
            onSelect = { scheduler ->
                val previous = currentIoScheduler
                currentIoScheduler = scheduler
                showIoSchedulerSheet = false
                coroutineScope.launch {
                    applyTweakChange(
                        snackbarHostState = snackbarHostState,
                        errorMessage = applyFailedText,
                        revert = { currentIoScheduler = previous }
                    ) { ConfigRepository.setIoScheduler(scheduler) }
                }
            },
            onDismiss = { showIoSchedulerSheet = false }
        )
    }

    // ---- Main layout ---------------------------------------------------------

    val isFloating = LocalFloatingNavBar.current
    val bottomPadding = if (isFloating) {
        110.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    } else {
        24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.tweaks_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                windowInsets = WindowInsets.statusBars,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = if (isFloating) 92.dp else 12.dp)
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    shape = RoundedCornerShape(18.dp),
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 6.dp,
                bottom = bottomPadding,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "display_performance") {
                NestedTweakCardGroup(
                    headerIcon = Icons.Rounded.Tune,
                    headerTitle = localizedLabel("Perf & Layar", "Perf & Display")
                ) {
                    NestedTweakSwitchItem(
                        icon = Icons.Rounded.BatterySaver,
                        title = stringResource(R.string.tweaks_lite_mode),
                        subtitle = localizedLabel(
                            "Batasi clock CPU untuk suhu lebih adem dan hemat daya",
                            "Limit CPU clock for cooler thermals and power saving"
                        ),
                        checked = isGlobalLiteMode,
                        onCheckedChange = { newState ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val previous = isGlobalLiteMode
                            isGlobalLiteMode = newState
                            coroutineScope.launch {
                                applyTweakChange(
                                    snackbarHostState = snackbarHostState,
                                    errorMessage = applyFailedText,
                                    revert = { isGlobalLiteMode = previous }
                                ) { ConfigRepository.setGlobalLiteMode(newState) }
                            }
                        }
                    )

                    NestedTweakActionItem(
                        icon = Icons.Rounded.Speed,
                        title = stringResource(R.string.tweaks_refresh_rate),
                        subtitle = currentRrText,
                        onClick = { showRefreshRateSheet = true }
                    )

                    NestedTweakActionItem(
                        icon = if (isGlobalTextureEnabled) Icons.Rounded.Lock else Icons.Rounded.DeveloperBoard,
                        title = stringResource(R.string.tweaks_renderer),
                        subtitle = effectiveRendererText,
                        enabled = !isGlobalTextureEnabled,
                        onClick = { showRenderSheet = true }
                    )
                }
            }

            item(key = "floating_fps") {
                FloatingFpsCardSection(
                    config = fpsConfig,
                    targetHz = liveRefreshRate,
                    isScrolling = pagerState?.isScrollInProgress == true,
                    onConfigChange = updateFpsConfig,
                    onOpenCustomize = { inFloatingFpsSubpage = true }
                )
            }

            item(key = "additional") {
                NestedTweakCardGroup(
                    headerIcon = Icons.Rounded.Memory,
                    headerTitle = stringResource(R.string.section_additional_tweaks)
                ) {
                    NestedTweakActionItem(
                        icon = Icons.Rounded.Memory,
                        title = stringResource(R.string.tweak_memory_zram),
                        subtitle = stringResource(R.string.tweak_memory_zram_desc),
                        onClick = { inMemoryZramSubpage = true }
                    )

                    NestedTweakActionItem(
                        icon = Icons.Rounded.Storage,
                        title = stringResource(R.string.tweak_io_scheduler),
                        subtitle = currentIoScheduler,
                        onClick = { showIoSchedulerSheet = true }
                    )
                }
            }

            item(key = "utility") {
                NestedTweakCardGroup(
                    headerIcon = Icons.Rounded.Widgets,
                    headerTitle = stringResource(R.string.section_utility_diagnostics)
                ) {
                    NestedTweakActionItem(
                        icon = Icons.Rounded.DeveloperBoard,
                        title = stringResource(R.string.hardware_diag_title),
                        subtitle = stringResource(R.string.hardware_diag_desc),
                        onClick = { inHardwareDiagnosticSubpage = true }
                    )

                    NestedTweakActionItem(
                        icon = Icons.Rounded.Widgets,
                        title = stringResource(R.string.utility_screen_title),
                        subtitle = stringResource(R.string.utility_screen_desc),
                        onClick = { inUtilitySubpage = true }
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Floating FPS Monitor Section & Live Preview
// ---------------------------------------------------------------------------

@Composable
private fun fpsStyleLabel(style: FpsOverlayStyle): String = when (style) {
    FpsOverlayStyle.PILL -> localizedLabel("Kapsul", "Pill")
    FpsOverlayStyle.GRAPH -> localizedLabel("Grafik", "Graph")
    FpsOverlayStyle.MINIMAL -> localizedLabel("Minimalis", "Minimal")
}

@Composable
private fun fpsCornerLabel(corner: FpsOverlayCorner): String = when (corner) {
    FpsOverlayCorner.TOP_LEFT -> localizedLabel("Kiri Atas", "Top Left")
    FpsOverlayCorner.TOP_RIGHT -> localizedLabel("Kanan Atas", "Top Right")
    FpsOverlayCorner.BOTTOM_LEFT -> localizedLabel("Kiri Bawah", "Bottom Left")
    FpsOverlayCorner.BOTTOM_RIGHT -> localizedLabel("Kanan Bawah", "Bottom Right")
}

@Composable
private fun FloatingFpsCardSection(
    config: FloatingFpsConfig,
    targetHz: Int,
    isScrolling: Boolean,
    onConfigChange: (FloatingFpsConfig) -> Unit,
    onOpenCustomize: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val telemetry = rememberSimulatedFpsTelemetry(
        targetHz = targetHz,
        intervalMs = config.intervalMs,
        active = !isScrolling
    )
    val innerRadius = (LocalCornerRadius.current * 0.78f).coerceIn(16.dp, 24.dp)

    val statusText = if (config.enabled) {
        localizedLabel(
            "Aktif · Gaya ${fpsStyleLabel(config.style)} · ${config.intervalMs} ms",
            "Active · ${fpsStyleLabel(config.style)} style · ${config.intervalMs} ms"
        )
    } else {
        localizedLabel(
            "Tampilkan penghitung FPS real-time mengambang di layar",
            "Display a real-time floating FPS counter on screen"
        )
    }

    val customizeSubtitle = buildString {
        append(fpsCornerLabel(config.corner))
        append(" · ")
        append(
            if (config.autoGame) {
                localizedLabel("Auto-Game Aktif", "Auto-Game On")
            } else {
                localizedLabel("Manual", "Manual")
            }
        )
        append(" · ${(config.scale * 100).roundToInt()}%")
    }

    NestedTweakCardGroup(
        headerIcon = Icons.Rounded.Speed,
        headerTitle = localizedLabel("Floating FPS Monitor", "Floating FPS Monitor")
    ) {
        NestedTweakSwitchItem(
            icon = Icons.Rounded.Speed,
            title = localizedLabel("Aktifkan Overlay FPS", "Enable FPS Overlay"),
            subtitle = statusText,
            checked = config.enabled,
            onCheckedChange = { enabled ->
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onConfigChange(config.copy(enabled = enabled))
            }
        )

        // Interactive Live Preview Stage
        Surface(
            shape = RoundedCornerShape(innerRadius),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = localizedLabel("Pratinjau Langsung", "Live Preview"),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val previewContext = LocalContext.current
                    val previewScope = rememberCoroutineScope()
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            previewScope.launch {
                                if (FloatingFpsService.ensureOverlayPermission(previewContext)) {
                                    FloatingFpsService.triggerPreviewBurst(previewContext)
                                }
                            }
                        },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = localizedLabel("TES OVERLAY 5D", "TEST 5S"),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                FpsViewportStage(
                    config = config,
                    telemetry = telemetry,
                    heightDp = 112
                )

                // Connected 3-style selector (MD3E ButtonGroup)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FpsOverlayStyle.entries.forEachIndexed { index, style ->
                        val shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            FpsOverlayStyle.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        }
                        ToggleButton(
                            checked = config.style == style,
                            onCheckedChange = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onConfigChange(config.copy(style = style))
                            },
                            shapes = shapes,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = fpsStyleLabel(style),
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        NestedTweakActionItem(
            icon = Icons.Rounded.Tune,
            title = localizedLabel("Kustomisasi & Auto-Game", "Customize & Auto-Game"),
            subtitle = customizeSubtitle,
            onClick = onOpenCustomize
        )
    }
}

@Composable
private fun FpsViewportStage(
    config: FloatingFpsConfig,
    telemetry: SimulatedFpsTelemetry,
    heightDp: Int
) {
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clipToBounds()
            .padding(12.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = 20.dp.toPx()
            var x = step
            while (x < size.width) {
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                x += step
            }
            var y = step
            while (y < size.height) {
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                y += step
            }
        }

        Box(
            modifier = Modifier
                .align(config.corner.alignment)
                .graphicsLayer {
                    scaleX = config.scale
                    scaleY = config.scale
                }
        ) {
            FpsOverlayPreviewBadge(
                config = config,
                telemetry = telemetry
            )
        }
    }
}

@Composable
private fun FpsOverlayPreviewBadge(
    config: FloatingFpsConfig,
    telemetry: SimulatedFpsTelemetry
) {
    val ratio = telemetry.fps.toFloat() / telemetry.targetHz.coerceAtLeast(1).toFloat()
    val targetStatusColor = when {
        ratio >= 0.90f -> MaterialTheme.colorScheme.primary
        ratio >= 0.65f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    val statusColor by animateColorAsState(
        targetValue = targetStatusColor,
        label = "fpsStatusColor"
    )
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = config.bgAlpha)
    val textColor = MaterialTheme.colorScheme.onSurface
    val subTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    val extraParts = remember(config, telemetry) {
        buildList {
            if (config.showFrametime) add(String.format(Locale.US, "%.1f ms", telemetry.frametimeMs))
            if (config.showTemp) add(telemetry.tempLabel)
            if (config.showProfile) add(telemetry.profileLabel)
        }
    }

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
                        .width(156.dp)
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

                    FrametimeSparkline(
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
                color = MaterialTheme.colorScheme.background.copy(alpha = (config.bgAlpha * 0.65f).coerceIn(0.15f, 0.75f))
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

@Composable
private fun FrametimeSparkline(
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FloatingFpsConfigScreen(
    config: FloatingFpsConfig,
    targetHz: Int,
    onConfigChange: (FloatingFpsConfig) -> Unit,
    onBack: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val telemetry = rememberSimulatedFpsTelemetry(
        targetHz = targetHz,
        intervalMs = config.intervalMs,
        active = true
    )
    val innerRadius = (LocalCornerRadius.current * 0.78f).coerceIn(16.dp, 24.dp)

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = localizedLabel("Floating FPS Monitor", "Floating FPS Monitor"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    Surface(
                        onClick = onBack,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .padding(start = 12.dp, end = 6.dp)
                            .size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.setup_back),
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                windowInsets = WindowInsets.statusBars,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 6.dp,
                bottom = 32.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "preview_stage") {
                NestedTweakCardGroup(
                    headerIcon = Icons.Rounded.Speed,
                    headerTitle = localizedLabel("Pratinjau & Gaya", "Preview & Style")
                ) {
                    FpsViewportStage(
                        config = config,
                        telemetry = telemetry,
                        heightDp = 156
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FpsOverlayStyle.entries.forEachIndexed { index, style ->
                            val shapes = when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                FpsOverlayStyle.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            }
                            ToggleButton(
                                checked = config.style == style,
                                onCheckedChange = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onConfigChange(config.copy(style = style))
                                },
                                shapes = shapes,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = fpsStyleLabel(style),
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    NestedTweakSwitchItem(
                        icon = Icons.Rounded.Speed,
                        title = localizedLabel("Status Overlay FPS", "FPS Overlay Status"),
                        subtitle = if (config.enabled) {
                            localizedLabel("Overlay diaktifkan", "Overlay enabled")
                        } else {
                            localizedLabel("Overlay dinonaktifkan", "Overlay disabled")
                        },
                        checked = config.enabled,
                        onCheckedChange = { enabled ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onConfigChange(config.copy(enabled = enabled))
                        }
                    )
                }
            }

            item(key = "automation_position") {
                NestedTweakCardGroup(
                    headerIcon = Icons.Rounded.SportsEsports,
                    headerTitle = localizedLabel("Otomatisasi & Posisi", "Automation & Position")
                ) {
                    NestedTweakSwitchItem(
                        icon = Icons.Rounded.SportsEsports,
                        title = localizedLabel("Auto-Muncul di Game", "Auto-Show in Games"),
                        subtitle = localizedLabel(
                            "Otomatis aktif saat membuka paket di daftar game",
                            "Automatically appear when launching games in game list"
                        ),
                        checked = config.autoGame,
                        onCheckedChange = { autoGame ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onConfigChange(config.copy(autoGame = autoGame))
                        }
                    )

                    NestedTweakSwitchItem(
                        icon = Icons.Rounded.Lock,
                        title = localizedLabel("Kunci Sentuhan (Touch Pass-Through)", "Touch Pass-Through Lock"),
                        subtitle = localizedLabel(
                            "Sentuhan menembus overlay agar tidak mengganggu tombol game",
                            "Let touches pass through so overlay never blocks game controls"
                        ),
                        checked = config.touchLock,
                        onCheckedChange = { touchLock ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onConfigChange(config.copy(touchLock = touchLock))
                        }
                    )

                    Surface(
                        shape = RoundedCornerShape(innerRadius),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = localizedLabel("Posisi Sudut Awal", "Initial Corner Position"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FpsOverlayCorner.entries.forEach { corner ->
                                    FilterChip(
                                        selected = config.corner == corner,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onConfigChange(config.copy(corner = corner))
                                        },
                                        label = {
                                            Text(
                                                text = fpsCornerLabel(corner),
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item(key = "appearance_metrics") {
                NestedTweakCardGroup(
                    headerIcon = Icons.Rounded.Palette,
                    headerTitle = localizedLabel("Tampilan & Metrik", "Appearance & Metrics")
                ) {
                    Surface(
                        shape = RoundedCornerShape(innerRadius),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = localizedLabel("Metrik Pendamping", "Secondary Metrics"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = config.showFrametime,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onConfigChange(config.copy(showFrametime = !config.showFrametime))
                                    },
                                    label = { Text("Frametime (ms)") }
                                )
                                FilterChip(
                                    selected = config.showTemp,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onConfigChange(config.copy(showTemp = !config.showTemp))
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Thermostat,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    label = { Text(localizedLabel("Suhu (°C)", "Temp (°C)")) }
                                )
                                FilterChip(
                                    selected = config.showProfile,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onConfigChange(config.copy(showProfile = !config.showProfile))
                                    },
                                    label = { Text(localizedLabel("Profil", "Profile")) }
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(innerRadius),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            FpsSliderRow(
                                label = localizedLabel("Skala Ukuran", "Overlay Scale"),
                                valueText = "${(config.scale * 100).roundToInt()}%",
                                value = config.scale,
                                valueRange = 0.8f..1.3f,
                                onValueChange = { onConfigChange(config.copy(scale = it)) }
                            )

                            FpsSliderRow(
                                label = localizedLabel("Opasitas Latar", "Background Opacity"),
                                valueText = "${(config.bgAlpha * 100).roundToInt()}%",
                                value = config.bgAlpha,
                                valueRange = 0.25f..1.0f,
                                onValueChange = { onConfigChange(config.copy(bgAlpha = it)) }
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = localizedLabel("Interval Pembaruan", "Refresh Interval"),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${config.intervalMs} ms",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                val intervals = listOf(250, 500, 1000)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    intervals.forEachIndexed { index, ms ->
                                        val shapes = when (index) {
                                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                            intervals.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                        }
                                        ToggleButton(
                                            checked = config.intervalMs == ms,
                                            onCheckedChange = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onConfigChange(config.copy(intervalMs = ms))
                                            },
                                            shapes = shapes,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = "$ms ms",
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FpsSliderRow(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange
        )
    }
}

// ---------------------------------------------------------------------------
// Nested Bento Card Container & Inner Items (MD3E)
// ---------------------------------------------------------------------------

@Composable
private fun NestedTweakCardGroup(
    headerIcon: ImageVector,
    headerTitle: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val outerRadius = LocalCornerRadius.current.coerceAtLeast(26.dp)

    Surface(
        shape = RoundedCornerShape(outerRadius),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 2.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = headerIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = headerTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            content()
        }
    }
}

@Composable
private fun NestedTweakSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val innerRadius = (LocalCornerRadius.current * 0.78f).coerceIn(16.dp, 24.dp)

    Surface(
        onClick = { onCheckedChange(!checked) },
        shape = RoundedCornerShape(innerRadius),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                thumbContent = {
                    Icon(
                        imageVector = if (checked) Icons.Rounded.Check else Icons.Rounded.Close,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize)
                    )
                }
            )
        }
    }
}

@Composable
private fun NestedTweakActionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "nestedItemScale"
    )
    val innerRadius = (LocalCornerRadius.current * 0.78f).coerceIn(16.dp, 24.dp)

    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        enabled = enabled,
        shape = RoundedCornerShape(innerRadius),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (enabled) 1f else 0.55f)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                imageVector = if (enabled) Icons.Rounded.ChevronRight else Icons.Rounded.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                modifier = Modifier.size(if (enabled) 20.dp else 18.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Option bottom sheet (MD3E segmented list)
// ---------------------------------------------------------------------------

@Composable
private fun <T> TweakOptionBottomSheet(
    title: String,
    options: List<TweakOption<T>>,
    isSelected: (T) -> Boolean,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    subtitle: String? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentWindowInsets = { WindowInsets(0.dp) },
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
            ) {
                if (options.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = localizedLabel(
                                "Belum ada opsi tersedia",
                                "No options available yet"
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    options.forEachIndexed { index, option ->
                        val selected = isSelected(option.value)
                        SegmentedListItem(
                            selected = selected,
                            onClick = { onSelect(option.value) },
                            shapes = ListItemDefaults.segmentedShapes(
                                index = index,
                                count = options.size
                            ),
                            colors = ListItemDefaults.segmentedColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedLeadingContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            leadingContent = {
                                Box(
                                    modifier = Modifier.size(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (selected) {
                                        Box(
                                            modifier = Modifier
                                                .size(22.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .border(
                                                    width = 1.5.dp,
                                                    color = MaterialTheme.colorScheme.outlineVariant,
                                                    shape = CircleShape
                                                )
                                        )
                                    }
                                }
                            }
                        ) {
                            Text(
                                text = option.label,
                                style = if (selected) {
                                    MaterialTheme.typography.titleSmallEmphasized
                                } else {
                                    MaterialTheme.typography.bodyLarge
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
