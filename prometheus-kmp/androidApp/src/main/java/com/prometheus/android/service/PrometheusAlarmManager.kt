package com.prometheus.android.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.prometheus.model.EarthquakeEvent
import com.prometheus.monitor.EmergencyBriefingFormatter
import java.util.Locale

class PrometheusAlarmManager(private val context: Context) {

    companion object {
        const val CHANNEL_ALERT = "prometheus_earthquake_alerts"
        const val CHANNEL_STATUS = "prometheus_status"
        const val ID_ALERT = 1001
        const val ID_STATUS = 1002
    }

    private var tts: TextToSpeech? = null
    private var mediaPlayer: MediaPlayer? = null
    private var ttsReady = false

    init {
        createChannels()
        initTts()
    }

    private fun createChannels() {
        val alertChannel = NotificationChannel(
            CHANNEL_ALERT, "Earthquake Alerts", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Critical earthquake and tsunami alerts"
            enableVibration(true)
            setSound(
                Uri.parse("android.resource://android/raw/notification_audible_buzz"),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }
        val statusChannel = NotificationChannel(
            CHANNEL_STATUS, "Monitoring Status", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "BMKG monitoring service status" }

        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.createNotificationChannel(alertChannel)
        mgr.createNotificationChannel(statusChannel)
    }

    private fun initTts() {
        tts = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            tts?.language = Locale.Builder().setLanguage("id").setRegion("ID").build()
        }
    }

    fun showStatus(active: Boolean) {
        val text = if (active) "BMKG monitoring active — polling every 60s"
        else "BMKG monitoring stopped"
        val n = NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Prometheus")
            .setContentText(text)
            .setPriority(NotificationManager.IMPORTANCE_LOW)
            .setOngoing(active)
            .build()
        NotificationManagerCompat.from(context).notify(ID_STATUS, n)
    }

    fun triggerAlert(event: EarthquakeEvent) {
        val briefing = EmergencyBriefingFormatter.buildBriefingText(event)
        val mag = event.magnitudeValue?.let { "%.1f".format(it) } ?: "?"
        val loc = event._wilayah?.take(50) ?: "Unknown location"

        val n = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("\uD83D\uDEA8 EARTHQUAKE DETECTED")
            .setContentText("M $mag — $loc")
            .setStyle(NotificationCompat.BigTextStyle().bigText(briefing))
            .setPriority(NotificationManager.IMPORTANCE_MAX)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(null, true)
            .build()
        NotificationManagerCompat.from(context).notify(ID_ALERT, n)

        speak(briefing)
        playAlarm()
    }

    private fun speak(text: String) {
        if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "alert")
    }

    private fun playAlarm() {
        try {
            val uri = Uri.parse("android.resource://android/raw/notification_audible_buzz")
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, uri)
                setVolume(1.0f, 1.0f)
                prepare()
                start()
                setOnCompletionListener { release() }
            }
        } catch (_: Exception) { }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
