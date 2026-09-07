package com.example.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
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
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.testTag
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
    modifier: Modifier = Modifier
) {
    var activePinId by remember { mutableStateOf<String?>(null) }
    var moveStartOffset by remember { mutableStateOf(Offset.Zero) }
    var initialTranslation by remember { mutableStateOf(Offset.Zero) }
    var lastStrokePoint by remember { mutableStateOf<PointData?>(null) }

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
                    }

                    // Initialize puppet pin selection
                    if (activeTool == ToolType.PUPPET && activeLayer != null) {
                        val lx = (down.position.x - viewportOffset.x) / zoomScale - activeLayer.transform.translationX
                        val ly = (down.position.y - viewportOffset.y) / zoomScale - activeLayer.transform.translationY
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

                    // Brush / Eraser start point
                    if ((activeTool == ToolType.BRUSH || activeTool == ToolType.ERASER) && activeLayer != null) {
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
                                onZoomScaleChange((zoomScale * zoom).coerceIn(0.15f, 6.0f))
                            }
                            event.changes.forEach { it.consume() }
                        } else if (pressedPointers.size == 1 && !isTwoFingerTransform) {
                            val change = pressedPointers.first()
                            val currentPos = change.position
                            val deltaPos = currentPos - startPos
                            totalDragDistance += sqrt(deltaPos.x * deltaPos.x + deltaPos.y * deltaPos.y)

                            when (activeTool) {
                                ToolType.BRUSH, ToolType.ERASER -> {
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
                                        onAddStrokePoint(targetPt, isEraser)
                                        lastStrokePoint = targetPt
                                        change.consume()
                                    }
                                }

                                ToolType.SELECT_MOVE -> {
                                    if (activeLayer != null) {
                                        val deltaX = (currentPos.x - moveStartOffset.x) / zoomScale
                                        val deltaY = (currentPos.y - moveStartOffset.y) / zoomScale
                                        val newTransform = activeLayer.transform.copy(
                                            translationX = initialTranslation.x + deltaX,
                                            translationY = initialTranslation.y + deltaY
                                        )
                                        onUpdateLayerTransform(activeLayer.id, newTransform, false)
                                        change.consume()
                                    }
                                }

                                ToolType.PUPPET -> {
                                    if (puppetMode == PuppetInteractionMode.MOVE_PIN && activeLayer != null && activePinId != null) {
                                        val lx = (currentPos.x - viewportOffset.x) / zoomScale - activeLayer.transform.translationX
                                        val ly = (currentPos.y - viewportOffset.y) / zoomScale - activeLayer.transform.translationY
                                        onMovePuppetPin(activeLayer.id, activePinId!!, lx, ly, false)
                                        change.consume()
                                    }
                                }

                                ToolType.HAND, ToolType.ZOOM -> {
                                    val panDelta = change.positionChange()
                                    if (panDelta != Offset.Zero) {
                                        onViewportOffsetChange(viewportOffset + panDelta)
                                        change.consume()
                                    }
                                }

                                else -> {}
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    // Gesture completion / finger up
                    if (activeTool == ToolType.BRUSH || activeTool == ToolType.ERASER) {
                        onFinishStroke()
                        lastStrokePoint = null
                    } else if (activeTool == ToolType.SELECT_MOVE && activeLayer != null) {
                        onUpdateLayerTransform(activeLayer.id, activeLayer.transform, true)
                    } else if (activeTool == ToolType.PUPPET && activeLayer != null) {
                        val lx = (down.position.x - viewportOffset.x) / zoomScale - activeLayer.transform.translationX
                        val ly = (down.position.y - viewportOffset.y) / zoomScale - activeLayer.transform.translationY

                        if (totalDragDistance < 15f) {
                            // Tap event on Puppet Canvas
                            when (puppetMode) {
                                PuppetInteractionMode.ADD_PIN -> {
                                    onAddPuppetPin(activeLayer.id, lx, ly)
                                }
                                PuppetInteractionMode.DELETE_PIN -> {
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
                                else -> {}
                            }
                        } else if (puppetMode == PuppetInteractionMode.MOVE_PIN && activePinId != null) {
                            val pin = activeLayer.puppetModifier.pins.find { it.id == activePinId }
                            if (pin != null) {
                                onMovePuppetPin(activeLayer.id, activePinId!!, pin.x, pin.y, true)
                            }
                        }
                        activePinId = null
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize().testTag("interactive_canvas")) {
            // Photoshop Canvas Background
            drawRect(Color(0xFF141418))

            translate(left = viewportOffset.x, top = viewportOffset.y) {
                scale(scale = zoomScale, pivot = Offset.Zero) {
                    // Canvas bounds rectangle
                    drawRect(
                        color = Color(0xFF1E1E24),
                        topLeft = Offset(-project.canvasWidth / 2f, -project.canvasHeight / 2f),
                        size = Size(project.canvasWidth, project.canvasHeight)
                    )

                    // Grid lines
                    if (project.settings.showGrid && project.settings.gridSpacing > 10f) {
                        val spacing = project.settings.gridSpacing
                        val halfW = project.canvasWidth / 2f
                        val halfH = project.canvasHeight / 2f
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
                                activePinId = activePinId
                            )
                        }
                    }

                    // Canvas Outer Border
                    drawRect(
                        color = Color(0xFF3898EC).copy(alpha = 0.5f),
                        topLeft = Offset(-project.canvasWidth / 2f, -project.canvasHeight / 2f),
                        size = Size(project.canvasWidth, project.canvasHeight),
                        style = Stroke(width = 2f)
                    )
                }
            }

            // Canvas Rulers
            if (project.settings.showRulers) {
                drawRulers(viewportOffset, zoomScale, project.settings.unit)
            }
        }
    }
}

private fun DrawScope.drawLayer(
    layer: Layer,
    isLayerActive: Boolean,
    isPuppetToolActive: Boolean,
    activePinId: String?
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

    translate(left = layer.transform.translationX, top = layer.transform.translationY) {
        rotate(degrees = layer.transform.rotation, pivot = Offset.Zero) {
            scale(scaleX = layer.transform.scaleX, scaleY = layer.transform.scaleY, pivot = Offset.Zero) {

                val puppet = layer.puppetModifier
                val hasPins = puppet.pins.isNotEmpty() || puppet.deformerType != InvisibleDeformerType.NONE

                // 1. Draw Raster Strokes with smooth Bézier interpolation & Puppet Deform
                for (stroke in layer.rasterStrokes) {
                    if (stroke.points.isEmpty()) continue

                    val strokeColor = if (stroke.isEraser) Color(0xFF1E1E24) else Color(stroke.color).copy(alpha = layer.opacity)

                    // Map stroke points through puppet deform if pins exist
                    val effectivePoints = if (hasPins) {
                        stroke.points.map { pt ->
                            PuppetWarpEngine.deformPoint(
                                origX = pt.x,
                                origY = pt.y,
                                pins = puppet.pins,
                                deformerType = puppet.deformerType,
                                deformerRotation = puppet.deformerRotation,
                                deformerRadius = puppet.deformerRadius
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
                            blendMode = composeBlendMode
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
                    }
                }

                // 2. Draw Vector Shapes
                layer.shapeData?.let { shape ->
                    val color = Color(shape.fillColor).copy(alpha = layer.opacity)
                    val strokeColor = Color(shape.strokeColor).copy(alpha = layer.opacity)
                    val halfW = shape.width / 2f
                    val halfH = shape.height / 2f

                    when (shape.shapeType) {
                        ShapeType.RECT -> {
                            if (shape.isFilled) {
                                drawRect(color, topLeft = Offset(-halfW, -halfH), size = Size(shape.width, shape.height), blendMode = composeBlendMode)
                            }
                            drawRect(strokeColor, topLeft = Offset(-halfW, -halfH), size = Size(shape.width, shape.height), style = Stroke(shape.strokeWidth), blendMode = composeBlendMode)
                        }
                        ShapeType.CIRCLE -> {
                            if (shape.isFilled) {
                                drawCircle(color, radius = shape.radius, center = Offset.Zero, blendMode = composeBlendMode)
                            }
                            drawCircle(strokeColor, radius = shape.radius, center = Offset.Zero, style = Stroke(shape.strokeWidth), blendMode = composeBlendMode)
                        }
                        ShapeType.TRIANGLE -> {
                            val path = Path().apply {
                                moveTo(0f, -halfH)
                                lineTo(halfW, halfH)
                                lineTo(-halfW, halfH)
                                close()
                            }
                            if (shape.isFilled) drawPath(path, color, blendMode = composeBlendMode)
                            drawPath(path, strokeColor, style = Stroke(shape.strokeWidth), blendMode = composeBlendMode)
                        }
                        ShapeType.STAR -> {
                            val path = Path()
                            val outerR = shape.radius
                            val innerR = outerR * 0.45f
                            for (i in 0 until 10) {
                                val r = if (i % 2 == 0) outerR else innerR
                                val angle = Math.toRadians((i * 36 - 90).toDouble())
                                val x = (r * cos(angle)).toFloat()
                                val y = (r * sin(angle)).toFloat()
                                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            path.close()
                            if (shape.isFilled) drawPath(path, color, blendMode = composeBlendMode)
                            drawPath(path, strokeColor, style = Stroke(shape.strokeWidth), blendMode = composeBlendMode)
                        }
                        ShapeType.CUBE_25D -> {
                            val frontPath = Path().apply {
                                moveTo(-50f, -20f); lineTo(50f, -20f); lineTo(50f, 60f); lineTo(-50f, 60f); close()
                            }
                            val topPath = Path().apply {
                                moveTo(-50f, -20f); lineTo(0f, -55f); lineTo(100f, -55f); lineTo(50f, -20f); close()
                            }
                            val sidePath = Path().apply {
                                moveTo(50f, -20f); lineTo(100f, -55f); lineTo(100f, 25f); lineTo(50f, 60f); close()
                            }
                            drawPath(frontPath, color, blendMode = composeBlendMode)
                            drawPath(topPath, color.copy(alpha = (layer.opacity * 0.8f).coerceIn(0f, 1f)), blendMode = composeBlendMode)
                            drawPath(sidePath, color.copy(alpha = (layer.opacity * 0.6f).coerceIn(0f, 1f)), blendMode = composeBlendMode)
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
                            for (depth in 1..6) {
                                drawText(textData.text, depth.toFloat(), depth.toFloat(), shadowPaint)
                            }
                        }
                        drawText(textData.text, 0f, 0f, paint)
                    }
                }

                // 4. Puppet Deform Mesh & Pins
                val deformedVertices = PuppetWarpEngine.deformMesh(
                    originalMesh = puppet.mesh,
                    pins = puppet.pins,
                    deformerType = puppet.deformerType,
                    deformerRotation = puppet.deformerRotation,
                    deformerRadius = puppet.deformerRadius
                )

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
                                color = StudioAccent.copy(alpha = 0.22f),
                                style = Stroke(width = 1f)
                            )
                        }
                    }
                }

                // Draw Puppet Pins
                if (isPuppetToolActive && isLayerActive) {
                    for (pin in puppet.pins) {
                        val isSelected = pin.id == activePinId
                        val pinColor = if (isSelected) StudioAccentAmber else StudioAccent

                        // Influence radius ring
                        drawCircle(
                            color = pinColor.copy(alpha = 0.12f),
                            radius = pin.radius,
                            center = Offset(pin.x, pin.y)
                        )
                        drawCircle(
                            color = pinColor.copy(alpha = 0.35f),
                            radius = pin.radius,
                            center = Offset(pin.x, pin.y),
                            style = Stroke(width = 1f)
                        )

                        // Z-Depth cue ring
                        if (pin.depth != 0f) {
                            drawCircle(
                                color = Color(0xFF6C5CE7).copy(alpha = 0.5f),
                                radius = pin.radius * (1f + (pin.depth / 100f).coerceIn(-0.5f, 0.5f)),
                                center = Offset(pin.x, pin.y),
                                style = Stroke(width = 1.5f)
                            )
                        }

                        // Pin Core & Center indicator
                        drawCircle(
                            color = Color.Black.copy(alpha = 0.5f),
                            radius = 9f,
                            center = Offset(pin.x + 1f, pin.y + 1f)
                        )
                        drawCircle(
                            color = pinColor,
                            radius = 8f,
                            center = Offset(pin.x, pin.y)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 3f,
                            center = Offset(pin.x, pin.y)
                        )
                    }
                }

                // Active layer center anchor
                if (isLayerActive) {
                    drawCircle(
                        color = StudioAccent.copy(alpha = 0.7f),
                        radius = 5f,
                        center = Offset.Zero
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawRulers(viewportOffset: Offset, zoom: Float, unit: RulerUnit) {
    val rulerColor = Color(0xFF22222A)
    val tickColor = Color(0xFF6B6B7B)
    val textColor = 0xFFA0A0AB.toInt()

    // Top horizontal ruler
    drawRect(color = rulerColor, topLeft = Offset.Zero, size = Size(size.width, 22f))
    drawLine(color = tickColor, start = Offset(0f, 22f), end = Offset(size.width, 22f), strokeWidth = 1f)

    // Left vertical ruler
    drawRect(color = rulerColor, topLeft = Offset.Zero, size = Size(22f, size.height))
    drawLine(color = tickColor, start = Offset(22f, 0f), end = Offset(22f, size.height), strokeWidth = 1f)

    val nativeCanvas = drawContext.canvas.nativeCanvas
    val textPaint = Paint().apply {
        color = textColor
        textSize = 9f
        isAntiAlias = true
    }

    val stepScreen = 60f
    var x = 22f
    while (x < size.width) {
        val worldX = (x - viewportOffset.x) / zoom
        val unitVal = (worldX * unit.factorFromPx).toInt()
        drawLine(color = tickColor, start = Offset(x, 14f), end = Offset(x, 22f), strokeWidth = 1f)
        nativeCanvas.drawText("$unitVal", x + 2f, 12f, textPaint)
        x += stepScreen
    }

    var y = 22f
    while (y < size.height) {
        val worldY = (y - viewportOffset.y) / zoom
        val unitVal = (worldY * unit.factorFromPx).toInt()
        drawLine(color = tickColor, start = Offset(14f, y), end = Offset(22f, y), strokeWidth = 1f)
        nativeCanvas.drawText("$unitVal", 2f, y - 2f, textPaint)
        y += stepScreen
    }
}
