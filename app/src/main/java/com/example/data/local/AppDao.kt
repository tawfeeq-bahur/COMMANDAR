package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {

    // --- User Queries ---
    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    // --- Status Queries ---
    @Query("SELECT * FROM statuses ORDER BY id ASC")
    fun getAllStatusesFlow(): Flow<List<StatusEntity>>

    @Query("SELECT * FROM statuses ORDER BY id ASC")
    suspend fun getAllStatuses(): List<StatusEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStatus(status: StatusEntity)

    @Delete
    suspend fun deleteStatus(status: StatusEntity)

    @Query("DELETE FROM statuses WHERE id = :id")
    suspend fun deleteStatusById(id: Int)

    // --- Contact Rule Queries ---
    @Query("SELECT * FROM contact_rules ORDER BY id DESC")
    fun getAllContactRulesFlow(): Flow<List<ContactRuleEntity>>

    @Query("SELECT * FROM contact_rules ORDER BY id DESC")
    suspend fun getAllContactRules(): List<ContactRuleEntity>

    @Query("SELECT * FROM contact_rules WHERE contactName = :name LIMIT 1")
    suspend fun getContactRuleForName(name: String): ContactRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContactRule(rule: ContactRuleEntity)

    @Query("DELETE FROM contact_rules WHERE id = :id")
    suspend fun deleteContactRuleById(id: Int)

    // --- Schedule Queries ---
    @Query("SELECT * FROM schedules ORDER BY startTime ASC")
    fun getAllSchedulesFlow(): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedules ORDER BY startTime ASC")
    suspend fun getAllSchedules(): List<ScheduleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(schedule: ScheduleEntity)

    @Query("DELETE FROM schedules WHERE id = :id")
    suspend fun deleteScheduleById(id: Int)

    // --- Reply History Queries ---
    @Query("SELECT * FROM reply_history ORDER BY timestamp DESC")
    fun getAllReplyHistoryFlow(): Flow<List<ReplyHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReplyHistory(history: ReplyHistoryEntity)

    @Query("DELETE FROM reply_history WHERE id = :id")
    suspend fun deleteReplyHistoryById(id: Int)

    @Query("DELETE FROM reply_history")
    suspend fun clearReplyHistory()

    @Query("SELECT * FROM reply_history WHERE contactName = :contactName AND timestamp >= :timestamp")
    suspend fun getRepliesForContactAfter(contactName: String, timestamp: Long): List<ReplyHistoryEntity>

    @Query("SELECT COUNT(*) FROM reply_history WHERE timestamp >= :startOfToday")
    suspend fun getRepliesCountToday(startOfToday: Long): Int

    @Query("SELECT COUNT(*) FROM reply_history WHERE timestamp >= :startOfToday")
    fun getRepliesCountTodayFlow(startOfToday: Long): Flow<Int>

    // --- Settings Queries ---
    @Query("SELECT * FROM settings WHERE id = 1 LIMIT 1")
    fun getSettingsFlow(): Flow<SettingsEntity?>

    @Query("SELECT * FROM settings WHERE id = 1 LIMIT 1")
    suspend fun getSettings(): SettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettings(settings: SettingsEntity)

    // --- Habit Queries ---
    @Query("SELECT * FROM habits ORDER BY id ASC")
    fun getAllHabitsFlow(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits ORDER BY id ASC")
    suspend fun getAllHabits(): List<HabitEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHabit(habit: HabitEntity)

    @Delete
    suspend fun deleteHabit(habit: HabitEntity)

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun deleteHabitById(id: Int)

    // --- Habit Log Queries ---
    @Query("SELECT * FROM habit_logs WHERE dateString = :dateString")
    fun getHabitLogsForDateFlow(dateString: String): Flow<List<HabitLogEntity>>

    @Query("SELECT * FROM habit_logs WHERE dateString = :dateString")
    suspend fun getHabitLogsForDate(dateString: String): List<HabitLogEntity>

    @Query("SELECT * FROM habit_logs ORDER BY dateString DESC")
    fun getAllHabitLogsFlow(): Flow<List<HabitLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHabitLog(log: HabitLogEntity)

    // --- Focus Session Queries ---
    @Query("SELECT * FROM focus_sessions ORDER BY timestamp DESC")
    fun getAllFocusSessionsFlow(): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions ORDER BY timestamp DESC")
    suspend fun getAllFocusSessions(): List<FocusSessionEntity>

    @Query("SELECT * FROM focus_sessions WHERE id = :id LIMIT 1")
    suspend fun getFocusSessionById(id: Int): FocusSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFocusSession(session: FocusSessionEntity): Long

    // --- Forest Cell Queries ---
    @Query("SELECT * FROM forest_cells ORDER BY row ASC, col ASC")
    fun getAllForestCellsFlow(): Flow<List<ForestCellEntity>>

    @Query("SELECT * FROM forest_cells ORDER BY row ASC, col ASC")
    suspend fun getAllForestCells(): List<ForestCellEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertForestCell(cell: ForestCellEntity)

    @Query("DELETE FROM forest_cells")
    suspend fun clearForestCells()
}
