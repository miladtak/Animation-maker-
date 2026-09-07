package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.*

enum class PuppetInteractionMode {
    ADD_PIN,
    MOVE_PIN,
    DELETE_PIN,
    ROTATE_PIN,
    MIRROR_PIN
}

@Composable
fun ContextualToolBar(
    currentTool: ToolType,
    brushSize: Float,
    onBrushSizeChange: (Float) -> Unit,
    currentColor: Color,
    onOpenColorPicker: () -> Unit,
    puppetMode: PuppetInteractionMode,
    onPuppetModeChange: (PuppetInteractionMode) -> Unit,
    puppetModifier: PuppetModifier,
    onUpdatePuppetModifier: (PuppetModifier) -> Unit,
    selectedShapeType: ShapeType,
    onShapeTypeChange: (ShapeType) -> Unit,
    isShapeFilled: Boolean,
    onToggleShapeFilled: () -> Unit,
    currentText: String,
    onTextChange: (String) -> Unit,
    textMode: TextMode,
    onTextModeChange: (TextMode) -> Unit,
    onResetTransform: () -> Unit,
    onResetPuppetPose: () -> Unit,
    isZenMode: Boolean,
    onToggleZenMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("contextual_tool_bar"),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
        tonalElevation = 6.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 5.dp)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (currentTool) {
                ToolType.BRUSH, ToolType.ERASER -> {
                    Text(
                        text = if (currentTool == ToolType.BRUSH) "اندازه قلم:" else "اندازه پاک‌کن:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Slider(
                        value = brushSize,
                        onValueChange = onBrushSizeChange,
                        valueRange = 4f..100f,
                        modifier = Modifier
                            .width(130.dp)
                            .testTag("brush_size_slider")
                    )
                    Text(
                        text = "${brushSize.toInt()}px",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (currentTool == ToolType.BRUSH) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(currentColor)
                                .border(1.5.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                                .clickable { onOpenColorPicker() }
                                .testTag("contextual_color_preview")
                        )
                    }
                }

                ToolType.PUPPET -> {
                    // Add Pin Chip
                    FilterChip(
                        selected = puppetMode == PuppetInteractionMode.ADD_PIN,
                        onClick = { onPuppetModeChange(PuppetInteractionMode.ADD_PIN) },
                        label = { Text(stringResource(R.string.puppet_add_pin), fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.AddLocation, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("puppet_chip_add_pin")
                    )

                    // Move Pin Chip
                    FilterChip(
                        selected = puppetMode == PuppetInteractionMode.MOVE_PIN,
                        onClick = { onPuppetModeChange(PuppetInteractionMode.MOVE_PIN) },
                        label = { Text(stringResource(R.string.puppet_move_pin), fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.OpenWith, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("puppet_chip_move_pin")
                    )

                    // Delete Pin Chip
                    FilterChip(
                        selected = puppetMode == PuppetInteractionMode.DELETE_PIN,
                        onClick = { onPuppetModeChange(PuppetInteractionMode.DELETE_PIN) },
                        label = { Text(stringResource(R.string.puppet_delete_pin), fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("puppet_chip_delete_pin")
                    )

                    // Reset Pose button
                    IconButton(
                        onClick = onResetPuppetPose,
                        modifier = Modifier.size(32.dp).testTag("puppet_reset_pose_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.puppet_reset_pose), tint = MaterialTheme.colorScheme.tertiary)
                    }

                    // Show mesh toggle
                    FilterChip(
                        selected = puppetModifier.showMesh,
                        onClick = {
                            onUpdatePuppetModifier(puppetModifier.copy(showMesh = !puppetModifier.showMesh))
                        },
                        label = { Text(stringResource(R.string.puppet_show_mesh), fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.GridOn, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )

                    // Density Selector
                    var showDensityMenu by remember { mutableStateOf(false) }
                    Box {
                        AssistChip(
                            onClick = { showDensityMenu = true },
                            label = { Text("تراکم: ${puppetModifier.density.name}", fontSize = 11.sp) },
                            leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                        DropdownMenu(
                            expanded = showDensityMenu,
                            onDismissRequest = { showDensityMenu = false }
                        ) {
                            MeshDensity.entries.forEach { d ->
                                DropdownMenuItem(
                                    text = { Text(d.name) },
                                    onClick = {
                                        onUpdatePuppetModifier(puppetModifier.copy(density = d))
                                        showDensityMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // Physics Presets
                    var showPhysicsMenu by remember { mutableStateOf(false) }
                    Box {
                        AssistChip(
                            onClick = { showPhysicsMenu = true },
                            label = { Text("فیزیک: ${getPhysicsPresetLabel(puppetModifier.physicsPreset)}", fontSize = 11.sp) },
                            leadingIcon = { Icon(Icons.Default.Science, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                        DropdownMenu(
                            expanded = showPhysicsMenu,
                            onDismissRequest = { showPhysicsMenu = false }
                        ) {
                            PhysicsPreset.entries.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text(getPhysicsPresetLabel(p)) },
                                    onClick = {
                                        onUpdatePuppetModifier(puppetModifier.copy(physicsPreset = p))
                                        showPhysicsMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // Invisible 3D Deformers (Sphere, Cylinder, Capsule, Balloon)
                    var showDeformerMenu by remember { mutableStateOf(false) }
                    Box {
                        AssistChip(
                            onClick = { showDeformerMenu = true },
                            label = { Text("حجم: ${getDeformerLabel(puppetModifier.deformerType)}", fontSize = 11.sp) },
                            leadingIcon = { Icon(Icons.Default.ViewInAr, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                        DropdownMenu(
                            expanded = showDeformerMenu,
                            onDismissRequest = { showDeformerMenu = false }
                        ) {
                            InvisibleDeformerType.entries.forEach { def ->
                                DropdownMenuItem(
                                    text = { Text(getDeformerLabel(def)) },
                                    onClick = {
                                        onUpdatePuppetModifier(puppetModifier.copy(deformerType = def))
                                        showDeformerMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // Slider for Deformer Rotation 360 wrap
                    if (puppetModifier.deformerType != InvisibleDeformerType.NONE) {
                        Text("زاویه چرخش:", fontSize = 11.sp)
                        Slider(
                            value = puppetModifier.deformerRotation,
                            onValueChange = { rot ->
                                onUpdatePuppetModifier(puppetModifier.copy(deformerRotation = rot))
                            },
                            valueRange = -180f..180f,
                            modifier = Modifier.width(100.dp)
                        )
                    }
                }

                ToolType.SHAPES -> {
                    ShapeType.entries.forEach { sType ->
                        FilterChip(
                            selected = selectedShapeType == sType,
                            onClick = { onShapeTypeChange(sType) },
                            label = { Text(sType.name, fontSize = 11.sp) },
                            modifier = Modifier.testTag("shape_chip_${sType.name.lowercase()}")
                        )
                    }
                    FilterChip(
                        selected = isShapeFilled,
                        onClick = onToggleShapeFilled,
                        label = { Text(if (isShapeFilled) "توپُر" else "فقط خط دور", fontSize = 11.sp) }
                    )
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(currentColor)
                            .clickable { onOpenColorPicker() }
                    )
                }

                ToolType.TEXT -> {
                    OutlinedTextField(
                        value = currentText,
                        onValueChange = onTextChange,
                        modifier = Modifier
                            .width(180.dp)
                            .height(48.dp)
                            .testTag("contextual_text_input"),
                        textStyle = MaterialTheme.typography.bodySmall,
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.text_input_hint), fontSize = 11.sp) }
                    )
                    TextMode.entries.forEach { tm ->
                        FilterChip(
                            selected = textMode == tm,
                            onClick = { onTextModeChange(tm) },
                            label = { Text(tm.name, fontSize = 11.sp) }
                        )
                    }
                }

                ToolType.SELECT_MOVE -> {
                    Text(
                        text = "ابزار جابه‌جایی زنده و غیرمخرب",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    OutlinedButton(
                        onClick = onResetTransform,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("reset_transform_button")
                    ) {
                        Icon(Icons.Default.CenterFocusStrong, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.action_reset), fontSize = 11.sp)
                    }
                }

                ToolType.COLOR_PICKER -> {
                    Text("انتخابگر سریع رنگ:", fontSize = 12.sp)
                    StudioColorPalette.take(8).forEach { cLong ->
                        val col = Color(cLong)
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(col)
                                .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                                .clickable { onOpenColorPicker() }
                        )
                    }
                    Button(
                        onClick = onOpenColorPicker,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(stringResource(R.string.tool_color), fontSize = 11.sp)
                    }
                }

                ToolType.HAND, ToolType.ZOOM -> {
                    Text("حرکت دو انگشتی و زوم روی بوم نقاشی فعال است", fontSize = 12.sp)
                }
            }

            Spacer(Modifier.weight(1f))

            // Zen Mode Quick Toggle Button
            IconButton(
                onClick = onToggleZenMode,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    if (isZenMode) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    contentDescription = stringResource(if (isZenMode) R.string.exit_zen_mode else R.string.action_zen_mode),
                    tint = if (isZenMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun getPhysicsPresetLabel(preset: PhysicsPreset): String = when (preset) {
    PhysicsPreset.NONE -> "دستی"
    PhysicsPreset.CLOTH -> "پارچه"
    PhysicsPreset.RUBBER -> "لاستیک"
    PhysicsPreset.JELLY -> "ژله‌ای"
    PhysicsPreset.HAIR -> "مو و خز"
    PhysicsPreset.PLANT -> "گیاه"
    PhysicsPreset.WIND -> "باد"
    PhysicsPreset.WATER -> "آب"
    PhysicsPreset.FIRE -> "آتش"
    PhysicsPreset.BALLOON -> "بادکنک"
    PhysicsPreset.HEAVY_STONE -> "سنگینی سنگ"
    PhysicsPreset.WOOD -> "چوب صلب"
    PhysicsPreset.METAL -> "فلز فنری"
    PhysicsPreset.SPRING -> "فنر ارتجاعی"
}

private fun getDeformerLabel(deformer: InvisibleDeformerType): String = when (deformer) {
    InvisibleDeformerType.NONE -> "هیچ‌کدام"
    InvisibleDeformerType.CYLINDER -> "استوانه"
    InvisibleDeformerType.SPHERE -> "کره سه‌بعدی"
    InvisibleDeformerType.CAPSULE -> "کپسول"
    InvisibleDeformerType.BALLOON -> "پف بادکنک"
}
