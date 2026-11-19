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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.luminatweaks.R
import com.luminatweaks.data.ConfigRepository
import com.luminatweaks.data.DisplayRrMode
import com.luminatweaks.service.FloatingFpsService
import com.luminatweaks.ui.theme.LocalFloatingNavBar
import com.luminatweaks.util.DeviceUtil
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

private data class TweakSheetOption<T>(
    val value: T,
    val label: String
)

@Composable
internal fun localizedLabel(id: String, en: String): String {
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

private suspend fun awaitPagerSettled(pagerState: PagerState?) {
    pagerState ?: return
    snapshotFlow { pagerState.isScrollInProgress }.first { !it }
}

@Composable
fun TweaksScreen(
    pagerState: PagerState? = null,
    onOpenFloatingHud: (() -> Unit)? = null,
    onOpenSessionStats: (() -> Unit)? = null
) {
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
    var inSessionStatsSubpage by remember { mutableStateOf(false) }
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
            TweakSheetOption(it.id, getRendererDisplayLabel(it.id, defaultRendererLabel))
        }
    }

    val ioSchedulerOptions = remember(availableIoSchedulers, currentIoScheduler) {
        (availableIoSchedulers + currentIoScheduler)
            .filter { it.isNotBlank() }
            .distinct()
    }

    if (inSessionStatsSubpage) {
        SessionStatsScreen(onBack = { inSessionStatsSubpage = false })
        return
    }

    if (inFloatingFpsSubpage) {
        BackHandler { inFloatingFpsSubpage = false }
        TelemetryOverlayScreen(
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

    val rrSubtitle = if (currentRrId == null) {
        "$liveRefreshRate Hz · $defaultDynamicLabel"
    } else {
        localizedLabel(
            "Layar saat ini: $liveRefreshRate Hz",
            "Current display: $liveRefreshRate Hz"
        )
    }

    AZenithOptionSheet(
        show = showRefreshRateSheet,
        title = stringResource(R.string.tweaks_dialog_select_rr),
        subtitle = rrSubtitle,
        options = rrModes.map { TweakSheetOption(it.id, "${it.hz} Hz") },
        isSelected = { it == currentRrId },
        onSelect = { id ->
            val previous = currentRrId
            currentRrId = id
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

    AZenithOptionSheet(
        show = showRenderSheet,
        title = stringResource(R.string.tweaks_dialog_select_renderer),
        options = rendererOptions,
        isSelected = { it.equals(currentRendererId, ignoreCase = true) },
        onSelect = { rawId ->
            val previous = currentRendererId
            currentRendererId = rawId
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

    AZenithOptionSheet(
        show = showIoSchedulerSheet,
        title = stringResource(R.string.dialog_select_io_scheduler),
        options = ioSchedulerOptions.map { TweakSheetOption(it, it) },
        isSelected = { it.equals(currentIoScheduler, ignoreCase = true) },
        onSelect = { scheduler ->
            val previous = currentIoScheduler
            currentIoScheduler = scheduler
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

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    val isFloating = LocalFloatingNavBar.current
    val bottomPadding = if (isFloating) {
        112.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    } else {
        28.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0.dp),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TweaksFlexibleTopBar(
                title = stringResource(R.string.tweaks_title),
                scrollBehavior = scrollBehavior
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
                top = innerPadding.calculateTopPadding(),
                bottom = bottomPadding,
                start = 16.dp,
                end = 16.dp
            )
        ) {
            item(key = "info_banner") {
                Spacer(modifier = Modifier.height(12.dp))
                TweaksInfoBanner(
                    text = localizedLabel(
                        "Pengaturan di bawah berlaku secara global sebagai default, dan dapat disesuaikan terpisah untuk tiap aplikasi",
                        "Settings below apply globally as defaults and can be configured individually per app"
                    )
                )
            }

            item(key = "header_performance") {
                TweaksSectionHeader(
                    title = localizedLabel("Performa", "Performance")
                )
            }

            item(key = "group_performance") {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    ConnectedTweakSwitchRow(
                        index = 0,
                        count = 1,
                        icon = Icons.Rounded.BatterySaver,
                        title = stringResource(R.string.tweaks_lite_mode),
                        subtitle = localizedLabel(
                            "Batasi frekuensi maksimum CPU untuk suhu lebih adem dan hemat daya",
                            "Limit maximum CPU frequency for cooler thermals and power saving"
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
                }
            }

            item(key = "display_tiles") {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ExpressiveTweakTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.Speed,
                        label = stringResource(R.string.tweaks_refresh_rate),
                        value = currentRrText,
                        highlight = true,
                        onClick = { showRefreshRateSheet = true }
                    )

                    ExpressiveTweakTile(
                        modifier = Modifier.weight(1f),
                        icon = if (isGlobalTextureEnabled) Icons.Rounded.Lock else Icons.Rounded.DeveloperBoard,
                        label = stringResource(R.string.tweaks_renderer),
                        value = effectiveRendererText,
                        highlight = !isGlobalTextureEnabled,
                        enabled = !isGlobalTextureEnabled,
                        onClick = { showRenderSheet = true }
                    )
                }
            }

            item(key = "header_additional") {
                TweaksSectionHeader(
                    title = stringResource(R.string.section_additional_tweaks)
                )
            }

            item(key = "group_additional") {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    ConnectedTweakActionRow(
                        index = 0,
                        count = 2,
                        icon = Icons.Rounded.Memory,
                        title = stringResource(R.string.tweak_memory_zram),
                        subtitle = stringResource(R.string.tweak_memory_zram_desc),
                        onClick = { inMemoryZramSubpage = true }
                    )

                    ConnectedTweakActionRow(
                        index = 1,
                        count = 2,
                        icon = Icons.Rounded.Storage,
                        title = stringResource(R.string.tweak_io_scheduler),
                        subtitle = currentIoScheduler,
                        highlightSubtitle = true,
                        onClick = { showIoSchedulerSheet = true }
                    )
                }
            }

            item(key = "header_utility") {
                TweaksSectionHeader(
                    title = stringResource(R.string.section_utility_diagnostics)
                )
            }

            item(key = "group_utility") {
                val telemetrySubtitle = if (fpsConfig.enabled) {
                    val count = fpsConfig.activeModuleCount.coerceAtLeast(1)
                    localizedLabel(
                        "Aktif · ${fpsStyleLabel(fpsConfig.style)} · $count metrik",
                        "Active · ${fpsStyleLabel(fpsConfig.style)} · $count metrics"
                    )
                } else {
                    localizedLabel(
                        "Overlay metrik sistem dan performa real-time",
                        "Real-time system and performance metrics overlay"
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    ConnectedTweakActionRow(
                        index = 0,
                        count = 4,
                        icon = Icons.Rounded.Speed,
                        title = "Floating HUD",
                        subtitle = telemetrySubtitle,
                        highlightSubtitle = fpsConfig.enabled,
                        onClick = {
                            if (onOpenFloatingHud != null) {
                                onOpenFloatingHud()
                            } else {
                                inFloatingFpsSubpage = true
                            }
                        }
                    )

                    ConnectedTweakActionRow(
                        index = 1,
                        count = 4,
                        icon = Icons.Rounded.Insights,
                        title = localizedLabel("Statistik Sesi", "Session Stats"),
                        subtitle = localizedLabel(
                            "Riwayat FPS, stabilitas frame & suhu saat bermain",
                            "FPS history, frame stability & thermal logs during gameplay"
                        ),
                        onClick = {
                            if (onOpenSessionStats != null) {
                                onOpenSessionStats()
                            } else {
                                inSessionStatsSubpage = true
                            }
                        }
                    )

                    ConnectedTweakActionRow(
                        index = 2,
                        count = 4,
                        icon = Icons.Rounded.DeveloperBoard,
                        title = stringResource(R.string.hardware_diag_title),
                        subtitle = stringResource(R.string.hardware_diag_desc),
                        onClick = { inHardwareDiagnosticSubpage = true }
                    )

                    ConnectedTweakActionRow(
                        index = 3,
                        count = 4,
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

@Composable
private fun TweaksFlexibleTopBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
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
private fun TweaksInfoBanner(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TweakSquircleIcon(icon = Icons.Rounded.Info)
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
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
            top = 16.dp,
            bottom = 8.dp
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
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
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
        color = colorScheme.surfaceColorAtElevation(1.dp),
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
private fun <T> AZenithOptionSheet(
    show: Boolean,
    title: String,
    options: List<TweakSheetOption<T>>,
    isSelected: (T) -> Boolean,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    subtitle: String? = null
) {
    if (!show) return

    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val scheme = MaterialTheme.colorScheme

    val hideSheet: (() -> Unit) -> Unit = { afterHide ->
        scope.launch {
            try {
                sheetState.hide()
            } catch (_: CancellationException) {
                return@launch
            }
            if (!sheetState.isVisible) {
                currentOnDismiss()
                afterHide()
            }
        }
    }

    val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    ModalBottomSheet(
        onDismissRequest = currentOnDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        scrimColor = if (scheme.surfaceContainerLowest == Color.Black) {
            Color.Black.copy(alpha = 0.5f)
        } else {
            scheme.scrim.copy(alpha = 0.32f)
        },
        shape = sheetShape,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0.dp) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(sheetShape)
                .background(scheme.surfaceContainerLow)
                .navigationBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 12.dp, bottom = 4.dp)
                    .width(32.dp)
                    .height(4.dp)
                    .background(scheme.onSurfaceVariant.copy(alpha = 0.4f), CircleShape)
            )

            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = scheme.onSurface,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp)
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 4.dp)
                )
            }

            if (options.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp),
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
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    itemsIndexed(options) { index, option ->
                        val selected = isSelected(option.value)
                        val rowShape = RoundedCornerShape(
                            topStart = if (index == 0) 20.dp else 0.dp,
                            topEnd = if (index == 0) 20.dp else 0.dp,
                            bottomStart = if (index == options.lastIndex) 20.dp else 0.dp,
                            bottomEnd = if (index == options.lastIndex) 20.dp else 0.dp
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(rowShape)
                                .background(
                                    if (selected) scheme.secondaryContainer else scheme.surfaceContainer
                                )
                                .clickable {
                                    hideSheet {}
                                    onSelect(option.value)
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
                                ),
                                color = if (selected) scheme.onSecondaryContainer else scheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            SheetRadioRing(selected = selected)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetRadioRing(selected: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val ringColor = if (selected) scheme.primary else scheme.onSurfaceVariant

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
            if (selected) {
                drawCircle(
                    color = scheme.primary,
                    radius = (size.width / 2f) * 0.3f
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
            .clickable { onCheckedChange(!checked) }
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
            .clickable(enabled = enabled) {
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
