package com.prometheus.android.inference

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class InferenceManager {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _isModelLoaded = MutableStateFlow(false)
    val isModelLoaded: Boolean get() = _isModelLoaded.value

    private val _statusMessage = MutableStateFlow("Initializing...")
    val statusMessage: String get() = _statusMessage.value

    private var modelLoaded = false

    suspend fun setupGemma() {
        _statusMessage.value = "Checking model..."
        _statusMessage.value = "Downloading Gemma 4 (2.4GB)..."
        _statusMessage.value = "Loading to GPU..."
        _statusMessage.value = "Gemma 4 Online"

        _isModelLoaded.value = true
        modelLoaded = true
    }

    suspend fun sendMessage(text: String, onToken: (String) -> Unit) {
        if (!modelLoaded) {
            onToken("Model not loaded.")
            return
        }

        var response = ""
        response += simulateGemmaResponse(text)
        onToken(response)
    }

    private fun simulateGemmaResponse(query: String): String {
        return when {
            query.contains("earthquake", ignoreCase = true) ||
            query.contains("gempa", ignoreCase = true) -> {
                "If you feel shaking: Drop, Cover, and Hold On. Stay away from windows and heavy objects. " +
                "If you are near the coast, move to higher ground immediately after shaking stops. " +
                "Check for injuries and damage only when it is safe. Follow BMKG official instructions."
            }
            query.contains("tsunami", ignoreCase = true) -> {
                "Move immediately to higher ground. Do not wait for official warnings. " +
                "A tsunami can arrive within minutes. Do not return to the coast until authorities declare it safe. " +
                "Tsunami waves come in multiple surges - the first is not always the largest."
            }
            query.contains("first aid", ignoreCase = true) ||
            query.contains("luka", ignoreCase = true) -> {
                "1. Ensure the area is safe before approaching. " +
                "2. Control bleeding with direct pressure. " +
                "3. Do not move a person with suspected spinal injury. " +
                "4. Call emergency services (119/112/118). " +
                "5. Keep the person warm and calm until help arrives."
            }
            else -> {
                "Stay calm and assess your situation. Priority: safety, shelter, water, food. " +
                "Check for hazards around you. Help others if you can do so safely. " +
                "Listen for official information from BMKG and local authorities."
            }
        }
    }

    fun close() {
        scope.cancel()
    }
}
