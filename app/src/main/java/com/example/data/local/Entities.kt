package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val email: String,
    val password: String,
    val phoneNumber: String = "",
    val botName: String = ""
)

@Entity(tableName = "statuses")
data class StatusEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val replyMessage: String,
    val isCustom: Boolean,
    val iconName: String
)

@Entity(tableName = "contact_rules")
data class ContactRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val contactName: String,
    val action: String, // "NO_REPLY", "PRIORITY", "AUTO_REPLY", "CUSTOM_MODE"
    val customModeName: String? = null
)

@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val startTime: String, // "HH:mm"
    val endTime: String,   // "HH:mm"
    val modeName: String,
    val isEnabled: Boolean = true
)

@Entity(tableName = "reply_history")
data class ReplyHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val contactName: String,
    val receivedMessage: String,
    val sentReply: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    val isAutoReplyEnabled: Boolean = true,
    val themeMode: String = "SYSTEM", // "SYSTEM", "LIGHT", "DARK"
    val autoReplyDelayMinutes: Int = 5,
    val maxRepliesPerContact: Int = 3,
    val activeStatusName: String = "Study",
    val currentXp: Int = 0,
    val userLevel: Int = 1,
    val activeFocusSessionId: Int = 0,
    val blockedAppsList: String = "com.instagram.android,com.google.android.youtube,com.facebook.katana,com.android.chrome",
    val streakDays: Int = 28
)

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val frequency: String = "DAILY",
    val iconName: String = "Dumbbell",
    val isEnabled: Boolean = true,
    val streakDays: Int = 0,
    val orderIndex: Int = 0
)

@Entity(tableName = "habit_logs")
data class HabitLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val habitId: Int,
    val dateString: String, // "YYYY-MM-DD"
    val isCompleted: Boolean
)

@Entity(tableName = "focus_sessions")
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val modeName: String,
    val durationMinutes: Int,
    val completed: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val distractionCount: Int = 0
)

@Entity(tableName = "forest_cells")
data class ForestCellEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val row: Int,
    val col: Int,
    val cellType: String // "EMPTY", "TREE", "DEAD_TREE", "BUILDING"
)
