package com.example.engine3d

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES30
import android.opengl.GLUtils
import org.json.JSONObject
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.ShortBuffer

data class GlbModel(
    val vboId: Int,
    val iboId: Int,
    val indexCount: Int,
    val indexGlType: Int,
    val albedoTexId: Int,
    val emissiveTexId: Int,
    val minX: Float,
    val maxX: Float,
    val minY: Float,
    val maxY: Float,
    val minZ: Float,
    val maxZ: Float
) {
    val widthX: Float get() = (maxX - minX).coerceAtLeast(0.01f)
    val height: Float get() = (maxY - minY).coerceAtLeast(0.01f)
    val depthZ: Float get() = (maxZ - minZ).coerceAtLeast(0.01f)
    val centerX: Float get() = (minX + maxX) * 0.5f
    val centerZ: Float get() = (minZ + maxZ) * 0.5f
    val baseOffsetY: Float get() = -minY

    fun draw(posAttr: Int, normAttr: Int, uvAttr: Int) {
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
        val stride = 8 * 4 // 8 floats: xyz, nxyz, uv
        GLES30.glEnableVertexAttribArray(posAttr)
        GLES30.glVertexAttribPointer(posAttr, 3, GLES30.GL_FLOAT, false, stride, 0)

        if (normAttr >= 0) {
            GLES30.glEnableVertexAttribArray(normAttr)
            GLES30.glVertexAttribPointer(normAttr, 3, GLES30.GL_FLOAT, false, stride, 3 * 4)
        }
        if (uvAttr >= 0) {
            GLES30.glEnableVertexAttribArray(uvAttr)
            GLES30.glVertexAttribPointer(uvAttr, 2, GLES30.GL_FLOAT, false, stride, 6 * 4)
        }

        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, iboId)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, indexCount, indexGlType, 0)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)
    }
}

object GlbModelLoader {

    fun loadTexture(assets: AssetManager, assetPath: String): Int {
        return runCatching {
            val bmp = assets.open(assetPath).use { BitmapFactory.decodeStream(it) } ?: return 0
            uploadBitmapTexture(bmp)
        }.getOrDefault(0)
    }

    fun load(assets: AssetManager, assetPath: String): GlbModel? {
        return runCatching {
            val bytes = assets.open(assetPath).use { it.readBytes() }
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

            val magic = buf.int
            val version = buf.int
            buf.int // total length
            if (magic != 0x46546C67 || version != 2) return null

            var jsonText: String? = null
            var binStart = 0
            var binLen = 0

            while (buf.remaining() >= 8) {
                val chunkLen = buf.int
                val chunkType = buf.int
                if (chunkType == 0x4E4F534A) { // JSON
                    val jsonBytes = ByteArray(chunkLen)
                    buf.get(jsonBytes)
                    jsonText = String(jsonBytes, Charsets.UTF_8)
                } else if (chunkType == 0x004E4942) { // BIN
                    binStart = buf.position()
                    binLen = chunkLen
                    buf.position(binStart + chunkLen)
                } else {
                    buf.position(buf.position() + chunkLen)
                }
            }

            if (jsonText == null || binLen <= 0) return null
            val root = JSONObject(jsonText)
            val bufferViews = root.getJSONArray("bufferViews")
            val accessors = root.getJSONArray("accessors")
            val meshes = root.getJSONArray("meshes")
            val prim = meshes.getJSONObject(0).getJSONArray("primitives").getJSONObject(0)
            val attrs = prim.getJSONObject("attributes")

            val posAccIdx = attrs.getInt("POSITION")
            val normAccIdx = attrs.optInt("NORMAL", -1)
            val uvAccIdx = attrs.optInt("TEXCOORD_0", -1)
            val indicesAccIdx = prim.getInt("indices")

            val posAcc = accessors.getJSONObject(posAccIdx)
            val vertCount = posAcc.getInt("count")

            val posData = readFloatAccessor(bytes, binStart, bufferViews, posAcc, 3)
            val normData = if (normAccIdx >= 0) {
                readFloatAccessor(bytes, binStart, bufferViews, accessors.getJSONObject(normAccIdx), 3)
            } else {
                FloatArray(vertCount * 3) { if (it % 3 == 1) 1f else 0f }
            }
            val uvData = if (uvAccIdx >= 0) {
                readFloatAccessor(bytes, binStart, bufferViews, accessors.getJSONObject(uvAccIdx), 2)
            } else {
                FloatArray(vertCount * 2)
            }

            var minX = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxY = -Float.MAX_VALUE
            var minZ = Float.MAX_VALUE
            var maxZ = -Float.MAX_VALUE
            val interleaved = FloatArray(vertCount * 8)
            for (i in 0 until vertCount) {
                val x = posData[i * 3 + 0]
                val y = posData[i * 3 + 1]
                val z = posData[i * 3 + 2]
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
                if (z < minZ) minZ = z
                if (z > maxZ) maxZ = z

                interleaved[i * 8 + 0] = x
                interleaved[i * 8 + 1] = y
                interleaved[i * 8 + 2] = z
                interleaved[i * 8 + 3] = normData[i * 3 + 0]
                interleaved[i * 8 + 4] = normData[i * 3 + 1]
                interleaved[i * 8 + 5] = normData[i * 3 + 2]
                interleaved[i * 8 + 6] = uvData[i * 2 + 0]
                interleaved[i * 8 + 7] = uvData[i * 2 + 1]
            }

            val vboBuffer: FloatBuffer = ByteBuffer.allocateDirect(interleaved.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply { put(interleaved); position(0) }

            val idxAcc = accessors.getJSONObject(indicesAccIdx)
            val idxCount = idxAcc.getInt("count")
            val (idxBuffer, idxGlType, idxByteSize) = readIndexAccessor(bytes, binStart, bufferViews, idxAcc)

            val buffers = IntArray(2)
            GLES30.glGenBuffers(2, buffers, 0)
            val vboId = buffers[0]
            val iboId = buffers[1]

            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, interleaved.size * 4, vboBuffer, GLES30.GL_STATIC_DRAW)

            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, iboId)
            GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, idxCount * idxByteSize, idxBuffer, GLES30.GL_STATIC_DRAW)

            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)

            var albedoTexId = 0
            var emissiveTexId = 0
            val matIdx = prim.optInt("material", -1)
            if (matIdx >= 0 && root.has("materials")) {
                val mat = root.getJSONArray("materials").getJSONObject(matIdx)
                val textures = root.optJSONArray("textures")
                val images = root.optJSONArray("images")
                if (textures != null && images != null) {
                    val pbr = mat.optJSONObject("pbrMetallicRoughness")
                    val baseTexObj = pbr?.optJSONObject("baseColorTexture")
                    if (baseTexObj != null) {
                        val texIdx = baseTexObj.getInt("index")
                        val imgIdx = textures.getJSONObject(texIdx).getInt("source")
                        albedoTexId = decodeEmbeddedTexture(bytes, binStart, bufferViews, images.getJSONObject(imgIdx))
                    }
                    val emitTexObj = mat.optJSONObject("emissiveTexture")
                    if (emitTexObj != null) {
                        val texIdx = emitTexObj.getInt("index")
                        val imgIdx = textures.getJSONObject(texIdx).getInt("source")
                        emissiveTexId = decodeEmbeddedTexture(bytes, binStart, bufferViews, images.getJSONObject(imgIdx))
                    }
                }
            }

            if (albedoTexId == 0) albedoTexId = createSolidTexture(200, 205, 215, 255)
            if (emissiveTexId == 0) emissiveTexId = createSolidTexture(0, 0, 0, 255)

            GlbModel(
                vboId = vboId,
                iboId = iboId,
                indexCount = idxCount,
                indexGlType = idxGlType,
                albedoTexId = albedoTexId,
                emissiveTexId = emissiveTexId,
                minX = minX,
                maxX = maxX,
                minY = minY,
                maxY = maxY,
                minZ = minZ,
                maxZ = maxZ
            )
        }.getOrNull()
    }

    private fun readFloatAccessor(
        bytes: ByteArray,
        binStart: Int,
        bufferViews: org.json.JSONArray,
        acc: JSONObject,
        comps: Int
    ): FloatArray {
        val bvIdx = acc.getInt("bufferView")
        val bv = bufferViews.getJSONObject(bvIdx)
        val bvOffset = bv.optInt("byteOffset", 0)
        val accOffset = acc.optInt("byteOffset", 0)
        val stride = bv.optInt("byteStride", comps * 4).let { if (it <= 0) comps * 4 else it }
        val count = acc.getInt("count")
        val start = binStart + bvOffset + accOffset
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val out = FloatArray(count * comps)
        for (i in 0 until count) {
            val base = start + i * stride
            for (c in 0 until comps) {
                out[i * comps + c] = bb.getFloat(base + c * 4)
            }
        }
        return out
    }

    private fun readIndexAccessor(
        bytes: ByteArray,
        binStart: Int,
        bufferViews: org.json.JSONArray,
        acc: JSONObject
    ): Triple<Buffer, Int, Int> {
        val bvIdx = acc.getInt("bufferView")
        val bv = bufferViews.getJSONObject(bvIdx)
        val start = binStart + bv.optInt("byteOffset", 0) + acc.optInt("byteOffset", 0)
        val count = acc.getInt("count")
        val compType = acc.getInt("componentType")
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        return when (compType) {
            5121 -> { // UNSIGNED_BYTE -> promote to UNSIGNED_SHORT
                val sb: ShortBuffer = ByteBuffer.allocateDirect(count * 2)
                    .order(ByteOrder.nativeOrder())
                    .asShortBuffer()
                for (i in 0 until count) {
                    sb.put((bytes[start + i].toInt() and 0xFF).toShort())
                }
                sb.position(0)
                Triple(sb, GLES30.GL_UNSIGNED_SHORT, 2)
            }
            5123 -> { // UNSIGNED_SHORT
                val sb: ShortBuffer = ByteBuffer.allocateDirect(count * 2)
                    .order(ByteOrder.nativeOrder())
                    .asShortBuffer()
                for (i in 0 until count) {
                    sb.put(bb.getShort(start + i * 2))
                }
                sb.position(0)
                Triple(sb, GLES30.GL_UNSIGNED_SHORT, 2)
            }
            else -> { // 5125 UNSIGNED_INT
                val ib: IntBuffer = ByteBuffer.allocateDirect(count * 4)
                    .order(ByteOrder.nativeOrder())
                    .asIntBuffer()
                for (i in 0 until count) {
                    ib.put(bb.getInt(start + i * 4))
                }
                ib.position(0)
                Triple(ib, GLES30.GL_UNSIGNED_INT, 4)
            }
        }
    }

    private fun decodeEmbeddedTexture(
        bytes: ByteArray,
        binStart: Int,
        bufferViews: org.json.JSONArray,
        imgObj: JSONObject
    ): Int {
        val bvIdx = imgObj.optInt("bufferView", -1)
        if (bvIdx < 0) return 0
        val bv = bufferViews.getJSONObject(bvIdx)
        val offset = binStart + bv.optInt("byteOffset", 0)
        val len = bv.getInt("byteLength")
        val bmp = BitmapFactory.decodeByteArray(bytes, offset, len) ?: return 0
        return uploadBitmapTexture(bmp)
    }

    private fun uploadBitmapTexture(bmp: Bitmap): Int {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        val texId = ids[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_REPEAT)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        bmp.recycle()
        return texId
    }

    private fun createSolidTexture(r: Int, g: Int, b: Int, a: Int): Int {
        val bmp = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        bmp.setPixel(0, 0, (a shl 24) or (r shl 16) or (g shl 8) or b)
        return uploadBitmapTexture(bmp)
    }
}
