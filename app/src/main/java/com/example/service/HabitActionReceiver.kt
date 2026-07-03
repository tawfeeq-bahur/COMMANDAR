package com.example.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.HabitLogEntity
import com.example.data.local.SettingsEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HabitActionReceiver : BroadcastReceiver() {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    override fun onReceive(context: Context, intent: Intent) {
        val habitId = intent.getIntExtra("HABIT_ID", -1)
        if (habitId == -1) return

        Log.d("HabitActionReceiver", "Received request to complete habit $habitId")

        scope.launch {
            try {
                val database = AppDatabase.getDatabase(context)
                val dao = database.appDao()
                val todayDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

                val currentLogs = dao.getHabitLogsForDate(todayDateStr)
                val existingLog = currentLogs.find { it.habitId == habitId }
                val newLog = existingLog?.copy(isCompleted = true)
                    ?: HabitLogEntity(habitId = habitId, dateString = todayDateStr, isCompleted = true)

                dao.insertHabitLog(newLog)

                // Update habit streak count
                val habit = dao.getAllHabits().find { it.id == habitId }
                if (habit != null) {
                    dao.insertHabit(habit.copy(streakDays = habit.streakDays + 1))
                }

                // Award XP and Level up if needed
                val settings = dao.getSettings() ?: SettingsEntity()
                val newXp = (settings.currentXp + 15).coerceAtLeast(0)
                val newLevel = (newXp / 1000) + 1
                dao.insertSettings(
                    settings.copy(
                        currentXp = newXp,
                        userLevel = newLevel,
                        streakDays = settings.streakDays + 1
                    )
                )

                Log.d("HabitActionReceiver", "Habit $habitId marked done. XP awarded.")

                // Trigger a local broadcast in case anyone needs to know immediately
                context.sendBroadcast(Intent("com.example.HABIT_TOGGLED_OUTSIDE"))

                // If this request came from the daily reminder notification (ID 1003),
                // we should update or cancel that reminder notification.
                val notificationId = intent.getIntExtra("NOTIFICATION_ID", -1)
                if (notificationId == 1003) {
                    val habits = dao.getAllHabits()
                    val logs = dao.getHabitLogsForDate(todayDateStr)
                    val completedIds = logs.filter { it.isCompleted }.map { it.habitId }.toSet()
                    val pending = habits.filter { !completedIds.contains(it.id) }
                    
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    if (pending.isEmpty()) {
                        notificationManager.cancel(1003)
                    } else {
                        RoutineReminderReceiver.postReminderNotification(context, pending)
                    }
                }
            } catch (e: Exception) {
                Log.e("HabitActionReceiver", "Error completing habit from notification action", e)
            }
        }
    }
}
