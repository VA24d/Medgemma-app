package com.google.mediapipe.examples.llminference.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        PatientEntity::class,
        MedicalImageEntity::class,
        ConsultationEntity::class,
        MedicalEntryEntity::class,
        DiagnosisEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class MedicalDatabase : RoomDatabase() {
    abstract fun patientDao(): PatientDao
    abstract fun medicalImageDao(): MedicalImageDao
    abstract fun consultationDao(): ConsultationDao
    abstract fun medicalEntryDao(): MedicalEntryDao
    abstract fun diagnosisDao(): DiagnosisDao

    companion object {
        @Volatile
        private var INSTANCE: MedicalDatabase? = null

        fun getDatabase(context: Context): MedicalDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MedicalDatabase::class.java,
                    "medical_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
