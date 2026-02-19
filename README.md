
# 🏥 MediPro Chronicler: Next-Gen On-Device Medical AI

![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-0095D5?style=for-the-badge&logo=kotlin&logoColor=white)
![MediaPipe](https://img.shields.io/badge/MediaPipe-LLM-orange?style=for-the-badge)
![Gemma](https://img.shields.io/badge/Model-MedGemma%204B-blue?style=for-the-badge)

**MediPro Chronicler** is a state-of-the-art, **privacy-first** medical assistant designed for the **Kaggle Google MedGemma Challenge**. It brings the power of the specialized **MedGemma 4B** Large Language Model directly to Android devices, enabling longitudinal patient history analysis and multimodal diagnostics without *ever* sending data to the cloud.

---

## 🌟 Why MediPro Chronicler?

In emergency rooms and remote clinics, every second counts. Cloud dependency introduces latency and privacy risks. **MediPro Chronicler** runs entirely offline, offering:

*   **⚡ Instant Analysis**: Zero network latency for life-saving insights.
*   **🔒 Absolute Privacy**: Patient data never leaves the device. HIPAA-ready by design.
*   **🧠 Specialized Medical Knowledge**: Fine-tuned on massive medical datasets (VQA, Radiology).
*   **👁️ Multimodal Vision**: Analyze X-Rays, MRIs, and CT scans simply by pointing your camera.

---

## 🛠️ Key Features

### 1. **Longitudinal Patient Memory**
Unlike standard chatbots, MediPro remembers. It builds a secure, encrypted local database of patient history, allowing it to answer context-aware questions like:
> *"Has this patient's lung opacity improved since last month's scan?"*

### 2. **Multimodal Diagnostics (Vision + Text)**
Seamlessly integrate visual data with clinical notes.
*   **Input**: "Check this X-ray for pneumothorax." + [Upload Image]
*   **Output**: "I detect a slight separation in the upper right pleural space..."

### 3. **Smart Data Ingestion**
*   **FHIR Export**: Interoperable data export for hospital systems.
*   **Voice-to-Text**: Dictate notes directly during patient exams.

---

## 🏗️ Architecture & Tech Stack

This project leverages **Modern Android Development (MAD)** best practices:

*   **LLM Engine**: MediaPipe LLM Inference (LiteRT) with **GPU/NPU Acceleration**.
*   **Model**: **MedGemma 4B** (Q4 Quantized / Int8 Optional).
    *   *Primary (Q4)*: `medgemma_4b_tpu_q4_block128_ekv512.tflite` (Faster, ~2GB)
    *   *Optional (Int8)*: `medgemma_4b_mobile_int8_q8_ekv2048.tflite` (Higher Quality, ~4GB)
    *   *Vision Encoder*: `siglip_encoder.tflite`
    *   *Multimodal Projector*: `projector.tflite`
*   **Hardware Acceleration**:
    *   Configured to use **GPU Logic** (`Backend.GPU`).
    *   Automatically delegates to **NPU** where supported by the Android Neural Networks API (NNAPI) or specific chipset drivers (TensorFlow Lite delegates).
*   **UI**: Jetpack Compose (Material 3).
*   **Database**: Room (SQLite) with encrypted storage.
*   **Dependency Injection**: Hilt.

---

## 🚀 Getting Started

### Prerequisites

*   **Device**: High-end Android Device.
    *   *8GB RAM+:* Sufficient for Q4 model.
    *   *12GB RAM+:* Recommended for Int8 model.
    *   *Note: Pixel 9 Pro or S24 Ultra highly recommended.*
*   **OS**: Android 14+ (API Level 34).
*   **Hugging Face Account**: Required to download the gated MedGemma weights.

### Installation

1.  **Clone the Repository**
    ```bash
    git clone https://github.com/Start_Antigravity/MediPro-Chronicler.git
    cd MediPro-Chronicler
    ```

2.  **Configure Access Token**
    *   Generate a **User Access Token** (Read permissions) from [Hugging Face Settings](https://huggingface.co/settings/tokens).
    *   Open `local.properties` in the project root.
    *   Add the token:
        ```properties
        HF_ACCESS_TOKEN=hf_your_generated_token_here
        ```

3.  **Build & Run**
    *   Open in **Android Studio Ladybug** (or newer).
    *   Sync Gradle.
    *   Select your physical device and press **Run**.

### 📦 Model Deployment (Local ADB)

To push the models manually (faster than downloading on device):
1.  Download `medgemma_4b_tpu_q4_block128_ekv512.tflite`, `siglip_encoder.tflite`, and `projector.tflite`.
2.  Push to device:
    ```bash
    adb shell mkdir -p /data/local/tmp/medgemma
    adb push medgemma_4b_tpu_q4_block128_ekv512.tflite /data/local/tmp/medgemma/
    adb push siglip_encoder.tflite /data/local/tmp/medgemma/
    adb push projector.tflite /data/local/tmp/medgemma/
    ```

### 📦 Model Download (Automatic)

Upon first launch, the app will automatically authenticate with your Hugging Face token and download the three required model artifacts to the device's private storage.
*   **Text Integration**: `medgemma_4b_tpu_q4...tflite`
*   **Vision Adapter**: `siglip_encoder.tflite`
*   **Projector**: `projector.tflite`

*Ensure your device has at least 10GB of free space.*

---

## 🧪 Testing

To run the verification tests (which simulate the download and inference pipeline):

```bash
# Verify Unit Logic
./gradlew testDebugUnitTest

# Verify On-Device Inference (Requires connected device/emulator)
./gradlew connectedDebugAndroidTest
```

---

## 🤝 Contribution

We welcome contributions! Please fork the repository and submit a Pull Request.
*   **Bug Reports**: Open an issue describing the crash/bug.
*   **Feature Requests**: Discuss new medical capabilities in Discussions.

---

## 📄 License & Credits

*   **MedGemma**: Developed by Google DeepMind & Megalodon ML.
*   **License**: Apache 2.0.
*   Disclaimer: *MediPro Chronicler is a research tool and should not be used as the sole basis for clinical diagnosis.*
