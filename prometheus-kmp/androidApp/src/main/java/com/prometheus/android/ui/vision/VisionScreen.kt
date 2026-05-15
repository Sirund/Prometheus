package com.prometheus.android.ui.vision

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraState = rememberCameraState(enabled = hasPermission)

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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PrometheusColors.cardBackground
                ),
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (isModelLoaded) Color.Green else Color(0xFFFFA500))
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = statusMessage,
                            color = Color.Gray,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            )
        },
        containerColor = PrometheusColors.darkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!hasPermission) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .padding(16.dp)
                        .background(PrometheusColors.cardBackground)
                        .border(1.dp, PrometheusColors.blue.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("\uD83D\uDCF7", style = MaterialTheme.typography.displayLarge)
                        Spacer(Modifier.height(8.dp))
                        Text("CAMERA PERMISSION REQUIRED", color = Color.White,
                            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrometheusColors.blue,
                                contentColor = Color.Black
                            )
                        ) { Text("GRANT CAMERA PERMISSION", fontWeight = FontWeight.Bold) }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .padding(horizontal = 16.dp)
                        .padding(top = 16.dp)
                        .background(PrometheusColors.cardBackground)
                        .border(1.dp, PrometheusColors.blue.copy(alpha = 0.3f))
                ) {
                    cameraState
                    if (isCapturing) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "CAPTURING...",
                                color = PrometheusColors.blue,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp)
                    .background(PrometheusColors.cardBackground)
                    .border(1.dp, PrometheusColors.blue.copy(alpha = 0.2f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isCapturing) "\u23F3" else "\uD83D\uDD0A",
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

            Spacer(Modifier.weight(1f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(PrometheusColors.cardBackground.copy(alpha = 0.5f))
                    .border(1.dp, PrometheusColors.blue.copy(alpha = 0.15f))
                    .padding(16.dp)
            ) {
                Text(
                    text = "VISION ACCESSIBILITY MODE",
                    color = PrometheusColors.blue,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Point the camera at surroundings, signage, or injuries. Gemma 4 describes what it sees in calm spoken language — no typing or screen reading needed.",
                    color = Color.Gray,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = {
                    if (!hasPermission) {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                        return@Button
                    }
                    if (isCapturing || !isModelLoaded) return@Button
                    isCapturing = true
                    description = null
                    cameraState.takePhoto { bytes ->
                        if (bytes == null) {
                            isCapturing = false
                            description = "Capture failed. Try again."
                            return@takePhoto
                        }
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bitmap == null) {
                            isCapturing = false
                            description = "Failed to decode image."
                            return@takePhoto
                        }
                        scope.launch {
                            val sb = StringBuilder()
                            visionManager.describeImage(bitmap) { token ->
                                sb.append(token)
                                description = sb.toString()
                            }
                            isCapturing = false
                            if (sb.isNotEmpty()) {
                                ttsManager.speak(sb.toString())
                            }
                        }
                    }
                },
                enabled = !isCapturing && hasPermission,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCapturing)
                        PrometheusColors.blue.copy(alpha = 0.3f)
                    else PrometheusColors.blue.copy(alpha = 0.12f),
                    contentColor = PrometheusColors.blue,
                    disabledContainerColor = PrometheusColors.blue.copy(alpha = 0.05f),
                    disabledContentColor = PrometheusColors.blue.copy(alpha = 0.3f)
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isCapturing) "\u23F3" else "\uD83D\uDCF7",
                        style = MaterialTheme.typography.displaySmall
                    )
                    Text(
                        text = if (isCapturing) "DESCRIBING..." else "TAP TO DESCRIBE SURROUNDINGS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
