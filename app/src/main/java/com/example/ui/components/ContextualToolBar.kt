package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.*
import kotlin.math.roundToInt
import com.example.ui.theme.StudioAccent

enum class PuppetInteractionMode {
    ROTATE_BONE, // چرخاندن استخوان و مفاصل با سینماتیک مستقیم (FK)
    ADD_BONE,    // افزودن استخوان جدید به زنجیره
    MOVE_PIN,
    ADD_PIN,
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
    onCommitTextToCanvas: () -> Unit,
    onResetTransform: () -> Unit,
    onResetPuppetPose: () -> Unit,
    isZenMode: Boolean,
    onToggleZenMode: () -> Unit,
    isJoystickVisible: Boolean,
    onToggleJoystick: () -> Unit,
    onClonePuppetArmy: () -> Unit,
    brush3dMaterial: String,
    onBrush3dMaterialChange: (String) -> Unit,
    brush3dDepth: Float,
    onBrush3dDepthChange: (Float) -> Unit,
    onPick3dTextureImage: () -> Unit,
    selectedBoneId: String? = null,
    onSelectBone: (String?) -> Unit = {},
    onSetupDefaultSkeleton: () -> Unit = {},
    onAutoSkinning: () -> Unit = {},
    onDeleteSelectedBone: () -> Unit = {},
    onBoneAngleChange: (Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showContourDialog by remember { mutableStateOf(false) }
    var panelOffset by remember { mutableStateOf(Offset(0f, 0f)) }
    var isCollapsed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .absoluteOffset { IntOffset(panelOffset.x.roundToInt(), panelOffset.y.roundToInt()) }
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .testTag("contextual_tool_bar")
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.96f),
            tonalElevation = 8.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.widthIn(max = 720.dp)) {
                // Header Drag Bar with title, reset-center and minimize/expand buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp))
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                panelOffset += dragAmount
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.DragIndicator,
                            contentDescription = "جابه‌جایی پنل",
                            tint = StudioAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = when (currentTool) {
                                ToolType.PUPPET -> "تنظیمات ریگ و پاپت (Puppet)"
                                ToolType.BRUSH, ToolType.BRUSH_3D -> "تنظیمات قلم‌مو"
                                ToolType.ERASER -> "تنظیمات پاک‌کن"
                                ToolType.SHAPES -> "تنظیمات اشکال"
                                ToolType.TEXT -> "تنظیمات متن"
                                ToolType.SELECT_MOVE -> "ابزار انتخاب و تبدیل"
                                else -> "تنظیمات ابزار"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { panelOffset = Offset.Zero },
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(
                                Icons.Default.CenterFocusWeak,
                                contentDescription = "تنظیم به مرکز",
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Spacer(Modifier.width(2.dp))
                        IconButton(
                            onClick = { isCollapsed = !isCollapsed },
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(
                                if (isCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                contentDescription = if (isCollapsed) "باز کردن" else "کوچک کردن",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                if (!isCollapsed) {
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
                        valueRange = 2f..120f,
                        modifier = Modifier
                            .width(110.dp)
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
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(currentColor)
                                .border(1.5.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                                .clickable { onOpenColorPicker() }
                                .testTag("contextual_color_preview")
                        )
                    }
                }

                ToolType.BRUSH_3D -> {
                    Text(
                        text = "قلم سه‌بعدی:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = StudioAccent
                    )

                    // Material Selector
                    listOf("SOLID" to "خالص", "CLAY" to "خمیری", "METALLIC" to "فلزی", "GLOSS" to "شیشه‌ای").forEach { (id, label) ->
                        FilterChip(
                            selected = brush3dMaterial == id,
                            onClick = { onBrush3dMaterialChange(id) },
                            label = { Text(label, fontSize = 10.sp) }
                        )
                    }

                    // Texture from photo button
                    AssistChip(
                        onClick = onPick3dTextureImage,
                        label = { Text("عکس/بافت", fontSize = 10.sp) },
                        leadingIcon = { Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    )

                    // Depth Slider
                    Text("برجستگی:", fontSize = 11.sp)
                    Slider(
                        value = brush3dDepth,
                        onValueChange = onBrush3dDepthChange,
                        valueRange = 1f..50f,
                        modifier = Modifier.width(90.dp)
                    )

                    // 3D Angle Joystick trigger
                    IconButton(
                        onClick = onToggleJoystick,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            Icons.Default.SportsEsports,
                            contentDescription = "جوی‌استیک زاویه",
                            tint = if (isJoystickVisible) StudioAccent else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                ToolType.PUPPET -> {
                    // 1. Skeletal Rigging Tools (Matching Puppet2D)
                    FilterChip(
                        selected = puppetMode == PuppetInteractionMode.ROTATE_BONE,
                        onClick = { onPuppetModeChange(PuppetInteractionMode.ROTATE_BONE) },
                        label = { Text("چرخش مفاصل (FK)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        leadingIcon = { Icon(Icons.Default.AccessibilityNew, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = StudioAccent.copy(alpha = 0.3f),
                            selectedLabelColor = StudioAccent
                        ),
                        modifier = Modifier.testTag("puppet_chip_rotate_bone")
                    )

                    FilterChip(
                        selected = puppetMode == PuppetInteractionMode.ADD_BONE,
                        onClick = { onPuppetModeChange(PuppetInteractionMode.ADD_BONE) },
                        label = { Text("افزودن استخوان +", fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("puppet_chip_add_bone")
                    )

                    // Default Skeleton Preset Button (Creates full humanoid rig matching image)
                    AssistChip(
                        onClick = onSetupDefaultSkeleton,
                        label = { Text("اسکلت آماده انسان", fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = StudioAccent, modifier = Modifier.size(16.dp)) }
                    )

                    // Auto Skinning Button (Weights mesh to bones)
                    AssistChip(
                        onClick = onAutoSkinning,
                        label = { Text("وزن‌دهی پوسته (Skinning)", fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = Color(0xFFFFB74D), modifier = Modifier.size(16.dp)) }
                    )

                    // Toggle Show Bones
                    FilterChip(
                        selected = puppetModifier.showBones,
                        onClick = {
                            onUpdatePuppetModifier(puppetModifier.copy(showBones = !puppetModifier.showBones))
                        },
                        label = { Text("نمایش اسکلت", fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.AccountTree, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )

                    // Selected Bone Info and Angle Slider if a bone is active
                    val activeBone = puppetModifier.bones.find { it.id == selectedBoneId }
                    if (activeBone != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = StudioAccent.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, StudioAccent.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${activeBone.name}: ${activeBone.angle.toInt()}°",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = StudioAccent
                                )
                                Slider(
                                    value = activeBone.angle,
                                    onValueChange = { onBoneAngleChange(it) },
                                    valueRange = -180f..180f,
                                    modifier = Modifier.width(90.dp)
                                )
                                IconButton(
                                    onClick = onDeleteSelectedBone,
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "حذف استخوان",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 2. Pin-based & Deformation Tools
                    FilterChip(
                        selected = puppetMode == PuppetInteractionMode.MOVE_PIN,
                        onClick = { onPuppetModeChange(PuppetInteractionMode.MOVE_PIN) },
                        label = { Text(stringResource(R.string.puppet_move_pin), fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.OpenWith, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("puppet_chip_move_pin")
                    )

                    FilterChip(
                        selected = puppetMode == PuppetInteractionMode.ADD_PIN,
                        onClick = { onPuppetModeChange(PuppetInteractionMode.ADD_PIN) },
                        label = { Text(stringResource(R.string.puppet_add_pin), fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.AddLocation, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("puppet_chip_add_pin")
                    )

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
                        modifier = Modifier.size(30.dp).testTag("puppet_reset_pose_button")
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

                    // Invisible 3D & Sculpting Deformers Menu
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

                    // Multi-level Volume Sculpting (Top, Upper-Mid, Lower-Mid, Bottom)
                    OutlinedButton(
                        onClick = { showContourDialog = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("تراش و فرم‌دهی بالا/پایین (سر/گردن/بدن)", fontSize = 10.sp)
                    }

                    // Floating Joystick button for 3D 360° rotation!
                    IconButton(
                        onClick = onToggleJoystick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.SportsEsports,
                            contentDescription = "جوی‌استیک چرخش",
                            tint = if (isJoystickVisible) StudioAccent else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Clone Puppet Character (Army builder)
                    Button(
                        onClick = onClonePuppetArmy,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.GroupAdd, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("تکثیر کاراکتر پاپت", fontSize = 10.sp)
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
                            .size(24.dp)
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
                            .width(170.dp)
                            .height(46.dp)
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

                    // Direct commit button to place text on canvas!
                    Button(
                        onClick = onCommitTextToCanvas,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = StudioAccent)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("درج متن روی بوم", fontSize = 11.sp)
                    }
                }

                ToolType.SELECT_MOVE -> {
                    Text(
                        text = "ابزار جابه‌جایی و تغییر مقیاس غیرمخرب",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    OutlinedButton(
                        onClick = onResetTransform,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.testTag("reset_transform_button")
                    ) {
                        Icon(Icons.Default.CenterFocusStrong, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.action_reset), fontSize = 10.sp)
                    }
                }

                ToolType.COLOR_PICKER -> {
                    Text("انتخابگر سریع رنگ:", fontSize = 11.sp)
                    StudioColorPalette.take(7).forEach { cLong ->
                        val col = Color(cLong)
                        Box(
                            modifier = Modifier
                                .size(22.dp)
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
                        Text(stringResource(R.string.tool_color), fontSize = 10.sp)
                    }
                }

                ToolType.HAND, ToolType.ZOOM -> {
                    Text("جابه‌جایی و بزرگ‌نمایی با دو انگشت فعال است", fontSize = 11.sp)
                }
            }

            Spacer(Modifier.weight(1f))

            // Zen Mode Quick Toggle Button
            IconButton(
                onClick = onToggleZenMode,
                modifier = Modifier.size(30.dp)
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
}

    // Modal dialog for Head & Neck / Body Contour Sculpting
    if (showContourDialog) {
        val contour = puppetModifier.volumeContour
        AlertDialog(
            onDismissRequest = { showContourDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = StudioAccent)
                    Spacer(Modifier.width(8.dp))
                    Text("تنظیمات فرم‌دهی حجم (سر، صورت، گردن و بدن)", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("با اسلایدرهای زیر می‌توانید ابعاد هر بخش را به دلخواه بزرگ یا کوچک کنید:", fontSize = 11.sp)

                    // Top (Head / Forehead / Shoulders)
                    Text("۱. بخش بالا (پیشانی / سر): ${(contour.topScale * 100).toInt()}%", fontSize = 11.sp)
                    Slider(
                        value = contour.topScale,
                        onValueChange = { s ->
                            onUpdatePuppetModifier(puppetModifier.copy(
                                deformerType = if (puppetModifier.deformerType == InvisibleDeformerType.NONE) InvisibleDeformerType.FREE_CONTOUR else puppetModifier.deformerType,
                                volumeContour = contour.copy(topScale = s)
                            ))
                        },
                        valueRange = 0.2f..2.5f
                    )

                    // Upper Mid (Cheeks / Chest)
                    Text("۲. بخش بالا-میانی (گونه‌ها / سینه): ${(contour.upperMidScale * 100).toInt()}%", fontSize = 11.sp)
                    Slider(
                        value = contour.upperMidScale,
                        onValueChange = { s ->
                            onUpdatePuppetModifier(puppetModifier.copy(
                                deformerType = if (puppetModifier.deformerType == InvisibleDeformerType.NONE) InvisibleDeformerType.FREE_CONTOUR else puppetModifier.deformerType,
                                volumeContour = contour.copy(upperMidScale = s)
                            ))
                        },
                        valueRange = 0.2f..2.5f
                    )

                    // Lower Mid (Jawline / Waist)
                    Text("۳. بخش پایین-میانی (چانه / کمر): ${(contour.lowerMidScale * 100).toInt()}%", fontSize = 11.sp)
                    Slider(
                        value = contour.lowerMidScale,
                        onValueChange = { s ->
                            onUpdatePuppetModifier(puppetModifier.copy(
                                deformerType = if (puppetModifier.deformerType == InvisibleDeformerType.NONE) InvisibleDeformerType.FREE_CONTOUR else puppetModifier.deformerType,
                                volumeContour = contour.copy(lowerMidScale = s)
                            ))
                        },
                        valueRange = 0.2f..2.5f
                    )

                    // Bottom (Neck / Hips)
                    Text("۴. بخش پایین (گردن / پایین‌تنه): ${(contour.bottomScale * 100).toInt()}%", fontSize = 11.sp)
                    Slider(
                        value = contour.bottomScale,
                        onValueChange = { s ->
                            onUpdatePuppetModifier(puppetModifier.copy(
                                deformerType = if (puppetModifier.deformerType == InvisibleDeformerType.NONE) InvisibleDeformerType.FREE_CONTOUR else puppetModifier.deformerType,
                                volumeContour = contour.copy(bottomScale = s)
                            ))
                        },
                        valueRange = 0.2f..2.5f
                    )

                    // Quick presets
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TextButton(onClick = {
                            onUpdatePuppetModifier(puppetModifier.copy(
                                deformerType = InvisibleDeformerType.HEAD_NECK,
                                volumeContour = VolumeContour(topScale = 1.0f, upperMidScale = 1.15f, lowerMidScale = 0.85f, bottomScale = 0.55f)
                            ))
                        }) {
                            Text("پریست سر و گردن", fontSize = 10.sp)
                        }

                        TextButton(onClick = {
                            onUpdatePuppetModifier(puppetModifier.copy(
                                deformerType = InvisibleDeformerType.BODY_TORSO,
                                volumeContour = VolumeContour(topScale = 1.25f, upperMidScale = 1.15f, lowerMidScale = 0.75f, bottomScale = 1.1f)
                            ))
                        }) {
                            Text("پریست بالاتنه و بدن", fontSize = 10.sp)
                        }

                        TextButton(onClick = {
                            onUpdatePuppetModifier(puppetModifier.copy(
                                volumeContour = VolumeContour(1f, 1f, 1f, 1f)
                            ))
                        }) {
                            Text("بازنشانی", fontSize = 10.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showContourDialog = false }) {
                    Text("تأیید و اعمال")
                }
            }
        )
    }
}
}

private fun getDeformerLabel(deformer: InvisibleDeformerType): String = when (deformer) {
    InvisibleDeformerType.NONE -> "هیچ‌کدام"
    InvisibleDeformerType.CYLINDER -> "استوانه"
    InvisibleDeformerType.SPHERE -> "کره سه‌بعدی"
    InvisibleDeformerType.CAPSULE -> "کپسول"
    InvisibleDeformerType.BALLOON -> "پف بادکنک"
    InvisibleDeformerType.HEAD_NECK -> "سر و گردن"
    InvisibleDeformerType.BODY_TORSO -> "بالاتنه و بدن"
    InvisibleDeformerType.FREE_CONTOUR -> "تراش آزاد حجم"
}
