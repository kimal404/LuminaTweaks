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

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.luminatweaks.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

fun <T> Modifier.trackSubpageBounds(
    key: T,
    onBoundsUpdated: (T, Rect) -> Unit
): Modifier = this.onGloballyPositioned { coordinates ->
    if (coordinates.isAttached) {
        val rect = coordinates.boundsInRoot()
        if (rect.width > 0f && rect.height > 0f) {
            onBoundsUpdated(key, rect)
        }
    }
}

fun dampOverscrollPull(pullPx: Float, maxReachPx: Float): Float {
    if (pullPx <= 0f || maxReachPx <= 0f) return 0f
    val normalized = (pullPx / (maxReachPx * 3.2f)).coerceIn(0f, 1f)
    return maxReachPx * FastOutSlowInEasing.transform(normalized)
}

@Composable
fun OdometerNumber(
    value: String,
    style: TextStyle = MaterialTheme.typography.displaySmall,
    color: Color = MaterialTheme.colorScheme.onSurface,
    digitWidthMultiplier: Float = 0.64f,
    spinOnEnter: Boolean = true,
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
        var digitColumnIndex = 0
        value.forEach { char ->
            if (char.isDigit()) {
                val colIndex = digitColumnIndex++
                SingleOdometerDigit(
                    targetDigit = char.digitToInt(),
                    columnIndex = colIndex,
                    spinOnEnter = spinOnEnter,
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
    columnIndex: Int,
    spinOnEnter: Boolean,
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
    val digitWidthDp: Dp = with(density) { resolvedFontSize.toDp() * digitWidthMultiplier }

    val spinSteps = 4
    var hasSpun by remember { mutableStateOf(!spinOnEnter) }
    var displayedDigit by remember {
        mutableIntStateOf(
            if (spinOnEnter) (targetDigit + 10 - spinSteps) % 10 else targetDigit
        )
    }

    LaunchedEffect(targetDigit) {
        if (!hasSpun) {
            delay((columnIndex * 32L).coerceAtMost(160L))
            for (step in (spinSteps - 1) downTo 0) {
                displayedDigit = (targetDigit + 10 - step) % 10
                if (step > 0) delay(48L)
            }
            hasSpun = true
        } else {
            displayedDigit = targetDigit
        }
    }

    AnimatedContent(
        targetState = displayedDigit,
        transitionSpec = {
            val rollingUp = !hasSpun || targetState >= initialState
            val enter = slideInVertically(
                animationSpec = spring(dampingRatio = 0.86f, stiffness = 480f)
            ) { height -> if (rollingUp) height else -height } + fadeIn(tween(150))
            val exit = slideOutVertically(
                animationSpec = spring(dampingRatio = 0.86f, stiffness = 480f)
            ) { height -> if (rollingUp) -height else height } + fadeOut(tween(120))
            enter togetherWith exit
        },
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(digitWidthDp)
            .clipToBounds(),
        label = "luminaDigitRoller"
    ) { digit ->
        Text(
            text = digit.toString(),
            style = style,
            color = color,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
fun LuminaMicroTicker(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    speed: Dp = 28.dp
) {
    val cleanStyle = remember(style) {
        style.copy(platformStyle = PlatformTextStyle(includeFontPadding = false))
    }

    Text(
        text = text,
        style = cleanStyle,
        color = color,
        maxLines = 1,
        softWrap = false,
        modifier = modifier.basicMarquee(
            iterations = Int.MAX_VALUE,
            velocity = speed
        )
    )
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
