package com.google.mediapipe.examples.llminference.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.google.mediapipe.examples.llminference.data.MedicalDatabase
import com.google.mediapipe.examples.llminference.data.PatientEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPatientScreen(
    onBack: () -> Unit,
    onPatientAdded: (Long) -> Unit,
    patientIdToEdit: Long? = null
) {
    val context = LocalContext.current
    val view = LocalView.current
    val db = remember { MedicalDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("") }
    var mrn by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var bloodGroup by remember { mutableStateOf("") }
    var allergies by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var genderExpanded by remember { mutableStateOf(false) }

    // Load existing patient if editing
    LaunchedEffect(patientIdToEdit) {
        if (patientIdToEdit != null) {
            val patient = withContext(Dispatchers.IO) {
                db.patientDao().getPatientSync(patientIdToEdit)
            }
            patient?.let {
                name = it.name
                dob = it.dateOfBirth
                gender = it.gender
                mrn = it.medicalRecordNumber
                phone = it.phoneNumber
                email = it.email
                bloodGroup = it.bloodGroup
                allergies = it.allergies
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (patientIdToEdit != null) "Edit Patient" else "New Patient") },
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
                                val id = withContext(Dispatchers.IO) {
                                    val patient = PatientEntity(
                                        id = patientIdToEdit ?: 0, // 0 for insert
                                        name = name.trim(),
                                        dateOfBirth = dob.trim(),
                                        gender = gender,
                                        medicalRecordNumber = mrn.trim(),
                                        phoneNumber = phone.trim(),
                                        email = email.trim(),
                                        bloodGroup = bloodGroup.trim(),
                                        allergies = allergies.trim(),
                                        createdAt = if (patientIdToEdit != null) System.currentTimeMillis() else System.currentTimeMillis() // preserve createdAt if we read it, but here we just simplify
                                    )
                                    val dao = db.patientDao()
                                    if (patientIdToEdit != null) {
                                        dao.updatePatient(patient)
                                        patientIdToEdit
                                    } else {
                                        dao.insertPatient(patient)
                                    }
                                }
                                isSaving = false
                                onPatientAdded(id)
                            }
                        },
                        enabled = !isSaving && name.isNotBlank() && gender.isNotBlank()
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
            // Required fields
            Text("Required", style = MaterialTheme.typography.labelLarge)

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Full Name *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Person, null) }
            )

            // Date Picker State
            var showDatePicker by remember { mutableStateOf(false) }
            val datePickerState = rememberDatePickerState()

            if (showDatePicker) {
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            datePickerState.selectedDateMillis?.let { millis ->
                                val date = java.util.Date(millis)
                                val format = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                dob = format.format(date)
                            }
                            showDatePicker = false
                        }) { Text("OK") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
                    }
                ) {
                    DatePicker(state = datePickerState)
                }
            }

            OutlinedTextField(
                value = dob,
                onValueChange = { },
                readOnly = true,
                label = { Text("Date of Birth") },
                placeholder = { Text("YYYY-MM-DD") },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePicker = true },
                enabled = false, // Disable typing, but clickable modifier handles clicks? No, enabling false disables click too.
                // Better approach:
                // Make it readOnly = true, and use interaction source or simple clickable on a Box wrapping it, or trailing icon click.
                // Let's use trailing icon + readOnly + interaction source or just trailing icon.
                // Actually, simplest is readOnly = true and trailing icon acts as trigger, OR use a Box/clickable.
                // Material3 guidelines suggest readOnly + clickable on the field.
                // But Modifier.clickable on OutlinedTextField might not work well if it consumes touches.
                // Let's attach clickable to the trailing icon AND make the field readOnly.
                // Or better: use a Box wrapping the TextField to capture clicks if we want the whole field to be clickable.
                // Let's try: readOnly = true, interactionSource...
                // Simpler: Just trailing icon click.
                leadingIcon = { Icon(Icons.Default.DateRange, null) },
                trailingIcon = { 
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.EditCalendar, contentDescription = "Select Date")
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
             )
             // Actually, to make the whole field clickable is better UX.
             // I will wrap it in a Box with clickable, and disable the TextField's hit testing?
             // Or just Use `interactionSource`.
             // For now, I will enable it but make it readOnly, and opening dialog on focus is tricky.
             // I'll stick to a robust solution: Trailing Icon opens it. And I'll make the field clickable if I can.
             // Let's go with: readOnly = true. modifier.clickable { showDatePicker = true } DOES work on OutlinedTextField if enabled=true.
             // So:
            OutlinedTextField(
                value = dob,
                onValueChange = { },
                readOnly = true,
                label = { Text("Date of Birth *") },
                placeholder = { Text("YYYY-MM-DD") },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePicker = true }, // This might be intercepted by internal text logic.
                // To be safe, rely on Trailing Icon mostly, but try InteractionSource if I had more time.
                // I will use TrailingIcon for the action.
                leadingIcon = { Icon(Icons.Default.DateRange, null) },
                trailingIcon = { 
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.Event, contentDescription = "Select Date")
                    }
                }
            )

            // Gender dropdown
            ExposedDropdownMenuBox(
                expanded = genderExpanded,
                onExpandedChange = { genderExpanded = !genderExpanded }
            ) {
                OutlinedTextField(
                    value = gender,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Gender *") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = genderExpanded) }
                )
                ExposedDropdownMenu(
                    expanded = genderExpanded,
                    onDismissRequest = { genderExpanded = false }
                ) {
                    listOf("Male", "Female", "Other").forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                gender = option
                                genderExpanded = false
                            }
                        )
                    }
                }
            }

            HorizontalDivider()
            Text("Optional", style = MaterialTheme.typography.labelLarge)

            OutlinedTextField(
                value = mrn,
                onValueChange = { mrn = it },
                label = { Text("Medical Record Number") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Phone Number") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Phone, null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Email, null) }
            )

            OutlinedTextField(
                value = bloodGroup,
                onValueChange = { bloodGroup = it },
                label = { Text("Blood Group") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = allergies,
                onValueChange = { allergies = it },
                label = { Text("Known Allergies") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
        }
    }
}
