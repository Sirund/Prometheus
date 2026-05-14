package com.prometheus.android.inference

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.prometheus.prompt.SystemPrompts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

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
                    statusMessage = "Model not found. Place a .litertlm file in app files or /sdcard."
                    return@withContext
                }

                statusMessage = "Loading model..."
                val config = EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.CPU
                )

                val newEngine = Engine(config)
                newEngine.initialize()

                engine = newEngine
                createNewConversation()
                isModelLoaded = true
                statusMessage = "Gemma 4 Online"
            } catch (e: Exception) {
                statusMessage = "Error: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    suspend fun sendMessage(text: String, onToken: (String) -> Unit) {
        val conv = conversation ?: run {
            onToken("Model not loaded.")
            return
        }
        try {
            val response = conv.sendMessage(Message.of(text))
            val responseText = response.contents
                .filterIsInstance<Content.Text>()
                .joinToString("") { it.text }
            onToken(responseText.ifEmpty { "(empty response)" })
        } catch (e: Exception) {
            onToken("Inference failed: ${e.message}")
        }
    }

    fun shutdown() {
        engine?.close()
        engine = null
        conversation = null
    }

    fun createNewConversation() {
        val systemMessage = Message.of(SystemPrompts.SURVIVAL_CHATBOT)
        conversation = engine?.createConversation(
            ConversationConfig(systemMessage = systemMessage)
        )
    }

    private fun findModelPath(): String? {
        val candidates = listOf(
            File(context.filesDir, "gemma4.litertlm"),
            File(context.getExternalFilesDir(null), "gemma4.litertlm"),
            File("/data/local/tmp/gemma4.litertlm"),
            File("/sdcard/gemma4.litertlm")
        )
        return candidates.firstOrNull { it.exists() }?.absolutePath
    }
}
