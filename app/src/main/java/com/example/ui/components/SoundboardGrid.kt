package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PrankVoiceClip
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonPurple

import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.AudioFile

import androidx.compose.material.icons.filled.CheckCircle

@Composable
fun SoundboardGrid(
    clips: List<PrankVoiceClip>,
    activePlayingId: String?,
    isPaired: Boolean = false,
    onActivatePairing: () -> Unit = {},
    selectedCategory: String,
    onCategorySelect: (String) -> Unit,
    onPlayClip: (PrankVoiceClip) -> Unit,
    onShareClip: (PrankVoiceClip) -> Unit,
    onDeleteClip: (PrankVoiceClip) -> Unit,
    onAddCustomClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val categories = listOf("All", "Custom", "Sample")

    val filteredClips = if (selectedCategory == "All") {
        clips
    } else {
        clips.filter { it.category.equals(selectedCategory, ignoreCase = true) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Pairing Gate Status Card
        if (!isPaired) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("card_pairing_warning"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF3B1E2B)),
                border = BorderStroke(1.dp, Color(0xFFEF4444))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🔴 NOT PAIRED (Injection Locked)",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEF4444)
                            )
                        )
                        Text(
                            text = "Pair device first to activate microphone voice sending",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFFE2E8F0),
                                fontSize = 11.sp
                            )
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onActivatePairing,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(34.dp).testTag("btn_grid_activate_pair")
                    ) {
                        Text("Activate", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("card_pairing_active"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF102820)),
                border = BorderStroke(1.dp, NeonEmerald)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = NeonEmerald, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "🟢 ACTIVATED: 100% Ready to Inject into Calls & Mic",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = NeonEmerald
                        )
                    )
                }
            }
        }
        // Hero Import Card for user's phone audio
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clickable { onAddCustomClick() }
                .testTag("card_import_phone_audio"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = NeonPurple.copy(alpha = 0.15f)
            ),
            border = BorderStroke(1.dp, NeonPurple)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(NeonPurple.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = NeonPurple,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "+ Add Voice from Phone",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = NeonPurple
                            )
                        )
                        Text(
                            text = "Import any MP3/Audio from phone storage",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                Button(
                    onClick = onAddCustomClick,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(34.dp).testTag("btn_add_custom_clip")
                ) {
                    Text("Browse", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Section Title & Category Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Soundboard Clips (${filteredClips.size})",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }

        // Horizontal Category Filter Chips
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories) { cat ->
                val isSelected = selectedCategory == cat
                FilterChip(
                    selected = isSelected,
                    onClick = { onCategorySelect(cat) },
                    label = { Text(cat, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                        selectedLabelColor = NeonCyan
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        selectedBorderColor = NeonCyan,
                        borderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (filteredClips.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎙️", fontSize = 40.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No voice clips here yet",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap '+ Add Voice from Phone' to import any audio file!",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        } else {
            // Side-by-Side Grid: 2 columns
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredClips, key = { it.id }) { clip ->
                val isPlaying = activePlayingId == clip.id
                val borderGlow by animateColorAsState(
                    targetValue = if (isPlaying) NeonCyan else MaterialTheme.colorScheme.outlineVariant,
                    animationSpec = tween(300),
                    label = "borderGlow"
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("clip_card_${clip.id}")
                        .clickable { onPlayClip(clip) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isPlaying) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(
                        width = if (isPlaying) 2.dp else 1.dp,
                        color = borderGlow
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (isPlaying) 6.dp else 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Emoji Avatar Box
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(clip.accentColor).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = clip.iconEmoji,
                                    fontSize = 22.sp
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Duration badge
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${clip.durationSeconds}s",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(2.dp))
                                IconButton(
                                    onClick = { onDeleteClip(clip) },
                                    modifier = Modifier.size(24.dp).testTag("btn_delete_${clip.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Delete Clip",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.75f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = clip.title,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = clip.category,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Bottom Actions: Play indicator & 1-tap WhatsApp Share
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Stop" else "Inject",
                                    tint = if (isPlaying) NeonCyan else Color(clip.accentColor),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isPlaying) "✅ SUCCESS • 100% REACHED" else if (isPaired) "▶ SEND TO CALL" else "🔒 LOCKED (PAIR FIRST)",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (isPlaying) NeonEmerald else if (isPaired) Color(clip.accentColor) else Color(0xFFEF4444)
                                )
                            }

                            // Share to WhatsApp / Chat
                            IconButton(
                                onClick = { onShareClip(clip) },
                                modifier = Modifier.size(28.dp).testTag("btn_share_${clip.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share to WhatsApp",
                                    tint = NeonEmerald,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        }
    }
}
