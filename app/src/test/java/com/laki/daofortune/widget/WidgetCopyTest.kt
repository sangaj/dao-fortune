package com.laki.daofortune.widget

import com.laki.daofortune.core.FortuneEngine
import com.laki.daofortune.core.FortuneModule
import com.laki.daofortune.core.LuopanEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Locks every line the today widget can show. Plain JVM test: the copy is
 * assembled by [widgetCopy], pure Kotlin, precisely so this test does not
 * need Glance or a device.
 */
class WidgetCopyTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun `the date line is the date, not the pillar`() {
        // 2026-10-31 is a Saturday; its pillar (WuYin) must never appear here.
        val snapshot = LuopanEngine.snapshot(LocalDateTime.of(2026, 10, 31, 10, 0), zone)
        val reading = FortuneEngine.reading(snapshot, FortuneModule.OVERALL)
        val copy = widgetCopy(snapshot.moment.toLocalDate(), reading)
        assertEquals("SAT 31 OCT", copy.dateLine)
    }

    @Test
    fun `score verdict and headline come straight from the reading`() {
        val snapshot = LuopanEngine.snapshot(LocalDateTime.of(2026, 10, 31, 10, 0), zone)
        val reading = FortuneEngine.reading(snapshot, FortuneModule.OVERALL)
        val copy = widgetCopy(snapshot.moment.toLocalDate(), reading)
        assertEquals(reading.score.toString(), copy.score)
        assertEquals(reading.verdict.label, copy.verdict)
        assertEquals(reading.headline, copy.headline)
        assertTrue(copy.headline.isNotBlank())
    }

    @Test
    fun `the best window line carries the first best hour`() {
        val snapshot = LuopanEngine.snapshot(LocalDateTime.of(2026, 10, 31, 10, 0), zone)
        val reading = FortuneEngine.reading(snapshot, FortuneModule.OVERALL)
        val copy = widgetCopy(snapshot.moment.toLocalDate(), reading)
        val first = reading.bestHours.firstOrNull()
        if (first != null) {
            assertEquals("Best window ${first.range}", copy.bestWindow)
        } else {
            assertNull(copy.bestWindow)
        }
    }

    @Test
    fun `a reading without best hours has no window line`() {
        val plate = LuopanEngine.dayPlate(LocalDate.of(2026, 11, 15), zone)
        val reading = FortuneEngine.reading(plate, FortuneModule.OVERALL)
        val copy = widgetCopy(LocalDate.of(2026, 11, 15), reading.copy(bestHours = emptyList()))
        assertNull(copy.bestWindow)
        assertEquals("SUN 15 NOV", copy.dateLine)
    }
}
