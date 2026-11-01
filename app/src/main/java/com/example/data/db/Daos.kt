package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BotLogDao {
    @Query("SELECT * FROM bot_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 200): Flow<List<BotLogEntity>>

    @Query("SELECT * FROM bot_logs WHERE category = :category ORDER BY timestamp DESC LIMIT :limit")
    fun getLogsByCategory(category: String, limit: Int = 100): Flow<List<BotLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: BotLogEntity): Long

    @Query("DELETE FROM bot_logs")
    suspend fun clearAllLogs()
}

@Dao
interface ErrorReportDao {
    @Query("SELECT * FROM error_reports ORDER BY timestamp DESC")
    fun getAllErrorReports(): Flow<List<ErrorReportEntity>>

    @Query("SELECT COUNT(*) FROM error_reports")
    fun getErrorCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertErrorReport(report: ErrorReportEntity): Long

    @Query("DELETE FROM error_reports")
    suspend fun clearErrorReports()
}
