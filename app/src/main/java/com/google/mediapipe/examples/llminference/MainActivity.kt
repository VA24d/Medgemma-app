package com.google.mediapipe.examples.llminference

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.mediapipe.examples.llminference.settings.AppPreferences
import com.google.mediapipe.examples.llminference.ui.screens.*
import com.google.mediapipe.examples.llminference.ui.components.NavigationSidebar
import com.google.mediapipe.examples.llminference.ui.theme.LLMInferenceTheme

// Navigation routes
const val SPLASH_SCREEN = "splash"
const val PIN_SCREEN = "pin"
const val PATIENTS_SCREEN = "patients"
const val ADD_PATIENT_SCREEN = "add_patient"
const val PATIENT_DETAIL_SCREEN = "patient_detail/{patientId}"
const val NEW_ENTRY_SCREEN = "new_entry/{patientId}"
const val XRAY_ANALYSIS_SCREEN = "xray_analysis/{patientId}/{analysisType}"
const val MANUAL_NOTES_SCREEN = "manual_notes/{patientId}"
const val HISTORY_SCREEN = "history/{patientId}"
const val DIAGNOSIS_SCREEN = "diagnosis/{patientId}"
const val QUICK_ANALYSIS_SCREEN = "quick_analysis"

// Keep old routes for model loading / chat
const val START_SCREEN = "start_screen"
const val WAITING_SCREEN = "waiting_screen"
const val LOAD_SCREEN = "load_screen"
const val CHAT_SCREEN = "chat_screen"

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val prefs = remember { AppPreferences(applicationContext) }
            prefs.initTheme()

            LLMInferenceTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val navController = rememberNavController()
                    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                    val scope = rememberCoroutineScope()

                    // Background model pre-download on app boot
                    val appContext = LocalContext.current.applicationContext
                    LaunchedEffect(Unit) {
                        launch(Dispatchers.IO) {
                            try {
                                if (!InferenceModel.modelExists(appContext) && InferenceModel.model.url.isNotEmpty()) {
                                    Log.d("MainActivity", "Starting background model pre-download…")
                                    val client = OkHttpClient.Builder()
                                        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                                        .readTimeout(0, java.util.concurrent.TimeUnit.SECONDS)
                                        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                                        .build()
                                    val downloads = mutableListOf<Pair<String, String>>()
                                    if (InferenceModel.model.url.isNotEmpty()) downloads.add(InferenceModel.model.url to InferenceModel.modelPathFromUrl(appContext))
                                    if (InferenceModel.model.visionUrl.isNotEmpty()) downloads.add(InferenceModel.model.visionUrl to InferenceModel.visionModelPath(appContext))
                                    if (InferenceModel.model.projectorUrl.isNotEmpty()) downloads.add(InferenceModel.model.projectorUrl to InferenceModel.projectorModelPath(appContext))
                                    
                                    downloadModels(appContext, downloads, InferenceModel.model.needsAuth, client, triggerAuth = false) { /* silent */ }
                                    Log.d("MainActivity", "Background model pre-download complete")
                                }
                            } catch (e: Exception) {
                                Log.w("MainActivity", "Background pre-download skipped: ${e.message}")
                            }
                        }
                    }

                    // Sidebar as modal drawer
                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        drawerContent = {
                            NavigationSidebar(
                                isOpen = true,
                                onClose = { scope.launch { drawerState.close() } },
                                onSignOut = {
                                    scope.launch { drawerState.close() }
                                    navController.navigate(PIN_SCREEN) {
                                        popUpTo(0) { inclusive = true }
                                    }
                                },
                                onChangePin = {
                                    prefs.isPinSet = false // Reset PIN state to force UI to show "Create PIN"
                                    scope.launch { drawerState.close() }
                                    navController.navigate("pin?changeMode=true") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                },
                                onExportFhir = {
                                    val exportManager = com.google.mediapipe.examples.llminference.data.FhirExportManager(appContext)
                                    val inferenceModel = InferenceModel.getInstance(appContext)
                                    scope.launch {
                                        exportManager.exportAllPatientsToFhir(
                                            inferenceModel = inferenceModel,
                                            onProgress = { /* Optional: show progress notification */ },
                                            onComplete = { /* Toast handled in manager */ }
                                        )
                                    }
                                },
                                onDeleteAllData = {
                                    scope.launch(Dispatchers.IO) {
                                        val db = com.google.mediapipe.examples.llminference.data.MedicalDatabase.getDatabase(appContext)
                                        db.patientDao().deleteAllPatients()
                                        db.medicalImageDao().deleteAllImages()
                                        db.consultationDao().deleteAllConsultations()
                                        db.medicalEntryDao().deleteAllEntries()
                                        
                                        // Clear images from disk
                                        val imagesDir = java.io.File(appContext.filesDir, "medical_images")
                                        if (imagesDir.exists()) {
                                            imagesDir.deleteRecursively()
                                        }
                                    }
                                }
                            )
                        },
                        gesturesEnabled = drawerState.isOpen
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = SPLASH_SCREEN
                        ) {
                            // ── Splash ──
                            composable(SPLASH_SCREEN) {
                                SplashScreen(onFinished = {
                                    navController.navigate(PIN_SCREEN) {
                                        popUpTo(SPLASH_SCREEN) { inclusive = true }
                                    }
                                })
                            }

                            // ── PIN ──
                            composable(
                                "pin?changeMode={changeMode}",
                                arguments = listOf(navArgument("changeMode") { defaultValue = false })
                            ) { backStackEntry ->
                                val isChangeMode = backStackEntry.arguments?.getBoolean("changeMode") ?: false

                                PinScreen(
                                    onPinVerified = {
                                        navController.navigate(PATIENTS_SCREEN) {
                                            popUpTo(PIN_SCREEN) { inclusive = true }
                                        }
                                    },
                                    onCancel = if (isChangeMode) {
                                        {
                                            // Restore PIN state and go back
                                            prefs.isPinSet = true
                                            navController.popBackStack()
                                        }
                                    } else null
                                )
                            }

                            // ── Patients List ──
                            composable(PATIENTS_SCREEN) {
                                PatientsScreen(
                                    onPatientClick = { patientId ->
                                        navController.navigate("patient_detail/$patientId")
                                    },
                                    onNewPatient = {
                                        navController.navigate(ADD_PATIENT_SCREEN)
                                    },
                                    onQuickAnalysis = {
                                        // Quick analysis goes to model loading then chat
                                        navController.navigate(START_SCREEN)
                                    },
                                    onOpenSidebar = {
                                        scope.launch { drawerState.open() }
                                    }
                                )
                            }

                            // ── Add Patient ──
                            composable(ADD_PATIENT_SCREEN) {
                                AddPatientScreen(
                                    onBack = { navController.popBackStack() },
                                    onPatientAdded = { patientId ->
                                        navController.navigate("patient_detail/$patientId") {
                                            popUpTo(ADD_PATIENT_SCREEN) { inclusive = true }
                                        }
                                    }
                                )
                            }
                            
                            // ── Edit Patient ──
                            composable(
                                "edit_patient/{patientId}",
                                arguments = listOf(navArgument("patientId") { type = NavType.LongType })
                            ) { backStackEntry ->
                                val patientId = backStackEntry.arguments?.getLong("patientId") ?: return@composable
                                AddPatientScreen(
                                    onBack = { navController.popBackStack() },
                                    onPatientAdded = { _ ->
                                        navController.popBackStack() // Go back to detail
                                    },
                                    patientIdToEdit = patientId
                                )
                            }

                            // ── Patient Detail ──
                            composable(
                                PATIENT_DETAIL_SCREEN,
                                arguments = listOf(navArgument("patientId") { type = NavType.LongType })
                            ) { backStackEntry ->
                                val patientId = backStackEntry.arguments?.getLong("patientId") ?: return@composable
                                PatientDetailScreen(
                                    patientId = patientId,
                                    onBack = { navController.popBackStack() },
                                    onNewEntry = { id ->
                                        navController.navigate("new_entry/$id")
                                    },
                                    onViewHistory = { id ->
                                        navController.navigate("history/$id")
                                    },
                                    onViewDiagnosis = { id ->
                                        navController.navigate("diagnosis/$id")
                                    },
                                    onEntryClick = { id ->
                                        navController.navigate("entry_detail/$id")
                                    },
                                    onEdit = { id ->
                                        navController.navigate("edit_patient/$id")
                                    },
                                    onDelete = {
                                        navController.popBackStack()
                                    }
                                )
                            }

                            // ── Entry Detail ──
                            composable(
                                "entry_detail/{entryId}",
                                arguments = listOf(navArgument("entryId") { type = NavType.LongType })
                            ) { backStackEntry ->
                                val entryId = backStackEntry.arguments?.getLong("entryId") ?: return@composable
                                EntryDetailScreen(
                                    entryId = entryId,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            // ── New Entry (type selection) ──
                            composable(
                                NEW_ENTRY_SCREEN,
                                arguments = listOf(navArgument("patientId") { type = NavType.LongType })
                            ) { backStackEntry ->
                                val patientId = backStackEntry.arguments?.getLong("patientId") ?: return@composable
                                NewEntryScreen(
                                    patientId = patientId,
                                    onBack = { navController.popBackStack() },
                                    onSelectType = { id, type ->
                                        when (type) {
                                            "MANUAL" -> navController.navigate("manual_notes/$id")
                                            "XRAY", "HISTOPATHOLOGY" -> navController.navigate("xray_analysis/$id/$type")
                                            "RECORDING" -> navController.navigate("recording/$id")
                                        }
                                    }
                                )
                            }
                            
                            // ── Recording Screen ──
                            composable(
                                "recording/{patientId}",
                                arguments = listOf(navArgument("patientId") { type = NavType.LongType })
                            ) { backStackEntry ->
                                val patientId = backStackEntry.arguments?.getLong("patientId") ?: return@composable
                                RecordingScreen(
                                    patientId = patientId,
                                    onBack = { navController.popBackStack() },
                                    onSaved = {
                                        navController.navigate("patient_detail/$patientId") {
                                            popUpTo("new_entry/$patientId") { inclusive = true }
                                        }
                                    }
                                )
                            }

                            // ── X-ray / Histopathology Analysis ──
                            composable(
                                XRAY_ANALYSIS_SCREEN,
                                arguments = listOf(
                                    navArgument("patientId") { type = NavType.LongType },
                                    navArgument("analysisType") { type = NavType.StringType }
                                )
                            ) { backStackEntry ->
                                val patientId = backStackEntry.arguments?.getLong("patientId") ?: return@composable
                                val analysisType = backStackEntry.arguments?.getString("analysisType") ?: "XRAY"
                                XrayAnalysisScreen(
                                    patientId = patientId,
                                    analysisType = analysisType,
                                    onBack = { navController.popBackStack() },
                                    onSaved = {
                                        navController.navigate("patient_detail/$patientId") {
                                            popUpTo("new_entry/$patientId") { inclusive = true }
                                        }
                                    }
                                )
                            }

                            // ── Manual Notes ──
                            composable(
                                MANUAL_NOTES_SCREEN,
                                arguments = listOf(navArgument("patientId") { type = NavType.LongType })
                            ) { backStackEntry ->
                                val patientId = backStackEntry.arguments?.getLong("patientId") ?: return@composable
                                ManualNotesScreen(
                                    patientId = patientId,
                                    onBack = { navController.popBackStack() },
                                    onSaved = {
                                        navController.navigate("patient_detail/$patientId") {
                                            popUpTo("new_entry/$patientId") { inclusive = true }
                                        }
                                    }
                                )
                            }

                            // ── Longitudinal History ──
                            composable(
                                HISTORY_SCREEN,
                                arguments = listOf(navArgument("patientId") { type = NavType.LongType })
                            ) { backStackEntry ->
                                val patientId = backStackEntry.arguments?.getLong("patientId") ?: return@composable
                                LongitudinalHistoryScreen(
                                    patientId = patientId,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            // ── Diagnosis ──
                            composable(
                                DIAGNOSIS_SCREEN,
                                arguments = listOf(navArgument("patientId") { type = NavType.LongType })
                            ) { backStackEntry ->
                                val patientId = backStackEntry.arguments?.getLong("patientId") ?: return@composable
                                DiagnosisScreen(
                                    patientId = patientId,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            // ── Legacy: Model Selection (for Quick Analysis) ──
                            composable(START_SCREEN) {
                                SelectionRoute(
                                    onModelSelected = {
                                        navController.navigate(WAITING_SCREEN) {
                                            popUpTo(START_SCREEN) { inclusive = true }
                                        }
                                    }
                                )
                            }

                            composable(WAITING_SCREEN) {
                                WaitingScreen(onFinished = {
                                    navController.navigate(LOAD_SCREEN) {
                                        popUpTo(WAITING_SCREEN) { inclusive = true }
                                    }
                                })
                            }

                            composable(LOAD_SCREEN) {
                                LoadingRoute(
                                    onModelLoaded = {
                                        navController.navigate(CHAT_SCREEN) {
                                            popUpTo(LOAD_SCREEN) { inclusive = true }
                                        }
                                    },
                                    onGoBack = {
                                        navController.navigate(PATIENTS_SCREEN) {
                                            popUpTo(LOAD_SCREEN) { inclusive = true }
                                        }
                                    }
                                )
                            }

                            composable(CHAT_SCREEN) {
                                ChatRoute(
                                    onClose = {
                                        navController.navigate(PATIENTS_SCREEN) {
                                            popUpTo(CHAT_SCREEN) { inclusive = true }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
