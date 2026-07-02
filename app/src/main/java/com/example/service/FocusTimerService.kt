package com.example.service

import android.app.*
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.data.local.AppDatabase
import com.example.data.local.FocusSessionEntity
import com.example.data.local.ForestCellEntity
import com.example.ui.BlockActivity
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class FocusTimerService : Service() {

    private val binder = LocalBinder()
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private var timerJob: Job? = null
    private var blockerJob: Job? = null

    // Session Parameters
    private var totalSeconds = 0
    var remainingSeconds = 0
        private set
    private var isStrictMode = false
    private var modeName = "Study"
    private var focusSessionId = 0L

    companion object {
        const val CHANNEL_ID = "FocusTimerChannel"
        const val NOTIFICATION_ID = 1001
        
        // Static state variables to communicate status directly with other services/VMs
        @Volatile
        var isSessionActive = false
            private set
            
        @Volatile
        var currentSessionMode = "Study"
            private set

        @Volatile
        var sessionRemainingSeconds = 0
            private set
    }

    inner class LocalBinder : Binder() {
        fun getService(): FocusTimerService = this@FocusTimerService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d("FocusTimerService", "Service started with action: $action")
        
        when (action) {
            "START" -> {
                val durationMinutes = intent.getIntExtra("DURATION", 25)
                isStrictMode = intent.getBooleanExtra("STRICT_MODE", false)
                modeName = intent.getStringExtra("MODE") ?: "Study"
                startFocusSession(durationMinutes)
            }
            "STOP" -> {
                stopFocusSession(aborted = true)
            }
        }
        return START_NOT_STICKY
    }

    private fun startFocusSession(durationMinutes: Int) {
        totalSeconds = durationMinutes * 60
        remainingSeconds = totalSeconds
        isSessionActive = true
        currentSessionMode = modeName
        sessionRemainingSeconds = remainingSeconds

        // Create foreground notification
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        // Insert Focus Session into Room
        serviceScope.launch(Dispatchers.IO) {
            val database = AppDatabase.getDatabase(this@FocusTimerService)
            val dao = database.appDao()
            val newSession = FocusSessionEntity(
                modeName = modeName,
                durationMinutes = durationMinutes,
                completed = false
            )
            focusSessionId = dao.insertFocusSession(newSession)
            
            // Switch Auto-Reply Settings status to match the focus mode and turn Auto-Reply ON
            val currentSettings = dao.getSettings()
            if (currentSettings != null) {
                dao.insertSettings(
                    currentSettings.copy(
                        isAutoReplyEnabled = true,
                        activeStatusName = modeName,
                        activeFocusSessionId = focusSessionId.toInt()
                    )
                )
            }
        }

        // Start Countdown Timer Job
        timerJob = serviceScope.launch {
            while (remainingSeconds > 0) {
                delay(1000)
                remainingSeconds--
                sessionRemainingSeconds = remainingSeconds

                // Update notification
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, createNotification())

                // Broadcast tick
                val tickIntent = Intent("com.example.FOCUS_TIMER_TICK").apply {
                    putExtra("REMAINING", remainingSeconds)
                    putExtra("TOTAL", totalSeconds)
                    putExtra("MODE", modeName)
                }
                sendBroadcast(tickIntent)
            }
            
            // Finished successfully
            stopFocusSession(aborted = false)
        }

        // Start App Blocker Job if Strict Mode is active
        if (isStrictMode) {
            startAppBlocker()
        }
    }

    private fun startAppBlocker() {
        blockerJob = serviceScope.launch(Dispatchers.IO) {
            val database = AppDatabase.getDatabase(this@FocusTimerService)
            val dao = database.appDao()
            
            while (isActive) {
                delay(500) // Poll every 500ms
                val settings = dao.getSettings()
                val blockedApps = settings?.blockedAppsList?.split(",") ?: emptyList()
                
                checkAndBlockApps(blockedApps)
            }
        }
    }

    private fun checkAndBlockApps(blockedApps: List<String>) {
        if (blockedApps.isEmpty()) return
        
        try {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val time = System.currentTimeMillis()
            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                time - 1000 * 60,
                time
            )
            
            if (stats != null && stats.isNotEmpty()) {
                val sortedStats = stats.sortedByDescending { it.lastTimeUsed }
                val foregroundApp = sortedStats.firstOrNull()?.packageName
                
                if (foregroundApp != null && blockedApps.contains(foregroundApp) && foregroundApp != packageName) {
                    Log.d("FocusTimerService", "Blocking active app: $foregroundApp")
                    
                    // Increment distraction count in database
                    serviceScope.launch(Dispatchers.IO) {
                        val database = AppDatabase.getDatabase(this@FocusTimerService)
                        val dao = database.appDao()
                        val currentSession = dao.getFocusSessionById(focusSessionId.toInt())
                        if (currentSession != null) {
                            dao.insertFocusSession(currentSession.copy(distractionCount = currentSession.distractionCount + 1))
                        }
                    }

                    // Launch overlay blocker activity
                    val blockIntent = Intent(this@FocusTimerService, BlockActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra("REMAINING_SECONDS", remainingSeconds)
                        putExtra("BLOCKED_APP", foregroundApp)
                    }
                    startActivity(blockIntent)
                }
            }
        } catch (e: Exception) {
            Log.e("FocusTimerService", "Error blocking apps", e)
        }
    }

    private fun stopFocusSession(aborted: Boolean) {
        timerJob?.cancel()
        blockerJob?.cancel()
        isSessionActive = false
        sessionRemainingSeconds = 0

        serviceScope.launch(Dispatchers.IO) {
            val database = AppDatabase.getDatabase(this@FocusTimerService)
            val dao = database.appDao()

            // 1. Update the Focus Session in DB
            val session = dao.getFocusSessionById(focusSessionId.toInt())
            if (session != null) {
                dao.insertFocusSession(session.copy(completed = !aborted))
            }

            // 2. Award XP & Level Up / Plant tree in Forest
            val currentSettings = dao.getSettings()
            if (currentSettings != null) {
                var xpGained = 0
                var xpString = ""
                
                if (!aborted) {
                    // Successful session: 20 XP default
                    xpGained = 30 
                    xpString = "Focus session completed! +30 XP"
                    
                    // Grow a tree in the Forest grid
                    growForestItem(dao, "TREE")
                } else {
                    // Aborted session: penalize or no XP, and plant dead tree
                    xpGained = -10
                    xpString = "Focus session aborted early! -10 XP"
                    
                    // Plant a dead tree
                    growForestItem(dao, "DEAD_TREE")
                }

                val newXp = (currentSettings.currentXp + xpGained).coerceAtLeast(0)
                // Level Up threshold: 1000 XP per level
                val newLevel = (newXp / 1000) + 1
                
                dao.insertSettings(
                    currentSettings.copy(
                        currentXp = newXp,
                        userLevel = newLevel,
                        activeFocusSessionId = 0
                    )
                )
                
                Log.d("FocusTimerService", "XP Updated: $xpString. New XP: $newXp, Level: $newLevel")
            }

            // 3. Broadcast completion
            val finishIntent = Intent(if (aborted) "com.example.FOCUS_TIMER_ABORTED" else "com.example.FOCUS_TIMER_FINISHED")
            sendBroadcast(finishIntent)

            // 4. Clean up service
            stopSelf()
        }
    }

    private suspend fun growForestItem(dao: com.example.data.local.AppDao, type: String) {
        val cells = dao.getAllForestCells()
        // Find first empty cell
        val emptyCell = cells.find { it.cellType == "EMPTY" }
        if (emptyCell != null) {
            dao.insertForestCell(emptyCell.copy(cellType = type))
        } else {
            // Grid is full! Clear and replant
            dao.clearForestCells()
            // Reset with r=0..4, c=0..4
            for (r in 0 until 5) {
                for (c in 0 until 5) {
                    val cellType = if (r == 0 && c == 0) type else "EMPTY"
                    dao.insertForestCell(ForestCellEntity(row = r, col = c, cellType = cellType))
                }
            }
        }
    }

    private fun createNotification(): Notification {
        val minutes = remainingSeconds / 60
        val seconds = remainingSeconds % 60
        val timeStr = String.format("%02d:%02d", minutes, seconds)

        val intent = Intent(this, com.example.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Focus Shield ACTIVE - $modeName")
            .setContentText("Focus session in progress: $timeStr remaining.")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Focus Timer Channels",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}
