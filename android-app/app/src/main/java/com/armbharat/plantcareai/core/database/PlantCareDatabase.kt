
package com.armbharat.plantcareai.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        TreatmentEntity::class,
        ScanHistoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class PlantCareDatabase : RoomDatabase() {

    abstract fun treatmentDao(): TreatmentDao

    abstract fun scanHistoryDao(): ScanHistoryDao
}
