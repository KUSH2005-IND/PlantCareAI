
package com.armbharat.plantcareai.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "treatments")
data class TreatmentEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val classKey: String,

    val category: String,

    val nameEn: String,

    val nameHi: String,

    val chemicalDosageEn: String,

    val chemicalDosageHi: String,

    val organicRemedyEn: String,

    val organicRemedyHi: String,

    val preventionEn: String,

    val preventionHi: String,

    val regionTag: String = "ALL"
)

