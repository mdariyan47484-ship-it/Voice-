package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
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

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingDurationMs = MutableStateFlow(0L)
    val recordingDurationMs: StateFlow<Long> = _recordingDurationMs.asStateFlow()

    private val _musicLevel = MutableStateFlow(0f)
    val musicLevel: StateFlow<Float> = _musicLevel.asStateFlow()

    private val _selectedTrack = MutableStateFlow(
        AudioTrackItem(
            id = "preset_lofi",
            title = "লো-ফাই চিল সুর (Lo-Fi Chill)",
            subtitle = "ক্লিন ব্যাকগ্রাউন্ড মিউজিক",
            synthPreset = SynthPreset.LOFI_CHILL
        )
    )
    val selectedTrack: StateFlow<AudioTrackItem> = _selectedTrack.asStateFlow()

    private val _savedClips = MutableStateFlow<List<RecordedVoiceClip>>(emptyList())
    val savedClips: StateFlow<List<RecordedVoiceClip>> = _savedClips.asStateFlow()

    private val _lastRecordedClip = MutableStateFlow<RecordedVoiceClip?>(null)
    val lastRecordedClip: StateFlow<RecordedVoiceClip?> = _lastRecordedClip.asStateFlow()

    // Smooth playback via MediaPlayer only (NO raw AudioTrack buzzing)
    private var backgroundMediaPlayer: MediaPlayer? = null
    private var previewMediaPlayer: MediaPlayer? = null
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
            applySafeAudioRouting(true)
            requestAudioFocus()
            startMusicPlayback()
        } else {
            applySafeAudioRouting(false)
            stopMusicPlayback()
            if (_isRecording.value) {
                stopRecording()
            }
        }
    }

    private fun applySafeAudioRouting(enable: Boolean) {
        try {
            // Keep MODE_NORMAL to avoid telephony echo-cancellation buzzing / speaker vibration
            audioManager.mode = AudioManager.MODE_NORMAL
            if (enable) {
                audioManager.isMicrophoneMute = true
            } else {
                audioManager.isMicrophoneMute = false
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
        stopMusicPlayback()
        val track = _selectedTrack.value

        scope.launch(Dispatchers.IO) {
            try {
                if (track.uri != null) {
                    playUriWithMediaPlayer(track.uri)
                } else if (track.synthPreset != null) {
                    // Pre-render a clean WAV file to eliminate speaker buzzing & distortion
                    val presetFile = File(context.cacheDir, "preset_${track.synthPreset.name}.wav")
                    if (!presetFile.exists() || presetFile.length() < 1000) {
                        AudioSynthesizer.renderPresetToWav(track.synthPreset, 12, presetFile)
                    }
                    if (presetFile.exists() && presetFile.length() > 0) {
                        playFileWithMediaPlayer(presetFile)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun playFileWithMediaPlayer(file: File) = withContext(Dispatchers.Main) {
        try {
            backgroundMediaPlayer?.release()
            backgroundMediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(file.absolutePath)
                isLooping = true
                setVolume(0.8f, 0.8f) // Clean, balanced, non-distorting volume
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun playUriWithMediaPlayer(uri: Uri) = withContext(Dispatchers.Main) {
        try {
            backgroundMediaPlayer?.release()
            backgroundMediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(context, uri)
                isLooping = true
                setVolume(0.85f, 0.85f)
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopMusicPlayback() {
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

    /**
     * Records a voice note containing 100% music and ZERO microphone speech
     */
    fun startRecording() {
        if (_isRecording.value) return

        if (!_isMusicOnlyActive.value) {
            setMusicOnlyActive(true)
        }

        val clipsDir = File(context.filesDir, "saved_clips").apply { mkdirs() }
        val fileName = "MusicVoice_${System.currentTimeMillis()}.wav"
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

        // Copy user audio or prepare preset
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
                val seconds = maxOf(3, (duration / 1000).toInt())

                if (track.uri == null) {
                    AudioSynthesizer.renderPresetToWav(preset, seconds, outFile)
                }

                if (outFile.exists() && outFile.length() > 0) {
                    val authority = "${context.packageName}.provider"
                    val contentUri = FileProvider.getUriForFile(context, authority, outFile)
                    val newClip = RecordedVoiceClip(
                        id = outFile.name,
                        title = "মিউজিক ভয়েস বার্তা",
                        filePath = outFile.absolutePath,
                        uri = contentUri,
                        durationMs = duration,
                        timestamp = System.currentTimeMillis(),
                        sizeBytes = outFile.length()
                    )

                    withContext(Dispatchers.Main) {
                        _lastRecordedClip.value = newClip
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
                        RecordedVoiceClip(
                            id = file.name,
                            title = "মিউজিক ভয়েস বার্তা",
                            filePath = file.absolutePath,
                            uri = uri,
                            durationMs = estimateDuration(file),
                            timestamp = file.lastModified(),
                            sizeBytes = file.length()
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
                _savedClips.value = list
                if (list.isNotEmpty()) {
                    _lastRecordedClip.value = list.first()
                }
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
                    val base = 0.5f + 0.35f * sin(step * 0.22f).toFloat()
                    _musicLevel.value = base.coerceIn(0.1f, 0.9f)
                } else {
                    _musicLevel.value = 0f
                }
                step++
                delay(75)
            }
        }
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
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
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
    }

    fun release() {
        applySafeAudioRouting(false)
        stopMusicPlayback()
        recordingJob?.cancel()
        visualizerJob?.cancel()
    }
}
