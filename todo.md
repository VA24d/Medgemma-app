1. ✅ The Problem
The app actually uses MedGemma 1.5 4B (GGUF files from unsloth/medgemma-1.5-4b-it-GGUF), but old "Gemma 3 1B" naming is still hardcoded in several places — both in user-visible UI strings and in code-level enum names/comments.

📍 All Occurrences (5 files, 7 locations)
1. 🔴 NavigationSidebar.kt — Line 125 (user-visible!)
File: 
app/src/main/java/com/google/mediapipe/examples/llminference/ui/components/NavigationSidebar.kt

kotlin
subtitle = "Gemma 3 1B",   // ← WRONG: should be "MedGemma 1.5 4B"
2. 🟡 Model.kt — Lines 17, 29, 41 (enum names)
File: 
app/src/main/java/com/google/mediapipe/examples/llminference/Model.kt

kotlin
GEMMA3_1B_IT_CPU(        // Line 17
GEMMA_3_1B_IT_GPU(       // Line 29
GEMMA_3_1B_IT_NPU(       // Line 41
3. 🟡 InferenceModel.kt — Line 240 (default model reference)
File: 
app/src/main/java/com/google/mediapipe/examples/llminference/InferenceModel.kt

kotlin
var model: Model = Model.GEMMA_3_1B_IT_GPU
4. 🟡 SelectionScreen.kt — Line 949 (model assignment)
File: 
app/src/main/java/com/google/mediapipe/examples/llminference/SelectionScreen.kt

kotlin
InferenceModel.model = if (useGpu) Model.GEMMA_3_1B_IT_GPU else Model.GEMMA3_1B_IT_CPU
5. 🟠 ConsultationViewModel.kt — Lines 116, 167 (comments only)
File: 
app/src/main/java/com/google/mediapipe/examples/llminference/viewmodel/ConsultationViewModel.kt

kotlin
// Line 116: "Combines MediaPipe Vision image analysis with Gemma 3 text generation"
// Line 167: "Step 3: Generate AI text response with Gemma 3"
📋 Prompt for your agent
Here's a copy-paste-ready prompt:

Task: Rename all old "Gemma 3 1B" references to "MedGemma 1.5 4B" across the codebase. Do NOT change any logic, just naming/strings. Here are the exact changes needed:

File 1: 
app/src/main/java/com/google/mediapipe/examples/llminference/ui/components/NavigationSidebar.kt

Line 125: Change subtitle = "Gemma 3 1B" → subtitle = "MedGemma 1.5 4B"
File 2: 
app/src/main/java/com/google/mediapipe/examples/llminference/Model.kt

Line 17: Rename enum GEMMA3_1B_IT_CPU → MEDGEMMA_4B_IT_CPU
Line 29: Rename enum GEMMA_3_1B_IT_GPU → MEDGEMMA_4B_IT_GPU
Line 41: Rename enum GEMMA_3_1B_IT_NPU → MEDGEMMA_4B_IT_NPU
File 3: 
app/src/main/java/com/google/mediapipe/examples/llminference/InferenceModel.kt

Line 240: Update Model.GEMMA_3_1B_IT_GPU → Model.MEDGEMMA_4B_IT_GPU
File 4: 
app/src/main/java/com/google/mediapipe/examples/llminference/SelectionScreen.kt

Line 949: Update Model.GEMMA_3_1B_IT_GPU → Model.MEDGEMMA_4B_IT_GPU and Model.GEMMA3_1B_IT_CPU → Model.MEDGEMMA_4B_IT_CPU
File 5: 
app/src/main/java/com/google/mediapipe/examples/llminference/viewmodel/ConsultationViewModel.kt

Line 116: Change comment from "Gemma 3" → "MedGemma 1.5 4B"
Line 167: Change comment from "Gemma 3" → "MedGemma 1.5 4B"
Important: These are all naming-only changes. The actual model files loaded (medgemma-1.5-4b-it-Q4_K_M.gguf and mmproj-F16.gguf) are already correct and should NOT be touched.




2. Here's the complete plan and ready-to-use prompt for your agent. The key insight after analyzing the native C++ layer:

The Big Win: KV Cache Reuse
The image embeddings from the first question are already baked into llama.cpp's KV cache. Multi-turn text chat already works this way (chat history stays in KV cache). So for same-image follow-ups:

DON'T re-run the 3-4 min vision encoder pipeline
DO use the text-only prompt path — the KV cache already has the image context
This means a follow-up question about the same X-ray goes from 3-4 minutes → ~10-30 seconds.

Copy-Paste Prompt for Your Agent
Task: Implement vision embedding caching for the MedGemma app to avoid re-computing expensive image embeddings when the same image is referenced multiple times in a chat session.

Problem: Every image question (including follow-ups about the SAME image) re-runs the full vision encoder pipeline (~3-4 minutes). The embeddings from the first image are already in the KV cache but get thrown away.

Solution — 3 changes across 5 files:

1. ChatViewModel.kt — Track a currentSessionImage: Bitmap?. When user sends a text-only follow-up and currentSessionImage is set, automatically pass it forward so the system knows this is a same-image follow-up. Add clearImageContext() method.

2. InferenceModel.kt — Add SHA-256 based image hash tracking (lastImageHash). In generateMultimodalResponse(), if the hash matches the previous image AND the KV cache still has image context (engine.hasActiveImageContext()), use the TEXT-ONLY path (generateTextResponse()) instead of the full multimodal path. This is the key optimization — the KV cache already contains the image embeddings. Also stop immediately deleting temp image files.

3. Native C++ ai_chat.cpp — Add a g_image_in_kv_cache boolean flag. Set it to true after successful processUserPromptWithImage(), set to false on reset_long_term_states(). Expose via new JNI method nativeHasActiveImageContext(). Also add image hash parameter to nativeLoadImage() to skip bitmap reload for same images.

4. InferenceEngine.kt interface — Add fun hasActiveImageContext(): Boolean

5. InferenceEngineImpl.kt — Wire hasActiveImageContext() to native nativeHasActiveImageContext() via JNI

Expected result: First image question = ~3-4 min (unchanged). Follow-up questions about same image = ~10-30 sec (uses text path with KV cache). New different image = ~3-4 min again.

Detailed plan with code snippets: See implementation_plan.md


Vision Embedding Cache — Implementation Plan
The Problem
When a user sends an image in chat and asks a follow-up question about the same image, the app re-runs the entire vision pipeline from scratch (~3–4 minutes):

Write bitmap to temp file → 
loadImage()
 → 
processUserPromptWithImage()
Inside native C++: mtmd_tokenize() + mtmd_helper_eval_chunks() (the slow part — vision encoder transform)
Temp file deleted, bitmap freed
No caching exists at any layer. Every image question pays the full cost.

Architecture Constraint
IMPORTANT

The llama.cpp mtmd API (version b5497) couples vision encoding with KV cache evaluation in mtmd_helper_eval_chunks(). It does NOT expose raw embedding vectors separately. This means we cannot trivially save/restore pre-computed embeddings across sessions.

However, there are two practical optimizations that avoid modifying the low-level mtmd API:

Proposed Changes
Strategy: Two-Tier Same-Image Detection
Tier 1 (Kotlin/App layer): Hash-based image tracking — if the same image is sent again in the same chat, keep the existing g_bitmap loaded and only re-run the prompt part, not the image load.

Tier 2 (Native C++ layer): Keep g_bitmap alive between calls and add a hash check — if the same bitmap is already loaded, skip the expensive mtmd_helper_bitmap_init_from_file() call.

Tier 3 (KV Cache reuse — biggest win): After the first image+prompt eval, the image embeddings are already in the KV cache at known positions. For follow-up questions about the same image, instead of clearing the KV cache and re-encoding the image, reuse the existing KV cache and just append the new text prompt. This is the same approach used for multi-turn text chat (the chat history stays in KV cache) — we just need to ensure image context persists too.

Component 1: Kotlin Layer — Image Hash Tracking
[MODIFY] 
InferenceModel.kt
Add a lastImageHash: String? field to track the hash of the last image bitmap sent

In 
generateMultimodalResponse()
:

Compute SHA-256 hash of the bitmap bytes
Compare with lastImageHash
If SAME image: call a new native method processFollowUpImagePrompt() (text-only prompt that uses existing KV cache with image context)
If DIFFERENT image: call existing 
loadImage()
 + 
processUserPromptWithImage()
 as before
Update lastImageHash after processing
Stop deleting the temp image file immediately — keep it until a different image is sent or the session ends

kotlin
// New fields
private var lastImageHash: String? = null
private var lastImageTempFile: File? = null
private fun bitmapHash(bitmap: Bitmap): String {
    val stream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream)
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(stream.toByteArray()).joinToString("") { "%02x".format(it) }
}
private suspend fun generateMultimodalResponse(
    prompt: String, image: Bitmap, progressListener: ...
): String {
    val hash = bitmapHash(image)
    
    if (hash == lastImageHash) {
        // SAME image — just send follow-up text prompt using existing KV cache
        return generateTextResponse(prompt, progressListener)  // KV cache already has image context
    }
    
    // DIFFERENT image — full pipeline
    lastImageTempFile?.delete()
    val tempImage = writeBitmapToTemp(image)
    lastImageTempFile = tempImage
    val loaded = engine.loadImage(tempImage.absolutePath)
    // ... existing code ...
    lastImageHash = hash
}
[MODIFY] 
ChatViewModel.kt
Track the "current session image" — when user sends a message without an image but the last message in the chat had an image, automatically carry the image context forward so follow-up text questions reference the same image
Add a currentSessionImage: Bitmap? field that persists the last-used image within the chat session
kotlin
private var currentSessionImage: Bitmap? = null
fun sendMessage(userMessage: String, userImages: List<Bitmap>) {
    val effectiveImages = if (userImages.isNotEmpty()) {
        currentSessionImage = userImages.first()
        userImages
    } else if (currentSessionImage != null) {
        // User is asking a follow-up about the same image
        listOf(currentSessionImage!!)
    } else {
        emptyList()
    }
    // Use effectiveImages instead of userImages for the inference call
}
fun clearImageContext() {
    currentSessionImage = null
}
Component 2: Native C++ Layer — Bitmap Caching
[MODIFY] 
ai_chat.cpp
Stop freeing g_bitmap in 
nativeLoadImage()
 — keep the bitmap alive for potential reuse
Add a g_last_image_hash field to store the hash of the currently loaded bitmap
In 
nativeLoadImage()
:
Accept an image hash parameter
If hash matches g_last_image_hash, skip loading (return true immediately)
Otherwise load the new image and update the hash
This saves the mtmd_helper_bitmap_init_from_file() call for same images (small savings, ~100ms)
cpp
static std::string g_last_image_hash;
// Modified nativeLoadImage signature
JNIEXPORT jboolean JNICALL
Java_com_arm_aichat_internal_InferenceEngineImpl_nativeLoadImage(
        JNIEnv *env, jobject, jstring jimage_path, jstring jimage_hash) {
    
    const auto *hash = env->GetStringUTFChars(jimage_hash, nullptr);
    if (g_bitmap && g_last_image_hash == hash) {
        LOGi("Image already loaded (hash match), skipping reload");
        env->ReleaseStringUTFChars(jimage_hash, hash);
        return JNI_TRUE;
    }
    // ... existing load code ...
    g_last_image_hash = hash;
}
Component 3: KV Cache Reuse (The Big Win)
IMPORTANT

This is where the 3-4 minute savings happen.

The key insight: multi-turn text chat already works by keeping the KV cache intact and appending new tokens. The same principle applies to image context.

After the first image+prompt is processed via 
processUserPromptWithImage()
, the image embeddings are baked into the KV cache at positions [current_position_before ... current_position_after]. For follow-up questions:

DON'T call 
processUserPromptWithImage()
 again
DO call 
processUserPrompt()
 (the text-only path) — this appends the new question to the existing KV cache that already contains the image context
[MODIFY] 
InferenceEngine.kt
Add a new method to check if image context is still in KV cache:

kotlin
fun hasActiveImageContext(): Boolean
[MODIFY] 
InferenceEngineImpl.kt
Implement hasActiveImageContext() via JNI
Add a native method nativeHasActiveImageContext()
[MODIFY] 
ai_chat.cpp
cpp
static bool g_image_in_kv_cache = false;
// Set to true after successful processUserPromptWithImage
// Set to false on reset_long_term_states
JNIEXPORT jboolean JNICALL
nativeHasActiveImageContext(JNIEnv*, jobject) {
    return g_image_in_kv_cache ? JNI_TRUE : JNI_FALSE;
}
Flow Summary
User sends Image + "Analyze this X-ray" (FIRST TIME)
  → hash = SHA256(bitmap)
  → lastImageHash = null, so FULL PIPELINE
  → loadImage() → processUserPromptWithImage() → 3-4 min
  → lastImageHash = hash, g_image_in_kv_cache = true
User sends text "What about the right lung?" (FOLLOW-UP)
  → ChatViewModel detects no new image, uses currentSessionImage
  → hash = SHA256(bitmap) → matches lastImageHash
  → g_image_in_kv_cache = true
  → Use TEXT-ONLY path: processUserPrompt() — KV cache still has image context
  → ⚡ ~10-30 seconds instead of 3-4 minutes!
User sends NEW image + "Compare with this one"
  → hash differs from lastImageHash
  → FULL PIPELINE for new image
  → lastImageHash updated
Files Changed Summary
File	Layer	Change
InferenceModel.kt
Kotlin	Add image hash tracking, conditional pipeline bypass
ChatViewModel.kt
Kotlin	Carry forward image context for follow-up messages
InferenceEngine.kt
Kotlin Interface	Add hasActiveImageContext()
InferenceEngineImpl.kt
Kotlin Impl	Wire JNI for new native method
ai_chat.cpp
Native C++	Add g_image_in_kv_cache flag, bitmap hash caching, new JNI method
Verification Plan
Manual Verification (on device)
Build and deploy the app on an Android device
Open chat, send an image with a question → note the time taken (should be ~3-4 min)
Send a follow-up text question about the same image → should be ~10-30 seconds (NO image re-encoding)
Send a DIFFERENT image → should be ~3-4 min again (full pipeline)
Check logcat for: "Image already loaded (hash match)" and "Using text-only path for image follow-up" log messages
Key Risks
KV cache clearing: if 
cleanUp()
 or 
reset_long_term_states()
 is called between messages, the image context is lost. Need to ensure the chat session doesn't unnecessarily reset the KV cache between messages.
The 
shift_context()
 sliding window could evict image tokens if the conversation gets long enough. This is acceptable — the image just needs to be re-encoded at that point.


Found it. Now I can see the bug clearly. Here's the scanning logic (lines 101-120):

kotlin
val localGgufFiles = remember {
    val dirs = listOfNotNull(
        "/storage/emulated/0/Download/medgemma",
        "/storage/emulated/0/Download/MedGemma",
        "/sdcard/Download/medgemma",
        "/sdcard/Download/MedGemma",
        "/storage/emulated/0/Download",
        context.filesDir.absolutePath,
        context.getExternalFilesDir(null)?.absolutePath
    )
    val files = mutableListOf<File>()
    dirs.forEach { dir ->
        val d = File(dir)
        if (d.exists() && d.isDirectory) {
            d.listFiles()?.filter { it.name.endsWith(".gguf") }?.let { files.addAll(it) }
        }
    }
    files.distinctBy { it.absolutePath }
}
Yes, the bug is real. Here's the problem:

🐛 Bug: Duplicate GGUF Files in "Found on Device" List
Root Cause
The scan searches 7 directories that overlap:

Directory	Problem
/storage/emulated/0/Download/medgemma	✅ Specific subfolder
/storage/emulated/0/Download/MedGemma	✅ Case variant (Android is case-sensitive, so separate folder)
/sdcard/Download/medgemma	🔴 SYMLINK — /sdcard is a symlink to /storage/emulated/0 on every modern Android device!
/sdcard/Download/MedGemma	🔴 Same symlink issue
/storage/emulated/0/Download	🔴 Parent folder — also catches files IN the medgemma subfolder if symlinks resolve differently
context.filesDir	✅ App internal storage
context.getExternalFilesDir(null)	✅ App external storage
Why distinctBy { it.absolutePath } Doesn't Fix It
The dedup uses absolutePath, but:

/sdcard/Download/medgemma/model.gguf and /storage/emulated/0/Download/medgemma/model.gguf — these are the same physical file but have different absolutePath strings because /sdcard is a symlink. Android's File.absolutePath does NOT resolve symlinks (that would require canonicalPath).
Additionally, /storage/emulated/0/Download scans ALL .gguf files in the root Download folder, which could overlap with files already found in the medgemma subfolder if the user places files directly there.
Result
The same .gguf file shows up 2x (or even 3x) in the "Found on Device" list — once from each symlink path.

📋 Prompt for Your Agent
Task: Fix duplicate model entries in the "Found on Device" list in 
SelectionScreen.kt
.

File: 
app/src/main/java/com/google/mediapipe/examples/llminference/SelectionScreen.kt

Problem: Lines 101-120 scan 7 directories for .gguf files, but several directories are symlinks to the same physical location (/sdcard → /storage/emulated/0). The dedup at line 119 uses absolutePath which does NOT resolve symlinks, so the same file appears multiple times.

Fix (line 119): Change files.distinctBy { it.absolutePath } to files.distinctBy { it.canonicalPath }. The canonicalPath resolves symlinks, so /sdcard/Download/medgemma/model.gguf and /storage/emulated/0/Download/medgemma/model.gguf will be recognized as the same file.

Also consider: Remove the redundant /sdcard/ paths entirely (lines 106-107) since /sdcard is always a symlink to /storage/emulated/0 on modern Android. This simplifies the directory list and avoids scanning the same folder twice.

Optional additional improvement: The scan also searches the parent folder /storage/emulated/0/Download (line 108) which could find .gguf files that aren't in the medgemma subfolder. This is probably intentional (catching loosely placed downloads), but if a file exists at both /storage/emulated/0/Download/model.gguf and was copied to context.filesDir/model.gguf, both would show up since they have different canonical paths. Consider deduping by file.name instead of path if you want unique model names in the list.

No logic changes needed — just the one-line fix at line 119, and optionally cleaning up the redundant symlink paths.
