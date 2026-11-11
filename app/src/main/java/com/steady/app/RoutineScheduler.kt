package com.steady.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object FocusPermissions {
    fun isAccessibilityEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        return manager.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any {
                it.resolveInfo.serviceInfo.packageName == context.packageName &&
                    it.resolveInfo.serviceInfo.name == FocusAccessibilityService::class.java.name
            }
    }
}

object RoutineScheduler {
    private const val ACTION_START_ROUTINE = "com.steady.app.action.START_ROUTINE"

    fun nextTriggerMillis(
        routine: FocusRoutine,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long? {
        if (!routine.enabled) return null
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val time = LocalTime.of(routine.hour, routine.minute)
        for (offset in 0L..7L) {
            val date = today.plusDays(offset)
            if (date.dayOfWeek.value !in routine.daysOfWeek) continue
            val candidate = LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli()
            if (candidate > nowMillis) return candidate
        }
        return null
    }

    fun schedule(context: Context, routine: FocusRoutine, nowMillis: Long = System.currentTimeMillis()) {
        val triggerAt = nextTriggerMillis(routine, nowMillis) ?: run {
            cancel(context, routine.id)
            return
        }
        val alarm = context.getSystemService(AlarmManager::class.java)
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent(context, routine.id))
    }

    fun cancel(context: Context, routineId: String) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        alarm.cancel(pendingIntent(context, routineId))
    }

    fun rescheduleAll(context: Context, nowMillis: Long = System.currentTimeMillis()) {
        FocusStore(context).loadRoutines().forEach { schedule(context, it, nowMillis) }
    }

    fun scheduleNext(context: Context, routine: FocusRoutine, nowMillis: Long = System.currentTimeMillis()) {
        schedule(context, routine, nowMillis)
    }

    private fun pendingIntent(context: Context, routineId: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        routineId.hashCode(),
        Intent(context, RoutineAlarmReceiver::class.java)
            .setAction(ACTION_START_ROUTINE)
            .setData(Uri.parse("steady://routine/$routineId")),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

class RoutineAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val routineId = intent?.data?.lastPathSegment ?: return
        val routine = FocusStore(context).loadRoutines().firstOrNull { it.id == routineId } ?: return
        if (!routine.enabled) return

        // Schedule the next occurrence before starting this one. The schedule
        // remains intact if the session is already active or permission changed.
        RoutineScheduler.scheduleNext(context, routine)
        val store = FocusStore(context)
        val now = System.currentTimeMillis()
        val previousSession = store.loadStoredSession()
        if (previousSession != null) {
            if (previousSession.isActive(now)) return
            store.finishSession(completed = true, nowMillis = now)
        }
        if (!FocusPermissions.isAccessibilityEnabled(context)) return

        val session = FocusSession.create(now, routine.durationMinutes, routine.blockedPackages, routine.intention.ifBlank { routine.name })
        store.saveSession(session)
        FocusNotifications.scheduleSessionEnd(context, session)
        if (store.areFocusNotificationsEnabled()) FocusNotifications.showActive(context, session)
    }
}

class FocusBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> {
                val now = System.currentTimeMillis()
                RoutineScheduler.rescheduleAll(context, now)
                val store = FocusStore(context)
                val storedSession = store.loadStoredSession() ?: return
                if (storedSession.isActive(now) && FocusPermissions.isAccessibilityEnabled(context)) {
                    FocusNotifications.scheduleSessionEnd(context, storedSession)
                    if (store.areFocusNotificationsEnabled()) FocusNotifications.showActive(context, storedSession)
                } else {
                    store.finishSession(completed = !storedSession.isActive(now), nowMillis = now)
                    FocusNotifications.cancelActive(context)
                }
            }
        }
    }
}
