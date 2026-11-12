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
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
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
        val state = ConfigRepository.getTweaksState()
        isGlobalLiteMode = state.isGlobalLiteMode
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
        110.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    } else {
        24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            MediumFlexibleTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.tweaks_title),
                        style = MaterialTheme.typography.titleLargeEmphasized
                    )
                },
                subtitle = {
                    Text(
                        text = stringResource(R.string.tweaks_hero_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                scrollBehavior = scrollBehavior
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
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            item {
                TweaksHero(
                    liveRefreshRate = liveRefreshRate,
                    rendererLabel = currentRendererShortText,
                    isLiteMode = isGlobalLiteMode
                )
            }

            item {
                TweakSection(
                    title = stringResource(R.string.tweaks_section_display_graphics),
                    icon = Icons.Rounded.Speed,
                    accent = MaterialTheme.colorScheme.primary
                ) {
                    TweakGroup {
                        TweakRow(
                            index = 0,
                            count = 2,
                            icon = Icons.Rounded.Speed,
                            iconContainer = MaterialTheme.colorScheme.primaryContainer,
                            iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                            title = stringResource(R.string.tweaks_refresh_rate),
                            subtitle = stringResource(R.string.tweaks_refresh_rate_desc),
                            trailingText = currentRrText,
                            onClick = { showRefreshRateDialog = true }
                        )
                        TweakRow(
                            index = 1,
                            count = 2,
                            icon = Icons.Rounded.DeveloperBoard,
                            iconContainer = MaterialTheme.colorScheme.primaryContainer,
                            iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                            title = stringResource(R.string.tweaks_renderer),
                            subtitle = stringResource(R.string.tweaks_renderer_desc),
                            trailingText = currentRendererShortText,
                            onClick = { showRenderDialog = true }
                        )
                    }
                }
            }

            item {
                TweakSection(
                    title = stringResource(R.string.tweaks_section_performance),
                    icon = Icons.Rounded.BatterySaver,
                    accent = MaterialTheme.colorScheme.tertiary
                ) {
                    TweakGroup {
                        TweakRow(
                            index = 0,
                            count = 1,
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
                            onClick = { inLiteModeSubpage = true }
                        )
                    }
                }
            }

            item {
                TweakSection(
                    title = stringResource(R.string.section_additional_tweaks),
                    icon = Icons.Rounded.Tune,
                    accent = MaterialTheme.colorScheme.secondary
                ) {
                    TweakGroup {
                        TweakRow(
                            index = 0,
                            count = 2,
                            icon = Icons.Rounded.Memory,
                            iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                            iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                            title = stringResource(R.string.tweak_memory_zram),
                            subtitle = stringResource(R.string.tweak_memory_zram_desc),
                            onClick = { inMemoryZramSubpage = true }
                        )
                        TweakRow(
                            index = 1,
                            count = 2,
                            icon = Icons.Rounded.Storage,
                            iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                            iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                            title = stringResource(R.string.tweak_io_scheduler),
                            subtitle = stringResource(R.string.tweak_io_scheduler_desc),
                            trailingText = currentIoScheduler,
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
                    TweakGroup {
                        TweakRow(
                            index = 0,
                            count = 2,
                            icon = Icons.Rounded.DeveloperBoard,
                            iconContainer = MaterialTheme.colorScheme.primaryContainer,
                            iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                            title = stringResource(R.string.hardware_diag_title),
                            subtitle = stringResource(R.string.hardware_diag_desc),
                            onClick = { inHardwareDiagnosticSubpage = true }
                        )
                        TweakRow(
                            index = 1,
                            count = 2,
                            icon = Icons.Rounded.Widgets,
                            iconContainer = MaterialTheme.colorScheme.primaryContainer,
                            iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                            title = stringResource(R.string.utility_screen_title),
                            subtitle = stringResource(R.string.utility_screen_desc),
                            onClick = { inUtilitySubpage = true }
                        )
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
    rendererLabel: String,
    isLiteMode: Boolean
) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val animatedHz by animateIntAsState(
        targetValue = liveRefreshRate,
        animationSpec = motion.defaultSpatialSpec(),
        label = "liveRefreshRate"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLargeIncreased,
        color = scheme.primaryContainer,
        contentColor = scheme.onPrimaryContainer
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 42.dp, y = (-52).dp)
                    .size(152.dp)
                    .rotate(12f)
                    .background(
                        scheme.tertiaryContainer.copy(alpha = 0.45f),
                        MaterialShapes.Cookie9Sided.toShape()
                    )
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = (-34).dp, y = 42.dp)
                    .size(104.dp)
                    .rotate(-18f)
                    .background(
                        scheme.secondaryContainer.copy(alpha = 0.45f),
                        MaterialShapes.Clover4Leaf.toShape()
                    )
            )

            Column(modifier = Modifier.padding(22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(scheme.tertiary)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.tweaks_quick_refresh),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = scheme.onPrimaryContainer.copy(alpha = 0.72f)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "$animatedHz",
                        style = MaterialTheme.typography.displayMediumEmphasized,
                        color = scheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Hz",
                        modifier = Modifier.padding(bottom = 10.dp),
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                        color = scheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeroMetric(
                        icon = Icons.Rounded.DeveloperBoard,
                        label = stringResource(R.string.tweaks_quick_renderer),
                        value = rendererLabel,
                        container = scheme.secondaryContainer,
                        content = scheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    HeroMetric(
                        icon = Icons.Rounded.BatterySaver,
                        label = stringResource(R.string.tweaks_quick_lite_mode),
                        value = if (isLiteMode) {
                            stringResource(R.string.status_on)
                        } else {
                            stringResource(R.string.status_off)
                        },
                        container = scheme.tertiaryContainer,
                        content = scheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroMetric(
    icon: ImageVector,
    label: String,
    value: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = container,
        contentColor = content
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = content.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
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
                    .size(30.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        content()
    }
}

@Composable
private fun TweakGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        content = content
    )
}

@Composable
private fun TweakRow(
    index: Int,
    count: Int,
    icon: ImageVector,
    iconContainer: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailingText: String? = null,
    trailingActive: Boolean = false
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
            supportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(iconContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(21.dp)
                )
            }
        },
        supportingContent = {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (trailingText != null) {
                    TweakValueChip(text = trailingText, active = trailingActive)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TweakValueChip(text: String, active: Boolean = false) {
    val container = if (active) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val content = if (active) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(shape = CircleShape, color = container, contentColor = content) {
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
