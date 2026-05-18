package com.prometheus.android.ui.alert

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prometheus.android.ui.theme.LocalPrometheusColors
import com.prometheus.android.ui.theme.PrometheusTheme
import com.prometheus.monitor.EmergencyBriefingFormatter
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder

class EarthquakeAlertActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_EVENT_JSON = "event_json"

        fun createIntent(context: Context, event: com.prometheus.model.EarthquakeEvent): Intent {
            val json = StringBuilder()
            json.append("{")
            json.append("\"mag\":\"${event._magnitude ?: "--"}\",")
            json.append("\"loc\":\"${event._wilayah?.let { URLEncoder.encode(it, "UTF-8") } ?: "Unknown"}\",")
            json.append("\"depth\":\"${event._kedalaman ?: "--"}\",")
            json.append("\"felt\":\"${event._dirasakan?.let { URLEncoder.encode(it, "UTF-8") } ?: "--"}\",")
            json.append("\"potensi\":\"${event._potensi?.let { URLEncoder.encode(it, "UTF-8") } ?: "--"}\",")
            json.append("\"lat\":\"${event.Lintang ?: ""}\",")
            json.append("\"lon\":\"${event.Bujur ?: ""}\",")
            json.append("\"tanggal\":\"${event._tanggal ?: ""}\",")
            json.append("\"jam\":\"${event._jam ?: ""}\",")
            json.append("\"tsunami\":${event.hasTsunamiPotential},")
            json.append("\"coord\":\"${event._coordinates ?: ""}\"")
            json.append("}")
            return Intent(context, EarthquakeAlertActivity::class.java).apply {
                putExtra(EXTRA_EVENT_JSON, json.toString())
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }

    }

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setTurnScreenOn(true)
            setShowWhenLocked(true)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        val eventJson = intent?.getStringExtra(EXTRA_EVENT_JSON)
        val event = parseEvent(eventJson)

        startAlarm()
        startVibration()

        setContent {
            PrometheusTheme(darkTheme = true) {
                AlertContent(
                    event = event,
                    onDismiss = {
                        stopAlarm()
                        stopVibration()
                        finishAndRemoveTask()
                    },
                    onOpenApp = {
                        stopAlarm()
                        stopVibration()
                        val appIntent = packageManager.getLaunchIntentForPackage(packageName)
                        if (appIntent != null) {
                            appIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            startActivity(appIntent)
                        }
                        finish()
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        stopAlarm()
        stopVibration()
        super.onDestroy()
    }

    @Deprecated("Blocked — user must explicitly dismiss", level = DeprecationLevel.HIDDEN)
    override fun onBackPressed() {
    }

    private fun startAlarm() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                        .build()
                )
                setDataSource(this@EarthquakeAlertActivity, uri)
                isLooping = true
                setVolume(1.0f, 1.0f)
                prepare()
                start()
            }
        } catch (_: Exception) { }
    }

    private fun stopAlarm() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) { }
        mediaPlayer = null
    }

    private fun startVibration() {
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }
            val pattern = longArrayOf(0, 400, 200, 400, 200, 800, 400, 200)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createWaveform(pattern, 1),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 1)
            }
        } catch (_: Exception) { }
    }

    private fun stopVibration() {
        try { vibrator?.cancel() } catch (_: Exception) { }
    }

    data class AlertEventData(
        val mag: String,
        val loc: String,
        val depth: String,
        val felt: String,
        val potensi: String,
        val lat: String,
        val lon: String,
        val tanggal: String,
        val jam: String,
        val tsunami: Boolean,
        val coord: String
    )

    private fun parseEvent(json: String?): AlertEventData? {
        if (json == null) return null
        return try {
            val obj = JSONObject(json)
            AlertEventData(
                mag = obj.optString("mag", "--"),
                loc = URLDecoder.decode(obj.optString("loc", "Unknown"), "UTF-8"),
                depth = obj.optString("depth", "--"),
                felt = URLDecoder.decode(obj.optString("felt", "--"), "UTF-8"),
                potensi = URLDecoder.decode(obj.optString("potensi", "--"), "UTF-8"),
                lat = obj.optString("lat", ""),
                lon = obj.optString("lon", ""),
                tanggal = obj.optString("tanggal", ""),
                jam = obj.optString("jam", ""),
                tsunami = obj.optBoolean("tsunami", false),
                coord = obj.optString("coord", "")
            )
        } catch (_: Exception) { null }
    }
}

@Composable
private fun AlertContent(
    event: EarthquakeAlertActivity.AlertEventData?,
    onDismiss: () -> Unit,
    onOpenApp: () -> Unit
) {
    val p = LocalPrometheusColors.current
    val dangerRed = Color(0xFFD32F2F)
    val darkBg = Color(0xFF1A0000)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(darkBg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "\uD83D\uDEA8",
                fontSize = 64.sp
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "EARTHQUAKE DETECTED",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = dangerRed,
                textAlign = TextAlign.Center
            )

            if (event?.tsunami == true) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "\u26A0\uFE0F TSUNAMI POTENTIAL \u26A0\uFE0F",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF5722),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(24.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = p.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    if (event != null) {
                        InfoRow("Magnitude", "M ${event.mag}")
                        InfoRow("Location", event.loc)
                        InfoRow("Depth", event.depth)
                        if (event.felt != "--") InfoRow("Felt", event.felt)
                        InfoRow("Time", buildString {
                            if (event.tanggal.isNotEmpty()) append(event.tanggal)
                            if (event.jam.isNotEmpty()) append(" ${event.jam}")
                        })
                        if (event.potensi != "--") InfoRow("Potential", event.potensi)
                    } else {
                        Text(
                            text = "Loading event data...",
                            color = p.textSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = onOpenApp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = p.blue),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = "OPEN APP",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = p.textSecondary),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = "DISMISS",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val p = LocalPrometheusColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = p.textSecondary,
            modifier = Modifier.weight(0.35f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = p.textPrimary,
            modifier = Modifier.weight(0.65f),
            textAlign = TextAlign.End
        )
    }
}
