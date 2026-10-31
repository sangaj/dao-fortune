package com.laki.daofortune.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** One double-hour of the day, scored for the reader. */
data class HourWindow(
    val branch: Branch,
    val range: String,
    val officer: HourOfficer,
    val score: Int,
    val reasons: List<String>
)

/** Everything the luopan knows about a single moment in time. */
data class LuopanSnapshot(
    val moment: LocalDateTime,
    val zone: ZoneId,
    val year: GanZhi,
    val month: GanZhi,
    val day: GanZhi,
    val hour: GanZhi,
    val sunLongitude: Double,
    val sunPalace: Palace,
    val term: SolarTerms.TermMoment,
    val officer: DayOfficer,
    val strength: CalendarEngine.DailyStrength,
    /** The element the reader controls — in classical terms, the wealth element. */
    val wealthElement: WuXing,
    val wealthPalaces: List<Palace>,
    /** Branches of the wealth element split into proper (zheng cai) and unexpected (pian cai). */
    val properWealthBranches: List<Branch>,
    val unexpectedWealthBranches: List<Branch>,
    /** The mountain directly opposite the day's branch — the direction to avoid. */
    val clashPalace: Palace,
    val noblemen: List<Branch>,
    val travelStar: Branch,
    val peachBlossom: Branch,
    val redPhoenix: Branch,
    val heavenBlessing: Branch,
    val voidBranches: Set<Branch>,
    val hourWindows: List<HourWindow>,
    /**
     * True when this plate describes a whole day rather than the moment in it.
     *
     * A day plate has no "current hour": the hour pillar still exists (it is
     * derived from the day), but it must not colour the reading — otherwise
     * the score of a chosen date would depend on an arbitrary reference hour.
     * The twelve double-hours of the day are unaffected; they are a property
     * of the day itself.
     */
    val dayOnly: Boolean = false
) {
    val bestHours: List<HourWindow> get() = hourWindows.sortedByDescending { it.score }.take(2)
    val auspiciousHourCount: Int get() = hourWindows.count { it.officer.auspicious }

    /** The elements in play: a day plate counts only what belongs to the day. */
    private val tallyElements: List<WuXing>
        get() = if (dayOnly) {
            listOf(day.element, day.branch.element, month.branch.element, sunPalace.element)
        } else {
            listOf(
                day.element, day.branch.element, hour.element, hour.branch.element,
                month.branch.element, sunPalace.element
            )
        }

    val dominantElement: WuXing
        get() {
            val tally = WuXing.entries.associateWith { 0 }.toMutableMap()
            tallyElements.forEach { tally[it] = (tally[it] ?: 0) + 1 }
            return tally.maxByOrNull { it.value }!!.key
        }
    val thinnestElement: WuXing
        get() {
            val tally = WuXing.entries.associateWith { 0 }.toMutableMap()
            tallyElements.forEach { tally[it] = (tally[it] ?: 0) + 1 }
            return tally.minByOrNull { it.value }!!.key
        }
}

/**
 * The luopan layer: it fixes the day's position on the twenty-four mountains,
 * works out where wealth is pointing, and scores every double-hour of the day.
 */
object LuopanEngine {

    /** The mountains each element rules, used to point the dial at the wealth direction. */
    fun sectorFor(element: WuXing): List<Palace> = when (element) {
        WuXing.WOOD -> listOf(Palace.YIN, Palace.JIA, Palace.MAO, Palace.YI)
        WuXing.FIRE -> listOf(Palace.SI, Palace.BING, Palace.WU, Palace.DING)
        WuXing.METAL -> listOf(Palace.SHEN, Palace.GENG, Palace.YOU, Palace.XIN)
        WuXing.WATER -> listOf(Palace.HAI, Palace.REN, Palace.ZI, Palace.GUI)
        WuXing.EARTH -> listOf(Palace.CHOU, Palace.CHEN, Palace.WEI, Palace.XU)
    }

    fun hourRange(branch: Branch): String = when (branch) {
        Branch.ZI -> "23:00 – 01:00"
        Branch.CHOU -> "01:00 – 03:00"
        Branch.YIN -> "03:00 – 05:00"
        Branch.MAO -> "05:00 – 07:00"
        Branch.CHEN -> "07:00 – 09:00"
        Branch.SI -> "09:00 – 11:00"
        Branch.WU -> "11:00 – 13:00"
        Branch.WEI -> "13:00 – 15:00"
        Branch.SHEN -> "15:00 – 17:00"
        Branch.YOU -> "17:00 – 19:00"
        Branch.XU -> "19:00 – 21:00"
        Branch.HAI -> "21:00 – 23:00"
    }

    /** Builds the full snapshot for a moment in time. */
    fun snapshot(moment: LocalDateTime, zone: ZoneId): LuopanSnapshot =
        build(moment, zone, dayOnly = false)

    /**
     * Builds the plate of a whole day, as used when the reader picks a date
     * rather than asking about right now.
     *
     * Noon is used as the reference moment so that the hour pillar is
     * well-formed, but the plate is marked [dayOnly]: the hour must not colour
     * a reading about a day that has not happened yet.
     */
    fun dayPlate(date: LocalDate, zone: ZoneId): LuopanSnapshot =
        build(date.atTime(12, 0), zone, dayOnly = true)

    private fun build(moment: LocalDateTime, zone: ZoneId, dayOnly: Boolean): LuopanSnapshot {
        val day = CalendarEngine.dayGanZhi(moment)
        val month = CalendarEngine.monthGanZhi(moment, zone)
        val year = CalendarEngine.yearGanZhi(moment, zone)
        val hour = CalendarEngine.hourGanZhi(moment)
        val term = SolarTerms.currentTerm(moment, zone)
        val officer = CalendarEngine.dayOfficer(day, month)

        val instant = moment.atZone(zone).toInstant()
        val sunLongitude = SolarTerms.sunApparentLongitude(SolarTerms.julianDayTT(instant))
        val sunBearing = ((sunLongitude + 90.0) % 360.0 + 360.0) % 360.0
        val sunPalace = Palace.ofBearing(sunBearing)

        val wealthElement = day.element.controls()
        val wealthPalaces = sectorFor(wealthElement)
        val wealthBranches = Branch.entries.filter { it.element == wealthElement }
        val proper = wealthBranches.filter { it.yang == day.stem.yang }
        val unexpected = wealthBranches.filter { it.yang != day.stem.yang }

        val strength = CalendarEngine.DailyStrength.of(day.element, month.branch.element)

        val snapshotBase = LuopanSnapshot(
            moment = moment,
            zone = zone,
            year = year,
            month = month,
            day = day,
            hour = hour,
            sunLongitude = sunLongitude,
            sunPalace = sunPalace,
            term = term,
            officer = officer,
            strength = strength,
            wealthElement = wealthElement,
            wealthPalaces = wealthPalaces,
            properWealthBranches = proper,
            unexpectedWealthBranches = unexpected,
            clashPalace = Palace.ofBearing((day.branch.ordinal + 6) * 30.0),
            noblemen = CalendarEngine.noblemen(day.stem),
            travelStar = CalendarEngine.travelStar(day.branch),
            peachBlossom = CalendarEngine.peachBlossom(day.branch),
            redPhoenix = CalendarEngine.redPhoenix(year.branch),
            heavenBlessing = CalendarEngine.heavenBlessing(year.branch),
            voidBranches = CalendarEngine.voidBranches(day),
            hourWindows = emptyList(),
            dayOnly = dayOnly
        )

        return snapshotBase.copy(hourWindows = scoreHours(snapshotBase))
    }

    /** Scores each of the twelve double-hours for the reader. */
    private fun scoreHours(s: LuopanSnapshot): List<HourWindow> =
        Branch.entries.map { branch ->
            var score = 0
            val reasons = mutableListOf<String>()

            val officer = CalendarEngine.hourOfficer(s.day, branch)
            if (officer.auspicious) {
                score += 10
                reasons += "${officer.en} hour"
            } else {
                score -= 10
                reasons += "${officer.en} hour (unfavourable)"
            }

            val hourStem = Stem.of((s.day.stem.ordinal % 5) * 2 + branch.ordinal)
            val relation = relationOf(dayElement = s.day.element, otherElement = hourStem.element)
            val relationScore = when (relation) {
                Relation.SAME -> 4
                Relation.GENERATED_BY -> 5
                Relation.GENERATES -> -3
                Relation.CONTROLS -> -7
                Relation.CONTROLLED -> 2
            }
            score += relationScore
            if (relationScore >= 4) reasons += "${hourStem.element.en} hour supports your ${s.day.element.en}"
            if (relationScore <= -3) reasons += "${hourStem.element.en} hour drains your ${s.day.element.en}"

            if (branch.element == s.wealthElement) {
                score += 6
                reasons += "wealth element ${s.wealthElement.en} is live"
            }
            if (s.wealthPalaces.contains(Branch.palaceOf(branch))) {
                score += 6
                reasons += "points toward the wealth direction"
            }
            if (branch == Branch.clashOf(s.day.branch)) {
                score -= 6
                reasons += "clashes your day branch"
            }
            if (Branch.harmonyOf(s.day.branch) == branch) {
                score += 4
                reasons += "harmonises with your day branch"
            }
            if (branch in Branch.triadOf(s.day.branch)) {
                score += 3
                reasons += "joins your day frame"
            }
            if (branch in s.noblemen) {
                score += 5
                reasons += "Nobleman hour"
            }
            if (branch == s.travelStar) {
                score += 5
                reasons += "Star of Motion"
            }
            if (branch == s.peachBlossom) {
                score += 3
                reasons += "Peach Blossom"
            }
            if (branch in s.voidBranches) {
                score -= 5
                reasons += "falls in the void"
            }

            HourWindow(branch, hourRange(branch), officer, score, reasons)
        }

    /** How [otherElement] relates to [dayElement]. */
    enum class Relation { SAME, GENERATED_BY, GENERATES, CONTROLS, CONTROLLED }

    fun relationOf(dayElement: WuXing, otherElement: WuXing): Relation = when {
        dayElement == otherElement -> Relation.SAME
        otherElement.generates() == dayElement -> Relation.GENERATED_BY
        dayElement.generates() == otherElement -> Relation.GENERATES
        otherElement.controls() == dayElement -> Relation.CONTROLS
        else -> Relation.CONTROLLED
    }
}
