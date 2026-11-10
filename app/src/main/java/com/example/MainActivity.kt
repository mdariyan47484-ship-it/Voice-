package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Badge
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.components.AddCustomClipDialog
import com.example.ui.components.DualVolumeControlCard
import com.example.ui.components.FloatingOverlayCard
import com.example.ui.components.SoundboardGrid
import com.example.ui.components.WirelessPairingCard
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonPurple
import com.example.ui.viewmodel.VoicePrankViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: VoicePrankViewModel by viewModels()
    private val openAddTrigger = mutableStateOf(false)

    override fun onResume() {
        super.onResume()
        viewModel.updateOverlayRunningState()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("OPEN_ADD_DIALOG", false)) {
            openAddTrigger.value = true
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (intent?.getBooleanExtra("OPEN_ADD_DIALOG", false) == true) {
            openAddTrigger.value = true
        }

        setContent {
            MyApplicationTheme {
                val context = LocalContext.current
                val snackbarHostState = remember { SnackbarHostState() }

                val adbState by viewModel.adbState.collectAsState()
                val isOverlayActive by viewModel.isOverlayActive.collectAsState()
                val myMonitorVolume by viewModel.myMonitorVolume.collectAsState()
                val theirStreamVolume by viewModel.theirStreamVolume.collectAsState()
                val activePlayingClipId by viewModel.activePlayingClipId.collectAsState()
                val allClips by viewModel.allClips.collectAsState()
                val selectedCategory by viewModel.selectedCategory.collectAsState()
                val snackbarMsg by viewModel.snackbarMessage.collectAsState()

                var showAddDialog by remember { mutableStateOf(false) }

                LaunchedEffect(openAddTrigger.value) {
                    if (openAddTrigger.value) {
                        showAddDialog = true
                        openAddTrigger.value = false
                    }
                }
                var hasOverlayPermission by remember {
                    mutableStateOf(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            Settings.canDrawOverlays(context)
                        } else true
                    )
                }

                // Overlay permission launcher
                val overlayPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) {
                    hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        Settings.canDrawOverlays(context)
                    } else true
                }

                // Audio & Notif permission launcher
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { /* permissions evaluated */ }

                LaunchedEffect(Unit) {
                    val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        perms.add(Manifest.permission.POST_NOTIFICATIONS)
                        perms.add(Manifest.permission.READ_MEDIA_AUDIO)
                    } else {
                        perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
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
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(NeonCyan.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.GraphicEq,
                                            contentDescription = null,
                                            tint = NeonCyan,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Voice Prank",
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 0.5.sp
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (adbState.isConnected) NeonEmerald.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
                                            )
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = if (adbState.isConnected) "SHIZUKU ACTIVE" else "STANDALONE",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = if (adbState.isConnected) NeonEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        )
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    },
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 10.dp)
                        ) {
                            item {
                                // Dual Volume System Card (My Volume & Their Call/Mic Volume)
                                DualVolumeControlCard(
                                    myMonitorVolume = myMonitorVolume,
                                    theirStreamVolume = theirStreamVolume,
                                    onMyMonitorVolumeChange = { viewModel.setMyMonitorVolume(it) },
                                    onTheirStreamVolumeChange = { viewModel.setTheirStreamVolume(it) },
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                )
                            }

                            item {
                                // Floating Logo Bubble Controller
                                FloatingOverlayCard(
                                    isOverlayActive = isOverlayActive,
                                    hasOverlayPermission = hasOverlayPermission,
                                    onToggleOverlay = { viewModel.toggleFloatingOverlay(context) },
                                    onRequestOverlayPermission = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                            val intent = Intent(
                                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                Uri.parse("package:${context.packageName}")
                                            )
                                            overlayPermissionLauncher.launch(intent)
                                        }
                                    },
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                )
                            }

                            item {
                                // Wireless Debugging & Shizuku Pairing Card
                                WirelessPairingCard(
                                    adbState = adbState,
                                    onPair = { port, code -> viewModel.pairWirelessAdb(port, code) },
                                    onAutoConnect = { viewModel.autoActivateAdb() },
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                )
                            }

                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                                // Side-by-Side Soundboard Grid with Phone Audio Import & Clip Management
                                SoundboardGrid(
                                    clips = allClips,
                                    activePlayingId = activePlayingClipId,
                                    isPaired = adbState.isConnected,
                                    onActivatePairing = { viewModel.autoActivateAdb() },
                                    selectedCategory = selectedCategory,
                                    onCategorySelect = { viewModel.setCategory(it) },
                                    onPlayClip = { viewModel.playClip(it) },
                                    onShareClip = { viewModel.shareClip(it, context) },
                                    onDeleteClip = { viewModel.deleteClip(it) },
                                    onAddCustomClick = { showAddDialog = true },
                                    modifier = Modifier.fillMaxWidth().height(600.dp)
                                )
                            }
                        }

                        // Add Custom Clip Dialog
                        if (showAddDialog) {
                            AddCustomClipDialog(
                                onDismiss = { showAddDialog = false },
                                onAddClip = { title, duration, emoji, uri ->
                                    viewModel.addCustomClip(title, duration, emoji, uri)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
