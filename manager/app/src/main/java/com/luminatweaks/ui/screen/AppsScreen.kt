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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.luminatweaks.data.AppProfileConfig
import com.luminatweaks.data.ConfigRepository
import com.luminatweaks.model.AppItem
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
    var searchQuery by remember { mutableStateOf("") }
    var hideSystemApps by remember { mutableStateOf(true) }
    var showMoreMenu by remember { mutableStateOf(false) }

    val appConfigs = remember { mutableStateMapOf<String, AppProfileConfig>() }
    var selectedPackageName by remember { mutableStateOf<String?>(null) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    LaunchedEffect(Unit) {
        val loadedConfigs = ConfigRepository.getAppConfigs()
        appConfigs.clear()
        appConfigs.putAll(loadedConfigs)

        if (ConfigRepository.cachedApps == null) {
            val apps = ConfigRepository.loadInstalledApps(packageManager)
            rawApps = apps
            isLoading = false
        } else {
            rawApps = ConfigRepository.cachedApps!!
            isLoading = false
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
            compareByDescending<AppItem> { appConfigs[it.packageName]?.saklarUtama == true }
                .thenByDescending { it.isGame }
                .thenBy { it.name.lowercase() }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(
                        text = "Applications",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "More",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(if (hideSystemApps) "Show system apps" else "Hide system apps")
                                },
                                onClick = {
                                    hideSystemApps = !hideSystemApps
                                    showMoreMenu = false
                                }
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                windowInsets = WindowInsets.statusBars,
                colors = TopAppBarDefaults.mediumTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
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
                    top = innerPadding.calculateTopPadding(),
                    bottom = 16.dp,
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
                                text = "Search apps or packages...",
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
                                        contentDescription = "Clear",
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

                    val profile = appConfigs[appItem.packageName]
                    val isEnabled = profile?.saklarUtama ?: false

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
                        text = "Rekomendasi",
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
                    text = if (isEnabled) "AKTIF" else "NONAKTIF",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = badgeContent
                )
            }
        }
    }
}
