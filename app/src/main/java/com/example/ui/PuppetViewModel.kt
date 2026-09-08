package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.engine.ProjectStorageManager
import com.example.engine.PuppetWarpEngine
import com.example.engine.UndoRedoManager
import com.example.engine.VideoExporter
import com.example.model.*
import com.example.ui.components.ExportFormat
import com.example.ui.components.PuppetInteractionMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class PuppetViewModel : ViewModel() {

    private val undoRedoManager = UndoRedoManager(100)

    private val _project = MutableStateFlow(createInitialProject())
    val project: StateFlow<Project> = _project.asStateFlow()

    // Tool & Mode state
    private val _activeTool = MutableStateFlow(ToolType.PUPPET)
    val activeTool: StateFlow<ToolType> = _activeTool.asStateFlow()

    private val _puppetMode = MutableStateFlow(PuppetInteractionMode.ROTATE_BONE)
    val puppetMode: StateFlow<PuppetInteractionMode> = _puppetMode.asStateFlow()

    private val _activeBoneId = MutableStateFlow<String?>("bone_pelvis")
    val activeBoneId: StateFlow<String?> = _activeBoneId.asStateFlow()

    // Brush properties
    private val _brushSize = MutableStateFlow(16f)
    val brushSize: StateFlow<Float> = _brushSize.asStateFlow()

    private val _brushColor = MutableStateFlow(Color(0xFF3898EC))
    val brushColor: StateFlow<Color> = _brushColor.asStateFlow()

    // Shapes & Text
    private val _selectedShapeType = MutableStateFlow(ShapeType.RECT)
    val selectedShapeType: StateFlow<ShapeType> = _selectedShapeType.asStateFlow()

    private val _isShapeFilled = MutableStateFlow(true)
    val isShapeFilled: StateFlow<Boolean> = _isShapeFilled.asStateFlow()

    private val _currentText = MutableStateFlow("استودیو پاپت 2D")
    val currentText: StateFlow<String> = _currentText.asStateFlow()

    private val _textMode = MutableStateFlow(TextMode.NORMAL)
    val textMode: StateFlow<TextMode> = _textMode.asStateFlow()

    // Canvas Camera (Zoom & Pan)
    private val _viewportOffset = MutableStateFlow(Offset(540f, 540f))
    val viewportOffset: StateFlow<Offset> = _viewportOffset.asStateFlow()

    private val _zoomScale = MutableStateFlow(0.85f)
    val zoomScale: StateFlow<Float> = _zoomScale.asStateFlow()

    // Playback state
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    private var playbackJob: Job? = null

    // UI Dialogs
    private val _showColorPicker = MutableStateFlow(false)
    val showColorPicker: StateFlow<Boolean> = _showColorPicker.asStateFlow()

    private val _showNewTimelineDialog = MutableStateFlow(false)
    val showNewTimelineDialog: StateFlow<Boolean> = _showNewTimelineDialog.asStateFlow()

    private val _showExportDialog = MutableStateFlow(false)
    val showExportDialog: StateFlow<Boolean> = _showExportDialog.asStateFlow()

    private val _showLayersPanel = MutableStateFlow(false)
    val showLayersPanel: StateFlow<Boolean> = _showLayersPanel.asStateFlow()

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _exportProgress = MutableStateFlow(0f)
    val exportProgress: StateFlow<Float> = _exportProgress.asStateFlow()

    private val _exportCompleted = MutableStateFlow(false)
    val exportCompleted: StateFlow<Boolean> = _exportCompleted.asStateFlow()

    private val _isHomeScreenVisible = MutableStateFlow(false)
    val isHomeScreenVisible: StateFlow<Boolean> = _isHomeScreenVisible.asStateFlow()

    private val _isZenMode = MutableStateFlow(false)
    val isZenMode: StateFlow<Boolean> = _isZenMode.asStateFlow()

    // 3D Brush & Joystick State
    private val _brush3dMaterial = MutableStateFlow("SOLID")
    val brush3dMaterial: StateFlow<String> = _brush3dMaterial.asStateFlow()

    private val _brush3dDepth = MutableStateFlow(12f)
    val brush3dDepth: StateFlow<Float> = _brush3dDepth.asStateFlow()

    private val _brush3dTextureUri = MutableStateFlow<String?>(null)
    val brush3dTextureUri: StateFlow<String?> = _brush3dTextureUri.asStateFlow()

    private val _isJoystickVisible = MutableStateFlow(false)
    val isJoystickVisible: StateFlow<Boolean> = _isJoystickVisible.asStateFlow()

    private val _recentProjects = MutableStateFlow<List<ProjectMetadata>>(emptyList())
    val recentProjects: StateFlow<List<ProjectMetadata>> = _recentProjects.asStateFlow()

    private var currentStrokePoints = mutableListOf<PointData>()

    init {
        // Record base state for Undo/Redo
        undoRedoManager.recordState(_project.value)

        // Physics step background loop for dynamic pins
        viewModelScope.launch {
            var simTime = 0f
            while (isActive) {
                delay(33) // ~30 fps physics step
                simTime += 0.033f
                stepPhysics(simTime)
            }
        }
    }

    private fun createInitialProject(): Project {
        val initialLayer = Layer(
            id = "layer_drawing_1",
            name = "لایه طراحی ۱",
            type = LayerType.RASTER,
            rasterStrokes = emptyList(),
            shapeData = null,
            textData = null,
            puppetModifier = PuppetModifier()
        )

        return Project(
            name = "استودیو پویانمایی ۱",
            canvasWidth = 1080f,
            canvasHeight = 1080f,
            layers = listOf(initialLayer),
            activeLayerId = initialLayer.id,
            settings = ProjectSettings(
                backgroundMode = CanvasBackgroundMode.DARK,
                showGrid = true,
                gridSpacing = 40f
            )
        )
    }

    fun selectTool(tool: ToolType) {
        _activeTool.value = tool
    }

    fun setPuppetMode(mode: PuppetInteractionMode) {
        _puppetMode.value = mode
    }

    fun setBrushSize(size: Float) {
        _brushSize.value = size
    }

    fun setBrushColor(color: Color) {
        _brushColor.value = color
    }

    fun setSelectedShapeType(shapeType: ShapeType) {
        _selectedShapeType.value = shapeType
        val activeId = _project.value.activeLayerId
        _project.update { p ->
            p.copy(
                layers = p.layers.map { l ->
                    if (l.id == activeId && l.shapeData != null) {
                        l.copy(shapeData = l.shapeData.copy(shapeType = shapeType))
                    } else l
                }
            )
        }
    }

    fun toggleShapeFilled() {
        _isShapeFilled.update { !it }
        val activeId = _project.value.activeLayerId
        val filled = _isShapeFilled.value
        _project.update { p ->
            p.copy(
                layers = p.layers.map { l ->
                    if (l.id == activeId && l.shapeData != null) {
                        l.copy(shapeData = l.shapeData.copy(isFilled = filled))
                    } else l
                }
            )
        }
    }

    fun setCurrentText(text: String) {
        _currentText.value = text
        val activeId = _project.value.activeLayerId
        _project.update { p ->
            p.copy(
                layers = p.layers.map { l ->
                    if (l.id == activeId && l.textData != null) {
                        l.copy(textData = l.textData.copy(text = text))
                    } else l
                }
            )
        }
    }

    fun setTextMode(mode: TextMode) {
        _textMode.value = mode
        val activeId = _project.value.activeLayerId
        _project.update { p ->
            p.copy(
                layers = p.layers.map { l ->
                    if (l.id == activeId && l.textData != null) {
                        l.copy(textData = l.textData.copy(mode = mode))
                    } else l
                }
            )
        }
    }

    fun setProjectSettings(settings: ProjectSettings) {
        _project.update { it.copy(settings = settings) }
    }

    fun setViewportOffset(offset: Offset) {
        _viewportOffset.value = offset
    }

    fun setZoomScale(scale: Float) {
        _zoomScale.value = scale
    }

    fun toggleColorPicker(show: Boolean) {
        _showColorPicker.value = show
    }

    fun toggleNewTimelineDialog(show: Boolean) {
        _showNewTimelineDialog.value = show
    }

    fun toggleExportDialog(show: Boolean) {
        _showExportDialog.value = show
        if (show) _exportCompleted.value = false
    }

    fun toggleLayersPanel() {
        _showLayersPanel.update { !it }
    }

    // --- 3D Brush, Joystick & Deformation Controls ---
    fun setBrush3dMaterial(mat: String) { _brush3dMaterial.value = mat }
    fun setBrush3dDepth(depth: Float) { _brush3dDepth.value = depth }
    fun setBrush3dTextureUri(uri: String?) { _brush3dTextureUri.value = uri }

    fun toggleJoystick(show: Boolean? = null) {
        _isJoystickVisible.value = show ?: !_isJoystickVisible.value
    }

    fun setDeformerRotation(angle: Float) {
        val activeId = _project.value.activeLayerId
        _project.update { p ->
            p.copy(
                layers = p.layers.map { l ->
                    if (l.id == activeId) {
                        l.copy(puppetModifier = l.puppetModifier.copy(deformerRotation = angle))
                    } else l
                }
            )
        }
    }

    fun setDeformer3D(rotation: Float, pitch: Float, yaw: Float) {
        val activeId = _project.value.activeLayerId
        _project.update { p ->
            p.copy(
                layers = p.layers.map { l ->
                    if (l.id == activeId) {
                        l.copy(puppetModifier = l.puppetModifier.copy(
                            deformerRotation = rotation,
                            deformerPitch = pitch,
                            deformerYaw = yaw
                        ))
                    } else l
                }
            )
        }
    }

    fun setCanvasBackgroundMode(mode: CanvasBackgroundMode) {
        _project.update { p ->
            p.copy(settings = p.settings.copy(backgroundMode = mode))
        }
        undoRedoManager.recordState(_project.value)
    }

    fun commitTextToCanvas() {
        val activeId = _project.value.activeLayerId
        val activeLayer = _project.value.layers.find { it.id == activeId }
        val textValue = _currentText.value.ifBlank { "متن انیمیشن" }
        val a = (_brushColor.value.alpha * 255).toInt().coerceIn(0, 255)
        val r = (_brushColor.value.red * 255).toInt().coerceIn(0, 255)
        val g = (_brushColor.value.green * 255).toInt().coerceIn(0, 255)
        val b = (_brushColor.value.blue * 255).toInt().coerceIn(0, 255)
        val colorLong = ((a.toLong() and 0xFF) shl 24) or
                        ((r.toLong() and 0xFF) shl 16) or
                        ((g.toLong() and 0xFF) shl 8) or
                        (b.toLong() and 0xFF)

        if (activeLayer?.type == LayerType.TEXT) {
            _project.update { p ->
                p.copy(
                    layers = p.layers.map { l ->
                        if (l.id == activeId) {
                            l.copy(
                                name = textValue.take(12),
                                textData = (l.textData ?: TextData()).copy(
                                    text = textValue,
                                    mode = _textMode.value,
                                    textColor = colorLong
                                )
                            )
                        } else l
                    }
                )
            }
        } else {
            val newTextLayerId = UUID.randomUUID().toString()
            val textLayer = Layer(
                id = newTextLayerId,
                name = textValue.take(12),
                type = LayerType.TEXT,
                textData = TextData(
                    text = textValue,
                    mode = _textMode.value,
                    textColor = colorLong
                )
            )
            _project.update { p ->
                p.copy(
                    layers = p.layers + textLayer,
                    activeLayerId = newTextLayerId
                )
            }
        }
        undoRedoManager.recordState(_project.value)
    }

    fun clonePuppetArmy() {
        val activeId = _project.value.activeLayerId
        val activeLayer = _project.value.layers.find { it.id == activeId } ?: return
        val cloneId = UUID.randomUUID().toString()
        val clonedLayer = activeLayer.copy(
            id = cloneId,
            name = "${activeLayer.name} (کپی)",
            transform = activeLayer.transform.copy(
                translationX = activeLayer.transform.translationX + 50f,
                translationY = activeLayer.transform.translationY + 50f
            ),
            puppetModifier = activeLayer.puppetModifier.copy(
                pins = activeLayer.puppetModifier.pins.map { it.copy(id = UUID.randomUUID().toString()) }
            )
        )
        _project.update { p ->
            p.copy(
                layers = p.layers + clonedLayer,
                activeLayerId = cloneId
            )
        }
        undoRedoManager.recordState(_project.value)
    }

    // --- Drawing on Raster Layer ---
    fun addStrokePoint(point: PointData, isEraser: Boolean) {
        currentStrokePoints.add(point)
        val activeId = _project.value.activeLayerId

        val a = (_brushColor.value.alpha * 255).toInt().coerceIn(0, 255)
        val r = (_brushColor.value.red * 255).toInt().coerceIn(0, 255)
        val g = (_brushColor.value.green * 255).toInt().coerceIn(0, 255)
        val b = (_brushColor.value.blue * 255).toInt().coerceIn(0, 255)
        val colorLong = ((a.toLong() and 0xFF) shl 24) or
                        ((r.toLong() and 0xFF) shl 16) or
                        ((g.toLong() and 0xFF) shl 8) or
                        (b.toLong() and 0xFF)

        val is3D = _activeTool.value == ToolType.BRUSH_3D
        val stroke = DrawingStroke(
            color = colorLong,
            strokeWidth = if (is3D) _brush3dDepth.value.coerceAtLeast(8f) else _brushSize.value,
            points = currentStrokePoints.toList(),
            isEraser = isEraser,
            is3D = is3D,
            depthAngle = _project.value.layers.find { it.id == activeId }?.puppetModifier?.deformerRotation ?: 0f,
            materialPreset = _brush3dMaterial.value,
            textureUri = _brush3dTextureUri.value
        )

        _project.update { p ->
            p.copy(
                layers = p.layers.map { l ->
                    if (l.id == activeId) {
                        val existingStrokes = if (currentStrokePoints.size > 1 && l.rasterStrokes.isNotEmpty()) {
                            l.rasterStrokes.dropLast(1)
                        } else {
                            l.rasterStrokes
                        }
                        l.copy(rasterStrokes = existingStrokes + stroke)
                    } else l
                }
            )
        }
    }

    fun finishStroke() {
        if (currentStrokePoints.isNotEmpty()) {
            undoRedoManager.recordState(_project.value)
            currentStrokePoints.clear()
        }
    }

    // --- NON-DESTRUCTIVE MOVE TOOL ---
    fun updateLayerTransform(layerId: String, transform: LayerTransform, commitToHistory: Boolean) {
        _project.update { p ->
            p.copy(
                layers = p.layers.map { l ->
                    if (l.id == layerId) l.copy(transform = transform) else l
                }
            )
        }
        if (commitToHistory) {
            undoRedoManager.recordState(_project.value)
        }
    }

    fun resetTransform(layerId: String) {
        updateLayerTransform(layerId, LayerTransform(), true)
    }

    // --- PUPPET WARP TOOL ---
    fun addPuppetPin(layerId: String, x: Float, y: Float) {
        val newPin = PuppetPin(
            id = UUID.randomUUID().toString(),
            x = x,
            y = y,
            originalX = x,
            originalY = y,
            weight = 1.0f,
            radius = 85f,
            pinType = PinType.STATIC
        )
        _project.update { p ->
            p.copy(
                layers = p.layers.map { l ->
                    if (l.id == layerId) {
                        l.copy(
                            puppetModifier = l.puppetModifier.copy(
                                pins = l.puppetModifier.pins + newPin
                            )
                        )
                    } else l
                }
            )
        }
        undoRedoManager.recordState(_project.value)
    }

    fun movePuppetPin(layerId: String, pinId: String, x: Float, y: Float, commitToHistory: Boolean) {
        _project.update { p ->
            p.copy(
                layers = p.layers.map { l ->
                    if (l.id == layerId) {
                        l.copy(
                            puppetModifier = l.puppetModifier.copy(
                                pins = l.puppetModifier.pins.map { pin ->
                                    if (pin.id == pinId) pin.copy(x = x, y = y) else pin
                                }
                            )
                        )
                    } else l
                }
            )
        }
        if (commitToHistory) {
            undoRedoManager.recordState(_project.value)
        }
    }

    fun deletePuppetPin(layerId: String, pinId: String) {
        _project.update { p ->
            p.copy(
                layers = p.layers.map { l ->
                    if (l.id == layerId) {
                        l.copy(
                            puppetModifier = l.puppetModifier.copy(
                                pins = l.puppetModifier.pins.filter { it.id != pinId }
                            )
                        )
                    } else l
                }
            )
        }
        undoRedoManager.recordState(_project.value)
    }

    fun resetPuppetPose(layerId: String) {
        _project.update { p ->
            p.copy(
                layers = p.layers.map { l ->
                    if (l.id == layerId) {
                        val resetBones = l.puppetModifier.bones.map { it.copy(angle = 0f) }
                        val updatedBones = PuppetWarpEngine.updateBonePositions(resetBones)
                        l.copy(
                            puppetModifier = l.puppetModifier.copy(
                                pins = l.puppetModifier.pins.map { pin ->
                                    pin.copy(x = pin.originalX, y = pin.originalY, velocityX = 0f, velocityY = 0f)
                                },
                                bones = updatedBones,
                                deformerRotation = 0f,
                                deformerPitch = 0f,
                                deformerYaw = 0f
                            )
                        )
                    } else l
                }
            )
        }
        undoRedoManager.recordState(_project.value)
    }

    // --- SKELETAL RIGGING & FORWARD KINEMATICS (FK) ---
    fun selectBone(boneId: String?) {
        _activeBoneId.value = boneId
    }

    fun setupDefaultSkeleton(layerId: String) {
        val defaultBones = PuppetWarpEngine.createDefaultCharacterSkeleton(0f, 0f)
        val targetLayer = _project.value.layers.find { it.id == layerId }
        val meshVerts = targetLayer?.puppetModifier?.mesh?.originalVertices ?: emptyList()
        val weights = PuppetWarpEngine.calculateAutoBoneWeights(meshVerts, defaultBones)

        _project.update { p ->
            p.copy(
                layers = p.layers.map { l ->
                    if (l.id == layerId) {
                        l.copy(
                            puppetModifier = l.puppetModifier.copy(
                                bones = defaultBones,
                                boneWeights = weights,
                                showBones = true
                            )
                        )
                    } else l
                }
            )
        }
        _activeBoneId.value = "bone_chest"
        undoRedoManager.recordState(_project.value)
    }

    fun addBone(layerId: String, clickX: Float, clickY: Float, parentId: String? = _activeBoneId.value) {
        val targetLayer = _project.value.layers.find { it.id == layerId } ?: return
        val currentBones = targetLayer.puppetModifier.bones
        val parent = parentId?.let { pid -> currentBones.find { it.id == pid } }

        val newBoneId = "bone_${System.currentTimeMillis() % 10000}"
        val (startX, startY, length, angle) = if (parent != null) {
            val sx = parent.globalEndX
            val sy = parent.globalEndY
            val dx = clickX - sx
            val dy = clickY - sy
            val dist = kotlin.math.hypot(dx, dy).coerceAtLeast(30f)
            val globalTouchAngle = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
            val localAngle = globalTouchAngle - parent.globalAngle
            Tuple4(sx, sy, dist, localAngle)
        } else {
            Tuple4(clickX, clickY, 80f, 0f)
        }

        val newBone = PuppetBone(
            id = newBoneId,
            name = "استخوان ${currentBones.size + 1}",
            parentId = parent?.id,
            startX = startX,
            startY = startY,
            length = length,
            angle = angle,
            color = if (parent == null) 0xFF00E5FF else 0xFF00E676,
            controlShape = if (parent == null) BoneControlShape.SQUARE else BoneControlShape.CIRCLE
        )

        val updatedBones = PuppetWarpEngine.updateBonePositions(currentBones + newBone)
        val meshVerts = targetLayer.puppetModifier.mesh.originalVertices
        val newWeights = PuppetWarpEngine.calculateAutoBoneWeights(meshVerts, updatedBones)

        _project.update { p ->
            p.copy(
                layers = p.layers.map { l ->
                    if (l.id == layerId) {
                        l.copy(
                            puppetModifier = l.puppetModifier.copy(
                                bones = updatedBones,
                                boneWeights = newWeights,
                                showBones = true
                            )
                        )
                    } else l
                }
            )
        }
        _activeBoneId.value = newBoneId
        undoRedoManager.recordState(_project.value)
    }

    fun rotateBone(layerId: String, boneId: String, deltaAngle: Float, commitToHistory: Boolean) {
        _project.update { p ->
            p.copy(
                layers = p.layers.map { l ->
                    if (l.id == layerId) {
                        val updated = l.puppetModifier.bones.map { b ->
                            if (b.id == boneId) b.copy(angle = b.angle + deltaAngle) else b
                        }
                        val resolved = PuppetWarpEngine.updateBonePositions(updated)
                        l.copy(puppetModifier = l.puppetModifier.copy(bones = resolved))
                    } else l
                }
            )
        }
        if (commitToHistory) {
            undoRedoManager.recordState(_project.value)
        }
    }

    fun setBoneAngle(layerId: String, boneId: String, absoluteAngle: Float, commitToHistory: Boolean) {
        _project.update { p ->
            p.copy(
                layers = p.layers.map { l ->
                    if (l.id == layerId) {
                        val updated = l.puppetModifier.bones.map { b ->
                            if (b.id == boneId) b.copy(angle = absoluteAngle) else b
                        }
                        val resolved = PuppetWarpEngine.updateBonePositions(updated)
                        l.copy(puppetModifier = l.puppetModifier.copy(bones = resolved))
                    } else l
                }
            )
        }
        if (commitToHistory) {
            undoRedoManager.recordState(_project.value)
        }
    }

    fun deleteBone(layerId: String, boneId: String) {
        _project.update { p ->
            p.copy(
                layers = p.layers.map { l ->
                    if (l.id == layerId) {
                        val remaining = l.puppetModifier.bones
                            .filter { it.id != boneId }
                            .map { if (it.parentId == boneId) it.copy(parentId = null) else it }
                        val resolved = PuppetWarpEngine.updateBonePositions(remaining)
                        val weights = PuppetWarpEngine.calculateAutoBoneWeights(
                            l.puppetModifier.mesh.originalVertices,
                            resolved
                        )
                        l.copy(puppetModifier = l.puppetModifier.copy(bones = resolved, boneWeights = weights))
                    } else l
                }
            )
        }
        if (_activeBoneId.value == boneId) _activeBoneId.value = null
        undoRedoManager.recordState(_project.value)
    }

    fun autoSkinning(layerId: String) {
        val targetLayer = _project.value.layers.find { it.id == layerId } ?: return
        val bones = targetLayer.puppetModifier.bones
        if (bones.isEmpty()) return
        val meshVerts = targetLayer.puppetModifier.mesh.originalVertices
        val weights = PuppetWarpEngine.calculateAutoBoneWeights(meshVerts, bones)

        _project.update { p ->
            p.copy(
                layers = p.layers.map { l ->
                    if (l.id == layerId) {
                        l.copy(puppetModifier = l.puppetModifier.copy(boneWeights = weights))
                    } else l
                }
            )
        }
        undoRedoManager.recordState(_project.value)
    }

    fun toggleShowBones(layerId: String) {
        _project.update { p ->
            p.copy(
                layers = p.layers.map { l ->
                    if (l.id == layerId) {
                        val cur = l.puppetModifier.showBones
                        l.copy(puppetModifier = l.puppetModifier.copy(showBones = !cur))
                    } else l
                }
            )
        }
    }

    private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    fun updatePuppetModifier(layerId: String, modifier: PuppetModifier) {
        // Regenerate mesh if density changed
        val current = _project.value.layers.find { it.id == layerId }?.puppetModifier
        val newMesh = if (current != null && current.density != modifier.density) {
            PuppetWarpEngine.generateMesh(360f, 360f, modifier.density)
        } else modifier.mesh

        _project.update { p ->
            p.copy(
                layers = p.layers.map { l ->
                    if (l.id == layerId) l.copy(puppetModifier = modifier.copy(mesh = newMesh)) else l
                }
            )
        }
        undoRedoManager.recordState(_project.value)
    }

    private fun stepPhysics(simTime: Float) {
        _project.update { p ->
            p.copy(
                layers = p.layers.map { l ->
                    val pm = l.puppetModifier
                    if (pm.physicsPreset != PhysicsPreset.NONE && pm.pins.isNotEmpty()) {
                        val steppedPins = PuppetWarpEngine.stepPhysics(
                            pins = pm.pins,
                            preset = pm.physicsPreset,
                            params = pm.physicsParams,
                            timeSeconds = simTime
                        )
                        l.copy(puppetModifier = pm.copy(pins = steppedPins))
                    } else l
                }
            )
        }
    }

    // --- LAYERS MANAGEMENT ---
    fun selectLayer(layerId: String) {
        _project.update { it.copy(activeLayerId = layerId) }
    }

    fun addLayer(type: LayerType) {
        val newId = UUID.randomUUID().toString()
        val defaultMesh = PuppetWarpEngine.generateMesh(300f, 300f, MeshDensity.MEDIUM)
        val newLayer = when (type) {
            LayerType.RASTER -> Layer(id = newId, name = "لایه نقاشی ${project.value.layers.size + 1}", type = type)
            LayerType.SHAPE -> Layer(
                id = newId,
                name = "لایه شکل ${project.value.layers.size + 1}",
                type = type,
                shapeData = ShapeData(shapeType = _selectedShapeType.value),
                puppetModifier = PuppetModifier(mesh = defaultMesh)
            )
            LayerType.TEXT -> Layer(
                id = newId,
                name = "لایه متن ${project.value.layers.size + 1}",
                type = type,
                textData = TextData(text = _currentText.value),
                puppetModifier = PuppetModifier(mesh = defaultMesh)
            )
            LayerType.IMAGE -> Layer(
                id = newId,
                name = "تصویر وارد شده",
                type = type,
                shapeData = ShapeData(shapeType = ShapeType.RECT, width = 300f, height = 300f, fillColor = 0xFF5C6BC0),
                puppetModifier = PuppetModifier(mesh = defaultMesh)
            )
            LayerType.VIDEO_REF -> Layer(
                id = newId,
                name = "ویدیو مرجع (روتوسکوپی)",
                type = type,
                videoRefData = VideoRefData()
            )
        }

        _project.update { p ->
            p.copy(
                layers = p.layers + newLayer,
                activeLayerId = newLayer.id
            )
        }
        undoRedoManager.recordState(_project.value)
    }

    fun toggleLayerVisibility(layerId: String) {
        _project.update { p ->
            p.copy(
                layers = p.layers.map { l ->
                    if (l.id == layerId) l.copy(isVisible = !l.isVisible) else l
                }
            )
        }
    }

    fun toggleLayerLock(layerId: String) {
        _project.update { p ->
            p.copy(
                layers = p.layers.map { l ->
                    if (l.id == layerId) l.copy(isLocked = !l.isLocked) else l
                }
            )
        }
    }

    fun updateLayerOpacity(layerId: String, opacity: Float) {
        _project.update { p ->
            p.copy(
                layers = p.layers.map { l ->
                    if (l.id == layerId) l.copy(opacity = opacity) else l
                }
            )
        }
    }

    fun updateLayerBlendMode(layerId: String, blendMode: BlendModeType) {
        _project.update { p ->
            p.copy(
                layers = p.layers.map { l ->
                    if (l.id == layerId) l.copy(blendMode = blendMode) else l
                }
            )
        }
        undoRedoManager.recordState(_project.value)
    }

    fun duplicateLayer(layerId: String) {
        val original = _project.value.layers.find { it.id == layerId } ?: return
        val clone = original.copy(
            id = UUID.randomUUID().toString(),
            name = "${original.name} (کپی)",
            transform = original.transform.copy(
                translationX = original.transform.translationX + 30f,
                translationY = original.transform.translationY + 30f
            )
        )
        _project.update { p ->
            p.copy(
                layers = p.layers + clone,
                activeLayerId = clone.id
            )
        }
        undoRedoManager.recordState(_project.value)
    }

    fun deleteLayer(layerId: String) {
        if (_project.value.layers.size <= 1) return
        _project.update { p ->
            val remaining = p.layers.filter { it.id != layerId }
            p.copy(
                layers = remaining,
                activeLayerId = remaining.lastOrNull()?.id ?: ""
            )
        }
        undoRedoManager.recordState(_project.value)
    }

    fun moveLayerUp(index: Int) {
        if (index < _project.value.layers.size - 1) {
            val list = _project.value.layers.toMutableList()
            val temp = list[index]
            list[index] = list[index + 1]
            list[index + 1] = temp
            _project.update { it.copy(layers = list) }
            undoRedoManager.recordState(_project.value)
        }
    }

    fun moveLayerDown(index: Int) {
        if (index > 0) {
            val list = _project.value.layers.toMutableList()
            val temp = list[index]
            list[index] = list[index - 1]
            list[index - 1] = temp
            _project.update { it.copy(layers = list) }
            undoRedoManager.recordState(_project.value)
        }
    }

    // --- TIMELINE & ANIMATION ---
    fun createTimeline(mode: TimelineMode) {
        _showNewTimelineDialog.value = false
        val newTimeline = Timeline(
            id = UUID.randomUUID().toString(),
            name = "تایم‌لاین ${_project.value.timeline.id.take(4)}",
            mode = mode,
            totalFrames = 24,
            currentFrame = 0,
            fps = _project.value.timeline.fps
        )

        when (mode) {
            TimelineMode.DUPLICATE, TimelineMode.LINKED -> {
                // Non-destructive: Preserve all existing layers, pins, and drawing contents!
                _project.update { it.copy(timeline = newTimeline) }
            }
            TimelineMode.BLANK -> {
                // User confirmed blank: creates clean base layer
                val blankLayer = Layer(
                    id = UUID.randomUUID().toString(),
                    name = "لایه پایه ۱",
                    type = LayerType.RASTER
                )
                _project.update {
                    it.copy(
                        timeline = newTimeline,
                        layers = listOf(blankLayer),
                        activeLayerId = blankLayer.id
                    )
                }
            }
        }
        undoRedoManager.recordState(_project.value)
    }

    fun selectFrame(frame: Int) {
        _project.update { p ->
            p.copy(timeline = p.timeline.copy(currentFrame = frame.coerceIn(0, p.timeline.totalFrames - 1)))
        }
    }

    fun addFrame() {
        _project.update { p ->
            val newTotal = p.timeline.totalFrames + 1
            p.copy(timeline = p.timeline.copy(totalFrames = newTotal, currentFrame = newTotal - 1))
        }
        undoRedoManager.recordState(_project.value)
    }

    fun duplicateFrame() {
        _project.update { p ->
            val newTotal = p.timeline.totalFrames + 1
            p.copy(timeline = p.timeline.copy(totalFrames = newTotal, currentFrame = p.timeline.currentFrame + 1))
        }
        undoRedoManager.recordState(_project.value)
    }

    fun deleteFrame() {
        if (_project.value.timeline.totalFrames > 1) {
            _project.update { p ->
                val newTotal = p.timeline.totalFrames - 1
                val newCurrent = p.timeline.currentFrame.coerceIn(0, newTotal - 1)
                p.copy(timeline = p.timeline.copy(totalFrames = newTotal, currentFrame = newCurrent))
            }
            undoRedoManager.recordState(_project.value)
        }
    }

    fun togglePlayPause() {
        if (_isPlaying.value) {
            stopPlayback()
        } else {
            startPlayback()
        }
    }

    private fun startPlayback() {
        _isPlaying.value = true
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            while (isActive && _isPlaying.value) {
                val delayMs = (1000L / _project.value.timeline.fps).coerceIn(10L, 200L)
                delay(delayMs)
                _project.update { p ->
                    val next = p.timeline.currentFrame + 1
                    val target = if (next >= p.timeline.totalFrames) {
                        if (p.timeline.isLooping) 0 else p.timeline.totalFrames - 1
                    } else next
                    p.copy(timeline = p.timeline.copy(currentFrame = target))
                }
            }
        }
    }

    fun stopPlayback() {
        _isPlaying.value = false
        playbackJob?.cancel()
    }

    fun toggleLoop() {
        _project.update { p ->
            p.copy(timeline = p.timeline.copy(isLooping = !p.timeline.isLooping))
        }
    }

    fun toggleOnionSkin() {
        _project.update { p ->
            p.copy(timeline = p.timeline.copy(isOnionSkinEnabled = !p.timeline.isOnionSkinEnabled))
        }
    }

    fun changeFps(fps: Int) {
        _project.update { p ->
            p.copy(timeline = p.timeline.copy(fps = fps))
        }
    }

    fun addAudioTrack() {
        val currentAudio = _project.value.timeline.audioTrack
        val updatedAudio = if (currentAudio == null) {
            AudioTrack(name = "موزیک پس‌زمینه", durationMs = 12000L)
        } else null
        _project.update { p ->
            p.copy(timeline = p.timeline.copy(audioTrack = updatedAudio))
        }
    }

    // --- UNDO / REDO ---
    fun undo() {
        val previousState = undoRedoManager.undo(_project.value)
        if (previousState != null) {
            _project.value = previousState
        }
    }

    fun redo() {
        val nextState = undoRedoManager.redo(_project.value)
        if (nextState != null) {
            _project.value = nextState
        }
    }

    // --- SAVE / AUTOSAVE ---
    fun autosave(context: Context) {
        ProjectStorageManager.autosaveProject(context, _project.value)
    }

    fun saveProject(context: Context): Boolean {
        ProjectStorageManager.autosaveProject(context, _project.value)
        return true
    }

    // --- EXPORT ---
    fun startExport(context: Context, format: ExportFormat) {
        _isExporting.value = true
        _exportProgress.value = 0.05f
        _exportCompleted.value = false

        viewModelScope.launch {
            try {
                val exportedFile = VideoExporter.exportAnimation(context, _project.value, format) { progress ->
                    _exportProgress.value = progress
                }
                _isExporting.value = false
                _exportCompleted.value = (exportedFile != null && exportedFile.exists())
            } catch (e: Exception) {
                e.printStackTrace()
                _isExporting.value = false
            }
        }
    }

    // --- HOME SCREEN & PRESETS ---
    fun showHomeScreen() { _isHomeScreenVisible.value = true }
    fun hideHomeScreen() { _isHomeScreenVisible.value = false }
    fun toggleHomeScreen() { _isHomeScreenVisible.value = !_isHomeScreenVisible.value }

    fun refreshRecentProjects(context: Context) {
        _recentProjects.value = ProjectStorageManager.getRecentProjects(context)
    }

    fun deleteProject(context: Context, projectId: String) {
        ProjectStorageManager.deleteProject(context, projectId)
        refreshRecentProjects(context)
    }

    fun renameProject(context: Context, projectId: String, newName: String) {
        ProjectStorageManager.renameProject(context, projectId, newName)
        if (_project.value.id == projectId) {
            _project.update { it.copy(name = newName) }
        }
        refreshRecentProjects(context)
    }

    fun loadProjectById(context: Context, projectId: String) {
        val p = ProjectStorageManager.loadProjectById(context, projectId)
        if (p != null) {
            _project.value = p
            undoRedoManager.clear()
            undoRedoManager.recordState(p)
            _isHomeScreenVisible.value = false
        }
    }

    fun newProjectFromPreset(preset: CanvasPreset, context: Context) {
        val newProj = ProjectStorageManager.createPresetProject(preset)
        _project.value = newProj
        ProjectStorageManager.autosaveProject(context, newProj)
        refreshRecentProjects(context)
        undoRedoManager.recordState(newProj)
        _isHomeScreenVisible.value = false
    }

    fun openProjectById(id: String, context: Context) {
        val file = File(context.filesDir, "project_${id}.json")
        if (file.exists()) {
            val json = file.readText()
            val loaded = ProjectStorageManager.loadProjectFromJson(json)
            if (loaded != null) {
                _project.value = loaded
                undoRedoManager.recordState(loaded)
                _isHomeScreenVisible.value = false
                return
            }
        }
        // Fallback: Autosave file if matches or latest
        val autosaved = ProjectStorageManager.loadAutosavedProject(context)
        if (autosaved != null) {
            _project.value = autosaved
            undoRedoManager.recordState(autosaved)
            _isHomeScreenVisible.value = false
        }
    }

    fun importPuppet2d(file: File, context: Context): Boolean {
        val loaded = ProjectStorageManager.importFromPuppet2dFile(file)
        if (loaded != null) {
            _project.value = loaded
            ProjectStorageManager.autosaveProject(context, loaded)
            refreshRecentProjects(context)
            undoRedoManager.recordState(loaded)
            _isHomeScreenVisible.value = false
            return true
        }
        return false
    }

    fun exportPuppet2d(destinationFile: File): Boolean {
        return ProjectStorageManager.exportToPuppet2dFile(_project.value, destinationFile)
    }

    // --- ZEN / FOCUS MODE ---
    fun toggleZenMode() { _isZenMode.value = !_isZenMode.value }
    fun exitZenMode() { _isZenMode.value = false }

    // --- SCENES & ROTOPROJECT ---
    fun addScene(name: String = "صحنه") {
        val current = _project.value
        val newScene = Scene(
            id = UUID.randomUUID().toString(),
            name = "$name ${current.scenes.size + 1}",
            timeline = Timeline(id = UUID.randomUUID().toString(), name = "تایم‌لاین صحنه ${current.scenes.size + 1}"),
            layers = current.layers
        )
        _project.update { p ->
            p.copy(
                scenes = p.scenes + newScene,
                activeSceneIndex = p.scenes.size
            )
        }
        undoRedoManager.recordState(_project.value)
    }

    fun switchScene(index: Int) {
        val current = _project.value
        if (index in current.scenes.indices) {
            val scene = current.scenes[index]
            _project.update { p ->
                p.copy(
                    activeSceneIndex = index,
                    layers = if (scene.layers.isNotEmpty()) scene.layers else p.layers,
                    timeline = scene.timeline
                )
            }
        }
    }

    fun addVideoReferenceLayer(uri: String, name: String = "ویدیو مرجع (روتوسکوپی)") {
        val newLayer = Layer(
            id = UUID.randomUUID().toString(),
            name = name,
            type = LayerType.VIDEO_REF,
            imageUri = uri,
            opacity = 0.5f
        )
        _project.update { p ->
            p.copy(
                layers = listOf(newLayer) + p.layers,
                activeLayerId = newLayer.id
            )
        }
        undoRedoManager.recordState(_project.value)
    }

    // --- PIN DEPTH & MIRROR ---
    fun setPinDepth(layerId: String, pinId: String, depth: Float) {
        _project.update { p ->
            p.copy(
                layers = p.layers.map { l ->
                    if (l.id == layerId) {
                        val mod = l.puppetModifier
                        l.copy(
                            puppetModifier = mod.copy(
                                pins = mod.pins.map { pin ->
                                    if (pin.id == pinId) pin.copy(depth = depth) else pin
                                }
                            )
                        )
                    } else l
                }
            )
        }
    }

    fun togglePinMirror(layerId: String, pinId: String) {
        _project.update { p ->
            p.copy(
                layers = p.layers.map { l ->
                    if (l.id == layerId) {
                        val mod = l.puppetModifier
                        val targetPin = mod.pins.find { it.id == pinId } ?: return@map l
                        val newMirrored = !targetPin.isMirrored

                        // If turning mirror on, create symmetric pin if not existing
                        val updatedPins = if (newMirrored && targetPin.mirrorPinId == null) {
                            val mirrorPin = PuppetPin(
                                id = UUID.randomUUID().toString(),
                                x = -targetPin.x,
                                y = targetPin.y,
                                originalX = -targetPin.originalX,
                                originalY = targetPin.originalY,
                                weight = targetPin.weight,
                                radius = targetPin.radius,
                                isMirrored = true,
                                mirrorPinId = targetPin.id
                            )
                            mod.pins.map { if (it.id == pinId) it.copy(isMirrored = true, mirrorPinId = mirrorPin.id) else it } + mirrorPin
                        } else {
                            mod.pins.map { if (it.id == pinId) it.copy(isMirrored = newMirrored) else it }
                        }
                        l.copy(puppetModifier = mod.copy(pins = updatedPins))
                    } else l
                }
            )
        }
        undoRedoManager.recordState(_project.value)
    }
}
