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

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.luminatweaks.ui.screen

import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luminatweaks.R
import com.luminatweaks.data.ConfigRepository
import com.luminatweaks.data.DisplayRrMode
import com.luminatweaks.ui.theme.LocalCornerRadius
import com.luminatweaks.ui.theme.LocalFloatingNavBar
import com.luminatweaks.util.DeviceUtil
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
// Soft connected-group geometry (MD3 Expressive, self-contained)
// ---------------------------------------------------------------------------

private enum class SoftPosition { Top, Middle, Bottom, Single }

private fun softContainerShape(position: SoftPosition): RoundedCornerShape {
    val outer = 22.dp
    val inner = 7.dp
    return when (position) {
        SoftPosition.Top -> RoundedCornerShape(
            topStart = outer, topEnd = outer, bottomStart = inner, bottomEnd = inner
        )
        SoftPosition.Middle -> RoundedCornerShape(inner)
        SoftPosition.Bottom -> RoundedCornerShape(
            topStart = inner, topEnd = inner, bottomStart = outer, bottomEnd = outer
        )
        SoftPosition.Single -> RoundedCornerShape(outer)
    }
}

// ---------------------------------------------------------------------------
// Small helpers
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

/**
 * Applies a tweak optimistically. Supports repository functions returning
 * either Unit (throws on failure) or Boolean (false on failure); on failure
 * the UI state is reverted and a snackbar is shown.
 */
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TweaksScreen() {
    val context = LocalContext.current
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

    var showRefreshRateDialog by rememberSaveable { mutableStateOf(false) }
    var showRenderDialog by rememberSaveable { mutableStateOf(false) }
    var showIoSchedulerDialog by rememberSaveable { mutableStateOf(false) }
    var inLiteModeSubpage by rememberSaveable { mutableStateOf(false) }
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
            val hz = currentDisplayHz(context, windowManager)
            if (hz != null && hz > 0) {
                liveRefreshRate = hz
            }
            delay(1200L)
        }
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            delay(3000L)
            val fresh = withContext(Dispatchers.IO) {
                ConfigRepository.getTweaksState(forceRefresh = true)
            }
            isGlobalTextureEnabled = fresh.isGlobalTextureEnabled
        }
    }

    val currentRrText = remember(liveRefreshRate, currentRrId, rrModes) {
        val targetMode = rrModes.find { it.id == currentRrId }
        if (targetMode != null && targetMode.hz != liveRefreshRate) {
            "${liveRefreshRate} Hz (${targetMode.hz} Hz)"
        } else {
            "${liveRefreshRate} Hz"
        }
    }

    val currentRendererShortText = remember(currentRendererId, defaultRendererLabel) {
        getRendererShortLabel(currentRendererId, defaultRendererLabel)
    }

    val effectiveRendererText = if (isGlobalTextureEnabled) {
        stringResource(R.string.profile_renderer_angle_badge)
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

    if (inLiteModeSubpage) {
        BackHandler { inLiteModeSubpage = false }
        LiteModeScreen(
            onBack = { inLiteModeSubpage = false },
            isLiteMode = isGlobalLiteMode,
            onLiteModeChange = { newState ->
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

    // ---- Dialogs -----------------------------------------------------------

    if (showRefreshRateDialog) {
        val rrSubtitle = if (currentRrId == null) {
            "$liveRefreshRate Hz · $defaultDynamicLabel"
        } else {
            localizedLabel(
                "Layar saat ini: $liveRefreshRate Hz",
                "Current display: $liveRefreshRate Hz"
            )
        }
        TweakOptionDialog(
            title = stringResource(R.string.tweaks_dialog_select_rr),
            subtitle = rrSubtitle,
            options = rrModes.map { TweakOption(it.id, "${it.hz} Hz") },
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
            },
            onDismiss = { showRefreshRateDialog = false }
        )
    }

    if (showRenderDialog) {
        TweakOptionDialog(
            title = stringResource(R.string.tweaks_dialog_select_renderer),
            options = rendererOptions,
            isSelected = { it.equals(currentRendererId, ignoreCase = true) },
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
            },
            onDismiss = { showRenderDialog = false }
        )
    }

    if (showIoSchedulerDialog) {
        TweakOptionDialog(
            title = stringResource(R.string.dialog_select_io_scheduler),
            options = ioSchedulerOptions.map { TweakOption(it, it) },
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
            },
            onDismiss = { showIoSchedulerDialog = false }
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
        },
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            text = stringResource(R.string.tweaks_title),
                            style = MaterialTheme.typography.titleLargeEmphasized,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = localizedLabel(
                                "Kontrol performa global",
                                "Global performance controls"
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    LiveRefreshPill(
                        hz = liveRefreshRate,
                        modifier = Modifier.padding(end = 12.dp)
                    )
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
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = bottomPadding,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item(key = "active_global_summary") {
                ActiveGlobalSummaryCard(
                    refreshRateText = currentRrText,
                    rendererText = effectiveRendererText,
                    isTextureActive = isGlobalTextureEnabled,
                    isLiteModeActive = isGlobalLiteMode,
                    liteModeText = if (isGlobalLiteMode) {
                        stringResource(R.string.status_on)
                    } else {
                        stringResource(R.string.status_off)
                    },
                    ioSchedulerText = currentIoScheduler,
                    isCustomRr = currentRrId != null,
                    isCustomRenderer = isGlobalTextureEnabled ||
                        !currentRendererId.equals("default", ignoreCase = true),
                    onRefreshRateClick = { showRefreshRateDialog = true },
                    onRendererClick = {
                        if (!isGlobalTextureEnabled) showRenderDialog = true
                    },
                    onLiteModeClick = { inLiteModeSubpage = true },
                    onIoSchedulerClick = { showIoSchedulerDialog = true }
                )
            }

            item(key = "display_performance") {
                TweakSection(
                    title = stringResource(R.string.tweaks_section_performance),
                    icon = Icons.Rounded.Speed,
                    accent = MaterialTheme.colorScheme.primary
                ) {
                    TweakConnectedGroup {
                        TweakRow(
                            icon = Icons.Rounded.Speed,
                            iconContainer = MaterialTheme.colorScheme.primaryContainer,
                            iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                            title = stringResource(R.string.tweaks_refresh_rate),
                            subtitle = stringResource(R.string.tweaks_dialog_select_rr),
                            trailingText = currentRrText,
                            trailingActive = currentRrId != null,
                            position = SoftPosition.Top,
                            onClick = { showRefreshRateDialog = true }
                        )
                        TweakRow(
                            icon = if (isGlobalTextureEnabled) Icons.Rounded.Lock else Icons.Rounded.DeveloperBoard,
                            iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                            iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                            title = stringResource(R.string.tweaks_renderer),
                            subtitle = if (isGlobalTextureEnabled) {
                                stringResource(R.string.tweaks_renderer_locked_note)
                            } else {
                                stringResource(R.string.tweaks_dialog_select_renderer)
                            },
                            trailingText = effectiveRendererText,
                            trailingActive = isGlobalTextureEnabled ||
                                !currentRendererId.equals("default", ignoreCase = true),
                            enabled = !isGlobalTextureEnabled,
                            position = SoftPosition.Middle,
                            onClick = { showRenderDialog = true }
                        )
                        TweakRow(
                            icon = Icons.Rounded.BatterySaver,
                            iconContainer = MaterialTheme.colorScheme.tertiaryContainer,
                            iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                            title = stringResource(R.string.tweaks_lite_mode),
                            subtitle = if (isGlobalLiteMode) {
                                stringResource(R.string.tweaks_lite_on_desc)
                            } else {
                                stringResource(R.string.tweaks_lite_off_desc)
                            },
                            trailingText = if (isGlobalLiteMode) {
                                stringResource(R.string.status_on)
                            } else {
                                stringResource(R.string.status_off)
                            },
                            trailingActive = isGlobalLiteMode,
                            position = SoftPosition.Bottom,
                            onClick = { inLiteModeSubpage = true }
                        )
                    }
                }
            }

            item(key = "additional") {
                TweakSection(
                    title = stringResource(R.string.section_additional_tweaks),
                    icon = Icons.Rounded.Tune,
                    accent = MaterialTheme.colorScheme.secondary
                ) {
                    TweakConnectedGroup {
                        TweakRow(
                            icon = Icons.Rounded.Memory,
                            iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                            iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                            title = stringResource(R.string.tweak_memory_zram),
                            subtitle = stringResource(R.string.tweak_memory_zram_desc),
                            position = SoftPosition.Top,
                            onClick = { inMemoryZramSubpage = true }
                        )
                        TweakRow(
                            icon = Icons.Rounded.Storage,
                            iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                            iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                            title = stringResource(R.string.tweak_io_scheduler),
                            subtitle = stringResource(R.string.tweak_io_scheduler_desc),
                            trailingText = currentIoScheduler,
                            position = SoftPosition.Bottom,
                            onClick = { showIoSchedulerDialog = true }
                        )
                    }
                }
            }

            item(key = "utility") {
                TweakSection(
                    title = stringResource(R.string.section_utility_diagnostics),
                    icon = Icons.Rounded.Widgets,
                    accent = MaterialTheme.colorScheme.tertiary
                ) {
                    TweakConnectedGroup {
                        TweakRow(
                            icon = Icons.Rounded.DeveloperBoard,
                            iconContainer = MaterialTheme.colorScheme.primaryContainer,
                            iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                            title = stringResource(R.string.hardware_diag_title),
                            subtitle = stringResource(R.string.hardware_diag_desc),
                            position = SoftPosition.Top,
                            onClick = { inHardwareDiagnosticSubpage = true }
                        )
                        TweakRow(
                            icon = Icons.Rounded.Widgets,
                            iconContainer = MaterialTheme.colorScheme.primaryContainer,
                            iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                            title = stringResource(R.string.utility_screen_title),
                            subtitle = stringResource(R.string.utility_screen_desc),
                            position = SoftPosition.Bottom,
                            onClick = { inUtilitySubpage = true }
                        )
                    }
                }
            }

            item(key = "global_footnote") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.65f))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = localizedLabel(
                            "Pengaturan global menimpa profil per-aplikasi. Renderer terkunci selama mode ANGLE aktif.",
                            "Global settings override per-app profiles. The renderer stays locked while ANGLE mode is active."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Live refresh-rate pill (top bar)
// ---------------------------------------------------------------------------

@Composable
private fun LiveRefreshPill(hz: Int, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "livePulse")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "livePulseAlpha"
    )
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = pulse))
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = "$hz Hz",
                style = MaterialTheme.typography.labelMediumEmphasized,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Hero summary card
// ---------------------------------------------------------------------------

@Composable
private fun ActiveGlobalSummaryCard(
    refreshRateText: String,
    rendererText: String,
    isTextureActive: Boolean,
    isLiteModeActive: Boolean,
    liteModeText: String,
    ioSchedulerText: String,
    isCustomRr: Boolean,
    isCustomRenderer: Boolean,
    onRefreshRateClick: () -> Unit,
    onRendererClick: () -> Unit,
    onLiteModeClick: () -> Unit,
    onIoSchedulerClick: () -> Unit
) {
    val headerTitle = localizedLabel("Pengaturan Global Aktif", "Active Global Settings")
    val headerCaption = localizedLabel("Berlaku untuk semua aplikasi", "Applies to all apps")
    val activeCount = listOf(isCustomRr, isCustomRenderer, isLiteModeActive).count { it }
    val statusBadgeText = if (activeCount > 0) {
        localizedLabel("$activeCount Kustom", "$activeCount Active")
    } else {
        localizedLabel("Standar", "Default")
    }
    val badgeContainer by animateColorAsState(
        targetValue = if (activeCount > 0) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        label = "summaryBadgeContainer"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(LocalCornerRadius.current.coerceAtLeast(26.dp)),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.50f),
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.24f),
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.10f)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = headerTitle,
                            style = MaterialTheme.typography.titleMediumEmphasized,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = headerCaption,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(shape = CircleShape, color = badgeContainer) {
                        Text(
                            text = statusBadgeText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (activeCount > 0) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            maxLines = 1
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GlobalStatusMiniTile(
                            icon = Icons.Rounded.Speed,
                            label = stringResource(R.string.tweaks_refresh_rate),
                            value = refreshRateText,
                            highlighted = isCustomRr,
                            onClick = onRefreshRateClick,
                            modifier = Modifier.weight(1f)
                        )
                        GlobalStatusMiniTile(
                            icon = if (isTextureActive) Icons.Rounded.Lock else Icons.Rounded.DeveloperBoard,
                            label = stringResource(R.string.tweaks_renderer),
                            value = rendererText,
                            highlighted = isCustomRenderer,
                            enabled = !isTextureActive,
                            onClick = onRendererClick,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GlobalStatusMiniTile(
                            icon = Icons.Rounded.BatterySaver,
                            label = stringResource(R.string.tweaks_lite_mode),
                            value = liteModeText,
                            highlighted = isLiteModeActive,
                            onClick = onLiteModeClick,
                            modifier = Modifier.weight(1f)
                        )
                        GlobalStatusMiniTile(
                            icon = Icons.Rounded.Storage,
                            label = stringResource(R.string.tweak_io_scheduler),
                            value = ioSchedulerText,
                            highlighted = false,
                            onClick = onIoSchedulerClick,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GlobalStatusMiniTile(
    icon: ImageVector,
    label: String,
    value: String,
    highlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "tileScale"
    )
    val containerColor by animateColorAsState(
        targetValue = if (highlighted) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        label = "tileContainer"
    )

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        border = if (highlighted) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
        } else {
            null
        },
        interactionSource = interactionSource,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (highlighted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (highlighted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Section + connected rows
// ---------------------------------------------------------------------------

@Composable
private fun TweakSection(
    title: String,
    icon: ImageVector,
    accent: Color,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(15.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLargeEmphasized.copy(letterSpacing = 0.3.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        content()
    }
}

@Composable
private fun TweakConnectedGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp),
        content = content
    )
}

@Composable
private fun TweakRow(
    icon: ImageVector,
    iconContainer: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    position: SoftPosition = SoftPosition.Single,
    trailingText: String? = null,
    trailingActive: Boolean = false,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "rowScale"
    )

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = softContainerShape(position),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
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
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(iconContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (trailingText != null) {
                TweakValueChip(text = trailingText, active = trailingActive)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Icon(
                imageVector = if (enabled) Icons.Rounded.ChevronRight else Icons.Rounded.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(if (enabled) 20.dp else 17.dp)
            )
        }
    }
}

@Composable
private fun TweakValueChip(text: String, active: Boolean = false) {
    val container by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        label = "chipContainer"
    )
    Surface(shape = CircleShape, color = container) {
        Text(
            text = text,
            modifier = Modifier
                .widthIn(max = 110.dp)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (active) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ---------------------------------------------------------------------------
// Option dialog (MD3E segmented list)
// ---------------------------------------------------------------------------

@Composable
private fun <T> TweakOptionDialog(
    title: String,
    options: List<TweakOption<T>>,
    isSelected: (T) -> Boolean,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    subtitle: String? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLargeEmphasized
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
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
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
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
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}