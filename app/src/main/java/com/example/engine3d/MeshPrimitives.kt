package com.example.engine3d

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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
    val cylinder: MeshData by lazy { buildCylinder(12) }
    val octahedron: MeshData by lazy { buildOctahedron() }
    val ring: MeshData by lazy { buildRing(24, 0.72f, 1.0f) }
    val disc: MeshData by lazy { buildRing(20, 0.0f, 1.0f) }
    val groundQuad: MeshData by lazy { buildGroundQuad() }

    private fun addTri(list: MutableList<Float>, ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, cx: Float, cy: Float, cz: Float) {
        val ux = bx - ax; val uy = by - ay; val uz = bz - az
        val vx = cx - ax; val vy = cy - ay; val vz = cz - az
        var nx = uy * vz - uz * vy
        var ny = uz * vx - ux * vz
        var nz = ux * vy - uy * vx
        val len = kotlin.math.sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-6f)
        nx /= len; ny /= len; nz /= len
        list.add(ax); list.add(ay); list.add(az); list.add(nx); list.add(ny); list.add(nz)
        list.add(bx); list.add(by); list.add(bz); list.add(nx); list.add(ny); list.add(nz)
        list.add(cx); list.add(cy); list.add(cz); list.add(nx); list.add(ny); list.add(nz)
    }

    private fun buildCube(): MeshData {
        val v = mutableListOf<Float>()
        val h = 0.5f
        // Front (+Z)
        addTri(v, -h, -h, h, h, -h, h, h, h, h)
        addTri(v, -h, -h, h, h, h, h, -h, h, h)
        // Back (-Z)
        addTri(v, h, -h, -h, -h, -h, -h, -h, h, -h)
        addTri(v, h, -h, -h, -h, h, -h, h, h, -h)
        // Left (-X)
        addTri(v, -h, -h, -h, -h, -h, h, -h, h, h)
        addTri(v, -h, -h, -h, -h, h, h, -h, h, -h)
        // Right (+X)
        addTri(v, h, -h, h, h, -h, -h, h, h, -h)
        addTri(v, h, -h, h, h, h, -h, h, h, h)
        // Top (+Y)
        addTri(v, -h, h, h, h, h, h, h, h, -h)
        addTri(v, -h, h, h, h, h, -h, -h, h, -h)
        // Bottom (-Y)
        addTri(v, -h, -h, -h, h, -h, -h, h, -h, h)
        addTri(v, -h, -h, -h, h, -h, h, -h, -h, h)
        return MeshData(v.toFloatArray())
    }

    private fun buildCylinder(segments: Int): MeshData {
        val v = mutableListOf<Float>()
        val r = 0.5f
        val h = 0.5f
        for (i in 0 until segments) {
            val a0 = (2.0 * PI * i / segments).toFloat()
            val a1 = (2.0 * PI * (i + 1) / segments).toFloat()
            val x0 = cos(a0) * r; val z0 = sin(a0) * r
            val x1 = cos(a1) * r; val z1 = sin(a1) * r
            // Side
            addTri(v, x0, -h, z0, x1, -h, z1, x1, h, z1)
            addTri(v, x0, -h, z0, x1, h, z1, x0, h, z0)
            // Top
            addTri(v, 0f, h, 0f, x0, h, z0, x1, h, z1)
            // Bottom
            addTri(v, 0f, -h, 0f, x1, -h, z1, x0, -h, z0)
        }
        return MeshData(v.toFloatArray())
    }

    private fun buildOctahedron(): MeshData {
        val v = mutableListOf<Float>()
        val r = 0.55f
        addTri(v, 0f, r, 0f, -r, 0f, r, r, 0f, r)
        addTri(v, 0f, r, 0f, r, 0f, r, r, 0f, -r)
        addTri(v, 0f, r, 0f, r, 0f, -r, -r, 0f, -r)
        addTri(v, 0f, r, 0f, -r, 0f, -r, -r, 0f, r)
        addTri(v, 0f, -r, 0f, r, 0f, r, -r, 0f, r)
        addTri(v, 0f, -r, 0f, r, 0f, -r, r, 0f, r)
        addTri(v, 0f, -r, 0f, -r, 0f, -r, r, 0f, -r)
        addTri(v, 0f, -r, 0f, -r, 0f, r, -r, 0f, -r)
        return MeshData(v.toFloatArray())
    }

    private fun buildRing(segments: Int, innerR: Float, outerR: Float): MeshData {
        val v = mutableListOf<Float>()
        for (i in 0 until segments) {
            val a0 = (2.0 * PI * i / segments).toFloat()
            val a1 = (2.0 * PI * (i + 1) / segments).toFloat()
            val ix0 = cos(a0) * innerR; val iz0 = sin(a0) * innerR
            val ix1 = cos(a1) * innerR; val iz1 = sin(a1) * innerR
            val ox0 = cos(a0) * outerR; val oz0 = sin(a0) * outerR
            val ox1 = cos(a1) * outerR; val oz1 = sin(a1) * outerR
            addTri(v, ix0, 0f, iz0, ox0, 0f, oz0, ox1, 0f, oz1)
            addTri(v, ix0, 0f, iz0, ox1, 0f, oz1, ix1, 0f, iz1)
        }
        return MeshData(v.toFloatArray())
    }

    private fun buildGroundQuad(): MeshData {
        val v = mutableListOf<Float>()
        val s = 0.5f
        addTri(v, -s, 0f, s, s, 0f, s, s, 0f, -s)
        addTri(v, -s, 0f, s, s, 0f, -s, -s, 0f, -s)
        return MeshData(v.toFloatArray())
    }
}
