@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package com.luminatweaks.ui.screen

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.provider.Settings
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.luminatweaks.R
import com.luminatweaks.data.AppProfileConfig
import com.luminatweaks.data.ConfigRepository
import com.luminatweaks.model.AppItem
import com.luminatweaks.ui.component.AospStatusPill
import com.luminatweaks.ui.component.luminaHazeHeader
import com.luminatweaks.ui.component.pressScale
import com.luminatweaks.ui.theme.LocalFloatingNavBar
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class AppsFilter {
    ALL,
    GAMES,
    ACTIVE
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
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val packageManager = remember { context.packageManager }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var rawApps by remember { mutableStateOf(emptyList<AppItem>()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchFocused by remember { mutableStateOf(false) }
    var hideSystemApps by remember { mutableStateOf(true) }
    var selectedFilter by remember { mutableStateOf(AppsFilter.ALL) }
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

    LaunchedEffect(searchQuery, selectedFilter) {
        if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) {
            listState.scrollToItem(0)
        }
    }

    DisposableEffect(Unit) {
        onDispose { focusManager.clearFocus() }
    }

    val activeApp = rawApps.find { it.packageName == selectedPackageName }

    val visibleBaseApps = remember(rawApps, hideSystemApps) {
        rawApps.filter { app -> if (hideSystemApps) !app.isSystemApp else true }
    }

    val filteredApps = remember(visibleBaseApps, searchQuery, selectedFilter, appConfigs.toMap()) {
        val byFilter = when (selectedFilter) {
            AppsFilter.ALL -> visibleBaseApps
            AppsFilter.GAMES -> visibleBaseApps.filter { app ->
                ConfigRepository.defaultGames.contains(app.packageName)
            }
            AppsFilter.ACTIVE -> visibleBaseApps.filter { app ->
                appConfigs[app.packageName]?.let { !it.disabled && it.saklarUtama }
                    ?: ConfigRepository.isAppActive(app.packageName)
            }
        }

        val filtered = if (searchQuery.isBlank()) {
            byFilter
        } else {
            byFilter.filter { app ->
                app.name.contains(searchQuery, ignoreCase = true) ||
                    app.packageName.contains(searchQuery, ignoreCase = true)
            }
        }

        filtered.sortedWith(
            compareByDescending<AppItem> { app ->
                appConfigs[app.packageName]?.let { !it.disabled && it.saklarUtama }
                    ?: ConfigRepository.isAppActive(app.packageName)
            }
                .thenBy { it.name.lowercase() }
        )
    }

    val isFloating = LocalFloatingNavBar.current
    val navBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

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
            BackHandler(enabled = isSearchFocused || searchQuery.isNotEmpty()) {
                if (searchQuery.isNotEmpty()) {
                    searchQuery = ""
                }
                isSearchFocused = false
                focusManager.clearFocus()
                keyboardController?.hide()
            }

            val scheme = MaterialTheme.colorScheme
            val hazeState = remember { HazeState() }
            val pullToRefreshState = rememberPullToRefreshState()

            val titleRowHeight = 52.dp
            val searchPillHeight = 46.dp
            val filterTopGap = 8.dp
            val filterPillHeight = 32.dp
            val filterBottomGap = 8.dp
            val filterSectionHeight = filterTopGap + filterPillHeight + filterBottomGap

            val expandedHeaderBodyHeight = titleRowHeight + 2.dp + searchPillHeight + filterSectionHeight
            val expandedHeaderHeight = statusBarTop + expandedHeaderBodyHeight
            val collapseRangePx = with(density) { titleRowHeight.toPx() }

            var targetCollapsePx by remember { mutableFloatStateOf(0f) }

            val nestedScrollConnection = remember(collapseRangePx) {
                object : NestedScrollConnection {
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        if (isSearchFocused || searchQuery.isNotEmpty()) return Offset.Zero
                        val dy = available.y
                        if (dy < 0f && targetCollapsePx < collapseRangePx) {
                            val consumedY = maxOf(dy, -(collapseRangePx - targetCollapsePx))
                            targetCollapsePx = (targetCollapsePx - consumedY).coerceIn(0f, collapseRangePx)
                            return Offset(0f, consumedY)
                        }
                        if (dy > 0f && targetCollapsePx > 0f) {
                            val consumedY = minOf(dy, targetCollapsePx)
                            targetCollapsePx = (targetCollapsePx - consumedY).coerceIn(0f, collapseRangePx)
                            return Offset(0f, consumedY)
                        }
                        return Offset.Zero
                    }

                    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                        if (!isSearchFocused && searchQuery.isEmpty() &&
                            targetCollapsePx > 0f && targetCollapsePx < collapseRangePx
                        ) {
                            targetCollapsePx = if (targetCollapsePx >= collapseRangePx * 0.45f) {
                                collapseRangePx
                            } else {
                                0f
                            }
                        }
                        return Velocity.Zero
                    }
                }
            }

            LaunchedEffect(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
                isSearchFocused,
                searchQuery
            ) {
                if (listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset == 0 &&
                    !isSearchFocused &&
                    searchQuery.isEmpty()
                ) {
                    targetCollapsePx = 0f
                }
            }

            val animatedScrollCollapsePx by animateFloatAsState(
                targetValue = targetCollapsePx,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = 650f
                ),
                label = "appsScrollCollapse"
            )

            val scrollCollapse = (animatedScrollCollapsePx / collapseRangePx).coerceIn(0f, 1f)

            val focusCollapse by animateFloatAsState(
                targetValue = if (isSearchFocused || searchQuery.isNotEmpty()) 1f else 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = 500f
                ),
                label = "appsSearchFocusCollapse"
            )

            val collapseProgress = maxOf(scrollCollapse, focusCollapse)
            val currentTitleHeight = titleRowHeight * (1f - collapseProgress)
            val searchTopOffset = lerp(
                titleRowHeight + 2.dp,
                (titleRowHeight - searchPillHeight) / 2f,
                collapseProgress
            )
            val searchEndPadding = lerp(16.dp, 60.dp, collapseProgress)
            val filterTopOffset = searchTopOffset + searchPillHeight + filterTopGap
            val currentHeaderBodyHeight = filterTopOffset + filterPillHeight + filterBottomGap
            val currentHeaderHeight = statusBarTop + currentHeaderBodyHeight

            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(scheme.background)
                    .pullToRefresh(
                        state = pullToRefreshState,
                        isRefreshing = isRefreshing,
                        onRefresh = refreshApps
                    )
            ) {
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = expandedHeaderHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = scheme.primary)
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(nestedScrollConnection)
                            .hazeSource(state = hazeState),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = expandedHeaderHeight + 4.dp,
                            bottom = if (isFloating) 112.dp + navBottomInset else 28.dp + navBottomInset
                        ),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (filteredApps.isEmpty() && !isRefreshing) {
                            item(key = "empty_state") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(26.dp))
                                        .background(scheme.surfaceContainerHigh)
                                        .padding(vertical = 40.dp, horizontal = 20.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = appsLocalizedLabel(
                                            "Tidak ada aplikasi yang cocok",
                                            "No matching applications found"
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = scheme.onSurfaceVariant
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
                                    onClick = {
                                        focusManager.clearFocus()
                                        selectedPackageName = appItem.packageName
                                    },
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
                            .padding(top = currentHeaderHeight)
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .luminaHazeHeader(
                            hazeState = hazeState,
                            surfaceColor = scheme.background,
                            overlappedFraction = collapseProgress
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(currentHeaderBodyHeight)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(currentTitleHeight)
                                .clipToBounds()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(titleRowHeight)
                                    .padding(start = 20.dp, end = 60.dp)
                                    .graphicsLayer {
                                        alpha = (1f - collapseProgress * 1.15f).coerceIn(0f, 1f)
                                        translationY = -size.height * 0.65f * collapseProgress
                                        val s = 1f - 0.03f * collapseProgress
                                        scaleX = s
                                        scaleY = s
                                        transformOrigin = TransformOrigin(0f, 0.5f)
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.apps_title),
                                    fontSize = 28.sp,
                                    letterSpacing = (-0.5).sp,
                                    fontWeight = FontWeight.Bold,
                                    color = scheme.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    top = searchTopOffset,
                                    start = 16.dp,
                                    end = searchEndPadding
                                )
                                .height(searchPillHeight)
                                .clip(CircleShape)
                                .background(scheme.surfaceContainerHigh)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    focusRequester.requestFocus()
                                }
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    tint = if (isSearchFocused) scheme.primary else scheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )

                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.apps_search_placeholder),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = scheme.onSurfaceVariant,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    BasicTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                                            color = scheme.onSurface
                                        ),
                                        cursorBrush = SolidColor(scheme.primary),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                        keyboardActions = KeyboardActions(
                                            onSearch = {
                                                focusManager.clearFocus()
                                                keyboardController?.hide()
                                            }
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusRequester(focusRequester)
                                            .onFocusChanged { state ->
                                                isSearchFocused = state.isFocused
                                            }
                                    )
                                }

                                AnimatedVisibility(
                                    visible = searchQuery.isNotEmpty(),
                                    enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.8f),
                                    exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.8f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .clickable {
                                                searchQuery = ""
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Clear,
                                            contentDescription = stringResource(R.string.action_clear),
                                            tint = scheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    top = filterTopOffset,
                                    start = 16.dp,
                                    end = 16.dp
                                )
                                .height(filterPillHeight),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AppsFilterPill(
                                label = appsLocalizedLabel("Semua", "All"),
                                selected = selectedFilter == AppsFilter.ALL,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedFilter = AppsFilter.ALL
                                }
                            )
                            AppsFilterPill(
                                label = appsLocalizedLabel("Game", "Games"),
                                selected = selectedFilter == AppsFilter.GAMES,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedFilter = if (selectedFilter == AppsFilter.GAMES) {
                                        AppsFilter.ALL
                                    } else {
                                        AppsFilter.GAMES
                                    }
                                }
                            )
                            AppsFilterPill(
                                label = appsLocalizedLabel("Aktif", "Active"),
                                selected = selectedFilter == AppsFilter.ACTIVE,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedFilter = if (selectedFilter == AppsFilter.ACTIVE) {
                                        AppsFilter.ALL
                                    } else {
                                        AppsFilter.ACTIVE
                                    }
                                }
                            )
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .height(titleRowHeight)
                                .padding(end = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(onClick = { showMoreMenu = true }) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = stringResource(R.string.action_more),
                                    tint = scheme.onSurfaceVariant
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
                    }
                }
            }
        }
    }
}

@Composable
private fun AppsFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressScaleModifier = pressScale(interactionSource, 0.96f)

    val containerColor by animateColorAsState(
        targetValue = if (selected) scheme.secondaryContainer else scheme.surfaceContainerHigh,
        animationSpec = tween(durationMillis = 180),
        label = "filterPillContainer"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = 180),
        label = "filterPillContent"
    )

    Box(
        modifier = Modifier
            .height(32.dp)
            .then(pressScaleModifier)
            .clip(CircleShape)
            .background(containerColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = contentColor,
            maxLines = 1
        )
    }
}

@Composable
private fun AppIconImage(
    packageName: String,
    contentDescription: String,
    size: Dp = 54.dp
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
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                )
            } else {
                Image(
                    bitmap = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(14.dp))
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
    val haptic = LocalHapticFeedback.current
    var showRowMenu by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressScaleModifier = pressScale(interactionSource, 0.975f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(pressScaleModifier)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.padding(end = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            AppIconImage(
                packageName = app.packageName,
                contentDescription = app.name,
                size = 54.dp
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp)
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
