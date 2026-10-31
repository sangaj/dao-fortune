package com.laki.daofortune.core

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One day in the forward scan, with the windfall reading it earns. */
data class FavourableDay(
    val date: LocalDate,
    val score: Int,
    val verdict: Verdict,
    val headline: String,
    val dayPillar: String,
    val term: String,
    val isToday: Boolean
) {
    val label: String
        get() = date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH))
}

/**
 * The forward scan: which days ahead are worth acting on.
 *
 * This is the part of the app that answers a question a daily reading cannot:
 * not "is today good" but "**when** should I make the move". Every day in the
 * horizon is scored on the same day-plate rules — no hour is involved, because
 * a day that has not happened yet has no hour — and the results are ranked by
 * the windfall reading, which is where the classical rules about wealth, the
 * season and the sun's mountain do their work.
 */
object FavourableDates {

    /** How far ahead the scan reaches. */
    const val HORIZON_DAYS = 60

    /** How many days the app shows. */
    const val SHOWN = 5

    /**
     * Scans [horizonDays] days from [start] and returns them ranked by the
     * windfall reading, best first.
     */
    fun scan(
        start: LocalDate,
        zone: ZoneId,
        natal: Branch? = null,
        horizonDays: Int = HORIZON_DAYS,
        shown: Int = SHOWN
    ): List<FavourableDay> {
        val scored = (0 until horizonDays).map { offset ->
            val date = start.plusDays(offset.toLong())
            val plate = LuopanEngine.dayPlate(date, zone)
            val reading = FortuneEngine.reading(plate, FortuneModule.WINDFALL, natal)
            FavourableDay(
                date = date,
                score = reading.score,
                verdict = reading.verdict,
                headline = reading.headline,
                dayPillar = plate.day.toString(),
                term = plate.term.term.en,
                isToday = offset == 0
            )
        }
        return scored
            .sortedWith(compareByDescending<FavourableDay> { it.score }.thenBy { it.date })
            .take(shown)
    }
}
