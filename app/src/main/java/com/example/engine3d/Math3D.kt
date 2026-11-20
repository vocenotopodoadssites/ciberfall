package com.example.engine3d

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class Vec3(var x: Float = 0f, var y: Float = 0f, var z: Float = 0f) {
    fun copyFrom(o: Vec3) { x = o.x; y = o.y; z = o.z }
    fun lenSq(): Float = x * x + y * y + z * z
    fun len(): Float = sqrt(lenSq())
    fun distTo(ox: Float, oz: Float): Float {
        val dx = x - ox
        val dz = z - oz
        return sqrt(dx * dx + dz * dz)
    }
    fun distTo(o: Vec3): Float {
        val dx = x - o.x
        val dy = y - o.y
        val dz = z - o.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
}

data class AABB(
    val minX: Float,
    val minY: Float,
    val minZ: Float,
    val maxX: Float,
    val maxY: Float,
    val maxZ: Float
) {
    fun overlapsFootprintXZ(x: Float, z: Float, margin: Float = 0.32f): Boolean {
        return x in (minX - margin)..(maxX + margin) && z in (minZ - margin)..(maxZ + margin)
    }

    fun resolveCircleXZ(pos: Vec3, radius: Float) {
        resolveCircleXZAndNormal(pos, radius, null)
    }

    fun resolveCircleXZAndNormal(pos: Vec3, radius: Float, outNormal: FloatArray?): Boolean {
        // If standing on top of or clearing the top of this box, do not push horizontally
        if (pos.y >= maxY - 0.15f) return false
        val closestX = pos.x.coerceIn(minX, maxX)
        val closestZ = pos.z.coerceIn(minZ, maxZ)
        val dx = pos.x - closestX
        val dz = pos.z - closestZ
        val distSq = dx * dx + dz * dz
        if (distSq < radius * radius) {
            if (distSq > 1e-6f) {
                val d = sqrt(distSq)
                val nx = dx / d
                val nz = dz / d
                val push = radius - d
                pos.x += nx * push
                pos.z += nz * push
                if (outNormal != null) {
                    outNormal[0] = nx
                    outNormal[1] = nz
                }
            } else {
                // Inside box, push out shortest axis
                val left = abs(pos.x - minX)
                val right = abs(maxX - pos.x)
                val front = abs(pos.z - minZ)
                val back = abs(maxZ - pos.z)
                val m = min(min(left, right), min(front, back))
                when (m) {
                    left -> { pos.x = minX - radius; outNormal?.set(0, -1f); outNormal?.set(1, 0f) }
                    right -> { pos.x = maxX + radius; outNormal?.set(0, 1f); outNormal?.set(1, 0f) }
                    front -> { pos.z = minZ - radius; outNormal?.set(0, 0f); outNormal?.set(1, -1f) }
                    else -> { pos.z = maxZ + radius; outNormal?.set(0, 0f); outNormal?.set(1, 1f) }
                }
            }
            return true
        }
        return false
    }

    /** Returns hit fraction t in [0, 1] along ray origin->target, or 1f if no hit. */
    fun intersectSegment(ox: Float, oy: Float, oz: Float, tx: Float, ty: Float, tz: Float): Float {
        val dx = tx - ox
        val dy = ty - oy
        val dz = tz - oz
        var tMin = 0f
        var tMax = 1f

        val axes = arrayOf(
            floatArrayOf(ox, dx, minX, maxX),
            floatArrayOf(oy, dy, minY, maxY),
            floatArrayOf(oz, dz, minZ, maxZ)
        )
        for (a in axes) {
            val o = a[0]
            val d = a[1]
            val mn = a[2]
            val mx = a[3]
            if (abs(d) < 1e-6f) {
                if (o < mn || o > mx) return 1f
            } else {
                var t1 = (mn - o) / d
                var t2 = (mx - o) / d
                if (t1 > t2) { val tmp = t1; t1 = t2; t2 = tmp }
                tMin = max(tMin, t1)
                tMax = min(tMax, t2)
                if (tMin > tMax) return 1f
            }
        }
        return tMin
    }
}

object Math3D {
    fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t.coerceIn(0f, 1f)

    fun lerpAngleDeg(current: Float, target: Float, t: Float): Float {
        var diff = (target - current) % 360f
        if (diff > 180f) diff -= 360f
        if (diff < -180f) diff += 360f
        return current + diff * t.coerceIn(0f, 1f)
    }

    fun rad(deg: Float): Float = deg * (Math.PI.toFloat() / 180f)
    fun deg(rad: Float): Float = rad * (180f / Math.PI.toFloat())
    fun sinDeg(deg: Float): Float = sin(rad(deg))
    fun cosDeg(deg: Float): Float = cos(rad(deg))
}
