package com.laki.daofortune.core

/**
 * The querent's year branch — the *nian ming*.
 *
 * The app reads a day's plate in the **day-establishment** tradition: the day's
 * own stem stands for whoever is asking (日建代身). That is enough to read the
 * day, but it means every reader on the same day sees the same plate. The year
 * branch is the classical remedy: in the da liu ren four-lesson system the
 * querent is identified by their **year branch**, asked for in one question —
 * "what year were you born?" — and it is what the day's reading is finally
 * measured against.
 *
 * It is deliberately *not* a birth chart. There is no day master, no strength
 * analysis, no useful-god judgement and no luck pillars: those need a full
 * eight-characters and a birth hour, and the useful-god judgement in particular
 * is the most subjective part of the whole tradition. The year branch is the
 * lightest classical anchor that still makes a reading personal.
 *
 * Only the branch is stored — twelve possibilities, chosen by zodiac animal.
 */
enum class YearSign(val branch: Branch) {
    RAT(Branch.ZI),
    OX(Branch.CHOU),
    TIGER(Branch.YIN),
    RABBIT(Branch.MAO),
    DRAGON(Branch.CHEN),
    SNAKE(Branch.SI),
    HORSE(Branch.WU),
    GOAT(Branch.WEI),
    MONKEY(Branch.SHEN),
    ROOSTER(Branch.YOU),
    DOG(Branch.XU),
    PIG(Branch.HAI);

    /** The animal name, as the English copy uses it. */
    val animal: String get() = branch.animal

    /** The mountain the year branch sits on — the querent's own position on the plate. */
    val palace: Palace get() = Branch.palaceOf(branch)

    /** How the year branch's element stands against the day element. */
    val element: WuXing get() = branch.element

    companion object {
        fun of(branch: Branch): YearSign = entries.first { it.branch == branch }

        fun ofOrdinal(ordinal: Int): YearSign = entries[Math.floorMod(ordinal, entries.size)]
    }
}

/**
 * How the querent's year branch stands against the day being read.
 */
object Natal {

    /** The relation between the year branch and the day branch. */
    enum class DayRelation(val en: String, val tone: Int) {
        /** Six-harmony: the year branch and the day branch pair up. */
        HARMONY("harmonises with", 8),

        /** Three-harmony frame: the year branch joins the day's own frame. */
        TRIAD("joins the frame of", 5),

        /** Direct clash: the year branch stands opposite the day branch. */
        CLASH("clashes", -9),

        /** Six-harm: a quiet, niggling friction. */
        HARM("harms", -4),

        /** No particular relation. */
        NEUTRAL("stands apart from", 0);

        companion object {
            fun of(yearBranch: Branch, dayBranch: Branch): DayRelation = when {
                Branch.harmonyOf(yearBranch) == dayBranch -> HARMONY
                dayBranch in Branch.triadOf(yearBranch) -> TRIAD
                yearBranch == Branch.clashOf(dayBranch) -> CLASH
                yearBranch == Branch.harmOf(dayBranch) -> HARM
                else -> NEUTRAL
            }
        }
    }

    /**
     * How the querent's own element stands against the day element.
     *
     * The day feeding the year element is the best of these: the day carries
     * the querent. The day cutting the year element is the worst: the day
     * trims them.
     */
    fun elementTone(yearElement: WuXing, dayElement: WuXing): Int = when {
        yearElement == dayElement -> 4
        dayElement.generates() == yearElement -> 5
        yearElement.generates() == dayElement -> 3
        yearElement.controls() == dayElement -> 3
        dayElement.controls() == yearElement -> -5
        else -> 0
    }

    /** The phrase used in the factor list and the prose. */
    fun elementPhrase(yearElement: WuXing, dayElement: WuXing): String = when {
        yearElement == dayElement -> "shares its element with"
        dayElement.generates() == yearElement -> "is fed by"
        yearElement.generates() == dayElement -> "feeds"
        yearElement.controls() == dayElement -> "has the edge over"
        dayElement.controls() == yearElement -> "is cut by"
        else -> "stands apart from"
    }
}
