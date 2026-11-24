
package com.armbharat.plantcareai.core.database

import android.content.Context
import androidx.room.Room

object DatabaseProvider {

    @Volatile
    private var INSTANCE: PlantCareDatabase? = null

    fun getDatabase(context: Context): PlantCareDatabase {

        return INSTANCE ?: synchronized(this) {

            val instance = Room.databaseBuilder(
                context.applicationContext,
                PlantCareDatabase::class.java,
                "plantcare_database"
            )
                .build()

            INSTANCE = instance
            instance
        }
    }
}
