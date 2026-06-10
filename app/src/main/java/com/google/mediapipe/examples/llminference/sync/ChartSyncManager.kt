package com.google.mediapipe.examples.llminference.sync

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.mediapipe.examples.llminference.cloud.CloudImageLoader
import com.google.mediapipe.examples.llminference.data.DiagnosisEntity
import com.google.mediapipe.examples.llminference.data.MedicalDatabase
import com.google.mediapipe.examples.llminference.data.MedicalEntryEntity
import com.google.mediapipe.examples.llminference.data.PatientEntity
import com.google.mediapipe.examples.llminference.network.EdgeCompanionClient
import com.google.mediapipe.examples.llminference.settings.LocalModelFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Two-way sync between Room DB and edge companion mirror. */
object ChartSyncManager {

    private const val TAG = "ChartSyncManager"
    const val DEVICE_ID = "medveda-android"

    data class SyncResult(
        val success: Boolean,
        val message: String,
        val cursor: Long = 0,
    )

    suspend fun syncIfEnabled(context: Context): SyncResult = withContext(Dispatchers.IO) {
        if (!LocalModelFiles.isCloudEnabled(context) || !LocalModelFiles.isAutoSyncEnabled(context)) {
            return@withContext SyncResult(false, "Sync disabled")
        }
        when (val health = EdgeCompanionClient.health(context)) {
            is EdgeCompanionClient.HealthResult.Error ->
                return@withContext SyncResult(false, health.message)
            is EdgeCompanionClient.HealthResult.Ok -> Unit
        }
        try {
            EdgeCompanionClient.ping(context)
            val pushPayload = buildPushPayload(context)
            val pushResult = EdgeCompanionClient.syncPush(context, pushPayload)
            if (pushResult.isFailure) {
                return@withContext SyncResult(false, pushResult.exceptionOrNull()?.message ?: "Push failed")
            }
            val since = LocalModelFiles.getLastSyncCursor(context)
            val pullResult = EdgeCompanionClient.syncPull(context, since)
            if (pullResult.isFailure) {
                return@withContext SyncResult(false, pullResult.exceptionOrNull()?.message ?: "Pull failed")
            }
            val pull = pullResult.getOrThrow()
            applyPull(context, pull)
            val cursor = pull.optLong("cursor", since)
            LocalModelFiles.setLastSyncCursor(context, cursor)
            LocalModelFiles.setLastSyncAt(context, System.currentTimeMillis())
            Log.i(TAG, "Sync OK cursor=$cursor")
            SyncResult(true, "Synced with laptop", cursor)
        } catch (e: Exception) {
            Log.e(TAG, "sync failed", e)
            SyncResult(false, e.localizedMessage ?: "Sync failed")
        }
    }

    private suspend fun buildPushPayload(context: Context): JSONObject {
        val db = MedicalDatabase.getDatabase(context)
        val patients = db.patientDao().getAllPatientsSync()
        val payload = JSONObject().put("device_id", DEVICE_ID)
        val pArr = JSONArray()
        val eArr = JSONArray()
        val dArr = JSONArray()
        val imgArr = JSONArray()

        for (p in patients) {
            pArr.put(patientToJson(p))
            val entries = db.medicalEntryDao().getEntriesForPatientSync(p.id)
            for (e in entries) {
                eArr.put(entryToJson(e))
                collectImages(context, e, imgArr)
            }
            val diagnoses = db.diagnosisDao().getLatestDiagnoses(p.id, 50)
            for (d in diagnoses) {
                dArr.put(diagnosisToJson(d))
            }
        }
        return payload
            .put("patients", pArr)
            .put("entries", eArr)
            .put("diagnoses", dArr)
            .put("consultations", JSONArray())
            .put("images", imgArr)
            .put("tombstones", JSONArray())
    }

    private fun collectImages(context: Context, entry: MedicalEntryEntity, arr: JSONArray) {
        if (entry.imagePaths.isBlank()) return
        entry.imagePaths.split(',').map { it.trim() }.filter { it.isNotBlank() }
            .forEachIndexed { idx, path ->
                val bmp = CloudImageLoader.loadBitmap(context, path) ?: return@forEachIndexed
                arr.put(
                    JSONObject()
                        .put("entryId", entry.id)
                        .put("index", idx)
                        .put("base64", CloudImageLoader.bitmapToJpegBase64(bmp))
                )
            }
    }

    private suspend fun applyPull(context: Context, pull: JSONObject) {
        val db = MedicalDatabase.getDatabase(context)
        val tombstones = pull.optJSONArray("tombstones") ?: JSONArray()
        for (i in 0 until tombstones.length()) {
            val t = tombstones.getJSONObject(i)
            val type = t.optString("entityType", t.optString("entity_type"))
            val id = t.optLong("entityId", t.optLong("entity_id"))
            when (type) {
                "patient" -> db.patientDao().getPatientSync(id)?.let { db.patientDao().deletePatient(it) }
                "entry" -> db.medicalEntryDao().getEntry(id)?.let { db.medicalEntryDao().deleteEntry(it) }
            }
        }
        val patients = pull.optJSONArray("patients") ?: JSONArray()
        for (i in 0 until patients.length()) {
            db.patientDao().insertPatient(jsonToPatient(patients.getJSONObject(i)))
        }
        val entries = pull.optJSONArray("entries") ?: JSONArray()
        for (i in 0 until entries.length()) {
            db.medicalEntryDao().insertEntry(jsonToEntry(entries.getJSONObject(i)))
        }
        val diagnoses = pull.optJSONArray("diagnoses") ?: JSONArray()
        for (i in 0 until diagnoses.length()) {
            db.diagnosisDao().insertDiagnosis(jsonToDiagnosis(diagnoses.getJSONObject(i)))
        }
        val images = pull.optJSONArray("images") ?: JSONArray()
        for (i in 0 until images.length()) {
            val img = images.getJSONObject(i)
            val entryId = img.getLong("entryId")
            val index = img.optInt("index", 0)
            val file = File(context.filesDir, "sync_${entryId}_$index.jpg")
            file.writeBytes(Base64.decode(img.getString("base64"), Base64.DEFAULT))
            val entry = db.medicalEntryDao().getEntry(entryId) ?: continue
            val paths = if (entry.imagePaths.isBlank()) {
                listOf(file.absolutePath)
            } else {
                entry.imagePaths.split(',').toMutableList().also {
                    while (it.size <= index) it.add("")
                    it[index] = file.absolutePath
                }
            }
            db.medicalEntryDao().updateEntry(
                entry.copy(imagePaths = paths.joinToString(","), updatedAt = System.currentTimeMillis())
            )
        }
    }

    private fun patientToJson(p: PatientEntity) = JSONObject()
        .put("id", p.id).put("name", p.name).put("dateOfBirth", p.dateOfBirth)
        .put("gender", p.gender).put("medicalRecordNumber", p.medicalRecordNumber)
        .put("phoneNumber", p.phoneNumber).put("email", p.email).put("address", p.address)
        .put("bloodGroup", p.bloodGroup).put("allergies", p.allergies).put("notes", p.notes)
        .put("createdAt", p.createdAt).put("updatedAt", p.updatedAt)

    private fun entryToJson(e: MedicalEntryEntity) = JSONObject()
        .put("id", e.id).put("patientId", e.patientId).put("entryType", e.entryType)
        .put("title", e.title).put("content", e.content).put("imagePaths", e.imagePaths)
        .put("analysisResult", e.analysisResult).put("visitSummary", e.visitSummary)
        .put("status", e.status).put("cloudProcessedAt", e.cloudProcessedAt)
        .put("createdAt", e.createdAt).put("updatedAt", e.updatedAt)

    private fun diagnosisToJson(d: DiagnosisEntity) = JSONObject()
        .put("id", d.id).put("patientId", d.patientId).put("diagnosis", d.diagnosis)
        .put("generatedAt", d.generatedAt).put("scope", d.scope)
        .put("entryCount", d.entryCount).put("modelName", d.modelName)
        .put("thinkingEnabled", d.thinkingEnabled)

    private fun jsonToPatient(j: JSONObject) = PatientEntity(
        id = j.getLong("id"), name = j.getString("name"),
        dateOfBirth = j.optString("dateOfBirth", ""), gender = j.optString("gender", ""),
        medicalRecordNumber = j.optString("medicalRecordNumber", ""),
        phoneNumber = j.optString("phoneNumber", ""), email = j.optString("email", ""),
        address = j.optString("address", ""), bloodGroup = j.optString("bloodGroup", ""),
        allergies = j.optString("allergies", ""), notes = j.optString("notes", ""),
        createdAt = j.optLong("createdAt", System.currentTimeMillis()),
        updatedAt = j.optLong("updatedAt", System.currentTimeMillis()),
    )

    private fun jsonToEntry(j: JSONObject) = MedicalEntryEntity(
        id = j.getLong("id"), patientId = j.getLong("patientId"),
        entryType = j.getString("entryType"), title = j.optString("title", ""),
        content = j.optString("content", ""), imagePaths = j.optString("imagePaths", ""),
        analysisResult = j.optString("analysisResult", ""),
        visitSummary = j.optString("visitSummary", ""),
        status = j.optString("status", "pending"),
        cloudProcessedAt = j.optLong("cloudProcessedAt", 0),
        createdAt = j.optLong("createdAt", System.currentTimeMillis()),
        updatedAt = j.optLong("updatedAt", System.currentTimeMillis()),
    )

    private fun jsonToDiagnosis(j: JSONObject) = DiagnosisEntity(
        id = j.getLong("id"), patientId = j.getLong("patientId"),
        diagnosis = j.getString("diagnosis"),
        generatedAt = j.optLong("generatedAt", System.currentTimeMillis()),
        scope = j.optString("scope", "FULL"), entryCount = j.optInt("entryCount", 0),
        modelName = j.optString("modelName", ""),
        thinkingEnabled = j.optBoolean("thinkingEnabled", false),
    )
}
