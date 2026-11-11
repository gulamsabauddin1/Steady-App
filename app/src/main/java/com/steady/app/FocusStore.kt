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
        return FocusSession(start, end, blocked.toSet(), prefs.getString(KEY_INTENTION, "").orEmpty())
    }

    fun saveSession(session: FocusSession) {
        prefs.edit()
            .putLong(KEY_START, session.startedAtMillis)
            .putLong(KEY_END, session.endsAtMillis)
            .putStringSet(KEY_BLOCKED, session.blockedPackages.toSet())
            .putString(KEY_INTENTION, session.intention)
            .apply()
    }

    fun clearSession() {
        prefs.edit().remove(KEY_START).remove(KEY_END).remove(KEY_BLOCKED).remove(KEY_INTENTION).apply()
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
                )
            }.getOrNull()
        }
    }

    fun saveRoutine(routine: FocusRoutine) {
        val routines = (loadRoutines().filterNot { it.id == routine.id } + routine).sortedBy { it.hour * 60 + it.minute }
        val json = JSONArray()
        routines.forEach { item ->
            json.put(
                JSONObject()
                    .put("id", item.id)
                    .put("name", item.name)
                    .put("intention", item.intention)
                    .put("hour", item.hour)
                    .put("minute", item.minute)
                    .put("days", JSONArray(item.daysOfWeek.sorted()))
                    .put("durationMinutes", item.durationMinutes)
                    .put("apps", JSONArray(item.blockedPackages.sorted()))
                    .put("enabled", item.enabled),
            )
        }
        prefs.edit().putString(KEY_ROUTINES, json.toString()).apply()
    }

    fun deleteRoutine(id: String) {
        val json = JSONArray()
        loadRoutines().filterNot { it.id == id }.forEach { item ->
            json.put(
                JSONObject()
                    .put("id", item.id)
                    .put("name", item.name)
                    .put("intention", item.intention)
                    .put("hour", item.hour)
                    .put("minute", item.minute)
                    .put("days", JSONArray(item.daysOfWeek.sorted()))
                    .put("durationMinutes", item.durationMinutes)
                    .put("apps", JSONArray(item.blockedPackages.sorted()))
                    .put("enabled", item.enabled),
            )
        }
        prefs.edit().putString(KEY_ROUTINES, json.toString()).apply()
    }

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
    }
}
