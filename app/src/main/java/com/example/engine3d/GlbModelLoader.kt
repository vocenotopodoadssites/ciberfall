package com.example.engine3d

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES30
import android.opengl.GLUtils
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.FileNotFoundException
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.ShortBuffer
import kotlin.math.abs
import kotlin.math.sqrt

data class ParsedGlbPart(
    val name: String,
    val interleavedVertices: FloatArray, // 8 floats/vert: xyz, nxyz, uv
    val vertexCount: Int,
    val indexCount: Int,
    val indexGlType: Int,
    val indexByteSize: Int,
    val indexBuffer: Buffer,
    val albedoBitmap: Bitmap?,
    val emissiveBitmap: Bitmap?,
    val baseColorFactor: FloatArray,
    val emissiveFactor: FloatArray,
    val alphaCutoff: Float,
    val doubleSided: Boolean,
    val unlit: Boolean
) {
    val triangleCount: Int get() = indexCount / 3
}

data class ParsedGlbData(
    val assetPath: String,
    val parts: List<ParsedGlbPart>,
    val minX: Float,
    val maxX: Float,
    val minY: Float,
    val maxY: Float,
    val minZ: Float,
    val maxZ: Float,
    val extensionsUsed: List<String>,
    val nodeCount: Int
) {
    val vertexCount: Int get() = parts.sumOf { it.vertexCount }
    val triangleCount: Int get() = parts.sumOf { it.triangleCount }
    val widthX: Float get() = (maxX - minX).coerceAtLeast(0.01f)
    val height: Float get() = (maxY - minY).coerceAtLeast(0.01f)
    val depthZ: Float get() = (maxZ - minZ).coerceAtLeast(0.01f)
    val albedoBitmap: Bitmap? get() = parts.firstOrNull { it.albedoBitmap != null }?.albedoBitmap
    val emissiveBitmap: Bitmap? get() = parts.firstOrNull { it.emissiveBitmap != null }?.emissiveBitmap
}

data class GlbSubMesh(
    val name: String,
    val vboId: Int,
    val iboId: Int,
    val indexCount: Int,
    val indexGlType: Int,
    val albedoTexId: Int,
    val emissiveTexId: Int,
    val baseColorFactor: FloatArray,
    val emissiveFactor: FloatArray,
    val alphaCutoff: Float,
    val doubleSided: Boolean,
    val unlit: Boolean
) {
    fun draw(posAttr: Int, normAttr: Int, uvAttr: Int) {
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
        val stride = 8 * 4 // 8 floats: xyz, nxyz, uv
        if (posAttr >= 0) {
            GLES30.glEnableVertexAttribArray(posAttr)
            GLES30.glVertexAttribPointer(posAttr, 3, GLES30.GL_FLOAT, false, stride, 0)
        }
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

        if (uvAttr >= 0) GLES30.glDisableVertexAttribArray(uvAttr)
        if (normAttr >= 0) GLES30.glDisableVertexAttribArray(normAttr)
        if (posAttr >= 0) GLES30.glDisableVertexAttribArray(posAttr)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)
    }
}

data class GlbModel(
    val assetPath: String,
    val parts: List<GlbSubMesh>,
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
}

object GlbModelLoader {
    private const val TAG = "GlbModelLoader"

    fun loadTexture(assets: AssetManager, assetPath: String): Int {
        return runCatching {
            val bmp = assets.open(assetPath).use { BitmapFactory.decodeStream(it) }
            if (bmp == null) {
                Log.e(TAG, "BitmapFactory.decodeStream returned null for $assetPath")
                return 0
            }
            val texId = uploadBitmapTexture(bmp)
            bmp.recycle()
            Log.i(TAG, "Loaded standalone texture $assetPath -> texId=$texId")
            texId
        }.onFailure { err ->
            Log.e(TAG, "Failed to load standalone texture $assetPath: ${err.message}", err)
        }.getOrDefault(0)
    }

    /**
     * Parses a glTF 2.0 binary (.glb) asset purely on the CPU (verifiable in JVM/Robolectric tests).
     * Supports scene node hierarchy transforms, multi-primitive meshes, standard & quantized accessors,
     * and EXT_texture_webp / standard image textures.
     */
    fun parseCpu(assets: AssetManager, assetPath: String): ParsedGlbData? {
        return try {
            val bytes = assets.open(assetPath).use { it.readBytes() }
            parseCpuBytes(bytes, assetPath)
        } catch (_: FileNotFoundException) {
            Log.w(TAG, "GLB asset not found in assets: $assetPath")
            null
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to parse GLB $assetPath: ${t.message}", t)
            null
        }
    }

    fun parseCpuBytes(bytes: ByteArray, assetPath: String = "<memory>"): ParsedGlbData? {
        if (bytes.size < 20) {
            Log.e(TAG, "GLB $assetPath is too small (${bytes.size} bytes)")
            return null
        }
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val magic = buf.int
        val version = buf.int
        buf.int // declared total length
        if (magic != 0x46546C67 || version != 2) {
            Log.e(TAG, "Invalid GLB header in $assetPath: magic=0x${magic.toString(16)}, ver=$version")
            return null
        }

        var jsonText: String? = null
        var binStart = 0
        var binLen = 0
        while (buf.remaining() >= 8) {
            val chunkLen = buf.int
            val chunkType = buf.int
            if (chunkLen < 0 || chunkLen > buf.remaining()) {
                Log.e(TAG, "Corrupt GLB chunk length $chunkLen in $assetPath")
                return null
            }
            when (chunkType) {
                0x4E4F534A -> { // JSON
                    val jsonBytes = ByteArray(chunkLen)
                    buf.get(jsonBytes)
                    jsonText = String(jsonBytes, Charsets.UTF_8)
                }
                0x004E4942 -> { // BIN
                    binStart = buf.position()
                    binLen = chunkLen
                    buf.position(binStart + chunkLen)
                }
                else -> buf.position(buf.position() + chunkLen)
            }
        }

        if (jsonText == null || binLen <= 0) {
            Log.e(TAG, "Missing JSON or BIN chunk in $assetPath")
            return null
        }

        val root = JSONObject(jsonText)
        val extsUsedArr = root.optJSONArray("extensionsUsed")
        val extsUsed = buildList {
            if (extsUsedArr != null) {
                for (i in 0 until extsUsedArr.length()) add(extsUsedArr.getString(i))
            }
        }
        val extsReqArr = root.optJSONArray("extensionsRequired")
        if (extsReqArr != null) {
            for (i in 0 until extsReqArr.length()) {
                val req = extsReqArr.getString(i)
                if (req == "KHR_draco_mesh_compression") {
                    Log.e(TAG, "Unsupported Draco compression required by $assetPath; export without Draco.")
                    return null
                }
            }
        }

        val bufferViews = root.optJSONArray("bufferViews") ?: JSONArray()
        val accessors = root.optJSONArray("accessors") ?: JSONArray()
        val meshes = root.optJSONArray("meshes") ?: JSONArray()
        if (meshes.length() == 0) {
            Log.e(TAG, "GLB $assetPath contains 0 meshes")
            return null
        }
        val materials = root.optJSONArray("materials")
        val textures = root.optJSONArray("textures")
        val images = root.optJSONArray("images")
        val nodes = root.optJSONArray("nodes")
        val scenes = root.optJSONArray("scenes")

        // Cache decoded images once per image index so multi-primitive meshes share textures efficiently
        val decodedImageCache = HashMap<Int, Bitmap?>()

        // Collect all (nodeName, meshIdx, worldMatrix) entries from scene graph
        val instances = mutableListOf<Triple<String, Int, FloatArray>>()
        if (nodes != null && nodes.length() > 0) {
            val rootNodes = mutableListOf<Int>()
            if (scenes != null && scenes.length() > 0) {
                val sceneIdx = root.optInt("scene", 0).coerceIn(0, scenes.length() - 1)
                val sceneNodes = scenes.getJSONObject(sceneIdx).optJSONArray("nodes")
                if (sceneNodes != null) {
                    for (i in 0 until sceneNodes.length()) rootNodes.add(sceneNodes.getInt(i))
                }
            }
            if (rootNodes.isEmpty()) {
                // Fallback: treat nodes with no parents as roots
                val hasParent = BooleanArray(nodes.length())
                for (ni in 0 until nodes.length()) {
                    val children = nodes.getJSONObject(ni).optJSONArray("children") ?: continue
                    for (ci in 0 until children.length()) {
                        val cIdx = children.getInt(ci)
                        if (cIdx in 0 until nodes.length()) hasParent[cIdx] = true
                    }
                }
                for (ni in 0 until nodes.length()) {
                    if (!hasParent[ni]) rootNodes.add(ni)
                }
            }

            val visited = BooleanArray(nodes.length())
            fun visitNode(nodeIdx: Int, parentMat: FloatArray) {
                if (nodeIdx !in 0 until nodes.length() || visited[nodeIdx]) return
                visited[nodeIdx] = true
                val nodeObj = nodes.getJSONObject(nodeIdx)
                val localMat = readNodeLocalMatrix(nodeObj)
                val worldMat = Mat4.mul(parentMat, localMat)
                val meshIdx = nodeObj.optInt("mesh", -1)
                if (meshIdx in 0 until meshes.length()) {
                    val nodeName = nodeObj.optString("name", "node_$nodeIdx")
                    instances.add(Triple(nodeName, meshIdx, worldMat))
                }
                val children = nodeObj.optJSONArray("children")
                if (children != null) {
                    for (ci in 0 until children.length()) {
                        visitNode(children.getInt(ci), worldMat)
                    }
                }
            }

            for (rIdx in rootNodes) {
                visitNode(rIdx, Mat4.identity())
            }
        }

        if (instances.isEmpty()) {
            for (mi in 0 until meshes.length()) {
                instances.add(Triple("mesh_$mi", mi, Mat4.identity()))
            }
        }

        val parsedParts = mutableListOf<ParsedGlbPart>()
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var minZ = Float.MAX_VALUE
        var maxZ = -Float.MAX_VALUE

        for ((nodeName, meshIdx, worldMat) in instances) {
            val meshObj = meshes.getJSONObject(meshIdx)
            val primitives = meshObj.optJSONArray("primitives") ?: continue
            val det3 = Mat4.det3x3(worldMat)
            val normalMat3 = Mat4.inverseTranspose3x3(worldMat)

            for (primIdx in 0 until primitives.length()) {
                val prim = primitives.getJSONObject(primIdx)
                if (prim.has("extensions") && prim.getJSONObject("extensions").has("KHR_draco_mesh_compression")) {
                    Log.e(TAG, "Primitive $primIdx in $assetPath uses KHR_draco_mesh_compression without fallback buffer!")
                    return null
                }

                val mode = prim.optInt("mode", 4)
                if (mode !in listOf(4, 5, 6)) {
                    Log.w(TAG, "Skipping non-triangle primitive mode=$mode in $assetPath ($nodeName:$primIdx)")
                    continue
                }

                val attrs = prim.optJSONObject("attributes") ?: continue
                if (!attrs.has("POSITION")) continue

                val posAccIdx = attrs.getInt("POSITION")
                val normAccIdx = attrs.optInt("NORMAL", -1)
                val uvAccIdx = attrs.optInt("TEXCOORD_0", -1)
                val indicesAccIdx = prim.optInt("indices", -1)

                val posAcc = accessors.getJSONObject(posAccIdx)
                val vertCount = posAcc.getInt("count")
                if (vertCount <= 0) continue

                val rawPos = readFloatAccessor(bytes, binStart, binLen, bufferViews, posAcc, 3)
                val rawNorm = if (normAccIdx >= 0) {
                    readFloatAccessor(bytes, binStart, binLen, bufferViews, accessors.getJSONObject(normAccIdx), 3)
                } else null
                val rawUv = if (uvAccIdx >= 0) {
                    readFloatAccessor(bytes, binStart, binLen, bufferViews, accessors.getJSONObject(uvAccIdx), 2)
                } else {
                    FloatArray(vertCount * 2)
                }

                val triIndices = readTriangulatedIndices(
                    bytes = bytes,
                    binStart = binStart,
                    binLen = binLen,
                    bufferViews = bufferViews,
                    acc = if (indicesAccIdx >= 0) accessors.getJSONObject(indicesAccIdx) else null,
                    vertexCount = vertCount,
                    mode = mode,
                    flipWinding = det3 < 0f
                )
                if (triIndices.isEmpty()) continue

                val transformedPos = FloatArray(vertCount * 3)
                val transformedNorm = FloatArray(vertCount * 3)

                for (i in 0 until vertCount) {
                    val px = rawPos[i * 3 + 0]
                    val py = rawPos[i * 3 + 1]
                    val pz = rawPos[i * 3 + 2]
                    val wx = worldMat[0] * px + worldMat[4] * py + worldMat[8] * pz + worldMat[12]
                    val wy = worldMat[1] * px + worldMat[5] * py + worldMat[9] * pz + worldMat[13]
                    val wz = worldMat[2] * px + worldMat[6] * py + worldMat[10] * pz + worldMat[14]

                    transformedPos[i * 3 + 0] = wx
                    transformedPos[i * 3 + 1] = wy
                    transformedPos[i * 3 + 2] = wz

                    if (wx < minX) minX = wx
                    if (wx > maxX) maxX = wx
                    if (wy < minY) minY = wy
                    if (wy > maxY) maxY = wy
                    if (wz < minZ) minZ = wz
                    if (wz > maxZ) maxZ = wz

                    if (rawNorm != null) {
                        val nx = rawNorm[i * 3 + 0]
                        val ny = rawNorm[i * 3 + 1]
                        val nz = rawNorm[i * 3 + 2]
                        val tnx = normalMat3[0] * nx + normalMat3[3] * ny + normalMat3[6] * nz
                        val tny = normalMat3[1] * nx + normalMat3[4] * ny + normalMat3[7] * nz
                        val tnz = normalMat3[2] * nx + normalMat3[5] * ny + normalMat3[8] * nz
                        val len = sqrt(tnx * tnx + tny * tny + tnz * tnz)
                        if (len > 1e-5f) {
                            transformedNorm[i * 3 + 0] = tnx / len
                            transformedNorm[i * 3 + 1] = tny / len
                            transformedNorm[i * 3 + 2] = tnz / len
                        } else {
                            transformedNorm[i * 3 + 1] = 1f
                        }
                    }
                }

                // Synthesize smooth vertex normals if GLB primitive omitted NORMAL attribute
                if (rawNorm == null) {
                    computeFallbackNormals(transformedPos, triIndices, transformedNorm)
                }

                val interleaved = FloatArray(vertCount * 8)
                for (i in 0 until vertCount) {
                    interleaved[i * 8 + 0] = transformedPos[i * 3 + 0]
                    interleaved[i * 8 + 1] = transformedPos[i * 3 + 1]
                    interleaved[i * 8 + 2] = transformedPos[i * 3 + 2]
                    interleaved[i * 8 + 3] = transformedNorm[i * 3 + 0]
                    interleaved[i * 8 + 4] = transformedNorm[i * 3 + 1]
                    interleaved[i * 8 + 5] = transformedNorm[i * 3 + 2]
                    interleaved[i * 8 + 6] = rawUv[i * 2 + 0]
                    interleaved[i * 8 + 7] = rawUv[i * 2 + 1]
                }

                val (idxBuf, idxGlType, idxByteSize) = packIndexBuffer(triIndices, vertCount)

                var albedoBitmap: Bitmap? = null
                var emissiveBitmap: Bitmap? = null
                val baseColorFactor = floatArrayOf(1f, 1f, 1f, 1f)
                val emissiveFactor = floatArrayOf(1f, 1f, 1f)
                var alphaCutoff = 0f
                var doubleSided = false
                var unlit = false

                val matIdx = prim.optInt("material", -1)
                if (matIdx >= 0 && materials != null && matIdx < materials.length()) {
                    val mat = materials.getJSONObject(matIdx)
                    doubleSided = mat.optBoolean("doubleSided", false)
                    val matExts = mat.optJSONObject("extensions")
                    unlit = matExts?.has("KHR_materials_unlit") == true
                    val alphaMode = mat.optString("alphaMode", "OPAQUE")
                    if (alphaMode == "MASK") {
                        alphaCutoff = mat.optDouble("alphaCutoff", 0.5).toFloat()
                    }

                    val ef = mat.optJSONArray("emissiveFactor")
                    if (ef != null && ef.length() >= 3) {
                        for (c in 0 until 3) emissiveFactor[c] = ef.getDouble(c).toFloat()
                    }

                    val pbr = mat.optJSONObject("pbrMetallicRoughness")
                    val factorArr = pbr?.optJSONArray("baseColorFactor")
                    if (factorArr != null && factorArr.length() >= 3) {
                        for (c in 0 until minOf(4, factorArr.length())) {
                            baseColorFactor[c] = factorArr.getDouble(c).toFloat()
                        }
                    }

                    if (textures != null && images != null) {
                        val baseTexObj = pbr?.optJSONObject("baseColorTexture")
                        if (baseTexObj != null) {
                            val texIdx = baseTexObj.getInt("index")
                            if (texIdx in 0 until textures.length()) {
                                val imgIdx = resolveTextureImageIndex(textures.getJSONObject(texIdx))
                                if (imgIdx in 0 until images.length()) {
                                    albedoBitmap = decodedImageCache.getOrPut(imgIdx) {
                                        decodeEmbeddedBitmap(bytes, binStart, binLen, bufferViews, images.getJSONObject(imgIdx), assetPath, "albedo")
                                    }
                                }
                            }
                        }
                        val emitTexObj = mat.optJSONObject("emissiveTexture")
                        if (emitTexObj != null) {
                            val texIdx = emitTexObj.getInt("index")
                            if (texIdx in 0 until textures.length()) {
                                val imgIdx = resolveTextureImageIndex(textures.getJSONObject(texIdx))
                                if (imgIdx in 0 until images.length()) {
                                    emissiveBitmap = decodedImageCache.getOrPut(imgIdx) {
                                        decodeEmbeddedBitmap(bytes, binStart, binLen, bufferViews, images.getJSONObject(imgIdx), assetPath, "emissive")
                                    }
                                }
                            }
                        }
                    }
                }

                parsedParts.add(
                    ParsedGlbPart(
                        name = "${nodeName}_p$primIdx",
                        interleavedVertices = interleaved,
                        vertexCount = vertCount,
                        indexCount = triIndices.size,
                        indexGlType = idxGlType,
                        indexByteSize = idxByteSize,
                        indexBuffer = idxBuf,
                        albedoBitmap = albedoBitmap,
                        emissiveBitmap = emissiveBitmap,
                        baseColorFactor = baseColorFactor,
                        emissiveFactor = emissiveFactor,
                        alphaCutoff = alphaCutoff,
                        doubleSided = doubleSided,
                        unlit = unlit
                    )
                )
            }
        }

        if (parsedParts.isEmpty()) {
            Log.e(TAG, "GLB $assetPath produced 0 renderable primitives")
            return null
        }

        val totalVerts = parsedParts.sumOf { it.vertexCount }
        val totalTris = parsedParts.sumOf { it.triangleCount }
        val firstAlbedo = parsedParts.firstOrNull { it.albedoBitmap != null }?.albedoBitmap
        val firstEmissive = parsedParts.firstOrNull { it.emissiveBitmap != null }?.emissiveBitmap
        Log.i(
            TAG,
            "Parsed GLB $assetPath: parts=${parsedParts.size}, nodes=${nodes?.length() ?: 0}, verts=$totalVerts, tris=$totalTris, " +
                "albedo=${firstAlbedo?.let { "${it.width}x${it.height}" } ?: "none"}, " +
                "emissive=${firstEmissive?.let { "${it.width}x${it.height}" } ?: "none"}, " +
                "bounds=[(${"%.2f".format(minX)},${"%.2f".format(minY)},${"%.2f".format(minZ)})..(${"%.2f".format(maxX)},${"%.2f".format(maxY)},${"%.2f".format(maxZ)})]"
        )

        return ParsedGlbData(
            assetPath = assetPath,
            parts = parsedParts,
            minX = minX,
            maxX = maxX,
            minY = minY,
            maxY = maxY,
            minZ = minZ,
            maxZ = maxZ,
            extensionsUsed = extsUsed,
            nodeCount = nodes?.length() ?: 0
        )
    }

    fun load(assets: AssetManager, assetPath: String): GlbModel? {
        val parsed = parseCpu(assets, assetPath) ?: return null
        return try {
            // Upload each unique decoded Bitmap only once to GPU texture memory
            val uploadedBitmapTexIds = IdentityHashMapSafe<Bitmap, Int>()
            val gpuParts = mutableListOf<GlbSubMesh>()

            for (part in parsed.parts) {
                val vboBuffer: FloatBuffer = ByteBuffer.allocateDirect(part.interleavedVertices.size * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer()
                    .apply {
                        put(part.interleavedVertices)
                        position(0)
                    }

                val buffers = IntArray(2)
                GLES30.glGenBuffers(2, buffers, 0)
                val vboId = buffers[0]
                val iboId = buffers[1]

                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
                GLES30.glBufferData(
                    GLES30.GL_ARRAY_BUFFER,
                    part.interleavedVertices.size * 4,
                    vboBuffer,
                    GLES30.GL_STATIC_DRAW
                )

                part.indexBuffer.position(0)
                GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, iboId)
                GLES30.glBufferData(
                    GLES30.GL_ELEMENT_ARRAY_BUFFER,
                    part.indexCount * part.indexByteSize,
                    part.indexBuffer,
                    GLES30.GL_STATIC_DRAW
                )

                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
                GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)

                val albedoTexId = if (part.albedoBitmap != null) {
                    uploadedBitmapTexIds.getOrPut(part.albedoBitmap) {
                        uploadBitmapTexture(part.albedoBitmap)
                    }
                } else {
                    createSolidTexture(255, 255, 255, 255)
                }

                val emissiveTexId = if (part.emissiveBitmap != null) {
                    uploadedBitmapTexIds.getOrPut(part.emissiveBitmap) {
                        uploadBitmapTexture(part.emissiveBitmap)
                    }
                } else {
                    createSolidTexture(0, 0, 0, 255)
                }

                gpuParts.add(
                    GlbSubMesh(
                        name = part.name,
                        vboId = vboId,
                        iboId = iboId,
                        indexCount = part.indexCount,
                        indexGlType = part.indexGlType,
                        albedoTexId = albedoTexId,
                        emissiveTexId = emissiveTexId,
                        baseColorFactor = part.baseColorFactor,
                        emissiveFactor = part.emissiveFactor,
                        alphaCutoff = part.alphaCutoff,
                        doubleSided = part.doubleSided,
                        unlit = part.unlit
                    )
                )
            }

            // Recycle bitmaps now that all GPU uploads are done
            for (bmp in uploadedBitmapTexIds.keys) {
                if (!bmp.isRecycled) bmp.recycle()
            }

            Log.i(
                TAG,
                "Uploaded GLB $assetPath to GPU: parts=${gpuParts.size}, dims=(${"%.2f".format(parsed.widthX)} x ${"%.2f".format(parsed.height)} x ${"%.2f".format(parsed.depthZ)})"
            )

            GlbModel(
                assetPath = assetPath,
                parts = gpuParts,
                minX = parsed.minX,
                maxX = parsed.maxX,
                minY = parsed.minY,
                maxY = parsed.maxY,
                minZ = parsed.minZ,
                maxZ = parsed.maxZ
            )
        } catch (t: Throwable) {
            Log.e(TAG, "GPU upload failed for GLB $assetPath: ${t.message}", t)
            null
        }
    }

    private fun readNodeLocalMatrix(nodeObj: JSONObject): FloatArray {
        val matArr = nodeObj.optJSONArray("matrix")
        if (matArr != null && matArr.length() == 16) {
            return FloatArray(16) { idx -> matArr.getDouble(idx).toFloat() }
        }
        val tArr = nodeObj.optJSONArray("translation")
        val rArr = nodeObj.optJSONArray("rotation")
        val sArr = nodeObj.optJSONArray("scale")
        val tx = if (tArr != null && tArr.length() >= 3) tArr.getDouble(0).toFloat() else 0f
        val ty = if (tArr != null && tArr.length() >= 3) tArr.getDouble(1).toFloat() else 0f
        val tz = if (tArr != null && tArr.length() >= 3) tArr.getDouble(2).toFloat() else 0f

        val qx = if (rArr != null && rArr.length() >= 4) rArr.getDouble(0).toFloat() else 0f
        val qy = if (rArr != null && rArr.length() >= 4) rArr.getDouble(1).toFloat() else 0f
        val qz = if (rArr != null && rArr.length() >= 4) rArr.getDouble(2).toFloat() else 0f
        val qw = if (rArr != null && rArr.length() >= 4) rArr.getDouble(3).toFloat() else 1f

        val sx = if (sArr != null && sArr.length() >= 3) sArr.getDouble(0).toFloat() else 1f
        val sy = if (sArr != null && sArr.length() >= 3) sArr.getDouble(1).toFloat() else 1f
        val sz = if (sArr != null && sArr.length() >= 3) sArr.getDouble(2).toFloat() else 1f

        return Mat4.fromTRS(tx, ty, tz, qx, qy, qz, qw, sx, sy, sz)
    }

    private fun resolveTextureImageIndex(texObj: JSONObject): Int {
        if (texObj.has("source")) return texObj.optInt("source", -1)
        val exts = texObj.optJSONObject("extensions") ?: return -1
        val webp = exts.optJSONObject("EXT_texture_webp")
        if (webp != null && webp.has("source")) return webp.optInt("source", -1)
        val keys = exts.keys()
        while (keys.hasNext()) {
            val extObj = exts.optJSONObject(keys.next())
            if (extObj != null && extObj.has("source")) return extObj.optInt("source", -1)
        }
        return -1
    }

    private fun readFloatAccessor(
        bytes: ByteArray,
        binStart: Int,
        binLen: Int,
        bufferViews: JSONArray,
        acc: JSONObject,
        comps: Int
    ): FloatArray {
        val count = acc.getInt("count")
        val out = FloatArray(count * comps)
        val bvIdx = acc.optInt("bufferView", -1)
        if (bvIdx < 0 || bvIdx >= bufferViews.length()) return out

        val bv = bufferViews.getJSONObject(bvIdx)
        val bvOffset = bv.optInt("byteOffset", 0)
        val accOffset = acc.optInt("byteOffset", 0)
        val compType = acc.optInt("componentType", 5126)
        val normalized = acc.optBoolean("normalized", false)
        val compSize = when (compType) {
            5120, 5121 -> 1
            5122, 5123 -> 2
            else -> 4
        }
        val defaultStride = comps * compSize
        val stride = bv.optInt("byteStride", defaultStride).let { if (it <= 0) defaultStride else it }
        val start = binStart + bvOffset + accOffset
        val binEnd = binStart + binLen
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until count) {
            val base = start + i * stride
            if (base < binStart || base + comps * compSize > binEnd) break
            for (c in 0 until comps) {
                val off = base + c * compSize
                out[i * comps + c] = when (compType) {
                    5126 -> bb.getFloat(off)
                    5121 -> {
                        val u = bytes[off].toInt() and 0xFF
                        if (normalized) u / 255f else u.toFloat()
                    }
                    5120 -> {
                        val s = bytes[off].toInt()
                        if (normalized) maxOf(s / 127f, -1f) else s.toFloat()
                    }
                    5123 -> {
                        val u = bb.getShort(off).toInt() and 0xFFFF
                        if (normalized) u / 65535f else u.toFloat()
                    }
                    5122 -> {
                        val s = bb.getShort(off).toInt()
                        if (normalized) maxOf(s / 32767f, -1f) else s.toFloat()
                    }
                    else -> bb.getFloat(off)
                }
            }
        }
        return out
    }

    private fun readTriangulatedIndices(
        bytes: ByteArray,
        binStart: Int,
        binLen: Int,
        bufferViews: JSONArray,
        acc: JSONObject?,
        vertexCount: Int,
        mode: Int,
        flipWinding: Boolean
    ): IntArray {
        val raw = if (acc == null) {
            IntArray(vertexCount) { it }
        } else {
            val bvIdx = acc.optInt("bufferView", -1)
            if (bvIdx < 0 || bvIdx >= bufferViews.length()) return IntArray(0)
            val bv = bufferViews.getJSONObject(bvIdx)
            val start = binStart + bv.optInt("byteOffset", 0) + acc.optInt("byteOffset", 0)
            val count = acc.getInt("count")
            val compType = acc.optInt("componentType", 5123)
            val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val binEnd = binStart + binLen

            IntArray(count) { i ->
                when (compType) {
                    5121 -> {
                        val off = start + i
                        if (off in binStart until binEnd) (bytes[off].toInt() and 0xFF) else 0
                    }
                    5123 -> {
                        val off = start + i * 2
                        if (off >= binStart && off + 2 <= binEnd) (bb.getShort(off).toInt() and 0xFFFF) else 0
                    }
                    else -> {
                        val off = start + i * 4
                        if (off >= binStart && off + 4 <= binEnd) bb.getInt(off) else 0
                    }
                }
            }
        }

        val tris = when (mode) {
            5 -> { // TRIANGLE_STRIP -> TRIANGLES
                if (raw.size < 3) IntArray(0) else {
                    val out = IntArray((raw.size - 2) * 3)
                    var oi = 0
                    for (i in 0 until raw.size - 2) {
                        if (i % 2 == 0) {
                            out[oi++] = raw[i]; out[oi++] = raw[i + 1]; out[oi++] = raw[i + 2]
                        } else {
                            out[oi++] = raw[i + 1]; out[oi++] = raw[i]; out[oi++] = raw[i + 2]
                        }
                    }
                    out
                }
            }
            6 -> { // TRIANGLE_FAN -> TRIANGLES
                if (raw.size < 3) IntArray(0) else {
                    val out = IntArray((raw.size - 2) * 3)
                    var oi = 0
                    for (i in 1 until raw.size - 1) {
                        out[oi++] = raw[0]; out[oi++] = raw[i]; out[oi++] = raw[i + 1]
                    }
                    out
                }
            }
            else -> raw
        }

        if (flipWinding) {
            var i = 0
            while (i + 2 < tris.size) {
                val tmp = tris[i + 1]
                tris[i + 1] = tris[i + 2]
                tris[i + 2] = tmp
                i += 3
            }
        }
        return tris
    }

    private fun packIndexBuffer(triIndices: IntArray, vertexCount: Int): Triple<Buffer, Int, Int> {
        val count = triIndices.size
        return if (vertexCount <= 65535 && triIndices.all { it in 0..65535 }) {
            val sb: ShortBuffer = ByteBuffer.allocateDirect(count * 2)
                .order(ByteOrder.nativeOrder())
                .asShortBuffer()
            for (v in triIndices) sb.put(v.toShort())
            sb.position(0)
            Triple(sb, GLES30.GL_UNSIGNED_SHORT, 2)
        } else {
            val ib: IntBuffer = ByteBuffer.allocateDirect(count * 4)
                .order(ByteOrder.nativeOrder())
                .asIntBuffer()
            for (v in triIndices) ib.put(v)
            ib.position(0)
            Triple(ib, GLES30.GL_UNSIGNED_INT, 4)
        }
    }

    private fun computeFallbackNormals(pos: FloatArray, indices: IntArray, outNorm: FloatArray) {
        var i = 0
        while (i + 2 < indices.size) {
            val a = indices[i] * 3
            val b = indices[i + 1] * 3
            val c = indices[i + 2] * 3
            if (a + 2 < pos.size && b + 2 < pos.size && c + 2 < pos.size) {
                val ux = pos[b] - pos[a]
                val uy = pos[b + 1] - pos[a + 1]
                val uz = pos[b + 2] - pos[a + 2]
                val vx = pos[c] - pos[a]
                val vy = pos[c + 1] - pos[a + 1]
                val vz = pos[c + 2] - pos[a + 2]
                val fx = uy * vz - uz * vy
                val fy = uz * vx - ux * vz
                val fz = ux * vy - uy * vx
                for (vi in intArrayOf(a, b, c)) {
                    outNorm[vi] += fx
                    outNorm[vi + 1] += fy
                    outNorm[vi + 2] += fz
                }
            }
            i += 3
        }
        var v = 0
        while (v + 2 < outNorm.size) {
            val nx = outNorm[v]
            val ny = outNorm[v + 1]
            val nz = outNorm[v + 2]
            val len = sqrt(nx * nx + ny * ny + nz * nz)
            if (len > 1e-5f) {
                outNorm[v] = nx / len
                outNorm[v + 1] = ny / len
                outNorm[v + 2] = nz / len
            } else {
                outNorm[v] = 0f
                outNorm[v + 1] = 1f
                outNorm[v + 2] = 0f
            }
            v += 3
        }
    }

    private fun decodeEmbeddedBitmap(
        bytes: ByteArray,
        binStart: Int,
        binLen: Int,
        bufferViews: JSONArray,
        imgObj: JSONObject,
        assetPath: String,
        role: String
    ): Bitmap? {
        val bvIdx = imgObj.optInt("bufferView", -1)
        if (bvIdx < 0 || bvIdx >= bufferViews.length()) {
            Log.w(TAG, "Image ($role) in $assetPath has no valid bufferView")
            return null
        }
        val bv = bufferViews.getJSONObject(bvIdx)
        val offset = binStart + bv.optInt("byteOffset", 0)
        val len = bv.getInt("byteLength")
        if (offset < binStart || offset + len > binStart + binLen) {
            Log.e(TAG, "Image ($role) byte range out of bounds in $assetPath")
            return null
        }
        val bmp = BitmapFactory.decodeByteArray(bytes, offset, len)
        if (bmp == null) {
            Log.e(TAG, "BitmapFactory failed decoding $role texture ($len bytes) in $assetPath")
        }
        return bmp
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
        return texId
    }

    private fun createSolidTexture(r: Int, g: Int, b: Int, a: Int): Int {
        val bmp = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        bmp.setPixel(0, 0, (a shl 24) or (r shl 16) or (g shl 8) or b)
        val id = uploadBitmapTexture(bmp)
        bmp.recycle()
        return id
    }

    private class IdentityHashMapSafe<K : Any, V> {
        private val entries = mutableListOf<Pair<K, V>>()
        val keys: List<K> get() = entries.map { it.first }
        fun getOrPut(key: K, create: () -> V): V {
            val found = entries.firstOrNull { it.first === key }
            if (found != null) return found.second
            val created = create()
            entries.add(key to created)
            return created
        }
    }

    private object Mat4 {
        fun identity(): FloatArray = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f
        )

        fun mul(a: FloatArray, b: FloatArray): FloatArray {
            val r = FloatArray(16)
            for (c in 0 until 4) {
                for (row in 0 until 4) {
                    r[c * 4 + row] =
                        a[0 * 4 + row] * b[c * 4 + 0] +
                        a[1 * 4 + row] * b[c * 4 + 1] +
                        a[2 * 4 + row] * b[c * 4 + 2] +
                        a[3 * 4 + row] * b[c * 4 + 3]
                }
            }
            return r
        }

        fun fromTRS(
            tx: Float, ty: Float, tz: Float,
            qx: Float, qy: Float, qz: Float, qw: Float,
            sx: Float, sy: Float, sz: Float
        ): FloatArray {
            val x2 = qx + qx; val y2 = qy + qy; val z2 = qz + qz
            val xx = qx * x2; val xy = qx * y2; val xz = qx * z2
            val yy = qy * y2; val yz = qy * z2; val zz = qz * z2
            val wx = qw * x2; val wy = qw * y2; val wz = qw * z2

            return floatArrayOf(
                (1f - (yy + zz)) * sx, (xy + wz) * sx, (xz - wy) * sx, 0f,
                (xy - wz) * sy, (1f - (xx + zz)) * sy, (yz + wx) * sy, 0f,
                (xz + wy) * sz, (yz - wx) * sz, (1f - (xx + yy)) * sz, 0f,
                tx, ty, tz, 1f
            )
        }

        fun det3x3(m: FloatArray): Float {
            val a00 = m[0]; val a01 = m[4]; val a02 = m[8]
            val a10 = m[1]; val a11 = m[5]; val a12 = m[9]
            val a20 = m[2]; val a21 = m[6]; val a22 = m[10]
            return a00 * (a11 * a22 - a12 * a21) -
                a01 * (a10 * a22 - a12 * a20) +
                a02 * (a10 * a21 - a11 * a20)
        }

        fun inverseTranspose3x3(m: FloatArray): FloatArray {
            val a00 = m[0]; val a01 = m[4]; val a02 = m[8]
            val a10 = m[1]; val a11 = m[5]; val a12 = m[9]
            val a20 = m[2]; val a21 = m[6]; val a22 = m[10]

            val c00 = a11 * a22 - a12 * a21
            val c01 = -(a10 * a22 - a12 * a20)
            val c02 = a10 * a21 - a11 * a20
            val c10 = -(a01 * a22 - a02 * a21)
            val c11 = a00 * a22 - a02 * a20
            val c12 = -(a00 * a21 - a01 * a20)
            val c20 = a01 * a12 - a02 * a11
            val c21 = -(a00 * a12 - a02 * a10)
            val c22 = a00 * a11 - a01 * a10

            val det = a00 * c00 + a01 * c01 + a02 * c02
            if (abs(det) < 1e-8f) {
                return floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
            }
            val invDet = 1f / det
            // Column-major 3x3 of (M^-1)^T == cofactor matrix / det laid out as columns
            return floatArrayOf(
                c00 * invDet, c10 * invDet, c20 * invDet,
                c01 * invDet, c11 * invDet, c21 * invDet,
                c02 * invDet, c12 * invDet, c22 * invDet
            )
        }
    }
}
