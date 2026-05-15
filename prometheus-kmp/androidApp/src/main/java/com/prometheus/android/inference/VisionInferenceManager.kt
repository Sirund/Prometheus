package com.prometheus.android.inference

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private const val TAG = "VisionInference"
private const val MODEL_FILENAME = "gemma4.litertlm"
private const val MODEL_FILENAME_V2 = "gemma-4-E2B-it.litertlm"

class VisionInferenceManager(private val context: Context) {

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    var isModelLoaded = false
    var statusMessage = "Initializing..."

    suspend fun setup() {
        withContext(Dispatchers.IO) {
            try {
                val modelPath = findModelPath()
                if (modelPath == null) {
                    statusMessage = "Model not found"
                    Log.w(TAG, statusMessage)
                    return@withContext
                }

                statusMessage = "Loading model for vision..."
                val config = EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.CPU(),
                    visionBackend = Backend.CPU(),
                    cacheDir = if (modelPath.startsWith("/data/local/tmp"))
                        context.getExternalFilesDir(null)?.absolutePath
                    else null
                )

                val newEngine = Engine(config)
                newEngine.initialize()
                engine = newEngine
                createConversation()
                isModelLoaded = true
                statusMessage = "Vision ready"
                Log.d(TAG, "Vision model ready")
            } catch (e: Exception) {
                statusMessage = "Error: ${e.message ?: e.javaClass.simpleName}"
                Log.e(TAG, "setup failed", e)
            }
        }
    }

    suspend fun describeImage(
        imageBitmap: Bitmap,
        onToken: (String) -> Unit
    ) {
        val conv = conversation ?: run {
            onToken("Model not loaded.")
            return
        }
        withContext(Dispatchers.IO) {
            try {
                val imageFile = saveBitmapToTempFile(imageBitmap)
                val prompt = """
You are a calm, practical vision assistant for visually impaired users in a disaster situation.
Describe what you see in 2-4 short sentences. Focus on:
- People, injuries, or hazards (fires, floods, debris, downed power lines)
- Signage, exits, or evacuation-related text
- General surroundings for spatial awareness

Use plain, spoken language. Do not use markdown. Keep it brief and calm.
""".trimIndent()

                conv.sendMessageAsync(Contents.of(
                    Content.ImageFile(imageFile.absolutePath),
                    Content.Text(prompt)
                )).collect { msg ->
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

    private fun createConversation() {
        try {
            conversation = engine?.createConversation(ConversationConfig())
        } catch (e: Exception) {
            Log.e(TAG, "createConversation failed", e)
        }
    }

    fun shutdown() {
        try { conversation?.close() } catch (_: Exception) {}
        try { engine?.close() } catch (_: Exception) {}
        engine = null
        conversation = null
    }

    private fun findModelPath(): String? {
        val names = listOf(MODEL_FILENAME_V2, MODEL_FILENAME)
        val dirs = listOf(
            context.filesDir,
            context.getExternalFilesDir(null),
            File("/data/local/tmp")
        )
        for (dir in dirs) {
            for (name in names) {
                val file = File(dir, name)
                if (file.exists()) return file.absolutePath
            }
        }
        return null
    }
}
