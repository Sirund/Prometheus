package com.prometheus.android.inference

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import java.util.Locale

class STTManager(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null
    private var onResult: ((String) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
    }

    fun startListening(
        onResult: (String) -> Unit,
        onError: ((String) -> Unit)? = null
    ) {
        this.onResult = onResult
        this.onError = onError

        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context)

        val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                val msg = when (error) {
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_AUDIO -> "Audio error"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No match found"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permission denied"
                    SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "Too many requests"
                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "Language not supported"
                    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "Language unavailable"
                    else -> "Unknown error ($error)"
                }
                this@STTManager.onError?.invoke(msg)
                this@STTManager.onResult = null
                this@STTManager.onError = null
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: ""
                if (text.isNotBlank()) {
                    this@STTManager.onResult?.invoke(text)
                } else {
                    this@STTManager.onError?.invoke("No speech detected")
                }
                this@STTManager.onResult = null
                this@STTManager.onError = null
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        recognizer?.startListening(intent)
    }

    fun stop() {
        recognizer?.stopListening()
        // Don't destroy — let onResults/onError fire naturally.
        // destroy() happens in next startListening() or shutdown().
    }

    fun shutdown() {
        stop()
    }
}
