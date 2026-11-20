package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.GameDataRepository
import com.example.data.PlayerSaveData
import com.example.data.skinsForClass
import com.example.engine3d.GameSimulation
import com.example.engine3d.MeshPrimitives
import com.example.engine3d.RunStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name matches Neonfall`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Neonfall", appName)
    }

    @Test
    fun `json catalog loads all classes skins enemies bosses and 56 cards`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val catalog = GameDataRepository(context).catalog
        assertEquals(7, catalog.classes.size)
        assertEquals(18, catalog.skins.size)
        assertTrue(skinsForClass(catalog.skins, "berserker").any { it.id == "berserker_in12" })
        assertTrue(skinsForClass(catalog.skins, "hacker").any { it.id == "hacker_virus" })
        assertEquals(8, catalog.enemies.size)
        assertEquals(3, catalog.bosses.size)
        assertEquals(56, catalog.cards.size)
        assertEquals(14, catalog.abilities.size)
    }

    @Test
    fun `simulation moves player triggers dodge and applies evolution card`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val catalog = GameDataRepository(context).catalog
        val berserker = catalog.classes.first { it.id == "berserker" }
        val sim = GameSimulation(catalog, PlayerSaveData(), berserker, berserker.palette)

        val startX = sim.playerPos.x
        val startZ = sim.playerPos.z
        sim.moveInputY = 1.0f
        repeat(20) { sim.step(0.016f) }
        assertTrue(sim.playerPos.x < startX && sim.playerPos.z < startZ)
        assertTrue(sim.desiredCameraDistance >= 19.0f)

        sim.triggerDodge()
        sim.triggerActiveAbility()
        sim.step(0.016f)

        val baseDmg = sim.attackDamage
        val dmgCard = catalog.cards.first { it.effectType == "DAMAGE_MULT" }
        sim.applyCard(dmgCard)
        assertTrue(sim.attackDamage > baseDmg)
        assertEquals(RunStatus.PLAYING, sim.status)
    }

    @Test
    fun `player fluidly climbs obstacle when forcing movement into wall`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val catalog = GameDataRepository(context).catalog
        val hacker = catalog.classes.first { it.id == "hacker" }
        val sim = GameSimulation(catalog, PlayerSaveData(cameraDistance = 28f), hacker, hacker.palette)
        assertEquals(28f, sim.desiredCameraDistance, 0.01f)

        // Position near container cnt_4 at (0, 1.3, -23.0) with size (5.2, 2.6, 2.2) and push into it
        sim.cameraYawDeg = 0f
        sim.playerPos.x = 0f
        sim.playerPos.y = 0f
        sim.playerPos.z = -20.8f
        sim.moveInputX = 0f
        sim.moveInputY = 1.0f // Forward along -Z into container

        var climbedAboveGround = false
        repeat(45) {
            sim.step(0.02f)
            if (sim.playerPos.y > 0.5f || sim.isClimbing) {
                climbedAboveGround = true
            }
        }
        assertTrue("Player should climb or vault onto obstacle smoothly", climbedAboveGround)
    }

    @Test
    fun `all procedural 3d mesh primitives build cleanly with valid unit normals`() {
        val meshes = listOf(
            MeshPrimitives.cube,
            MeshPrimitives.cylinder,
            MeshPrimitives.octahedron,
            MeshPrimitives.ring,
            MeshPrimitives.disc,
            MeshPrimitives.groundQuad,
            MeshPrimitives.sphere,
            MeshPrimitives.capsule,
            MeshPrimitives.beveledBox,
            MeshPrimitives.cone,
            MeshPrimitives.blade,
            MeshPrimitives.torus
        )
        for (m in meshes) {
            assertTrue(m.vertexCount >= 6)
            assertEquals(0, m.vertices.size % 6)
            var idx = 0
            while (idx < m.vertices.size) {
                val nx = m.vertices[idx + 3]
                val ny = m.vertices[idx + 4]
                val nz = m.vertices[idx + 5]
                val len = kotlin.math.sqrt(nx * nx + ny * ny + nz * nz)
                assertTrue("Normal must be unit length", len in 0.99f..1.01f)
                idx += 6
            }
        }
    }
}
