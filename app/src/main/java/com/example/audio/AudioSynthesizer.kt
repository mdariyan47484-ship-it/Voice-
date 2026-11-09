package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.model.SynthPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

object AudioSynthesizer {
    const val SAMPLE_RATE = 44100

    private val chordProgressionLofi = listOf(
        listOf(261.63, 329.63, 392.00, 493.88), // Cmaj7 (C4, E4, G4, B4)
        listOf(220.00, 261.63, 329.63, 392.00), // Am7 (A3, C4, E4, G4)
        listOf(174.61, 220.00, 261.63, 329.63), // Fmaj7 (F3, A3, C4, E4)
        listOf(196.00, 246.94, 293.66, 349.23)  // G7 (G3, B3, D4, F4)
    )

    private val chordProgressionAcoustic = listOf(
        listOf(196.00, 246.94, 293.66, 392.00), // G
        listOf(146.83, 220.00, 293.66, 369.99), // D
        listOf(164.81, 196.00, 246.94, 329.63), // Em
        listOf(174.61, 220.00, 261.63, 349.23)  // C
    )

    private val chordProgressionUpbeat = listOf(
        listOf(293.66, 369.99, 440.00), // D
        listOf(220.00, 277.18, 329.63), // A
        listOf(246.94, 293.66, 369.99), // Bm
        listOf(196.00, 246.94, 293.66)  // G
    )

    /**
     * Synthesizes audio samples for a given duration into a WAV file.
     * This creates a pure 100% music audio file with ZERO microphone vocal noise.
     */
    suspend fun renderPresetToWav(
        preset: SynthPreset,
        durationSeconds: Int,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val totalSamples = SAMPLE_RATE * durationSeconds
            val bytesPerSample = 2 // 16-bit mono
            val dataSize = totalSamples * bytesPerSample

            val chords = when (preset) {
                SynthPreset.LOFI_CHILL -> chordProgressionLofi
                SynthPreset.ACOUSTIC_GUITAR -> chordProgressionAcoustic
                SynthPreset.UPBEAT_GROOVE -> chordProgressionUpbeat
            }

            FileOutputStream(outputFile).use { fos ->
                writeWavHeader(fos, SAMPLE_RATE, 1, 16, dataSize)

                val buffer = ByteBuffer.allocate(SAMPLE_RATE * 2).order(ByteOrder.LITTLE_ENDIAN)
                val chordDurationSamples = SAMPLE_RATE * 2 // 2 seconds per chord
                var currentSample = 0

                while (currentSample < totalSamples) {
                    buffer.clear()
                    val chunkSize = minOf(SAMPLE_RATE, totalSamples - currentSample)

                    for (i in 0 until chunkSize) {
                        val globalIdx = currentSample + i
                        val chordIdx = (globalIdx / chordDurationSamples) % chords.size
                        val chordNotes = chords[chordIdx]
                        val subProgress = (globalIdx % chordDurationSamples).toDouble() / chordDurationSamples

                        // Soft envelope
                        val envelope = if (subProgress < 0.05) {
                            subProgress / 0.05
                        } else if (subProgress > 0.85) {
                            (1.0 - subProgress) / 0.15
                        } else {
                            1.0
                        }

                        // Harmonic synthesis of chord notes
                        var sampleVal = 0.0
                        for ((noteIdx, freq) in chordNotes.withIndex()) {
                            val t = globalIdx.toDouble() / SAMPLE_RATE
                            val harmonic = sin(2.0 * PI * freq * t)
                            val overtone = sin(4.0 * PI * freq * t) * 0.25
                            val weight = when (preset) {
                                SynthPreset.LOFI_CHILL -> if (noteIdx == 0) 0.4 else 0.2
                                SynthPreset.ACOUSTIC_GUITAR -> 0.25
                                SynthPreset.UPBEAT_GROOVE -> 0.3
                            }
                            sampleVal += (harmonic + overtone) * weight
                        }

                        // Add rhythmic bass & lo-fi beat element for chill / upbeat
                        if (preset == SynthPreset.LOFI_CHILL || preset == SynthPreset.UPBEAT_GROOVE) {
                            val beatSample = globalIdx % (SAMPLE_RATE / 2) // 120 bpm pulse
                            val beatT = beatSample.toDouble() / SAMPLE_RATE
                            if (beatT < 0.1) {
                                val kickFreq = 120.0 * (1.0 - beatT / 0.1) + 45.0
                                val kick = sin(2.0 * PI * kickFreq * beatT) * (1.0 - beatT / 0.1) * 0.35
                                sampleVal += kick
                            }
                        }

                        sampleVal = (sampleVal * envelope).coerceIn(-1.0, 1.0)
                        val pcm16 = (sampleVal * 32767).toInt().toShort()
                        buffer.putShort(pcm16)
                    }

                    fos.write(buffer.array(), 0, chunkSize * 2)
                    currentSample += chunkSize
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
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

        // RIFF header
        bb.put('R'.code.toByte())
        bb.put('I'.code.toByte())
        bb.put('F'.code.toByte())
        bb.put('F'.code.toByte())
        bb.putInt(totalDataLen)
        bb.put('W'.code.toByte())
        bb.put('A'.code.toByte())
        bb.put('V'.code.toByte())
        bb.put('E'.code.toByte())

        // fmt chunk
        bb.put('f'.code.toByte())
        bb.put('m'.code.toByte())
        bb.put('t'.code.toByte())
        bb.put(' '.code.toByte())
        bb.putInt(16) // SubChunk1Size (16 for PCM)
        bb.putShort(1) // AudioFormat (1 for PCM)
        bb.putShort(channels.toShort())
        bb.putInt(sampleRate)
        bb.putInt(byteRate)
        bb.putShort(blockAlign)
        bb.putShort(bitsPerSample.toShort())

        // data chunk
        bb.put('d'.code.toByte())
        bb.put('a'.code.toByte())
        bb.put('t'.code.toByte())
        bb.put('a'.code.toByte())
        bb.putInt(audioDataSize)

        out.write(header)
    }

    /**
     * Creates a looping audio player for live playback of synthetic notes
     */
    fun createLoopingAudioTrack(preset: SynthPreset): AudioTrack {
        val minBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBufferSize, SAMPLE_RATE * 2) // 1 second buffer

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        return track
    }
}
