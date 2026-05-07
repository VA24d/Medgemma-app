package com.google.mediapipe.examples.llminference.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * One-time demo dataset for conferences and QA: **Appa Rao** with longitudinal CXR entries.
 *
 * Bundled images under `assets/demo_xrays/` are generic placeholders copied from the TFLite
 * test harness so multimodal paths always resolve; swap files or paths when you have de-identified
 * clinical radiographs.
 */
object DemoDataSeeder {

    private const val PREFS = "medgemma_demo_seed"
    private const val KEY_APPA_RAO_DONE = "appa_rao_demo_v1_done"
    /** One-time: populate [MedicalEntryEntity.visitSummary] for existing demo installs. */
    private const val KEY_VISIT_SUMMARY_BACKFILL = "demo_visit_summary_v1_done"
    /** One-time: four additional MANUAL-only demo patients (names listed in [FourManualDemoPatients]). */
    private const val KEY_FOUR_MANUAL_DEMO = "demo_four_manual_patients_v1_done"

    suspend fun seedIfNeeded(context: Context) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_APPA_RAO_DONE, false)) return@withContext

        val db = MedicalDatabase.getDatabase(context)
        if (db.patientDao().getAllPatientsSync().any { it.name == "Appa Rao" }) {
            prefs.edit().putBoolean(KEY_APPA_RAO_DONE, true).apply()
            return@withContext
        }

        val patientId = db.patientDao().insertPatient(
            PatientEntity(
                name = "Appa Rao",
                dateOfBirth = "1958-03-15",
                gender = "Male",
                medicalRecordNumber = "MRN-DEMO-2024-AR-001",
                phoneNumber = "+91 98765 43210",
                email = "appa.rao.demo@example.org",
                address = "12 Gandhi Nagar, Rasoolpura, Secunderabad, Telangana 500003",
                bloodGroup = "B+",
                allergies = "Penicillin (maculopapular rash); sulfonamides",
                notes = "Type 2 diabetes mellitus (since ~2015), essential hypertension, dyslipidemia. Former smoker (~15 pack-years; quit 2019). Demo-only synthetic record — not real PHI."
            )
        )

        val imagesDir = File(context.filesDir, "medical_images/demo_appa").apply { mkdirs() }

        fun copyAsset(assetPath: String, fileName: String): String {
            val out = File(imagesDir, fileName)
            context.assets.open(assetPath).use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
            return out.absolutePath
        }

        val pathBaseline = copyAsset(
            "demo_xrays/appa_rao_cxr_2024_10_baseline.jpg",
            "appa_rao_cxr_2024_10_baseline.jpg"
        )
        val pathFollowUp = copyAsset(
            "demo_xrays/appa_rao_cxr_2024_11_followup.png",
            "appa_rao_cxr_2024_11_followup.png"
        )

        val tBaseline = parseLocalDate("2024-10-14")
        val tFollowUp = parseLocalDate("2024-11-25")

        db.medicalImageDao().insertImage(
            MedicalImageEntity(
                patientId = patientId,
                imageType = ImageType.XRAY.name,
                filePath = pathBaseline,
                fileName = "appa_rao_cxr_2024_10_baseline.jpg",
                captureDate = tBaseline,
                bodyPart = "Chest (PA)",
                notes = "Admission / baseline CXR — timeline position 1 (demo)"
            )
        )
        db.medicalImageDao().insertImage(
            MedicalImageEntity(
                patientId = patientId,
                imageType = ImageType.XRAY.name,
                filePath = pathFollowUp,
                fileName = "appa_rao_cxr_2024_11_followup.png",
                captureDate = tFollowUp,
                bodyPart = "Chest (PA)",
                notes = "Follow-up CXR — timeline position 2 (demo)"
            )
        )

        db.medicalEntryDao().insertEntry(
            MedicalEntryEntity(
                patientId = patientId,
                entryType = "XRAY",
                title = "Chest X-ray — baseline (Oct 2024)",
                visitSummary = """
                    Oct 2024 admission CXR during acute lower respiratory illness: productive cough, fever,
                    pleuritic pain; SpO₂ 94% RA. CAP workup with comorbid T2DM/HTN; empiric antibiotics
                    per protocol with clinical and laboratory correlation.
                """.trimIndent().replace("\n", " ").trim(),
                content = """
                    Presenting complaint: productive cough ~10 days, fever, pleuritic right-sided chest discomfort.
                    Vitals at triage: T 38.2 °C, HR 92, BP 138/86 mmHg, RR 20, SpO₂ 94% on room air.

                    Demo storyline: findings on imaging discussed in context of possible community-acquired pneumonia;
                    empirical antibiotics and supportive care per local protocol.

                    **Note:** The bundled image file is a placeholder for multimodal UI testing. Replace with a
                    de-identified clinical radiograph for real demonstrations.
                """.trimIndent(),
                imagePaths = pathBaseline,
                analysisResult = "Draft chart summary (demo): Baseline chest imaging obtained for lower respiratory symptoms; clinical correlation and laboratory data advised.",
                status = "reviewed",
                createdAt = tBaseline,
                updatedAt = tBaseline
            )
        )

        db.medicalEntryDao().insertEntry(
            MedicalEntryEntity(
                patientId = patientId,
                entryType = "XRAY",
                title = "Chest X-ray — follow-up (Nov 2024)",
                visitSummary = """
                    Nov 2024 interval CXR after antibiotic therapy: patient reports resolution of cough and fever;
                    SpO₂ 97% RA. Interval change reviewed against October baseline; outpatient continuation of
                    cardiometabolic regimen and smoking abstinence reinforcement.
                """.trimIndent().replace("\n", " ").trim(),
                content = """
                    Interval visit after completing antibiotic therapy. Patient reports marked improvement in cough,
                    no fever x 2 weeks. SpO₂ 97% RA; ambulating without dyspnea at rest.

                    Demo storyline: interval change reviewed alongside symptoms — outpatient continuation of
                    cardiometabolic medications and smoking abstinence reinforcement.

                    **Note:** Replace placeholder follow-up image with your second time-point radiograph when available.
                """.trimIndent(),
                imagePaths = pathFollowUp,
                analysisResult = "Draft chart summary (demo): Follow-up imaging for comparison to prior study after treatment course.",
                status = "reviewed",
                createdAt = tFollowUp,
                updatedAt = tFollowUp
            )
        )

        db.medicalEntryDao().insertEntry(
            MedicalEntryEntity(
                patientId = patientId,
                entryType = "MANUAL",
                title = "Outpatient note — chronic problems",
                visitSummary = """
                    Chronic disease management: metformin ER, telmisartan, atorvastatin; home glucose and BP
                    logs reviewed. Immunizations discussed; return precautions for recurrent respiratory symptoms
                    or hypoglycemia.
                """.trimIndent().replace("\n", " ").trim(),
                content = """
                    Metformin extended-release 1000 mg nightly; telmisartan 40 mg daily; atorvastatin 20 mg nightly.
                    Diabetes and BP logs reviewed; hypoglycemia education reinforced.

                    Plan: pneumococcal and influenza vaccines when due; return precautions for recurrent fever,
                    worsening shortness of breath, or new neurological symptoms.
                """.trimIndent(),
                imagePaths = "",
                analysisResult = "",
                status = "reviewed",
                createdAt = tFollowUp + 86_400_000L,
                updatedAt = tFollowUp + 86_400_000L
            )
        )

        db.consultationDao().insertConsultation(
            ConsultationEntity(
                patientId = patientId,
                consultationDate = tBaseline,
                chiefComplaint = "Cough with fever and pleuritic chest pain",
                symptoms = "Productive sputum, fatigue, anorexia; no hemoptysis (demo review)",
                vitalSigns = "BP 138/86 mmHg · HR 92 · RR 20 · T 38.2 °C · SpO₂ 94% RA",
                diagnosis = "Lower respiratory tract infection — clinical evaluation (demo narrative only)",
                prognosis = "Expected improvement with completed therapy; seek care if hypoxia or sepsis concerns.",
                aiSuggestions = "",
                prescriptions = "Demo entry — not a real prescription",
                followUpDate = tFollowUp,
                notes = "Medication allergy banner reviewed; inhaler technique deferred (not applicable to this demo)."
            )
        )

        db.diagnosisDao().insertDiagnosis(
            DiagnosisEntity(
                patientId = patientId,
                diagnosis = """
                    Demo aggregate impression: cardiometabolic comorbidities (T2DM, HTN) with an acute respiratory
                    illness narrative illustrated by longitudinal imaging placeholders. For presentation only —
                    not generated from live model inference in this seed.
                """.trimIndent(),
                scope = "FULL",
                entryCount = 3,
                modelName = "Demo seed (static text)",
                thinkingEnabled = false
            )
        )

        prefs.edit().putBoolean(KEY_APPA_RAO_DONE, true).apply()
    }

    /**
     * Fills [MedicalEntryEntity.visitSummary] for existing **Appa Rao** demo rows so longitudinal
     * chat can use compact prompts without re-seeding the database.
     */
    suspend fun backfillVisitSummariesIfNeeded(context: Context) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_VISIT_SUMMARY_BACKFILL, false)) return@withContext

        val db = MedicalDatabase.getDatabase(context)
        val patient = db.patientDao().getAllPatientsSync().find { it.name == "Appa Rao" }
            ?: run {
                prefs.edit().putBoolean(KEY_VISIT_SUMMARY_BACKFILL, true).apply()
                return@withContext
            }

        val baselineSummary =
            "Oct 2024 admission CXR during acute lower respiratory illness: productive cough, fever, pleuritic pain; SpO₂ 94% RA. CAP workup with comorbid T2DM/HTN; empiric antibiotics per protocol with clinical and laboratory correlation."
        val followSummary =
            "Nov 2024 interval CXR after antibiotic therapy: patient reports resolution of cough and fever; SpO₂ 97% RA. Interval change reviewed against October baseline; outpatient continuation of cardiometabolic regimen and smoking abstinence reinforcement."
        val chronicSummary =
            "Chronic disease management: metformin ER, telmisartan, atorvastatin; home glucose and BP logs reviewed. Immunizations discussed; return precautions for recurrent respiratory symptoms or hypoglycemia."

        for (e in db.medicalEntryDao().getEntriesForPatientSync(patient.id)) {
            if (e.visitSummary.isNotBlank()) continue
            val sum = when {
                e.title.contains("baseline", ignoreCase = true) && e.entryType == "XRAY" -> baselineSummary
                e.title.contains("follow-up", ignoreCase = true) && e.entryType == "XRAY" -> followSummary
                e.title.contains("Outpatient", ignoreCase = true) -> chronicSummary
                else -> null
            } ?: continue
            db.medicalEntryDao().updateEntry(
                e.copy(visitSummary = sum, updatedAt = System.currentTimeMillis())
            )
        }
        prefs.edit().putBoolean(KEY_VISIT_SUMMARY_BACKFILL, true).apply()
    }

    /**
     * Inserts four synthetic patients (**Brahmaiah**, **Lakshmidevi**, **Satyavathi**, **Rajeswari**) with
     * only [MedicalEntryEntity] rows of type **MANUAL** (no images). Idempotent per patient name.
     */
    suspend fun seedFourManualDemoPatientsIfNeeded(context: Context) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_FOUR_MANUAL_DEMO, false)) return@withContext

        val db = MedicalDatabase.getDatabase(context)
        val existingNames = db.patientDao().getAllPatientsSync().map { it.name }.toSet()
        val required = setOf("Brahmaiah", "Lakshmidevi", "Satyavathi", "Rajeswari")
        if (existingNames.containsAll(required)) {
            prefs.edit().putBoolean(KEY_FOUR_MANUAL_DEMO, true).apply()
            return@withContext
        }

        for (bundle in FourManualDemoPatients.bundles()) {
            if (db.patientDao().getAllPatientsSync().any { it.name == bundle.patient.name }) continue

            val patientId = db.patientDao().insertPatient(bundle.patient)
            for (v in bundle.visits) {
                val t = parseLocalDate(v.isoDate)
                db.medicalEntryDao().insertEntry(
                    MedicalEntryEntity(
                        patientId = patientId,
                        entryType = "MANUAL",
                        title = v.title,
                        visitSummary = v.visitSummary.trim().replace("\n", " ").trim(),
                        content = v.chartNote,
                        imagePaths = "",
                        analysisResult = v.assistantSummary,
                        status = "reviewed",
                        createdAt = t,
                        updatedAt = t,
                    )
                )
            }
            db.diagnosisDao().insertDiagnosis(
                DiagnosisEntity(
                    patientId = patientId,
                    diagnosis = bundle.aggregateDiagnosis,
                    scope = "FULL",
                    entryCount = bundle.visits.size,
                    modelName = "MedGemma-style demo summary (seed)",
                    thinkingEnabled = false,
                )
            )
        }
        prefs.edit().putBoolean(KEY_FOUR_MANUAL_DEMO, true).apply()
    }

    private fun parseLocalDate(iso: String): Long {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }
        return fmt.parse(iso)?.time ?: System.currentTimeMillis()
    }
}
