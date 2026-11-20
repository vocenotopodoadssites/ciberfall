package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

enum class SoundEffect {
    MELEE_HIT,  // Golpes e cortes
    SHOOT,      // Tiros e disparos de plasma
    DEFENSE,    // Defesa e absorção de escudo
    POWER,      // Ativação de poder e habilidades
    JUMP,       // Pulos e acrobacias parkour
    STEP        // Passos no concreto molhado
}

/**
 * Real-time procedural PCM audio mixer for combat, defense, powers, jumps, and footsteps.
 * Synthesizes cyberpunk sound effects in-memory with zero external asset dependencies.
 */
class NeonfallAudioEngine {
    private val sampleRate = 22050
    private val bank: Map<SoundEffect, ShortArray> = mapOf(
        SoundEffect.MELEE_HIT to synthMeleeHit(),
        SoundEffect.SHOOT to synthShoot(),
        SoundEffect.DEFENSE to synthDefense(),
        SoundEffect.POWER to synthPower(),
        SoundEffect.JUMP to synthJump(),
        SoundEffect.STEP to synthStep()
    )

    private class Voice(
        val buffer: ShortArray,
        var pos: Float = 0f,
        val step: Float = 1f,
        val gain: Float = 1f
    )

    private val voices = CopyOnWriteArrayList<Voice>()
    @Volatile var enabled: Boolean = true
    @Volatile private var released = false
    private var audioTrack: AudioTrack? = null
    private var mixerThread: Thread? = null

    init {
        startMixerIfSupported()
    }

    private fun startMixerIfSupported() {
        runCatching {
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuf <= 0) return
            val bufBytes = maxOf(minBuf, 1024 * 2)
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            track.play()
            audioTrack = track

            val thread = Thread({
                val chunkSize = 512
                val mixOut = ShortArray(chunkSize)
                while (!released) {
                    if (voices.isEmpty() || !enabled) {
                        try {
                            Thread.sleep(16)
                        } catch (_: InterruptedException) {
                            break
                        }
                        continue
                    }

                    mixOut.fill(0)
                    val toRemove = ArrayList<Voice>()
                    for (i in 0 until chunkSize) {
                        var sum = 0f
                        for (v in voices) {
                            val idx = v.pos.toInt()
                            if (idx >= v.buffer.size) {
                                toRemove.add(v)
                                continue
                            }
                            sum += (v.buffer[idx] / 32767f) * v.gain
                            v.pos += v.step
                        }
                        if (toRemove.isNotEmpty()) {
                            voices.removeAll(toRemove.toSet())
                            toRemove.clear()
                        }
                        val clamped = (sum * 0.72f).coerceIn(-0.98f, 0.98f)
                        mixOut[i] = (clamped * 32767f).toInt().toShort()
                    }
                    runCatching {
                        track.write(mixOut, 0, chunkSize)
                    }.onFailure {
                        break
                    }
                }
            }, "NeonfallAudioMixer").apply {
                isDaemon = true
                priority = Thread.MAX_PRIORITY - 1
                start()
            }
            mixerThread = thread
        }
    }

    fun play(effect: SoundEffect, volume: Float = 1f, pitch: Float = 1f) {
        if (!enabled || released) return
        val buf = bank[effect] ?: return
        if (voices.size >= 14) {
            voices.removeAt(0)
        }
        voices.add(
            Voice(
                buffer = buf,
                pos = 0f,
                step = pitch.coerceIn(0.6f, 1.8f),
                gain = volume.coerceIn(0.05f, 1.25f)
            )
        )
    }

    fun clearPending() {
        voices.clear()
    }

    fun release() {
        released = true
        voices.clear()
        runCatching { mixerThread?.interrupt() }
        runCatching {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.release()
        }
        audioTrack = null
    }

    private fun synthMeleeHit(): ShortArray {
        val len = (sampleRate * 0.13f).toInt()
        val rng = Random(101)
        var phase = 0.0
        return ShortArray(len) { i ->
            val t = i.toFloat() / sampleRate
            val env = exp(-t * 26.0).toFloat()
            val freq = 260.0 - 180.0 * (i.toFloat() / len)
            phase += 2.0 * PI * freq / sampleRate
            val punch = sin(phase).toFloat() * 0.65f
            val slashNoise = (rng.nextFloat() * 2f - 1f) * exp(-t * 34.0).toFloat() * 0.55f
            val metallic = sin(2.0 * PI * 920.0 * t).toFloat() * exp(-t * 42.0).toFloat() * 0.25f
            val sample = ((punch + slashNoise + metallic) * env * 0.85f).coerceIn(-1f, 1f)
            (sample * 32767f).toInt().toShort()
        }
    }

    private fun synthShoot(): ShortArray {
        val len = (sampleRate * 0.10f).toInt()
        val rng = Random(202)
        var phase = 0.0
        return ShortArray(len) { i ->
            val t = i.toFloat() / sampleRate
            val progress = i.toFloat() / len
            val env = exp(-t * 32.0).toFloat()
            val freq = 1280.0 * exp(-progress * 2.4) + 170.0
            phase += 2.0 * PI * freq / sampleRate
            val laser = sin(phase).toFloat() * 0.72f
            val transient = (rng.nextFloat() * 2f - 1f) * exp(-t * 95.0).toFloat() * 0.45f
            val sample = ((laser + transient) * env * 0.78f).coerceIn(-1f, 1f)
            (sample * 32767f).toInt().toShort()
        }
    }

    private fun synthDefense(): ShortArray {
        val len = (sampleRate * 0.17f).toInt()
        return ShortArray(len) { i ->
            val t = i.toFloat() / sampleRate
            val env = exp(-t * 17.0).toFloat()
            val c1 = sin(2.0 * PI * 540.0 * t).toFloat()
            val c2 = sin(2.0 * PI * 810.0 * t).toFloat()
            val c3 = sin(2.0 * PI * 1080.0 * t).toFloat()
            val shimmer = sin(2.0 * PI * 28.0 * t).toFloat() * 0.2f + 0.8f
            val sample = ((c1 * 0.45f + c2 * 0.35f + c3 * 0.25f) * env * shimmer * 0.72f).coerceIn(-1f, 1f)
            (sample * 32767f).toInt().toShort()
        }
    }

    private fun synthPower(): ShortArray {
        val len = (sampleRate * 0.34f).toInt()
        val rng = Random(303)
        var phase = 0.0
        return ShortArray(len) { i ->
            val t = i.toFloat() / sampleRate
            val progress = i.toFloat() / len
            val attack = (t / 0.035f).coerceIn(0f, 1f)
            val env = attack * exp(-(t - 0.03f).coerceAtLeast(0f) * 7.5).toFloat()
            val sweepFreq = 110.0 + 460.0 * (1.0 - exp(-progress * 3.5))
            phase += 2.0 * PI * sweepFreq / sampleRate
            val sub = sin(phase).toFloat() * 0.55f
            val harmonic = sin(phase * 2.02).toFloat() * 0.30f
            val air = (rng.nextFloat() * 2f - 1f) * 0.22f * exp(-t * 9.0).toFloat()
            val sample = ((sub + harmonic + air) * env * 0.88f).coerceIn(-1f, 1f)
            (sample * 32767f).toInt().toShort()
        }
    }

    private fun synthJump(): ShortArray {
        val len = (sampleRate * 0.14f).toInt()
        val rng = Random(404)
        var phase = 0.0
        return ShortArray(len) { i ->
            val t = i.toFloat() / sampleRate
            val progress = i.toFloat() / len
            val env = (t / 0.015f).coerceIn(0f, 1f) * exp(-t * 18.0).toFloat()
            val freq = 190.0 + 420.0 * progress
            phase += 2.0 * PI * freq / sampleRate
            val whoosh = (rng.nextFloat() * 2f - 1f) * 0.25f * exp(-t * 22.0).toFloat()
            val tone = sin(phase).toFloat() * 0.60f
            val sample = ((tone + whoosh) * env * 0.72f).coerceIn(-1f, 1f)
            (sample * 32767f).toInt().toShort()
        }
    }

    private fun synthStep(): ShortArray {
        val len = (sampleRate * 0.055f).toInt()
        val rng = Random(505)
        var phase = 0.0
        return ShortArray(len) { i ->
            val t = i.toFloat() / sampleRate
            val env = exp(-t * 58.0).toFloat()
            val freq = 135.0 - 75.0 * (i.toFloat() / len)
            phase += 2.0 * PI * freq / sampleRate
            val thump = sin(phase).toFloat() * 0.65f
            val grit = (rng.nextFloat() * 2f - 1f) * 0.40f * exp(-t * 90.0).toFloat()
            val sample = ((thump + grit) * env * 0.55f).coerceIn(-1f, 1f)
            (sample * 32767f).toInt().toShort()
        }
    }
}
