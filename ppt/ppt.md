# Med Veda — SparQ submission copy

## Abstract (≤ 300 words)

Mobile clinical workflows operate under intermittent connectivity, strict latency budgets at the point of care, and thermal constraints on sustained inference. Protected health information must remain amenable to privacy-preserving processing, yet multimodal decisions still require tight coupling between longitudinal records and high-resolution imaging.

Med Veda is a deployable Android clinician assistant centered on on-device Google MedGemma 1.5 4B multimodal inference. The system architecture partitions responsibility explicitly: privacy-sensitive longitudinal reasoning, chart-conditioned dialogue, and multilingual clinical generation execute locally on Snapdragon-class hardware behind a native GGUF runtime suitable for heterogeneous acceleration, including NPU-assisted paths where exposed by the stack. This edge-first design preserves low-latency interaction in offline-first or bandwidth-limited wards and keeps narrative PHI under local control during inference. Multilingual clinical responses are produced from the same on-device conditioned state, avoiding external translation services for chart-bearing prompts.

Compute-intensive vision stages are selectively orchestrated to enterprise imaging services under institutional credentials. Those services return compact structured analytics rather than open-ended cloud language generation; the handset fuses those signals with local embeddings and record context so multimodal fusion remains clinically grounded without shipping full charts to third-party language endpoints.

FHIR-oriented export closes the loop for health-system scale: the same longitudinal artifact can be ingested on hospital on-premises or VPC GPU clusters for deeper screening, cohort analytics, or larger specialist models while the handset remains the bedside copilot.

The contribution is a production-oriented hybrid edge-cloud pattern for pervasive clinical AI: Snapdragon-edge multimodal reasoning as the default trust boundary, governed delegation for heavy vision, and standards-based extensibility for institutional compute.

---

## Detailed description

### Pipeline: models, quantization, and validation

We start from **Hugging Face**–hosted **MedGemma 1.5 4B IT** weights and community **GGUF** builds (e.g. **Unsloth** `medgemma-1.5-4b-it-GGUF`), packaged for **on-device** use with a **multimodal projector** (**mmproj**) for vision. The app loads **GGUF** through a **native llama.cpp-class** Android library (`aichatlib`) with **heterogeneous-friendly** CPU/GPU-style backend configuration suitable for **Snapdragon** class devices (with a path to **NPU-accelerated** stacks where the runtime and OS expose them).

We ran a **reproducible benchmark suite** in-repo (`benchmarking/`) across **MedMCQA, MedQA, PubMedQA, MMLU-Med, MedXpertQA** (500 questions per benchmark vs BF16). **Findings (published in our README / writeup):** **Q8_0 GGUF** tracks BF16 within noise on all five (**e.g. MedMCQA 44.40% vs BF16 43.80%**); **Q6_K** is near-lossless with a smaller footprint; **Q4_K_M** is the practical phone default (~2.4 GB) with **known** drops on knowledge-heavy subsets but **stable** MedQA/PubMedQA-style reasoning curves—so we **characterized** accuracy vs size and **selected quantizations deliberately** rather than shipping blind. We also maintain a **TFLite** conversion track for **alternative** Snapdragon deployment (text + MedSigLIP-style vision) with separate MedMCQA numbers where context limits apply.

### Application — features (most → least central)

1. **Multimodal imaging (X-ray, MRI)** — capture or attach studies, run **local MedGemma + mmproj** or, in the **hybrid** deployment, **enterprise imaging API** first for **fast structured vision features**, then **local** model for **interpretation, differentials, and documentation** grounded in the chart.  
2. **Histopathology** — same pattern: very large fields of view favor **short server-side vision** + **local** synthesis for clinician Q&A.  
3. **Longitudinal patient history** — **Room**-stored timeline; **history screen** with filters (e.g. imaging types), **AI “expand”** actions that bridge prior entries with current context, and **chart-grounded chat** so the model sees **dated entries** instead of a stateless chat box.  
4. **Diagnosis / prognosis** — aggregates imaging-capable entries and prior impressions; ties to **scheduled prognosis** worker for **offline-friendly** batch generation when configured.  
5. **Other entry types** — **audio/recording** and **manual notes** feed the same longitudinal record.  
6. **Security & model ops** — **encrypted preferences**, **PIN**, **Hugging Face token / OAuth** for **artifact download**, **model picker** (multiple GGUF quantizations), **thinking vs direct** inference mode, **energy / backend** preferences.  
7. **Language** — **Telugu / Hindi** output steering plus **vernacular term maps** on streamed text for low-literacy contexts without shipping PHI to translation APIs.

### Why the enterprise imaging API hybrid is intentional

**Whole-slide / large stacks and deep pre-processing** can take **minutes** on a handset CPU/GPU path; a **hospital-contracted** vision endpoint on **datacenter GPUs** often returns **labels, heatmaps, or embedding summaries in 1–2 seconds**. The phone keeps **liability-sensitive narrative** and **full chart** under **local MedGemma**; the API sees **only what imaging governance allows** (typically **study pixels** under **enterprise keys**), not the entire longitudinal narrative—**latency where physics wins**, **privacy where law and ethics win**.

### Export and on-premises “big hospital” path

**FHIR export** (and related bundles) lets the **organization** ingest the same longitudinal artifact into **self-hosted** clusters: run **larger open or licensed models**, **rare-disease** classifiers, **cohort analytics**, or **full-history** graph jobs on **on-prem / VPC GPUs**—the **phone remains the capture and bedside copilot**; the **data center** is the **scale-out** tier. That is the **pervasive AI continuum**: **edge** for always-on copiloting, **private cloud** for **heavy** and **institutional** compute.

---

*Figures cited for GGUF benchmarks match `benchmarking/README.md` (500-sample GPU runs). Deployment claims for enterprise APIs and on-prem jobs describe the **intended production architecture** alongside the **current open-source app** codebase.*
