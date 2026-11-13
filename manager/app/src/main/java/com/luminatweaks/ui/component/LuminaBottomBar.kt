/*
 * Copyright (C) 2026-2027 KowX
 * Copyright (C) 2026-2027 Zexshia
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

package com.luminatweaks.ui.component

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.ShortNavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.luminatweaks.R
import com.luminatweaks.ui.theme.LocalCornerRadius
import com.luminatweaks.ui.theme.LocalFloatingNavBar

@Composable
fun LuminaBottomBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    val isFloating = LocalFloatingNavBar.current
    val cornerRadius = LocalCornerRadius.current

    if (isFloating) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
                .navigationBarsPadding(),
            shape = RoundedCornerShape(cornerRadius),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            shadowElevation = 4.dp
        ) {
            ShortNavigationBar(
                containerColor = Color.Transparent,
                windowInsets = WindowInsets(0.dp)
            ) {
                LuminaBottomBarItems(selectedTab = selectedTab, onTabSelected = onTabSelected)
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
