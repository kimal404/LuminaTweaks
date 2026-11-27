@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package com.luminatweaks.ui.screen

import android.annotation.SuppressLint
import android.app.Activity
import android.app.LocaleManager
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.LocaleList
import android.os.SystemClock
import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luminatweaks.BuildConfig
import com.luminatweaks.R
import com.luminatweaks.data.ConfigRepository
import com.luminatweaks.ui.component.TouchOriginDialog
import com.luminatweaks.ui.component.pressScale
import com.luminatweaks.ui.component.rememberDialogOrigin
import com.luminatweaks.ui.theme.LocalFloatingNavBar
import com.luminatweaks.util.DeviceUtil
import com.luminatweaks.util.LogUtil
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class SettingsSheetOption<T>(
    val value: T,
    val label: String,
    val supporting: String? = null
)

@Composable
private fun settingsLocalizedLabel(id: String, en: String): String {
    val isIndonesian = remember {
        val lang = Locale.getDefault().language.lowercase(Locale.ROOT)
        lang == "in" || lang == "id"
    }
    return if (isIndonesian) id else en
}

@Composable
fun SettingsScreen(
    onNavigateToAbout: (() -> Unit)? = null,
    onNavigateToPersonalisasi: (() -> Unit)? = null,
    onNavigateToCompatibility: (() -> Unit)? = null,
    onNavigateToTweaks: (() -> Unit)? = null,
    onOpenSetupWizard: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val isFloating = LocalFloatingNavBar.current

    var currentLanguage by remember {
        val lang = getCurrentLanguage(context)
        mutableStateOf(if (lang.startsWith("en")) "en" else "id")
    }

    val deviceModel = remember { DeviceUtil.getMarketDeviceName(context) }
    val packageName = remember { context.packageName }
    val versionSummary = remember {
        "${BuildConfig.VERSION_NAME.removeSuffix(".0")} (${BuildConfig.VERSION_CODE})"
    }

    var systemWallpaperBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var showLanguageSheet by remember { mutableStateOf(false) }
    var showLogBottomSheet by remember { mutableStateOf(false) }
    var isGeneratingLog by remember { mutableStateOf(false) }
    var showAboutScreen by remember { mutableStateOf(false) }
    var showPersonalisasiScreen by remember { mutableStateOf(false) }
    var showCompatibilityScreen by remember { mutableStateOf(false) }
    var showTweaksScreen by remember { mutableStateOf(false) }
    var showUninstallDialog by remember { mutableStateOf(false) }
    var isToastNotifications by remember { mutableStateOf(true) }
    var isUninstalling by remember { mutableStateOf(false) }
    var uninstallOriginPoint by remember { mutableStateOf(Offset(0.5f, 0.5f)) }
    val uninstallOrigin = rememberDialogOrigin()

    val hasInlineSubscreen = (showAboutScreen && onNavigateToAbout == null) ||
        (showPersonalisasiScreen && onNavigateToPersonalisasi == null) ||
        (showCompatibilityScreen && onNavigateToCompatibility == null) ||
        (showTweaksScreen && onNavigateToTweaks == null)

    LaunchedEffect(Unit) {
        isToastNotifications = ConfigRepository.getToastNotifications()
        systemWallpaperBitmap = loadSystemWallpaperBitmap(context)
    }

    if (hasInlineSubscreen) {
        when {
            showAboutScreen && onNavigateToAbout == null ->
                AboutScreen(onBackClick = { showAboutScreen = false })
            showPersonalisasiScreen && onNavigateToPersonalisasi == null ->
                PersonalisasiScreen(onBackClick = { showPersonalisasiScreen = false })
            showCompatibilityScreen && onNavigateToCompatibility == null ->
                CompatibilityScreen(onBackClick = { showCompatibilityScreen = false })
            showTweaksScreen && onNavigateToTweaks == null ->
                TweaksScreen(onBackClick = { showTweaksScreen = false })
        }
        return
    }

    val scheme = MaterialTheme.colorScheme
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomPadding = if (isFloating) 112.dp + navInset else 28.dp + navInset
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val fadeSurface = scheme.background
    val hazeState = remember { HazeState() }
    val blurProgress by remember {
        derivedStateOf {
            (scrollBehavior.state.overlappedFraction * 1.35f).coerceIn(0f, 1f)
        }
    }

    val openPersonalisasi: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (onNavigateToPersonalisasi != null) {
            onNavigateToPersonalisasi()
        } else {
            showPersonalisasiScreen = true
        }
    }

    val openCompatibility: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (onNavigateToCompatibility != null) {
            onNavigateToCompatibility()
        } else {
            showCompatibilityScreen = true
        }
    }

    val openAbout: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (onNavigateToAbout != null) {
            onNavigateToAbout()
        } else {
            showAboutScreen = true
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0.dp),
        containerColor = scheme.background,
        topBar = {
            val topBarModifier = if (blurProgress > 0.01f) {
                Modifier
                    .fillMaxWidth()
                    .hazeEffect(
                        state = hazeState,
                        style = HazeStyle(
                            backgroundColor = fadeSurface,
                            tints = listOf(
                                HazeTint(fadeSurface.copy(alpha = 0.62f * blurProgress))
                            ),
                            blurRadius = (24f * blurProgress).dp,
                            noiseFactor = 0f
                        )
                    ) {
                        progressive = HazeProgressive.verticalGradient(
                            startIntensity = 1f,
                            endIntensity = 0f
                        )
                    }
                    .background(
                        brush = Brush.verticalGradient(
                            0.0f to fadeSurface.copy(alpha = 0.90f * blurProgress),
                            0.65f to fadeSurface.copy(alpha = 0.65f * blurProgress),
                            1.0f to Color.Transparent
                        )
                    )
                    .statusBarsPadding()
            } else {
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            }

            Box(modifier = topBarModifier) {
                LargeFlexibleTopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.title_settings),
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
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = bottomPadding,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "hero_bento") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(332.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MonetPersonalizationTile(
                            modifier = Modifier.weight(0.35f),
                            title = stringResource(R.string.personalization_title),
                            onClick = openPersonalisasi
                        )

                        DeviceSpecsOverviewTile(
                            modifier = Modifier.weight(0.65f),
                            deviceLabel = settingsLocalizedLabel("Model perangkat", "Device model"),
                            deviceValue = deviceModel,
                            packageLabel = settingsLocalizedLabel("Nama paket", "Package name"),
                            packageValue = packageName,
                            versionLabel = settingsLocalizedLabel("Versi manajer", "Manager version"),
                            versionValue = versionSummary,
                            uptimeLabel = settingsLocalizedLabel("Waktu aktif perangkat", "Device uptime")
                        )
                    }

                    LockscreenHeroTile(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        wallpaper = systemWallpaperBitmap,
                        languageTag = currentLanguage
                    )
                }
            }

            item(key = "quick_tools_pair") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SuiteMediumTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.Description,
                        title = if (isGeneratingLog) {
                            settingsLocalizedLabel("Menyimpan...", "Bundling...")
                        } else {
                            stringResource(R.string.settings_save_log)
                        },
                        enabled = !isGeneratingLog,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showLogBottomSheet = true
                        }
                    )

                    SuiteMediumTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.AutoFixHigh,
                        title = stringResource(R.string.settings_setup_wizard),
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onOpenSetupWizard()
                        }
                    )
                }
            }

            item(key = "about_and_module") {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SettingsSwitchRow(
                        index = 0,
                        count = 5,
                        icon = Icons.Rounded.Notifications,
                        title = stringResource(R.string.settings_toast_notifications),
                        subtitle = settingsLocalizedLabel(
                            "Tampilkan info saat profil aktif berganti",
                            "Show alert when active profile switches"
                        ),
                        checked = isToastNotifications,
                        onCheckedChange = { enabled ->
                            isToastNotifications = enabled
                            coroutineScope.launch {
                                ConfigRepository.setToastNotifications(enabled)
                            }
                        }
                    )

                    SettingsActionRow(
                        index = 1,
                        count = 5,
                        icon = Icons.Rounded.Language,
                        title = stringResource(R.string.settings_language),
                        subtitle = if (currentLanguage == "en") {
                            stringResource(R.string.language_english)
                        } else {
                            stringResource(R.string.language_indonesian)
                        },
                        highlightSubtitle = true,
                        onClick = { showLanguageSheet = true }
                    )

                    SettingsActionRow(
                        index = 2,
                        count = 5,
                        icon = Icons.Rounded.Security,
                        title = stringResource(R.string.settings_compatibility),
                        subtitle = stringResource(R.string.settings_compatibility_desc),
                        onClick = openCompatibility
                    )

                    SettingsActionRow(
                        index = 3,
                        count = 5,
                        icon = Icons.Rounded.Info,
                        title = stringResource(R.string.settings_about),
                        subtitle = stringResource(
                            R.string.settings_version_format,
                            BuildConfig.VERSION_NAME.removeSuffix(".0")
                        ),
                        onClick = openAbout
                    )

                    SettingsActionRow(
                        index = 4,
                        count = 5,
                        icon = Icons.Rounded.DeleteForever,
                        title = stringResource(R.string.settings_module_uninstall_title),
                        subtitle = stringResource(R.string.settings_module_uninstall_desc),
                        enabled = !isUninstalling,
                        accentColor = scheme.error,
                        modifier = uninstallOrigin.trackedModifier(),
                        onClick = {
                            uninstallOriginPoint = uninstallOrigin.origin
                            showUninstallDialog = true
                        }
                    )
                }
            }
        }
    }

    TouchOriginDialog(
        visible = showUninstallDialog,
        onDismiss = { if (!isUninstalling) showUninstallDialog = false },
        origin = uninstallOriginPoint
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Rounded.DeleteForever,
                contentDescription = null,
                tint = scheme.error,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.settings_module_uninstall_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_module_uninstall_dialog_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = { showUninstallDialog = false },
                    enabled = !isUninstalling
                ) {
                    Text(
                        text = stringResource(R.string.action_cancel),
                        fontWeight = FontWeight.SemiBold
                    )
                }
                TextButton(
                    onClick = {
                        isUninstalling = true
                        coroutineScope.launch {
                            uninstallModule(context)
                        }
                    },
                    enabled = !isUninstalling
                ) {
                    Text(
                        text = if (isUninstalling) {
                            stringResource(R.string.settings_module_uninstall_processing)
                        } else {
                            stringResource(R.string.settings_module_uninstall_confirm)
                        },
                        color = scheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    val languages = listOf(
        "id" to stringResource(R.string.language_indonesian),
        "en" to stringResource(R.string.language_english)
    )
    SettingsOptionSheet(
        show = showLanguageSheet,
        title = stringResource(R.string.dialog_select_language),
        selectable = true,
        options = languages.map { SettingsSheetOption(it.first, it.second) },
        isSelected = { it == currentLanguage },
        onSelect = { tag ->
            if (currentLanguage != tag) {
                currentLanguage = tag
                coroutineScope.launch {
                    delay(120L)
                    setAppLanguage(context, tag)
                }
            }
        },
        onDismiss = { showLanguageSheet = false }
    )

    SettingsOptionSheet(
        show = showLogBottomSheet,
        title = stringResource(R.string.dialog_log_subsystem),
        selectable = false,
        options = listOf(
            SettingsSheetOption(
                value = "save",
                label = stringResource(R.string.settings_save_log),
                supporting = stringResource(R.string.log_option_save_desc)
            ),
            SettingsSheetOption(
                value = "share",
                label = stringResource(R.string.log_option_share),
                supporting = stringResource(R.string.log_option_share_desc)
            )
        ),
        isSelected = { false },
        onSelect = { action ->
            isGeneratingLog = true
            coroutineScope.launch {
                if (action == "save") {
                    val file = LogUtil.dumpDiagnosticLogs(context, saveToDownloads = true)
                    isGeneratingLog = false
                    if (file != null) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.toast_log_saved, file.name),
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.toast_log_export_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    val file = LogUtil.dumpDiagnosticLogs(context, saveToDownloads = false)
                    isGeneratingLog = false
                    if (file != null) {
                        val intent = LogUtil.getShareLogIntent(context, file)
                        context.startActivity(intent)
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.toast_log_share_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        },
        onDismiss = { showLogBottomSheet = false }
    )
}

@Composable
private fun MonetPersonalizationTile(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val scaleModifier = pressScale(interactionSource, 0.97f)
    val swatchColors = listOf(
        scheme.primaryContainer,
        scheme.inversePrimary,
        scheme.tertiary,
        scheme.primary
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(scaleModifier)
            .clip(RoundedCornerShape(26.dp))
            .background(scheme.surfaceContainerHigh)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.SemiBold,
                platformStyle = PlatformTextStyle(includeFontPadding = false)
            ),
            color = scheme.onSurface,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .width(113.dp)
                .height(38.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            swatchColors.forEachIndexed { idx, color ->
                Box(
                    modifier = Modifier
                        .offset(x = (idx * 25).dp)
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(color)
                )
            }
        }
    }
}

@Composable
private fun DeviceSpecsOverviewTile(
    deviceLabel: String,
    deviceValue: String,
    packageLabel: String,
    packageValue: String,
    versionLabel: String,
    versionValue: String,
    uptimeLabel: String,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    var uptimeText by remember { mutableStateOf(formatDeviceUptime()) }

    LaunchedEffect(Unit) {
        while (isActive) {
            uptimeText = formatDeviceUptime()
            delay(1000L)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        EditorialSpecItem(
            label = deviceLabel,
            value = deviceValue
        )
        EditorialSpecItem(
            label = packageLabel,
            value = packageValue
        )
        EditorialSpecItem(
            label = versionLabel,
            value = versionValue
        )
        EditorialSpecItem(
            label = uptimeLabel,
            value = uptimeText
        )
    }
}

@Composable
private fun EditorialSpecItem(
    label: String,
    value: String
) {
    val scheme = MaterialTheme.colorScheme
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

@Composable
private fun LockscreenHeroTile(
    wallpaper: ImageBitmap?,
    languageTag: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    var clockTickMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (isActive) {
            val now = System.currentTimeMillis()
            clockTickMs = now
            val remainder = 1000L - (now % 1000L)
            delay(remainder.coerceIn(250L, 1000L))
        }
    }

    val clockParts = remember(clockTickMs, context, languageTag) {
        val now = Date(clockTickMs)
        val cal = Calendar.getInstance().apply { time = now }
        val is24h = DateFormat.is24HourFormat(context)
        val rawHour = if (is24h) {
            cal.get(Calendar.HOUR_OF_DAY)
        } else {
            val h12 = cal.get(Calendar.HOUR)
            if (h12 == 0) 12 else h12
        }
        val hourStr = String.format(Locale.US, "%02d", rawHour)
        val minuteStr = String.format(Locale.US, "%02d", cal.get(Calendar.MINUTE))
        val locale = if (languageTag == "en") {
            Locale.ENGLISH
        } else {
            Locale.forLanguageTag("id-ID")
        }
        val rawDay = SimpleDateFormat("EEEE", locale).format(now)
        val dayStr = rawDay.replaceFirstChar { ch ->
            if (ch.isLowerCase()) ch.titlecase(locale) else ch.toString()
        }
        val dateStr = SimpleDateFormat("d MMMM yyyy", locale).format(now)
        listOf(hourStr, minuteStr, dayStr, dateStr)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(26.dp))
            .background(scheme.surfaceContainerHighest)
    ) {
        if (wallpaper != null) {
            Image(
                bitmap = wallpaper,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                scheme.primaryContainer,
                                scheme.surfaceContainerHighest
                            )
                        )
                    )
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.24f),
                            Color.Black.copy(alpha = 0.14f),
                            Color.Black.copy(alpha = 0.36f)
                        )
                    )
                )
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            val digitSize = (maxWidth.value * 0.31f).coerceIn(38f, 50f).sp
            val daySize = (digitSize.value * 0.48f).sp
            val dateSize = (digitSize.value * 0.27f).coerceIn(11f, 13.5f).sp

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.Center) {
                    Row(
                        modifier = Modifier.graphicsLayer { scaleY = 1.1f },
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = clockParts[0],
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontFamily = FontFamily.SansSerif,
                                fontSize = digitSize,
                                lineHeight = digitSize,
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-1.0).sp,
                                fontFeatureSettings = "tnum",
                                platformStyle = PlatformTextStyle(includeFontPadding = false)
                            ),
                            color = Color.White.copy(alpha = 0.56f),
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = clockParts[1],
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontFamily = FontFamily.SansSerif,
                                fontSize = digitSize,
                                lineHeight = digitSize,
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-1.0).sp,
                                fontFeatureSettings = "tnum",
                                platformStyle = PlatformTextStyle(includeFontPadding = false)
                            ),
                            color = Color.White.copy(alpha = 0.56f),
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Text(
                        text = clockParts[2],
                        modifier = Modifier.offset(y = 1.dp),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = FontFamily.SansSerif,
                            fontSize = daySize,
                            lineHeight = daySize,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.3).sp,
                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                        ),
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = clockParts[3],
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = FontFamily.SansSerif,
                        fontSize = dateSize,
                        lineHeight = dateSize,
                        fontWeight = FontWeight.Bold,
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    ),
                    color = Color.White,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SuiteMediumTile(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val scheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val scaleModifier = pressScale(interactionSource, 0.97f, enabled)

    Row(
        modifier = modifier
            .height(64.dp)
            .then(scaleModifier)
            .clip(RoundedCornerShape(22.dp))
            .background(scheme.surfaceContainerHigh)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .alpha(if (enabled) 1f else 0.55f)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = scheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Medium,
                platformStyle = PlatformTextStyle(includeFontPadding = false)
            ),
            color = scheme.onSurface,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    index: Int,
    count: Int,
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    val haptic = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    val shape = connectedContainerShape(index = index, count = count)
    val interactionSource = remember { MutableInteractionSource() }
    val pressScaleModifier = pressScale(interactionSource, 0.975f, enabled)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(pressScaleModifier)
            .clip(shape)
            .background(scheme.surfaceContainerHigh)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onCheckedChange(!checked)
            }
            .alpha(if (enabled) 1f else 0.55f)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.padding(end = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(scheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier.padding(start = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Switch(
                checked = checked,
                onCheckedChange = { next ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onCheckedChange(next)
                },
                enabled = enabled,
                interactionSource = interactionSource,
                thumbContent = {
                    if (checked) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize)
                        )
                    }
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = scheme.onPrimary,
                    checkedIconColor = scheme.primary,
                    checkedTrackColor = scheme.primary,
                    uncheckedThumbColor = scheme.outline,
                    uncheckedIconColor = scheme.surfaceContainerHighest,
                    uncheckedTrackColor = scheme.surfaceContainerHighest
                )
            )
        }
    }
}

@Composable
private fun SettingsActionRow(
    index: Int,
    count: Int,
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    highlightSubtitle: Boolean = false,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    val haptic = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    val shape = connectedContainerShape(index = index, count = count)
    val interactionSource = remember { MutableInteractionSource() }
    val pressScaleModifier = pressScale(interactionSource, 0.975f, enabled)
    val titleColor = if (accentColor == scheme.error) {
        scheme.error
    } else {
        scheme.onSurface
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(pressScaleModifier)
            .clip(shape)
            .background(scheme.surfaceContainerHigh)
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
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (highlightSubtitle) {
                    scheme.primary
                } else {
                    scheme.onSurfaceVariant
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier.padding(start = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = if (accentColor == scheme.error) {
                    scheme.error
                } else {
                    scheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun <T> SettingsOptionSheet(
    show: Boolean,
    title: String,
    selectable: Boolean,
    options: List<SettingsSheetOption<T>>,
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
                    val selected = selectable && isSelected(option.value)
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
                                ),
                                color = if (selected) scheme.onSecondaryContainer else scheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (option.supporting != null) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = option.supporting,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        if (selectable) {
                            SettingsRadioRing(selected = selected)
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = scheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsRadioRing(selected: Boolean) {
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

private fun formatDeviceUptime(): String {
    val totalSec = (SystemClock.elapsedRealtime() / 1000L).coerceAtLeast(0L)
    val hours = totalSec / 3600L
    val minutes = (totalSec % 3600L) / 60L
    val seconds = totalSec % 60L
    return "${hours}h ${minutes}m ${seconds}s"
}

@SuppressLint("MissingPermission")
private suspend fun loadSystemWallpaperBitmap(context: Context): ImageBitmap? {
    return withContext(Dispatchers.IO) {
        val wm = runCatching { WallpaperManager.getInstance(context) }.getOrNull()
        if (wm != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val lockBitmap = runCatching {
                    wm.getWallpaperFile(WallpaperManager.FLAG_LOCK)?.use { pfd ->
                        decodeSampledBitmapFromFd(pfd.fileDescriptor, 540)
                    }
                }.getOrNull()
                if (lockBitmap != null) return@withContext lockBitmap.asImageBitmap()

                val sysBitmap = runCatching {
                    wm.getWallpaperFile(WallpaperManager.FLAG_SYSTEM)?.use { pfd ->
                        decodeSampledBitmapFromFd(pfd.fileDescriptor, 540)
                    }
                }.getOrNull()
                if (sysBitmap != null) return@withContext sysBitmap.asImageBitmap()
            }

            val drawableBitmap = runCatching {
                wm.drawable?.let { drawableToSampledBitmap(it, 540) }
            }.getOrNull()
            if (drawableBitmap != null) return@withContext drawableBitmap.asImageBitmap()
        }

        val rootBitmap = runCatching {
            val p = Runtime.getRuntime().exec("su")
            p.outputStream.bufferedWriter().use { writer ->
                writer.write(
                    "for f in /data/system/users/0/wallpaper_lock /data/system/users/0/wallpaper_lock_orig /data/system/users/0/wallpaper /data/system/users/0/wallpaper_orig; do " +
                        "if [ -s \"\$f\" ]; then cat \"\$f\"; break; fi; " +
                        "done\n"
                )
                writer.write("exit\n")
                writer.flush()
            }
            val bytes = p.inputStream.use { it.readBytes() }
            if (!p.waitFor(5, TimeUnit.SECONDS)) p.destroyForcibly()
            if (bytes.isNotEmpty()) decodeSampledBitmapFromBytes(bytes, 540) else null
        }.getOrNull()

        rootBitmap?.asImageBitmap()
    }
}

private fun decodeSampledBitmapFromFd(fd: java.io.FileDescriptor, reqWidth: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFileDescriptor(fd, null, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val opts = BitmapFactory.Options().apply {
        inSampleSize = calculateInSampleSize(bounds.outWidth, reqWidth)
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return BitmapFactory.decodeFileDescriptor(fd, null, opts)
}

private fun decodeSampledBitmapFromBytes(data: ByteArray, reqWidth: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val opts = BitmapFactory.Options().apply {
        inSampleSize = calculateInSampleSize(bounds.outWidth, reqWidth)
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return BitmapFactory.decodeByteArray(data, 0, data.size, opts)
}

private fun drawableToSampledBitmap(drawable: Drawable, maxWidth: Int): Bitmap? {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        val src = drawable.bitmap
        if (src.width <= maxWidth) return src
        val ratio = maxWidth.toFloat() / src.width.toFloat()
        val dstH = (src.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, maxWidth, dstH, true)
    }
    val rawW = drawable.intrinsicWidth.takeIf { it > 0 } ?: maxWidth
    val rawH = drawable.intrinsicHeight.takeIf { it > 0 } ?: (maxWidth * 2)
    val scale = (maxWidth.toFloat() / rawW.toFloat()).coerceAtMost(1f)
    val outW = (rawW * scale).toInt().coerceAtLeast(1)
    val outH = (rawH * scale).toInt().coerceAtLeast(1)
    val bmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    drawable.setBounds(0, 0, outW, outH)
    drawable.draw(canvas)
    return bmp
}

private fun calculateInSampleSize(rawWidth: Int, reqWidth: Int): Int {
    var inSampleSize = 1
    while (rawWidth / (inSampleSize * 2) >= reqWidth) {
        inSampleSize *= 2
    }
    return inSampleSize
}

private suspend fun uninstallModule(context: Context) {
    withContext(Dispatchers.IO) {
        try {
            val p = Runtime.getRuntime().exec("su")
            p.outputStream.bufferedWriter().use { writer ->
                writer.write("touch /data/adb/.config/lumina/daemon.disabled 2>/dev/null || true\n")
                writer.write("if [ -f /data/local/tmp/lumina_watchdog.pid ]; then\n")
                writer.write("  P=\$(cat /data/local/tmp/lumina_watchdog.pid 2>/dev/null)\n")
                writer.write("  if [ -n \"\$P\" ] && grep -qa service.sh \"/proc/\$P/cmdline\" 2>/dev/null; then kill -9 \"\$P\" 2>/dev/null; fi\n")
                writer.write("  rm -f /data/local/tmp/lumina_watchdog.pid\n")
                writer.write("fi\n")
                writer.write("pkill -TERM luminad 2>/dev/null || killall -TERM luminad 2>/dev/null\n")
                writer.write("pkill -TERM lumina-binder 2>/dev/null || killall -TERM lumina-binder 2>/dev/null\n")
                writer.write("sleep 1\n")
                writer.write("pkill -9 luminad 2>/dev/null || killall -9 luminad 2>/dev/null\n")
                writer.write("pkill -9 lumina-binder 2>/dev/null || killall -9 lumina-binder 2>/dev/null\n")
                writer.write("rm -f /dev/socket/lumina.sock /dev/socket/lumina_ipc.sock /dev/socket/lumina_binder.sock\n")
                writer.write("rm -f /data/local/tmp/lumina.sock /data/local/tmp/lumina_ipc.sock /data/local/tmp/lumina_binder.sock\n")
                writer.write("rm -f /data/local/tmp/luminad.lock /data/local/tmp/lumina-binder.lock\n")
                writer.write("rm -rf /data/adb/.config/lumina /data/adb/luminatweaks\n")
                writer.write("rm -rf /data/adb/modules/luminatweaks\n")
                writer.write("rm -rf /data/adb/modules_update/luminatweaks\n")
                writer.write("(sleep 1 && pm uninstall com.luminatweaks) &\n")
                writer.write("exit\n")
                writer.flush()
            }
            if (!p.waitFor(30, TimeUnit.SECONDS)) p.destroyForcibly()
        } catch (_: Exception) {}
    }
    withContext(Dispatchers.Main) {
        (context as? Activity)?.finishAffinity()
        android.os.Process.killProcess(android.os.Process.myPid())
    }
}

private fun getCurrentLanguage(context: Context): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
        if (locales != null && !locales.isEmpty) {
            locales[0].language
        } else {
            Locale.getDefault().language
        }
    } else {
        val config = context.resources.configuration
        config.locales[0].language
    }
}

private fun setAppLanguage(context: Context, languageTag: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.getSystemService(LocaleManager::class.java)?.applicationLocales =
            LocaleList.forLanguageTags(languageTag)
    } else {
        val locale = Locale.forLanguageTag(languageTag)
        Locale.setDefault(locale)
        val config = context.resources.configuration
        config.setLocale(locale)
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }
}
