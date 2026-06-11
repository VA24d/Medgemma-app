Med Veda — Complete Codebase Guide (A to Z)
Med Veda is an Android medical assistant built for the Kaggle Google MedGemma Challenge. It runs MedGemma 1.5 4B Multimodal entirely on-device via a custom llama.cpp JNI wrapper, with longitudinal patient records in Room, multimodal chat (text + X-rays), and zero cloud inference for clinical data.

1. What This Repo Is
Layer	Role
app/
Shipped Android app (Jetpack Compose UI, Room DB, orchestration)
aichatlib/
Native inference library (llama.cpp + mtmd multimodal via JNI)
tflite/
Separate R&D pipeline — custom TFLite conversion (not used in the shipping app)
benchmarking/
GGUF and TFLite evaluation scripts
conversion/
HF → GGUF conversion utility
ppt2/diagrams/
Accurate architecture diagrams for demos/proposals
The production inference path is: Kotlin → InferenceModel → aichatlib → ai_chat.cpp → GGUF + optional mmproj-F16.gguf. The app does not use LiteRT/TFLite at runtime (despite STRUCTURE.md describing that work as a parallel engineering effort).

2. High-Level Architecture
Presentation (Jetpack Compose)
Kotlin orchestration
Local persistence
aichatlib · JNI
MainActivity + NavHost
Patients · X-ray · Chat · Diagnosis · Settings
ChatViewModel
InferenceModel singleton
ScheduledPrognosisWorker
FhirExportManager
MedicalDatabase / Room
Encrypted prefs · PIN
GGUF + mmproj on disk
InferenceEngineImpl
ai_chat.cpp · llama.cpp + mtmd
Network is used only for: Hugging Face model download, OAuth/token verification — never for chart Q&A or imaging inference.

3. Gradle Modules
Root (settings.gradle.kts)
Two modules: :app (application) and :aichatlib (Android library with NDK)
app/build.gradle.kts
Kotlin 1.9, Compose, Room + KSP, WorkManager, Coil, OkHttp, AppAuth, security-crypto
Depends on :aichatlib
HF_ACCESS_TOKEN from local.properties → BuildConfig
Legacy: com.google.mediapipe:tasks-vision is declared but not referenced in source (leftover from the original MediaPipe template)
aichatlib/build.gradle.kts
NDK 29, CMake builds ai_chat.cpp
Fetches llama.cpp b5497 at build time
Links mtmd (multimodal) library
ABIs: arm64-v8a, x86_64
arm64: KleidiAI + OpenMP; x86_64: standard CPU backend
4. App Entry & Navigation Flow
Launcher: MainActivity → NavHost starting at SPLASH_SCREEN.

User journey (typical)
Splash → PIN → Patients list → Patient detail → (entries / chat / diagnosis)
                                    ↓
                              Sidebar drawer → Model setup / HF login / FHIR export / wipe data
Route map (from MainActivity.kt)
Route	Screen	Purpose
splash
SplashScreen
Branding; seeds demo data
pin
PinScreen
4-digit app lock (AppPreferences)
patients
PatientsScreen
Patient dashboard
add_patient / edit_patient/{id}
AddPatientScreen
CRUD patients
patient_detail/{id}
PatientDetailScreen
Entries, actions
new_entry/{id}
NewEntryScreen
Pick entry type
xray_analysis/{id}/{type}
XrayAnalysisScreen
X-ray / histopath / MRI
manual_notes/{id}
ManualNotesScreen
Free-text notes
recording/{id}
RecordingScreen
Voice → text (Android speech API)
history/{id}
LongitudinalHistoryScreen
Timeline view
diagnosis/{id}
DiagnosisScreen
AI prognosis generation
patient_chat/{id}
ChatRoute
Patient-context chat
start_screen
SelectionRoute
Model pick / HF download
load_screen
LoadingRoute
Load GGUF into memory
chat_screen
ChatRoute
General quick analysis chat
hf_login
HfLoginScreen
HF token setup
On Android 11+, MainActivity requests MANAGE_EXTERNAL_STORAGE so GGUF files in Download/medgemma are readable.

5. Data Layer (Room)
Database: MedicalDatabase v4, file medical_database

Entities
Entity	Table	Purpose
PatientEntity
patients
Demographics, MRN, allergies, notes
MedicalEntryEntity
medical_entries
Unified entries: XRAY, HISTOPATHOLOGY, RECORDING, MANUAL, DOCUMENT
MedicalImageEntity
medical_images
Image metadata (legacy/separate from entries)
ConsultationEntity
consultations
Consultation records
DiagnosisEntity
diagnoses
Saved AI prognoses (scope, model name, thinking flag)
MedicalEntryEntity is the workhorse: content, analysisResult, visitSummary (short headline for fast prompts), imagePaths (comma-separated).

DAOs (MedicalDaos.kt)
Full CRUD + search for patients
Entries by patient/type, diagnosis history
Cascade deletes on patient removal
Repositories (thin wrappers)
PatientRepository, MedicalImageRepository, ConsultationRepository
ViewModels (PatientListViewModel, PatientDetailViewModel) use these where needed; many screens talk to DAOs directly
Demo data (DemoDataSeeder)
On first splash: seeds Appa Rao (longitudinal CXR demo) + 4 manual demo patients
Copies bundled assets from assets/demo_xrays/ into filesDir/medical_images/
6. AI Inference Pipeline
Model configuration (Model.kt)
Default: MEDGEMMA_4B_IT_GPU (same GGUF as CPU/NPU variants)

Weights: medgemma-1.5-4b-it-Q4_K_M.gguf (~2.5 GB) from unsloth/medgemma-1.5-4b-it-GGUF
Vision: mmproj-F16.gguf (~812 MB)
Gated model — requires HF token
HfModelRepository lists all quantizations (Q2_K through F16).

InferenceModel (singleton orchestrator)
Responsibilities:

Resolve model paths — custom path in prefs → default path → Download/medgemma scan
Load engine via AiChat.getInferenceEngine()
Lazy-load mmproj on first image (ensureMmprojLoaded())
Thinking mode — setSkipThinking() toggles MedGemma chain-of-thought prefill
Language reset — clears native KV cache when Telugu/Hindi extension changes
Generation — generateResponseAsync() with streaming callbacks
Text path: engine.sendUserPrompt() → token Flow
Vision path: bitmap → temp JPEG → loadImage() → sendImagePrompt()

aichatlib native stack
InferenceEngineImpl (Kotlin, single-thread dispatcher)
    ↓ JNI
ai_chat.cpp
    ↓
llama.cpp (model load, context 8192, batch 512)
mtmd (multimodal: mmproj + image bitmap)
Key native settings:

2–4 CPU threads
g_skip_thinking for fast chart Q&A
KleidiAI on arm64 for optimized matmul
ChatViewModel — prompt intelligence
When chatting about a patient:

Instant DB replies for trivial questions (“how many entries?”, “who is this patient?”) — no LLM
Full chart injection — builds system prompt with patient info + all entries + saved diagnoses
Token budgets — 512 (short), 896 (longitudinal), 1024 (images/general)
Demo fast path — known demo CXRs skip vision encode; uses pre-authored summaries from DemoXraySummaries
Language extension — Telugu/Hindi output instructions appended to prompts
Thinking control — per-request forceSkipThinkingForRequest for chart Q&A speed
Other AI call sites
Component	What it does
XrayAnalysisScreen
Auto-describes image on upload (radiology prompt)
DiagnosisScreen
On-demand + scoped prognosis (FULL / IMAGING / etc.)
ScheduledPrognosisWorker
Nightly batch prognosis for all patients with entries
ManualNotesScreen / RecordingScreen
Optional AI refinement of notes
LongitudinalHistoryScreen
AI summary of timeline
FhirExportManager
Uses LLM to format FHIR JSON per patient
7. UI Layer (Compose)
Core screens
PatientsScreen — list, search, quick analysis shortcut
PatientDetailScreen — entry list, FAB for new entry, chat/diagnosis/history buttons
ChatScreen — markdown rendering (MarkdownText), image attach, thinking toggle, stop generation
DiagnosisScreen — generate/save diagnoses, schedule nightly runs, strip <unused94> thinking tokens
XrayAnalysisScreen — gallery/camera pick, auto AI description, save as entry
Settings (NavigationSidebar)
Theme, doctor name, location
Thinking mode on/off
Vision on/off
Language extension (Off / Telugu / Hindi)
Scheduled prognosis time picker → ScheduledPrognosisWorker.syncSchedule()
HF account status, model selection, FHIR export, delete all data, change PIN
Auth
TokenManager — AES-encrypted HF token in EncryptedSharedPreferences
HfApiClient — verify token, check gated access, download with progress
LoginActivity / OAuthCallbackActivity — AppAuth OAuth flow (legacy path; direct token preferred)
SecureStorage — OAuth token fallback
8. Security & Privacy Model
Concern	Implementation
App lock
4-digit PIN (AppPreferences)
HF credentials
EncryptedSharedPreferences + Android Keystore
Clinical data
Room DB + filesDir/medical_images — never sent to cloud
Model download
HF CDN only; bearer token for gated weights
FHIR export
User-initiated; writes to Downloads/MediaStore
Wipe
Sidebar deletes all DB tables + image folder
9. Prompt & Medical Logic
MedicalPromptTemplates — reusable prompts (image analysis, SOAP, prognosis, transcription)
PatientChartPrompt — detects longitudinal questions; builds compact timeline lines via visitSummary
LanguageExtension — 70+ English→Telugu clinical term mappings (post-processing utility; sidebar also steers LLM output language)
Thinking markers — <unused94>thought>…<unused95> stripped in diagnosis UI when thinking is off
10. Model Setup Flow
SelectionScreen (two tabs)
Download from HF — pick quantization, optional mmproj, progress bar via HfApiClient
Load local — file picker for .gguf paths stored in LocalModelFiles
LoadingScreen
Verifies model + mmproj exist
Downloads if missing (with auth)
Calls InferenceModel.getInstance() → loads weights into native engine
Navigates to chat when ready
Model discovery paths
context.filesDir
/storage/emulated/0/Download/medgemma (and MedGemma variant)
Custom paths from settings
11. Background Work
ScheduledPrognosisWorker (WorkManager, 24h periodic):

Runs at user-configured time (default 2 AM)
Skips patients with diagnosis < 20 hours old
Builds prompt from entry summaries
Saves DiagnosisEntity with scope FULL
Requires model already loaded; retries if not
12. Supporting Directories (Not in Shipping App)
tflite/ (~85+ hrs R&D)
Custom MedGemma → TFLite conversion (text decoder + MedSigLIP vision), Python inference, Android LiteRT prototypes documented in CONTRIBUTIONS.md. Valuable for benchmarking; not wired into app.

benchmarking/
gguf/eval_multi_benchmark.py — GGUF accuracy
tflite/eval_medmcqa.py — TFLite MedMCQA (~44.4% Q8_0 baseline claimed)
download_models.py — portable model fetcher
conversion/convert_hf_to_gguf.py
HF safetensors → GGUF for fine-tuned models.

Docs / presentation
ppt2/diagrams/architecture.md — accurate shipped + roadmap diagrams
writeup.md, Video_Script.md — submission materials
diagram/ — older diagram tooling (points to ppt2)
13. Tests
Unit tests (app/src/test/):

Entity/DAO logic, navigation routes, PatientChartPrompt, LanguageExtension, FhirExportManager, ScheduledPrognosisWorker prompt building, theme manager
Instrumented (app/src/androidTest/):

TokenManagerTest
14. Key Design Patterns & Optimizations
Singleton inference — one InferenceModel + one native engine; language changes reset conversation
Lazy vision — mmproj not loaded until first image (saves ~800 MB RAM at startup)
Fast paths — DB-only answers, demo CXR text summaries, skip-thinking for chart Q&A
Streaming UI — tokens appended live via ChatUiState.appendMessage()
Visit summaries — visitSummary field keeps longitudinal/worker prompts compact
Coroutine + executor — ViewModel on Dispatchers.IO; native on single-thread executor in both Kotlin layers
15. Package Layout (Mental Map)
com.google.mediapipe.examples.llminference/
├── MainActivity.kt          # Navigation hub
├── ChatViewModel.kt         # Chat + patient context logic
├── InferenceModel.kt        # Model lifecycle + generation
├── Model.kt                 # Enum + HF repo metadata
├── SelectionScreen.kt       # Model setup UI
├── LoadingScreen.kt         # Model load/download
├── ChatScreen.kt            # Chat UI
├── ai/                      # Prompt templates
├── data/                    # Room entities, DAOs, seeder, FHIR
├── demo/                    # Demo CXR fast-path summaries
├── network/                 # HfApiClient
├── repository/              # Thin data repos
├── settings/                # LocalModelFiles, TokenManager, AppPreferences
├── ui/screens/              # All Compose screens
├── ui/components/           # NavigationSidebar
├── ui/theme/                # Material theme
├── utils/                   # LanguageExtension
├── viewmodel/               # Patient list/detail VMs
└── worker/                  # ScheduledPrognosisWorker
com.arm.aichat/
├── AiChat.kt                # Public API
├── InferenceEngine.kt       # Interface
├── internal/InferenceEngineImpl.kt  # JNI wrapper
└── gguf/                    # Metadata readers
16. Important Caveats
Item	Note
Package name
Still com.google.mediapipe.examples.llminference (forked from Google MediaPipe sample)
MediaPipe Vision dep
Declared but unused — safe to remove eventually
STRUCTURE.md
Partially outdated (references com.example.medgemmademo, TFLite in app)
TFLite Android code
Lives under tflite/android/, not integrated into :app
Clinical disclaimer
All AI output is decision-support only; demo patients are synthetic
NPU/GPU enum variants
Model has CPU/GPU/NPU entries but all point to same GGUF; backend selection is via llama.cpp/ggml at runtime
17. End-to-End Request Example
“Summarize this patient’s progress” (patient chat):

User opens patient_chat/{id} → ChatViewModel.loadPatientContext()
User sends message → PatientChartPrompt.wantsLongitudinalQuestion() → true
buildPatientSystemPrompt() injects full chart (entries oldest→newest)
InferenceModel.generateResponseAsync() with maxPredictTokens=896, skip-thinking if enabled
Native: sendUserPrompt() → stream tokens → UI updates via appendMessage()
Response stays on device; nothing hits the network
“Analyze this chest X-ray” (with image):

Bitmap attached in ChatScreen
Unless demo fast-path matches → ensureMmprojLoaded() → loadImage() → sendImagePrompt()
If patient context loaded, chart is prepended to the vision prompt
Tokens stream to UI
That is the full picture of Med Veda from repository layout through native inference, data model, UI flows, and supporting R&D tooling. If you want to go deeper on any slice — native C++ (ai_chat.cpp), a specific screen, the TFLite pipeline, or how to extend patient chat — say which area and we can zoom in there.