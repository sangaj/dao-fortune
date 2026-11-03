package com.laki.daofortune.core

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One day in the forward scan, with the reading it earns. */
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
 * Something the reader wants to do, and the modules that speak for it.
 *
 * The forward scan answers "when should I move" — but the move is never
 * generic. Signing a contract asks the career and the windfall readings; a
 * wedding asks the love reading first of all. Each activity is a weighting
 * over the same five module scores the home screen shows, so a day picked
 * here never disagrees with the readings behind it.
 *
 * The default, [FINANCIAL_MOVE], is the scan the app has always done: the
 * windfall reading alone.
 */
enum class PickActivity(
    val label: String,
    /** The modules that speak for the activity, with their weights. */
    val weights: Map<FortuneModule, Int>
) {
    FINANCIAL_MOVE("a financial move", mapOf(FortuneModule.WINDFALL to 100)),
    SIGNING("signing & deals", mapOf(FortuneModule.CAREER to 60, FortuneModule.WINDFALL to 40)),
    LOVE_WEDDING("love & weddings", mapOf(FortuneModule.LOVE to 80, FortuneModule.OVERALL to 20)),
    MOVE_TRAVEL("moving & travel", mapOf(FortuneModule.OVERALL to 60, FortuneModule.HEALTH to 40)),
    WELLNESS("body & new habits", mapOf(FortuneModule.HEALTH to 80, FortuneModule.OVERALL to 20)),
    LAUNCH("launches & big purchases", mapOf(FortuneModule.WINDFALL to 50, FortuneModule.CAREER to 50));

    /** The module whose headline represents a day picked for this activity. */
    val leadModule: FortuneModule get() = weights.maxByOrNull { it.value }!!.key
}

/**
 * The forward scan: which days ahead are worth acting on.
 *
 * This is the part of the app that answers a question a daily reading cannot:
 * not "is today good" but "**when** should I make the move". Every day in the
 * horizon is scored on the same day-plate rules — no hour is involved, because
 * a day that has not happened yet has no hour — and the results are ranked for
 * the activity the reader has in mind.
 */
object FavourableDates {

    /** How far ahead the scan reaches. */
    const val HORIZON_DAYS = 60

    /** How many days the app shows. */
    const val SHOWN = 5

    /**
     * Scans [horizonDays] days from [start] and returns them ranked for
     * [activity], best first.
     */
    fun scan(
        start: LocalDate,
        zone: ZoneId,
        natal: Branch? = null,
        activity: PickActivity = PickActivity.FINANCIAL_MOVE,
        horizonDays: Int = HORIZON_DAYS,
        shown: Int = SHOWN
    ): List<FavourableDay> {
        val scored = (0 until horizonDays).map { offset ->
            val date = start.plusDays(offset.toLong())
            val plate = LuopanEngine.dayPlate(date, zone)
            val readings = activity.weights.keys.associateWith {
                FortuneEngine.reading(plate, it, natal)
            }
            val score = weightedScore(readings, activity.weights)
            FavourableDay(
                date = date,
                score = score,
                verdict = Verdict.of(score),
                headline = readings.getValue(activity.leadModule).headline,
                dayPillar = plate.day.toString(),
                term = plate.term.term.en,
                isToday = offset == 0
            )
        }
        return scored
            .sortedWith(compareByDescending<FavourableDay> { it.score }.thenBy { it.date })
            .take(shown)
    }

    /** The activity score of one day: the module scores, weighted. */
    fun weightedScore(
        readings: Map<FortuneModule, ModuleReading>,
        weights: Map<FortuneModule, Int>
    ): Int {
        val total = weights.values.sum()
        return readings.entries.sumOf { (module, reading) ->
            reading.score * (weights[module] ?: 0)
        } / total
    }
}
