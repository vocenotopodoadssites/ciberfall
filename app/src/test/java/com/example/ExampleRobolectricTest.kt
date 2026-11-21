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
        assertEquals(2, sim.stageNumber)
        assertEquals(2, sim.world.currentStage)
        assertEquals("Porto Carga-09", sim.world.stageName)
        assertTrue(sim.world.objects.any { it.id.startsWith("d_") })
        assertTrue(!sim.bossDuelActive)
    }

    @Test
    fun `unbounded free roam streams new obstacles beyond old boundary without delimitations`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val catalog = GameDataRepository(context).catalog
        val cacador = catalog.classes.first { it.id == "cacador" }
        val sim = GameSimulation(catalog, PlayerSaveData(), cacador, cacador.palette)

        sim.maxHp = 10000f
        sim.hp = 10000f
        sim.cameraYawDeg = 0f
        sim.moveInputX = 1.0f // Move east (+X) continuously
        sim.moveInputY = 0f

        repeat(420) {
            if (sim.status == RunStatus.LEVEL_UP && sim.pendingChoices.isNotEmpty()) {
                sim.applyCard(sim.pendingChoices.first())
            }
            sim.step(1f / 30f)
        }

        assertTrue("Player should travel freely past old 35m wall", sim.playerPos.x > 55f)
        assertTrue("Streamed chunks should keep obstacles populated around player", sim.world.objects.any { it.x > 45f && it.hasCollider })
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

    @Test
    fun `all bundled glb assets parse cleanly via GlbModelLoader and world stages map glbs and fallback textures`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bundledGlbKeys = listOf(
            "street_lamp",
            "drone",
            "cyberpunk_building",
            "cyberpunk_building_1",
            "cyberpunk_building_2",
            "industrial_tower"
        )
        for (key in bundledGlbKeys) {
            val parsed = com.example.engine3d.GlbModelLoader.parseCpu(context.assets, "models/$key.glb")
            assertTrue("Expected GLB $key to parse cleanly", parsed != null)
            parsed!!
            assertTrue("Expected non-empty parts for $key", parsed.parts.isNotEmpty())
            assertTrue("GLB $key vertexCount=${parsed.vertexCount} too small", parsed.vertexCount > 1000)
            assertTrue("GLB $key triangleCount=${parsed.triangleCount} too small", parsed.triangleCount > 3000)
            assertEquals(parsed.vertexCount * 8, parsed.parts.sumOf { it.interleavedVertices.size })
            assertTrue("GLB $key widthX=${parsed.widthX} invalid", parsed.widthX in 0.2f..50f)
            assertTrue("GLB $key height=${parsed.height} invalid", parsed.height in 0.2f..50f)
            assertTrue("GLB $key depthZ=${parsed.depthZ} invalid", parsed.depthZ in 0.2f..50f)
            assertTrue(
                "Draco compression must not block $key",
                "KHR_draco_mesh_compression" !in parsed.extensionsUsed
            )
            // Verify EXT_texture_webp bitmaps decode without returning null
            assertTrue("Expected $key albedo WebP texture to decode", parsed.albedoBitmap != null)
            assertTrue("Expected $key emissive WebP texture to decode", parsed.emissiveBitmap != null)
        }

        // Unbundled car GLBs must fail gracefully without crashing while procedural car fallback stays active
        val missingCar = com.example.engine3d.GlbModelLoader.parseCpu(
            context.assets,
            "models/delorean_time_machine.glb"
        )
        assertEquals(null, missingCar)

        val texPaths = listOf(
            "textures/cyberpunk_street_floor.webp",
            "textures/building_industrial.webp",
            "textures/building_market.webp",
            "textures/building_rowhouse.webp"
        )
        for (path in texPaths) {
            val bytes = context.assets.open(path).use { it.readBytes() }
            assertTrue("Texture asset $path should not be empty", bytes.size > 10_000)
            assertEquals('R'.code.toByte(), bytes[0])
            assertEquals('I'.code.toByte(), bytes[1])
            assertEquals('F'.code.toByte(), bytes[2])
            assertEquals('F'.code.toByte(), bytes[3])
        }

        val sector = com.example.engine3d.CyberpunkWorldSector()
        val stage1Ids = sector.objects.map { it.id }.toSet()
        assertTrue("Expected isolated visual test building", "visual_test_building" in stage1Ids)

        val testBld = sector.objects.first { it.id == "visual_test_building" }
        assertEquals("cyberpunk_building_1", testBld.modelKey)
        assertEquals(com.example.engine3d.WorldPropKind.HOUSE_WORKSHOP, testBld.kind)

        // Ensure isolated visual_test_building has no overlapping neighbors within 5.5m of its perimeter
        val othersNearTest = sector.objects.filter { obj ->
            obj.id != "visual_test_building" &&
                kotlin.math.abs(obj.x - testBld.x) < (obj.sx + testBld.sx) * 0.5f + 2.0f &&
                kotlin.math.abs(obj.z - testBld.z) < (obj.sz + testBld.sz) * 0.5f + 2.0f
        }
        assertTrue(
            "Expected empty clearance around visual_test_building, found=${othersNearTest.map { it.id }}",
            othersNearTest.isEmpty()
        )

        // Verify uniform GLB scale preserves native aspect ratio (no vertical stretching)
        val towerParsed = com.example.engine3d.GlbModelLoader.parseCpu(context.assets, "models/industrial_tower.glb")!!
        val uniform = com.example.engine3d.NeonfallGLRenderer.computeUniformGlbScale(
            kind = com.example.engine3d.WorldPropKind.SKYSCRAPER,
            objSx = 8.4f,
            objSy = 22.0f,
            objSz = 8.4f,
            widthX = towerParsed.widthX,
            height = towerParsed.height,
            depthZ = towerParsed.depthZ
        )
        assertTrue("Uniform scale must fit footprint width", uniform * towerParsed.widthX <= 8.41f)
        assertTrue("Uniform scale must fit footprint depth", uniform * towerParsed.depthZ <= 8.41f)

        val assignedKeys = mutableSetOf<String>()
        for (stage in 1..3) {
            sector.loadStage(stage)
            sector.objects.mapNotNullTo(assignedKeys) { it.modelKey }
        }
        for (key in listOf(
            "street_lamp",
            "cyberpunk_building",
            "cyberpunk_building_1",
            "cyberpunk_building_2",
            "industrial_tower"
        )) {
            assertTrue("Expected world stages to associate GLB key $key", key in assignedKeys)
        }
    }

    @Test
    fun `glb parser applies node hierarchy transforms and multi-primitive meshes`() {
        // Minimal in-memory GLB with 2 child nodes (translated + scaled) and 2 primitives on mesh 0
        val posBytes = java.nio.ByteBuffer.allocate(3 * 3 * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply {
            putFloat(0f); putFloat(0f); putFloat(0f)
            putFloat(1f); putFloat(0f); putFloat(0f)
            putFloat(0f); putFloat(1f); putFloat(0f)
        }.array()
        val idxBytes = java.nio.ByteBuffer.allocate(3 * 2 + 2).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply {
            putShort(0); putShort(1); putShort(2)
            putShort(0) // 4-byte padding
        }.array()
        val binChunk = posBytes + idxBytes
        val json = """
            {
              "asset": {"version": "2.0"},
              "scene": 0,
              "scenes": [{"nodes": [0]}],
              "nodes": [
                {"name": "parent", "translation": [10.0, 2.0, 0.0], "children": [1]},
                {"name": "child", "scale": [2.0, 3.0, 2.0], "mesh": 0}
              ],
              "meshes": [{
                "primitives": [
                  {"attributes": {"POSITION": 0}, "indices": 1, "material": 0},
                  {"attributes": {"POSITION": 0}, "indices": 1, "material": 1}
                ]
              }],
              "materials": [
                {"pbrMetallicRoughness": {"baseColorFactor": [1.0, 0.2, 0.2, 1.0]}},
                {"pbrMetallicRoughness": {"baseColorFactor": [0.2, 1.0, 0.2, 1.0]}}
              ],
              "bufferViews": [
                {"buffer": 0, "byteOffset": 0, "byteLength": 36},
                {"buffer": 0, "byteOffset": 36, "byteLength": 6}
              ],
              "accessors": [
                {"bufferView": 0, "componentType": 5126, "count": 3, "type": "VEC3"},
                {"bufferView": 1, "componentType": 5123, "count": 3, "type": "SCALAR"}
              ]
            }
        """.trimIndent().toByteArray(Charsets.UTF_8)
        val jsonPad = (4 - (json.size % 4)) % 4
        val paddedJson = json + ByteArray(jsonPad) { ' '.code.toByte() }
        val totalSize = 12 + 8 + paddedJson.size + 8 + binChunk.size
        val glb = java.nio.ByteBuffer.allocate(totalSize).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply {
            putInt(0x46546C67)
            putInt(2)
            putInt(totalSize)
            putInt(paddedJson.size)
            putInt(0x4E4F534A)
            put(paddedJson)
            putInt(binChunk.size)
            putInt(0x004E4942)
            put(binChunk)
        }.array()

        val parsed = com.example.engine3d.GlbModelLoader.parseCpuBytes(glb, "synthetic_multi.glb")!!
        assertEquals(2, parsed.parts.size)
        // Parent translation (10, 2, 0) + child scale (2, 3, 2) turns [0..1] into X=[10..12], Y=[2..5]
        assertEquals(10f, parsed.minX, 0.01f)
        assertEquals(12f, parsed.maxX, 0.01f)
        assertEquals(2f, parsed.minY, 0.01f)
        assertEquals(5f, parsed.maxY, 0.01f)
    }
}
