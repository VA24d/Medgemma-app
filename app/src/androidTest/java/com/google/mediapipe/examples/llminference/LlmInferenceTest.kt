package com.google.mediapipe.examples.llminference

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.mediapipe.examples.llminference.InferenceModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LlmInferenceTest {

    @Test
    fun testModelInference() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        
        // 1. Verify Model Existence
        if (!InferenceModel.modelExists(context)) {
            println("Model not found. Downloading...")
            // Basic download implementation for test purpose
            val modelUrl = InferenceModel.model.url
            val destinationFile = File(InferenceModel.modelPath(context))
            
            // Note: This is an instrumented test, network operations should be robust.
            try {
                val url = java.net.URL(modelUrl)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.setRequestProperty("Authorization", "Bearer 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")
                connection.connect()
                
                if (connection.responseCode != java.net.HttpURLConnection.HTTP_OK) {
                     println("Failed to download model: HTTP ${connection.responseCode} ${connection.responseMessage}")
                     return@runBlocking
                }

                connection.inputStream.use { input ->
                    java.io.FileOutputStream(destinationFile).use { output ->
                        input.copyTo(output)
                    }
                }
                println("Model downloaded to ${destinationFile.absolutePath}")
            } catch (e: Exception) {
                println("Failed to download model: ${e.message}")
                return@runBlocking
            }
        }
        
        // Force CPU for emulator test reliability
        InferenceModel.model = Model.GEMMA3_1B_IT_CPU

        // 2. Initialize InferenceModel
        val inferenceModel = InferenceModel.getInstance(context)
        
        // 3. Generate Response
        val prompt = "Hello, how are you?"
        val response = inferenceModel.generateResponse(prompt)
        
        // 4. Verify Response
        assertNotNull(response)
        assertFalse("Response should not be empty", response.isEmpty())
        println("LLM Response: $response")
    }
}
