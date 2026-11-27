@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.luminatweaks.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

fun luminaRubberBand(over: Float, reach: Float): Float {
    if (over == 0f || reach <= 0f) return 0f
    val d = abs(over)
    return sign(over) * (1f - 1f / (d * 0.55f / reach + 1f)) * reach
}

@Composable
fun OdometerNumber(
    value: String,
    style: TextStyle = MaterialTheme.typography.displaySmall,
    color: Color = MaterialTheme.colorScheme.onSurface,
    digitWidthMultiplier: Float = 0.62f,
    modifier: Modifier = Modifier
) {
    val cleanStyle = remember(style) {
        style.copy(platformStyle = PlatformTextStyle(includeFontPadding = false))
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.animateContentSize(
            animationSpec = spring(dampingRatio = 0.86f, stiffness = 420f)
        )
    ) {
        value.forEach { char ->
            if (char.isDigit()) {
                SingleOdometerDigit(
                    targetDigit = char.digitToInt(),
                    style = cleanStyle,
                    color = color,
                    digitWidthMultiplier = digitWidthMultiplier
                )
            } else {
                Text(
                    text = char.toString(),
                    style = cleanStyle,
                    color = color,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun SingleOdometerDigit(
    targetDigit: Int,
    style: TextStyle,
    color: Color,
    digitWidthMultiplier: Float
) {
    val density = LocalDensity.current
    val resolvedFontSize: TextUnit = if (style.fontSize != TextUnit.Unspecified) {
        style.fontSize
    } else {
        24.sp
    }
    val digitHeightDp: Dp = with(density) { resolvedFontSize.toDp() * 1.18f }
    val digitWidthDp: Dp = with(density) { resolvedFontSize.toDp() * digitWidthMultiplier }
    val digitHeightPx = with(density) { digitHeightDp.toPx() }
    val animatable = remember { Animatable(0f) }

    LaunchedEffect(targetDigit) {
        animatable.animateTo(
            targetValue = targetDigit.toFloat(),
            animationSpec = tween(
                durationMillis = 750,
                easing = FastOutSlowInEasing
            )
        )
    }

    Box(
        modifier = Modifier
            .width(digitWidthDp)
            .height(digitHeightDp)
            .clipToBounds()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.20f to Color.Black,
                        0.80f to Color.Black,
                        1.0f to Color.Transparent
                    ),
                    blendMode = BlendMode.DstIn
                )
            },
        contentAlignment = Alignment.Center
    ) {
        val currentVal = animatable.value
        val baseIndex = currentVal.toInt()
        val fractional = currentVal - baseIndex

        for (i in 0..9) {
            val offsetFromCurrent = i - currentVal
            if (offsetFromCurrent in -1.2f..1.2f) {
                val yOffsetDp = with(density) { (offsetFromCurrent * digitHeightPx).toDp() }
                val alpha = (1f - abs(offsetFromCurrent)).coerceIn(0f, 1f)
                val blurScale = 1f + abs(fractional) * 0.08f

                Text(
                    text = i.toString(),
                    style = style,
                    color = color.copy(alpha = alpha),
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .offset(y = yOffsetDp)
                        .graphicsLayer { scaleY = blurScale }
                )
            }
        }
    }
}

@Composable
fun LuminaMicroTicker(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    speed: Dp = 28.dp,
    gap: Dp = 36.dp,
    alwaysScroll: Boolean = true
) {
    val density = LocalDensity.current
    val speedPx = with(density) { speed.toPx() }
    val gapPx = with(density) { gap.toPx() }
    var viewportWidthPx by remember { mutableIntStateOf(0) }
    var contentWidthPx by remember { mutableIntStateOf(0) }
    val shouldScroll = contentWidthPx > 0 && viewportWidthPx > 0 &&
        (alwaysScroll || contentWidthPx > viewportWidthPx)

    val elapsedNanos = remember(text) { mutableLongStateOf(0L) }
    LaunchedEffect(shouldScroll, text) {
        if (!shouldScroll) {
            elapsedNanos.longValue = 0L
            return@LaunchedEffect
        }
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                val step = (now - last).coerceIn(0L, 50_000_000L)
                elapsedNanos.longValue += step
                last = now
            }
        }
    }

    val cleanStyle = remember(style) {
        style.copy(platformStyle = PlatformTextStyle(includeFontPadding = false))
    }

    Box(
        modifier = modifier
            .onSizeChanged { viewportWidthPx = it.width }
            .clipToBounds()
            .graphicsLayer {
                if (shouldScroll) {
                    compositingStrategy = CompositingStrategy.Offscreen
                }
            }
            .drawWithContent {
                drawContent()
                if (shouldScroll) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            0.0f to Color.Transparent,
                            0.04f to Color.Black,
                            0.92f to Color.Black,
                            1.0f to Color.Transparent
                        ),
                        blendMode = BlendMode.DstIn
                    )
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Layout(
            content = {
                Text(
                    text = text,
                    style = cleanStyle,
                    color = color,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = text,
                    style = cleanStyle,
                    color = color,
                    maxLines = 1,
                    softWrap = false
                )
            }
        ) { measurables, constraints ->
            val loose = constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity)
            val first = measurables[0].measure(loose)
            val second = measurables[1].measure(loose)
            contentWidthPx = first.width
            val layoutWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else first.width

            layout(layoutWidth, first.height) {
                if (!shouldScroll) {
                    first.placeRelative(0, 0)
                } else {
                    val cycle = first.width + gapPx
                    val travelled = ((elapsedNanos.longValue / 1_000_000_000.0) * speedPx) % cycle
                    val x1 = -travelled.toFloat().roundToInt()
                    val x2 = (x1 + cycle).roundToInt()
                    first.placeRelative(x1, 0)
                    if (x2 < layoutWidth) {
                        second.placeRelative(x2, 0)
                    }
                }
            }
        }
    }
}

@Composable
fun <S> LuminaAnchoredSheet(
    visible: Boolean,
    step: S,
    onDismiss: () -> Unit,
    isForward: (initial: S, target: S) -> Boolean = { _, _ -> true },
    content: @Composable ColumnScope.(S) -> Unit
) {
    if (!visible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentWindowInsets = { WindowInsets(0.dp) },
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .animateContentSize(
                    animationSpec = spring(dampingRatio = 0.86f, stiffness = 380f)
                )
        ) {
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val forward = isForward(initialState, targetState)
                    if (forward) {
                        (slideInHorizontally(tween(240, easing = FastOutSlowInEasing)) { it / 3 } +
                            fadeIn(tween(200))) togetherWith
                            (slideOutHorizontally(tween(200, easing = FastOutSlowInEasing)) { -it / 3 } +
                                fadeOut(tween(150)))
                    } else {
                        (slideInHorizontally(tween(240, easing = FastOutSlowInEasing)) { -it / 3 } +
                            fadeIn(tween(200))) togetherWith
                            (slideOutHorizontally(tween(200, easing = FastOutSlowInEasing)) { it / 3 } +
                                fadeOut(tween(150)))
                    }
                },
                label = "anchoredSheetStep"
            ) { currentStep ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    content = { content(currentStep) }
                )
            }
        }
    }
}
