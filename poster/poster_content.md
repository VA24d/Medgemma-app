# Med Veda — SparQ 2026 Research Poster Content

Source-of-truth for all text blocks on `Med-Veda-SparQ-2026-Poster-v3.pptx`.

---

## Title

**Med Veda**

Pervasive Clinical AI on Snapdragon + Governed Hybrid Compute

On-device MedGemma multimodal | Edge companion hub | Governed cloud tiers | Privacy-first by design

**Subtitle tags:** On-Device Inference · Edge Companion · FHIR Interop · Offline-First

---

## Abstract (~280 words)

Mobile clinical workflows operate under intermittent connectivity, strict latency budgets at the point of care, and thermal constraints on sustained inference. Protected health information must remain amenable to privacy-preserving processing, yet multimodal decisions still require tight coupling between longitudinal records and high-resolution imaging.

Med Veda is a deployable Android clinician assistant centered on on-device Google MedGemma 1.5 4B multimodal inference. The system architecture partitions responsibility explicitly: privacy-sensitive longitudinal reasoning, chart-conditioned dialogue, and multilingual clinical generation execute locally on Snapdragon-class hardware behind a native GGUF runtime suitable for heterogeneous acceleration, including NPU-assisted paths. This edge-first design preserves low-latency interaction in offline-first wards and keeps narrative PHI under local control.

Compute-intensive vision stages are selectively orchestrated to enterprise imaging services under institutional credentials, returning compact structured analytics rather than open-ended cloud language generation. FHIR-oriented export enables hospital on-premises or VPC GPU clusters for deeper screening, cohort analytics, or larger specialist models while the handset remains the bedside copilot.

The contribution is a production-oriented hybrid edge–cloud pattern for pervasive clinical AI: Snapdragon-edge multimodal reasoning as the default trust boundary, governed delegation for heavy vision, and standards-based extensibility for institutional compute.

---

## Problem Statement

- Clinicians need longitudinal chart + imaging at the bedside, often with intermittent or no connectivity
- Cloud-bound AI introduces unacceptable latency at point of care and mandates PHI egress to third-party servers
- India's DPDP Act and global data protection laws impose strict constraints on clinical data movement
- A single device cannot handle all workloads at full speed: large imaging studies hit thermal and compute limits
- Existing solutions force a false choice between "all on phone" (slow, limited) or "all in cloud" (privacy-violating, offline-incompatible)

---

## Methods & Technical Approach

- **MedGemma 1.5 4B IT (Multimodal):** Deployed via GGUF (Q4_K_M default, 2.4 GB) through custom llama.cpp JNI wrapper (aichatlib) with lazy mmproj vision encoder loading
- **Heterogeneous Acceleration:** CPU (KleidiAI on arm64), Adreno GPU, and Hexagon NPU runtime paths; user-selectable energy mode with 2B/4B dynamic switching
- **Chart-Grounded Inference:** Room DB stores longitudinal patient records; system prompts inject full chart context with dated entries for temporally-aware reasoning
- **Hybrid Vision Pipeline:** Study pixels sent to near-edge GPU (prototype: RTX 4070); only structured JSON findings return to device for local text synthesis
- **FHIR Interoperability:** On-device LLM-assisted FHIR bundle export enables hospital VPC ingestion for batch analytics with larger models

---

## Three-Tier Pervasive AI Architecture

### Tier 1 — Device Edge [SHIPPED]
Android phone (Snapdragon): MedGemma 1.5 4B GGUF + mmproj; chart-grounded chat; Room DB; PIN auth; full offline operation

### Tier 2 — Near Edge [PROTOTYPE]
Hospital/clinic LAN GPU (lab: RTX 4070): Heavy imaging → structured JSON findings (not full chart to cloud LLM); edge companion hub with web dashboard

### Tier 3 — Hospital VPC / Batch [ROADMAP]
Overnight batch: 27B+ models, rare-disease screens, cohort analytics via FHIR ingest. On-device ScheduledPrognosisWorker is the shipped edge version of this tier

---

## Privacy & Trust Boundary

Clinical narrative and chart Q&A default on-device. Edge companion and cloud tiers are user-governed; chart text does not go to third-party LLMs unless the clinician explicitly selects Gemini API.

Public internet is used only for model weight delivery (Hugging Face CDN) and OAuth sign-in — never for clinical inference on patient data.

---

## Key Highlights & Innovations

1. **On-Device Multimodal AI:** MedGemma 1.5 4B runs text + vision entirely on Snapdragon via custom llama.cpp JNI (aichatlib). Zero cloud inference for clinical data.
2. **Quantization Discipline:** In-repo benchmarks across 5 medical datasets (500 samples each) prove Q8_0 is lossless (47% size reduction) and Q4_K_M preserves reasoning accuracy (zero drop on MedQA/PubMedQA) at 2.4 GB.
3. **Hybrid Vision Pipeline:** Heavy imaging (X-ray, histopathology) offloaded to near-edge GPU; only structured JSON returns to phone. Chart text never leaves device in hybrid mode.
4. **Edge Companion Hub:** FastAPI-based laptop server (port 8787) with patient sync, Ollama/Gemini chat routing, chart processing, and web dashboard for clinical oversight.
5. **Smart Vernacular Injection:** Zero-overhead regex interceptor translates complex medical jargon to Telugu/Hindi in the output stream without degrading MedGemma's core reasoning benchmarks.
6. **Longitudinal Intelligence:** Room DB stores chronological patient profiles; AI bridges years of historical records with current symptoms via chart-grounded prompts with dated entries.
7. **FHIR Export & Interoperability:** On-device LLM-assisted FHIR bundle generation enables data flow to hospital systems without cloud intermediary. Encrypted local storage + PIN/biometric auth.
8. **Scheduled Prognosis Worker:** WorkManager-based overnight batch generates AI prognoses for all patients when device is charging — shipped edge version of the hospital batch tier.

---

## Benchmarks & Performance

### Device Performance Metrics

| Metric | Value |
|--------|-------|
| Text Inference Speed | ~18–22 tok/s |
| RAM Consumption | 4.1 GB peak |
| Model Size (Q4_K_M) | 2.4 GB |
| Medical Benchmarks | 5 datasets |
| Samples per Benchmark | 500 |

### Quantization vs. BF16 Baseline (500 samples × 5 benchmarks, RTX 4090 GPU)

| Model | Size | MedMCQA | MedQA | PubMedQA | MMLU Med | MedXpertQA |
|-------|------|---------|-------|----------|----------|------------|
| BF16 (baseline) | 7.3 GB | 43.80% | 29.00% | 55.40% | 43.00% | 8.80% |
| Q8_0 | 3.9 GB | 44.40% | 28.60% | 55.40% | 43.60% | 8.80% |
| Q6_K | 3.0 GB | 40.80% | 28.40% | 57.40% | 41.40% | 9.80% |
| Q4_K_M | 2.4 GB | 32.60% | 29.00% | 55.40% | 29.80% | 10.00% |

### Key Findings

- Q8_0 is lossless: zero meaningful accuracy drop across all 5 benchmarks at ~47% size reduction (7.3 GB → 3.9 GB)
- Q6_K is near-lossless: within noise margin on all benchmarks at ~59% size reduction (7.3 GB → 3.0 GB)
- Q4_K_M trades knowledge recall (−11.2pp MedMCQA) for zero reasoning drop (MedQA, PubMedQA) at 2.4 GB — ideal edge compromise
- TFLite Q8 alternative: 39.55% MedMCQA natively on CPU, matching GGUF Q8_0 quality for CPU-only deployment paths

---

## Authors (Footer)

Itikela Bhaskar | Vijay Aravynthan
Qualcomm India · SparQ 2026

---

## Images Used

- `poster/assets/architecture_diagram.png` — Three-tier pervasive AI continuum (from ppt2/diagrams/png/diagram_1.png)
- `poster/assets/trust_boundaries.png` — Trust boundary data flow (from ppt2/diagrams/png/diagram_5.png)
- `poster/assets/sparq_logo.png` — SparQ 2026 event branding (extracted from template)
- App screenshots: home screen, X-ray analysis, patient page (from `app images/`)
