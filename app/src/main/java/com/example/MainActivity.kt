package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ai.ChatMessage
import com.example.ai.GeminiLiveClient
import com.example.ai.NamuPersonaBrain
import com.example.bridge.NamuCapacitorBridge
import com.example.service.NamuAccessibilityService
import com.example.service.NamuOverlayService
import com.example.ui.theme.AccentGreen
import com.example.ui.theme.CardNavy
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberPink
import com.example.ui.theme.DarkNavy
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SurfaceNavy
import com.example.ui.theme.TextDim
import com.example.ui.theme.TextLight
import com.example.vision.CameraVisionManager
import com.example.vision.ScreenCaptureManager
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var personaBrain: NamuPersonaBrain
    private lateinit var geminiLiveClient: GeminiLiveClient
    private lateinit var screenCaptureManager: ScreenCaptureManager
    private lateinit var cameraVisionManager: CameraVisionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        personaBrain = NamuPersonaBrain(this)
        geminiLiveClient = GeminiLiveClient()
        screenCaptureManager = ScreenCaptureManager(this)
        cameraVisionManager = CameraVisionManager(this)

        setContent {
            MyApplicationTheme {
                NamuApp(
                    brain = personaBrain,
                    liveClient = geminiLiveClient,
                    screenCaptureManager = screenCaptureManager,
                    cameraVisionManager = cameraVisionManager
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        personaBrain.destroy()
        geminiLiveClient.disconnect()
        cameraVisionManager.stop()
        screenCaptureManager.stopProjection()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NamuApp(
    brain: NamuPersonaBrain,
    liveClient: GeminiLiveClient,
    screenCaptureManager: ScreenCaptureManager,
    cameraVisionManager: CameraVisionManager
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Companion HUD", "OS Control Deck", "Screen Inspector", "Telemetry Logs")

    // Collect States
    val isAccessibilityBound by NamuAccessibilityService.isServiceBound.collectAsState()
    val currentApp by NamuAccessibilityService.currentForegroundApp.collectAsState()
    val isOverlayRunning by NamuOverlayService.isOverlayRunning.collectAsState()
    val namuEmotion by NamuCapacitorBridge.namuEmotion.collectAsState()
    val logs by NamuCapacitorBridge.bridgeLogs.collectAsState()
    val chatMessages by brain.messages.collectAsState()
    val isThinking by brain.isThinking.collectAsState()
    val liveState by liveClient.connectionState.collectAsState()
    val detectedEmotion by cameraVisionManager.detectedEmotion.collectAsState()

    var userCommandText by remember { mutableStateOf("") }
    var inspectedJson by remember { mutableStateOf("") }

    // Overlay Permission Checker
    var hasOverlayPermission by remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }

    // Permissions Launchers
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val micGranted = perms[Manifest.permission.RECORD_AUDIO] == true
        val camGranted = perms[Manifest.permission.CAMERA] == true
        if (camGranted) {
            cameraVisionManager.startFrontCamera(context as androidx.lifecycle.LifecycleOwner)
        }
        if (micGranted) {
            Toast.makeText(context, "Microphone access granted for Namu", Toast.LENGTH_SHORT).show()
        }
    }

    // MediaProjection Launcher
    val screenCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            screenCaptureManager.startProjection(result.resultCode, result.data!!)
            Toast.makeText(context, "MediaProjection streaming to Gemini Live", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA,
                Manifest.permission.POST_NOTIFICATIONS
            )
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = DarkNavy,
        topBar = {
            NamuTopBar(
                isAccessibilityBound = isAccessibilityBound,
                isOverlayRunning = isOverlayRunning,
                onToggleOverlay = {
                    if (!Settings.canDrawOverlays(context)) {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    } else {
                        if (isOverlayRunning) {
                            NamuOverlayService.stopOverlay(context)
                        } else {
                            NamuOverlayService.startOverlay(context)
                        }
                    }
                }
            )
        },
        bottomBar = {
            NamuCommandBar(
                text = userCommandText,
                isThinking = isThinking,
                onTextChange = { userCommandText = it },
                onSend = {
                    if (userCommandText.isNotBlank()) {
                        brain.processUserInput(userCommandText.trim())
                        userCommandText = ""
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Segmented Navigation
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = SurfaceNavy,
                contentColor = CyberCyan,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = CyberCyan
                    )
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                text = title,
                                fontSize = 11.sp,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTabIndex == index) CyberCyan else TextDim
                            )
                        }
                    )
                }
            }

            // Tab Content
            when (selectedTabIndex) {
                0 -> CompanionHUDTab(
                    brain = brain,
                    liveClient = liveClient,
                    messages = chatMessages,
                    emotion = namuEmotion,
                    isThinking = isThinking,
                    liveState = liveState,
                    detectedEmotion = detectedEmotion,
                    isAccessibilityBound = isAccessibilityBound,
                    onOpenAccessibilitySettings = {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        context.startActivity(intent)
                    },
                    onExecuteQuickAction = { actionText ->
                        brain.processUserInput(actionText)
                    }
                )
                1 -> OSControlDeckTab(
                    isAccessibilityBound = isAccessibilityBound,
                    currentApp = currentApp,
                    onStartScreenCapture = {
                        screenCaptureLauncher.launch(screenCaptureManager.createScreenCaptureIntent())
                    },
                    onExecuteCommand = { cmd ->
                        brain.processUserInput(cmd)
                    }
                )
                2 -> ScreenInspectorTab(
                    inspectedJson = inspectedJson,
                    onScanScreen = {
                        val service = NamuAccessibilityService.instance
                        inspectedJson = service?.inspectNodeTree()
                            ?: "{\n  \"error\": \"AccessibilityService not connected. Enable Namu in Settings -> Accessibility.\"\n}"
                    }
                )
                3 -> TelemetryLogsTab(logs = logs)
            }
        }
    }
}

@Composable
fun NamuTopBar(
    isAccessibilityBound: Boolean,
    isOverlayRunning: Boolean,
    onToggleOverlay: () -> Unit
) {
    Surface(
        color = SurfaceNavy,
        tonalElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (isAccessibilityBound) AccentGreen else CyberPink)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "NAMU ASSISTANT",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = CyberCyan,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Android 14 // Realme UI 5.0",
                        fontSize = 10.sp,
                        color = TextDim,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Floating HUD Overlay Toggle Button
            Button(
                onClick = onToggleOverlay,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isOverlayRunning) CyberPink.copy(alpha = 0.2f) else CyberCyan.copy(alpha = 0.15f),
                    contentColor = if (isOverlayRunning) CyberPink else CyberCyan
                ),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, if (isOverlayRunning) CyberPink else CyberCyan),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = "Floating HUD",
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isOverlayRunning) "HUD ACTIVE" else "FLOAT HUD",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun CompanionHUDTab(
    brain: NamuPersonaBrain,
    liveClient: GeminiLiveClient,
    messages: List<ChatMessage>,
    emotion: String,
    isThinking: Boolean,
    liveState: GeminiLiveClient.LiveState,
    detectedEmotion: String,
    isAccessibilityBound: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onExecuteQuickAction: (String) -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // Accessibility Permission Notice Banner
        if (!isAccessibilityBound) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberPink.copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, CyberPink),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = CyberPink,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Accessibility Engine Offline",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = TextLight
                            )
                            Text(
                                text = "Enable Namu in Accessibility settings to allow auto-tapping, swiping, and screen automation.",
                                fontSize = 11.sp,
                                color = TextDim
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onOpenAccessibilitySettings,
                            colors = ButtonDefaults.buttonColors(containerColor = CyberPink),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Enable", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }

        // 3D Avatar Companion Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceNavy),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    HolographicAvatarHead(emotion = emotion, isThinking = isThinking)

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "NAMU",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = TextLight,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "Sassy • Multimodal • Autonomous OS Agent",
                        fontSize = 11.sp,
                        color = CyberCyan
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Live Vision & Emotion Telemetry Chip
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(CardNavy)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Front Camera: $detectedEmotion",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = AccentGreen
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(CardNavy)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Emotion: ${emotion.uppercase()}",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = CyberPink
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Gemini Live Voice Connection Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(DarkNavy)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    if (liveState == GeminiLiveClient.LiveState.CONNECTED) {
                                        liveClient.disconnect()
                                    } else {
                                        liveClient.connect()
                                    }
                                },
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (liveState == GeminiLiveClient.LiveState.CONNECTED) CyberPink else CyberCyan.copy(alpha = 0.2f))
                            ) {
                                Icon(
                                    imageVector = if (liveState == GeminiLiveClient.LiveState.CONNECTED) Icons.Default.Mic else Icons.Default.MicOff,
                                    contentDescription = "Gemini Live",
                                    tint = if (liveState == GeminiLiveClient.LiveState.CONNECTED) Color.White else CyberCyan
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Gemini Live Bidi Voice",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextLight
                                )
                                Text(
                                    text = when (liveState) {
                                        GeminiLiveClient.LiveState.CONNECTED -> "Streaming audio 16kHz PCM"
                                        GeminiLiveClient.LiveState.CONNECTING -> "Connecting to Live WebSocket..."
                                        GeminiLiveClient.LiveState.ERROR_NO_KEY -> "API Key needed in Secrets"
                                        else -> "Bidirectional low-latency audio"
                                    },
                                    fontSize = 10.sp,
                                    color = TextDim
                                )
                            }
                        }

                        if (liveState == GeminiLiveClient.LiveState.CONNECTED) {
                            Button(
                                onClick = { liveClient.startLiveMicStreaming() },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("Stream Mic", fontSize = 10.sp, color = DarkNavy, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Quick Suggestion Actions
        item {
            Text(
                text = "QUICK OS COMMANDS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextDim,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickActionChip("💬 WhatsApp Rahul", "Open WhatsApp and send a message to Rahul saying I'm almost there", onExecuteQuickAction)
                QuickActionChip("▶ YouTube Lo-Fi", "Open YouTube and search for relaxing lo-fi music", onExecuteQuickAction)
                QuickActionChip("⬇ Scroll Feed", "Scroll down on the active screen", onExecuteQuickAction)
                QuickActionChip("🔍 Inspect Window", "Inspect the on-screen UI node tree and tell me what's visible", onExecuteQuickAction)
                QuickActionChip("🔔 Check Alerts", "Check my recent notifications and summarize what arrived", onExecuteQuickAction)
            }
        }

        // Conversation Feed
        items(messages) { msg ->
            ChatBubbleItem(msg = msg)
        }
    }
}

@Composable
fun HolographicAvatarHead(emotion: String, isThinking: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "avatarPulse"
    )

    val ringColor by animateColorAsState(
        targetValue = when (emotion) {
            "listening" -> AccentGreen
            "thinking" -> Color(0xFFF59E0B)
            "speaking" -> CyberPink
            "executing" -> CyberCyan
            else -> CyberCyan.copy(alpha = 0.6f)
        },
        label = "ringColor"
    )

    Box(
        modifier = Modifier
            .size(130.dp)
            .scale(pulseScale),
        contentAlignment = Alignment.Center
    ) {
        // Glowing animated outer halo
        Box(
            modifier = Modifier
                .size(126.dp)
                .clip(CircleShape)
                .border(2.dp, ringColor, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(114.dp)
                .clip(CircleShape)
                .border(1.dp, CyberPink.copy(alpha = 0.4f), CircleShape)
        )

        // Avatar Image
        Image(
            painter = painterResource(id = R.drawable.ic_namu_avatar_1789292386605),
            contentDescription = "Namu AI Avatar Head",
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
        )
    }
}

@Composable
fun QuickActionChip(label: String, prompt: String, onClick: (String) -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CardNavy)
            .border(1.dp, CyberCyan.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .clickable { onClick(prompt) }
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(text = label, fontSize = 11.sp, color = CyberCyan, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ChatBubbleItem(msg: ChatMessage) {
    val isUser = msg.isUser
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (isUser) "YOU" else "NAMU",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (isUser) CyberCyan else CyberPink,
                letterSpacing = 1.sp
            )
            if (!msg.actionExecuted.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(CyberPink.copy(alpha = 0.2f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = msg.actionExecuted,
                        fontSize = 9.sp,
                        color = CyberPink,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 2.dp,
                        bottomEnd = if (isUser) 2.dp else 16.dp
                    )
                )
                .background(if (isUser) CyberCyan.copy(alpha = 0.15f) else SurfaceNavy)
                .border(
                    1.dp,
                    if (isUser) CyberCyan.copy(alpha = 0.4f) else Color(0xFF1E293B),
                    RoundedCornerShape(16.dp)
                )
                .padding(12.dp)
        ) {
            Text(
                text = msg.text,
                fontSize = 13.sp,
                color = TextLight,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun OSControlDeckTab(
    isAccessibilityBound: Boolean,
    currentApp: String,
    onStartScreenCapture: () -> Unit,
    onExecuteCommand: (String) -> Unit
) {
    val context = LocalContext.current
    val bridge = remember { NamuCapacitorBridge(context) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceNavy),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "MOBILE OS CONTROL BRIDGE",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberCyan,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Current Foreground Package: $currentApp",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = AccentGreen
                    )
                }
            }
        }

        item {
            Text(
                text = "APP LAUNCHERS & TASK AUTOMATION",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextDim,
                letterSpacing = 1.sp
            )
        }

        // WhatsApp Automation
        item {
            OSControlActionCard(
                title = "Open WhatsApp & Chat",
                subtitle = "Launches com.whatsapp and simulates search & tap",
                icon = Icons.Default.Smartphone,
                tint = AccentGreen,
                onClick = {
                    bridge.openApp("com.whatsapp")
                }
            )
        }

        // YouTube Automation
        item {
            OSControlActionCard(
                title = "Launch YouTube",
                subtitle = "Launches com.google.android.youtube",
                icon = Icons.Default.PlayArrow,
                tint = CyberPink,
                onClick = {
                    bridge.openApp("com.google.android.youtube")
                }
            )
        }

        // Chrome Browser
        item {
            OSControlActionCard(
                title = "Launch Chrome",
                subtitle = "Launches com.android.chrome",
                icon = Icons.Default.Layers,
                tint = CyberCyan,
                onClick = {
                    bridge.openApp("com.android.chrome")
                }
            )
        }

        // System Settings
        item {
            OSControlActionCard(
                title = "Open System Settings",
                subtitle = "Launches com.android.settings",
                icon = Icons.Default.Settings,
                tint = Color(0xFFF59E0B),
                onClick = {
                    bridge.openApp("com.android.settings")
                }
            )
        }

        item {
            Text(
                text = "GESTURE & SENSORY ACTIONS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextDim,
                letterSpacing = 1.sp
            )
        }

        // Coordinate Tap
        item {
            OSControlActionCard(
                title = "Dispatch Coordinate Click (540, 960)",
                subtitle = "Simulates physical screen touch at center",
                icon = Icons.Default.TouchApp,
                tint = CyberCyan,
                onClick = {
                    bridge.click(540f, 960f)
                }
            )
        }

        // Scroll Down
        item {
            OSControlActionCard(
                title = "Scroll Screen Down",
                subtitle = "Simulates physical gesture swipe down",
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                tint = CyberPink,
                onClick = {
                    bridge.scroll("down")
                }
            )
        }

        // MediaProjection Screen Stream
        item {
            OSControlActionCard(
                title = "Start MediaProjection Stream",
                subtitle = "Streams real-time display frames to Gemini Multimodal Live",
                icon = Icons.Default.Visibility,
                tint = CyberCyan,
                onClick = onStartScreenCapture
            )
        }
    }
}

@Composable
fun OSControlActionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceNavy),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = title, tint = tint, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextLight)
                Text(text = subtitle, fontSize = 11.sp, color = TextDim)
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = TextDim,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun ScreenInspectorTab(
    inspectedJson: String,
    onScanScreen: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "ON-SCREEN UI NODE TREE",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberCyan
                )
                Text(
                    text = "Real-time AccessibilityNodeInfo inspector",
                    fontSize = 11.sp,
                    color = TextDim
                )
            }
            Button(
                onClick = onScanScreen,
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Scan", tint = DarkNavy, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Scan Now", color = DarkNavy, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF030611))
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(18.dp))
                .padding(14.dp)
        ) {
            LazyColumn {
                item {
                    Text(
                        text = if (inspectedJson.isBlank())
                            "// Tap 'Scan Now' to inspect the active window's UI element hierarchy.\n// Namu parses text, class names, view IDs, and screen coordinates for Gemini."
                        else inspectedJson,
                        color = Color(0xFF38BDF8),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
fun TelemetryLogsTab(logs: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "BRIDGE & ACTION TELEMETRY",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = CyberCyan,
            letterSpacing = 1.sp
        )
        Text(
            text = "Live gesture execution, package launches, and WebSocket packets",
            fontSize = 11.sp,
            color = TextDim
        )

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF030611))
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(18.dp))
                .padding(14.dp)
        ) {
            LazyColumn(
                reverseLayout = false,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(logs) { logEntry ->
                    Text(
                        text = logEntry,
                        color = if (logEntry.contains("ERROR") || logEntry.contains("Warning")) CyberPink else AccentGreen,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun NamuCommandBar(
    text: String,
    isThinking: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Surface(
        color = SurfaceNavy,
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = {
                    Text(
                        text = if (isThinking) "Namu is thinking..." else "Tell Namu what to do...",
                        fontSize = 12.sp,
                        color = TextDim
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyberCyan,
                    unfocusedBorderColor = Color(0xFF1E293B),
                    focusedContainerColor = DarkNavy,
                    unfocusedContainerColor = DarkNavy,
                    focusedTextColor = TextLight,
                    unfocusedTextColor = TextLight
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.width(10.dp))

            IconButton(
                onClick = onSend,
                enabled = text.isNotBlank(),
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(if (text.isNotBlank()) CyberCyan else CyberCyan.copy(alpha = 0.2f))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (text.isNotBlank()) DarkNavy else TextDim
                )
            }
        }
    }
}
