package com.prometheus.android.inference.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.prometheus.android.R
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "DownloadWorker"

class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "model_download"
        val channel = NotificationChannel(channelId, "Model Download", NotificationManager.IMPORTANCE_LOW)
        notificationManager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Downloading Gemma 4")
            .setContentText("0%")
            .setProgress(100, 0, false)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()

        setForeground(ForegroundInfo(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC))

        val file = File(applicationContext.filesDir, MODEL_FILENAME_V2_PART)
        val existingBytes = if (file.exists()) file.length() else 0L

        Log.d(TAG, "Starting download from byte $existingBytes")

        return try {
            val url = URL(applicationContext.getString(R.string.model_download_url))
            val connection = url.openConnection() as HttpURLConnection
            connection.setRequestProperty("Range", "bytes=$existingBytes-")
            connection.connectTimeout = 30000
            connection.readTimeout = 30000

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_PARTIAL) {
                val totalBytes: Long
                val contentLength = connection.contentLengthLong
                val contentRange = connection.getHeaderField("Content-Range")

                if (responseCode == HttpURLConnection.HTTP_PARTIAL && contentRange != null) {
                    val match = Regex("bytes \\d+-(\\d+)/(\\d+)").find(contentRange)
                    totalBytes = match?.groupValues?.get(2)?.toLongOrNull()
                        ?: (existingBytes + contentLength)
                } else {
                    totalBytes = if (contentLength > 0) contentLength else -1L
                    file.parentFile?.mkdirs()
                }

                var downloaded = existingBytes
                var lastPercent = -1
                val buffer = ByteArray(8192)
                connection.inputStream.use { input ->
                    FileOutputStream(file, true).buffered().use { output ->
                        while (true) {
                            if (isStopped) {
                                Log.d(TAG, "Download cancelled at byte $downloaded")
                                return Result.failure()
                            }
                            val bytes = input.read(buffer)
                            if (bytes == -1) break
                            output.write(buffer, 0, bytes)
                            downloaded += bytes
                            if (totalBytes > 0) {
                                val percent = (downloaded * 100L / totalBytes).toInt()
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    setProgress(workDataOf("progress" to percent))
                                    val notification2 = NotificationCompat.Builder(applicationContext, channelId)
                                        .setContentTitle("Downloading Gemma 4")
                                        .setContentText("$percent%")
                                        .setProgress(100, percent, false)
                                        .setSmallIcon(android.R.drawable.stat_sys_download)
                                        .setOngoing(true)
                                        .build()
                                    setForeground(ForegroundInfo(1, notification2, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC))
                                }
                            }
                        }
                    }
                }

                Log.d(TAG, "Download complete: ${file.length()} bytes")
                val finalNotification = NotificationCompat.Builder(applicationContext, channelId)
                    .setContentTitle("Download complete")
                    .setContentText("Verifying...")
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setOngoing(false)
                    .build()
                setForeground(ForegroundInfo(1, finalNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC))

                val outputData = workDataOf(
                    "expected_size" to totalBytes,
                    "file_path" to file.absolutePath
                )
                Result.success(outputData)
            } else {
                Log.e(TAG, "Server returned $responseCode")
                Result.failure()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val MODEL_FILENAME_V2_PART = "gemma-4-E2B-it.litertlm.part"
    }
}
