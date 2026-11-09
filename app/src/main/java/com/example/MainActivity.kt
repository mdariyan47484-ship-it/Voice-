package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
                val snackbarMsg by viewModel.snackbarMessage.collectAsState()

                // Permission launcher for required permissions
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
                    if (audioGranted && !isMusicOnlyActive) {
                        viewModel.toggleMusicOnly(context)
                    }
                }

                // Check and request permissions automatically if needed
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
                    // Pre-request permissions gently on initial launch
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
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        SingleButtonScreen(
                            isActive = isMusicOnlyActive,
                            onToggle = { handleToggleWithPermissions() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SingleButtonScreen(
    isActive: Boolean,
    onToggle: () -> Unit
) {
    val pulseAnim = remember { Animatable(1f) }

    LaunchedEffect(isActive) {
        if (isActive) {
            pulseAnim.animateTo(
                targetValue = 1.15f,
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
            modifier = Modifier.padding(top = 36.dp)
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
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "সিস্টেম সাউন্ড ও মিউজিক কানেক্টর",
                style = MaterialTheme.typography.bodyMedium.copy(
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
                modifier = Modifier.size(230.dp)
            ) {
                // Outer glowing pulse ring when active
                if (isActive) {
                    Box(
                        modifier = Modifier
                            .size(230.dp)
                            .scale(pulseAnim.value)
                            .clip(CircleShape)
                            .background(NeonCyan.copy(alpha = 0.15f))
                    )
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .scale((pulseAnim.value + 1f) / 2f)
                            .clip(CircleShape)
                            .background(NeonPurple.copy(alpha = 0.25f))
                    )
                }

                // Main Circular Button
                Box(
                    modifier = Modifier
                        .size(170.dp)
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
                        modifier = Modifier.size(76.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Status Label
            Text(
                text = if (isActive) "সক্রিয় আছে (ON) 🟢" else "বন্ধ আছে (OFF) ⚪",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) NeonCyan else MaterialTheme.colorScheme.onSurface
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isActive) {
                    "মাইক্রোফোনে কথা বন্ধ 🔇 | ফোনের গান লাইভ যুক্ত 🎵"
                } else {
                    "চালু করতে বাটনে চাপ দিন"
                },
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = if (isActive) NeonEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                ),
                textAlign = TextAlign.Center
            )
        }

        // Bottom Clear Assurance Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
                .testTag("status_info_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ),
            border = BorderStroke(1.dp, borderColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isActive) Icons.Default.CheckCircle else Icons.Default.Security,
                        contentDescription = null,
                        tint = if (isActive) NeonEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isActive) "কাজ চলমান রয়েছে" else "অ্যাপটির কাজ:",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isActive) NeonEmerald else MaterialTheme.colorScheme.onSurface
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (isActive) {
                        "অ্যাপটি এখন অন রয়েছে। আপনি এখন কাউকে ভয়েস দিলে বা কলে কথা বললে আপনার মুখের কোনো কথা শোনা যাবে না—শুধুমাত্র আপনার ফোনে যে গান বাজবে সেটাই অপর প্রান্তের মানুষ শুনতে পাবে।"
                    } else {
                        "জাস্ট উপরের বাটনটি অন করে দিন। আর কোনো কিছু করতে হবে না। মাইক্রোফোনের সাথে সরাসরি ফোনের গান যুক্ত হয়ে যাবে এবং মুখের কথা স্বয়ংক্রিয়ভাবে বন্ধ থাকবে।"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                )
            }
        }
    }
}
