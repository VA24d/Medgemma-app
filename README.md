
# 🏆 Kaggle Google MedGemma Challenge Submission

**MediPro Chronicler** is an on-device, multimodal AI assistant designed to empower healthcare professionals with secure, longitudinal patient history analysis. Built for the **Kaggle Google MedGemma Challenge**, it leverages the lightweight yet powerful **Gemma 3 1B-IT** model to process medical inquiries and analyze X-rays directly on Android devices—ensure 100% data privacy with zero cloud egress.

---

# 📖 Overview

In the fast-paced medical environment, retrieving patient history and analyzing diagnostic images quickly is critical. Cloud-based solutions often raise privacy concerns and latency issues.

**MediPro Chronicler** solves this by running a specialized medical LLM locally. It allows doctors to:
- **Chat with Patient Records**: Ask natural language questions about a patient's history.
- **Analyze X-Rays**: Upload and discuss medical imagery for preliminary insights.
- **Maintain Privacy**: All data stays on the device, compliant with strict healthcare privacy standards.

# ✨ Key Features

-   **🤖 On-Device Intelligence**: Powered by **Gemma 3 1B-IT** via MediaPipe LLM Inference, offering low-latency responses without internet.
-   **👁️ Multimodal Capabilities**: Seamlessly integrates text and image inputs. Show an X-ray and ask for an analysis.
-   **🔒 Privacy First**: Complete local execution ensures patient data never leaves the tablet/phone.
-   **📂 Longitudinal Patient Records**: Securely store and retrieve patient profiles and history using a local Room Database.
-   **⚡ Optimized Performance**: Built with Android Jetpack Compose and hardware-accelerated TFLite/MediaPipe delegates.

# 🛠️ Technology Stack

-   **Model**: Google Gemma 3 1B-IT (Quantized int8)
-   **Inference Engine**: MediaPipe LLM Inference (LiteRT)
-   **Android Architecture**: Modern Android Development (MAD)
    -   **Language**: Kotlin
    -   **UI**: Jetpack Compose
    -   **Database**: Room
    -   **Image Loading**: Coil
    -   **Camera**: CameraX

# 🚀 Getting Started

## Prerequisites

-   **Hardware**: Physical Android device (SDK 24+) with developer mode enabled. *GPU acceleration recommended.*
    -   *Note: Emulators may crash due to memory limits with large models.*
-   **Software**: [Android Studio](https://developer.android.com/studio) (Hedgehog or newer).
-   **Hugging Face Account**: You need an account to access the gated Gemma model.

## Installation

1.  **Clone the Repository**
    ```bash
    git clone https://github.com/Start_Antigravity/MediPro-Chronicler.git
    cd MediPro-Chronicler
    ```

2.  **Configure Hugging Face Token**
    -   Get your Access Token from [Hugging Face Settings](https://huggingface.co/settings/tokens).
    -   Create/Open `local.properties` in the project root.
    -   Add your token:
        ```properties
        HF_ACCESS_TOKEN=hf_your_token_here
        ```

3.  **Open in Android Studio**
    -   Select **File > Open** and navigate to the project directory.
    -   Allow Gradle to sync.

4.  **Build the Project**
    -   Go to **Build > Make Project**.
    -   Connect your Android device via USB.
    -   Select **Run > Run 'app'**.

## Model Setup

The app uses the **Gemma 3 1B-IT** model.
-   **Automatic Download**: If you provided the `HF_ACCESS_TOKEN` in `local.properties`, the app/test will attempt to download the model automatically upon first run.
-   **Manual Setup**:
    1.  Download `Gemma3-1B-IT_multi-prefill-seq_q8_ekv2048.task` from [Hugging Face](https://huggingface.co/litert-community/Gemma3-1B-IT/tree/main).
    2.  Push it to your device:
        ```bash
        adb push Gemma3-1B-IT_multi-prefill-seq_q8_ekv2048.task /data/local/tmp/
        ```

# 📱 Usage Guide

## 1. Patient Dashboard
View your list of patients. Add new profiles or select an existing one to view their innovative "longitudinal chat" interface.

![Patient Dashboard Mockup](dashboard_mockup.png)

## 2. Multimodal Chat
Enter symptoms or upload an X-ray image.
> **User**: "Analyze this chest X-ray for signs of pneumonia."
> **MedGemma**: "Based on the image opacity in the lower lobes..."

![Chat Interface Mockup](chat_mockup.png)

## 3. X-Ray Analysis Tool
Use the dedicated X-ray tool to highlight specific regions of interest before sending them to the model.

![X-Ray Tool Mockup](xray_tool_mockup.png)

# 📄 License & Acknowledgments

-   **MedGemma**: Copyright Google DeepMind.
-   **MediaPipe**: Apache 2.0 License.
-   This project is a submission for the **Kaggle Google MedGemma Challenge**.
