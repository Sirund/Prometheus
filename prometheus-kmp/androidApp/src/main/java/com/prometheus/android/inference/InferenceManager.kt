package com.prometheus.android.inference

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
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
import com.prometheus.model.ChatMessage
import com.prometheus.prompt.SystemPrompts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "InferenceManager"
private const val MODEL_FILENAME = "gemma4.litertlm"
const val MODEL_FILENAME_V2 = "gemma-4-E2B-it.litertlm"
private const val DOWNLOAD_URL = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"

private const val PREFS_NAME = "model_download"
private const val PREF_DOWNLOAD_ID = "download_id"
private const val PREF_DOWNLOAD_COMPLETE = "download_complete"

data class DownloadProgress(
    val bytesDownloaded: Long,
    val bytesTotal: Long,
    val status: Int,
    val reason: Int
) {
    val percent: Int get() = if (bytesTotal > 0) ((bytesDownloaded * 100) / bytesTotal).toInt() else 0
    val isComplete: Boolean get() = status == DownloadManager.STATUS_SUCCESSFUL
    val isFailed: Boolean get() = status == DownloadManager.STATUS_FAILED
    val isPaused: Boolean get() = status == DownloadManager.STATUS_PAUSED
    val isPending: Boolean get() = status == DownloadManager.STATUS_PENDING
    val isRunning: Boolean get() = status == DownloadManager.STATUS_RUNNING
}

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
                    statusMessage = "Model not found"
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

    suspend fun sendMessage(text: String, history: List<ChatMessage> = emptyList(), onToken: (String) -> Unit) = withContext(Dispatchers.IO) {
        val eng = engine ?: run {
            onToken("Model not loaded.")
            return@withContext
        }
        try {
            val prompt = buildString {
                appendLine(SystemPrompts.SURVIVAL_CHATBOT)
                appendLine()
                for (msg in history) {
                    val role = if (msg.isUser) "User" else "Assistant"
                    appendLine("$role: ${msg.text}")
                }
                appendLine("User: $text")
                append("Assistant:")
            }

            try { conversation?.close() } catch (_: Exception) {}
            val systemInstruction = Contents.of(
                listOf(Content.Text(SystemPrompts.SURVIVAL_CHATBOT))
            )
            val conv = eng.createConversation(
                ConversationConfig(systemInstruction = systemInstruction)
            )
            conversation = conv

            val contents = Contents.of(listOf(Content.Text(prompt)))
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

    fun enqueueDownload(): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        val request = DownloadManager.Request(Uri.parse(DOWNLOAD_URL))
            .setTitle("Downloading Gemma 4")
            .setDescription("2.4 GB model for on-device AI")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(false)
            .setAllowedOverRoaming(false)
            .setRequiresCharging(false)

        val id = dm.enqueue(request)
        prefs.edit()
            .putLong(PREF_DOWNLOAD_ID, id)
            .putBoolean(PREF_DOWNLOAD_COMPLETE, false)
            .apply()
        statusMessage = "Download pending..."
        Log.d(TAG, "Download enqueued: $id")
        return id
    }

    fun getDownloadProgress(): DownloadProgress? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val id = prefs.getLong(PREF_DOWNLOAD_ID, -1)
        if (id == -1L) return null

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val cursor: Cursor = dm.query(DownloadManager.Query().setFilterById(id))
        if (!cursor.moveToFirst()) {
            cursor.close()
            return null
        }

        val bytesDownloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
        val bytesTotal = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
        val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
        val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
        cursor.close()

        return DownloadProgress(bytesDownloaded, bytesTotal, status, reason)
    }

    fun isDownloadComplete(): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(PREF_DOWNLOAD_COMPLETE, false)
    }

    fun findActiveDownloadByUrl(): Long? {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val cursor = dm.query(DownloadManager.Query().setFilterByStatus(
            DownloadManager.STATUS_RUNNING or DownloadManager.STATUS_PAUSED or DownloadManager.STATUS_PENDING
        ))
        while (cursor.moveToNext()) {
            val uri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_URI))
            if (uri == DOWNLOAD_URL) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
                cursor.close()
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putLong(PREF_DOWNLOAD_ID, id).apply()
                return id
            }
        }
        cursor.close()
        return null
    }

    fun getCurrentDownloadId(): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(PREF_DOWNLOAD_ID, -1)
    }

    fun pauseDownload(): Boolean {
        val id = getCurrentDownloadId()
        if (id == -1L) return false
        return try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val method = dm.javaClass.getMethod("pauseDownload", LongArray::class.java)
            method.invoke(dm, longArrayOf(id))
            true
        } catch (e: Exception) {
            Log.w(TAG, "pauseDownload failed: ${e.message}")
            false
        }
    }

    fun resumeDownload(): Boolean {
        val id = getCurrentDownloadId()
        if (id == -1L) return false
        return try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val method = dm.javaClass.getMethod("resumeDownload", LongArray::class.java)
            method.invoke(dm, longArrayOf(id))
            true
        } catch (e: Exception) {
            Log.w(TAG, "resumeDownload failed: ${e.message}")
            false
        }
    }

    fun cancelDownload() {
        val id = getCurrentDownloadId()
        if (id == -1L) return
        try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.remove(id)
        } catch (_: Exception) {}
        clearDownloadState()
    }

    fun clearDownloadState() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(PREF_DOWNLOAD_ID)
            .remove(PREF_DOWNLOAD_COMPLETE)
            .apply()
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
