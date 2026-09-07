package com.example

import com.example.engine.ProjectStorageManager
import com.example.engine.PuppetWarpEngine
import com.example.engine.UndoRedoManager
import com.example.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleUnitTest {

  @Test
  fun testMeshGeneration() {
    val mesh = PuppetWarpEngine.generateMesh(300f, 300f, MeshDensity.LOW)
    assertEquals(49, mesh.vertices.size) // (6+1)*(6+1) = 49
    assertEquals(72, mesh.triangles.size) // 6*6*2 = 72
  }

  @Test
  fun testMeshDeformationWithPins() {
    val mesh = PuppetWarpEngine.generateMesh(200f, 200f, MeshDensity.LOW)
    val pin = PuppetPin(
      id = "pin1",
      x = 50f,
      y = 50f,
      originalX = 0f,
      originalY = 0f,
      radius = 100f
    )
    val deformed = PuppetWarpEngine.deformMesh(mesh, listOf(pin))
    assertEquals(mesh.vertices.size, deformed.size)
    assertNotEquals(mesh.vertices[0].x, deformed[0].x)
  }

  @Test
  fun testUndoRedoSystem() {
    val manager = UndoRedoManager(100)
    val p1 = Project(name = "V1")
    val p2 = Project(name = "V2")

    manager.recordState(p1)
    val restored1 = manager.undo(p2)
    assertEquals("V1", restored1?.name)

    val restored2 = manager.redo(restored1!!)
    assertEquals("V2", restored2?.name)
  }

  @Test
  fun testProjectSerialization() {
    val original = Project(
      name = "پروژه تست",
      layers = listOf(
        Layer(name = "لایه ۱", type = LayerType.SHAPE, shapeData = ShapeData(shapeType = ShapeType.STAR))
      )
    )
    val json = ProjectStorageManager.saveProjectToJson(original)
    assertTrue(json.contains("پروژه تست"))
    val loaded = ProjectStorageManager.loadProjectFromJson(json)
    assertNotNull(loaded)
    assertEquals("پروژه تست", loaded?.name)
    assertEquals(1, loaded?.layers?.size)
  }
}

