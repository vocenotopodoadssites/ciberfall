package com.example.engine3d

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class MeshData(val vertices: FloatArray) {
    val vertexCount: Int = vertices.size / 6
    val buffer: FloatBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(vertices)
            position(0)
        }

    fun draw(posAttr: Int, normAttr: Int, mode: Int = GLES30.GL_TRIANGLES) {
        buffer.position(0)
        GLES30.glEnableVertexAttribArray(posAttr)
        GLES30.glVertexAttribPointer(posAttr, 3, GLES30.GL_FLOAT, false, 24, buffer)

        buffer.position(3)
        GLES30.glEnableVertexAttribArray(normAttr)
        GLES30.glVertexAttribPointer(normAttr, 3, GLES30.GL_FLOAT, false, 24, buffer)

        GLES30.glDrawArrays(mode, 0, vertexCount)
    }
}

object MeshPrimitives {
    val cube: MeshData by lazy { buildCube() }
    val cylinder: MeshData by lazy { buildCylinder(16) }
    val octahedron: MeshData by lazy { buildOctahedron() }
    val ring: MeshData by lazy { buildRing(32, 0.72f, 1.0f) }
    val disc: MeshData by lazy { buildRing(24, 0.0f, 1.0f) }
    val groundQuad: MeshData by lazy { buildGroundQuad() }

    // Procedural meshes centered at origin (~1x1x1 world unit bounds)
    val sphere: MeshData by lazy { buildSphere(14, 10) }
    val capsule: MeshData by lazy { buildCapsule(14, 5) }
    val beveledBox: MeshData by lazy { buildBeveledBox() }
    val cone: MeshData by lazy { buildCone(14) }
    val blade: MeshData by lazy { buildBlade() }
    val torus: MeshData by lazy { buildTorus(24, 8) }

    private fun p(x: Float, y: Float, z: Float) = floatArrayOf(x, y, z)

    private fun tri(v: MutableList<Float>, a: FloatArray, b: FloatArray, c: FloatArray) {
        val ux = b[0] - a[0]; val uy = b[1] - a[1]; val uz = b[2] - a[2]
        val vx = c[0] - a[0]; val vy = c[1] - a[1]; val vz = c[2] - a[2]
        var nx = uy * vz - uz * vy
        var ny = uz * vx - ux * vz
        var nz = ux * vy - uy * vx
        val len = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-6f)
        nx /= len; ny /= len; nz /= len
        for (pt in arrayOf(a, b, c)) {
            v.add(pt[0]); v.add(pt[1]); v.add(pt[2])
            v.add(nx); v.add(ny); v.add(nz)
        }
    }

    private fun quad(v: MutableList<Float>, a: FloatArray, b: FloatArray, c: FloatArray, d: FloatArray) {
        tri(v, a, b, c)
        tri(v, a, c, d)
    }

    private fun buildCube(): MeshData {
        val v = mutableListOf<Float>()
        val h = 0.5f
        quad(v, p(-h, -h, h), p(h, -h, h), p(h, h, h), p(-h, h, h))       // Front (+Z)
        quad(v, p(h, -h, -h), p(-h, -h, -h), p(-h, h, -h), p(h, h, -h))   // Back (-Z)
        quad(v, p(-h, -h, -h), p(-h, -h, h), p(-h, h, h), p(-h, h, -h))   // Left (-X)
        quad(v, p(h, -h, h), p(h, -h, -h), p(h, h, -h), p(h, h, h))       // Right (+X)
        quad(v, p(-h, h, h), p(h, h, h), p(h, h, -h), p(-h, h, -h))       // Top (+Y)
        quad(v, p(-h, -h, -h), p(h, -h, -h), p(h, -h, h), p(-h, -h, h))   // Bottom (-Y)
        return MeshData(v.toFloatArray())
    }

    private fun buildCylinder(n: Int): MeshData {
        val v = mutableListOf<Float>()
        val r = 0.5f
        val h = 0.5f
        for (i in 0 until n) {
            val a = 2.0 * PI * i / n
            val b = 2.0 * PI * (i + 1) / n
            val x0 = (r * cos(a)).toFloat(); val z0 = (r * sin(a)).toFloat()
            val x1 = (r * cos(b)).toFloat(); val z1 = (r * sin(b)).toFloat()
            // CCW outward winding in RH OpenGL (cos a, sin a moves +Z at a=0 -> swap x1/x0)
            quad(v, p(x1, -h, z1), p(x0, -h, z0), p(x0, h, z0), p(x1, h, z1))
            tri(v, p(0f, h, 0f), p(x1, h, z1), p(x0, h, z0))
            tri(v, p(0f, -h, 0f), p(x0, -h, z0), p(x1, -h, z1))
        }
        return MeshData(v.toFloatArray())
    }

    private fun buildOctahedron(): MeshData {
        val v = mutableListOf<Float>()
        val r = 0.55f
        for (i in 0 until 4) {
            val a = 2.0 * PI * i / 4
            val b = 2.0 * PI * (i + 1) / 4
            val aa = p((r * cos(a)).toFloat(), 0f, (r * sin(a)).toFloat())
            val bb = p((r * cos(b)).toFloat(), 0f, (r * sin(b)).toFloat())
            tri(v, p(0f, r, 0f), bb, aa)
            tri(v, p(0f, -r, 0f), aa, bb)
        }
        return MeshData(v.toFloatArray())
    }

    private fun buildRing(n: Int, inner: Float, outer: Float): MeshData {
        val v = mutableListOf<Float>()
        for (i in 0 until n) {
            val a = 2.0 * PI * i / n
            val b = 2.0 * PI * (i + 1) / n
            val oa = p((outer * cos(a)).toFloat(), 0f, (outer * sin(a)).toFloat())
            val ob = p((outer * cos(b)).toFloat(), 0f, (outer * sin(b)).toFloat())
            if (inner <= 1e-5f) {
                tri(v, p(0f, 0f, 0f), ob, oa)
            } else {
                quad(
                    v,
                    p((inner * cos(a)).toFloat(), 0f, (inner * sin(a)).toFloat()),
                    p((inner * cos(b)).toFloat(), 0f, (inner * sin(b)).toFloat()),
                    ob,
                    oa
                )
            }
        }
        return MeshData(v.toFloatArray())
    }

    private fun buildGroundQuad(): MeshData {
        val v = mutableListOf<Float>()
        val s = 0.5f
        quad(v, p(-s, 0f, s), p(s, 0f, s), p(s, 0f, -s), p(-s, 0f, -s))
        return MeshData(v.toFloatArray())
    }

    private fun buildSphere(n: Int, rings: Int): MeshData {
        val v = mutableListOf<Float>()
        fun point(i: Int, j: Int): FloatArray {
            val lat = PI * j / rings
            val lon = 2.0 * PI * i / n
            return p(
                (0.5 * sin(lat) * cos(lon)).toFloat(),
                (0.5 * cos(lat)).toFloat(),
                (0.5 * sin(lat) * sin(lon)).toFloat()
            )
        }
        for (j in 0 until rings) {
            for (i in 0 until n) {
                val a = point(i, j)
                val b = point(i + 1, j)
                val c = point(i + 1, j + 1)
                val d = point(i, j + 1)
                if (j > 0) tri(v, a, b, d)
                if (j < rings - 1) tri(v, b, c, d)
            }
        }
        return MeshData(v.toFloatArray())
    }

    private fun buildCapsule(n: Int, hemiRings: Int): MeshData {
        val v = mutableListOf<Float>()
        val radius = 0.38
        val halfShaft = 0.12f
        val totalLatSamples = hemiRings * 2 + 1

        fun ringPoint(i: Int, sampleIdx: Int): FloatArray {
            val lon = 2.0 * PI * i / n
            val lat: Double
            val offsetY: Float
            if (sampleIdx <= hemiRings) {
                lat = (PI * 0.5) * sampleIdx / hemiRings
                offsetY = halfShaft
            } else {
                val lowerIdx = sampleIdx - (hemiRings + 1)
                lat = (PI * 0.5) + (PI * 0.5) * lowerIdx / hemiRings
                offsetY = -halfShaft
            }
            return p(
                (radius * sin(lat) * cos(lon)).toFloat(),
                (radius * cos(lat)).toFloat() + offsetY,
                (radius * sin(lat) * sin(lon)).toFloat()
            )
        }

        for (j in 0 until totalLatSamples) {
            for (i in 0 until n) {
                val a = ringPoint(i, j)
                val b = ringPoint(i + 1, j)
                val c = ringPoint(i + 1, j + 1)
                val d = ringPoint(i, j + 1)
                if (j > 0) tri(v, a, b, d)
                if (j < totalLatSamples - 1) tri(v, b, c, d)
            }
        }
        return MeshData(v.toFloatArray())
    }

    private fun buildBeveledBox(): MeshData {
        // Chamfered octagonal cross-section with flat top/bottom caps
        val v = mutableListOf<Float>()
        val h = 0.5f
        val k = 0.36f
        val x = floatArrayOf(-k, k, h, h, k, -k, -h, -h)
        val z = floatArrayOf(h, h, k, -k, -h, -h, -k, k)
        for (i in 0..7) {
            val j = (i + 1) % 8
            quad(v, p(x[i], -h, z[i]), p(x[j], -h, z[j]), p(x[j], h, z[j]), p(x[i], h, z[i]))
            tri(v, p(0f, h, 0f), p(x[i], h, z[i]), p(x[j], h, z[j]))
            tri(v, p(0f, -h, 0f), p(x[j], -h, z[j]), p(x[i], -h, z[i]))
        }
        return MeshData(v.toFloatArray())
    }

    private fun buildCone(n: Int): MeshData {
        val v = mutableListOf<Float>()
        for (i in 0 until n) {
            val a = 2.0 * PI * i / n
            val b = 2.0 * PI * (i + 1) / n
            val aa = p((0.5 * cos(a)).toFloat(), -0.5f, (0.5 * sin(a)).toFloat())
            val bb = p((0.5 * cos(b)).toFloat(), -0.5f, (0.5 * sin(b)).toFloat())
            tri(v, bb, aa, p(0f, 0.5f, 0f))
            tri(v, p(0f, -0.5f, 0f), aa, bb)
        }
        return MeshData(v.toFloatArray())
    }

    private fun buildBlade(): MeshData {
        // Faceted diamond energy blade, tip pointing forward along +Z
        val v = mutableListOf<Float>()
        val tip = p(0f, 0f, 0.5f)
        val base = p(0f, 0f, -0.5f)
        val left = p(-0.5f, 0f, -0.16f)
        val right = p(0.5f, 0f, -0.16f)
        val top = p(0f, 0.14f, -0.12f)
        val bottom = p(0f, -0.14f, -0.12f)

        // Left bevel (outward normals)
        tri(v, tip, left, top)
        tri(v, base, top, left)
        tri(v, tip, bottom, left)
        tri(v, base, left, bottom)

        // Right bevel (mirrored winding for outward normals)
        tri(v, tip, top, right)
        tri(v, base, right, top)
        tri(v, tip, right, bottom)
        tri(v, base, bottom, right)

        return MeshData(v.toFloatArray())
    }

    private fun buildTorus(n: Int, m: Int): MeshData {
        val v = mutableListOf<Float>()
        fun point(i: Int, j: Int): FloatArray {
            val a = 2.0 * PI * i / n
            val b = 2.0 * PI * j / m
            val r = 0.40 + 0.10 * cos(b)
            return p(
                (r * cos(a)).toFloat(),
                (0.10 * sin(b)).toFloat(),
                (r * sin(a)).toFloat()
            )
        }
        for (i in 0 until n) {
            for (j in 0 until m) {
                quad(v, point(i, j), point(i, j + 1), point(i + 1, j + 1), point(i + 1, j))
            }
        }
        return MeshData(v.toFloatArray())
    }
}
