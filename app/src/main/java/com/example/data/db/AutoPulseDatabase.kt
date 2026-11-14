package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [AudioTrackEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class AutoPulseDatabase : RoomDatabase() {
    abstract fun audioTrackDao(): AudioTrackDao

    companion object {
        @Volatile
        private var INSTANCE: AutoPulseDatabase? = null

        fun getInstance(context: Context): AutoPulseDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AutoPulseDatabase::class.java,
                    "autopulse_headunit.db",
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
