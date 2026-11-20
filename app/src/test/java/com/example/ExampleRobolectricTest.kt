package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.audio.ProceduralWaveSynth
import com.example.audio.SoundEffect
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
        assertEquals(5, catalog.pets.size)
        assertEquals(6, catalog.relics.size)
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

    @Test
    fun `boss duel stops mob spawns darkens mood and escalates difficulty tier on defeat`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val catalog = GameDataRepository(context).catalog
        val berserker = catalog.classes.first { it.id == "berserker" }
        val sim = GameSimulation(catalog, PlayerSaveData(), berserker, berserker.palette)

        assertTrue(sim.equippedPet != null)
        assertTrue(!sim.bossDuelActive)
        sim.maxHp = 10000f
        sim.hp = 10000f

        // Advance simulation past boss milestone (52s elapsed)
        repeat(60 * 55) {
            if (sim.status == RunStatus.LEVEL_UP && sim.pendingChoices.isNotEmpty()) {
                sim.applyCard(sim.pendingChoices.first())
            }
            if (sim.status == RunStatus.PLAYING) {
                sim.step(1f / 60f)
            }
            if (sim.bossDuelActive) return@repeat
        }

        assertTrue("Boss 1v1 duel should trigger automatically", sim.bossDuelActive)
        assertEquals(1, sim.enemies.count { it.isBoss })
        assertEquals("Only the boss should remain in 1v1 duel", 0, sim.enemies.count { !it.isBoss })

        // Step a little more to advance dark atmosphere blend
        repeat(30) {
            if (sim.status == RunStatus.LEVEL_UP && sim.pendingChoices.isNotEmpty()) {
                sim.applyCard(sim.pendingChoices.first())
            }
            sim.step(0.03f)
        }
        assertTrue("bossMoodBlend should transition toward dark boss duel", sim.bossMoodBlend > 0.25f)

        // Defeat the boss and verify difficulty escalates & game continues (endless progression)
        if (sim.status == RunStatus.LEVEL_UP && sim.pendingChoices.isNotEmpty()) {
            sim.applyCard(sim.pendingChoices.first())
        }
        val boss = sim.enemies.first { it.isBoss }
        boss.hp = -1f
        sim.step(0.05f)
        if (sim.status == RunStatus.LEVEL_UP && sim.pendingChoices.isNotEmpty()) {
            sim.applyCard(sim.pendingChoices.first())
        }
        assertEquals(RunStatus.PLAYING, sim.status)
        assertTrue(sim.bossesDefeatedInRun >= 1)
        assertTrue(sim.difficultyTier >= 2)
        assertTrue(!sim.bossDuelActive)
    }

    @Test
    fun `sound synthesizer creates valid wavs and simulation emits combat footstep jump defense and power sfx`() {
        for (effect in SoundEffect.entries) {
            val wav = ProceduralWaveSynth.synthesizeWav(effect)
            assertTrue("WAV must have RIFF header + PCM payload", wav.size > 44)
            assertEquals('R'.code.toByte(), wav[0])
            assertEquals('I'.code.toByte(), wav[1])
            assertEquals('F'.code.toByte(), wav[2])
            assertEquals('F'.code.toByte(), wav[3])
            assertEquals('W'.code.toByte(), wav[8])
            assertEquals('A'.code.toByte(), wav[9])
            assertEquals('V'.code.toByte(), wav[10])
            assertEquals('E'.code.toByte(), wav[11])
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val catalog = GameDataRepository(context).catalog
        val berserker = catalog.classes.first { it.id == "berserker" }
        val atirador = catalog.classes.first { it.id == "atirador" }
        val emitted = mutableSetOf<SoundEffect>()

        val meleeSim = GameSimulation(catalog, PlayerSaveData(), berserker, berserker.palette) { emitted.add(it) }
        meleeSim.moveInputY = 1.0f
        repeat(28) { meleeSim.step(0.02f) } // triggers footsteps
        meleeSim.triggerDodge() // defense/evade sound
        meleeSim.triggerActiveAbility() // power + strike sound
        meleeSim.triggerManualAttack() // strike slash

        // Finish dodge and then push into container to climb & perform parkour mantle flip
        repeat(20) { meleeSim.step(0.02f) }
        meleeSim.cameraYawDeg = 0f
        meleeSim.playerPos.x = 0f
        meleeSim.playerPos.y = 0f
        meleeSim.playerPos.z = -20.8f
        meleeSim.moveInputX = 0f
        meleeSim.moveInputY = 1.0f
        repeat(45) { meleeSim.step(0.02f) }

        val rangedSim = GameSimulation(catalog, PlayerSaveData(), atirador, atirador.palette) { emitted.add(it) }
        rangedSim.triggerManualAttack() // shot plasma

        assertTrue(SoundEffect.FOOTSTEP in emitted)
        assertTrue(SoundEffect.DEFENSE_SHIELD in emitted)
        assertTrue(SoundEffect.POWER_SKILL in emitted)
        assertTrue(SoundEffect.STRIKE_SLASH in emitted)
        assertTrue(SoundEffect.SHOT_PLASMA in emitted)
        assertTrue(SoundEffect.JUMP_PARKOUR in emitted)
    }
}
