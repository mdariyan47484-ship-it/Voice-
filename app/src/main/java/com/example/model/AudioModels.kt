package com.example.model

import android.net.Uri

enum class SynthPreset(val displayName: String, val description: String) {
    LOFI_CHILL("লো-ফাই চিল সুর (Lo-Fi Chill)", "মিষ্টি ধীরগতির মেলোডি ও বেস"),
    ACOUSTIC_GUITAR("অ্যাকোস্টিক সুর (Acoustic Chords)", "শান্ত পিয়ানো ও অ্যাকোস্টিক নোট"),
    UPBEAT_GROOVE("ইলেকট্রিক গ্রুভ (Upbeat Groove)", "উচ্ছ্বল মডার্ন পপ বিট ও সিন্থ")
}

data class AudioTrackItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val uri: Uri? = null,
    val synthPreset: SynthPreset? = null,
    val durationFormatted: String = "অসীম (লুপ)"
)

data class RecordedVoiceClip(
    val id: String,
    val title: String,
    val filePath: String,
    val uri: Uri,
    val durationMs: Long,
    val timestamp: Long,
    val sizeBytes: Long
) {
    val durationFormatted: String
        get() {
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format("%02d:%02d", minutes, seconds)
        }

    val sizeFormatted: String
        get() {
            val kb = sizeBytes / 1024.0
            return if (kb > 1024) {
                String.format("%.1f MB", kb / 1024.0)
            } else {
                String.format("%.0f KB", kb)
            }
        }
}
