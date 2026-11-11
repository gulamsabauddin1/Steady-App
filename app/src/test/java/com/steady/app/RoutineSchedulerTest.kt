package com.steady.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class RoutineSchedulerTest {
    private val utc = ZoneId.of("UTC")

    @Test
    fun `routine chooses its next upcoming local time`() {
        val routine = routine(days = setOf(DayOfWeek.MONDAY.value))
        val before = LocalDateTime.of(2026, 11, 9, 7, 59).atZone(utc).toInstant().toEpochMilli()
        val expected = LocalDateTime.of(2026, 11, 9, 8, 0).atZone(utc).toInstant().toEpochMilli()

        assertEquals(expected, RoutineScheduler.nextTriggerMillis(routine, before, utc))
    }

    @Test
    fun `routine at current time schedules for its next selected day`() {
        val routine = routine(days = setOf(DayOfWeek.MONDAY.value))
        val current = LocalDateTime.of(2026, 11, 9, 8, 0).atZone(utc).toInstant().toEpochMilli()
        val expected = LocalDateTime.of(2026, 11, 16, 8, 0).atZone(utc).toInstant().toEpochMilli()

        assertEquals(expected, RoutineScheduler.nextTriggerMillis(routine, current, utc))
    }

    @Test
    fun `disabled routine has no scheduled occurrence`() {
        val routine = routine(days = setOf(DayOfWeek.TUESDAY.value)).copy(enabled = false)

        assertNull(RoutineScheduler.nextTriggerMillis(routine, 0L, utc))
    }

    private fun routine(days: Set<Int>) = FocusRoutine(
        id = "routine-test",
        name = "Study",
        intention = "Review notes",
        hour = 8,
        minute = 0,
        daysOfWeek = days,
        durationMinutes = 25,
        blockedPackages = setOf("social.app"),
    )
}
