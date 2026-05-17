package com.prometheus.android.ui.vision

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
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
import androidx.compose.material.icons.filled.Mic
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
import com.prometheus.android.ui.theme.PrometheusColors
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

    val manager = remember { conversationManager ?: ConversationManager() }
    val ttsManager = remember { TTSManager(context) }
    val sttManager = remember { STTManager(context) }

    var isModelLoaded by remember { mutableStateOf(ModelManager.isLoaded) }
    var statusMessage by remember { mutableStateOf(ModelManager.statusMessage) }
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
        isModelLoaded = ModelManager.isLoaded
        statusMessage = ModelManager.statusMessage

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
                title = { Text("Virtual Assistant", color = PrometheusColors.blue) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PrometheusColors.surface),
                actions = {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                        Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(if (isModelLoaded) Color.Green else Color(0xFFFFA500)))
                        Spacer(Modifier.width(4.dp))
                        Text(statusMessage, color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                    }
                }
            )
        },
        containerColor = PrometheusColors.background
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {

            if (!isModelLoaded) {
                DownloadPrompt(
                    isDownloading = isDownloading,
                    downloadProgress = downloadProgress,
                    context = context,
                    onDownloadChange = { downloading, progress, msg ->
                        isDownloading = downloading
                        downloadProgress = progress
                        statusMessage = msg
                    },
                    onModelLoaded = {
                        isModelLoaded = ModelManager.isLoaded
                        statusMessage = ModelManager.statusMessage
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
                    if (talkMode == TalkMode.Idle) {
                        pendingSttResult = null
                        pendingSttError = null
                        talkMode = TalkMode.Recording
                        description = "Listening..."
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
                        description = "Transcribing voice..."
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
                                description = "No speech detected"
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
                            description = "Analyzing..."
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

                            capturedImage = null
                            freezeBitmap = null
                            talkMode = TalkMode.Idle
                            description = null
                        }
                    }
                }
            )

            // --- Status box (bottom) — clickable(false) blocks gesture pass-through ---
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .background(PrometheusColors.surface.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
                    .border(1.dp, PrometheusColors.blue.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
                    .heightIn(max = 80.dp)
                    .clickable(enabled = false) {}
            ) {
                when (talkMode) {
                    TalkMode.Idle -> Text(
                        text = description ?: "Hold mic to ask",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    TalkMode.Recording -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Text("\uD83C\uDF99\uFE0F", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.width(8.dp))
                        Text("Listening...", color = PrometheusColors.blue, fontWeight = FontWeight.Bold)
                    }
                    TalkMode.Transcribing -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = PrometheusColors.blue
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Transcribing voice...", color = Color.Gray)
                    }
                    TalkMode.Sending -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = PrometheusColors.blue
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Analyzing...", color = Color.Gray)
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
                color = PrometheusColors.blue
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
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("\uD83D\uDCF7\uD83C\uDF99\uFE0F", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(12.dp))
            Text("PERMISSIONS REQUIRED", color = Color.White,
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            val missing = buildList {
                if (!cameraGranted) add("Camera")
                if (!audioGranted) add("Microphone")
            }
            Text("Grant ${missing.joinToString(" & ")} to use Talk to Gemma",
                color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onRequest,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrometheusColors.blue, contentColor = Color.Black)
            ) { Text("GRANT PERMISSIONS", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun DownloadPrompt(
    isDownloading: Boolean,
    downloadProgress: Int,
    context: android.content.Context,
    onDownloadChange: (Boolean, Int, String) -> Unit,
    onModelLoaded: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("\uD83E\uDD16", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(8.dp))
            Text("MODEL NOT FOUND", color = Color.White,
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Download Gemma 4 (2.4 GB) to enable TALK",
                color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))

            val isPaused = isDownloading && downloadProgress >= 0 &&
                ModelManager.getDownloadProgress(context)?.isPaused == true
            val btnColor = when {
                !isDownloading -> PrometheusColors.blue
                isPaused -> Color(0xFFFFA500).copy(alpha = 0.6f)
                else -> PrometheusColors.blue.copy(alpha = 0.6f)
            }
            val btnText = when {
                !isDownloading -> "\u2B07\uFE0F  DOWNLOAD MODEL (2.4 GB)"
                isPaused -> "\u25B6\uFE0F  Download Paused: $downloadProgress%"
                downloadProgress < 0 -> "\u23F3  Starting..."
                downloadProgress >= 100 -> "\u2705  Moving file..."
                else -> "\u23F8\uFE0F  Downloading: $downloadProgress%"
            }
            Button(
                onClick = {
                    when {
                        !isDownloading -> {
                            ModelManager.enqueueDownload(context)
                            onDownloadChange(true, 0, "Download pending...")
                        }
                        isPaused -> {
                            ModelManager.resumeDownload(context)
                            onDownloadChange(true, downloadProgress, "Downloading: $downloadProgress%")
                        }
                        else -> {
                            val ok = ModelManager.pauseDownload(context)
                            if (ok) {
                                onDownloadChange(true, downloadProgress, "Download Paused: $downloadProgress%")
                            } else {
                                ModelManager.cancelDownload(context)
                                onDownloadChange(false, -1, "Tap to restart.")
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = btnColor,
                    contentColor = Color.Black
                )
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (isDownloading && downloadProgress in 0..99 && !isPaused) {
                        LinearProgressIndicator(
                            progress = { downloadProgress / 100f },
                            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                            color = PrometheusColors.blue.copy(alpha = 0.3f),
                            trackColor = Color.Transparent
                        )
                    }
                    Text(btnText, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
