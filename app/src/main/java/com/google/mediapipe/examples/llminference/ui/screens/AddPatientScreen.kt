package com.google.mediapipe.examples.llminference.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mediapipe.examples.llminference.data.MedicalDatabase
import com.google.mediapipe.examples.llminference.data.PatientEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val GENDER_OPTIONS = listOf("Male", "Female", "Other")
private val BLOOD_GROUP_OPTIONS = listOf("A+", "A−", "B+", "B−", "AB+", "AB−", "O+", "O−", "Unknown")

private fun avatarColor(name: String): Color {
    val colors = listOf(
        Color(0xFF1A73E8), Color(0xFF0D652D), Color(0xFF7627BB),
        Color(0xFFD93025), Color(0xFFF29900), Color(0xFF1E8E3E),
        Color(0xFF039BE5), Color(0xFFE52592)
    )
    val index = if (name.isBlank()) 0 else name.uppercase().first().code % colors.size
    return colors[index]
}

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
    var address by remember { mutableStateOf("") }
    var bloodGroup by remember { mutableStateOf("") }
    var allergies by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var bloodGroupExpanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    val isEdit = patientIdToEdit != null
    val canSave = !isSaving && name.isNotBlank() && dob.isNotBlank() && gender.isNotBlank()

    LaunchedEffect(patientIdToEdit) {
        if (patientIdToEdit != null) {
            val patient = withContext(Dispatchers.IO) { db.patientDao().getPatientSync(patientIdToEdit) }
            patient?.let {
                name = it.name; dob = it.dateOfBirth; gender = it.gender
                mrn = it.medicalRecordNumber; phone = it.phoneNumber; email = it.email
                address = it.address; bloodGroup = it.bloodGroup
                allergies = it.allergies; notes = it.notes
            }
        }
    }

    fun save() {
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        isSaving = true
        scope.launch {
            val id = withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                val origCreated = if (patientIdToEdit != null)
                    db.patientDao().getPatientSync(patientIdToEdit)?.createdAt ?: now else now
                val patient = PatientEntity(
                    id = patientIdToEdit ?: 0, name = name.trim(), dateOfBirth = dob.trim(),
                    gender = gender, medicalRecordNumber = mrn.trim(), phoneNumber = phone.trim(),
                    email = email.trim(), address = address.trim(), bloodGroup = bloodGroup,
                    allergies = allergies.trim(), notes = notes.trim(),
                    createdAt = origCreated, updatedAt = now
                )
                if (patientIdToEdit != null) { db.patientDao().updatePatient(patient); patientIdToEdit }
                else db.patientDao().insertPatient(patient)
            }
            isSaving = false; onPatientAdded(id)
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        dob = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(millis))
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = datePickerState) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEdit) "Edit patient" else "New patient") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // ── Avatar hero ──────────────────────────────────────────────
            AvatarHero(name = name)

            // ── Identity section ─────────────────────────────────────────
            SectionCard {
                Column {
                    FormField(
                        value = name, onValueChange = { name = it }, label = "Full name",
                        required = true, icon = Icons.Default.Person,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next
                        )
                    )
                    FieldDivider()
                    Box(modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true }) {
                        FormField(
                            value = dob, onValueChange = {}, label = "Date of birth",
                            required = true, icon = Icons.Default.Cake,
                            placeholder = "YYYY-MM-DD", readOnly = true,
                            trailingIcon = {
                                Icon(
                                    Icons.Default.EditCalendar, contentDescription = "Pick date",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { showDatePicker = true }
                                )
                            }
                        )
                    }
                    FieldDivider()
                    // Gender chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Wc, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Gender *",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(72.dp)
                        )
                        GENDER_OPTIONS.forEach { option ->
                            val selected = gender == option
                            val chipBg by animateColorAsState(
                                if (selected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant, label = "chip"
                            )
                            val chipFg by animateColorAsState(
                                if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant, label = "chipFg"
                            )
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = chipBg,
                                modifier = Modifier.clickable { gender = option }
                            ) {
                                Text(
                                    option,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = chipFg,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }

            // ── Contact section ──────────────────────────────────────────
            SectionHeader("Contact")
            SectionCard {
                Column {
                    FormField(
                        value = mrn, onValueChange = { mrn = it }, label = "Medical record number",
                        icon = Icons.Default.Badge,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
                    )
                    FieldDivider()
                    FormField(
                        value = phone, onValueChange = { phone = it }, label = "Phone number",
                        icon = Icons.Default.Phone,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next)
                    )
                    FieldDivider()
                    FormField(
                        value = email, onValueChange = { email = it }, label = "Email address",
                        icon = Icons.Default.Email,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
                    )
                    FieldDivider()
                    FormField(
                        value = address, onValueChange = { address = it }, label = "Address",
                        icon = Icons.Default.Home, singleLine = false, minLines = 2,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next
                        )
                    )
                }
            }

            // ── Medical section ──────────────────────────────────────────
            SectionHeader("Medical")
            SectionCard {
                Column {
                    // Blood group dropdown
                    ExposedDropdownMenuBox(
                        expanded = bloodGroupExpanded,
                        onExpandedChange = { bloodGroupExpanded = !bloodGroupExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Bloodtype, null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Blood group",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    bloodGroup.ifBlank { "Select" },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (bloodGroup.isBlank())
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = bloodGroupExpanded)
                        }
                        ExposedDropdownMenu(
                            expanded = bloodGroupExpanded,
                            onDismissRequest = { bloodGroupExpanded = false }
                        ) {
                            BLOOD_GROUP_OPTIONS.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = { bloodGroup = option; bloodGroupExpanded = false },
                                    leadingIcon = if (bloodGroup == option) ({
                                        Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                                    }) else null
                                )
                            }
                        }
                    }
                    FieldDivider()
                    FormField(
                        value = allergies, onValueChange = { allergies = it }, label = "Known allergies",
                        icon = Icons.Default.Warning, singleLine = false, minLines = 2,
                        placeholder = "e.g. Penicillin, Aspirin",
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next
                        )
                    )
                    FieldDivider()
                    FormField(
                        value = notes, onValueChange = { notes = it }, label = "Clinical notes",
                        icon = Icons.Default.Notes, singleLine = false, minLines = 3,
                        placeholder = "Any additional notes…",
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done
                        )
                    )
                }
            }

            // ── Save button ──────────────────────────────────────────────
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { if (canSave) save() },
                enabled = canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(10.dp)); Text("Saving…")
                } else {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (isEdit) "Save changes" else "Create patient",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Text(
                "Name, date of birth and gender are required",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 28.dp)
            )
        }
    }
}

// ── Sub-composables ──────────────────────────────────────────────────────────

@Composable
private fun AvatarHero(name: String) {
    val initials = name.trim().split(" ")
        .filter { it.isNotBlank() }.take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }
        .ifBlank { "?" }
    val bgColor = avatarColor(name)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Text(text = initials, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Medium)
        }
        if (name.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                name.trim(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp, end = 16.dp)
    )
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        content = content
    )
}

@Composable
private fun FieldDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 52.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        thickness = 0.5.dp
    )
}

@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    required: Boolean = false,
    placeholder: String? = null,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top
    ) {
        Icon(
            imageVector = icon, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(20.dp)
                .padding(top = if (!singleLine) 16.dp else 0.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (required) "$label *" else label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.text.BasicTextField(
                    value = value, onValueChange = onValueChange, readOnly = readOnly,
                    singleLine = singleLine,
                    minLines = if (singleLine) 1 else minLines,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    keyboardOptions = keyboardOptions,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 4.dp),
                    decorationBox = { inner ->
                        Box {
                            if (value.isEmpty() && placeholder != null) {
                                Text(
                                    placeholder,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                )
                            }
                            inner()
                        }
                    }
                )
                if (trailingIcon != null) {
                    Spacer(Modifier.width(8.dp))
                    trailingIcon()
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
