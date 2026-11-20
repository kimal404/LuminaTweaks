@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.luminatweaks.ui.screen

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Tune
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
import androidx.compose.material3.surfaceColorAtElevation
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
import com.luminatweaks.ui.component.pressScale
import com.luminatweaks.ui.theme.ColorEngine
import com.luminatweaks.ui.theme.ColorMode
import com.luminatweaks.ui.theme.ThemeController
import com.luminatweaks.util.RootUtil
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PAGE_COUNT = 4
private val CardShape = RoundedCornerShape(26.dp)

private enum class CheckState { CHECKING, READY, MISSING }

private data class FloatingBadgeSpec(
    val icon: ImageVector,
    val xFraction: Float,
    val yFraction: Float,
    val boxSize: Dp,
    val rotation: Float,
    val driftAmplitude: Float,
    val tone: Int
)

private val welcomeFloatingSpecs = listOf(
    FloatingBadgeSpec(Icons.Rounded.RocketLaunch, 0.43f, 0.34f, 60.dp, -6f, 7f, 0),
    FloatingBadgeSpec(Icons.Rounded.Speed, 0.14f, 0.12f, 48.dp, -14f, -6f, 1),
    FloatingBadgeSpec(Icons.Rounded.Memory, 0.74f, 0.10f, 50.dp, 12f, 6f, 2),
    FloatingBadgeSpec(Icons.Rounded.Bolt, 0.06f, 0.46f, 44.dp, 10f, 5f, 2),
    FloatingBadgeSpec(Icons.Rounded.DeveloperBoard, 0.80f, 0.44f, 46.dp, -11f, -6f, 1),
    FloatingBadgeSpec(Icons.Rounded.Thermostat, 0.22f, 0.72f, 44.dp, -9f, -5f, 3),
    FloatingBadgeSpec(Icons.Rounded.Tune, 0.66f, 0.74f, 46.dp, 13f, 6f, 0),
    FloatingBadgeSpec(Icons.Rounded.Security, 0.44f, 0.04f, 40.dp, 7f, -5f, 3),
    FloatingBadgeSpec(Icons.Rounded.SportsEsports, 0.44f, 0.78f, 42.dp, -8f, 5f, 1)
)

private val compactFloatingSpecs = listOf(
    FloatingBadgeSpec(Icons.Rounded.Memory, 0.14f, 0.16f, 46.dp, -12f, 6f, 0),
    FloatingBadgeSpec(Icons.Rounded.Speed, 0.45f, 0.32f, 54.dp, 6f, -6f, 1),
    FloatingBadgeSpec(Icons.Rounded.DeveloperBoard, 0.76f, 0.14f, 46.dp, 11f, 5f, 2),
    FloatingBadgeSpec(Icons.Rounded.Bolt, 0.24f, 0.66f, 42.dp, 9f, -5f, 2),
    FloatingBadgeSpec(Icons.Rounded.Tune, 0.68f, 0.64f, 44.dp, -10f, 6f, 3),
    FloatingBadgeSpec(Icons.Rounded.RocketLaunch, 0.46f, 0.74f, 40.dp, -5f, 5f, 0)
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
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 12.dp)
    ) {
        AnimatedContent(
            targetState = page,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            label = "setupFooterNote"
        ) { targetPage ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CardShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(footerNoteForPage(targetPage)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

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
private fun FloatingIconCluster(
    specs: List<FloatingBadgeSpec>,
    areaHeight: Dp,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "wizardFloatingCluster")
    val waveA by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatingWaveA"
    )
    val waveB by transition.animateFloat(
        initialValue = 1f,
        targetValue = -1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatingWaveB"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(areaHeight)
    ) {
        specs.forEachIndexed { index, spec ->
            val containerColor = when (spec.tone) {
                0 -> scheme.primaryContainer
                1 -> scheme.secondaryContainer
                2 -> scheme.tertiaryContainer
                else -> scheme.surfaceContainerHigh
            }
            val iconTint = when (spec.tone) {
                0 -> scheme.onPrimaryContainer
                1 -> scheme.onSecondaryContainer
                2 -> scheme.onTertiaryContainer
                else -> scheme.primary
            }
            val phase = if (index % 2 == 0) waveA else waveB
            val xPos = (maxWidth - spec.boxSize) * spec.xFraction
            val yPos = (maxHeight - spec.boxSize) * spec.yFraction
            val cornerRadius = (spec.boxSize.value * 0.34f).dp
            val iconSize = (spec.boxSize.value * 0.48f).dp

            Box(
                modifier = Modifier
                    .offset(x = xPos, y = yPos)
                    .graphicsLayer {
                        translationY = phase * spec.driftAmplitude.dp.toPx()
                        rotationZ = spec.rotation + (phase * 2.5f)
                    }
                    .size(spec.boxSize)
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(containerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = spec.icon,
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                    tint = iconTint
                )
            }
        }
    }
}

@Composable
private fun WizardPage(
    @StringRes titleRes: Int,
    @StringRes descriptionRes: Int,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 24.dp)
    ) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(descriptionRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Spacer(modifier = Modifier.height(20.dp))
        content()
    }
}

@Composable
private fun WelcomePage(
    isCheckingRoot: Boolean,
    isRootGranted: Boolean,
    rootProvider: String?,
    onRetry: () -> Unit
) {
    WizardPage(
        titleRes = R.string.setup_title_welcome,
        descriptionRes = R.string.setup_desc_welcome
    ) {
        val badgeBg = when {
            isCheckingRoot -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            isRootGranted -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            else -> MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
        }
        val badgeTint = when {
            isCheckingRoot -> MaterialTheme.colorScheme.primary
            isRootGranted -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.error
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
            badgeColor = badgeBg,
            badgeContentColor = badgeTint,
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

        Spacer(modifier = Modifier.height(20.dp))

        FloatingIconCluster(
            specs = welcomeFloatingSpecs,
            areaHeight = 250.dp
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
        descriptionRes = R.string.setup_desc_diagnostics
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

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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

        Spacer(modifier = Modifier.height(16.dp))

        FloatingIconCluster(
            specs = compactFloatingSpecs,
            areaHeight = 165.dp
        )
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
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
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 6.dp)
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = statusText,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = statusColor,
            maxLines = 1
        )
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

        GroupLabel(text = stringResource(R.string.setup_color_mode))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            colorModeOptions.forEachIndexed { index, (mode, labelRes) ->
                GroupedOptionRow(
                    label = stringResource(labelRes),
                    selected = colorMode == mode,
                    shape = groupedShape(index, colorModeOptions.size),
                    onClick = { onColorModeChange(mode) }
                )
            }
        }

        GroupLabel(text = stringResource(R.string.setup_color_engine))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 8.dp)
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
    val scheme = MaterialTheme.colorScheme
    val container = if (selected) {
        scheme.secondaryContainer
    } else {
        scheme.surfaceColorAtElevation(1.dp)
    }
    val content = if (selected) {
        scheme.onSecondaryContainer
    } else {
        scheme.onSurface
    }

    val interactionSource = remember { MutableInteractionSource() }
    val pressScaleModifier = pressScale(interactionSource, 0.97f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(pressScaleModifier)
            .clip(shape)
            .background(container)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
            ),
            color = content
        )
        Spacer(modifier = Modifier.width(12.dp))
        WizardRadioRing(selected = selected)
    }
}

@Composable
private fun WizardRadioRing(selected: Boolean) {
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
private fun ToggleHeroCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressScaleModifier = pressScale(interactionSource, 0.975f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(pressScaleModifier)
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 6.dp)
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

        Spacer(modifier = Modifier.width(12.dp))

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

@Composable
private fun GamesPage(isRootGranted: Boolean) {
    val context = LocalContext.current
    var games by remember { mutableStateOf<List<DetectedGame>>(emptyList()) }
    var isScanning by remember { mutableStateOf(true) }
    var scanToken by remember { mutableIntStateOf(0) }

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
        descriptionRes = R.string.setup_desc_games
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
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
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SportsEsports,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = stringResource(R.string.setup_games_count, games.size),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
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
                                    style = MaterialTheme.typography.bodyLarge,
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
                            .size(56.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SportsEsports,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.primary
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

        if (games.size <= 2) {
            Spacer(modifier = Modifier.height(16.dp))
            FloatingIconCluster(
                specs = compactFloatingSpecs,
                areaHeight = 160.dp
            )
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(badgeColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = badgeContentColor
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp)
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

        Spacer(modifier = Modifier.width(10.dp))

        trailing()
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
    val outer = 26.dp
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
