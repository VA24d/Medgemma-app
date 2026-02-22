# Bhaskar GGUF Integration Plan

## Goal
Deliver **friend's UI** (Medgemma-app) with **your working GGUF multimodal backend** (QGemma `AiChat` JNI + llama.cpp + mmproj + image pipeline).

## Current State Summary
- UI app (`Medgemma-app`) has complete Compose UI + flows for patients/chat/analysis.
- Backend in `Medgemma-app/main` is MediaPipe `LlmInference` (`.task`) and ignores true multimodal GGUF flow.
- Your backend in `qgemma/android/lib` already supports:
  - GGUF model loading
  - mmproj loading
  - image loading
  - multimodal prompt processing (image + text in one context)
  - token streaming

## Branches Analyzed
- `main`: UI complete and stable base.
- `gguf`: closest to target backend direction (`+2` commits vs main), but still mixed with TFLite vision/projector config and external submodule assumptions.
- `tflite-conversion`: heavily coupled to LiteRT/TFLite (`+19` commits), not preferred for GGUF target.

## Selected Base
- Use `Medgemma-app` branch `bhaskar-gguf` (created from `main`) as integration branch.

## Integration Strategy (Recommended)

### Phase 1 — Backend module import (from your app)
1. Import `qgemma/android/lib` into `Medgemma-app` as a local module (e.g., `:aichatlib`).
2. Wire `settings.gradle.kts` to include the module.
3. Add app dependency on module in `app/build.gradle.kts`.
4. Keep JNI/CMake settings from your backend module unchanged initially.

### Phase 2 — Replace inference bridge only (keep UI unchanged)
1. Replace `InferenceModel.kt` implementation internals to call `com.arm.aichat.AiChat` engine.
2. Preserve existing UI-facing API so other screens stay unchanged:
   - `generateResponseAsync(prompt, images, progressListener)`
   - `generateResponse(prompt)`
   - `modelExists(...)`
   - `modelPath(...)`
3. Add multimodal path in `InferenceModel`:
   - if `images` not empty: save first image to cache -> `loadImage(...)` -> `sendImagePrompt(...)`
   - if no image: `sendUserPrompt(...)`
4. Keep message streaming callback behavior exactly as expected by `ChatViewModel`.

### Phase 3 — Model + mmproj resolution policy
1. Add GGUF model enum in `Model.kt` for MedGemma GGUF files.
2. Resolve paths with priority:
   - `/sdcard/Download/MedGemma/`
   - app-internal files dir
3. Resolve mmproj path similarly; fail fast with clear UI error if missing.
4. Keep Selection/Loading screens mostly as-is but update copy/messages for GGUF + mmproj requirements.

### Phase 4 — Backend lifecycle + stability
1. Ensure one engine instance lifecycle mirrors current `InferenceModel` singleton.
2. Ensure no duplicate model loads.
3. Free multimodal resources on close/reset where safe.
4. Keep token generation cancellation behavior compatible with current ViewModel flow.

### Phase 5 — Validation
1. Build `Medgemma-app` debug APK.
2. Install on device.
3. Validate text-only prompt.
4. Validate multimodal prompt with sample image.
5. Confirm logs show:
   - model loaded
   - mmproj loaded
   - image loaded
   - multimodal prompt processed
   - token streaming works

## File-level Change Map (Planned)

### High impact
- `Medgemma-app/settings.gradle.kts`
- `Medgemma-app/app/build.gradle.kts`
- `Medgemma-app/app/src/main/java/com/google/mediapipe/examples/llminference/InferenceModel.kt`
- `Medgemma-app/app/src/main/java/com/google/mediapipe/examples/llminference/Model.kt`
- `Medgemma-app/app/src/main/java/com/google/mediapipe/examples/llminference/LoadingScreen.kt`
- `Medgemma-app/app/src/main/java/com/google/mediapipe/examples/llminference/SelectionScreen.kt`

### Imported module
- `Medgemma-app/aichatlib/**` (from `qgemma/android/lib/**`)

## Feasibility
- **Feasible** with medium integration effort.
- Lowest-risk route is **retain all UI contracts** and swap only `InferenceModel` internals to your backend.
- Biggest technical risk is module/CMake integration portability; this is manageable with staged build checks.

## Acceptance Criteria
- App runs with existing friend UI routes/screens.
- Inference path uses your `AiChat` JNI backend (not MediaPipe `LlmInference` nor LiteRT).
- Text + image prompts both work.
- Responses stream into existing chat UI without regressions.
- Build/install succeeds on your current Android device setup.

## Execution Order in This Branch
1. Module import + Gradle wiring
2. InferenceModel swap
3. Model/path policy
4. UI copy/tips update for GGUF/mmproj
5. Build and device test
