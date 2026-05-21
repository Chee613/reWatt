package com.rewatt.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TelemetryEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class ReWattDatabase : RoomDatabase() {
    abstract fun telemetryDao(): TelemetryDao

    companion object {
        fun create(context: Context): ReWattDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                ReWattDatabase::class.java,
                "rewatt.db",
            )
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
