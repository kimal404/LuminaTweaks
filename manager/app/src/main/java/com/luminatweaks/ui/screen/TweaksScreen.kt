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

package com.luminatweaks.ui.screen

import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    return when (id.lowercase()) {
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
    return when (id.lowercase()) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TweaksScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val cached = ConfigRepository.cachedTweaksState
    var isGlobalLiteMode by remember { mutableStateOf(cached?.isGlobalLiteMode ?: false) }
    var isGlobalTextureEnabled by remember { mutableStateOf(cached?.isGlobalTextureEnabled ?: false) }

    var rrModes by remember { mutableStateOf<List<DisplayRrMode>>(cached?.rrModes ?: emptyList()) }
    var currentRrId by remember { mutableStateOf<Int?>(cached?.currentRrId) }
    var liveRefreshRate by remember { mutableIntStateOf(120) }

    var currentRendererId by remember { mutableStateOf(cached?.currentRendererId ?: "default") }
    var currentIoScheduler by remember { mutableStateOf(cached?.currentIoScheduler ?: "mq-deadline") }
    var availableIoSchedulers by remember { mutableStateOf(emptyList<String>()) }

    var showRefreshRateDialog by remember { mutableStateOf(false) }
    var showRenderDialog by remember { mutableStateOf(false) }
    var showIoSchedulerDialog by remember { mutableStateOf(false) }

    var inLiteModeSubpage by remember { mutableStateOf(false) }
    var inMemoryZramSubpage by remember { mutableStateOf(false) }
    var inHardwareDiagnosticSubpage by remember { mutableStateOf(false) }
    var inUtilitySubpage by remember { mutableStateOf(false) }

    val defaultDynamicLabel = stringResource(R.string.tweaks_rr_default_dynamic)
    val defaultRendererLabel = stringResource(R.string.tweaks_renderer_system_default)

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
            val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runCatching { context.display }.getOrNull() ?: windowManager?.defaultDisplay
            } else {
                @Suppress("DEPRECATION")
                windowManager?.defaultDisplay
            }
            val currentHz = display?.refreshRate?.roundToInt() ?: 60
            if (currentHz > 0) {
                liveRefreshRate = currentHz
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

    val currentRrText = remember(liveRefreshRate, currentRrId, rrModes, defaultDynamicLabel) {
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

    val rendererOptions = remember(defaultRendererLabel) {
        listOf(
            "default",
            "skiavk",
            "skiavkthreaded",
            "skiagl",
            "skiaglthreaded",
            "opengl",
            "openglthreaded",
            "vulkan"
        ).map { TweakOption(it, getRendererDisplayLabel(it, defaultRendererLabel)) }
    }

    val ioSchedulerOptions = remember(availableIoSchedulers, currentIoScheduler) {
        (availableIoSchedulers + currentIoScheduler)
            .filter { it.isNotBlank() }
            .distinct()
    }

    if (inLiteModeSubpage) {
        BackHandler { inLiteModeSubpage = false }
        LiteModeScreen(
            onBack = { inLiteModeSubpage = false },
            isLiteMode = isGlobalLiteMode,
            onLiteModeChange = { newState ->
                isGlobalLiteMode = newState
                coroutineScope.launch {
                    ConfigRepository.setGlobalLiteMode(newState)
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

    if (showRefreshRateDialog) {
        TweakOptionDialog(
            title = stringResource(R.string.tweaks_dialog_select_rr),
            options = rrModes.map { TweakOption(it.id, "${it.hz} Hz") },
            isSelected = { it == currentRrId },
            onSelect = { id ->
                currentRrId = id
                showRefreshRateDialog = false
                coroutineScope.launch {
                    ConfigRepository.setRefreshRate(id)
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
                currentRendererId = rawId
                showRenderDialog = false
                coroutineScope.launch {
                    ConfigRepository.setRenderer(rawId)
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
                currentIoScheduler = scheduler
                showIoSchedulerDialog = false
                coroutineScope.launch {
                    ConfigRepository.setIoScheduler(scheduler)
                }
            },
            onDismiss = { showIoSchedulerDialog = false }
        )
    }

    val isFloating = LocalFloatingNavBar.current
    val bottomPadding = if (isFloating) {
        100.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    } else {
        16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    MaterialExpressiveTheme(
        colorScheme = MaterialTheme.colorScheme,
        shapes = MaterialTheme.shapes,
        typography = MaterialTheme.typography,
        motionScheme = MotionScheme.expressive()
    ) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            contentWindowInsets = WindowInsets(0.dp),
            topBar = {
                MediumFlexibleTopAppBar(
                    title = { Text(stringResource(R.string.tweaks_title)) },
                    subtitle = { Text(stringResource(R.string.tweaks_hero_desc)) },
                    scrollBehavior = scrollBehavior,
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
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                item {
                    TweaksHero(
                        liveRefreshRate = liveRefreshRate,
                        rrText = currentRrText,
                        rendererLabel = if (isGlobalTextureEnabled) {
                            stringResource(R.string.profile_renderer_angle_badge)
                        } else {
                            currentRendererShortText
                        },
                        isLiteMode = isGlobalLiteMode
                    )
                }

                item {
                    TweakSection(
                        title = stringResource(R.string.tweaks_section_display_graphics),
                        icon = Icons.Rounded.Speed,
                        accent = MaterialTheme.colorScheme.primary
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
                        ) {
                            SegmentedSettingRow(
                                index = 0,
                                count = 2,
                                icon = Icons.Rounded.Speed,
                                title = stringResource(R.string.tweaks_refresh_rate),
                                subtitle = stringResource(R.string.tweaks_refresh_rate_desc),
                                valueText = currentRrText,
                                valueActive = false,
                                locked = false,
                                onClick = { showRefreshRateDialog = true }
                            )
                            SegmentedSettingRow(
                                index = 1,
                                count = 2,
                                icon = if (isGlobalTextureEnabled) Icons.Rounded.Lock else Icons.Rounded.DeveloperBoard,
                                title = stringResource(R.string.tweaks_renderer),
                                subtitle = if (isGlobalTextureEnabled) {
                                    stringResource(R.string.profile_renderer_locked_desc)
                                } else {
                                    stringResource(R.string.tweaks_renderer_desc)
                                },
                                valueText = if (isGlobalTextureEnabled) {
                                    stringResource(R.string.profile_renderer_angle_badge)
                                } else {
                                    currentRendererShortText
                                },
                                valueActive = isGlobalTextureEnabled,
                                locked = isGlobalTextureEnabled,
                                onClick = {
                                    if (!isGlobalTextureEnabled) showRenderDialog = true
                                }
                            )
                        }
                        AnimatedRendererLockNote(visible = isGlobalTextureEnabled)
                    }
                }

                item {
                    TweakSection(
                        title = stringResource(R.string.tweaks_section_performance),
                        icon = Icons.Rounded.BatterySaver,
                        accent = MaterialTheme.colorScheme.tertiary
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ActionTile(
                                icon = Icons.Rounded.BatterySaver,
                                title = stringResource(R.string.tweaks_lite_mode),
                                subtitle = if (isGlobalLiteMode) {
                                    stringResource(R.string.tweaks_lite_on_desc)
                                } else {
                                    stringResource(R.string.tweaks_lite_off_desc)
                                },
                                active = isGlobalLiteMode,
                                onClick = { inLiteModeSubpage = true },
                                modifier = Modifier.weight(1f)
                            )
                            ActionTile(
                                icon = Icons.Rounded.Memory,
                                title = stringResource(R.string.tweak_memory_zram),
                                subtitle = stringResource(R.string.tweak_memory_zram_desc),
                                active = false,
                                onClick = { inMemoryZramSubpage = true },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                item {
                    TweakSection(
                        title = stringResource(R.string.section_additional_tweaks),
                        icon = Icons.Rounded.Storage,
                        accent = MaterialTheme.colorScheme.secondary
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
                        ) {
                            SegmentedSettingRow(
                                index = 0,
                                count = 1,
                                icon = Icons.Rounded.Storage,
                                title = stringResource(R.string.tweak_io_scheduler),
                                subtitle = stringResource(R.string.tweak_io_scheduler_desc),
                                valueText = currentIoScheduler,
                                valueActive = false,
                                locked = false,
                                onClick = { showIoSchedulerDialog = true }
                            )
                        }
                    }
                }

                item {
                    TweakSection(
                        title = stringResource(R.string.section_utility_diagnostics),
                        icon = Icons.Rounded.Widgets,
                        accent = MaterialTheme.colorScheme.primary
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ActionTile(
                                icon = Icons.Rounded.DeveloperBoard,
                                title = stringResource(R.string.hardware_diag_title),
                                subtitle = stringResource(R.string.hardware_diag_desc),
                                active = false,
                                onClick = { inHardwareDiagnosticSubpage = true },
                                modifier = Modifier.weight(1f)
                            )
                            ActionTile(
                                icon = Icons.Rounded.Widgets,
                                title = stringResource(R.string.utility_screen_title),
                                subtitle = stringResource(R.string.utility_screen_desc),
                                active = false,
                                onClick = { inUtilitySubpage = true },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TweaksHero(
    liveRefreshRate: Int,
    rrText: String,
    rendererLabel: String,
    isLiteMode: Boolean
) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme

    val spin = rememberInfiniteTransition(label = "liveSpin")
    val liveAngle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "liveAngle"
    )

    val liteStatColor by animateColorAsState(
        targetValue = if (isLiteMode) scheme.primary else scheme.onSurface,
        animationSpec = motion.defaultEffectsSpec(),
        label = "liteStatColor"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(LocalCornerRadius.current),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
            contentColor = scheme.onSurface
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(scheme.surfaceContainerHigh, scheme.surfaceContainerHighest)
                    )
                )
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 42.dp, y = (-52).dp)
                    .size(140.dp)
                    .background(scheme.primary.copy(alpha = 0.08f), MaterialShapes.SoftBurst.toShape())
            )

            Column(modifier = Modifier.padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .graphicsLayer { rotationZ = liveAngle }
                            .background(scheme.primary, MaterialShapes.Sunny.toShape())
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.tweaks_quick_refresh),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "$liveRefreshRate",
                        style = MaterialTheme.typography.displaySmallEmphasized,
                        color = scheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Hz",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                Text(
                    text = rrText,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(14.dp))

                HorizontalDivider(
                    thickness = 1.dp,
                    color = scheme.outlineVariant.copy(alpha = 0.5f)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    HeroMiniStat(
                        label = stringResource(R.string.tweaks_quick_renderer),
                        value = rendererLabel,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .width(1.dp)
                            .height(28.dp)
                            .background(scheme.outlineVariant.copy(alpha = 0.5f))
                    )
                    HeroMiniStat(
                        label = stringResource(R.string.tweaks_quick_lite_mode),
                        value = if (isLiteMode) {
                            stringResource(R.string.status_on)
                        } else {
                            stringResource(R.string.status_off)
                        },
                        valueColor = liteStatColor,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroMiniStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmallEmphasized,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TweakSection(
    title: String,
    icon: ImageVector,
    accent: Color,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.3.sp),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        content()
    }
}

@Composable
private fun SegmentedSettingRow(
    index: Int,
    count: Int,
    icon: ImageVector,
    title: String,
    subtitle: String,
    valueText: String,
    valueActive: Boolean,
    locked: Boolean,
    onClick: () -> Unit
) {
    val motion = MaterialTheme.motionScheme
    val corner = LocalCornerRadius.current

    val rowAlpha by animateFloatAsState(
        targetValue = if (locked) 0.6f else 1f,
        animationSpec = motion.fastEffectsSpec(),
        label = "rowAlpha"
    )
    val iconCorner by animateDpAsState(
        targetValue = if (locked) 18.dp else 11.dp,
        animationSpec = motion.fastSpatialSpec(),
        label = "iconCorner"
    )
    val iconBoxColor by animateColorAsState(
        targetValue = if (locked) {
            MaterialTheme.colorScheme.surfaceContainerHighest
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        animationSpec = motion.defaultEffectsSpec(),
        label = "iconBoxColor"
    )
    val iconTintColor by animateColorAsState(
        targetValue = if (locked) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        },
        animationSpec = motion.defaultEffectsSpec(),
        label = "iconTintColor"
    )

    Column(modifier = Modifier.alpha(rowAlpha)) {
        SegmentedListItem(
            selected = false,
            onClick = onClick,
            shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
            colors = ListItemDefaults.segmentedColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            leadingContent = {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(iconCorner))
                        .background(iconBoxColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTintColor,
                        modifier = Modifier.size(19.dp)
                    )
                }
            },
            supportingContent = {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TweakValueChip(text = valueText, active = valueActive)
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = if (locked) Icons.Rounded.Lock else Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmallEmphasized,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ActionTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val motion = MaterialTheme.motionScheme
    val scheme = MaterialTheme.colorScheme

    val tileContainer by animateColorAsState(
        targetValue = if (active) scheme.primaryContainer else scheme.surfaceContainerLow,
        animationSpec = motion.defaultEffectsSpec(),
        label = "tileContainer"
    )
    val tileContent by animateColorAsState(
        targetValue = if (active) scheme.onPrimaryContainer else scheme.onSurface,
        animationSpec = motion.defaultEffectsSpec(),
        label = "tileContent"
    )
    val iconBox by animateColorAsState(
        targetValue = if (active) scheme.primary else scheme.secondaryContainer,
        animationSpec = motion.defaultEffectsSpec(),
        label = "iconBox"
    )
    val iconTint by animateColorAsState(
        targetValue = if (active) scheme.onPrimary else scheme.onSecondaryContainer,
        animationSpec = motion.defaultEffectsSpec(),
        label = "iconTint"
    )

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(LocalCornerRadius.current),
        colors = CardDefaults.cardColors(
            containerColor = tileContainer,
            contentColor = tileContent
        )
    ) {
        Column(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(iconBox),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = tileContent.copy(alpha = 0.45f),
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = tileContent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = tileContent.copy(alpha = 0.7f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AnimatedRendererLockNote(visible: Boolean) {
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(animationSpec = motion.defaultSpatialSpec()) +
            fadeIn(animationSpec = motion.defaultEffectsSpec()),
        exit = shrinkVertically(animationSpec = motion.defaultSpatialSpec()) +
            fadeOut(animationSpec = motion.defaultEffectsSpec())
    ) {
        Text(
            text = stringResource(R.string.tweaks_renderer_locked_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun TweakValueChip(text: String, active: Boolean = false) {
    val motion = MaterialTheme.motionScheme

    val container by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = motion.defaultEffectsSpec(),
        label = "chipContainer"
    )
    val content by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = motion.defaultEffectsSpec(),
        label = "chipContent"
    )

    Surface(shape = CircleShape, color = container) {
        Text(
            text = text,
            modifier = Modifier
                .widthIn(max = 104.dp)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun <T> TweakOptionDialog(
    title: String,
    options: List<TweakOption<T>>,
    isSelected: (T) -> Boolean,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLargeEmphasized
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
            ) {
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
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(20.dp)
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
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
