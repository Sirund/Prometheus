package com.prometheus.android.inference

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.prometheus.android.inference.download.DownloadWorker
import com.prometheus.android.inference.download.InitEngineWorker
import com.prometheus.android.inference.download.VerifyWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "ModelManager"
private const val MODEL_FILENAME = "gemma4.litertlm"
const val MODEL_FILENAME_V2 = "gemma-4-E2B-it.litertlm"

object ModelManager {

    private var engine: Engine? = null
    private var appContext: Context? = null
    private val sessionLock = Any()
    private var currentConversation: Conversation? = null
    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()
    private val _statusMessage = MutableStateFlow("Initializing...")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    suspend fun init(context: Context) {
        if (engine != null) return
        appContext = context.applicationContext
        withContext(Dispatchers.IO) {
            try {
                val modelPath = findModelPath(context)
                if (modelPath == null) {
                    _statusMessage.value = "Model Not Found"
                    Log.w(TAG, _statusMessage.value)
                    return@withContext
                }

                Log.d(TAG, "Loading model from: $modelPath")

                var backendUsed = "CPU"
                val newEngine = try {
                    _statusMessage.value = "Loading model on GPU..."
                    val gpuConfig = EngineConfig(
                        modelPath = modelPath,
                        backend = Backend.GPU(),
                        visionBackend = Backend.GPU(),
                        cacheDir = null
                    )
                    val eng = Engine(gpuConfig)
                    eng.initialize()
                    backendUsed = "GPU"
                    Log.d(TAG, "Engine initialized (GPU)")
                    eng
                } catch (e: Exception) {
                    Log.w(TAG, "GPU init failed, falling back to CPU: ${e.message}")
                    _statusMessage.value = "GPU unavailable, loading on CPU..."
                    val cpuConfig = EngineConfig(
                        modelPath = modelPath,
                        backend = Backend.CPU(),
                        visionBackend = Backend.CPU(),
                        cacheDir = null
                    )
                    val eng = Engine(cpuConfig)
                    eng.initialize()
                    Log.d(TAG, "Engine initialized (CPU fallback)")
                    eng
                }

                engine = newEngine
                _isLoaded.value = true
                _statusMessage.value = "Gemma 4 Online ($backendUsed)"
                Log.d(TAG, "Model ready (shared engine)")
            } catch (e: Exception) {
                _statusMessage.value = "Model Not Found"
                Log.e(TAG, "ModelManager init failed", e)
            }
        }
    }

    fun createConversation(systemPrompt: String): Conversation? {
        synchronized(sessionLock) {
            try {
                currentConversation?.close()
            } catch (_: Exception) {}
            currentConversation = null
            return try {
                val conv = engine?.createConversation(
                    ConversationConfig(
                        systemInstruction = Contents.of(listOf(Content.Text(systemPrompt)))
                    )
                )
                currentConversation = conv
                conv
            } catch (e: Exception) {
                Log.e(TAG, "createConversation failed", e)
                null
            }
        }
    }

    fun shutdown() {
        synchronized(sessionLock) {
            try { currentConversation?.close() } catch (_: Exception) {}
            currentConversation = null
        }
        try { engine?.close() } catch (_: Exception) {}
        engine = null
        _isLoaded.value = false
        appContext = null
    }

    fun reload(context: Context) {
        Log.d(TAG, "Reloading engine...")
        synchronized(sessionLock) {
            try { currentConversation?.close() } catch (_: Exception) {}
            currentConversation = null
            try { engine?.close() } catch (_: Exception) {}
            engine = null
            _isLoaded.value = false
            _statusMessage.value = "Reloading model..."
        }
        appContext = context.applicationContext
        GlobalScope.launch(Dispatchers.Default) {
            init(context)
        }
    }

    fun isModelFileExists(context: Context): Boolean {
        return findModelPath(context) != null
    }

    fun findModelPath(context: Context): String? {
        val names = listOf(MODEL_FILENAME_V2, MODEL_FILENAME)
        val dirs = listOf(
            context.filesDir,
            context.getExternalFilesDir(null)
        )
        for (dir in dirs) {
            for (name in names) {
                val file = File(dir, name)
                if (file.exists()) return file.absolutePath
            }
        }
        return null
    }

    fun enqueueDownload(context: Context, allowCellular: Boolean = false) {
        val constraints = Constraints.Builder().apply {
            setRequiresStorageNotLow(true)
            if (!allowCellular) setRequiredNetworkType(NetworkType.UNMETERED)
        }.build()

        val downloadRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .addTag("model_download")
            .build()

        val verifyRequest = OneTimeWorkRequestBuilder<VerifyWorker>()
            .addTag("model_verify")
            .build()

        val initRequest = OneTimeWorkRequestBuilder<InitEngineWorker>()
            .addTag("model_init")
            .build()

        WorkManager.getInstance(context)
            .beginUniqueWork("model_download", ExistingWorkPolicy.KEEP, downloadRequest)
            .then(verifyRequest)
            .then(initRequest)
            .enqueue()

        _statusMessage.value = "Download pending..."
        Log.d(TAG, "Download enqueued via WorkManager")
    }

    fun cancelDownload(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork("model_download")
        _statusMessage.value = "Download cancelled"
        Log.d(TAG, "Download cancelled")
    }
}
