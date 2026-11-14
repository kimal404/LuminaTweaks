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

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.luminatweaks.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.ShortNavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.luminatweaks.R
import com.luminatweaks.ui.theme.LocalFloatingNavBar
import kotlin.math.roundToInt

private data class NavTab(
    val icon: ImageVector,
    val labelRes: Int
)

private val navTabs = listOf(
    NavTab(Icons.Rounded.Home, R.string.nav_home),
    NavTab(Icons.Rounded.Apps, R.string.nav_apps),
    NavTab(Icons.Rounded.Tune, R.string.nav_tweaks),
    NavTab(Icons.Rounded.Settings, R.string.nav_settings)
)

@Composable
fun LuminaBottomBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    val isFloating = LocalFloatingNavBar.current

    if (isFloating) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.wrapContentWidth(),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 4.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(7.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    navTabs.forEachIndexed { index, tab ->
                        FloatingNavItem(
                            tab = tab,
                            selected = selectedTab == index,
                            onClick = { onTabSelected(index) }
                        )
                    }
                }
            }
        }
    } else {
        ShortNavigationBar(
            containerColor = MaterialTheme.colorScheme.background
        ) {
            LuminaBottomBarItems(selectedTab = selectedTab, onTabSelected = onTabSelected)
        }
    }
}

@Composable
private fun FloatingNavItem(
    tab: NavTab,
    selected: Boolean,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val motionScheme = MaterialTheme.motionScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    // Single progress value drives the width, padding, and label opacity so the
    // morph stays in lockstep and never clips words through a visibility
    // animation.
    val expandProgress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 420f),
        label = "floatingNavExpand"
    )
    val clampedProgress = expandProgress.coerceIn(0f, 1f)
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = motionScheme.defaultSpatialSpec(),
        label = "floatingNavPressScale"
    )
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "floatingNavContainer"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "floatingNavContent"
    )
    // 26.dp icon + 2 * 14.dp = an exact 54.dp circle when idle.
    val horizontalPadding = 14.dp + 7.dp * clampedProgress
    val label = stringResource(tab.labelRes)

    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        interactionSource = interactionSource,
        modifier = Modifier.graphicsLayer {
            scaleX = pressScale
            scaleY = pressScale
        }
    ) {
        Row(
            modifier = Modifier
                .height(54.dp)
                .padding(horizontal = horizontalPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = label,
                modifier = Modifier.size(26.dp),
                tint = contentColor
            )
            CollapsibleLabel(progress = clampedProgress) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible
                    )
                }
            }
        }
    }
}

@Composable
private fun CollapsibleLabel(
    progress: Float,
    content: @Composable () -> Unit
) {
    val alpha = ((progress - 0.25f) / 0.75f).coerceIn(0f, 1f)
    Layout(
        content = content,
        modifier = Modifier
            .clipToBounds()
            .graphicsLayer { this.alpha = alpha }
    ) { measurables, _ ->
        val placeable = measurables.firstOrNull()?.measure(Constraints())
        if (placeable == null) {
            layout(0, 0) {}
        } else {
            val width = (placeable.width * progress.coerceIn(0f, 1f)).roundToInt()
            layout(width, placeable.height) {
                placeable.place(0, 0)
            }
        }
    }
}

@Composable
private fun LuminaBottomBarItems(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    ShortNavigationBarItem(
        selected = selectedTab == 0,
        onClick = { onTabSelected(0) },
        icon = {
            Icon(
                imageVector = Icons.Rounded.Home,
                contentDescription = stringResource(R.string.nav_home)
            )
        },
        label = { Text(stringResource(R.string.nav_home)) },
        colors = ShortNavigationBarItemDefaults.colors(
            selectedIndicatorColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
            selectedTextColorTopIconPosition = MaterialTheme.colorScheme.onSurface,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )

    ShortNavigationBarItem(
        selected = selectedTab == 1,
        onClick = { onTabSelected(1) },
        icon = {
            Icon(
                imageVector = Icons.Rounded.Apps,
                contentDescription = stringResource(R.string.nav_apps)
            )
        },
        label = { Text(stringResource(R.string.nav_apps)) },
        colors = ShortNavigationBarItemDefaults.colors(
            selectedIndicatorColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
            selectedTextColorTopIconPosition = MaterialTheme.colorScheme.onSurface,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )

    ShortNavigationBarItem(
        selected = selectedTab == 2,
        onClick = { onTabSelected(2) },
        icon = {
            Icon(
                imageVector = Icons.Rounded.Tune,
                contentDescription = stringResource(R.string.nav_tweaks)
            )
        },
        label = { Text(stringResource(R.string.nav_tweaks)) },
        colors = ShortNavigationBarItemDefaults.colors(
            selectedIndicatorColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
            selectedTextColorTopIconPosition = MaterialTheme.colorScheme.onSurface,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )

    ShortNavigationBarItem(
        selected = selectedTab == 3,
        onClick = { onTabSelected(3) },
        icon = {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = stringResource(R.string.nav_settings)
            )
        },
        label = { Text(stringResource(R.string.nav_settings)) },
        colors = ShortNavigationBarItemDefaults.colors(
            selectedIndicatorColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
            selectedTextColorTopIconPosition = MaterialTheme.colorScheme.onSurface,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}
