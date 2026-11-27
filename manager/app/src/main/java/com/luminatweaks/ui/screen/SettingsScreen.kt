@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package com.luminatweaks.ui.screen

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Shield
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luminatweaks.BuildConfig
import com.luminatweaks.R
import com.luminatweaks.data.ConfigRepository
import com.luminatweaks.ui.component.TouchOriginDialog
import com.luminatweaks.ui.component.appBarFade
import com.luminatweaks.ui.component.pressScale
import com.luminatweaks.ui.component.rememberDialogOrigin
import com.luminatweaks.ui.theme.ColorMode
import com.luminatweaks.ui.theme.LocalFloatingNavBar
import com.luminatweaks.ui.theme.ThemeController
import com.luminatweaks.util.LogUtil
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    val uriHandler = LocalUriHandler.current
    val coroutineScope = rememberCoroutineScope()
    val isFloating = LocalFloatingNavBar.current

    var currentLanguage by remember {
        val lang = getCurrentLanguage(context)
        mutableStateOf(if (lang.startsWith("en")) "en" else "id")
    }

    var currentColorMode by remember {
        mutableStateOf(ThemeController.getAppSettings(context).colorMode)
    }

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

    val activeSettingsSubscreen = when {
        showAboutScreen && onNavigateToAbout == null -> "about"
        showPersonalisasiScreen && onNavigateToPersonalisasi == null -> "personalisasi"
        showCompatibilityScreen && onNavigateToCompatibility == null -> "compatibility"
        showTweaksScreen && onNavigateToTweaks == null -> "tweaks"
        else -> "main"
    }

    LaunchedEffect(Unit) {
        isToastNotifications = ConfigRepository.getToastNotifications()
        currentColorMode = ThemeController.getAppSettings(context).colorMode
    }

    LuminaDrillTransition(
        targetState = activeSettingsSubscreen,
        isForward = { _, target -> target != "main" }
    ) { route ->
        when (route) {
            "about" -> AboutScreen(onBackClick = { showAboutScreen = false })
            "personalisasi" -> PersonalisasiScreen(
                onBackClick = {
                    currentColorMode = ThemeController.getAppSettings(context).colorMode
                    showPersonalisasiScreen = false
                }
            )
            "compatibility" -> CompatibilityScreen(onBackClick = { showCompatibilityScreen = false })
            "tweaks" -> TweaksScreen(onBackClick = { showTweaksScreen = false })
            else -> {
                val scheme = MaterialTheme.colorScheme
                val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                val bottomPadding = if (isFloating) 112.dp + navInset else 28.dp + navInset
                val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
                val fadeSurface = scheme.background
                val cleanTextStyle = MaterialTheme.typography.labelLarge.copy(
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                )

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

                val cycleThemeMode: () -> Unit = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val saved = ThemeController.getAppSettings(context)
                    val nextMode = when (saved.colorMode) {
                        ColorMode.SYSTEM -> ColorMode.DARK
                        ColorMode.DARK -> ColorMode.DARKAMOLED
                        ColorMode.DARKAMOLED -> ColorMode.LIGHT
                        ColorMode.LIGHT -> ColorMode.SYSTEM
                    }
                    currentColorMode = nextMode
                    ThemeController.saveTheme(
                        context = context,
                        mode = nextMode,
                        key = saved.keyColor,
                        spec = saved.colorSpec,
                        intensity = saved.accentIntensity,
                        cornerRadius = saved.cornerRadius,
                        floatingNavBar = saved.floatingNavBar
                    )
                }

                val selectLanguage: (String) -> Unit = { tag ->
                    if (currentLanguage != tag) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        currentLanguage = tag
                        coroutineScope.launch {
                            delay(90L)
                            setAppLanguage(context, tag)
                        }
                    }
                }

                Scaffold(
                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentWindowInsets = WindowInsets(0.dp),
                    containerColor = scheme.background,
                    topBar = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .appBarFade(fadeSurface) { scrollBehavior.state.overlappedFraction }
                                .statusBarsPadding()
                        ) {
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
                        modifier = Modifier.fillMaxSize(),
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
                                    .height(IntrinsicSize.Max),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    MonetPersonalizationTile(
                                        title = stringResource(R.string.personalization_title),
                                        onClick = openPersonalisasi
                                    )

                                    ThemeAndLanguageTile(
                                        colorMode = currentColorMode,
                                        currentLanguage = currentLanguage,
                                        languageTitle = stringResource(R.string.settings_language),
                                        onCycleThemeMode = cycleThemeMode,
                                        onSelectLanguage = selectLanguage,
                                        onOpenLanguageSheet = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            showLanguageSheet = true
                                        }
                                    )
                                }

                                CapabilityHeroTile(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    title = stringResource(R.string.settings_compatibility),
                                    onClick = openCompatibility
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

                        item(key = "toast_banner_tile") {
                            val toggleToast: (Boolean) -> Unit = { enabled ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                isToastNotifications = enabled
                                coroutineScope.launch {
                                    ConfigRepository.setToastNotifications(enabled)
                                }
                            }
                            val interactionSource = remember { MutableInteractionSource() }
                            val scaleModifier = pressScale(interactionSource, 0.98f)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(scaleModifier)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(scheme.surfaceContainerHigh)
                                    .clickable(
                                        interactionSource = interactionSource,
                                        indication = null
                                    ) { toggleToast(!isToastNotifications) }
                                    .padding(horizontal = 18.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.settings_toast_notifications),
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                                        ),
                                        color = scheme.onSurface,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = settingsLocalizedLabel(
                                            "Tampilkan info saat profil aktif berganti",
                                            "Show alert when active profile switches"
                                        ),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                                        ),
                                        color = scheme.onSurfaceVariant,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Switch(
                                    checked = isToastNotifications,
                                    onCheckedChange = toggleToast,
                                    interactionSource = interactionSource,
                                    thumbContent = {
                                        if (isToastNotifications) {
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

                        item(key = "bottom_icon_grid") {
                            val versionClean = remember {
                                "v" + BuildConfig.VERSION_NAME.removeSuffix(".0")
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                SuiteSquareIconTile(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Rounded.Info,
                                    label = stringResource(R.string.settings_about),
                                    badge = versionClean,
                                    textStyle = cleanTextStyle,
                                    onClick = openAbout
                                )

                                SuiteSquareIconTile(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Rounded.Code,
                                    label = "GitHub",
                                    textStyle = cleanTextStyle,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        uriHandler.openUri("https://github.com/kimal404/LuminaTweaks")
                                    }
                                )

                                SuiteSquareIconTile(
                                    modifier = Modifier
                                        .weight(1f)
                                        .then(uninstallOrigin.trackedModifier()),
                                    icon = Icons.Rounded.DeleteForever,
                                    label = settingsLocalizedLabel("Hapus", "Uninstall"),
                                    isDestructive = true,
                                    enabled = !isUninstalling,
                                    textStyle = cleanTextStyle,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
        }
    }
}

@Composable
private fun MonetPersonalizationTile(
    title: String,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val scaleModifier = pressScale(interactionSource, 0.97f)
    val swatchColors = listOf(
        scheme.inversePrimary,
        scheme.primary,
        scheme.tertiary,
        scheme.primaryContainer
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(scaleModifier)
            .clip(RoundedCornerShape(26.dp))
            .background(scheme.surfaceContainerHigh)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
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

        Box(
            modifier = Modifier
                .width(106.dp)
                .height(34.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            swatchColors.forEachIndexed { idx, color ->
                Box(
                    modifier = Modifier
                        .offset(x = (idx * 24).dp)
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(color)
                )
            }
        }
    }
}

@Composable
private fun ThemeAndLanguageTile(
    colorMode: ColorMode,
    currentLanguage: String,
    languageTitle: String,
    onCycleThemeMode: () -> Unit,
    onSelectLanguage: (String) -> Unit,
    onOpenLanguageSheet: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val themeInteraction = remember { MutableInteractionSource() }
    val themeScale = pressScale(themeInteraction, 0.96f)

    val modeLabel = when (colorMode) {
        ColorMode.SYSTEM -> settingsLocalizedLabel("Tema Sistem", "System Theme")
        ColorMode.LIGHT -> settingsLocalizedLabel("Tema Terang", "Light Theme")
        ColorMode.DARK -> settingsLocalizedLabel("Tema Gelap", "Dark Theme")
        ColorMode.DARKAMOLED -> settingsLocalizedLabel("AMOLED Hitam", "AMOLED Pitch")
    }
    val modeIcon = when (colorMode) {
        ColorMode.LIGHT -> Icons.Rounded.LightMode
        else -> Icons.Rounded.DarkMode
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(themeScale)
                .clip(RoundedCornerShape(18.dp))
                .background(scheme.surfaceContainerLowest)
                .clickable(
                    interactionSource = themeInteraction,
                    indication = null,
                    onClick = onCycleThemeMode
                )
                .padding(horizontal = 12.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = modeIcon,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = modeLabel,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                ),
                color = scheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = languageTitle,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Medium,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                ),
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable(onClick = onOpenLanguageSheet)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LanguageMiniPill(
                    modifier = Modifier.weight(1f),
                    label = "ID",
                    selected = currentLanguage != "en",
                    onClick = { onSelectLanguage("id") }
                )
                LanguageMiniPill(
                    modifier = Modifier.weight(1f),
                    label = "EN",
                    selected = currentLanguage == "en",
                    onClick = { onSelectLanguage("en") }
                )
            }
        }
    }
}

@Composable
private fun LanguageMiniPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val scaleModifier = pressScale(interactionSource, 0.94f)
    val bgColor by animateColorAsState(
        targetValue = if (selected) scheme.primary else scheme.surfaceContainerLowest,
        label = "langPillBg"
    )
    val fgColor by animateColorAsState(
        targetValue = if (selected) scheme.onPrimary else scheme.onSurface,
        label = "langPillFg"
    )

    Box(
        modifier = modifier
            .height(34.dp)
            .then(scaleModifier)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Bold,
                platformStyle = PlatformTextStyle(includeFontPadding = false)
            ),
            color = fgColor,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun CapabilityHeroTile(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val scaleModifier = pressScale(interactionSource, 0.97f)
    val accentStroke = scheme.primary.copy(alpha = 0.22f)
    val secondaryStroke = scheme.tertiary.copy(alpha = 0.16f)

    Box(
        modifier = modifier
            .then(scaleModifier)
            .clip(RoundedCornerShape(26.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        scheme.primaryContainer.copy(alpha = 0.65f),
                        scheme.surfaceContainerHighest
                    )
                )
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = accentStroke,
                radius = size.minDimension * 0.56f,
                center = Offset(size.width * 0.85f, size.height * 0.28f),
                style = Stroke(width = 18.dp.toPx())
            )
            drawCircle(
                color = secondaryStroke,
                radius = size.minDimension * 0.42f,
                center = Offset(size.width * 0.18f, size.height * 0.78f),
                style = Stroke(width = 12.dp.toPx())
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
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
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "SAFE",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        lineHeight = 30.sp,
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    ),
                    color = scheme.onPrimaryContainer,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = "GUARD",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        lineHeight = 30.sp,
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    ),
                    color = scheme.primary,
                    maxLines = 1,
                    softWrap = false
                )
            }

            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(scheme.surfaceContainerLowest.copy(alpha = 0.72f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(16.dp)
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
private fun SuiteSquareIconTile(
    icon: ImageVector,
    label: String,
    textStyle: androidx.compose.ui.text.TextStyle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
    isDestructive: Boolean = false,
    enabled: Boolean = true
) {
    val scheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val scaleModifier = pressScale(interactionSource, 0.95f, enabled)
    val bgColor = if (isDestructive) {
        scheme.errorContainer.copy(alpha = 0.42f)
    } else {
        scheme.surfaceContainerHigh
    }
    val fgColor = if (isDestructive) scheme.error else scheme.onSurface
    val iconColor = if (isDestructive) scheme.error else scheme.primary

    Column(
        modifier = modifier
            .height(92.dp)
            .then(scaleModifier)
            .clip(RoundedCornerShape(24.dp))
            .background(bgColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .alpha(if (enabled) 1f else 0.55f)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = textStyle.copy(fontWeight = FontWeight.Medium),
            color = fgColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        if (badge != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = badge,
                style = MaterialTheme.typography.labelSmall.copy(
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                ),
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false
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
