package com.example.engine3d

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.CharacterClassDef
import com.example.data.PetDef
import com.example.data.SkinDef
import com.example.data.WeaponType
import com.example.data.parseHexColor
import com.example.data.resolvePalette
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class TurntableGLRenderer(private val showLobbyAtmosphere: Boolean) : GLSurfaceView.Renderer {
    @Volatile var classDef: CharacterClassDef? = null
    @Volatile var skinDef: SkinDef? = null
    @Volatile var petDef: PetDef? = null
    @Volatile var manualYawOffset: Float = 0f

    private lateinit var shader: CyberpunkLitShader
    private lateinit var lineShader: ParticleLineShader
    private lateinit var rigRenderer: CharacterRigRenderer

    private val projMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val vpMatrix = FloatArray(16)
    private val modelMat = FloatArray(16)
    private val mvpMat = FloatArray(16)

    private var startTime = System.nanoTime()
    private var lastFrameNs = System.nanoTime()

    private val rainCount = 120
    private val rainCoords = FloatArray(rainCount * 6)
    private val rainDrops = Array(rainCount) { floatArrayOf(0f, 0f, 0f) }
    private val rainBuffer: FloatBuffer = ByteBuffer.allocateDirect(rainCoords.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
    private val rng = Random(99)

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.031f, 0.043f, 0.063f, 1f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        shader = CyberpunkLitShader()
        lineShader = ParticleLineShader()
        rigRenderer = CharacterRigRenderer(shader)

        for (i in 0 until rainCount) {
            rainDrops[i][0] = (rng.nextFloat() - 0.5f) * 14f
            rainDrops[i][1] = rng.nextFloat() * 8f
            rainDrops[i][2] = (rng.nextFloat() - 0.5f) * 12f - 2f
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        val aspect = width.toFloat() / height.coerceAtLeast(1).toFloat()
        Matrix.perspectiveM(projMatrix, 0, 42f, aspect, 0.2f, 60f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val elapsed = (now - startTime) / 1_000_000_000f
        val dt = ((now - lastFrameNs) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
        lastFrameNs = now

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        val camY = if (showLobbyAtmosphere) 1.55f else 1.75f
        val camZ = if (showLobbyAtmosphere) 4.4f else 3.85f
        val lookY = if (showLobbyAtmosphere) 0.92f else 1.05f
        Matrix.setLookAtM(viewMatrix, 0, 0f, camY, camZ, 0f, lookY, 0f, 0f, 1f, 0f)
        Matrix.multiplyMM(vpMatrix, 0, projMatrix, 0, viewMatrix, 0)

        val charDef = classDef ?: return
        val pal = resolvePalette(charDef, skinDef)
        val pCol = parseHexColor(pal.primaryHex)
        val sCol = parseHexColor(pal.secondaryHex)
        val vCol = parseHexColor(pal.visorHex)
        val wCol = parseHexColor(pal.weaponHex)

        GLES30.glUseProgram(shader.programId)
        GLES30.glUniform3f(shader.uCameraPos, 0f, camY, camZ)
        GLES30.glUniform3f(shader.uFogColor, 0.031f, 0.043f, 0.063f)
        GLES30.glUniform1f(shader.uFogDensity, 0.035f)
        GLES30.glUniform3f(shader.uLightPos1, -3.5f, 4.0f, 2.5f)
        GLES30.glUniform3f(shader.uLightColor1, 0.25f, 0.65f, 0.95f)
        GLES30.glUniform3f(shader.uLightPos2, 3.5f, 4.0f, 2.5f)
        GLES30.glUniform3f(shader.uLightColor2, sCol[0] * 0.9f, sCol[1] * 0.9f, sCol[2] * 0.9f)
        GLES30.glUniform3f(shader.uLightPos3, 0f, 1.8f, 3.2f)
        GLES30.glUniform3f(shader.uLightColor3, wCol[0] * 0.8f, wCol[1] * 0.8f, wCol[2] * 0.8f)

        if (showLobbyAtmosphere) {
            // Wet concrete & moss alley floor matching the arena aesthetic
            drawPart(0f, -0.02f, 0f, 0f, 0f, 0f, 28f, 1f, 28f, floatArrayOf(0.14f, 0.17f, 0.22f), floatArrayOf(0f, 0f, 0f), 0f, 80f, 1f, MeshPrimitives.groundQuad, materialMode = 1)
            drawPart(-4.5f, 4.5f, -9.5f, 0f, 15f, 0f, 2.2f, 9f, 2.2f, floatArrayOf(0.08f, 0.11f, 0.15f), sCol, 0.22f, materialMode = 2)
            drawPart(4.8f, 5.2f, -10.5f, 0f, -18f, 0f, 2.6f, 10.4f, 2.6f, floatArrayOf(0.08f, 0.11f, 0.15f), vCol, 0.22f, materialMode = 2)
            drawPart(0f, 6.0f, -13.0f, 0f, 0f, 0f, 4.2f, 12f, 3.0f, floatArrayOf(0.07f, 0.10f, 0.14f), wCol, 0.16f, materialMode = 2)
        }

        // Sleek Metallic Pedestal + Outer Ring
        drawPart(0f, 0.02f, 0f, 0f, 0f, 0f, 1.25f, 1f, 1.25f, floatArrayOf(0.09f, 0.12f, 0.17f), sCol, 0.22f, 56f, 1f, MeshPrimitives.disc)
        drawPart(0f, 0.03f, 0f, 0f, elapsed * 25f, 0f, 1.48f, 1f, 1.48f, sCol, sCol, 0.85f, 32f, 0.85f, MeshPrimitives.ring)

        // Character 3D Model
        rigRenderer.drawPlayerRig(
            vpMatrix = vpMatrix,
            x = 0f, y = 0.05f, z = 0f,
            yawDeg = elapsed * 22f + manualYawOffset,
            weaponType = charDef.weaponType,
            primaryCol = pCol,
            secondaryCol = sCol,
            visorCol = vCol,
            weaponCol = wCol,
            animState = RigAnimState.IDLE,
            animTime = elapsed,
            hitFlash = 0f,
            shieldActive = false,
            skinStyle = skinDef?.accentStyle
        )

        petDef?.let { pet ->
            rigRenderer.drawPetRig(
                vpMatrix = vpMatrix,
                archetype = pet.archetype,
                x = -1.05f,
                y = 0.06f,
                z = 0.28f,
                yawDeg = elapsed * 25f + manualYawOffset + 14f,
                primaryCol = parseHexColor(pet.primaryHex),
                glowCol = parseHexColor(pet.glowHex),
                animTime = elapsed
            )
        }

        // Character-Specific 3D Lobby VFX
        drawClassSpecificVfx(charDef.weaponType, elapsed, sCol, vCol, wCol)

        if (showLobbyAtmosphere) {
            drawLobbyRain(dt)
        }
    }

    private fun drawClassSpecificVfx(
        weaponType: WeaponType,
        t: Float,
        sCol: FloatArray,
        vCol: FloatArray,
        wCol: FloatArray
    ) {
        when (weaponType) {
            WeaponType.ENERGY_BLADES -> {
                // Berserker: orbiting thermal blade embers & rising heat sparks
                for (i in 0 until 6) {
                    val ang = t * 2.4f + i * 1.047f
                    val y = 0.25f + ((t * 0.9f + i * 0.35f) % 1.8f)
                    drawPart(cos(ang) * 1.15f, y, sin(ang) * 1.15f, t * 90f, ang * 57f, 0f, 0.09f, 0.16f, 0.09f, wCol, wCol, 1.5f, mesh = MeshPrimitives.octahedron)
                }
            }
            WeaponType.PULSE_WRENCH_GUN -> {
                // Engenheiro: hovering tactical companion drone orbiting shoulder + ground scanner ring
                val ang = t * 1.6f
                drawPart(cos(ang) * 1.1f, 1.68f + sin(t * 4f) * 0.08f, sin(ang) * 1.1f, 0f, -ang * 57f, 0f, 0.28f, 0.20f, 0.28f, vCol, vCol, 1.4f, mesh = MeshPrimitives.octahedron)
                drawPart(0f, 0.06f, 0f, 0f, -t * 40f, 0f, 1.72f, 1f, 1.72f, sCol, sCol, 0.7f, alpha = 0.45f, mesh = MeshPrimitives.ring)
            }
            WeaponType.PLASMA_RIFLE -> {
                // Atirador: laser targeting sweep & precision rangefinder reticle ring
                val sweepY = 0.2f + (sin(t * 1.5f) * 0.5f + 0.5f) * 1.6f
                drawPart(0f, sweepY, 0f, 0f, t * 50f, 0f, 1.05f, 1f, 1.05f, vCol, vCol, 1.1f, alpha = 0.4f, mesh = MeshPrimitives.ring)
            }
            WeaponType.ARC_STAFF -> {
                // Tecnomago: 3 levitating arc crystals & orbital gravity halo
                for (i in 0 until 3) {
                    val ang = t * 2.1f + i * 2.094f
                    drawPart(cos(ang) * 1.05f, 1.35f + sin(t * 3f + i) * 0.22f, sin(ang) * 1.05f, t * 120f, t * 90f, 0f, 0.16f, 0.24f, 0.16f, wCol, wCol, 1.7f, mesh = MeshPrimitives.octahedron)
                }
            }
            WeaponType.CYBER_DECK -> {
                // Hacker: ascending matrix data cubes & pulsing EMP ground ring
                for (i in 0 until 7) {
                    val ang = i * 0.9f + t * 0.8f
                    val y = 0.15f + ((t * 0.75f + i * 0.28f) % 1.95f)
                    drawPart(cos(ang) * 0.95f, y, sin(ang) * 0.95f, t * 45f, t * 70f, 0f, 0.10f, 0.10f, 0.10f, wCol, wCol, 1.4f)
                }
                val pulseR = 0.9f + ((t * 0.7f) % 1.0f) * 1.2f
                drawPart(0f, 0.05f, 0f, 0f, 0f, 0f, pulseR, 1f, pulseR, vCol, vCol, 0.9f, alpha = 0.45f, mesh = MeshPrimitives.ring)
            }
            WeaponType.HEAVY_SHIELD_CANNON -> {
                // Guardião: dual rotating holographic fortress barrier rings
                drawPart(0f, 0.95f, 0f, 0f, t * 35f, 0f, 1.28f, 1f, 1.28f, vCol, vCol, 1.0f, alpha = 0.45f, mesh = MeshPrimitives.ring)
                drawPart(0f, 1.35f, 0f, 0f, -t * 35f, 0f, 1.15f, 1f, 1.15f, sCol, sCol, 0.9f, alpha = 0.35f, mesh = MeshPrimitives.ring)
            }
            WeaponType.DUAL_PISTOLS -> {
                // Caçador: rapid twin kinetic thruster trails orbiting low
                for (i in 0 until 4) {
                    val ang = t * 4.2f + i * 1.57f
                    drawPart(cos(ang) * 1.1f, 0.55f + sin(t * 5f + i) * 0.18f, sin(ang) * 1.1f, 0f, -ang * 57f, 0f, 0.08f, 0.08f, 0.32f, wCol, wCol, 1.6f)
                }
            }
        }
    }

    private fun drawLobbyRain(dt: Float) {
        var idx = 0
        for (i in 0 until rainCount) {
            val d = rainDrops[i]
            d[1] -= 14f * dt
            if (d[1] < 0f) {
                d[0] = (rng.nextFloat() - 0.5f) * 14f
                d[1] = 7.5f + rng.nextFloat() * 2f
                d[2] = (rng.nextFloat() - 0.5f) * 12f - 2f
            }
            rainCoords[idx++] = d[0]
            rainCoords[idx++] = d[1]
            rainCoords[idx++] = d[2]
            rainCoords[idx++] = d[0] - 0.08f
            rainCoords[idx++] = d[1] - 0.55f
            rainCoords[idx++] = d[2]
        }
        rainBuffer.clear()
        rainBuffer.put(rainCoords)
        rainBuffer.position(0)

        GLES30.glUseProgram(lineShader.programId)
        GLES30.glUniformMatrix4fv(lineShader.uMVP, 1, false, vpMatrix, 0)
        GLES30.glUniform4f(lineShader.uColor, 0.55f, 0.75f, 0.92f, 0.25f)
        GLES30.glEnableVertexAttribArray(lineShader.aPos)
        GLES30.glVertexAttribPointer(lineShader.aPos, 3, GLES30.GL_FLOAT, false, 12, rainBuffer)
        GLES30.glDrawArrays(GLES30.GL_LINES, 0, rainCount * 2)
    }

    private fun drawPart(
        tx: Float, ty: Float, tz: Float,
        rx: Float, ry: Float, rz: Float,
        sx: Float, sy: Float, sz: Float,
        baseColor: FloatArray,
        emissive: FloatArray,
        emissiveStrength: Float,
        shininess: Float = 28f,
        alpha: Float = 1f,
        mesh: MeshData = MeshPrimitives.cube,
        materialMode: Int = 0
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
}

@Composable
fun CharacterTurntable3D(
    classDef: CharacterClassDef,
    skinDef: SkinDef?,
    petDef: PetDef? = null,
    showLobbyAtmosphere: Boolean = false,
    modifier: Modifier = Modifier
) {
    var dragYaw by remember { mutableFloatStateOf(0f) }
    val renderer = remember(showLobbyAtmosphere) { TurntableGLRenderer(showLobbyAtmosphere) }
    renderer.classDef = classDef
    renderer.skinDef = skinDef
    renderer.petDef = petDef
    renderer.manualYawOffset = dragYaw

    AndroidView(
        modifier = modifier.pointerInput(Unit) {
            detectDragGestures { change, dragAmount ->
                change.consume()
                dragYaw += dragAmount.x * 0.6f
            }
        },
        factory = { ctx: Context ->
            GLSurfaceView(ctx).apply {
                setEGLContextClientVersion(3)
                setRenderer(renderer)
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            }
        }
    )
}
