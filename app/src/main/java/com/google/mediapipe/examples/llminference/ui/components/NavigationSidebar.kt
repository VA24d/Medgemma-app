package com.google.mediapipe.examples.llminference.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.mediapipe.examples.llminference.network.EdgeCompanionClient
import com.google.mediapipe.examples.llminference.sync.ChartSyncManager
import com.google.mediapipe.examples.llminference.settings.AppPreferences
import com.google.mediapipe.examples.llminference.settings.TokenManager
import com.google.mediapipe.examples.llminference.settings.LocalModelFiles
import com.google.mediapipe.examples.llminference.ui.theme.AppTheme
import com.google.mediapipe.examples.llminference.ui.theme.ThemeManager
import kotlinx.coroutines.launch
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationSidebar(
    isOpen: Boolean,
    onClose: () -> Unit,
    onOpenModelSelection: () -> Unit,
    onOpenHfLogin: () -> Unit = {},
    onSignOut: () -> Unit,
    onChangePin: () -> Unit,
    onExportFhir: () -> Unit,
    onProcessAllOnCloud: () -> Unit = {},
    onDeleteAllData: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { AppPreferences(context) }
    val tokenManager = remember { TokenManager(context) }

    var selectedLanguage by remember { mutableStateOf(LocalModelFiles.getLanguageExtension(context)) }
    LaunchedEffect(isOpen) {
        if (isOpen) {
            selectedLanguage = LocalModelFiles.getLanguageExtension(context)
        }
    }

    // Settings states
    var showThemeDialog by remember { mutableStateOf(false) }
    var showEnergyDialog by remember { mutableStateOf(false) }
    var showBackendDialog by remember { mutableStateOf(false) }
    var showDoctorDialog by remember { mutableStateOf(false) }
    var showLocationDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showEncryptionDialog by remember { mutableStateOf(false) }
    var showCloudDialog by remember { mutableStateOf(false) }
    var cloudMode by remember { mutableStateOf(LocalModelFiles.getCloudConnectionMode(context)) }
    var cloudWifiUrl by remember { mutableStateOf(LocalModelFiles.getCloudServerUrlWifi(context)) }
    var cloudUsbUrl by remember { mutableStateOf(LocalModelFiles.getCloudServerUrlUsb(context)) }
    var cloudModel by remember { mutableStateOf(LocalModelFiles.getCloudModelName(context)) }
    var cloudTestStatus by remember { mutableStateOf("") }
    var lastSyncLabel by remember { mutableStateOf("") }
    var syncStatus by remember { mutableStateOf("") }
    var nightBatchEnabled by remember { mutableStateOf(true) }
    var nightBatchStatus by remember { mutableStateOf("") }

    LaunchedEffect(isOpen) {
        if (isOpen) {
            val at = LocalModelFiles.getLastSyncAt(context)
            lastSyncLabel = if (at > 0) {
                java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(at))
            } else "Never"
            EdgeCompanionClient.getSettings(context).onSuccess { s ->
                nightBatchEnabled = s.nightBatchEnabled
                nightBatchStatus = if (s.nightBatchEnabled) {
                    "On ${s.nightStartHour}:00–${s.nightEndHour}:00"
                } else "Off"
            }
        }
    }

    if (!isOpen) return

    ModalDrawerSheet(
        modifier = Modifier.fillMaxWidth(0.85f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(24.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Settings",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        IconButton(onClick = onClose) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    if (prefs.doctorName.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Dr. ${prefs.doctorName}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        if (prefs.doctorSpecialty.isNotBlank()) {
                            Text(
                                prefs.doctorSpecialty,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Appearance ──
            SidebarSection("Appearance")
            SidebarItem(
                icon = Icons.Default.Settings,
                title = "Theme",
                subtitle = ThemeManager.currentTheme.displayName,
                onClick = { showThemeDialog = true }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ── Localization ──
            SidebarSection("Localization")
            SidebarItem(
                icon = Icons.Default.Translate,
                title = "Language Extension",
                subtitle = if (selectedLanguage == "Off") "Off" else "$selectedLanguage (Enabled)",
                onClick = { showLanguageDialog = true }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ── Model & Auth ──
            SidebarSection("Model & Authentication")
            SidebarItem(
                icon = Icons.Default.AutoAwesome,
                title = "Model Selection",
                subtitle = "MedGemma 1.5 4B",
                onClick = {
                    onClose()
                    onOpenModelSelection()
                }
            )
            SidebarItem(
                icon = Icons.Default.Lock,
                title = "Hugging Face Token",
                subtitle = if (tokenManager.hasToken()) {
                    val username = tokenManager.getUsername()
                    if (username != null) "@$username ✓" else "Token saved ✓"
                } else "Not configured",
                onClick = {
                    onClose()
                    onOpenHfLogin()
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ── Edge cloud (laptop GPU) ──
            SidebarSection("Edge Cloud (GPU)")
            SidebarItem(
                icon = Icons.Default.CloudUpload,
                title = "Cloud server settings",
                subtitle = if (cloudMode == LocalModelFiles.CLOUD_MODE_USB) "USB" else "Wi-Fi",
                onClick = { showCloudDialog = true }
            )
            SidebarItem(
                icon = Icons.Default.Cloud,
                title = "Process all on cloud",
                subtitle = "Enrich every patient chart",
                onClick = {
                    onClose()
                    onProcessAllOnCloud()
                }
            )
            SidebarItem(
                icon = Icons.Default.Sync,
                title = "Sync with laptop",
                subtitle = if (syncStatus.isNotBlank()) syncStatus else "Last: $lastSyncLabel",
                onClick = {
                    scope.launch {
                        syncStatus = "Syncing…"
                        val r = ChartSyncManager.syncIfEnabled(context)
                        syncStatus = if (r.success) "Synced" else r.message
                        lastSyncLabel = java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault())
                            .format(java.util.Date(LocalModelFiles.getLastSyncAt(context)))
                    }
                }
            )
            SidebarItem(
                icon = Icons.Default.Nightlight,
                title = "Night GPU batch (laptop)",
                subtitle = if (nightBatchStatus.isNotBlank()) nightBatchStatus
                else if (nightBatchEnabled) "On — laptop must stay awake" else "Off",
                onClick = {
                    scope.launch {
                        val next = !nightBatchEnabled
                        nightBatchStatus = "Updating…"
                        EdgeCompanionClient.setNightBatchEnabled(context, next).fold(
                            onSuccess = {
                                nightBatchEnabled = next
                                nightBatchStatus = if (next) "On (laptop + start.ps1)" else "Off"
                            },
                            onFailure = { nightBatchStatus = it.message ?: "Failed" },
                        )
                    }
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ── Performance ──
            SidebarSection("Performance")
            SidebarItem(
                icon = Icons.Default.BatteryChargingFull,
                title = "Energy Mode",
                subtitle = prefs.energyMode,
                onClick = { showEnergyDialog = true }
            )
            SidebarItem(
                icon = Icons.Default.Memory,
                title = "Backend Mode",
                subtitle = prefs.backendMode,
                onClick = { showBackendDialog = true }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ── Security ──
            SidebarSection("Security")
            SidebarItem(
                icon = Icons.Default.Lock,
                title = "Change PIN",
                subtitle = "Update your security PIN",
                onClick = onChangePin
            )
            SidebarItem(
                icon = Icons.Default.Shield,
                title = "Encryption",
                subtitle = "AES-256 enabled",
                onClick = { showEncryptionDialog = true }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ── Data ──
            SidebarSection("Data Management")
            SidebarItem(
                icon = Icons.Default.Delete,
                title = "Clear All Patients",
                subtitle = "Remove all patient data",
                onClick = { showDeleteConfirm = true }
            )
            SidebarItem(
                icon = Icons.Default.Share,
                title = "Export (FHIR)",
                subtitle = "Export data in FHIR standard",
                onClick = {
                    onClose() // Close sidebar
                    onExportFhir()
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ── Doctor & Location ──
            SidebarSection("Professional Details")
            SidebarItem(
                icon = Icons.Default.Person,
                title = "Doctor Details",
                subtitle = prefs.doctorName.ifBlank { "Not set" },
                onClick = { showDoctorDialog = true }
            )
            SidebarItem(
                icon = Icons.Default.Place,
                title = "Location",
                subtitle = prefs.location.ifBlank { "Not set" },
                onClick = { showLocationDialog = true }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Sign out
            SidebarItem(
                icon = Icons.AutoMirrored.Filled.ExitToApp,
                title = "Sign Out",
                subtitle = "",
                onClick = onSignOut,
                isDestructive = true
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // ── Dialogs ──

    LaunchedEffect(showCloudDialog) {
        if (showCloudDialog) {
            EdgeCompanionClient.getSettings(context).onSuccess { s ->
                nightBatchEnabled = s.nightBatchEnabled
            }
        }
    }

    if (showCloudDialog) {
        AlertDialog(
            onDismissRequest = { showCloudDialog = false },
            title = { Text("Edge Cloud Server") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Connection", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = cloudMode == LocalModelFiles.CLOUD_MODE_USB,
                            onClick = { cloudMode = LocalModelFiles.CLOUD_MODE_USB },
                            label = { Text("USB") },
                        )
                        FilterChip(
                            selected = cloudMode == LocalModelFiles.CLOUD_MODE_WIFI,
                            onClick = { cloudMode = LocalModelFiles.CLOUD_MODE_WIFI },
                            label = { Text("Wi-Fi") },
                        )
                    }
                    OutlinedTextField(
                        value = cloudUsbUrl,
                        onValueChange = { cloudUsbUrl = it },
                        label = { Text("USB URL (adb reverse)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = cloudWifiUrl,
                        onValueChange = { cloudWifiUrl = it },
                        label = { Text("Wi-Fi URL") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = cloudModel,
                        onValueChange = { cloudModel = it },
                        label = { Text("Ollama model") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (cloudTestStatus.isNotBlank()) {
                        Text(cloudTestStatus, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Night GPU batch", style = MaterialTheme.typography.labelMedium)
                            Text(
                                "Only while laptop is on and start.ps1 is running",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = nightBatchEnabled,
                            onCheckedChange = { enabled ->
                                nightBatchEnabled = enabled
                                scope.launch {
                                    EdgeCompanionClient.setNightBatchEnabled(context, enabled)
                                }
                            },
                        )
                    }
                    Text(
                        "Laptop: run edge-companion\\start.ps1 — web at http://localhost:8787/patients",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    LocalModelFiles.setCloudConnectionMode(context, cloudMode)
                    LocalModelFiles.setCloudServerUrlUsb(context, cloudUsbUrl)
                    LocalModelFiles.setCloudServerUrlWifi(context, cloudWifiUrl)
                    LocalModelFiles.setCloudModelName(context, cloudModel)
                    showCloudDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        scope.launch {
                            cloudTestStatus = "Testing…"
                            LocalModelFiles.setCloudConnectionMode(context, cloudMode)
                            LocalModelFiles.setCloudServerUrlUsb(context, cloudUsbUrl)
                            LocalModelFiles.setCloudServerUrlWifi(context, cloudWifiUrl)
                            when (val h = EdgeCompanionClient.health(context)) {
                                is EdgeCompanionClient.HealthResult.Ok ->
                                    cloudTestStatus = if (h.ollamaOk) "Connected ✓" else "Companion OK, Ollama down"
                                is EdgeCompanionClient.HealthResult.Error ->
                                    cloudTestStatus = h.message
                            }
                        }
                    }) { Text("Test") }
                    TextButton(onClick = { showCloudDialog = false }) { Text("Cancel") }
                }
            },
        )
    }

    // Theme picker
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Choose Theme") },
            text = {
                Column {
                    AppTheme.entries.forEach { theme ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    prefs.theme = theme
                                    showThemeDialog = false
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = ThemeManager.currentTheme == theme,
                                onClick = {
                                    prefs.theme = theme
                                    showThemeDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(theme.displayName)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Language extension dialog
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text("Language Extension") },
            text = {
                Column {
                    listOf("Off", "Telugu", "Hindi").forEach { lang ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    LocalModelFiles.setLanguageExtension(context, lang)
                                    selectedLanguage = lang
                                    showLanguageDialog = false
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = LocalModelFiles.getLanguageExtension(context) == lang,
                                onClick = {
                                    LocalModelFiles.setLanguageExtension(context, lang)
                                    selectedLanguage = lang
                                    showLanguageDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(lang)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Token dialog removed — now handled by HfLoginScreen navigation

    // Energy mode dialog
    if (showEnergyDialog) {
        AlertDialog(
            onDismissRequest = { showEnergyDialog = false },
            title = { Text("Energy Mode") },
            text = {
                Column {
                    listOf("Low", "Medium", "Heavy").forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    prefs.energyMode = mode
                                    showEnergyDialog = false
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = prefs.energyMode == mode,
                                onClick = {
                                    prefs.energyMode = mode
                                    showEnergyDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(mode)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showEnergyDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Backend mode dialog
    if (showBackendDialog) {
        AlertDialog(
            onDismissRequest = { showBackendDialog = false },
            title = { Text("Backend Mode") },
            text = {
                Column {
                    listOf("CPU", "GPU", "NPU", "Auto").forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    prefs.backendMode = mode
                                    showBackendDialog = false
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = prefs.backendMode == mode,
                                onClick = {
                                    prefs.backendMode = mode
                                    showBackendDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(mode)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBackendDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Doctor details dialog
    if (showDoctorDialog) {
        var name by remember { mutableStateOf(prefs.doctorName) }
        var specialty by remember { mutableStateOf(prefs.doctorSpecialty) }
        AlertDialog(
            onDismissRequest = { showDoctorDialog = false },
            title = { Text("Doctor Details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        label = { Text("Name") }, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = specialty, onValueChange = { specialty = it },
                        label = { Text("Specialty") }, modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    prefs.doctorName = name
                    prefs.doctorSpecialty = specialty
                    showDoctorDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showDoctorDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Location dialog
    if (showLocationDialog) {
        var location by remember { mutableStateOf(prefs.location) }
        AlertDialog(
            onDismissRequest = { showLocationDialog = false },
            title = { Text("Location Details") },
            text = {
                Column {
                    Text(
                        "Location context helps improve analysis accuracy.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = location, onValueChange = { location = it },
                        label = { Text("City / Region / Country") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    val allLocations = remember {
                        listOf(
                            "New York, USA", "San Francisco, USA", "London, UK", 
                            "Paris, France", "Berlin, Germany", "Mumbai, India", 
                            "Tokyo, Japan", "Sydney, Australia", "Toronto, Canada", 
                            "Singapore", "Dubai, UAE"
                        )
                    }
                    val filteredLocations = remember(location) {
                        if (location.isBlank()) allLocations
                        else allLocations.filter { it.contains(location, ignoreCase = true) }
                    }

                    if (filteredLocations.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(filteredLocations) { suggestion ->
                                SuggestionChip(
                                    onClick = { location = suggestion },
                                    label = { Text(suggestion) }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    prefs.location = location
                    showLocationDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showLocationDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Delete confirmation
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = { Icon(Icons.Default.Warning, null) },
            title = { Text("Delete All Patients?") },
            text = { Text("This will permanently delete all patient data. This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteAllData()
                        showDeleteConfirm = false
                        onClose()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete All") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    // Encryption info dialog
    if (showEncryptionDialog) {
        AlertDialog(
            onDismissRequest = { showEncryptionDialog = false },
            icon = { Icon(Icons.Default.Lock, null) },
            title = { Text("Database Encryption") },
            text = { Text("All local patient data is encrypted using AES-256 (Advanced Encryption Standard). Your media files are also stored securely within the app's private storage.") },
            confirmButton = {
                TextButton(onClick = { showEncryptionDialog = false }) { Text("OK") }
            }
        )
    }
}

@Composable
private fun SidebarSection(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun SidebarItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = if (isDestructive) MaterialTheme.colorScheme.error
                   else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isDestructive) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}
