package com.laki.daofortune.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.floor
import kotlin.math.sin

/**
 * Solar longitude and the twenty-four solar terms (jie qi).
 *
 * Accuracy note: the sun's apparent longitude is computed with Meeus' low-precision
 * formulae, which carry an error of roughly 0.01 degrees — about fifteen minutes of
 * time near the equinoxes. Solar-term *dates* are therefore correct to the day except
 * in the rare case where a term falls within ~15 minutes of midnight.
 */
object SolarTerms {

    private const val J2000 = 2451545.0

    /** A solar term together with the moment it occurs. */
    data class Term(
        val longitude: Int,
        val pinyin: String,
        val chinese: String,
        val en: String,
        /** True for the twelve "section" terms that open a lunar month. */
        val isJie: Boolean,
        /** The branch of the month this term opens (jie only). */
        val monthBranch: Branch?
    )

    /** Twenty-four terms ordered by solar longitude starting at 315 (Beginning of Spring). */
    val terms: List<Term> = listOf(
        Term(315, "Lichun", "立春", "Beginning of Spring", true, Branch.YIN),
        Term(330, "Yushui", "雨水", "Rain Water", false, null),
        Term(345, "Jingzhe", "惊蛰", "Awakening of Insects", true, Branch.MAO),
        Term(0, "Chunfen", "春分", "Spring Equinox", false, null),
        Term(15, "Qingming", "清明", "Pure Brightness", true, Branch.CHEN),
        Term(30, "Guyu", "谷雨", "Grain Rain", false, null),
        Term(45, "Lixia", "立夏", "Beginning of Summer", true, Branch.SI),
        Term(60, "Xiaoman", "小满", "Grain Buds", false, null),
        Term(75, "Mangzhong", "芒种", "Grain in Ear", true, Branch.WU),
        Term(90, "Xiazhi", "夏至", "Summer Solstice", false, null),
        Term(105, "Xiaoshu", "小暑", "Minor Heat", true, Branch.WEI),
        Term(120, "Dashu", "大暑", "Major Heat", false, null),
        Term(135, "Liqiu", "立秋", "Beginning of Autumn", true, Branch.SHEN),
        Term(150, "Chushu", "处暑", "End of Heat", false, null),
        Term(165, "Bailu", "白露", "White Dew", true, Branch.YOU),
        Term(180, "Qiufen", "秋分", "Autumn Equinox", false, null),
        Term(195, "Hanlu", "寒露", "Cold Dew", true, Branch.XU),
        Term(210, "Shuangjiang", "霜降", "Frost's Descent", false, null),
        Term(225, "Lidong", "立冬", "Beginning of Winter", true, Branch.HAI),
        Term(240, "Xiaoxue", "小雪", "Minor Snow", false, null),
        Term(255, "Daxue", "大雪", "Major Snow", true, Branch.ZI),
        Term(270, "Dongzhi", "冬至", "Winter Solstice", false, null),
        Term(285, "Xiaohan", "小寒", "Minor Cold", true, Branch.CHOU),
        Term(300, "Dahan", "大寒", "Major Cold", false, null)
    )

    /** A term resolved to an exact instant. */
    data class TermMoment(val term: Term, val instant: Instant)

    private val cache = HashMap<Int, List<TermMoment>>()

    /** Julian Day for a Gregorian date, at the given UTC hour fraction. */
    fun julianDay(date: LocalDate, hourUt: Double): Double {
        var y = date.year
        var m = date.monthValue
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        val day = date.dayOfMonth + hourUt / 24.0
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    /** Instant -> Julian Day in Terrestrial Time (TT). */
    fun julianDayTT(instant: Instant): Double =
        julianDay(instant.atZone(ZoneOffset.UTC).toLocalDate(),
            instant.atZone(ZoneOffset.UTC).hour + instant.atZone(ZoneOffset.UTC).minute / 60.0) +
            deltaT(instant.atZone(ZoneOffset.UTC).year + 0.5) / 86400.0

    /** TT difference (seconds) for a given decimal year — Espenak & Meeus polynomials. */
    fun deltaT(year: Double): Double {
        val y = year
        return when {
            y < 1900 -> {
                val t = (y - 1820) / 100.0
                -20 + 32 * t * t
            }
            y < 1920 -> {
                val t = y - 1900
                -2.79 + 1.494119 * t - 0.0598939 * t * t + 0.0061966 * t * t * t - 0.000197 * t * t * t * t
            }
            y < 1941 -> {
                val t = y - 1920
                21.20 + 0.84493 * t - 0.076100 * t * t + 0.0020936 * t * t * t
            }
            y < 1961 -> {
                val t = y - 1950
                29.07 + 0.407 * t - t * t / 233.0 + t * t * t / 2547.0
            }
            y < 1986 -> {
                val t = y - 1975
                45.45 + 1.067 * t - t * t / 260.0 - t * t * t / 718.0
            }
            y < 2005 -> {
                val t = y - 2000
                63.86 + 0.3345 * t - 0.060374 * t * t + 0.0017275 * t * t * t +
                    0.000651814 * t * t * t * t + 0.00002373599 * t * t * t * t * t
            }
            y < 2050 -> {
                val t = y - 2000
                62.92 + 0.32217 * t + 0.005589 * t * t
            }
            y < 2150 -> {
                val u = (y - 1820) / 100.0
                -20 + 32 * u * u - 0.5628 * (2150 - y)
            }
            else -> {
                val u = (y - 1820) / 100.0
                -20 + 32 * u * u
            }
        }
    }

    /** Apparent geocentric longitude of the sun, in degrees, for a TT Julian Day. */
    fun sunApparentLongitude(jde: Double): Double {
        val t = (jde - J2000) / 36525.0
        val l0 = 280.46646 + 36000.76983 * t + 0.0003032 * t * t
        val m = 357.52911 + 35999.05029 * t - 0.0001537 * t * t
        val mr = Math.toRadians(m)
        val c = (1.914602 - 0.004817 * t - 0.000014 * t * t) * sin(mr) +
            (0.019993 - 0.000101 * t) * sin(2 * mr) +
            0.000289 * sin(3 * mr)
        val trueLong = l0 + c
        val omega = 125.04 - 1934.136 * t
        val apparent = trueLong - 0.00569 - 0.00478 * sin(Math.toRadians(omega))
        return ((apparent % 360.0) + 360.0) % 360.0
    }

    /** Signed shortest difference between the sun's longitude and a target, in degrees. */
    private fun longitudeDelta(jde: Double, target: Double): Double {
        var d = sunApparentLongitude(jde) - target
        d = ((d + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
        return d
    }

    /** Finds the TT Julian Day at which the sun's longitude crosses [target]. */
    private fun findCrossing(target: Double, guessJd: Double): Double {
        var lo = guessJd
        var hi = guessJd

        // Walk forward a day at a time until the sign of (longitude - target) flips.
        var cursor = guessJd
        var deltaPrev = longitudeDelta(cursor, target)
        var found = false
        var steps = 0
        while (steps < 40 && !found) {
            cursor += 1.0
            val deltaNow = longitudeDelta(cursor, target)
            if (deltaPrev <= 0 && deltaNow >= 0) {
                lo = cursor - 1.0
                hi = cursor
                found = true
            }
            deltaPrev = deltaNow
            steps++
        }

        if (!found) {
            // The guess sat after the crossing: walk backwards instead.
            cursor = guessJd
            deltaPrev = longitudeDelta(cursor, target)
            steps = 0
            while (steps < 40 && !found) {
                cursor -= 1.0
                val deltaNow = longitudeDelta(cursor, target)
                if (deltaPrev >= 0 && deltaNow <= 0) {
                    lo = cursor
                    hi = cursor + 1.0
                    found = true
                }
                deltaPrev = deltaNow
                steps++
            }
        }
        if (!found) return guessJd

        // Bisect: the crossing sits between lo (before) and hi (after).
        var a = lo
        var b = hi
        repeat(60) {
            val mid = (a + b) / 2
            if (longitudeDelta(mid, target) <= 0) a = mid else b = mid
        }
        return (a + b) / 2
    }

    /** All twenty-four solar-term moments occurring within the given calendar year. */
    @Synchronized
    fun momentsForYear(year: Int): List<TermMoment> = cache.getOrPut(year) {
        val jdJan1 = julianDay(LocalDate.of(year, 1, 1), 12.0)
        val list = terms.map { term ->
            // Sun longitude grows ~0.9856 deg/day; day-of-year guess for each 15-degree step.
            val dayOfYearGuess = 79.0 + term.longitude * 365.2422 / 360.0
            var guess = jdJan1 + (dayOfYearGuess - 1.0)
            if (dayOfYearGuess > 366.0) guess -= 365.0
            var jde = findCrossing(term.longitude.toDouble(), guess)
            // Convert TT -> UT.
            val dtSeconds = deltaT(year + 0.5)
            val jdUt = jde - dtSeconds / 86400.0
            val instant = instantFromJulianDay(jdUt)
            TermMoment(term, instant)
        }.sortedBy { it.instant }
        list
    }

    /** Converts a Julian Day (UT) to an instant. */
    fun instantFromJulianDay(jd: Double): Instant {
        val z = floor(jd + 0.5)
        val f = jd + 0.5 - z
        val alpha = floor((z - 1867216.25) / 36524.25)
        val a = if (z < 2299161.0) z else z + 1 + alpha - floor(alpha / 4)
        val b = a + 1524
        val c = floor((b - 122.1) / 365.25)
        val d = floor(365.25 * c)
        val e = floor((b - d) / 30.6001)
        val dayFraction = b - d - floor(30.6001 * e) + f
        val day = floor(dayFraction).toInt()
        val month = if (e < 14) (e - 1).toInt() else (e - 13).toInt()
        val year = if (month > 2) (c - 4716).toInt() else (c - 4715).toInt()
        val hours = (dayFraction - day) * 24.0
        val hour = floor(hours)
        val minute = floor((hours - hour) * 60)
        val second = floor(((hours - hour) * 60 - minute) * 60)
        val localDate = LocalDate.of(year, month, day)
        return localDate.atTime(hour.toInt(), minute.toInt(), second.toInt()).toInstant(ZoneOffset.UTC)
    }

    /** The most recent solar term at or before [moment]. */
    fun currentTerm(moment: LocalDateTime, zone: ZoneId): TermMoment {
        val instant = moment.atZone(zone).toInstant()
        val year = moment.year
        val all = (year - 1..year + 1).flatMap { momentsForYear(it) }.sortedBy { it.instant }
        return all.last { !it.instant.isAfter(instant) }
    }

    /** The most recent "section" term (jie) at or before [moment] — it fixes the month branch. */
    fun currentSection(moment: LocalDateTime, zone: ZoneId): TermMoment {
        val instant = moment.atZone(zone).toInstant()
        val year = moment.year
        val all = (year - 1..year + 1)
            .flatMap { momentsForYear(it) }
            .filter { it.term.isJie }
            .sortedBy { it.instant }
        return all.last { !it.instant.isAfter(instant) }
    }
}
