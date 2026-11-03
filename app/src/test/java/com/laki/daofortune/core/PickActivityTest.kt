package com.laki.daofortune.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Locks the activity picking of the forward scan. The default activity must
 * rank exactly as the scan always has — that is the regression guard — and
 * the other activities are the same module scores, weighted.
 */
class PickActivityTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val start = LocalDate.of(2026, 10, 31)

    @Test
    fun `the default activity is the scan the app has always done`() {
        val days = FavourableDates.scan(start, zone, null, PickActivity.FINANCIAL_MOVE, horizonDays = 14, shown = 14)
        days.forEach { day ->
            val plate = LuopanEngine.dayPlate(day.date, zone)
            val windfall = FortuneEngine.reading(plate, FortuneModule.WINDFALL, null)
            assertEquals(windfall.score, day.score)
            assertEquals(windfall.headline, day.headline)
        }
    }

    @Test
    fun `every activity's weights sum to a hundred and the lead module carries the most`() {
        PickActivity.entries.forEach { activity ->
            assertEquals(100, activity.weights.values.sum())
            assertEquals(activity.weights.maxByOrNull { it.value }!!.key, activity.leadModule)
        }
    }

    @Test
    fun `an activity score is the weighted average of the module scores`() {
        val plate = LuopanEngine.dayPlate(start, zone)
        val activity = PickActivity.SIGNING
        val readings = activity.weights.keys.associateWith { FortuneEngine.reading(plate, it, null) }
        val expected = readings.entries.sumOf { (module, reading) ->
            reading.score * activity.weights.getValue(module)
        } / 100
        assertEquals(expected, FavourableDates.weightedScore(readings, activity.weights))
    }

    @Test
    fun `a wedding day is read by the love reading`() {
        val days = FavourableDates.scan(start, zone, null, PickActivity.LOVE_WEDDING, horizonDays = 14, shown = 14)
        days.forEach { day ->
            val plate = LuopanEngine.dayPlate(day.date, zone)
            val love = FortuneEngine.reading(plate, FortuneModule.LOVE, null)
            assertEquals(love.headline, day.headline)
        }
    }

    @Test
    fun `the scan is deterministic`() {
        val first = FavourableDates.scan(start, zone, null, PickActivity.LAUNCH)
        val second = FavourableDates.scan(start, zone, null, PickActivity.LAUNCH)
        assertEquals(first.map { it.date to it.score }, second.map { it.date to it.score })
    }

    @Test
    fun `the activity changes what the best day is for`() {
        // Over a full horizon the rankings of at least two activities must
        // differ somewhere — otherwise the weights would be dead code.
        val financial = FavourableDates.scan(start, zone, null, PickActivity.FINANCIAL_MOVE)
        val wedding = FavourableDates.scan(start, zone, null, PickActivity.LOVE_WEDDING)
        assertTrue(
            financial.map { it.date } != wedding.map { it.date } ||
                financial.map { it.score } != wedding.map { it.score }
        )
    }
}
