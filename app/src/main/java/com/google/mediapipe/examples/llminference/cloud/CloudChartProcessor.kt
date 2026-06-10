package com.google.mediapipe.examples.llminference.cloud

import android.content.Context
import android.util.Log
import com.google.mediapipe.examples.llminference.data.DiagnosisEntity
import com.google.mediapipe.examples.llminference.data.MedicalDatabase
import com.google.mediapipe.examples.llminference.data.MedicalEntryEntity
import com.google.mediapipe.examples.llminference.network.EdgeCompanionClient
import com.google.mediapipe.examples.llminference.settings.LocalModelFiles
import com.google.mediapipe.examples.llminference.sync.ChartSyncManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CloudProgress(
    val phase: String,
    val patientName: String = "",
    val current: Int = 0,
    val total: Int = 0,
    val entryTitle: String = "",
    val message: String = "",
)

class CloudChartProcessor(private val context: Context) {

    companion object {
        private const val TAG = "CloudChartProcessor"
        const val ALL_PATIENTS_ID: Long = -1L
    }

    suspend fun processPatient(
        patientId: Long,
        forceReprocess: Boolean = false,
        onProgress: (CloudProgress) -> Unit = {},
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            onProgress(CloudProgress(phase = "checking", message = "Checking edge companion…"))
            when (val health = EdgeCompanionClient.health(context)) {
                is EdgeCompanionClient.HealthResult.Error ->
                    return@withContext Result.failure(Exception(health.message))
                is EdgeCompanionClient.HealthResult.Ok -> {
                    if (!health.ollamaOk) {
                        return@withContext Result.failure(Exception("Ollama not running on laptop"))
                    }
                    val model = LocalModelFiles.getCloudModelName(context)
                    if (health.models.isNotEmpty() && model !in health.models) {
                        return@withContext Result.failure(
                            Exception("Model '$model' not on server. Available: ${health.models.take(3).joinToString()}")
                        )
                    }
                }
            }
            EdgeCompanionClient.ping(context)

            val db = MedicalDatabase.getDatabase(context)
            val patient = db.patientDao().getPatientSync(patientId)
                ?: return@withContext Result.failure(Exception("Patient not found"))

            val entries = db.medicalEntryDao().getEntriesForPatientSync(patientId)
                .sortedBy { it.createdAt }
            if (entries.isEmpty()) {
                return@withContext Result.failure(Exception("No entries to process"))
            }

            val toProcess = entries.filter { e ->
                forceReprocess ||
                    CloudEntryPrompts.needsVisionProcessing(e, forceReprocess) ||
                    CloudEntryPrompts.needsTextProcessing(e, forceReprocess) ||
                    (e.visitSummary.isBlank() && e.analysisResult.isNotBlank())
            }.ifEmpty { entries }

            var idx = 0
            for (entry in toProcess) {
                idx++
                onProgress(
                    CloudProgress(
                        phase = "entry",
                        patientName = patient.name,
                        current = idx,
                        total = toProcess.size,
                        entryTitle = entry.title,
                        message = "Processing ${entry.entryType}: ${entry.title}",
                    )
                )

                val updated = processOneEntry(patient.name, entry, forceReprocess)
                db.medicalEntryDao().updateEntry(updated)
            }

            onProgress(
                CloudProgress(
                    phase = "longitudinal",
                    patientName = patient.name,
                    message = "Synthesizing longitudinal prognosis…",
                )
            )

            val refreshed = db.medicalEntryDao().getEntriesForPatientSync(patientId).sortedBy { it.createdAt }
            val prompt = CloudEntryPrompts.buildLongitudinalPrompt(patient, refreshed)
            val longResult = EdgeCompanionClient.processLongitudinal(context, patient.name, prompt)
            longResult.fold(
                onSuccess = { lr ->
                    val diagnosis = CloudThinkingStrip.stripFull(lr.diagnosis)
                    db.diagnosisDao().insertDiagnosis(
                        DiagnosisEntity(
                            patientId = patientId,
                            diagnosis = diagnosis,
                            scope = "CLOUD_FULL",
                            entryCount = refreshed.size,
                            modelName = "Ollama:${LocalModelFiles.getCloudModelName(context)}",
                            thinkingEnabled = false,
                        )
                    )
                    onProgress(
                        CloudProgress(
                            phase = "done",
                            patientName = patient.name,
                            message = "Complete — ${refreshed.size} entries enriched",
                        )
                    )
                    ChartSyncManager.syncIfEnabled(context)
                    Result.success(Unit)
                },
                onFailure = { Result.failure(it) },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "processPatient failed", e)
            Result.failure(e)
        }
    }

    suspend fun processAllPatients(
        forceReprocess: Boolean = false,
        onProgress: (CloudProgress) -> Unit = {},
    ): Result<Int> = withContext(Dispatchers.IO) {
        val db = MedicalDatabase.getDatabase(context)
        val patients = db.patientDao().getAllPatientsSync()
        var done = 0
        for (p in patients) {
            val count = db.medicalEntryDao().getEntryCount(p.id)
            if (count == 0) continue
            onProgress(
                CloudProgress(
                    phase = "patient",
                    patientName = p.name,
                    message = "Starting patient: ${p.name}",
                )
            )
            val r = processPatient(p.id, forceReprocess, onProgress)
            if (r.isFailure) {
                return@withContext Result.failure(r.exceptionOrNull() ?: Exception("Failed on ${p.name}"))
            }
            done++
        }
        onProgress(CloudProgress(phase = "done", message = "All patients processed ($done)"))
        ChartSyncManager.syncIfEnabled(context)
        Result.success(done)
    }

    private suspend fun processOneEntry(
        patientName: String,
        entry: MedicalEntryEntity,
        force: Boolean,
    ): MedicalEntryEntity {
        val now = System.currentTimeMillis()
        var analysis = entry.analysisResult
        var summary = entry.visitSummary

        if (CloudEntryPrompts.needsVisionProcessing(entry, force)) {
            val path = CloudImageLoader.firstImagePath(entry)
            val b64 = path?.let { p ->
                CloudImageLoader.loadBitmap(context, p)?.let { CloudImageLoader.bitmapToJpegBase64(it) }
            }.orEmpty()
            if (b64.isBlank()) {
                Log.w(TAG, "No image for entry ${entry.id}, skipping vision")
            } else {
                val prompt = CloudEntryPrompts.visionPrompt(entry.entryType)
                val result = EdgeCompanionClient.processEntry(
                    context = context,
                    patientName = patientName,
                    entryId = entry.id,
                    entryType = entry.entryType,
                    title = entry.title,
                    content = entry.content,
                    prompt = prompt,
                    imageBase64 = b64,
                )
                result.getOrThrow().let {
                    analysis = CloudThinkingStrip.stripFull(it.analysisResult)
                    summary = CloudThinkingStrip.stripFull(it.visitSummary)
                }
            }
        } else if (CloudEntryPrompts.needsTextProcessing(entry, force)) {
            val prompt = CloudEntryPrompts.textEntryPrompt(entry)
            val result = EdgeCompanionClient.processEntry(
                context = context,
                patientName = patientName,
                entryId = entry.id,
                entryType = entry.entryType,
                title = entry.title,
                content = entry.content,
                prompt = prompt,
            )
            result.getOrThrow().let {
                analysis = CloudThinkingStrip.stripFull(it.analysisResult)
                summary = CloudThinkingStrip.stripFull(it.visitSummary)
            }
        } else if (summary.isBlank() && analysis.isNotBlank()) {
            val prompt = "One sentence chart headline for: ${entry.title}\n\n$analysis"
            val result = EdgeCompanionClient.processEntry(
                context = context,
                patientName = patientName,
                entryId = entry.id,
                entryType = entry.entryType,
                title = entry.title,
                content = entry.content,
                prompt = prompt,
                numPredict = 128,
            )
            result.getOrThrow().let {
                summary = CloudThinkingStrip.stripFull(it.visitSummary.ifBlank { it.analysisResult.take(200) })
            }
        }

        return entry.copy(
            analysisResult = analysis,
            visitSummary = summary,
            cloudProcessedAt = now,
            status = "analyzed",
            updatedAt = now,
        )
    }
}
