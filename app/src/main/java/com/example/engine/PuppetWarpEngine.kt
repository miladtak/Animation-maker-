package com.example.engine

import com.example.model.InvisibleDeformerType
import com.example.model.MeshDensity
import com.example.model.PhysicsParams
import com.example.model.PhysicsPreset
import com.example.model.PinType
import com.example.model.PointData
import com.example.model.PuppetMesh
import com.example.model.PuppetPin
import com.example.model.TriangleIndices
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
        deformerRadius: Float = 150f,
        width: Float = 300f,
        height: Float = 300f
    ): PointData {
        if (pins.isEmpty() && deformerType == InvisibleDeformerType.NONE) {
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

        if (deformerType != InvisibleDeformerType.NONE && deformerRadius > 10f) {
            val halfW = width / 2f
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
                    val halfH = height / 2f
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
                InvisibleDeformerType.NONE -> {}
            }
        }

        return PointData(currentX, currentY)
    }

    /**
     * Deforms the mesh vertices using Pin positions via smooth Radial Basis Function (RBF)
     * and optional Invisible Deformers (Sphere / Cylinder / Capsule 360 wrap).
     */
    fun deformMesh(
        originalMesh: PuppetMesh,
        pins: List<PuppetPin>,
        deformerType: InvisibleDeformerType = InvisibleDeformerType.NONE,
        deformerRotation: Float = 0f,
        deformerRadius: Float = 150f
    ): List<PointData> {
        if (originalMesh.originalVertices.isEmpty()) return emptyList()
        if (pins.isEmpty() && deformerType == InvisibleDeformerType.NONE) {
            return originalMesh.originalVertices
        }

        return originalMesh.originalVertices.map { orig ->
            deformPoint(
                origX = orig.x,
                origY = orig.y,
                pins = pins,
                deformerType = deformerType,
                deformerRotation = deformerRotation,
                deformerRadius = deformerRadius,
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
}
