package com.google.mediapipe.examples.llminference.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PatientEntity::class,
        MedicalImageEntity::class,
        ConsultationEntity::class,
        MedicalEntryEntity::class,
        DiagnosisEntity::class
    ],
    version = 5,
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

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE medical_entries ADD COLUMN visitSummary TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE medical_entries ADD COLUMN cloudProcessedAt INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        fun getDatabase(context: Context): MedicalDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MedicalDatabase::class.java,
                    "medical_database"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
