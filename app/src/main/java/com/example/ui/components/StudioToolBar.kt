package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.ToolType
import com.example.ui.theme.StudioAccent

@Composable
fun StudioToolBar(
    activeTool: ToolType,
    onSelectTool: (ToolType) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .testTag("studio_tool_bar"),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ToolItem(
                tool = ToolType.SELECT_MOVE,
                icon = Icons.Default.NearMe,
                contentDescription = "جابه‌جایی",
                isSelected = activeTool == ToolType.SELECT_MOVE,
                onSelect = { onSelectTool(ToolType.SELECT_MOVE) }
            )
            ToolItem(
                tool = ToolType.PUPPET,
                icon = Icons.Default.AccessibilityNew,
                contentDescription = "پاپت وارپ",
                isSelected = activeTool == ToolType.PUPPET,
                onSelect = { onSelectTool(ToolType.PUPPET) }
            )
            ToolItem(
                tool = ToolType.BRUSH,
                icon = Icons.Default.Brush,
                contentDescription = "قلم‌مو",
                isSelected = activeTool == ToolType.BRUSH,
                onSelect = { onSelectTool(ToolType.BRUSH) }
            )
            ToolItem(
                tool = ToolType.ERASER,
                icon = Icons.Default.AutoFixNormal,
                contentDescription = "پاک‌کن",
                isSelected = activeTool == ToolType.ERASER,
                onSelect = { onSelectTool(ToolType.ERASER) }
            )
            ToolItem(
                tool = ToolType.SHAPES,
                icon = Icons.Default.Category,
                contentDescription = "اشکال هندسی",
                isSelected = activeTool == ToolType.SHAPES,
                onSelect = { onSelectTool(ToolType.SHAPES) }
            )
            ToolItem(
                tool = ToolType.TEXT,
                icon = Icons.Default.TextFields,
                contentDescription = "متن",
                isSelected = activeTool == ToolType.TEXT,
                onSelect = { onSelectTool(ToolType.TEXT) }
            )
            ToolItem(
                tool = ToolType.COLOR_PICKER,
                icon = Icons.Default.ColorLens,
                contentDescription = "رنگ",
                isSelected = activeTool == ToolType.COLOR_PICKER,
                onSelect = { onSelectTool(ToolType.COLOR_PICKER) }
            )
            ToolItem(
                tool = ToolType.HAND,
                icon = Icons.Default.PanTool,
                contentDescription = "دست",
                isSelected = activeTool == ToolType.HAND,
                onSelect = { onSelectTool(ToolType.HAND) }
            )
            ToolItem(
                tool = ToolType.ZOOM,
                icon = Icons.Default.ZoomIn,
                contentDescription = "بزرگ‌نمایی",
                isSelected = activeTool == ToolType.ZOOM,
                onSelect = { onSelectTool(ToolType.ZOOM) }
            )
        }
    }
}

@Composable
private fun ToolItem(
    tool: ToolType,
    icon: ImageVector,
    contentDescription: String,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) StudioAccent.copy(alpha = 0.25f) else Color.Transparent)
            .border(
                width = if (isSelected) 1.5.dp else 0.dp,
                color = if (isSelected) StudioAccent else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onSelect() }
            .testTag("tool_button_${tool.name.lowercase()}"),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isSelected) StudioAccent else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}
