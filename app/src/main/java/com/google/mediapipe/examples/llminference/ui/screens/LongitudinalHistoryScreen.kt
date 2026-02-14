package com.google.mediapipe.examples.llminference.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.mediapipe.examples.llminference.data.MedicalDatabase
import com.google.mediapipe.examples.llminference.data.MedicalEntryEntity
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LongitudinalHistoryScreen(
    patientId: Long,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { MedicalDatabase.getDatabase(context) }
    var entries by remember { mutableStateOf<List<MedicalEntryEntity>>(emptyList()) }
    var expandedEntryId by remember { mutableStateOf<Long?>(null) }
    var filterType by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(patientId) {
        db.medicalEntryDao().getEntriesForPatient(patientId).collect { list ->
            entries = list
        }
    }

    val filteredEntries = if (filterType == null) entries
    else entries.filter { it.entryType == filterType }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filterType == null,
                    onClick = { filterType = null },
                    label = { Text("All") }
                )
                FilterChip(
                    selected = filterType == "XRAY",
                    onClick = { filterType = if (filterType == "XRAY") null else "XRAY" },
                    label = { Text("X-ray") },
                    leadingIcon = { Icon(Icons.Default.Image, null, Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = filterType == "HISTOPATHOLOGY",
                    onClick = { filterType = if (filterType == "HISTOPATHOLOGY") null else "HISTOPATHOLOGY" },
                    label = { Text("Histo") },
                    leadingIcon = { Icon(Icons.Default.Biotech, null, Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = filterType == "MANUAL",
                    onClick = { filterType = if (filterType == "MANUAL") null else "MANUAL" },
                    label = { Text("Notes") },
                    leadingIcon = { Icon(Icons.Default.EditNote, null, Modifier.size(16.dp)) }
                )
            }

            if (filteredEntries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.History,
                            null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "No entries found",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredEntries, key = { it.id }) { entry ->
                        TimelineEntryCard(
                            entry = entry,
                            isExpanded = expandedEntryId == entry.id,
                            onToggleExpand = {
                                expandedEntryId = if (expandedEntryId == entry.id) null else entry.id
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineEntryCard(
    entry: MedicalEntryEntity,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val icon = when (entry.entryType) {
        "XRAY" -> Icons.Default.Image
        "HISTOPATHOLOGY" -> Icons.Default.Biotech
        "RECORDING" -> Icons.Default.Mic
        "DOCUMENT" -> Icons.Default.Description
        "MANUAL" -> Icons.Default.EditNote
        else -> Icons.AutoMirrored.Filled.Article
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        // Timeline line + dot
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(if (isExpanded) 200.dp else 60.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Entry card
        Card(
            onClick = onToggleExpand,
            modifier = Modifier.weight(1f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        entry.title.ifBlank { entry.entryType.lowercase().replaceFirstChar { it.uppercase() } },
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        dateFormat.format(Date(entry.createdAt)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isExpanded) {
                    Spacer(modifier = Modifier.height(12.dp))
                    if (entry.content.isNotBlank()) {
                        Text(
                            entry.content,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    if (entry.analysisResult.isNotBlank()) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    "AI Analysis",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    entry.analysisResult,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    } else {
                        OutlinedButton(onClick = { }) {
                            Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Generate Analysis")
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Created at ${timeFormat.format(Date(entry.createdAt))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}
