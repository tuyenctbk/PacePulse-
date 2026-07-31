package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "run_sessions")
data class RunSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val targetSpm: Int,
    val avgDetectedSpm: Int,
    val durationSeconds: Long,
    val totalSteps: Int,
    val soundTypeName: String,
    val accuracyPercentage: Int
)
