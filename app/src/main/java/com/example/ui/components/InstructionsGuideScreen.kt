package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonPurple

@Composable
fun InstructionsGuideScreen(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "ব্যবহার নির্দেশিকা ও টিপস",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "সহজ নিয়মে ফোনে বাজা গান দিয়ে ভয়েস বার্তা তৈরি করুন",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }

        item {
            GuideStepCard(
                stepNumber = "১",
                icon = Icons.Default.MusicNote,
                iconColor = NeonCyan,
                title = "সুইচ অন করে গান সিলেক্ট করুন",
                description = "মেইন সুইচটি অন করলেই অডিও চ্যানেল একটিভ হবে। আপনি রেডিমেড সুর অথবা ফোন স্টোরেজ থেকে যেকোনো পছন্দের MP3/WAV গান বেছে নিতে পারেন।"
            )
        }

        item {
            GuideStepCard(
                stepNumber = "২",
                icon = Icons.Default.MicOff,
                iconColor = NeonEmerald,
                title = "ভয়েস রেকর্ড চাপুন (মানুষের কথা বন্ধ)",
                description = "ভয়েস রেকর্ড বাটন অন করলে আপনার কথা বলা সম্পূর্ণ মিউট/ব্লক হয়ে যাবে। আউটপুট ট্র্যাকে শুধুমাত্র আপনার ফোনের মিউজিক বা সিস্টেম সাউন্ড রেকর্ড হবে।"
            )
        }

        item {
            GuideStepCard(
                stepNumber = "৩",
                icon = Icons.Default.Send,
                iconColor = NeonPurple,
                title = "১-ক্লিকে WhatsApp বা Messenger-এ পাঠান",
                description = "রেকর্ডিং শেষে 'থামিয়ে পাঠান' বাটনে চাপলে সরাসরি শেয়ার মেনু আসবে। বন্ধুকে পাঠালে সে শুধু আপনার ফোনের গান শুনবে, আপনার কথা নয়!"
            )
        }

        item {
            GuideStepCard(
                stepNumber = "৪",
                icon = Icons.Default.Bolt,
                iconColor = Color(0xFFF59E0B),
                title = "আল্ট্রা-লাইটওয়েট ও দ্রুতগতির",
                description = "অ্যাপটি বিশেষভাবে ১-২ মেগাবাইটের মধ্যে ডিজাইন করা হয়েছে। এটি ফোনের র‍্যাম ও ব্যাটারি খরচ করে না এবং দ্রুত ওপেন হয়।"
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = NeonEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "গোপনীয়তা ও নিরাপত্তা",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "আপনার কোনো অডিও বা ভয়েস অনলাইনে আপলোড হয় না। সকল সাউন্ড লোকালি আপনার ডিভাইসেই প্রসেস হয়।",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun GuideStepCard(
    stepNumber: String,
    icon: ImageVector,
    iconColor: Color,
    title: String,
    description: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                )
            }
        }
    }
}
