package com.laki.daofortune.widget

import com.laki.daofortune.core.ModuleReading
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The text of the today widget, as plain data.
 *
 * The widget itself is Glance, which is awkward to unit test; the copy is
 * not. Keeping the string assembly here — pure Kotlin, no Android imports —
 * lets a plain JVM test lock every line the widget can show, including the
 * trap that the date line must come from the date and never from the day's
 * pillar (a String.format slip once printed "WUYIN" there).
 */
data class WidgetCopy(
    val dateLine: String,
    val score: String,
    val verdict: String,
    val bestWindow: String?,
    val headline: String
)

/** Assembles the widget's copy for [date] and its overall [reading]. */
fun widgetCopy(date: LocalDate, reading: ModuleReading): WidgetCopy = WidgetCopy(
    dateLine = date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH))
        .uppercase(Locale.ENGLISH),
    score = reading.score.toString(),
    verdict = reading.verdict.label,
    bestWindow = reading.bestHours.firstOrNull()?.let { "Best window ${it.range}" },
    headline = reading.headline
)
