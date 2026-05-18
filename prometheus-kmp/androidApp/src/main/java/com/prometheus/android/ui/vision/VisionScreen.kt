package com.prometheus.android.ui.vision

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.ui.res.painterResource
import com.prometheus.android.R
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.prometheus.android.inference.ConversationManager
import com.prometheus.android.inference.ModelManager
import com.prometheus.android.inference.STTManager
import com.prometheus.android.inference.TTSManager
import com.prometheus.android.ui.assistant.ConversationData
import com.prometheus.android.ui.assistant.saveChatImage
import com.prometheus.android.ui.theme.LocalPrometheusColors
import com.prometheus.model.ChatMessage
import com.prometheus.model.EarthquakeEvent
import com.prometheus.prompt.SystemPrompts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

enum class TalkMode { Idle, Recording, Transcribing, Sending, Result }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisionScreen(
    conversations: List<ConversationData>,
    activeIndex: Int,
    conversationManager: ConversationManager?,
    onConversationsChange: (List<ConversationData>) -> Unit,
    onActiveIndexChange: (Int) -> Unit,
    currentEvent: EarthquakeEvent? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val p = LocalPrometheusColors.current

    val manager = remember { conversationManager ?: ConversationManager() }
    val ttsManager = remember { TTSManager(context) }
    val sttManager = remember { STTManager(context) }

    var isModelLoaded by remember { mutableStateOf(ModelManager.isLoaded.value) }
    var statusMessage by remember { mutableStateOf(ModelManager.statusMessage.value) }
    var capturedImage by remember { mutableStateOf<Bitmap?>(null) }
    var freezeBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var cameraActions by remember { mutableStateOf<CameraActions?>(null) }
    var talkMode by remember { mutableStateOf(TalkMode.Idle) }
    var description by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableStateOf(-1) }
    var isDownloading by remember { mutableStateOf(false) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }

    // LOCAL state — bypass AnimatedContent barrier
    var localConversations by remember { mutableStateOf(conversations) }

    // STT bridging: callback → coroutine
    var pendingSttResult by remember { mutableStateOf<String?>(null) }
    var pendingSttError by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasCameraPermission = permissions[Manifest.permission.CAMERA] == true
        hasAudioPermission = permissions[Manifest.permission.RECORD_AUDIO] == true
    }

    LaunchedEffect(Unit) {
        isModelLoaded = ModelManager.isLoaded.value
        statusMessage = ModelManager.statusMessage.value

        if (!hasCameraPermission || !hasAudioPermission) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            ttsManager.shutdown()
            sttManager.shutdown()
        }
    }

    if (!hasCameraPermission || !hasAudioPermission) {
        PermissionGate(
            cameraGranted = hasCameraPermission,
            audioGranted = hasAudioPermission,
            onRequest = { permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)) }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Virtual Assistant", color = p.blue) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = p.surface)
            )
        },
        containerColor = p.background
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, top = 4.dp)) {
                Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(if (isModelLoaded) Color.Green else Color(0xFFFFA500)))
                Spacer(Modifier.width(4.dp))
                Text(if (isModelLoaded) "Gemma 4 Online" else "Initializing...", color = p.textSecondary, style = MaterialTheme.typography.labelSmall)
            }

            if (!isModelLoaded) {
                DownloadPrompt(
                    isDownloading = isDownloading,
                    downloadProgress = downloadProgress,
                    context = context,
                    onDownloadChange = { downloading, progress ->
                        isDownloading = downloading
                        downloadProgress = progress
                    },
                    onModelLoaded = {
                        isModelLoaded = ModelManager.isLoaded.value
                    }
                )
                return@Scaffold
            }

            // --- Camera preview (full area) ---
            CameraFrame(
                modifier = Modifier.fillMaxSize(),
                hasPermission = hasCameraPermission,
                freezeBitmap = freezeBitmap,
                isCapturing = talkMode == TalkMode.Sending,
                borderColor = if (talkMode == TalkMode.Recording) Color.Red else Color.Transparent,
                borderWidth = if (talkMode == TalkMode.Recording) 3.dp else 0.dp,
                onPermissionRequest = { permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)) },
                onCameraActionsReady = { cameraActions = it }
            )

            // --- Mic button overlay ---
            MicButton(
                talkMode = talkMode,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = (-100).dp)
                    .size(72.dp),
                onPress = {
                    if (talkMode == TalkMode.Idle || talkMode == TalkMode.Result) {
                        description = null
                        pendingSttResult = null
                        pendingSttError = null
                        talkMode = TalkMode.Recording
                        sttManager.startListening(
                            onResult = { text -> pendingSttResult = text },
                            onError = { error -> pendingSttError = error }
                        )
                    }
                },
                onRelease = {
                    if (talkMode == TalkMode.Recording) {
                        sttManager.stop()
                        talkMode = TalkMode.Transcribing
                        scope.launch {
                            // Poll for STT result (max 5s) — STTManager no longer destroys, callbacks fire naturally
                            var text: String? = null
                            repeat(50) {
                                if (pendingSttResult != null) {
                                    text = pendingSttResult
                                    return@repeat
                                }
                                if (pendingSttError != null) return@repeat
                                delay(100)
                            }
                            if (text == null || text.isEmpty()) {
                                talkMode = TalkMode.Idle
                                return@launch
                            }

                            // Auto-capture photo via CameraActions callback
                            val photoBytes = withContext(Dispatchers.Default) {
                                suspendCancellableCoroutine<ByteArray?> { cont ->
                                    cameraActions?.takePhoto { bytes ->
                                        cont.resume(bytes)
                                    } ?: cont.resume(null)
                                }
                            }
                            val bmp = if (photoBytes != null) {
                                BitmapFactory.decodeByteArray(photoBytes, 0, photoBytes.size)
                            } else null
                            capturedImage = bmp
                            freezeBitmap = bmp
                            talkMode = TalkMode.Sending
                            delay(1200)

                            // Save image to permanent storage
                            val savedPath = if (capturedImage != null) {
                                withContext(Dispatchers.IO) { saveChatImage(context, capturedImage!!) }
                            } else null

                            val history = localConversations.getOrNull(activeIndex)?.messages ?: emptyList()
                            val oldSize = history.size
                            val aiIndex = oldSize + 1

                            // Use local currentList to avoid stale conversations
                            var currentList = localConversations.toMutableList()
                            currentList[activeIndex] = currentList[activeIndex].copy(
                                messages = history + ChatMessage(text = text, isUser = true, imagePath = savedPath) + ChatMessage(text = "...", isUser = false)
                            )
                            localConversations = currentList
                            onConversationsChange(currentList)

                            val bmkgCtx = SystemPrompts.buildBmkgContext(currentEvent)
                            val sysPrompt = if (bmkgCtx.isNotBlank()) "$bmkgCtx\n\n${SystemPrompts.GENERAL_PROMPT}"
                                            else SystemPrompts.GENERAL_PROMPT
                            var isSpeaking = false
                            manager.sendMessage(
                                text = text,
                                history = history,
                                systemPrompt = sysPrompt,
                                imagePath = savedPath
                            ) { responseText ->
                                description = responseText
                                // Update AI response using currentList
                                currentList = currentList.toMutableList()
                                val msgs = currentList[activeIndex].messages.toMutableList()
                                if (aiIndex < msgs.size) {
                                    msgs[aiIndex] = ChatMessage(text = responseText, isUser = false)
                                }
                                currentList[activeIndex] = currentList[activeIndex].copy(messages = msgs)
                                localConversations = currentList
                                onConversationsChange(currentList)
                            }

                            val responseText = description ?: ""
                            if (responseText.isNotEmpty() && !isSpeaking) {
                                isSpeaking = true
                                talkMode = TalkMode.Result
                                ttsManager.speak(responseText)
                                delay(3000)
                                isSpeaking = false
                            }

                            talkMode = TalkMode.Idle
                            capturedImage = null
                            freezeBitmap = null
                        }
                    }
                }
            )

            // --- Process info (above mic button, on screen) ---
            when (talkMode) {
                TalkMode.Idle -> Text(
                    text = "Hold to Speak",
                    color = p.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = (-190).dp)
                )
                TalkMode.Transcribing -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = (-190).dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = p.blue
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Transcribing the audio...", color = p.blue, style = MaterialTheme.typography.labelSmall)
                }
                TalkMode.Sending -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = (-190).dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = p.blue
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Analyzing the input...", color = p.blue, style = MaterialTheme.typography.labelSmall)
                }
                else -> {}
            }

            // --- Response box (bottom) — only shows content, not process info ---
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .background(p.surface.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
                    .border(1.dp, p.blue.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
                    .heightIn(max = 80.dp)
                    .clickable(enabled = false) {}
            ) {
                when (talkMode) {
                    TalkMode.Idle -> Text(
                        text = description ?: "Say something",
                        color = if (description != null) Color.White else p.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = if (description != null) 3 else 1
                    )
                    TalkMode.Recording -> Text(
                        text = "...",
                        color = p.textSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    TalkMode.Recording -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Icon(Icons.Filled.Mic, contentDescription = "Mic", modifier = Modifier.size(24.dp), tint = p.blue)
                        Spacer(Modifier.width(8.dp))
                        Text("Listening...", color = p.blue, fontWeight = FontWeight.Bold)
                    }
                    TalkMode.Transcribing -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = p.blue
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Transcribing voice...", color = p.textSecondary)
                    }
                    TalkMode.Sending -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = p.blue
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Analyzing...", color = p.textSecondary)
                    }
                    TalkMode.Result -> Text(
                        text = description ?: "",
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3
                    )
                }
            }
        }
    }
}

@Composable
private fun MicButton(
    talkMode: TalkMode,
    modifier: Modifier,
    onPress: () -> Unit,
    onRelease: () -> Unit
) {
    val p = LocalPrometheusColors.current
    val infiniteTransition = rememberInfiniteTransition(label = "mic")
    val breathingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val isRecording = talkMode == TalkMode.Recording
    val isTranscribing = talkMode == TalkMode.Transcribing

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onPress()
                    waitForUpOrCancellation()
                    onRelease()
                }
            }
            .scale(if (isRecording) pulseScale else 1f)
            .alpha(if (isRecording) 1f else if (talkMode == TalkMode.Idle) breathingAlpha else 0.4f),
        contentAlignment = Alignment.Center
    ) {
        if (isTranscribing) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                strokeWidth = 3.dp,
                color = p.blue
            )
        } else {
            // Pulse ring for recording
            if (isRecording) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(Color.Red.copy(alpha = 0.2f))
                )
            }
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(if (isRecording) Color.Red.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Mic,
                    contentDescription = "Mic",
                    tint = if (isRecording) Color.Red else Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun PermissionGate(
    cameraGranted: Boolean,
    audioGranted: Boolean,
    onRequest: () -> Unit
) {
    val p = LocalPrometheusColors.current
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painter = painterResource(R.drawable.camera), contentDescription = "Camera", modifier = Modifier.size(36.dp))
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Filled.Mic, contentDescription = "Mic", modifier = Modifier.size(36.dp), tint = p.blue)
            }
            Spacer(Modifier.height(12.dp))
            Text("PERMISSIONS REQUIRED", color = p.textPrimary,
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            val missing = buildList {
                if (!cameraGranted) add("Camera")
                if (!audioGranted) add("Microphone")
            }
            Text("Grant ${missing.joinToString(" & ")} to use Talk to Gemma",
                color = p.textSecondary, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onRequest,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = p.blue, contentColor = Color.Black)
            ) { Text("GRANT PERMISSIONS", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun DownloadPrompt(
    isDownloading: Boolean,
    downloadProgress: Int,
    context: android.content.Context,
    onDownloadChange: (Boolean, Int) -> Unit,
    onModelLoaded: () -> Unit
) {
    val p = LocalPrometheusColors.current
    Box(
        modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.SmartToy, contentDescription = "Model", modifier = Modifier.size(48.dp), tint = p.blue)
            Spacer(Modifier.height(8.dp))
            Text("MODEL NOT FOUND", color = p.textPrimary,
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Download Gemma 4 (2.4 GB) to enable TALK",
                color = p.textSecondary, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))

            val btnColor = if (!isDownloading) p.blue else p.blue.copy(alpha = 0.6f)
            val btnText = when {
                !isDownloading -> "DOWNLOAD MODEL (2.4 GB)"
                downloadProgress >= 0 -> "Downloading: $downloadProgress%"
                else -> "Starting..."
            }
            Button(
                onClick = {
                    if (!isDownloading) {
                        ModelManager.enqueueDownload(context)
                        onDownloadChange(true, 0)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = btnColor,
                    contentColor = Color.Black
                )
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (isDownloading && downloadProgress in 0..99) {
                        LinearProgressIndicator(
                            progress = { downloadProgress / 100f },
                            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                            color = p.blue.copy(alpha = 0.3f),
                            trackColor = Color.Transparent
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val icon = when {
                            !isDownloading -> Icons.Filled.Download
                            downloadProgress >= 100 -> Icons.Filled.CheckCircle
                            else -> Icons.Filled.HourglassEmpty
                        }
                        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(btnText, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
