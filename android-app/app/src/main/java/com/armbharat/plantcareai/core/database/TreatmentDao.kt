
package com.armbharat.plantcareai.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TreatmentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTreatment(treatment: TreatmentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTreatments(
        treatments: List<TreatmentEntity>
    )

    @Query("SELECT * FROM treatments ORDER BY nameEn ASC")
    fun getAllTreatments(): Flow<List<TreatmentEntity>>

    @Query("""
        SELECT * FROM treatments
        WHERE classKey = :classKey
        LIMIT 1
    """)
    suspend fun getTreatmentByClassKey(
        classKey: String
    ): TreatmentEntity?

    @Query("""
        SELECT * FROM treatments
        WHERE classKey = :classKey
        AND (regionTag = :region OR regionTag = 'ALL')
        ORDER BY CASE
            WHEN regionTag = :region THEN 0
            ELSE 1
        END
        LIMIT 1
    """)
    suspend fun getTreatmentByRegion(
        classKey: String,
        region: String
    ): TreatmentEntity?

    @Query("SELECT COUNT(*) FROM treatments")
    suspend fun getTreatmentCount(): Int

    @Query("SELECT * FROM treatments LIMIT 10")
    suspend fun getSampleTreatments(): List<TreatmentEntity>
}
