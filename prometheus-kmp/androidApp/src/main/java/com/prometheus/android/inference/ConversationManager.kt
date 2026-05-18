package com.prometheus.android.inference

import android.util.Log
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.prometheus.model.ChatMessage
import com.prometheus.prompt.SystemPrompts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "ConversationManager"

class ConversationManager {

    private var conversation: Conversation? = null

    suspend fun sendMessage(
        text: String,
        history: List<ChatMessage> = emptyList(),
        systemPrompt: String = SystemPrompts.SURVIVAL_CHATBOT,
        imagePath: String? = null,
        onToken: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        if (!ModelManager.isLoaded.value) {
            onToken("Model not loaded.")
            return@withContext
        }
        try {
            val historyPrompt = buildString {
                for (msg in history.takeLast(6)) {
                    val role = if (msg.isUser) "User" else "Assistant"
                    appendLine("$role: ${msg.text}")
                }
            }

            Log.d(TAG, "=== HISTORY PROMPT ===")
            Log.d(TAG, historyPrompt)
            Log.d(TAG, "=== END HISTORY ===")
            if (imagePath != null) Log.d(TAG, "IMAGE: $imagePath")
            Log.d(TAG, "QUERY: $text")

            try { conversation?.close() } catch (_: Exception) {}
            val conv = ModelManager.createConversation(systemPrompt)
            if (conv == null) {
                onToken("Failed to create conversation.")
                return@withContext
            }
            conversation = conv

            val contentsList = mutableListOf<Content>()
            contentsList.add(Content.Text(historyPrompt))
            if (imagePath != null) {
                contentsList.add(Content.ImageFile(imagePath))
            }
            contentsList.add(Content.Text("User: $text\nAssistant:"))
            val contents = Contents.of(contentsList)

            val response = StringBuilder()
            conv.sendMessageAsync(contents).collect { msg ->
                val token = msg.toString()
                response.append(token)
                Log.d(TAG, "TOKEN: $token")
                onToken(response.toString())
            }
            Log.d(TAG, "=== FULL RESPONSE: ${response} ===")
            if (response.isEmpty()) {
                onToken("(empty response)")
            }
        } catch (e: CancellationException) {
            Log.d(TAG, "Inference cancelled by scope")
        } catch (e: Exception) {
            onToken("Inference failed: ${e.message}")
        }
    }

    fun shutdown() {
        try { conversation?.close() } catch (_: Exception) {}
        conversation = null
    }
}
