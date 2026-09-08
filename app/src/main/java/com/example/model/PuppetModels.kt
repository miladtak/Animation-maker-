package com.example.model

import java.util.UUID

enum class ToolType {
    SELECT_MOVE,
    PUPPET,
    BRUSH,
    BRUSH_3D,
    ERASER,
    SHAPES,
    TEXT,
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
    DARKEN,
    LIGHTEN,
    COLOR_DODGE,
    COLOR_BURN,
    HARD_LIGHT,
    SOFT_LIGHT,
    DIFFERENCE,
    EXCLUSION,
    HUE,
    SATURATION,
    COLOR,
    LUMINOSITY
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

enum class BoneControlShape {
    CIRCLE,   // حلقه و مفصل دایره‌ای (مانند زانوها و دست‌ها در عکس)
    SQUARE,   // دستگیره کنترلی مربعی (مانند سینه، گردن و لگن در عکس)
    DIAMOND   // لوزی کنترلی
}

data class PuppetBone(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "استخوان",
    val parentId: String? = null, // کلید اتصال سلسله‌مراتبی مفاصل
    val startX: Float = 0f,
    val startY: Float = 0f,
    val length: Float = 80f,
    val angle: Float = 0f, // زاویه محلی نسبت به والد (درجه)
    val globalStartX: Float = 0f, // محاسبه شده توسط موتور FK
    val globalStartY: Float = 0f,
    val globalEndX: Float = 0f,
    val globalEndY: Float = 0f,
    val globalAngle: Float = 0f,
    val color: Long = 0xFF00E5FF, // فیروزه‌ای مطابق عکس کاربر
    val controlShape: BoneControlShape = BoneControlShape.CIRCLE
)

data class BoneWeight(
    val boneId: String,
    val weight: Float // وزن بین 0.0 تا 1.0 (مجموع وزن‌های هر نقطه = 1.0)
)

data class WeightedVertex(
    val id: String = UUID.randomUUID().toString(),
    val originalX: Float,
    val originalY: Float,
    val weights: List<BoneWeight> = emptyList()
)

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
    BALLOON,
    HEAVY_STONE,
    WOOD,
    METAL,
    SPRING
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
    CAPSULE,
    BALLOON,
    HEAD_NECK,
    BODY_TORSO,
    FREE_CONTOUR
}

data class VolumeContour(
    val topScale: Float = 1.0f,
    val upperMidScale: Float = 1.0f,
    val lowerMidScale: Float = 1.0f,
    val bottomScale: Float = 1.0f,
    val verticalCurve: Float = 0f
)

enum class RulerUnit(val factorFromPx: Float, val label: String) {
    PIXELS(1.0f, "px"),
    POINTS(0.75f, "pt"),
    INCHES(1f / 160f, "in"),
    MILLIMETERS(25.4f / 160f, "mm"),
    CENTIMETERS(2.54f / 160f, "cm")
}

enum class CanvasPreset(val title: String, val width: Float, val height: Float, val ratioLabel: String) {
    STORY_9_16("استوری / ریلز", 1080f, 1920f, "9:16"),
    CINEMATIC_16_9("سینمایی / یوتیوب", 1920f, 1080f, "16:9"),
    SQUARE_1_1("پست اینستاگرام", 1080f, 1080f, "1:1"),
    CUSTOM("اندازه دلخواه", 1080f, 1080f, "دلخواه")
}

data class PointData(
    val x: Float = 0f,
    val y: Float = 0f,
    val pressure: Float = 1.0f
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
    val scale: Float = 1f,
    val depth: Float = 0f,
    val group: Int = 0,
    val isMirrored: Boolean = false,
    val mirrorPinId: String? = null
)

data class PuppetModifier(
    val mesh: PuppetMesh = PuppetMesh(),
    val pins: List<PuppetPin> = emptyList(),
    val bones: List<PuppetBone> = emptyList(),
    val boneWeights: Map<Int, List<BoneWeight>> = emptyMap(),
    val strokeBoneWeights: Map<Int, List<BoneWeight>> = emptyMap(), // vertex index in strokes to bone weights
    val physicsPreset: PhysicsPreset = PhysicsPreset.NONE,
    val density: MeshDensity = MeshDensity.MEDIUM,
    val showMesh: Boolean = true,
    val showBones: Boolean = true,
    val selectedBoneId: String? = null,
    val deformerType: InvisibleDeformerType = InvisibleDeformerType.NONE,
    val deformerRotation: Float = 0f,
    val deformerPitch: Float = 0f,
    val deformerYaw: Float = 0f,
    val deformerRadius: Float = 150f,
    val volumeContour: VolumeContour = VolumeContour(),
    val physicsParams: PhysicsParams = PhysicsParams()
)

data class DrawingStroke(
    val color: Long = 0xFFFFFFFF,
    val strokeWidth: Float = 12f,
    val points: List<PointData> = emptyList(),
    val isEraser: Boolean = false,
    val is3D: Boolean = false,
    val depthAngle: Float = 0f,
    val materialPreset: String = "SOLID",
    val textureUri: String? = null
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
    val chromaKeyEnabled: Boolean = false,
    val chromaKeyThreshold: Float = 0.35f,
    val puppetModifier: PuppetModifier = PuppetModifier()
)

data class Frame(
    val index: Int = 0,
    val layerOverrides: Map<String, LayerTransform> = emptyMap(),
    val pinOverrides: Map<String, List<PuppetPin>> = emptyMap(),
    val boneOverrides: Map<String, List<PuppetBone>> = emptyMap(),
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

enum class CanvasBackgroundMode(val label: String, val colorLong: Long) {
    DARK("مشکی استودیویی", 0xFF1E1E24),
    WHITE("سفید", 0xFFFFFFFF),
    GRAY("خاکستری", 0xFF808080),
    TRANSPARENT("شطرنجی (شفاف)", 0x00000000),
    GREEN_SCREEN("پرده سبز (کروماکی)", 0xFF00FF00)
}

data class ProjectSettings(
    val unit: RulerUnit = RulerUnit.PIXELS,
    val showGrid: Boolean = true,
    val showRulers: Boolean = true,
    val snapToGrid: Boolean = true,
    val gridSpacing: Float = 40f,
    val backgroundMode: CanvasBackgroundMode = CanvasBackgroundMode.DARK
)

data class Scene(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "صحنه ۱",
    val timeline: Timeline = Timeline(),
    val layers: List<Layer> = emptyList(),
    val frames: List<Frame> = emptyList()
)

data class ProjectMetadata(
    val id: String,
    val name: String,
    val lastModified: Long = System.currentTimeMillis(),
    val canvasWidth: Float = 1080f,
    val canvasHeight: Float = 1080f,
    val layersCount: Int = 1,
    val totalFrames: Int = 24
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
    val scenes: List<Scene> = emptyList(),
    val activeSceneIndex: Int = 0,
    val settings: ProjectSettings = ProjectSettings()
)
