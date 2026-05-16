package com.prometheus.android.inference

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.File

class DownloadReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return

        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
        val savedId = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(PREF_DOWNLOAD_ID, -1)

        if (downloadId != savedId) return

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = dm.getUriForDownloadedFile(downloadId) ?: run {
            Log.e(TAG, "Downloaded file URI is null")
            return
        }

        try {
            val input = context.contentResolver.openInputStream(uri)
            val destDir = context.getExternalFilesDir(null)
                ?: context.filesDir
            val destFile = File(destDir, MODEL_FILENAME_V2)
            destDir.mkdirs()

            input?.use { src ->
                destFile.outputStream().use { dst ->
                    src.copyTo(dst)
                }
            }

            Log.d(TAG, "Model moved to: ${destFile.absolutePath}")

            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(PREF_DOWNLOAD_COMPLETE, true).apply()

        } catch (e: Exception) {
            Log.e(TAG, "Failed to move downloaded model", e)
        }
    }

    companion object {
        private const val TAG = "DownloadReceiver"
        private const val PREFS_NAME = "model_download"
        private const val PREF_DOWNLOAD_ID = "download_id"
        private const val PREF_DOWNLOAD_COMPLETE = "download_complete"
        const val MODEL_FILENAME_V2 = "gemma-4-E2B-it.litertlm"
    }
}
