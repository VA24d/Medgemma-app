package com.google.mediapipe.examples.llminference.vision

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.imageclassifier.ImageClassifier
import com.google.mediapipe.tasks.vision.imageclassifier.ImageClassifierResult
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectorResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Medical Image Analyzer using MediaPipe Vision
 * Performs image classification and object detection on medical images
 */
class MedicalImageAnalyzer(private val context: Context) {

    private var imageClassifier: ImageClassifier? = null
    private var objectDetector: ObjectDetector? = null

    /**
     * Initialize the image classifier with a medical model
     * Note: You'll need to add a trained medical image classification model to assets
     */
    fun initializeClassifier(modelPath: String = "efficientnet_lite0.tflite") {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(modelPath)
                .build()

            val options = ImageClassifier.ImageClassifierOptions.builder()
                .setBaseOptions(baseOptions)
                .setMaxResults(5) // Top 5 classifications
                .setScoreThreshold(0.3f) // 30% confidence threshold
                .build()

            imageClassifier = ImageClassifier.createFromOptions(context, options)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Initialize object detector for detecting anomalies in medical images
     */
    fun initializeObjectDetector(modelPath: String = "efficientdet_lite0.tflite") {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(modelPath)
                .build()

            val options = ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(baseOptions)
                .setMaxResults(10)
                .setScoreThreshold(0.4f)
                .build()

            objectDetector = ObjectDetector.createFromOptions(context, options)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Analyze medical image for classification
     * Returns structured findings that can be passed to LLM
     */
    suspend fun classifyMedicalImage(bitmap: Bitmap): MedicalImageAnalysis = withContext(Dispatchers.Default) {
        try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val result = imageClassifier?.classify(mpImage)
            
            result?.let {
                parseMedicalClassification(it)
            } ?: MedicalImageAnalysis(
                error = "Image classifier not initialized"
            )
        } catch (e: Exception) {
            MedicalImageAnalysis(error = e.message ?: "Classification failed")
        }
    }

    /**
     * Detect objects/anomalies in medical image
     */
    suspend fun detectAnomalies(bitmap: Bitmap): MedicalImageAnalysis = withContext(Dispatchers.Default) {
        try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val result = objectDetector?.detect(mpImage)
            
            result?.let {
                parseObjectDetection(it)
            } ?: MedicalImageAnalysis(
                error = "Object detector not initialized"
            )
        } catch (e: Exception) {
            MedicalImageAnalysis(error = e.message ?: "Detection failed")
        }
    }

    /**
     * Comprehensive analysis combining classification and detection
     */
    suspend fun analyzeImage(bitmap: Bitmap): MedicalImageAnalysis {
        val classification = classifyMedicalImage(bitmap)
        val detection = detectAnomalies(bitmap)
        
        return MedicalImageAnalysis(
            classifications = classification.classifications + detection.classifications,
            detections = detection.detections,
            confidenceScore = (classification.confidenceScore + detection.confidenceScore) / 2,
            findings = "${classification.findings}\n${detection.findings}".trim()
        )
    }

    private fun parseMedicalClassification(result: ImageClassifierResult): MedicalImageAnalysis {
        val classifications = mutableListOf<String>()
        val findings = StringBuilder()
        var totalConfidence = 0f

        result.classificationResult().classifications().firstOrNull()?.categories()?.forEach { category ->
            val label = category.categoryName()
            val score = category.score()
            
            classifications.add("$label (${(score * 100).toInt()}%)")
            findings.append("- $label: ${(score * 100).toInt()}% confidence\n")
            totalConfidence += score
        }

        return MedicalImageAnalysis(
            classifications = classifications,
            confidenceScore = if (classifications.isNotEmpty()) totalConfidence / classifications.size else 0f,
            findings = findings.toString()
        )
    }

    private fun parseObjectDetection(result: ObjectDetectorResult): MedicalImageAnalysis {
        val detections = mutableListOf<String>()
        val findings = StringBuilder()
        var totalConfidence = 0f

        result.detections().forEach { detection ->
            val label = detection.categories().firstOrNull()?.categoryName() ?: "Unknown"
            val score = detection.categories().firstOrNull()?.score() ?: 0f
            val boundingBox = detection.boundingBox()
            
            detections.add("$label at [${boundingBox.left},${boundingBox.top},${boundingBox.right},${boundingBox.bottom}]")
            findings.append("- Detected $label (${(score * 100).toInt()}% confidence) at location (${boundingBox.centerX()}, ${boundingBox.centerY()})\n")
            totalConfidence += score
        }

        return MedicalImageAnalysis(
            detections = detections,
            confidenceScore = if (detections.isNotEmpty()) totalConfidence / detections.size else 0f,
            findings = findings.toString()
        )
    }

    fun close() {
        imageClassifier?.close()
        objectDetector?.close()
        imageClassifier = null
        objectDetector = null
    }
}

/**
 * Structured analysis result from MediaPipe Vision
 * Can be converted to text for LLM input
 */
data class MedicalImageAnalysis(
    val classifications: List<String> = emptyList(),
    val detections: List<String> = emptyList(),
    val confidenceScore: Float = 0f,
    val findings: String = "",
    val error: String? = null
) {
    /**
     * Convert to human-readable text for LLM prompts
     */
    fun toPromptText(): String {
        if (error != null) return "Image analysis error: $error"
        
        return buildString {
            if (classifications.isNotEmpty()) {
                appendLine("Image Classifications:")
                classifications.forEach { appendLine("  - $it") }
            }
            
            if (detections.isNotEmpty()) {
                appendLine("\nDetected Objects/Anomalies:")
                detections.forEach { appendLine("  - $it") }
            }
            
            if (findings.isNotEmpty()) {
                appendLine("\nDetailed Findings:")
                appendLine(findings)
            }
            
            appendLine("\nOverall Confidence: ${(confidenceScore * 100).toInt()}%")
        }.trim()
    }
    
    val hasResults: Boolean
        get() = classifications.isNotEmpty() || detections.isNotEmpty()
}
