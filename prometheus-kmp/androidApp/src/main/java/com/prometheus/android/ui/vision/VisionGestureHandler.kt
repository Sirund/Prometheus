package com.prometheus.android.ui.vision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.prometheus.android.inference.STTManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VisionGestureHandler(
    visionMode: VisionMode,
    isCapturing: Boolean,
    isModelLoaded: Boolean,
    cameraActions: CameraActions?,
    sttManager: STTManager,
    longPressTimeoutMs: Long,
    onModeChange: (VisionMode) -> Unit,
    onRecordedTextChange: (String?) -> Unit,
    onDescriptionChange: (String?) -> Unit,
    onFreezeChange: (Bitmap?) -> Unit,
    onCapturedImageChange: (Bitmap?) -> Unit,
    onCapturingChange: (Boolean) -> Unit,
    onSend: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var recording by remember { mutableStateOf(false) }
    var pendingTap by remember { mutableStateOf(false) }
    var lastTapTime by remember { mutableLongStateOf(0L) }

    val canInteract = isModelLoaded && !isCapturing &&
        visionMode != VisionMode.Sending &&
        visionMode != VisionMode.Transcribing &&
        visionMode != VisionMode.Result

    Box(
        modifier = Modifier
            .fillMaxSize()
            .combinedClickable(
                enabled = canInteract,
                onClick = {
                    if (!canInteract) return@combinedClickable

                    val now = System.currentTimeMillis()
                    val isDoubleTap = pendingTap && (now - lastTapTime) < 300L
                    if (isDoubleTap) {
                        pendingTap = false
                        return@combinedClickable
                    }

                    // Single tap → capture photo
                    pendingTap = true
                    lastTapTime = now
                    scope.launch {
                        delay(300L)
                        pendingTap = false
                    }

                    if (cameraActions != null && !isCapturing) {
                        onCapturingChange(true)
                        cameraActions.takePhoto { bytes ->
                            onCapturingChange(false)
                            if (bytes == null) {
                                onFreezeChange(null)
                                onDescriptionChange("Camera error. Try again.")
                                return@takePhoto
                            }
                            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            if (bitmap == null) {
                                onFreezeChange(null)
                                onDescriptionChange("Failed to decode image.")
                                return@takePhoto
                            }
                            onCapturedImageChange(bitmap)
                            onFreezeChange(bitmap)
                            onModeChange(VisionMode.PhotoCaptured)
                            scope.launch {
                                delay(1200)
                                onFreezeChange(null)
                                if (visionMode == VisionMode.PhotoCaptured) onModeChange(VisionMode.Idle)
                            }
                        }
                    }
                },
                onLongClick = {
                    if (!canInteract || recording) return@combinedClickable
                    recording = true
                    onModeChange(VisionMode.Recording)
                    sttManager.startListening(
                        onResult = { text ->
                            recording = false
                            onRecordedTextChange(text)
                            onDescriptionChange("You said: \"$text\"")
                            onModeChange(VisionMode.AudioCaptured)
                            scope.launch {
                                delay(1500)
                                onModeChange(VisionMode.Idle)
                            }
                        },
                        onError = { msg ->
                            recording = false
                            onDescriptionChange("Voice: $msg")
                            onModeChange(VisionMode.Idle)
                        }
                    )
                },
                onDoubleClick = {
                    if (!canInteract || visionMode == VisionMode.Sending) return@combinedClickable
                    onSend()
                }
            )
    )
}
