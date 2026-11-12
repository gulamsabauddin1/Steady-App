package com.steady.app

import org.junit.Assert.assertEquals
import org.junit.Test

class RoutineProgressTest {
    @Test
    fun `weekly summary counts completed missed pending and skipped routine occurrences`() {
        val now = 100L * DAY_MILLIS
        val records = listOf(
            record("r1", now - 6 * DAY_MILLIS, RoutineOutcome.MINIMUM),
            record("r1", now - 2 * DAY_MILLIS, RoutineOutcome.MISSED),
            record("r2", now - DAY_MILLIS, RoutineOutcome.PENDING),
            record("r3", now, RoutineOutcome.SKIPPED),
        )

        val summary = summarizeRoutineOutcomes(records, now)

        assertEquals(4, summary.total)
        assertEquals(1, summary.completed)
        assertEquals(1, summary.missed)
        assertEquals(1, summary.pending)
        assertEquals(1, summary.skipped)
    }

    @Test
    fun `weekly summary excludes occurrences outside the last seven days and in the future`() {
        val now = 100L * DAY_MILLIS
        val records = listOf(
            record("old", now - 8 * DAY_MILLIS, RoutineOutcome.NORMAL),
            record("future", now + DAY_MILLIS, RoutineOutcome.EXTRA),
            record("current", now - 7 * DAY_MILLIS, RoutineOutcome.EXTRA),
        )

        val summary = summarizeRoutineOutcomes(records, now)

        assertEquals(1, summary.total)
        assertEquals(1, summary.completed)
        assertEquals(0, summary.missed)
    }

    private fun record(id: String, scheduledAt: Long, outcome: RoutineOutcome) = RoutineRunRecord(
        routineId = id,
        routineName = id,
        scheduledAtMillis = scheduledAt,
        outcome = outcome,
    )

    private companion object {
        const val DAY_MILLIS = 24 * 60 * 60 * 1000L
    }
}
