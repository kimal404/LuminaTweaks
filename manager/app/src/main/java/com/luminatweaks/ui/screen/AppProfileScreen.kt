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
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Info
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
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.luminatweaks.ui.component.LuminaBackButton
import com.luminatweaks.ui.component.appBarFade
import com.luminatweaks.ui.component.pressScale
import com.luminatweaks.ui.theme.LocalFloatingNavBar
import kotlinx.coroutines.CancellationException
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

private data class ProfileSheetOption<T>(
    val value: T,
    val label: String
)

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

            var showProfileSheet by remember { mutableStateOf(false) }
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

            val profileOptions = listOf(
                ProfileSheetOption("default", stringResource(R.string.opt_default)),
                ProfileSheetOption("powersave", stringResource(R.string.aosp_profile_powersave)),
                ProfileSheetOption("balanced", stringResource(R.string.aosp_profile_balanced)),
                ProfileSheetOption("performance", stringResource(R.string.aosp_profile_performance)),
                ProfileSheetOption("maestro", stringResource(R.string.aosp_profile_maestro))
            )
            val currentAppProfileLabel = profileOptions
                .find { it.value == profile.profile }?.label
                ?: stringResource(R.string.opt_default)

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
            val bottomPadding = if (isFloating) 112.dp + navInset else 28.dp + navInset
            val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
            val fadeSurface = MaterialTheme.colorScheme.background

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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .appBarFade(fadeSurface, scrollBehavior.state.overlappedFraction)
                            .statusBarsPadding()
                    ) {
                        LargeFlexibleTopAppBar(
                            navigationIcon = {
                                LuminaBackButton(
                                    onBack = onBack,
                                    contentDescription = stringResource(R.string.action_back)
                                )
                            },
                            title = {
                                Text(
                                    text = app.name,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
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
                    item(key = "app_header") {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(26.dp))
                                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
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
                                    .size(50.dp)
                                    .clip(RoundedCornerShape(15.dp))
                            )

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "$versionName ($versionCode)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(1.dp))
                                Text(
                                    text = app.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    item(key = "master_switch") {
                        ProfileSectionHeader(title = stringResource(R.string.aosp_section_override))
                        ProfileSwitchRow(
                            index = 0,
                            count = 1,
                            icon = Icons.Rounded.PowerSettingsNew,
                            title = stringResource(R.string.profile_master_switch),
                            subtitle = if (isMasterEnabled) {
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
                            checked = isMasterEnabled,
                            onCheckedChange = { toggleMaster(it) }
                        )
                    }

                    item(key = "override_notice") {
                        if (!isMasterEnabled) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(26.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                    .padding(horizontal = 16.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ProfileSquircleIcon(icon = Icons.Rounded.Info)
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = stringResource(R.string.profile_override_notice),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    item(key = "profile_options") {
                        AnimatedVisibility(
                            visible = isMasterEnabled,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                ProfileSectionHeader(title = stringResource(R.string.profile_section_performance))
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    ProfileActionRow(
                                        index = 0,
                                        count = 2,
                                        icon = Icons.Rounded.Speed,
                                        title = stringResource(R.string.profile_app_profile),
                                        subtitle = currentAppProfileLabel,
                                        highlightSubtitle = true,
                                        onClick = { showProfileSheet = true }
                                    )
                                    ProfileActionRow(
                                        index = 1,
                                        count = 2,
                                        icon = Icons.Rounded.BatterySaver,
                                        title = stringResource(R.string.profile_lite_mode),
                                        subtitle = OptionItem.fromValue(profile.liteMode).getLabel(),
                                        onClick = { showLiteModeSheet = true }
                                    )
                                }

                                ProfileSectionHeader(title = stringResource(R.string.profile_section_display_render))
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    ProfileActionRow(
                                        index = 0,
                                        count = 3,
                                        icon = Icons.Rounded.Tune,
                                        title = stringResource(R.string.profile_texture),
                                        subtitle = currentTextureLabel,
                                        onClick = { inTeksturRinganScreen = true }
                                    )
                                    ProfileActionRow(
                                        index = 1,
                                        count = 3,
                                        icon = Icons.Rounded.Speed,
                                        title = stringResource(R.string.profile_refresh_rate),
                                        subtitle = currentHzLabel,
                                        highlightSubtitle = true,
                                        onClick = { showRefreshRateSheet = true }
                                    )
                                    ProfileActionRow(
                                        index = 2,
                                        count = 3,
                                        icon = if (isTextureLocked) Icons.Rounded.Lock else Icons.Rounded.Brush,
                                        title = stringResource(R.string.profile_renderer),
                                        subtitle = currentRendererLabel,
                                        enabled = !isTextureLocked,
                                        onClick = { showRendererSheet = true }
                                    )
                                }

                                ProfileSectionHeader(title = stringResource(R.string.profile_section_additional))
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    ProfileSwitchRow(
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
                                    ProfileActionRow(
                                        index = 1,
                                        count = 2,
                                        icon = Icons.Rounded.NotificationsOff,
                                        title = stringResource(R.string.profile_dnd),
                                        subtitle = OptionItem.fromValue(profile.dnd).getLabel(),
                                        onClick = { showDndSheet = true }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            ProfileOptionSheet(
                show = showProfileSheet,
                title = stringResource(R.string.dialog_select_profile),
                options = profileOptions,
                isSelected = { it == profile.profile },
                onSelect = { value ->
                    onProfileChanged(profile.copy(profile = value))
                },
                onDismiss = { showProfileSheet = false }
            )

            ProfileOptionSheet(
                show = showLiteModeSheet,
                title = stringResource(R.string.dialog_select_lite_mode),
                options = triStateOptions.map { ProfileSheetOption(it.value, it.getLabel()) },
                isSelected = { it == profile.liteMode },
                onSelect = { value ->
                    onProfileChanged(profile.copy(liteMode = value))
                },
                onDismiss = { showLiteModeSheet = false }
            )

            val defaultOpt = listOf(ProfileSheetOption("default", stringResource(R.string.opt_default)))
            val modeOpts = rrModes.map { ProfileSheetOption(it.id.toString(), "${it.hz} Hz") }
            ProfileOptionSheet(
                show = showRefreshRateSheet,
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
                },
                onDismiss = { showRefreshRateSheet = false }
            )

            ProfileOptionSheet(
                show = showRendererSheet && !isTextureLocked,
                title = stringResource(R.string.dialog_select_renderer),
                options = rendererOptions.map { ProfileSheetOption(it.id, it.label) },
                isSelected = { it.equals(profile.renderer, ignoreCase = true) },
                onSelect = { id ->
                    onProfileChanged(profile.copy(renderer = id))
                },
                onDismiss = { showRendererSheet = false }
            )

            ProfileOptionSheet(
                show = showDndSheet,
                title = stringResource(R.string.dialog_select_dnd),
                options = triStateOptions.map { ProfileSheetOption(it.value, it.getLabel()) },
                isSelected = { it == profile.dnd },
                onSelect = { value ->
                    onProfileChanged(profile.copy(dnd = value))
                },
                onDismiss = { showDndSheet = false }
            )
        }
    }
}

@Composable
private fun ProfileSectionHeader(title: String) {
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
private fun ProfileSquircleIcon(
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
private fun ProfileSwitchRow(
    index: Int,
    count: Int,
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val shape = connectedContainerShape(index = index, count = count)
    val interactionSource = remember { MutableInteractionSource() }
    val pressScaleModifier = pressScale(interactionSource, 0.975f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(pressScaleModifier)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.padding(end = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            ProfileSquircleIcon(icon = icon)
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
private fun ProfileActionRow(
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
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
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
            ProfileSquircleIcon(icon = icon, enabled = enabled)
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
private fun <T> ProfileOptionSheet(
    show: Boolean,
    title: String,
    options: List<ProfileSheetOption<T>>,
    isSelected: (T) -> Boolean,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
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
                    val haptic = LocalHapticFeedback.current
                    val interactionSource = remember { MutableInteractionSource() }
                    val pressScaleModifier = pressScale(interactionSource, 0.97f)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(pressScaleModifier)
                            .clip(rowShape)
                            .background(
                                if (selected) scheme.secondaryContainer else scheme.surfaceContainer
                            )
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
                        ProfileRadioRing(selected = selected)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileRadioRing(selected: Boolean) {
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
fun ProfileSectionTitle(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 8.dp)
    )
}
