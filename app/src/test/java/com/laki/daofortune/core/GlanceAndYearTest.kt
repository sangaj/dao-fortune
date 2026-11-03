package com.laki.daofortune.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Locks the two home-screen glance cards. Both are plain Kotlin over the same
 * plate the readings use, so the tests run on the JVM with no device.
 */
class GlanceAndYearTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val snapshot = LuopanEngine.snapshot(LocalDateTime.of(2026, 10, 31, 10, 0), zone)

    // ------------------------------------------------------------ the year officer

    @Test
    fun `a rat clashes the year of the horse - the fan taisui year`() {
        val reading = YearReading.of(YearSign.RAT, Branch.WU)
        assertEquals(Natal.DayRelation.CLASH, reading.relation)
        assertEquals("Your sign clashes the year", reading.headline)
        assertTrue(reading.body.contains("fan taisui"))
        assertTrue(reading.body.contains("Rat"))
        assertTrue(reading.body.contains("Horse"))
    }

    @Test
    fun `a goat harmonises with the year of the horse`() {
        val reading = YearReading.of(YearSign.GOAT, Branch.WU)
        assertEquals(Natal.DayRelation.HARMONY, reading.relation)
        assertEquals("The year carries you", reading.headline)
    }

    @Test
    fun `a dog joins the frame of the year of the horse`() {
        val reading = YearReading.of(YearSign.DOG, Branch.WU)
        assertEquals(Natal.DayRelation.TRIAD, reading.relation)
        assertEquals("You join the year's frame", reading.headline)
    }

    @Test
    fun `an ox quietly harms the year of the horse`() {
        val reading = YearReading.of(YearSign.OX, Branch.WU)
        assertEquals(Natal.DayRelation.HARM, reading.relation)
        assertEquals("A year of small frictions", reading.headline)
    }

    @Test
    fun `a monkey stands apart from the year of the horse`() {
        val reading = YearReading.of(YearSign.MONKEY, Branch.WU)
        assertEquals(Natal.DayRelation.NEUTRAL, reading.relation)
        assertEquals("The year is yours to shape", reading.headline)
    }

    @Test
    fun `the teaser names the year animal`() {
        assertTrue(YearReading.teaser(Branch.WU).contains("Horse"))
    }

    // ------------------------------------------------------------- the day glance

    @Test
    fun `the five-phase colours are the classical ones`() {
        assertEquals("Green", DayGlance.colourOf(WuXing.WOOD))
        assertEquals("Red", DayGlance.colourOf(WuXing.FIRE))
        assertEquals("Yellow", DayGlance.colourOf(WuXing.EARTH))
        assertEquals("White", DayGlance.colourOf(WuXing.METAL))
        assertEquals("Black", DayGlance.colourOf(WuXing.WATER))
    }

    @Test
    fun `the glance wears the colour of the wealth element`() {
        val glance = DayGlance.of(snapshot)
        assertEquals(DayGlance.colourOf(snapshot.wealthElement), glance.colour)
        assertTrue(glance.colourNote.contains(snapshot.wealthElement.en))
    }

    @Test
    fun `the glance can never disagree with the full reading on direction`() {
        val glance = DayGlance.of(snapshot)
        val direction = FortuneEngine.wealthDirection(snapshot)
        // Both surfaces are built from one source.
        assertEquals(direction.short, glance.direction)
        assertEquals(direction.role, glance.directionNote)
        // And the reading's own line names the same bearing and phase.
        val label = FortuneEngine.wealthDirectionLabel(snapshot)
        assertTrue(label.contains(direction.compass))
        assertTrue(label.contains(direction.pinyin))
        assertTrue(label.contains(direction.element.en))
    }

    @Test
    fun `the glance never says the same thing twice`() {
        val glance = DayGlance.of(snapshot)
        // The value names the thing; the note says what it is for. A glance
        // that repeats itself is a glance the reader reads twice.
        assertFalse(glance.directionNote.contains(glance.direction))
        assertFalse(glance.colourNote.contains(glance.colour))
        assertFalse(glance.bestHourLabel.contains(glance.bestHourRange))
    }

    @Test
    fun `the best hour on the glance is the plate's own best hour`() {
        val glance = DayGlance.of(snapshot)
        val best = snapshot.bestHours.first()
        assertEquals(best.range, glance.bestHourRange)
        assertTrue(glance.bestHourLabel.contains(best.branch.pinyin))
        assertTrue(glance.bestHourLabel.contains(best.officer.en))
    }

    @Test
    fun `the year the card names turns at Li Chun, not on New Year's Day`() {
        // 2026 is a Horse year; it runs from Li Chun 2026 to Li Chun 2027.
        assertEquals(2026, CalendarEngine.yearNumber(LocalDateTime.of(2026, 10, 31, 12, 0), zone))
        assertEquals(2026, snapshot.yearNumber)
        // Still the old year in early February, before the new one begins.
        assertEquals(2026, CalendarEngine.yearNumber(LocalDateTime.of(2027, 1, 20, 12, 0), zone))
        // And the new one once Li Chun has passed.
        assertEquals(2027, CalendarEngine.yearNumber(LocalDateTime.of(2027, 2, 10, 12, 0), zone))
    }
}
