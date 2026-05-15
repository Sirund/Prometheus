package com.prometheus.android.inference

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class TTSManager(context: Context) {

    private var tts: TextToSpeech? = null
    var isReady = false
    private var onDone: (() -> Unit)? = null

    init {
        tts = TextToSpeech(context) { status ->
            isReady = (status == TextToSpeech.SUCCESS)
            tts?.language = Locale.ENGLISH
        }
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (!isReady) {
            onDone?.invoke()
            return
        }
        this.onDone = onDone
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                this@TTSManager.onDone?.invoke()
            }
            override fun onError(utteranceId: String?) {
                this@TTSManager.onDone?.invoke()
            }
        })
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "vision_tts")
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        try { tts?.stop() } catch (_: Exception) {}
        try { tts?.shutdown() } catch (_: Exception) {}
        tts = null
    }
}
