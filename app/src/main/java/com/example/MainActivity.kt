package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.MutedRed
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonPurple
import com.example.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val context = LocalContext.current
                val snackbarHostState = remember { SnackbarHostState() }
                val isMusicOnlyActive by viewModel.isMusicOnlyActive.collectAsState()
                val isRecording by viewModel.isRecording.collectAsState()
                val recordingDurationMs by viewModel.recordingDurationMs.collectAsState()
                val lastClip by viewModel.lastRecordedClip.collectAsState()
                val selectedTrack by viewModel.selectedTrack.collectAsState()
                val snackbarMsg by viewModel.snackbarMessage.collectAsState()

                // File picker for custom phone song
                val audioPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri: Uri? ->
                    if (uri != null) {
                        val fileName = queryFileName(uri) ?: "কাস্টম গান"
                        viewModel.selectCustomAudio(uri, fileName)
                    }
                }

                // Permission launcher for required permissions
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
                    if (audioGranted && !isMusicOnlyActive) {
                        viewModel.toggleMusicOnly(context)
                    }
                }

                fun handleToggleWithPermissions() {
                    val needsAudio = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) != PackageManager.PERMISSION_GRANTED

                    val needsNotif = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED

                    val permsToRequest = mutableListOf<String>()
                    if (needsAudio) permsToRequest.add(Manifest.permission.RECORD_AUDIO)
                    if (needsNotif) permsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)

                    if (permsToRequest.isNotEmpty()) {
                        permissionLauncher.launch(permsToRequest.toTypedArray())
                    } else {
                        viewModel.toggleMusicOnly(context)
                    }
                }

                LaunchedEffect(Unit) {
                    val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        perms.add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    val missing = perms.filter {
                        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                    }
                    if (missing.isNotEmpty()) {
                        permissionLauncher.launch(missing.toTypedArray())
                    }
                }

                LaunchedEffect(snackbarMsg) {
                    snackbarMsg?.let {
                        snackbarHostState.showSnackbar(it)
                        viewModel.clearSnackbar()
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    containerColor = MaterialTheme.colorScheme.background,
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        SingleButtonScreen(
                            isActive = isMusicOnlyActive,
                            isRecording = isRecording,
                            recordingDurationMs = recordingDurationMs,
                            trackTitle = selectedTrack.title,
                            onToggle = { handleToggleWithPermissions() },
                            onStartRecording = { viewModel.startRecording() },
                            onStopAndShare = { viewModel.stopRecordingAndShare(context) },
                            onStopRecording = { viewModel.stopRecording() },
                            onPickSong = { audioPickerLauncher.launch("audio/*") },
                            onShareLastClip = { lastClip?.let { viewModel.shareClip(context, it) } }
                        )
                    }
                }
            }
        }
    }

    private fun queryFileName(uri: Uri): String? {
        var name: String? = null
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    name = it.getString(index)
                }
            }
        }
        return name
    }
}

@Composable
fun SingleButtonScreen(
    isActive: Boolean,
    isRecording: Boolean,
    recordingDurationMs: Long,
    trackTitle: String,
    onToggle: () -> Unit,
    onStartRecording: () -> Unit,
    onStopAndShare: () -> Unit,
    onStopRecording: () -> Unit,
    onPickSong: () -> Unit,
    onShareLastClip: () -> Unit
) {
    val pulseAnim = remember { Animatable(1f) }

    LaunchedEffect(isActive) {
        if (isActive) {
            pulseAnim.animateTo(
                targetValue = 1.12f,
                animationSpec = infiniteRepeatable(
                    animation = tween(900, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )
        } else {
            pulseAnim.snapTo(1f)
        }
    }

    val buttonBgGradient = if (isActive) {
        Brush.linearGradient(listOf(NeonCyan, NeonPurple))
    } else {
        Brush.linearGradient(listOf(Color(0xFF1E293B), Color(0xFF0F172A)))
    }

    val borderColor by animateColorAsState(
        targetValue = if (isActive) NeonCyan else Color(0xFF334155),
        animationSpec = tween(400),
        label = "border"
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Header
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = if (isActive) NeonCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "MusicMic",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "সিস্টেম সাউন্ড ও মিউজিক কানেক্টর",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }

        // Center: Giant Single Power Button
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(210.dp)
            ) {
                // Outer glowing pulse ring when active
                if (isActive) {
                    Box(
                        modifier = Modifier
                            .size(210.dp)
                            .scale(pulseAnim.value)
                            .clip(CircleShape)
                            .background(NeonCyan.copy(alpha = 0.15f))
                    )
                    Box(
                        modifier = Modifier
                            .size(180.dp)
                            .scale((pulseAnim.value + 1f) / 2f)
                            .clip(CircleShape)
                            .background(NeonPurple.copy(alpha = 0.25f))
                    )
                }

                // Main Circular Button
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .clip(CircleShape)
                        .background(buttonBgGradient)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onToggle() }
                        .testTag("single_power_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = "Power",
                        tint = if (isActive) Color.Black else Color(0xFF94A3B8),
                        modifier = Modifier.size(72.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Status Label
            Text(
                text = if (isActive) "সক্রিয় আছে (ON) 🟢" else "বন্ধ আছে (OFF) ⚪",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) NeonCyan else MaterialTheme.colorScheme.onSurface
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (isActive) {
                    "স্পিকার ভাইব্রেশন ফিক্সড • মাইক্রোফোনে কথা বন্ধ 🔇"
                } else {
                    "চালু করতে বাটনে চাপ দিন"
                },
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = if (isActive) NeonEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                ),
                textAlign = TextAlign.Center
            )

            // Current Song badge with option to change
            if (isActive) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onPickSong() }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = "Pick song",
                        tint = NeonCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "গান: $trackTitle (বদলান)",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = NeonCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                }
            }
        }

        // Bottom Action & Assurance Area
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // If active: 1-Tap Pure Music Voice Creator for WhatsApp/Messenger
            if (isActive) {
                if (isRecording) {
                    val sec = recordingDurationMs / 1000
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = BorderStroke(1.dp, MutedRed)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "🔴 রেকর্ড হচ্ছে (${String.format("%02d:%02d", sec / 60, sec % 60)})",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MutedRed
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "আপনার কথা বাদ দিয়ে শুধু গান রেকর্ড হচ্ছে",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = onStopAndShare,
                                colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(46.dp)
                            ) {
                                Icon(Icons.Default.Send, contentDescription = null, tint = Color.Black)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("থামিয়ে সরাসরি WhatsApp/Messenger-এ পাঠান", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    Button(
                        onClick = onStartRecording,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .padding(bottom = 8.dp)
                            .testTag("record_music_voice_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.FiberManualRecord, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "কাউকে শুধু গানের ভয়েস পাঠান (WhatsApp)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Assurance Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .testTag("status_info_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                border = BorderStroke(1.dp, borderColor)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = NeonEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isActive) "কাজ সক্রিয় রয়েছে" else "অ্যাপের নিয়ম:",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isActive) NeonEmerald else MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (isActive) {
                            "স্পিকার ভাইব্রেশন এবং কর্কশ শব্দ সম্পূর্ণরূপে বন্ধ করা হয়েছে। আপনি কাউকে ভয়েস দিলে সে আপনার মুখের কথা শুনতে পাবে না—কেবল ফোনের গান শুনবে।"
                        } else {
                            "জাস্ট বাটনটি অন করে দিন। ফোনের কোনো স্পিকার ভাইব্রেট করবে না এবং মাইক্রোফোনে কথা বন্ধ হয়ে শুধু গান সক্রিয় থাকবে।"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    )
                }
            }
        }
    }
}
