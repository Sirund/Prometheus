package com.prometheus.android.inference

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.prometheus.prompt.SystemPrompts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "InferenceManager"

class InferenceManager(private val context: Context) {

    private var engine: Engine? = null
    private var conversation: Conversation? = null

    var isModelLoaded = false
    var statusMessage = "Initializing..."

    suspend fun setupGemma() {
        withContext(Dispatchers.IO) {
            try {
                val modelPath = findModelPath()
                if (modelPath == null) {
                    statusMessage = "Model not found. Push to:\n" +
                        "adb push gemma4.litertlm ${context.getExternalFilesDir(null)}/gemma4.litertlm"
                    Log.w(TAG, statusMessage)
                    return@withContext
                }

                Log.d(TAG, "Loading model from: $modelPath")
                statusMessage = "Loading model on CPU..."

                val config = EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.CPU(),
                    cacheDir = if (modelPath.startsWith("/data/local/tmp"))
                        context.getExternalFilesDir(null)?.absolutePath
                    else null
                )

                val newEngine = Engine(config)
                newEngine.initialize()
                Log.d(TAG, "Engine initialized")

                engine = newEngine
                createNewConversation()
                isModelLoaded = true
                statusMessage = "Gemma 4 Online"
                Log.d(TAG, "Gemma 4 ready")
            } catch (e: Exception) {
                statusMessage = "Error: ${e.message ?: e.javaClass.simpleName}"
                Log.e(TAG, "setupGemma failed", e)
            }
        }
    }

    suspend fun sendMessage(text: String, onToken: (String) -> Unit) = withContext(Dispatchers.IO) {
        val conv = conversation ?: run {
            onToken("Model not loaded.")
            return@withContext
        }
        try {
            val contents = Contents.of(listOf(Content.Text(text)))
            val response = StringBuilder()
            conv.sendMessageAsync(contents).collect { msg ->
                response.append(msg.toString())
                onToken(response.toString())
            }
            if (response.isEmpty()) {
                onToken("(empty response)")
            }
        } catch (e: Exception) {
            onToken("Inference failed: ${e.message}")
        }
    }

    fun shutdown() {
        try { conversation?.close() } catch (_: Exception) {}
        try { engine?.close() } catch (_: Exception) {}
        engine = null
        conversation = null
    }

    fun createNewConversation() {
        try {
            val systemInstruction = Contents.of(
                listOf(Content.Text(SystemPrompts.SURVIVAL_CHATBOT))
            )
            conversation = engine?.createConversation(
                ConversationConfig(systemInstruction = systemInstruction)
            )
            Log.d(TAG, "New conversation created")
        } catch (e: Exception) {
            Log.e(TAG, "createConversation failed", e)
        }
    }

    suspend fun downloadModel(onProgress: (Int) -> Unit): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val destDir = context.getExternalFilesDir(null)
                    ?: context.filesDir
                val destFile = File(destDir, "gemma4.litertlm")
                if (destFile.exists()) {
                    statusMessage = "Model already downloaded"
                    return@withContext true
                }
                destDir.mkdirs()

                statusMessage = "Downloading Gemma 4 (2.4GB)..."
                Log.d(TAG, "Downloading to: ${destFile.absolutePath}")

                val url = java.net.URL("https://huggingface.co/litert-community/gemma-4-e2b-litertlm/resolve/main/gemma4.litertlm")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 30_000
                connection.readTimeout = 60_000
                connection.connect()

                val totalBytes = connection.contentLengthLong
                val input = connection.inputStream
                val output = java.io.FileOutputStream(destFile)
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalRead = 0L

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    if (totalBytes > 0) {
                        val percent = ((totalRead * 100) / totalBytes).toInt()
                        onProgress(percent)
                        statusMessage = "Downloading: $percent%"
                    }
                }
                output.close()
                input.close()
                connection.disconnect()

                statusMessage = "Download complete. Loading model..."
                Log.d(TAG, "Downloaded to: ${destFile.absolutePath}")
                true
            } catch (e: Exception) {
                statusMessage = "Download failed: ${e.message}"
                Log.e(TAG, "Download failed", e)
                false
            }
        }
    }

    private fun findModelPath(): String? {
        val candidates = listOf(
            File(context.filesDir, "gemma4.litertlm"),
            File(context.getExternalFilesDir(null), "gemma4.litertlm"),
            File("/data/local/tmp/gemma4.litertlm"),
            File("/sdcard/gemma4.litertlm"),
            File("/sdcard/Android/data/${context.packageName}/files/gemma4.litertlm")
        )
        return candidates.firstOrNull { it.exists() }?.absolutePath
    }
}
