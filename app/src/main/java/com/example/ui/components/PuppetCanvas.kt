package com.example.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.platform.testTag
import com.example.engine.PuppetWarpEngine
import com.example.model.*
import com.example.ui.theme.StudioAccent
import com.example.ui.theme.StudioAccentAmber
import com.example.ui.theme.StudioKeyframeDiamond
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

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

    val activeLayer = project.layers.find { it.id == project.activeLayerId }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("puppet_canvas_container")
            // Handle multi-touch pinch to zoom and pan
            .pointerInput(activeTool) {
                if (activeTool == ToolType.HAND || activeTool == ToolType.ZOOM) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        onViewportOffsetChange(viewportOffset + pan)
                        onZoomScaleChange((zoomScale * zoom).coerceIn(0.2f, 5.0f))
                    }
                }
            }
            // Handle tap gestures for Puppet pin adding/deleting
            .pointerInput(activeTool, puppetMode, activeLayer) {
                if (activeTool == ToolType.PUPPET && activeLayer != null) {
                    detectTapGestures { tapOffset ->
                        // Convert screen point to layer local coordinates
                        val localX = (tapOffset.x - viewportOffset.x) / zoomScale - activeLayer.transform.translationX
                        val localY = (tapOffset.y - viewportOffset.y) / zoomScale - activeLayer.transform.translationY

                        when (puppetMode) {
                            PuppetInteractionMode.ADD_PIN -> {
                                onAddPuppetPin(activeLayer.id, localX, localY)
                            }
                            PuppetInteractionMode.DELETE_PIN -> {
                                val nearest = activeLayer.puppetModifier.pins.minByOrNull { p ->
                                    val dx = p.x - localX
                                    val dy = p.y - localY
                                    dx * dx + dy * dy
                                }
                                if (nearest != null) {
                                    val dist = sqrt((nearest.x - localX) * (nearest.x - localX) + (nearest.y - localY) * (nearest.y - localY))
                                    if (dist < 40f) {
                                        onDeletePuppetPin(activeLayer.id, nearest.id)
                                    }
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }
            // Handle drag gestures for Brush drawing, Non-destructive Move, and Puppet pin moving
            .pointerInput(activeTool, puppetMode, activeLayer, viewportOffset, zoomScale) {
                when (activeTool) {
                    ToolType.BRUSH, ToolType.ERASER -> {
                        val isEraser = activeTool == ToolType.ERASER
                        detectDragGestures(
                            onDragStart = { startOffset ->
                                if (activeLayer != null) {
                                    val lx = (startOffset.x - viewportOffset.x) / zoomScale - activeLayer.transform.translationX
                                    val ly = (startOffset.y - viewportOffset.y) / zoomScale - activeLayer.transform.translationY
                                    onAddStrokePoint(PointData(lx, ly), isEraser)
                                }
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                if (activeLayer != null) {
                                    val lx = (change.position.x - viewportOffset.x) / zoomScale - activeLayer.transform.translationX
                                    val ly = (change.position.y - viewportOffset.y) / zoomScale - activeLayer.transform.translationY
                                    onAddStrokePoint(PointData(lx, ly), isEraser)
                                }
                            },
                            onDragEnd = { onFinishStroke() },
                            onDragCancel = { onFinishStroke() }
                        )
                    }

                    ToolType.SELECT_MOVE -> {
                        // NON-DESTRUCTIVE MOVE TOOL: Only transforms the layer offsets; raster contents remain intact!
                        detectDragGestures(
                            onDragStart = { startOffset ->
                                if (activeLayer != null) {
                                    moveStartOffset = startOffset
                                    initialTranslation = Offset(
                                        activeLayer.transform.translationX,
                                        activeLayer.transform.translationY
                                    )
                                }
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                if (activeLayer != null) {
                                    val deltaX = (change.position.x - moveStartOffset.x) / zoomScale
                                    val deltaY = (change.position.y - moveStartOffset.y) / zoomScale
                                    val newTransform = activeLayer.transform.copy(
                                        translationX = initialTranslation.x + deltaX,
                                        translationY = initialTranslation.y + deltaY
                                    )
                                    onUpdateLayerTransform(activeLayer.id, newTransform, false)
                                }
                            },
                            onDragEnd = {
                                if (activeLayer != null) {
                                    onUpdateLayerTransform(activeLayer.id, activeLayer.transform, true)
                                }
                            }
                        )
                    }

                    ToolType.PUPPET -> {
                        if (puppetMode == PuppetInteractionMode.MOVE_PIN && activeLayer != null) {
                            detectDragGestures(
                                onDragStart = { startOffset ->
                                    val lx = (startOffset.x - viewportOffset.x) / zoomScale - activeLayer.transform.translationX
                                    val ly = (startOffset.y - viewportOffset.y) / zoomScale - activeLayer.transform.translationY
                                    val nearest = activeLayer.puppetModifier.pins.minByOrNull { p ->
                                        val dx = p.x - lx
                                        val dy = p.y - ly
                                        dx * dx + dy * dy
                                    }
                                    activePinId = if (nearest != null) {
                                        val dist = sqrt((nearest.x - lx) * (nearest.x - lx) + (nearest.y - ly) * (nearest.y - ly))
                                        if (dist < 60f) nearest.id else null
                                    } else null
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val pinId = activePinId
                                    if (pinId != null && activeLayer != null) {
                                        val lx = (change.position.x - viewportOffset.x) / zoomScale - activeLayer.transform.translationX
                                        val ly = (change.position.y - viewportOffset.y) / zoomScale - activeLayer.transform.translationY
                                        onMovePuppetPin(activeLayer.id, pinId, lx, ly, false)
                                    }
                                },
                                onDragEnd = {
                                    val pinId = activePinId
                                    if (pinId != null && activeLayer != null) {
                                        val pin = activeLayer.puppetModifier.pins.find { it.id == pinId }
                                        if (pin != null) {
                                            onMovePuppetPin(activeLayer.id, pinId, pin.x, pin.y, true)
                                        }
                                    }
                                    activePinId = null
                                },
                                onDragCancel = { activePinId = null }
                            )
                        }
                    }

                    else -> {}
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize().testTag("interactive_canvas")) {
            // Draw dark Photoshop canvas background
            drawRect(Color(0xFF141418))

            translate(left = viewportOffset.x, top = viewportOffset.y) {
                scale(scale = zoomScale, pivot = Offset.Zero) {
                    // Canvas bounds rectangle (1080x1080 default workspace)
                    drawRect(
                        color = Color(0xFF1E1E24),
                        topLeft = Offset(-project.canvasWidth / 2f, -project.canvasHeight / 2f),
                        size = Size(project.canvasWidth, project.canvasHeight)
                    )

                    // Draw grid if enabled
                    if (project.settings.showGrid) {
                        val spacing = project.settings.gridSpacing
                        val halfW = project.canvasWidth / 2f
                        val halfH = project.canvasHeight / 2f

                        var gx = -halfW
                        while (gx <= halfW) {
                            drawLine(
                                color = Color(0xFF2B2B36),
                                start = Offset(gx, -halfH),
                                end = Offset(gx, halfH),
                                strokeWidth = 1f
                            )
                            gx += spacing
                        }

                        var gy = -halfH
                        while (gy <= halfH) {
                            drawLine(
                                color = Color(0xFF2B2B36),
                                start = Offset(-halfW, gy),
                                end = Offset(halfW, gy),
                                strokeWidth = 1f
                            )
                            gy += spacing
                        }
                    }

                    // Render Onion skinning if enabled
                    if (project.timeline.isOnionSkinEnabled && project.timeline.totalFrames > 1) {
                        val onionAlpha = project.timeline.onionSkinOpacity
                        // Previous frame preview (tinted red/magenta)
                        drawCircle(
                            color = Color(0xFFFF3366).copy(alpha = onionAlpha * 0.4f),
                            radius = 40f,
                            center = Offset(-10f, -10f)
                        )
                        // Next frame preview (tinted cyan/green)
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

                    // Draw Canvas outer border
                    drawRect(
                        color = Color(0xFF3898EC).copy(alpha = 0.5f),
                        topLeft = Offset(-project.canvasWidth / 2f, -project.canvasHeight / 2f),
                        size = Size(project.canvasWidth, project.canvasHeight),
                        style = Stroke(width = 2f)
                    )
                }
            }

            // Draw Canvas Rulers along top and left edge if enabled
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
    translate(left = layer.transform.translationX, top = layer.transform.translationY) {
        rotate(degrees = layer.transform.rotation, pivot = Offset.Zero) {
            scale(scaleX = layer.transform.scaleX, scaleY = layer.transform.scaleY, pivot = Offset.Zero) {

                // 1. Draw Raster Strokes
                for (stroke in layer.rasterStrokes) {
                    if (stroke.points.size > 1) {
                        val path = Path()
                        val p0 = stroke.points[0]
                        path.moveTo(p0.x, p0.y)
                        for (i in 1 until stroke.points.size) {
                            val pt = stroke.points[i]
                            path.lineTo(pt.x, pt.y)
                        }
                        drawPath(
                            path = path,
                            color = if (stroke.isEraser) Color(0xFF1E1E24) else Color(stroke.color).copy(alpha = layer.opacity),
                            style = Stroke(
                                width = stroke.strokeWidth,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }

                // 2. Draw Shape
                layer.shapeData?.let { shape ->
                    val color = Color(shape.fillColor).copy(alpha = layer.opacity)
                    val strokeColor = Color(shape.strokeColor).copy(alpha = layer.opacity)
                    val halfW = shape.width / 2f
                    val halfH = shape.height / 2f

                    when (shape.shapeType) {
                        ShapeType.RECT -> {
                            if (shape.isFilled) {
                                drawRect(color, topLeft = Offset(-halfW, -halfH), size = Size(shape.width, shape.height))
                            }
                            drawRect(strokeColor, topLeft = Offset(-halfW, -halfH), size = Size(shape.width, shape.height), style = Stroke(shape.strokeWidth))
                        }
                        ShapeType.CIRCLE -> {
                            if (shape.isFilled) {
                                drawCircle(color, radius = shape.radius, center = Offset.Zero)
                            }
                            drawCircle(strokeColor, radius = shape.radius, center = Offset.Zero, style = Stroke(shape.strokeWidth))
                        }
                        ShapeType.TRIANGLE -> {
                            val path = Path().apply {
                                moveTo(0f, -halfH)
                                lineTo(halfW, halfH)
                                lineTo(-halfW, halfH)
                                close()
                            }
                            if (shape.isFilled) drawPath(path, color)
                            drawPath(path, strokeColor, style = Stroke(shape.strokeWidth))
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
                            if (shape.isFilled) drawPath(path, color)
                            drawPath(path, strokeColor, style = Stroke(shape.strokeWidth))
                        }
                        ShapeType.CUBE_25D -> {
                            // Isometric 2.5D cube representation
                            val frontPath = Path().apply {
                                moveTo(-50f, -20f); lineTo(50f, -20f); lineTo(50f, 60f); lineTo(-50f, 60f); close()
                            }
                            val topPath = Path().apply {
                                moveTo(-50f, -20f); lineTo(0f, -55f); lineTo(100f, -55f); lineTo(50f, -20f); close()
                            }
                            val sidePath = Path().apply {
                                moveTo(50f, -20f); lineTo(100f, -55f); lineTo(100f, 25f); lineTo(50f, 60f); close()
                            }
                            drawPath(frontPath, color)
                            drawPath(topPath, color.copy(alpha = (layer.opacity * 0.8f).coerceIn(0f, 1f)))
                            drawPath(sidePath, color.copy(alpha = (layer.opacity * 0.6f).coerceIn(0f, 1f)))
                            drawPath(frontPath, strokeColor, style = Stroke(2f))
                            drawPath(topPath, strokeColor, style = Stroke(2f))
                            drawPath(sidePath, strokeColor, style = Stroke(2f))
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
                val puppet = layer.puppetModifier
                val deformedVertices = PuppetWarpEngine.deformMesh(
                    originalMesh = puppet.mesh,
                    pins = puppet.pins,
                    deformerType = puppet.deformerType,
                    deformerRotation = puppet.deformerRotation,
                    deformerRadius = puppet.deformerRadius
                )

                // Wireframe mesh lines if showMesh is enabled
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
                            color = pinColor.copy(alpha = 0.15f),
                            radius = pin.radius,
                            center = Offset(pin.x, pin.y)
                        )
                        drawCircle(
                            color = pinColor.copy(alpha = 0.35f),
                            radius = pin.radius,
                            center = Offset(pin.x, pin.y),
                            style = Stroke(width = 1f)
                        )

                        // Pin Core & Shadow
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

                // Active layer selection bounding box
                if (isLayerActive) {
                    drawCircle(
                        color = StudioAccent.copy(alpha = 0.8f),
                        radius = 6f,
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
