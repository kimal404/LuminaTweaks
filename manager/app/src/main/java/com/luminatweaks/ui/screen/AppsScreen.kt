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

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package com.luminatweaks.ui.screen

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.provider.Settings
import android.util.LruCache
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.luminatweaks.ui.component.luminaHazeHeader
import com.luminatweaks.R
import com.luminatweaks.data.AppProfileConfig
import com.luminatweaks.data.ConfigRepository
import com.luminatweaks.model.AppItem
import com.luminatweaks.ui.component.AospStatusPill
import com.luminatweaks.ui.component.pressScale
import com.luminatweaks.ui.theme.LocalFloatingNavBar
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class AppListFilter {
    ALL, ACTIVE, GAMES
}

private object LuminaAppIconCache {
    private val cache = LruCache<String, ImageBitmap>(150)

    fun get(packageName: String): ImageBitmap? = synchronized(cache) { cache.get(packageName) }

    fun clear() = synchronized(cache) { cache.evictAll() }

    suspend fun loadIcon(pm: PackageManager, packageName: String, targetSizePx: Int): ImageBitmap =
        withContext(Dispatchers.IO) {
            get(packageName)?.let { return@withContext it }

            val drawable = runCatching {
                pm.getApplicationIcon(packageName)
            }.getOrElse {
                pm.defaultActivityIcon
            }

            val bitmap = Bitmap.createBitmap(targetSizePx, targetSizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, targetSizePx, targetSizePx)
            drawable.draw(canvas)

            val imageBitmap = bitmap.asImageBitmap()
            synchronized(cache) {
                cache.put(packageName, imageBitmap)
            }
            imageBitmap
        }
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

    var rawApps by remember { mutableStateOf(emptyList<AppItem>()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var hideSystemApps by remember { mutableStateOf(true) }
    var selectedFilter by remember { mutableStateOf(AppListFilter.GAMES) }
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

        val syncedConfigs = ConfigRepository.syncInstalledGameWhitelist(apps.map { it.packageName })
        appConfigs.clear()
        appConfigs.putAll(syncedConfigs)
    }

    val refreshApps: () -> Unit = {
        if (!isRefreshing) {
            coroutineScope.launch {
                isRefreshing = true
                try {
                    LuminaAppIconCache.clear()
                    val refreshedApps = ConfigRepository.loadInstalledApps(packageManager)
                    rawApps = refreshedApps
                    val syncedConfigs = ConfigRepository.syncInstalledGameWhitelist(
                        refreshedApps.map { it.packageName }
                    )
                    appConfigs.clear()
                    appConfigs.putAll(syncedConfigs)
                    delay(350L)
                } finally {
                    isRefreshing = false
                }
            }
        }
    }

    LaunchedEffect(selectedFilter, searchQuery) {
        if (listState.firstVisibleItemIndex > 0) {
            listState.scrollToItem(0)
        }
    }

    val activeApp = rawApps.find { it.packageName == selectedPackageName }
    val screenTitle = if (selectedFilter == AppListFilter.GAMES) {
        R.string.apps_title_games
    } else {
        R.string.apps_title
    }

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
            val topAppBarState = rememberSaveable(saver = TopAppBarState.Saver) {
                TopAppBarState(
                    initialHeightOffsetLimit = -Float.MAX_VALUE,
                    initialHeightOffset = 0f,
                    initialContentOffset = 0f
                )
            }
            val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)
            val fadeSurface = MaterialTheme.colorScheme.background
            val hazeState = remember { HazeState() }
            val pullToRefreshState = rememberPullToRefreshState()

            Scaffold(
                contentWindowInsets = WindowInsets(0.dp),
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    Box(
                        modifier = Modifier
                            .luminaHazeHeader(
                                hazeState = hazeState,
                                surfaceColor = fadeSurface,
                                overlappedFraction = scrollBehavior.state.overlappedFraction
                            )
                    ) {
                        LargeFlexibleTopAppBar(
                            title = {
                                Text(
                                    text = stringResource(screenTitle),
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            actions = {
                                Box {
                                    IconButton(onClick = { showMoreMenu = true }) {
                                        Icon(
                                            imageVector = Icons.Rounded.MoreVert,
                                            contentDescription = stringResource(R.string.action_more),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
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

                    Box(
                        modifier = modifier
                            .fillMaxSize()
                            .pullToRefresh(
                                state = pullToRefreshState,
                                isRefreshing = isRefreshing,
                                onRefresh = refreshApps
                            )
                            .nestedScroll(scrollBehavior.nestedScrollConnection)
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().hazeSource(state = hazeState),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = innerPadding.calculateTopPadding() + 8.dp,
                                bottom = if (isFloating) 112.dp + navBottomInset else 28.dp + navBottomInset
                            ),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            item(key = "search_and_filters") {
                                Column(modifier = Modifier.padding(bottom = 12.dp)) {
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
                                            focusedContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                                            focusedIndicatorColor = Color.Transparent,
                                            unfocusedIndicatorColor = Color.Transparent,
                                            disabledIndicatorColor = Color.Transparent
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    LazyRow(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(filterChips, key = { it.first }) { (filter, label) ->
                                            val isSelected = selectedFilter == filter
                                            val chipBg by animateColorAsState(
                                                targetValue = if (isSelected) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                                                },
                                                label = "filterChipBg"
                                            )
                                            val chipContent by animateColorAsState(
                                                targetValue = if (isSelected) {
                                                    MaterialTheme.colorScheme.onPrimary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                                label = "filterChipText"
                                            )
                                            val chipSource = remember { MutableInteractionSource() }
                                            val chipPress = pressScale(chipSource, 0.92f)

                                            Surface(
                                                onClick = {
                                                    if (!isSelected) {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        selectedFilter = filter
                                                    }
                                                },
                                                interactionSource = chipSource,
                                                shape = CircleShape,
                                                color = chipBg,
                                                modifier = chipPress
                                            ) {
                                                Text(
                                                    text = label,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = chipContent,
                                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (filteredApps.isEmpty() && !isRefreshing) {
                                item(key = "empty_state") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(26.dp))
                                            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                                            .padding(vertical = 40.dp, horizontal = 20.dp),
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
                                }
                            } else {
                                itemsIndexed(filteredApps, key = { _, appItem -> appItem.packageName }) { index, appItem ->
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
                                        modifier = Modifier.animateItem(
                                            fadeInSpec = null,
                                            placementSpec = spring(
                                                dampingRatio = Spring.DampingRatioLowBouncy,
                                                stiffness = Spring.StiffnessLow
                                            ),
                                            fadeOutSpec = null
                                        ),
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
                                        },
                                        shape = connectedContainerShape(
                                            index = index,
                                            count = filteredApps.size
                                        )
                                    )
                                }
                            }
                        }

                        PullToRefreshDefaults.LoadingIndicator(
                            state = pullToRefreshState,
                            isRefreshing = isRefreshing,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = innerPadding.calculateTopPadding())
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppIconImage(
    packageName: String,
    contentDescription: String,
    size: Dp = 60.dp
) {
    val context = LocalContext.current
    val pm = remember(context) { context.packageManager }
    val density = LocalDensity.current
    val targetSizePx = remember(size, density) {
        with(density) { size.roundToPx() }
    }

    var appBitmap by remember(packageName) {
        mutableStateOf(LuminaAppIconCache.get(packageName))
    }

    LaunchedEffect(packageName, targetSizePx) {
        if (appBitmap == null) {
            appBitmap = runCatching {
                LuminaAppIconCache.loadIcon(pm, packageName, targetSizePx)
            }.getOrNull()
        }
    }

    Box(modifier = Modifier.size(size)) {
        Crossfade(
            targetState = appBitmap,
            animationSpec = tween(durationMillis = 200),
            label = "appIconFade"
        ) { icon ->
            if (icon == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                )
            } else {
                Image(
                    bitmap = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                )
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
    onOpenAppInfo: () -> Unit = {},
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp)
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var showRowMenu by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressScaleModifier = pressScale(interactionSource, 0.975f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(pressScaleModifier)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.padding(end = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            AppIconImage(
                packageName = app.packageName,
                contentDescription = app.name,
                size = 60.dp
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp)
        ) {
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

        AnimatedVisibility(visible = isEnabled) {
            AospStatusPill(
                text = appsLocalizedLabel("Aktif", "Active"),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        Box {
            IconButton(onClick = { showRowMenu = true }) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.action_more),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
