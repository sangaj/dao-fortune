package com.laki.daofortune.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Locks the rule for the Play review ask: enough days with the app, an
 * exceptional day, and a full cooldown since the last ask. Each condition
 * alone must be able to keep the ask down, and the boundaries are exact.
 */
class ReviewPromptTest {

    private val today = LocalDate.of(2026, 11, 3)

    @Test
    fun `too few days with the app never prompts`() {
        assertFalse(
            ReviewPrompt.shouldPrompt(
                daysOpened = ReviewPrompt.MIN_DAYS_OPENED - 1,
                todayScore = 100,
                lastPrompt = null,
                today = today
            )
        )
    }

    @Test
    fun `the exact minimum of days can prompt`() {
        assertTrue(
            ReviewPrompt.shouldPrompt(
                daysOpened = ReviewPrompt.MIN_DAYS_OPENED,
                todayScore = 90,
                lastPrompt = null,
                today = today
            )
        )
    }

    @Test
    fun `a merely good day never prompts`() {
        assertFalse(
            ReviewPrompt.shouldPrompt(
                daysOpened = 30,
                todayScore = ReviewPrompt.MIN_SCORE - 1,
                lastPrompt = null,
                today = today
            )
        )
    }

    @Test
    fun `the exact minimum score can prompt`() {
        assertTrue(
            ReviewPrompt.shouldPrompt(
                daysOpened = 30,
                todayScore = ReviewPrompt.MIN_SCORE,
                lastPrompt = null,
                today = today
            )
        )
    }

    @Test
    fun `inside the cooldown never prompts`() {
        assertFalse(
            ReviewPrompt.shouldPrompt(
                daysOpened = 30,
                todayScore = 95,
                lastPrompt = today.minusDays(ReviewPrompt.COOLDOWN_DAYS - 1),
                today = today
            )
        )
    }

    @Test
    fun `the first day after the cooldown can prompt`() {
        assertTrue(
            ReviewPrompt.shouldPrompt(
                daysOpened = 30,
                todayScore = 95,
                lastPrompt = today.minusDays(ReviewPrompt.COOLDOWN_DAYS),
                today = today
            )
        )
    }

    @Test
    fun `never asked before can prompt`() {
        assertTrue(
            ReviewPrompt.shouldPrompt(
                daysOpened = ReviewPrompt.MIN_DAYS_OPENED,
                todayScore = ReviewPrompt.MIN_SCORE,
                lastPrompt = null,
                today = today
            )
        )
    }
}
