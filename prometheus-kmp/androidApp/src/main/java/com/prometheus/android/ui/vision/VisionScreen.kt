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
import com.prometheus.android.inference.TTSManager
import com.prometheus.android.inference.VisionInferenceManager
import com.prometheus.android.ui.theme.PrometheusColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisionScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val visionManager = remember { VisionInferenceManager(context) }
    val ttsManager = remember { TTSManager(context) }

    var isModelLoaded by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Initializing...") }
    var isCapturing by remember { mutableStateOf(false) }
    var description by remember { mutableStateOf<String?>(null) }
    var capturedImage by remember { mutableStateOf<Bitmap?>(null) }
    var cameraActions by remember { mutableStateOf<CameraActions?>(null) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    LaunchedEffect(Unit) {
        visionManager.setup()
        isModelLoaded = visionManager.isModelLoaded
        statusMessage = visionManager.statusMessage
    }

    DisposableEffect(Unit) {
        onDispose {
            visionManager.shutdown()
            ttsManager.shutdown()
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

            // --- Camera / Captured image area ---
            CameraFrame(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp, vertical = 8.dp),
                hasPermission = hasPermission,
                capturedImage = capturedImage,
                isCapturing = isCapturing,
                onPermissionRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
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

            // --- Action button ---
            Button(
                onClick = {
                    if (!hasPermission) {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                        return@Button
                    }
                    if (isCapturing || !isModelLoaded) return@Button

                    if (capturedImage != null) {
                        capturedImage = null
                        description = null
                        return@Button
                    }

                    isCapturing = true
                    description = null

                    cameraActions?.takePhoto { bytes ->
                        if (bytes == null) {
                            isCapturing = false; description = "Capture failed. Try again."
                            return@takePhoto
                        }
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bitmap == null) {
                            isCapturing = false; description = "Failed to decode image."
                            return@takePhoto
                        }
                        capturedImage = bitmap
                        isCapturing = false
                        scope.launch {
                            val sb = StringBuilder()
                            visionManager.describeImage(bitmap) { token ->
                                sb.append(token); description = sb.toString()
                            }
                            if (sb.isNotEmpty()) ttsManager.speak(sb.toString())
                        }
                    }
                },
                enabled = hasPermission,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp),
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
                        text = when { isCapturing -> "DESCRIBING..."; capturedImage != null -> "TAP FOR NEW CAPTURE"; else -> "TAP TO DESCRIBE SURROUNDINGS" },
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
    onPermissionRequest: () -> Unit,
    onCameraActionsReady: (CameraActions?) -> Unit
) {
    Box(
        modifier = modifier
            .background(PrometheusColors.surface)
            .border(1.dp, PrometheusColors.blue.copy(alpha = 0.3f))
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
