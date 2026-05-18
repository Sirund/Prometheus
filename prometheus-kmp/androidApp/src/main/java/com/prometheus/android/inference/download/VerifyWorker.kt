package com.prometheus.android.inference.download

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.File

private const val TAG = "VerifyWorker"

class VerifyWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val expectedSize = inputData.getLong("expected_size", -1L)
        val filePath = inputData.getString("file_path") ?: return Result.failure()

        val partFile = File(filePath)
        if (!partFile.exists()) {
            Log.e(TAG, "File not found: $filePath")
            return Result.failure()
        }

        val actualSize = partFile.length()
        Log.d(TAG, "Expected: $expectedSize, Actual: $actualSize")

        if (expectedSize > 0 && actualSize != expectedSize) {
            Log.e(TAG, "Size mismatch — corrupt download")
            return Result.failure()
        }

        val finalName = filePath.removeSuffix(".part")
        val finalFile = File(finalName)
        if (!partFile.renameTo(finalFile)) {
            Log.e(TAG, "renameTo failed")
            return Result.failure()
        }

        Log.d(TAG, "Verified and renamed to: $finalName")
        return Result.success()
    }
}
