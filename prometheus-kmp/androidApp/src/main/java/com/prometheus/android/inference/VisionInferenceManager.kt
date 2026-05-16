package com.prometheus.android.inference

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.prometheus.prompt.SystemPrompts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private const val TAG = "VisionInference"

class VisionInferenceManager(private val context: Context) {

    private var conversation: Conversation? = null

    val isModelLoaded get() = ModelManager.isLoaded
    val statusMessage get() = ModelManager.statusMessage

    suspend fun describeImage(
        imageBitmap: Bitmap,
        prompt: String = "Describe what you see.",
        onToken: (String) -> Unit
    ) {
        sendMessage(text = prompt, imageBitmap = imageBitmap, onToken = onToken)
    }

    suspend fun sendMessage(
        text: String,
        imageBitmap: Bitmap? = null,
        onToken: (String) -> Unit
    ) {
        if (!ModelManager.isLoaded) {
            onToken("Model not loaded.")
            return
        }
        withContext(Dispatchers.IO) {
            try {
                try { conversation?.close() } catch (_: Exception) {}
                val conv = ModelManager.createConversation(SystemPrompts.VISION_ASSIST)
                if (conv == null) {
                    onToken("Failed to create conversation.")
                    return@withContext
                }
                conversation = conv

                val contents = mutableListOf<Content>()
                if (imageBitmap != null) {
                    val imageFile = saveBitmapToTempFile(imageBitmap)
                    contents.add(Content.ImageFile(imageFile.absolutePath))
                }
                contents.add(Content.Text(text))

                conv.sendMessageAsync(Contents.of(contents)).collect { msg ->
                    onToken(msg.toString())
                }
            } catch (e: Exception) {
                onToken("Vision failed: ${e.message}")
            }
        }
    }

    private fun saveBitmapToTempFile(bitmap: Bitmap): File {
        val dir = File(context.cacheDir, "vision_captures")
        dir.mkdirs()
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        return file
    }

    fun shutdown() {
        try { conversation?.close() } catch (_: Exception) {}
        conversation = null
    }
}
