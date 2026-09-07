package com.example.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.PuppetModifier
import com.example.ui.components.*

@Composable
fun PuppetStudioScreen(
    viewModel: PuppetViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val project by viewModel.project.collectAsStateWithLifecycle()
    val activeTool by viewModel.activeTool.collectAsStateWithLifecycle()
    val puppetMode by viewModel.puppetMode.collectAsStateWithLifecycle()
    val brushSize by viewModel.brushSize.collectAsStateWithLifecycle()
    val brushColor by viewModel.brushColor.collectAsStateWithLifecycle()
    val selectedShapeType by viewModel.selectedShapeType.collectAsStateWithLifecycle()
    val isShapeFilled by viewModel.isShapeFilled.collectAsStateWithLifecycle()
    val currentText by viewModel.currentText.collectAsStateWithLifecycle()
    val textMode by viewModel.textMode.collectAsStateWithLifecycle()
    val viewportOffset by viewModel.viewportOffset.collectAsStateWithLifecycle()
    val zoomScale by viewModel.zoomScale.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()

    val showColorPicker by viewModel.showColorPicker.collectAsStateWithLifecycle()
    val showNewTimelineDialog by viewModel.showNewTimelineDialog.collectAsStateWithLifecycle()
    val showExportDialog by viewModel.showExportDialog.collectAsStateWithLifecycle()
    val showLayersPanel by viewModel.showLayersPanel.collectAsStateWithLifecycle()
    val isExporting by viewModel.isExporting.collectAsStateWithLifecycle()
    val exportProgress by viewModel.exportProgress.collectAsStateWithLifecycle()
    val exportCompleted by viewModel.exportCompleted.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    val activeLayer = project.layers.find { it.id == project.activeLayerId }
    val activePuppetModifier = activeLayer?.puppetModifier ?: PuppetModifier()

    // Enforce RTL layout for Persian UI overall
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            modifier = modifier.fillMaxSize().testTag("puppet_studio_scaffold"),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                StudioTopBar(
                    projectName = project.name,
                    canUndo = true,
                    canRedo = true,
                    onUndo = { viewModel.undo() },
                    onRedo = { viewModel.redo() },
                    onSave = {
                        viewModel.saveProject(context)
                        Toast.makeText(context, "پروژه با موفقیت ذخیره شد.", Toast.LENGTH_SHORT).show()
                    },
                    onExport = { viewModel.toggleExportDialog(true) },
                    onToggleLayers = { viewModel.toggleLayersPanel() },
                    layersCount = project.layers.size,
                    showGrid = project.settings.showGrid,
                    onToggleGrid = {
                        // toggle grid in settings
                    }
                )
            },
            bottomBar = {
                TimelineBar(
                    timeline = project.timeline,
                    isPlaying = isPlaying,
                    onPlayPauseToggle = { viewModel.togglePlayPause() },
                    onStop = { viewModel.stopPlayback() },
                    onSelectFrame = { viewModel.selectFrame(it) },
                    onAddFrame = { viewModel.addFrame() },
                    onDuplicateFrame = { viewModel.duplicateFrame() },
                    onDeleteFrame = { viewModel.deleteFrame() },
                    onToggleLoop = { viewModel.toggleLoop() },
                    onToggleOnionSkin = { viewModel.toggleOnionSkin() },
                    onFpsChange = { viewModel.changeFps(it) },
                    onOpenNewTimelineDialog = { viewModel.toggleNewTimelineDialog(true) },
                    onAddAudioTrack = { viewModel.addAudioTrack() }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Central Interactive Canvas (kept LTR for technical coordinates/rulers)
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    PuppetCanvas(
                        project = project,
                        activeTool = activeTool,
                        puppetMode = puppetMode,
                        brushSize = brushSize,
                        brushColor = brushColor,
                        viewportOffset = viewportOffset,
                        onViewportOffsetChange = { viewModel.setViewportOffset(it) },
                        zoomScale = zoomScale,
                        onZoomScaleChange = { viewModel.setZoomScale(it) },
                        onAddStrokePoint = { pt, isEraser -> viewModel.addStrokePoint(pt, isEraser) },
                        onFinishStroke = { viewModel.finishStroke() },
                        onUpdateLayerTransform = { id, t, commit -> viewModel.updateLayerTransform(id, t, commit) },
                        onAddPuppetPin = { lId, x, y -> viewModel.addPuppetPin(lId, x, y) },
                        onMovePuppetPin = { lId, pId, x, y, commit -> viewModel.movePuppetPin(lId, pId, x, y, commit) },
                        onDeletePuppetPin = { lId, pId -> viewModel.deletePuppetPin(lId, pId) },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Top Contextual Tool Options Bar (Floats directly beneath top bar)
                ContextualToolBar(
                    currentTool = activeTool,
                    brushSize = brushSize,
                    onBrushSizeChange = { viewModel.setBrushSize(it) },
                    currentColor = brushColor,
                    onOpenColorPicker = { viewModel.toggleColorPicker(true) },
                    puppetMode = puppetMode,
                    onPuppetModeChange = { viewModel.setPuppetMode(it) },
                    puppetModifier = activePuppetModifier,
                    onUpdatePuppetModifier = { activeLayer?.let { l -> viewModel.updatePuppetModifier(l.id, it) } },
                    selectedShapeType = selectedShapeType,
                    onShapeTypeChange = { viewModel.setSelectedShapeType(it) },
                    isShapeFilled = isShapeFilled,
                    onToggleShapeFilled = { viewModel.toggleShapeFilled() },
                    currentText = currentText,
                    onTextChange = { viewModel.setCurrentText(it) },
                    textMode = textMode,
                    onTextModeChange = { viewModel.setTextMode(it) },
                    onResetTransform = { activeLayer?.let { viewModel.resetTransform(it.id) } },
                    onResetPuppetPose = { activeLayer?.let { viewModel.resetPuppetPose(it.id) } },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                )

                // Vertical Tool Bar on Right/Left edge
                StudioToolBar(
                    activeTool = activeTool,
                    onSelectTool = { viewModel.selectTool(it) },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 8.dp)
                )

                // Slide-in Layers Panel
                AnimatedVisibility(
                    visible = showLayersPanel,
                    enter = slideInHorizontally(initialOffsetX = { it }),
                    exit = slideOutHorizontally(targetOffsetX = { it }),
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    LayersPanel(
                        layers = project.layers,
                        activeLayerId = project.activeLayerId,
                        onSelectLayer = { viewModel.selectLayer(it) },
                        onToggleVisibility = { viewModel.toggleLayerVisibility(it) },
                        onToggleLock = { viewModel.toggleLayerLock(it) },
                        onOpacityChange = { id, op -> viewModel.updateLayerOpacity(id, op) },
                        onBlendModeChange = { id, bm -> viewModel.updateLayerBlendMode(id, bm) },
                        onAddLayer = { viewModel.addLayer(it) },
                        onDuplicateLayer = { viewModel.duplicateLayer(it) },
                        onDeleteLayer = { viewModel.deleteLayer(it) },
                        onMoveLayerUp = { viewModel.moveLayerUp(it) },
                        onMoveLayerDown = { viewModel.moveLayerDown(it) },
                        onClose = { viewModel.toggleLayersPanel() }
                    )
                }

                // Safe Color Picker Dialog
                if (showColorPicker) {
                    SafeColorPickerDialog(
                        initialColor = brushColor,
                        onColorSelected = { col ->
                            viewModel.setBrushColor(col)
                            viewModel.toggleColorPicker(false)
                        },
                        onDismiss = { viewModel.toggleColorPicker(false) }
                    )
                }

                // New Timeline Dialog
                if (showNewTimelineDialog) {
                    NewTimelineDialog(
                        onSelectMode = { mode -> viewModel.createTimeline(mode) },
                        onDismiss = { viewModel.toggleNewTimelineDialog(false) }
                    )
                }

                // Export Dialog
                if (showExportDialog) {
                    ExportDialog(
                        isExporting = isExporting,
                        exportProgress = exportProgress,
                        onStartExport = { format -> viewModel.startExport(context, format) },
                        onCancel = { viewModel.toggleExportDialog(false) },
                        onShare = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "image/png"
                                putExtra(Intent.EXTRA_TEXT, "خروجی انیمیشن ساخته شده با Puppet Studio 2D")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "اشتراک‌گذاری انیمیشن"))
                        },
                        exportCompleted = exportCompleted
                    )
                }
            }
        }
    }
}
