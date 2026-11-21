package com.example.engine3d

import kotlin.math.abs
import kotlin.math.floor
import kotlin.random.Random

enum class WorldPropKind {
    SKYSCRAPER,
    HOUSE_WORKSHOP,
    CONTAINER,
    WRECKED_CAR,
    HANDRAIL_BARRIER,
    STAIR_PLATFORM,
    CRATE_CLUSTER,
    STREETLAMP,
    HOLO_BILLBOARD
}

data class StageTheme(
    val stageName: String,
    val fogColor: FloatArray,
    val floorBase: FloatArray,
    val light1: FloatArray,
    val light2: FloatArray,
    val light3: FloatArray,
    val primaryAccent: FloatArray,
    val secondaryAccent: FloatArray,
    val tertiaryAccent: FloatArray,
    val concreteDark: FloatArray,
    val concreteMid: FloatArray,
    val metalA: FloatArray,
    val metalB: FloatArray
)

data class WorldBoxObject(
    val id: String,
    val kind: WorldPropKind,
    val x: Float,
    val y: Float,
    val z: Float,
    val sx: Float,
    val sy: Float,
    val sz: Float,
    val yawDeg: Float,
    val baseColor: FloatArray,
    val neonColor: FloatArray,
    val hasCollider: Boolean,
    val chunkX: Int = 0,
    val chunkZ: Int = 0,
    val modelKey: String? = null
) {
    val aabb: AABB by lazy {
        val halfX = if (abs(yawDeg) > 40f && abs(yawDeg) < 50f) (sx + sz) * 0.38f else sx * 0.5f
        val halfZ = if (abs(yawDeg) > 40f && abs(yawDeg) < 50f) (sx + sz) * 0.38f else sz * 0.5f
        AABB(x - halfX, y - sy * 0.5f, z - halfZ, x + halfX, y + sy * 0.5f, z + halfZ)
    }
}

data class PuddleStrip(
    val x: Float,
    val z: Float,
    val radiusX: Float,
    val radiusZ: Float,
    val tint: FloatArray,
    val chunkX: Int = 0,
    val chunkZ: Int = 0
)

class CyberpunkWorldSector {
    val chunkSize = 48f
    var currentStage: Int = 1
        private set
    var stageTheme: StageTheme = themeForStage(1)
        private set
    val stageName: String
        get() = stageTheme.stageName

    val objects = mutableListOf<WorldBoxObject>()
    val colliders = mutableListOf<AABB>()
    val puddles = mutableListOf<PuddleStrip>()

    private val loadedChunks = mutableSetOf<Pair<Int, Int>>()
    private var lastCenterChunkX = Int.MIN_VALUE
    private var lastCenterChunkZ = Int.MIN_VALUE

    init {
        loadStage(1)
    }

    fun loadStage(stage: Int) {
        currentStage = stage.coerceAtLeast(1)
        stageTheme = themeForStage(currentStage)
        objects.clear()
        colliders.clear()
        puddles.clear()
        loadedChunks.clear()
        lastCenterChunkX = Int.MIN_VALUE
        lastCenterChunkZ = Int.MIN_VALUE

        // Build core sector at (0, 0) and stream immediate surrounding chunks
        loadedChunks.add(0 to 0)
        buildCoreSector(currentStage)
        ensureChunksAround(0f, 4f)
    }

    fun ensureChunksAround(px: Float, pz: Float) {
        val cx = floor((px + chunkSize * 0.5f) / chunkSize).toInt()
        val cz = floor((pz + chunkSize * 0.5f) / chunkSize).toInt()
        if (cx == lastCenterChunkX && cz == lastCenterChunkZ && loadedChunks.isNotEmpty()) {
            return
        }
        lastCenterChunkX = cx
        lastCenterChunkZ = cz

        var dirty = false
        // Prune distant non-core chunks beyond radius 1 (keeps ~144m x 144m populated seamlessly)
        val toRemove = loadedChunks.filter { (gx, gz) ->
            (gx != 0 || gz != 0) && (abs(gx - cx) > 1 || abs(gz - cz) > 1)
        }
        if (toRemove.isNotEmpty()) {
            val removeSet = toRemove.toSet()
            objects.removeAll { (it.chunkX to it.chunkZ) in removeSet }
            puddles.removeAll { (it.chunkX to it.chunkZ) in removeSet }
            loadedChunks.removeAll(removeSet)
            dirty = true
        }

        // Load 3x3 chunk neighborhood around player
        for (gx in (cx - 1)..(cx + 1)) {
            for (gz in (cz - 1)..(cz + 1)) {
                val key = gx to gz
                if (key !in loadedChunks) {
                    loadedChunks.add(key)
                    if (gx == 0 && gz == 0) {
                        buildCoreSector(currentStage)
                    } else {
                        buildProceduralChunk(gx, gz, currentStage)
                    }
                    dirty = true
                }
            }
        }

        if (dirty) {
            rebuildColliders()
        }
    }

    private fun rebuildColliders() {
        colliders.clear()
        for (obj in objects) {
            if (obj.hasCollider) {
                colliders.add(obj.aabb)
            }
        }
    }

    private fun addProp(obj: WorldBoxObject) {
        objects.add(obj)
        if (obj.hasCollider) {
            colliders.add(obj.aabb)
        }
    }

    private fun buildCoreSector(stage: Int) {
        val t = stageTheme
        val variant = ((stage - 1) % 3) + 1

        when (variant) {
            1 -> buildStage1AlleyCore(t)
            2 -> buildStage2DockyardCore(t)
            else -> buildStage3CitadelCore(t)
        }
    }

    private fun randomBuildingTexPool(seed: Int): List<String> {
        val base = listOf("tex_building_industrial", "tex_building_market", "tex_building_rowhouse")
        val r = Random(seed)
        return (base + base + base).shuffled(r)
    }

    private fun buildStage1AlleyCore(t: StageTheme) {
        val mossGreenRail = floatArrayOf(0.22f, 0.38f, 0.28f)
        val bldTex = randomBuildingTexPool(currentStage * 4099 + 11)

        // Open corner workshops & scattered skyscrapers with randomized cyberpunk facade textures
        addProp(WorldBoxObject("house_nw", WorldPropKind.HOUSE_WORKSHOP, -16.5f, 2.5f, -15.5f, 6.8f, 5.0f, 5.8f, 0f, t.concreteMid, t.primaryAccent, true, modelKey = bldTex[0]))
        addProp(WorldBoxObject("house_ne", WorldPropKind.HOUSE_WORKSHOP, 16.5f, 2.5f, -15.5f, 6.8f, 5.0f, 5.8f, 180f, t.concreteMid, t.secondaryAccent, true, modelKey = bldTex[1]))
        addProp(WorldBoxObject("house_sw", WorldPropKind.HOUSE_WORKSHOP, -16.5f, 2.4f, 16.0f, 6.8f, 4.8f, 5.8f, 90f, t.concreteMid, t.tertiaryAccent, true, modelKey = bldTex[2]))
        addProp(WorldBoxObject("house_se", WorldPropKind.HOUSE_WORKSHOP, 16.5f, 2.5f, 16.0f, 6.8f, 5.0f, 5.8f, -90f, t.concreteMid, t.primaryAccent, true, modelKey = bldTex[3]))

        // Corner high-rises framing the block while keeping N/S/E/W streets completely open
        addProp(WorldBoxObject("bld_nw", WorldPropKind.SKYSCRAPER, -23f, 10.5f, -23f, 8.2f, 21f, 8.2f, 0f, t.concreteDark, t.secondaryAccent, true, modelKey = bldTex[4]))
        addProp(WorldBoxObject("bld_ne", WorldPropKind.SKYSCRAPER, 23f, 12.0f, -23f, 7.2f, 24f, 7.2f, 90f, t.concreteDark, t.tertiaryAccent, true, modelKey = bldTex[5]))
        addProp(WorldBoxObject("bld_sw", WorldPropKind.SKYSCRAPER, -23f, 10.5f, 23f, 7.8f, 21f, 7.2f, 180f, t.concreteDark, t.primaryAccent, true, modelKey = bldTex[6]))
        addProp(WorldBoxObject("bld_se", WorldPropKind.SKYSCRAPER, 23f, 11.5f, 23f, 8.2f, 23f, 8.2f, -90f, t.concreteDark, t.secondaryAccent, true, modelKey = bldTex[7]))

        // Concrete stairways & handrails
        addProp(WorldBoxObject("stair_n", WorldPropKind.STAIR_PLATFORM, -8.5f, 0.55f, -18.5f, 4.4f, 1.1f, 3.8f, 0f, t.concreteMid, mossGreenRail, true))
        addProp(WorldBoxObject("stair_s", WorldPropKind.STAIR_PLATFORM, 8.5f, 0.55f, 18.5f, 4.4f, 1.1f, 3.8f, 0f, t.concreteMid, mossGreenRail, true))

        val rails = listOf(
            Triple(-5.2f, -9.2f, 0f),
            Triple(5.8f, -10.0f, 90f),
            Triple(-6.0f, 10.5f, 90f),
            Triple(6.4f, 9.2f, 0f),
            Triple(-21.5f, 4.5f, 90f),
            Triple(21.5f, -4.5f, 90f)
        )
        rails.forEachIndexed { idx, (rx, rz, ry) ->
            addProp(WorldBoxObject("rail_$idx", WorldPropKind.HANDRAIL_BARRIER, rx, 0.55f, rz, 4.2f, 1.1f, 0.35f, ry, mossGreenRail, t.primaryAccent, true))
        }

        // Shipping containers (preserving cnt_4 at (0, 1.3, -23.0) for climbable rooftop play!)
        val containers = listOf(
            listOf(-11.5f, 1.3f, -5.5f, 4.8f, 2.6f, 2.2f, 10f, t.metalB, t.secondaryAccent),
            listOf(12.0f, 1.3f, -6.8f, 4.8f, 2.6f, 2.2f, -16f, t.metalA, t.primaryAccent),
            listOf(-12.0f, 1.3f, 7.5f, 4.8f, 2.6f, 2.2f, -8f, t.concreteDark, t.tertiaryAccent),
            listOf(11.5f, 1.3f, 7.2f, 4.8f, 2.6f, 2.2f, 18f, t.metalB, t.primaryAccent),
            listOf(0f, 1.3f, -23.0f, 5.2f, 2.6f, 2.2f, 0f, t.metalA, t.secondaryAccent),
            listOf(0f, 1.3f, 23.0f, 5.2f, 2.6f, 2.2f, 0f, t.metalB, t.tertiaryAccent)
        )
        containers.forEachIndexed { idx, c ->
            addProp(
                WorldBoxObject(
                    "cnt_$idx", WorldPropKind.CONTAINER,
                    c[0] as Float, c[1] as Float, c[2] as Float,
                    c[3] as Float, c[4] as Float, c[5] as Float, c[6] as Float,
                    c[7] as FloatArray, c[8] as FloatArray, true
                )
            )
        }

        // Wrecked / cyber cars (automatically render GLB once delorean_time_machine.glb / combat_car.glb are uploaded)
        val cars = listOf(
            listOf(-6.8f, 0.55f, 3.8f, 3.3f, 1.1f, 1.65f, 24f, t.primaryAccent, "delorean_time_machine"),
            listOf(7.5f, 0.55f, -3.5f, 3.3f, 1.1f, 1.65f, -30f, t.tertiaryAccent, "combat_car"),
            listOf(-22.5f, 0.55f, -2.0f, 3.3f, 1.1f, 1.65f, 76f, t.secondaryAccent, "delorean_time_machine"),
            listOf(22.5f, 0.55f, 3.5f, 3.3f, 1.1f, 1.65f, -82f, t.primaryAccent, "combat_car")
        )
        cars.forEachIndexed { idx, c ->
            addProp(
                WorldBoxObject(
                    "car_$idx", WorldPropKind.WRECKED_CAR,
                    c[0] as Float, c[1] as Float, c[2] as Float,
                    c[3] as Float, c[4] as Float, c[5] as Float, c[6] as Float,
                    floatArrayOf(0.18f, 0.20f, 0.24f), c[7] as FloatArray, true,
                    modelKey = c[8] as String
                )
            )
        }

        // Crates, streetlamps & holo signs
        val clutter = listOf(
            Triple(-13.5f, -11.5f, t.primaryAccent),
            Triple(13.8f, 12.2f, t.tertiaryAccent),
            Triple(-20.0f, -9.5f, t.secondaryAccent),
            Triple(20.0f, 9.5f, t.primaryAccent)
        )
        clutter.forEachIndexed { idx, (cx, cz, neon) ->
            addProp(WorldBoxObject("crt_$idx", WorldPropKind.CRATE_CLUSTER, cx, 0.45f, cz, 1.2f, 0.9f, 1.2f, 18f * idx, t.metalA, neon, true))
        }

        val lamps = listOf(
            Pair(-9.5f, -15.5f) to t.primaryAccent,
            Pair(9.5f, -15.5f) to t.secondaryAccent,
            Pair(-9.5f, 15.5f) to t.tertiaryAccent,
            Pair(9.5f, 15.5f) to t.primaryAccent
        )
        lamps.forEachIndexed { idx, (pos, neon) ->
            val yaw = if (pos.first < 0f) 0f else 180f
            addProp(WorldBoxObject("lmp_$idx", WorldPropKind.STREETLAMP, pos.first, 2.7f, pos.second, 0.22f, 5.4f, 0.22f, yaw, t.concreteDark, neon, false))
        }

        addProp(WorldBoxObject("holo_nw", WorldPropKind.HOLO_BILLBOARD, -16.5f, 6.2f, -11.8f, 1.6f, 4.2f, 0.35f, 0f, t.concreteDark, t.tertiaryAccent, false))
        addProp(WorldBoxObject("holo_se", WorldPropKind.HOLO_BILLBOARD, 16.5f, 6.2f, 11.8f, 1.6f, 4.2f, 0.35f, 0f, t.concreteDark, t.secondaryAccent, false))

        puddles.add(PuddleStrip(-4.0f, -3.5f, 3.8f, 2.4f, t.secondaryAccent))
        puddles.add(PuddleStrip(5.2f, 3.2f, 3.2f, 3.8f, t.tertiaryAccent))
        puddles.add(PuddleStrip(-12.5f, 2.5f, 4.0f, 2.6f, t.primaryAccent))
        puddles.add(PuddleStrip(13.5f, -2.5f, 3.4f, 3.4f, t.secondaryAccent))
    }

    private fun buildStage2DockyardCore(t: StageTheme) {
        val safetyRail = floatArrayOf(0.86f, 0.56f, 0.16f)
        val bldTex = randomBuildingTexPool(currentStage * 6151 + 29)

        // Industrial cargo dockyard: container stacks lining central freight lane & textured buildings
        addProp(WorldBoxObject("dock_house_w", WorldPropKind.HOUSE_WORKSHOP, -19.5f, 2.6f, 0f, 6.8f, 5.2f, 6.4f, 90f, t.concreteMid, t.primaryAccent, true, modelKey = bldTex[0]))
        addProp(WorldBoxObject("dock_house_e", WorldPropKind.HOUSE_WORKSHOP, 19.5f, 2.6f, 0f, 6.8f, 5.2f, 6.4f, -90f, t.concreteMid, t.secondaryAccent, true, modelKey = bldTex[1]))

        // Twin container rows (climbable rooftops)
        val dockContainers = listOf(
            listOf(-9.0f, 1.3f, -12.5f, 2.4f, 2.6f, 5.6f, 0f, t.metalA, t.primaryAccent),
            listOf(-9.0f, 3.8f, -12.5f, 2.2f, 2.4f, 5.0f, 4f, t.metalB, t.secondaryAccent),
            listOf(9.0f, 1.3f, -12.5f, 2.4f, 2.6f, 5.6f, 0f, t.metalB, t.secondaryAccent),
            listOf(9.0f, 1.3f, 12.5f, 2.4f, 2.6f, 5.6f, 0f, t.metalA, t.tertiaryAccent),
            listOf(-9.0f, 1.3f, 12.5f, 2.4f, 2.6f, 5.6f, 0f, t.metalB, t.primaryAccent),
            listOf(0f, 1.3f, -19.5f, 5.8f, 2.6f, 2.4f, 0f, t.metalA, t.secondaryAccent),
            listOf(14.5f, 1.3f, -18.5f, 4.8f, 2.6f, 2.2f, 25f, t.metalB, t.primaryAccent),
            listOf(-14.5f, 1.3f, 18.5f, 4.8f, 2.6f, 2.2f, -20f, t.metalA, t.tertiaryAccent)
        )
        dockContainers.forEachIndexed { idx, c ->
            addProp(
                WorldBoxObject(
                    "d_cnt_$idx", WorldPropKind.CONTAINER,
                    c[0] as Float, c[1] as Float, c[2] as Float,
                    c[3] as Float, c[4] as Float, c[5] as Float, c[6] as Float,
                    c[7] as FloatArray, c[8] as FloatArray, true
                )
            )
        }

        // Elevated loading docks (stairs)
        addProp(WorldBoxObject("d_stair_w", WorldPropKind.STAIR_PLATFORM, -13.5f, 0.6f, -3.5f, 4.2f, 1.2f, 4.0f, 0f, t.concreteMid, safetyRail, true))
        addProp(WorldBoxObject("d_stair_e", WorldPropKind.STAIR_PLATFORM, 13.5f, 0.6f, 3.5f, 4.2f, 1.2f, 4.0f, 0f, t.concreteMid, safetyRail, true))

        // Safety handrails
        val dockRails = listOf(
            Triple(-4.8f, -4.5f, 90f),
            Triple(4.8f, -4.5f, 90f),
            Triple(-4.8f, 14.0f, 0f),
            Triple(4.8f, 14.0f, 0f)
        )
        dockRails.forEachIndexed { idx, (rx, rz, ry) ->
            addProp(WorldBoxObject("d_rail_$idx", WorldPropKind.HANDRAIL_BARRIER, rx, 0.55f, rz, 4.0f, 1.1f, 0.35f, ry, safetyRail, t.primaryAccent, true))
        }

        // Wrecked cargo vans / combat vehicles & crates
        addProp(WorldBoxObject("d_car_0", WorldPropKind.WRECKED_CAR, -5.0f, 0.55f, 8.8f, 3.6f, 1.2f, 1.8f, 14f, t.metalB, t.primaryAccent, true, modelKey = "combat_car"))
        addProp(WorldBoxObject("d_car_1", WorldPropKind.WRECKED_CAR, 5.5f, 0.55f, -9.2f, 3.6f, 1.2f, 1.8f, -22f, t.metalA, t.secondaryAccent, true, modelKey = "delorean_time_machine"))
        addProp(WorldBoxObject("d_crt_0", WorldPropKind.CRATE_CLUSTER, -14.2f, 0.45f, 7.5f, 1.4f, 0.9f, 1.4f, 12f, t.metalA, t.primaryAccent, true))
        addProp(WorldBoxObject("d_crt_1", WorldPropKind.CRATE_CLUSTER, 14.2f, 0.45f, -7.5f, 1.4f, 0.9f, 1.4f, 38f, t.metalB, t.secondaryAccent, true))

        // Corner dock industrial towers & cyberpunk buildings (open streets around them)
        addProp(WorldBoxObject("d_bld_nw", WorldPropKind.SKYSCRAPER, -22.5f, 11.5f, -22f, 7.8f, 23f, 7.0f, 0f, t.concreteDark, t.secondaryAccent, true, modelKey = bldTex[2]))
        addProp(WorldBoxObject("d_bld_ne", WorldPropKind.SKYSCRAPER, 22.5f, 11.5f, -22f, 7.6f, 23f, 7.6f, 90f, t.concreteDark, t.primaryAccent, true, modelKey = bldTex[3]))
        addProp(WorldBoxObject("d_bld_sw", WorldPropKind.SKYSCRAPER, -22.5f, 11.0f, 22f, 7.2f, 22f, 7.2f, 180f, t.concreteDark, t.tertiaryAccent, true, modelKey = bldTex[4]))
        addProp(WorldBoxObject("d_bld_se", WorldPropKind.SKYSCRAPER, 22.5f, 11.5f, 22f, 7.8f, 23f, 7.0f, -90f, t.concreteDark, t.primaryAccent, true, modelKey = bldTex[5]))

        val lampCoords = listOf(
            Pair(-5.5f, -15f) to t.primaryAccent,
            Pair(5.5f, -15f) to t.secondaryAccent,
            Pair(-5.5f, 15f) to t.secondaryAccent,
            Pair(5.5f, 15f) to t.primaryAccent
        )
        lampCoords.forEachIndexed { idx, (pos, neon) ->
            val yaw = if (pos.first < 0f) 0f else 180f
            addProp(WorldBoxObject("d_lmp_$idx", WorldPropKind.STREETLAMP, pos.first, 2.7f, pos.second, 0.22f, 5.4f, 0.22f, yaw, t.concreteDark, neon, false))
        }

        puddles.add(PuddleStrip(0f, -6.5f, 4.6f, 2.6f, t.secondaryAccent))
        puddles.add(PuddleStrip(0f, 10.5f, 4.2f, 3.0f, t.primaryAccent))
        puddles.add(PuddleStrip(-13.5f, 1.5f, 3.4f, 3.6f, t.secondaryAccent))
        puddles.add(PuddleStrip(13.5f, -1.5f, 3.4f, 3.6f, t.primaryAccent))
    }

    private fun buildStage3CitadelCore(t: StageTheme) {
        val cyberRail = floatArrayOf(0.24f, 0.54f, 0.78f)
        val bldTex = randomBuildingTexPool(currentStage * 8191 + 43)

        // High-tech Omega Citadel Plaza: textured monoliths, sky-stairs & neon holo barriers
        val monoliths = listOf(
            listOf(-16.5f, -16.5f, t.primaryAccent, bldTex[0], 0f),
            listOf(16.5f, -16.5f, t.secondaryAccent, bldTex[1], 90f),
            listOf(-16.5f, 16.5f, t.secondaryAccent, bldTex[2], 180f),
            listOf(16.5f, 16.5f, t.primaryAccent, bldTex[3], -90f)
        )
        monoliths.forEachIndexed { idx, m ->
            addProp(
                WorldBoxObject(
                    "c_mon_$idx", WorldPropKind.SKYSCRAPER,
                    m[0] as Float, 11f, m[1] as Float,
                    6.4f, 22f, 6.4f, m[4] as Float,
                    t.concreteDark, m[2] as FloatArray, true,
                    modelKey = m[3] as String
                )
            )
        }

        // 4 Climbable Cyber Platforms framing central duel courtyard
        addProp(WorldBoxObject("c_stair_nw", WorldPropKind.STAIR_PLATFORM, -8.5f, 0.65f, -8.5f, 4.2f, 1.3f, 4.2f, 0f, t.concreteMid, cyberRail, true))
        addProp(WorldBoxObject("c_stair_ne", WorldPropKind.STAIR_PLATFORM, 8.5f, 0.65f, -8.5f, 4.2f, 1.3f, 4.2f, 0f, t.concreteMid, cyberRail, true))
        addProp(WorldBoxObject("c_stair_sw", WorldPropKind.STAIR_PLATFORM, -8.5f, 0.65f, 8.5f, 4.2f, 1.3f, 4.2f, 0f, t.concreteMid, cyberRail, true))
        addProp(WorldBoxObject("c_stair_se", WorldPropKind.STAIR_PLATFORM, 8.5f, 0.65f, 8.5f, 4.2f, 1.3f, 4.2f, 0f, t.concreteMid, cyberRail, true))

        // Tech supply pods & workshops on outer plaza
        addProp(WorldBoxObject("c_hub_n", WorldPropKind.HOUSE_WORKSHOP, 0f, 2.5f, -20.0f, 6.8f, 5.0f, 5.8f, 0f, t.concreteMid, t.primaryAccent, true, modelKey = bldTex[4]))
        addProp(WorldBoxObject("c_hub_s", WorldPropKind.HOUSE_WORKSHOP, 0f, 2.5f, 20.5f, 6.8f, 5.0f, 5.8f, 180f, t.concreteMid, t.secondaryAccent, true, modelKey = bldTex[5]))
        addProp(WorldBoxObject("c_cnt_w", WorldPropKind.CONTAINER, -19.5f, 1.3f, 0f, 2.4f, 2.6f, 5.4f, 0f, t.metalA, t.primaryAccent, true))
        addProp(WorldBoxObject("c_cnt_e", WorldPropKind.CONTAINER, 19.5f, 1.3f, 0f, 2.4f, 2.6f, 5.4f, 0f, t.metalB, t.secondaryAccent, true))

        // Holo handrails
        val rails = listOf(
            Triple(-12.5f, 0f, 90f),
            Triple(12.5f, 0f, 90f),
            Triple(0f, -12.5f, 0f),
            Triple(0f, 13.5f, 0f)
        )
        rails.forEachIndexed { idx, (rx, rz, ry) ->
            addProp(WorldBoxObject("c_rail_$idx", WorldPropKind.HANDRAIL_BARRIER, rx, 0.55f, rz, 4.4f, 1.1f, 0.35f, ry, cyberRail, t.primaryAccent, true))
        }

        // Cyber vehicles
        addProp(WorldBoxObject("c_car_0", WorldPropKind.WRECKED_CAR, -14.5f, 0.55f, 6.2f, 3.3f, 1.1f, 1.65f, 45f, t.concreteDark, t.secondaryAccent, true, modelKey = "delorean_time_machine"))
        addProp(WorldBoxObject("c_car_1", WorldPropKind.WRECKED_CAR, 14.5f, 0.55f, -6.2f, 3.3f, 1.1f, 1.65f, -45f, t.concreteDark, t.primaryAccent, true, modelKey = "combat_car"))

        val lamps = listOf(
            Pair(-5.5f, -15f) to t.primaryAccent,
            Pair(5.5f, -15f) to t.secondaryAccent,
            Pair(-15f, -5.5f) to t.tertiaryAccent,
            Pair(15f, 5.5f) to t.primaryAccent
        )
        lamps.forEachIndexed { idx, (pos, neon) ->
            val yaw = if (pos.first < 0f) 0f else 180f
            addProp(WorldBoxObject("c_lmp_$idx", WorldPropKind.STREETLAMP, pos.first, 2.7f, pos.second, 0.22f, 5.4f, 0.22f, yaw, t.concreteDark, neon, false))
        }
        addProp(WorldBoxObject("c_holo_n", WorldPropKind.HOLO_BILLBOARD, -8.5f, 5.6f, -17.5f, 2.0f, 5.5f, 0.35f, 0f, t.concreteDark, t.primaryAccent, false))
        addProp(WorldBoxObject("c_holo_s", WorldPropKind.HOLO_BILLBOARD, 8.5f, 5.6f, 17.5f, 2.0f, 5.5f, 0.35f, 0f, t.concreteDark, t.secondaryAccent, false))

        puddles.add(PuddleStrip(0f, -3.5f, 4.5f, 3.0f, t.primaryAccent))
        puddles.add(PuddleStrip(-6.5f, 4.5f, 3.2f, 3.2f, t.secondaryAccent))
        puddles.add(PuddleStrip(6.5f, 4.5f, 3.2f, 3.2f, t.tertiaryAccent))
    }

    private fun buildProceduralChunk(gx: Int, gz: Int, stage: Int) {
        val t = stageTheme
        val seed = (stage * 73856093) xor (gx * 19349663) xor (gz * 83492791)
        val rng = Random(seed)
        val cx = gx * chunkSize
        val cz = gz * chunkSize
        val tag = "${stage}_${gx}_${gz}"

        val accents = arrayOf(t.primaryAccent, t.secondaryAccent, t.tertiaryAccent)
        val railCol = when ((stage - 1) % 3) {
            0 -> floatArrayOf(0.22f, 0.38f, 0.28f)
            1 -> floatArrayOf(0.86f, 0.56f, 0.16f)
            else -> floatArrayOf(0.24f, 0.54f, 0.78f)
        }

        // 1. Place 2 corner city-block structures away from the central cross-streets (leaves wide open exploration paths)
        val cornerOffsets = listOf(
            -15.5f to -15.5f,
            15.5f to -15.5f,
            -15.5f to 15.5f,
            15.5f to 15.5f
        )
        val firstCorner = rng.nextInt(4)
        val secondCorner = (firstCorner + 1 + rng.nextInt(3)) % 4
        val texOptions = arrayOf("tex_building_industrial", "tex_building_market", "tex_building_rowhouse")
        listOf(firstCorner, secondCorner).forEachIndexed { i, cIdx ->
            val (ox, oz) = cornerOffsets[cIdx]
            val isTower = rng.nextFloat() < 0.56f
            val accent = accents[(cIdx + stage) % accents.size]
            val yaw = listOf(0f, 90f, 180f, -90f)[rng.nextInt(4)]
            val texKey = texOptions[rng.nextInt(texOptions.size)]
            if (isTower) {
                val h = 18f + rng.nextFloat() * 10f
                objects.add(
                    WorldBoxObject(
                        "ch_bld_${tag}_$i", WorldPropKind.SKYSCRAPER,
                        cx + ox, h * 0.5f, cz + oz,
                        7.4f, h, 7.2f, yaw, t.concreteDark, accent, true, gx, gz,
                        modelKey = texKey
                    )
                )
            } else {
                val h = 4.6f + rng.nextFloat() * 1.1f
                objects.add(
                    WorldBoxObject(
                        "ch_ws_${tag}_$i", WorldPropKind.HOUSE_WORKSHOP,
                        cx + ox, h * 0.5f, cz + oz,
                        6.8f, h, 5.8f, yaw, t.concreteMid, accent, true, gx, gz,
                        modelKey = texKey
                    )
                )
            }
        }

        // 2. Shipping containers (climbable!)
        val containerSpots = listOf(-8.5f to 6.5f, 8.5f to -6.5f, -6.0f to -11.5f, 7.5f to 10.5f)
        val cPick = containerSpots[rng.nextInt(containerSpots.size)]
        val yawCnt = if (rng.nextBoolean()) 12f else -15f
        objects.add(
            WorldBoxObject(
                "ch_cnt_${tag}", WorldPropKind.CONTAINER,
                cx + cPick.first, 1.3f, cz + cPick.second,
                4.8f, 2.6f, 2.2f, yawCnt,
                if (rng.nextBoolean()) t.metalA else t.metalB,
                accents[rng.nextInt(accents.size)], true, gx, gz
            )
        )

        // 3. Stair platform OR Handrail barrier
        if (rng.nextBoolean()) {
            val sx = if (rng.nextBoolean()) -7.5f else 7.5f
            val sz = if (rng.nextBoolean()) -3.5f else 3.5f
            objects.add(
                WorldBoxObject(
                    "ch_st_${tag}", WorldPropKind.STAIR_PLATFORM,
                    cx + sx, 0.55f, cz + sz,
                    4.2f, 1.1f, 3.8f, 0f, t.concreteMid, railCol, true, gx, gz
                )
            )
        } else {
            val rx = if (rng.nextBoolean()) -4.5f else 4.5f
            val rz = if (rng.nextBoolean()) 7.5f else -7.5f
            objects.add(
                WorldBoxObject(
                    "ch_rl_${tag}", WorldPropKind.HANDRAIL_BARRIER,
                    cx + rx, 0.55f, cz + rz,
                    4.2f, 1.1f, 0.35f, if (rng.nextBoolean()) 0f else 90f,
                    railCol, accents[0], true, gx, gz
                )
            )
        }

        // 4. Wrecked / combat cars & crate cluster
        val carX = (rng.nextFloat() - 0.5f) * 14f
        val carZ = (rng.nextFloat() - 0.5f) * 14f
        val carModel = if (rng.nextBoolean()) "delorean_time_machine" else "combat_car"
        objects.add(
            WorldBoxObject(
                "ch_car_${tag}", WorldPropKind.WRECKED_CAR,
                cx + carX, 0.55f, cz + carZ,
                3.3f, 1.1f, 1.65f, rng.nextFloat() * 160f - 80f,
                floatArrayOf(0.18f, 0.20f, 0.24f), accents[rng.nextInt(accents.size)], true, gx, gz,
                modelKey = carModel
            )
        )

        val crtX = cx + (if (carX > 0f) -11f else 11f)
        val crtZ = cz + (if (carZ > 0f) -9f else 9f)
        objects.add(
            WorldBoxObject(
                "ch_crt_${tag}", WorldPropKind.CRATE_CLUSTER,
                crtX, 0.45f, crtZ,
                1.3f, 0.9f, 1.3f, 22f, t.metalA, accents[1], true, gx, gz
            )
        )

        // 5. Streetlamps & puddles (non-colliders for visual depth)
        objects.add(
            WorldBoxObject(
                "ch_lmp_${tag}", WorldPropKind.STREETLAMP,
                cx - 9.5f, 2.7f, cz + 9.5f,
                0.22f, 5.4f, 0.22f, 0f, t.concreteDark, accents[rng.nextInt(accents.size)], false, gx, gz
            )
        )
        puddles.add(
            PuddleStrip(
                cx + (rng.nextFloat() - 0.5f) * 10f,
                cz + (rng.nextFloat() - 0.5f) * 10f,
                3.5f, 2.5f, accents[rng.nextInt(accents.size)], gx, gz
            )
        )
    }

    companion object {
        fun themeForStage(stage: Int): StageTheme {
            val variant = (stage - 1) % 3
            return when (variant) {
                0 -> StageTheme(
                    stageName = if (stage == 1) "Beco Neo-Vértice" else "Distrito Vértice-$stage",
                    fogColor = floatArrayOf(0.060f, 0.090f, 0.138f),
                    floorBase = floatArrayOf(0.145f, 0.175f, 0.225f),
                    light1 = floatArrayOf(0.28f, 0.68f, 0.95f),
                    light2 = floatArrayOf(0.88f, 0.30f, 0.64f),
                    light3 = floatArrayOf(0.95f, 0.58f, 0.24f),
                    primaryAccent = floatArrayOf(0.92f, 0.58f, 0.22f),
                    secondaryAccent = floatArrayOf(0.22f, 0.65f, 0.85f),
                    tertiaryAccent = floatArrayOf(0.84f, 0.22f, 0.58f),
                    concreteDark = floatArrayOf(0.17f, 0.21f, 0.26f),
                    concreteMid = floatArrayOf(0.24f, 0.28f, 0.34f),
                    metalA = floatArrayOf(0.30f, 0.21f, 0.17f),
                    metalB = floatArrayOf(0.18f, 0.25f, 0.23f)
                )
                1 -> StageTheme(
                    stageName = if (stage == 2) "Porto Carga-09" else "Docas Ômega-$stage",
                    fogColor = floatArrayOf(0.048f, 0.095f, 0.112f),
                    floorBase = floatArrayOf(0.130f, 0.180f, 0.195f),
                    light1 = floatArrayOf(0.22f, 0.82f, 0.78f),
                    light2 = floatArrayOf(0.96f, 0.56f, 0.18f),
                    light3 = floatArrayOf(0.82f, 0.78f, 0.34f),
                    primaryAccent = floatArrayOf(0.96f, 0.56f, 0.18f),
                    secondaryAccent = floatArrayOf(0.18f, 0.82f, 0.80f),
                    tertiaryAccent = floatArrayOf(0.92f, 0.34f, 0.22f),
                    concreteDark = floatArrayOf(0.15f, 0.21f, 0.22f),
                    concreteMid = floatArrayOf(0.22f, 0.29f, 0.30f),
                    metalA = floatArrayOf(0.34f, 0.22f, 0.14f),
                    metalB = floatArrayOf(0.15f, 0.28f, 0.31f)
                )
                else -> StageTheme(
                    stageName = if (stage == 3) "Cidadela Ômega" else "Núcleo Ômega-$stage",
                    fogColor = floatArrayOf(0.068f, 0.060f, 0.135f),
                    floorBase = floatArrayOf(0.155f, 0.155f, 0.235f),
                    light1 = floatArrayOf(0.34f, 0.64f, 1.00f),
                    light2 = floatArrayOf(0.86f, 0.28f, 0.92f),
                    light3 = floatArrayOf(0.36f, 0.90f, 0.86f),
                    primaryAccent = floatArrayOf(0.26f, 0.82f, 0.98f),
                    secondaryAccent = floatArrayOf(0.80f, 0.28f, 0.96f),
                    tertiaryAccent = floatArrayOf(0.35f, 0.96f, 0.72f),
                    concreteDark = floatArrayOf(0.16f, 0.17f, 0.26f),
                    concreteMid = floatArrayOf(0.23f, 0.25f, 0.35f),
                    metalA = floatArrayOf(0.20f, 0.22f, 0.32f),
                    metalB = floatArrayOf(0.26f, 0.19f, 0.34f)
                )
            }
        }
    }
}
