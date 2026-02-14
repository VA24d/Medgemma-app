package com.google.mediapipe.examples.llminference.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.mediapipe.examples.llminference.data.MedicalImageDao
import com.google.mediapipe.examples.llminference.data.MedicalImageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.*

class MedicalImageRepository(
    private val medicalImageDao: MedicalImageDao,
    private val context: Context
) {
    private val imagesDir by lazy {
        File(context.filesDir, "medical_images").apply { mkdirs() }
    }

    fun getImagesForPatient(patientId: Long): Flow<List<MedicalImageEntity>> {
        return medicalImageDao.getImagesForPatient(patientId)
    }

    fun getImagesByType(patientId: Long, imageType: String): Flow<List<MedicalImageEntity>> {
        return medicalImageDao.getImagesByType(patientId, imageType)
    }

    suspend fun getImage(imageId: Long): MedicalImageEntity? {
        return medicalImageDao.getImage(imageId)
    }

    suspend fun saveImage(
        patientId: Long,
        imageType: String,
        bitmap: Bitmap,
        bodyPart: String = "",
        notes: String = ""
    ): Long = withContext(Dispatchers.IO) {
        // Generate unique filename
        val fileName = "img_${UUID.randomUUID()}.jpg"
        val imageFile = File(imagesDir, fileName)

        // Save bitmap to file
        FileOutputStream(imageFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }

        // Create database entry
        val entity = MedicalImageEntity(
            patientId = patientId,
            imageType = imageType,
            filePath = imageFile.absolutePath,
            fileName = fileName,
            bodyPart = bodyPart,
            notes = notes
        )

        medicalImageDao.insertImage(entity)
    }

    suspend fun loadBitmap(imagePath: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            if (File(imagePath).exists()) {
                BitmapFactory.decodeFile(imagePath)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun updateImage(image: MedicalImageEntity) {
        medicalImageDao.updateImage(image)
    }

    suspend fun deleteImage(image: MedicalImageEntity) {
        // Delete file from filesystem
        File(image.filePath).delete()
        // Delete database entry
        medicalImageDao.deleteImage(image)
    }

    suspend fun deleteAllImagesForPatient(patientId: Long) = withContext(Dispatchers.IO) {
        // Get the flow and collect images first
        val allImages = mutableListOf<MedicalImageEntity>()
        medicalImageDao.getImagesForPatient(patientId).collect { images ->
            allImages.addAll(images)
        }
        
        // Delete files from filesystem
        allImages.forEach { image ->
            File(image.filePath).delete()
        }
        
        // Delete database entries
        medicalImageDao.deleteAllImagesForPatient(patientId)
    }
}
