package com.steady.app

data class FocusSession(
    val startedAtMillis: Long,
    val endsAtMillis: Long,
    val blockedPackages: Set<String>,
    val intention: String = "",
    val routineId: String? = null,
    val routineName: String = "",
    val minimumAction: String = "",
    val normalAction: String = "",
    val extraAction: String = "",
    val fallbackPlan: String = "",
) {
    fun isActive(nowMillis: Long): Boolean = endsAtMillis > nowMillis

    companion object {
        fun create(
            nowMillis: Long,
            durationMinutes: Int,
            blockedPackages: Set<String>,
            intention: String = "",
            routine: FocusRoutine? = null,
        ): FocusSession {
            require(durationMinutes in MIN_DURATION_MINUTES..MAX_DURATION_MINUTES)
            require(blockedPackages.isNotEmpty())
            return FocusSession(
                startedAtMillis = nowMillis,
                endsAtMillis = nowMillis + durationMinutes * MILLIS_PER_MINUTE,
                blockedPackages = blockedPackages.toSet(),
                intention = intention.trim().take(MAX_INTENTION_LENGTH),
                routineId = routine?.id,
                routineName = routine?.name.orEmpty(),
                minimumAction = routine?.minimumAction.orEmpty(),
                normalAction = routine?.normalAction.orEmpty(),
                extraAction = routine?.extraAction.orEmpty(),
                fallbackPlan = routine?.fallbackPlan.orEmpty(),
            )
        }

        const val MIN_DURATION_MINUTES = 5
        const val MAX_DURATION_MINUTES = 180
        const val MAX_INTENTION_LENGTH = 80
        private const val MILLIS_PER_MINUTE = 60_000L
    }
}
