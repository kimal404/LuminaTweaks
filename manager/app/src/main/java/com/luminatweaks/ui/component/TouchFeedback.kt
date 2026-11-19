package com.luminatweaks.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object MotionTokens {
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    const val DurationShort4 = 200
    const val DurationMedium2 = 300

    fun scrimEnter() = fadeIn(
        animationSpec = tween(DurationShort4, easing = Emphasized)
    )

    fun scrimExit() = fadeOut(
        animationSpec = tween(DurationShort4, easing = Emphasized)
    )

    fun cardEnterFrom(origin: Offset) = fadeIn(
        animationSpec = tween(DurationShort4, easing = EmphasizedDecelerate)
    ) + scaleIn(
        initialScale = 0.1f,
        animationSpec = tween(DurationMedium2, easing = EmphasizedDecelerate),
        transformOrigin = TransformOrigin(origin.x, origin.y)
    )

    fun cardExitTo(origin: Offset) = fadeOut(
        animationSpec = tween(DurationShort4, easing = EmphasizedAccelerate)
    ) + scaleOut(
        targetScale = 0.1f,
        animationSpec = tween(DurationShort4, easing = EmphasizedAccelerate),
        transformOrigin = TransformOrigin(origin.x, origin.y)
    )
}

@Stable
class DialogOriginState internal constructor() {
    var origin by mutableStateOf(Offset(0.5f, 0.5f))
        internal set

    @Composable
    fun trackedModifier(): Modifier {
        val configuration = LocalConfiguration.current
        val density = LocalDensity.current
        val widthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
        val heightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
        return Modifier.onGloballyPositioned { coordinates ->
            val bounds = coordinates.boundsInWindow()
            if (bounds.width > 0f && bounds.height > 0f) {
                origin = Offset(
                    (bounds.center.x / widthPx).coerceIn(0.05f, 0.95f),
                    (bounds.center.y / heightPx).coerceIn(0.05f, 0.95f)
                )
            }
        }
    }
}

@Composable
fun rememberDialogOrigin(): DialogOriginState = remember { DialogOriginState() }

@Composable
fun rememberPressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.96f,
    enabled: Boolean = true
): Float {
    val pressed by interactionSource.collectIsPressedAsState()
    return animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "luminaPressScale"
    ).value
}

@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.96f,
    enabled: Boolean = true
): Modifier {
    val scale = rememberPressScale(interactionSource, pressedScale, enabled)
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

@Composable
fun Modifier.bouncyClickable(
    enabled: Boolean = true,
    pressedScale: Float = 0.96f,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    return pressScale(interactionSource, pressedScale, enabled)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled
        ) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
}

@Composable
fun Modifier.tapPressBounce(
    enabled: Boolean = true,
    pressedScale: Float = 0.96f
): Modifier {
    if (!enabled) return this
    val scaleAnim = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    return graphicsLayer {
        scaleX = scaleAnim.value
        scaleY = scaleAnim.value
    }.pointerInput(enabled) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            scope.launch {
                scaleAnim.animateTo(
                    targetValue = pressedScale,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }
            waitForUpOrCancellation()
            scope.launch {
                scaleAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }
        }
    }
}

fun sliderTick(value: Float, range: ClosedFloatingPointRange<Float>, steps: Int): Int {
    val span = range.endInclusive - range.start
    if (span <= 0f) return 0
    val divisions = if (steps > 0) steps + 1 else 100
    return ((value - range.start) / span * divisions).toInt()
}

@Composable
fun rememberSliderHaptics(): (Float, ClosedFloatingPointRange<Float>, Int) -> Unit {
    val haptic = LocalHapticFeedback.current
    var lastTick by remember { mutableIntStateOf(Int.MIN_VALUE) }
    return { value, range, steps ->
        val tick = sliderTick(value, range, steps)
        if (tick != lastTick) {
            lastTick = tick
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
}

@Composable
fun TouchOriginDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    origin: Offset = Offset(0.5f, 0.5f),
    minWidth: Dp = 280.dp,
    maxWidth: Dp = 360.dp,
    cornerRadius: Dp = 28.dp,
    contentPadding: Dp = 24.dp,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable ColumnScope.() -> Unit
) {
    var composed by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) {
            composed = true
        } else if (composed) {
            delay(240)
            composed = false
        }
    }
    if (!composed) return

    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    var cardSize by remember { mutableStateOf(IntSize.Zero) }
    val cardOrigin = if (rootSize == IntSize.Zero || cardSize == IntSize.Zero) {
        origin
    } else {
        Offset(
            x = ((origin.x * rootSize.width) - (rootSize.width - cardSize.width) / 2f) / cardSize.width,
            y = ((origin.y * rootSize.height) - (rootSize.height - cardSize.height) / 2f) / cardSize.height
        )
    }
    val shape = RoundedCornerShape(cornerRadius)
    val scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { rootSize = it.size }
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = MotionTokens.scrimEnter(),
                exit = MotionTokens.scrimExit()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(scrimColor)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss
                        )
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                AnimatedVisibility(
                    visible = visible,
                    enter = MotionTokens.cardEnterFrom(cardOrigin),
                    exit = MotionTokens.cardExitTo(cardOrigin)
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(min = minWidth, max = maxWidth)
                            .onGloballyPositioned { cardSize = it.size }
                            .clip(shape)
                            .background(containerColor, shape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {}
                            )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(contentPadding),
                            content = content
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DialogOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    shape: Shape = RoundedCornerShape(20.dp)
) {
    val haptic = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val fill by animateColorAsState(
        targetValue = if (selected) scheme.primaryContainer else Color.Transparent,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "dialogOptionFill"
    )
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val checkScale by animateFloatAsState(
        targetValue = if (appeared && selected) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "dialogOptionCheck"
    )
    Row(
        modifier = modifier
            .pressScale(interactionSource, 0.97f)
            .clip(shape)
            .background(fill, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (selected) scheme.onPrimaryContainer.copy(alpha = 0.12f)
                else scheme.primary.copy(alpha = 0.12f),
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) scheme.onPrimaryContainer else scheme.primary,
                    modifier = Modifier
                        .padding(7.dp)
                        .size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = null,
            tint = scheme.onPrimaryContainer,
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer {
                    scaleX = checkScale
                    scaleY = checkScale
                    alpha = checkScale
                }
        )
    }
}
