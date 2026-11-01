package com.laki.daofortune.core

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One reason that moved the score, so the app can show its working. */
data class Factor(val label: String, val delta: Int, val note: String)

/** A finished reading for one module. */
data class ModuleReading(
    val module: FortuneModule,
    val score: Int,
    val verdict: Verdict,
    val headline: String,
    val summary: String,
    val insights: List<String>,
    val advice: List<String>,
    val bestHours: List<HourWindow>,
    val bestDirection: String,
    val avoidDirection: String,
    val factors: List<Factor>,
    /** Which day (and which hour, if the reading is about right now) it describes. */
    val contextLabel: String
)

/**
 * Turns the luopan snapshot into a scored reading. Every module reads the same
 * plate but weights it differently: overall fortune leans on the day's own
 * constitution, windfall leans on the wealth element and the sun's mountain,
 * and so on.
 *
 * The querent's year branch, when given, is measured against the day as well —
 * see [Natal] for why that is the classical way to identify the reader.
 */
object FortuneEngine {

    fun reading(
        s: LuopanSnapshot,
        module: FortuneModule,
        natal: Branch? = null
    ): ModuleReading {
        val factors = mutableListOf<Factor>()
        var score = 50

        // A day plate has no current hour, so the hour must not colour it.
        val useHour = !s.dayOnly

        fun add(label: String, delta: Int, note: String = "") {
            if (delta != 0) {
                factors += Factor(label, delta, note)
                score += delta
            }
        }

        when (module) {
            FortuneModule.OVERALL -> {
                add("Day strength: ${s.strength.en}", s.strength.tone, "Your day element is ${s.day.element.en}; the month is ${s.month.branch.element.en}.")
                add("Day officer: ${s.officer.en}", if (s.officer.auspicious) 13 else -13, s.officer.deity)
                if (s.officer == DayOfficer.CHENG || s.officer == DayOfficer.KAI) {
                    add("Officer favours decisive moves", 4)
                }
                if (useHour) {
                    val current = CalendarEngine.hourOfficer(s.day, s.hour.branch)
                    add("This hour: ${current.en}", if (current.auspicious) 7 else -7)
                }

                if (Branch.harmonyOf(s.day.branch) == s.month.branch) add("Day and month branches combine", 6)
                if (s.day.branch in Branch.triadOf(s.month.branch)) add("Day joins the month's frame", 4)
                if (s.day.branch == Branch.clashOf(s.month.branch)) add("Day clashes the month — friction with authority", -10)
                if (s.day.branch == Branch.clashOf(s.year.branch)) add("Day clashes the year — plans meet resistance", -6)
                if (s.day.branch in s.voidBranches) add("Day branch falls in the void", -7)
                if (s.hour.branch in s.voidBranches && useHour) add("Hour branch falls in the void", -4)
                if (s.day.branch in s.noblemen || (useHour && s.hour.branch in s.noblemen)) add("Nobleman present", 8)
                if (useHour && s.hour.branch == s.travelStar) add("Star of Motion in this hour", 6)
                if (s.day.branch == s.peachBlossom || s.year.branch == s.peachBlossom) add("Peach Blossom aspect", 5)
            }

            FortuneModule.WINDFALL -> {
                val monthElement = s.month.branch.element
                when {
                    monthElement == s.wealthElement -> add("Wealth element is in season", 12)
                    monthElement.generates() == s.wealthElement -> add("Month nourishes the wealth element", 8)
                    s.wealthElement.generates() == monthElement -> add("Wealth element feeds the season — it leaks away", -4)
                    s.wealthElement.controls() == monthElement -> add("Wealth element overpowers the season", -3)
                    else -> add("Season restrains the wealth element", -8)
                }

                when {
                    s.sunPalace.element == s.wealthElement -> add("Today's sun rests on the wealth element", 14)
                    s.sunPalace.element.generates() == s.wealthElement -> add("The sun's mountain nourishes the wealth element", 10)
                    s.wealthElement.generates() == s.sunPalace.element -> add("Wealth element feeds the sun's mountain", -2)
                    s.wealthElement.controls() == s.sunPalace.element -> add("The sun's mountain cuts the wealth element", -10)
                    else -> add("Wealth element cuts the sun's mountain", -7)
                }

                val wealthBearing = primaryBearing(s.wealthElement)
                val sunBearing = s.sunPalace.bearing
                val diff = angularDistance(sunBearing, wealthBearing)
                when {
                    diff == 0 -> add("Sun and wealth direction coincide", 10)
                    diff == 180 -> add("Sun opposes the wealth direction", -12)
                    diff in 60..120 -> add("Sun sits within a quarter turn of the wealth direction", 3)
                }

                add("Day officer: ${s.officer.en}", if (s.officer.auspicious) 13 else -13, s.officer.deity)
                if (s.officer == DayOfficer.MAN) add("'Full' officer favours collection", 6)
                if (s.officer == DayOfficer.PO) add("'Break' officer warns of loss", -8)

                add("Share of auspicious hours", (s.auspiciousHourCount * 10 / 12) - 5, "${s.auspiciousHourCount} of 12 double-hours are favourable.")

                if (s.unexpectedWealthBranches.any { it in s.voidBranches }) {
                    add("Unexpected wealth falls in the void", -12, "The odds arrive but slip away.")
                }
                if (s.strength == CalendarEngine.DailyStrength.WANG || s.strength == CalendarEngine.DailyStrength.XIANG) {
                    add("You are strong enough to carry the money", 8)
                } else {
                    add("A weak day element — heavy money weighs on you", -8)
                }

                if (useHour) {
                    if (Branch.harmonyOf(s.day.branch) == s.hour.branch) add("Day and hour combine — the omen of a sudden gain", 6)
                    if (s.hour.branch in Branch.triadOf(s.day.branch)) add("Hour joins your day frame", 4)
                    if (s.hour.branch == Branch.clashOf(s.day.branch)) add("Hour clashes the day — a gain that costs you", -6)
                    if (s.hour.branch == s.travelStar) add("Star of Motion — money is on the move", 5)
                }
                if (s.day.branch in s.noblemen) add("Nobleman support", 6)
            }

            FortuneModule.LOVE -> {
                if (useHour) {
                    if (Branch.harmonyOf(s.day.branch) == s.hour.branch) add("Day and hour branches combine", 14)
                    else if (s.hour.branch in Branch.triadOf(s.day.branch)) add("Hour joins your day frame", 8)
                    else if (s.hour.branch == Branch.clashOf(s.day.branch)) add("Hour clashes the day — misreadings and old wounds", -14)
                }

                if (s.day.branch == s.peachBlossom || s.year.branch == s.peachBlossom) add("Peach Blossom is lit", 12)
                if (s.day.branch == s.redPhoenix || (useHour && s.hour.branch == s.redPhoenix)) add("Red Phoenix touches the day", 10)
                if (s.day.branch == s.heavenBlessing || (useHour && s.hour.branch == s.heavenBlessing)) add("Heaven's Blessing touches the day", 8)
                if (s.day.stem.element == s.day.branch.element) add("Stem and branch share one element — you speak plainly", 6)
                add("Day officer: ${s.officer.en}", if (s.officer.auspicious) 6 else -6)
                if (s.day.branch in s.noblemen || (useHour && s.hour.branch in s.noblemen)) add("Nobleman present", 6)
                if (s.day.branch in s.voidBranches) add("Day branch falls in the void", -6)
            }

            FortuneModule.CAREER -> {
                when (s.officer) {
                    DayOfficer.CHENG, DayOfficer.KAI, DayOfficer.DING, DayOfficer.ZHI ->
                        add("Officer '${s.officer.en}' suits negotiation", 10)
                    DayOfficer.PO, DayOfficer.BI ->
                        add("Officer '${s.officer.en}' breaks the momentum", -10)
                    else -> add("Officer '${s.officer.en}' is neutral for business", 0)
                }
                add("Day strength: ${s.strength.en}", if (s.strength.tone >= 0) s.strength.tone else s.strength.tone / 2)
                if (s.day.branch in s.noblemen || (useHour && s.hour.branch in s.noblemen)) add("Nobleman present — help arrives", 10)
                if (useHour && s.hour.branch == s.travelStar) add("Star of Motion — posts, travel, change", 8)
                if (s.day.branch in s.voidBranches) add("Day branch falls in the void — effort returns thin", -8)
                if (s.day.branch == Branch.clashOf(s.month.branch)) add("Day clashes the month — authority resists", -8)
                val wealthBearing = primaryBearing(s.wealthElement)
                val diff = angularDistance(s.sunPalace.bearing, wealthBearing)
                if (diff == 0) add("The day's direction matches where opportunity waits", 8)
                else if (diff == 180) add("The day's direction opposes where opportunity waits", -8)
                if (useHour) {
                    val current = CalendarEngine.hourOfficer(s.day, s.hour.branch)
                    add("This hour: ${current.en}", if (current.auspicious) 5 else -5)
                }
            }

            FortuneModule.HEALTH -> {
                add("Day strength: ${s.strength.en}", if (s.strength.tone >= 0) s.strength.tone else s.strength.tone)
                if (useHour) {
                    if (s.hour.branch == Branch.clashOf(s.day.branch)) add("Hour clashes the day — the body is pulled two ways", -8)
                    if (s.hour.branch == Branch.harmOf(s.day.branch)) add("Hour harms the day — a low, nagging kind of day", -5)
                }
                add("Day officer: ${s.officer.en}", if (s.officer.auspicious) 5 else -5)
                if (s.day.branch in s.voidBranches) add("Day branch falls in the void", -5)
            }
        }

        // The querent's year branch is measured against every module: it is the
        // reader's own position in the day's field.
        natalFactors(s, natal, module).forEach { add(it.label, it.delta, it.note) }

        val clamped = score.coerceIn(0, 100)
        val verdict = Verdict.of(clamped)
        val narrative = Narrative.compose(s, module, clamped, verdict, factors, natal)

        return ModuleReading(
            module = module,
            score = clamped,
            verdict = verdict,
            headline = narrative.headline,
            summary = narrative.summary,
            insights = narrative.insights,
            advice = narrative.advice,
            bestHours = s.bestHours,
            bestDirection = wealthDirectionLabel(s),
            avoidDirection = avoidDirectionLabel(s),
            factors = factors,
            contextLabel = contextLabel(s)
        )
    }

    /**
     * How the querent's year branch stands against the day.
     *
     * The day branch and the year branch are compared the way any two branches
     * are compared in this tradition: harmony, triad, clash, harm. The elements
     * are compared the way the five phases are compared. Nothing here needs a
     * birth hour, a day master or a useful god.
     */
    private fun natalFactors(s: LuopanSnapshot, natal: Branch?, module: FortuneModule): List<Factor> {
        if (natal == null) return emptyList()
        val out = mutableListOf<Factor>()

        val relation = Natal.DayRelation.of(natal, s.day.branch)
        if (relation.tone != 0) {
            out += Factor(
                "Your year sign ${relation.en} the day's branch",
                relation.tone,
                "Year branch ${natal.pinyin} against day branch ${s.day.branch.pinyin}."
            )
        }

        val tone = Natal.elementTone(natal.element, s.day.element)
        if (tone != 0) {
            out += Factor(
                "Your year sign ${Natal.elementPhrase(natal.element, s.day.element)} the day",
                tone,
                "Your branch is ${natal.element.en}; the day's element is ${s.day.element.en}."
            )
        }

        if (module == FortuneModule.WINDFALL) {
            val palace = Branch.palaceOf(natal)
            if (palace in s.wealthPalaces) {
                out += Factor(
                    "Your year sign sits in the wealth sector",
                    6,
                    "Your own branch falls on the ${palace.pinyin} mountain."
                )
            }
        }
        return out
    }

    /** The compass bearing that points at the wealth element. */
    fun primaryBearing(element: WuXing): Int = when (element) {
        WuXing.WOOD -> 90
        WuXing.FIRE -> 180
        WuXing.METAL -> 270
        WuXing.WATER -> 0
        WuXing.EARTH -> 120
    }

    /** Smallest angle between two bearings, 0..180. */
    fun angularDistance(a: Int, b: Int): Int {
        val raw = Math.abs(a - b) % 360
        return Math.min(raw, 360 - raw)
    }

    fun wealthDirectionLabel(s: LuopanSnapshot): String {
        val primary = when (s.wealthElement) {
            WuXing.WOOD -> Palace.MAO
            WuXing.FIRE -> Palace.WU
            WuXing.METAL -> Palace.YOU
            WuXing.WATER -> Palace.ZI
            WuXing.EARTH -> Palace.CHEN
        }
        val extra = if (s.wealthElement == WuXing.EARTH) " and the centre" else ""
        return "${Palace.directionName(primary.bearing)} — ${primary.pinyin}$extra (${s.wealthElement.en})"
    }

    fun avoidDirectionLabel(s: LuopanSnapshot): String =
        "${Palace.directionName(s.clashPalace.bearing)} — ${s.clashPalace.pinyin} (clashes your day branch)"

    /** Which day, and which hour, the reading describes. */
    private fun contextLabel(s: LuopanSnapshot): String = if (s.dayOnly) {
        s.moment.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH)) + " · day plate"
    } else {
        "Today · " + s.moment.format(DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH))
    }

    /**
     * The readings the home screen carries without asking for anything.
     *
     * Today's Fortune is free every day, in full: it is drawn as soon as the
     * plate is known, so its card shows the score and never the preview teaser,
     * and the one free reading is never spent on it. The other four modules and
     * the dream are not here — they are what the unlock is for.
     */
    fun alwaysFree(
        date: LocalDate,
        s: LuopanSnapshot,
        natal: Branch? = null
    ): Map<ReadingKey, ModuleReading> =
        mapOf(ReadingKey(date, FortuneModule.OVERALL) to reading(s, FortuneModule.OVERALL, natal))
}
