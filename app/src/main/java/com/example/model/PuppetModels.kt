package com.example.model

import java.util.UUID

enum class ToolType {
    SELECT_MOVE,
    BRUSH,
    ERASER,
    SHAPES,
    TEXT,
    PUPPET,
    COLOR_PICKER,
    HAND,
    ZOOM
}

enum class LayerType {
    RASTER,
    SHAPE,
    TEXT,
    IMAGE,
    VIDEO_REF
}

enum class BlendModeType {
    NORMAL,
    MULTIPLY,
    SCREEN,
    OVERLAY,
    ADD,
    DARKEN,
    LIGHTEN,
    DIFFERENCE
}

enum class ShapeType {
    RECT,
    CIRCLE,
    TRIANGLE,
    STAR,
    CUBE_25D
}

enum class TextMode {
    NORMAL,
    EXTRUDE_3D,
    ARC
}

enum class TimelineMode {
    DUPLICATE,
    LINKED,
    BLANK
}

enum class PinType {
    STATIC,
    DYNAMIC,
    ROTATION,
    SCALE,
    CONTROLLER
}

enum class PhysicsPreset {
    NONE,
    CLOTH,
    RUBBER,
    JELLY,
    HAIR,
    PLANT,
    WIND,
    WATER,
    FIRE,
    BALLOON
}

enum class MeshDensity(val cols: Int, val rows: Int) {
    LOW(6, 6),
    MEDIUM(12, 12),
    HIGH(20, 20)
}

enum class InvisibleDeformerType {
    NONE,
    SPHERE,
    CYLINDER,
    CAPSULE
}

enum class RulerUnit(val factorFromPx: Float, val label: String) {
    PIXELS(1.0f, "px"),
    POINTS(0.75f, "pt"),
    INCHES(1f / 160f, "in"),
    MILLIMETERS(25.4f / 160f, "mm"),
    CENTIMETERS(2.54f / 160f, "cm")
}

data class PointData(
    val x: Float = 0f,
    val y: Float = 0f
)

data class TriangleIndices(
    val p0: Int = 0,
    val p1: Int = 0,
    val p2: Int = 0
)

data class PuppetMesh(
    val vertices: List<PointData> = emptyList(),
    val originalVertices: List<PointData> = emptyList(),
    val triangles: List<TriangleIndices> = emptyList(),
    val width: Float = 300f,
    val height: Float = 300f
)

data class PhysicsParams(
    val gravity: Float = 9.8f,
    val wind: Float = 0f,
    val damping: Float = 0.85f,
    val stiffness: Float = 0.4f,
    val elasticity: Float = 0.6f,
    val jiggle: Float = 0.2f
)

data class PuppetPin(
    val id: String = UUID.randomUUID().toString(),
    val x: Float = 0f,
    val y: Float = 0f,
    val originalX: Float = 0f,
    val originalY: Float = 0f,
    val weight: Float = 1.0f,
    val pinType: PinType = PinType.STATIC,
    val velocityX: Float = 0f,
    val velocityY: Float = 0f,
    val radius: Float = 90f,
    val angle: Float = 0f,
    val scale: Float = 1f
)

data class PuppetModifier(
    val mesh: PuppetMesh = PuppetMesh(),
    val pins: List<PuppetPin> = emptyList(),
    val physicsPreset: PhysicsPreset = PhysicsPreset.NONE,
    val density: MeshDensity = MeshDensity.MEDIUM,
    val showMesh: Boolean = true,
    val deformerType: InvisibleDeformerType = InvisibleDeformerType.NONE,
    val deformerRotation: Float = 0f,
    val deformerRadius: Float = 150f,
    val physicsParams: PhysicsParams = PhysicsParams()
)

data class DrawingStroke(
    val color: Long = 0xFFFFFFFF,
    val strokeWidth: Float = 12f,
    val points: List<PointData> = emptyList(),
    val isEraser: Boolean = false
)

data class ShapeData(
    val shapeType: ShapeType = ShapeType.RECT,
    val strokeWidth: Float = 6f,
    val isFilled: Boolean = true,
    val fillColor: Long = 0xFF3898EC,
    val strokeColor: Long = 0xFFFFFFFF,
    val radius: Float = 80f,
    val width: Float = 200f,
    val height: Float = 200f
)

data class TextData(
    val text: String = "استودیو پاپت",
    val fontSize: Float = 36f,
    val textColor: Long = 0xFFF0F0F5,
    val mode: TextMode = TextMode.NORMAL,
    val arcRadius: Float = 140f
)

data class LayerTransform(
    val translationX: Float = 0f,
    val translationY: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotation: Float = 0f,
    val pivotX: Float = 0.5f,
    val pivotY: Float = 0.5f
)

data class VideoRefData(
    val videoUri: String = "",
    val opacity: Float = 0.4f,
    val currentFrame: Int = 0,
    val totalFrames: Int = 60,
    val isUnderlay: Boolean = true
)

data class Layer(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "لایه جدید",
    val type: LayerType = LayerType.RASTER,
    val isVisible: Boolean = true,
    val isLocked: Boolean = false,
    val opacity: Float = 1.0f,
    val blendMode: BlendModeType = BlendModeType.NORMAL,
    val transform: LayerTransform = LayerTransform(),
    val rasterStrokes: List<DrawingStroke> = emptyList(),
    val shapeData: ShapeData? = null,
    val textData: TextData? = null,
    val imageUri: String? = null,
    val videoRefData: VideoRefData? = null,
    val puppetModifier: PuppetModifier = PuppetModifier()
)

data class Frame(
    val index: Int = 0,
    val layerOverrides: Map<String, LayerTransform> = emptyMap(),
    val pinOverrides: Map<String, List<PuppetPin>> = emptyMap(),
    val strokesPerLayer: Map<String, List<DrawingStroke>> = emptyMap()
)

data class AudioTrack(
    val name: String = "صداگذاری",
    val durationMs: Long = 0L,
    val isMuted: Boolean = false,
    val volume: Float = 1.0f
)

data class Timeline(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "تایم‌لاین ۱",
    val mode: TimelineMode = TimelineMode.DUPLICATE,
    val totalFrames: Int = 24,
    val currentFrame: Int = 0,
    val fps: Int = 24,
    val isLooping: Boolean = true,
    val isOnionSkinEnabled: Boolean = true,
    val onionSkinOpacity: Float = 0.35f,
    val audioTrack: AudioTrack? = null
)

data class ProjectSettings(
    val unit: RulerUnit = RulerUnit.PIXELS,
    val showGrid: Boolean = true,
    val showRulers: Boolean = true,
    val snapToGrid: Boolean = true,
    val gridSpacing: Float = 40f
)

data class Project(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "پروژه انیمیشن من",
    val canvasWidth: Float = 1080f,
    val canvasHeight: Float = 1080f,
    val layers: List<Layer> = emptyList(),
    val activeLayerId: String = "",
    val timeline: Timeline = Timeline(),
    val frames: List<Frame> = emptyList(),
    val settings: ProjectSettings = ProjectSettings()
)
