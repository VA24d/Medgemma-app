package com.google.mediapipe.examples.llminference.data

/**
 * Synthetic demo-only patients: **MANUAL** chart entries only (no imaging paths).
 * Assistant-style blocks imitate on-device model chart summaries; **not** live inference.
 */
internal data class ManualVisitSeed(
    val title: String,
    val isoDate: String,
    val visitSummary: String,
    val chartNote: String,
    /** Stored in [MedicalEntryEntity.analysisResult] like an AI chart assistant line. */
    val assistantSummary: String,
)

internal data class ManualPatientBundle(
    val patient: PatientEntity,
    val visits: List<ManualVisitSeed>,
    /** Stored as [DiagnosisEntity.diagnosis] — aggregate MedGemma-style narrative (seed). */
    val aggregateDiagnosis: String,
)

internal object FourManualDemoPatients {

    /** DOBs chosen so ages in 2026 fall between ~27 and ~55 (within user 25–60). */
    fun bundles(): List<ManualPatientBundle> = listOf(
        brahmaiah(),
        lakshmidevi(),
        satyavathi(),
        rajeswari(),
    )

    private fun brahmaiah() = ManualPatientBundle(
        patient = PatientEntity(
            name = "Brahmaiah",
            dateOfBirth = "1971-04-10",
            gender = "Male",
            medicalRecordNumber = "MRN-DEMO-BR-001",
            phoneNumber = "+91 98410 11223",
            email = "brahmaiah.demo@example.org",
            address = "44 Sultan Bazar, Karimnagar, Telangana 505001",
            bloodGroup = "O+",
            allergies = "NKDA (demo)",
            notes = "Demo synthetic chart — GERD/dyspepsia focus; overweight; dyslipidemia on statin."
        ),
        visits = listOf(
            ManualVisitSeed(
                title = "OPD — dyspepsia & reflux symptoms",
                isoDate = "2025-09-10",
                visitSummary = "Initial visit for epigastric burning and post-prandial reflux; BMI elevated; started PPI trial and lifestyle counselling.",
                chartNote = """
                    Chief complaint: Heartburn and sour regurgitation ~6 weeks; worse after late meals and spicy food.
                    ROS: No dysphagia, odynophagia, melena, or weight loss. Occasional bloating.
                    Vitals: BP 132/84, BMI 29 kg/m².
                    Assessment: Likely GERD vs dyspepsia; alarm features absent on history.
                    Plan: Omeprazole 20 mg daily before breakfast × 8 weeks; avoid late eating, elevate head of bed;
                    return if alarm symptoms or inadequate relief.
                """.trimIndent(),
                assistantSummary = """
                    **Chart assistant (demo narrative)** — Summary aligns with typical GERD presentation without reported alarm features.
                    Recommend structured PPI trial and lifestyle modification; document alarm symptom education.
                    If persistent symptoms beyond 8 weeks, consider follow-up for possible endoscopy referral per local protocol.
                """.trimIndent(),
            ),
            ManualVisitSeed(
                title = "Follow-up — PPI response",
                isoDate = "2025-11-05",
                visitSummary = "Partial improvement on PPI; dietary triggers reduced; discussed weight and lipid panel.",
                chartNote = """
                    Reports ~60% improvement in heartburn frequency; still occasional night symptoms.
                    Adherence good. Started walking 25 min most days.
                    Labs ordered: fasting lipid panel (prior dyslipidemia); reinforces low-fat meals.
                    Continue omeprazole; review lifestyle barriers.
                """.trimIndent(),
                assistantSummary = """
                    **Chart assistant (demo narrative)** — Partial response to PPI supports GERD component; ongoing nocturnal symptoms warrant sleep-position and meal-timing counselling.
                    Lipid monitoring reasonable given metabolic risk; reinforce gradual weight reduction.
                """.trimIndent(),
            ),
            ManualVisitSeed(
                title = "Chronic problems review",
                isoDate = "2026-02-18",
                visitSummary = "Symptoms well controlled on maintenance PPI; dyslipidemia stable on statin; continue surveillance.",
                chartNote = """
                    Heartburn minimal on omeprazole 20 mg; patient wishes to continue rather than step down at this time.
                    Lipids improved on atorvastatin 10 mg (details in lab module — demo).
                    BP borderline; counsel sodium and activity.
                    Plan: continue current GERD regimen; annual metabolic review; return sooner for alarm GI symptoms.
                """.trimIndent(),
                assistantSummary = """
                    **Chart assistant (demo narrative)** — Clinical course consistent with controlled GERD on maintenance therapy.
                    Cardiometabolic comorbidities addressed in parallel; emphasise ongoing adherence and periodic labs.
                """.trimIndent(),
            ),
        ),
        aggregateDiagnosis = """
            ### Aggregate impression (demo seed — MedGemma-style)

            **Problem list:** Gastro-oesophageal reflux disease / functional dyspepsia (symptom-predominant), overweight, dyslipidemia on statin therapy.

            **Course:** Initial PPI trial with measurable symptom reduction; night symptoms partially responsive to behavioural measures. No documented alarm features in seeded visits.

            **Current stability:** Symptoms acceptable on maintenance PPI; metabolic risk factors under longitudinal review.

            **Suggested monitoring:** Periodic lipid panel and weight/BP check; prompt reassessment for dysphagia, GI bleeding, or unintended weight loss.
        """.trimIndent(),
    )

    private fun lakshmidevi() = ManualPatientBundle(
        patient = PatientEntity(
            name = "Lakshmidevi",
            dateOfBirth = "1979-12-05",
            gender = "Female",
            medicalRecordNumber = "MRN-DEMO-LK-002",
            phoneNumber = "+91 94902 33445",
            email = "lakshmidevi.demo@example.org",
            address = "Plot 18, Bhagyanagar Colony, Warangal, Telangana 506002",
            bloodGroup = "A+",
            allergies = "Iodine contrast — nausea (demo)",
            notes = "Demo synthetic chart — primary hypothyroidism on levothyroxine; prior iron deficiency addressed."
        ),
        visits = listOf(
            ManualVisitSeed(
                title = "New patient — fatigue & thyroid evaluation",
                isoDate = "2025-08-22",
                visitSummary = "Elevated TSH with low-normal free T4; started levothyroxine; fatigue and cold intolerance documented.",
                chartNote = """
                    Presenting: Months of fatigue, dry skin, constipation; weight gain ~3 kg.
                    Exam: Bradycardia 58 bpm; non-pitting edema subtle.
                    Labs (demo values): TSH 12.4 mIU/L, free T4 low-normal; anti-TPO positive.
                    Plan: Levothyroxine 50 µg daily fasting; repeat TSH 6–8 weeks; counsel adherence and pregnancy precautions if applicable.
                """.trimIndent(),
                assistantSummary = """
                    **Chart assistant (demo narrative)** — Biochemical pattern consistent with primary hypothyroidism; initiating replacement with appropriate follow-up testing interval.
                    Highlight adherence and timing of levothyroxine away from interfering supplements/meals.
                """.trimIndent(),
            ),
            ManualVisitSeed(
                title = "Thyroid replacement titration visit",
                isoDate = "2025-12-02",
                visitSummary = "TSH improving on 62.5 µg; fewer fatigue complaints; monitor CBC after prior iron course.",
                chartNote = """
                    Repeat TSH 3.2 mIU/L on levothyroxine 62.5 µg (Wednesday/Sunday 50 µg per pill-split schedule — demo).
                    Energy improved; constipation better.
                    Hb stable after oral iron course (records in demo).

                    Continue current dose; repeat TSH in 3 months or sooner if pregnancy planned.
                """.trimIndent(),
                assistantSummary = """
                    **Chart assistant (demo narrative)** — Dose adjustment appears effective with symptom improvement and moving TSH toward target range.
                    Reinforce consistent daily dosing; coordinate obstetric planning if conception considered.
                """.trimIndent(),
            ),
            ManualVisitSeed(
                title = "Annual thyroid & wellness review",
                isoDate = "2026-03-15",
                visitSummary = "Euthyroid on stable levothyroxine; routine wellness and bone-health counselling.",
                chartNote = """
                    TSH 2.1 mIU/L on levothyroxine 62.5 µg daily. Patient feels “back to usual energy.”
                    Vitamin D low-normal — cholecalciferol supplementation discussed.
                    Bone health: adequate calcium intake counselling (dietary).

                    Plan: maintain thyroid dose; annual TSH; routine preventive care per age.
                """.trimIndent(),
                assistantSummary = """
                    **Chart assistant (demo narrative)** — Maintenance phase of hypothyroid therapy with goal TSH in reference range and good symptom control.
                    Address adjunct nutritional factors (vitamin D) within primary-care scope.
                """.trimIndent(),
            ),
        ),
        aggregateDiagnosis = """
            ### Aggregate impression (demo seed — MedGemma-style)

            **Problem list:** Primary hypothyroidism (autoimmune pattern suggested by serology in chart), history of iron deficiency (resolved or improving per notes).

            **Course:** Levothyroxine titration with improving TSH and reduced constitutional symptoms.

            **Current stability:** Euthyroid on stable replacement dose in seeded timeline.

            **Suggested monitoring:** Periodic TSH; pregnancy planning counselling if relevant; bone health and vitamin D as clinically indicated.
        """.trimIndent(),
    )

    private fun satyavathi() = ManualPatientBundle(
        patient = PatientEntity(
            name = "Satyavathi",
            dateOfBirth = "1988-07-30",
            gender = "Female",
            medicalRecordNumber = "MRN-DEMO-SV-003",
            phoneNumber = "+91 99887 55661",
            email = "satyavathi.demo@example.org",
            address = "Flat 302, SR Nagar, Nizamabad, Telangana 503002",
            bloodGroup = "B+",
            allergies = "NSAID-induced gastritis (demo — avoid unless necessary)",
            notes = "Demo synthetic chart — migraine without persistent aura; mood/stress contributors discussed."
        ),
        visits = listOf(
            ManualVisitSeed(
                title = "Neurology-style OPD — recurrent headaches",
                isoDate = "2025-10-30",
                visitSummary = "Episodic unilateral throbbing headaches with photophobia; migraine provisional; trigger diary started.",
                chartNote = """
                    HPI: Headaches 4–6 times monthly, 8–12 h duration, nausea without vomiting; worse with menses and poor sleep.
                    Neuro exam non-focal (demo documentation).
                    Abortive: discuss NSAID cautions given gastric history — triptan considered if criteria met locally.

                    Plan: Headache diary; sleep hygiene; consider magnesium trial; safety-net for thunderclap features.
                """.trimIndent(),
                assistantSummary = """
                    **Chart assistant (demo narrative)** — History fits migraine without aura for likelihood; emphasise red-flag exclusion via clinical interview.
                    Abortive therapy selection should respect GI contraindications documented in allergy list.
                """.trimIndent(),
            ),
            ManualVisitSeed(
                title = "Follow-up — migraine management",
                isoDate = "2026-01-20",
                visitSummary = "Reduced attack frequency with diary-guided triggers; discuss stress and prophylaxis criteria.",
                chartNote = """
                    Attack frequency down to ~2 per month with improved sleep.
                    Stressors: workplace deadlines — referred to coping strategies / EAP (demo).

                    Prophylaxis: if attacks remain disabling despite optimised acute care, consider beta-blocker or topiramate per guideline — deferred this visit (patient preference).

                    Return sooner for neurological deficits or sudden severe headache.
                """.trimIndent(),
                assistantSummary = """
                    **Chart assistant (demo narrative)** — Improvement with non-pharmacologic measures supports behavioural triggers.
                    Document criteria for escalating to migraine prophylaxis if frequency or disability increases.
                """.trimIndent(),
            ),
        ),
        aggregateDiagnosis = """
            ### Aggregate impression (demo seed — MedGemma-style)

            **Problem list:** Episodic migraine (probable, without persistent aura in seeded notes), stress-related exacerbation.

            **Course:** Initial evaluation with trigger identification and abortive planning; follow-up shows reduced attack burden with lifestyle measures.

            **Current stability:** Improved but monitor for need of prophylaxis if disability returns.

            **Safety:** Reinforce urgent evaluation for thunderclap onset, focal deficits, or persistent neurological signs.
        """.trimIndent(),
    )

    private fun rajeswari() = ManualPatientBundle(
        patient = PatientEntity(
            name = "Rajeswari",
            dateOfBirth = "1997-10-15",
            gender = "Female",
            medicalRecordNumber = "MRN-DEMO-RJ-004",
            phoneNumber = "+91 91234 77889",
            email = "rajeswari.demo@example.org",
            address = "H.No. 9-4-87, Khammam, Telangana 507002",
            bloodGroup = "AB+",
            allergies = "No known drug allergies (demo)",
            notes = "Demo synthetic chart — oligomenorrhea and hyperandrogenic features consistent with PCOS workup."
        ),
        visits = listOf(
            ManualVisitSeed(
                title = "Gynaec / medicine — irregular cycles",
                isoDate = "2025-07-14",
                visitSummary = "Oligomenorrhea ~8 months; acne and weight gain; PCOS labs planned; lifestyle counselling.",
                chartNote = """
                    LMP unreliable; cycles every 45–90 days. Acne along jawline; hirsutism mild.
                    BMI 31; BP 118/76.
                    Labs planned (demo): testosterone, SHBG, 17-OHP if indicated, fasting glucose/HbA1c, lipid panel.
                    Pelvic ultrasound referral per protocol.

                    Counselling: nutrition and activity for metabolic risk; contraception preferences discussed.
                """.trimIndent(),
                assistantSummary = """
                    **Chart assistant (demo narrative)** — Clinical picture compatible with PCOS as leading differential in oligomenorrhoea with metabolic features.
                    Recommend staged investigation consistent with local endocrine/GYN pathways.
                """.trimIndent(),
            ),
            ManualVisitSeed(
                title = "Review — labs & metformin start",
                isoDate = "2025-11-28",
                visitSummary = "HbA1c prediabetes range; started metformin XR; ultrasound consistent with polycystic ovaries.",
                chartNote = """
                    Results (demo): HbA1c 6.1%; fasting insulin elevated; ultrasound multifollicular ovaries.
                    Started metformin extended-release 500 mg nightly with meal titration plan.

                    Discuss menstrual regulation options including combined hormonal contraception — patient deferring.

                    Dermatology referral optional for persistent acne if topical regimen insufficient.
                """.trimIndent(),
                assistantSummary = """
                    **Chart assistant (demo narrative)** — Metabolic markers support insulin resistance phenotype alongside PCOS morphology on imaging.
                    Metformin initiation aligns with common cardiometabolic risk mitigation strategies; monitor GI tolerance.
                """.trimIndent(),
            ),
            ManualVisitSeed(
                title = "Follow-up — cycles & tolerability",
                isoDate = "2026-04-02",
                visitSummary = "Metformin tolerated; slight weight reduction; cycles somewhat more predictable; ongoing shared decision on contraception.",
                chartNote = """
                    Metformin XR 1000 mg nightly — GI side effects minimal after dose escalation.
                    Weight −3 kg over interval with dietary changes.

                    Menses occurred twice in last 90 days — improvement but still irregular.

                    Plan: continue metformin; repeat HbA1c in ~3 months; discuss family planning and cycle regulation options at next visit.
                """.trimIndent(),
                assistantSummary = """
                    **Chart assistant (demo narrative)** — Partial metabolic and menstrual improvement on therapy and lifestyle changes.
                    Long-term plan should individualise fertility intentions and cardiovascular risk monitoring.
                """.trimIndent(),
            ),
        ),
        aggregateDiagnosis = """
            ### Aggregate impression (demo seed — MedGemma-style)

            **Problem list:** Polycystic ovary syndrome phenotype with oligomenorrhoea, hyperandrogenic skin manifestations, overweight/obesity, prediabetes/insulin resistance pattern on seeded labs.

            **Course:** Diagnostic work-up with imaging and metabolic labs; metformin introduced with improving tolerability and modest weight change; menstrual frequency improving but not yet normal.

            **Current stability:** Partial response; ongoing need for contraceptive counselling, fertility planning, and cardiometabolic surveillance.

            **Suggested monitoring:** Glycaemic indices, lipids, BP; revisit menstrual regulation and dermatologic therapy as needed.
        """.trimIndent(),
    )
}
