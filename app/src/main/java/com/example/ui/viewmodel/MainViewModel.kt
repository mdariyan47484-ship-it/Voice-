package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.example.audio.MusicMicEngine
import com.example.model.AudioTrackItem
import com.example.model.RecordedVoiceClip
import com.example.service.AudioBridgeService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = MusicMicEngine(application.applicationContext)

    val isMusicOnlyActive: StateFlow<Boolean> = engine.isMusicOnlyActive
    val musicLevel: StateFlow<Float> = engine.musicLevel
    val isRecording: StateFlow<Boolean> = engine.isRecording
    val recordingDurationMs: StateFlow<Long> = engine.recordingDurationMs
    val lastRecordedClip: StateFlow<RecordedVoiceClip?> = engine.lastRecordedClip
    val selectedTrack: StateFlow<AudioTrackItem> = engine.selectedTrack

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun toggleMusicOnly(context: Context) {
        val nextState = !isMusicOnlyActive.value
        engine.setMusicOnlyActive(nextState)
        if (nextState) {
            AudioBridgeService.startService(context, selectedTrack.value.title)
            _snackbarMessage.value = "সক্রিয়: স্পিকার ভাইব্রেশন ফিক্সড, মাইক্রোফোনে কথা বন্ধ!"
        } else {
            AudioBridgeService.stopService(context)
            _snackbarMessage.value = "বন্ধ করা হয়েছে।"
        }
    }

    fun selectCustomAudio(uri: Uri, fileName: String) {
        val track = AudioTrackItem(
            id = "custom_${System.currentTimeMillis()}",
            title = fileName,
            subtitle = "ফোন স্টোরেজের গান",
            uri = uri
        )
        engine.setSelectedTrack(track)
        _snackbarMessage.value = "গান লোড হয়েছে: $fileName"
    }

    fun startRecording() {
        engine.startRecording()
        _snackbarMessage.value = "রেকর্ড শুরু... কথা বললেও শুধু ফোনের গান রেকর্ড হবে!"
    }

    fun stopRecordingAndShare(context: Context) {
        engine.stopRecording { clip ->
            if (clip != null) {
                shareClip(context, clip)
            }
        }
    }

    fun stopRecording() {
        engine.stopRecording { clip ->
            if (clip != null) {
                _snackbarMessage.value = "মিউজিক ভয়েস নোট তৈরি হয়েছে! WhatsApp-এ পাঠাতে চাপুন।"
            }
        }
    }

    fun shareClip(context: Context, clip: RecordedVoiceClip) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/wav"
                putExtra(Intent.EXTRA_STREAM, clip.uri)
                putExtra(Intent.EXTRA_SUBJECT, "Music Voice Message")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "🎵 ফোনের গান দিয়ে পাঠানো ভয়েস বার্তা (কথা সম্পূর্ণ মিউট)"
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(
                Intent.createChooser(shareIntent, "কাউকে ভয়েস হিসেবে পাঠান (WhatsApp, Messenger...)")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            e.printStackTrace()
            _snackbarMessage.value = "শেয়ার করতে সমস্যা হয়েছে।"
        }
    }

    override fun onCleared() {
        super.onCleared()
        engine.release()
    }
}
