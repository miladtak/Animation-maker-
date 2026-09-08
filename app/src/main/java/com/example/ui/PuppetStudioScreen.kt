package com.example.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.model.PuppetModifier
import com.example.model.ToolType
import com.example.ui.components.*
import java.io.File
import java.io.FileOutputStream

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

    val isHomeScreenVisible by viewModel.isHomeScreenVisible.collectAsStateWithLifecycle()
    val isZenMode by viewModel.isZenMode.collectAsStateWithLifecycle()
    val recentProjects by viewModel.recentProjects.collectAsStateWithLifecycle()

    val brush3dMaterial by viewModel.brush3dMaterial.collectAsStateWithLifecycle()
    val brush3dDepth by viewModel.brush3dDepth.collectAsStateWithLifecycle()
    val isJoystickVisible by viewModel.isJoystickVisible.collectAsStateWithLifecycle()
    val activeBoneId by viewModel.activeBoneId.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    val activeLayer = project.layers.find { it.id == project.activeLayerId }
    val activePuppetModifier = activeLayer?.puppetModifier ?: PuppetModifier()

    // Load recent projects when screen opens
    LaunchedEffect(Unit) {
        viewModel.refreshRecentProjects(context)
    }

    // Media reference picker (video/photo)
    val mediaReferenceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.addVideoReferenceLayer(uri.toString(), "مرجع تصویری")
            viewModel.hideHomeScreen()
            Toast.makeText(context, "مرجع تصویری به لایه‌ها اضافه شد.", Toast.LENGTH_SHORT).show()
        }
    }

    // Import file picker launcher for .puppet2d
    val fileImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val tempFile = File(context.cacheDir, "import_${System.currentTimeMillis()}.puppet2d")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                val success = viewModel.importPuppet2d(tempFile, context)
                if (success) {
                    Toast.makeText(context, "پروژه با موفقیت وارد شد.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "خطا در خواندن فایل پروژه.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "خطا در وارد کردن فایل: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Enforce RTL layout for Persian UI overall
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        if (isHomeScreenVisible) {
            HomeScreen(
                recentProjects = recentProjects,
                onSelectPreset = { preset -> viewModel.newProjectFromPreset(preset, context) },
                onOpenProject = { id -> viewModel.loadProjectById(context, id) },
                onDeleteProject = { id -> viewModel.deleteProject(context, id) },
                onRenameProject = { id, name -> viewModel.renameProject(context, id, name) },
                onImportPuppet2d = { fileImportLauncher.launch("*/*") },
                onImportPhotoVideoReference = { mediaReferenceLauncher.launch("*/*") },
                onResumeCurrentProject = { viewModel.hideHomeScreen() },
                hasActiveProject = project.layers.isNotEmpty()
            )
        } else {
            Scaffold(
                modifier = modifier.fillMaxSize().testTag("puppet_studio_scaffold"),
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    if (!isZenMode) {
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
                                viewModel.setProjectSettings(
                                    project.settings.copy(showGrid = !project.settings.showGrid)
                                )
                            },
                            onOpenHome = {
                                viewModel.refreshRecentProjects(context)
                                viewModel.showHomeScreen()
                            },
                            isZenMode = isZenMode,
                            onToggleZenMode = { viewModel.toggleZenMode() }
                        )
                    }
                },
                bottomBar = {
                    if (!isZenMode) {
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
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(if (isZenMode) PaddingValues(0.dp) else innerPadding)
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
                            selectedBoneId = activeBoneId,
                            onSelectBone = { viewModel.selectBone(it) },
                            onRotateBone = { lId, bId, deltaAngle, commit -> viewModel.rotateBone(lId, bId, deltaAngle, commit) },
                            onAddBone = { lId, x, y -> viewModel.addBone(lId, x, y) },
                            onBackgroundModeChange = { viewModel.setCanvasBackgroundMode(it) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Top Contextual Tool Options Bar (Visible when not in Zen Mode)
                    if (!isZenMode) {
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
                            onCommitTextToCanvas = { viewModel.commitTextToCanvas() },
                            onResetTransform = { activeLayer?.let { viewModel.resetTransform(it.id) } },
                            onResetPuppetPose = { activeLayer?.let { viewModel.resetPuppetPose(it.id) } },
                            isZenMode = isZenMode,
                            onToggleZenMode = { viewModel.toggleZenMode() },
                            isJoystickVisible = isJoystickVisible,
                            onToggleJoystick = { viewModel.toggleJoystick() },
                            onClonePuppetArmy = { viewModel.clonePuppetArmy() },
                            brush3dMaterial = brush3dMaterial,
                            onBrush3dMaterialChange = { viewModel.setBrush3dMaterial(it) },
                            brush3dDepth = brush3dDepth,
                            onBrush3dDepthChange = { viewModel.setBrush3dDepth(it) },
                            onPick3dTextureImage = { mediaReferenceLauncher.launch("image/*") },
                            selectedBoneId = activeBoneId,
                            onSelectBone = { viewModel.selectBone(it) },
                            onSetupDefaultSkeleton = { activeLayer?.let { viewModel.setupDefaultSkeleton(it.id) } },
                            onAutoSkinning = { activeLayer?.let { viewModel.autoSkinning(it.id) } },
                            onDeleteSelectedBone = {
                                activeLayer?.let { l ->
                                    activeBoneId?.let { bId -> viewModel.deleteBone(l.id, bId) }
                                }
                            },
                            onBoneAngleChange = { angle ->
                                activeLayer?.let { l ->
                                    activeBoneId?.let { bId -> viewModel.setBoneAngle(l.id, bId, angle, true) }
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 8.dp)
                        )
                    }

                    // FlipaClip-like Draggable, Auto-Hiding Studio ToolBar
                    if (!isZenMode) {
                        StudioToolBar(
                            activeTool = activeTool,
                            onSelectTool = { viewModel.selectTool(it) }
                        )
                    }

                    // Floating 360° Rotation & 3D Globe Trackball Widget (گوی کره زمین)
                    if (isJoystickVisible && !isZenMode) {
                        val activeBone = activePuppetModifier.bones.find { it.id == activeBoneId }
                        val currentAngle = when {
                            activeBone != null -> activeBone.angle
                            activeTool == ToolType.SELECT_MOVE && activeLayer != null -> activeLayer.transform.rotation
                            else -> activePuppetModifier.deformerRotation
                        }
                        val title = when {
                            activeBone != null -> "استخوان: ${activeBone.name}"
                            activeTool == ToolType.SELECT_MOVE && activeLayer != null -> "چرخش: ${activeLayer.name}"
                            else -> "گوی سه‌بعدی پاپت (کره زمین)"
                        }

                        FloatingJoystickWidget(
                            currentAngle = currentAngle,
                            currentPitch = activePuppetModifier.deformerPitch,
                            currentYaw = activePuppetModifier.deformerYaw,
                            title = title,
                            onAngleChange = { newAngle ->
                                if (activeBone != null && activeLayer != null) {
                                    val delta = newAngle - activeBone.angle
                                    viewModel.rotateBone(activeLayer.id, activeBone.id, delta, false)
                                } else if (activeTool == ToolType.SELECT_MOVE && activeLayer != null) {
                                    viewModel.updateLayerTransform(
                                        activeLayer.id,
                                        activeLayer.transform.copy(rotation = newAngle),
                                        false
                                    )
                                } else {
                                    viewModel.setDeformerRotation(newAngle)
                                }
                            },
                            on3DChange = { rot, pitch, yaw ->
                                if (activeBone != null && activeLayer != null) {
                                    val delta = rot - activeBone.angle
                                    viewModel.rotateBone(activeLayer.id, activeBone.id, delta, false)
                                } else if (activeTool == ToolType.SELECT_MOVE && activeLayer != null) {
                                    viewModel.updateLayerTransform(
                                        activeLayer.id,
                                        activeLayer.transform.copy(rotation = rot),
                                        false
                                    )
                                } else {
                                    viewModel.setDeformer3D(rot, pitch, yaw)
                                }
                            },
                            onClose = { viewModel.toggleJoystick(false) }
                        )
                    }

                    // Floating Exit Button for Zen Mode
                    if (isZenMode) {
                        ElevatedButton(
                            onClick = { viewModel.exitZenMode() },
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(16.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
                            )
                        ) {
                            Icon(Icons.Default.FullscreenExit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.exit_zen_mode), fontSize = 11.sp)
                        }
                    }

                    // Slide-in Layers Panel
                    AnimatedVisibility(
                        visible = showLayersPanel && !isZenMode,
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
                                    type = "video/mp4"
                                    putExtra(Intent.EXTRA_TEXT, "خروجی انیمیشن ساخته شده با استودیو پاپت 2D")
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
}
