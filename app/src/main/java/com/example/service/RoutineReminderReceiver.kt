package com.example.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.local.AppDatabase
import com.example.data.local.HabitEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class RoutineReminderReceiver : BroadcastReceiver() {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    companion object {
        const val CHANNEL_ID = "RoutineReminderChannel"
        const val NOTIFICATION_ID = 1003

        fun scheduleDailyRoutineReminder(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, RoutineReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, 1002, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val calendar = Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis()
                set(Calendar.HOUR_OF_DAY, 20) // 8:00 PM
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }

            if (canScheduleExact) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                }
            } else {
                Log.d("RoutineReminderReceiver", "Exact alarms permission not granted. Falling back to inexact alarm.")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                }
            }
            Log.d("RoutineReminderReceiver", "Scheduled next reminder for: ${calendar.time}")
        }

        fun postReminderNotification(context: Context, pendingHabits: List<HabitEntity>) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            createNotificationChannel(context, notificationManager)

            val names = pendingHabits.joinToString(", ") { it.name }
            val count = pendingHabits.size

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingOpenIntent = PendingIntent.getActivity(
                context, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Pending Routines Reminder")
                .setContentText("You have $count routine${if (count > 1) "s" else ""} left to complete today!")
                .setStyle(NotificationCompat.BigTextStyle()
                    .bigText("Don't break your streak! You still need to complete:\n$names")
                )
                .setContentIntent(pendingOpenIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)

            // Add up to 2 quick action buttons to check them off directly
            pendingHabits.take(2).forEach { habit ->
                val completeIntent = Intent(context, HabitActionReceiver::class.java).apply {
                    putExtra("HABIT_ID", habit.id)
                    putExtra("NOTIFICATION_ID", NOTIFICATION_ID)
                }
                val pendingCompleteIntent = PendingIntent.getBroadcast(
                    context, habit.id + 2000, completeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
                builder.addAction(
                    android.R.drawable.checkbox_on_background,
                    "Done: ${habit.name}",
                    pendingCompleteIntent
                )
            }

            notificationManager.notify(NOTIFICATION_ID, builder.build())
        }

        private fun createNotificationChannel(context: Context, manager: NotificationManager) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Routine Reminders",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Reminders for completing daily habits and routines"
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("RoutineReminderReceiver", "Alarm received for daily routine check")

        // Reschedule alarm for tomorrow
        scheduleDailyRoutineReminder(context)

        scope.launch {
            try {
                val database = AppDatabase.getDatabase(context)
                val dao = database.appDao()
                val todayDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

                val habits = dao.getAllHabits()
                val logs = dao.getHabitLogsForDate(todayDateStr)
                val completedIds = logs.filter { it.isCompleted }.map { it.habitId }.toSet()
                val pending = habits.filter { !completedIds.contains(it.id) }

                if (pending.isNotEmpty()) {
                    Log.d("RoutineReminderReceiver", "Posting notification. Pending habits: ${pending.size}")
                    postReminderNotification(context, pending)
                } else {
                    Log.d("RoutineReminderReceiver", "All habits completed today. No notification posted.")
                }
            } catch (e: Exception) {
                Log.e("RoutineReminderReceiver", "Error running routine reminder check", e)
            }
        }
    }
}
