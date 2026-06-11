# Edge vs cloud — what actually makes sense (clinical copilot apps)

How this is usually done: **anything that touches patient narrative, identifiers, imaging, or audio stays on-device or inside infrastructure the hospital already treats as HIPAA/BAA-covered.** Third-party **cloud LLM APIs with PHI in the prompt** are the pattern people avoid unless there is a **BAA** and a **minimum-necessary** data design. Model files and app binaries are fetched from the cloud like any app — that is not “clinical AI in the cloud.”

---

## Process on-device (Snapdragon 8 Elite — your default)

| Workload | Why |
|----------|-----|
| **LLM + vision over charts, notes, and images** | PHI + latency + offline; avoids making a random vendor a “clinical inference processor.” |
| **Embeddings / retrieval over the patient chart** | Query + chunks are PHI; belongs next to the DB. |
| **Speech → text for clinical dictation** | Audio is PHI; use **on-device** speech APIs, not a cloud STT that sees raw audio. |
| **OCR / doc scan → text for patient paperwork** | Same; on-device ML kits, not “upload page photo to cloud OCR” unless that service is under contract for PHI. |
| **Rules / calculators** (dose, scores, hardcoded pathways) | Fast, deterministic, no reason to ship data out. |

---

## Use the cloud where it is standard and not PHI inference

| Workload | Why |
|----------|-----|
| **Delivering model weights, app updates, config manifests** | Big blobs + versioning; no patient content in those blobs. |
| **Identity for downloads** (OAuth, HF token) | Operational; not clinical processing. |
| **Hospital-controlled sync** (FHIR to **their** EHR endpoint, VPN) | Still “cloud,” but it is **their** trust domain, not “ask ChatGPT.” |

---

## Hybrid that is still defensible (only if you implement it cleanly)

| Pattern | Local | Cloud |
|---------|-------|-------|
| **Orchestration only** | All tokens over PHI. | Returns **non-PHI** artifacts only: e.g. “new model version available,” feature flags, **public** guideline PDF URLs — not the patient text. |
| **Structured lookup APIs** | Model drafts plan using chart. | Optional call with **codes or short structured fields** only (e.g. NDC, ICD) to a **drug interaction or formulary** API that is designed for that input shape — **not** full free-text notes. |
| **Weak model on device, strong model in VPC** | N/A for consumer phone alone. | Real pattern in enterprises: PHI stays in **customer VPC / on-prem GPU** with contracts — not the same as hitting a public API from Med Veda. |

---

## Do not use cloud for (unless you redo the product as enterprise + BAA)

- **Remote LLM inference** on full prompts that include **history, imaging context, or dictation.**  
- **“Smarter” cloud RAG** where **patient chunks** are sent to a hosted vector DB you do not control.

---

## What to change in *your* app for SparQ / “Pervasive AI”

You already match the **hard** part (MedGemma local). To match the theme **without** selling out PHI:

1. **Name the split in the UI:** one line — “Clinical AI runs on this device; internet is used for model delivery and sign-in only.”  
2. **Optional:** tiny **manifest** URL (JSON) for model URL + hash — cloud does **release management**, phone does **inference**.  
3. **Optional later:** **on-device** STT/OCR if you add voice or document intake; that is where teams usually slip and accidentally cloud PHI.

That is the real industry shape: **pervasive = inference at the edge**, cloud = **distribution, identity, and enterprise pipes** — not “split the brain so half the tokens go to GPT-4.”
