package com.prometheus.android.inference

import android.util.Log
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.prometheus.model.ChatMessage
import com.prometheus.prompt.SystemPrompts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "InferenceManager"

class InferenceManager {

    private var conversation: Conversation? = null

    val isModelLoaded get() = ModelManager.isLoaded
    val statusMessage get() = ModelManager.statusMessage

    suspend fun sendMessage(
        text: String,
        history: List<ChatMessage> = emptyList(),
        systemPrompt: String = SystemPrompts.SURVIVAL_CHATBOT,
        imagePath: String? = null,
        onToken: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        if (!ModelManager.isLoaded) {
            onToken("Model not loaded.")
            return@withContext
        }
        try {
            val prompt = buildString {
                appendLine(systemPrompt)
                appendLine()
                for (msg in history.takeLast(6)) {
                    val role = if (msg.isUser) "User" else "Assistant"
                    appendLine("$role: ${msg.text}")
                }
                appendLine("User: $text")
                append("Assistant:")
            }

            try { conversation?.close() } catch (_: Exception) {}
            val conv = ModelManager.createConversation(systemPrompt)
            if (conv == null) {
                onToken("Failed to create conversation.")
                return@withContext
            }
            conversation = conv

            val contentsList = mutableListOf<Content>()
            if (imagePath != null) {
                contentsList.add(Content.ImageFile(imagePath))
            }
            contentsList.add(Content.Text(prompt))
            val contents = Contents.of(contentsList)

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
        conversation = null
    }

    fun createNewConversation(systemPrompt: String = SystemPrompts.SURVIVAL_CHATBOT) {
        try {
            conversation = ModelManager.createConversation(systemPrompt)
            Log.d(TAG, "New conversation created")
        } catch (e: Exception) {
            Log.e(TAG, "createConversation failed", e)
        }
    }
}
