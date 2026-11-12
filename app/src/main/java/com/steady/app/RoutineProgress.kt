package com.steady.app

enum class RoutineOutcome {
    PENDING,
    MINIMUM,
    NORMAL,
    EXTRA,
    MISSED,
    SKIPPED,
}

data class RoutineRunRecord(
    val routineId: String,
    val routineName: String,
    val scheduledAtMillis: Long,
    val outcome: RoutineOutcome,
    val detail: String = "",
)

data class PendingRoutineCheckIn(
    val routineId: String,
    val routineName: String,
    val scheduledAtMillis: Long,
    val minimumAction: String,
    val normalAction: String,
    val extraAction: String,
    val fallbackPlan: String,
) {
    val key: String get() = "$routineId:$scheduledAtMillis"
}

data class RoutineWeeklySummary(
    val total: Int,
    val completed: Int,
    val missed: Int,
    val pending: Int,
    val skipped: Int,
    val minimumCount: Int,
) {
    val completionPercent: Int
        get() = if (total == 0) 0 else (completed * 100 / total)
}

fun summarizeRoutineOutcomes(
    records: List<RoutineRunRecord>,
    nowMillis: Long = System.currentTimeMillis(),
): RoutineWeeklySummary {
    val weekMillis = 7L * 24 * 60 * 60 * 1000
    val recent = records.filter { it.scheduledAtMillis in (nowMillis - weekMillis)..nowMillis }
    return RoutineWeeklySummary(
        total = recent.size,
        completed = recent.count { it.outcome in setOf(RoutineOutcome.MINIMUM, RoutineOutcome.NORMAL, RoutineOutcome.EXTRA) },
        missed = recent.count { it.outcome == RoutineOutcome.MISSED },
        pending = recent.count { it.outcome == RoutineOutcome.PENDING },
        skipped = recent.count { it.outcome == RoutineOutcome.SKIPPED },
        minimumCount = recent.count { it.outcome == RoutineOutcome.MINIMUM },
    )
}
