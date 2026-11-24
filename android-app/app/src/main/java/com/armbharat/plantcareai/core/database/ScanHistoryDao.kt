
package com.armbharat.plantcareai.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScan(scan: ScanHistoryEntity)

    @Query("""
        SELECT * FROM scan_history
        ORDER BY createdAt DESC
    """)
    fun getAllScans(): Flow<List<ScanHistoryEntity>>

    @Query("""
        SELECT * FROM scan_history
        WHERE id = :scanId
        LIMIT 1
    """)
    suspend fun getScanById(scanId: Int): ScanHistoryEntity?

    @Query("DELETE FROM scan_history WHERE id = :scanId")
    suspend fun deleteScan(scanId: Int)

    @Query("DELETE FROM scan_history")
    suspend fun clearHistory()
}
