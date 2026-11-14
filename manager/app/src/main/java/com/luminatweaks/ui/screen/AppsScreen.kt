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

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package com.luminatweaks.ui.screen

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.luminatweaks.ui.theme.LocalCornerRadius
import com.luminatweaks.ui.theme.LocalFloatingNavBar
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private enum class AppListFilter {
    ALL, ACTIVE, GAMES
}

@Composable
private fun appsLocalizedLabel(id: String, en: String): String {
    val isIndonesian = remember {
        val lang = Locale.getDefault().language.lowercase(Locale.ROOT)
        lang == "in" || lang == "id"
    }
    return if (isIndonesian) id else en
}

private suspend fun awaitPagerSettled(pagerState: PagerState?) {
    pagerState ?: return
    snapshotFlow { pagerState.isScrollInProgress }.first { !it }
}

@Composable
fun AppsScreen(
    modifier: Modifier = Modifier,
    pagerState: PagerState? = null
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val packageManager = remember { context.packageManager }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Start empty so a page that is pre-composed during a swipe does not
    // decode dozens of icons until the pager has settled.
    var rawApps by remember { mutableStateOf(emptyList<AppItem>()) }
    var isLoading by remember { mutableStateOf(true) }
    var isScanningGames by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var hideSystemApps by remember { mutableStateOf(true) }
    var selectedFilter by remember { mutableStateOf(AppListFilter.ALL) }
    var showMoreMenu by remember { mutableStateOf(false) }

    val appConfigs = remember { mutableStateMapOf<String, AppProfileConfig>() }
    var selectedPackageName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        awaitPagerSettled(pagerState)
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

    LaunchedEffect(selectedFilter, searchQuery) {
        if (listState.firstVisibleItemIndex > 0) {
            listState.scrollToItem(0)
        }
    }

    val activeApp = rawApps.find { it.packageName == selectedPackageName }

    val visibleBaseApps = remember(rawApps, hideSystemApps) {
        rawApps.filter { app -> if (hideSystemApps) !app.isSystemApp else true }
    }

    val activeCount = remember(visibleBaseApps, appConfigs.toMap()) {
        visibleBaseApps.count { app ->
            val cfg = appConfigs[app.packageName]
            if (cfg != null) (!cfg.disabled && cfg.saklarUtama) else ConfigRepository.isAppActive(app.packageName)
        }
    }

    val gameCount = remember(visibleBaseApps) {
        visibleBaseApps.count { it.isGame }
    }

    val filteredApps = remember(visibleBaseApps, searchQuery, selectedFilter, appConfigs.toMap()) {
        val filtered = visibleBaseApps.filter { app ->
            val isEnabled = appConfigs[app.packageName]?.let { !it.disabled && it.saklarUtama }
                ?: ConfigRepository.isAppActive(app.packageName)

            val matchesFilter = when (selectedFilter) {
                AppListFilter.ALL -> true
                AppListFilter.ACTIVE -> isEnabled
                AppListFilter.GAMES -> app.isGame
            }

            val matchesSearch = if (searchQuery.isBlank()) true else {
                app.name.contains(searchQuery, ignoreCase = true) ||
                    app.packageName.contains(searchQuery, ignoreCase = true)
            }
            matchesFilter && matchesSearch
        }

        filtered.sortedWith(
            compareByDescending<AppItem> { app ->
                appConfigs[app.packageName]?.let { !it.disabled && it.saklarUtama }
                    ?: ConfigRepository.isAppActive(app.packageName)
            }
                .thenByDescending { it.isGame }
                .thenBy { it.name.lowercase() }
        )
    }

    val isFloating = LocalFloatingNavBar.current
    val navBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val outerContainerRadius = LocalCornerRadius.current.coerceAtLeast(28.dp)

    LuminaDrillTransition(
        targetState = activeApp,
        isForward = { _, target -> target != null },
        contentKey = { it?.packageName ?: "apps_list" }
    ) { targetApp ->
        if (targetApp != null) {
            val currentProfile = appConfigs[targetApp.packageName] ?: AppProfileConfig(pkg = targetApp.packageName)
            AppProfileScreen(
                app = targetApp,
                profile = currentProfile,
                onProfileChanged = { updated ->
                    appConfigs[updated.pkg] = updated
                    coroutineScope.launch {
                        ConfigRepository.saveAppConfig(updated)
                    }
                },
                onBack = { selectedPackageName = null }
            )
        } else {
            Scaffold(
                modifier = modifier,
                contentWindowInsets = WindowInsets(0.dp),
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = stringResource(R.string.apps_title),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
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
                    val allLabel = appsLocalizedLabel("Semua", "All")
                    val activeLabel = appsLocalizedLabel("Aktif", "Active")
                    val gamesLabel = appsLocalizedLabel("Game", "Games")

                    val filterChips = listOf(
                        AppListFilter.ALL to "$allLabel (${visibleBaseApps.size})",
                        AppListFilter.ACTIVE to "$activeLabel ($activeCount)",
                        AppListFilter.GAMES to "$gamesLabel ($gameCount)"
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                top = innerPadding.calculateTopPadding() + 4.dp,
                                start = 16.dp,
                                end = 16.dp,
                                bottom = if (isFloating) 12.dp + navBottomInset else 12.dp
                            )
                    ) {
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
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp, bottom = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filterChips, key = { it.first }) { (filter, label) ->
                                val isSelected = selectedFilter == filter
                                val chipBg by animateColorAsState(
                                    targetValue = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    },
                                    label = "filterChipBg"
                                )
                                val chipContent by animateColorAsState(
                                    targetValue = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    label = "filterChipText"
                                )

                                Surface(
                                    onClick = {
                                        if (!isSelected) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            selectedFilter = filter
                                        }
                                    },
                                    shape = CircleShape,
                                    color = chipBg
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = chipContent,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(outerContainerRadius),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(outerContainerRadius))
                        ) {
                            if (filteredApps.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(20.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = appsLocalizedLabel(
                                            "Tidak ada aplikasi yang cocok",
                                            "No matching applications found"
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(
                                        start = 12.dp,
                                        end = 12.dp,
                                        top = 12.dp,
                                        bottom = if (isFloating) 84.dp else 12.dp
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(filteredApps, key = { it.packageName }) { appItem ->
                                        val currentCfg = appConfigs[appItem.packageName]
                                            ?: AppProfileConfig(pkg = appItem.packageName)
                                        val isEnabled = if (appConfigs.containsKey(appItem.packageName)) {
                                            !currentCfg.disabled && currentCfg.saklarUtama
                                        } else {
                                            ConfigRepository.isAppActive(appItem.packageName)
                                        }

                                        AppRowCard(
                                            app = appItem,
                                            isEnabled = isEnabled,
                                            onClick = { selectedPackageName = appItem.packageName },
                                            onToggleActive = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                val updated = if (!isEnabled) {
                                                    currentCfg.copy(
                                                        saklarUtama = true,
                                                        disabled = false,
                                                        custom = currentCfg.custom || !ConfigRepository.defaultGames.contains(appItem.packageName)
                                                    )
                                                } else {
                                                    currentCfg.copy(
                                                        saklarUtama = false,
                                                        disabled = true,
                                                        custom = false
                                                    )
                                                }
                                                appConfigs[appItem.packageName] = updated
                                                coroutineScope.launch {
                                                    ConfigRepository.saveAppConfig(updated)
                                                }
                                            },
                                            onOpenAppInfo = {
                                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                    data = Uri.fromParts("package", appItem.packageName, null)
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                context.startActivity(intent)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppRowCard(
    app: AppItem,
    isEnabled: Boolean,
    onClick: () -> Unit,
    onToggleActive: () -> Unit = {},
    onOpenAppInfo: () -> Unit = {}
) {
    val context = LocalContext.current
    val packageManager = remember { context.packageManager }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "appCardScale"
    )
    val cardRadius = (LocalCornerRadius.current * 0.78f).coerceIn(18.dp, 24.dp)
    var showRowMenu by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(cardRadius),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
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
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isEnabled) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = stringResource(R.string.status_active_upper),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box {
                Surface(
                    onClick = { showRowMenu = true },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = stringResource(R.string.action_more),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = showRowMenu,
                    onDismissRequest = { showRowMenu = false }
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (isEnabled) {
                                    appsLocalizedLabel("Nonaktifkan Profil", "Disable Profile")
                                } else {
                                    appsLocalizedLabel("Aktifkan Profil", "Enable Profile")
                                }
                            )
                        },
                        onClick = {
                            showRowMenu = false
                            onToggleActive()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(text = stringResource(R.string.profile_title))
                        },
                        onClick = {
                            showRowMenu = false
                            onClick()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(text = stringResource(R.string.profile_app_info))
                        },
                        onClick = {
                            showRowMenu = false
                            onOpenAppInfo()
                        }
                    )
                }
            }
        }
    }
}
