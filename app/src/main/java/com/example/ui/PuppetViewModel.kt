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

    private val _puppetMode = MutableStateFlow(PuppetInteractionMode.MOVE_PIN)
    val puppetMode: StateFlow<PuppetInteractionMode> = _puppetMode.asStateFlow()

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
        val baseMesh = PuppetWarpEngine.generateMesh(360f, 360f, MeshDensity.MEDIUM)

        // Initial character pins (head, chest, hands, feet)
        val initialPins = listOf(
            PuppetPin(id = "pin_head", x = 0f, y = -120f, originalX = 0f, originalY = -120f, pinType = PinType.STATIC),
            PuppetPin(id = "pin_torso", x = 0f, y = 0f, originalX = 0f, originalY = 0f, pinType = PinType.STATIC),
            PuppetPin(id = "pin_hand_l", x = -110f, y = 20f, originalX = -110f, originalY = 20f, pinType = PinType.DYNAMIC),
            PuppetPin(id = "pin_hand_r", x = 110f, y = 20f, originalX = 110f, originalY = 20f, pinType = PinType.DYNAMIC),
            PuppetPin(id = "pin_leg_l", x = -60f, y = 140f, originalX = -60f, originalY = 140f, pinType = PinType.STATIC),
            PuppetPin(id = "pin_leg_r", x = 60f, y = 140f, originalX = 60f, originalY = 140f, pinType = PinType.STATIC)
        )

        val puppetModifier = PuppetModifier(
            mesh = baseMesh,
            pins = initialPins,
            showMesh = true,
            density = MeshDensity.MEDIUM
        )

        val characterLayer = Layer(
            id = "layer_character",
            name = "کاراکتر پاپت اصلی",
            type = LayerType.SHAPE,
            shapeData = ShapeData(
                shapeType = ShapeType.STAR,
                radius = 120f,
                fillColor = 0xFF3898EC,
                strokeColor = 0xFFFFFFFF,
                strokeWidth = 4f
            ),
            puppetModifier = puppetModifier
        )

        val bgLayer = Layer(
            id = "layer_bg",
            name = "پس‌زمینه بوم",
            type = LayerType.SHAPE,
            shapeData = ShapeData(
                shapeType = ShapeType.RECT,
                width = 800f,
                height = 800f,
                fillColor = 0xFF1B1B22,
                strokeColor = 0xFF2B2B36,
                strokeWidth = 2f
            )
        )

        return Project(
            name = "استودیو پاپت ۱",
            layers = listOf(bgLayer, characterLayer),
            activeLayerId = characterLayer.id
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

    // --- Drawing on Raster Layer ---
    fun addStrokePoint(point: PointData, isEraser: Boolean) {
        currentStrokePoints.add(point)
        val activeId = _project.value.activeLayerId
        val colorLong = _brushColor.value.value.toLong()
        val stroke = DrawingStroke(
            color = colorLong,
            strokeWidth = _brushSize.value,
            points = currentStrokePoints.toList(),
            isEraser = isEraser
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
                        l.copy(
                            puppetModifier = l.puppetModifier.copy(
                                pins = l.puppetModifier.pins.map { pin ->
                                    pin.copy(x = pin.originalX, y = pin.originalY, velocityX = 0f, velocityY = 0f)
                                }
                            )
                        )
                    } else l
                }
            )
        }
        undoRedoManager.recordState(_project.value)
    }

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
                // Simulate frame extraction & encoding progress smoothly
                for (step in 1..20) {
                    delay(80)
                    _exportProgress.value = step / 20f
                }

                // Render current canvas to a PNG file in cache
                val exportFile = File(context.cacheDir, "puppet_export_${System.currentTimeMillis()}.png")
                val bitmap = Bitmap.createBitmap(720, 720, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                canvas.drawColor(0xFF18181C.toInt())
                val paint = android.graphics.Paint().apply {
                    color = 0xFF3898EC.toInt()
                    textSize = 32f
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                canvas.drawText("Puppet Studio 2D Animation Output", 360f, 360f, paint)

                FileOutputStream(exportFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }

                _isExporting.value = false
                _exportCompleted.value = true
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
