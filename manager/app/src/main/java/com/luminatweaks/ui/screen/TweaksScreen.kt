@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package com.luminatweaks.ui.screen

import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.luminatweaks.R
import com.luminatweaks.data.ConfigRepository
import com.luminatweaks.data.DisplayRrMode
import com.luminatweaks.service.FloatingFpsService
import com.luminatweaks.ui.component.AospChevron
import com.luminatweaks.ui.component.AospConnectedGroup
import com.luminatweaks.ui.component.AospOptionBottomSheet
import com.luminatweaks.ui.component.AospPreferenceItem
import com.luminatweaks.ui.component.AospPreferenceSwitch
import com.luminatweaks.ui.component.AospSectionHeader
import com.luminatweaks.ui.component.AospSheetOption
import com.luminatweaks.ui.component.AospTopBar
import com.luminatweaks.ui.component.aospConnectedShape
import com.luminatweaks.ui.component.aospGroupOuterRadius
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
            AospSheetOption(it.id, getRendererDisplayLabel(it.id, defaultRendererLabel))
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

    if (showRefreshRateSheet) {
        val rrSubtitle = if (currentRrId == null) {
            "$liveRefreshRate Hz · $defaultDynamicLabel"
        } else {
            localizedLabel(
                "Layar saat ini: $liveRefreshRate Hz",
                "Current display: $liveRefreshRate Hz"
            )
        }
        AospOptionBottomSheet(
            title = stringResource(R.string.tweaks_dialog_select_rr),
            subtitle = rrSubtitle,
            options = rrModes.map { AospSheetOption(it.id, "${it.hz} Hz") },
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
        AospOptionBottomSheet(
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
        AospOptionBottomSheet(
            title = stringResource(R.string.dialog_select_io_scheduler),
            options = ioSchedulerOptions.map { AospSheetOption(it, it) },
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

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val isFloating = LocalFloatingNavBar.current
    val bottomPadding = if (isFloating) {
        104.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    } else {
        24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0.dp),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AospTopBar(
                title = stringResource(R.string.tweaks_title),
                subtitle = localizedLabel(
                    "Sesuaikan performa dan tampilan",
                    "Fine-tune performance and display"
                ),
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
                top = innerPadding.calculateTopPadding() + 4.dp,
                bottom = bottomPadding,
                start = 0.dp,
                end = 0.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "display_performance") {
                Column {
                    AospSectionHeader(
                        title = localizedLabel("Performa & Layar", "Performance & Display")
                    )
                    AospConnectedGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
                        AospPreferenceSwitch(
                            index = 0,
                            count = 3,
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

                        AospPreferenceItem(
                            index = 1,
                            count = 3,
                            icon = Icons.Rounded.Speed,
                            title = stringResource(R.string.tweaks_refresh_rate),
                            subtitle = currentRrText,
                            highlightSubtitle = true,
                            onClick = { showRefreshRateSheet = true },
                            trailing = { AospChevron() }
                        )

                        AospPreferenceItem(
                            index = 2,
                            count = 3,
                            icon = if (isGlobalTextureEnabled) Icons.Rounded.Lock else Icons.Rounded.DeveloperBoard,
                            title = stringResource(R.string.tweaks_renderer),
                            subtitle = effectiveRendererText,
                            highlightSubtitle = !isGlobalTextureEnabled,
                            enabled = !isGlobalTextureEnabled,
                            onClick = { showRenderSheet = true },
                            trailing = {
                                if (!isGlobalTextureEnabled) {
                                    AospChevron()
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        )
                    }
                }
            }

            item(key = "additional") {
                Column {
                    AospSectionHeader(title = stringResource(R.string.section_additional_tweaks))
                    AospConnectedGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
                        AospPreferenceItem(
                            index = 0,
                            count = 2,
                            icon = Icons.Rounded.Memory,
                            title = stringResource(R.string.tweak_memory_zram),
                            subtitle = stringResource(R.string.tweak_memory_zram_desc),
                            onClick = { inMemoryZramSubpage = true },
                            trailing = { AospChevron() }
                        )

                        AospPreferenceItem(
                            index = 1,
                            count = 2,
                            icon = Icons.Rounded.Storage,
                            title = stringResource(R.string.tweak_io_scheduler),
                            subtitle = currentIoScheduler,
                            highlightSubtitle = true,
                            onClick = { showIoSchedulerSheet = true },
                            trailing = { AospChevron() }
                        )
                    }
                }
            }

            item(key = "utility") {
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

                Column {
                    AospSectionHeader(title = stringResource(R.string.section_utility_diagnostics))
                    AospConnectedGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
                        AospPreferenceItem(
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
                            },
                            trailing = { AospChevron() }
                        )

                        AospPreferenceItem(
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
                            },
                            trailing = { AospChevron() }
                        )

                        AospPreferenceItem(
                            index = 2,
                            count = 4,
                            icon = Icons.Rounded.DeveloperBoard,
                            title = stringResource(R.string.hardware_diag_title),
                            subtitle = stringResource(R.string.hardware_diag_desc),
                            onClick = { inHardwareDiagnosticSubpage = true },
                            trailing = { AospChevron() }
                        )

                        AospPreferenceItem(
                            index = 3,
                            count = 4,
                            icon = Icons.Rounded.Widgets,
                            title = stringResource(R.string.utility_screen_title),
                            subtitle = stringResource(R.string.utility_screen_desc),
                            onClick = { inUtilitySubpage = true },
                            trailing = { AospChevron() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun connectedContainerShape(
    index: Int,
    count: Int,
    outerRadius: Dp = aospGroupOuterRadius(),
    innerRadius: Dp = 5.dp
): Shape = aospConnectedShape(index = index, count = count, outerRadius = outerRadius)

@Composable
internal fun ConnectedSectionGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        AospSectionHeader(title = title)
        AospConnectedGroup(content = content)
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
    AospPreferenceSwitch(
        index = index,
        count = count,
        icon = icon,
        title = title,
        subtitle = subtitle,
        checked = checked,
        onCheckedChange = onCheckedChange
    )
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
    AospPreferenceItem(
        index = index,
        count = count,
        icon = icon,
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        enabled = enabled,
        highlightSubtitle = highlightSubtitle,
        trailing = { AospChevron() }
    )
}

@Composable
internal fun NestedTweakCardGroup(
    headerIcon: ImageVector,
    headerTitle: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        AospSectionHeader(title = headerTitle)
        AospConnectedGroup(content = content)
    }
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
    AospPreferenceItem(
        index = 0,
        count = 1,
        icon = icon,
        title = title,
        subtitle = value,
        onClick = onClick,
        enabled = enabled,
        highlightSubtitle = enabled,
        modifier = modifier,
        trailing = { AospChevron() }
    )
}