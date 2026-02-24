package com.google.mediapipe.examples.llminference.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.google.mediapipe.examples.llminference.InferenceModel
import com.google.mediapipe.examples.llminference.data.DiagnosisEntity
import com.google.mediapipe.examples.llminference.data.MedicalDatabase
import com.google.mediapipe.examples.llminference.data.MedicalEntryEntity
import com.google.mediapipe.examples.llminference.settings.LocalModelFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Background worker that generates a new full prognosis for every patient
 * that has at least 1 medical entry. Runs via WorkManager at the scheduled time.
 */
class ScheduledPrognosisWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "ScheduledPrognosis"
        const val UNIQUE_WORK_NAME = "scheduled_prognosis"

        /** Schedule or cancel the periodic work based on user preference. */
        fun syncSchedule(context: Context) {
            val wm = WorkManager.getInstance(context)
            if (!LocalModelFiles.isScheduledPrognosisEnabled(context)) {
                wm.cancelUniqueWork(UNIQUE_WORK_NAME)
                Log.i(TAG, "Scheduled prognosis cancelled")
                return
            }

            val hour = LocalModelFiles.getScheduleHour(context)
            val minute = LocalModelFiles.getScheduleMinute(context)

            // Calculate initial delay to the next occurrence of HH:MM
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
            }
            val delayMs = target.timeInMillis - now.timeInMillis

            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<ScheduledPrognosisWorker>(
                24, TimeUnit.HOURS
            )
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .build()

            wm.enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
            Log.i(TAG, "Scheduled prognosis at %02d:%02d (delay ${delayMs / 60000} min)".format(hour, minute))
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Starting scheduled prognosis generation…")
            val db = MedicalDatabase.getDatabase(applicationContext)
            val patients = db.patientDao().getAllPatientsSync()

            if (patients.isEmpty()) {
                Log.i(TAG, "No patients found, skipping")
                return@withContext Result.success()
            }

            // Try to get inference model — may fail if not yet set up
            val inferenceModel = try {
                InferenceModel.getInstance(applicationContext)
            } catch (e: Exception) {
                Log.w(TAG, "Model not available: ${e.message}")
                return@withContext Result.retry()
            }

            val thinkingOn = LocalModelFiles.isThinkingEnabled(applicationContext)
            val modelName = File(InferenceModel.model.path).nameWithoutExtension
                .replace('-', ' ').replace('_', ' ')

            var generated = 0
            for (patient in patients) {
                val entries = db.medicalEntryDao().getEntriesForPatientSync(patient.id)
                if (entries.isEmpty()) continue

                val latestDiag = db.diagnosisDao().getLatestDiagnosis(patient.id)
                // Skip if we already have a diagnosis from the last 20 hours
                if (latestDiag != null && System.currentTimeMillis() - latestDiag.generatedAt < 20 * 3600 * 1000L) {
                    Log.d(TAG, "Skipping patient ${patient.id} (recent diagnosis exists)")
                    continue
                }

                val sorted = entries.sortedBy { it.createdAt }
                val prompt = buildAutoPrompt(sorted)

                Log.i(TAG, "Generating for patient ${patient.id} (${sorted.size} entries)")
                val result = StringBuilder()
                try {
                    val future = inferenceModel.generateResponseAsync(prompt, emptyList()) { token, done ->
                        if (!done && token.isNotEmpty()) result.append(token)
                    }
                    future.get() // blocks on worker thread — that's fine

                    if (result.isNotBlank()) {
                        db.diagnosisDao().insertDiagnosis(
                            DiagnosisEntity(
                                patientId = patient.id,
                                diagnosis = result.toString(),
                                scope = "FULL",
                                entryCount = sorted.size,
                                modelName = modelName,
                                thinkingEnabled = thinkingOn
                            )
                        )
                        generated++
                        Log.i(TAG, "Saved diagnosis for patient ${patient.id} (${result.length} chars)")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed for patient ${patient.id}: ${e.message}")
                }
            }

            Log.i(TAG, "Scheduled prognosis complete: $generated diagnoses generated")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Worker failed", e)
            Result.retry()
        }
    }

    private fun buildAutoPrompt(entries: List<MedicalEntryEntity>): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val summary = entries.joinToString("\n") { e ->
            val ai = if (e.analysisResult.isNotBlank()) " | AI: ${e.analysisResult.take(120)}…" else ""
            "[${fmt.format(Date(e.createdAt))}][${e.entryType}] ${e.title}: ${e.content.take(120)}$ai"
        }
        return """You are a specialist AI medical assistant. Generate a concise clinical prognosis.

Patient has ${entries.size} medical record entries (oldest→newest):
$summary

Provide:
1. **Summary of findings**
2. **Diagnosis / differentials** (with confidence levels)
3. **Disease progression** (improving / stable / deteriorating)
4. **Recommended next steps**
5. **Red flags** to monitor

Format in Markdown. Be concise and clinically precise."""
    }
}
