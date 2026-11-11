package com.steady.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object FocusNotifications {
    const val ACTIVE_CHANNEL_ID = "focus-active"
    const val COMPLETE_CHANNEL_ID = "focus-complete"
    const val ACTIVE_NOTIFICATION_ID = 1200
    const val COMPLETE_NOTIFICATION_ID = 1201
    const val ACTION_END_SESSION = "com.steady.app.action.END_FOCUS_SESSION"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(ACTIVE_CHANNEL_ID, "Active focus", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "A quiet status notification while a focus session is active" },
            )
            manager.createNotificationChannel(
                NotificationChannel(COMPLETE_CHANNEL_ID, "Focus session complete", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "A notification when a timed focus session finishes" },
            )
        }
    }

    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun showActive(context: Context, session: FocusSession) {
        if (!canNotify(context)) return
        ensureChannels(context)

        val openApp = PendingIntent.getActivity(
            context,
            ACTIVE_NOTIFICATION_ID,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val endSession = PendingIntent.getBroadcast(
            context,
            ACTIVE_NOTIFICATION_ID,
            Intent(context, FocusEndReceiver::class.java).setAction(ACTION_END_SESSION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val minutes = ((session.endsAtMillis - session.startedAtMillis) / 60_000L).toInt()
        val title = session.intention.ifBlank { "Focus session active" }
        val notification = NotificationCompat.Builder(context, ACTIVE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_steady)
            .setContentTitle(title)
            .setContentText("$minutes-minute focus · ${session.blockedPackages.size} apps paused")
            .setWhen(session.endsAtMillis)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setContentIntent(openApp)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .addAction(android.R.drawable.ic_media_pause, "End focus", endSession)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ACTIVE_NOTIFICATION_ID, notification) }
    }

    fun cancelActive(context: Context) {
        NotificationManagerCompat.from(context).cancel(ACTIVE_NOTIFICATION_ID)
    }

    fun cancelSessionAlarm(context: Context) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            COMPLETE_NOTIFICATION_ID,
            Intent(context, FocusEndReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent)
    }

    fun scheduleSessionEnd(context: Context, session: FocusSession) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            COMPLETE_NOTIFICATION_ID,
            Intent(context, FocusEndReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        context.getSystemService(AlarmManager::class.java)
            .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, session.endsAtMillis, pendingIntent)
    }

    fun showComplete(context: Context) {
        if (!canNotify(context)) return
        ensureChannels(context)
        val notification = NotificationCompat.Builder(context, COMPLETE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_steady)
            .setContentTitle("Focus session complete")
            .setContentText("Your focus time is finished. Choose what you want to do next.")
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(COMPLETE_NOTIFICATION_ID, notification) }
    }
}

class FocusEndReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val isUserEnding = intent?.action == FocusNotifications.ACTION_END_SESSION
        val store = FocusStore(context)
        store.finishSession(completed = !isUserEnding)
        FocusNotifications.cancelActive(context)

        if (isUserEnding) {
            FocusNotifications.cancelSessionAlarm(context)
        } else if (store.areFocusNotificationsEnabled()) {
            FocusNotifications.showComplete(context)
        }
    }
}
