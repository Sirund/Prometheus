package com.prometheus.android.inference.download

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.prometheus.android.inference.ModelManager

private const val TAG = "InitEngineWorker"

class InitEngineWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "Initializing engine after download...")
        try {
            ModelManager.reload(applicationContext)
            if (ModelManager.isLoaded) {
                Log.d(TAG, "Engine loaded successfully")
                return Result.success()
            } else {
                Log.e(TAG, "Engine failed to load: ${ModelManager.statusMessage}")
                return Result.failure()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Engine init failed", e)
            return Result.failure()
        }
    }
}
