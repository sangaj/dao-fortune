package com.laki.daofortune.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Decides whether the app may ask for a Play review right now.
 *
 * The ask is deliberately rare and only ever made on a good day: the reader
 * has had the app for a while, today's fortune is exceptional, and a full
 * cooldown has passed since the last time the app asked. Pure logic, kept
 * apart from the Play In-App Review API so the whole rule is unit-testable.
 *
 * Whether the review card actually appears is Google's to decide — the API
 * is quota-based and silent about showing or not showing it, which is exactly
 * why the cooldown is recorded the moment the app asks, not the moment a
 * review might have been left.
 */
object ReviewPrompt {

    /** The app asks only once it has been opened on this many distinct days. */
    const val MIN_DAYS_OPENED = 5

    /** The ask is only made on an exceptional day. */
    const val MIN_SCORE = 85

    /** At least this many days between one ask and the next. */
    const val COOLDOWN_DAYS = 30L

    fun shouldPrompt(
        daysOpened: Int,
        todayScore: Int,
        lastPrompt: LocalDate?,
        today: LocalDate
    ): Boolean {
        if (daysOpened < MIN_DAYS_OPENED) return false
        if (todayScore < MIN_SCORE) return false
        if (lastPrompt != null &&
            ChronoUnit.DAYS.between(lastPrompt, today) < COOLDOWN_DAYS
        ) return false
        return true
    }
}
