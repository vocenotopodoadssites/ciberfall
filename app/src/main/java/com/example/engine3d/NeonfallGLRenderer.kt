package com.example.engine3d

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class NeonfallGLRenderer(
    private val sim: GameSimulation,
    private val onHudTick: (HudSnapshot) -> Unit
) : GLSurfaceView.Renderer {

    private lateinit var shader: CyberpunkLitShader
    private lateinit var lineShader: ParticleLineShader
    private lateinit var rigRenderer: CharacterRigRenderer

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

    // Deep slate-blue atmospheric alley fog matching the reference image
    private val fogColor = floatArrayOf(0.055f, 0.082f, 0.125f)
    private val zeroEmit = floatArrayOf(0f, 0f, 0f)

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(fogColor[0], fogColor[1], fogColor[2], 1f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)

        shader = CyberpunkLitShader()
        lineShader = ParticleLineShader()
        rigRenderer = CharacterRigRenderer(shader)

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
        hudPushAcc += dt
        if (hudPushAcc >= 0.05f) {
            hudPushAcc = 0f
            onHudTick(sim.snapshotHud())
        }

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        Matrix.setLookAtM(
            viewMatrix, 0,
            sim.cameraPos.x, sim.cameraPos.y, sim.cameraPos.z,
            sim.playerPos.x, sim.camTargetY, sim.playerPos.z,
            0f, 1f, 0f
        )
        Matrix.multiplyMM(vpMatrix, 0, projMatrix, 0, viewMatrix, 0)

        GLES30.glUseProgram(shader.programId)
        GLES30.glUniform3f(shader.uCameraPos, sim.cameraPos.x, sim.cameraPos.y, sim.cameraPos.z)
        GLES30.glUniform3fv(shader.uFogColor, 1, fogColor, 0)
        GLES30.glUniform1f(shader.uFogDensity, sim.dynamicFogDensity)

        // Alley lighting palette matching reference: cool slate-blue, magenta sign glow, warm amber garage lantern
        GLES30.glUniform3f(shader.uLightPos1, sim.playerPos.x - 11f, 7.5f, sim.playerPos.z - 10f)
        GLES30.glUniform3f(shader.uLightColor1, 0.25f, 0.65f, 0.92f)
        GLES30.glUniform3f(shader.uLightPos2, sim.playerPos.x + 12f, 6.5f, sim.playerPos.z + 9f)
        GLES30.glUniform3f(shader.uLightColor2, 0.85f, 0.28f, 0.62f)
        GLES30.glUniform3f(shader.uLightPos3, sim.playerPos.x - 4f, 4.0f, sim.playerPos.z + 7f)
        GLES30.glUniform3f(shader.uLightColor3, 0.92f, 0.56f, 0.22f)

        drawWetConcreteMossFloor()
        drawWorldObjects()
        drawPlayerAndAllies()
        drawEnemies()
        drawProjectilesAndPickups()
        drawShockwavesAndSparks()

        if (sim.rainEnabled) {
            drawRain(dt)
        }
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
        // Mode 1: procedural wet stone/concrete tiles + damp moss + reflective puddles
        drawMesh(
            0f, 0f, 0f, 0f, 0f, 0f,
            92f, 1f, 92f,
            baseColor = floatArrayOf(0.14f, 0.17f, 0.22f),
            shininess = if (sim.graphicsQuality.puddleReflect) 96f else 20f,
            materialMode = 1,
            mesh = MeshPrimitives.groundQuad
        )

        if (sim.graphicsQuality.puddleReflect) {
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

    private fun drawWorldObjects() {
        for (obj in sim.world.objects) {
            when (obj.kind) {
                WorldPropKind.SKYSCRAPER -> {
                    drawMesh(obj.x, obj.y, obj.z, 0f, obj.yawDeg, 0f, obj.sx, obj.sy, obj.sz, obj.baseColor, shininess = 18f, materialMode = 2)
                    // Subtle vertical neon sign / window strip
                    drawMesh(obj.x, obj.y * 0.8f, obj.z, 0f, obj.yawDeg, 0f, obj.sx + 0.12f, 0.35f, obj.sz + 0.12f, obj.neonColor, obj.neonColor, 0.85f)
                }
                WorldPropKind.HOUSE_WORKSHOP -> {
                    // Main concrete structure with moss & rain stains (mode 2)
                    drawMesh(obj.x, obj.y, obj.z, 0f, obj.yawDeg, 0f, obj.sx, obj.sy, obj.sz, obj.baseColor, shininess = 16f, materialMode = 2)
                    // Upper corrugated metal overhang / loft
                    drawMesh(obj.x, obj.y + obj.sy * 0.22f, obj.z, 0f, obj.yawDeg, 0f, obj.sx + 0.25f, obj.sy * 0.36f, obj.sz + 0.25f, floatArrayOf(0.16f, 0.20f, 0.25f), shininess = 28f, materialMode = 3)
                    // Rooftop concrete parapet & AC unit box
                    drawMesh(obj.x - obj.sx * 0.22f, obj.y + obj.sy * 0.56f, obj.z, 0f, obj.yawDeg, 0f, 1.3f, 0.85f, 1.1f, floatArrayOf(0.24f, 0.27f, 0.31f), materialMode = 3)
                    drawMesh(obj.x + obj.sx * 0.20f, obj.y + obj.sy * 0.55f, obj.z + 0.4f, 0f, 0f, 0f, 0.65f, 0.75f, 0.65f, floatArrayOf(0.20f, 0.23f, 0.28f), materialMode = 3, mesh = MeshPrimitives.cylinder)
                    // Warm lit garage opening on ground floor
                    drawMesh(obj.x, 1.1f, obj.z, 0f, obj.yawDeg, 0f, obj.sx * 0.62f, 1.9f, obj.sz + 0.14f, floatArrayOf(0.85f, 0.52f, 0.20f), floatArrayOf(0.92f, 0.56f, 0.22f), 0.78f)
                    // Exterior wall lantern
                    drawMesh(obj.x + obj.sx * 0.48f, obj.y + 0.5f, obj.z + obj.sz * 0.48f, 0f, 0f, 0f, 0.25f, 0.65f, 0.25f, obj.neonColor, obj.neonColor, 1.4f)
                }
                WorldPropKind.STAIR_PLATFORM -> {
                    // Concrete base + steps + green metal center handrail (corrimão)
                    drawMesh(obj.x, obj.y, obj.z, 0f, obj.yawDeg, 0f, obj.sx, obj.sy, obj.sz, obj.baseColor, shininess = 18f, materialMode = 4)
                    // Center handrail top bar & posts
                    drawMesh(obj.x, obj.y + 0.92f, obj.z, 0f, obj.yawDeg, 0f, 0.10f, 0.08f, obj.sz * 0.95f, obj.neonColor, shininess = 36f)
                    drawMesh(obj.x, obj.y + 0.52f, obj.z - obj.sz * 0.35f, 0f, 0f, 0f, 0.08f, 0.85f, 0.08f, obj.neonColor)
                    drawMesh(obj.x, obj.y + 0.52f, obj.z + obj.sz * 0.35f, 0f, 0f, 0f, 0.08f, 0.85f, 0.08f, obj.neonColor)
                }
                WorldPropKind.HANDRAIL_BARRIER -> {
                    // Concrete curb base + green/steel tubular guardrail (corrimão)
                    drawMesh(obj.x, 0.14f, obj.z, 0f, obj.yawDeg, 0f, obj.sx, 0.28f, 0.32f, floatArrayOf(0.22f, 0.25f, 0.29f), materialMode = 2)
                    drawMesh(obj.x, 0.96f, obj.z, 0f, obj.yawDeg, 0f, obj.sx, 0.09f, 0.09f, obj.baseColor, shininess = 38f)
                    drawMesh(obj.x, 0.62f, obj.z, 0f, obj.yawDeg, 0f, obj.sx, 0.07f, 0.07f, obj.baseColor, shininess = 38f)
                    // 3 vertical posts
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
            skinStyle = sim.skinStyle
        )

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
            drawMesh(bx, sim.playerPos.y + 1.0f, bz, 0f, Math3D.deg(ang) + 90f, 0f, 0.12f, 0.12f, 0.95f, sim.weaponCol, sim.weaponCol, 1.5f)
        }

        for (t in sim.turrets) {
            drawMesh(t.pos.x, 0.45f, t.pos.z, 0f, 0f, 0f, 0.55f, 0.9f, 0.55f, floatArrayOf(0.22f, 0.28f, 0.35f), mesh = MeshPrimitives.cylinder)
            drawMesh(t.pos.x, 1.05f, t.pos.z, 0f, t.yawDeg, 0f, 0.5f, 0.32f, 0.72f, sim.visorCol, sim.visorCol, 1.1f)
        }

        for (m in sim.mines) {
            drawMesh(m.pos.x, 0.12f, m.pos.z, 0f, sim.elapsedSeconds * 90f, 0f, 0.45f, 0.16f, 0.45f, floatArrayOf(0.95f, 0.55f, 0.15f), floatArrayOf(0.95f, 0.55f, 0.15f), 1.4f, mesh = MeshPrimitives.octahedron)
        }
    }

    private fun drawEnemies() {
        GLES30.glUniform1i(shader.uMaterialMode, 0)
        for (e in sim.enemies) {
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

    private fun drawProjectilesAndPickups() {
        for (p in sim.projectiles) {
            drawMesh(p.pos.x, p.pos.y, p.pos.z, 0f, sim.elapsedSeconds * 240f, 0f, p.scale, p.scale, p.scale * 1.8f, p.color, p.color, 1.8f, mesh = MeshPrimitives.octahedron)
        }
        val xpColor = floatArrayOf(0.22f, 0.75f, 0.97f)
        val crColor = floatArrayOf(0.92f, 0.66f, 0.22f)
        for (orb in sim.pickups) {
            val col = if (orb.isCredit) crColor else xpColor
            drawMesh(orb.pos.x, 0.45f + sin(sim.elapsedSeconds * 4f + orb.pos.x) * 0.1f, orb.pos.z, 0f, sim.elapsedSeconds * 130f, 0f, 0.34f, 0.34f, 0.34f, col, col, 1.5f, mesh = MeshPrimitives.octahedron)
        }
    }

    private fun drawShockwavesAndSparks() {
        for (s in sim.shockwaves) {
            val t = 1f - (s.life / s.maxLife).coerceIn(0f, 1f)
            val curRadius = Math3D.lerp(s.radius, s.maxRadius, t)
            drawMesh(s.x, s.y, s.z, 0f, 0f, 0f, curRadius, 1f, curRadius, s.color, s.color, 1.5f, alpha = (s.life / s.maxLife).coerceIn(0f, 1f), mesh = MeshPrimitives.ring)
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
            if (d[1] < 0f) {
                d[0] = sim.playerPos.x + (rng.nextFloat() - 0.5f) * 44f
                d[1] = 18f + rng.nextFloat() * 5f
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
}
