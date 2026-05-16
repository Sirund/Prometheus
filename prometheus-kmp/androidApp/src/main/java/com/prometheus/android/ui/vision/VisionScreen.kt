package com.prometheus.android.ui.vision

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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

private enum class MicState { Idle, Listening, Success, Error }

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
    var description by remember { mutableStateOf<String?>(null) }
    var capturedImage by remember { mutableStateOf<Bitmap?>(null) }
    var cameraActions by remember { mutableStateOf<CameraActions?>(null) }
    var micState by remember { mutableStateOf(MicState.Idle) }
    var cameraError by remember { mutableStateOf(false) }
    var sttText by remember { mutableStateOf<String?>(null) }
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

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
    }

    val inferManager = remember { InferenceManager(context) }

    LaunchedEffect(Unit) {
        visionManager.setup()
        isModelLoaded = visionManager.isModelLoaded
        statusMessage = visionManager.statusMessage

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vision Assist", color = PrometheusColors.blue) },
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

            // --- Download prompt + progress (unified toggle button) ---
            if (!isModelLoaded) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(32.dp),
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
                                        isDownloading = true
                                        downloadProgress = 0
                                        statusMessage = "Download pending..."
                                    }
                                    isPaused -> {
                                        inferManager.resumeDownload()
                                        statusMessage = "Downloading: $downloadProgress%"
                                    }
                                    else -> {
                                        val ok = inferManager.pauseDownload()
                                        if (ok) statusMessage = "Download Paused: $downloadProgress%"
                                        else {
                                            inferManager.cancelDownload()
                                            isDownloading = false
                                            downloadProgress = -1
                                            statusMessage = "Tap to restart."
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
                return@Column
            }

            // --- Camera / Captured image area ---
            CameraFrame(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp, vertical = 8.dp),
                hasPermission = hasCameraPermission,
                capturedImage = capturedImage,
                isCapturing = isCapturing,
                borderColor = if (cameraError) Color.Red else PrometheusColors.blue.copy(alpha = 0.3f),
                onPermissionRequest = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                onCameraActionsReady = { cameraActions = it }
            )

            // --- Description row ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(PrometheusColors.surface)
                    .border(1.dp, PrometheusColors.blue.copy(alpha = 0.2f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when {
                        isCapturing -> "\u23F3"
                        capturedImage != null -> "\uD83D\uDCF8"
                        else -> "\uD83D\uDD0A"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = PrometheusColors.blue.copy(alpha = 0.5f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = description ?: "Point camera and tap Describe to hear surroundings",
                    color = Color.Gray,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 3
                )
            }

            Spacer(Modifier.height(8.dp))

            // --- Info card ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(PrometheusColors.surface.copy(alpha = 0.5f))
                    .border(1.dp, PrometheusColors.blue.copy(alpha = 0.15f))
                    .padding(16.dp)
            ) {
                Text("VISION ACCESSIBILITY MODE",
                    color = PrometheusColors.blue,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Point the camera at surroundings, signage, or injuries. Gemma 4 describes what it sees in calm spoken language.",
                    color = Color.Gray,
                    style = MaterialTheme.typography.labelSmall)
            }

            Spacer(Modifier.height(8.dp))

            // --- Camera button ---
            Button(
                onClick = {
                    if (!hasCameraPermission) {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        return@Button
                    }
                    if (isCapturing || !isModelLoaded) return@Button

                    if (capturedImage != null) {
                        capturedImage = null
                        description = null
                        sttText = null
                        cameraError = false
                        micState = MicState.Idle
                        return@Button
                    }

                    isCapturing = true
                    description = null
                    cameraError = false

                    cameraActions?.takePhoto { bytes ->
                        if (bytes == null) {
                            isCapturing = false
                            cameraError = true
                            description = "Camera error. Try again."
                            return@takePhoto
                        }
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bitmap == null) {
                            isCapturing = false
                            cameraError = true
                            description = "Failed to decode image."
                            return@takePhoto
                        }
                        capturedImage = bitmap
                        isCapturing = false
                        val prompt = sttText?.takeIf { it.isNotBlank() } ?: "Describe what you see."
                        scope.launch {
                            val sb = StringBuilder()
                            visionManager.describeImage(bitmap, prompt = prompt) { token ->
                                sb.append(token); description = sb.toString()
                            }
                            if (sb.isNotEmpty()) {
                                micState = MicState.Success
                                ttsManager.speak(sb.toString())
                                delay(2000)
                                micState = MicState.Idle
                            }
                        }
                    }
                },
                enabled = hasCameraPermission,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrometheusColors.blue.copy(alpha = 0.12f),
                    contentColor = PrometheusColors.blue,
                    disabledContainerColor = PrometheusColors.blue.copy(alpha = 0.05f),
                    disabledContentColor = PrometheusColors.blue.copy(alpha = 0.3f)
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = when { isCapturing -> "\u23F3"; capturedImage != null -> "\uD83D\uDDBC\uFE0F"; else -> "\uD83D\uDCF7" },
                        style = MaterialTheme.typography.displaySmall)
                    Text(
                        text = when { isCapturing -> "DESCRIBING..."; capturedImage != null -> "TAP FOR NEW CAPTURE"; else -> "TAP TO DESCRIBE" },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(8.dp))

            // --- Mic button ---
            val micColor = when (micState) {
                MicState.Idle -> PrometheusColors.blue
                MicState.Listening -> PrometheusColors.blue.copy(alpha = 0.4f)
                MicState.Success -> Color.Green
                MicState.Error -> Color.Red
            }
            Button(
                onClick = {
                    if (!hasAudioPermission) {
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        return@Button
                    }
                    if (!hasCameraPermission) {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        return@Button
                    }
                    if (!isModelLoaded || isCapturing || micState == MicState.Listening) return@Button

                    micState = MicState.Listening
                    description = null
                    sttText = null
                    cameraError = false

                    sttManager.startListening(
                        onResult = { text ->
                            sttText = text
                            description = "You said: \"$text\""
                            micState = MicState.Idle

                            // Auto-capture after STT
                            isCapturing = true
                            cameraActions?.takePhoto { bytes ->
                                if (bytes == null) {
                                    isCapturing = false
                                    cameraError = true
                                    description = "Camera error. Say it again."
                                    micState = MicState.Error
                                    scope.launch { delay(2000); micState = MicState.Idle }
                                    return@takePhoto
                                }
                                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                if (bitmap == null) {
                                    isCapturing = false
                                    cameraError = true
                                    micState = MicState.Error
                                    scope.launch { delay(2000); micState = MicState.Idle }
                                    return@takePhoto
                                }
                                capturedImage = bitmap
                                isCapturing = false
                                scope.launch {
                                    val sb = StringBuilder()
                                    visionManager.describeImage(bitmap, prompt = sttText ?: "Describe what you see.") { token ->
                                        sb.append(token); description = sb.toString()
                                    }
                                    if (sb.isNotEmpty()) {
                                        micState = MicState.Success
                                        ttsManager.speak(sb.toString())
                                        delay(2000)
                                        micState = MicState.Idle
                                    }
                                }
                            }
                        },
                        onError = { errorMsg ->
                            description = "Voice: $errorMsg"
                            micState = MicState.Error
                            capturedImage = null
                            scope.launch {
                                delay(2000)
                                micState = MicState.Idle
                                if (errorMsg == "No speech detected") {
                                    description = "Tap mic and speak, or use camera button"
                                }
                            }
                        }
                    )
                },
                enabled = hasAudioPermission,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = micColor.copy(alpha = 0.12f),
                    contentColor = micColor,
                    disabledContainerColor = PrometheusColors.blue.copy(alpha = 0.05f),
                    disabledContentColor = PrometheusColors.blue.copy(alpha = 0.3f)
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = when (micState) {
                            MicState.Listening -> "\uD83C\uDF99\uFE0F"
                            MicState.Success -> "\u2705"
                            MicState.Error -> "\u274C"
                            MicState.Idle -> "\uD83C\uDF99\uFE0F"
                        },
                        style = MaterialTheme.typography.displaySmall)
                    Text(
                        text = when (micState) {
                            MicState.Listening -> "LISTENING..."
                            MicState.Success -> "DONE"
                            MicState.Error -> "ERROR"
                            MicState.Idle -> "ASK WITH VOICE"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CameraFrame(
    modifier: Modifier = Modifier,
    hasPermission: Boolean,
    capturedImage: Bitmap?,
    isCapturing: Boolean,
    borderColor: Color = PrometheusColors.blue.copy(alpha = 0.3f),
    onPermissionRequest: () -> Unit,
    onCameraActionsReady: (CameraActions?) -> Unit
) {
    Box(
        modifier = modifier
            .background(PrometheusColors.surface)
            .border(1.dp, borderColor)
    ) {
        if (!hasPermission) {
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("\uD83D\uDCF7", style = MaterialTheme.typography.displayLarge)
                Spacer(Modifier.height(8.dp))
                Text("CAMERA PERMISSION REQUIRED", color = Color.White,
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onPermissionRequest,
                    colors = ButtonDefaults.buttonColors(containerColor = PrometheusColors.blue, contentColor = Color.Black)
                ) { Text("GRANT CAMERA PERMISSION", fontWeight = FontWeight.Bold) }
            }
        } else {
            onCameraActionsReady(rememberCameraActions(
                modifier = Modifier.fillMaxSize(),
                enabled = capturedImage == null
            ))

            Crossfade(
                targetState = if (capturedImage != null) 1 else 0,
                animationSpec = tween(400),
                label = "camera_crossfade"
            ) { showCapture ->
                when {
                    showCapture == 1 -> Image(
                        bitmap = capturedImage!!.asImageBitmap(),
                        contentDescription = "Captured view",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                    else -> Box(Modifier.fillMaxSize())
                }
            }

            if (isCapturing) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("CAPTURING...", color = PrometheusColors.blue,
                        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
