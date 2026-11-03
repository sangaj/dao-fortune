package com.laki.daofortune.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * The Sunday-evening look at the week ahead.
 *
 * Every Sunday evening the app works out the coming seven days and names the
 * best one. The picking and the words live here, away from WorkManager and
 * the notification API, so the whole behaviour is unit-testable on the JVM.
 */
object WeekAhead {

    /** The outlook always covers the seven days that start tomorrow. */
    const val LOOK_AHEAD_DAYS = 7

    /** Sunday at eight in the evening, local time. */
    val FIRE_AT: LocalTime = LocalTime.of(20, 0)

    /**
     * The moment the outlook should fire next.
     *
     * Fired on a Sunday before eight in the evening, that same evening is
     * still ahead; after it, the next Sunday is meant.
     */
    fun nextFire(now: LocalDateTime): LocalDateTime {
        val today = now.toLocalDate()
        val daysAhead = (DayOfWeek.SUNDAY.value - today.dayOfWeek.value + 7) % 7
        var next = today.plusDays(daysAhead.toLong()).atTime(FIRE_AT)
        if (!next.isAfter(now)) next = next.plusWeeks(1)
        return next
    }

    /**
     * The seven days of the coming calendar week, Monday to Sunday.
     *
     * A **calendar** week, not "the next seven days": WorkManager does not
     * fire exactly when it is asked to, and if the note slipped from Sunday
     * to Monday then "the next seven days" would run from Tuesday to Monday —
     * a week that is not the week the note talks about. Anchoring on the
     * Monday keeps the promise true however late the note arrives.
     */
    fun weekDays(today: LocalDate): List<LocalDate> {
        val monday = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        return (0 until LOOK_AHEAD_DAYS).map { monday.plusDays(it.toLong()) }
    }

    /**
     * The best day of the outlook, or null when there are no days.
     *
     * Days are (date, score) pairs in any order; a tie goes to the earlier
     * day — the first good day is the one worth waiting for least.
     */
    fun bestDay(days: List<Pair<LocalDate, Int>>): Pair<LocalDate, Int>? {
        return days.minWithOrNull(compareByDescending<Pair<LocalDate, Int>> { it.second }.thenBy { it.first })
    }

    /** The one line the notification carries. */
    fun line(day: LocalDate, score: Int): String {
        val weekday = day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        return "Your best day next week is $weekday — $score/100, ${Verdict.of(score).label}."
    }
}
