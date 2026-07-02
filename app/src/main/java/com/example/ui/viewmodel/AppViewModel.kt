package com.example.ui.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppRepository
import com.example.data.local.*
import com.example.data.remote.GeminiClient
import com.example.service.StatusNotificationListenerService
import com.example.service.FocusTimerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = AppRepository(database.appDao())

    // --- Authentication State ---
    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    private val _authStateMessage = MutableStateFlow<String?>(null)
    val authStateMessage: StateFlow<String?> = _authStateMessage.asStateFlow()

    // --- Screen State (Local State Navigation) ---
    private val _currentScreen = MutableStateFlow("LOGIN") // LOGIN, REGISTER, FORGOT, DASHBOARD, FOCUS, HABITS, ACTIVITY, SETTINGS
    val currentScreen: StateFlow<String> = _currentScreen.asStateFlow()

    // --- Flows from Repository ---
    val statuses: StateFlow<List<StatusEntity>> = repository.statusesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rules: StateFlow<List<ContactRuleEntity>> = repository.rulesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val schedules: StateFlow<List<ScheduleEntity>> = repository.schedulesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history: StateFlow<List<ReplyHistoryEntity>> = repository.historyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<SettingsEntity?> = repository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val habits: StateFlow<List<HabitEntity>> = repository.habitsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val forestCells: StateFlow<List<ForestCellEntity>> = repository.forestCellsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val focusSessions: StateFlow<List<FocusSessionEntity>> = repository.allFocusSessionsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Habits Logs Today ---
    private val todayDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    val todayLogs: StateFlow<List<HabitLogEntity>> = repository.getHabitLogsForDateFlow(todayDateStr)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Focus Timer States (Real-time updates via broadcast) ---
    private val _isFocusActive = MutableStateFlow(false)
    val isFocusActive: StateFlow<Boolean> = _isFocusActive.asStateFlow()

    private val _focusRemainingSeconds = MutableStateFlow(0)
    val focusRemainingSeconds: StateFlow<Int> = _focusRemainingSeconds.asStateFlow()

    private val _focusTotalSeconds = MutableStateFlow(0)
    val focusTotalSeconds: StateFlow<Int> = _focusTotalSeconds.asStateFlow()

    private val _focusMode = MutableStateFlow("Study")
    val focusMode: StateFlow<String> = _focusMode.asStateFlow()

    // --- Notification Access Permission State ---
    private val _isNotificationAccessGranted = MutableStateFlow(false)
    val isNotificationAccessGranted: StateFlow<Boolean> = _isNotificationAccessGranted.asStateFlow()

    // --- Replies Sent Today ---
    private val _repliesTodayCount = MutableStateFlow(0)
    val repliesTodayCount: StateFlow<Int> = _repliesTodayCount.asStateFlow()

    // Receiver to trigger updates when auto-replies are sent
    private val replySentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateRepliesTodayCount()
        }
    }

    // Receiver to sync countdown timer state from FocusTimerService
    private val focusTimerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.example.FOCUS_TIMER_TICK" -> {
                    _isFocusActive.value = true
                    _focusRemainingSeconds.value = intent.getIntExtra("REMAINING", 0)
                    _focusTotalSeconds.value = intent.getIntExtra("TOTAL", 0)
                    _focusMode.value = intent.getStringExtra("MODE") ?: "Study"
                }
                "com.example.FOCUS_TIMER_FINISHED", "com.example.FOCUS_TIMER_ABORTED" -> {
                    _isFocusActive.value = false
                    _focusRemainingSeconds.value = 0
                    _focusTotalSeconds.value = 0
                    viewModelScope.launch {
                        updateRepliesTodayCount()
                    }
                }
            }
        }
    }

    init {
        viewModelScope.launch {
            repository.initializeDefaults()
            checkNotificationAccess()
            updateRepliesTodayCount()
            checkAndApplyScheduleAutomation()
        }

        // Register local broadcast receiver for notification replies updates
        val replyFilter = IntentFilter("com.example.STATUS_AUTO_REPLY_SENT")
        application.registerReceiver(replySentReceiver, replyFilter, Context.RECEIVER_NOT_EXPORTED)

        // Register broadcast receiver for focus timer events
        val timerFilter = IntentFilter().apply {
            addAction("com.example.FOCUS_TIMER_TICK")
            addAction("com.example.FOCUS_TIMER_FINISHED")
            addAction("com.example.FOCUS_TIMER_ABORTED")
        }
        application.registerReceiver(focusTimerReceiver, timerFilter, Context.RECEIVER_NOT_EXPORTED)
    }

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().unregisterReceiver(replySentReceiver)
            getApplication<Application>().unregisterReceiver(focusTimerReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun navigateTo(screen: String) {
        _currentScreen.value = screen
    }

    private val _isUsageAccessGranted = MutableStateFlow(false)
    val isUsageAccessGranted: StateFlow<Boolean> = _isUsageAccessGranted.asStateFlow()

    private val _isOverlayAccessGranted = MutableStateFlow(false)
    val isOverlayAccessGranted: StateFlow<Boolean> = _isOverlayAccessGranted.asStateFlow()

    fun checkNotificationAccess() {
        val context = getApplication<Application>()
        _isNotificationAccessGranted.value = StatusNotificationListenerService.isServiceEnabled(context)
        _isUsageAccessGranted.value = checkUsageStatsPermission(context)
        _isOverlayAccessGranted.value = android.provider.Settings.canDrawOverlays(context)
    }

    private fun checkUsageStatsPermission(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
            val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    context.packageName
                )
            } else {
                appOps.checkOpNoThrow(
                    android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    context.packageName
                )
            }
            mode == android.app.AppOpsManager.MODE_ALLOWED
        } catch (e: java.lang.Exception) {
            false
        }
    }

    fun updateRepliesTodayCount() {
        viewModelScope.launch {
            val startOfToday = getStartOfTodayTimestamp()
            _repliesTodayCount.value = repository.getRepliesCountToday(startOfToday)
        }
    }

    private fun getStartOfTodayTimestamp(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    // --- Authentication Actions ---
    fun login(email: String, password: String) {
        viewModelScope.launch {
            if (email.isBlank() || password.isBlank()) {
                _authStateMessage.value = "Please fill in all fields"
                return@launch
            }
            val user = repository.getUserByEmail(email)
            if (user != null && user.password == password) {
                _currentUser.value = user
                _authStateMessage.value = null
                _currentScreen.value = "DASHBOARD"
            } else {
                _authStateMessage.value = "Invalid email or password"
            }
        }
    }

    fun register(name: String, email: String, password: String) {
        viewModelScope.launch {
            if (name.isBlank() || email.isBlank() || password.isBlank()) {
                _authStateMessage.value = "All fields are required"
                return@launch
            }
            val existing = repository.getUserByEmail(email)
            if (existing != null) {
                _authStateMessage.value = "Email is already registered"
                return@launch
            }
            val newUser = UserEntity(name = name, email = email, password = password)
            repository.registerUser(newUser)
            _currentUser.value = newUser
            _authStateMessage.value = null
            _currentScreen.value = "DASHBOARD"
        }
    }

    fun resetPassword(email: String) {
        viewModelScope.launch {
            if (email.isBlank()) {
                _authStateMessage.value = "Please enter your email address"
                return@launch
            }
            val existing = repository.getUserByEmail(email)
            if (existing != null) {
                _authStateMessage.value = "Instructions sent! Check your inbox (Simulation)"
            } else {
                _authStateMessage.value = "Email address not found"
            }
        }
    }

    fun logout() {
        _currentUser.value = null
        _currentScreen.value = "LOGIN"
    }

    // --- Status Actions ---
    fun selectActiveStatus(statusName: String) {
        viewModelScope.launch {
            repository.updateActiveStatus(statusName)
        }
    }

    fun updateStatusMessage(statusId: Int, newReplyMessage: String) {
        viewModelScope.launch {
            val statusList = statuses.value
            val status = statusList.find { it.id == statusId }
            if (status != null && newReplyMessage.isNotBlank()) {
                repository.saveStatus(status.copy(replyMessage = newReplyMessage))
            }
        }
    }

    fun addCustomStatus(name: String, replyText: String, icon: String = "Work") {
        viewModelScope.launch {
            if (name.isNotBlank() && replyText.isNotBlank()) {
                val newStatus = StatusEntity(name = name, replyMessage = replyText, isCustom = true, iconName = icon)
                repository.saveStatus(newStatus)
            }
        }
    }

    fun deleteStatus(id: Int) {
        viewModelScope.launch {
            repository.deleteStatusById(id)
        }
    }

    // --- Contact Rules Actions ---
    fun addContactRule(contactName: String, action: String, customMode: String? = null) {
        viewModelScope.launch {
            if (contactName.isNotBlank() && action.isNotBlank()) {
                val newRule = ContactRuleEntity(contactName = contactName, action = action, customModeName = customMode)
                repository.saveContactRule(newRule)
            }
        }
    }

    fun deleteContactRule(id: Int) {
        viewModelScope.launch {
            repository.deleteContactRule(id)
        }
    }

    // --- Schedules Actions ---
    fun addSchedule(startTime: String, endTime: String, modeName: String) {
        viewModelScope.launch {
            if (startTime.isNotBlank() && endTime.isNotBlank() && modeName.isNotBlank()) {
                val schedule = ScheduleEntity(startTime = startTime, endTime = endTime, modeName = modeName, isEnabled = true)
                repository.saveSchedule(schedule)
                checkAndApplyScheduleAutomation()
            }
        }
    }

    fun toggleSchedule(schedule: ScheduleEntity) {
        viewModelScope.launch {
            repository.saveSchedule(schedule.copy(isEnabled = !schedule.isEnabled))
            checkAndApplyScheduleAutomation()
        }
    }

    fun deleteSchedule(id: Int) {
        viewModelScope.launch {
            repository.deleteSchedule(id)
        }
    }

    // --- Reply History Actions ---
    fun deleteHistoryItem(id: Int) {
        viewModelScope.launch {
            repository.deleteHistoryEntry(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearHistory()
            updateRepliesTodayCount()
        }
    }

    // --- Settings Actions ---
    fun updateAutoReplyToggle(enabled: Boolean) {
        viewModelScope.launch {
            val currentSettings = repository.getSettings() ?: SettingsEntity()
            repository.saveSettings(currentSettings.copy(isAutoReplyEnabled = enabled))
        }
    }

    fun updateDelayMinutes(minutes: Int) {
        viewModelScope.launch {
            val currentSettings = repository.getSettings() ?: SettingsEntity()
            repository.saveSettings(currentSettings.copy(autoReplyDelayMinutes = minutes))
        }
    }

    fun updateSettingsConfig(delayMinutes: Int, maxReplies: Int, theme: String) {
        viewModelScope.launch {
            val currentSettings = repository.getSettings() ?: SettingsEntity()
            repository.saveSettings(
                currentSettings.copy(
                    autoReplyDelayMinutes = delayMinutes,
                    maxRepliesPerContact = maxReplies,
                    themeMode = theme
                )
            )
        }
    }

    fun updateBlockedApps(blockedApps: String) {
        viewModelScope.launch {
            val currentSettings = repository.getSettings() ?: SettingsEntity()
            repository.saveSettings(currentSettings.copy(blockedAppsList = blockedApps))
        }
    }

    fun toggleHabit(habitId: Int, isCompleted: Boolean) {
        viewModelScope.launch {
            val currentLogs = repository.getHabitLogsForDate(todayDateStr)
            val existingLog = currentLogs.find { it.habitId == habitId }
            val newLog = existingLog?.copy(isCompleted = isCompleted)
                ?: HabitLogEntity(habitId = habitId, dateString = todayDateStr, isCompleted = isCompleted)

            repository.saveHabitLog(newLog)

            // Dynamic Habit Streak Update
            val habit = database.appDao().getAllHabits().find { it.id == habitId }
            if (habit != null) {
                val newStreak = if (isCompleted) habit.streakDays + 1 else (habit.streakDays - 1).coerceAtLeast(0)
                repository.saveHabit(habit.copy(streakDays = newStreak))
            }

            // Award XP: +15 on check, -15 on uncheck
            val currentSettings = repository.getSettings() ?: SettingsEntity()
            val xpChange = if (isCompleted) 15 else -15
            val newXp = (currentSettings.currentXp + xpChange).coerceAtLeast(0)
            val newLevel = (newXp / 1000) + 1
            
            val newSettingsStreak = if (isCompleted) currentSettings.streakDays + 1 else (currentSettings.streakDays - 1).coerceAtLeast(0)

            repository.saveSettings(
                currentSettings.copy(
                    currentXp = newXp,
                    userLevel = newLevel,
                    streakDays = newSettingsStreak
                )
            )
        }
    }

    fun addHabit(name: String, iconName: String = "Dumbbell") {
        viewModelScope.launch {
            if (name.isNotBlank()) {
                val newHabit = HabitEntity(name = name, iconName = iconName)
                repository.saveHabit(newHabit)
            }
        }
    }

    fun deleteHabit(id: Int) {
        viewModelScope.launch {
            repository.deleteHabit(id)
        }
    }

    // --- Focus Actions ---
    fun startFocusSession(context: Context, durationMinutes: Int, mode: String, strictMode: Boolean) {
        val intent = Intent(context, FocusTimerService::class.java).apply {
            action = "START"
            putExtra("DURATION", durationMinutes)
            putExtra("MODE", mode)
            putExtra("STRICT_MODE", strictMode)
        }
        context.startForegroundService(intent)
    }

    fun stopFocusSession(context: Context) {
        val intent = Intent(context, FocusTimerService::class.java).apply {
            action = "STOP"
        }
        context.startService(intent)
    }

    // --- Schedule Automation Runner ---
    fun checkAndApplyScheduleAutomation() {
        viewModelScope.launch(Dispatchers.IO) {
            val activeSchedules = database.appDao().getAllSchedules()
            if (activeSchedules.isEmpty()) return@launch

            val nowTimeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            val matchedMode = getActiveModeForTime(nowTimeStr, activeSchedules)

            if (matchedMode != null) {
                val currentSettings = repository.getSettings()
                if (currentSettings != null && currentSettings.activeStatusName != matchedMode) {
                    repository.updateActiveStatus(matchedMode)
                    Log.d("ScheduleAutomation", "Automatically switched status to $matchedMode matching schedule.")
                }
            }
        }
    }

    private fun getActiveModeForTime(currentTimeString: String, activeSchedules: List<ScheduleEntity>): String? {
        for (schedule in activeSchedules) {
            if (!schedule.isEnabled) continue
            if (isTimeBetween(currentTimeString, schedule.startTime, schedule.endTime)) {
                return schedule.modeName
            }
        }
        return null
    }

    private fun isTimeBetween(now: String, start: String, end: String): Boolean {
        return if (start <= end) {
            now >= start && now <= end
        } else {
            now >= start || now <= end
        }
    }

    // --- Simulator tool ---
    fun simulateIncomingNotification(senderName: String, messageText: String, onComplete: (String) -> Unit) {
        viewModelScope.launch {
            val settings = repository.getSettings() ?: SettingsEntity()
            if (!settings.isAutoReplyEnabled) {
                onComplete("Skipped: Auto reply is globally disabled.")
                return@launch
            }

            // 1. Check Contact Rules
            val rule = repository.getContactRuleForName(senderName)
            if (rule != null && rule.action == "NO_REPLY") {
                onComplete("Skipped: No-reply rule is active for contact '$senderName'.")
                return@launch
            }

            // 2. Check Anti-Spam (Once every X minutes per contact)
            val now = System.currentTimeMillis()
            val delayMs = settings.autoReplyDelayMinutes * 60 * 1000L
            val recentReplies = repository.getRepliesForContactAfter(senderName, now - delayMs)
            if (recentReplies.isNotEmpty()) {
                onComplete("Skipped: Anti-Spam active. Already replied to '$senderName' in the last ${settings.autoReplyDelayMinutes} mins.")
                return@launch
            }

            // 3. Enforce Max Replies limit per contact today
            val startOfToday = getStartOfTodayTimestamp()
            val allRepliesToday = repository.getRepliesForContactAfter(senderName, startOfToday)
            if (allRepliesToday.size >= settings.maxRepliesPerContact) {
                onComplete("Skipped: Max replies limit (${settings.maxRepliesPerContact}) reached for '$senderName' today.")
                return@launch
            }

            // 4. Resolve Active Mode and Reply Message
            var activeModeName = settings.activeStatusName
            if (rule != null && rule.action == "CUSTOM_MODE" && !rule.customModeName.isNullOrEmpty()) {
                activeModeName = rule.customModeName
            }

            val allStatuses = database.appDao().getAllStatuses()
            var activeStatus = allStatuses.find { it.name.lowercase() == activeModeName.lowercase() }
            if (activeStatus == null) {
                activeStatus = allStatuses.find { it.name.lowercase() == settings.activeStatusName.lowercase() }
            }

            val baseReply = activeStatus?.replyMessage ?: "Hi! I am currently busy. I will get back to you soon."

            // 5. Generate Reply (AI vs Base Status Reply)
            val finalReplyText = if (activeModeName.lowercase() == "ai assistant" || activeModeName.lowercase().contains("ai")) {
                GeminiClient.generateSmartReply(baseReply, messageText)
            } else {
                baseReply
            }

            // 6. Record to history
            val historyEntry = ReplyHistoryEntity(
                contactName = senderName,
                receivedMessage = messageText,
                sentReply = finalReplyText,
                timestamp = System.currentTimeMillis()
            )
            repository.addHistoryEntry(historyEntry)
            updateRepliesTodayCount()

            onComplete("Replied to $senderName: \"$finalReplyText\"")
        }
    }
}
