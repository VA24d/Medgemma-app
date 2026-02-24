# MediPro Chronicler: Feature List

This is a comprehensive feature list for the MediPro Chronicler application, built for the Kaggle Google MedGemma Challenge (Edge Track). The features are organized by user interface flows and backend capabilities.

## 1. Core On-Device AI Capabilities
* **100% Local Inference:** No cloud egress, ensuring strict adherence to healthcare privacy standards (e.g., HIPAA compliance).
* **Multimodal Analysis:** Integrates MedGemma to handle both text inquiries and medical imagery (X-rays, MRIs, Histopathology).
* **Longitudinal Context:** Ability to summarize and generate analyses based on a patient's historical records.

## 2. Theming & Personalization
* **Material UI Themes:** Three options available (White, Black, and Purplish Blue) for accessibility and user preference.
* **Doctor Profile Details:** Captures location and specialty details to seamlessly improve the base prompt passed to MedGemma, ensuring more relevant and tailored analysis.

## 3. High-Fidelity UI/UX & Screens
* **Splash Screen:** Engaging animated logo with a loading ellipses at the bottom.
* **Security lock (PIN Screen):**
  * Configurable PIN entry to secure the application locally.
  * MVP features a seamless zoom transition to the main dashboard.
* **Dashboard (Patients Screen):**
  * Persistent MedGemma branding.
  * Easy-access top bar (quick actions like adding a new patient or running a quick analysis).
  * Smooth sidebar transition morphing the hamburger menu into an 'X'.

## 4. Settings & Sidebar Controls
* **Model Selection:** Switch between different MedGemma model variants (e.g., 2B, 4B, GGUF vs TFLite).
* **Hugging Face Token:** In-app management for secure model downloading.
* **Performance Control:** 
  * *Energy Mode:* Low, Medium, Heavy.
  * *Backend Mode:* CPU, GPU, NPU, or Auto.
* **Data Management & Export:**
  * Bulk storage options (e.g., Delete all patients).
  * **FHIR Export Standard:** Ensure interoperability with professional EMR/EHR systems.
  * Modular encryption options for exports.

## 5. Patient-Specific Workflows
* **Individual Patient View:**
  * Unified name, demographic info, and historical timeline.
* **New Entry Modalities (5 Options):**
  * X-ray / MRI Analysis
  * Histopathology Analysis
  * Recording Analysis
  * Document Analysis (Coming soon)
  * Manual notes entry
* **Longitudinal History Screen:**
  * Chronological feed of past notes, tagged with input type and date.
  * **"Click to Expand" Analysis:** Generate AI-driven insights bridging past and present data at any point.

## 6. Actionable Output
* **Diagnosis & Prognosis Screen:** 
  * Aggregates past images and data to provide a rapid MedGemma-driven prognosis and suggested actions.
* **Patient Sharing (QR Code):** 
  * Allows the clinician to directly and securely share insights, prescriptions, or suggestions with the patient via a generated QR code or other secure local means, avoiding cloud intermediaries.

## 7. Technical Foundation
* **OS:** Android (Kotlin, Jetpack Compose, Modern Android Development).
* **Database:** Room (Local DB for fast, offline access).
* **Inference Pipeline:** MediaPipe LLM Inference / LiteRT.
* **Media Handling:** CameraX for live capture, Coil for image rendering.
