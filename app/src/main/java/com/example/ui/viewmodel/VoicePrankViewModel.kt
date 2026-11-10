package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.VoicePrankRepository
import com.example.model.PrankVoiceClip
import com.example.model.WirelessAdbState
import com.example.service.FloatingPrankOverlayService
import com.example.service.WirelessDebuggingManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VoicePrankViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VoicePrankRepository.getInstance(application.applicationContext)

    val adbState: StateFlow<WirelessAdbState> = repository.adbState

    private val _isOverlayActive = MutableStateFlow(FloatingPrankOverlayService.isRunning)
    val isOverlayActive: StateFlow<Boolean> = _isOverlayActive.asStateFlow()

    val myMonitorVolume: StateFlow<Float> = repository.myMonitorVolume
    val theirStreamVolume: StateFlow<Float> = repository.theirStreamVolume
    val activePlayingClipId: StateFlow<String?> = repository.activePlayingClipId
    val allClips: StateFlow<List<PrankVoiceClip>> = repository.allClips

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    fun updateOverlayRunningState() {
        _isOverlayActive.value = FloatingPrankOverlayService.isRunning
    }

    fun setCategory(category: String) {
        _selectedCategory.value = category
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun setMyMonitorVolume(vol: Float) {
        repository.setMyMonitorVolume(vol)
    }

    fun setTheirStreamVolume(vol: Float) {
        repository.setTheirStreamVolume(vol)
    }

    fun playClip(clip: PrankVoiceClip) {
        viewModelScope.launch {
            repository.playClip(clip) { isSuccess, msg ->
                _snackbarMessage.value = msg
            }
        }
    }

    fun stopPlayback() {
        repository.stopPlayback()
    }

    fun shareClip(clip: PrankVoiceClip, context: Context) {
        viewModelScope.launch {
            try {
                val file = repository.player.getOrCreateClipFile(clip)
                val uri = repository.player.getShareableUri(file)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "audio/wav"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Voice Prank - ${clip.title}")
                    putExtra(Intent.EXTRA_TEXT, "🎙️ Voice Prank audio: ${clip.title}")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(
                    Intent.createChooser(shareIntent, "Send Voice Prank to WhatsApp/Messenger...")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: Exception) {
                e.printStackTrace()
                _snackbarMessage.value = "Failed to share voice prank clip."
            }
        }
    }

    fun pairWirelessAdb(port: String, code: String) {
        repository.pairWithCode(port, code) { isSuccess, msg ->
            _snackbarMessage.value = msg
        }
    }

    fun autoActivateAdb() {
        repository.activatePairing { isSuccess, msg ->
            _snackbarMessage.value = msg
        }
    }

    fun toggleFloatingOverlay(context: Context) {
        val next = !_isOverlayActive.value
        _isOverlayActive.value = next
        if (next) {
            FloatingPrankOverlayService.start(context)
            _snackbarMessage.value = "Floating Logo activated! Tap bubble anytime to prank."
        } else {
            FloatingPrankOverlayService.stop(context)
            _snackbarMessage.value = "Floating Logo closed."
        }
    }

    fun addCustomClip(title: String, durationSec: Int, iconEmoji: String, uri: Uri?) {
        repository.addCustomClip(title, durationSec, iconEmoji, uri)
        _snackbarMessage.value = "Added voice from phone: $title"
    }

    fun deleteClip(clip: PrankVoiceClip) {
        repository.deleteClip(clip)
        _snackbarMessage.value = "Removed: ${clip.title}"
    }

    override fun onCleared() {
        super.onCleared()
    }
}
