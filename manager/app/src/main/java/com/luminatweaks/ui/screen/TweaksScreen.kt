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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
                        fontWeight = FontWeight.SemiBold
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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                TweaksHero(
                    liveRefreshRate = liveRefreshRate,
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
                    TweakGroup {
                        TweakRow(
                            icon = Icons.Rounded.Speed,
                            iconContainer = MaterialTheme.colorScheme.primaryContainer,
                            iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                            title = stringResource(R.string.tweaks_refresh_rate),
                            subtitle = stringResource(R.string.tweaks_refresh_rate_desc),
                            trailingText = currentRrText,
                            onClick = { showRefreshRateDialog = true }
                        )
                        TweakRowDivider()
                        TweakRow(
                            icon = if (isGlobalTextureEnabled) Icons.Rounded.Lock else Icons.Rounded.DeveloperBoard,
                            iconContainer = if (isGlobalTextureEnabled) {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            },
                            iconTint = if (isGlobalTextureEnabled) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            },
                            title = stringResource(R.string.tweaks_renderer),
                            subtitle = if (isGlobalTextureEnabled) {
                                stringResource(R.string.profile_renderer_locked_desc)
                            } else {
                                stringResource(R.string.tweaks_renderer_desc)
                            },
                            trailingText = if (isGlobalTextureEnabled) {
                                stringResource(R.string.profile_renderer_angle_badge)
                            } else {
                                currentRendererShortText
                            },
                            onClick = {
                                if (!isGlobalTextureEnabled) showRenderDialog = true
                            },
                            enabled = !isGlobalTextureEnabled
                        )
                        if (isGlobalTextureEnabled) {
                            Text(
                                text = stringResource(R.string.tweaks_renderer_locked_note),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }
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
                            icon = Icons.Rounded.Memory,
                            iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                            iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                            title = stringResource(R.string.tweak_memory_zram),
                            subtitle = stringResource(R.string.tweak_memory_zram_desc),
                            onClick = { inMemoryZramSubpage = true }
                        )
                        TweakRowDivider()
                        TweakRow(
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
                            icon = Icons.Rounded.DeveloperBoard,
                            iconContainer = MaterialTheme.colorScheme.primaryContainer,
                            iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                            title = stringResource(R.string.hardware_diag_title),
                            subtitle = stringResource(R.string.hardware_diag_desc),
                            onClick = { inHardwareDiagnosticSubpage = true }
                        )
                        TweakRowDivider()
                        TweakRow(
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

@Composable
private fun TweaksHero(
    liveRefreshRate: Int,
    rendererLabel: String,
    isLiteMode: Boolean
) {
    val scheme = MaterialTheme.colorScheme

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
                    .offset(x = 36.dp, y = (-48).dp)
                    .size(150.dp)
                    .background(scheme.primary.copy(alpha = 0.10f), CircleShape)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = (-40).dp, y = 36.dp)
                    .size(120.dp)
                    .background(scheme.tertiary.copy(alpha = 0.08f), CircleShape)
            )

            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(scheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = null,
                            tint = scheme.onPrimaryContainer,
                            modifier = Modifier.size(23.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.tweaks_hero_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.tweaks_hero_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    QuickStat(
                        label = stringResource(R.string.tweaks_quick_refresh),
                        value = "$liveRefreshRate Hz",
                        live = true,
                        modifier = Modifier.weight(1f)
                    )
                    QuickStatDivider()
                    QuickStat(
                        label = stringResource(R.string.tweaks_quick_renderer),
                        value = rendererLabel,
                        modifier = Modifier.weight(1f)
                    )
                    QuickStatDivider()
                    QuickStat(
                        label = stringResource(R.string.tweaks_quick_lite_mode),
                        value = if (isLiteMode) {
                            stringResource(R.string.status_on)
                        } else {
                            stringResource(R.string.status_off)
                        },
                        valueColor = if (isLiteMode) scheme.primary else scheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    live: Boolean = false,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (live) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Spacer(modifier = Modifier.width(5.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    letterSpacing = 0.8.sp
                ),
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun QuickStatDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .width(1.dp)
            .height(30.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    )
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
private fun TweakGroup(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(LocalCornerRadius.current),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(content = content)
    }
}

@Composable
private fun TweakRow(
    icon: ImageVector,
    iconContainer: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailingText: String? = null,
    trailingActive: Boolean = false,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.55f)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(13.dp))
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
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.5.sp,
                    lineHeight = 16.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (trailingText != null) {
            Spacer(modifier = Modifier.width(10.dp))
            TweakValueChip(text = trailingText, active = trailingActive)
            Spacer(modifier = Modifier.width(4.dp))
        } else {
            Spacer(modifier = Modifier.width(6.dp))
        }
        if (enabled) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.size(18.dp)
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.size(16.dp)
            )
        }
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
private fun TweakRowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp, end = 16.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    )
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
