/*
 * Copyright (C) 2026 kimal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.luminatweaks.ui.screen

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.VideogameAsset
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.luminatweaks.R
import com.luminatweaks.data.ConfigRepository
import com.luminatweaks.data.DaemonManager
import com.luminatweaks.data.DetectedGame
import com.luminatweaks.ui.theme.ColorEngine
import com.luminatweaks.ui.theme.ColorMode
import com.luminatweaks.ui.theme.ThemeController
import com.luminatweaks.util.RootUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

private const val PAGE_COUNT = 4
private val CardShape = RoundedCornerShape(24.dp)

private enum class CheckState { CHECKING, READY, MISSING }

private data class DecorIconSpec(
    val icon: ImageVector,
    val size: Dp,
    val alignment: Alignment,
    val offsetX: Dp,
    val offsetY: Dp,
    val rotation: Float,
    val alpha: Float,
    val usePrimaryTint: Boolean = true,
    val phaseOffset: Float = 0f
)

private val colorModeOptions = listOf(
    ColorMode.SYSTEM to R.string.mode_system,
    ColorMode.DARK to R.string.mode_dark,
    ColorMode.DARKAMOLED to R.string.mode_amoled
)

private val colorEngineOptions = listOf(
    ColorEngine.MaterialYou to R.string.personalization_engine_material_you,
    ColorEngine.MaterialExpressive to R.string.personalization_engine_expressive
)

@Composable
fun SetupWizardScreen(
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val savedTheme = remember { ThemeController.getAppSettings(context) }

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { PAGE_COUNT })

    var isCheckingRoot by remember { mutableStateOf(true) }
    var isRootGranted by remember { mutableStateOf(false) }
    var rootProvider by remember { mutableStateOf<String?>(null) }
    var resumeTrigger by remember { mutableIntStateOf(0) }

    var floatingNavBar by remember { mutableStateOf(savedTheme.floatingNavBar) }
    var colorMode by remember { mutableStateOf(savedTheme.colorMode) }
    var colorEngine by remember { mutableStateOf(savedTheme.colorSpec) }

    suspend fun verifyRoot(showSpinner: Boolean = false) {
        if (showSpinner && !isRootGranted) {
            isCheckingRoot = true
        }
        val granted = probeRootAccess()
        val provider = if (granted) detectRootManagerName(context) else null
        isRootGranted = granted
        rootProvider = provider
        isCheckingRoot = false
    }

    fun persistTheme(
        mode: ColorMode = colorMode,
        engine: ColorEngine = colorEngine,
        floating: Boolean = floatingNavBar
    ) {
        ThemeController.saveTheme(
            context = context,
            mode = mode,
            key = savedTheme.keyColor,
            spec = engine,
            intensity = savedTheme.accentIntensity,
            cornerRadius = savedTheme.cornerRadius,
            floatingNavBar = floating
        )
    }

    DisposableEffect(context) {
        val lifecycleOwner = context as? LifecycleOwner
        if (lifecycleOwner == null) {
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    resumeTrigger += 1
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }
    }

    LaunchedEffect(Unit) {
        verifyRoot(showSpinner = true)
    }

    LaunchedEffect(resumeTrigger) {
        if (resumeTrigger > 0) {
            verifyRoot(showSpinner = false)
        }
    }

    val currentPage = pagerState.currentPage

    // Auto-deteksi saat dialog Superuser overlay (KernelSU / Magisk / APatch) baru disetujui
    LaunchedEffect(currentPage, isRootGranted) {
        if (currentPage == 0 && !isRootGranted) {
            while (isActive && !isRootGranted) {
                delay(1200L)
                verifyRoot(showSpinner = false)
            }
        }
    }

    val goNext: () -> Unit = {
        if (currentPage < PAGE_COUNT - 1) {
            scope.launch { pagerState.animateScrollToPage(currentPage + 1) }
        } else {
            onComplete()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            WizardTopBar(
                page = currentPage,
                pageOffsetFraction = pagerState.currentPageOffsetFraction,
                canGoBack = currentPage > 0,
                onBack = {
                    if (currentPage > 0) {
                        scope.launch { pagerState.animateScrollToPage(currentPage - 1) }
                    }
                },
                onSkip = onComplete
            )
        },
        bottomBar = {
            WizardFooter(
                page = currentPage,
                onNext = goNext
            )
        }
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            beyondViewportPageCount = 1,
            key = { it }
        ) { page ->
            when (page) {
                0 -> WelcomePage(
                    isCheckingRoot = isCheckingRoot,
                    isRootGranted = isRootGranted,
                    rootProvider = rootProvider,
                    onRetry = { scope.launch { verifyRoot(showSpinner = true) } }
                )

                1 -> DiagnosticsPage(isActive = currentPage == 1)

                2 -> AppearancePage(
                    floatingNavBar = floatingNavBar,
                    onFloatingNavBarChange = { enabled ->
                        floatingNavBar = enabled
                        persistTheme(floating = enabled)
                    },
                    colorMode = colorMode,
                    onColorModeChange = { mode ->
                        colorMode = mode
                        persistTheme(mode = mode)
                    },
                    colorEngine = colorEngine,
                    onColorEngineChange = { engine ->
                        colorEngine = engine
                        persistTheme(engine = engine)
                    }
                )

                else -> GamesPage(isRootGranted = isRootGranted)
            }
        }
    }
}

@Composable
private fun WizardTopBar(
    page: Int,
    pageOffsetFraction: Float,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onSkip: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(84.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            WizardBackButton(visible = canGoBack, onClick = onBack)
        }

        val position = (page + pageOffsetFraction).coerceIn(0f, (PAGE_COUNT - 1).toFloat())

        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(PAGE_COUNT) { index ->
                val strength = (1f - abs(position - index)).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .height(6.dp)
                        .width((16f + 20f * strength).dp)
                        .clip(CircleShape)
                        .background(
                            lerp(
                                MaterialTheme.colorScheme.surfaceContainerHighest,
                                MaterialTheme.colorScheme.primary,
                                strength
                            )
                        )
                )
            }
        }

        Box(
            modifier = Modifier.width(84.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            WizardSkipButton(
                visible = page < PAGE_COUNT - 1,
                onClick = onSkip
            )
        }
    }
}

@Composable
private fun WizardBackButton(
    visible: Boolean,
    onClick: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.8f),
        exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.8f)
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.setup_back),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun WizardSkipButton(
    visible: Boolean,
    onClick: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(120))
    ) {
        TextButton(
            onClick = onClick,
            modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 36.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = stringResource(R.string.setup_skip),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible
            )
        }
    }
}

@Composable
private fun WizardFooter(
    page: Int,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(top = 8.dp, bottom = 12.dp)
    ) {
        AnimatedContent(
            targetState = page,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            label = "setupFooterNote"
        ) { targetPage ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(footerNoteForPage(targetPage)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = onNext,
            shape = CircleShape,
            contentPadding = PaddingValues(horizontal = 24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Text(
                text = stringResource(
                    if (page == PAGE_COUNT - 1) R.string.setup_start else R.string.setup_next
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = if (page == PAGE_COUNT - 1) {
                    Icons.Rounded.RocketLaunch
                } else {
                    Icons.Rounded.ArrowForward
                },
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@StringRes
private fun footerNoteForPage(page: Int): Int = when (page) {
    0 -> R.string.setup_root_note
    1 -> R.string.setup_diag_note
    2 -> R.string.setup_appearance_note
    else -> R.string.setup_games_note
}

@Composable
private fun WizardBackgroundDecor(
    specs: List<DecorIconSpec>,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wizardBgDecor")
    val drift by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wizardBgDrift"
    )

    Box(modifier = modifier.fillMaxSize()) {
        specs.forEach { spec ->
            val bobY = (drift * 6f * (if (spec.phaseOffset >= 0f) 1f else -1f)).dp
            val tint = if (spec.usePrimaryTint) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onBackground
            }
            Box(
                modifier = Modifier
                    .align(spec.alignment)
                    .offset(x = spec.offsetX, y = spec.offsetY + bobY)
                    .rotate(spec.rotation)
                    .alpha(spec.alpha)
                    .size(spec.size + 22.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(tint.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = spec.icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(spec.size)
                )
            }
        }
    }
}

@Composable
private fun WizardPage(
    @StringRes titleRes: Int,
    @StringRes descriptionRes: Int,
    decorSpecs: List<DecorIconSpec> = emptyList(),
    content: @Composable ColumnScope.() -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (decorSpecs.isNotEmpty()) {
            WizardBackgroundDecor(specs = decorSpecs)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 24.dp)
        ) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(descriptionRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            content()
        }
    }
}

@Composable
private fun WelcomePage(
    isCheckingRoot: Boolean,
    isRootGranted: Boolean,
    rootProvider: String?,
    onRetry: () -> Unit
) {
    val rootDecor = remember {
        listOf(
            DecorIconSpec(
                icon = Icons.Rounded.Security,
                size = 64.dp,
                alignment = Alignment.BottomEnd,
                offsetX = (-18).dp,
                offsetY = (-44).dp,
                rotation = -14f,
                alpha = 0.14f,
                usePrimaryTint = true,
                phaseOffset = 1f
            ),
            DecorIconSpec(
                icon = Icons.Rounded.AdminPanelSettings,
                size = 54.dp,
                alignment = Alignment.BottomStart,
                offsetX = 20.dp,
                offsetY = (-84).dp,
                rotation = 12f,
                alpha = 0.12f,
                usePrimaryTint = false,
                phaseOffset = -1f
            ),
            DecorIconSpec(
                icon = Icons.Rounded.Terminal,
                size = 44.dp,
                alignment = Alignment.BottomCenter,
                offsetX = (-6).dp,
                offsetY = (-20).dp,
                rotation = -8f,
                alpha = 0.11f,
                usePrimaryTint = true,
                phaseOffset = 1f
            ),
            DecorIconSpec(
                icon = Icons.Rounded.Key,
                size = 38.dp,
                alignment = Alignment.CenterEnd,
                offsetX = (-28).dp,
                offsetY = 68.dp,
                rotation = 22f,
                alpha = 0.10f,
                usePrimaryTint = false,
                phaseOffset = -1f
            ),
            DecorIconSpec(
                icon = Icons.Rounded.VerifiedUser,
                size = 40.dp,
                alignment = Alignment.CenterStart,
                offsetX = 28.dp,
                offsetY = 56.dp,
                rotation = -16f,
                alpha = 0.10f,
                usePrimaryTint = true,
                phaseOffset = 1f
            )
        )
    }

    WizardPage(
        titleRes = R.string.setup_title_welcome,
        descriptionRes = R.string.setup_desc_welcome,
        decorSpecs = rootDecor
    ) {
        val badgeColor = when {
            isCheckingRoot -> MaterialTheme.colorScheme.surfaceContainerHigh
            isRootGranted -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.error
        }
        val badgeContentColor = when {
            isCheckingRoot -> MaterialTheme.colorScheme.onSurfaceVariant
            isRootGranted -> MaterialTheme.colorScheme.onPrimary
            else -> MaterialTheme.colorScheme.onError
        }
        val grantedText = stringResource(R.string.setup_root_granted)
        val subtitle = when {
            isCheckingRoot -> stringResource(R.string.setup_root_checking)
            isRootGranted -> rootProvider
                ?.takeIf { it.isNotBlank() }
                ?.let { "$grantedText · $it" }
                ?: grantedText

            else -> stringResource(R.string.setup_root_denied)
        }

        StatusHeroCard(
            icon = Icons.Rounded.Security,
            title = stringResource(R.string.setup_root_privilege),
            subtitle = subtitle,
            badgeColor = badgeColor,
            badgeContentColor = badgeContentColor,
            trailing = {
                AnimatedContent(
                    targetState = when {
                        isCheckingRoot -> 0
                        isRootGranted -> 1
                        else -> 2
                    },
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                    label = "setupRootStatus"
                ) { state ->
                    when (state) {
                        0 -> CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.primary
                        )

                        1 -> Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        else -> IconButton(onClick = onRetry) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = stringResource(R.string.setup_retry),
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun DiagnosticsPage(isActive: Boolean) {
    var revealCount by remember { mutableIntStateOf(0) }
    var coreChecked by remember { mutableStateOf(false) }
    var corePid by remember { mutableStateOf<String?>(null) }
    var socChecked by remember { mutableStateOf(false) }
    var socLabel by remember { mutableStateOf<String?>(null) }
    var binderChecked by remember { mutableStateOf(false) }
    var binderReady by remember { mutableStateOf(false) }

    val gearRocketDecor = remember {
        listOf(
            DecorIconSpec(
                icon = Icons.Rounded.RocketLaunch,
                size = 64.dp,
                alignment = Alignment.BottomEnd,
                offsetX = (-20).dp,
                offsetY = (-36).dp,
                rotation = -12f,
                alpha = 0.14f,
                usePrimaryTint = true,
                phaseOffset = 1f
            ),
            DecorIconSpec(
                icon = Icons.Rounded.Settings,
                size = 56.dp,
                alignment = Alignment.BottomStart,
                offsetX = 22.dp,
                offsetY = (-68).dp,
                rotation = 18f,
                alpha = 0.12f,
                usePrimaryTint = false,
                phaseOffset = -1f
            ),
            DecorIconSpec(
                icon = Icons.Rounded.Tune,
                size = 42.dp,
                alignment = Alignment.BottomCenter,
                offsetX = (-4).dp,
                offsetY = (-16).dp,
                rotation = -10f,
                alpha = 0.11f,
                usePrimaryTint = true,
                phaseOffset = 1f
            ),
            DecorIconSpec(
                icon = Icons.Rounded.Speed,
                size = 40.dp,
                alignment = Alignment.BottomEnd,
                offsetX = (-96).dp,
                offsetY = (-96).dp,
                rotation = 14f,
                alpha = 0.09f,
                usePrimaryTint = false,
                phaseOffset = -1f
            )
        )
    }

    LaunchedEffect(isActive) {
        if (!isActive || revealCount >= 3) return@LaunchedEffect
        delay(80)
        revealCount = 1
        delay(110)
        revealCount = 2
        delay(110)
        revealCount = 3
    }

    LaunchedEffect(isActive) {
        if (!isActive) return@LaunchedEffect
        launch {
            val pid = runCatching { DaemonManager.getDaemonPid() }.getOrNull()
            corePid = pid
            coreChecked = true
        }
        launch {
            socLabel = runCatching { DaemonManager.getSocLabel() }.getOrNull()
            socChecked = true
        }
        launch {
            binderReady = runCatching { DaemonManager.isBinderBridgeReady() }.getOrDefault(false)
            binderChecked = true
        }
    }

    WizardPage(
        titleRes = R.string.setup_title_diagnostics,
        descriptionRes = R.string.setup_desc_diagnostics,
        decorSpecs = gearRocketDecor
    ) {
        val coreState = when {
            !coreChecked -> CheckState.CHECKING
            corePid != null -> CheckState.READY
            else -> CheckState.MISSING
        }
        val socState = when {
            !socChecked -> CheckState.CHECKING
            socLabel != null -> CheckState.READY
            else -> CheckState.MISSING
        }
        val binderState = when {
            !binderChecked -> CheckState.CHECKING
            binderReady -> CheckState.READY
            else -> CheckState.MISSING
        }

        val coreText = when {
            !coreChecked -> stringResource(R.string.setup_status_checking)
            corePid == null -> stringResource(R.string.setup_status_offline)
            corePid == "ipc" -> stringResource(R.string.setup_core_ipc)
            else -> stringResource(R.string.setup_core_pid, corePid ?: "-")
        }
        val socText = when {
            !socChecked -> stringResource(R.string.setup_status_checking)
            socLabel != null -> socLabel ?: ""
            else -> stringResource(R.string.setup_status_missing)
        }
        val binderText = when {
            !binderChecked -> stringResource(R.string.setup_status_checking)
            binderReady -> stringResource(R.string.setup_status_detected)
            else -> stringResource(R.string.setup_status_missing)
        }

        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            StaggeredItem(visible = revealCount > 0) {
                DiagnosticRow(
                    icon = Icons.Rounded.Memory,
                    title = stringResource(R.string.setup_core_name),
                    subtitle = stringResource(R.string.setup_core_desc),
                    status = coreState,
                    statusText = coreText,
                    shape = groupedShape(0, 3)
                )
            }
            StaggeredItem(visible = revealCount > 1) {
                DiagnosticRow(
                    icon = Icons.Rounded.DeveloperBoard,
                    title = stringResource(R.string.setup_soc_name),
                    subtitle = stringResource(R.string.setup_soc_desc),
                    status = socState,
                    statusText = socText,
                    shape = groupedShape(1, 3)
                )
            }
            StaggeredItem(visible = revealCount > 2) {
                DiagnosticRow(
                    icon = Icons.Rounded.Settings,
                    title = stringResource(R.string.setup_binder_name),
                    subtitle = stringResource(R.string.setup_binder_desc),
                    status = binderState,
                    statusText = binderText,
                    shape = groupedShape(2, 3)
                )
            }
        }
    }
}

@Composable
private fun StaggeredItem(
    visible: Boolean,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(240)) + slideInVertically(tween(240)) { it / 3 }
    ) {
        content()
    }
}

@Composable
private fun DiagnosticRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    status: CheckState,
    statusText: String,
    shape: Shape
) {
    val statusColor = when (status) {
        CheckState.CHECKING -> MaterialTheme.colorScheme.onSurfaceVariant
        CheckState.READY -> MaterialTheme.colorScheme.primary
        CheckState.MISSING -> MaterialTheme.colorScheme.error
    }
    val iconContainer = when (status) {
        CheckState.READY -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val iconTint = when (status) {
        CheckState.READY -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconContainer),
                contentAlignment = Alignment.Center
            ) {
                if (status == CheckState.CHECKING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = iconTint
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (status != CheckState.CHECKING) {
                    Icon(
                        imageVector = if (status == CheckState.READY) {
                            Icons.Rounded.CheckCircle
                        } else {
                            Icons.Rounded.ErrorOutline
                        },
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = statusColor
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = statusColor,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun AppearancePage(
    floatingNavBar: Boolean,
    onFloatingNavBarChange: (Boolean) -> Unit,
    colorMode: ColorMode,
    onColorModeChange: (ColorMode) -> Unit,
    colorEngine: ColorEngine,
    onColorEngineChange: (ColorEngine) -> Unit
) {
    WizardPage(
        titleRes = R.string.setup_title_appearance,
        descriptionRes = R.string.setup_desc_appearance
    ) {
        ToggleHeroCard(
            icon = Icons.Rounded.Palette,
            title = stringResource(R.string.setup_floating_nav),
            subtitle = stringResource(R.string.setup_floating_nav_desc),
            checked = floatingNavBar,
            onCheckedChange = onFloatingNavBarChange
        )

        Spacer(modifier = Modifier.height(22.dp))

        GroupLabel(text = stringResource(R.string.setup_color_mode))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            colorModeOptions.forEachIndexed { index, (mode, labelRes) ->
                GroupedOptionRow(
                    label = stringResource(labelRes),
                    selected = colorMode == mode,
                    shape = groupedShape(index, colorModeOptions.size),
                    onClick = { onColorModeChange(mode) }
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        GroupLabel(text = stringResource(R.string.setup_color_engine))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            colorEngineOptions.forEachIndexed { index, (engine, labelRes) ->
                GroupedOptionRow(
                    label = stringResource(labelRes),
                    selected = colorEngine == engine,
                    shape = groupedShape(index, colorEngineOptions.size),
                    onClick = { onColorEngineChange(engine) }
                )
            }
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun GroupedOptionRow(
    label: String,
    selected: Boolean,
    shape: Shape,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val container = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val content = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        shape = shape,
        color = container,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = content
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ToggleHeroCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                thumbContent = if (checked) {
                    {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    null
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    checkedIconColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
private fun GamesPage(isRootGranted: Boolean) {
    val context = LocalContext.current
    var games by remember { mutableStateOf<List<DetectedGame>>(emptyList()) }
    var isScanning by remember { mutableStateOf(true) }
    var scanToken by remember { mutableIntStateOf(0) }

    val gamingDecor = remember {
        listOf(
            DecorIconSpec(
                icon = Icons.Rounded.SportsEsports,
                size = 64.dp,
                alignment = Alignment.BottomEnd,
                offsetX = (-20).dp,
                offsetY = (-36).dp,
                rotation = -14f,
                alpha = 0.14f,
                usePrimaryTint = true,
                phaseOffset = 1f
            ),
            DecorIconSpec(
                icon = Icons.Rounded.VideogameAsset,
                size = 54.dp,
                alignment = Alignment.BottomStart,
                offsetX = 20.dp,
                offsetY = (-74).dp,
                rotation = 14f,
                alpha = 0.12f,
                usePrimaryTint = false,
                phaseOffset = -1f
            ),
            DecorIconSpec(
                icon = Icons.Rounded.EmojiEvents,
                size = 44.dp,
                alignment = Alignment.BottomCenter,
                offsetX = (-4).dp,
                offsetY = (-16).dp,
                rotation = -8f,
                alpha = 0.11f,
                usePrimaryTint = true,
                phaseOffset = 1f
            ),
            DecorIconSpec(
                icon = Icons.Rounded.Bolt,
                size = 40.dp,
                alignment = Alignment.BottomEnd,
                offsetX = (-96).dp,
                offsetY = (-96).dp,
                rotation = 18f,
                alpha = 0.09f,
                usePrimaryTint = false,
                phaseOffset = -1f
            )
        )
    }

    LaunchedEffect(scanToken, isRootGranted) {
        isScanning = true
        val canScan = isRootGranted || probeRootAccess()
        games = if (canScan) {
            runCatching {
                ConfigRepository.detectInstalledGames(context.packageManager)
            }.getOrElse { emptyList() }
        } else {
            emptyList()
        }
        isScanning = false
    }

    WizardPage(
        titleRes = R.string.setup_title_games,
        descriptionRes = R.string.setup_desc_games,
        decorSpecs = gamingDecor
    ) {
        Surface(
            shape = CardShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
        ) {
            when {
                isScanning -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = stringResource(R.string.setup_games_scanning),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                games.isNotEmpty() -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SportsEsports,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = stringResource(R.string.setup_games_count, games.size),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    games.take(6).forEach { game ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = game.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = game.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    if (games.size > 6) {
                        Text(
                            text = stringResource(R.string.setup_games_more, games.size - 6),
                            modifier = Modifier.padding(start = 20.dp, top = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    TextButton(
                        onClick = { scanToken += 1 },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = stringResource(R.string.setup_games_scan_again))
                    }
                }

                else -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SportsEsports,
                            contentDescription = null,
                            modifier = Modifier.size(30.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = stringResource(R.string.setup_games_empty),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.setup_games_empty_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    TextButton(onClick = { scanToken += 1 }) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = stringResource(R.string.setup_games_scan_again))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusHeroCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badgeColor: Color,
    badgeContentColor: Color,
    trailing: @Composable () -> Unit
) {
    Surface(
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(badgeColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = badgeContentColor
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            trailing()
        }
    }
}

private suspend fun probeRootAccess(): Boolean = withContext(Dispatchers.IO) {
    if (runCatching { RootUtil.checkRootAccess() }.getOrDefault(false)) {
        return@withContext true
    }
    runCatching {
        val process = ProcessBuilder("su", "-c", "id")
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        exitCode == 0 && output.contains("uid=0")
    }.getOrDefault(false)
}

private suspend fun detectRootManagerName(context: Context): String? = withContext(Dispatchers.IO) {
    val fromUtil = runCatching { RootUtil.detectRootProvider() }.getOrNull()
        ?.takeIf { it.isNotBlank() }
    if (fromUtil != null) return@withContext fromUtil

    val knownPackages = listOf(
        "com.rifsxd.ksunext" to "KernelSU Next",
        "me.weishu.kernelsu" to "KernelSU",
        "me.bmax.apatch" to "APatch",
        "com.topjohnwu.magisk" to "Magisk"
    )
    val pm = context.packageManager
    for ((pkg, label) in knownPackages) {
        val installed = runCatching {
            pm.getPackageInfo(pkg, 0)
            true
        }.getOrDefault(false)
        if (installed) return@withContext label
    }

    runCatching {
        val process = ProcessBuilder("su", "-v")
            .redirectErrorStream(true)
            .start()
        val out = process.inputStream.bufferedReader().use { it.readText() }.trim()
        process.waitFor()
        when {
            out.contains("kernelsu", ignoreCase = true) -> "KernelSU"
            out.contains("apatch", ignoreCase = true) -> "APatch"
            out.contains("magisk", ignoreCase = true) -> "Magisk"
            out.isNotBlank() -> out.lineSequence().firstOrNull()?.take(24)
            else -> null
        }
    }.getOrNull()
}

private fun groupedShape(index: Int, count: Int): Shape {
    val outer = 24.dp
    val inner = 4.dp
    return when {
        count <= 1 -> RoundedCornerShape(outer)
        index == 0 -> RoundedCornerShape(
            topStart = outer,
            topEnd = outer,
            bottomStart = inner,
            bottomEnd = inner
        )

        index == count - 1 -> RoundedCornerShape(
            topStart = inner,
            topEnd = inner,
            bottomStart = outer,
            bottomEnd = outer
        )

        else -> RoundedCornerShape(inner)
    }
}
