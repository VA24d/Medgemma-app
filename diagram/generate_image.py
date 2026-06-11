import base64
import urllib.request
import json
import os

mermaid_code = """flowchart TD
    classDef qualcomm fill:#3253B8,stroke:#FFFFFF,stroke-width:2px,color:#FFFFFF,rx:5px,ry:5px;
    classDef appLayer fill:#E3F2FD,stroke:#2B5797,stroke-width:2px,color:#000000,rx:5px,ry:5px;
    classDef engineLayer fill:#E8F5E9,stroke:#2E7D32,stroke-width:2px,color:#000000,rx:5px,ry:5px;
    classDef modelLayer fill:#FFF3E0,stroke:#E65100,stroke-width:2px,color:#000000,rx:5px,ry:5px;

    subgraph App ["📱 Application Layer (Android)"]
        UI[MainActivity UI Multi-Modal Input]:::appLayer
        VM[ChatViewModel Inference Orchestrator]:::appLayer
        VE[VernacularEngine & Language Extension]:::appLayer
        UI <--> VE
        UI <--> VM
        VE <--> VM
    end

    subgraph Inference ["⚙️ On-Device AI Engines (Edge Compute)"]
        direction LR
        LlamaCPP[aichatlib Custom llama.cpp JNI Wrapper]:::engineLayer
        LiteRT[LiteRT / TFLite Engine]:::engineLayer
        Tokenizer[Pure Kotlin HFTokenizer]:::engineLayer
        VM -->|Text Query| LlamaCPP
        VM -->|Vision / Image Data| LiteRT
        VM <--> Tokenizer
        Tokenizer <--> LiteRT
    end

    subgraph Models ["🧠 Local Quantized Models"]
        MedGemma[(MedGemma 1.5 4B Q4_K_M GGUF)]:::modelLayer
        SigLIP[(MedSigLIP 400M Custom TFLite Vision)]:::modelLayer
        LlamaCPP --> MedGemma
        LiteRT --> SigLIP
    end

    subgraph HW ["⚡ Target: Qualcomm Snapdragon"]
        CPU[Kryo CPU INT8/FP16]:::qualcomm
        GPU[Adreno GPU Vulkan/OpenCL]:::qualcomm
        NPU[Hexagon NPU Tensor Accelerators]:::qualcomm
        LlamaCPP -.-> CPU
        LlamaCPP -.-> GPU
        LiteRT -.-> NPU
        LiteRT -.-> CPU
    end

    App --> Inference
    Inference --> Models
"""

# Mermaid.ink requires base64 encoding without padding or standard base64 works depending on the payload.
encoded_string = base64.urlsafe_b64encode(mermaid_code.encode('utf-8')).decode('utf-8')
url = f"https://mermaid.ink/img/{encoded_string}?type=png&bgColor=white"

try:
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
    with urllib.request.urlopen(req) as response:
        with open(os.path.join(os.path.dirname(__file__), 'system_architecture.png'), 'wb') as out_file:
            out_file.write(response.read())
    print("PNG generated successfully inside the diagram folder!")
except Exception as e:
    print(f"Failed to generate PNG: {e}")
