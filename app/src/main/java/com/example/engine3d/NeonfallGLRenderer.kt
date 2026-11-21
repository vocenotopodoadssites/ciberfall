package com.example.engine3d

import android.content.res.AssetManager
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class NeonfallGLRenderer(
    private val sim: GameSimulation,
    private val onHudTick: (HudSnapshot) -> Unit,
    private val assets: AssetManager? = null
) : GLSurfaceView.Renderer {

    private lateinit var shader: CyberpunkLitShader
    private lateinit var texturedShader: TexturedGlbShader
    private lateinit var lineShader: ParticleLineShader
    private lateinit var rigRenderer: CharacterRigRenderer
    private val glbModels = mutableMapOf<String, GlbModel>()
    private val buildingTexIds = mutableMapOf<String, Int>()
    private var floorTexId: Int = 0

    private val projMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val vpMatrix = FloatArray(16)
    private val modelMat = FloatArray(16)
    private val mvpMat = FloatArray(16)

    private var lastFrameNs = System.nanoTime()
    private var hudPushAcc = 0f

    private val maxRainDrops = 600
    private val rainCoords = FloatArray(maxRainDrops * 6)
    private val rainDrops = Array(maxRainDrops) { floatArrayOf(0f, 0f, 0f) }
    private val rainBuffer: FloatBuffer = ByteBuffer.allocateDirect(rainCoords.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
    private val rng = Random(42)

    private val activeFogColor = floatArrayOf(0.060f, 0.090f, 0.138f)
    private val zeroEmit = floatArrayOf(0f, 0f, 0f)
    private val worldPointScratch = FloatArray(4)
    private val clipPointScratch = FloatArray(4)

    private var loggedGlbFrameSummary = false
    @Volatile
    var glbSimpleMaterialMode: Boolean = false

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        val initFog = sim.world.stageTheme.fogColor
        GLES30.glClearColor(initFog[0], initFog[1], initFog[2], 1f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)

        shader = CyberpunkLitShader()
        texturedShader = TexturedGlbShader()
        lineShader = ParticleLineShader()
        rigRenderer = CharacterRigRenderer(shader)
        assets?.let { assetMgr ->
            val keys = listOf(
                "street_lamp",
                "cyberpunk_building",
                "cyberpunk_building_1",
                "cyberpunk_building_2",
                "industrial_tower",
                "delorean_time_machine",
                "combat_car",
                "drone"
            )
            for (key in keys) {
                val loaded = GlbModelLoader.load(assetMgr, "models/$key.glb")
                if (loaded != null) {
                    glbModels[key] = loaded
                } else {
                    android.util.Log.w("NeonfallGLB", "GLB not available for key=$key (fallback active)")
                }
            }
            android.util.Log.i("NeonfallGLB", "GLB models loaded (${glbModels.size}): ${glbModels.keys.joinToString()}")
            floorTexId = GlbModelLoader.loadTexture(assetMgr, "textures/cyberpunk_street_floor.webp")
            mapOf(
                "tex_building_industrial" to "textures/building_industrial.webp",
                "tex_building_market" to "textures/building_market.webp",
                "tex_building_rowhouse" to "textures/building_rowhouse.webp"
            ).forEach { (key, path) ->
                val id = GlbModelLoader.loadTexture(assetMgr, path)
                if (id != 0) buildingTexIds[key] = id
            }
        }

        for (i in 0 until maxRainDrops) {
            rainDrops[i][0] = (rng.nextFloat() - 0.5f) * 44f
            rainDrops[i][1] = rng.nextFloat() * 18f
            rainDrops[i][2] = (rng.nextFloat() - 0.5f) * 44f
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        val aspect = width.toFloat() / height.coerceAtLeast(1).toFloat()
        Matrix.perspectiveM(projMatrix, 0, 48f, aspect, 0.35f, 145f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = ((now - lastFrameNs) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
        lastFrameNs = now

        sim.step(dt)

        val theme = sim.world.stageTheme
        val mood = sim.bossMoodBlend.coerceIn(0f, 1f)
        // Keep boss arena crisp and clearly lit: subtly shift fog accent without darkening the screen
        activeFogColor[0] = Math3D.lerp(theme.fogColor[0], (theme.fogColor[0] + 0.045f).coerceAtMost(0.18f), mood)
        activeFogColor[1] = Math3D.lerp(theme.fogColor[1], theme.fogColor[1] * 0.92f, mood)
        activeFogColor[2] = Math3D.lerp(theme.fogColor[2], (theme.fogColor[2] + 0.025f).coerceAtMost(0.22f), mood)

        GLES30.glClearColor(activeFogColor[0], activeFogColor[1], activeFogColor[2], 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        Matrix.setLookAtM(
            viewMatrix, 0,
            sim.cameraPos.x, sim.cameraPos.y, sim.cameraPos.z,
            sim.playerPos.x, sim.camTargetY, sim.playerPos.z,
            0f, 1f, 0f
        )
        Matrix.multiplyMM(vpMatrix, 0, projMatrix, 0, viewMatrix, 0)

        hudPushAcc += dt
        if (hudPushAcc >= 0.045f) {
            hudPushAcc = 0f
            onHudTick(sim.snapshotHud(projectDamageNumbers()))
        }

        GLES30.glUseProgram(shader.programId)
        GLES30.glUniform3f(shader.uCameraPos, sim.cameraPos.x, sim.cameraPos.y, sim.cameraPos.z)
        GLES30.glUniform3fv(shader.uFogColor, 1, activeFogColor, 0)
        GLES30.glUniform1f(shader.uFogDensity, sim.dynamicFogDensity)

        // Stage biome lighting with high-contrast boss rim boost (preserves full visibility)
        GLES30.glUniform3f(shader.uLightPos1, sim.playerPos.x - 11f, 8.0f, sim.playerPos.z - 10f)
        GLES30.glUniform3f(
            shader.uLightColor1,
            Math3D.lerp(theme.light1[0], (theme.light1[0] * 1.15f).coerceAtMost(1.1f), mood),
            Math3D.lerp(theme.light1[1], (theme.light1[1] * 0.95f).coerceAtMost(1.0f), mood),
            Math3D.lerp(theme.light1[2], (theme.light1[2] * 1.15f).coerceAtMost(1.1f), mood)
        )
        GLES30.glUniform3f(shader.uLightPos2, sim.playerPos.x + 12f, 7.2f, sim.playerPos.z + 9f)
        GLES30.glUniform3f(
            shader.uLightColor2,
            Math3D.lerp(theme.light2[0], 1.10f, mood),
            Math3D.lerp(theme.light2[1], (theme.light2[1] * 0.85f + 0.12f), mood),
            Math3D.lerp(theme.light2[2], (theme.light2[2] * 0.90f + 0.10f), mood)
        )
        GLES30.glUniform3f(shader.uLightPos3, sim.playerPos.x - 4f, 5.0f, sim.playerPos.z + 7f)
        GLES30.glUniform3f(
            shader.uLightColor3,
            Math3D.lerp(theme.light3[0], (theme.light3[0] * 1.1f).coerceAtMost(1.05f), mood),
            Math3D.lerp(theme.light3[1], (theme.light3[1] * 1.0f).coerceAtMost(1.0f), mood),
            Math3D.lerp(theme.light3[2], (theme.light3[2] + 0.12f).coerceAtMost(1.0f), mood)
        )

        drawWetConcreteMossFloor()
        drawWorldObjects()
        drawGlbWorldModels()
        drawPlayerAndAllies()
        drawEnemies()
        drawGlbEnemies()
        drawProjectilesAndPickups()
        drawShockwavesAndSparks()

        if (sim.rainEnabled) {
            drawRain(dt)
        }
    }

    private fun projectDamageNumbers(): List<ProjectedDamageText> {
        if (sim.damageNumbers.isEmpty()) return emptyList()
        val out = ArrayList<ProjectedDamageText>(minOf(20, sim.damageNumbers.size))
        for (dn in sim.damageNumbers) {
            if (out.size >= 20) break
            worldPointScratch[0] = dn.x
            worldPointScratch[1] = dn.y
            worldPointScratch[2] = dn.z
            worldPointScratch[3] = 1f
            Matrix.multiplyMV(clipPointScratch, 0, vpMatrix, 0, worldPointScratch, 0)
            val w = clipPointScratch[3]
            if (w <= 0.05f) continue
            val ndcX = clipPointScratch[0] / w
            val ndcY = clipPointScratch[1] / w
            val normX = (ndcX + 1f) * 0.5f
            val normY = (1f - ndcY) * 0.5f
            if (normX in -0.08f..1.08f && normY in -0.08f..1.08f) {
                val label = when {
                    dn.isPlayerHit -> "-${dn.amount}"
                    dn.isCrit -> "CRIT -${dn.amount}!"
                    else -> "-${dn.amount}"
                }
                out.add(
                    ProjectedDamageText(
                        id = dn.uid,
                        normX = normX,
                        normY = normY,
                        text = label,
                        isCrit = dn.isCrit,
                        isPlayerHit = dn.isPlayerHit,
                        alpha = (dn.life / dn.maxLife).coerceIn(0f, 1f)
                    )
                )
            }
        }
        return out
    }

    private fun drawMesh(
        tx: Float, ty: Float, tz: Float,
        rx: Float, ry: Float, rz: Float,
        sx: Float, sy: Float, sz: Float,
        baseColor: FloatArray,
        emissive: FloatArray = zeroEmit,
        emissiveStrength: Float = 0f,
        shininess: Float = 24f,
        alpha: Float = 1f,
        materialMode: Int = 0,
        mesh: MeshData = MeshPrimitives.cube
    ) {
        Matrix.setIdentityM(modelMat, 0)
        Matrix.translateM(modelMat, 0, tx, ty, tz)
        if (ry != 0f) Matrix.rotateM(modelMat, 0, ry, 0f, 1f, 0f)
        if (rx != 0f) Matrix.rotateM(modelMat, 0, rx, 1f, 0f, 0f)
        if (rz != 0f) Matrix.rotateM(modelMat, 0, rz, 0f, 0f, 1f)
        Matrix.scaleM(modelMat, 0, sx, sy, sz)
        Matrix.multiplyMM(mvpMat, 0, vpMatrix, 0, modelMat, 0)

        GLES30.glUniformMatrix4fv(shader.uMVP, 1, false, mvpMat, 0)
        GLES30.glUniformMatrix4fv(shader.uModel, 1, false, modelMat, 0)
        GLES30.glUniform3fv(shader.uBaseColor, 1, baseColor, 0)
        GLES30.glUniform3fv(shader.uEmissiveColor, 1, emissive, 0)
        GLES30.glUniform1f(shader.uEmissiveStrength, emissiveStrength)
        GLES30.glUniform1f(shader.uShininess, shininess)
        GLES30.glUniform1f(shader.uAlpha, alpha)
        GLES30.glUniform1i(shader.uMaterialMode, materialMode)
        mesh.draw(shader.aPos, shader.aNorm)
    }

    private fun drawWetConcreteMossFloor() {
        // Mode 1: world-space tiled cyberpunk street texture (infinite around player); falls back if unavailable
        if (floorTexId != 0) {
            GLES30.glActiveTexture(GLES30.GL_TEXTURE2)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, floorTexId)
            GLES30.glUniform1i(shader.uFloorMap, 2)
            GLES30.glUniform1i(shader.uHasFloorMap, 1)
        } else {
            GLES30.glUniform1i(shader.uHasFloorMap, 0)
        }

        drawMesh(
            sim.playerPos.x, 0f, sim.playerPos.z, 0f, 0f, 0f,
            135f, 1f, 135f,
            baseColor = sim.world.stageTheme.floorBase,
            shininess = if (sim.graphicsQuality.puddleReflect) 96f else 20f,
            materialMode = 1,
            mesh = MeshPrimitives.groundQuad
        )
        GLES30.glUniform1i(shader.uHasFloorMap, 0)

        if (sim.graphicsQuality.puddleReflect && floorTexId == 0) {
            for (p in sim.world.puddles) {
                drawMesh(
                    p.x, 0.015f, p.z, 0f, 0f, 0f,
                    p.radiusX, 1f, p.radiusZ,
                    baseColor = p.tint,
                    emissive = p.tint,
                    emissiveStrength = 0.16f,
                    shininess = 128f,
                    alpha = 0.24f,
                    materialMode = 0,
                    mesh = MeshPrimitives.disc
                )
            }
        }
    }

    private fun resolveGlbKey(obj: WorldBoxObject): String? {
        val explicit = obj.modelKey
        if (explicit != null && explicit in glbModels) return explicit
        return when (obj.kind) {
            WorldPropKind.STREETLAMP -> if ("street_lamp" in glbModels) "street_lamp" else null
            WorldPropKind.HOUSE_WORKSHOP -> if ("cyberpunk_building_1" in glbModels) "cyberpunk_building_1" else null
            WorldPropKind.SKYSCRAPER -> {
                val candidates = listOf("cyberpunk_building", "cyberpunk_building_2", "industrial_tower")
                    .filter { it in glbModels }
                if (candidates.isNotEmpty()) {
                    candidates[kotlin.math.abs(obj.id.hashCode()) % candidates.size]
                } else null
            }
            WorldPropKind.WRECKED_CAR -> {
                val candidates = listOf("delorean_time_machine", "combat_car").filter { it in glbModels }
                if (candidates.isNotEmpty()) {
                    candidates[kotlin.math.abs(obj.id.hashCode()) % candidates.size]
                } else null
            }
            else -> null
        }
    }

    private fun bindBuildingTexture(obj: WorldBoxObject): Boolean {
        if (buildingTexIds.isEmpty()) return false
        val texId = obj.modelKey?.let { buildingTexIds[it] }
            ?: buildingTexIds.values.elementAt(kotlin.math.abs(obj.id.hashCode()) % buildingTexIds.size)
        if (texId == 0) return false

        GLES30.glActiveTexture(GLES30.GL_TEXTURE3)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)
        GLES30.glUniform1i(shader.uBuildingMap, 3)
        GLES30.glUniform1i(shader.uHasBuildingMap, 1)

        val tilesX = kotlin.math.round((obj.sx / 6.8f).coerceAtLeast(1f))
        val tilesY = if (obj.kind == WorldPropKind.SKYSCRAPER) {
            kotlin.math.round((obj.sy / 7.2f).coerceAtLeast(2f))
        } else {
            1f
        }
        GLES30.glUniform2f(shader.uWallTiles, tilesX, tilesY)
        return true
    }

    private fun drawWorldObjects() {
        for (obj in sim.world.objects) {
            if (resolveGlbKey(obj) != null) continue
            when (obj.kind) {
                WorldPropKind.SKYSCRAPER -> {
                    val hasTex = bindBuildingTexture(obj)
                    drawMesh(
                        obj.x, obj.y, obj.z, 0f, obj.yawDeg, 0f,
                        obj.sx, obj.sy, obj.sz, obj.baseColor,
                        shininess = if (hasTex) 28f else 18f,
                        materialMode = if (hasTex) 5 else 2
                    )
                    if (hasTex) GLES30.glUniform1i(shader.uHasBuildingMap, 0)
                    // 3D rooftop parapet & neon crown bar
                    drawMesh(obj.x, obj.y + obj.sy * 0.5f + 0.16f, obj.z, 0f, obj.yawDeg, 0f, obj.sx + 0.24f, 0.32f, obj.sz + 0.24f, obj.baseColor, materialMode = 3)
                    drawMesh(obj.x, obj.y + obj.sy * 0.5f + 0.42f, obj.z, 0f, obj.yawDeg, 0f, 2.2f, 0.85f, 1.8f, floatArrayOf(0.20f, 0.23f, 0.28f), materialMode = 3)
                    drawMesh(obj.x, obj.y * 1.82f, obj.z, 0f, obj.yawDeg, 0f, obj.sx + 0.14f, 0.28f, obj.sz + 0.14f, obj.neonColor, obj.neonColor, 1.05f)
                }
                WorldPropKind.HOUSE_WORKSHOP -> {
                    val hasTex = bindBuildingTexture(obj)
                    drawMesh(
                        obj.x, obj.y, obj.z, 0f, obj.yawDeg, 0f,
                        obj.sx, obj.sy, obj.sz, obj.baseColor,
                        shininess = if (hasTex) 26f else 16f,
                        materialMode = if (hasTex) 5 else 2
                    )
                    if (hasTex) GLES30.glUniform1i(shader.uHasBuildingMap, 0)
                    // 3D rooftop rim, HVAC unit, cylindrical water tank & neon corner beacon
                    drawMesh(obj.x, obj.y + obj.sy * 0.5f + 0.12f, obj.z, 0f, obj.yawDeg, 0f, obj.sx + 0.28f, 0.24f, obj.sz + 0.28f, floatArrayOf(0.16f, 0.20f, 0.25f), shininess = 28f, materialMode = 3)
                    drawMesh(obj.x - obj.sx * 0.22f, obj.y + obj.sy * 0.56f, obj.z, 0f, obj.yawDeg, 0f, 1.3f, 0.85f, 1.1f, floatArrayOf(0.24f, 0.27f, 0.31f), materialMode = 3)
                    drawMesh(obj.x + obj.sx * 0.20f, obj.y + obj.sy * 0.55f, obj.z + 0.4f, 0f, 0f, 0f, 0.65f, 0.75f, 0.65f, floatArrayOf(0.20f, 0.23f, 0.28f), materialMode = 3, mesh = MeshPrimitives.cylinder)
                    drawMesh(obj.x + obj.sx * 0.48f, obj.y + 0.5f, obj.z + obj.sz * 0.48f, 0f, 0f, 0f, 0.24f, 0.65f, 0.24f, obj.neonColor, obj.neonColor, 1.4f)
                }
                WorldPropKind.STAIR_PLATFORM -> {
                    drawMesh(obj.x, obj.y, obj.z, 0f, obj.yawDeg, 0f, obj.sx, obj.sy, obj.sz, obj.baseColor, shininess = 18f, materialMode = 4)
                    drawMesh(obj.x, obj.y + 0.92f, obj.z, 0f, obj.yawDeg, 0f, 0.10f, 0.08f, obj.sz * 0.95f, obj.neonColor, shininess = 36f)
                    drawMesh(obj.x, obj.y + 0.52f, obj.z - obj.sz * 0.35f, 0f, 0f, 0f, 0.08f, 0.85f, 0.08f, obj.neonColor)
                    drawMesh(obj.x, obj.y + 0.52f, obj.z + obj.sz * 0.35f, 0f, 0f, 0f, 0.08f, 0.85f, 0.08f, obj.neonColor)
                }
                WorldPropKind.HANDRAIL_BARRIER -> {
                    drawMesh(obj.x, 0.14f, obj.z, 0f, obj.yawDeg, 0f, obj.sx, 0.28f, 0.32f, floatArrayOf(0.22f, 0.25f, 0.29f), materialMode = 2)
                    drawMesh(obj.x, 0.96f, obj.z, 0f, obj.yawDeg, 0f, obj.sx, 0.09f, 0.09f, obj.baseColor, shininess = 38f)
                    drawMesh(obj.x, 0.62f, obj.z, 0f, obj.yawDeg, 0f, obj.sx, 0.07f, 0.07f, obj.baseColor, shininess = 38f)
                    for (px in listOf(-obj.sx * 0.42f, 0f, obj.sx * 0.42f)) {
                        val rad = Math3D.rad(obj.yawDeg)
                        val wx = obj.x + cos(rad) * px
                        val wz = obj.z - sin(rad) * px
                        drawMesh(wx, 0.58f, wz, 0f, 0f, 0f, 0.09f, 0.85f, 0.09f, obj.baseColor)
                    }
                }
                WorldPropKind.CONTAINER -> {
                    drawMesh(obj.x, obj.y, obj.z, 0f, obj.yawDeg, 0f, obj.sx, obj.sy, obj.sz, obj.baseColor, shininess = 24f, materialMode = 3)
                    drawMesh(obj.x, obj.y + obj.sy * 0.44f, obj.z, 0f, obj.yawDeg, 0f, obj.sx + 0.06f, 0.12f, obj.sz + 0.06f, obj.neonColor, obj.neonColor, 0.55f)
                }
                WorldPropKind.WRECKED_CAR -> {
                    drawMesh(obj.x, obj.y, obj.z, 0f, obj.yawDeg, 4f, obj.sx, obj.sy * 0.62f, obj.sz, obj.baseColor, shininess = 46f, materialMode = 3)
                    drawMesh(obj.x, obj.y + 0.44f, obj.z, 0f, obj.yawDeg, 4f, obj.sx * 0.56f, obj.sy * 0.48f, obj.sz * 0.84f, obj.baseColor, obj.neonColor, 0.18f, materialMode = 3)
                }
                WorldPropKind.STREETLAMP -> {
                    drawMesh(obj.x, obj.y, obj.z, 0f, 0f, 0f, obj.sx, obj.sy, obj.sz, obj.baseColor, mesh = MeshPrimitives.cylinder)
                    drawMesh(obj.x, obj.y * 2f, obj.z, 0f, 0f, 0f, 0.85f, 0.22f, 0.42f, obj.neonColor, obj.neonColor, 1.35f)
                }
                WorldPropKind.HOLO_BILLBOARD -> {
                    drawMesh(obj.x, obj.y, obj.z, 0f, obj.yawDeg, 0f, obj.sx, obj.sy, obj.sz, obj.baseColor, obj.neonColor, 1.35f, alpha = 0.82f)
                }
                else -> {
                    drawMesh(obj.x, obj.y, obj.z, 0f, obj.yawDeg, 0f, obj.sx, obj.sy, obj.sz, obj.baseColor, obj.neonColor, 0.20f, materialMode = 3)
                }
            }
        }
    }

    private fun applyTexturedShaderEnvironment() {
        val theme = sim.world.stageTheme
        val mood = sim.bossMoodBlend.coerceIn(0f, 1f)

        GLES30.glUseProgram(texturedShader.programId)
        GLES30.glUniform3f(texturedShader.uCameraPos, sim.cameraPos.x, sim.cameraPos.y, sim.cameraPos.z)
        GLES30.glUniform3fv(texturedShader.uFogColor, 1, activeFogColor, 0)
        GLES30.glUniform1f(texturedShader.uFogDensity, sim.dynamicFogDensity)

        GLES30.glUniform3f(texturedShader.uLightPos1, sim.playerPos.x - 11f, 8.0f, sim.playerPos.z - 10f)
        GLES30.glUniform3f(
            texturedShader.uLightColor1,
            Math3D.lerp(theme.light1[0], (theme.light1[0] * 1.15f).coerceAtMost(1.1f), mood),
            Math3D.lerp(theme.light1[1], (theme.light1[1] * 0.95f).coerceAtMost(1.0f), mood),
            Math3D.lerp(theme.light1[2], (theme.light1[2] * 1.15f).coerceAtMost(1.1f), mood)
        )
        GLES30.glUniform3f(texturedShader.uLightPos2, sim.playerPos.x + 12f, 7.2f, sim.playerPos.z + 9f)
        GLES30.glUniform3f(
            texturedShader.uLightColor2,
            Math3D.lerp(theme.light2[0], 1.10f, mood),
            Math3D.lerp(theme.light2[1], (theme.light2[1] * 0.85f + 0.12f), mood),
            Math3D.lerp(theme.light2[2], (theme.light2[2] * 0.90f + 0.10f), mood)
        )
        GLES30.glUniform3f(texturedShader.uLightPos3, sim.playerPos.x - 4f, 5.0f, sim.playerPos.z + 7f)
        GLES30.glUniform3f(
            texturedShader.uLightColor3,
            Math3D.lerp(theme.light3[0], (theme.light3[0] * 1.1f).coerceAtMost(1.05f), mood),
            Math3D.lerp(theme.light3[1], (theme.light3[1] * 1.0f).coerceAtMost(1.0f), mood),
            Math3D.lerp(theme.light3[2], (theme.light3[2] + 0.12f).coerceAtMost(1.0f), mood)
        )
    }

    private fun drawGlbWorldModels() {
        if (glbModels.isEmpty()) return
        applyTexturedShaderEnvironment()
        GLES30.glUniform1i(texturedShader.uSimpleMaterial, if (glbSimpleMaterialMode) 1 else 0)

        val drawCounts = if (!loggedGlbFrameSummary) mutableMapOf<String, Int>() else null
        for (obj in sim.world.objects) {
            val key = resolveGlbKey(obj) ?: continue
            val model = glbModels[key] ?: continue

            val uniformScale = computeUniformGlbScale(
                kind = obj.kind,
                objSx = obj.sx,
                objSy = obj.sy,
                objSz = obj.sz,
                widthX = model.widthX,
                height = model.height,
                depthZ = model.depthZ
            )

            Matrix.setIdentityM(modelMat, 0)
            val baseY = obj.y - obj.sy * 0.5f
            Matrix.translateM(modelMat, 0, obj.x, baseY, obj.z)
            if (obj.yawDeg != 0f) Matrix.rotateM(modelMat, 0, obj.yawDeg, 0f, 1f, 0f)
            Matrix.scaleM(modelMat, 0, uniformScale, uniformScale, uniformScale)
            Matrix.translateM(modelMat, 0, -model.centerX, model.baseOffsetY, -model.centerZ)
            Matrix.multiplyMM(mvpMat, 0, vpMatrix, 0, modelMat, 0)

            GLES30.glUniformMatrix4fv(texturedShader.uMVP, 1, false, mvpMat, 0)
            GLES30.glUniformMatrix4fv(texturedShader.uModel, 1, false, modelMat, 0)
            GLES30.glUniform1f(texturedShader.uEmissiveBoost, if (obj.kind == WorldPropKind.STREETLAMP) 1.55f else 1.20f)
            GLES30.glUniform3f(
                texturedShader.uEmissiveTint,
                Math3D.lerp(1.0f, obj.neonColor[0], 0.20f),
                Math3D.lerp(1.0f, obj.neonColor[1], 0.20f),
                Math3D.lerp(1.0f, obj.neonColor[2], 0.20f)
            )

            for (part in model.parts) {
                GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, part.albedoTexId)
                GLES30.glUniform1i(texturedShader.uAlbedoMap, 0)

                GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, part.emissiveTexId)
                GLES30.glUniform1i(texturedShader.uEmissiveMap, 1)

                GLES30.glUniform4fv(texturedShader.uBaseColorFactor, 1, part.baseColorFactor, 0)
                GLES30.glUniform3fv(texturedShader.uEmissiveFactor, 1, part.emissiveFactor, 0)
                GLES30.glUniform1f(texturedShader.uAlphaCutoff, part.alphaCutoff)
                GLES30.glUniform1i(texturedShader.uUnlit, if (part.unlit) 1 else 0)

                part.draw(texturedShader.aPos, texturedShader.aNorm, texturedShader.aTexCoord)
            }
            drawCounts?.set(key, (drawCounts[key] ?: 0) + 1)
        }

        if (drawCounts != null) {
            loggedGlbFrameSummary = true
            val glErr = GLES30.glGetError()
            android.util.Log.i("NeonfallGLB", "First-frame GLB draws=$drawCounts, glError=$glErr")
        }

        // Restore default texture unit & main lit shader for character/enemy/VFX rendering
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glUseProgram(shader.programId)
    }

    private fun drawPlayerAndAllies() {
        GLES30.glUniform1i(shader.uMaterialMode, 0)
        rigRenderer.drawPlayerRig(
            vpMatrix = vpMatrix,
            x = sim.playerPos.x,
            y = sim.playerPos.y,
            z = sim.playerPos.z,
            yawDeg = sim.playerYawDeg,
            weaponType = sim.classDef.weaponType,
            primaryCol = sim.primaryCol,
            secondaryCol = sim.secondaryCol,
            visorCol = sim.visorCol,
            weaponCol = sim.weaponCol,
            animState = sim.playerAnimState,
            animTime = sim.playerAnimTime,
            hitFlash = sim.playerHitFlash,
            shieldActive = sim.shield > 0f,
            skinStyle = sim.skinStyle,
            jumpProgress = sim.jumpAnimTimer,
            jumpStyle = sim.jumpStyle
        )

        sim.equippedPet?.let { pet ->
            rigRenderer.drawPetRig(
                vpMatrix = vpMatrix,
                archetype = pet.archetype,
                x = sim.petPos.x,
                y = sim.petPos.y,
                z = sim.petPos.z,
                yawDeg = sim.petYawDeg,
                primaryCol = sim.petPrimaryCol,
                glowCol = sim.petGlowCol,
                animTime = sim.elapsedSeconds
            )
        }

        for (i in 0 until sim.droneCount) {
            val ang = sim.elapsedSeconds * 2.2f + (i * 2f * Math.PI.toFloat() / sim.droneCount)
            val dx = sim.playerPos.x + cos(ang) * 1.9f
            val dz = sim.playerPos.z + sin(ang) * 1.9f
            val dy = sim.playerPos.y + 2.0f + sin(sim.elapsedSeconds * 5f + i) * 0.16f
            drawMesh(dx, dy, dz, 0f, sim.elapsedSeconds * 110f, 0f, 0.42f, 0.28f, 0.42f, sim.primaryCol, sim.visorCol, 1.3f, mesh = MeshPrimitives.octahedron)
        }

        for (i in 0 until sim.orbitalBladeCount) {
            val ang = sim.elapsedSeconds * 4.2f + (i * 2f * Math.PI.toFloat() / sim.orbitalBladeCount)
            val bx = sim.playerPos.x + cos(ang) * 2.4f
            val bz = sim.playerPos.z + sin(ang) * 2.4f
            drawMesh(bx, sim.playerPos.y + 1.0f, bz, 0f, Math3D.deg(ang) + 90f, 0f, 0.14f, 0.14f, 0.98f, sim.weaponCol, sim.weaponCol, 1.55f, mesh = MeshPrimitives.blade)
        }

        for (t in sim.turrets) {
            val rad = Math3D.rad(t.yawDeg)
            val bx = t.pos.x + sin(rad) * 0.36f
            val bz = t.pos.z + cos(rad) * 0.36f
            drawMesh(t.pos.x, 0.45f, t.pos.z, 0f, 0f, 0f, 0.55f, 0.9f, 0.55f, floatArrayOf(0.22f, 0.28f, 0.35f), mesh = MeshPrimitives.cylinder)
            drawMesh(t.pos.x, 1.05f, t.pos.z, 0f, t.yawDeg, 0f, 0.50f, 0.32f, 0.62f, sim.visorCol, sim.visorCol, 1.05f, mesh = MeshPrimitives.beveledBox)
            drawMesh(bx, 1.05f, bz, 90f, t.yawDeg, 0f, 0.14f, 0.46f, 0.14f, sim.weaponCol, sim.weaponCol, 1.35f, mesh = MeshPrimitives.cylinder)
        }

        for (m in sim.mines) {
            drawMesh(m.pos.x, 0.14f, m.pos.z, 0f, sim.elapsedSeconds * 90f, 0f, 0.56f, 0.4f, 0.56f, floatArrayOf(0.95f, 0.42f, 0.15f), floatArrayOf(0.95f, 0.42f, 0.15f), 1.2f, mesh = MeshPrimitives.torus)
            drawMesh(m.pos.x, 0.18f, m.pos.z, 0f, 0f, 0f, 0.28f, 0.20f, 0.28f, floatArrayOf(1.0f, 0.68f, 0.22f), floatArrayOf(1.0f, 0.68f, 0.22f), 1.6f, mesh = MeshPrimitives.sphere)
        }
    }

    private fun drawEnemies() {
        GLES30.glUniform1i(shader.uMaterialMode, 0)
        val hasDroneGlb = "drone" in glbModels
        for (e in sim.enemies) {
            if (hasDroneGlb && e.def.id == "drone_voador" && !e.isBoss) {
                // Ground shadow indicator under flying GLB drone
                val gCol = com.example.data.parseHexColor(e.def.glowHex)
                drawMesh(
                    e.pos.x, 0.04f, e.pos.z,
                    0f, e.animTime * 60f, 0f,
                    0.75f * e.def.scale, 1f, 0.75f * e.def.scale,
                    gCol, gCol, 0.9f, alpha = 0.45f, mesh = MeshPrimitives.ring
                )
                continue
            }
            val pCol = com.example.data.parseHexColor(e.def.primaryHex)
            val gCol = com.example.data.parseHexColor(e.def.glowHex)
            val wCol = com.example.data.parseHexColor(e.def.weakpointHex)
            rigRenderer.drawEnemyRig(
                vpMatrix = vpMatrix,
                enemyId = e.def.id,
                isBoss = e.isBoss,
                x = e.pos.x,
                y = e.pos.y,
                z = e.pos.z,
                yawDeg = e.yawDeg,
                scale = e.def.scale,
                primaryCol = pCol,
                glowCol = gCol,
                weakpointCol = wCol,
                animTime = e.animTime,
                hitFlash = e.hitFlash,
                phase2 = e.phase2
            )
        }
    }

    private fun drawGlbEnemies() {
        val droneModel = glbModels["drone"] ?: return
        var anyDrawn = false
        val centerY = (droneModel.minY + droneModel.maxY) * 0.5f
        val maxSpan = maxOf(droneModel.widthX, droneModel.depthZ)

        for (e in sim.enemies) {
            if (e.isBoss || e.def.id != "drone_voador") continue
            if (!anyDrawn) {
                applyTexturedShaderEnvironment()
                GLES30.glUniform1i(texturedShader.uSimpleMaterial, if (glbSimpleMaterialMode) 1 else 0)
                anyDrawn = true
            }

            val hoverY = e.pos.y + (1.42f + sin(e.animTime * 5.2f) * 0.20f) * e.def.scale
            val uniformScale = (1.52f * e.def.scale) / maxSpan
            val bankRoll = sin(e.animTime * 3.4f) * 9f

            Matrix.setIdentityM(modelMat, 0)
            Matrix.translateM(modelMat, 0, e.pos.x, hoverY, e.pos.z)
            Matrix.rotateM(modelMat, 0, e.yawDeg, 0f, 1f, 0f)
            Matrix.rotateM(modelMat, 0, 10f, 1f, 0f, 0f)
            Matrix.rotateM(modelMat, 0, bankRoll, 0f, 0f, 1f)
            Matrix.scaleM(modelMat, 0, uniformScale, uniformScale, uniformScale)
            Matrix.translateM(modelMat, 0, -droneModel.centerX, -centerY, -droneModel.centerZ)
            Matrix.multiplyMM(mvpMat, 0, vpMatrix, 0, modelMat, 0)

            GLES30.glUniformMatrix4fv(texturedShader.uMVP, 1, false, mvpMat, 0)
            GLES30.glUniformMatrix4fv(texturedShader.uModel, 1, false, modelMat, 0)

            if (e.hitFlash > 0f) {
                GLES30.glUniform1f(texturedShader.uEmissiveBoost, 2.8f)
                GLES30.glUniform3f(texturedShader.uEmissiveTint, 1.0f, 0.35f, 0.35f)
            } else {
                GLES30.glUniform1f(texturedShader.uEmissiveBoost, 1.65f)
                GLES30.glUniform3f(texturedShader.uEmissiveTint, 1.0f, 1.0f, 1.0f)
            }

            for (part in droneModel.parts) {
                GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, part.albedoTexId)
                GLES30.glUniform1i(texturedShader.uAlbedoMap, 0)

                GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, part.emissiveTexId)
                GLES30.glUniform1i(texturedShader.uEmissiveMap, 1)

                GLES30.glUniform4fv(texturedShader.uBaseColorFactor, 1, part.baseColorFactor, 0)
                GLES30.glUniform3fv(texturedShader.uEmissiveFactor, 1, part.emissiveFactor, 0)
                GLES30.glUniform1f(texturedShader.uAlphaCutoff, part.alphaCutoff)
                GLES30.glUniform1i(texturedShader.uUnlit, if (part.unlit) 1 else 0)

                part.draw(texturedShader.aPos, texturedShader.aNorm, texturedShader.aTexCoord)
            }
        }

        if (anyDrawn) {
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glUseProgram(shader.programId)
        }
    }

    private fun drawProjectilesAndPickups() {
        for (p in sim.projectiles) {
            val projYaw = Math3D.deg(atan2(p.vx, p.vz))
            drawMesh(p.pos.x, p.pos.y, p.pos.z, 0f, projYaw, 0f, p.scale * 0.95f, p.scale * 0.95f, p.scale * 2.2f, p.color, p.color, 1.85f, mesh = MeshPrimitives.blade)
        }
        val xpColor = floatArrayOf(0.22f, 0.75f, 0.97f)
        val crColor = floatArrayOf(0.92f, 0.66f, 0.22f)
        for (orb in sim.pickups) {
            val col = if (orb.isCredit) crColor else xpColor
            drawMesh(orb.pos.x, 0.45f + sin(sim.elapsedSeconds * 4f + orb.pos.x) * 0.1f, orb.pos.z, 0f, sim.elapsedSeconds * 130f, 0f, 0.34f, 0.34f, 0.34f, col, col, 1.5f, mesh = MeshPrimitives.octahedron)
        }
        for (col in sim.arenaCollectibles) {
            val bob = sin(sim.elapsedSeconds * 3.5f + col.pos.x) * 0.16f
            drawMesh(col.pos.x, col.pos.y - 0.38f, col.pos.z, 0f, sim.elapsedSeconds * 65f, 0f, 0.95f, 1f, 0.95f, col.color, col.color, 1.2f, alpha = 0.75f, mesh = MeshPrimitives.ring)
            drawMesh(col.pos.x, col.pos.y + 0.32f + bob, col.pos.z, 25f, sim.elapsedSeconds * 140f, 0f, 0.46f, 0.56f, 0.46f, col.color, col.color, 1.8f, mesh = MeshPrimitives.octahedron)
            drawMesh(col.pos.x, col.pos.y + 0.32f + bob, col.pos.z, 65f, -sim.elapsedSeconds * 110f, 0f, 0.78f, 0.60f, 0.78f, col.color, col.color, 1.4f, alpha = 0.85f, mesh = MeshPrimitives.torus)
        }
    }

    private fun drawShockwavesAndSparks() {
        for (s in sim.shockwaves) {
            val t = 1f - (s.life / s.maxLife).coerceIn(0f, 1f)
            val curRadius = Math3D.lerp(s.radius, s.maxRadius, t)
            drawMesh(s.x, s.y, s.z, 0f, 0f, 0f, curRadius, 1f, curRadius, s.color, s.color, 1.5f, alpha = (s.life / s.maxLife).coerceIn(0f, 1f), mesh = MeshPrimitives.ring)
        }
        for (sl in sim.slashArcs) {
            val prog = 1f - (sl.life / sl.maxLife).coerceIn(0f, 1f)
            val sweepYaw = sl.yawDeg + sl.swingDir * (-70f + prog * 145f)
            val rad = Math3D.rad(sweepYaw)
            val bx = sl.x + sin(rad) * sl.radius * 0.58f
            val bz = sl.z + cos(rad) * sl.radius * 0.58f
            val alpha = (sl.life / sl.maxLife).coerceIn(0f, 1f) * 0.92f
            drawMesh(bx, sl.y, bz, 0f, sweepYaw, sl.swingDir * 14f, 0.24f, 0.10f, sl.radius * 1.15f, sl.color, sl.color, 2.0f, alpha = alpha, mesh = MeshPrimitives.blade)
            drawMesh(sl.x, sl.y - 0.1f, sl.z, 0f, sweepYaw, 0f, sl.radius * 0.88f, 1f, sl.radius * 0.88f, sl.color, sl.color, 1.6f, alpha = alpha * 0.65f, mesh = MeshPrimitives.ring)
        }
        for (mf in sim.muzzleFlashes) {
            val alpha = (mf.life / mf.maxLife).coerceIn(0f, 1f)
            drawMesh(mf.x, mf.y, mf.z, 0f, mf.yawDeg + 45f, 0f, mf.size, mf.size * 0.72f, mf.size * 1.35f, mf.color, mf.color, 2.1f, alpha = alpha, mesh = MeshPrimitives.octahedron)
            drawMesh(mf.x, mf.y, mf.z, 90f, mf.yawDeg, 0f, mf.size * 0.85f, mf.size * 0.85f, mf.size * 0.85f, mf.color, mf.color, 1.7f, alpha = alpha * 0.75f, mesh = MeshPrimitives.torus)
        }
        for (sp in sim.sparks) {
            drawMesh(sp.x, sp.y, sp.z, 0f, 0f, 0f, 0.14f, 0.14f, 0.14f, sp.color, sp.color, 1.6f)
        }
    }

    private fun drawRain(dt: Float) {
        val dropCount = (sim.graphicsQuality.rainCount * sim.dynamicRainScale).toInt().coerceIn(0, maxRainDrops)
        if (dropCount <= 0) return
        var idx = 0
        for (i in 0 until dropCount) {
            val d = rainDrops[i]
            d[1] -= 26f * dt
            if (d[1] < 0f || kotlin.math.abs(d[0] - sim.playerPos.x) > 26f || kotlin.math.abs(d[2] - sim.playerPos.z) > 26f) {
                d[0] = sim.playerPos.x + (rng.nextFloat() - 0.5f) * 44f
                if (d[1] < 0f) d[1] = 18f + rng.nextFloat() * 5f
                d[2] = sim.playerPos.z + (rng.nextFloat() - 0.5f) * 44f
            }
            rainCoords[idx++] = d[0]
            rainCoords[idx++] = d[1]
            rainCoords[idx++] = d[2]
            rainCoords[idx++] = d[0] - 0.18f
            rainCoords[idx++] = d[1] - 0.95f
            rainCoords[idx++] = d[2]
        }
        rainBuffer.clear()
        rainBuffer.put(rainCoords, 0, dropCount * 6)
        rainBuffer.position(0)

        GLES30.glUseProgram(lineShader.programId)
        GLES30.glUniformMatrix4fv(lineShader.uMVP, 1, false, vpMatrix, 0)
        GLES30.glUniform4f(lineShader.uColor, 0.55f, 0.72f, 0.88f, 0.36f)
        GLES30.glEnableVertexAttribArray(lineShader.aPos)
        GLES30.glVertexAttribPointer(lineShader.aPos, 3, GLES30.GL_FLOAT, false, 12, rainBuffer)
        GLES30.glDrawArrays(GLES30.GL_LINES, 0, dropCount * 2)
    }

    companion object {
        /**
         * Computes strictly uniform scaling so GLB proportions (width:height:depth) are never stretched
         * or deformed while keeping bases anchored cleanly on the ground.
         */
        fun computeUniformGlbScale(
            kind: WorldPropKind,
            objSx: Float,
            objSy: Float,
            objSz: Float,
            widthX: Float,
            height: Float,
            depthZ: Float
        ): Float {
            val safeW = widthX.coerceAtLeast(0.01f)
            val safeH = height.coerceAtLeast(0.01f)
            val safeD = depthZ.coerceAtLeast(0.01f)
            return when (kind) {
                WorldPropKind.STREETLAMP -> objSy / safeH
                WorldPropKind.WRECKED_CAR -> minOf(
                    maxOf(objSx, objSz) / maxOf(safeW, safeD),
                    minOf(objSx, objSz) / minOf(safeW, safeD)
                )
                else -> minOf(objSx / safeW, objSz / safeD)
            }
        }
    }
}
