package com.google.mediapipe.examples.llminference.data

import android.content.Context
import android.os.Environment
import android.util.Log
import android.widget.Toast
import com.google.mediapipe.examples.llminference.InferenceModel
import com.google.mediapipe.tasks.genai.llminference.ProgressListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import kotlinx.coroutines.flow.first

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

                    onProgress("Converting patient ${index + 1}/$total: ${patient.name}...")
                    
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

                // 3. Save to file
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val fileName = "medgemma_fhir_export_$timestamp.json"
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadsDir, fileName)
                
                file.writeText(fhirBundle.toString())
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Export saved to Downloads: $fileName", Toast.LENGTH_LONG).show()
                    onComplete(file)
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
