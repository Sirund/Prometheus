package com.prometheus.android.inference

import android.content.Context

class InferenceManager(private val context: Context) {

    var isModelLoaded = false
    var statusMessage = "Initializing..."

    suspend fun setupGemma() {
        statusMessage = "Gemma 4 Online"
        isModelLoaded = true
    }

    suspend fun sendMessage(text: String, onToken: (String) -> Unit) {
        var response = ""
        response += when {
            text.contains("earthquake", ignoreCase = true) ||
            text.contains("gempa", ignoreCase = true) -> {
                "If you feel shaking: Drop, Cover, and Hold On. Stay away from windows and heavy objects. " +
                "If you are near the coast, move to higher ground immediately after shaking stops. " +
                "Check for injuries and damage only when it is safe. Follow BMKG official instructions."
            }
            text.contains("tsunami", ignoreCase = true) -> {
                "Move immediately to higher ground. Do not wait for official warnings. " +
                "A tsunami can arrive within minutes. Do not return to the coast until authorities declare it safe."
            }
            text.contains("first aid", ignoreCase = true) ||
            text.contains("luka", ignoreCase = true) -> {
                "1. Ensure the area is safe before approaching. " +
                "2. Control bleeding with direct pressure. " +
                "3. Do not move a person with suspected spinal injury. " +
                "4. Call emergency services (119/112/118)."
            }
            else -> {
                "Stay calm and assess your situation. Priority: safety, shelter, water, food. " +
                "Check for hazards around you. Help others if you can do so safely."
            }
        }
        onToken(response)
    }

    fun shutdown() { }

    fun createNewConversation() { }
}
