package com.example.engine3d

import kotlin.math.abs

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
    val hasCollider: Boolean
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
    val tint: FloatArray
)

class CyberpunkWorldSector {
    val arenaHalfSize = 35f
    val objects = mutableListOf<WorldBoxObject>()
    val colliders = mutableListOf<AABB>()
    val puddles = mutableListOf<PuddleStrip>()

    init {
        buildModularSector()
    }

    private fun addProp(obj: WorldBoxObject) {
        objects.add(obj)
        if (obj.hasCollider) {
            colliders.add(obj.aabb)
        }
    }

    private fun buildModularSector() {
        // Colors inspired directly by the reference wet concrete alley & neon night aesthetic
        val warmAmber = floatArrayOf(0.92f, 0.58f, 0.22f)
        val alleyMagenta = floatArrayOf(0.84f, 0.22f, 0.58f)
        val coolTeal = floatArrayOf(0.22f, 0.62f, 0.80f)
        val mossGreenRail = floatArrayOf(0.22f, 0.38f, 0.28f)

        val concreteDark = floatArrayOf(0.17f, 0.21f, 0.26f)
        val concreteMid = floatArrayOf(0.24f, 0.28f, 0.34f)
        val rustSteel = floatArrayOf(0.30f, 0.21f, 0.17f)
        val oliveMetal = floatArrayOf(0.18f, 0.25f, 0.23f)

        // 1. Perimeter Weathered Concrete Industrial Buildings & Houses
        val perimeterSpecs = listOf(
            listOf(-28f, -38f, 11f, 22f, 9f, coolTeal, WorldPropKind.SKYSCRAPER),
            listOf(-14f, -39f, 12f, 28f, 10f, alleyMagenta, WorldPropKind.SKYSCRAPER),
            listOf(0f, -38f, 13f, 18f, 9f, warmAmber, WorldPropKind.HOUSE_WORKSHOP),
            listOf(14f, -39f, 12f, 26f, 10f, coolTeal, WorldPropKind.SKYSCRAPER),
            listOf(28f, -38f, 11f, 21f, 9f, warmAmber, WorldPropKind.SKYSCRAPER),

            listOf(-28f, 38f, 11f, 20f, 9f, warmAmber, WorldPropKind.SKYSCRAPER),
            listOf(-14f, 39f, 12f, 25f, 10f, coolTeal, WorldPropKind.SKYSCRAPER),
            listOf(0f, 38f, 13f, 18f, 9f, alleyMagenta, WorldPropKind.HOUSE_WORKSHOP),
            listOf(14f, 39f, 12f, 27f, 10f, warmAmber, WorldPropKind.SKYSCRAPER),
            listOf(28f, 38f, 11f, 22f, 9f, alleyMagenta, WorldPropKind.SKYSCRAPER),

            listOf(-38f, -24f, 9f, 24f, 12f, coolTeal, WorldPropKind.SKYSCRAPER),
            listOf(-38f, -8f, 9f, 16f, 12f, warmAmber, WorldPropKind.HOUSE_WORKSHOP),
            listOf(-38f, 8f, 9f, 26f, 12f, alleyMagenta, WorldPropKind.SKYSCRAPER),
            listOf(-38f, 24f, 9f, 19f, 12f, coolTeal, WorldPropKind.SKYSCRAPER),

            listOf(38f, -24f, 9f, 24f, 12f, alleyMagenta, WorldPropKind.SKYSCRAPER),
            listOf(38f, -8f, 9f, 28f, 12f, coolTeal, WorldPropKind.SKYSCRAPER),
            listOf(38f, 8f, 9f, 17f, 12f, warmAmber, WorldPropKind.HOUSE_WORKSHOP),
            listOf(38f, 24f, 9f, 23f, 12f, warmAmber, WorldPropKind.SKYSCRAPER)
        )

        perimeterSpecs.forEachIndexed { idx, s ->
            val x = s[0] as Float
            val z = s[1] as Float
            val sx = s[2] as Float
            val sy = s[3] as Float
            val sz = s[4] as Float
            val neon = s[5] as FloatArray
            val kind = s[6] as WorldPropKind
            addProp(
                WorldBoxObject(
                    id = "bld_$idx",
                    kind = kind,
                    x = x, y = sy * 0.5f, z = z,
                    sx = sx, sy = sy, sz = sz,
                    yawDeg = 0f,
                    baseColor = if (kind == WorldPropKind.HOUSE_WORKSHOP) concreteMid else concreteDark,
                    neonColor = neon,
                    hasCollider = true
                )
            )
        }

        // 2. Two-Story Concrete Houses / Workshops with Warm Garage Bays & Rooftop AC Units (Climbable Rooftops!)
        addProp(WorldBoxObject("house_nw", WorldPropKind.HOUSE_WORKSHOP, -16.5f, 2.4f, -15.5f, 6.2f, 4.8f, 5.4f, 0f, concreteMid, warmAmber, true))
        addProp(WorldBoxObject("house_ne", WorldPropKind.HOUSE_WORKSHOP, 16.5f, 2.5f, -15.5f, 5.8f, 5.0f, 5.2f, 0f, concreteMid, coolTeal, true))
        addProp(WorldBoxObject("house_sw", WorldPropKind.HOUSE_WORKSHOP, -16.5f, 2.3f, 16.0f, 6.0f, 4.6f, 5.4f, 0f, concreteMid, alleyMagenta, true))
        addProp(WorldBoxObject("house_se", WorldPropKind.HOUSE_WORKSHOP, 16.5f, 2.4f, 16.0f, 5.8f, 4.8f, 5.2f, 0f, concreteMid, warmAmber, true))

        // 3. Concrete Stairways / Raised Retaining Curbs with Green Metal Handrails (Corrimão)
        addProp(WorldBoxObject("stair_n", WorldPropKind.STAIR_PLATFORM, -8.5f, 0.55f, -18.5f, 4.4f, 1.1f, 3.8f, 0f, concreteMid, mossGreenRail, true))
        addProp(WorldBoxObject("stair_s", WorldPropKind.STAIR_PLATFORM, 8.5f, 0.55f, 18.5f, 4.4f, 1.1f, 3.8f, 0f, concreteMid, mossGreenRail, true))

        // Standalone Tactical Metal Handrails (Corrimão) along alley walkways
        val rails = listOf(
            Triple(-5.2f, -9.2f, 0f),
            Triple(5.8f, -10.0f, 90f),
            Triple(-6.0f, 10.5f, 90f),
            Triple(6.4f, 9.2f, 0f),
            Triple(-21.5f, 4.5f, 90f),
            Triple(21.5f, -4.5f, 90f)
        )
        rails.forEachIndexed { idx, (rx, rz, ry) ->
            addProp(
                WorldBoxObject(
                    id = "rail_$idx",
                    kind = WorldPropKind.HANDRAIL_BARRIER,
                    x = rx, y = 0.55f, z = rz,
                    sx = 4.2f, sy = 1.1f, sz = 0.35f,
                    yawDeg = ry,
                    baseColor = mossGreenRail,
                    neonColor = warmAmber,
                    hasCollider = true
                )
            )
        }

        // 4. Rusted & Weathered Shipping Containers
        val containers = listOf(
            listOf(-11.5f, 1.3f, -5.5f, 4.8f, 2.6f, 2.2f, 10f, oliveMetal, coolTeal),
            listOf(12.0f, 1.3f, -6.8f, 4.8f, 2.6f, 2.2f, -16f, rustSteel, warmAmber),
            listOf(-12.0f, 1.3f, 7.5f, 4.8f, 2.6f, 2.2f, -8f, concreteDark, alleyMagenta),
            listOf(11.5f, 1.3f, 7.2f, 4.8f, 2.6f, 2.2f, 18f, oliveMetal, warmAmber),
            listOf(0f, 1.3f, -23.0f, 5.2f, 2.6f, 2.2f, 0f, rustSteel, coolTeal),
            listOf(0f, 1.3f, 23.0f, 5.2f, 2.6f, 2.2f, 0f, oliveMetal, alleyMagenta)
        )
        containers.forEachIndexed { idx, c ->
            addProp(
                WorldBoxObject(
                    id = "cnt_$idx",
                    kind = WorldPropKind.CONTAINER,
                    x = c[0] as Float, y = c[1] as Float, z = c[2] as Float,
                    sx = c[3] as Float, sy = c[4] as Float, sz = c[5] as Float,
                    yawDeg = c[6] as Float,
                    baseColor = c[7] as FloatArray,
                    neonColor = c[8] as FloatArray,
                    hasCollider = true
                )
            )
        }

        // 5. Wrecked Cars & Street Clutter
        val cars = listOf(
            listOf(-6.8f, 0.55f, 3.8f, 3.3f, 1.1f, 1.65f, 24f, warmAmber),
            listOf(7.5f, 0.55f, -3.5f, 3.3f, 1.1f, 1.65f, -30f, alleyMagenta),
            listOf(-22.5f, 0.55f, -2.0f, 3.3f, 1.1f, 1.65f, 76f, coolTeal),
            listOf(22.5f, 0.55f, 3.5f, 3.3f, 1.1f, 1.65f, -82f, warmAmber)
        )
        cars.forEachIndexed { idx, c ->
            addProp(
                WorldBoxObject(
                    id = "car_$idx",
                    kind = WorldPropKind.WRECKED_CAR,
                    x = c[0] as Float, y = c[1] as Float, z = c[2] as Float,
                    sx = c[3] as Float, sy = c[4] as Float, sz = c[5] as Float,
                    yawDeg = c[6] as Float,
                    baseColor = floatArrayOf(0.18f, 0.20f, 0.24f),
                    neonColor = c[7] as FloatArray,
                    hasCollider = true
                )
            )
        }

        // 6. Alley Trash Bags, Crates & Barrels
        val clutter = listOf(
            Triple(-13.5f, -11.5f, warmAmber),
            Triple(13.8f, 12.2f, alleyMagenta),
            Triple(-20.0f, -9.5f, coolTeal),
            Triple(20.0f, 9.5f, warmAmber)
        )
        clutter.forEachIndexed { idx, (cx, cz, neon) ->
            addProp(WorldBoxObject("crt_$idx", WorldPropKind.CRATE_CLUSTER, cx, 0.45f, cz, 1.2f, 0.9f, 1.2f, 18f * idx, rustSteel, neon, true))
        }

        // 7. Streetlamps & Vertical Alley Neon Signs (like the vertical pink "NEON" sign in reference)
        val lampCoords = listOf(
            Pair(-9.5f, -15.5f) to warmAmber,
            Pair(9.5f, -15.5f) to coolTeal,
            Pair(-9.5f, 15.5f) to alleyMagenta,
            Pair(9.5f, 15.5f) to warmAmber,
            Pair(-24.5f, 0f) to coolTeal,
            Pair(24.5f, 0f) to alleyMagenta
        )
        lampCoords.forEachIndexed { idx, (pos, neon) ->
            addProp(WorldBoxObject("lmp_$idx", WorldPropKind.STREETLAMP, pos.first, 2.7f, pos.second, 0.22f, 5.4f, 0.22f, 0f, concreteDark, neon, false))
        }

        addProp(WorldBoxObject("holo_n", WorldPropKind.HOLO_BILLBOARD, 11.5f, 5.6f, -31.8f, 1.6f, 6.5f, 0.35f, 0f, concreteDark, alleyMagenta, false))
        addProp(WorldBoxObject("holo_s", WorldPropKind.HOLO_BILLBOARD, -11.5f, 5.6f, 31.8f, 1.6f, 6.5f, 0.35f, 0f, concreteDark, coolTeal, false))
        addProp(WorldBoxObject("holo_w", WorldPropKind.HOLO_BILLBOARD, -31.8f, 5.6f, -11.5f, 0.35f, 6.5f, 1.6f, 0f, concreteDark, warmAmber, false))
        addProp(WorldBoxObject("holo_e", WorldPropKind.HOLO_BILLBOARD, 31.8f, 5.6f, 11.5f, 0.35f, 6.5f, 1.6f, 0f, concreteDark, alleyMagenta, false))

        // 8. Wet Alley Puddles reflecting warm amber, cool teal & magenta
        puddles.add(PuddleStrip(-4.0f, -3.5f, 3.8f, 2.4f, coolTeal))
        puddles.add(PuddleStrip(5.2f, 3.2f, 3.2f, 3.8f, alleyMagenta))
        puddles.add(PuddleStrip(-12.5f, 2.5f, 4.0f, 2.6f, warmAmber))
        puddles.add(PuddleStrip(13.5f, -2.5f, 3.4f, 3.4f, coolTeal))
        puddles.add(PuddleStrip(0f, -13.0f, 4.2f, 2.4f, warmAmber))
        puddles.add(PuddleStrip(0f, 13.0f, 3.8f, 2.6f, alleyMagenta))
    }
}
