package com.example.data

import android.content.Context
import com.example.data.local.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class AppRepository(private val appDao: AppDao) {

    val statusesFlow: Flow<List<StatusEntity>> = appDao.getAllStatusesFlow()
    val rulesFlow: Flow<List<ContactRuleEntity>> = appDao.getAllContactRulesFlow()
    val schedulesFlow: Flow<List<ScheduleEntity>> = appDao.getAllSchedulesFlow()
    val historyFlow: Flow<List<ReplyHistoryEntity>> = appDao.getAllReplyHistoryFlow()
    val settingsFlow: Flow<SettingsEntity?> = appDao.getSettingsFlow()
    val habitsFlow: Flow<List<HabitEntity>> = appDao.getAllHabitsFlow()
    val allFocusSessionsFlow: Flow<List<FocusSessionEntity>> = appDao.getAllFocusSessionsFlow()
    val forestCellsFlow: Flow<List<ForestCellEntity>> = appDao.getAllForestCellsFlow()

    fun getRepliesCountTodayFlow(startOfToday: Long): Flow<Int> =
        appDao.getRepliesCountTodayFlow(startOfToday)

    suspend fun getRepliesCountToday(startOfToday: Long): Int = withContext(Dispatchers.IO) {
        appDao.getRepliesCountToday(startOfToday)
    }

    suspend fun initializeDefaults() = withContext(Dispatchers.IO) {
        // Pre-populate Settings if empty
        val currentSettings = appDao.getSettings()
        if (currentSettings == null) {
            appDao.insertSettings(
                SettingsEntity(
                    id = 1,
                    isAutoReplyEnabled = true,
                    themeMode = "SYSTEM",
                    autoReplyDelayMinutes = 5,
                    maxRepliesPerContact = 3,
                    activeStatusName = "Study",
                    currentXp = 2450,
                    userLevel = 12,
                    activeFocusSessionId = 0
                )
            )
        }

        // Pre-populate Statuses if empty
        val existingStatuses = appDao.getAllStatuses()
        if (existingStatuses.isEmpty()) {
            val defaultStatuses = listOf(
                StatusEntity(name = "Study", replyMessage = "I am currently studying. I will reply later.", isCustom = false, iconName = "School"),
                StatusEntity(name = "Gym", replyMessage = "I am currently at the gym. I will respond once I finish.", isCustom = false, iconName = "Fitness"),
                StatusEntity(name = "Prayer", replyMessage = "I am currently praying. I will reply shortly.", isCustom = false, iconName = "SelfImprovement"),
                StatusEntity(name = "Meeting", replyMessage = "I am currently in a meeting. I will get back to you soon.", isCustom = false, iconName = "MeetingRoom"),
                StatusEntity(name = "Sleep", replyMessage = "I am sleeping right now. I will respond when I wake up.", isCustom = false, iconName = "Bedtime"),
                StatusEntity(name = "Driving", replyMessage = "I am currently driving. I cannot respond immediately.", isCustom = false, iconName = "DirectionsCar"),
                StatusEntity(name = "Work", replyMessage = "Hi, I am currently working on my FleetFlow project. I will respond once I am available.", isCustom = true, iconName = "Work")
            )
            for (status in defaultStatuses) {
                appDao.insertStatus(status)
            }
        }

        // Pre-populate Rules if empty
        val existingRules = appDao.getAllContactRules()
        if (existingRules.isEmpty()) {
            val defaultRules = listOf(
                ContactRuleEntity(contactName = "Mom", action = "NO_REPLY"),
                ContactRuleEntity(contactName = "Dad", action = "PRIORITY"),
                ContactRuleEntity(contactName = "Unknown Numbers", action = "AUTO_REPLY")
            )
            for (rule in defaultRules) {
                appDao.insertContactRule(rule)
            }
        }

        // Pre-populate Schedules if empty
        val existingSchedules = appDao.getAllSchedules()
        if (existingSchedules.isEmpty()) {
            val defaultSchedules = listOf(
                ScheduleEntity(startTime = "05:30", endTime = "07:00", modeName = "Gym", isEnabled = true),
                ScheduleEntity(startTime = "09:00", endTime = "17:00", modeName = "Study", isEnabled = true),
                ScheduleEntity(startTime = "17:30", endTime = "18:00", modeName = "Prayer", isEnabled = true),
                ScheduleEntity(startTime = "20:00", endTime = "22:00", modeName = "Study", isEnabled = true)
            )
            for (sch in defaultSchedules) {
                appDao.insertSchedule(sch)
            }
        }

        // Pre-populate Habits if empty
        val existingHabits = appDao.getAllHabits()
        if (existingHabits.isEmpty()) {
            val defaultHabits = listOf(
                HabitEntity(name = "Gym", iconName = "Dumbbell", streakDays = 12),
                HabitEntity(name = "Minoxidil", iconName = "Medication", streakDays = 27),
                HabitEntity(name = "Read 20 mins", iconName = "Book", streakDays = 7),
                HabitEntity(name = "Fajr", iconName = "SelfImprovement", streakDays = 112),
                HabitEntity(name = "Dhuhr", iconName = "SelfImprovement", streakDays = 112),
                HabitEntity(name = "Coding Practice", iconName = "Code", streakDays = 18)
            )
            for (habit in defaultHabits) {
                appDao.insertHabit(habit)
            }
        }

        // Pre-populate Forest cells if empty (5x5 grid)
        val existingCells = appDao.getAllForestCells()
        if (existingCells.isEmpty()) {
            for (r in 0 until 5) {
                for (c in 0 until 5) {
                    appDao.insertForestCell(ForestCellEntity(row = r, col = c, cellType = "EMPTY"))
                }
            }
        }
    }

    // --- User Profile ---
    suspend fun getUserByEmail(email: String): UserEntity? = withContext(Dispatchers.IO) {
        appDao.getUserByEmail(email)
    }

    suspend fun registerUser(user: UserEntity) = withContext(Dispatchers.IO) {
        appDao.insertUser(user)
    }

    // --- Status ---
    suspend fun saveStatus(status: StatusEntity) = withContext(Dispatchers.IO) {
        appDao.insertStatus(status)
    }

    suspend fun deleteStatusById(id: Int) = withContext(Dispatchers.IO) {
        appDao.deleteStatusById(id)
    }

    suspend fun updateActiveStatus(statusName: String) = withContext(Dispatchers.IO) {
        val current = appDao.getSettings() ?: SettingsEntity()
        appDao.insertSettings(current.copy(activeStatusName = statusName))
    }

    // --- Rules ---
    suspend fun saveContactRule(rule: ContactRuleEntity) = withContext(Dispatchers.IO) {
        appDao.insertContactRule(rule)
    }

    suspend fun deleteContactRule(id: Int) = withContext(Dispatchers.IO) {
        appDao.deleteContactRuleById(id)
    }

    suspend fun getContactRuleForName(name: String): ContactRuleEntity? = withContext(Dispatchers.IO) {
        appDao.getContactRuleForName(name)
    }

    // --- Schedules ---
    suspend fun saveSchedule(schedule: ScheduleEntity) = withContext(Dispatchers.IO) {
        appDao.insertSchedule(schedule)
    }

    suspend fun deleteSchedule(id: Int) = withContext(Dispatchers.IO) {
        appDao.deleteScheduleById(id)
    }

    // --- History ---
    suspend fun addHistoryEntry(history: ReplyHistoryEntity) = withContext(Dispatchers.IO) {
        appDao.insertReplyHistory(history)
    }

    suspend fun deleteHistoryEntry(id: Int) = withContext(Dispatchers.IO) {
        appDao.deleteReplyHistoryById(id)
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        appDao.clearReplyHistory()
    }

    suspend fun getRepliesForContactAfter(contactName: String, timestamp: Long): List<ReplyHistoryEntity> =
        withContext(Dispatchers.IO) {
            appDao.getRepliesForContactAfter(contactName, timestamp)
        }

    // --- Settings ---
    suspend fun saveSettings(settings: SettingsEntity) = withContext(Dispatchers.IO) {
        appDao.insertSettings(settings)
    }

    suspend fun getSettings(): SettingsEntity? = withContext(Dispatchers.IO) {
        appDao.getSettings()
    }

    // --- Habits ---
    suspend fun saveHabit(habit: HabitEntity) = withContext(Dispatchers.IO) {
        appDao.insertHabit(habit)
    }

    suspend fun deleteHabit(id: Int) = withContext(Dispatchers.IO) {
        appDao.deleteHabitById(id)
    }

    // --- Habit Logs ---
    suspend fun saveHabitLog(log: HabitLogEntity) = withContext(Dispatchers.IO) {
        appDao.insertHabitLog(log)
    }

    suspend fun getHabitLogsForDate(dateString: String): List<HabitLogEntity> = withContext(Dispatchers.IO) {
        appDao.getHabitLogsForDate(dateString)
    }

    fun getHabitLogsForDateFlow(dateString: String): Flow<List<HabitLogEntity>> {
        return appDao.getHabitLogsForDateFlow(dateString)
    }

    // --- Focus Sessions ---
    suspend fun saveFocusSession(session: FocusSessionEntity): Long = withContext(Dispatchers.IO) {
        appDao.insertFocusSession(session)
    }

    suspend fun getFocusSessionById(id: Int): FocusSessionEntity? = withContext(Dispatchers.IO) {
        appDao.getFocusSessionById(id)
    }

    // --- Forest Cells ---
    suspend fun saveForestCell(cell: ForestCellEntity) = withContext(Dispatchers.IO) {
        appDao.insertForestCell(cell)
    }

    suspend fun clearForestCells() = withContext(Dispatchers.IO) {
        appDao.clearForestCells()
    }
}
