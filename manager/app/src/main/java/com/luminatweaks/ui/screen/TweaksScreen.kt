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
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luminatweaks.R
import com.luminatweaks.ui.theme.LocalHapticConfig
import com.luminatweaks.ui.theme.LuminaHapticEngine
import com.luminatweaks.ui.theme.LuminaHapticPulse
import com.luminatweaks.data.ConfigRepository
import com.luminatweaks.data.DisplayRrMode
import com.luminatweaks.gamedeck.GameDeckPrefs
import com.luminatweaks.gamedeck.GameDeckSettingsScreen
import com.luminatweaks.service.FloatingFpsService
import com.luminatweaks.ui.component.ExpressiveInfoTile
import com.luminatweaks.ui.component.LuminaAnchoredSheet
import com.luminatweaks.ui.component.LuminaBackButton
import com.luminatweaks.ui.component.LuminaSharedContainerSource
import com.luminatweaks.ui.component.OdometerNumber
import com.luminatweaks.ui.component.luminaHazeHeader
import com.luminatweaks.ui.component.pressScale
import com.luminatweaks.ui.component.rememberDialogOrigin
import com.luminatweaks.ui.theme.LocalFloatingNavBar
import com.luminatweaks.util.DeviceUtil
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class TweaksSubpage(val key: String) {
    FLOATING_HUD("tweaks_floating_hud"),
    GAME_BAR("tweaks_game_bar"),
    MEMORY_ZRAM("tweaks_memory_zram"),
    HARDWARE("tweaks_hardware"),
    UTILITY("tweaks_utility")
}

data class RendererItem(
    val id: String,
    val labelRes: Int
)

val RENDERER_OPTIONS = listOf(
    RendererItem("default", R.string.tweaks_renderer_system_default),
    RendererItem("skiagl", R.string.opt_default),
    RendererItem("skiavk", R.string.opt_default)
)

fun normalizeRendererId(id: String): String {
    return when (id.trim().lowercase()) {
        "skiavk", "skiavkthreaded", "vulkan", "vk" -> "skiavk"
        "skiagl", "skiaglthreaded", "opengl", "openglthreaded", "gles" -> "skiagl"
        else -> "default"
    }
}

fun getRendererDisplayLabel(id: String, defaultLabel: String): String {
    return when (normalizeRendererId(id)) {
        "skiavk" -> "Skia Vulkan"
        "skiagl" -> "Skia OpenGL"
        else -> defaultLabel
    }
}

fun getRendererShortLabel(id: String, defaultLabel: String): String {
    return when (normalizeRendererId(id)) {
        "skiavk" -> "Skia Vulkan"
        "skiagl" -> "Skia OpenGL"
        else -> defaultLabel
    }
}

private data class TweakDialogOption<T>(
    val value: T,
    val label: String
)

internal fun localizedLabel(id: String, en: String): String {
    val lang = Locale.getDefault().language.lowercase(Locale.ROOT)
    val isIndonesian = lang == "in" || lang == "id"
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

private suspend fun awaitPagerSettled(pagerState: PagerState?) {
    pagerState ?: return
    snapshotFlow { pagerState.isScrollInProgress }.first { !it }
}

@Composable
fun TweaksScreen(
    pagerState: PagerState? = null,
    activeSubpage: TweaksSubpage? = null,
    onOpenSubpage: ((TweaksSubpage, Int) -> Unit)? = null,
    onOpenFloatingHud: (() -> Unit)? = null,
    onBackClick: (() -> Unit)? = null
) {
    BackHandler(enabled = onBackClick != null) { onBackClick?.invoke() }
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
    var currentRendererId by remember { mutableStateOf(normalizeRendererId(cached?.currentRendererId ?: "default")) }
    var currentIoScheduler by remember { mutableStateOf(cached?.currentIoScheduler ?: "mq-deadline") }
    var availableIoSchedulers by remember { mutableStateOf(emptyList<String>()) }

    var fpsConfig by remember { mutableStateOf(FloatingFpsPrefs.load(context)) }
    var gameBarEnabled by remember { mutableStateOf(GameDeckPrefs.load(context).enabled) }
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

    var showRefreshRateDialog by rememberSaveable { mutableStateOf(false) }
    var showRenderDialog by rememberSaveable { mutableStateOf(false) }
    var showIoSchedulerDialog by rememberSaveable { mutableStateOf(false) }
    val refreshRateOrigin = rememberDialogOrigin()
    val rendererOrigin = rememberDialogOrigin()
    val ioSchedulerOrigin = rememberDialogOrigin()

    var fallbackSubpage by rememberSaveable { mutableStateOf<TweaksSubpage?>(null) }
    val resolvedActiveSubpage = if (onOpenSubpage != null) activeSubpage else fallbackSubpage

    LaunchedEffect(resolvedActiveSubpage) {
        if (resolvedActiveSubpage == null) {
            gameBarEnabled = GameDeckPrefs.load(context).enabled
            fpsConfig = FloatingFpsPrefs.load(context)
        }
    }

    val openSubpage: (TweaksSubpage) -> Unit = { target ->
        if (onOpenSubpage != null) {
            onOpenSubpage(target, liveRefreshRate)
        } else if (target == TweaksSubpage.FLOATING_HUD && onOpenFloatingHud != null) {
            onOpenFloatingHud()
        } else {
            fallbackSubpage = target
        }
    }

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
        currentRendererId = normalizeRendererId(state.currentRendererId)
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

    val rrModeSubtitle = remember(liveRefreshRate, currentRrId, rrModes, defaultDynamicLabel) {
        val targetMode = rrModes.find { it.id == currentRrId }
        when {
            targetMode != null && targetMode.hz != liveRefreshRate ->
                localizedLabel("Target ${targetMode.hz} Hz", "Target ${targetMode.hz} Hz")
            currentRrId == null ->
                defaultDynamicLabel
            else ->
                localizedLabel("Terkunci ${targetMode?.hz ?: liveRefreshRate} Hz", "Locked ${targetMode?.hz ?: liveRefreshRate} Hz")
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
            TweakDialogOption(it.id, getRendererDisplayLabel(it.id, defaultRendererLabel))
        }
    }

    val ioSchedulerOptions = remember(availableIoSchedulers, currentIoScheduler) {
        (availableIoSchedulers + currentIoScheduler)
            .filter { it.isNotBlank() }
            .distinct()
    }

    val rrDialogSubtitle = if (currentRrId == null) {
        "$liveRefreshRate Hz · $defaultDynamicLabel"
    } else {
        localizedLabel(
            "Layar saat ini: $liveRefreshRate Hz",
            "Current display: $liveRefreshRate Hz"
        )
    }

    TweakOptionSheet(
        visible = showRefreshRateDialog,
        onDismiss = { showRefreshRateDialog = false },
        icon = Icons.Rounded.Speed,
        title = stringResource(R.string.tweaks_dialog_select_rr),
        subtitle = rrDialogSubtitle,
        options = rrModes.map { TweakDialogOption(it.id, "${it.hz} Hz") },
        isSelected = { it == currentRrId },
        onSelect = { id ->
            val previous = currentRrId
            currentRrId = id
            showRefreshRateDialog = false
            coroutineScope.launch {
                applyTweakChange(
                    snackbarHostState = snackbarHostState,
                    errorMessage = applyFailedText,
                    revert = { currentRrId = previous }
                ) { ConfigRepository.setRefreshRate(id) }
            }
        }
    )

    TweakOptionSheet(
        visible = showRenderDialog,
        onDismiss = { showRenderDialog = false },
        icon = Icons.Rounded.DeveloperBoard,
        title = stringResource(R.string.tweaks_dialog_select_renderer),
        subtitle = localizedLabel(
            "Berlaku global saat aplikasi dibuka ulang",
            "Applies globally on app cold start"
        ),
        options = rendererOptions,
        isSelected = { it.equals(normalizeRendererId(currentRendererId), ignoreCase = true) },
        onSelect = { rawId ->
            val previous = currentRendererId
            currentRendererId = rawId
            showRenderDialog = false
            coroutineScope.launch {
                applyTweakChange(
                    snackbarHostState = snackbarHostState,
                    errorMessage = applyFailedText,
                    revert = { currentRendererId = previous }
                ) { ConfigRepository.setRenderer(rawId) }
            }
        }
    )

    TweakOptionSheet(
        visible = showIoSchedulerDialog,
        onDismiss = { showIoSchedulerDialog = false },
        icon = Icons.Rounded.Storage,
        title = stringResource(R.string.dialog_select_io_scheduler),
        options = ioSchedulerOptions.map { TweakDialogOption(it, it) },
        isSelected = { it.equals(currentIoScheduler, ignoreCase = true) },
        onSelect = { scheduler ->
            val previous = currentIoScheduler
            currentIoScheduler = scheduler
            showIoSchedulerDialog = false
            coroutineScope.launch {
                applyTweakChange(
                    snackbarHostState = snackbarHostState,
                    errorMessage = applyFailedText,
                    revert = { currentIoScheduler = previous }
                ) { ConfigRepository.setIoScheduler(scheduler) }
            }
        }
    )

    val scheme = MaterialTheme.colorScheme
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val hazeState = remember { HazeState() }
    val isFloating = LocalFloatingNavBar.current
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomPadding = if (isFloating) 112.dp + navInset else 28.dp + navInset

    val tweaksMainContent: @Composable () -> Unit = {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            contentWindowInsets = WindowInsets(0.dp),
            containerColor = scheme.background,
            topBar = {
                TweaksFlexibleTopBar(
                    title = stringResource(R.string.tweaks_title),
                    scrollBehavior = scrollBehavior,
                    hazeState = hazeState,
                    onBackClick = onBackClick
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
                        containerColor = scheme.errorContainer,
                        contentColor = scheme.onErrorContainer
                    )
                }
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 6.dp,
                    bottom = bottomPadding,
                    start = 16.dp,
                    end = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "hero_tweaks_bento") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(228.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RefreshRateHeroBentoTile(
                                modifier = Modifier
                                    .weight(0.60f)
                                    .then(refreshRateOrigin.trackedModifier()),
                                label = stringResource(R.string.tweaks_refresh_rate),
                                hzValue = "$liveRefreshRate Hz",
                                subtitle = rrModeSubtitle,
                                onClick = { showRefreshRateDialog = true }
                            )

                            CompactSelectorBentoTile(
                                modifier = Modifier
                                    .weight(0.40f)
                                    .then(ioSchedulerOrigin.trackedModifier()),
                                icon = Icons.Rounded.Storage,
                                label = stringResource(R.string.tweak_io_scheduler),
                                value = currentIoScheduler,
                                onClick = { showIoSchedulerDialog = true }
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RendererBentoTile(
                                modifier = Modifier
                                    .weight(0.50f)
                                    .then(rendererOrigin.trackedModifier()),
                                label = stringResource(R.string.tweaks_renderer),
                                value = effectiveRendererText,
                                locked = isGlobalTextureEnabled,
                                onClick = { showRenderDialog = true }
                            )

                            LiteModeBentoTile(
                                modifier = Modifier.weight(0.50f),
                                title = stringResource(R.string.tweaks_lite_mode),
                                subtitle = localizedLabel("Hemat daya CPU", "CPU power limit"),
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
                        }
                    }
                }

                item(key = "overlay_bento_pair") {
                    val telemetrySubtitle = if (fpsConfig.enabled) {
                        val count = fpsConfig.activeModuleCount.coerceAtLeast(1)
                        localizedLabel("Aktif · $count metrik", "Active · $count metrics")
                    } else {
                        localizedLabel("Metrik real-time", "Real-time metrics")
                    }

                    val gameBarSubtitle = if (gameBarEnabled) {
                        localizedLabel("Aktif · DockRail", "Active · DockRail")
                    } else {
                        localizedLabel("Panel game cepat", "Quick game dock")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        LuminaSharedContainerSource(
                            sharedKey = TweaksSubpage.FLOATING_HUD.key,
                            isExpanded = resolvedActiveSubpage == TweaksSubpage.FLOATING_HUD,
                            minHeight = 112.dp,
                            cornerRadius = 26.dp,
                            modifier = Modifier.weight(1f)
                        ) {
                            ExpressiveInfoTile(
                                modifier = Modifier.fillMaxWidth(),
                                icon = Icons.Rounded.Speed,
                                title = "Floating HUD",
                                subtitle = telemetrySubtitle,
                                accentColor = if (fpsConfig.enabled) scheme.primary else scheme.secondary,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    openSubpage(TweaksSubpage.FLOATING_HUD)
                                }
                            )
                        }

                        LuminaSharedContainerSource(
                            sharedKey = TweaksSubpage.GAME_BAR.key,
                            isExpanded = resolvedActiveSubpage == TweaksSubpage.GAME_BAR,
                            minHeight = 112.dp,
                            cornerRadius = 26.dp,
                            modifier = Modifier.weight(1f)
                        ) {
                            ExpressiveInfoTile(
                                modifier = Modifier.fillMaxWidth(),
                                icon = Icons.Rounded.SportsEsports,
                                title = "Game Bar",
                                subtitle = gameBarSubtitle,
                                accentColor = if (gameBarEnabled) scheme.tertiary else scheme.primary,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    openSubpage(TweaksSubpage.GAME_BAR)
                                }
                            )
                        }
                    }
                }

                item(key = "system_modules_group") {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        LuminaSharedContainerSource(
                            sharedKey = TweaksSubpage.MEMORY_ZRAM.key,
                            isExpanded = resolvedActiveSubpage == TweaksSubpage.MEMORY_ZRAM,
                            minHeight = 64.dp,
                            cornerRadius = 26.dp
                        ) {
                            ConnectedTweakActionRow(
                                index = 0,
                                count = 3,
                                icon = Icons.Rounded.Memory,
                                title = stringResource(R.string.tweak_memory_zram),
                                subtitle = stringResource(R.string.tweak_memory_zram_desc),
                                onClick = { openSubpage(TweaksSubpage.MEMORY_ZRAM) }
                            )
                        }

                        LuminaSharedContainerSource(
                            sharedKey = TweaksSubpage.HARDWARE.key,
                            isExpanded = resolvedActiveSubpage == TweaksSubpage.HARDWARE,
                            minHeight = 64.dp,
                            cornerRadius = 12.dp
                        ) {
                            ConnectedTweakActionRow(
                                index = 1,
                                count = 3,
                                icon = Icons.Rounded.DeveloperBoard,
                                title = stringResource(R.string.hardware_diag_title),
                                subtitle = stringResource(R.string.hardware_diag_desc),
                                onClick = { openSubpage(TweaksSubpage.HARDWARE) }
                            )
                        }

                        LuminaSharedContainerSource(
                            sharedKey = TweaksSubpage.UTILITY.key,
                            isExpanded = resolvedActiveSubpage == TweaksSubpage.UTILITY,
                            minHeight = 64.dp,
                            cornerRadius = 26.dp
                        ) {
                            ConnectedTweakActionRow(
                                index = 2,
                                count = 3,
                                icon = Icons.Rounded.Widgets,
                                title = stringResource(R.string.utility_screen_title),
                                subtitle = stringResource(R.string.utility_screen_desc),
                                onClick = { openSubpage(TweaksSubpage.UTILITY) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (onOpenSubpage != null) {
        tweaksMainContent()
    } else {
        BackHandler(enabled = fallbackSubpage != null) {
            fallbackSubpage = null
        }
        LuminaDrillTransition(
            targetState = fallbackSubpage,
            isForward = { _, target -> target != null },
            contentKey = { it?.key ?: "tweaks_root" }
        ) { targetSubpage ->
            when (targetSubpage) {
                null -> tweaksMainContent()
                TweaksSubpage.FLOATING_HUD -> TelemetryOverlayScreen(
                    config = fpsConfig,
                    targetHz = liveRefreshRate,
                    onConfigChange = updateFpsConfig,
                    onBack = { fallbackSubpage = null }
                )
                TweaksSubpage.GAME_BAR -> GameDeckSettingsScreen(
                    onBack = { fallbackSubpage = null }
                )
                TweaksSubpage.MEMORY_ZRAM -> MemoryZramScreen(
                    onBack = { fallbackSubpage = null }
                )
                TweaksSubpage.HARDWARE -> HardwareDiagnosticScreen(
                    onBack = { fallbackSubpage = null }
                )
                TweaksSubpage.UTILITY -> UtilityScreen(
                    onBack = { fallbackSubpage = null }
                )
            }
        }
    }
}

@Composable
private fun RefreshRateHeroBentoTile(
    label: String,
    hzValue: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val scaleModifier = pressScale(interactionSource, 0.965f)

    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        interactionSource = interactionSource,
        shape = RoundedCornerShape(26.dp),
        color = scheme.secondaryContainer,
        contentColor = scheme.onSecondaryContainer,
        modifier = modifier
            .fillMaxWidth()
            .then(scaleModifier)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    ),
                    color = scheme.onSecondaryContainer.copy(alpha = 0.85f),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )

                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(scheme.onSecondaryContainer.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = scheme.onSecondaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                OdometerNumber(
                    value = hzValue,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 26.sp,
                        lineHeight = 30.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.8).sp
                    ),
                    color = scheme.onSecondaryContainer
                )

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Medium,
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    ),
                    color = scheme.onSecondaryContainer.copy(alpha = 0.8f),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CompactSelectorBentoTile(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val scaleModifier = pressScale(interactionSource, 0.965f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(scaleModifier)
            .clip(RoundedCornerShape(26.dp))
            .background(scheme.surfaceContainerHigh)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(scheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                ),
                color = scheme.primary,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                ),
                color = scheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RendererBentoTile(
    label: String,
    value: String,
    locked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val enabled = !locked
    val scaleModifier = pressScale(interactionSource, 0.965f, enabled)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(scaleModifier)
            .clip(RoundedCornerShape(26.dp))
            .background(scheme.surfaceContainerHigh)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .alpha(if (enabled) 1f else 0.58f)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(scheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (locked) Icons.Rounded.Lock else Icons.Rounded.DeveloperBoard,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Icon(
                imageVector = if (locked) Icons.Rounded.Lock else Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                ),
                color = scheme.primary,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                ),
                color = scheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LiteModeBentoTile(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val scaleModifier = pressScale(interactionSource, 0.965f)
    val containerColor by animateColorAsState(
        targetValue = if (checked) scheme.tertiaryContainer else scheme.surfaceContainerHigh,
        animationSpec = tween(220),
        label = "liteModeBentoContainer"
    )
    val iconTint = if (checked) scheme.onTertiaryContainer else scheme.primary
    val titleColor = if (checked) scheme.onTertiaryContainer else scheme.onSurface
    val subColor = if (checked) scheme.onTertiaryContainer.copy(alpha = 0.8f) else scheme.onSurfaceVariant

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(scaleModifier)
            .clip(RoundedCornerShape(26.dp))
            .background(containerColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                onCheckedChange(!checked)
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.BatterySaver,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                interactionSource = interactionSource,
                thumbContent = {
                    if (checked) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize)
                        )
                    }
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = scheme.onTertiary,
                    checkedIconColor = scheme.tertiary,
                    checkedTrackColor = scheme.tertiary,
                    uncheckedThumbColor = scheme.outline,
                    uncheckedIconColor = scheme.surfaceContainerHighest,
                    uncheckedTrackColor = scheme.surfaceContainerHighest
                )
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                ),
                color = titleColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                ),
                color = subColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TweaksFlexibleTopBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior,
    hazeState: HazeState,
    onBackClick: (() -> Unit)? = null
) {
    val fadeSurface = MaterialTheme.colorScheme.background
    Box(
        modifier = Modifier.luminaHazeHeader(
            hazeState = hazeState,
            surfaceColor = fadeSurface,
            overlappedFraction = scrollBehavior.state.overlappedFraction
        )
    ) {
        LargeFlexibleTopAppBar(
            title = {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = {
                if (onBackClick != null) {
                    LuminaBackButton(onBack = onBackClick)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent
            ),
            scrollBehavior = scrollBehavior,
            windowInsets = WindowInsets(0, 0, 0, 0)
        )
    }
}

@Composable
private fun TweaksSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(
            start = 12.dp,
            end = 12.dp,
            top = 12.dp,
            bottom = 6.dp
        )
    )
}

@Composable
private fun TweakSquircleIcon(
    icon: ImageVector,
    enabled: Boolean = true
) {
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    }

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun ExpressiveTweakTile(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    highlight: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "tweakTileScale"
    )

    val iconBoxBgColor by animateColorAsState(
        targetValue = if (highlight && enabled) {
            colorScheme.primaryContainer
        } else {
            colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        animationSpec = tween(350),
        label = "tileIconBoxBg"
    )

    val iconColor by animateColorAsState(
        targetValue = if (highlight && enabled) {
            colorScheme.onPrimaryContainer
        } else {
            colorScheme.onSurfaceVariant
        },
        animationSpec = tween(350),
        label = "tileIconColor"
    )

    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        enabled = enabled,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(26.dp),
        color = colorScheme.surfaceContainerHigh,
        modifier = modifier
            .alpha(if (enabled) 1f else 0.55f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(82.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(iconBoxBgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(36.dp)
                )

                Icon(
                    imageVector = if (enabled) Icons.Rounded.ChevronRight else Icons.Rounded.Lock,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(if (enabled) 20.dp else 17.dp),
                    tint = iconColor.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Column(
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun <T> TweakOptionSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    icon: ImageVector,
    title: String,
    options: List<TweakDialogOption<T>>,
    isSelected: (T) -> Boolean,
    onSelect: (T) -> Unit,
    subtitle: String? = null
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme

    LuminaAnchoredSheet(
        visible = visible,
        step = title,
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(scheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(21.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

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
                        color = scheme.onSurfaceVariant
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    options.forEachIndexed { index, option ->
                        val selected = isSelected(option.value)
                        val rowShape = connectedContainerShape(
                            index = index,
                            count = options.size,
                            outerRadius = 22.dp,
                            innerRadius = 4.dp
                        )
                        val fillColor by animateColorAsState(
                            targetValue = if (selected) scheme.secondaryContainer else scheme.surfaceContainerLow,
                            animationSpec = tween(200),
                            label = "tweakOptionFill"
                        )
                        val textColor by animateColorAsState(
                            targetValue = if (selected) scheme.onSecondaryContainer else scheme.onSurface,
                            animationSpec = tween(200),
                            label = "tweakOptionText"
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(rowShape)
                                .background(fillColor, rowShape)
                                .clickable {
                                    LuminaHapticEngine.perform(context, LocalHapticConfig.current, LuminaHapticPulse.Click)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelect(option.value)
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                ),
                                color = textColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            DialogRadioRing(selected = selected)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogRadioRing(selected: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val ringColor by animateColorAsState(
        targetValue = if (selected) scheme.primary else scheme.onSurfaceVariant,
        animationSpec = tween(200),
        label = "dialogRadioRingColor"
    )
    val dotScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "dialogRadioRingDot"
    )

    Box(
        modifier = Modifier.size(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(20.dp)) {
            val stroke = 2.dp.toPx()
            val radius = (size.width / 2f) - (stroke / 2f)
            drawCircle(
                color = ringColor,
                radius = radius,
                style = Stroke(width = stroke)
            )
            if (dotScale > 0f) {
                drawCircle(
                    color = scheme.primary,
                    radius = (size.width / 2f) * 0.3f * dotScale
                )
            }
        }
    }
}

@Composable
internal fun connectedContainerShape(
    index: Int,
    count: Int,
    outerRadius: Dp = 26.dp,
    innerRadius: Dp = 4.dp
): Shape {
    return when {
        count <= 1 -> RoundedCornerShape(outerRadius)
        index <= 0 -> RoundedCornerShape(
            topStart = outerRadius,
            topEnd = outerRadius,
            bottomStart = innerRadius,
            bottomEnd = innerRadius
        )
        index >= count - 1 -> RoundedCornerShape(
            topStart = innerRadius,
            topEnd = innerRadius,
            bottomStart = outerRadius,
            bottomEnd = outerRadius
        )
        else -> RoundedCornerShape(innerRadius)
    }
}

@Composable
internal fun ConnectedSectionGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        TweaksSectionHeader(title = title)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = content
        )
    }
}

@Composable
internal fun ConnectedTweakSwitchRow(
    index: Int,
    count: Int,
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val shape = connectedContainerShape(index = index, count = count)
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressScaleModifier = pressScale(interactionSource, 0.975f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(pressScaleModifier)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                LuminaHapticEngine.perform(context, LocalHapticConfig.current, LuminaHapticPulse.Tick)
                onCheckedChange(!checked)
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.padding(end = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            TweakSquircleIcon(icon = icon)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            modifier = Modifier.padding(start = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                interactionSource = interactionSource,
                thumbContent = {
                    if (checked) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(SwitchDefaults.IconSize)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.size(SwitchDefaults.IconSize)
                        )
                    }
                }
            )
        }
    }
}

@Composable
internal fun ConnectedTweakActionRow(
    index: Int,
    count: Int,
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    highlightSubtitle: Boolean = false
) {
    val haptic = LocalHapticFeedback.current
    val shape = connectedContainerShape(index = index, count = count)
    val interactionSource = remember { MutableInteractionSource() }
    val pressScaleModifier = pressScale(interactionSource, 0.975f, enabled)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(pressScaleModifier)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .alpha(if (enabled) 1f else 0.55f)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.padding(end = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            TweakSquircleIcon(icon = icon, enabled = enabled)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (highlightSubtitle) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        Box(
            modifier = Modifier.padding(start = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (enabled) Icons.Rounded.ChevronRight else Icons.Rounded.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun NestedTweakCardGroup(
    headerIcon: ImageVector,
    headerTitle: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    ConnectedSectionGroup(
        title = headerTitle,
        modifier = modifier,
        content = content
    )
}

@Composable
internal fun NestedTweakSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ConnectedTweakSwitchRow(
        index = 0,
        count = 1,
        icon = icon,
        title = title,
        subtitle = subtitle,
        checked = checked,
        onCheckedChange = onCheckedChange
    )
}

@Composable
internal fun NestedTweakActionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    ConnectedTweakActionRow(
        index = 0,
        count = 1,
        icon = icon,
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        enabled = enabled
    )
}

@Composable
internal fun NestedTweakCompactCard(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    ExpressiveTweakTile(
        modifier = modifier,
        icon = icon,
        label = title,
        value = value,
        highlight = enabled,
        enabled = enabled,
        onClick = onClick
    )
}
