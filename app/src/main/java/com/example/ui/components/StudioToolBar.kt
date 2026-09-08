package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ToolType
import com.example.ui.theme.StudioAccent
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun StudioToolBar(
    activeTool: ToolType,
    onSelectTool: (ToolType) -> Unit,
    modifier: Modifier = Modifier
) {
    // Toolbar state: Draggable offset, Bubble mode, Auto-hide, Size scale
    var barOffset by remember { mutableStateOf(Offset(0f, 0f)) }
    var isBubbleMode by remember { mutableStateOf(false) }
    var isPinned by remember { mutableStateOf(true) } // Pinned open by default
    var toolSizeScale by remember { mutableStateOf(1.0f) } // 0.85f, 1.0f, 1.25f

    val itemSize = (38 * toolSizeScale).dp
    val iconSize = (20 * toolSizeScale).dp

    // Auto-hide timer when unpinned
    LaunchedEffect(activeTool, isPinned) {
        if (!isPinned && !isBubbleMode) {
            delay(5000)
            isBubbleMode = true
        }
    }

    Box(
        modifier = modifier
            .absoluteOffset { IntOffset(barOffset.x.roundToInt(), barOffset.y.roundToInt()) }
            .testTag("flipaclip_dock_container")
    ) {
        if (isBubbleMode) {
            // Collapsed FlipaClip Floating Bubble
            Surface(
                modifier = Modifier
                    .size((48 * toolSizeScale).dp)
                    .clip(CircleShape)
                    .shadow(8.dp, CircleShape)
                    .border(2.dp, StudioAccent, CircleShape)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            barOffset += dragAmount
                        }
                    }
                    .clickable { isBubbleMode = false }
                    .testTag("toolbar_bubble_button"),
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp),
                tonalElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    val activeIcon = getToolIcon(activeTool)
                    Icon(
                        imageVector = activeIcon,
                        contentDescription = "بازکردن نوار ابزار",
                        tint = StudioAccent,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        } else {
            // Expanded FlipaClip Floating / Dockable Toolbar
            Surface(
                modifier = Modifier
                    .width((itemSize + 14.dp))
                    .clip(RoundedCornerShape(22.dp))
                    .shadow(10.dp, RoundedCornerShape(22.dp))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                        RoundedCornerShape(22.dp)
                    )
                    .testTag("studio_tool_bar"),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top Drag Handle & Pin & Collapse Controls
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp)
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    barOffset += dragAmount
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(24.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                        )
                    }

                    // Pin & Scale Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Pin button
                        IconButton(
                            onClick = { isPinned = !isPinned },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                if (isPinned) Icons.Default.PushPin else Icons.Default.VerticalAlignBottom,
                                contentDescription = if (isPinned) "پین شده" else "پنهان‌سازی خودکار",
                                tint = if (isPinned) StudioAccent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        // Size cycle button
                        IconButton(
                            onClick = {
                                toolSizeScale = when {
                                    toolSizeScale < 0.95f -> 1.0f
                                    toolSizeScale < 1.15f -> 1.25f
                                    else -> 0.85f
                                }
                            },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                Icons.Default.ZoomOutMap,
                                contentDescription = "تغییر اندازه آیکون‌ها",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                        }

                        // Collapse to bubble button
                        IconButton(
                            onClick = { isBubbleMode = true },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                Icons.Default.CloseFullscreen,
                                contentDescription = "حالت حبابی",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }

                    Divider(
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .fillMaxWidth(0.8f),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )

                    // Scrollable Tools Column organized into functional studio groups
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Group 1: Select, Transform & Puppet Rig
                        ToolItem(
                            tool = ToolType.SELECT_MOVE,
                            icon = Icons.Default.NearMe,
                            contentDescription = "انتخاب و جابه‌جایی",
                            isSelected = activeTool == ToolType.SELECT_MOVE,
                            size = itemSize,
                            iconSize = iconSize,
                            onSelect = { onSelectTool(ToolType.SELECT_MOVE) }
                        )

                        ToolItem(
                            tool = ToolType.PUPPET,
                            icon = Icons.Default.AccessibilityNew,
                            contentDescription = "تغییر شکل پاپت (Puppet Warp)",
                            isSelected = activeTool == ToolType.PUPPET,
                            size = itemSize,
                            iconSize = iconSize,
                            onSelect = { onSelectTool(ToolType.PUPPET) }
                        )

                        HorizontalDivider(
                            modifier = Modifier
                                .width(itemSize * 0.65f)
                                .padding(vertical = 2.dp),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        )

                        // Group 2: Artistic Drawing & Volume
                        ToolItem(
                            tool = ToolType.BRUSH,
                            icon = Icons.Default.Brush,
                            contentDescription = "قلم‌مو (Brush)",
                            isSelected = activeTool == ToolType.BRUSH,
                            size = itemSize,
                            iconSize = iconSize,
                            onSelect = { onSelectTool(ToolType.BRUSH) }
                        )

                        ToolItem(
                            tool = ToolType.BRUSH_3D,
                            icon = Icons.Default.Draw,
                            contentDescription = "قلم سه‌بعدی حجم‌دار (3D Brush)",
                            isSelected = activeTool == ToolType.BRUSH_3D,
                            size = itemSize,
                            iconSize = iconSize,
                            onSelect = { onSelectTool(ToolType.BRUSH_3D) }
                        )

                        ToolItem(
                            tool = ToolType.ERASER,
                            icon = Icons.Default.AutoFixNormal,
                            contentDescription = "پاک‌کن",
                            isSelected = activeTool == ToolType.ERASER,
                            size = itemSize,
                            iconSize = iconSize,
                            onSelect = { onSelectTool(ToolType.ERASER) }
                        )

                        HorizontalDivider(
                            modifier = Modifier
                                .width(itemSize * 0.65f)
                                .padding(vertical = 2.dp),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        )

                        // Group 3: Graphic Vectors & Text
                        ToolItem(
                            tool = ToolType.SHAPES,
                            icon = Icons.Default.Category,
                            contentDescription = "اشکال هندسی",
                            isSelected = activeTool == ToolType.SHAPES,
                            size = itemSize,
                            iconSize = iconSize,
                            onSelect = { onSelectTool(ToolType.SHAPES) }
                        )

                        ToolItem(
                            tool = ToolType.TEXT,
                            icon = Icons.Default.TextFields,
                            contentDescription = "ابزار متن",
                            isSelected = activeTool == ToolType.TEXT,
                            size = itemSize,
                            iconSize = iconSize,
                            onSelect = { onSelectTool(ToolType.TEXT) }
                        )

                        ToolItem(
                            tool = ToolType.COLOR_PICKER,
                            icon = Icons.Default.ColorLens,
                            contentDescription = "پالت و انتخاب رنگ",
                            isSelected = activeTool == ToolType.COLOR_PICKER,
                            size = itemSize,
                            iconSize = iconSize,
                            onSelect = { onSelectTool(ToolType.COLOR_PICKER) }
                        )

                        HorizontalDivider(
                            modifier = Modifier
                                .width(itemSize * 0.65f)
                                .padding(vertical = 2.dp),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        )

                        // Group 4: Canvas Navigation
                        ToolItem(
                            tool = ToolType.HAND,
                            icon = Icons.Default.PanTool,
                            contentDescription = "دست و جابه‌جایی بوم",
                            isSelected = activeTool == ToolType.HAND,
                            size = itemSize,
                            iconSize = iconSize,
                            onSelect = { onSelectTool(ToolType.HAND) }
                        )

                        ToolItem(
                            tool = ToolType.ZOOM,
                            icon = Icons.Default.ZoomIn,
                            contentDescription = "بزرگ‌نمایی و کوچک‌نمایی",
                            isSelected = activeTool == ToolType.ZOOM,
                            size = itemSize,
                            iconSize = iconSize,
                            onSelect = { onSelectTool(ToolType.ZOOM) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolItem(
    tool: ToolType,
    icon: ImageVector,
    contentDescription: String,
    isSelected: Boolean,
    size: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    onSelect: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) StudioAccent.copy(alpha = 0.25f) else Color.Transparent)
            .border(
                width = if (isSelected) 1.5.dp else 0.dp,
                color = if (isSelected) StudioAccent else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onSelect() }
            .testTag("tool_button_${tool.name.lowercase()}"),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isSelected) StudioAccent else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(iconSize)
        )
    }
}

private fun getToolIcon(tool: ToolType): ImageVector = when (tool) {
    ToolType.SELECT_MOVE -> Icons.Default.NearMe
    ToolType.PUPPET -> Icons.Default.AccessibilityNew
    ToolType.BRUSH -> Icons.Default.Brush
    ToolType.BRUSH_3D -> Icons.Default.Draw
    ToolType.ERASER -> Icons.Default.AutoFixNormal
    ToolType.SHAPES -> Icons.Default.Category
    ToolType.TEXT -> Icons.Default.TextFields
    ToolType.COLOR_PICKER -> Icons.Default.ColorLens
    ToolType.HAND -> Icons.Default.PanTool
    ToolType.ZOOM -> Icons.Default.ZoomIn
}
