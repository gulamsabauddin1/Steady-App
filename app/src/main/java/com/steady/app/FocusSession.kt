package com.steady.app

data class FocusSession(
    val startedAtMillis: Long,
    val endsAtMillis: Long,
    val blockedPackages: Set<String>,
    val intention: String = "",
) {
    fun isActive(nowMillis: Long): Boolean = endsAtMillis > nowMillis

    companion object {
        fun create(
            nowMillis: Long,
            durationMinutes: Int,
            blockedPackages: Set<String>,
            intention: String = "",
        ): FocusSession {
            require(durationMinutes in MIN_DURATION_MINUTES..MAX_DURATION_MINUTES)
            require(blockedPackages.isNotEmpty())
            return FocusSession(
                startedAtMillis = nowMillis,
                endsAtMillis = nowMillis + durationMinutes * MILLIS_PER_MINUTE,
                blockedPackages = blockedPackages.toSet(),
                intention = intention.trim().take(MAX_INTENTION_LENGTH),
            )
        }

        const val MIN_DURATION_MINUTES = 5
        const val MAX_DURATION_MINUTES = 180
        const val MAX_INTENTION_LENGTH = 80
        private const val MILLIS_PER_MINUTE = 60_000L
    }
}
