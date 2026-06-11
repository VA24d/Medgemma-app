# Med Veda — SparQ / Qualcomm pitch (`ppt2`)

**Audience:** Qualcomm evaluators (Pervasive AI, edge–cloud hybrid)  
**Date target:** 5 June (time TBD)  
**Status:** Working deck outline + speaker notes. **All latency numbers are placeholders** until measured on target hardware.

---

## How to use this file

| Tag | Meaning |
|-----|---------|
| **[SHIPPED]** | In the open-source app / repo today |
| **[PROTOTYPE]** | Lab demo (e.g. home **RTX 4070** standing in for hospital GPU) |
| **[ROADMAP]** | Designed, not wired in app yet |
| **[TBD]** | Fill after benchmark run |

**Lab note:** When this doc says **VPC / hospital GPU**, the **prototype** uses a **4070 (or 4090) machine on LAN** — not production Google Cloud. Rename in talk: *“prototype near-edge imaging node.”*

**Architecture diagrams:** [`ppt2/diagrams/architecture.md`](diagrams/architecture.md) — six Mermaid figures (continuum, hybrid sequence, on-device stack, benchmark modes, trust boundaries, night batch). Export slide PNGs: `.venv\Scripts\python ppt2\diagrams\generate_images.py` → `ppt2/diagrams/png/`.

---

## One-line thesis (say early)

> **Med Veda puts clinical language and chart reasoning on the Snapdragon device (offline, private); heavy imaging runs on hospital GPU near the data; large batch analytics run overnight in the hospital cloud — pervasive AI as a continuum, not “everything in the cloud.”**

---

## Slide 1 — Title

**Med Veda: Pervasive clinical AI on Snapdragon + governed hybrid compute**

- On-device MedGemma 1.5 4B multimodal (edge)
- Optional hospital GPU for vision (near edge)
- Optional overnight institutional batch (VPC)

---

## Slide 2 — Problem (30–45 sec)

- Clinicians need **longitudinal chart + imaging** at the bedside.
- **Intermittent connectivity** (rural PHC, wards).
- **PHI** must not default to third-party cloud LLM APIs.
- **One device cannot do everything at full speed** (large studies, cold vision load, thermal limits).

*No numbers on this slide.*

---

## Slide 3 — What “Pervasive AI” means for us

**Pervasive AI** = intelligence **embedded where care happens** (phone/tablet), continuously available, context-aware — plus **orchestrated** tiers when connectivity and policy allow.

Not: “one datacenter chatbot.”  
Yes: **many nodes** — device → hospital LAN → hospital VPC — each running the **right workload**.

---

## Slide 4 — Three clinical tiers (main architecture)

| Tier | Where | Workload | Trigger | Status |
|------|--------|----------|---------|--------|
| **1 — Device (edge)** | Clinician phone / tablet (Snapdragon) | MedGemma **text**, chart-grounded chat, quick CXR, manual notes, PIN, local DB | Always; **offline OK** | **[SHIPPED]** |
| **2 — Near edge** | **Hospital / clinic LAN GPU** (prototype: **4070 box**) | **Heavy imaging** → structured JSON (findings, scores, regions) — **not** full cloud LLM on chart | Wi‑Fi / LAN; user or workflow | **[PROTOTYPE]** |
| **3 — VPC / batch** | Hospital DC or contracted cloud | Rare-disease screens, full-history reasoning, **27B-class** models, cohort jobs | **Night**, charging, Wi‑Fi | **[ROADMAP]** (on-device `ScheduledPrognosisWorker` is **[SHIPPED]** edge version of “overnight job”) |

**Trust line (memorize):**  
*Clinical narrative and chart Q&A stay on the device. The network delivers **models** and optional **hospital-controlled** imaging/batch tiers — not “send the chart to GPT.”*

---

## Slide 5 — Near edge vs VPC (one slide, kills confusion)

| | **Near edge** | **VPC / cloud (hospital)** |
|--|----------------|----------------------------|
| **Location** | Same building / LAN | Datacenter / private tenant |
| **Latency** | Seconds (vision) | Minutes–hours (batch OK) |
| **Example hardware** | 4070/4090 server, PACS adjunct | GPU cluster, archive |
| **Med Veda example** | Upload **study** → get **structured vision** → phone fuses + chats locally | Ingest **FHIR / exports** → run bigger models on **full history** |
| **Prototype today** | **4070 at lab** | Not live; **FHIR export [SHIPPED]** as handoff |

---

## Slide 6 — Why hybrid beats “only phone” or “only cloud”

| Approach | Wins | Loses |
|----------|------|-------|
| **All on phone** | Privacy, offline, no upload | Slow/heavy on **large** imaging; thermals; cold mmproj |
| **All on cloud/VPS** | Fast GPU for vision + text | Needs network; PHI/pixels leave device; no offline copilot |
| **Split (our design)** | Fast vision on GPU; **text + chart on device**; offline Q&A after sync | Requires hospital tier or lab prototype; upload on bad 4G hurts |

**Punchline:** Monolithic edge and monolithic cloud are both wrong for **multimodal clinical** workflows; **distribution** is the product.

---

## Slide 7 — Live story: three-act benchmark (numbers TBD)

**Purpose:** Prove Act 3 on a slide — *not* claim cloud is the product.

**Fixed test (define once, reuse everywhere):**

- Image: `[TBD: e.g. bundled demo CXR / specify resolution]`  
- Prompt: `[TBD: same radiology prompt in all acts]`  
- Device: `[TBD: Qualcomm reference device — model, OS, build]`  
- Server: `[TBD: 4070, LAN RTT ___ ms, same image bytes]`  
- Network: `[TBD: Wi‑Fi / USB tether / lab LAN only]`

| Act | Setup | What you measure | Status |
|-----|--------|------------------|--------|
| **1 — All local** | MedGemma + mmproj on phone | E2E, vision-only, text-only | **[SHIPPED]** app |
| **2 — All remote** | Image + full LLM on 4070 | E2E (incl. upload + download) | **[PROTOTYPE]** |
| **3 — Hybrid** | Vision on 4070 → JSON → **text on phone** | E2E vs Acts 1 & 2 | **[PROTOTYPE]** |

### Results table — **leave empty until measured**

| Metric | Act 1 (phone) | Act 2 (all cloud) | Act 3 (hybrid) |
|--------|---------------|-------------------|----------------|
| Cold start (first run of session) | **[TBD]** | **[TBD]** | **[TBD]** |
| Vision stage only | **[TBD]** | **[TBD]** | **[TBD]** |
| Text stage only (chart prompt, no image) | **[TBD]** | **[TBD]** | N/A (text on phone) |
| **E2E** (tap → final answer) | **[TBD]** | **[TBD]** | **[TBD]** |
| Works offline after prep? | **Yes** | **No** | **Partial** (text yes; vision needs sync) |

**Footnotes for slide:**

- Include **upload time** in Act 2 & 3 on a second row: **[TBD]** — hybrid loses on poor uplink; wins on Wi‑Fi + heavy image.
- Do **not** claim “NPU faster than 4090” — say **“Snapdragon runs text with strong perf-per-watt; datacenter GPU runs vision burst.”**
- Optional sub-row: phone text backend CPU vs GPU vs NPU → **[TBD]** / **[TBD]** / **[TBD]**

---

## Slide 8 — What runs on the phone today [SHIPPED]

- **MedGemma 1.5 4B** GGUF (`Q4_K_M` default) + lazy **mmproj** vision.
- **Room** longitudinal entries (X-ray, histo, manual, recording).
- **Chart-grounded chat**, diagnosis / scheduled prognosis worker.
- **Thinking vs direct**, multilingual prompts (Telugu/Hindi steering).
- **HF download / OAuth** — cloud = **artifacts + auth only**.
- **FHIR export** — user-triggered bundle (local LLM assists JSON per patient).

*Demo path:* PIN → patient → X-ray or chat → answer **without internet** (after model load).

---

## Slide 9 — Hybrid vision pipeline [PROTOTYPE → ROADMAP]

```text
[Camera / gallery]
       │
       ▼
┌──────────────┐     LAN / Wi‑Fi      ┌─────────────────────┐
│ Med Veda app │ ─── image bytes ───► │ Near-edge GPU node   │
│ (Snapdragon) │ ◄── JSON findings ── │ (4070 lab / 4090 Hosp)│
└──────────────┘                      └─────────────────────┘
       │
       │  chart + user question + vision JSON
       ▼
 Local MedGemma TEXT decode (offline-capable Q&A)
```

**Cloud LLM on full chart:** intentionally **out of scope** for v1.

---

## Slide 10 — Overnight batch tier [ROADMAP]

**Today [SHIPPED]:** `ScheduledPrognosisWorker` — on-device batch when charging (local model).

**Tomorrow [ROADMAP]:**

- Sync **new entries** (encrypted) to hospital VPC when on Wi‑Fi.
- **Batch** rare-disease / differential expansion / cohort rules on **bigger models**.
- Results **pulled down** next morning — clinician sees summary in app.

**Why batch in VPC:** doesn’t need bedside latency; uses **full history** and **heavy compute** once per day.

| | Bedside (tier 1) | Night batch (tier 3) |
|--|------------------|----------------------|
| Latency | Seconds | Minutes–hours OK |
| Model size | ~4B quantized | 27B+ / ensembles |
| User waiting | Yes | No |

---

## Slide 11 — Privacy & trust boundaries

| Data | Device | Near edge (hospital GPU) | Public cloud LLM API |
|------|--------|---------------------------|----------------------|
| Full chart text | **Stays** | Only if hospital policy sends | **No (v1)** |
| Study pixels | Processed locally or uploaded to **hospital node** | **Governed** | **No** |
| Model weights | Downloaded from HF/CDN | — | — |
| FHIR export | User-initiated file | Hospital ingests | — |

**Demo ethics:** Act 2/3 benchmarks use **[TBD: synthetic / public demo CXR only]** until hospital BAA path exists.

---

## Slide 12 — Snapdragon / Qualcomm angle

- **Edge-first** MedGemma on **Android** via native **llama.cpp** (`aichatlib`), multimodal **mtmd**.
- **Heterogeneous compute:** CPU / GPU / NPU preferences in app settings — measured paths **[TBD]** on Qualcomm device.
- **Quantization discipline:** in-repo benchmarks (Q4 vs Q8 vs BF16) — cite README; phone ships **Q4_K_M** for footprint.
- **Pervasive:** copilot **at point of care**, not only in datacenter.

*Optional small table — device token speed text-only:*

| Backend | tok/s [TBD] |
|---------|-------------|
| CPU | |
| GPU | |
| NPU | |

---

## Slide 13 — What we ask / next steps

- **[TBD]** Pilot hardware (Snapdragon reference kit) to fill benchmark table.
- **[TBD]** Hospital LAN imaging API partner or PACS integration.
- Align on **NPU** path for sustained text decode on device.

---

## Slide 14 — Backup / Q&A cheatsheet

**Q: Is this just downloading the model from the internet?**  
A: No — hybrid means **where inference runs**: device text, hospital GPU vision, VPC batch. HF is delivery only.

**Q: Why not all cloud if Act 2 is faster?**  
A: Act 2 **[TBD]** may win wall-clock in lab but fails **offline, privacy, and narrative PHI** constraints; Act 3 trades a little complexity for **both**.

**Q: What’s implemented vs slideware?**  
A: Tier 1 + FHIR export + local multimodal **[SHIPPED]**; Acts 2–3 imaging split **[PROTOTYPE on 4070]**; tier 3 cloud batch **[ROADMAP]**.

**Q: Near edge vs VPC?**  
A: Near edge = **same hospital LAN, seconds**; VPC = **overnight / big history / big models**.

---

## Benchmark protocol (fill before 5 June)

Run each act **3 times**, report **median**. Note **cold vs warm** (first mmproj load vs second image).

### Environment checklist

- [ ] Qualcomm device: `[TBD model]`  
- [ ] App build / commit: `[TBD]`  
- [ ] GGUF quant on phone: `[TBD e.g. Q4_K_M]`  
- [ ] 4070 server: `[TBD OS, CUDA, what runs vision]`  
- [ ] Image file + SHA: `[TBD]`  
- [ ] Prompt text (paste in appendix): `[TBD]`  

### Commands / scripts

- Phone Act 1: use app + stopwatch or log timestamps from `InferenceModel` / logcat tags.  
- Act 2/3: `[TBD: FastAPI/Flask on 4070 — script path when written]`  

### Where to paste results

Update **Slide 7 table** and add a row to **Slide 12** token speeds. Mark doc header: *“Benchmarks completed [date] on [device].”*

---

## 5-minute talk track (no numbers until TBD filled)

| Min | Content |
|-----|---------|
| 0:00 | Title + thesis |
| 0:30 | Problem + pervasive definition |
| 1:15 | Three tiers diagram (device / near edge / VPC batch) |
| 2:00 | Why not monolithic edge or cloud |
| 2:45 | Three-act benchmark (**“numbers on next slide — measured on [date]”** or walk structure only if empty) |
| 3:30 | Live demo tier 1 [SHIPPED] |
| 4:15 | Prototype hybrid + roadmap batch |
| 4:45 | Privacy + ask |

---

## Appendix A — Act 2 vs Act 3 implementation sketch [PROTOTYPE]

**Act 2 — All remote (4070):**

1. POST image + prompt to lab server.  
2. Server runs vision + text; returns full markdown answer.  
3. Phone displays result.  

**Act 3 — Hybrid:**

1. POST image → server returns **only** structured vision JSON (no patient chart on server).  
2. Phone builds prompt: `chart + vision_json + user_question`.  
3. Local `InferenceModel` text decode only.  

*Chart never leaves phone in Act 3 — strong privacy story.*

---

## Appendix B — Shipped vs not (audit for judges)

| Capability | Status |
|------------|--------|
| Local GGUF + mmproj multimodal | **[SHIPPED]** |
| Patient Room DB + entries | **[SHIPPED]** |
| HF model download / token | **[SHIPPED]** |
| FHIR export to Downloads | **[SHIPPED]** |
| Scheduled on-device prognosis | **[SHIPPED]** |
| 4070 vision API | **[PROTOTYPE]** |
| Hospital LAN deployment | **[ROADMAP]** |
| VPC overnight batch ingest | **[ROADMAP]** |
| Vernacular bracket injection on UI stream | **[ROADMAP]** (dictionary exists; wire TBD) |

---

## Appendix C — Do not say on stage

- “NPU is faster than RTX 4090 for MedGemma” (unless **[TBD]** proves it).  
- “Everything runs in Google Cloud” for clinical inference.  
- “Fully implemented hybrid imaging” without **[PROTOTYPE]** label.  
- Single latency number without cold/warm and image size context.

---

*Last updated: outline for Qualcomm SparQ; benchmarks **[TBD]** until Qualcomm device + 4070 protocol are run.*
