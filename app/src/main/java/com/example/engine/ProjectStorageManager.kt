package com.example.engine

import android.content.Context
import com.example.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object ProjectStorageManager {

    private const val AUTOSAVE_FILE_NAME = "puppet_studio_autosave.json"

    fun saveProjectToJson(project: Project): String {
        val root = JSONObject()
        root.put("id", project.id)
        root.put("name", project.name)
        root.put("canvasWidth", project.canvasWidth.toDouble())
        root.put("canvasHeight", project.canvasHeight.toDouble())
        root.put("activeLayerId", project.activeLayerId)

        // Timeline
        val tl = JSONObject()
        tl.put("id", project.timeline.id)
        tl.put("name", project.timeline.name)
        tl.put("mode", project.timeline.mode.name)
        tl.put("totalFrames", project.timeline.totalFrames)
        tl.put("currentFrame", project.timeline.currentFrame)
        tl.put("fps", project.timeline.fps)
        tl.put("isLooping", project.timeline.isLooping)
        tl.put("isOnionSkinEnabled", project.timeline.isOnionSkinEnabled)
        tl.put("onionSkinOpacity", project.timeline.onionSkinOpacity.toDouble())
        root.put("timeline", tl)

        // Layers
        val layersArray = JSONArray()
        for (layer in project.layers) {
            val layerObj = JSONObject()
            layerObj.put("id", layer.id)
            layerObj.put("name", layer.name)
            layerObj.put("type", layer.type.name)
            layerObj.put("isVisible", layer.isVisible)
            layerObj.put("isLocked", layer.isLocked)
            layerObj.put("opacity", layer.opacity.toDouble())
            layerObj.put("blendMode", layer.blendMode.name)

            // Transform
            val tObj = JSONObject()
            tObj.put("tx", layer.transform.translationX.toDouble())
            tObj.put("ty", layer.transform.translationY.toDouble())
            tObj.put("sx", layer.transform.scaleX.toDouble())
            tObj.put("sy", layer.transform.scaleY.toDouble())
            tObj.put("rot", layer.transform.rotation.toDouble())
            layerObj.put("transform", tObj)

            // Raster strokes
            val strokesArray = JSONArray()
            for (stroke in layer.rasterStrokes) {
                val sObj = JSONObject()
                sObj.put("color", stroke.color)
                sObj.put("width", stroke.strokeWidth.toDouble())
                sObj.put("eraser", stroke.isEraser)
                val ptsArray = JSONArray()
                for (pt in stroke.points) {
                    val ptObj = JSONObject()
                    ptObj.put("x", pt.x.toDouble())
                    ptObj.put("y", pt.y.toDouble())
                    ptsArray.put(ptObj)
                }
                sObj.put("points", ptsArray)
                strokesArray.put(sObj)
            }
            layerObj.put("strokes", strokesArray)

            // Puppet modifier
            val puppetObj = JSONObject()
            puppetObj.put("showMesh", layer.puppetModifier.showMesh)
            puppetObj.put("density", layer.puppetModifier.density.name)
            puppetObj.put("preset", layer.puppetModifier.physicsPreset.name)
            puppetObj.put("deformer", layer.puppetModifier.deformerType.name)
            puppetObj.put("defRot", layer.puppetModifier.deformerRotation.toDouble())
            puppetObj.put("defRad", layer.puppetModifier.deformerRadius.toDouble())

            val pinsArray = JSONArray()
            for (pin in layer.puppetModifier.pins) {
                val pObj = JSONObject()
                pObj.put("id", pin.id)
                pObj.put("x", pin.x.toDouble())
                pObj.put("y", pin.y.toDouble())
                pObj.put("ox", pin.originalX.toDouble())
                pObj.put("oy", pin.originalY.toDouble())
                pObj.put("weight", pin.weight.toDouble())
                pObj.put("radius", pin.radius.toDouble())
                pObj.put("type", pin.pinType.name)
                pObj.put("depth", pin.depth.toDouble())
                pObj.put("group", pin.group)
                pObj.put("isMirrored", pin.isMirrored)
                pin.mirrorPinId?.let { pObj.put("mirrorPinId", it) }
                pinsArray.put(pObj)
            }
            puppetObj.put("pins", pinsArray)

            val bonesArray = JSONArray()
            for (bone in layer.puppetModifier.bones) {
                val bObj = JSONObject()
                bObj.put("id", bone.id)
                bObj.put("name", bone.name)
                bone.parentId?.let { bObj.put("parentId", it) }
                bObj.put("sx", bone.startX.toDouble())
                bObj.put("sy", bone.startY.toDouble())
                bObj.put("len", bone.length.toDouble())
                bObj.put("ang", bone.angle.toDouble())
                bObj.put("col", bone.color)
                bObj.put("ctrl", bone.controlShape.name)
                bonesArray.put(bObj)
            }
            puppetObj.put("bones", bonesArray)
            puppetObj.put("showBones", layer.puppetModifier.showBones)
            layerObj.put("puppet", puppetObj)

            // Shapes & Text
            layer.shapeData?.let { sd ->
                val shapeObj = JSONObject()
                shapeObj.put("type", sd.shapeType.name)
                shapeObj.put("width", sd.width.toDouble())
                shapeObj.put("height", sd.height.toDouble())
                shapeObj.put("fillColor", sd.fillColor)
                shapeObj.put("strokeColor", sd.strokeColor)
                shapeObj.put("strokeWidth", sd.strokeWidth.toDouble())
                shapeObj.put("isFilled", sd.isFilled)
                layerObj.put("shape", shapeObj)
            }

            layer.textData?.let { td ->
                val textObj = JSONObject()
                textObj.put("text", td.text)
                textObj.put("fontSize", td.fontSize.toDouble())
                textObj.put("color", td.textColor)
                textObj.put("mode", td.mode.name)
                layerObj.put("text", textObj)
            }

            layer.imageUri?.let { uri ->
                layerObj.put("imageUri", uri)
            }

            layersArray.put(layerObj)
        }
        root.put("layers", layersArray)

        return root.toString(2)
    }

    fun loadProjectFromJson(jsonString: String): Project? {
        return try {
            val root = JSONObject(jsonString)
            val id = root.optString("id", "")
            val name = root.optString("name", "پروژه انیمیشن")
            val canvasWidth = root.optDouble("canvasWidth", 1080.0).toFloat()
            val canvasHeight = root.optDouble("canvasHeight", 1080.0).toFloat()
            val activeLayerId = root.optString("activeLayerId", "")

            val tlObj = root.optJSONObject("timeline")
            val timeline = if (tlObj != null) {
                Timeline(
                    id = tlObj.optString("id", ""),
                    name = tlObj.optString("name", "تایم‌لاین ۱"),
                    mode = TimelineMode.valueOf(tlObj.optString("mode", TimelineMode.DUPLICATE.name)),
                    totalFrames = tlObj.optInt("totalFrames", 24),
                    currentFrame = tlObj.optInt("currentFrame", 0),
                    fps = tlObj.optInt("fps", 24),
                    isLooping = tlObj.optBoolean("isLooping", true),
                    isOnionSkinEnabled = tlObj.optBoolean("isOnionSkinEnabled", true),
                    onionSkinOpacity = tlObj.optDouble("onionSkinOpacity", 0.35).toFloat()
                )
            } else Timeline()

            val layersArray = root.optJSONArray("layers")
            val layersList = mutableListOf<Layer>()
            if (layersArray != null) {
                for (i in 0 until layersArray.length()) {
                    val lObj = layersArray.getJSONObject(i)
                    val lId = lObj.optString("id", "")
                    val lName = lObj.optString("name", "لایه")
                    val lType = LayerType.valueOf(lObj.optString("type", LayerType.RASTER.name))
                    val isVis = lObj.optBoolean("isVisible", true)
                    val isLock = lObj.optBoolean("isLocked", false)
                    val opac = lObj.optDouble("opacity", 1.0).toFloat()
                    val bMode = BlendModeType.valueOf(lObj.optString("blendMode", BlendModeType.NORMAL.name))

                    val tObj = lObj.optJSONObject("transform")
                    val transform = if (tObj != null) {
                        LayerTransform(
                            translationX = tObj.optDouble("tx", 0.0).toFloat(),
                            translationY = tObj.optDouble("ty", 0.0).toFloat(),
                            scaleX = tObj.optDouble("sx", 1.0).toFloat(),
                            scaleY = tObj.optDouble("sy", 1.0).toFloat(),
                            rotation = tObj.optDouble("rot", 0.0).toFloat()
                        )
                    } else LayerTransform()

                    // Strokes
                    val strokesList = mutableListOf<DrawingStroke>()
                    val sArr = lObj.optJSONArray("strokes")
                    if (sArr != null) {
                        for (sIdx in 0 until sArr.length()) {
                            val so = sArr.getJSONObject(sIdx)
                            val sColor = so.optLong("color", 0xFFFFFFFF)
                            val sWidth = so.optDouble("width", 12.0).toFloat()
                            val sEraser = so.optBoolean("eraser", false)
                            val pts = mutableListOf<PointData>()
                            val ptsArr = so.optJSONArray("points")
                            if (ptsArr != null) {
                                for (pIdx in 0 until ptsArr.length()) {
                                    val po = ptsArr.getJSONObject(pIdx)
                                    pts.add(PointData(po.optDouble("x", 0.0).toFloat(), po.optDouble("y", 0.0).toFloat()))
                                }
                            }
                            strokesList.add(DrawingStroke(sColor, sWidth, pts, sEraser))
                        }
                    }

                    // Puppet
                    var puppetMod = PuppetModifier()
                    val pObj = lObj.optJSONObject("puppet")
                    if (pObj != null) {
                        val density = MeshDensity.valueOf(pObj.optString("density", MeshDensity.MEDIUM.name))
                        val preset = PhysicsPreset.valueOf(pObj.optString("preset", PhysicsPreset.NONE.name))
                        val deformer = InvisibleDeformerType.valueOf(pObj.optString("deformer", InvisibleDeformerType.NONE.name))
                        val pinsList = mutableListOf<PuppetPin>()
                        val pinsArr = pObj.optJSONArray("pins")
                        if (pinsArr != null) {
                            for (pIdx in 0 until pinsArr.length()) {
                                val pinJson = pinsArr.getJSONObject(pIdx)
                                pinsList.add(
                                    PuppetPin(
                                        id = pinJson.optString("id", ""),
                                        x = pinJson.optDouble("x", 0.0).toFloat(),
                                        y = pinJson.optDouble("y", 0.0).toFloat(),
                                        originalX = pinJson.optDouble("ox", 0.0).toFloat(),
                                        originalY = pinJson.optDouble("oy", 0.0).toFloat(),
                                        weight = pinJson.optDouble("weight", 1.0).toFloat(),
                                        radius = pinJson.optDouble("radius", 90.0).toFloat(),
                                        pinType = PinType.valueOf(pinJson.optString("type", PinType.STATIC.name)),
                                        depth = pinJson.optDouble("depth", 0.0).toFloat(),
                                        group = pinJson.optInt("group", 0),
                                        isMirrored = pinJson.optBoolean("isMirrored", false),
                                        mirrorPinId = if (pinJson.has("mirrorPinId")) pinJson.getString("mirrorPinId") else null
                                    )
                                )
                            }
                        }
                        val bonesList = mutableListOf<PuppetBone>()
                        val bonesArr = pObj.optJSONArray("bones")
                        if (bonesArr != null) {
                            for (bIdx in 0 until bonesArr.length()) {
                                val bJson = bonesArr.getJSONObject(bIdx)
                                bonesList.add(
                                    PuppetBone(
                                        id = bJson.optString("id", ""),
                                        name = bJson.optString("name", "استخوان"),
                                        parentId = if (bJson.has("parentId")) bJson.getString("parentId") else null,
                                        startX = bJson.optDouble("sx", 0.0).toFloat(),
                                        startY = bJson.optDouble("sy", 0.0).toFloat(),
                                        length = bJson.optDouble("len", 80.0).toFloat(),
                                        angle = bJson.optDouble("ang", 0.0).toFloat(),
                                        color = bJson.optLong("col", 0xFF00E5FF),
                                        controlShape = try {
                                            BoneControlShape.valueOf(bJson.optString("ctrl", BoneControlShape.CIRCLE.name))
                                        } catch (_: Exception) { BoneControlShape.CIRCLE }
                                    )
                                )
                            }
                        }
                        val resolvedBones = PuppetWarpEngine.updateBonePositions(bonesList)
                        val generatedMesh = PuppetWarpEngine.generateMesh(360f, 360f, density)
                        puppetMod = PuppetModifier(
                            mesh = generatedMesh,
                            pins = pinsList,
                            bones = resolvedBones,
                            physicsPreset = preset,
                            density = density,
                            showMesh = pObj.optBoolean("showMesh", true),
                            showBones = pObj.optBoolean("showBones", true),
                            deformerType = deformer,
                            deformerRotation = pObj.optDouble("defRot", 0.0).toFloat(),
                            deformerRadius = pObj.optDouble("defRad", 150.0).toFloat()
                        )
                    }

                    // Shape
                    var shapeData: ShapeData? = null
                    lObj.optJSONObject("shape")?.let { sh ->
                        shapeData = ShapeData(
                            shapeType = ShapeType.valueOf(sh.optString("type", ShapeType.RECT.name)),
                            width = sh.optDouble("width", 200.0).toFloat(),
                            height = sh.optDouble("height", 200.0).toFloat(),
                            fillColor = sh.optLong("fillColor", 0xFF3898EC),
                            strokeColor = sh.optLong("strokeColor", 0xFFFFFFFF),
                            strokeWidth = sh.optDouble("strokeWidth", 6.0).toFloat(),
                            isFilled = sh.optBoolean("isFilled", true)
                        )
                    }

                    // Text
                    var textData: TextData? = null
                    lObj.optJSONObject("text")?.let { tx ->
                        textData = TextData(
                            text = tx.optString("text", "استودیو"),
                            fontSize = tx.optDouble("fontSize", 36.0).toFloat(),
                            textColor = tx.optLong("color", 0xFFF0F0F5),
                            mode = TextMode.valueOf(tx.optString("mode", TextMode.NORMAL.name))
                        )
                    }

                    val imageUri = lObj.optString("imageUri", null)

                    layersList.add(
                        Layer(
                            id = lId,
                            name = lName,
                            type = lType,
                            isVisible = isVis,
                            isLocked = isLock,
                            opacity = opac,
                            blendMode = bMode,
                            transform = transform,
                            rasterStrokes = strokesList,
                            puppetModifier = puppetMod,
                            shapeData = shapeData,
                            textData = textData,
                            imageUri = imageUri
                        )
                    )
                }
            }

            Project(
                id = id,
                name = name,
                canvasWidth = canvasWidth,
                canvasHeight = canvasHeight,
                layers = layersList,
                activeLayerId = activeLayerId,
                timeline = timeline
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private const val RECENT_PROJECTS_FILE = "puppet_recent_projects.json"

    fun autosaveProject(context: Context, project: Project) {
        try {
            val json = saveProjectToJson(project)
            val file = File(context.filesDir, AUTOSAVE_FILE_NAME)
            file.writeText(json)
            saveToRecentProjects(context, project)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadAutosavedProject(context: Context): Project? {
        val file = File(context.filesDir, AUTOSAVE_FILE_NAME)
        if (!file.exists()) return null
        return try {
            val json = file.readText()
            loadProjectFromJson(json)
        } catch (e: Exception) {
            null
        }
    }

    fun exportToPuppet2dFile(project: Project, destinationFile: File): Boolean {
        return try {
            val json = saveProjectToJson(project)
            java.util.zip.ZipOutputStream(destinationFile.outputStream()).use { zipOut ->
                val entry = java.util.zip.ZipEntry("project.json")
                zipOut.putNextEntry(entry)
                zipOut.write(json.toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun importFromPuppet2dFile(sourceFile: File): Project? {
        return try {
            java.util.zip.ZipInputStream(sourceFile.inputStream()).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    if (entry.name == "project.json") {
                        val jsonString = zipIn.bufferedReader(Charsets.UTF_8).readText()
                        return loadProjectFromJson(jsonString)
                    }
                    entry = zipIn.nextEntry
                }
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getRecentProjects(context: Context): List<ProjectMetadata> {
        val file = File(context.filesDir, RECENT_PROJECTS_FILE)
        if (!file.exists()) return emptyList()
        return try {
            val jsonArr = JSONArray(file.readText())
            val list = mutableListOf<ProjectMetadata>()
            for (i in 0 until jsonArr.length()) {
                val obj = jsonArr.getJSONObject(i)
                list.add(
                    ProjectMetadata(
                        id = obj.optString("id"),
                        name = obj.optString("name"),
                        lastModified = obj.optLong("lastModified", System.currentTimeMillis()),
                        canvasWidth = obj.optDouble("canvasWidth", 1080.0).toFloat(),
                        canvasHeight = obj.optDouble("canvasHeight", 1080.0).toFloat(),
                        layersCount = obj.optInt("layersCount", 1),
                        totalFrames = obj.optInt("totalFrames", 24)
                    )
                )
            }
            list.sortedByDescending { it.lastModified }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun deleteProject(context: Context, projectId: String): Boolean {
        return try {
            val projFile = File(context.filesDir, "project_$projectId.json")
            if (projFile.exists()) projFile.delete()

            val current = getRecentProjects(context).filter { it.id != projectId }
            val jsonArr = JSONArray()
            current.forEach { item ->
                val obj = JSONObject()
                obj.put("id", item.id)
                obj.put("name", item.name)
                obj.put("lastModified", item.lastModified)
                obj.put("canvasWidth", item.canvasWidth.toDouble())
                obj.put("canvasHeight", item.canvasHeight.toDouble())
                obj.put("layersCount", item.layersCount)
                obj.put("totalFrames", item.totalFrames)
                jsonArr.put(obj)
            }
            File(context.filesDir, RECENT_PROJECTS_FILE).writeText(jsonArr.toString())
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun renameProject(context: Context, projectId: String, newName: String): Boolean {
        return try {
            val projFile = File(context.filesDir, "project_$projectId.json")
            if (projFile.exists()) {
                val p = loadProjectFromJson(projFile.readText())
                if (p != null) {
                    val updated = p.copy(name = newName)
                    projFile.writeText(saveProjectToJson(updated))
                }
            }

            val current = getRecentProjects(context).map {
                if (it.id == projectId) it.copy(name = newName, lastModified = System.currentTimeMillis()) else it
            }
            val jsonArr = JSONArray()
            current.forEach { item ->
                val obj = JSONObject()
                obj.put("id", item.id)
                obj.put("name", item.name)
                obj.put("lastModified", item.lastModified)
                obj.put("canvasWidth", item.canvasWidth.toDouble())
                obj.put("canvasHeight", item.canvasHeight.toDouble())
                obj.put("layersCount", item.layersCount)
                obj.put("totalFrames", item.totalFrames)
                jsonArr.put(obj)
            }
            File(context.filesDir, RECENT_PROJECTS_FILE).writeText(jsonArr.toString())
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun loadProjectById(context: Context, projectId: String): Project? {
        val projFile = File(context.filesDir, "project_$projectId.json")
        if (projFile.exists()) {
            val p = loadProjectFromJson(projFile.readText())
            if (p != null) return p
        }
        return loadAutosavedProject(context)
    }

    fun saveToRecentProjects(context: Context, project: Project) {
        try {
            val projFile = File(context.filesDir, "project_${project.id}.json")
            projFile.writeText(saveProjectToJson(project))

            val current = getRecentProjects(context).filter { it.id != project.id }.toMutableList()
            current.add(
                0,
                ProjectMetadata(
                    id = project.id,
                    name = project.name,
                    lastModified = System.currentTimeMillis(),
                    canvasWidth = project.canvasWidth,
                    canvasHeight = project.canvasHeight,
                    layersCount = project.layers.size,
                    totalFrames = project.timeline.totalFrames
                )
            )
            val jsonArr = JSONArray()
            current.take(20).forEach { item ->
                val obj = JSONObject()
                obj.put("id", item.id)
                obj.put("name", item.name)
                obj.put("lastModified", item.lastModified)
                obj.put("canvasWidth", item.canvasWidth.toDouble())
                obj.put("canvasHeight", item.canvasHeight.toDouble())
                obj.put("layersCount", item.layersCount)
                obj.put("totalFrames", item.totalFrames)
                jsonArr.put(obj)
            }
            File(context.filesDir, RECENT_PROJECTS_FILE).writeText(jsonArr.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun createPresetProject(preset: CanvasPreset, name: String = "پروژه جدید"): Project {
        val baseLayerId = java.util.UUID.randomUUID().toString()
        val baseLayer = Layer(
            id = baseLayerId,
            name = "لایه نقاشی ۱",
            type = LayerType.RASTER
        )
        return Project(
            id = java.util.UUID.randomUUID().toString(),
            name = name,
            canvasWidth = preset.width,
            canvasHeight = preset.height,
            layers = listOf(baseLayer),
            activeLayerId = baseLayerId,
            timeline = Timeline(
                id = java.util.UUID.randomUUID().toString(),
                name = "تایم‌لاین اصلی",
                totalFrames = 24,
                fps = 24
            )
        )
    }
}
