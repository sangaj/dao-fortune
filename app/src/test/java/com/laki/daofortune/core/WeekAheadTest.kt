package com.laki.daofortune.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Locks the Sunday-evening outlook: when it fires next, which day it names,
 * and the exact words the notification carries.
 */
class WeekAheadTest {

    @Test
    fun `a weekday aims for the coming Sunday evening`() {
        // 2026-11-03 is a Tuesday.
        val now = LocalDateTime.of(2026, 11, 3, 10, 30)
        val next = WeekAhead.nextFire(now)
        assertEquals(DayOfWeek.SUNDAY, next.dayOfWeek)
        assertEquals(LocalDate.of(2026, 11, 8), next.toLocalDate())
        assertEquals(WeekAhead.FIRE_AT, next.toLocalTime())
    }

    @Test
    fun `Sunday before eight in the evening still means today`() {
        val now = LocalDateTime.of(2026, 11, 8, 19, 59)
        assertEquals(
            LocalDateTime.of(2026, 11, 8, 20, 0),
            WeekAhead.nextFire(now)
        )
    }

    @Test
    fun `Sunday at eight in the evening means next week`() {
        val now = LocalDateTime.of(2026, 11, 8, 20, 0)
        assertEquals(
            LocalDateTime.of(2026, 11, 15, 20, 0),
            WeekAhead.nextFire(now)
        )
    }

    @Test
    fun `Sunday after eight in the evening means next week`() {
        val now = LocalDateTime.of(2026, 11, 8, 22, 15)
        assertEquals(
            LocalDateTime.of(2026, 11, 15, 20, 0),
            WeekAhead.nextFire(now)
        )
    }

    @Test
    fun `the best day is the highest score`() {
        val monday = LocalDate.of(2026, 11, 9)
        val days = listOf(
            monday to 60,
            monday.plusDays(1) to 88,
            monday.plusDays(2) to 71
        )
        assertEquals(monday.plusDays(1) to 88, WeekAhead.bestDay(days))
    }

    @Test
    fun `a tie goes to the earlier day`() {
        val monday = LocalDate.of(2026, 11, 9)
        val days = listOf(
            monday.plusDays(3) to 90,
            monday to 90
        )
        assertEquals(monday to 90, WeekAhead.bestDay(days))
    }

    @Test
    fun `no days means no outlook`() {
        assertNull(WeekAhead.bestDay(emptyList()))
    }

    @Test
    fun `the notification line names the day, the score and the verdict`() {
        // 2026-11-10 is a Tuesday; 87 sits in the Exceptional band.
        assertEquals(
            "Your best day next week is Tuesday — 87/100, Exceptional.",
            WeekAhead.line(LocalDate.of(2026, 11, 10), 87)
        )
    }
}
