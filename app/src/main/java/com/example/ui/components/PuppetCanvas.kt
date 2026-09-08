package com.example.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.PuppetWarpEngine
import com.example.model.*
import com.example.ui.theme.StudioAccent
import com.example.ui.theme.StudioAccentAmber
import kotlin.math.*

@Composable
fun PuppetCanvas(
    project: Project,
    activeTool: ToolType,
    puppetMode: PuppetInteractionMode,
    brushSize: Float,
    brushColor: Color,
    viewportOffset: Offset,
    onViewportOffsetChange: (Offset) -> Unit,
    zoomScale: Float,
    onZoomScaleChange: (Float) -> Unit,
    onAddStrokePoint: (PointData, Boolean) -> Unit,
    onFinishStroke: () -> Unit,
    onUpdateLayerTransform: (String, LayerTransform, Boolean) -> Unit,
    onAddPuppetPin: (String, Float, Float) -> Unit,
    onMovePuppetPin: (String, String, Float, Float, Boolean) -> Unit,
    onDeletePuppetPin: (String, String) -> Unit,
    selectedBoneId: String? = null,
    onSelectBone: (String?) -> Unit = {},
    onRotateBone: (String, String, Float, Boolean) -> Unit = { _, _, _, _ -> },
    onAddBone: (String, Float, Float) -> Unit = { _, _, _ -> },
    onBackgroundModeChange: ((CanvasBackgroundMode) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var activePinId by remember { mutableStateOf<String?>(null) }
    var moveStartOffset by remember { mutableStateOf(Offset.Zero) }
    var initialTranslation by remember { mutableStateOf(Offset.Zero) }
    var latestMoveTransform by remember { mutableStateOf<LayerTransform?>(null) }
    var lastStrokePoint by remember { mutableStateOf<PointData?>(null) }
    var lastBoneTouchAngle by remember { mutableStateOf<Float?>(null) }

    val activeLayer = project.layers.find { it.id == project.activeLayerId }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("puppet_canvas_container")
            // Unified gesture processing: Pointer events, 2-finger zoom/pan, stylus pressure, and interpolation
            .pointerInput(activeTool, puppetMode, activeLayer?.id, viewportOffset, zoomScale) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var isTwoFingerTransform = false
                    var totalDragDistance = 0f
                    val startPos = down.position
                    val isEraser = activeTool == ToolType.ERASER

                    // Initialize move tool translation state
                    if (activeTool == ToolType.SELECT_MOVE && activeLayer != null) {
                        moveStartOffset = down.position
                        initialTranslation = Offset(
                            activeLayer.transform.translationX,
                            activeLayer.transform.translationY
                        )
                        latestMoveTransform = activeLayer.transform
                    }

                    // Initialize puppet pin or bone selection
                    if (activeTool == ToolType.PUPPET && activeLayer != null) {
                        val lx = (down.position.x - viewportOffset.x) / zoomScale - activeLayer.transform.translationX
                        val ly = (down.position.y - viewportOffset.y) / zoomScale - activeLayer.transform.translationY

                        if (puppetMode == PuppetInteractionMode.ROTATE_BONE) {
                            // Find nearest bone to touch point (finite segment or joints)
                            val nearestBone = activeLayer.puppetModifier.bones.minByOrNull { bone ->
                                PuppetWarpEngine.distanceToBoneSegment(lx, ly, bone)
                            }
                            if (nearestBone != null) {
                                val dist = PuppetWarpEngine.distanceToBoneSegment(lx, ly, nearestBone)
                                if (dist < 65f) {
                                    onSelectBone(nearestBone.id)
                                    val angle = Math.toDegrees(
                                        atan2((ly - nearestBone.globalStartY).toDouble(), (lx - nearestBone.globalStartX).toDouble())
                                    ).toFloat()
                                    lastBoneTouchAngle = angle
                                }
                            }
                        } else {
                            val nearest = activeLayer.puppetModifier.pins.minByOrNull { p ->
                                val dx = p.x - lx
                                val dy = p.y - ly
                                dx * dx + dy * dy
                            }
                            activePinId = if (nearest != null) {
                                val dist = sqrt((nearest.x - lx) * (nearest.x - lx) + (nearest.y - ly) * (nearest.y - ly))
                                if (dist < 60f) nearest.id else null
                            } else null
                        }
                    }

                    // Brush / Eraser start point
                    if ((activeTool == ToolType.BRUSH || activeTool == ToolType.BRUSH_3D || activeTool == ToolType.ERASER) && activeLayer != null) {
                        val lx = (down.position.x - viewportOffset.x) / zoomScale - activeLayer.transform.translationX
                        val ly = (down.position.y - viewportOffset.y) / zoomScale - activeLayer.transform.translationY
                        val initialPt = PointData(lx, ly, down.pressure.coerceIn(0.1f, 2.0f))
                        lastStrokePoint = initialPt
                        onAddStrokePoint(initialPt, isEraser)
                        down.consume()
                    }

                    do {
                        val event = awaitPointerEvent()
                        val pressedPointers = event.changes.filter { it.pressed }

                        if (pressedPointers.size >= 2) {
                            // Two-finger pinch-to-zoom & pan gestures (works everywhere!)
                            isTwoFingerTransform = true
                            val pan = event.calculatePan()
                            val zoom = event.calculateZoom()

                            if (pan != Offset.Zero) {
                                onViewportOffsetChange(viewportOffset + pan)
                            }
                            if (zoom != 1f) {
                                onZoomScaleChange((zoomScale * zoom).coerceIn(0.02f, 30.0f))
                            }
                            event.changes.forEach { it.consume() }
                        } else if (pressedPointers.size == 1 && !isTwoFingerTransform) {
                            val change = pressedPointers.first()
                            val currentPos = change.position
                            val deltaPos = currentPos - startPos
                            totalDragDistance += sqrt(deltaPos.x * deltaPos.x + deltaPos.y * deltaPos.y)

                            when (activeTool) {
                                ToolType.BRUSH, ToolType.BRUSH_3D, ToolType.ERASER -> {
                                    if (activeLayer != null) {
                                        val lx = (currentPos.x - viewportOffset.x) / zoomScale - activeLayer.transform.translationX
                                        val ly = (currentPos.y - viewportOffset.y) / zoomScale - activeLayer.transform.translationY
                                        val pressure = change.pressure.coerceIn(0.1f, 2.0f)
                                        val targetPt = PointData(lx, ly, pressure)

                                        // Anti-gap interpolation: insert intermediate points on fast strokes
                                        val last = lastStrokePoint
                                        if (last != null) {
                                            val dx = targetPt.x - last.x
                                            val dy = targetPt.y - last.y
                                            val dist = sqrt(dx * dx + dy * dy)
                                            if (dist > 8f) {
                                                val steps = (dist / 6f).toInt().coerceIn(1, 10)
                                                for (s in 1 until steps) {
                                                    val fraction = s.toFloat() / steps
                                                    val interpX = last.x + dx * fraction
                                                    val interpY = last.y + dy * fraction
                                                    val interpPressure = last.pressure + (pressure - last.pressure) * fraction
                                                    onAddStrokePoint(PointData(interpX, interpY, interpPressure), isEraser)
                                                }
                                            }
                                        }

                                        lastStrokePoint = targetPt
                                        onAddStrokePoint(targetPt, isEraser)
                                        change.consume()
                                    }
                                }

                                ToolType.SELECT_MOVE -> {
                                    if (activeLayer != null) {
                                        val delta = currentPos - moveStartOffset
                                        val newTx = initialTranslation.x + delta.x / zoomScale
                                        val newTy = initialTranslation.y + delta.y / zoomScale
                                        val updatedTransform = activeLayer.transform.copy(translationX = newTx, translationY = newTy)
                                        latestMoveTransform = updatedTransform
                                        onUpdateLayerTransform(
                                            activeLayer.id,
                                            updatedTransform,
                                            false
                                        )
                                        change.consume()
                                    }
                                }

                                ToolType.PUPPET -> {
                                    if (activeLayer != null) {
                                        val lx = (currentPos.x - viewportOffset.x) / zoomScale - activeLayer.transform.translationX
                                        val ly = (currentPos.y - viewportOffset.y) / zoomScale - activeLayer.transform.translationY

                                        if (puppetMode == PuppetInteractionMode.ROTATE_BONE && selectedBoneId != null) {
                                            val bone = activeLayer.puppetModifier.bones.find { it.id == selectedBoneId }
                                            if (bone != null) {
                                                val curAngle = Math.toDegrees(
                                                    atan2((ly - bone.globalStartY).toDouble(), (lx - bone.globalStartX).toDouble())
                                                ).toFloat()
                                                val lastAngle = lastBoneTouchAngle
                                                if (lastAngle != null) {
                                                    var delta = curAngle - lastAngle
                                                    if (delta > 180f) delta -= 360f
                                                    if (delta < -180f) delta += 360f
                                                    if (abs(delta) > 0.05f) {
                                                        onRotateBone(activeLayer.id, bone.id, delta, false)
                                                    }
                                                }
                                                lastBoneTouchAngle = curAngle
                                                change.consume()
                                            }
                                        } else if (puppetMode == PuppetInteractionMode.MOVE_PIN && activePinId != null) {
                                            onMovePuppetPin(activeLayer.id, activePinId!!, lx, ly, false)
                                            change.consume()
                                        }
                                    }
                                }

                                ToolType.HAND -> {
                                    val panDelta = currentPos - startPos
                                    onViewportOffsetChange(viewportOffset + panDelta)
                                    change.consume()
                                }

                                ToolType.ZOOM -> {
                                    val deltaY = -(currentPos.y - startPos.y)
                                    val zoomFactor = (1f + deltaY * 0.005f).coerceIn(0.85f, 1.15f)
                                    onZoomScaleChange((zoomScale * zoomFactor).coerceIn(0.02f, 30.0f))
                                    change.consume()
                                }

                                else -> {}
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    // Pointer Up action commit
                    if (!isTwoFingerTransform) {
                        if (activeTool == ToolType.BRUSH || activeTool == ToolType.BRUSH_3D || activeTool == ToolType.ERASER) {
                            onFinishStroke()
                            lastStrokePoint = null
                        } else if (activeTool == ToolType.SELECT_MOVE && activeLayer != null) {
                            val finalTransform = latestMoveTransform ?: activeLayer.transform
                            onUpdateLayerTransform(activeLayer.id, finalTransform, true)
                            initialTranslation = Offset(finalTransform.translationX, finalTransform.translationY)
                            latestMoveTransform = null
                        } else if (activeTool == ToolType.PUPPET && activeLayer != null) {
                            val lx = (startPos.x - viewportOffset.x) / zoomScale - activeLayer.transform.translationX
                            val ly = (startPos.y - viewportOffset.y) / zoomScale - activeLayer.transform.translationY

                            when (puppetMode) {
                                PuppetInteractionMode.ROTATE_BONE -> {
                                    if (selectedBoneId != null) {
                                        onRotateBone(activeLayer.id, selectedBoneId, 0f, true)
                                    }
                                }
                                PuppetInteractionMode.ADD_BONE -> {
                                    if (totalDragDistance < 25f) {
                                        onAddBone(activeLayer.id, lx, ly)
                                    }
                                }
                                PuppetInteractionMode.ADD_PIN -> {
                                    if (totalDragDistance < 20f) {
                                        onAddPuppetPin(activeLayer.id, lx, ly)
                                    }
                                }
                                PuppetInteractionMode.DELETE_PIN -> {
                                    if (totalDragDistance < 20f) {
                                        val nearest = activeLayer.puppetModifier.pins.minByOrNull { p ->
                                            val dx = p.x - lx
                                            val dy = p.y - ly
                                            dx * dx + dy * dy
                                        }
                                        if (nearest != null) {
                                            val dist = sqrt((nearest.x - lx) * (nearest.x - lx) + (nearest.y - ly) * (nearest.y - ly))
                                            if (dist < 50f) {
                                                onDeletePuppetPin(activeLayer.id, nearest.id)
                                            }
                                        }
                                    }
                                }
                                else -> {}
                            }
                        } else if (puppetMode == PuppetInteractionMode.MOVE_PIN && activePinId != null && activeLayer != null) {
                            val pin = activeLayer.puppetModifier.pins.find { it.id == activePinId }
                            if (pin != null) {
                                onMovePuppetPin(activeLayer.id, activePinId!!, pin.x, pin.y, true)
                            }
                        }
                        activePinId = null
                        lastBoneTouchAngle = null
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize().testTag("interactive_canvas")) {
            // Photoshop Canvas Workspace Background
            drawRect(Color(0xFF141418))

            translate(left = viewportOffset.x, top = viewportOffset.y) {
                scale(scale = zoomScale, pivot = Offset.Zero) {
                    val halfW = project.canvasWidth / 2f
                    val halfH = project.canvasHeight / 2f
                    val canvasTopLeft = Offset(-halfW, -halfH)
                    val canvasSize = Size(project.canvasWidth, project.canvasHeight)

                    // Render Canvas Background according to ProjectSettings
                    when (project.settings.backgroundMode) {
                        CanvasBackgroundMode.WHITE -> {
                            drawRect(Color.White, topLeft = canvasTopLeft, size = canvasSize)
                        }
                        CanvasBackgroundMode.DARK -> {
                            drawRect(Color(0xFF1E1E24), topLeft = canvasTopLeft, size = canvasSize)
                        }
                        CanvasBackgroundMode.GRAY -> {
                            drawRect(Color(0xFF808080), topLeft = canvasTopLeft, size = canvasSize)
                        }
                        CanvasBackgroundMode.GREEN_SCREEN -> {
                            drawRect(Color(0xFF00FF00), topLeft = canvasTopLeft, size = canvasSize)
                        }
                        CanvasBackgroundMode.TRANSPARENT -> {
                            // Transparent Checkerboard
                            val cellSize = 32f
                            var cx = -halfW
                            var row = 0
                            while (cx < halfW) {
                                var cy = -halfH
                                var col = 0
                                while (cy < halfH) {
                                    val isEven = (row + col) % 2 == 0
                                    val colColor = if (isEven) Color(0xFF2A2A32) else Color(0xFF1E1E24)
                                    val w = cellSize.coerceAtMost(halfW - cx)
                                    val h = cellSize.coerceAtMost(halfH - cy)
                                    drawRect(colColor, topLeft = Offset(cx, cy), size = Size(w, h))
                                    cy += cellSize
                                    col++
                                }
                                cx += cellSize
                                row++
                            }
                        }
                    }

                    // Grid lines
                    if (project.settings.showGrid && project.settings.gridSpacing > 10f) {
                        val spacing = project.settings.gridSpacing
                        val gridColor = Color.White.copy(alpha = 0.05f)

                        var gx = -halfW
                        while (gx <= halfW) {
                            drawLine(gridColor, Offset(gx, -halfH), Offset(gx, halfH), strokeWidth = 1f)
                            gx += spacing
                        }
                        var gy = -halfH
                        while (gy <= halfH) {
                            drawLine(gridColor, Offset(-halfW, gy), Offset(halfW, gy), strokeWidth = 1f)
                            gy += spacing
                        }
                    }

                    // Onion Skinning
                    if (project.timeline.isOnionSkinEnabled && project.timeline.currentFrame > 0) {
                        val onionAlpha = project.timeline.onionSkinOpacity
                        drawCircle(
                            color = Color(0xFFFF5555).copy(alpha = onionAlpha * 0.4f),
                            radius = 40f,
                            center = Offset(-10f, -10f)
                        )
                        drawCircle(
                            color = Color(0xFF33CC66).copy(alpha = onionAlpha * 0.4f),
                            radius = 40f,
                            center = Offset(10f, 10f)
                        )
                    }

                    // Draw all layers from bottom to top
                    project.layers.forEach { layer ->
                        if (layer.isVisible) {
                            drawLayer(
                                layer = layer,
                                isLayerActive = layer.id == project.activeLayerId,
                                isPuppetToolActive = activeTool == ToolType.PUPPET,
                                activePinId = activePinId,
                                selectedBoneId = selectedBoneId,
                                backgroundMode = project.settings.backgroundMode
                            )
                        }
                    }

                    // Canvas Outer Border & Animation Studio Guides
                    // 1. Studio Drop Shadow border
                    drawRect(
                        color = Color.Black.copy(alpha = 0.5f),
                        topLeft = canvasTopLeft - Offset(2f, 2f),
                        size = Size(project.canvasWidth + 4f, project.canvasHeight + 4f),
                        style = Stroke(width = 4f)
                    )
                    // 2. Primary Artboard Border
                    drawRect(
                        color = Color(0xFF3898EC),
                        topLeft = canvasTopLeft,
                        size = canvasSize,
                        style = Stroke(width = 2.5f)
                    )

                    // 3. Animation Camera Safe Guides
                    // 90% Action Safe frame (subtle cyan)
                    val actionSafeW = project.canvasWidth * 0.90f
                    val actionSafeH = project.canvasHeight * 0.90f
                    drawRect(
                        color = Color(0xFF00E5FF).copy(alpha = 0.22f),
                        topLeft = Offset(-actionSafeW / 2f, -actionSafeH / 2f),
                        size = Size(actionSafeW, actionSafeH),
                        style = Stroke(width = 1.2f)
                    )
                    // 80% Title Safe frame (subtle gold)
                    val titleSafeW = project.canvasWidth * 0.80f
                    val titleSafeH = project.canvasHeight * 0.80f
                    drawRect(
                        color = Color(0xFFFFB300).copy(alpha = 0.18f),
                        topLeft = Offset(-titleSafeW / 2f, -titleSafeH / 2f),
                        size = Size(titleSafeW, titleSafeH),
                        style = Stroke(width = 1f)
                    )

                    // 4. Center Crosshair (+)
                    val chSize = 16f
                    drawLine(Color(0xFF3898EC).copy(alpha = 0.4f), Offset(-chSize, 0f), Offset(chSize, 0f), strokeWidth = 1.5f)
                    drawLine(Color(0xFF3898EC).copy(alpha = 0.4f), Offset(0f, -chSize), Offset(0f, chSize), strokeWidth = 1.5f)
                    drawCircle(Color(0xFF3898EC).copy(alpha = 0.25f), radius = 6f, center = Offset.Zero, style = Stroke(1f))

                    // 5. Active Layer Transform Frame (when in SELECT_MOVE tool)
                    if (activeTool == ToolType.SELECT_MOVE && activeLayer != null) {
                        val lx = activeLayer.transform.translationX
                        val ly = activeLayer.transform.translationY
                        val boxSize = 180f
                        val halfBox = boxSize / 2f

                        // Bounding Box
                        drawRect(
                            color = Color(0xFF00E5FF).copy(alpha = 0.85f),
                            topLeft = Offset(lx - halfBox, ly - halfBox),
                            size = Size(boxSize, boxSize),
                            style = Stroke(width = 1.8f)
                        )
                        // Corner Handles
                        val handleRadius = 5f
                        listOf(
                            Offset(lx - halfBox, ly - halfBox),
                            Offset(lx + halfBox, ly - halfBox),
                            Offset(lx + halfBox, ly + halfBox),
                            Offset(lx - halfBox, ly + halfBox)
                        ).forEach { pt ->
                            drawCircle(Color.White, radius = handleRadius, center = pt)
                            drawCircle(Color(0xFF00E5FF), radius = handleRadius, center = pt, style = Stroke(1.5f))
                        }
                        // Rotation Pivot Indicator
                        drawCircle(Color(0xFFFFB300), radius = 4f, center = Offset(lx, ly))
                        drawCircle(Color.White, radius = 7f, center = Offset(lx, ly), style = Stroke(1.2f))
                    }
                }
            }

            // Canvas Rulers
            if (project.settings.showRulers) {
                drawRulers(viewportOffset, zoomScale, project.settings.unit)
            }
        }

        // Floating Canvas Controls: Free Zoom (+, -, 100%, Reset) and 3 Background Modes (سفید / سیاه / بدون رنگ شطرنجی)
        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 12.dp)
                .testTag("canvas_hud_controls"),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f),
            tonalElevation = 6.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Zoom Out Button
                IconButton(
                    onClick = { onZoomScaleChange((zoomScale / 1.3f).coerceIn(0.02f, 30.0f)) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "کوچک‌نمایی آزاد", modifier = Modifier.size(16.dp))
                }

                // Zoom Level Display (Click to reset to 100%)
                Text(
                    text = "${(zoomScale * 100).toInt()}%",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = StudioAccent,
                    modifier = Modifier
                        .clickable {
                            onZoomScaleChange(1.0f)
                            onViewportOffsetChange(Offset.Zero)
                        }
                        .padding(horizontal = 4.dp)
                )

                // Zoom In Button
                IconButton(
                    onClick = { onZoomScaleChange((zoomScale * 1.3f).coerceIn(0.02f, 30.0f)) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "بزرگ‌نمایی آزاد", modifier = Modifier.size(16.dp))
                }

                // Reset to Center 100%
                IconButton(
                    onClick = {
                        onZoomScaleChange(1.0f)
                        onViewportOffsetChange(Offset.Zero)
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.CenterFocusStrong, contentDescription = "تنظیم وسط و ۱۰۰٪", modifier = Modifier.size(16.dp))
                }

                Box(
                    modifier = Modifier
                        .height(20.dp)
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                )

                // 3 Background Mode Buttons: White (سفید), Dark (سیاه), Transparent (بدون رنگ)
                val currentBgMode = project.settings.backgroundMode

                FilterChip(
                    selected = currentBgMode == CanvasBackgroundMode.WHITE,
                    onClick = { onBackgroundModeChange?.invoke(CanvasBackgroundMode.WHITE) },
                    label = { Text("سفید", fontSize = 10.sp) },
                    modifier = Modifier.height(26.dp)
                )

                FilterChip(
                    selected = currentBgMode == CanvasBackgroundMode.DARK,
                    onClick = { onBackgroundModeChange?.invoke(CanvasBackgroundMode.DARK) },
                    label = { Text("سیاه", fontSize = 10.sp) },
                    modifier = Modifier.height(26.dp)
                )

                FilterChip(
                    selected = currentBgMode == CanvasBackgroundMode.TRANSPARENT,
                    onClick = { onBackgroundModeChange?.invoke(CanvasBackgroundMode.TRANSPARENT) },
                    label = { Text("بدون رنگ 🏁", fontSize = 10.sp) },
                    modifier = Modifier.height(26.dp)
                )
            }
        }
    }
}

private fun DrawScope.drawLayer(
    layer: Layer,
    isLayerActive: Boolean,
    isPuppetToolActive: Boolean,
    activePinId: String?,
    selectedBoneId: String?,
    backgroundMode: CanvasBackgroundMode
) {
    val composeBlendMode = when (layer.blendMode) {
        BlendModeType.NORMAL -> BlendMode.SrcOver
        BlendModeType.MULTIPLY -> BlendMode.Multiply
        BlendModeType.SCREEN -> BlendMode.Screen
        BlendModeType.OVERLAY -> BlendMode.Overlay
        BlendModeType.DARKEN -> BlendMode.Darken
        BlendModeType.LIGHTEN -> BlendMode.Lighten
        BlendModeType.COLOR_DODGE -> BlendMode.ColorDodge
        BlendModeType.COLOR_BURN -> BlendMode.ColorBurn
        BlendModeType.HARD_LIGHT -> BlendMode.Hardlight
        BlendModeType.SOFT_LIGHT -> BlendMode.Softlight
        BlendModeType.DIFFERENCE -> BlendMode.Difference
        BlendModeType.EXCLUSION -> BlendMode.Exclusion
        BlendModeType.HUE -> BlendMode.Hue
        BlendModeType.SATURATION -> BlendMode.Saturation
        BlendModeType.COLOR -> BlendMode.Color
        BlendModeType.LUMINOSITY -> BlendMode.Luminosity
    }

    val canvasEraserColor = when (backgroundMode) {
        CanvasBackgroundMode.WHITE -> Color.White
        CanvasBackgroundMode.DARK -> Color(0xFF1E1E24)
        CanvasBackgroundMode.GRAY -> Color(0xFF808080)
        CanvasBackgroundMode.GREEN_SCREEN -> Color(0xFF00FF00)
        CanvasBackgroundMode.TRANSPARENT -> Color.Transparent
    }

    translate(left = layer.transform.translationX, top = layer.transform.translationY) {
        rotate(degrees = layer.transform.rotation, pivot = Offset.Zero) {
            scale(scaleX = layer.transform.scaleX, scaleY = layer.transform.scaleY, pivot = Offset.Zero) {

                val puppet = layer.puppetModifier
                val hasDeformers = puppet.bones.isNotEmpty() || puppet.pins.isNotEmpty() || puppet.deformerType != InvisibleDeformerType.NONE || puppet.deformerPitch != 0f || puppet.deformerYaw != 0f

                // 1. Draw Raster Strokes with smooth Bézier interpolation, 3D volume, & Puppet Deform
                for (stroke in layer.rasterStrokes) {
                    if (stroke.points.isEmpty()) continue

                    val strokeColor = if (stroke.isEraser) {
                        canvasEraserColor
                    } else {
                        Color(stroke.color).copy(alpha = layer.opacity)
                    }

                    // Map stroke points through unified puppet deform if bones, pins or deformer exist
                    val effectivePoints = if (hasDeformers) {
                        stroke.points.map { pt ->
                            PuppetWarpEngine.deformPointUnified(
                                origX = pt.x,
                                origY = pt.y,
                                bones = puppet.bones,
                                pins = puppet.pins,
                                deformerType = puppet.deformerType,
                                deformerRotation = puppet.deformerRotation,
                                deformerPitch = puppet.deformerPitch,
                                deformerYaw = puppet.deformerYaw,
                                deformerRadius = puppet.deformerRadius,
                                volumeContour = puppet.volumeContour
                            )
                        }
                    } else {
                        stroke.points
                    }

                    if (effectivePoints.size == 1) {
                        val p = effectivePoints[0]
                        drawCircle(
                            color = strokeColor,
                            radius = (stroke.strokeWidth / 2f).coerceAtLeast(2f),
                            center = Offset(p.x, p.y),
                            blendMode = if (stroke.isEraser && backgroundMode == CanvasBackgroundMode.TRANSPARENT) BlendMode.Clear else composeBlendMode
                        )
                    } else {
                        val path = Path()
                        path.moveTo(effectivePoints[0].x, effectivePoints[0].y)

                        // Smooth quadratic Bézier mid-point smoothing
                        for (i in 1 until effectivePoints.size) {
                            val prev = effectivePoints[i - 1]
                            val curr = effectivePoints[i]
                            val midX = (prev.x + curr.x) / 2f
                            val midY = (prev.y + curr.y) / 2f
                            path.quadraticBezierTo(prev.x, prev.y, midX, midY)
                        }
                        path.lineTo(effectivePoints.last().x, effectivePoints.last().y)

                        if (stroke.is3D) {
                            // 3D Volumetric Extrusion multi-pass rendering
                            val rad = Math.toRadians(stroke.depthAngle.toDouble())
                            val depthPx = stroke.strokeWidth.coerceIn(4f, 30f)
                            val offX = (cos(rad) * depthPx * 0.5f).toFloat()
                            val offY = (sin(rad) * depthPx * 0.5f).toFloat()

                            // Base shadow extrusion
                            val shadowColor = Color.Black.copy(alpha = 0.45f * layer.opacity)
                            translate(left = offX, top = offY) {
                                drawPath(
                                    path = path,
                                    color = shadowColor,
                                    style = Stroke(
                                        width = stroke.strokeWidth * 1.15f,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }

                            // Secondary depth layer
                            translate(left = offX * 0.5f, top = offY * 0.5f) {
                                drawPath(
                                    path = path,
                                    color = strokeColor.copy(alpha = 0.85f * layer.opacity),
                                    style = Stroke(
                                        width = stroke.strokeWidth,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }

                            // Main surface stroke
                            drawPath(
                                path = path,
                                color = strokeColor,
                                style = Stroke(
                                    width = stroke.strokeWidth,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                ),
                                blendMode = composeBlendMode
                            )

                            // Material Highlights (CLAY, METALLIC, GLOSS)
                            when (stroke.materialPreset) {
                                "METALLIC" -> {
                                    // High-specular central shine highlight
                                    drawPath(
                                        path = path,
                                        color = Color.White.copy(alpha = 0.7f * layer.opacity),
                                        style = Stroke(
                                            width = (stroke.strokeWidth * 0.25f).coerceAtLeast(1.5f),
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round
                                        )
                                    )
                                }
                                "GLOSS" -> {
                                    // Soft glassy glint
                                    drawPath(
                                        path = path,
                                        color = Color.White.copy(alpha = 0.45f * layer.opacity),
                                        style = Stroke(
                                            width = (stroke.strokeWidth * 0.35f).coerceAtLeast(2f),
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round
                                        )
                                    )
                                }
                                "CLAY" -> {
                                    // Warm matte top diffuse
                                    drawPath(
                                        path = path,
                                        color = strokeColor.copy(alpha = 0.3f),
                                        style = Stroke(
                                            width = (stroke.strokeWidth * 0.6f).coerceAtLeast(2f),
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round
                                        )
                                    )
                                }
                            }
                        } else {
                            // Standard 2D brush stroke
                            drawPath(
                                path = path,
                                color = strokeColor,
                                style = Stroke(
                                    width = stroke.strokeWidth,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                ),
                                blendMode = if (stroke.isEraser && backgroundMode == CanvasBackgroundMode.TRANSPARENT) BlendMode.Clear else composeBlendMode
                            )
                        }
                    }
                }

                // 2. Draw Vector Shapes (deformed with bones/pins if present)
                layer.shapeData?.let { shape ->
                    val color = Color(shape.fillColor).copy(alpha = layer.opacity)
                    val strokeColor = Color(shape.strokeColor).copy(alpha = layer.opacity)
                    val halfW = shape.width / 2f
                    val halfH = shape.height / 2f

                    fun deformPt(x: Float, y: Float): Offset {
                        if (!hasDeformers) return Offset(x, y)
                        val dp = PuppetWarpEngine.deformPointUnified(
                            origX = x,
                            origY = y,
                            bones = puppet.bones,
                            pins = puppet.pins,
                            deformerType = puppet.deformerType,
                            deformerRotation = puppet.deformerRotation,
                            deformerPitch = puppet.deformerPitch,
                            deformerYaw = puppet.deformerYaw,
                            deformerRadius = puppet.deformerRadius,
                            volumeContour = puppet.volumeContour
                        )
                        return Offset(dp.x, dp.y)
                    }

                    when (shape.shapeType) {
                        ShapeType.RECT -> {
                            val path = Path()
                            val perimeter = mutableListOf<Offset>()
                            for (i in 0 until 4) perimeter.add(deformPt(-halfW + (shape.width * i / 4f), -halfH))
                            for (i in 0 until 4) perimeter.add(deformPt(halfW, -halfH + (shape.height * i / 4f)))
                            for (i in 0 until 4) perimeter.add(deformPt(halfW - (shape.width * i / 4f), halfH))
                            for (i in 0 until 4) perimeter.add(deformPt(-halfW, halfH - (shape.height * i / 4f)))

                            path.moveTo(perimeter[0].x, perimeter[0].y)
                            for (i in 1 until perimeter.size) path.lineTo(perimeter[i].x, perimeter[i].y)
                            path.close()

                            if (shape.isFilled) drawPath(path, color, blendMode = composeBlendMode)
                            drawPath(path, strokeColor, style = Stroke(shape.strokeWidth), blendMode = composeBlendMode)
                        }
                        ShapeType.CIRCLE -> {
                            val path = Path()
                            val segments = 24
                            for (i in 0 until segments) {
                                val angle = i * 2.0 * Math.PI / segments
                                val x = (shape.radius * cos(angle)).toFloat()
                                val y = (shape.radius * sin(angle)).toFloat()
                                val pt = deformPt(x, y)
                                if (i == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
                            }
                            path.close()
                            if (shape.isFilled) drawPath(path, color, blendMode = composeBlendMode)
                            drawPath(path, strokeColor, style = Stroke(shape.strokeWidth), blendMode = composeBlendMode)
                        }
                        ShapeType.TRIANGLE -> {
                            val p0 = deformPt(0f, -halfH)
                            val p1 = deformPt(halfW, halfH)
                            val p2 = deformPt(-halfW, halfH)
                            val path = Path().apply {
                                moveTo(p0.x, p0.y)
                                lineTo(p1.x, p1.y)
                                lineTo(p2.x, p2.y)
                                close()
                            }
                            if (shape.isFilled) drawPath(path, color, blendMode = composeBlendMode)
                            drawPath(path, strokeColor, style = Stroke(shape.strokeWidth), blendMode = composeBlendMode)
                        }
                        ShapeType.STAR -> {
                            val path = Path()
                            val points = 5
                            val outerRadius = shape.radius
                            val innerRadius = shape.radius * 0.45f
                            for (i in 0 until points * 2) {
                                val r = if (i % 2 == 0) outerRadius else innerRadius
                                val angle = i * Math.PI / points - Math.PI / 2
                                val x = (r * cos(angle)).toFloat()
                                val y = (r * sin(angle)).toFloat()
                                val pt = deformPt(x, y)
                                if (i == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
                            }
                            path.close()
                            if (shape.isFilled) drawPath(path, color, blendMode = composeBlendMode)
                            drawPath(path, strokeColor, style = Stroke(shape.strokeWidth), blendMode = composeBlendMode)
                        }
                        ShapeType.CUBE_25D -> {
                            val w = shape.width * 0.7f
                            val h = shape.height * 0.7f
                            val depth = 45f

                            val c0 = deformPt(-w / 2f, -h / 2f + depth / 2f)
                            val c1 = deformPt(w / 2f, -h / 2f + depth / 2f)
                            val c2 = deformPt(w / 2f, h / 2f + depth / 2f)
                            val c3 = deformPt(-w / 2f, h / 2f + depth / 2f)
                            val c4 = deformPt(0f, -h / 2f - depth / 2f)
                            val c5 = deformPt(w, -h / 2f - depth / 2f)

                            val frontPath = Path().apply {
                                moveTo(c0.x, c0.y)
                                lineTo(c1.x, c1.y)
                                lineTo(c2.x, c2.y)
                                lineTo(c3.x, c3.y)
                                close()
                            }
                            val topPath = Path().apply {
                                moveTo(c0.x, c0.y)
                                lineTo(c4.x, c4.y)
                                lineTo(c5.x, c5.y)
                                lineTo(c1.x, c1.y)
                                close()
                            }
                            val sidePath = Path().apply {
                                moveTo(c1.x, c1.y)
                                lineTo(c5.x, c5.y)
                                lineTo(deformPt(w, h / 2f - depth / 2f).x, deformPt(w, h / 2f - depth / 2f).y)
                                lineTo(c2.x, c2.y)
                                close()
                            }

                            if (shape.isFilled) {
                                drawPath(frontPath, color, blendMode = composeBlendMode)
                                drawPath(topPath, color.copy(alpha = (layer.opacity * 0.8f).coerceIn(0f, 1f)), blendMode = composeBlendMode)
                                drawPath(sidePath, color.copy(alpha = (layer.opacity * 0.6f).coerceIn(0f, 1f)), blendMode = composeBlendMode)
                            }
                            drawPath(frontPath, strokeColor, style = Stroke(2f), blendMode = composeBlendMode)
                            drawPath(topPath, strokeColor, style = Stroke(2f), blendMode = composeBlendMode)
                            drawPath(sidePath, strokeColor, style = Stroke(2f), blendMode = composeBlendMode)
                        }
                    }
                }

                // 3. Draw Text
                layer.textData?.let { textData ->
                    drawContext.canvas.nativeCanvas.apply {
                        val paint = Paint().apply {
                            this.color = textData.textColor.toInt()
                            this.textSize = textData.fontSize
                            this.textAlign = Paint.Align.CENTER
                            this.isAntiAlias = true
                            this.typeface = Typeface.DEFAULT_BOLD
                        }
                        if (textData.mode == TextMode.EXTRUDE_3D) {
                            val shadowPaint = Paint(paint).apply {
                                this.color = 0xFF101015.toInt()
                            }
                            for (depth in 1..8) {
                                drawText(textData.text, depth.toFloat(), depth.toFloat(), shadowPaint)
                            }
                        }
                        drawText(textData.text, 0f, 0f, paint)
                    }
                }

                // 4. Puppet Deform Mesh (Linear Blend Skinning + Pins/Invisible Deformers)
                val deformedVertices = if (puppet.bones.isNotEmpty()) {
                    val lbsVertices = PuppetWarpEngine.deformMeshWithBones(
                        originalVertices = puppet.mesh.originalVertices,
                        boneWeightsMap = puppet.boneWeights,
                        bones = puppet.bones
                    )
                    if (puppet.pins.isNotEmpty() || puppet.deformerType != InvisibleDeformerType.NONE) {
                        val tempMesh = puppet.mesh.copy(originalVertices = lbsVertices)
                        PuppetWarpEngine.deformMesh(
                            originalMesh = tempMesh,
                            pins = puppet.pins,
                            deformerType = puppet.deformerType,
                            deformerRotation = puppet.deformerRotation,
                            deformerRadius = puppet.deformerRadius,
                            volumeContour = puppet.volumeContour
                        )
                    } else {
                        lbsVertices
                    }
                } else {
                    PuppetWarpEngine.deformMesh(
                        originalMesh = puppet.mesh,
                        pins = puppet.pins,
                        deformerType = puppet.deformerType,
                        deformerRotation = puppet.deformerRotation,
                        deformerRadius = puppet.deformerRadius,
                        volumeContour = puppet.volumeContour
                    )
                }

                // Wireframe mesh lines
                if (puppet.showMesh && isPuppetToolActive && isLayerActive && deformedVertices.isNotEmpty()) {
                    for (tri in puppet.mesh.triangles) {
                        if (tri.p0 < deformedVertices.size && tri.p1 < deformedVertices.size && tri.p2 < deformedVertices.size) {
                            val v0 = deformedVertices[tri.p0]
                            val v1 = deformedVertices[tri.p1]
                            val v2 = deformedVertices[tri.p2]

                            val triPath = Path().apply {
                                moveTo(v0.x, v0.y)
                                lineTo(v1.x, v1.y)
                                lineTo(v2.x, v2.y)
                                close()
                            }
                            drawPath(
                                path = triPath,
                                color = StudioAccent.copy(alpha = 0.25f),
                                style = Stroke(width = 1f)
                            )
                        }
                    }
                }

                // 5. Draw Skeletal Rig Bones (Hierarchical Joint System)
                if (puppet.showBones && (isPuppetToolActive || puppet.bones.isNotEmpty())) {
                    for (bone in puppet.bones) {
                        val isSelected = bone.id == selectedBoneId
                        val startOffset = Offset(bone.globalStartX, bone.globalStartY)
                        val endOffset = Offset(bone.globalEndX, bone.globalEndY)

                        // Glowing highlight for selected bone
                        if (isSelected) {
                            drawLine(
                                color = Color(0xFFFFD54F).copy(alpha = 0.45f),
                                start = startOffset,
                                end = endOffset,
                                strokeWidth = 9.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }

                        // Main Bone Link
                        drawLine(
                            color = if (isSelected) Color(0xFFFFD54F) else Color(bone.color),
                            start = startOffset,
                            end = endOffset,
                            strokeWidth = if (isSelected) 4.5.dp.toPx() else 3.dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        // Joint Controller Shape (Square vs Circle controller matching image)
                        if (bone.controlShape == BoneControlShape.SQUARE) {
                            val sqSize = if (isSelected) 22f else 18f
                            val rectTopLeft = Offset(bone.globalStartX - sqSize / 2f, bone.globalStartY - sqSize / 2f)
                            val rectSize = Size(sqSize, sqSize)

                            drawRect(
                                color = if (isSelected) Color(0xFFFFD54F).copy(alpha = 0.35f) else Color(bone.color).copy(alpha = 0.25f),
                                topLeft = rectTopLeft,
                                size = rectSize
                            )
                            drawRect(
                                color = if (isSelected) Color(0xFFFFD54F) else Color(bone.color),
                                topLeft = rectTopLeft,
                                size = rectSize,
                                style = Stroke(width = 2.5f)
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 3.5f,
                                center = startOffset
                            )
                        } else {
                            val r = if (isSelected) 14f else 11f
                            drawCircle(
                                color = if (isSelected) Color(0xFFFFD54F).copy(alpha = 0.35f) else Color(bone.color).copy(alpha = 0.25f),
                                radius = r,
                                center = endOffset
                            )
                            drawCircle(
                                color = if (isSelected) Color(0xFFFFD54F) else Color(bone.color),
                                radius = r,
                                center = endOffset,
                                style = Stroke(width = 2.5f)
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 3.5f,
                                center = endOffset
                            )
                        }

                        // Name label for active bone
                        if (isSelected && isPuppetToolActive) {
                            drawContext.canvas.nativeCanvas.apply {
                                val labelPaint = Paint().apply {
                                    color = 0xFFFFD54F.toInt()
                                    textSize = 12.dp.toPx()
                                    isAntiAlias = true
                                    typeface = Typeface.DEFAULT_BOLD
                                }
                                val midX = (bone.globalStartX + bone.globalEndX) / 2f + 14f
                                val midY = (bone.globalStartY + bone.globalEndY) / 2f
                                drawText("${bone.name} (${bone.angle.toInt()}°)", midX, midY, labelPaint)
                            }
                        }
                    }
                }

                // 6. Draw Interactive Puppet Pins
                if (isPuppetToolActive && isLayerActive) {
                    for (pin in puppet.pins) {
                        val isSelected = pin.id == activePinId
                        val pinColor = when (pin.pinType) {
                            PinType.STATIC -> Color(0xFF3898EC)
                            PinType.DYNAMIC -> StudioAccentAmber
                            PinType.ROTATION -> Color(0xFF26A69A)
                            PinType.SCALE -> Color(0xFFE91E63)
                            PinType.CONTROLLER -> Color(0xFFAB47BC)
                        }

                        // Influence radius halo
                        drawCircle(
                            color = pinColor.copy(alpha = if (isSelected) 0.35f else 0.12f),
                            radius = pin.radius,
                            center = Offset(pin.x, pin.y)
                        )

                        // Outer ring
                        drawCircle(
                            color = if (isSelected) Color.White else pinColor,
                            radius = if (isSelected) 13f else 10f,
                            center = Offset(pin.x, pin.y),
                            style = Stroke(width = 2.5f)
                        )

                        // Core Pin bead
                        drawCircle(
                            color = pinColor,
                            radius = if (isSelected) 8f else 6f,
                            center = Offset(pin.x, pin.y)
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawRulers(offset: Offset, zoom: Float, unit: RulerUnit) {
    val rulerWidth = 24.dp.toPx()
    val bg = Color(0xFF1B1B20)
    val textPaint = Paint().apply {
        color = 0xFF888899.toInt()
        textSize = 10.dp.toPx()
        isAntiAlias = true
    }

    // Top horizontal ruler
    drawRect(bg, topLeft = Offset.Zero, size = Size(size.width, rulerWidth))
    // Left vertical ruler
    drawRect(bg, topLeft = Offset.Zero, size = Size(rulerWidth, size.height))

    val step = 100f * zoom
    var curX = (offset.x % step)
    while (curX < size.width) {
        if (curX > rulerWidth) {
            drawLine(
                Color.White.copy(alpha = 0.25f),
                Offset(curX, rulerWidth - 8f),
                Offset(curX, rulerWidth),
                strokeWidth = 1f
            )
        }
        curX += step
    }

    var curY = (offset.y % step)
    while (curY < size.height) {
        if (curY > rulerWidth) {
            drawLine(
                Color.White.copy(alpha = 0.25f),
                Offset(rulerWidth - 8f, curY),
                Offset(rulerWidth, curY),
                strokeWidth = 1f
            )
        }
        curY += step
    }

    // Corner box
    drawRect(Color(0xFF24242C), topLeft = Offset.Zero, size = Size(rulerWidth, rulerWidth))
}
