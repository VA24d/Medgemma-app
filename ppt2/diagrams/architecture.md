# Med Veda — architecture diagrams (SparQ / Pervasive AI)

Use these in slides, proposals, and live demos. **Diagram 1** is the hero slide for Qualcomm (edge–cloud continuum). **Diagram 2** is the hybrid request path. **Diagram 3** is what ships on-device today (technically accurate).

**Export PNGs:** from repo root, with network:

```bash
py ppt2\diagrams\generate_images.py
# or: .venv\Scripts\python ppt2\diagrams\generate_images.py
```

PNGs land in `ppt2/diagrams/png/` (`diagram_1.png` … `diagram_6.png`).

---

## Diagram 1 — Pervasive AI continuum (hero)

*Three tiers + public internet for models only. Chart narrative stays on device.*

```mermaid
flowchart TB
    classDef edge fill:#1B5E20,stroke:#FFFFFF,stroke-width:2px,color:#FFFFFF
    classDef near fill:#1565C0,stroke:#FFFFFF,stroke-width:2px,color:#FFFFFF
    classDef vpc fill:#4527A0,stroke:#FFFFFF,stroke-width:2px,color:#FFFFFF
    classDef net fill:#ECEFF1,stroke:#546E7A,stroke-width:2px,color:#263238
    classDef data fill:#FFF8E1,stroke:#F9A825,stroke-width:2px,color:#263238
    classDef person fill:#37474F,stroke:#FFFFFF,stroke-width:2px,color:#FFFFFF

    Clinician([Clinician]):::person

    subgraph T1["Tier 1 — Device edge  ·  SHIPPED"]
        direction TB
        App["Med Veda Android app\nJetpack Compose"]:::edge
        DB[("Room DB\nlongitudinal chart")]:::data
        LLM["MedGemma 1.5 4B GGUF\ntext + chart chat"]:::edge
        VisionLocal["mmproj vision\n(optional local CXR)"]:::edge
        App --> DB
        App --> LLM
        App --> VisionLocal
    end

    subgraph T2["Tier 2 — Near edge  ·  PROTOTYPE"]
        direction TB
        LAN{{"Hospital / clinic LAN"}}:::near
        GPU["GPU imaging node\n4070 lab → 4090 hospital"]:::near
        VisionAPI["Vision service\nstructured JSON only"]:::near
        LAN --> GPU --> VisionAPI
    end

    subgraph T3["Tier 3 — Hospital VPC  ·  ROADMAP"]
        direction TB
        FHIRIn["FHIR / export ingest"]:::vpc
        Batch["Overnight batch jobs\n27B+ · rare-disease · cohorts"]:::vpc
        FHIRIn --> Batch
    end

    subgraph NET["Public internet  ·  no clinical PHI inference"]
        HF["Hugging Face\nmodel + mmproj download"]:::net
        Auth["OAuth / token"]:::net
    end

    SOC["Snapdragon\nCPU · Adreno GPU · Hexagon NPU"]:::edge
    LLM -.-> SOC
    VisionLocal -.-> SOC

    Clinician --> App
    App <-->|"Wi‑Fi: study pixels\n(governed)"| VisionAPI
    VisionAPI -->|"findings JSON"| App
    App -->|"user-initiated\nFHIR export"| FHIRIn
    Batch -->|"morning summaries"| App
    App <-->|"artifacts only"| HF
    App <-->|"sign-in"| Auth
```

**Say on slide:** Tier 1 is the pervasive copilot (always with the clinician). Tier 2 accelerates heavy imaging. Tier 3 scales history and batch analytics. HF/Auth never see chart Q&A prompts.

---

## Diagram 2 — Hybrid vision workflow (recommended path)

*Chart text never leaves the phone in hybrid mode.*

```mermaid
sequenceDiagram
    autonumber
    actor MD as Clinician
    participant App as Med Veda<br/>Snapdragon device
    participant DB as Room chart
    participant Edge as Near-edge GPU<br/>prototype 4070
    participant LLM as Local MedGemma<br/>text decode

    MD->>App: Capture image + question
    App->>DB: Load patient entries
    alt All local (Act 1)
        App->>LLM: Image + prompt + chart
        LLM-->>App: Streamed answer
    else Hybrid (Act 3 — target)
        App->>Edge: POST study image only
        Edge-->>App: Structured vision JSON
        App->>LLM: Chart + question + vision JSON
        Note over App,LLM: No full chart on server
        LLM-->>App: Streamed answer
    end
    App-->>MD: Markdown response
    opt Offline follow-up
        MD->>App: Further questions
        App->>LLM: Text only (cached vision summary)
        LLM-->>App: Answer without network
    end
```

---

## Diagram 3 — On-device stack (shipped, accurate)

*Single inference path: `aichatlib` + llama.cpp + mtmd — not LiteRT in the app.*

```mermaid
flowchart TB
    classDef ui fill:#E3F2FD,stroke:#1565C0,stroke-width:2px,color:#0D47A1
    classDef logic fill:#E8F5E9,stroke:#2E7D32,stroke-width:2px,color:#1B5E20
    classDef native fill:#FFF3E0,stroke:#E65100,stroke-width:2px,color:#BF360C
    classDef store fill:#F3E5F5,stroke:#6A1B9A,stroke-width:2px,color:#4A148C
    classDef hw fill:#3253B8,stroke:#FFFFFF,stroke-width:2px,color:#FFFFFF

    subgraph UI["Presentation"]
        MA[MainActivity / Nav]:::ui
        Screens["Patients · X-ray · Chat\nDiagnosis · Settings"]:::ui
        MA --> Screens
    end

    subgraph Logic["Kotlin orchestration"]
        CVM[ChatViewModel]:::logic
        IM[InferenceModel singleton]:::logic
        Worker[ScheduledPrognosisWorker]:::logic
        FHIR[FhirExportManager]:::logic
        Screens --> CVM
        Screens --> Worker
        Screens --> FHIR
        CVM --> IM
        Worker --> IM
        FHIR --> IM
    end

    subgraph Data["Local persistence"]
        Room[("MedicalDatabase / Room")]:::store
        Prefs[Encrypted prefs · PIN]:::store
        Files[("GGUF + mmproj on disk")]:::store
        Screens --> Room
        CVM --> Room
    end

    subgraph Native["aichatlib · JNI"]
        Engine[InferenceEngineImpl]:::native
        CPP["ai_chat.cpp\nllama.cpp + mtmd"]:::native
        IM --> Engine --> CPP
    end

    subgraph Models["On-device models"]
        GGUF[("medgemma Q4_K_M.gguf")]:::native
        MMP[("mmproj-F16.gguf\nlazy load")]:::native
        CPP --> GGUF
        CPP --> MMP
    end

    subgraph HW["Qualcomm Snapdragon"]
        CPU[CPU KleidiAI path]:::hw
        GPU[Adreno / GPU backend]:::hw
        NPU[Hexagon NPU when exposed]:::hw
        CPP -.-> CPU
        CPP -.-> GPU
        CPP -.-> NPU
    end

    HF[HF CDN download]:::ui
    HF -.->|"weights only"| Files
```

---

## Diagram 4 — Three deployment modes (benchmark story)

*Numbers on slide: `[TBD]` until measured.*

```mermaid
flowchart LR
    classDef bad fill:#FFEBEE,stroke:#C62828,stroke-width:2px,color:#B71C1C
    classDef mid fill:#FFF8E1,stroke:#F9A825,stroke-width:2px,color:#E65100
    classDef good fill:#E8F5E9,stroke:#2E7D32,stroke-width:3px,color:#1B5E20

    subgraph A1["Act 1 — All device"]
        P1[Phone\nvision + text]:::bad
        L1["E2E: TBD\nOffline: yes"]:::bad
        P1 --- L1
    end

    subgraph A2["Act 2 — All server"]
        S2[4070 / hospital GPU\nvision + text]:::mid
        L2["E2E: TBD\nOffline: no"]:::mid
        S2 --- L2
    end

    subgraph A3["Act 3 — Hybrid ✓"]
        P3[Phone\ntext + chart]:::good
        S3[GPU\nvision only]:::good
        L3["E2E: TBD\nOffline: partial"]:::good
        S3 -->|"JSON"| P3
        P3 --- L3
    end

    A1 ~~~ A2 ~~~ A3
```

---

## Diagram 5 — Trust boundaries & data types

```mermaid
flowchart LR
    classDef ok fill:#C8E6C9,stroke:#388E3C,color:#1B5E20
    classDef warn fill:#FFE0B2,stroke:#F57C00,color:#E65100
    classDef no fill:#FFCDD2,stroke:#D32F2F,color:#B71C1C

    subgraph Device["Device — trust zone A"]
        Chart[Chart text · notes · chat]:::ok
        QLocal[Local LLM inference]:::ok
    end

    subgraph Near["Near edge — trust zone B"]
        Pixels[Study pixels]:::warn
        Struct[Structured findings]:::ok
    end

    subgraph VPC["Hospital VPC — trust zone C"]
        Export[FHIR bundles]:::warn
        BatchOut[Batch insights]:::ok
    end

    subgraph Blocked["Not in v1"]
        PubLLM[Public cloud LLM\non full chart]:::no
    end

    Chart --> QLocal
    Pixels --> Struct --> QLocal
    Chart -.->|"user export only"| Export --> BatchOut
    Chart x--x PubLLM
```

---

## Diagram 6 — Night batch tier (roadmap)

```mermaid
flowchart TB
    classDef edge fill:#1B5E20,stroke:#fff,color:#fff
    classDef cloud fill:#4527A0,stroke:#fff,color:#fff

    Phone["Med Veda device\ncharging · Wi‑Fi"]:::edge
    LocalJob["On-device worker today\nScheduledPrognosisWorker"]:::edge
    Sync["Encrypted sync\nnew entries"]:::edge
    VPC["Hospital VPC\n27B · rare disease · cohort rules"]:::cloud
    Results["Summary cards\nnext morning"]:::edge

    Phone --> LocalJob
    Phone --> Sync --> VPC --> Results --> Phone
```

---

## Slide mapping

| Slide in `ppt2.md` | Use diagram |
|--------------------|-------------|
| Slide 4 — Three tiers | **Diagram 1** |
| Slide 9 — Hybrid pipeline | **Diagram 2** |
| Slide 8 — Shipped stack | **Diagram 3** |
| Slide 7 — Benchmark | **Diagram 4** |
| Slide 11 — Privacy | **Diagram 5** |
| Slide 10 — Night batch | **Diagram 6** |

---

## Legacy note

The old `diagram/system_architecture.md` showed LiteRT/TFLite as the app vision path and referenced `VernacularEngine` — **not accurate** for the shipping app. Use this folder instead.
