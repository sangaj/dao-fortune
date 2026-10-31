package com.laki.daofortune.core

import java.time.LocalDate

/** Five phases (wu xing). Each phase generates the next and controls the one after it. */
enum class WuXing(val en: String) {
    WOOD("Wood"),
    FIRE("Fire"),
    EARTH("Earth"),
    METAL("Metal"),
    WATER("Water");

    fun generates(): WuXing = when (this) {
        WOOD -> FIRE
        FIRE -> EARTH
        EARTH -> METAL
        METAL -> WATER
        WATER -> WOOD
    }

    fun controls(): WuXing = when (this) {
        WOOD -> EARTH
        EARTH -> WATER
        WATER -> FIRE
        FIRE -> METAL
        METAL -> WOOD
    }

    fun generatedBy(): WuXing = entries.first { it.generates() == this }
    fun controlledBy(): WuXing = entries.first { it.controls() == this }
}

/** The ten heavenly stems, in canonical order. */
enum class Stem(val pinyin: String, val element: WuXing, val yang: Boolean) {
    JIA("Jia", WuXing.WOOD, true),
    YI("Yi", WuXing.WOOD, false),
    BING("Bing", WuXing.FIRE, true),
    DING("Ding", WuXing.FIRE, false),
    WU("Wu", WuXing.EARTH, true),
    JI("Ji", WuXing.EARTH, false),
    GENG("Geng", WuXing.METAL, true),
    XIN("Xin", WuXing.METAL, false),
    REN("Ren", WuXing.WATER, true),
    GUI("Gui", WuXing.WATER, false);

    companion object {
        fun of(index: Int): Stem = entries[Math.floorMod(index, 10)]
    }
}

/** The twelve earthly branches, in canonical order. Zi = Rat = North. */
enum class Branch(val pinyin: String, val animal: String, val element: WuXing, val yang: Boolean) {
    ZI("Zi", "Rat", WuXing.WATER, true),
    CHOU("Chou", "Ox", WuXing.EARTH, false),
    YIN("Yin", "Tiger", WuXing.WOOD, true),
    MAO("Mao", "Rabbit", WuXing.WOOD, false),
    CHEN("Chen", "Dragon", WuXing.EARTH, true),
    SI("Si", "Snake", WuXing.FIRE, false),
    WU("Wu", "Horse", WuXing.FIRE, true),
    WEI("Wei", "Goat", WuXing.EARTH, false),
    SHEN("Shen", "Monkey", WuXing.METAL, true),
    YOU("You", "Rooster", WuXing.METAL, false),
    XU("Xu", "Dog", WuXing.EARTH, true),
    HAI("Hai", "Pig", WuXing.WATER, false);

    companion object {
        fun of(index: Int): Branch = entries[Math.floorMod(index, 12)]

        /** The compass bearing of this branch: the twelve branches sit every 30 degrees. */
        fun bearingOf(branch: Branch): Int = branch.ordinal * 30

        /** The mountain this branch sits on. */
        fun palaceOf(branch: Branch): Palace = Palace.ofBearing(bearingOf(branch).toDouble())

        /** The branch directly opposite (clash) — used for "avoid this direction". */
        fun clashOf(branch: Branch): Branch = of(branch.ordinal + 6)

        /** Six-harm pairing (liu hai) — a quieter kind of friction. */
        fun harmOf(branch: Branch): Branch = when (branch) {
            ZI -> WEI
            WEI -> ZI
            CHOU -> WU
            WU -> CHOU
            YIN -> SI
            SI -> YIN
            MAO -> CHEN
            CHEN -> MAO
            SHEN -> HAI
            HAI -> SHEN
            YOU -> XU
            XU -> YOU
        }

        /** Six-harmony pairing, if any. */
        fun harmonyOf(branch: Branch): Branch? = when (branch) {
            ZI -> CHOU
            CHOU -> ZI
            YIN -> HAI
            HAI -> YIN
            MAO -> XU
            XU -> MAO
            CHEN -> YOU
            YOU -> CHEN
            SI -> SHEN
            SHEN -> SI
            WU -> WEI
            WEI -> WU
        }

        /** Branches that join branch in a three-harmony frame (san he). */
        fun triadOf(branch: Branch): Set<Branch> = when (branch) {
            ZI, SHEN, WEI -> setOf(ZI, SHEN, WEI)
            YIN, HAI, MAO -> setOf(YIN, HAI, MAO)
            WU, XU, SI -> setOf(WU, XU, SI)
            CHEN, YOU, CHOU -> setOf(CHEN, YOU, CHOU)
        }
    }
}

/**
 * The twenty-four mountains of the luopan (earth plate), laid out clockwise from north.
 * [bearing] is the compass bearing in degrees where 0 = due north, 90 = due east.
 */
enum class Palace(
    val pinyin: String,
    val element: WuXing,
    val trigram: String,
    val yuan: String,
    val yang: Boolean,
    val bearing: Int
) {
    ZI("Zi", WuXing.WATER, "Kan", "Tian", false, 0),
    GUI("Gui", WuXing.WATER, "Kan", "Ren", true, 15),
    CHOU("Chou", WuXing.EARTH, "Gen", "Di", false, 30),
    GEN("Gen", WuXing.EARTH, "Gen", "Tian", true, 45),
    YIN("Yin", WuXing.WOOD, "Zhen", "Di", true, 60),
    JIA("Jia", WuXing.WOOD, "Zhen", "Ren", true, 75),
    MAO("Mao", WuXing.WOOD, "Zhen", "Di", false, 90),
    YI("Yi", WuXing.WOOD, "Xun", "Ren", true, 105),
    CHEN("Chen", WuXing.EARTH, "Xun", "Di", false, 120),
    XUN("Xun", WuXing.WOOD, "Xun", "Tian", true, 135),
    SI("Si", WuXing.FIRE, "Xun", "Di", false, 150),
    BING("Bing", WuXing.FIRE, "Li", "Ren", true, 165),
    WU("Wu", WuXing.FIRE, "Li", "Di", false, 180),
    DING("Ding", WuXing.FIRE, "Li", "Ren", true, 195),
    WEI("Wei", WuXing.EARTH, "Kun", "Di", false, 210),
    KUN("Kun", WuXing.EARTH, "Kun", "Tian", true, 225),
    SHEN("Shen", WuXing.METAL, "Kun", "Di", true, 240),
    GENG("Geng", WuXing.METAL, "Dui", "Ren", true, 255),
    YOU("You", WuXing.METAL, "Dui", "Di", false, 270),
    XIN("Xin", WuXing.METAL, "Dui", "Ren", true, 285),
    XU("Xu", WuXing.EARTH, "Qian", "Di", false, 300),
    QIAN("Qian", WuXing.METAL, "Qian", "Tian", true, 315),
    HAI("Hai", WuXing.WATER, "Qian", "Di", false, 330),
    REN("Ren", WuXing.WATER, "Kan", "Ren", true, 345);

    companion object {
        /** Mountain by its position on the dial, 0 = Zi (north). */
        fun of(index: Int): Palace = entries[Math.floorMod(index, 24)]

        /** Mountain whose 15-degree sector contains the given compass bearing. */
        fun ofBearing(bearing: Double): Palace = of(Math.floorMod(Math.round(bearing / 15.0).toInt(), 24))

        /** Traditional compass name of the bearing. */
        fun directionName(bearing: Int): String = when {
            bearing == 0 -> "Due North"
            bearing == 90 -> "Due East"
            bearing == 180 -> "Due South"
            bearing == 270 -> "Due West"
            else -> {
                val cardinal = when {
                    bearing < 45 || bearing > 315 -> "North"
                    bearing < 135 -> "East"
                    bearing < 225 -> "South"
                    else -> "West"
                }
                val inter = when {
                    bearing in 23..67 -> "North-East"
                    bearing in 113..157 -> "South-East"
                    bearing in 203..247 -> "South-West"
                    else -> "North-West"
                }
                if (bearing in 23..67 || bearing in 113..157 || bearing in 203..247) inter else cardinal
            }
        }
    }
}

/** The twelve day-officer build-up cycle (jian chu shi er shen). */
enum class DayOfficer(val pinyin: String, val en: String, val auspicious: Boolean, val deity: String) {
    JIAN("Jian", "Establish", true, "Azure Dragon"),
    CHU("Chu", "Remove", true, "Bright Hall"),
    MAN("Man", "Full", false, "Heavenly Punishment"),
    PING("Ping", "Level", false, "Vermilion Bird"),
    DING("Ding", "Stabilise", true, "Golden Cabinet"),
    ZHI("Zhi", "Hold", true, "Heavenly Virtue"),
    PO("Po", "Break", false, "Dark Warrior"),
    WEI("Wei", "Danger", false, "Hooked Array"),
    CHENG("Cheng", "Succeed", true, "Director of Fate"),
    SHOU("Shou", "Receive", true, "Jade Hall"),
    KAI("Kai", "Open", true, "Heavenly Kitchen"),
    BI("Bi", "Close", false, "Greater Yin");

    companion object {
        fun of(offset: Int): DayOfficer = entries[Math.floorMod(offset, 12)]
    }
}

/** The twelve hour-officers (shi shen), used to judge individual double-hours. */
enum class HourOfficer(val pinyin: String, val en: String, val auspicious: Boolean) {
    QING_LONG("Qing Long", "Azure Dragon", true),
    MING_TANG("Ming Tang", "Bright Hall", true),
    TIAN_XING("Tian Xing", "Heavenly Punishment", false),
    ZHU_QUE("Zhu Que", "Vermilion Bird", false),
    JIN_GUI("Jin Gui", "Golden Cabinet", true),
    TIAN_DE("Tian De", "Heavenly Virtue", true),
    BAI_HU("Bai Hu", "White Tiger", false),
    YU_TANG("Yu Tang", "Jade Hall", true),
    TIAN_LAO("Tian Lao", "Heavenly Prison", false),
    XUAN_WU("Xuan Wu", "Dark Warrior", false),
    SI_MING("Si Ming", "Director of Fate", true),
    GOU_CHEN("Gou Chen", "Hooked Array", false);

    companion object {
        fun of(offset: Int): HourOfficer = entries[Math.floorMod(offset, 12)]
    }
}

/** The five fortune modules the app reports on. */
enum class FortuneModule(val title: String, val blurb: String) {
    OVERALL("Today's Fortune", "The overall quality of the day for the one person reading it."),
    WINDFALL("Windfall & Wealth", "Whether unexpected money is likely to arrive, and how to catch it."),
    LOVE("Love & Bonds", "Affinity, attachment and the emotional weather of the day."),
    CAREER("Career & Ambition", "Whether this is a day to push, pitch, sign, or hold."),
    HEALTH("Body & Balance", "Which element is overworked today — and how to settle it.")
}

/**
 * A reading is filed under the day it was drawn on, so that changing the date
 * does not throw away what the reader has already paid for or been given.
 */
data class ReadingKey(val date: LocalDate, val module: FortuneModule)

/** Score bands used across the app. */
enum class Verdict(val label: String, val tone: String, val min: Int, val max: Int) {
    EXCEPTIONAL("Exceptional", "gold", 85, 100),
    FAVORABLE("Favorable", "jade", 70, 84),
    MILDLY_GOOD("Mildly Favorable", "jade", 56, 69),
    MIXED("Mixed", "amber", 42, 55),
    CAUTIOUS("Cautious", "amber", 26, 41),
    UNFAVORABLE("Unfavorable", "crimson", 0, 25);

    companion object {
        fun of(score: Int): Verdict = entries.first { score in it.min..it.max }
    }
}
