package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RunSessionDao {
    @Query("SELECT * FROM run_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<RunSession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: RunSession): Long

    @Delete
    suspend fun deleteSession(session: RunSession)

    @Query("DELETE FROM run_sessions")
    suspend fun deleteAllSessions()

    @Query("SELECT COUNT(*) FROM run_sessions")
    fun getSessionCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM run_sessions")
    suspend fun getSessionCountValue(): Int

    @Query("SELECT SUM(totalSteps) FROM run_sessions")
    fun getTotalStepsCount(): Flow<Int?>

    @Query("SELECT SUM(durationSeconds) FROM run_sessions")
    fun getTotalDurationSeconds(): Flow<Long?>
}
