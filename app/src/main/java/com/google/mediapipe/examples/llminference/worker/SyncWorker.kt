package com.google.mediapipe.examples.llminference.worker

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.mediapipe.examples.llminference.settings.LocalModelFiles
import com.google.mediapipe.examples.llminference.sync.ChartSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Periodic background sync with edge companion when on Wi-Fi and charging. */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    companion object {
        const val TAG = "SyncWorker"
        const val WORK_NAME = "edge_companion_sync"

        fun syncSchedule(context: Context) {
            val wm = WorkManager.getInstance(context)
            if (!LocalModelFiles.isCloudEnabled(context) || !LocalModelFiles.isAutoSyncEnabled(context)) {
                wm.cancelUniqueWork(WORK_NAME)
                return
            }
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresCharging(true)
                .build()
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
            Log.i(TAG, "Sync worker scheduled (15 min, charging + network)")
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val r = ChartSyncManager.syncIfEnabled(applicationContext)
        if (r.success) Result.success() else Result.retry()
    }
}
