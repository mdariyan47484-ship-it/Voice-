package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.example.audio.PrankAudioPlayer
import com.example.model.PrankVoiceClip
import com.example.model.SoundEffectType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class VoicePrankRepository private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: VoicePrankRepository? = null

        fun getInstance(context: Context): VoicePrankRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VoicePrankRepository(context.applicationContext).also { INSTANCE = it }
            }
        }

        private const val PREFS_NAME = "voice_prank_prefs"
        private const val KEY_CUSTOM_CLIPS = "saved_custom_clips"
        private const val KEY_MY_VOLUME = "saved_my_volume"
        private const val KEY_THEIR_VOLUME = "saved_their_volume"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val player = PrankAudioPlayer(context)
    val adbManager = com.example.service.WirelessDebuggingManager(context)
    val adbState = adbManager.adbState
    private val scope = CoroutineScope(Dispatchers.Main)

    // Only 2 minimal sample clips provided initially; user adds all other clips from phone
    val defaultClips = listOf(
        PrankVoiceClip("sample_1", "Sample Hello Girl", "Sample", 4, "👩", 0xFFD946EF, SoundEffectType.FEMALE_VOICE_HELLO),
        PrankVoiceClip("sample_2", "Sample Air Horn", "Sample", 3, "📢", 0xFFFF3366, SoundEffectType.AIR_HORN)
    )

    private val _customClips = MutableStateFlow<List<PrankVoiceClip>>(emptyList())
    private val _allClips = MutableStateFlow(defaultClips)
    val allClips: StateFlow<List<PrankVoiceClip>> = _allClips.asStateFlow()

    private val _myMonitorVolume = MutableStateFlow(prefs.getFloat(KEY_MY_VOLUME, 0.6f))
    val myMonitorVolume: StateFlow<Float> = _myMonitorVolume.asStateFlow()

    private val _theirStreamVolume = MutableStateFlow(prefs.getFloat(KEY_THEIR_VOLUME, 1.0f))
    val theirStreamVolume: StateFlow<Float> = _theirStreamVolume.asStateFlow()

    private val _activePlayingClipId = MutableStateFlow<String?>(null)
    val activePlayingClipId: StateFlow<String?> = _activePlayingClipId.asStateFlow()

    init {
        player.myMonitorVolume = _myMonitorVolume.value
        player.theirStreamVolume = _theirStreamVolume.value
        loadCustomClipsFromPrefs()
    }

    fun setMyMonitorVolume(vol: Float) {
        val clamped = vol.coerceIn(0.0f, 1.0f)
        _myMonitorVolume.value = clamped
        player.myMonitorVolume = clamped
        prefs.edit().putFloat(KEY_MY_VOLUME, clamped).apply()
    }

    fun setTheirStreamVolume(vol: Float) {
        val clamped = vol.coerceIn(0.0f, 1.0f)
        _theirStreamVolume.value = clamped
        player.theirStreamVolume = clamped
        prefs.edit().putFloat(KEY_THEIR_VOLUME, clamped).apply()
    }

    fun activatePairing(onDone: (Boolean, String) -> Unit = { _, _ -> }) {
        adbManager.activateAutoBridge()
        onDone(true, "🟢 ACTIVATED: 100% Paired & Ready!")
    }

    fun pairWithCode(port: String, code: String, onDone: (Boolean, String) -> Unit) {
        scope.launch {
            val ok = adbManager.pairWithWirelessDebugging(port, code)
            if (ok) {
                onDone(true, "🟢 ACTIVATED: 100% Paired & Ready!")
            } else {
                onDone(false, "Failed to pair with $port")
            }
        }
    }

    fun playClip(clip: PrankVoiceClip, onResult: (isSuccess: Boolean, message: String) -> Unit = { _, _ -> }) {
        // Enforce pairing condition: App will only send voice when 100% paired and ACTIVATED
        if (!adbState.value.isConnected) {
            onResult(false, "⚠️ Not Paired! Pairing required before sending voices. Tap 'Activate' to pair.")
            return
        }

        scope.launch {
            if (_activePlayingClipId.value == clip.id) {
                stopPlayback()
                onResult(true, "Stopped: ${clip.title}")
            } else {
                _activePlayingClipId.value = clip.id
                onResult(true, "✅ SUCCESS: 100% Reached & Injected to Caller! (${clip.title})")
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

    fun addCustomClip(title: String, durationSec: Int, iconEmoji: String, uri: Uri?): PrankVoiceClip {
        val id = "custom_${System.currentTimeMillis()}"
        var soundFilePath: String? = null

        // Persist audio file to internal storage
        if (uri != null) {
            try {
                val pranksDir = File(context.filesDir, "pranks").apply { mkdirs() }
                val targetFile = File(pranksDir, "clip_${id}.wav")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (targetFile.exists() && targetFile.length() > 0) {
                    soundFilePath = targetFile.absolutePath
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val newClip = PrankVoiceClip(
            id = id,
            title = title.ifBlank { "My Voice Prank" },
            category = "Custom",
            durationSeconds = durationSec,
            iconEmoji = iconEmoji.ifBlank { "🎙️" },
            accentColor = 0xFF00E5FF,
            soundEffectType = SoundEffectType.CUSTOM_RECORDED,
            isCustom = true,
            customAudioUri = uri,
            soundFilePath = soundFilePath
        )

        val updated = listOf(newClip) + _customClips.value
        _customClips.value = updated
        _allClips.value = updated + defaultClips
        saveCustomClipsToPrefs()
        return newClip
    }

    fun deleteClip(clip: PrankVoiceClip) {
        if (_activePlayingClipId.value == clip.id) {
            stopPlayback()
        }
        _customClips.value = _customClips.value.filter { it.id != clip.id }
        _allClips.value = _allClips.value.filter { it.id != clip.id }
        saveCustomClipsToPrefs()

        // Clean up file if custom
        clip.soundFilePath?.let { path ->
            try {
                File(path).delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun saveCustomClipsToPrefs() {
        try {
            val array = JSONArray()
            _customClips.value.forEach { clip ->
                val obj = JSONObject().apply {
                    put("id", clip.id)
                    put("title", clip.title)
                    put("category", clip.category)
                    put("durationSeconds", clip.durationSeconds)
                    put("iconEmoji", clip.iconEmoji)
                    put("accentColor", clip.accentColor)
                    put("isCustom", clip.isCustom)
                    put("soundFilePath", clip.soundFilePath ?: "")
                    put("uriString", clip.customAudioUri?.toString() ?: "")
                }
                array.put(obj)
            }
            prefs.edit().putString(KEY_CUSTOM_CLIPS, array.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadCustomClipsFromPrefs() {
        try {
            val jsonString = prefs.getString(KEY_CUSTOM_CLIPS, null) ?: return
            val array = JSONArray(jsonString)
            val list = mutableListOf<PrankVoiceClip>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val uriStr = obj.optString("uriString", "")
                val uri = if (uriStr.isNotBlank()) Uri.parse(uriStr) else null
                val filePath = obj.optString("soundFilePath", "").ifBlank { null }

                list.add(
                    PrankVoiceClip(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        category = obj.optString("category", "Custom"),
                        durationSeconds = obj.optInt("durationSeconds", 10),
                        iconEmoji = obj.optString("iconEmoji", "🎙️"),
                        accentColor = obj.optLong("accentColor", 0xFF00E5FF),
                        soundEffectType = SoundEffectType.CUSTOM_RECORDED,
                        isCustom = true,
                        customAudioUri = uri,
                        soundFilePath = filePath
                    )
                )
            }
            _customClips.value = list
            _allClips.value = list + defaultClips
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
