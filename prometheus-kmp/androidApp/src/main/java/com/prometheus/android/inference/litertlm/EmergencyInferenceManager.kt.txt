package com.prometheus.android.inference

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.prometheus.model.EarthquakeEvent
import com.prometheus.monitor.EmergencyBriefingFormatter
import com.prometheus.prompt.SystemPrompts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

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
                    backend = Backend.CPU
                )
                val newEngine = Engine(config)
                newEngine.initialize()
                engine = newEngine
                ready = true
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    suspend fun generateBriefing(event: EarthquakeEvent): String {
        if (!ensureLoaded()) return EmergencyBriefingFormatter.buildBriefingText(event)

        return withContext(Dispatchers.IO) {
            try {
                val prompt = buildEmergencyPrompt(event)
                val config = ConversationConfig(
                    systemMessage = Message.of(SystemPrompts.EMERGENCY_BRIEFING)
                )
                val conversation = engine!!.createConversation(config)
                val response = conversation.sendMessage(Message.of(prompt))
                val text = response.contents
                    .filterIsInstance<Content.Text>()
                    .joinToString("") { it.text }
                conversation.close()
                text.ifEmpty { EmergencyBriefingFormatter.buildBriefingText(event) }
            } catch (_: Exception) {
                EmergencyBriefingFormatter.buildBriefingText(event)
            }
        }
    }

    private fun buildEmergencyPrompt(event: EarthquakeEvent): String {
        val summary = EmergencyBriefingFormatter.formatEventSummary(event)
        return "${SystemPrompts.EMERGENCY_BRIEFING}\n\n$summary"
    }

    fun shutdown() {
        engine?.close()
        engine = null
        ready = false
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
