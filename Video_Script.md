# MedVed: Video Demonstration Script (Max 3 Minutes)
*This script is designed to be punchy, highly visual, and directly address the "Edge of AI" prize criteria (No Data Egress, Clinician Persona, Material Design UI, Multi-Modal).*

---

**[0:00 - 0:15] INTRODUCTION (The Problem & The Persona)**
* **Visual:** Split screen. Left side: A chaotic, busy rural Indian clinic (stock footage or B-roll). Right side: A close-up of a doctor looking frustrated at a spinning "Loading Cloud Data..." icon on an old computer.
* **Voiceover (Confident, Professional):** "In rural clinics and hyper-compliant metro hospitals alike, healthcare workers face two massive barriers to AI adoption: unreliable internet and strict data privacy regulations. Cloud delays and data egress aren't just inconveniences—they are critical blockers to patient care."

**[0:15 - 0:35] THE SOLUTION (MedVed & Hardware Showcase)**
* **Visual:** Hard cut to a sleek, modern smartphone (or the Qualcomm Innovator Development Kit) running the MedVed app. Show the clean, Google Material Design 3 interface. The user securely logs in using a PIN/Biometric prompt.
* **Voiceover:** "Meet MedVed. An intuitive, secure, zero-latency clinical co-pilot built entirely for the edge. Powered by Google's MedGemma 1.5 4B Multimodal model running fully on-device via a highly optimized GGUF backend, MedVed ensures absolutely no patient data ever leaves the hardware."

**[0:36 - 1:15] DEMO 1: AMBIENT VOICE & LONGITUDINAL ANALYSIS**
* **Visual:** Screen recording of the app. The user taps the microphone icon. A waveform appears. 
* **Text on Screen:** *Ambient Clinical Dictation (MedASR)*
* **Voiceover:** "Watch as MedVed handles complex pharmacological and clinical dictation flawlessly in real-time, even completely offline."
* **Visual:** The dictated text is instantly summarized by MedGemma into a structured SOAP note. The user taps a previous date on a visual timeline.
* **Voiceover:** "Through its encrypted local SQLite database, clinicians can instantly query years of longitudinal patient history, cross-referencing past symptoms with today's presentation in milliseconds."

**[1:16 - 2:00] DEMO 2: MULTIMODAL VISION (SigLIP in Action)**
* **Visual:** The user taps the camera icon and snaps a photo of a Chest X-Ray (or histopathology slide) displayed on a monitor.
* **Text on Screen:** *On-Device Radiographic Analysis (SigLIP Encoder)*
* **Voiceover:** "But MedVed goes beyond text. By unlocking the MedGemma SigLIP encoder locally, physicians can process complex radiographic or histopathological imagery directly on the device. Notice how the system deliberately highlights critical *negative* signs—a crucial nuance for building clinical trust."
* **Visual:** The app outputs the visual analysis, clearly stating "No evidence of pleural effusion."

**[2:01 - 2:30] UNDER THE HOOD (Hardware & Real-World Resilience)**
* **Visual:** Quick montage of the settings screen: showing the hot-swappable model options (Q4_K_M selected), the "Energy Mode" toggle, and a "Background Download" toast notification.
* **Voiceover:** "We built MedVed for the real world. It features background model payloads that download only on Wi-Fi, dynamic hardware routing to prevent thermal throttling during long shifts, and smart vernacular renaming to translate elite clinical terms into local dialects for patient handoffs."

**[2:31 - 3:00] THE EXPORT & CONCLUSION**
* **Visual:** The user taps "Export." A secure QR code generates on the screen alongside standard FHIR JSON format text.
* **Voiceover:** "Finally, while processing is local, data isn't siloed. MedVed synthesizes the interaction into standard FHIR formats and secure AES-256 QR codes for instant, air-gapped handoffs to hospital networks."
* **Visual:** Clean outro screen. The MedVed logo, Kaggle Logo, and "Built with Google HAI-DEF."
* **Voiceover:** "MedVed: Securing the future of medical intelligence, right at the edge."
