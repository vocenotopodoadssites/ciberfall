package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

enum class SoundEffect(
    val minIntervalMs: Long,
    val defaultVolume: Float,
    val pitchJitter: Float
) {
    STRIKE_SLASH(minIntervalMs = 70L, defaultVolume = 0.85f, pitchJitter = 0.08f),
    HIT_IMPACT(minIntervalMs = 55L, defaultVolume = 0.72f, pitchJitter = 0.10f),
    SHOT_PLASMA(minIntervalMs = 60L, defaultVolume = 0.75f, pitchJitter = 0.07f),
    SHOT_ENEMY(minIntervalMs = 110L, defaultVolume = 0.48f, pitchJitter = 0.08f),
    DEFENSE_SHIELD(minIntervalMs = 95L, defaultVolume = 0.82f, pitchJitter = 0.05f),
    POWER_SKILL(minIntervalMs = 160L, defaultVolume = 0.95f, pitchJitter = 0.03f),
    JUMP_PARKOUR(minIntervalMs = 120L, defaultVolume = 0.78f, pitchJitter = 0.09f),
    LAND_IMPACT(minIntervalMs = 100L, defaultVolume = 0.72f, pitchJitter = 0.08f),
    FOOTSTEP(minIntervalMs = 190L, defaultVolume = 0.40f, pitchJitter = 0.12f),
    BOSS_ALERT(minIntervalMs = 800L, defaultVolume = 0.95f, pitchJitter = 0.0f),
    COLLECTIBLE_CHIME(minIntervalMs = 150L, defaultVolume = 0.85f, pitchJitter = 0.03f)
}

/**
 * Pure Kotlin 16-bit PCM mono WAV synthesizer for deterministic, zero-asset-latency game SFX.
 */
object ProceduralWaveSynth {
    private const val SAMPLE_RATE = 22050

    fun synthesizeWav(effect: SoundEffect): ByteArray {
        val samples = when (effect) {
            SoundEffect.STRIKE_SLASH -> genStrikeSlash()
            SoundEffect.HIT_IMPACT -> genHitImpact()
            SoundEffect.SHOT_PLASMA -> genShotPlasma()
            SoundEffect.SHOT_ENEMY -> genShotEnemy()
            SoundEffect.DEFENSE_SHIELD -> genDefenseShield()
            SoundEffect.POWER_SKILL -> genPowerSkill()
            SoundEffect.JUMP_PARKOUR -> genJumpParkour()
            SoundEffect.LAND_IMPACT -> genLandImpact()
            SoundEffect.FOOTSTEP -> genFootstep()
            SoundEffect.BOSS_ALERT -> genBossAlert()
            SoundEffect.COLLECTIBLE_CHIME -> genCollectibleChime()
        }
        return encodeWav16Mono(samples, SAMPLE_RATE)
    }

    private fun genStrikeSlash(): ShortArray {
        val dur = 0.16f
        val count = (SAMPLE_RATE * dur).toInt()
        val out = ShortArray(count)
        val rng = Random(101)
        var lp = 0f
        var phase = 0f
        for (i in 0 until count) {
            val t = i.toFloat() / SAMPLE_RATE
            val env = exp(-t * 18f) * (t / 0.012f).coerceAtMost(1f)
            val freq = 420f - 280f * (t / dur)
            phase += 2f * PI.toFloat() * freq / SAMPLE_RATE
            val noise = rng.nextFloat() * 2f - 1f
            lp += (0.28f - 0.18f * (t / dur)) * (noise - lp)
            val s = (lp * 0.72f + sin(phase) * 0.42f) * env
            out[i] = clampSample(s)
        }
        return out
    }

    private fun genHitImpact(): ShortArray {
        val dur = 0.11f
        val count = (SAMPLE_RATE * dur).toInt()
        val out = ShortArray(count)
        val rng = Random(202)
        var phase = 0f
        for (i in 0 until count) {
            val t = i.toFloat() / SAMPLE_RATE
            val env = exp(-t * 28f)
            val freq = 185f * exp(-t * 14f) + 46f
            phase += 2f * PI.toFloat() * freq / SAMPLE_RATE
            val crack = (rng.nextFloat() * 2f - 1f) * exp(-t * 55f) * 0.55f
            val thud = sin(phase) * 0.85f
            out[i] = clampSample((thud + crack) * env)
        }
        return out
    }

    private fun genShotPlasma(): ShortArray {
        val dur = 0.13f
        val count = (SAMPLE_RATE * dur).toInt()
        val out = ShortArray(count)
        val rng = Random(303)
        var phase = 0f
        for (i in 0 until count) {
            val t = i.toFloat() / SAMPLE_RATE
            val env = exp(-t * 22f)
            val freq = 1150f * exp(-t * 19f) + 170f
            phase += 2f * PI.toFloat() * freq / SAMPLE_RATE
            val snap = (rng.nextFloat() * 2f - 1f) * exp(-t * 70f) * 0.65f
            val body = (sin(phase) * 0.7f + sin(phase * 2f) * 0.25f)
            out[i] = clampSample((body + snap) * env)
        }
        return out
    }

    private fun genShotEnemy(): ShortArray {
        val dur = 0.12f
        val count = (SAMPLE_RATE * dur).toInt()
        val out = ShortArray(count)
        var phase = 0f
        for (i in 0 until count) {
            val t = i.toFloat() / SAMPLE_RATE
            val env = exp(-t * 24f)
            val freq = 620f - 410f * (t / dur)
            phase += 2f * PI.toFloat() * freq / SAMPLE_RATE
            val sq = if (sin(phase) >= 0f) 0.55f else -0.55f
            out[i] = clampSample(sq * env)
        }
        return out
    }

    private fun genDefenseShield(): ShortArray {
        val dur = 0.24f
        val count = (SAMPLE_RATE * dur).toInt()
        val out = ShortArray(count)
        for (i in 0 until count) {
            val t = i.toFloat() / SAMPLE_RATE
            val attack = (t / 0.015f).coerceAtMost(1f)
            val env = attack * exp(-t * 11f)
            val shimmer = sin(2f * PI.toFloat() * 520f * t) * 0.42f +
                sin(2f * PI.toFloat() * 780f * t) * 0.34f +
                sin(2f * PI.toFloat() * 1170f * t) * 0.22f
            val barrierBass = sin(2f * PI.toFloat() * 140f * t) * 0.40f * exp(-t * 16f)
            out[i] = clampSample((shimmer + barrierBass) * env)
        }
        return out
    }

    private fun genPowerSkill(): ShortArray {
        val dur = 0.36f
        val count = (SAMPLE_RATE * dur).toInt()
        val out = ShortArray(count)
        val rng = Random(505)
        var phaseUp = 0f
        var phaseSub = 0f
        for (i in 0 until count) {
            val t = i.toFloat() / SAMPLE_RATE
            val attack = (t / 0.025f).coerceAtMost(1f)
            val env = attack * exp(-t * 6.8f)
            val freqUp = 210f + 620f * (t / dur)
            val freqSub = 130f * exp(-t * 5f) + 45f
            phaseUp += 2f * PI.toFloat() * freqUp / SAMPLE_RATE
            phaseSub += 2f * PI.toFloat() * freqSub / SAMPLE_RATE
            val surge = sin(phaseUp) * 0.45f + sin(phaseUp * 1.5f) * 0.25f
            val sub = sin(phaseSub) * 0.58f
            val air = (rng.nextFloat() * 2f - 1f) * 0.18f * exp(-t * 9f)
            out[i] = clampSample((surge + sub + air) * env)
        }
        return out
    }

    private fun genJumpParkour(): ShortArray {
        val dur = 0.15f
        val count = (SAMPLE_RATE * dur).toInt()
        val out = ShortArray(count)
        val rng = Random(606)
        var phase = 0f
        var lp = 0f
        for (i in 0 until count) {
            val t = i.toFloat() / SAMPLE_RATE
            val env = (t / 0.015f).coerceAtMost(1f) * exp(-t * 15f)
            val freq = 210f + 380f * (t / dur)
            phase += 2f * PI.toFloat() * freq / SAMPLE_RATE
            val noise = rng.nextFloat() * 2f - 1f
            lp += (0.14f + 0.18f * (t / dur)) * (noise - lp)
            out[i] = clampSample((sin(phase) * 0.42f + lp * 0.68f) * env)
        }
        return out
    }

    private fun genLandImpact(): ShortArray {
        val dur = 0.12f
        val count = (SAMPLE_RATE * dur).toInt()
        val out = ShortArray(count)
        val rng = Random(707)
        var phase = 0f
        for (i in 0 until count) {
            val t = i.toFloat() / SAMPLE_RATE
            val env = exp(-t * 25f)
            val freq = 125f * exp(-t * 16f) + 38f
            phase += 2f * PI.toFloat() * freq / SAMPLE_RATE
            val grit = (rng.nextFloat() * 2f - 1f) * 0.35f * exp(-t * 45f)
            out[i] = clampSample((sin(phase) * 0.85f + grit) * env)
        }
        return out
    }

    private fun genFootstep(): ShortArray {
        val dur = 0.065f
        val count = (SAMPLE_RATE * dur).toInt()
        val out = ShortArray(count)
        val rng = Random(808)
        var phase = 0f
        var lp = 0f
        for (i in 0 until count) {
            val t = i.toFloat() / SAMPLE_RATE
            val env = exp(-t * 42f)
            val freq = 105f * exp(-t * 22f) + 42f
            phase += 2f * PI.toFloat() * freq / SAMPLE_RATE
            val noise = rng.nextFloat() * 2f - 1f
            lp += 0.25f * (noise - lp)
            out[i] = clampSample((sin(phase) * 0.65f + lp * 0.45f) * env)
        }
        return out
    }

    private fun genBossAlert(): ShortArray {
        val dur = 0.52f
        val count = (SAMPLE_RATE * dur).toInt()
        val out = ShortArray(count)
        for (i in 0 until count) {
            val t = i.toFloat() / SAMPLE_RATE
            val env = (t / 0.06f).coerceAtMost(1f) * exp(-t * 3.8f)
            val horn = sin(2f * PI.toFloat() * 110f * t) * 0.45f +
                sin(2f * PI.toFloat() * 114f * t) * 0.40f +
                sin(2f * PI.toFloat() * 55f * t) * 0.50f
            out[i] = clampSample(horn * env)
        }
        return out
    }

    private fun genCollectibleChime(): ShortArray {
        val dur = 0.28f
        val count = (SAMPLE_RATE * dur).toInt()
        val out = ShortArray(count)
        for (i in 0 until count) {
            val t = i.toFloat() / SAMPLE_RATE
            val noteFreq = when {
                t < 0.09f -> 523.25f
                t < 0.18f -> 659.25f
                else -> 1046.5f
            }
            val localT = t % 0.09f
            val env = (localT / 0.01f).coerceAtMost(1f) * exp(-t * 6.5f)
            val chime = sin(2f * PI.toFloat() * noteFreq * t) * 0.65f +
                sin(2f * PI.toFloat() * noteFreq * 2f * t) * 0.22f
            out[i] = clampSample(chime * env)
        }
        return out
    }

    private fun clampSample(v: Float): Short {
        return (v.coerceIn(-0.98f, 0.98f) * Short.MAX_VALUE).toInt().toShort()
    }

    private fun encodeWav16Mono(samples: ShortArray, sampleRate: Int): ByteArray {
        val dataSize = samples.size * 2
        val totalSize = 44 + dataSize
        val baos = ByteArrayOutputStream(totalSize)

        fun writeStr(s: String) {
            baos.write(s.toByteArray(Charsets.US_ASCII))
        }

        fun writeIntLE(v: Int) {
            baos.write(v and 0xFF)
            baos.write((v ushr 8) and 0xFF)
            baos.write((v ushr 16) and 0xFF)
            baos.write((v ushr 24) and 0xFF)
        }

        fun writeShortLE(v: Int) {
            baos.write(v and 0xFF)
            baos.write((v ushr 8) and 0xFF)
        }

        writeStr("RIFF")
        writeIntLE(36 + dataSize)
        writeStr("WAVE")
        writeStr("fmt ")
        writeIntLE(16) // PCM chunk size
        writeShortLE(1) // PCM format
        writeShortLE(1) // mono
        writeIntLE(sampleRate)
        writeIntLE(sampleRate * 2) // byte rate
        writeShortLE(2) // block align
        writeShortLE(16) // bits per sample
        writeStr("data")
        writeIntLE(dataSize)
        for (s in samples) {
            writeShortLE(s.toInt())
        }
        return baos.toByteArray()
    }
}

/**
 * Hardware-mixed SoundPool SFX engine backed by procedural PCM WAV files cached on disk.
 */
class SoundEffectsEngine(context: Context) {
    private val cacheDir = File(context.cacheDir, "neonfall_sfx").apply { mkdirs() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sampleIds = ConcurrentHashMap<SoundEffect, Int>()
    private val loadedIds = ConcurrentHashMap<Int, Boolean>()
    private val lastPlayedMs = ConcurrentHashMap<SoundEffect, Long>()
    private val rng = Random(System.currentTimeMillis())

    @Volatile
    var enabled: Boolean = true

    private var soundPool: SoundPool? = runCatching {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        SoundPool.Builder()
            .setMaxStreams(12)
            .setAudioAttributes(attrs)
            .build().apply {
                setOnLoadCompleteListener { _, sampleId, status ->
                    if (status == 0) {
                        loadedIds[sampleId] = true
                    }
                }
            }
    }.getOrNull()

    init {
        scope.launch {
            val pool = soundPool ?: return@launch
            for (effect in SoundEffect.entries) {
                runCatching {
                    val file = File(cacheDir, "${effect.name.lowercase()}.wav")
                    val bytes = ProceduralWaveSynth.synthesizeWav(effect)
                    FileOutputStream(file).use { it.write(bytes) }
                    val id = pool.load(file.absolutePath, 1)
                    if (id > 0) {
                        sampleIds[effect] = id
                    }
                }
            }
        }
    }

    fun play(effect: SoundEffect) {
        if (!enabled) return
        val pool = soundPool ?: return
        val now = System.currentTimeMillis()
        val prev = lastPlayedMs[effect] ?: 0L
        if (now - prev < effect.minIntervalMs) return
        lastPlayedMs[effect] = now

        val sampleId = sampleIds[effect] ?: return
        if (loadedIds[sampleId] != true) return

        val rate = if (effect.pitchJitter > 0f) {
            1f + (rng.nextFloat() * 2f - 1f) * effect.pitchJitter
        } else {
            1f
        }
        runCatching {
            pool.play(sampleId, effect.defaultVolume, effect.defaultVolume, 1, 0, rate.coerceIn(0.75f, 1.35f))
        }
    }

    fun release() {
        scope.cancel()
        runCatching { soundPool?.release() }
        soundPool = null
        sampleIds.clear()
        loadedIds.clear()
    }
}
