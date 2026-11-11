package com.steady.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusSessionTest {
    @Test
    fun `session ends after selected duration`() {
        val now = 1_000_000L
        val session = FocusSession.create(now, 25, setOf("social.app"))

        assertTrue(session.isActive(now))
        assertTrue(session.isActive(now + 24 * 60_000L))
        assertFalse(session.isActive(now + 25 * 60_000L))
        assertEquals(now + 25 * 60_000L, session.endsAtMillis)
    }

    @Test
    fun `session freezes a copy of selected package list`() {
        val mutable = mutableSetOf("video.app")
        val session = FocusSession.create(0L, 15, mutable)
        mutable += "game.app"

        assertEquals(setOf("video.app"), session.blockedPackages)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `session rejects duration below minimum`() {
        FocusSession.create(0L, 4, setOf("social.app"))
    }

    @Test
    fun `custom duration and intention are kept within supported limits`() {
        val session = FocusSession.create(0L, 120, setOf("social.app"), "  Write my essay  ")

        assertEquals(120 * 60_000L, session.endsAtMillis)
        assertEquals("Write my essay", session.intention)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `session needs at least one blocked app`() {
        FocusSession.create(0L, 15, emptySet())
    }
}
