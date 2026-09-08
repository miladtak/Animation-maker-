package com.example.engine

import com.example.model.*
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

object PuppetWarpEngine {

    /**
     * Generates a 2D triangulated mesh over the specified rectangle.
     */
    fun generateMesh(width: Float, height: Float, density: MeshDensity): PuppetMesh {
        val cols = density.cols
        val rows = density.rows
        val vertices = mutableListOf<PointData>()
        val triangles = mutableListOf<TriangleIndices>()

        val stepX = width / cols
        val stepY = height / rows

        val halfW = width / 2f
        val halfH = height / 2f

        for (r in 0..rows) {
            for (c in 0..cols) {
                val vx = (c * stepX) - halfW
                val vy = (r * stepY) - halfH
                vertices.add(PointData(vx, vy))
            }
        }

        val rowStride = cols + 1
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val i0 = r * rowStride + c
                val i1 = i0 + 1
                val i2 = (r + 1) * rowStride + c
                val i3 = i2 + 1

                // Two triangles per quad
                triangles.add(TriangleIndices(i0, i1, i2))
                triangles.add(TriangleIndices(i1, i3, i2))
            }
        }

        return PuppetMesh(
            vertices = vertices,
            originalVertices = vertices,
            triangles = triangles,
            width = width,
            height = height
        )
    }

    /**
     * Deforms a single 2D point using Pin positions via smooth RBF and optional 3D deformers.
     */
    fun deformPoint(
        origX: Float,
        origY: Float,
        pins: List<PuppetPin>,
        deformerType: InvisibleDeformerType = InvisibleDeformerType.NONE,
        deformerRotation: Float = 0f,
        deformerPitch: Float = 0f,
        deformerYaw: Float = 0f,
        deformerRadius: Float = 150f,
        volumeContour: com.example.model.VolumeContour = com.example.model.VolumeContour(),
        width: Float = 300f,
        height: Float = 300f
    ): PointData {
        if (pins.isEmpty() && deformerType == InvisibleDeformerType.NONE && deformerPitch == 0f && deformerYaw == 0f) {
            return PointData(origX, origY)
        }

        var totalWeight = 0f
        var deltaX = 0f
        var deltaY = 0f

        for (pin in pins) {
            val dx = origX - pin.originalX
            val dy = origY - pin.originalY
            val distSq = dx * dx + dy * dy
            val radiusSq = pin.radius * pin.radius

            val weight = exp(-distSq / (2f * radiusSq)) * pin.weight
            if (weight > 0.0001f) {
                val pinDeltaX = pin.x - pin.originalX
                val pinDeltaY = pin.y - pin.originalY
                deltaX += pinDeltaX * weight
                deltaY += pinDeltaY * weight
                totalWeight += weight
            }
        }

        var currentX = if (totalWeight > 0.0001f) {
            origX + (deltaX / (totalWeight + 0.15f))
        } else {
            origX
        }

        var currentY = if (totalWeight > 0.0001f) {
            origY + (deltaY / (totalWeight + 0.15f))
        } else {
            origY
        }

        if (deformerType != InvisibleDeformerType.NONE) {
            val halfW = width / 2f
            val halfH = (height / 2f).coerceAtLeast(50f)
            val normX = ((currentX / halfW).coerceIn(-1f, 1f))
            val angleRad = (normX * Math.PI.toFloat() * 0.5f) + Math.toRadians(deformerRotation.toDouble()).toFloat()

            when (deformerType) {
                InvisibleDeformerType.CYLINDER -> {
                    val cylinderX = deformerRadius * sin(angleRad)
                    val depthZ = deformerRadius * cos(angleRad)
                    val perspectiveScale = (depthZ + deformerRadius * 1.8f) / (deformerRadius * 2f)
                    currentX = cylinderX
                    currentY *= perspectiveScale.coerceIn(0.5f, 1.5f)
                }
                InvisibleDeformerType.SPHERE -> {
                    val normY = (currentY / halfH).coerceIn(-1f, 1f)
                    val latRad = normY * Math.PI.toFloat() * 0.5f
                    val sphereX = deformerRadius * sin(angleRad) * cos(latRad)
                    val sphereY = deformerRadius * sin(latRad)
                    currentX = sphereX
                    currentY = sphereY
                }
                InvisibleDeformerType.CAPSULE -> {
                    val capX = deformerRadius * sin(angleRad)
                    currentX = capX
                }
                InvisibleDeformerType.BALLOON -> {
                    val inflateFactor = 1f + (deformerRadius / 300f) * sin(Math.toRadians(deformerRotation.toDouble()).toFloat() + 1f).coerceIn(0.1f, 1.8f)
                    currentX *= inflateFactor
                    currentY *= inflateFactor
                }
                InvisibleDeformerType.HEAD_NECK,
                InvisibleDeformerType.BODY_TORSO,
                InvisibleDeformerType.FREE_CONTOUR -> {
                    val t = ((currentY + halfH) / (halfH * 2f)).coerceIn(0f, 1f)

                    val top = when (deformerType) {
                        InvisibleDeformerType.HEAD_NECK -> volumeContour.topScale * 1.05f
                        InvisibleDeformerType.BODY_TORSO -> volumeContour.topScale * 1.35f
                        else -> volumeContour.topScale
                    }
                    val upperMid = when (deformerType) {
                        InvisibleDeformerType.HEAD_NECK -> volumeContour.upperMidScale * 1.22f
                        InvisibleDeformerType.BODY_TORSO -> volumeContour.upperMidScale * 1.2f
                        else -> volumeContour.upperMidScale
                    }
                    val lowerMid = when (deformerType) {
                        InvisibleDeformerType.HEAD_NECK -> volumeContour.lowerMidScale * 0.85f
                        InvisibleDeformerType.BODY_TORSO -> volumeContour.lowerMidScale * 0.72f
                        else -> volumeContour.lowerMidScale
                    }
                    val bottom = when (deformerType) {
                        InvisibleDeformerType.HEAD_NECK -> volumeContour.bottomScale * 0.52f
                        InvisibleDeformerType.BODY_TORSO -> volumeContour.bottomScale * 1.18f
                        else -> volumeContour.bottomScale
                    }

                    val scaleFactor = when {
                        t < 0.333f -> {
                            val localT = t / 0.333f
                            val smooth = localT * localT * (3f - 2f * localT)
                            top + (upperMid - top) * smooth
                        }
                        t < 0.666f -> {
                            val localT = (t - 0.333f) / 0.333f
                            val smooth = localT * localT * (3f - 2f * localT)
                            upperMid + (lowerMid - upperMid) * smooth
                        }
                        else -> {
                            val localT = (t - 0.666f) / 0.334f
                            val smooth = localT * localT * (3f - 2f * localT)
                            lowerMid + (bottom - lowerMid) * smooth
                        }
                    }

                    val rotRad = Math.toRadians(deformerRotation.toDouble()).toFloat()
                    val cosRot = cos(rotRad)
                    val sinRot = sin(rotRad)

                    currentX = currentX * scaleFactor * cosRot
                    currentY += (currentX * sinRot * 0.12f)
                }
                InvisibleDeformerType.NONE -> {}
            }
        }

        // Apply 3D Trackball Rotation (Pitch and Yaw)
        if (deformerPitch != 0f || deformerYaw != 0f) {
            val pitchRad = Math.toRadians(deformerPitch.toDouble()).toFloat()
            val yawRad = Math.toRadians(deformerYaw.toDouble()).toFloat()
            val cosPitch = cos(pitchRad)
            val sinPitch = sin(pitchRad)
            val cosYaw = cos(yawRad)
            val sinYaw = sin(yawRad)

            // 3D perspective rotation around X (pitch) and Y (yaw)
            val tiltedY = currentY * cosPitch - (currentX * 0.16f) * sinPitch
            val turnedX = currentX * cosYaw + (currentY * 0.16f) * sinYaw
            currentX = turnedX
            currentY = tiltedY
        }

        return PointData(currentX, currentY)
    }

    /**
     * Deforms a 2D point according to skeletal bones using Linear Blend Skinning (LBS).
     * When bones rotate or cascade via Forward Kinematics, points naturally bend and follow.
     */
    fun deformPointWithBones(
        origX: Float,
        origY: Float,
        bones: List<PuppetBone>
    ): PointData {
        if (bones.isEmpty()) return PointData(origX, origY)

        val boneInfluences = mutableListOf<Pair<PuppetBone, Float>>()
        var totalWeight = 0f

        for (bone in bones) {
            val dist = distanceToBoneSegment(origX, origY, bone)
            val radius = (bone.length * 2.0f).coerceIn(100f, 350f)
            if (dist < radius) {
                val factor = 1f - (dist / radius)
                val weight = factor * factor
                if (weight > 0.001f) {
                    boneInfluences.add(Pair(bone, weight))
                    totalWeight += weight
                }
            }
        }

        if (boneInfluences.isEmpty() || totalWeight < 0.0001f) {
            val nearest = bones.minByOrNull { distanceToBoneSegment(origX, origY, it) }
            if (nearest != null) {
                boneInfluences.add(Pair(nearest, 1.0f))
                totalWeight = 1.0f
            } else {
                return PointData(origX, origY)
            }
        }

        var finalX = 0f
        var finalY = 0f

        for ((bone, rawWeight) in boneInfluences) {
            val w = rawWeight / totalWeight
            val rad = Math.toRadians(bone.globalAngle.toDouble()).toFloat()
            val cosA = cos(rad)
            val sinA = sin(rad)

            val dx = origX - bone.startX
            val dy = origY - bone.startY

            val rotX = dx * cosA - dy * sinA
            val rotY = dx * sinA + dy * cosA

            finalX += (bone.globalStartX + rotX) * w
            finalY += (bone.globalStartY + rotY) * w
        }

        return PointData(finalX, finalY)
    }

    /**
     * Unified deformer pipeline: Bones -> Pins -> 3D Invisible Deformer.
     * All tools (Drawing brushes, Vector Shapes, Bones, Pins, Deformers) apply together harmoniously.
     */
    fun deformPointUnified(
        origX: Float,
        origY: Float,
        bones: List<PuppetBone>,
        pins: List<PuppetPin>,
        deformerType: InvisibleDeformerType = InvisibleDeformerType.NONE,
        deformerRotation: Float = 0f,
        deformerPitch: Float = 0f,
        deformerYaw: Float = 0f,
        deformerRadius: Float = 150f,
        volumeContour: VolumeContour = VolumeContour(),
        width: Float = 300f,
        height: Float = 300f
    ): PointData {
        // Step 1: Deform by skeletal bones if present
        val afterBones = if (bones.isNotEmpty()) {
            deformPointWithBones(origX, origY, bones)
        } else {
            PointData(origX, origY)
        }

        // Step 2: Deform by puppet pins and 3D volume deformers
        return deformPoint(
            origX = afterBones.x,
            origY = afterBones.y,
            pins = pins,
            deformerType = deformerType,
            deformerRotation = deformerRotation,
            deformerPitch = deformerPitch,
            deformerYaw = deformerYaw,
            deformerRadius = deformerRadius,
            volumeContour = volumeContour,
            width = width,
            height = height
        )
    }

    /**
     * Deforms the mesh vertices using Pin positions via smooth Radial Basis Function (RBF)
     * and optional Invisible Deformers (Sphere / Cylinder / Capsule 360 wrap / Head-Neck & Body Contours).
     */
    fun deformMesh(
        originalMesh: PuppetMesh,
        pins: List<PuppetPin>,
        deformerType: InvisibleDeformerType = InvisibleDeformerType.NONE,
        deformerRotation: Float = 0f,
        deformerPitch: Float = 0f,
        deformerYaw: Float = 0f,
        deformerRadius: Float = 150f,
        volumeContour: com.example.model.VolumeContour = com.example.model.VolumeContour()
    ): List<PointData> {
        if (originalMesh.originalVertices.isEmpty()) return emptyList()
        if (pins.isEmpty() && deformerType == InvisibleDeformerType.NONE && deformerPitch == 0f && deformerYaw == 0f) {
            return originalMesh.originalVertices
        }

        return originalMesh.originalVertices.map { orig ->
            deformPoint(
                origX = orig.x,
                origY = orig.y,
                pins = pins,
                deformerType = deformerType,
                deformerRotation = deformerRotation,
                deformerPitch = deformerPitch,
                deformerYaw = deformerYaw,
                deformerRadius = deformerRadius,
                volumeContour = volumeContour,
                width = originalMesh.width,
                height = originalMesh.height
            )
        }
    }

    /**
     * Steps physical simulation for dynamic pins using a lightweight Verlet/spring integrator.
     */
    fun stepPhysics(
        pins: List<PuppetPin>,
        preset: PhysicsPreset,
        params: PhysicsParams,
        timeSeconds: Float
    ): List<PuppetPin> {
        if (preset == PhysicsPreset.NONE || pins.isEmpty()) return pins

        return pins.map { pin ->
            if (pin.pinType == PinType.STATIC) return@map pin

            var vx = pin.velocityX
            var vy = pin.velocityY

            // Calculate preset specific physical forces
            when (preset) {
                PhysicsPreset.CLOTH -> {
                    val windOsc = sin(timeSeconds * 4f + pin.originalX * 0.02f) * 15f
                    vx += windOsc * 0.2f
                    vy += params.gravity * 0.1f
                }
                PhysicsPreset.WIND -> {
                    val windForce = sin(timeSeconds * 5f + pin.originalY * 0.03f) * 35f
                    vx += windForce * 0.35f
                }
                PhysicsPreset.JELLY -> {
                    val jiggle = sin(timeSeconds * 12f) * params.jiggle * 18f
                    vx += jiggle
                    vy += cos(timeSeconds * 12f) * params.jiggle * 18f
                }
                PhysicsPreset.RUBBER -> {
                    val bounce = sin(timeSeconds * 9f) * 12f
                    vy += bounce
                }
                PhysicsPreset.HAIR -> {
                    val sway = sin(timeSeconds * 3.5f + pin.originalY * 0.05f) * 20f
                    vx += sway * 0.25f
                    vy += 2f
                }
                PhysicsPreset.PLANT -> {
                    val sway = sin(timeSeconds * 2f + pin.originalX * 0.01f) * 15f
                    vx += sway * 0.15f
                }
                PhysicsPreset.WATER -> {
                    val wave = sin(timeSeconds * 6f + pin.originalX * 0.04f) * 25f
                    vy += wave * 0.2f
                }
                PhysicsPreset.FIRE -> {
                    val flame = sin(timeSeconds * 10f + pin.originalY * 0.05f) * 20f
                    vx += flame * 0.3f
                    vy -= 15f * 0.2f // upward motion
                }
                PhysicsPreset.BALLOON -> {
                    val inflate = sin(timeSeconds * 3f) * 12f
                    vx += (pin.originalX / 100f) * inflate * 0.1f
                    vy += (pin.originalY / 100f) * inflate * 0.1f
                }
                PhysicsPreset.HEAVY_STONE -> {
                    // Heavy stone: strong gravity, minimal wobble, high damping
                    vy += params.gravity * 0.25f
                    vx *= 0.7f
                }
                PhysicsPreset.WOOD -> {
                    // Stiff wood: very little micro-vibration
                    val woodJitter = sin(timeSeconds * 16f) * 2f
                    vx += woodJitter * 0.05f
                }
                PhysicsPreset.METAL -> {
                    // Metallic springiness: fast ping-pong decay
                    val metalPing = sin(timeSeconds * 20f) * 6f
                    vx += metalPing * 0.1f
                }
                PhysicsPreset.SPRING -> {
                    // Bouncy dynamic spring oscillation
                    val springBob = sin(timeSeconds * 8f) * 18f
                    vy += springBob * 0.3f
                }
                PhysicsPreset.NONE -> {}
            }

            // Return-to-origin spring force
            val springDx = pin.originalX - pin.x
            val springDy = pin.originalY - pin.y
            vx += springDx * params.stiffness
            vy += springDy * params.stiffness

            // Damping
            vx *= params.damping
            vy *= params.damping

            val newX = pin.x + vx
            val newY = pin.y + vy

            pin.copy(
                x = newX,
                y = newY,
                velocityX = vx,
                velocityY = vy
            )
        }
    }

    /**
     * Updates Forward Kinematics (FK) positions for a hierarchical bone tree.
     * When a parent bone rotates or moves, all child bones cascade naturally.
     */
    fun updateBonePositions(bones: List<PuppetBone>): List<PuppetBone> {
        if (bones.isEmpty()) return emptyList()

        val updatedMap = mutableMapOf<String, PuppetBone>()
        val remaining = bones.toMutableList()
        var iterations = 0
        val maxIterations = bones.size + 2

        while (remaining.isNotEmpty() && iterations < maxIterations) {
            iterations++
            val iterator = remaining.iterator()
            while (iterator.hasNext()) {
                val bone = iterator.next()
                val parent = bone.parentId?.let { updatedMap[it] }

                if (bone.parentId == null || parent != null || !bones.any { it.id == bone.parentId }) {
                    // Root bone or parent already resolved
                    val (startX, startY, globalAngle) = if (parent != null) {
                        Triple(parent.globalEndX, parent.globalEndY, parent.globalAngle + bone.angle)
                    } else {
                        Triple(bone.startX, bone.startY, bone.angle)
                    }

                    val rad = Math.toRadians(globalAngle.toDouble()).toFloat()
                    val endX = startX + bone.length * cos(rad)
                    val endY = startY + bone.length * sin(rad)

                    val updated = bone.copy(
                        globalStartX = startX,
                        globalStartY = startY,
                        globalEndX = endX,
                        globalEndY = endY,
                        globalAngle = globalAngle
                    )
                    updatedMap[bone.id] = updated
                    iterator.remove()
                }
            }
        }

        // Fallback for any cyclic reference: keep computed or original
        return bones.map { updatedMap[it.id] ?: it }
    }

    /**
     * Calculates distance from a 2D point to a finite bone line segment.
     */
    fun distanceToBoneSegment(px: Float, py: Float, bone: PuppetBone): Float {
        val ax = bone.globalStartX
        val ay = bone.globalStartY
        val bx = bone.globalEndX
        val by = bone.globalEndY
        val vx = bx - ax
        val vy = by - ay
        val lenSq = vx * vx + vy * vy
        if (lenSq < 0.0001f) {
            val dx = px - ax
            val dy = py - ay
            return sqrt(dx * dx + dy * dy)
        }
        val t = (((px - ax) * vx + (py - ay) * vy) / lenSq).coerceIn(0f, 1f)
        val projX = ax + t * vx
        val projY = ay + t * vy
        val dx = px - projX
        val dy = py - projY
        return sqrt(dx * dx + dy * dy)
    }

    /**
     * Automatically calculates Linear Blend Skinning weights for a list of points (mesh vertices or strokes)
     * based on proximity to bone segments with smooth quadratic falloff.
     */
    fun calculateAutoBoneWeights(
        points: List<PointData>,
        bones: List<PuppetBone>,
        radius: Float = 140f
    ): Map<Int, List<com.example.model.BoneWeight>> {
        if (points.isEmpty() || bones.isEmpty()) return emptyMap()

        val result = mutableMapOf<Int, List<com.example.model.BoneWeight>>()

        for ((idx, pt) in points.withIndex()) {
            val candidateWeights = mutableListOf<com.example.model.BoneWeight>()
            var totalRawWeight = 0f

            for (bone in bones) {
                val dist = distanceToBoneSegment(pt.x, pt.y, bone)
                if (dist < radius * 2.5f) {
                    val normDist = (dist / radius).coerceAtLeast(0.01f)
                    val rawWeight = 1.0f / (1.0f + normDist * normDist * normDist)
                    candidateWeights.add(com.example.model.BoneWeight(bone.id, rawWeight))
                    totalRawWeight += rawWeight
                }
            }

            if (totalRawWeight > 0.0001f) {
                // Keep top 4 influential bones and normalize so sum = 1.0
                val normalized = candidateWeights
                    .sortedByDescending { it.weight }
                    .take(4)
                    .map { it.copy(weight = it.weight / totalRawWeight) }
                val subTotal = normalized.sumOf { it.weight.toDouble() }.toFloat()
                result[idx] = if (subTotal > 0f) {
                    normalized.map { it.copy(weight = it.weight / subTotal) }
                } else normalized
            } else {
                // Nearest bone fallback with 100% weight
                val nearest = bones.minByOrNull { distanceToBoneSegment(pt.x, pt.y, it) }
                if (nearest != null) {
                    result[idx] = listOf(com.example.model.BoneWeight(nearest.id, 1.0f))
                }
            }
        }

        return result
    }

    /**
     * Deforms points with Linear Blend Skinning (LBS) according to bone hierarchy and vertex weights.
     */
    fun deformMeshWithBones(
        originalVertices: List<PointData>,
        boneWeightsMap: Map<Int, List<com.example.model.BoneWeight>>,
        bones: List<PuppetBone>
    ): List<PointData> {
        if (originalVertices.isEmpty() || bones.isEmpty()) return originalVertices
        val boneMap = bones.associateBy { it.id }

        return originalVertices.mapIndexed { index, orig ->
            val weights = boneWeightsMap[index]
            if (weights.isNullOrEmpty()) {
                return@mapIndexed orig
            }

            var finalX = 0f
            var finalY = 0f
            var totalWeight = 0f

            for (bw in weights) {
                val bone = boneMap[bw.boneId] ?: continue
                val w = bw.weight
                if (w <= 0.001f) continue

                val rad = Math.toRadians(bone.globalAngle.toDouble()).toFloat()
                val cosA = cos(rad)
                val sinA = sin(rad)

                // Vector relative to bone's initial origin
                val dx = orig.x - bone.startX
                val dy = orig.y - bone.startY

                // Rotate and translate to bone global start
                val rotatedX = dx * cosA - dy * sinA
                val rotatedY = dx * sinA + dy * cosA

                finalX += (bone.globalStartX + rotatedX) * w
                finalY += (bone.globalStartY + rotatedY) * w
                totalWeight += w
            }

            if (totalWeight > 0.001f) {
                PointData(finalX / totalWeight, finalY / totalWeight, orig.pressure)
            } else {
                orig
            }
        }
    }

    /**
     * Creates a ready-to-animate 2D character skeletal rig matching the Puppet2D reference in the image.
     * (Pelvis, Spine, Chest, Neck, Head, Left/Right Arms and Legs with cyan squares & green circles).
     */
    fun createDefaultCharacterSkeleton(centerX: Float = 0f, centerY: Float = 0f): List<PuppetBone> {
        val bones = mutableListOf<PuppetBone>()

        // 1. Pelvis / Hip (Root) - Cyan Square controller
        val pelvisId = "bone_pelvis"
        bones.add(
            PuppetBone(
                id = pelvisId,
                name = "لگن (Pelvis)",
                parentId = null,
                startX = centerX,
                startY = centerY + 30f,
                length = 45f,
                angle = -90f, // Points upwards toward chest
                color = 0xFF00E5FF,
                controlShape = com.example.model.BoneControlShape.SQUARE
            )
        )

        // 2. Spine / Torso - Cyan Square controller
        val spineId = "bone_spine"
        bones.add(
            PuppetBone(
                id = spineId,
                name = "کمر (Spine)",
                parentId = pelvisId,
                length = 50f,
                angle = 0f,
                color = 0xFF00E5FF,
                controlShape = com.example.model.BoneControlShape.SQUARE
            )
        )

        // 3. Chest - Cyan Square controller
        val chestId = "bone_chest"
        bones.add(
            PuppetBone(
                id = chestId,
                name = "سینه (Chest)",
                parentId = spineId,
                length = 45f,
                angle = 0f,
                color = 0xFF00E5FF,
                controlShape = com.example.model.BoneControlShape.SQUARE
            )
        )

        // 4. Neck - Cyan Square controller
        val neckId = "bone_neck"
        bones.add(
            PuppetBone(
                id = neckId,
                name = "گردن (Neck)",
                parentId = chestId,
                length = 30f,
                angle = 0f,
                color = 0xFF00E5FF,
                controlShape = com.example.model.BoneControlShape.SQUARE
            )
        )

        // 5. Head - Circle controller
        val headId = "bone_head"
        bones.add(
            PuppetBone(
                id = headId,
                name = "سر (Head)",
                parentId = neckId,
                length = 45f,
                angle = 0f,
                color = 0xFFFFFFFF,
                controlShape = com.example.model.BoneControlShape.CIRCLE
            )
        )

        // 6. Left Arm Chain (Shoulder -> Upper Arm -> Forearm -> Hand)
        val shoulderL = "bone_shoulder_l"
        bones.add(
            PuppetBone(
                id = shoulderL,
                name = "شانه چپ",
                parentId = chestId,
                length = 25f,
                angle = 80f,
                color = 0xFF80D8FF
            )
        )
        val armL = "bone_arm_l"
        bones.add(
            PuppetBone(
                id = armL,
                name = "بازو چپ",
                parentId = shoulderL,
                length = 55f,
                angle = 45f,
                color = 0xFF00E676,
                controlShape = com.example.model.BoneControlShape.CIRCLE
            )
        )
        val forearmL = "bone_forearm_l"
        bones.add(
            PuppetBone(
                id = forearmL,
                name = "ساعد چپ",
                parentId = armL,
                length = 50f,
                angle = -35f,
                color = 0xFF00E676,
                controlShape = com.example.model.BoneControlShape.CIRCLE
            )
        )
        val handL = "bone_hand_l"
        bones.add(
            PuppetBone(
                id = handL,
                name = "دست چپ",
                parentId = forearmL,
                length = 25f,
                angle = 10f,
                color = 0xFF00E676,
                controlShape = com.example.model.BoneControlShape.CIRCLE
            )
        )

        // 7. Right Arm Chain (Shoulder -> Upper Arm -> Forearm -> Hand)
        val shoulderR = "bone_shoulder_r"
        bones.add(
            PuppetBone(
                id = shoulderR,
                name = "شانه راست",
                parentId = chestId,
                length = 25f,
                angle = -80f,
                color = 0xFF80D8FF
            )
        )
        val armR = "bone_arm_r"
        bones.add(
            PuppetBone(
                id = armR,
                name = "بازو راست",
                parentId = shoulderR,
                length = 55f,
                angle = -45f,
                color = 0xFF00E676,
                controlShape = com.example.model.BoneControlShape.CIRCLE
            )
        )
        val forearmR = "bone_forearm_r"
        bones.add(
            PuppetBone(
                id = forearmR,
                name = "ساعد راست",
                parentId = armR,
                length = 50f,
                angle = 35f,
                color = 0xFF00E676,
                controlShape = com.example.model.BoneControlShape.CIRCLE
            )
        )
        val handR = "bone_hand_r"
        bones.add(
            PuppetBone(
                id = handR,
                name = "دست راست",
                parentId = forearmR,
                length = 25f,
                angle = -10f,
                color = 0xFF00E676,
                controlShape = com.example.model.BoneControlShape.CIRCLE
            )
        )

        // 8. Left Leg Chain (Thigh -> Shin / Knee -> Foot)
        val thighL = "bone_thigh_l"
        bones.add(
            PuppetBone(
                id = thighL,
                name = "ران چپ",
                parentId = pelvisId,
                length = 70f,
                angle = 150f,
                color = 0xFF00E676,
                controlShape = com.example.model.BoneControlShape.CIRCLE
            )
        )
        val shinL = "bone_shin_l"
        bones.add(
            PuppetBone(
                id = shinL,
                name = "ساق چپ",
                parentId = thighL,
                length = 65f,
                angle = 20f,
                color = 0xFF00E676,
                controlShape = com.example.model.BoneControlShape.CIRCLE
            )
        )
        val footL = "bone_foot_l"
        bones.add(
            PuppetBone(
                id = footL,
                name = "پا چپ",
                parentId = shinL,
                length = 30f,
                angle = -70f,
                color = 0xFF00E676,
                controlShape = com.example.model.BoneControlShape.CIRCLE
            )
        )

        // 9. Right Leg Chain (Thigh -> Shin / Knee -> Foot)
        val thighR = "bone_thigh_r"
        bones.add(
            PuppetBone(
                id = thighR,
                name = "ران راست",
                parentId = pelvisId,
                length = 70f,
                angle = -150f,
                color = 0xFF00E676,
                controlShape = com.example.model.BoneControlShape.CIRCLE
            )
        )
        val shinR = "bone_shin_r"
        bones.add(
            PuppetBone(
                id = shinR,
                name = "ساق راست",
                parentId = thighR,
                length = 65f,
                angle = -20f,
                color = 0xFF00E676,
                controlShape = com.example.model.BoneControlShape.CIRCLE
            )
        )
        val footR = "bone_foot_r"
        bones.add(
            PuppetBone(
                id = footR,
                name = "پا راست",
                parentId = shinR,
                length = 30f,
                angle = 70f,
                color = 0xFF00E676,
                controlShape = com.example.model.BoneControlShape.CIRCLE
            )
        )

        return updateBonePositions(bones)
    }
}
