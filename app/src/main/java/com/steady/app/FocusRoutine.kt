package com.steady.app

import java.util.UUID

data class FocusRoutine(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val intention: String,
    val hour: Int,
    val minute: Int,
    val daysOfWeek: Set<Int>,
    val durationMinutes: Int,
    val blockedPackages: Set<String>,
    val enabled: Boolean = true,
) {
    init {
        require(name.isNotBlank())
        require(hour in 0..23 && minute in 0..59)
        require(daysOfWeek.all { it in 1..7 })
        require(daysOfWeek.isNotEmpty())
        require(durationMinutes in FocusSession.MIN_DURATION_MINUTES..FocusSession.MAX_DURATION_MINUTES)
        require(blockedPackages.isNotEmpty())
    }
}
