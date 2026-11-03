package com.laki.daofortune.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Decides whether the app may ask for a Play review right now.
 *
 * The ask is deliberately rare, and it is **never tied to the day's luck**. An
 * app that asks for a review *because the reading happened to be excellent*
 * reads as trading a good day for a five-star rating, and Play's reviewers
 * are entitled to see it that way. So the rule asks about the reader's
 * relationship with the app instead: they have lived with it for a while,
 * they have used it for real — the full readings are behind the unlock, so
 * unlocking is the proof — and a long cooldown has passed since the last
 * time the app asked.
 *
 * Whether the review card actually appears is Google's to decide — the API
 * is quota-based and silent about showing or not showing it, which is exactly
 * why the cooldown is recorded the moment the app asks, not the moment a
 * review might have been left.
 */
object ReviewPrompt {

    /** The app asks only once it has been opened on this many distinct days. */
    const val MIN_DAYS_OPENED = 5

    /** At least this many days between one ask and the next. */
    const val COOLDOWN_DAYS = 30L

    /**
     * Whether the app may ask for a review today.
     *
     * @param daysOpened how many distinct days the app has been opened on
     * @param hasUnlocked whether the reader has bought the full readings —
     *   the proof that they have used the app for what it is
     * @param lastPrompt the day the app last asked, if it ever did
     */
    fun shouldPrompt(
        daysOpened: Int,
        hasUnlocked: Boolean,
        lastPrompt: LocalDate?,
        today: LocalDate
    ): Boolean {
        if (daysOpened < MIN_DAYS_OPENED) return false
        if (!hasUnlocked) return false
        if (lastPrompt != null &&
            ChronoUnit.DAYS.between(lastPrompt, today) < COOLDOWN_DAYS
        ) return false
        return true
    }
}
