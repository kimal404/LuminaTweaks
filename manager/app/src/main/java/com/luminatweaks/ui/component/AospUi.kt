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

package com.luminatweaks.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luminatweaks.R

val AospItemTitleSize: TextUnit = 17.sp
val AospItemTitleLineHeight: TextUnit = 22.sp
val AospItemSubtitleSize: TextUnit = 13.5.sp
val AospItemSubtitleLineHeight: TextUnit = 18.sp
val AospSectionHeaderSize: TextUnit = 14.sp
val AospIconBadgeSize = 42.dp
val AospIconSize = 22.dp
val AospGroupGap = 2.dp

fun aospGroupOuterRadius(): Dp = 24.dp

fun aospGroupInnerRadius(): Dp = 5.dp

@Composable
fun aospConnectedShape(index: Int, count: Int, outerRadius: Dp = aospGroupOuterRadius()): Shape {
    val inner = aospGroupInnerRadius()
    return when {
        count <= 1 -> RoundedCornerShape(outerRadius)
        index <= 0 -> RoundedCornerShape(
            topStart = outerRadius,
            topEnd = outerRadius,
            bottomStart = inner,
            bottomEnd = inner
        )
        index >= count - 1 -> RoundedCornerShape(
            topStart = inner,
            topEnd = inner,
            bottomStart = outerRadius,
            bottomEnd = outerRadius
        )
        else -> RoundedCornerShape(inner)
    }
}

@Composable
fun AospSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = AospSectionHeaderSize,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) trailing()
    }
}

@Composable
fun AospConnectedGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AospGroupGap),
        content = content
    )
}

@Composable
fun AospLeadingIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh
) {
    Box(
        modifier = modifier
            .size(AospIconBadgeSize)
            .clip(RoundedCornerShape(13.dp))
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(AospIconSize)
        )
    }
}

@Composable
private fun PreferenceTextBlock(
    title: String,
    subtitle: String?,
    titleColor: Color,
    subtitleColor: Color,
    subtitleWeight: FontWeight,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            fontSize = AospItemTitleSize,
            lineHeight = AospItemTitleLineHeight,
            fontWeight = FontWeight.SemiBold,
            color = titleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = AospItemSubtitleSize,
                lineHeight = AospItemSubtitleLineHeight,
                fontWeight = subtitleWeight,
                color = subtitleColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun AospPreferenceSwitch(
    index: Int,
    count: Int,
    icon: ImageVector?,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onCheckedChange(!checked)
        },
        shape = aospConnectedShape(index, count),
        color = MaterialTheme.colorScheme.surfaceContainer,
        enabled = enabled,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = if (icon != null) 16.dp else 20.dp, top = 14.dp, end = 12.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                AospLeadingIcon(icon = icon)
                Spacer(modifier = Modifier.width(16.dp))
            }
            PreferenceTextBlock(
                title = title,
                subtitle = subtitle,
                titleColor = MaterialTheme.colorScheme.onSurface,
                subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant,
                subtitleWeight = FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                thumbContent = {
                    Icon(
                        imageVector = if (checked) Icons.Rounded.Check else Icons.Rounded.Close,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize)
                    )
                }
            )
        }
    }
}

@Composable
fun AospPreferenceItem(
    index: Int,
    count: Int,
    icon: ImageVector?,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    highlightSubtitle: Boolean = false,
    valueText: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    isLoading: Boolean = false,
    iconTint: Color = MaterialTheme.colorScheme.onSurface,
    titleColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val haptic = LocalHapticFeedback.current
    val motion = MaterialTheme.motionScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.985f else 1f,
        animationSpec = motion.defaultSpatialSpec(),
        label = "aospPreferencePress"
    )

    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        shape = aospConnectedShape(index, count),
        color = MaterialTheme.colorScheme.surfaceContainer,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = if (icon != null) 16.dp else 20.dp, top = 14.dp, end = 16.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                if (isLoading) {
                    Box(
                        modifier = Modifier.size(AospIconBadgeSize),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    AospLeadingIcon(icon = icon, tint = iconTint)
                }
                Spacer(modifier = Modifier.width(16.dp))
            }
            PreferenceTextBlock(
                title = title,
                subtitle = subtitle,
                titleColor = if (enabled) titleColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                subtitleColor = when {
                    !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    highlightSubtitle -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                subtitleWeight = if (highlightSubtitle) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )
            if (valueText != null) {
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = valueText,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (trailing != null) {
                Spacer(modifier = Modifier.width(10.dp))
                trailing()
            }
        }
    }
}

@Composable
fun AospChevron(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Icon(
        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(22.dp)
    )
}

@Composable
fun AospMetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    supporting: String? = null
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLargeEmphasized,
                fontSize = 24.sp,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!supporting.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = supporting,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun AospStatusPill(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    leadingDotColor: Color? = null
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingDotColor != null) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(leadingDotColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                maxLines = 1
            )
        }
    }
}

@Composable
fun AospTopBar(
    title: String,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    val collapsedFraction = scrollBehavior.state.collapsedFraction
    val titleSize = (26f - (6f * collapsedFraction)).sp
    LargeTopAppBar(
        title = {
            if (subtitle.isNullOrBlank()) {
                Text(
                    text = title,
                    fontSize = titleSize,
                    letterSpacing = (-0.4).sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Column {
                    Text(
                        text = title,
                        fontSize = titleSize,
                        letterSpacing = (-0.4).sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        modifier = modifier,
        navigationIcon = navigationIcon ?: {},
        actions = actions ?: {},
        collapsedHeight = 56.dp,
        expandedHeight = 104.dp,
        windowInsets = WindowInsets.statusBars,
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.largeTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

typealias RowScope = androidx.compose.foundation.layout.RowScope

@Composable
fun LuminaBackButton(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = stringResource(R.string.action_back)
) {
    IconButton(
        onClick = onBack,
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onBackground
        )
    }
}

data class AospSheetOption<T>(
    val value: T,
    val label: String,
    val supporting: String? = null
)

@Composable
fun <T> AospOptionBottomSheet(
    title: String,
    options: List<AospSheetOption<T>>,
    isSelected: (T) -> Boolean,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    subtitle: String? = null
) {
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
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
            ) {
                if (options.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.aosp_no_options),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    options.forEachIndexed { index, option ->
                        val selected = isSelected(option.value)
                        SegmentedListItem(
                            selected = selected,
                            onClick = { onSelect(option.value) },
                            shapes = ListItemDefaults.segmentedShapes(
                                index = index,
                                count = options.size
                            ),
                            colors = ListItemDefaults.segmentedColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedLeadingContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            leadingContent = {
                                SelectionMark(selected = selected)
                            },
                            supportingContent = option.supporting?.let { support ->
                                {
                                    Text(
                                        text = support,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        ) {
                            Text(
                                text = option.label,
                                style = if (selected) {
                                    MaterialTheme.typography.titleSmallEmphasized
                                } else {
                                    MaterialTheme.typography.bodyLarge
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SelectionMark(
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(24.dp),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp)
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                    .padding(1.5.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
            )
        }
    }
}