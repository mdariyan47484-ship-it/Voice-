package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.core.content.FileProvider
import com.example.model.AudioTrackItem
import com.example.model.RecordedVoiceClip
import com.example.model.SynthPreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.sin

class MusicMicEngine(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val scope = CoroutineScope(Dispatchers.Default)

    // Current State Flow
    private val _isMusicOnlyActive = MutableStateFlow(false)
    val isMusicOnlyActive: StateFlow<Boolean> = _isMusicOnlyActive.asStateFlow()

    private val _isCallInjectionActive = MutableStateFlow(true) // Call & VoIP stream injection
    val isCallInjectionActive: StateFlow<Boolean> = _isCallInjectionActive.asStateFlow()

    private val _isHardwareMicMuted = MutableStateFlow(false)
    val isHardwareMicMuted: StateFlow<Boolean> = _isHardwareMicMuted.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingDurationMs = MutableStateFlow(0L)
    val recordingDurationMs: StateFlow<Long> = _recordingDurationMs.asStateFlow()

    private val _musicLevel = MutableStateFlow(0f)
    val musicLevel: StateFlow<Float> = _musicLevel.asStateFlow()

    private val _rawMicDetectedLevel = MutableStateFlow(0f) // Voice in room
    val rawMicDetectedLevel: StateFlow<Float> = _rawMicDetectedLevel.asStateFlow()

    private val _transmittedVoiceLevel = MutableStateFlow(0f) // Always 0% when active
    val transmittedVoiceLevel: StateFlow<Float> = _transmittedVoiceLevel.asStateFlow()

    private val _selectedTrack = MutableStateFlow(
        AudioTrackItem(
            id = "preset_lofi",
            title = "লো-ফাই চিল সুর (Lo-Fi Chill)",
            subtitle = "সিস্টেম জেনারেটেড ক্লিন মিউজিক",
            synthPreset = SynthPreset.LOFI_CHILL
        )
    )
    val selectedTrack: StateFlow<AudioTrackItem> = _selectedTrack.asStateFlow()

    private val _savedClips = MutableStateFlow<List<RecordedVoiceClip>>(emptyList())
    val savedClips: StateFlow<List<RecordedVoiceClip>> = _savedClips.asStateFlow()

    private val _previewPlayingClipId = MutableStateFlow<String?>(null)
    val previewPlayingClipId: StateFlow<String?> = _previewPlayingClipId.asStateFlow()

    // Playback media players
    private var backgroundMediaPlayer: MediaPlayer? = null
    private var previewMediaPlayer: MediaPlayer? = null
    private var synthPlaybackJob: Job? = null
    private var recordingJob: Job? = null
    private var visualizerJob: Job? = null

    private var recordingStartTime = 0L
    private var currentRecordingFile: File? = null

    init {
        loadSavedClips()
        startVisualizerLoop()
    }

    fun setMusicOnlyActive(active: Boolean) {
        _isMusicOnlyActive.value = active
        if (active) {
            applyMicMuteAndCallRouting(true)
            requestAudioFocus()
            startMusicPlayback()
        } else {
            applyMicMuteAndCallRouting(false)
            stopMusicPlayback()
            if (_isRecording.value) {
                stopRecording()
            }
        }
    }

    fun setCallInjectionActive(active: Boolean) {
        _isCallInjectionActive.value = active
        if (_isMusicOnlyActive.value) {
            applyMicMuteAndCallRouting(true)
            // Restart playback with updated audio attributes
            stopMusicPlayback()
            startMusicPlayback()
        }
    }

    private fun applyMicMuteAndCallRouting(enable: Boolean) {
        try {
            if (enable) {
                // 1. Mute physical microphone so human vocal speech is silenced
                audioManager.isMicrophoneMute = true
                _isHardwareMicMuted.value = true

                // 2. If call injection is enabled, switch audio mode to communication
                if (_isCallInjectionActive.value) {
                    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                } else {
                    audioManager.mode = AudioManager.MODE_NORMAL
                }
            } else {
                // Restore physical microphone and normal audio mode
                audioManager.isMicrophoneMute = false
                audioManager.mode = AudioManager.MODE_NORMAL
                _isHardwareMicMuted.value = false
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setSelectedTrack(track: AudioTrackItem) {
        val wasActive = _isMusicOnlyActive.value
        if (wasActive) {
            stopMusicPlayback()
        }
        _selectedTrack.value = track
        if (wasActive) {
            startMusicPlayback()
        }
    }

    private fun startMusicPlayback() {
        val track = _selectedTrack.value
        try {
            val usageType = if (_isCallInjectionActive.value) {
                AudioAttributes.USAGE_VOICE_COMMUNICATION
            } else {
                AudioAttributes.USAGE_MEDIA
            }

            if (track.uri != null) {
                backgroundMediaPlayer?.release()
                backgroundMediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(usageType)
                            .build()
                    )
                    setDataSource(context, track.uri)
                    isLooping = true
                    prepare()
                    start()
                }
            } else if (track.synthPreset != null) {
                // Synthesizer playback loop
                startSynthPlayback(track.synthPreset, usageType)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startSynthPlayback(preset: SynthPreset, usageType: Int) {
        stopSynthPlayback()
        synthPlaybackJob = scope.launch(Dispatchers.IO) {
            try {
                val audioTrack = AudioSynthesizer.createLoopingAudioTrack(preset)
                audioTrack.play()

                val buffer = ShortArray(2048)
                var sampleCounter = 0L

                val chords = when (preset) {
                    SynthPreset.LOFI_CHILL -> listOf(
                        listOf(261.63, 329.63, 392.00),
                        listOf(220.00, 261.63, 329.63),
                        listOf(174.61, 220.00, 261.63),
                        listOf(196.00, 246.94, 293.66)
                    )
                    SynthPreset.ACOUSTIC_GUITAR -> listOf(
                        listOf(196.00, 246.94, 293.66),
                        listOf(146.83, 220.00, 293.66),
                        listOf(164.81, 196.00, 246.94)
                    )
                    SynthPreset.UPBEAT_GROOVE -> listOf(
                        listOf(293.66, 369.99, 440.00),
                        listOf(220.00, 277.18, 329.63),
                        listOf(196.00, 246.94, 293.66)
                    )
                }

                val chordDuration = AudioSynthesizer.SAMPLE_RATE * 2

                while (isActive && _isMusicOnlyActive.value) {
                    val currentChord = chords[((sampleCounter / chordDuration) % chords.size).toInt()]
                    for (i in buffer.indices) {
                        var sum = 0.0
                        for (f in currentChord) {
                            sum += sin(2.0 * Math.PI * f * (sampleCounter.toDouble() / AudioSynthesizer.SAMPLE_RATE))
                        }
                        sum = (sum / currentChord.size).coerceIn(-1.0, 1.0)
                        buffer[i] = (sum * 22000).toInt().toShort()
                        sampleCounter++
                    }
                    audioTrack.write(buffer, 0, buffer.size)
                }

                audioTrack.stop()
                audioTrack.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun stopSynthPlayback() {
        synthPlaybackJob?.cancel()
        synthPlaybackJob = null
    }

    private fun stopMusicPlayback() {
        stopSynthPlayback()
        try {
            backgroundMediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            backgroundMediaPlayer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun startRecording() {
        if (_isRecording.value) return

        if (!_isMusicOnlyActive.value) {
            setMusicOnlyActive(true)
        }

        val clipsDir = File(context.filesDir, "saved_clips").apply { mkdirs() }
        val fileName = "VoiceClip_${System.currentTimeMillis()}.wav"
        val outFile = File(clipsDir, fileName)
        currentRecordingFile = outFile

        _isRecording.value = true
        recordingStartTime = SystemClock.elapsedRealtime()

        recordingJob = scope.launch(Dispatchers.IO) {
            while (isActive && _isRecording.value) {
                _recordingDurationMs.value = SystemClock.elapsedRealtime() - recordingStartTime
                delay(100)
            }
        }

        // Generate the audio track in background
        scope.launch(Dispatchers.IO) {
            val track = _selectedTrack.value
            if (track.uri != null) {
                copyUriToWav(track.uri, outFile)
            }
        }
    }

    fun stopRecording(onComplete: ((RecordedVoiceClip?) -> Unit)? = null) {
        if (!_isRecording.value) return

        _isRecording.value = false
        recordingJob?.cancel()
        recordingJob = null

        val duration = _recordingDurationMs.value
        _recordingDurationMs.value = 0L

        scope.launch(Dispatchers.IO) {
            val outFile = currentRecordingFile
            if (outFile != null) {
                val track = _selectedTrack.value
                val preset = track.synthPreset ?: SynthPreset.LOFI_CHILL
                val seconds = maxOf(2, (duration / 1000).toInt())

                if (track.uri == null) {
                    AudioSynthesizer.renderPresetToWav(preset, seconds, outFile)
                }

                if (outFile.exists() && outFile.length() > 0) {
                    val authority = "${context.packageName}.provider"
                    val contentUri = FileProvider.getUriForFile(context, authority, outFile)
                    val newClip = RecordedVoiceClip(
                        id = outFile.name,
                        title = "ভয়েস বার্তা (${outFile.nameWithoutExtension.takeLast(6)})",
                        filePath = outFile.absolutePath,
                        uri = contentUri,
                        durationMs = duration,
                        timestamp = System.currentTimeMillis(),
                        sizeBytes = outFile.length()
                    )

                    withContext(Dispatchers.Main) {
                        _savedClips.value = listOf(newClip) + _savedClips.value
                        onComplete?.invoke(newClip)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onComplete?.invoke(null)
                    }
                }
            }
        }
    }

    private suspend fun copyUriToWav(sourceUri: Uri, destinationFile: File) = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playPreview(clip: RecordedVoiceClip) {
        if (_previewPlayingClipId.value == clip.id) {
            stopPreview()
            return
        }

        stopPreview()
        try {
            previewMediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(context, clip.uri)
                prepare()
                setOnCompletionListener {
                    _previewPlayingClipId.value = null
                }
                start()
            }
            _previewPlayingClipId.value = clip.id
        } catch (e: Exception) {
            e.printStackTrace()
            _previewPlayingClipId.value = null
        }
    }

    fun stopPreview() {
        try {
            previewMediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            previewMediaPlayer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _previewPlayingClipId.value = null
    }

    fun deleteClip(clip: RecordedVoiceClip) {
        if (_previewPlayingClipId.value == clip.id) {
            stopPreview()
        }
        try {
            val file = File(clip.filePath)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _savedClips.value = _savedClips.value.filter { it.id != clip.id }
    }

    private fun loadSavedClips() {
        scope.launch(Dispatchers.IO) {
            val clipsDir = File(context.filesDir, "saved_clips")
            if (clipsDir.exists()) {
                val files = clipsDir.listFiles { f -> f.extension == "wav" || f.extension == "m4a" || f.extension == "mp3" }
                    ?.sortedByDescending { it.lastModified() }
                    ?: emptyList()

                val authority = "${context.packageName}.provider"
                val list = files.mapNotNull { file ->
                    try {
                        val uri = FileProvider.getUriForFile(context, authority, file)
                        val duration = estimateDuration(file)
                        RecordedVoiceClip(
                            id = file.name,
                            title = "ভয়েস বার্তা (${file.nameWithoutExtension.takeLast(6)})",
                            filePath = file.absolutePath,
                            uri = uri,
                            durationMs = duration,
                            timestamp = file.lastModified(),
                            sizeBytes = file.length()
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
                _savedClips.value = list
            }
        }
    }

    private fun estimateDuration(file: File): Long {
        val bytes = file.length()
        return if (bytes > 44) {
            ((bytes - 44) * 1000L) / 88200L
        } else {
            3000L
        }
    }

    private fun startVisualizerLoop() {
        visualizerJob = scope.launch(Dispatchers.Default) {
            var step = 0
            while (isActive) {
                if (_isMusicOnlyActive.value || _isRecording.value) {
                    val base = 0.5f + 0.4f * sin(step * 0.25f).toFloat()
                    _musicLevel.value = base.coerceIn(0.1f, 1.0f)

                    // Simulated room mic pick-up: User may be talking, but output voice is strictly 0%
                    _rawMicDetectedLevel.value = (0.1f + 0.25f * sin(step * 0.2f).toFloat()).coerceIn(0f, 0.5f)
                    _transmittedVoiceLevel.value = 0f // Strictly 0% voice reaches listeners
                } else {
                    _musicLevel.value = 0f
                    _rawMicDetectedLevel.value = 0f
                    _transmittedVoiceLevel.value = 0f
                }
                step++
                delay(70)
            }
        }
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val usage = if (_isCallInjectionActive.value) {
                AudioAttributes.USAGE_VOICE_COMMUNICATION
            } else {
                AudioAttributes.USAGE_MEDIA
            }
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(usage)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { /* maintain */ }
                .build()
            audioManager.requestAudioFocus(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                if (_isCallInjectionActive.value) AudioManager.STREAM_VOICE_CALL else AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
    }

    fun release() {
        applyMicMuteAndCallRouting(false)
        stopMusicPlayback()
        stopPreview()
        synthPlaybackJob?.cancel()
        recordingJob?.cancel()
        visualizerJob?.cancel()
    }
}
