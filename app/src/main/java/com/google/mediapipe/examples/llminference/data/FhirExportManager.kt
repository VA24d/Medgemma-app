package com.google.mediapipe.examples.llminference.data

import android.content.Context
import android.os.Environment
import android.util.Log
import android.widget.Toast
import com.google.mediapipe.examples.llminference.InferenceModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import kotlinx.coroutines.flow.first

import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore

class FhirExportManager(private val context: Context) {

    private val db = MedicalDatabase.getDatabase(context)

    suspend fun exportAllPatientsToFhir(
        inferenceModel: InferenceModel,
        onProgress: (String) -> Unit,
        onComplete: (File?) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                // 1. Fetch all data
                val patients = db.patientDao().getAllPatients().first()

                if (patients.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "No patients to export", Toast.LENGTH_SHORT).show()
                        onComplete(null)
                    }
                    return@withContext
                }

                val fhirBundle = StringBuilder()
                fhirBundle.append("{\n  \"resourceType\": \"Bundle\",\n  \"type\": \"collection\",\n  \"entry\": [\n")

                var processedCount = 0
                val total = patients.size

                patients.forEachIndexed { index, patient ->
                    // 2. Generate FHIR JSON using LLM for each patient
                    val prompt = """
                        You are a medical data assistant. Convert this patient data to a valid FHIR R4 Patient resource JSON.
                        Output ONLY the JSON object. Do not include markdown code blocks, explanations, or any other text.
                        
                        Name: ${patient.name}
                        DOB: ${patient.dateOfBirth}
                        Gender: ${patient.gender}
                        MRN: ${patient.medicalRecordNumber}
                        Phone: ${patient.phoneNumber}
                        Email: ${patient.email}
                        Address: ${patient.address}
                    """.trimIndent()

                    withContext(Dispatchers.Main) {
                        onProgress("Converting patient ${index + 1}/$total: ${patient.name}...")
                    }
                    
                    // Suspend generation for export
                    val fhirJson = try {
                        inferenceModel.generateResponse(prompt)
                    } catch (e: Exception) {
                        Log.e("FhirExport", "Failed to convert patient ${patient.name}", e)
                        // Fallback JSON in case of failure
                        """{
                          "resourceType": "Patient",
                          "id": "${patient.id}",
                          "name": [ { "family": "${patient.name}" } ],
                          "gender": "${patient.gender.lowercase()}",
                          "birthDate": "${patient.dateOfBirth}"
                        }"""
                    }
                    
                    // Clean up LLM output (remove ```json ... ``` if present)
                    val cleanJson = fhirJson.replace("```json", "").replace("```", "").trim()

                    fhirBundle.append("    {\n      \"resource\": $cleanJson\n    }")
                    if (index < patients.lastIndex) fhirBundle.append(",\n")
                    
                    processedCount++
                }

                fhirBundle.append("\n  ]\n}")

                // 3. Save to file using MediaStore (Scoped Storage compliant)
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val fileName = "medgemma_fhir_export_$timestamp.json"
                val content = fhirBundle.toString()

                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                        ?: throw Exception("Failed to create file in Downloads")
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(content.toByteArray())
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val file = File(downloadsDir, fileName)
                    file.writeText(content)
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Export saved to Downloads: $fileName", Toast.LENGTH_LONG).show()
                    onComplete(null) // Scoped storage doesn't return a File path we can use directly
                }

            } catch (e: Exception) {
                Log.e("FhirExport", "Export failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                    onComplete(null)
                }
            }
        }
    }
}
