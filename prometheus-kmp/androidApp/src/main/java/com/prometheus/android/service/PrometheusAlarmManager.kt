package com.prometheus.android.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.prometheus.android.ui.alert.EarthquakeAlertActivity
import com.prometheus.model.EarthquakeEvent
import com.prometheus.model.NowcastAlert
import com.prometheus.monitor.EmergencyBriefingFormatter
import java.util.Locale

class PrometheusAlarmManager(private val context: Context) {

    companion object {
        const val CHANNEL_ALERT = "prometheus_earthquake_alerts"
        const val CHANNEL_WEATHER = "prometheus_weather_warnings"
        const val CHANNEL_STATUS = "prometheus_status"
        const val ID_ALERT = 1001
        const val ID_WEATHER = 1003
        const val ID_STATUS = 1002
    }

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    init {
        createChannels()
        initTts()
    }

    private fun createChannels() {
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: Uri.parse("android.resource://android/raw/notification_audible_buzz")
        val alertChannel = NotificationChannel(
            CHANNEL_ALERT, "Earthquake Alerts", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Critical earthquake and tsunami alerts"
            enableVibration(true)
            setBypassDnd(true)
            setSound(
                alarmUri,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                    .build()
            )
        }
        val weatherChannel = NotificationChannel(
            CHANNEL_WEATHER, "Weather Warnings", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Weather Early Warning (Nowcast) alerts"
            enableVibration(true)
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
            tts?.language = Locale.US
        }
    }

    fun showStatus(active: Boolean) {
        val text = if (active) "BMKG monitoring active"
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

    private fun openAppIntent(): PendingIntent {
        val appContext = context.applicationContext
        val intent = Intent(appContext, com.prometheus.android.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(appContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun triggerAlert(event: EarthquakeEvent, gemmaBriefing: String? = null) {
        val briefing = gemmaBriefing ?: EmergencyBriefingFormatter.buildBriefingText(event)

        val alertIntent = EarthquakeAlertActivity.createIntent(context, event)
        val alertPendingIntent = PendingIntent.getActivity(
            context.applicationContext, ID_ALERT, alertIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mag = event.magnitudeValue?.let { "%.1f".format(it) } ?: "?"
        val loc = event._wilayah?.take(50) ?: "Unknown location"
        val depth = event._kedalaman ?: "?"
        val time = buildString {
            event._tanggal?.let { append(it) }
            if (event._jam != null) append(" ${event._jam}")
        }
        val tsunami = if (event.hasTsunamiPotential) " ⚠️ TSUNAMI POTENTIAL" else ""
        val felt = event._dirasakan?.take(60)?.let { "\nFelt: $it" } ?: ""

        val detail = "M$mag | Depth: $depth | $time$tsunami$felt"

        try {
            context.startActivity(alertIntent)
        } catch (_: Exception) { }

        val n = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("\uD83D\uDEA8 EARTHQUAKE DETECTED — M$mag")
            .setContentText(loc)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$detail\n\n$briefing"))
            .setPriority(NotificationManager.IMPORTANCE_MAX)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(alertPendingIntent)
            .setFullScreenIntent(alertPendingIntent, true)
            .build()
        NotificationManagerCompat.from(context).notify(ID_ALERT, n)

        speak(briefing)
    }

    fun triggerMediumAlert(event: EarthquakeEvent) {
        val mag = event.magnitudeValue?.let { "%.1f".format(it) } ?: "?"
        val loc = event._wilayah?.take(50) ?: "Unknown location"
        val depth = event._kedalaman ?: "?"
        val time = buildString {
            event._tanggal?.let { append(it) }
            if (event._jam != null) append(" ${event._jam}")
        }

        val n = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Gempa Terdeteksi — M$mag")
            .setContentText("$loc — Depth: $depth — $time")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Magnitude $mag di $loc, kedalaman $depth.\nTerasa jauh, tidak perlu panik."))
            .setPriority(NotificationManager.IMPORTANCE_DEFAULT)
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(ID_ALERT + 1, n)
    }

    private fun speak(text: String) {
        if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "alert")
    }

    fun sendNowcastNotification(alert: NowcastAlert) {
        val n = NotificationCompat.Builder(context, CHANNEL_WEATHER)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("\u26A0\uFE0F Weather Warning — ${alert.eventType}")
            .setContentText(alert.summary.take(100))
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.summary))
            .setPriority(NotificationManager.IMPORTANCE_HIGH)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(openAppIntent())
            .build()
        NotificationManagerCompat.from(context).notify(ID_WEATHER, n)
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
