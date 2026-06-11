# Med Veda — technical knowledge dump for downstream authoring

**Purpose:** Answers to the interview framework below, synthesized from **repository inspection**, **in-repo benchmarks/docs**, and **explicit product intents** stated in prior working sessions. Tags: `[REPO]` = implemented or stated in code/docs; `[INTENDED]` = target architecture described for submissions but **not** necessarily implemented as a network client in app code; `[DOCS]` = project markdown only; `[UNKNOWN]` = not evidenced in repo.

---

## 1. Project objective

**Q: What exact clinical workflow problem does Med Veda solve?**  
**A:** Point-of-care synthesis of **longitudinal patient context** (Room-stored entries) with **multimodal** questions (text + medical imaging) using a **single local** MedGemma-class stack, reducing dependence on cloud LLM inference for chart-bearing prompts. `[REPO: ChatViewModel patient chart prompts, Imaging flows, DiagnosisScreen]`

**Q: What deployment scenario is targeted?**  
**A:** **Android handheld/tablet**, minSdk 24, physical device testing narrative includes Snapdragon-class hardware. Primary model delivery via user-selected paths + Hugging Face download for GGUF/mmproj. `[REPO: build.gradle.kts, README, Model.kt, SelectionScreen]`

**Q: Why edge AI instead of cloud-only AI?**  
**A:** PHI residency and latency for chart-grounded dialogue; offline-first ward operation; thermal/memory control by running quantized local inference. `[REPO+DOCS: InferenceModel local engine; writeup privacy narrative]`

**Q: Why Snapdragon/mobile deployment matters?**  
**A:** Clinical copilot must exist **where the clinician already is**; heterogeneous SoC (CPU + optional GPU/NPU paths) is the realistic execution substrate for 4B-class multimodal on battery. Native build enables **ARM64** optimizations (`GGML_CPU_KLEIDIAI` ON in `aichatlib` CMake for `arm64-v8a`). `[REPO: aichatlib/CMakeLists.txt]`

**Section summary:** Edge-first bedside assistant; mobile is the deployment unit of trust for narrative PHI during inference.

---

## 2. Core problem statement

| Dimension | Captured detail | Evidence |
|-----------|-----------------|----------|
| **Latency** | Local inference avoids round-trip to remote LLM; chart instant replies for trivial DB queries short-circuit LLM. | `[REPO: ChatViewModel.maybeInstantPatientReply]` |
| **Connectivity** | Designed for intermittent networks; model download is separate session; inference path does not require cloud. | `[REPO+DOCS]` |
| **PHI / privacy** | Chart + images processed in-process; export is explicit user action. | `[REPO: InferenceModel, FhirExportManager]` |
| **Multimodal reasoning** | MedGemma + mmproj lazy-loaded; patient prompts bundle entries + optional bitmaps. | `[REPO: InferenceModel.ensureMmprojLoaded, ChatViewModel]` |
| **Thermal / power** | User-facing energy mode + backend mode prefs exist; native decode caps (e.g. max predict length) in InferenceModel. | `[REPO: AppPreferences, InferenceModel]` |
| **Limits of cloud copilots** | Sending full longitudinal notes to third-party LLM endpoints raises governance issues; architecture keeps language inference local by default. | `[INTENDED positioning + REPO local default]` |

**Section summary:** Constraints are connectivity, handset DRAM/thermals, and PHI boundary—not model quality alone.

---

## 3. System architecture

**Q: Overall edge–cloud architecture?**  
**A (as implemented):** **Edge:** Kotlin Compose app + Room + `InferenceModel` → `aichatlib` JNI → llama.cpp/GGUF + optional mmproj. **Cloud/network:** Hugging Face artifact download, OAuth/token surfaces, optional future telemetry—not required for inference loop. `[REPO]`  
**A (target / submission):** Add **enterprise imaging API** tier for heavy vision preprocessing; responses are **structured analytics** fused locally. `[INTENDED — no OkHttp client dedicated to enterprise imaging found]`

**Q: What runs locally?**  
**A:** LLM prefill/decode, multimodal projector load, chart DB R/W, PIN/auth prefs, FHIR bundle **generation** (uses **local** `InferenceModel.generateResponse` per patient to emit JSON resources). `[REPO: FhirExportManager]`

**Q: What runs remotely?**  
**A:** HF weight download; OAuth/HF login flows. `[REPO: HfApiClient, SelectionScreen, OAuth paths]`  
**INTENDED:** Enterprise vision API.

**Q: Trust boundaries?**  
**A:** PHI in app process + SQLite Room; encrypted prefs for PIN-related settings; model files on device storage. Third-party HF hosts **weights only**, not patient prompts during normal chat. FHIR export writes user-accessible JSON to Downloads (user-controlled exfil). `[REPO]`

**Q: Inference orchestration?**  
**A:** `ChatViewModel` schedules IO work; `InferenceModel.generateResponseAsync` uses executor + optional skip-thinking per request; vision mmproj lazy-loaded on first image. `[REPO]`

**Q: Multimodal fusion flow?**  
**A:** User message + optional `List<Bitmap>` passed to native engine after prompts built from chart text; demo CXR fast-path may bypass images for known fixtures. `[REPO: ChatViewModel, DemoXraySummaries]`  
**INTENDED:** Fuse **enterprise vision JSON/embeddings** + local text—**orchestration spec not in repo**.

**Q: Why this architecture?**  
**A:** Prior docs cite OOM issues with prior MediaPipe-heavy multimodal path; llama.cpp GGUF path chosen for memory orchestration. `[DOCS: writeup.md]` MediaPipe `tasks-vision` dependency present in Gradle but **no Kotlin usage found** in earlier grep—likely template residue. `[REPO: app/build.gradle.kts — verify before claiming vision feature]`

**Section summary:** Strict edge default; cloud is distribution/auth; optional hybrid vision tier is a **declared** extension point.

---

## 4. Model pipeline

| Topic | Detail | Tag |
|-------|--------|-----|
| **HF base** | `google/medgemma-1.5-4b-it` family; weights consumed as **Unsloth** GGUF builds in app defaults. | `[REPO: Model.kt, HfModelRepository]` |
| **MedGemma version** | **1.5 4B IT** multimodal (marketing/docs align). | `[DOCS+REPO]` |
| **Quantizations exposed** | Q2_K, Q3_K_M, Q4_K_M, Q5_K_M, Q6_K, Q8_0, F16 from `HfModelRepository.availableModels`; enum defaults point to **Q4_K_M** + **mmproj-F16**. | `[REPO: Model.kt]` |
| **GGUF conversion** | Repo ships `conversion/convert_hf_to_gguf.py` (large upstream-style script); Android does not convert on device—it **loads** GGUF. | `[REPO]` |
| **mmproj** | Separate `mmproj-F16.gguf`; `InferenceModel.ensureMmprojLoaded()` lazy `engine.loadMMProj`. | `[REPO]` |
| **Native runtime** | `aichatlib`: FetchContent **llama.cpp** tag `b5497`, links `llama`, `common`, **`mtmd`** (multimodal). `GGML_BACKEND_DL=ON` on library module in Gradle (dynamic backends). arm64: `GGML_CPU_KLEIDIAI ON`, OpenMP ON, `GGML_CPU_ALL_VARIANTS OFF` to ensure base CPU backend exists. | `[REPO: aichatlib/CMakeLists.txt, aichatlib/build.gradle.kts]` |
| **Snapdragon / NPU** | CMake enables **KleidiAI** CPU path on arm64; explicit NPU delegate not documented in CMake flags—**NPU** remains **runtime/OS dependent**. UI exposes backend mode strings (Auto/CPU/GPU/NPU) in prefs. | `[REPO + partial UNKNOWN for true NPU offload]` |
| **Android inference path** | Bitmap → native generate; `MAX_TOKENS` / decode offsets in `InferenceModel.kt`. | `[REPO]` |
| **Memory footprint** | Marketing writeup cites **~4.1 GB peak** and **~2.8 GB** Q4 disk size narrative; treat as **project claim**, validate per build/quant. | `[DOCS: writeup.md]` |
| **Tokens/sec** | Writeup claims **~18–22 tok/s** text on cited edge device class. | `[DOCS — not re-benchmarked in this session]` |

**Section summary:** HF-sourced GGUF + mmproj, llama.cpp b5497 + mtmd, KleidiAI-enabled CPU build on arm64, quant ladder user-selectable.

---

## 5. Benchmarking and validation

**Q: Benchmarks run?**  
**A:** `benchmarking/gguf/eval_medmcqa.py`, `eval_multi_benchmark.py`; `benchmarking/tflite/eval_medmcqa.py`. `[REPO: benchmarking/README.md]`

**Q: Datasets?**  
**A:** MedMCQA, MedQA (USMLE 4-opt), PubMedQA (pqa_labeled), MMLU medical subsets, MedXpertQA text—loaded from HuggingFace datasets per README. `[REPO]`

**Q: Sample sizes?**  
**A:** Tables use **500** examples per benchmark for GGUF table; methodology section documents 50 smoke / 500 full / 0=all for MedMCQA. `[REPO]`

**Q: BF16 comparison?**  
**A:** BF16 baseline row in README (GPU RTX 4090 for GGUF table). `[REPO]` — **not** on-phone numbers.

**Q: Q4/Q6/Q8 findings?**  
**A (verbatim table summary):** Q8_0 ~matches BF16 on listed bench; Q6_K near; Q4_K_M drops on MedMCQA/MMLU Med, not on MedQA/PubMedQA in table. TFLite Q8 MedMCQA **39.55%** at 6.4 s/q CPU with ekv128 context caveat. `[REPO: benchmarking/README.md]`

**Q: Why quant chosen?**  
**A:** Q4_K_M as mobile default in `Model.kt` for footprint; README positions Q8_0 for accuracy-first deployments. `[REPO]`

**Section summary:** Rigorous **desktop-GPU** GGUF characterization; mobile on-device accuracy/speed is **separate** from table environment.

---

## 6. App features (ordered by clinical centrality — repo truth)

For each: **problem / local vs API / clinical note.**

1. **X-ray analysis (`XRAY`)** — Imaging-informed assessment stored as `MedicalEntryEntity` with `analysisResult`, `imagePaths`. **Local:** MedGemma+mmproj; camera/gallery flows. **API:** `[INTENDED]` enterprise vision. `[REPO: XrayAnalysisScreen, entity]`

2. **MRI (`MRI`)** — Same UI pattern as X-ray routing in navigation. **Local** inference path same as multimodal. `[REPO: NewEntryScreen, MainActivity routes]`

3. **Histopathology (`HISTOPATHOLOGY`)** — Shares `XrayAnalysisScreen` with `analysisType` flag. Large FOV → `[INTENDED]` enterprise offload narrative. `[REPO]`

4. **Longitudinal patient history** — Room timeline, filters, entry types. Grounds prompts with dated entries. **Local only.** `[REPO: LongitudinalHistoryScreen, Daos]`

5. **AI chart expansion** — History screen prompts stage1/stage2 by entry type; uses `InferenceModel`. **Local.** `[REPO: LongitudinalHistoryScreen.kt prompts]`

6. **Diagnosis / prognosis** — `DiagnosisScreen` aggregates imaging entries + runs generation; links `ScheduledPrognosisWorker`. **Local** model calls. `[REPO]`

7. **Audio / recording (`RECORDING`)** — `RecordingScreen` uses **`RecognizerIntent` / speech activity** for transcription (OEM-dependent: may use on-device or network speech stack). Saved text is summarized by **`InferenceModel.generateResponseAsync`** locally. `[REPO: RecordingScreen.kt]`

8. **Manual notes (`MANUAL`)** — `ManualNotesScreen`. **Local** storage; may feed later LLM. `[REPO]`

9. **Chat-grounded reasoning** — `ChatViewModel` builds `buildPatientSystemPrompt` from patient + entries + diagnoses; instant DB replies for narrow intents. **Local.** `[REPO]`

10. **Background scheduling** — `ScheduledPrognosisWorker` + `LocalModelFiles` schedule prefs. **Local** WorkManager; no cloud job queue in repo. `[REPO]`

11. **Security / encryption** — `EncryptedSharedPreferences` (`AppPreferences`); `SecureStorage` for OAuth tokens. **Local.** `[REPO]`

12. **PIN** — `PinScreen`, prefs gate. **Local.** `[REPO]`

13. **Model management** — `SelectionScreen`: HF download, local scan of `.gguf` dirs, mmproj pairing, vision toggle, thinking toggle persistence. `[REPO]`

14. **Thinking vs direct** — `LocalModelFiles.isThinkingEnabled` + `engine.setSkipThinking`. `[REPO]`

15. **Multilingual** — Prompt instructions for Hindi/Telugu output; resets native conversation on language key change. `[REPO: ChatViewModel, InferenceModel]`

16. **Telugu / Hindi** — Explicit branches in `buildReplyLanguageInstruction`. `[REPO]`

17. **Vernacular glossing** — `LanguageExtension` regex dictionary post-processing. **Local**, no translation API. `[REPO]`

**Gaps:** **Document analysis** UI is disabled (“Coming Soon”). `[REPO: NewEntryScreen]`

**Section summary:** Feature-complete multimodal + longitudinal local copilot; enterprise vision is **external** to current Kotlin tree.

---

## 7. Enterprise imaging API hybrid `[INTENDED + design rationale]`

| Question | Captured answer |
|----------|-----------------|
| Why huge imaging hard locally? | Handset DRAM, thermal throttling, long vision prefill on multi-megapixel / whole-slide proxies. |
| Latency order of magnitude | Submission narrative: **minutes** local deep stack vs **sub-second to few seconds** datacenter GPU vision service (org-dependent). **Not benchmarked in repo for enterprise path.** |
| What is sent to enterprise API? | **Imaging pixels or tiles** under org credentials; governance-defined payload. |
| What is **not** sent? | Full longitudinal narrative as default; broad chart text should stay for **local** MedGemma. |
| What returns? | Structured outputs: labels, scores, embedding summaries, heatmap metadata—compact tensors/JSON. |
| How local MedGemma consumes it? | Inject structured fields into prompt context or parallel channel to native multimodal fusion—**implementation spec open**. |
| Governance | BAA / enterprise contract path; scoped purpose (vision analytics only). |
| vs cloud-only copilots | Language model never receives full chart on vendor multitenant endpoint; only partitioned vision job crosses to governed service. |

**Section summary:** This hybrid is the **strong systems story** for Qualcomm; wire-up is **future engineering**.

---

## 8. FHIR and export pipeline `[REPO]`

| Question | Answer |
|----------|--------|
| **Formats** | Single **FHIR R4**-style JSON **Bundle** (`collection`) written to **Downloads** as `medgemma_fhir_export_<timestamp>.json` via MediaStore. |
| **Workflow** | For each patient, build prompt with demographics → **`InferenceModel.generateResponse`** asks model for Patient resource JSON → strip fences → append as Bundle entry. Fallback minimal JSON on failure. |
| **Hospital integration** | Consumer of file is **EHR ingestion / on-prem pipeline**—out of app scope; file is user-retrievable. |
| **On-prem / VPC GPU** | `[INTENDED]` Institution runs larger models / cohort jobs on exported bundles. |
| **Larger models / rare disease / cohort analytics** | Same `[INTENDED]` institutional tier. |

**Caveat:** Export currently **embeds demographics in LLM prompt** for FHIR generation—still **local** inference, but reviewers should note **PII in prompt** during export action (mitigate in hardened product).

**Section summary:** Operational FHIR Bundle export exists; heavy analytics is **downstream** of the file.

---

## 9. Snapdragon and edge AI relevance

| Theme | Concrete hook |
|-------|---------------|
| **Why Snapdragon** | Primary mass-market Android SoC for regional clinical deployments; heterogeneous cores map to backend prefs. |
| **Offline-first** | Inference does not require HTTP once weights local. |
| **Bedside latency** | Local decode avoids WAN tail latencies. |
| **Thermal-aware** | Energy/backend prefs; long generations are user-visible workload. |
| **Mobile multimodal feasibility** | Demonstrated path: GGUF + mmproj + lazy load. |
| **NPU** | UI + prefs acknowledge NPU; **native CMake does not show QNN/HTP flags**—treat NPU as roadmap or runtime plugin unless verified. |
| **Heterogeneous compute** | `GGML_BACKEND_DL` suggests dynamic backend loading in library build. `[REPO: aichatlib/build.gradle.kts]` |

**Section summary:** Credible edge story; **verify NPU claims** against actual backend `.so` loading on device before hard marketing.

---

## 10. Contribution / novelty

**Q: What is novel?**  
**A:** Engineering integration of **MedGemma-class multimodal GGUF** on Android with **longitudinal Room grounding** + **benchmarked quantization ladder** + optional **TFLite parallel pipeline** for alternative Snapdragon deployment. FHIR export via **on-device** LLM-assisted JSON is an unusual product choice. `[REPO]`

**Q: vs cloud medical copilots?**  
**A:** Default inference path keeps **chart tokens** off multitenant LLM APIs. `[positioning]`

**Q: vs simple local chat apps?**  
**A:** Persistent patient model, multimodal capture screens, diagnosis aggregation, scheduled prognosis, vernacular layer, export. `[REPO]`

**Q: Systems contribution?**  
**A:** **Inference partitioning** narrative (local language + optional governed vision tier) + **standards-based export** for institutional scale-out. `[INTENDED hybrid + REPO export]`

---

## Consolidated gaps / risks for the next writer

1. **Enterprise imaging API:** not found in Kotlin network layer as dedicated integration—document as **roadmap** unless added.  
2. **NPU acceleration:** KleidiAI CPU path is real in CMake; **NPU offload** needs device-side verification.  
3. **Benchmark table** is **RTX 4090** environment, not Snapdragon.  
4. **MediaPipe tasks-vision** dependency unused in app source (last check)—do not claim vision SDK features from it.  
5. **FHIR export** uses LLM with patient fields in prompt—note privacy handling for audits.

---

*End of knowledge dump. Safe for handoff to another model to produce final polished narrative.*
