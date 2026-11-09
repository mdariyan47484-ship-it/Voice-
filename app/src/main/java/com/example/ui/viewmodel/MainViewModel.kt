package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.example.audio.MusicMicEngine
import com.example.service.AudioBridgeService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = MusicMicEngine(application.applicationContext)

    val isMusicOnlyActive: StateFlow<Boolean> = engine.isMusicOnlyActive
    val musicLevel: StateFlow<Float> = engine.musicLevel

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun toggleMusicOnly(context: Context) {
        val nextState = !isMusicOnlyActive.value
        engine.setMusicOnlyActive(nextState)
        if (nextState) {
            AudioBridgeService.startService(context, "সিস্টেম সাউন্ড ও মিউজিক ব্রিজ")
            _snackbarMessage.value = "সক্রিয়: মাইক্রোফোনে কথা বন্ধ, শুধুমাত্র ফোনের গান চলবে!"
        } else {
            AudioBridgeService.stopService(context)
            _snackbarMessage.value = "বন্ধ করা হয়েছে।"
        }
    }

    override fun onCleared() {
        super.onCleared()
        engine.release()
    }
}
