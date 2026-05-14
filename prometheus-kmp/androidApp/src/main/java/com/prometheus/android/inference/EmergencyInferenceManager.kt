package com.prometheus.android.inference

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.prometheus.model.EarthquakeEvent
import com.prometheus.monitor.EmergencyBriefingFormatter
import com.prometheus.prompt.SystemPrompts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "EmergencyInference"

class EmergencyInferenceManager(private val context: Context) {

    private var engine: Engine? = null
    private var ready = false

    suspend fun ensureLoaded(): Boolean {
        if (ready) return true
        return withContext(Dispatchers.IO) {
            try {
                val modelPath = findModelPath() ?: return@withContext false
                val config = EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.CPU(),
                    cacheDir = if (modelPath.startsWith("/data/local/tmp"))
                        context.getExternalFilesDir(null)?.absolutePath
                    else null
                )
                val newEngine = Engine(config)
                newEngine.initialize()
                engine = newEngine
                ready = true
                Log.d(TAG, "Emergency engine ready")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load emergency engine", e)
                false
            }
        }
    }

    suspend fun generateBriefing(event: EarthquakeEvent): String {
        if (!ensureLoaded()) return EmergencyBriefingFormatter.buildBriefingText(event)

        return withContext(Dispatchers.IO) {
            try {
                val prompt = buildEmergencyPrompt(event)
                val systemMessage = Contents.of(
                    listOf(Content.Text(SystemPrompts.EMERGENCY_BRIEFING))
                )
                val conversation = engine!!.createConversation(
                    ConversationConfig(systemInstruction = systemMessage)
                )
                val stream = conversation.sendMessageAsync(
                    Contents.of(listOf(Content.Text(prompt)))
                )
                val response = StringBuilder()
                stream.collect { msg -> response.append(msg.toString()) }
                conversation.close()
                response.toString().ifEmpty {
                    EmergencyBriefingFormatter.buildBriefingText(event)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Briefing generation failed", e)
                EmergencyBriefingFormatter.buildBriefingText(event)
            }
        }
    }

    private fun buildEmergencyPrompt(event: EarthquakeEvent): String {
        val summary = EmergencyBriefingFormatter.formatEventSummary(event)
        return "${SystemPrompts.EMERGENCY_BRIEFING}\n\n$summary"
    }

    fun shutdown() {
        try { engine?.close() } catch (_: Exception) {}
        engine = null
        ready = false
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
