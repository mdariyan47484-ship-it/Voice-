package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.model.PrankVoiceClip
import com.example.model.SoundEffectType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random
import kotlin.math.PI
import kotlin.math.sin

class PrankAudioPlayer(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var localPlayer: MediaPlayer? = null
    private var injectedPlayer: MediaPlayer? = null

    var myMonitorVolume: Float = 0.5f
    var theirStreamVolume: Float = 1.0f

    suspend fun playClip(clip: PrankVoiceClip, onComplete: () -> Unit = {}) = withContext(Dispatchers.IO) {
        stopAll()

        // Unmute microphone if muted by system
        try {
            audioManager.isMicrophoneMute = false
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Get or generate audio file for this clip
        val audioFile = getOrCreateClipFile(clip)

        withContext(Dispatchers.Main) {
            try {
                // 1. Local Player (For user to hear based on myMonitorVolume slider)
                if (myMonitorVolume > 0.01f) {
                    localPlayer = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .build()
                        )
                        setDataSource(audioFile.absolutePath)
                        setVolume(myMonitorVolume, myMonitorVolume)
                        setOnCompletionListener {
                            onComplete()
                        }
                        prepare()
                        start()
                    }
                }

                // 2. Injected Call / Mic Stream Player (100% full volume for other callers/receivers)
                if (theirStreamVolume > 0.05f) {
                    injectedPlayer = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                                .build()
                        )
                        setDataSource(audioFile.absolutePath)
                        setVolume(theirStreamVolume, theirStreamVolume)
                        setOnCompletionListener {
                            onComplete()
                        }
                        prepare()
                        start()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onComplete()
            }
        }
    }

    fun stopAll() {
        try {
            localPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
            localPlayer = null

            injectedPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
            injectedPlayer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun getOrCreateClipFile(clip: PrankVoiceClip): File = withContext(Dispatchers.IO) {
        val pranksDir = File(context.filesDir, "pranks").apply { mkdirs() }
        val targetFile = File(pranksDir, "clip_${clip.id}.wav")

        if (targetFile.exists() && targetFile.length() > 100) {
            return@withContext targetFile
        }

        if (clip.isCustom && clip.customAudioUri != null) {
            // Copy custom user file
            try {
                context.contentResolver.openInputStream(clip.customAudioUri)?.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                return@withContext targetFile
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Synthesize dynamic procedural audio for preloaded effects
        generateProceduralAudio(clip.soundEffectType, targetFile)
        return@withContext targetFile
    }

    fun getShareableUri(clipFile: File): Uri {
        val authority = "${context.packageName}.provider"
        return FileProvider.getUriForFile(context, authority, clipFile)
    }

    private fun generateProceduralAudio(type: SoundEffectType, file: File) {
        val sampleRate = 44100
        val durationSeconds = when (type) {
            SoundEffectType.AIR_HORN -> 3
            SoundEffectType.POLICE_SIREN -> 6
            SoundEffectType.BOMB_EXPLOSION -> 4
            SoundEffectType.JUMP_SCARE -> 3
            SoundEffectType.RICKROLL_TUNE -> 8
            SoundEffectType.GAME_OVER -> 4
            SoundEffectType.TRAFFIC_JAM -> 6
            else -> 4
        }

        val totalSamples = sampleRate * durationSeconds
        val dataSize = totalSamples * 2
        val random = Random()

        FileOutputStream(file).use { fos ->
            writeWavHeader(fos, sampleRate, 1, 16, dataSize)

            val buffer = ByteBuffer.allocate(sampleRate * 2).order(ByteOrder.LITTLE_ENDIAN)
            var currentSample = 0

            while (currentSample < totalSamples) {
                buffer.clear()
                val chunkSize = minOf(sampleRate, totalSamples - currentSample)

                for (i in 0 until chunkSize) {
                    val globalIdx = currentSample + i
                    val t = globalIdx.toDouble() / sampleRate
                    var sampleVal = 0.0

                    when (type) {
                        SoundEffectType.AIR_HORN -> {
                            // Triple airhorn blast (0..0.8s, 1.0..1.8s, 2.0..2.8s)
                            val blastPhase = (t % 1.0)
                            if (blastPhase < 0.75) {
                                val f1 = 466.16 // Bb4
                                val f2 = 587.33 // D5
                                val f3 = 700.00
                                val tone = sin(2 * PI * f1 * t) * 0.4 + sin(2 * PI * f2 * t) * 0.35 + sin(2 * PI * f3 * t) * 0.25
                                sampleVal = tone
                            }
                        }

                        SoundEffectType.POLICE_SIREN -> {
                            // Two-tone wail (600Hz to 1200Hz)
                            val freq = 850.0 + 350.0 * sin(2 * PI * 0.7 * t)
                            sampleVal = sin(2 * PI * freq * t) * 0.8
                        }

                        SoundEffectType.FEMALE_VOICE_HELLO, SoundEffectType.FEMALE_VOICE_CRY -> {
                            // Formant vocal simulation (F0 ~ 240Hz, F1 ~ 600Hz, F2 ~ 1800Hz)
                            val f0 = 240.0 + 30.0 * sin(2 * PI * 2.0 * t)
                            val vocal = sin(2 * PI * f0 * t) * 0.5 + sin(2 * PI * (f0 * 2.5) * t) * 0.3 + sin(2 * PI * (f0 * 7.5) * t) * 0.2
                            val envelope = (sin(PI * (t % 1.5) / 1.5)).coerceIn(0.0, 1.0)
                            sampleVal = vocal * envelope
                        }

                        SoundEffectType.GHOST_WHISPER, SoundEffectType.JUMP_SCARE -> {
                            // Filtered noise + eerie descending/ascending scream
                            val noise = (random.nextDouble() * 2.0 - 1.0) * 0.4
                            val screamFreq = if (type == SoundEffectType.JUMP_SCARE) 1200.0 + 800.0 * sin(2 * PI * 8.0 * t) else 440.0
                            val scream = sin(2 * PI * screamFreq * t) * 0.5
                            val env = if (type == SoundEffectType.JUMP_SCARE) (1.0 - t / durationSeconds).coerceIn(0.0, 1.0) else 0.7
                            sampleVal = (noise + scream) * env
                        }

                        SoundEffectType.ROBOT_CYBER -> {
                            // Ring-modulated digital synth
                            val carrier = sin(2 * PI * 180.0 * t)
                            val modulator = sin(2 * PI * 40.0 * t)
                            sampleVal = carrier * modulator * 0.85
                        }

                        SoundEffectType.RICKROLL_TUNE -> {
                            // 8-bit recognizable chime melody notes (F, G, Ab, Bb, C)
                            val noteIdx = ((t * 4.0).toInt()) % 8
                            val notes = listOf(349.23, 392.00, 440.00, 466.16, 523.25, 466.16, 440.00, 392.00)
                            val freq = notes[noteIdx]
                            sampleVal = (sin(2 * PI * freq * t) + 0.3 * sin(2 * PI * freq * 2 * t)) * 0.7
                        }

                        SoundEffectType.BOMB_EXPLOSION -> {
                            // Sub-bass thump + decayed white noise
                            val env = (1.0 - (t / durationSeconds)).coerceIn(0.0, 1.0)
                            val subBass = sin(2 * PI * (65.0 * env + 30.0) * t) * 0.6
                            val noise = (random.nextDouble() * 2.0 - 1.0) * 0.4
                            sampleVal = (subBass + noise) * (env * env)
                        }

                        SoundEffectType.LAUGH_TRACK -> {
                            // Rhythmic staccato laughs
                            val pulse = (t * 5.0) % 1.0
                            if (pulse < 0.6) {
                                val freq = 360.0 + 80.0 * sin(2 * PI * 12.0 * t)
                                sampleVal = sin(2 * PI * freq * t) * 0.7 * (1.0 - pulse / 0.6)
                            }
                        }

                        SoundEffectType.BABY_CRY -> {
                            // High-pitched wail (550Hz ~ 800Hz with vibrato)
                            val freq = 650.0 + 150.0 * sin(2 * PI * 3.5 * t)
                            sampleVal = sin(2 * PI * freq * t) * 0.8
                        }

                        SoundEffectType.DOG_BARK -> {
                            // Short 0.25s bark bursts
                            val barkTime = t % 0.8
                            if (barkTime < 0.25) {
                                val freq = 280.0 * (1.0 - barkTime / 0.25) + 90.0
                                val noise = (random.nextDouble() * 2.0 - 1.0) * 0.2
                                sampleVal = (sin(2 * PI * freq * t) + noise) * 0.85
                            }
                        }

                        SoundEffectType.CAT_MEOW -> {
                            // Sweeping formant: 400Hz up to 900Hz then down to 350Hz
                            val meowPhase = (t % 1.8) / 1.8
                            val freq = if (meowPhase < 0.5) 450.0 + 500.0 * (meowPhase / 0.5) else 950.0 - 600.0 * ((meowPhase - 0.5) / 0.5)
                            sampleVal = sin(2 * PI * freq * t) * 0.75
                        }

                        SoundEffectType.CRICKETS_AWKWARD -> {
                            // High chirps at 4500Hz
                            val chirpTime = t % 0.5
                            if (chirpTime < 0.15) {
                                sampleVal = sin(2 * PI * 4600.0 * t) * 0.5 * sin(2 * PI * 40.0 * t)
                            }
                        }

                        SoundEffectType.RADIO_DISPATCH -> {
                            // Bandpass walkie-talkie burst + squelch tone
                            val squelch = if (t < 0.25 || t > durationSeconds - 0.25) sin(2 * PI * 1200.0 * t) * 0.5 else 0.0
                            val noise = (random.nextDouble() * 2.0 - 1.0) * 0.3
                            sampleVal = squelch + noise
                        }

                        SoundEffectType.GAME_OVER -> {
                            // Retro 8-bit descending arpeggio (C, G, Eb, C)
                            val step = ((t * 4.0).toInt()).coerceIn(0, 7)
                            val notes = listOf(523.25, 466.16, 415.30, 369.99, 329.63, 293.66, 261.63, 196.00)
                            val freq = notes[step]
                            sampleVal = sin(2 * PI * freq * t) * 0.75
                        }

                        SoundEffectType.DRUM_RIMSHOT -> {
                            // Ba-dum... TSS
                            if (t < 0.2) {
                                sampleVal = sin(2 * PI * 140.0 * t) * 0.7
                            } else if (t in 0.25..0.45) {
                                sampleVal = sin(2 * PI * 180.0 * t) * 0.7
                            } else if (t in 0.5..1.8) {
                                val decay = (1.0 - (t - 0.5) / 1.3).coerceIn(0.0, 1.0)
                                val cymbal = (random.nextDouble() * 2.0 - 1.0) * decay
                                sampleVal = cymbal * 0.8
                            }
                        }

                        SoundEffectType.CARTOON_BOING -> {
                            // Exponential pitch drop / spring boing
                            val springFreq = 160.0 + 350.0 * sin(2 * PI * 18.0 * t) * (1.0 - t / durationSeconds)
                            sampleVal = sin(2 * PI * springFreq * t) * 0.75
                        }

                        SoundEffectType.LOUD_SNORE -> {
                            // Heavy raspy breathing cycle (inhale rasp 1s, pause 0.5s)
                            val cycle = t % 2.0
                            if (cycle < 1.2) {
                                val rasp = (random.nextDouble() * 2.0 - 1.0) * 0.5
                                val low = sin(2 * PI * 75.0 * t) * 0.5
                                sampleVal = (rasp + low) * sin(PI * cycle / 1.2)
                            }
                        }

                        SoundEffectType.TRAFFIC_JAM -> {
                            // Car horns (dual 440Hz + 520Hz honks)
                            val hornCycle = t % 1.5
                            if (hornCycle < 0.6) {
                                sampleVal = (sin(2 * PI * 420.0 * t) * 0.5 + sin(2 * PI * 510.0 * t) * 0.4)
                            }
                        }

                        else -> {
                            sampleVal = sin(2 * PI * 440.0 * t) * 0.5
                        }
                    }

                    val pcm16 = (sampleVal.coerceIn(-1.0, 1.0) * 32767).toInt().toShort()
                    buffer.putShort(pcm16)
                }

                fos.write(buffer.array(), 0, chunkSize * 2)
                currentSample += chunkSize
            }
        }
    }

    private fun writeWavHeader(
        out: FileOutputStream,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int,
        audioDataSize: Int
    ) {
        val totalDataLen = audioDataSize + 36
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = (channels * bitsPerSample / 8).toShort()

        val header = ByteArray(44)
        val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

        bb.put('R'.code.toByte())
        bb.put('I'.code.toByte())
        bb.put('F'.code.toByte())
        bb.put('F'.code.toByte())
        bb.putInt(totalDataLen)
        bb.put('W'.code.toByte())
        bb.put('A'.code.toByte())
        bb.put('V'.code.toByte())
        bb.put('E'.code.toByte())

        bb.put('f'.code.toByte())
        bb.put('m'.code.toByte())
        bb.put('t'.code.toByte())
        bb.put(' '.code.toByte())
        bb.putInt(16)
        bb.putShort(1)
        bb.putShort(channels.toShort())
        bb.putInt(sampleRate)
        bb.putInt(byteRate)
        bb.putShort(blockAlign)
        bb.putShort(bitsPerSample.toShort())

        bb.put('d'.code.toByte())
        bb.put('a'.code.toByte())
        bb.put('t'.code.toByte())
        bb.put('a'.code.toByte())
        bb.putInt(audioDataSize)

        out.write(header)
    }
}
