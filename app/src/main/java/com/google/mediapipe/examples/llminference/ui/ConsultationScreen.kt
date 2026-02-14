package com.google.mediapipe.examples.llminference.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mediapipe.examples.llminference.InferenceModel
import com.google.mediapipe.examples.llminference.viewmodel.ConsultationViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsultationScreen(
    patientId: Long,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: ConsultationViewModel = viewModel()
) {
    val context = LocalContext.current
    val patient by viewModel.patient.collectAsState()
    val chiefComplaint by viewModel.chiefComplaint.collectAsState()
    val symptoms by viewModel.symptoms.collectAsState()
    val vitalSigns by viewModel.vitalSigns.collectAsState()
    val diagnosis by viewModel.diagnosis.collectAsState()
    val aiSuggestions by viewModel.aiSuggestions.collectAsState()
    val isGeneratingAI by viewModel.isGeneratingAI.collectAsState()




    LaunchedEffect(patientId) {
        viewModel.loadPatient(patientId)
        val inferenceModel = InferenceModel.getInstance(context)
        viewModel.setInferenceModel(inferenceModel)
    }



    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(patient?.name ?: "Consultation") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.saveConsultation()
                            onSaved()
                        }
                    ) {
                        Icon(Icons.Default.Check, "Save consultation")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
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
            // Chief Complaint
            ConsultationField(
                label = "Chief Complaint",
                value = chiefComplaint,
                onValueChange = { viewModel.updateChiefComplaint(it) }
            )

            // Symptoms
            ConsultationField(
                label = "Symptoms & History",
                value = symptoms,
                onValueChange = { viewModel.updateSymptoms(it) },
                minLines = 4
            )

            // Vital Signs
            ConsultationField(
                label = "Vital Signs",
                value = vitalSigns,
                onValueChange = { viewModel.updateVitalSigns(it) },
                placeholder = "BP, Temp, Pulse, SpO2, etc."
            )

            // AI Prognosis Button
            Button(
                onClick = { viewModel.generateAIPrognosis() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isGeneratingAI && chiefComplaint.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary
                )
            ) {
                if (isGeneratingAI) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Icon(Icons.Default.AutoAwesome, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isGeneratingAI) "Generating..." else "Get AI Analysis")
            }

            // AI Suggestions
            if (aiSuggestions.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Info,
                                null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "AI Suggestions",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            aiSuggestions,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            // Diagnosis
            ConsultationField(
                label = "Diagnosis",
                value = diagnosis,
                onValueChange = { viewModel.updateDiagnosis(it) },
                minLines = 3
            )
        }
    }
}

@Composable
private fun ConsultationField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    minLines: Int = 1,
    placeholder: String = ""
) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = minLines,
            placeholder = { if (placeholder.isNotEmpty()) Text(placeholder) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
    }
}
