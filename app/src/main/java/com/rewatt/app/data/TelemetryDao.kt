package com.rewatt.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TelemetryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: TelemetryEntity)

    @Query("SELECT * FROM telemetry_readings ORDER BY capturedAtMillis DESC LIMIT 1")
    fun latestReading(): Flow<TelemetryEntity?>

    @Query("SELECT * FROM telemetry_readings ORDER BY capturedAtMillis DESC LIMIT :limit")
    fun recentReadings(limit: Int): Flow<List<TelemetryEntity>>
}

