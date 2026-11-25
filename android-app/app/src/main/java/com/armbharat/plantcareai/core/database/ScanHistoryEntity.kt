
package com.armbharat.plantcareai.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_history")
data class ScanHistoryEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val imagePath: String,

    val diseaseKey: String,

    val pestSummary: String = "",

    val severity: String,

    val advisoryText: String = "",

    val language: String = "en",

    val createdAt: Long = System.currentTimeMillis()
)
