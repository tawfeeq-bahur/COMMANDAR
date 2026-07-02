package com.example.service

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.content.ContentUris
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.data.AppRepository
import com.example.data.local.AppDatabase
import com.example.data.local.ReplyHistoryEntity
import com.example.data.remote.GeminiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StatusNotificationListenerService : NotificationListenerService() {

    private val job = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + job)
    private lateinit var database: AppDatabase
    private lateinit var repository: AppRepository

    override fun onCreate() {
        super.onCreate()
        Log.d("StatusListener", "Service created")
        database = AppDatabase.getDatabase(this)
        repository = AppRepository(database.appDao())
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName ?: return

        // Handle WhatsApp notifications
        if (packageName == "com.whatsapp" || packageName == "com.whatsapp.w4b" || packageName == "com.example" || packageName == "com.google.android.apps.messaging") {
            val extras = sbn.notification.extras ?: return
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return

            // 1. Group and summary notifications check
            val isGroup = extras.getBoolean("android.isGroupConversation", false)
            val conversationTitle = extras.getCharSequence("android.conversationTitle")?.toString()
            val isGroupSummary = (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0

            if (isGroup || !conversationTitle.isNullOrBlank() || isGroupSummary) {
                Log.d("StatusListener", "Skipping group or summary notification: title=$title, conversationTitle=$conversationTitle, isGroupSummary=$isGroupSummary")
                return
            }

            // Avoid processing system notifications, group notifications with no sender, or outgoing notifications
            if (title.isBlank() || text.isBlank() || title.contains("WhatsApp") || title.lowercase().contains("chat") || title.lowercase().contains("messages")) {
                return
            }

            serviceScope.launch {
                try {
                    handleIncomingMessage(sbn, title, text)
                } catch (e: Exception) {
                    Log.e("StatusListener", "Error processing auto-reply", e)
                }
            }
        }
    }

    private suspend fun handleIncomingMessage(sbn: StatusBarNotification, senderName: String, messageText: String) {
        val now = System.currentTimeMillis()

        // 1. In-memory contact lock: check if we are currently/recently processing this contact
        val lastProcessingTime = activeProcessingContacts[senderName]
        if (lastProcessingTime != null && (now - lastProcessingTime) < 10000L) { // 10 seconds lock
            Log.d("StatusListener", "In-memory debounce active: already processing reply for $senderName in the last 10s")
            return
        }

        // 2. In-memory message duplicate check: check if we already replied to the exact same message text from this contact recently
        val lastReplied = lastRepliedMessages[senderName]
        if (lastReplied != null && lastReplied.messageText == messageText && (now - lastReplied.timestamp) < 60000L) { // 1 minute debounce for identical message text
            Log.d("StatusListener", "Duplicate check active: already replied to same message '$messageText' from $senderName in the last 60s")
            return
        }

        // Acquire lock
        activeProcessingContacts[senderName] = now

        try {
            val settings = repository.getSettings() ?: return
            if (!settings.isAutoReplyEnabled) {
                Log.d("StatusListener", "Auto reply globally disabled")
                return
            }

            // 3. Check Contact Rules
            val rule = repository.getContactRuleForName(senderName)
            if (rule != null) {
                if (rule.action == "NO_REPLY") {
                    Log.d("StatusListener", "No-reply rule active for contact: $senderName")
                    return
                }
            }

            // 4. Enforce Anti-Spam (Once every X minutes per contact)
            val delayMs = settings.autoReplyDelayMinutes * 60 * 1000L
            val recentReplies = repository.getRepliesForContactAfter(senderName, now - delayMs)
            if (recentReplies.isNotEmpty()) {
                Log.d("StatusListener", "Anti-Spam active: already replied to $senderName in the last ${settings.autoReplyDelayMinutes} mins")
                return
            }

            // 5. Enforce Max Replies limit per contact today
            val startOfToday = getStartOfTodayTimestamp()
            val allRepliesToday = repository.getRepliesForContactAfter(senderName, startOfToday)
            if (allRepliesToday.size >= settings.maxRepliesPerContact) {
                Log.d("StatusListener", "Max replies limit (${settings.maxRepliesPerContact}) reached for $senderName today")
                return
            }

            // 6. Resolve Active Mode and Reply Message
            var activeModeName = settings.activeStatusName
            if (rule != null && rule.action == "CUSTOM_MODE" && !rule.customModeName.isNullOrEmpty()) {
                activeModeName = rule.customModeName
            }

            val statuses = repository.statusesFlow
            var activeStatus = database.appDao().getAllStatuses().find { it.name.lowercase() == activeModeName.lowercase() }
            if (activeStatus == null) {
                // Fallback to activeStatusName
                activeStatus = database.appDao().getAllStatuses().find { it.name.lowercase() == settings.activeStatusName.lowercase() }
            }

            val baseReply = activeStatus?.replyMessage ?: "Hi! I am currently busy. I will get back to you soon."

            // Resolve availability context containing active timer and calendar details
            var availabilityContext = baseReply
            if (FocusTimerService.isSessionActive) {
                val remainingMins = FocusTimerService.sessionRemainingSeconds / 60
                availabilityContext += " (I'm currently in focus mode: '${FocusTimerService.currentSessionMode}' with $remainingMins minutes remaining)."
            }
            
            val calendarContext = getNextCalendarEventContext(this)
            if (calendarContext.isNotEmpty()) {
                availabilityContext += " (Availability context: $calendarContext)"
            }

            // 7. Generate Reply (AI vs Base Status Reply)
            val finalReplyText = if (activeModeName.lowercase() == "ai assistant" || activeModeName.lowercase().contains("ai")) {
                // Call Gemini with rich availability context
                GeminiClient.generateSmartReply(availabilityContext, messageText)
            } else {
                // For standard replies, dynamically append the timer if focus session is active
                if (FocusTimerService.isSessionActive) {
                    val remainingMins = FocusTimerService.sessionRemainingSeconds / 60
                    "$baseReply (Expected available in $remainingMins mins)"
                } else {
                    baseReply
                }
            }

            // 8. Attempt Notification Action Reply
            val sentSuccessfully = attemptNotificationReply(sbn, finalReplyText)
            if (sentSuccessfully) {
                // Save to last replied cache
                lastRepliedMessages[senderName] = LastRepliedInfo(messageText, System.currentTimeMillis())

                // Save to history
                val historyEntry = ReplyHistoryEntity(
                    contactName = senderName,
                    receivedMessage = messageText,
                    sentReply = finalReplyText,
                    timestamp = System.currentTimeMillis()
                )
                repository.addHistoryEntry(historyEntry)

                // Trigger visual feedback update in UI
                val broadcastIntent = Intent("com.example.STATUS_AUTO_REPLY_SENT")
                sendBroadcast(broadcastIntent)
                Log.d("StatusListener", "Auto-reply sent successfully to $senderName: $finalReplyText")
            }
        } finally {
            // We keep the lock in the activeProcessingContacts map for 10s.
            // Do NOT remove it immediately here, so that any notification updates triggered by sending the reply
            // will be caught by the 10s check.
        }
    }

    private fun attemptNotificationReply(sbn: StatusBarNotification, replyText: String): Boolean {
        val actions = sbn.notification.actions ?: return false
        for (action in actions) {
            val remoteInputs = action.remoteInputs ?: continue
            for (remoteInput in remoteInputs) {
                if (remoteInput.resultKey != null) {
                    try {
                        val replyIntent = Intent()
                        val replyBundle = Bundle()
                        replyBundle.putCharSequence(remoteInput.resultKey, replyText)
                        RemoteInput.addResultsToIntent(
                            arrayOf(remoteInput),
                            replyIntent,
                            replyBundle
                        )
                        action.actionIntent.send(this, 0, replyIntent)
                        return true
                    } catch (e: Exception) {
                        Log.e("StatusListener", "Failed sending action intent", e)
                    }
                }
            }
        }
        return false
    }

    private fun getStartOfTodayTimestamp(): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun getNextCalendarEventContext(context: Context): String {
        if (context.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return ""
        }
        return try {
            val uri = android.provider.CalendarContract.Instances.CONTENT_URI
            val now = System.currentTimeMillis()
            val builder = uri.buildUpon()
            ContentUris.appendId(builder, now)
            ContentUris.appendId(builder, now + 1000 * 60 * 60 * 24) // next 24 hours
            
            val projection = arrayOf(
                android.provider.CalendarContract.Instances.TITLE,
                android.provider.CalendarContract.Instances.BEGIN,
                android.provider.CalendarContract.Instances.END
            )
            
            val selection = "${android.provider.CalendarContract.Instances.ALL_DAY} = 0"
            
            val cursor = context.contentResolver.query(
                builder.build(),
                projection,
                selection,
                null,
                "${android.provider.CalendarContract.Instances.BEGIN} ASC"
            )
            
            var eventContext = ""
            cursor?.use {
                if (it.moveToFirst()) {
                    val title = it.getString(0)
                    val begin = it.getLong(1)
                    val formatter = SimpleDateFormat("h:mm a", Locale.getDefault())
                    val startTimeStr = formatter.format(Date(begin))
                    eventContext = "Next calendar event is '$title' starting at $startTimeStr."
                }
            }
            eventContext
        } catch (e: Exception) {
            Log.e("StatusListener", "Error reading calendar", e)
            ""
        }
    }

    data class LastRepliedInfo(val messageText: String, val timestamp: Long)

    companion object {
        private val lastRepliedMessages = java.util.concurrent.ConcurrentHashMap<String, LastRepliedInfo>()
        private val activeProcessingContacts = java.util.concurrent.ConcurrentHashMap<String, Long>()

        fun isServiceEnabled(context: Context): Boolean {
            val pkgName = context.packageName
            val flat = android.provider.Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            )
            if (flat != null) {
                val names = flat.split(":")
                for (name in names) {
                    val cn = android.content.ComponentName.unflattenFromString(name)
                    if (cn != null && cn.packageName == pkgName) {
                        return true
                    }
                }
            }
            return false
        }
    }
}
