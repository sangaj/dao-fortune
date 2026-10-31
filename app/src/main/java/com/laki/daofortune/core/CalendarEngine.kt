package com.laki.daofortune.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** A stem-branch pair (gan zhi) with its sixty-cycle index. */
data class GanZhi(val stem: Stem, val branch: Branch) {
    /** Lazily resolved so the lookup table can build GanZhi values without recursion. */
    val index: Int get() = GanzhiIndex.of(stem, branch)
    val element: WuXing get() = stem.element
    override fun toString(): String = "${stem.pinyin}${branch.pinyin}"
}

/** Helper for mapping a (stem, branch) pair onto the sixty-cycle. */
object GanzhiIndex {
    private val table = Array(60) { GanZhi(Stem.of(it % 10), Branch.of(it % 12)) }

    fun of(stem: Stem, branch: Branch): Int =
        table.indexOfFirst { it.stem == stem && it.branch == branch }.also { require(it >= 0) }

    fun of(index: Int): GanZhi = table[Math.floorMod(index, 60)]
}

/**
 * Calendar layer: the four pillars, the day officer, the hour officer and the
 * auxiliary stars. All rules follow the mainstream Chinese calendar tradition:
 * the day changes at 23:00 (the zi hour opens the next day) and the year changes
 * at Li Chun rather than on 1 January.
 */
object CalendarEngine {

    /** 1 October 1949 was a Jia-Zi day; every day count is measured from it. */
    private val EPOCH: LocalDate = LocalDate.of(1949, 10, 1)

    fun dayGanZhi(dateTime: LocalDateTime): GanZhi {
        val date = if (dateTime.hour >= 23) dateTime.toLocalDate().plusDays(1) else dateTime.toLocalDate()
        val days = ChronoUnit.DAYS.between(EPOCH, date)
        return GanzhiIndex.of(Math.floorMod(days, 60).toInt())
    }

    fun yearGanZhi(dateTime: LocalDateTime, zone: ZoneId): GanZhi {
        val lichun = SolarTerms.momentsForYear(dateTime.year)
            .first { it.term.longitude == 315 }
        val instant = dateTime.atZone(zone).toInstant()
        val year = if (!instant.isBefore(lichun.instant)) dateTime.year else dateTime.year - 1
        return GanzhiIndex.of(Math.floorMod(year - 1984, 60))
    }

    fun monthGanZhi(dateTime: LocalDateTime, zone: ZoneId): GanZhi {
        val section = SolarTerms.currentSection(dateTime, zone)
        val branch = section.term.monthBranch ?: Branch.YIN
        val yearPillar = yearGanZhi(dateTime, zone)
        // Wu Hu Dun: the Yin month's stem is fixed by the year's stem.
        val yinMonthStem = (yearPillar.stem.ordinal % 5) * 2 + 2
        val monthsFromYin = Math.floorMod(branch.ordinal - Branch.YIN.ordinal, 12)
        return GanZhi(Stem.of(yinMonthStem + monthsFromYin), branch)
    }

    fun hourGanZhi(dateTime: LocalDateTime): GanZhi {
        val branchIndex = ((dateTime.hour + 1) / 2) % 12
        val day = dayGanZhi(dateTime)
        val stemIndex = ((day.stem.ordinal % 5) * 2 + branchIndex) % 10
        return GanZhi(Stem.of(stemIndex), Branch.of(branchIndex))
    }

    /** The twelve day-officers, positioned by the month's branch. */
    fun dayOfficer(day: GanZhi, month: GanZhi): DayOfficer =
        DayOfficer.of(Math.floorMod(day.branch.ordinal - month.branch.ordinal, 12))

    /** The twelve hour-officers, rotated by the day's stem (Wu Shu Dun). */
    fun hourOfficer(day: GanZhi, hourBranch: Branch): HourOfficer {
        // Jia/Ji start at the Azure Dragon hour, Yi/Geng at Bright Hall, and so on;
        // each subsequent double-hour steps forward through the twelve officers.
        val offset = when (day.stem.ordinal) {
            0, 5 -> 0
            1, 6 -> 1
            2, 7 -> 2
            3, 8 -> 3
            else -> 4
        }
        return HourOfficer.of(hourBranch.ordinal + offset)
    }

    /** The two branches "empty" in the ten-day frame of the given pillar. */
    fun voidBranches(pillar: GanZhi): Set<Branch> {
        val xun = pillar.index / 10
        val start = GanzhiIndex.of(xun * 10).branch.ordinal
        return setOf(Branch.of(start + 10), Branch.of(start + 11))
    }

    /** Heavenly Nobleman (tian yi gui ren) for a given day stem. */
    fun noblemen(dayStem: Stem): List<Branch> = when (dayStem.ordinal) {
        0, 4, 6 -> listOf(Branch.CHOU, Branch.WEI)
        1, 5 -> listOf(Branch.ZI, Branch.SHEN)
        2, 3 -> listOf(Branch.HAI, Branch.YOU)
        8, 9 -> listOf(Branch.SI, Branch.MAO)
        else -> listOf(Branch.WU, Branch.YIN)
    }

    /** The Star of Motion (yi ma) for a given branch. */
    fun travelStar(branch: Branch): Branch = when (branch) {
        Branch.SHEN, Branch.ZI, Branch.WEI -> Branch.YIN
        Branch.YIN, Branch.WU, Branch.XU -> Branch.SHEN
        Branch.SI, Branch.YOU, Branch.CHOU -> Branch.HAI
        else -> Branch.SI
    }

    /** The Peach Blossom (xian chi) for a given branch. */
    fun peachBlossom(branch: Branch): Branch = when (branch) {
        Branch.SHEN, Branch.ZI, Branch.WEI -> Branch.YOU
        Branch.YIN, Branch.WU, Branch.XU -> Branch.MAO
        Branch.SI, Branch.YOU, Branch.CHOU -> Branch.WU
        else -> Branch.ZI
    }

    /** Red Phoenix (hong luan) for a given year branch. */
    fun redPhoenix(yearBranch: Branch): Branch = Branch.of(3 - yearBranch.ordinal)

    /** Heaven's Happiness (tian xi) for a given year branch. */
    fun heavenBlessing(yearBranch: Branch): Branch = Branch.of(3 - yearBranch.ordinal + 6)

    /**
     * Daily strength: wang (flourishing), xiang (supported), xiu (resting),
     * qiu (trapped), si (spent) — expressed as the relationship between the day
     * element and the element of the month that rules the day.
     */
    enum class DailyStrength(val en: String, val tone: Int) {
        WANG("Flourishing", 12),
        XIANG("Supported", 8),
        XIU("Resting", 4),
        QIU("Trapped", -4),
        SI("Spent", -10);

        companion object {
            fun of(day: WuXing, month: WuXing): DailyStrength = when {
                day == month -> WANG
                day.generates() == month -> XIANG
                month.generates() == day -> XIU
                day.controls() == month -> QIU
                else -> SI
            }
        }
    }
}
