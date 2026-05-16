package com.prometheus.android.ui.vision

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.prometheus.android.inference.InferenceManager
import com.prometheus.android.inference.STTManager
import com.prometheus.android.inference.TTSManager
import com.prometheus.android.inference.VisionInferenceManager
import com.prometheus.android.ui.theme.PrometheusColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisionScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val visionManager = remember { VisionInferenceManager(context) }
    val ttsManager = remember { TTSManager(context) }
    val sttManager = remember { STTManager(context) }

    var isModelLoaded by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Initializing...") }
    var isCapturing by remember { mutableStateOf(false) }
    var capturedImage by remember { mutableStateOf<Bitmap?>(null) }
    var freezeBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var cameraActions by remember { mutableStateOf<CameraActions?>(null) }
    var visionMode by remember { mutableStateOf(VisionMode.Idle) }
    var recordedText by remember { mutableStateOf<String?>(null) }
    var description by remember { mutableStateOf<String?>(null) }
    var responseText by remember { mutableStateOf<String?>(null) }
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

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasCameraPermission = permissions[Manifest.permission.CAMERA] == true
        hasAudioPermission = permissions[Manifest.permission.RECORD_AUDIO] == true
    }

    val inferManager = remember { InferenceManager(context) }
    val longPressTimeoutMs = LocalViewConfiguration.current.longPressTimeoutMillis

    LaunchedEffect(Unit) {
        visionManager.setup()
        isModelLoaded = visionManager.isModelLoaded
        statusMessage = visionManager.statusMessage

        if (!hasCameraPermission || !hasAudioPermission) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            )
        }

        if (!isModelLoaded) {
            var existing = inferManager.getDownloadProgress()
            if (existing == null) {
                val activeId = inferManager.findActiveDownloadByUrl()
                if (activeId != null) existing = inferManager.getDownloadProgress()
            }
            if (existing != null && (existing.isRunning || existing.isPending || existing.isPaused)) {
                isDownloading = true
                downloadProgress = existing.percent
                statusMessage = "Downloading: ${existing.percent}%"
            } else if (inferManager.isDownloadComplete()) {
                inferManager.clearDownloadState()
                visionManager.setup()
                isModelLoaded = visionManager.isModelLoaded
                statusMessage = visionManager.statusMessage
            }
        }
    }

    LaunchedEffect(isDownloading) {
        if (!isDownloading) return@LaunchedEffect
        while (true) {
            delay(2000)
            val progress = inferManager.getDownloadProgress()
            if (progress != null) {
                downloadProgress = progress.percent
                statusMessage = when {
                    progress.isComplete -> "Download complete"
                    progress.isFailed -> "Download failed"
                    progress.isPaused -> "Download paused"
                    progress.isPending -> "Download pending..."
                    progress.isRunning -> "Downloading: ${progress.percent}%"
                    else -> "Downloading: ${progress.percent}%"
                }
                if (progress.isComplete) {
                    inferManager.clearDownloadState()
                    visionManager.setup()
                    isModelLoaded = visionManager.isModelLoaded
                    isDownloading = !isModelLoaded
                    return@LaunchedEffect
                }
            } else {
                if (inferManager.isDownloadComplete()) {
                    inferManager.clearDownloadState()
                    visionManager.setup()
                    isModelLoaded = visionManager.isModelLoaded
                    isDownloading = !isModelLoaded
                    return@LaunchedEffect
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            visionManager.shutdown()
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
                title = { Text("Talk to Gemma", color = PrometheusColors.blue) },
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
        Column(Modifier.fillMaxSize().padding(padding)) {

            if (!isModelLoaded) {
                Box(Modifier.weight(1f)) {
                    DownloadPrompt(
                        isDownloading = isDownloading,
                        downloadProgress = downloadProgress,
                        inferManager = inferManager,
                        onDownloadChange = { downloading, progress, msg ->
                            isDownloading = downloading
                            downloadProgress = progress
                            statusMessage = msg
                        },
                        onModelLoaded = {
                            scope.launch {
                                visionManager.setup()
                                isModelLoaded = visionManager.isModelLoaded
                            }
                        }
                    )
                }
                return@Column
            }

            // --- Camera area with gestures ---
            val borderWidth = if (visionMode == VisionMode.Recording) 3.dp else 1.dp

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                CameraFrame(
                    modifier = Modifier.fillMaxSize(),
                    hasPermission = hasCameraPermission,
                    freezeBitmap = freezeBitmap,
                    isCapturing = isCapturing,
                    borderColor = borderColorForMode(visionMode),
                    borderWidth = borderWidth,
                    onPermissionRequest = { permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)) },
                    onCameraActionsReady = { cameraActions = it }
                )

                VisionGestureHandler(
                    visionMode = visionMode,
                    isCapturing = isCapturing,
                    isModelLoaded = isModelLoaded,
                    cameraActions = cameraActions,
                    sttManager = sttManager,
                    longPressTimeoutMs = longPressTimeoutMs,
                    onModeChange = { visionMode = it },
                    onRecordedTextChange = { recordedText = it },
                    onDescriptionChange = { description = it },
                    onFreezeChange = { freezeBitmap = it },
                    onCapturedImageChange = { capturedImage = it },
                    onCapturingChange = { isCapturing = it },
                    onSend = {
                        scope.launch {
                            val text = recordedText
                            val image = capturedImage
                            responseText = null
                            if (text == null) {
                                description = "Hold to speak before sending"
                                visionMode = VisionMode.Idle
                                return@launch
                            }
                            visionMode = VisionMode.Sending
                            val sb = StringBuilder()
                            val prompt = when {
                                text != null && image == null -> "Only use the following voice input, no image provided: $text"
                                text != null -> "Describe what you see based on the image. Additional context from user: $text"
                                else -> "Describe what you see."
                            }
                            visionManager.sendMessage(
                                text = prompt,
                                imageBitmap = image
                            ) { token ->
                                sb.append(token)
                                description = sb.toString()
                            }
                            if (sb.isNotEmpty()) {
                                responseText = sb.toString()
                                visionMode = VisionMode.Result
                                ttsManager.speak(sb.toString())
                                delay(3000)
                            }
                            capturedImage = null
                            freezeBitmap = null
                            recordedText = null
                            visionMode = VisionMode.Idle
                        }
                    }
                )
            }

            // --- Description / Status row ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(PrometheusColors.surface)
                    .border(1.dp, PrometheusColors.blue.copy(alpha = 0.2f))
                    .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = when {
                            visionMode == VisionMode.Sending -> "\u23F3"
                            visionMode == VisionMode.Result -> "\u2705"
                            recordedText != null -> "\uD83C\uDF99\uFE0F"
                            capturedImage != null -> "\uD83D\uDCF8"
                            else -> "\u2139\uFE0F"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = PrometheusColors.blue.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = description ?: visionStatusText(visionMode, recordedText),
                        color = Color.Gray,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 3,
                        modifier = Modifier.weight(1f)
                    )

                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(PrometheusColors.surface.copy(alpha = 0.5f))
                    .border(1.dp, PrometheusColors.blue.copy(alpha = 0.15f))
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = if (recordedText != null) Modifier.clickable {
                        recordedText = null
                        description = null
                    } else Modifier
                ) {
                    Text(
                        if (recordedText != null) "\u2716" else "\uD83C\uDF99\uFE0F",
                        style = MaterialTheme.typography.titleLarge,
                        color = if (recordedText != null) PrometheusColors.blue else Color.Unspecified
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (recordedText != null) "Clear voice" else "Hold to speak",
                        color = Color.Gray,
                        style = MaterialTheme.typography.labelSmall)
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = if (capturedImage != null) Modifier.clickable {
                        capturedImage = null
                        freezeBitmap = null
                    } else Modifier
                ) {
                    Text(
                        if (capturedImage != null) "\u2716" else "\uD83D\uDCF7",
                        style = MaterialTheme.typography.titleLarge,
                        color = if (capturedImage != null) PrometheusColors.blue else Color.Unspecified
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (capturedImage != null) "Clear image" else "Tap to capture",
                        color = Color.Gray,
                        style = MaterialTheme.typography.labelSmall)
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = if (responseText != null) Modifier.clickable {
                        ttsManager.speak(responseText!!)
                    } else Modifier
                ) {
                    Text(
                        if (responseText != null) "\uD83D\uDD01" else "\u27A1\uFE0F",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (responseText != null) "Replay" else "Double-tap to send",
                        color = Color.Gray,
                        style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(Modifier.height(8.dp))
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
    inferManager: InferenceManager,
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
            Text("Download Gemma 4 (2.4 GB) to enable vision",
                color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))

            val isPaused = isDownloading && downloadProgress >= 0 &&
                inferManager.getDownloadProgress()?.isPaused == true
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
                            inferManager.enqueueDownload()
                            onDownloadChange(true, 0, "Download pending...")
                        }
                        isPaused -> {
                            inferManager.resumeDownload()
                            onDownloadChange(true, downloadProgress, "Downloading: $downloadProgress%")
                        }
                        else -> {
                            val ok = inferManager.pauseDownload()
                            if (ok) {
                                onDownloadChange(true, downloadProgress, "Download Paused: $downloadProgress%")
                            } else {
                                inferManager.cancelDownload()
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
