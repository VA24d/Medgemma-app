package com.google.mediapipe.examples.llminference.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.mediapipe.examples.llminference.cloud.CloudChartProcessor
import com.google.mediapipe.examples.llminference.settings.LocalModelFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Background enrichment of all patient charts via edge companion when network is available.
 */
class CloudBatchWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    companion object {
        const val TAG = "CloudBatchWorker"
        const val WORK_NAME = "cloud_batch_enrichment"
        const val KEY_FORCE = "force_reprocess"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!LocalModelFiles.isCloudEnabled(applicationContext)) {
            return@withContext Result.success()
        }
        val force = inputData.getBoolean(KEY_FORCE, false)
        try {
            val processor = CloudChartProcessor(applicationContext)
            val r = processor.processAllPatients(force) { p ->
                Log.i(TAG, "${p.phase}: ${p.message}")
            }
            if (r.isSuccess) Result.success() else Result.retry()
        } catch (e: Exception) {
            Log.e(TAG, "batch failed", e)
            Result.retry()
        }
    }
}
