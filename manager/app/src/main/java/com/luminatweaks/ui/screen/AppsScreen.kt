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

package com.luminatweaks.ui.screen

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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.luminatweaks.R
import com.luminatweaks.data.AppProfileConfig
import com.luminatweaks.data.ConfigRepository
import com.luminatweaks.model.AppItem
import com.luminatweaks.ui.theme.LocalFloatingNavBar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val packageManager = remember { context.packageManager }
    val coroutineScope = rememberCoroutineScope()

    var rawApps by remember { mutableStateOf(ConfigRepository.cachedApps ?: emptyList()) }
    var isLoading by remember { mutableStateOf(ConfigRepository.cachedApps == null) }
    var isScanningGames by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var hideSystemApps by remember { mutableStateOf(true) }
    var showMoreMenu by remember { mutableStateOf(false) }

    val appConfigs = remember { mutableStateMapOf<String, AppProfileConfig>() }
    var selectedPackageName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val loadedConfigs = ConfigRepository.getAppConfigs()
        appConfigs.clear()
        appConfigs.putAll(loadedConfigs)

        val apps = ConfigRepository.cachedApps ?: ConfigRepository.loadInstalledApps(packageManager)
        rawApps = apps
        isLoading = false

        isScanningGames = true
        try {
            val syncedConfigs = ConfigRepository.syncInstalledGameWhitelist(apps.map { it.packageName })
            appConfigs.clear()
            appConfigs.putAll(syncedConfigs)
        } finally {
            isScanningGames = false
        }
    }

    val activeApp = rawApps.find { it.packageName == selectedPackageName }
    if (activeApp != null) {
        val currentProfile = appConfigs[activeApp.packageName] ?: AppProfileConfig(pkg = activeApp.packageName)
        AppProfileScreen(
            app = activeApp,
            profile = currentProfile,
            onProfileChanged = { updated ->
                appConfigs[updated.pkg] = updated
                coroutineScope.launch {
                    ConfigRepository.saveAppConfig(updated)
                }
            },
            onBack = { selectedPackageName = null }
        )
        return
    }

    val filteredApps = remember(rawApps, searchQuery, hideSystemApps, appConfigs.toMap()) {
        val filtered = rawApps.filter { app ->
            val matchesSystem = if (hideSystemApps) !app.isSystemApp else true
            val matchesSearch = if (searchQuery.isBlank()) true else {
                app.name.contains(searchQuery, ignoreCase = true) ||
                        app.packageName.contains(searchQuery, ignoreCase = true)
            }
            matchesSystem && matchesSearch
        }

        filtered.sortedWith(
            compareByDescending<AppItem> {
                ConfigRepository.isAppActive(it.packageName)
            }
                .thenByDescending { it.isGame }
                .thenBy { it.name.lowercase() }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.apps_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (!isScanningGames) {
                                coroutineScope.launch {
                                    isScanningGames = true
                                    try {
                                        val syncedConfigs = ConfigRepository.syncInstalledGameWhitelist(
                                            rawApps.map { it.packageName }
                                        )
                                        appConfigs.clear()
                                        appConfigs.putAll(syncedConfigs)
                                    } finally {
                                        isScanningGames = false
                                    }
                                }
                            }
                        },
                        enabled = !isScanningGames
                    ) {
                        if (isScanningGames) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = stringResource(R.string.apps_scan_games),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = stringResource(R.string.action_more),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (hideSystemApps) {
                                            stringResource(R.string.apps_show_system)
                                        } else {
                                            stringResource(R.string.apps_hide_system)
                                        }
                                    )
                                },
                                onClick = {
                                    hideSystemApps = !hideSystemApps
                                    showMoreMenu = false
                                }
                            )
                        }
                    }
                },
                windowInsets = WindowInsets.statusBars,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 4.dp,
                    bottom = if (LocalFloatingNavBar.current) 100.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() else 16.dp,
                    start = 16.dp,
                    end = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                item {
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.apps_search_placeholder),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Clear,
                                        contentDescription = stringResource(R.string.action_clear),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = CircleShape,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    )
                }

                itemsIndexed(filteredApps, key = { _, item -> item.packageName }) { index, appItem ->
                    val position = when {
                        filteredApps.size == 1 -> ContainerPosition.Single
                        index == 0 -> ContainerPosition.Top
                        index == filteredApps.lastIndex -> ContainerPosition.Bottom
                        else -> ContainerPosition.Middle
                    }

                    val isEnabled = ConfigRepository.isAppActive(appItem.packageName)

                    AppRowCard(
                        app = appItem,
                        isEnabled = isEnabled,
                        position = position,
                        onClick = { selectedPackageName = appItem.packageName }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
fun AppRowCard(
    app: AppItem,
    isEnabled: Boolean,
    position: ContainerPosition,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val packageManager = remember { context.packageManager }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = getConnectedContainerShape(position, outerRadius = 18.dp, innerRadius = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
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
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (app.isGame) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.apps_badge_recommended),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            val badgeContainer = if (isEnabled) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }

            val badgeContent = if (isEnabled) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
            }

            Box(
                modifier = Modifier
                    .background(
                        color = badgeContainer,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isEnabled) stringResource(R.string.status_active_upper) else stringResource(R.string.status_inactive_upper),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = badgeContent
                )
            }
        }
    }
}
