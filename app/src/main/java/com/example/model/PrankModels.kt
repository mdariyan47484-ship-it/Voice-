package com.example.model

import android.net.Uri

data class PrankVoiceClip(
    val id: String,
    val title: String,
    val category: String,
    val durationSeconds: Int,
    val iconEmoji: String,
    val accentColor: Long, // Hex color like 0xFF00E5FF
    val soundEffectType: SoundEffectType,
    val isCustom: Boolean = false,
    val customAudioUri: Uri? = null,
    val soundFilePath: String? = null
)

enum class SoundEffectType {
    AIR_HORN,
    POLICE_SIREN,
    FEMALE_VOICE_HELLO,
    FEMALE_VOICE_CRY,
    GHOST_WHISPER,
    JUMP_SCARE,
    ROBOT_CYBER,
    RICKROLL_TUNE,
    BOMB_EXPLOSION,
    LAUGH_TRACK,
    BABY_CRY,
    DOG_BARK,
    CAT_MEOW,
    CRICKETS_AWKWARD,
    RADIO_DISPATCH,
    GAME_OVER,
    DRUM_RIMSHOT,
    CARTOON_BOING,
    LOUD_SNORE,
    TRAFFIC_JAM,
    CUSTOM_RECORDED
}

data class WirelessAdbState(
    val isConnected: Boolean = false,
    val ipAddress: String = "127.0.0.1",
    val pairingPort: String = "42135",
    val pairingCode: String = "",
    val statusMessage: String = "Ready to pair with Wireless Debugging"
)

data class VolumeSettings(
    val myMonitorVolume: Float = 0.5f,     // 0% - 100% (What user hears)
    val theirStreamVolume: Float = 1.0f    // 100% Boosted (What receivers hear)
)
