package com.example.engine3d

import android.opengl.GLES30
import android.opengl.Matrix
import com.example.data.CharacterPalette
import com.example.data.WeaponType
import kotlin.math.abs
import kotlin.math.sin

enum class RigAnimState {
    IDLE, WALK, RUN, ATTACK, DODGE, CLIMB, JUMP, HIT, DEFEAT
}

/**
 * Hierarchical 3D Character & Robot Rig Renderer.
 * Mirrors the Godot scene architecture:
 *   Entity -> VisualRoot -> Pelvis -> Torso -> (Head, LeftArm, RightArm, LeftLeg, RightLeg, WeaponMount)
 */
class CharacterRigRenderer(private val shader: CyberpunkLitShader) {
    private val modelMat = FloatArray(16)
    private val mvpMat = FloatArray(16)
    private val tempMat = FloatArray(16)
    private val zeroEmissive = floatArrayOf(0f, 0f, 0f)

    fun drawBox(
        vpMatrix: FloatArray,
        parentMat: FloatArray,
        tx: Float, ty: Float, tz: Float,
        rx: Float, ry: Float, rz: Float,
        sx: Float, sy: Float, sz: Float,
        color: FloatArray,
        emissive: FloatArray = zeroEmissive,
        emissiveStrength: Float = 0f,
        shininess: Float = 24f,
        mesh: MeshData = MeshPrimitives.cube
    ) {
        Matrix.setIdentityM(tempMat, 0)
        Matrix.translateM(tempMat, 0, tx, ty, tz)
        if (ry != 0f) Matrix.rotateM(tempMat, 0, ry, 0f, 1f, 0f)
        if (rx != 0f) Matrix.rotateM(tempMat, 0, rx, 1f, 0f, 0f)
        if (rz != 0f) Matrix.rotateM(tempMat, 0, rz, 0f, 0f, 1f)
        Matrix.scaleM(tempMat, 0, sx, sy, sz)
        Matrix.multiplyMM(modelMat, 0, parentMat, 0, tempMat, 0)
        Matrix.multiplyMM(mvpMat, 0, vpMatrix, 0, modelMat, 0)

        GLES30.glUniformMatrix4fv(shader.uMVP, 1, false, mvpMat, 0)
        GLES30.glUniformMatrix4fv(shader.uModel, 1, false, modelMat, 0)
        GLES30.glUniform3fv(shader.uBaseColor, 1, color, 0)
        GLES30.glUniform3fv(shader.uEmissiveColor, 1, emissive, 0)
        GLES30.glUniform1f(shader.uEmissiveStrength, emissiveStrength)
        GLES30.glUniform1f(shader.uShininess, shininess)
        GLES30.glUniform1f(shader.uAlpha, 1f)
        GLES30.glUniform1i(shader.uMaterialMode, 0)

        mesh.draw(shader.aPos, shader.aNorm)
    }

    fun drawPlayerRig(
        vpMatrix: FloatArray,
        x: Float, y: Float, z: Float,
        yawDeg: Float,
        weaponType: WeaponType,
        primaryCol: FloatArray,
        secondaryCol: FloatArray,
        visorCol: FloatArray,
        weaponCol: FloatArray,
        animState: RigAnimState,
        animTime: Float,
        hitFlash: Float,
        shieldActive: Boolean,
        skinStyle: String? = null
    ) {
        val rootMat = FloatArray(16)
        Matrix.setIdentityM(rootMat, 0)
        Matrix.translateM(rootMat, 0, x, y, z)
        Matrix.rotateM(rootMat, 0, yawDeg, 0f, 1f, 0f)

        val bob: Float
        val legSwing: Float
        val armSwing: Float
        val torsoLean: Float
        val attackSwing: Float

        when (animState) {
            RigAnimState.IDLE -> {
                bob = sin(animTime * 2.8f) * 0.03f
                legSwing = 0f
                armSwing = sin(animTime * 2.8f) * 4f
                torsoLean = 0f
                attackSwing = 0f
            }
            RigAnimState.WALK, RigAnimState.RUN -> {
                val freq = if (animState == RigAnimState.RUN) 12f else 8f
                val amp = if (animState == RigAnimState.RUN) 38f else 24f
                bob = abs(sin(animTime * freq)) * 0.08f
                legSwing = sin(animTime * freq) * amp
                armSwing = -sin(animTime * freq) * amp * 0.8f
                torsoLean = if (animState == RigAnimState.RUN) 14f else 7f
                attackSwing = 0f
            }
            RigAnimState.ATTACK -> {
                bob = 0.04f
                legSwing = 12f
                armSwing = -20f
                torsoLean = 18f
                attackSwing = sin(animTime * 24f) * 65f - 45f
            }
            RigAnimState.DODGE -> {
                bob = -0.15f
                legSwing = 35f
                armSwing = -45f
                torsoLean = 36f
                attackSwing = 0f
            }
            RigAnimState.CLIMB -> {
                // Alternating upward wall-climb motion
                val climbWave = sin(animTime * 14f)
                bob = abs(climbWave) * 0.07f
                legSwing = climbWave * 46f
                armSwing = -climbWave * 52f - 78f
                torsoLean = 10f
                attackSwing = -35f
            }
            RigAnimState.JUMP -> {
                // Dynamic mid-air parkour vault/leap pose
                bob = 0.06f
                legSwing = 34f
                armSwing = -38f
                torsoLean = 20f
                attackSwing = 0f
            }
            RigAnimState.HIT -> {
                bob = 0f
                legSwing = -12f
                armSwing = 25f
                torsoLean = -18f
                attackSwing = 0f
            }
            RigAnimState.DEFEAT -> {
                Matrix.rotateM(rootMat, 0, 82f, 1f, 0f, 0f)
                Matrix.translateM(rootMat, 0, 0f, -0.7f, 0f)
                bob = 0f; legSwing = 0f; armSwing = 0f; torsoLean = 0f; attackSwing = 0f
            }
        }

        val flashEmit = if (hitFlash > 0f) 0.9f else 0.1f
        val bodyColor = if (hitFlash > 0f) floatArrayOf(1f, 0.35f, 0.35f) else primaryCol

        // Pelvis
        drawBox(vpMatrix, rootMat, 0f, 0.85f + bob, 0f, torsoLean * 0.4f, 0f, 0f, 0.48f, 0.22f, 0.30f, bodyColor, secondaryCol, 0.15f, mesh = MeshPrimitives.beveledBox)

        // Left & Right Legs
        drawBox(vpMatrix, rootMat, -0.15f, 0.42f + bob * 0.3f, sin(Math3D.rad(legSwing)) * 0.15f, legSwing, 0f, 0f, 0.18f, 0.72f, 0.20f, bodyColor, zeroEmissive, 0f, mesh = MeshPrimitives.capsule)
        drawBox(vpMatrix, rootMat, 0.15f, 0.42f + bob * 0.3f, -sin(Math3D.rad(legSwing)) * 0.15f, -legSwing, 0f, 0f, 0.18f, 0.72f, 0.20f, bodyColor, zeroEmissive, 0f, mesh = MeshPrimitives.capsule)
        // Neon Knee Pads
        drawBox(vpMatrix, rootMat, -0.15f, 0.44f + bob * 0.3f, sin(Math3D.rad(legSwing)) * 0.15f + 0.11f, legSwing, 0f, 0f, 0.12f, 0.10f, 0.05f, secondaryCol, secondaryCol, 0.8f)
        drawBox(vpMatrix, rootMat, 0.15f, 0.44f + bob * 0.3f, -sin(Math3D.rad(legSwing)) * 0.15f + 0.11f, -legSwing, 0f, 0f, 0.12f, 0.10f, 0.05f, secondaryCol, secondaryCol, 0.8f)

        // Torso
        val torsoWidth = if (weaponType == WeaponType.HEAVY_SHIELD_CANNON) 0.68f else 0.54f
        drawBox(vpMatrix, rootMat, 0f, 1.24f + bob, 0f, torsoLean, 0f, 0f, torsoWidth, 0.56f, 0.34f, bodyColor, secondaryCol, flashEmit, mesh = MeshPrimitives.beveledBox)
        // Chest Neon Core Strip
        drawBox(vpMatrix, rootMat, 0f, 1.28f + bob, 0.18f, torsoLean, 0f, 0f, 0.22f, 0.18f, 0.05f, secondaryCol, secondaryCol, 1.15f)

        // Dorsal Cybernetic Pack / Thrusters
        drawBox(vpMatrix, rootMat, 0f, 1.28f + bob, -0.22f, torsoLean, 0f, 0f, 0.34f, 0.38f, 0.16f, bodyColor, visorCol, 0.45f)

        // Head & Helmet Visor
        drawBox(vpMatrix, rootMat, 0f, 1.72f + bob, 0.02f, torsoLean * 0.5f, 0f, 0f, 0.34f, 0.34f, 0.34f, bodyColor, zeroEmissive, 0f, mesh = MeshPrimitives.beveledBox)
        drawBox(vpMatrix, rootMat, 0f, 1.74f + bob, 0.18f, torsoLean * 0.5f, 0f, 0f, 0.28f, 0.11f, 0.08f, visorCol, visorCol, 1.4f)

        // Shoulder Pauldrons
        drawBox(vpMatrix, rootMat, -torsoWidth * 0.58f, 1.46f + bob, 0f, torsoLean, 0f, 12f, 0.22f, 0.18f, 0.26f, secondaryCol, secondaryCol, 0.35f, mesh = MeshPrimitives.beveledBox)
        drawBox(vpMatrix, rootMat, torsoWidth * 0.58f, 1.46f + bob, 0f, torsoLean, 0f, -12f, 0.22f, 0.18f, 0.26f, secondaryCol, secondaryCol, 0.35f, mesh = MeshPrimitives.beveledBox)

        // Arms & Class Weapons
        val rightArmPitch = -35f + armSwing + attackSwing
        val leftArmPitch = -25f - armSwing + (if (weaponType == WeaponType.ENERGY_BLADES || weaponType == WeaponType.DUAL_PISTOLS) attackSwing else 0f)

        // Left Arm
        drawBox(vpMatrix, rootMat, -torsoWidth * 0.62f, 1.18f + bob, 0.08f, leftArmPitch, 0f, 0f, 0.16f, 0.56f, 0.16f, bodyColor, zeroEmissive, 0f, mesh = MeshPrimitives.capsule)
        // Right Arm
        drawBox(vpMatrix, rootMat, torsoWidth * 0.62f, 1.18f + bob, 0.08f, rightArmPitch, 0f, 0f, 0.16f, 0.56f, 0.16f, bodyColor, zeroEmissive, 0f, mesh = MeshPrimitives.capsule)

        // Extra armor plates, segmented gloves and boots (kept attached to existing animation poses)
        for (side in floatArrayOf(-1f, 1f)) {
            val swing = if (side < 0f) legSwing else -legSwing
            val armPitch = if (side < 0f) leftArmPitch else rightArmPitch
            val legZ = if (side < 0f) sin(Math3D.rad(legSwing)) * 0.15f else -sin(Math3D.rad(legSwing)) * 0.15f
            drawBox(vpMatrix, rootMat, side * 0.15f, 0.18f + bob * 0.3f, 0.13f + legZ,
                swing, 0f, 0f, 0.24f, 0.20f, 0.37f, secondaryCol, zeroEmissive, 0f,
                mesh = MeshPrimitives.beveledBox)
            drawBox(vpMatrix, rootMat, side * (torsoWidth * 0.62f), 0.96f + bob, 0.10f,
                armPitch, 0f, 0f, 0.21f, 0.24f, 0.22f, secondaryCol, visorCol, 0.25f,
                mesh = MeshPrimitives.beveledBox)
            drawBox(vpMatrix, rootMat, side * (torsoWidth * 0.59f), 1.49f + bob, 0.06f,
                torsoLean, 0f, side * -16f, 0.28f, 0.19f, 0.32f, bodyColor, zeroEmissive, 0f,
                mesh = MeshPrimitives.beveledBox)
        }
        // Helmet jaw and two lateral sensor modules
        drawBox(vpMatrix, rootMat, 0f, 1.61f + bob, 0.16f, torsoLean * 0.5f, 0f, 0f,
            0.27f, 0.10f, 0.16f, secondaryCol, zeroEmissive, 0f,
            mesh = MeshPrimitives.beveledBox)
        for (side in floatArrayOf(-1f, 1f)) {
            drawBox(vpMatrix, rootMat, side * 0.20f, 1.73f + bob, 0.02f, 0f, 0f, 0f,
                0.08f, 0.15f, 0.15f, secondaryCol, visorCol, 0.35f,
                mesh = MeshPrimitives.cylinder)
        }

        // Class-specific Weapon Geometry
        when (weaponType) {
            WeaponType.ENERGY_BLADES -> {
                drawBox(vpMatrix, rootMat, torsoWidth * 0.65f, 1.05f + bob, 0.55f, rightArmPitch * 0.3f, 0f, 0f, 0.06f, 0.10f, 0.95f, weaponCol, weaponCol, 1.6f, mesh = MeshPrimitives.blade)
                drawBox(vpMatrix, rootMat, -torsoWidth * 0.65f, 1.05f + bob, 0.55f, leftArmPitch * 0.3f, 0f, 0f, 0.06f, 0.10f, 0.95f, weaponCol, weaponCol, 1.6f, mesh = MeshPrimitives.blade)
            }
            WeaponType.PLASMA_RIFLE -> {
                drawBox(vpMatrix, rootMat, torsoWidth * 0.52f, 1.15f + bob, 0.58f, 0f, 0f, 0f, 0.10f, 0.16f, 1.15f, floatArrayOf(0.15f, 0.18f, 0.24f), weaponCol, 0.3f)
                drawBox(vpMatrix, rootMat, torsoWidth * 0.52f, 1.17f + bob, 0.85f, 0f, 0f, 0f, 0.06f, 0.06f, 0.65f, weaponCol, weaponCol, 1.7f)
            }
            WeaponType.ARC_STAFF -> {
                drawBox(vpMatrix, rootMat, torsoWidth * 0.65f, 1.25f + bob, 0.42f, 15f, 0f, 0f, 0.07f, 1.65f, 0.07f, floatArrayOf(0.22f, 0.20f, 0.30f), weaponCol, 0.5f, mesh = MeshPrimitives.cylinder)
                drawBox(vpMatrix, rootMat, torsoWidth * 0.65f, 2.05f + bob, 0.62f, animTime * 120f, animTime * 90f, 0f, 0.22f, 0.22f, 0.22f, weaponCol, weaponCol, 1.8f, mesh = MeshPrimitives.octahedron)
            }
            WeaponType.HEAVY_SHIELD_CANNON -> {
                // Tower shield on left arm
                drawBox(vpMatrix, rootMat, -torsoWidth * 0.72f, 1.15f + bob, 0.36f, 0f, -15f, 0f, 0.10f, 1.05f, 0.68f, floatArrayOf(0.15f, 0.20f, 0.28f), weaponCol, 0.9f)
                // Heavy cannon on right arm
                drawBox(vpMatrix, rootMat, torsoWidth * 0.66f, 1.12f + bob, 0.48f, 0f, 0f, 0f, 0.18f, 0.20f, 0.85f, weaponCol, weaponCol, 1.2f, mesh = MeshPrimitives.cylinder)
            }
            WeaponType.DUAL_PISTOLS -> {
                drawBox(vpMatrix, rootMat, torsoWidth * 0.65f, 1.12f + bob, 0.45f, 0f, 0f, 0f, 0.08f, 0.14f, 0.46f, weaponCol, weaponCol, 1.5f)
                drawBox(vpMatrix, rootMat, -torsoWidth * 0.65f, 1.12f + bob, 0.45f, 0f, 0f, 0f, 0.08f, 0.14f, 0.46f, weaponCol, weaponCol, 1.5f)
            }
            WeaponType.PULSE_WRENCH_GUN, WeaponType.CYBER_DECK -> {
                drawBox(vpMatrix, rootMat, torsoWidth * 0.62f, 1.12f + bob, 0.46f, 0f, 0f, 0f, 0.14f, 0.14f, 0.62f, weaponCol, weaponCol, 1.4f)
                if (weaponType == WeaponType.CYBER_DECK) {
                    drawBox(vpMatrix, rootMat, -torsoWidth * 0.62f, 1.15f + bob, 0.36f, -35f, 0f, 0f, 0.26f, 0.04f, 0.20f, visorCol, visorCol, 1.5f)
                }
            }
        }

        // Modular Skin Attachments (Capes, Hoods, Halos, Respirators, Forearm Holo-Screens)
        val capeFlutter = if (animState == RigAnimState.RUN || animState == RigAnimState.JUMP || animState == RigAnimState.DODGE) {
            24f + sin(animTime * 11f) * 7f
        } else {
            10f + sin(animTime * 2.5f) * 3f
        }
        when (skinStyle) {
            "CAPE_BLADES" -> {
                // Berserker scarf collar + tattered crimson cape
                drawBox(vpMatrix, rootMat, 0f, 1.54f + bob, 0.02f, torsoLean, 0f, 0f, 0.44f, 0.14f, 0.40f, secondaryCol, secondaryCol, 0.35f)
                drawBox(vpMatrix, rootMat, 0f, 1.05f + bob, -0.26f, capeFlutter, 0f, 0f, 0.48f, 0.88f, 0.06f, secondaryCol, secondaryCol, 0.25f)
            }
            "HOOD_CAPE" -> {
                // Sniper stealth hood + flowing rear cloak
                drawBox(vpMatrix, rootMat, 0f, 1.78f + bob, -0.02f, torsoLean * 0.5f, 0f, 0f, 0.40f, 0.38f, 0.40f, bodyColor, secondaryCol, 0.25f, mesh = MeshPrimitives.beveledBox)
                drawBox(vpMatrix, rootMat, 0f, 1.08f + bob, -0.25f, capeFlutter, 0f, 0f, 0.46f, 0.86f, 0.05f, secondaryCol, secondaryCol, 0.32f)
            }
            "HALO_COAT" -> {
                // Tecnomago floating dorsal arcane halo + armored long coat tails
                drawBox(vpMatrix, rootMat, 0f, 1.72f + bob, -0.24f, 90f, animTime * 45f, 0f, 0.88f, 1f, 0.88f, weaponCol, weaponCol, 1.3f, mesh = MeshPrimitives.ring)
                drawBox(vpMatrix, rootMat, -0.24f, 0.72f + bob, -0.20f, capeFlutter * 0.7f, 0f, 8f, 0.22f, 0.74f, 0.06f, bodyColor, secondaryCol, 0.45f)
                drawBox(vpMatrix, rootMat, 0.24f, 0.72f + bob, -0.20f, capeFlutter * 0.7f, 0f, -8f, 0.22f, 0.74f, 0.06f, bodyColor, secondaryCol, 0.45f)
            }
            "HOOD_RESPIRATOR" -> {
                // Hacker stealth hood + glowing green/cyan respirator mask + glowing backpack
                drawBox(vpMatrix, rootMat, 0f, 1.78f + bob, -0.02f, torsoLean * 0.5f, 0f, 0f, 0.40f, 0.38f, 0.40f, bodyColor, secondaryCol, 0.35f, mesh = MeshPrimitives.beveledBox)
                drawBox(vpMatrix, rootMat, 0f, 1.64f + bob, 0.19f, torsoLean * 0.5f, 0f, 0f, 0.22f, 0.12f, 0.10f, visorCol, visorCol, 1.3f)
                drawBox(vpMatrix, rootMat, 0f, 1.24f + bob, -0.28f, torsoLean, 0f, 0f, 0.38f, 0.44f, 0.20f, bodyColor, visorCol, 0.8f)
            }
            "COAT_GOGGLES" -> {
                // Engineer forearm holo-projection + tactical chest reactor & trench tail
                drawBox(vpMatrix, rootMat, -torsoWidth * 0.65f, 1.20f + bob, 0.28f, -25f, 0f, 0f, 0.22f, 0.04f, 0.18f, visorCol, visorCol, 1.6f)
                drawBox(vpMatrix, rootMat, 0f, 0.72f + bob, -0.22f, capeFlutter * 0.6f, 0f, 0f, 0.50f, 0.68f, 0.06f, bodyColor, secondaryCol, 0.28f)
            }
            "HEAVY_SHIELD_CAPE" -> {
                // Guardian heavy shoulder crests + dark armored half-cape
                drawBox(vpMatrix, rootMat, -torsoWidth * 0.66f, 1.56f + bob, 0f, torsoLean, 0f, 18f, 0.30f, 0.22f, 0.34f, secondaryCol, secondaryCol, 0.45f)
                drawBox(vpMatrix, rootMat, torsoWidth * 0.66f, 1.56f + bob, 0f, torsoLean, 0f, -18f, 0.30f, 0.22f, 0.34f, secondaryCol, secondaryCol, 0.45f)
                drawBox(vpMatrix, rootMat, 0f, 0.96f + bob, -0.28f, capeFlutter, 0f, 0f, 0.52f, 0.85f, 0.06f, floatArrayOf(0.14f, 0.16f, 0.20f), secondaryCol, 0.25f)
            }
            "SCARF_BLADE" -> {
                // Hunter flowing cyber scarf
                drawBox(vpMatrix, rootMat, 0f, 1.54f + bob, 0.02f, torsoLean, 0f, 0f, 0.42f, 0.12f, 0.38f, secondaryCol, secondaryCol, 0.45f)
                drawBox(vpMatrix, rootMat, -0.14f, 1.15f + bob, -0.26f, capeFlutter * 1.15f, 0f, 10f, 0.20f, 0.82f, 0.04f, secondaryCol, secondaryCol, 0.65f)
            }
        }

        // Optional energy shield bubble ring
        if (shieldActive) {
            drawBox(vpMatrix, rootMat, 0f, 0.06f, 0f, 0f, animTime * 60f, 0f, 1.1f, 1f, 1.1f, visorCol, visorCol, 1.1f, mesh = MeshPrimitives.ring)
        }
    }

    fun drawEnemyRig(
        vpMatrix: FloatArray,
        enemyId: String,
        isBoss: Boolean,
        x: Float, y: Float, z: Float,
        yawDeg: Float,
        scale: Float,
        primaryCol: FloatArray,
        glowCol: FloatArray,
        weakpointCol: FloatArray,
        animTime: Float,
        hitFlash: Float,
        phase2: Boolean
    ) {
        val rootMat = FloatArray(16)
        Matrix.setIdentityM(rootMat, 0)
        Matrix.translateM(rootMat, 0, x, y, z)
        Matrix.rotateM(rootMat, 0, yawDeg, 0f, 1f, 0f)
        Matrix.scaleM(rootMat, 0, scale, scale, scale)

        val bodyCol = if (hitFlash > 0f) floatArrayOf(1f, 0.85f, 0.85f) else primaryCol
        val emitCol = if (hitFlash > 0f) floatArrayOf(1f, 1f, 1f) else glowCol
        val emitPower = if (hitFlash > 0f) 1.5f else 1.25f

        if (isBoss) {
            // Giant Boss Colossus (OMEGA-01 / SERAPH-X / TITAN ZERO)
            val hover = if (enemyId == "seraph_x") 0.85f + sin(animTime * 3.5f) * 0.25f else 0f
            val stomp = sin(animTime * 4.5f) * 18f

            if (enemyId != "seraph_x") {
                drawBox(vpMatrix, rootMat, -0.35f, 0.45f, 0f, stomp, 0f, 0f, 0.30f, 0.90f, 0.34f, bodyCol, zeroEmissive, 0f, mesh = MeshPrimitives.capsule)
                drawBox(vpMatrix, rootMat, 0.35f, 0.45f, 0f, -stomp, 0f, 0f, 0.30f, 0.90f, 0.34f, bodyCol, zeroEmissive, 0f, mesh = MeshPrimitives.capsule)
            } else {
                // Winged thruster pods
                drawBox(vpMatrix, rootMat, -0.95f, 1.4f + hover, -0.1f, 0f, 0f, -20f, 0.95f, 0.18f, 0.55f, bodyCol, emitCol, 0.9f)
                drawBox(vpMatrix, rootMat, 0.95f, 1.4f + hover, -0.1f, 0f, 0f, 20f, 0.95f, 0.18f, 0.55f, bodyCol, emitCol, 0.9f)
            }

            // Heavy Torso Chassis
            drawBox(vpMatrix, rootMat, 0f, 1.35f + hover, 0f, 0f, 0f, 0f, 1.15f, 1.05f, 0.78f, bodyCol, emitCol, 0.25f, mesh = MeshPrimitives.beveledBox)
            // Vulnerable Spinning Reactor Core
            val coreColor = if (phase2) floatArrayOf(1f, 0.15f, 0.2f) else weakpointCol
            drawBox(vpMatrix, rootMat, 0f, 1.38f + hover, 0.42f, animTime * 110f, animTime * 90f, 0f, 0.42f, 0.42f, 0.42f, coreColor, coreColor, 2.0f, mesh = MeshPrimitives.octahedron)
            // Armored Head & Visor
            drawBox(vpMatrix, rootMat, 0f, 2.05f + hover, 0.05f, 0f, 0f, 0f, 0.52f, 0.38f, 0.48f, bodyCol, zeroEmissive, 0f, mesh = MeshPrimitives.beveledBox)
            drawBox(vpMatrix, rootMat, 0f, 2.07f + hover, 0.30f, 0f, 0f, 0f, 0.42f, 0.12f, 0.08f, emitCol, emitCol, 1.8f)

            // Massive Arms / Cannons
            drawBox(vpMatrix, rootMat, -0.85f, 1.28f + hover, 0.22f, -20f + sin(animTime * 3f) * 14f, 0f, 0f, 0.34f, 0.95f, 0.36f, bodyCol, emitCol, 0.5f, mesh = MeshPrimitives.capsule)
            drawBox(vpMatrix, rootMat, 0.85f, 1.28f + hover, 0.22f, -20f - sin(animTime * 3f) * 14f, 0f, 0f, 0.34f, 0.95f, 0.36f, bodyCol, emitCol, 0.5f, mesh = MeshPrimitives.capsule)
            // Boss Ground Threat Ring
            drawBox(vpMatrix, rootMat, 0f, 0.05f, 0f, 0f, animTime * 45f, 0f, 1.5f, 1f, 1.5f, emitCol, emitCol, 1.2f, mesh = MeshPrimitives.ring)
            return
        }

        // All standard enemy types retain their original hit and movement logic.
        when (enemyId) {
            "drone_voador" -> {
                val bob = 1.45f + sin(animTime * 5.2f) * 0.22f
                drawBox(vpMatrix, rootMat, 0f, bob, 0f, 0f, animTime * 40f, 0f, 0.55f, 0.32f, 0.55f, bodyCol, emitCol, 0.3f, mesh = MeshPrimitives.octahedron)
                drawBox(vpMatrix, rootMat, -0.45f, bob + 0.08f, 0f, 0f, 0f, 18f, 0.45f, 0.06f, 0.22f, bodyCol, emitCol, 0.8f)
                drawBox(vpMatrix, rootMat, 0.45f, bob + 0.08f, 0f, 0f, 0f, -18f, 0.45f, 0.06f, 0.22f, bodyCol, emitCol, 0.8f)
                drawBox(vpMatrix, rootMat, 0f, bob, 0.28f, 0f, 0f, 0f, 0.18f, 0.14f, 0.12f, emitCol, emitCol, emitPower)
            }
            "rastejador", "aranha_industrial" -> {
                val legBob = sin(animTime * 14f) * 18f
                drawBox(vpMatrix, rootMat, 0f, 0.45f, 0f, 0f, 0f, 0f, 0.62f, 0.28f, 0.68f, bodyCol, emitCol, 0.25f)
                drawBox(vpMatrix, rootMat, 0f, 0.52f, 0.34f, 0f, 0f, 0f, 0.22f, 0.14f, 0.14f, emitCol, emitCol, emitPower)
                drawBox(vpMatrix, rootMat, -0.42f, 0.28f, 0.22f, legBob, 0f, 35f, 0.10f, 0.48f, 0.10f, bodyCol, zeroEmissive, 0f, mesh = MeshPrimitives.capsule)
                drawBox(vpMatrix, rootMat, 0.42f, 0.28f, 0.22f, -legBob, 0f, -35f, 0.10f, 0.48f, 0.10f, bodyCol, zeroEmissive, 0f, mesh = MeshPrimitives.capsule)
                drawBox(vpMatrix, rootMat, -0.42f, 0.28f, -0.22f, -legBob, 0f, 35f, 0.10f, 0.48f, 0.10f, bodyCol, zeroEmissive, 0f, mesh = MeshPrimitives.capsule)
                drawBox(vpMatrix, rootMat, 0.42f, 0.28f, -0.22f, legBob, 0f, -35f, 0.10f, 0.48f, 0.10f, bodyCol, zeroEmissive, 0f, mesh = MeshPrimitives.capsule)
            }
            "torre_automatica" -> {
                drawBox(vpMatrix, rootMat, 0f, 0.45f, 0f, 0f, 0f, 0f, 0.60f, 0.90f, 0.60f, bodyCol, zeroEmissive, 0f, mesh = MeshPrimitives.cylinder)
                drawBox(vpMatrix, rootMat, 0f, 1.05f, 0f, 0f, 0f, 0f, 0.55f, 0.36f, 0.62f, bodyCol, emitCol, 0.4f)
                drawBox(vpMatrix, rootMat, -0.15f, 1.05f, 0.45f, 90f, 0f, 0f, 0.10f, 0.58f, 0.10f, emitCol, emitCol, 1.2f, mesh = MeshPrimitives.cylinder)
                drawBox(vpMatrix, rootMat, 0.15f, 1.05f, 0.45f, 90f, 0f, 0f, 0.10f, 0.58f, 0.10f, emitCol, emitCol, 1.2f, mesh = MeshPrimitives.cylinder)
            }
            "robo_explosivo" -> {
                val pulse = 1.0f + abs(sin(animTime * 11f)) * 0.28f
                drawBox(vpMatrix, rootMat, 0f, 0.72f, 0f, animTime * 60f, animTime * 90f, 0f, 0.58f * pulse, 0.58f * pulse, 0.58f * pulse, emitCol, emitCol, 1.9f, mesh = MeshPrimitives.octahedron)
                drawBox(vpMatrix, rootMat, -0.20f, 0.28f, 0f, sin(animTime * 14f) * 30f, 0f, 0f, 0.12f, 0.52f, 0.12f, bodyCol, zeroEmissive, 0f, mesh = MeshPrimitives.capsule)
                drawBox(vpMatrix, rootMat, 0.20f, 0.28f, 0f, -sin(animTime * 14f) * 30f, 0f, 0f, 0.12f, 0.52f, 0.12f, bodyCol, zeroEmissive, 0f, mesh = MeshPrimitives.capsule)
            }
            else -> {
                // Bipedal Combat Robot (Soldado Robótico / Caçador Cibernético / Titã Industrial)
                val walk = sin(animTime * 9f) * 26f
                drawBox(vpMatrix, rootMat, -0.18f, 0.42f, 0f, walk, 0f, 0f, 0.18f, 0.78f, 0.20f, bodyCol, zeroEmissive, 0f, mesh = MeshPrimitives.capsule)
                drawBox(vpMatrix, rootMat, 0.18f, 0.42f, 0f, -walk, 0f, 0f, 0.18f, 0.78f, 0.20f, bodyCol, zeroEmissive, 0f, mesh = MeshPrimitives.capsule)
                drawBox(vpMatrix, rootMat, 0f, 1.15f, 0f, 8f, 0f, 0f, 0.56f, 0.62f, 0.36f, bodyCol, emitCol, 0.25f, mesh = MeshPrimitives.beveledBox)
                drawBox(vpMatrix, rootMat, 0f, 1.18f, 0.19f, 8f, 0f, 0f, 0.24f, 0.18f, 0.05f, emitCol, emitCol, emitPower)
                drawBox(vpMatrix, rootMat, 0f, 1.65f, 0.04f, 0f, 0f, 0f, 0.32f, 0.30f, 0.32f, bodyCol, zeroEmissive, 0f, mesh = MeshPrimitives.beveledBox)
                drawBox(vpMatrix, rootMat, 0f, 1.66f, 0.21f, 0f, 0f, 0f, 0.26f, 0.08f, 0.06f, emitCol, emitCol, 1.6f)
                drawBox(vpMatrix, rootMat, -0.40f, 1.12f, 0.16f, -35f - walk * 0.4f, 0f, 0f, 0.16f, 0.56f, 0.16f, bodyCol, emitCol, 0.4f, mesh = MeshPrimitives.capsule)
                drawBox(vpMatrix, rootMat, 0.40f, 1.12f, 0.16f, -35f + walk * 0.4f, 0f, 0f, 0.16f, 0.56f, 0.16f, bodyCol, emitCol, 0.4f, mesh = MeshPrimitives.capsule)
            }
        }
    }
}
