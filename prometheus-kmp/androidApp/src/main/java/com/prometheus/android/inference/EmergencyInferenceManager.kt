package com.prometheus.android.inference

import android.util.Log
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.prometheus.model.EarthquakeEvent
import com.prometheus.monitor.EmergencyBriefingFormatter
import com.prometheus.prompt.SystemPrompts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "EmergencyInference"

class EmergencyInferenceManager {

    suspend fun generateBriefing(event: EarthquakeEvent): String {
        if (!ModelManager.isLoaded.value) {
            return EmergencyBriefingFormatter.buildBriefingText(event)
        }

        return withContext(Dispatchers.IO) {
            try {
                val prompt = buildEmergencyPrompt(event)
                val conversation = ModelManager.createConversation(SystemPrompts.EMERGENCY_BRIEFING)
                    ?: return@withContext EmergencyBriefingFormatter.buildBriefingText(event)
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
        // Engine belongs to ModelManager; nothing to close here
    }
}
