package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MutedRed
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonMagenta
import kotlin.math.sin

@Composable
fun LiveWaveformView(
    isActive: Boolean,
    isCallInjectionActive: Boolean,
    musicLevel: Float,
    rawMicDetectedLevel: Float,
    modifier: Modifier = Modifier
) {
    val phaseAnim = remember { Animatable(0f) }

    LaunchedEffect(isActive) {
        if (isActive) {
            phaseAnim.animateTo(
                targetValue = 100f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 20000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        } else {
            phaseAnim.snapTo(0f)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(16.dp)
            .testTag("waveform_visualizer_container")
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = "Music Output",
                        tint = if (isActive) NeonCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isActive) "ফোনের গান: ১০০% ট্রান্সমিটেড 🎵" else "গান: বন্ধ",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isActive) NeonCyan else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.MicOff,
                        contentDescription = "Human Voice Muted",
                        tint = if (isActive) NeonEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isActive) "কথা সম্পূর্ণ মিউট (০%) 🔇" else "মাইক্রোফোন সাধারণ",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isActive) NeonEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Animated Audio Wave Canvas
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
            ) {
                val barCount = 28
                val totalWidth = size.width
                val barSpacing = 4.dp.toPx()
                val totalSpacing = barSpacing * (barCount - 1)
                val barWidth = (totalWidth - totalSpacing) / barCount
                val centerY = size.height / 2f
                val phase = phaseAnim.value

                for (i in 0 until barCount) {
                    val normalizedIndex = i.toFloat() / barCount
                    val sinFactor = sin((normalizedIndex * 6.28f * 2f) + phase * 0.1f)
                    val heightMultiplier = if (isActive) {
                        (0.2f + 0.8f * (sinFactor + 1f) / 2f) * (0.4f + 0.6f * musicLevel)
                    } else {
                        0.08f
                    }
                    val barHeight = (size.height * heightMultiplier).coerceIn(4.dp.toPx(), size.height)

                    val x = i * (barWidth + barSpacing)
                    val y = centerY - (barHeight / 2f)

                    val barColor = if (isActive) {
                        if (i % 2 == 0) NeonCyan else NeonMagenta
                    } else {
                        Color(0xFF475569)
                    }

                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Verification Dual Meter:
            // Channel 1: Phone Music
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "১. ফোনের গান ও সিস্টেম সাউন্ড",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isActive) NeonCyan else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Text(
                        text = if (isActive) "১০০% কানেক্টেড (অন্যরা শুনবে)" else "০%",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isActive) NeonCyan else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { if (isActive) 1f else 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = NeonCyan,
                    trackColor = MaterialTheme.colorScheme.surface
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Channel 2: Physical Microphone (Human Voice)
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "২. মাইক্রোফোনে আপনার মুখের কথা",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isActive) NeonEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Text(
                        text = if (isActive) "০% (সম্পূর্ণ ব্লকড, কেউ শুনবে না)" else "স্বাভাবিক",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isActive) NeonEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { 0f }, // Always 0% voice reaches listeners
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MutedRed,
                    trackColor = if (isActive) NeonEmerald.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
                )
            }

            if (isCallInjectionActive && isActive) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(NeonCyan.copy(alpha = 0.1f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneInTalk,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "কল লাইন সক্রিয়: ফোন/হোয়াটসঅ্যাপ কলে শুধু গান যাবে",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            color = NeonCyan,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
        }
    }
}
