package com.prometheus.android.ui.assistant

import android.content.Context
import android.graphics.Bitmap
import com.prometheus.model.ChatMessage
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class ConversationData(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "New conversation",
    val messages: List<ChatMessage> = emptyList()
)

internal fun saveConversations(file: File, conversations: List<ConversationData>) {
    try {
        val arr = JSONArray()
        for (conv in conversations) {
            val obj = JSONObject().apply {
                put("id", conv.id)
                put("title", conv.title)
                val msgs = JSONArray()
                for (msg in conv.messages) {
                    msgs.put(JSONObject().apply {
                        put("id", msg.id)
                        put("text", msg.text)
                        put("isUser", msg.isUser)
                        if (msg.imagePath != null) put("imagePath", msg.imagePath)
                    })
                }
                put("messages", msgs)
            }
            arr.put(obj)
        }
        file.writeText(JSONObject().apply { put("conversations", arr) }.toString())
    } catch (_: Exception) {}
}

internal fun loadConversations(file: File): List<ConversationData> {
    return try {
        if (!file.exists()) return listOf(ConversationData())
        val arr = JSONObject(file.readText()).getJSONArray("conversations")
        val list = mutableListOf<ConversationData>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val msgs = mutableListOf<ChatMessage>()
            val msgsArr = obj.getJSONArray("messages")
            for (j in 0 until msgsArr.length()) {
                val m = msgsArr.getJSONObject(j)
                msgs.add(ChatMessage(
                    id = m.getString("id"),
                    text = m.getString("text"),
                    isUser = m.getBoolean("isUser"),
                    imagePath = m.optString("imagePath", null)
                ))
            }
            list.add(ConversationData(
                id = obj.getString("id"),
                title = obj.getString("title"),
                messages = msgs
            ))
        }
        if (list.isEmpty()) listOf(ConversationData()) else list
    } catch (_: Exception) {
        listOf(ConversationData())
    }
}

internal fun saveChatImage(context: Context, bitmap: Bitmap): String {
    val dir = File(context.filesDir, "chat_images")
    dir.mkdirs()
    val file = File(dir, "chat_${System.currentTimeMillis()}.jpg")
    FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 75, out)
    }
    return file.absolutePath
}
