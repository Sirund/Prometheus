package com.prometheus.android.ui.vision

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.res.painterResource
import com.prometheus.android.R
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.prometheus.android.ui.theme.LocalPrometheusColors
import java.io.File
import java.util.concurrent.Executors

enum class VisionMode { Idle, Recording, Transcribing, AudioCaptured, PhotoCaptured, Sending, Result }

fun borderColorForMode(mode: VisionMode, defaultBlue: Color = Color(0xFF4FC3F7)): Color {
    return when (mode) {
        VisionMode.Recording -> Color(0xFF4CAF50).copy(alpha = 0.9f)
        VisionMode.Transcribing -> Color(0xFF4CAF50).copy(alpha = 0.6f)
        VisionMode.Idle -> defaultBlue.copy(alpha = 0.3f)
        VisionMode.AudioCaptured -> Color(0xFF4CAF50).copy(alpha = 0.5f)
        VisionMode.PhotoCaptured -> defaultBlue.copy(alpha = 0.6f)
        VisionMode.Sending -> defaultBlue.copy(alpha = 0.8f)
        VisionMode.Result -> defaultBlue.copy(alpha = 0.3f)
    }
}

fun visionStatusText(mode: VisionMode, audioText: String?): String {
    return when (mode) {
        VisionMode.Idle -> "Hold \uD83C\uDF99\uFE0F Record \u00B7 Tap \uD83D\uDCF7 Capture \u00B7 Double-Tap \u27A1\uFE0F Send"
        VisionMode.Recording -> "\uD83C\uDF99\uFE0F Listening... release when done"
        VisionMode.Transcribing -> "\uD83D\uDD0D Transcribing..."
        VisionMode.AudioCaptured -> "\uD83C\uDF99\uFE0F Audio: \"${audioText?.take(50) ?: ""}\""
        VisionMode.PhotoCaptured -> "\uD83D\uDCF8 Photo captured"
        VisionMode.Sending -> "\u23F3 Sending to Gemma..."
        VisionMode.Result -> "\u2705 Done"
    }
}

@Composable
fun rememberCameraActions(
    modifier: Modifier = Modifier,
    enabled: Boolean = true
): CameraActions? {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }

    val hasPermission = ContextCompat.checkSelfPermission(
        context, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    if (!hasPermission || !enabled) return null

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                providerFuture.addListener({
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = surfaceProvider
                    }
                    try {
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageCapture
                        )
                    } catch (_: Exception) {}
                }, ContextCompat.getMainExecutor(ctx))
            }
        },
        modifier = modifier
    )

    DisposableEffect(Unit) {
        onDispose {
            try { imageCapture } catch (_: Exception) {}
        }
    }

    return remember { CameraActions(imageCapture) }
}

class CameraActions(private val imageCapture: ImageCapture) {
    fun takePhoto(onResult: (ByteArray?) -> Unit) {
        val executor = Executors.newSingleThreadExecutor()
        val file = File.createTempFile("capture", ".jpg")
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        imageCapture.takePicture(options, executor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val bytes = try { file.readBytes() } catch (_: Exception) { null }
                file.delete()
                onResult(bytes)
            }
            override fun onError(exception: ImageCaptureException) {
                onResult(null)
            }
        })
    }
}

@Composable
fun CameraFrame(
    modifier: Modifier = Modifier,
    hasPermission: Boolean,
    freezeBitmap: Bitmap?,
    isCapturing: Boolean,
    borderColor: Color = Color(0xFF4FC3F7).copy(alpha = 0.3f),
    borderWidth: Dp = 1.dp,
    onPermissionRequest: () -> Unit,
    onCameraActionsReady: (CameraActions?) -> Unit
) {
    val p = LocalPrometheusColors.current
    Box(
        modifier = modifier
            .background(p.surface)
            .border(borderWidth, borderColor)
    ) {
        if (!hasPermission) {
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(R.drawable.camera),
                    contentDescription = "Camera",
                    modifier = Modifier.size(64.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text("CAMERA PERMISSION REQUIRED", color = p.textPrimary,
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onPermissionRequest,
                    colors = ButtonDefaults.buttonColors(containerColor = p.blue, contentColor = Color.Black)
                ) { Text("GRANT CAMERA PERMISSION", fontWeight = FontWeight.Bold) }
            }
        } else {
            onCameraActionsReady(rememberCameraActions(
                modifier = Modifier.fillMaxSize(),
                enabled = freezeBitmap == null
            ))

            AnimatedVisibility(
                visible = freezeBitmap != null,
                enter = fadeIn(),
                exit = fadeOut(),
                label = "freeze_overlay"
            ) {
                freezeBitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Captured view",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            if (isCapturing) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("CAPTURING...", color = p.blue,
                        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
