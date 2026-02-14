package com.google.mediapipe.examples.llminference.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.google.mediapipe.examples.llminference.data.MedicalDatabase
import com.google.mediapipe.examples.llminference.data.MedicalEntryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualNotesScreen(
    patientId: Long,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val db = remember { MedicalDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var symptoms by remember { mutableStateOf("") }
    var vitalSigns by remember { mutableStateOf("") }
    var observations by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manual Notes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            isSaving = true
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    val content = buildString {
                                        if (symptoms.isNotBlank()) appendLine("Symptoms: $symptoms")
                                        if (vitalSigns.isNotBlank()) appendLine("Vitals: $vitalSigns")
                                        if (observations.isNotBlank()) appendLine("Observations: $observations")
                                        if (notes.isNotBlank()) appendLine("Notes: $notes")
                                    }
                                    db.medicalEntryDao().insertEntry(
                                        MedicalEntryEntity(
                                            patientId = patientId,
                                            entryType = "MANUAL",
                                            title = title.ifBlank { "Clinical Notes" },
                                            content = content
                                        )
                                    )
                                }
                                isSaving = false
                                onSaved()
                            }
                        },
                        enabled = !isSaving && (symptoms.isNotBlank() || observations.isNotBlank() || notes.isNotBlank())
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title (optional)") },
                placeholder = { Text("e.g., Follow-up visit") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = symptoms,
                onValueChange = { symptoms = it },
                label = { Text("Symptoms") },
                placeholder = { Text("Patient-reported symptoms…") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            OutlinedTextField(
                value = vitalSigns,
                onValueChange = { vitalSigns = it },
                label = { Text("Vital Signs") },
                placeholder = { Text("BP, Temperature, Heart Rate, SpO₂…") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            OutlinedTextField(
                value = observations,
                onValueChange = { observations = it },
                label = { Text("Clinical Observations") },
                placeholder = { Text("Physical examination findings…") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Additional Notes") },
                placeholder = { Text("Any additional clinical notes…") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
        }
    }
}
