package com.example.engine3d

import android.opengl.GLES30
import android.opengl.Matrix
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
        skinStyle: String? = null,
        jumpProgress: Float = 0f,
        jumpStyle: Int = 0
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
                val climbWave = sin(animTime * 14f)
                bob = abs(climbWave) * 0.07f
                legSwing = climbWave * 46f
                armSwing = -climbWave * 52f - 78f
                torsoLean = 10f
                attackSwing = -35f
            }
            RigAnimState.JUMP -> {
                // Acrobatic parkour moves (360° Front-Flip Tuck, Side Vault Roll, Wall-Kick Aerial Spin)
                val flipFrac = (jumpProgress / 0.56f).coerceIn(0f, 1f)
                when (jumpStyle % 3) {
                    0 -> {
                        val flipDeg = flipFrac * 360f
                        Matrix.translateM(rootMat, 0, 0f, 1.0f, 0f)
                        Matrix.rotateM(rootMat, 0, flipDeg, 1f, 0f, 0f)
                        Matrix.translateM(rootMat, 0, 0f, -1.0f, 0f)
                        bob = 0.10f
                        legSwing = 52f
                        armSwing = -58f
                        torsoLean = 28f
                    }
                    1 -> {
                        val rollDeg = flipFrac * 360f
                        Matrix.translateM(rootMat, 0, 0f, 0.95f, 0f)
                        Matrix.rotateM(rootMat, 0, 26f, 1f, 0f, 0f)
                        Matrix.rotateM(rootMat, 0, rollDeg, 0f, 0f, 1f)
                        Matrix.translateM(rootMat, 0, 0f, -0.95f, 0f)
                        bob = 0.08f
                        legSwing = -36f
                        armSwing = -62f
                        torsoLean = 22f
                    }
                    else -> {
                        val spinDeg = flipFrac * 360f
                        Matrix.rotateM(rootMat, 0, spinDeg, 0f, 1f, 0f)
                        bob = 0.08f
                        legSwing = 48f
                        armSwing = -48f
                        torsoLean = 18f
                    }
                }
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
        val darkArmor = floatArrayOf(
            (bodyColor[0] * 0.65f).coerceAtLeast(0.06f),
            (bodyColor[1] * 0.65f).coerceAtLeast(0.06f),
            (bodyColor[2] * 0.65f).coerceAtLeast(0.08f)
        )

        // Pelvis + Tactical Belt & Core Buckle
        drawBox(vpMatrix, rootMat, 0f, 0.85f + bob, 0f, torsoLean * 0.4f, 0f, 0f, 0.50f, 0.22f, 0.32f, bodyColor, secondaryCol, 0.18f, shininess = 42f, mesh = MeshPrimitives.beveledBox)
        drawBox(vpMatrix, rootMat, 0f, 0.94f + bob, 0.01f, torsoLean * 0.4f, 0f, 0f, 0.52f, 0.08f, 0.35f, darkArmor, zeroEmissive, 0f, shininess = 32f, mesh = MeshPrimitives.beveledBox)
        drawBox(vpMatrix, rootMat, 0f, 0.94f + bob, 0.18f, torsoLean * 0.4f, 0f, 0f, 0.14f, 0.09f, 0.06f, visorCol, visorCol, 1.0f, mesh = MeshPrimitives.beveledBox)

        // Hip & Knee Joints + Capsule Legs + Armored Shin Greaves + Cyber Boots
        for (side in floatArrayOf(-1f, 1f)) {
            val swing = if (side < 0f) legSwing else -legSwing
            val legZ = sin(Math3D.rad(swing)) * 0.15f
            // Hip ball joint
            drawBox(vpMatrix, rootMat, side * 0.15f, 0.78f + bob * 0.5f, 0f, 0f, 0f, 0f, 0.16f, 0.16f, 0.16f, darkArmor, zeroEmissive, 0f, mesh = MeshPrimitives.sphere)
            // Leg capsule
            drawBox(vpMatrix, rootMat, side * 0.15f, 0.44f + bob * 0.3f, legZ, swing, 0f, 0f, 0.20f, 0.72f, 0.22f, bodyColor, zeroEmissive, 0f, shininess = 34f, mesh = MeshPrimitives.capsule)
            // Glowing Neon Knee Pad
            drawBox(vpMatrix, rootMat, side * 0.15f, 0.44f + bob * 0.3f, legZ + 0.11f, swing, 0f, 0f, 0.13f, 0.11f, 0.07f, secondaryCol, secondaryCol, 0.95f, mesh = MeshPrimitives.beveledBox)
            // Armored Shin Boot
            drawBox(vpMatrix, rootMat, side * 0.15f, 0.17f + bob * 0.3f, legZ + 0.12f, swing, 0f, 0f, 0.23f, 0.20f, 0.36f, secondaryCol, zeroEmissive, 0f, shininess = 48f, mesh = MeshPrimitives.beveledBox)
            drawBox(vpMatrix, rootMat, side * 0.15f, 0.12f + bob * 0.3f, legZ + 0.24f, swing, 0f, 0f, 0.20f, 0.07f, 0.12f, visorCol, visorCol, 0.65f, mesh = MeshPrimitives.beveledBox)
        }

        // Torso & Layered Chest Armor
        val torsoWidth = if (weaponType == WeaponType.HEAVY_SHIELD_CANNON) 0.70f else 0.56f
        drawBox(vpMatrix, rootMat, 0f, 1.25f + bob, 0f, torsoLean, 0f, 0f, torsoWidth, 0.58f, 0.36f, bodyColor, secondaryCol, flashEmit, shininess = 48f, mesh = MeshPrimitives.beveledBox)
        // Pectoral Armor Plate + Glowing Chest Reactor Strip
        drawBox(vpMatrix, rootMat, 0f, 1.32f + bob, 0.14f, torsoLean, 0f, 0f, torsoWidth * 0.84f, 0.28f, 0.14f, darkArmor, secondaryCol, 0.20f, shininess = 54f, mesh = MeshPrimitives.beveledBox)
        drawBox(vpMatrix, rootMat, 0f, 1.30f + bob, 0.21f, torsoLean, 0f, 0f, 0.22f, 0.18f, 0.06f, secondaryCol, secondaryCol, 1.25f, mesh = MeshPrimitives.beveledBox)
        drawBox(vpMatrix, rootMat, 0f, 1.30f + bob, 0.23f, torsoLean, 0f, 0f, 0.11f, 0.11f, 0.05f, visorCol, visorCol, 1.6f, mesh = MeshPrimitives.octahedron)

        // Dorsal Cybernetic Jet-Pack + Twin Vertical Exhaust Nozzles
        drawBox(vpMatrix, rootMat, 0f, 1.28f + bob, -0.22f, torsoLean, 0f, 0f, 0.36f, 0.38f, 0.18f, darkArmor, visorCol, 0.45f, shininess = 42f, mesh = MeshPrimitives.beveledBox)
        for (side in floatArrayOf(-1f, 1f)) {
            drawBox(vpMatrix, rootMat, side * 0.11f, 1.22f + bob, -0.30f, torsoLean + 12f, 0f, 0f, 0.10f, 0.22f, 0.10f, secondaryCol, visorCol, 0.9f, mesh = MeshPrimitives.cylinder)
        }

        // Head, Visor, Jaw Guard & Tactical Lateral Sensor Pods
        drawBox(vpMatrix, rootMat, 0f, 1.73f + bob, 0.02f, torsoLean * 0.5f, 0f, 0f, 0.35f, 0.35f, 0.35f, bodyColor, zeroEmissive, 0f, shininess = 56f, mesh = MeshPrimitives.beveledBox)
        drawBox(vpMatrix, rootMat, 0f, 1.75f + bob, 0.18f, torsoLean * 0.5f, 0f, 0f, 0.29f, 0.11f, 0.09f, visorCol, visorCol, 1.5f, mesh = MeshPrimitives.beveledBox)
        drawBox(vpMatrix, rootMat, 0f, 1.62f + bob, 0.16f, torsoLean * 0.5f, 0f, 0f, 0.28f, 0.11f, 0.16f, secondaryCol, zeroEmissive, 0f, shininess = 44f, mesh = MeshPrimitives.beveledBox)
        for (side in floatArrayOf(-1f, 1f)) {
            drawBox(vpMatrix, rootMat, side * 0.20f, 1.74f + bob, 0.02f, 0f, 0f, 90f, 0.14f, 0.09f, 0.14f, secondaryCol, visorCol, 0.45f, mesh = MeshPrimitives.cylinder)
        }

        // Layered Shoulder Pauldrons & Shoulder Joints
        for (side in floatArrayOf(-1f, 1f)) {
            drawBox(vpMatrix, rootMat, side * torsoWidth * 0.55f, 1.44f + bob, 0f, 0f, 0f, 0f, 0.18f, 0.18f, 0.18f, darkArmor, zeroEmissive, 0f, mesh = MeshPrimitives.sphere)
            drawBox(vpMatrix, rootMat, side * torsoWidth * 0.58f, 1.47f + bob, 0f, torsoLean, 0f, side * -12f, 0.24f, 0.18f, 0.28f, secondaryCol, secondaryCol, 0.38f, shininess = 52f, mesh = MeshPrimitives.beveledBox)
            drawBox(vpMatrix, rootMat, side * torsoWidth * 0.61f, 1.51f + bob, 0.04f, torsoLean, 0f, side * -18f, 0.26f, 0.12f, 0.30f, bodyColor, zeroEmissive, 0f, shininess = 48f, mesh = MeshPrimitives.beveledBox)
        }

        // Arms & Segmented Tactical Gauntlets
        val rightArmPitch = -35f + armSwing + attackSwing
        val leftArmPitch = -25f - armSwing + (if (weaponType == WeaponType.ENERGY_BLADES || weaponType == WeaponType.DUAL_PISTOLS) attackSwing else 0f)

        for (side in floatArrayOf(-1f, 1f)) {
            val armPitch = if (side < 0f) leftArmPitch else rightArmPitch
            drawBox(vpMatrix, rootMat, side * torsoWidth * 0.62f, 1.19f + bob, 0.08f, armPitch, 0f, 0f, 0.18f, 0.56f, 0.18f, bodyColor, zeroEmissive, 0f, shininess = 34f, mesh = MeshPrimitives.capsule)
            drawBox(vpMatrix, rootMat, side * torsoWidth * 0.62f, 0.96f + bob, 0.11f, armPitch, 0f, 0f, 0.21f, 0.24f, 0.22f, secondaryCol, visorCol, 0.35f, shininess = 48f, mesh = MeshPrimitives.beveledBox)
        }

        // Class-specific Weapon Geometry (upgraded silhouette with beveled frames, blades, barrels & emitter rings)
        when (weaponType) {
            WeaponType.ENERGY_BLADES -> {
                for (side in floatArrayOf(-1f, 1f)) {
                    val pitch = if (side < 0f) leftArmPitch else rightArmPitch
                    drawBox(vpMatrix, rootMat, side * torsoWidth * 0.65f, 1.04f + bob, 0.25f, pitch * 0.3f, 0f, 0f, 0.11f, 0.14f, 0.22f, darkArmor, weaponCol, 0.5f, mesh = MeshPrimitives.beveledBox)
                    drawBox(vpMatrix, rootMat, side * torsoWidth * 0.65f, 1.05f + bob, 0.62f, pitch * 0.3f, 0f, 0f, 0.09f, 0.14f, 0.96f, weaponCol, weaponCol, 1.7f, mesh = MeshPrimitives.blade)
                }
            }
            WeaponType.PLASMA_RIFLE -> {
                drawBox(vpMatrix, rootMat, torsoWidth * 0.52f, 1.15f + bob, 0.54f, 0f, 0f, 0f, 0.12f, 0.18f, 0.86f, darkArmor, weaponCol, 0.35f, shininess = 56f, mesh = MeshPrimitives.beveledBox)
                drawBox(vpMatrix, rootMat, torsoWidth * 0.52f, 1.17f + bob, 0.96f, 90f, 0f, 0f, 0.08f, 0.68f, 0.08f, weaponCol, weaponCol, 1.75f, mesh = MeshPrimitives.cylinder)
                drawBox(vpMatrix, rootMat, torsoWidth * 0.52f, 1.28f + bob, 0.52f, 90f, 0f, 0f, 0.07f, 0.36f, 0.07f, visorCol, visorCol, 1.2f, mesh = MeshPrimitives.cylinder)
            }
            WeaponType.ARC_STAFF -> {
                drawBox(vpMatrix, rootMat, torsoWidth * 0.65f, 1.25f + bob, 0.42f, 15f, 0f, 0f, 0.08f, 1.65f, 0.08f, floatArrayOf(0.20f, 0.18f, 0.28f), weaponCol, 0.55f, mesh = MeshPrimitives.cylinder)
                drawBox(vpMatrix, rootMat, torsoWidth * 0.65f, 2.06f + bob, 0.63f, 90f, animTime * 75f, 0f, 0.34f, 0.34f, 0.34f, visorCol, visorCol, 1.3f, mesh = MeshPrimitives.torus)
                drawBox(vpMatrix, rootMat, torsoWidth * 0.65f, 2.06f + bob, 0.63f, animTime * 120f, animTime * 90f, 0f, 0.22f, 0.22f, 0.22f, weaponCol, weaponCol, 1.85f, mesh = MeshPrimitives.octahedron)
            }
            WeaponType.HEAVY_SHIELD_CANNON -> {
                // Layered heavy tower shield on left arm
                drawBox(vpMatrix, rootMat, -torsoWidth * 0.72f, 1.15f + bob, 0.36f, 0f, -15f, 0f, 0.12f, 1.08f, 0.70f, floatArrayOf(0.14f, 0.19f, 0.26f), weaponCol, 0.75f, shininess = 60f, mesh = MeshPrimitives.beveledBox)
                drawBox(vpMatrix, rootMat, -torsoWidth * 0.75f, 1.15f + bob, 0.38f, 0f, -15f, 0f, 0.06f, 0.78f, 0.38f, visorCol, visorCol, 1.35f, mesh = MeshPrimitives.beveledBox)
                // Heavy rotary cannon on right arm
                drawBox(vpMatrix, rootMat, torsoWidth * 0.66f, 1.12f + bob, 0.44f, 0f, 0f, 0f, 0.20f, 0.22f, 0.56f, darkArmor, weaponCol, 0.5f, mesh = MeshPrimitives.beveledBox)
                drawBox(vpMatrix, rootMat, torsoWidth * 0.66f, 1.12f + bob, 0.74f, 90f, 0f, 0f, 0.16f, 0.56f, 0.16f, weaponCol, weaponCol, 1.35f, mesh = MeshPrimitives.cylinder)
                drawBox(vpMatrix, rootMat, torsoWidth * 0.66f, 1.12f + bob, 0.96f, 90f, 0f, 0f, 0.22f, 0.22f, 0.22f, visorCol, visorCol, 1.25f, mesh = MeshPrimitives.torus)
            }
            WeaponType.DUAL_PISTOLS -> {
                for (side in floatArrayOf(-1f, 1f)) {
                    drawBox(vpMatrix, rootMat, side * torsoWidth * 0.65f, 1.12f + bob, 0.42f, 0f, 0f, 0f, 0.09f, 0.15f, 0.36f, darkArmor, weaponCol, 0.6f, mesh = MeshPrimitives.beveledBox)
                    drawBox(vpMatrix, rootMat, side * torsoWidth * 0.65f, 1.16f + bob, 0.56f, 90f, 0f, 0f, 0.06f, 0.28f, 0.06f, weaponCol, weaponCol, 1.6f, mesh = MeshPrimitives.cylinder)
                }
            }
            WeaponType.PULSE_WRENCH_GUN -> {
                drawBox(vpMatrix, rootMat, torsoWidth * 0.62f, 1.12f + bob, 0.42f, 0f, 0f, 0f, 0.16f, 0.16f, 0.44f, darkArmor, weaponCol, 0.65f, mesh = MeshPrimitives.beveledBox)
                drawBox(vpMatrix, rootMat, torsoWidth * 0.62f, 1.12f + bob, 0.68f, 90f, 0f, 0f, 0.11f, 0.38f, 0.11f, weaponCol, weaponCol, 1.45f, mesh = MeshPrimitives.cylinder)
                drawBox(vpMatrix, rootMat, torsoWidth * 0.62f, 1.12f + bob, 0.68f, 90f, animTime * 90f, 0f, 0.20f, 0.20f, 0.20f, visorCol, visorCol, 1.3f, mesh = MeshPrimitives.torus)
            }
            WeaponType.CYBER_DECK -> {
                drawBox(vpMatrix, rootMat, torsoWidth * 0.62f, 1.12f + bob, 0.44f, 0f, 0f, 0f, 0.13f, 0.14f, 0.44f, darkArmor, weaponCol, 0.8f, mesh = MeshPrimitives.beveledBox)
                drawBox(vpMatrix, rootMat, torsoWidth * 0.62f, 1.14f + bob, 0.66f, 90f, 0f, 0f, 0.07f, 0.26f, 0.07f, weaponCol, weaponCol, 1.5f, mesh = MeshPrimitives.cylinder)
                drawBox(vpMatrix, rootMat, -torsoWidth * 0.62f, 1.15f + bob, 0.36f, -35f, 0f, 0f, 0.28f, 0.05f, 0.22f, visorCol, visorCol, 1.55f, mesh = MeshPrimitives.beveledBox)
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
                drawBox(vpMatrix, rootMat, 0f, 1.54f + bob, 0.02f, torsoLean, 0f, 0f, 0.44f, 0.14f, 0.40f, secondaryCol, secondaryCol, 0.35f, mesh = MeshPrimitives.beveledBox)
                drawBox(vpMatrix, rootMat, 0f, 1.05f + bob, -0.27f, capeFlutter, 0f, 0f, 0.48f, 0.88f, 0.06f, secondaryCol, secondaryCol, 0.25f, mesh = MeshPrimitives.beveledBox)
            }
            "HOOD_CAPE" -> {
                drawBox(vpMatrix, rootMat, 0f, 1.79f + bob, -0.02f, torsoLean * 0.5f, 0f, 0f, 0.42f, 0.38f, 0.42f, bodyColor, secondaryCol, 0.25f, mesh = MeshPrimitives.beveledBox)
                drawBox(vpMatrix, rootMat, 0f, 1.98f + bob, -0.04f, -35f, 0f, 0f, 0.34f, 0.16f, 0.36f, bodyColor, secondaryCol, 0.25f, mesh = MeshPrimitives.cone)
                drawBox(vpMatrix, rootMat, 0f, 1.08f + bob, -0.26f, capeFlutter, 0f, 0f, 0.46f, 0.86f, 0.05f, secondaryCol, secondaryCol, 0.32f, mesh = MeshPrimitives.beveledBox)
            }
            "HALO_COAT" -> {
                drawBox(vpMatrix, rootMat, 0f, 1.72f + bob, -0.24f, 90f, animTime * 45f, 0f, 0.88f, 1f, 0.88f, weaponCol, weaponCol, 1.3f, mesh = MeshPrimitives.ring)
                drawBox(vpMatrix, rootMat, 0f, 1.72f + bob, -0.24f, 90f, -animTime * 60f, 0f, 0.64f, 0.64f, 0.64f, visorCol, visorCol, 1.2f, mesh = MeshPrimitives.torus)
                drawBox(vpMatrix, rootMat, -0.24f, 0.72f + bob, -0.20f, capeFlutter * 0.7f, 0f, 8f, 0.22f, 0.74f, 0.06f, bodyColor, secondaryCol, 0.45f, mesh = MeshPrimitives.beveledBox)
                drawBox(vpMatrix, rootMat, 0.24f, 0.72f + bob, -0.20f, capeFlutter * 0.7f, 0f, -8f, 0.22f, 0.74f, 0.06f, bodyColor, secondaryCol, 0.45f, mesh = MeshPrimitives.beveledBox)
            }
            "HOOD_RESPIRATOR" -> {
                drawBox(vpMatrix, rootMat, 0f, 1.79f + bob, -0.02f, torsoLean * 0.5f, 0f, 0f, 0.42f, 0.38f, 0.42f, bodyColor, secondaryCol, 0.35f, mesh = MeshPrimitives.beveledBox)
                drawBox(vpMatrix, rootMat, 0f, 1.64f + bob, 0.20f, torsoLean * 0.5f, 0f, 0f, 0.23f, 0.13f, 0.11f, visorCol, visorCol, 1.35f, mesh = MeshPrimitives.beveledBox)
                drawBox(vpMatrix, rootMat, 0f, 1.24f + bob, -0.30f, torsoLean, 0f, 0f, 0.38f, 0.44f, 0.20f, bodyColor, visorCol, 0.8f, mesh = MeshPrimitives.beveledBox)
            }
            "COAT_GOGGLES" -> {
                drawBox(vpMatrix, rootMat, -torsoWidth * 0.65f, 1.20f + bob, 0.28f, -25f, 0f, 0f, 0.24f, 0.04f, 0.19f, visorCol, visorCol, 1.6f, mesh = MeshPrimitives.beveledBox)
                drawBox(vpMatrix, rootMat, 0f, 0.72f + bob, -0.22f, capeFlutter * 0.6f, 0f, 0f, 0.50f, 0.68f, 0.06f, bodyColor, secondaryCol, 0.28f, mesh = MeshPrimitives.beveledBox)
            }
            "HEAVY_SHIELD_CAPE" -> {
                drawBox(vpMatrix, rootMat, -torsoWidth * 0.66f, 1.57f + bob, 0f, torsoLean, 0f, 18f, 0.30f, 0.22f, 0.34f, secondaryCol, secondaryCol, 0.45f, mesh = MeshPrimitives.beveledBox)
                drawBox(vpMatrix, rootMat, torsoWidth * 0.66f, 1.57f + bob, 0f, torsoLean, 0f, -18f, 0.30f, 0.22f, 0.34f, secondaryCol, secondaryCol, 0.45f, mesh = MeshPrimitives.beveledBox)
                drawBox(vpMatrix, rootMat, 0f, 0.96f + bob, -0.29f, capeFlutter, 0f, 0f, 0.52f, 0.85f, 0.06f, floatArrayOf(0.14f, 0.16f, 0.20f), secondaryCol, 0.25f, mesh = MeshPrimitives.beveledBox)
            }
            "SCARF_BLADE" -> {
                drawBox(vpMatrix, rootMat, 0f, 1.54f + bob, 0.02f, torsoLean, 0f, 0f, 0.42f, 0.12f, 0.38f, secondaryCol, secondaryCol, 0.45f, mesh = MeshPrimitives.beveledBox)
                drawBox(vpMatrix, rootMat, -0.14f, 1.15f + bob, -0.26f, capeFlutter * 1.15f, 0f, 10f, 0.20f, 0.82f, 0.04f, secondaryCol, secondaryCol, 0.65f, mesh = MeshPrimitives.blade)
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
                drawBox(vpMatrix, rootMat, -0.95f, 1.4f + hover, -0.1f, 0f, 0f, -20f, 0.95f, 0.18f, 0.55f, bodyCol, emitCol, 0.9f, mesh = MeshPrimitives.blade)
                drawBox(vpMatrix, rootMat, 0.95f, 1.4f + hover, -0.1f, 0f, 0f, 20f, 0.95f, 0.18f, 0.55f, bodyCol, emitCol, 0.9f, mesh = MeshPrimitives.blade)
            }

            // Heavy Torso Chassis + Shoulder Thruster Cones
            drawBox(vpMatrix, rootMat, 0f, 1.35f + hover, 0f, 0f, 0f, 0f, 1.15f, 1.05f, 0.78f, bodyCol, emitCol, 0.25f, shininess = 48f, mesh = MeshPrimitives.beveledBox)
            drawBox(vpMatrix, rootMat, -0.78f, 1.92f + hover, -0.05f, 0f, 0f, 18f, 0.32f, 0.45f, 0.32f, bodyCol, emitCol, 0.65f, mesh = MeshPrimitives.cone)
            drawBox(vpMatrix, rootMat, 0.78f, 1.92f + hover, -0.05f, 0f, 0f, -18f, 0.32f, 0.45f, 0.32f, bodyCol, emitCol, 0.65f, mesh = MeshPrimitives.cone)

            // Vulnerable Spinning Reactor Core + Containment Torus Ring
            val coreColor = if (phase2) floatArrayOf(1f, 0.15f, 0.2f) else weakpointCol
            drawBox(vpMatrix, rootMat, 0f, 1.38f + hover, 0.42f, 90f, animTime * 75f, 0f, 0.62f, 0.62f, 0.62f, emitCol, emitCol, 1.25f, mesh = MeshPrimitives.torus)
            drawBox(vpMatrix, rootMat, 0f, 1.38f + hover, 0.42f, animTime * 110f, animTime * 90f, 0f, 0.42f, 0.42f, 0.42f, coreColor, coreColor, 2.0f, mesh = MeshPrimitives.octahedron)

            // Armored Head & Visor
            drawBox(vpMatrix, rootMat, 0f, 2.05f + hover, 0.05f, 0f, 0f, 0f, 0.52f, 0.38f, 0.48f, bodyCol, zeroEmissive, 0f, mesh = MeshPrimitives.beveledBox)
            drawBox(vpMatrix, rootMat, 0f, 2.07f + hover, 0.30f, 0f, 0f, 0f, 0.42f, 0.12f, 0.08f, emitCol, emitCol, 1.8f, mesh = MeshPrimitives.beveledBox)

            // Massive Arms / Cannons
            drawBox(vpMatrix, rootMat, -0.85f, 1.28f + hover, 0.22f, -20f + sin(animTime * 3f) * 14f, 0f, 0f, 0.34f, 0.95f, 0.36f, bodyCol, emitCol, 0.5f, mesh = MeshPrimitives.capsule)
            drawBox(vpMatrix, rootMat, 0.85f, 1.28f + hover, 0.22f, -20f - sin(animTime * 3f) * 14f, 0f, 0f, 0.34f, 0.95f, 0.36f, bodyCol, emitCol, 0.5f, mesh = MeshPrimitives.capsule)
            // Boss Ground Threat Ring
            drawBox(vpMatrix, rootMat, 0f, 0.05f, 0f, 0f, animTime * 45f, 0f, 1.5f, 1f, 1.5f, emitCol, emitCol, 1.2f, mesh = MeshPrimitives.ring)
            return
        }

        when (enemyId) {
            "drone_voador" -> {
                val bob = 1.45f + sin(animTime * 5.2f) * 0.22f
                drawBox(vpMatrix, rootMat, 0f, bob, 0f, 0f, animTime * 40f, 0f, 0.54f, 0.34f, 0.54f, bodyCol, emitCol, 0.35f, mesh = MeshPrimitives.sphere)
                drawBox(vpMatrix, rootMat, 0f, bob, 0f, 0f, -animTime * 80f, 0f, 0.78f, 0.55f, 0.78f, emitCol, emitCol, 0.85f, mesh = MeshPrimitives.torus)
                drawBox(vpMatrix, rootMat, -0.45f, bob + 0.08f, 0f, 0f, 0f, 18f, 0.45f, 0.06f, 0.22f, bodyCol, emitCol, 0.8f, mesh = MeshPrimitives.blade)
                drawBox(vpMatrix, rootMat, 0.45f, bob + 0.08f, 0f, 0f, 0f, -18f, 0.45f, 0.06f, 0.22f, bodyCol, emitCol, 0.8f, mesh = MeshPrimitives.blade)
                drawBox(vpMatrix, rootMat, 0f, bob, 0.28f, 0f, 0f, 0f, 0.18f, 0.16f, 0.14f, emitCol, emitCol, emitPower, mesh = MeshPrimitives.sphere)
            }
            "rastejador", "aranha_industrial" -> {
                val legBob = sin(animTime * 14f) * 18f
                drawBox(vpMatrix, rootMat, 0f, 0.45f, 0f, 0f, 0f, 0f, 0.64f, 0.30f, 0.70f, bodyCol, emitCol, 0.25f, mesh = MeshPrimitives.beveledBox)
                drawBox(vpMatrix, rootMat, 0f, 0.52f, 0.34f, 0f, 0f, 0f, 0.22f, 0.16f, 0.16f, emitCol, emitCol, emitPower, mesh = MeshPrimitives.sphere)
                drawBox(vpMatrix, rootMat, -0.42f, 0.28f, 0.22f, legBob, 0f, 35f, 0.12f, 0.48f, 0.12f, bodyCol, zeroEmissive, 0f, mesh = MeshPrimitives.capsule)
                drawBox(vpMatrix, rootMat, 0.42f, 0.28f, 0.22f, -legBob, 0f, -35f, 0.12f, 0.48f, 0.12f, bodyCol, zeroEmissive, 0f, mesh = MeshPrimitives.capsule)
                drawBox(vpMatrix, rootMat, -0.42f, 0.28f, -0.22f, -legBob, 0f, 35f, 0.12f, 0.48f, 0.12f, bodyCol, zeroEmissive, 0f, mesh = MeshPrimitives.capsule)
                drawBox(vpMatrix, rootMat, 0.42f, 0.28f, -0.22f, legBob, 0f, -35f, 0.12f, 0.48f, 0.12f, bodyCol, zeroEmissive, 0f, mesh = MeshPrimitives.capsule)
            }
            "torre_automatica" -> {
                drawBox(vpMatrix, rootMat, 0f, 0.45f, 0f, 0f, 0f, 0f, 0.60f, 0.90f, 0.60f, bodyCol, zeroEmissive, 0f, mesh = MeshPrimitives.cylinder)
                drawBox(vpMatrix, rootMat, 0f, 0.88f, 0f, 0f, animTime * 60f, 0f, 0.66f, 0.66f, 0.66f, emitCol, emitCol, 0.8f, mesh = MeshPrimitives.torus)
                drawBox(vpMatrix, rootMat, 0f, 1.05f, 0f, 0f, 0f, 0f, 0.56f, 0.36f, 0.64f, bodyCol, emitCol, 0.4f, mesh = MeshPrimitives.beveledBox)
                drawBox(vpMatrix, rootMat, -0.15f, 1.05f, 0.45f, 90f, 0f, 0f, 0.10f, 0.58f, 0.10f, emitCol, emitCol, 1.2f, mesh = MeshPrimitives.cylinder)
                drawBox(vpMatrix, rootMat, 0.15f, 1.05f, 0.45f, 90f, 0f, 0f, 0.10f, 0.58f, 0.10f, emitCol, emitCol, 1.2f, mesh = MeshPrimitives.cylinder)
            }
            "robo_explosivo" -> {
                val pulse = 1.0f + abs(sin(animTime * 11f)) * 0.28f
                drawBox(vpMatrix, rootMat, 0f, 0.72f, 0f, animTime * 60f, animTime * 90f, 0f, 0.58f * pulse, 0.58f * pulse, 0.58f * pulse, emitCol, emitCol, 1.9f, mesh = MeshPrimitives.octahedron)
                drawBox(vpMatrix, rootMat, 0f, 0.72f, 0f, -animTime * 70f, 0f, animTime * 45f, 0.72f * pulse, 0.72f * pulse, 0.72f * pulse, bodyCol, emitCol, 0.8f, mesh = MeshPrimitives.torus)
                drawBox(vpMatrix, rootMat, -0.20f, 0.28f, 0f, sin(animTime * 14f) * 30f, 0f, 0f, 0.14f, 0.52f, 0.14f, bodyCol, zeroEmissive, 0f, mesh = MeshPrimitives.capsule)
                drawBox(vpMatrix, rootMat, 0.20f, 0.28f, 0f, -sin(animTime * 14f) * 30f, 0f, 0f, 0.14f, 0.52f, 0.14f, bodyCol, zeroEmissive, 0f, mesh = MeshPrimitives.capsule)
            }
            else -> {
                // Bipedal Combat Robot (Soldado Robótico / Caçador Cibernético / Titã Industrial)
                val walk = sin(animTime * 9f) * 26f
                drawBox(vpMatrix, rootMat, -0.18f, 0.42f, 0f, walk, 0f, 0f, 0.20f, 0.78f, 0.22f, bodyCol, zeroEmissive, 0f, mesh = MeshPrimitives.capsule)
                drawBox(vpMatrix, rootMat, 0.18f, 0.42f, 0f, -walk, 0f, 0f, 0.20f, 0.78f, 0.22f, bodyCol, zeroEmissive, 0f, mesh = MeshPrimitives.capsule)
                drawBox(vpMatrix, rootMat, 0f, 1.15f, 0f, 8f, 0f, 0f, 0.58f, 0.62f, 0.38f, bodyCol, emitCol, 0.25f, mesh = MeshPrimitives.beveledBox)
                drawBox(vpMatrix, rootMat, 0f, 1.18f, 0.20f, 8f, 0f, 0f, 0.24f, 0.18f, 0.06f, emitCol, emitCol, emitPower, mesh = MeshPrimitives.beveledBox)
                drawBox(vpMatrix, rootMat, 0f, 1.65f, 0.04f, 0f, 0f, 0f, 0.32f, 0.30f, 0.32f, bodyCol, zeroEmissive, 0f, mesh = MeshPrimitives.beveledBox)
                drawBox(vpMatrix, rootMat, 0f, 1.66f, 0.21f, 0f, 0f, 0f, 0.26f, 0.08f, 0.07f, emitCol, emitCol, 1.6f, mesh = MeshPrimitives.beveledBox)
                drawBox(vpMatrix, rootMat, -0.40f, 1.12f, 0.16f, -35f - walk * 0.4f, 0f, 0f, 0.18f, 0.56f, 0.18f, bodyCol, emitCol, 0.4f, mesh = MeshPrimitives.capsule)
                drawBox(vpMatrix, rootMat, 0.40f, 1.12f, 0.16f, -35f + walk * 0.4f, 0f, 0f, 0.18f, 0.56f, 0.18f, bodyCol, emitCol, 0.4f, mesh = MeshPrimitives.capsule)
            }
        }
    }

    fun drawPetRig(
        vpMatrix: FloatArray,
        archetype: String,
        x: Float, y: Float, z: Float,
        yawDeg: Float,
        primaryCol: FloatArray,
        glowCol: FloatArray,
        animTime: Float
    ) {
        val rootMat = FloatArray(16)
        Matrix.setIdentityM(rootMat, 0)
        Matrix.translateM(rootMat, 0, x, y, z)
        Matrix.rotateM(rootMat, 0, yawDeg, 0f, 1f, 0f)

        when (archetype) {
            "OWL_DRONE" -> {
                val hover = 1.15f + sin(animTime * 4.5f) * 0.16f
                val flap = sin(animTime * 10f) * 18f
                drawBox(vpMatrix, rootMat, 0f, hover, 0f, 0f, 0f, 0f, 0.42f, 0.44f, 0.38f, primaryCol, glowCol, 0.3f, mesh = MeshPrimitives.sphere)
                drawBox(vpMatrix, rootMat, 0f, hover + 0.08f, 0.16f, 0f, 0f, 0f, 0.30f, 0.11f, 0.08f, glowCol, glowCol, 1.6f, mesh = MeshPrimitives.beveledBox)
                drawBox(vpMatrix, rootMat, -0.34f, hover + 0.04f, 0f, 0f, 0f, 14f + flap, 0.42f, 0.06f, 0.24f, primaryCol, glowCol, 0.7f, mesh = MeshPrimitives.blade)
                drawBox(vpMatrix, rootMat, 0.34f, hover + 0.04f, 0f, 0f, 0f, -14f - flap, 0.42f, 0.06f, 0.24f, primaryCol, glowCol, 0.7f, mesh = MeshPrimitives.blade)
                drawBox(vpMatrix, rootMat, 0f, hover, 0f, 0f, animTime * 70f, 0f, 0.62f, 0.35f, 0.62f, glowCol, glowCol, 0.9f, mesh = MeshPrimitives.torus)
            }
            "CYBER_CAT" -> {
                val bob = abs(sin(animTime * 8f)) * 0.05f
                val trot = sin(animTime * 10f) * 24f
                drawBox(vpMatrix, rootMat, 0f, 0.40f + bob, 0f, 0f, 0f, 0f, 0.32f, 0.24f, 0.56f, primaryCol, glowCol, 0.3f, mesh = MeshPrimitives.beveledBox)
                drawBox(vpMatrix, rootMat, 0f, 0.54f + bob, 0.32f, 0f, 0f, 0f, 0.26f, 0.22f, 0.24f, primaryCol, zeroEmissive, 0f, mesh = MeshPrimitives.beveledBox)
                drawBox(vpMatrix, rootMat, 0f, 0.56f + bob, 0.44f, 0f, 0f, 0f, 0.20f, 0.07f, 0.06f, glowCol, glowCol, 1.7f, mesh = MeshPrimitives.beveledBox)
                drawBox(vpMatrix, rootMat, -0.09f, 0.72f + bob, 0.32f, 0f, 0f, 12f, 0.09f, 0.16f, 0.09f, glowCol, glowCol, 1.0f, mesh = MeshPrimitives.cone)
                drawBox(vpMatrix, rootMat, 0.09f, 0.72f + bob, 0.32f, 0f, 0f, -12f, 0.09f, 0.16f, 0.09f, glowCol, glowCol, 1.0f, mesh = MeshPrimitives.cone)
                drawBox(vpMatrix, rootMat, 0f, 0.58f + bob, -0.34f, -35f + sin(animTime * 6f) * 14f, 0f, 0f, 0.07f, 0.42f, 0.07f, glowCol, glowCol, 1.2f, mesh = MeshPrimitives.capsule)
                drawBox(vpMatrix, rootMat, -0.12f, 0.20f, 0.20f, trot, 0f, 0f, 0.09f, 0.34f, 0.09f, primaryCol, zeroEmissive, 0f, mesh = MeshPrimitives.capsule)
                drawBox(vpMatrix, rootMat, 0.12f, 0.20f, 0.20f, -trot, 0f, 0f, 0.09f, 0.34f, 0.09f, primaryCol, zeroEmissive, 0f, mesh = MeshPrimitives.capsule)
                drawBox(vpMatrix, rootMat, -0.12f, 0.20f, -0.20f, -trot, 0f, 0f, 0.09f, 0.34f, 0.09f, primaryCol, zeroEmissive, 0f, mesh = MeshPrimitives.capsule)
                drawBox(vpMatrix, rootMat, 0.12f, 0.20f, -0.20f, trot, 0f, 0f, 0.09f, 0.34f, 0.09f, primaryCol, zeroEmissive, 0f, mesh = MeshPrimitives.capsule)
            }
            "RAPTOR" -> {
                val step = sin(animTime * 11f) * 32f
                val bob = abs(sin(animTime * 11f)) * 0.06f
                drawBox(vpMatrix, rootMat, 0f, 0.54f + bob, 0f, 22f, 0f, 0f, 0.30f, 0.30f, 0.58f, primaryCol, glowCol, 0.35f, mesh = MeshPrimitives.beveledBox)
                drawBox(vpMatrix, rootMat, 0f, 0.76f + bob, 0.32f, 10f, 0f, 0f, 0.24f, 0.22f, 0.36f, primaryCol, glowCol, 0.4f, mesh = MeshPrimitives.beveledBox)
                drawBox(vpMatrix, rootMat, 0f, 0.80f + bob, 0.45f, 0f, 0f, 0f, 0.20f, 0.08f, 0.10f, glowCol, glowCol, 1.6f, mesh = MeshPrimitives.beveledBox)
                drawBox(vpMatrix, rootMat, 0f, 0.48f + bob, -0.44f, -15f, 0f, 0f, 0.12f, 0.12f, 0.48f, glowCol, glowCol, 1.1f, mesh = MeshPrimitives.blade)
                drawBox(vpMatrix, rootMat, -0.14f, 0.26f, 0f, step, 0f, 0f, 0.11f, 0.44f, 0.12f, primaryCol, zeroEmissive, 0f, mesh = MeshPrimitives.capsule)
                drawBox(vpMatrix, rootMat, 0.14f, 0.26f, 0f, -step, 0f, 0f, 0.11f, 0.44f, 0.12f, primaryCol, zeroEmissive, 0f, mesh = MeshPrimitives.capsule)
            }
            "ARC_ORB" -> {
                val hover = 1.25f + sin(animTime * 3.8f) * 0.18f
                drawBox(vpMatrix, rootMat, 0f, hover, 0f, animTime * 90f, animTime * 120f, 0f, 0.34f, 0.34f, 0.34f, glowCol, glowCol, 1.85f, mesh = MeshPrimitives.octahedron)
                drawBox(vpMatrix, rootMat, 0f, hover, 0f, 55f, animTime * 110f, 25f, 0.68f, 0.55f, 0.68f, primaryCol, glowCol, 1.15f, mesh = MeshPrimitives.torus)
                drawBox(vpMatrix, rootMat, 0f, hover, 0f, -55f, -animTime * 95f, -25f, 0.56f, 0.45f, 0.56f, glowCol, glowCol, 1.35f, mesh = MeshPrimitives.torus)
            }
            else -> {
                // QUADRUPED (K-09 Cyber-Lobo)
                val bob = abs(sin(animTime * 9f)) * 0.05f
                val trot = sin(animTime * 11f) * 28f
                drawBox(vpMatrix, rootMat, 0f, 0.44f + bob, 0f, 0f, 0f, 0f, 0.36f, 0.28f, 0.66f, primaryCol, glowCol, 0.3f, mesh = MeshPrimitives.beveledBox)
                drawBox(vpMatrix, rootMat, 0f, 0.46f + bob, 0f, 0f, 0f, 0f, 0.38f, 0.08f, 0.22f, glowCol, glowCol, 1.1f, mesh = MeshPrimitives.beveledBox)
                drawBox(vpMatrix, rootMat, 0f, 0.58f + bob, 0.36f, 8f, 0f, 0f, 0.26f, 0.24f, 0.34f, primaryCol, zeroEmissive, 0f, mesh = MeshPrimitives.beveledBox)
                drawBox(vpMatrix, rootMat, 0f, 0.61f + bob, 0.52f, 0f, 0f, 0f, 0.20f, 0.08f, 0.08f, glowCol, glowCol, 1.7f, mesh = MeshPrimitives.beveledBox)
                drawBox(vpMatrix, rootMat, -0.09f, 0.76f + bob, 0.28f, 0f, 0f, 10f, 0.08f, 0.16f, 0.08f, glowCol, glowCol, 0.9f, mesh = MeshPrimitives.cone)
                drawBox(vpMatrix, rootMat, 0.09f, 0.76f + bob, 0.28f, 0f, 0f, -10f, 0.08f, 0.16f, 0.08f, glowCol, glowCol, 0.9f, mesh = MeshPrimitives.cone)
                drawBox(vpMatrix, rootMat, 0f, 0.54f + bob, -0.40f, -45f + sin(animTime * 8f) * 14f, 0f, 0f, 0.08f, 0.36f, 0.08f, glowCol, glowCol, 1.2f, mesh = MeshPrimitives.blade)
                drawBox(vpMatrix, rootMat, -0.14f, 0.22f, 0.24f, trot, 0f, 0f, 0.10f, 0.38f, 0.10f, primaryCol, zeroEmissive, 0f, mesh = MeshPrimitives.capsule)
                drawBox(vpMatrix, rootMat, 0.14f, 0.22f, 0.24f, -trot, 0f, 0f, 0.10f, 0.38f, 0.10f, primaryCol, zeroEmissive, 0f, mesh = MeshPrimitives.capsule)
                drawBox(vpMatrix, rootMat, -0.14f, 0.22f, -0.24f, -trot, 0f, 0f, 0.10f, 0.38f, 0.10f, primaryCol, zeroEmissive, 0f, mesh = MeshPrimitives.capsule)
                drawBox(vpMatrix, rootMat, 0.14f, 0.22f, -0.24f, trot, 0f, 0f, 0.10f, 0.38f, 0.10f, primaryCol, zeroEmissive, 0f, mesh = MeshPrimitives.capsule)
            }
        }
    }
}
