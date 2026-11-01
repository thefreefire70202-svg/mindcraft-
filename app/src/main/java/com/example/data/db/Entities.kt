package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.LogCategory
import com.example.data.model.LogLevel

@Entity(tableName = "bot_logs")
data class BotLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val level: String = LogLevel.INFO.name,
    val category: String = LogCategory.SYSTEM.name,
    val message: String,
    val details: String? = null
)

@Entity(tableName = "error_reports")
data class ErrorReportEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val errorType: String,
    val errorMessage: String,
    val stackTraceSnippet: String,
    val botCoordinates: String,
    val currentGoal: String,
    val recoveryStrategyApplied: String,
    val isResolved: Boolean = true,
    val retryAttemptCount: Int = 1
)
