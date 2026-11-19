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

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.luminatweaks.R
import com.luminatweaks.data.AppProfileConfig
import com.luminatweaks.data.ConfigRepository
import com.luminatweaks.data.DisplayRrMode
import com.luminatweaks.model.AppItem
import com.luminatweaks.ui.component.AospChevron
import com.luminatweaks.ui.component.AospConnectedGroup
import com.luminatweaks.ui.component.AospOptionBottomSheet
import com.luminatweaks.ui.component.AospPreferenceItem
import com.luminatweaks.ui.component.AospPreferenceSwitch
import com.luminatweaks.ui.component.AospSectionHeader
import com.luminatweaks.ui.component.AospSheetOption
import com.luminatweaks.ui.component.AospTopBar
import com.luminatweaks.ui.theme.LocalFloatingNavBar
import kotlinx.coroutines.launch

enum class OptionItem(val value: String) {
    OFF("off"),
    DEFAULT("default"),
    ON("on");

    companion object {
        fun fromValue(v: String): OptionItem = entries.find { it.value == v } ?: DEFAULT
    }
}

@Composable
fun OptionItem.getLabel(): String = when (this) {
    OptionItem.OFF -> stringResource(R.string.opt_off)
    OptionItem.DEFAULT -> stringResource(R.string.opt_default)
    OptionItem.ON -> stringResource(R.string.opt_on)
}

data class RendererOption(val id: String, val label: String)

@Composable
fun AppProfileScreen(
    app: AppItem,
    profile: AppProfileConfig,
    onProfileChanged: (AppProfileConfig) -> Unit,
    onBack: () -> Unit
) {
    var inTeksturRinganScreen by remember { mutableStateOf(false) }

    LuminaDrillTransition(
        targetState = inTeksturRinganScreen,
        isForward = { _, target -> target }
    ) { showTextureScreen ->
        if (showTextureScreen) {
            TeksturRinganScreen(
                appName = app.name,
                profile = profile,
                onProfileChanged = onProfileChanged,
                onBack = { inTeksturRinganScreen = false }
            )
        } else {
            BackHandler(onBack = onBack)

            val context = LocalContext.current
            val haptic = LocalHapticFeedback.current
            val packageManager = remember { context.packageManager }
            val isFloating = LocalFloatingNavBar.current

            var rrModes by remember { mutableStateOf<List<DisplayRrMode>>(emptyList()) }

            var showLiteModeSheet by remember { mutableStateOf(false) }
            var showRefreshRateSheet by remember { mutableStateOf(false) }
            var showRendererSheet by remember { mutableStateOf(false) }
            var showDndSheet by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            var isSessionRecordEnabled by remember(app.packageName) { mutableStateOf(false) }

            DisposableEffect(context, app.packageName) {
                val lifecycleOwner = context as? LifecycleOwner
                var observer: LifecycleEventObserver? = null
                fun refreshRecordingState() {
                    scope.launch {
                        isSessionRecordEnabled = ConfigRepository.recordEnabled(context, app.packageName)
                    }
                }
                refreshRecordingState()

                if (lifecycleOwner != null) {
                    observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) refreshRecordingState()
                    }.also { lifecycleOwner.lifecycle.addObserver(it) }
                }
                onDispose { observer?.let { lifecycleOwner?.lifecycle?.removeObserver(it) } }
            }

            LaunchedEffect(Unit) {
                rrModes = ConfigRepository.getRefreshRateModes()
            }

            val triStateOptions = remember {
                listOf(OptionItem.OFF, OptionItem.DEFAULT, OptionItem.ON)
            }

            val defaultRendererLabel = stringResource(R.string.opt_default)
            val rendererOptions = remember(defaultRendererLabel) {
                listOf(
                    RendererOption("default", defaultRendererLabel),
                    RendererOption("skiavk", "Skia Vulkan"),
                    RendererOption("skiavkthreaded", "Skia Vulkan (Threaded)"),
                    RendererOption("skiagl", "Skia OpenGL"),
                    RendererOption("skiaglthreaded", "Skia OpenGL (Threaded)"),
                    RendererOption("opengl", "OpenGL ES"),
                    RendererOption("openglthreaded", "OpenGL ES (Threaded)"),
                    RendererOption("vulkan", "Vulkan")
                )
            }

            val isTextureLocked = profile.textureEnabled || profile.textureLod > 0

            val currentHzLabel = if (profile.refreshRate == "default" || profile.refreshRate.isBlank()) {
                stringResource(R.string.opt_default)
            } else {
                val matched = rrModes.find {
                    it.id.toString() == profile.refreshRate || it.hz.toString() == profile.refreshRate
                }
                if (matched != null) "${matched.hz} Hz" else stringResource(R.string.profile_mode_format, profile.refreshRate)
            }

            val currentTextureLabel = if (!isTextureLocked) {
                stringResource(R.string.opt_off)
            } else {
                when (profile.textureLod.coerceAtLeast(profile.textureDetail)) {
                    1 -> stringResource(R.string.texture_level_1)
                    2 -> stringResource(R.string.texture_level_2)
                    3 -> stringResource(R.string.texture_level_3)
                    4 -> stringResource(R.string.texture_level_4)
                    else -> stringResource(R.string.opt_active)
                }
            }

            val currentRendererLabel = if (isTextureLocked) {
                stringResource(R.string.profile_renderer_locked_desc)
            } else {
                rendererOptions.find { it.id == profile.renderer }?.label ?: defaultRendererLabel
            }

            val packageInfo = remember(app.packageName) {
                runCatching { packageManager.getPackageInfo(app.packageName, 0) }.getOrNull()
            }

            val versionName = packageInfo?.versionName ?: "1.0.0"
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo?.longVersionCode ?: 1L
            } else {
                @Suppress("DEPRECATION")
                packageInfo?.versionCode?.toLong() ?: 1L
            }

            val isMasterEnabled = !profile.disabled && profile.saklarUtama

            val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val bottomPadding = if (isFloating) 100.dp + navInset else 24.dp + navInset
            val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

            fun toggleMaster(nextEnabled: Boolean) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onProfileChanged(
                    if (nextEnabled) {
                        profile.copy(
                            saklarUtama = true,
                            disabled = false,
                            custom = profile.custom || !ConfigRepository.defaultGames.contains(profile.pkg)
                        )
                    } else {
                        profile.copy(
                            saklarUtama = false,
                            disabled = true,
                            custom = false
                        )
                    }
                )
            }

            Scaffold(
                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                contentWindowInsets = WindowInsets(0.dp),
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    AospTopBar(
                        title = app.name,
                        subtitle = stringResource(R.string.profile_title),
                        scrollBehavior = scrollBehavior,
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.automirrored.rounded.ArrowBack,
                                    contentDescription = stringResource(R.string.action_back),
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", app.packageName, null)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = stringResource(R.string.profile_app_info),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )
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
                    item(key = "app_header") {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(remember(app.packageName) {
                                            try {
                                                packageManager.getApplicationIcon(app.packageName)
                                            } catch (_: Exception) {
                                                null
                                            }
                                        })
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                )

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.name,
                                        style = MaterialTheme.typography.titleMediumEmphasized,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "$versionName ($versionCode)",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(1.dp))
                                    Text(
                                        text = app.packageName,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    item(key = "master_switch") {
                        Column {
                            AospSectionHeader(title = stringResource(R.string.aosp_section_override))
                            AospConnectedGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
                                Surface(
                                    onClick = { toggleMaster(!isMasterEnabled) },
                                    shape = RoundedCornerShape(24.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 16.dp, top = 14.dp, end = 12.dp, bottom = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(RoundedCornerShape(13.dp))
                                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.PowerSettingsNew,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = stringResource(R.string.profile_master_switch),
                                                fontSize = 17.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = if (isMasterEnabled) {
                                                    localizedLabel(
                                                        "Profil khusus aktif untuk aplikasi ini",
                                                        "Custom profile is active for this app"
                                                    )
                                                } else {
                                                    localizedLabel(
                                                        "Mengikuti pengaturan global",
                                                        "Follows global settings"
                                                    )
                                                },
                                                fontSize = 13.5.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Switch(
                                            checked = isMasterEnabled,
                                            onCheckedChange = { toggleMaster(it) },
                                            thumbContent = {
                                                Icon(
                                                    imageVector = if (isMasterEnabled) {
                                                        androidx.compose.material.icons.Icons.Rounded.Check
                                                    } else {
                                                        androidx.compose.material.icons.Icons.Rounded.Close
                                                    },
                                                    contentDescription = null,
                                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item(key = "override_notice") {
                        if (!isMasterEnabled) {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = stringResource(R.string.profile_override_notice),
                                        fontSize = 12.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    item(key = "profile_options") {
                        AnimatedVisibility(
                            visible = isMasterEnabled,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column {
                                    AospSectionHeader(title = stringResource(R.string.profile_section_performance))
                                    AospConnectedGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
                                        AospPreferenceItem(
                                            index = 0,
                                            count = 1,
                                            icon = Icons.Rounded.BatterySaver,
                                            title = stringResource(R.string.profile_lite_mode),
                                            subtitle = OptionItem.fromValue(profile.liteMode).getLabel(),
                                            onClick = { showLiteModeSheet = true },
                                            trailing = { AospChevron() }
                                        )
                                    }
                                }

                                Column {
                                    AospSectionHeader(title = stringResource(R.string.profile_section_display_render))
                                    AospConnectedGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
                                        AospPreferenceItem(
                                            index = 0,
                                            count = 3,
                                            icon = Icons.Rounded.Tune,
                                            title = stringResource(R.string.profile_texture),
                                            subtitle = currentTextureLabel,
                                            onClick = { inTeksturRinganScreen = true },
                                            trailing = { AospChevron() }
                                        )
                                        AospPreferenceItem(
                                            index = 1,
                                            count = 3,
                                            icon = Icons.Rounded.Speed,
                                            title = stringResource(R.string.profile_refresh_rate),
                                            subtitle = currentHzLabel,
                                            highlightSubtitle = true,
                                            onClick = { showRefreshRateSheet = true },
                                            trailing = { AospChevron() }
                                        )
                                        AospPreferenceItem(
                                            index = 2,
                                            count = 3,
                                            icon = if (isTextureLocked) Icons.Rounded.Lock else Icons.Rounded.Brush,
                                            title = stringResource(R.string.profile_renderer),
                                            subtitle = currentRendererLabel,
                                            enabled = !isTextureLocked,
                                            onClick = { showRendererSheet = true },
                                            trailing = { AospChevron() }
                                        )
                                    }
                                }

                                Column {
                                    AospSectionHeader(title = stringResource(R.string.profile_section_additional))
                                    AospConnectedGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
                                        AospPreferenceSwitch(
                                            index = 0,
                                            count = 2,
                                            icon = Icons.Rounded.Insights,
                                            title = localizedLabel("Rekam Statistik Sesi", "Record Session Stats"),
                                            subtitle = localizedLabel(
                                                "Simpan riwayat FPS, suhu & daya saat aplikasi berjalan",
                                                "Log FPS, temperature & power while this app runs"
                                            ),
                                            checked = isSessionRecordEnabled,
                                            onCheckedChange = { enabled ->
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                isSessionRecordEnabled = enabled
                                                scope.launch {
                                                    ConfigRepository.setRecordEnabled(
                                                        context,
                                                        app.packageName,
                                                        enabled
                                                    )
                                                }
                                            }
                                        )
                                        AospPreferenceItem(
                                            index = 1,
                                            count = 2,
                                            icon = Icons.Rounded.NotificationsOff,
                                            title = stringResource(R.string.profile_dnd),
                                            subtitle = OptionItem.fromValue(profile.dnd).getLabel(),
                                            onClick = { showDndSheet = true },
                                            trailing = { AospChevron() }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item(key = "footer") {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            if (showLiteModeSheet) {
                AospOptionBottomSheet(
                    title = stringResource(R.string.dialog_select_lite_mode),
                    options = triStateOptions.map { AospSheetOption(it.value, it.getLabel()) },
                    isSelected = { it == profile.liteMode },
                    onSelect = { value ->
                        onProfileChanged(profile.copy(liteMode = value))
                        showLiteModeSheet = false
                    },
                    onDismiss = { showLiteModeSheet = false }
                )
            }

            if (showRefreshRateSheet) {
                val defaultOpt = listOf(AospSheetOption("default", stringResource(R.string.opt_default)))
                val modeOpts = rrModes.map { AospSheetOption(it.id.toString(), "${it.hz} Hz") }
                AospOptionBottomSheet(
                    title = stringResource(R.string.dialog_select_refresh_rate),
                    options = defaultOpt + modeOpts,
                    isSelected = { value ->
                        if (value == "default") {
                            profile.refreshRate == "default" || profile.refreshRate.isBlank()
                        } else {
                            profile.refreshRate == value ||
                                rrModes.find { it.id.toString() == value }?.hz?.toString() == profile.refreshRate
                        }
                    },
                    onSelect = { value ->
                        onProfileChanged(profile.copy(refreshRate = value))
                        showRefreshRateSheet = false
                    },
                    onDismiss = { showRefreshRateSheet = false }
                )
            }

            if (showRendererSheet && !isTextureLocked) {
                AospOptionBottomSheet(
                    title = stringResource(R.string.dialog_select_renderer),
                    options = rendererOptions.map { AospSheetOption(it.id, it.label) },
                    isSelected = { it.equals(profile.renderer, ignoreCase = true) },
                    onSelect = { id ->
                        onProfileChanged(profile.copy(renderer = id))
                        showRendererSheet = false
                    },
                    onDismiss = { showRendererSheet = false }
                )
            }

            if (showDndSheet) {
                AospOptionBottomSheet(
                    title = stringResource(R.string.dialog_select_dnd),
                    options = triStateOptions.map { AospSheetOption(it.value, it.getLabel()) },
                    isSelected = { it == profile.dnd },
                    onSelect = { value ->
                        onProfileChanged(profile.copy(dnd = value))
                        showDndSheet = false
                    },
                    onDismiss = { showDndSheet = false }
                )
            }
        }
    }
}

@Composable
fun ProfileSectionTitle(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, top = 6.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}