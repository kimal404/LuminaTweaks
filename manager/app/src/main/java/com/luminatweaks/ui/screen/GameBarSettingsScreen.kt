package com.luminatweaks.ui.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.luminatweaks.R
import com.luminatweaks.gamebar.ZXSidePanelService
import com.luminatweaks.ui.component.LuminaBackButton

private const val PREFS = "settings"
private const val KEY_ENABLED = "gamespace_sidepanel_enabled"
private const val KEY_SIDE = "gamespace_sidepanel_side"
private const val KEY_SENSITIVITY = "gamespace_sidepanel_sensitivity"
private const val KEY_LENGTH = "gamespace_sidepanel_length"
private const val KEY_THICKNESS = "gamespace_sidepanel_thickness"
private const val KEY_OPACITY = "gamespace_sidepanel_opacity"
private const val KEY_BLUR = "gamespace_sidepanel_blur"
private const val KEY_STYLE = "gamespace_sidepanel_style"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameBarSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    var enabled by remember { mutableStateOf(prefs.getBoolean(KEY_ENABLED, false)) }
    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var side by remember { mutableStateOf(prefs.getString(KEY_SIDE, "left") ?: "left") }
    var sensitivity by remember { mutableStateOf(prefs.getInt(KEY_SENSITIVITY, 65)) }
    var length by remember { mutableStateOf(prefs.getInt(KEY_LENGTH, 50)) }
    var thickness by remember { mutableStateOf(prefs.getInt(KEY_THICKNESS, 5)) }
    var opacity by remember { mutableStateOf(prefs.getInt(KEY_OPACITY, 96)) }
    var blur by remember { mutableStateOf(prefs.getInt(KEY_BLUR, 24)) }
    var style by remember { mutableStateOf(prefs.getInt(KEY_STYLE, 0)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayGranted = Settings.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        if (value) {
            if (Settings.canDrawOverlays(context)) {
                ZXSidePanelService.show(context)
            }
        } else {
            ZXSidePanelService.quit(context)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    LuminaBackButton(
                        onBack = onBack,
                        contentDescription = stringResource(R.string.action_back)
                    )
                },
                title = {
                    Text(
                        text = stringResource(R.string.gamebar_title),
                        fontWeight = FontWeight.SemiBold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.gamebar_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                SectionCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.gamebar_enable),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = stringResource(R.string.gamebar_enable_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = { setEnabled(it) }
                        )
                    }
                }
            }

            item {
                SectionCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.gamebar_overlay_permission),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = if (overlayGranted) {
                                    stringResource(R.string.gamebar_permission_granted)
                                } else {
                                    stringResource(R.string.gamebar_permission_required)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (overlayGranted) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
                            )
                        }
                        if (!overlayGranted) {
                            TextButton(onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                    )
                                }
                            }) {
                                Text(stringResource(R.string.gamebar_grant))
                            }
                        }
                    }
                }
            }

            item {
                SectionCard {
                    Text(
                        text = stringResource(R.string.gamebar_side),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoiceChip(
                            label = stringResource(R.string.gamebar_side_left),
                            selected = side == "left",
                            onClick = {
                                side = "left"
                                prefs.edit().putString(KEY_SIDE, "left").apply()
                            }
                        )
                        ChoiceChip(
                            label = stringResource(R.string.gamebar_side_right),
                            selected = side == "right",
                            onClick = {
                                side = "right"
                                prefs.edit().putString(KEY_SIDE, "right").apply()
                            }
                        )
                    }
                }
            }

            item {
                SectionCard {
                    Text(
                        text = stringResource(R.string.gamebar_style),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoiceChip(
                            label = stringResource(R.string.gamebar_style_solid),
                            selected = style == 0,
                            onClick = {
                                style = 0
                                prefs.edit().putInt(KEY_STYLE, 0).apply()
                            }
                        )
                        ChoiceChip(
                            label = stringResource(R.string.gamebar_style_image),
                            selected = style == 1,
                            onClick = {
                                style = 1
                                prefs.edit().putInt(KEY_STYLE, 1).apply()
                            }
                        )
                    }
                }
            }

            item {
                SliderCard(
                    title = stringResource(R.string.gamebar_sensitivity),
                    valueText = "$sensitivity%",
                    value = sensitivity.toFloat(),
                    range = 20f..100f,
                    onValueChange = { sensitivity = it.toInt() },
                    onValueChangeFinished = {
                        prefs.edit().putInt(KEY_SENSITIVITY, sensitivity).apply()
                    }
                )
            }

            item {
                SliderCard(
                    title = stringResource(R.string.gamebar_length),
                    valueText = "$length%",
                    value = length.toFloat(),
                    range = 20f..100f,
                    onValueChange = { length = it.toInt() },
                    onValueChangeFinished = {
                        prefs.edit().putInt(KEY_LENGTH, length).apply()
                    }
                )
            }

            item {
                SliderCard(
                    title = stringResource(R.string.gamebar_thickness),
                    valueText = "$thickness dp",
                    value = thickness.toFloat(),
                    range = 3f..10f,
                    steps = 6,
                    onValueChange = { thickness = it.toInt() },
                    onValueChangeFinished = {
                        prefs.edit().putInt(KEY_THICKNESS, thickness).apply()
                    }
                )
            }

            item {
                SliderCard(
                    title = stringResource(R.string.gamebar_opacity),
                    valueText = "$opacity%",
                    value = opacity.toFloat(),
                    range = 55f..100f,
                    onValueChange = { opacity = it.toInt() },
                    onValueChangeFinished = {
                        prefs.edit().putInt(KEY_OPACITY, opacity).apply()
                    }
                )
            }

            item {
                SliderCard(
                    title = stringResource(R.string.gamebar_blur),
                    valueText = "$blur",
                    value = blur.toFloat(),
                    range = 0f..60f,
                    onValueChange = { blur = it.toInt() },
                    onValueChangeFinished = {
                        prefs.edit().putInt(KEY_BLUR, blur).apply()
                    }
                )
            }

            item {
                SectionCard {
                    Text(
                        text = stringResource(R.string.gamebar_status_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.gamebar_status_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

@Composable
private fun SliderCard(
    title: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = range,
            steps = steps
        )
    }
}

@Composable
private fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
        )
    }
}
