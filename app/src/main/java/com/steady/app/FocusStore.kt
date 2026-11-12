package com.steady.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class FocusHistoryEntry(
    val startedAtMillis: Long,
    val durationMinutes: Int,
    val intention: String,
    val blockedAppCount: Int,
    val completed: Boolean,
)

class FocusStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun loadSession(nowMillis: Long = System.currentTimeMillis()): FocusSession? {
        return loadStoredSession()?.takeIf { it.isActive(nowMillis) }
    }

    fun loadStoredSession(): FocusSession? {
        val end = prefs.getLong(KEY_END, 0L)
        val start = prefs.getLong(KEY_START, 0L)
        val blocked = prefs.getStringSet(KEY_BLOCKED, emptySet()).orEmpty()
        if (start <= 0L || end <= start || blocked.isEmpty()) return null
        return FocusSession(
            startedAtMillis = start,
            endsAtMillis = end,
            blockedPackages = blocked.toSet(),
            intention = prefs.getString(KEY_INTENTION, "").orEmpty(),
            routineId = prefs.getString(KEY_ROUTINE_ID, null),
            routineName = prefs.getString(KEY_ROUTINE_NAME, "").orEmpty(),
            minimumAction = prefs.getString(KEY_MINIMUM_ACTION, "").orEmpty(),
            normalAction = prefs.getString(KEY_NORMAL_ACTION, "").orEmpty(),
            extraAction = prefs.getString(KEY_EXTRA_ACTION, "").orEmpty(),
            fallbackPlan = prefs.getString(KEY_FALLBACK_PLAN, "").orEmpty(),
        )
    }

    fun saveSession(session: FocusSession) {
        prefs.edit()
            .putLong(KEY_START, session.startedAtMillis)
            .putLong(KEY_END, session.endsAtMillis)
            .putStringSet(KEY_BLOCKED, session.blockedPackages.toSet())
            .putString(KEY_INTENTION, session.intention)
            .putString(KEY_ROUTINE_ID, session.routineId)
            .putString(KEY_ROUTINE_NAME, session.routineName)
            .putString(KEY_MINIMUM_ACTION, session.minimumAction)
            .putString(KEY_NORMAL_ACTION, session.normalAction)
            .putString(KEY_EXTRA_ACTION, session.extraAction)
            .putString(KEY_FALLBACK_PLAN, session.fallbackPlan)
            .apply()
    }

    fun clearSession() {
        prefs.edit()
            .remove(KEY_START).remove(KEY_END).remove(KEY_BLOCKED).remove(KEY_INTENTION)
            .remove(KEY_ROUTINE_ID).remove(KEY_ROUTINE_NAME).remove(KEY_MINIMUM_ACTION)
            .remove(KEY_NORMAL_ACTION).remove(KEY_EXTRA_ACTION).remove(KEY_FALLBACK_PLAN)
            .apply()
    }

    fun finishSession(completed: Boolean, nowMillis: Long = System.currentTimeMillis()) {
        val startedAt = prefs.getLong(KEY_START, 0L)
        val endsAt = prefs.getLong(KEY_END, 0L)
        val blocked = prefs.getStringSet(KEY_BLOCKED, emptySet()).orEmpty()
        if (startedAt > 0L && endsAt > startedAt) {
            val history = runCatching { JSONArray(prefs.getString(KEY_HISTORY, "[]")) }.getOrDefault(JSONArray())
            history.put(
                JSONObject()
                    .put("startedAt", startedAt)
                    .put("durationMinutes", ((endsAt - startedAt) / 60_000L).toInt())
                    .put("intention", prefs.getString(KEY_INTENTION, "").orEmpty())
                    .put("blockedAppCount", blocked.size)
                    .put("completed", completed)
                    .put("endedAt", nowMillis),
            )
            while (history.length() > MAX_HISTORY_ENTRIES) history.remove(0)
            prefs.edit().putString(KEY_HISTORY, history.toString()).apply()
        }
        clearSession()
    }

    fun loadHistory(): List<FocusHistoryEntry> {
        val history = runCatching { JSONArray(prefs.getString(KEY_HISTORY, "[]")) }.getOrDefault(JSONArray())
        return (0 until history.length()).mapNotNull { index ->
            runCatching {
                val item = history.getJSONObject(index)
                FocusHistoryEntry(
                    startedAtMillis = item.getLong("startedAt"),
                    durationMinutes = item.getInt("durationMinutes"),
                    intention = item.optString("intention"),
                    blockedAppCount = item.getInt("blockedAppCount"),
                    completed = item.getBoolean("completed"),
                )
            }.getOrNull()
        }
    }

    fun loadSelectedApps(): Set<String> = prefs.getStringSet(KEY_SELECTED, emptySet()).orEmpty().toSet()

    fun saveSelectedApps(packages: Set<String>) {
        prefs.edit().putStringSet(KEY_SELECTED, packages.toSet()).apply()
    }

    fun areFocusNotificationsEnabled(): Boolean = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, false)

    fun setFocusNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply()
    }

    fun loadRoutines(): List<FocusRoutine> {
        val routines = runCatching { JSONArray(prefs.getString(KEY_ROUTINES, "[]")) }.getOrDefault(JSONArray())
        return (0 until routines.length()).mapNotNull { index ->
            runCatching {
                val item = routines.getJSONObject(index)
                val days = item.getJSONArray("days")
                val apps = item.getJSONArray("apps")
                FocusRoutine(
                    id = item.getString("id"),
                    name = item.getString("name"),
                    intention = item.optString("intention"),
                    hour = item.getInt("hour"),
                    minute = item.getInt("minute"),
                    daysOfWeek = (0 until days.length()).map { days.getInt(it) }.toSet(),
                    durationMinutes = item.getInt("durationMinutes"),
                    blockedPackages = (0 until apps.length()).map { apps.getString(it) }.toSet(),
                    enabled = item.optBoolean("enabled", true),
                    minimumAction = item.optString("minimumAction", "Start with 2 minutes").ifBlank { "Start with 2 minutes" },
                    normalAction = item.optString("normalAction", ""),
                    extraAction = item.optString("extraAction", ""),
                    fallbackPlan = item.optString("fallbackPlan", ""),
                )
            }.getOrNull()
        }
    }

    fun saveRoutine(routine: FocusRoutine) {
        val routines = (loadRoutines().filterNot { it.id == routine.id } + routine).sortedBy { it.hour * 60 + it.minute }
        val json = JSONArray()
        routines.forEach { item -> json.put(item.toJson()) }
        prefs.edit().putString(KEY_ROUTINES, json.toString()).apply()
    }

    fun deleteRoutine(id: String) {
        val json = JSONArray()
        loadRoutines().filterNot { it.id == id }.forEach { item -> json.put(item.toJson()) }
        prefs.edit().putString(KEY_ROUTINES, json.toString()).apply()
    }

    fun loadRoutineOutcomes(): List<RoutineRunRecord> {
        val records = runCatching { JSONArray(prefs.getString(KEY_ROUTINE_OUTCOMES, "[]")) }.getOrDefault(JSONArray())
        return (0 until records.length()).mapNotNull { index ->
            runCatching {
                val item = records.getJSONObject(index)
                RoutineRunRecord(
                    routineId = item.getString("routineId"),
                    routineName = item.getString("routineName"),
                    scheduledAtMillis = item.getLong("scheduledAt"),
                    outcome = RoutineOutcome.valueOf(item.getString("outcome")),
                    detail = item.optString("detail", ""),
                )
            }.getOrNull()
        }
    }

    fun recordRoutineOutcome(record: RoutineRunRecord) {
        val records = loadRoutineOutcomes()
            .filterNot { it.routineId == record.routineId && it.scheduledAtMillis == record.scheduledAtMillis } + record
        val json = JSONArray()
        records.sortedBy { it.scheduledAtMillis }.takeLast(MAX_ROUTINE_OUTCOMES).forEach { item ->
            json.put(
                JSONObject()
                    .put("routineId", item.routineId)
                    .put("routineName", item.routineName)
                    .put("scheduledAt", item.scheduledAtMillis)
                    .put("outcome", item.outcome.name)
                    .put("detail", item.detail),
            )
        }
        prefs.edit().putString(KEY_ROUTINE_OUTCOMES, json.toString()).apply()
    }

    fun loadPendingCheckIns(): List<PendingRoutineCheckIn> {
        val pending = runCatching { JSONArray(prefs.getString(KEY_PENDING_CHECK_INS, "[]")) }.getOrDefault(JSONArray())
        return (0 until pending.length()).mapNotNull { index ->
            runCatching {
                val item = pending.getJSONObject(index)
                PendingRoutineCheckIn(
                    routineId = item.getString("routineId"),
                    routineName = item.getString("routineName"),
                    scheduledAtMillis = item.getLong("scheduledAt"),
                    minimumAction = item.optString("minimumAction", ""),
                    normalAction = item.optString("normalAction", ""),
                    extraAction = item.optString("extraAction", ""),
                    fallbackPlan = item.optString("fallbackPlan", ""),
                )
            }.getOrNull()
        }
    }

    fun savePendingCheckIn(checkIn: PendingRoutineCheckIn) {
        val pending = (loadPendingCheckIns().filterNot { it.key == checkIn.key } + checkIn).takeLast(MAX_PENDING_CHECK_INS)
        val json = JSONArray()
        pending.forEach { item ->
            json.put(
                JSONObject()
                    .put("routineId", item.routineId)
                    .put("routineName", item.routineName)
                    .put("scheduledAt", item.scheduledAtMillis)
                    .put("minimumAction", item.minimumAction)
                    .put("normalAction", item.normalAction)
                    .put("extraAction", item.extraAction)
                    .put("fallbackPlan", item.fallbackPlan),
            )
        }
        prefs.edit().putString(KEY_PENDING_CHECK_INS, json.toString()).apply()
    }

    fun createPendingCheckIn(session: FocusSession): PendingRoutineCheckIn? {
        val routineId = session.routineId ?: return null
        val checkIn = PendingRoutineCheckIn(
            routineId = routineId,
            routineName = session.routineName,
            scheduledAtMillis = session.startedAtMillis,
            minimumAction = session.minimumAction,
            normalAction = session.normalAction,
            extraAction = session.extraAction,
            fallbackPlan = session.fallbackPlan,
        )
        savePendingCheckIn(checkIn)
        recordRoutineOutcome(
            RoutineRunRecord(
                routineId = routineId,
                routineName = session.routineName,
                scheduledAtMillis = session.startedAtMillis,
                outcome = RoutineOutcome.PENDING,
            ),
        )
        return checkIn
    }

    fun resolvePendingCheckIn(checkIn: PendingRoutineCheckIn, outcome: RoutineOutcome) {
        require(outcome != RoutineOutcome.PENDING)
        recordRoutineOutcome(
            RoutineRunRecord(
                routineId = checkIn.routineId,
                routineName = checkIn.routineName,
                scheduledAtMillis = checkIn.scheduledAtMillis,
                outcome = outcome,
            ),
        )
        val json = JSONArray()
        loadPendingCheckIns().filterNot { it.key == checkIn.key }.forEach { item ->
            json.put(
                JSONObject()
                    .put("routineId", item.routineId)
                    .put("routineName", item.routineName)
                    .put("scheduledAt", item.scheduledAtMillis)
                    .put("minimumAction", item.minimumAction)
                    .put("normalAction", item.normalAction)
                    .put("extraAction", item.extraAction)
                    .put("fallbackPlan", item.fallbackPlan),
            )
        }
        prefs.edit().putString(KEY_PENDING_CHECK_INS, json.toString()).apply()
    }

    private fun FocusRoutine.toJson() = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("intention", intention)
        .put("hour", hour)
        .put("minute", minute)
        .put("days", JSONArray(daysOfWeek.sorted()))
        .put("durationMinutes", durationMinutes)
        .put("apps", JSONArray(blockedPackages.sorted()))
        .put("enabled", enabled)
        .put("minimumAction", minimumAction)
        .put("normalAction", normalAction)
        .put("extraAction", extraAction)
        .put("fallbackPlan", fallbackPlan)

    private companion object {
        const val PREFS = "steady-focus"
        const val KEY_START = "session-start"
        const val KEY_END = "session-end"
        const val KEY_BLOCKED = "session-blocked"
        const val KEY_SELECTED = "selected-apps"
        const val KEY_INTENTION = "session-intention"
        const val KEY_HISTORY = "session-history"
        const val KEY_NOTIFICATIONS_ENABLED = "focus-notifications-enabled"
        const val KEY_ROUTINES = "focus-routines"
        const val MAX_HISTORY_ENTRIES = 20
        const val KEY_ROUTINE_ID = "session-routine-id"
        const val KEY_ROUTINE_NAME = "session-routine-name"
        const val KEY_MINIMUM_ACTION = "session-minimum-action"
        const val KEY_NORMAL_ACTION = "session-normal-action"
        const val KEY_EXTRA_ACTION = "session-extra-action"
        const val KEY_FALLBACK_PLAN = "session-fallback-plan"
        const val KEY_ROUTINE_OUTCOMES = "routine-outcomes"
        const val KEY_PENDING_CHECK_INS = "pending-routine-check-ins"
        const val MAX_ROUTINE_OUTCOMES = 180
        const val MAX_PENDING_CHECK_INS = 10
    }
}
