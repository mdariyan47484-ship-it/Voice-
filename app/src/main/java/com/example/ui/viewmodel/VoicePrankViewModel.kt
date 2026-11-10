package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.PrankAudioPlayer
import com.example.model.PrankVoiceClip
import com.example.model.SoundEffectType
import com.example.model.WirelessAdbState
import com.example.service.FloatingPrankOverlayService
import com.example.service.WirelessDebuggingManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VoicePrankViewModel(application: Application) : AndroidViewModel(application) {

    private val player = PrankAudioPlayer(application.applicationContext)
    private val adbManager = WirelessDebuggingManager(application.applicationContext)

    val adbState: StateFlow<WirelessAdbState> = adbManager.adbState

    private val _isOverlayActive = MutableStateFlow(false)
    val isOverlayActive: StateFlow<Boolean> = _isOverlayActive.asStateFlow()

    private val _myMonitorVolume = MutableStateFlow(0.6f)
    val myMonitorVolume: StateFlow<Float> = _myMonitorVolume.asStateFlow()

    private val _theirStreamVolume = MutableStateFlow(1.0f)
    val theirStreamVolume: StateFlow<Float> = _theirStreamVolume.asStateFlow()

    private val _activePlayingClipId = MutableStateFlow<String?>(null)
    val activePlayingClipId: StateFlow<String?> = _activePlayingClipId.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    // Only 2 minimal sample clips provided initially; all other clips added from phone storage
    private val defaultClips = listOf(
        PrankVoiceClip("sample_1", "Sample Hello Girl", "Sample", 4, "👩", 0xFFD946EF, SoundEffectType.FEMALE_VOICE_HELLO),
        PrankVoiceClip("sample_2", "Sample Air Horn", "Sample", 3, "📢", 0xFFFF3366, SoundEffectType.AIR_HORN)
    )

    private val _customClips = MutableStateFlow<List<PrankVoiceClip>>(emptyList())
    private val _allClips = MutableStateFlow(defaultClips)
    val allClips: StateFlow<List<PrankVoiceClip>> = _allClips.asStateFlow()

    init {
        player.myMonitorVolume = _myMonitorVolume.value
        player.theirStreamVolume = _theirStreamVolume.value
    }

    fun setCategory(category: String) {
        _selectedCategory.value = category
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun setMyMonitorVolume(vol: Float) {
        _myMonitorVolume.value = vol
        player.myMonitorVolume = vol
    }

    fun setTheirStreamVolume(vol: Float) {
        _theirStreamVolume.value = vol
        player.theirStreamVolume = vol
    }

    fun playClip(clip: PrankVoiceClip) {
        viewModelScope.launch {
            if (_activePlayingClipId.value == clip.id) {
                player.stopAll()
                _activePlayingClipId.value = null
            } else {
                // Auto-pair device with privileged Shizuku engine whenever a voice is clicked
                if (!adbState.value.isConnected) {
                    adbManager.activateAutoBridge()
                }

                _activePlayingClipId.value = clip.id
                _snackbarMessage.value = "⚡ Paired & Injected: ${clip.title} (Microphone Stream Active)"

                player.playClip(clip) {
                    if (_activePlayingClipId.value == clip.id) {
                        _activePlayingClipId.value = null
                    }
                }
            }
        }
    }

    fun stopPlayback() {
        player.stopAll()
        _activePlayingClipId.value = null
    }

    fun shareClip(clip: PrankVoiceClip, context: Context) {
        viewModelScope.launch {
            try {
                val file = player.getOrCreateClipFile(clip)
                val uri = player.getShareableUri(file)
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
        viewModelScope.launch {
            adbManager.pairWithWirelessDebugging(port, code)
            _snackbarMessage.value = adbState.value.statusMessage
        }
    }

    fun autoActivateAdb() {
        adbManager.activateAutoBridge()
        _snackbarMessage.value = "Shizuku Direct Privileged Engine Activated!"
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
        val newClip = PrankVoiceClip(
            id = "custom_${System.currentTimeMillis()}",
            title = title.ifBlank { "My Voice Prank" },
            category = "Custom",
            durationSeconds = durationSec,
            iconEmoji = iconEmoji.ifBlank { "🎙️" },
            accentColor = 0xFF00E5FF,
            soundEffectType = SoundEffectType.CUSTOM_RECORDED,
            isCustom = true,
            customAudioUri = uri
        )
        val updatedCustom = listOf(newClip) + _customClips.value
        _customClips.value = updatedCustom
        _allClips.value = updatedCustom + defaultClips
        _snackbarMessage.value = "Added voice from phone: $title"
    }

    fun deleteClip(clip: PrankVoiceClip) {
        if (_activePlayingClipId.value == clip.id) {
            stopPlayback()
        }
        _customClips.value = _customClips.value.filter { it.id != clip.id }
        _allClips.value = _allClips.value.filter { it.id != clip.id }
        _snackbarMessage.value = "Removed: ${clip.title}"
    }

    override fun onCleared() {
        super.onCleared()
        player.stopAll()
    }
}
