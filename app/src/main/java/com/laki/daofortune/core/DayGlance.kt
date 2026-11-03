package com.laki.daofortune.core

/**
 * The day at a glance: colour, direction and hour, in one row.
 *
 * The full readings carry the reasoning; this card is the ten-second version
 * for the reader who opens the app with their morning tea. Everything on it
 * is derived from the same plate the readings use — the direction labels are
 * the very strings [FortuneEngine] puts in a full reading, so the glance can
 * never disagree with the detail.
 *
 * The colour is the classical five-phase colour of the day's **wealth
 * element** — the element the day master controls, which is the element the
 * luopan points at when it points at wealth.
 */
data class DayGlance(
    val colour: String,
    val colourNote: String,
    val face: String,
    val faceNote: String,
    val bestHourRange: String,
    val bestHourLabel: String
) {
    companion object {

        /** The five-phase colour of an element. */
        fun colourOf(element: WuXing): String = when (element) {
            WuXing.WOOD -> "Green"
            WuXing.FIRE -> "Red"
            WuXing.EARTH -> "Yellow"
            WuXing.METAL -> "White"
            WuXing.WATER -> "Black"
        }

        /** Reads the glance off the plate. */
        fun of(s: LuopanSnapshot): DayGlance {
            val best = s.bestHours.first()
            val primary = FortuneEngine.primaryWealthPalace(s.wealthElement)
            return DayGlance(
                colour = colourOf(s.wealthElement),
                colourNote = "today's ${s.wealthElement.en} wealth",
                face = "${Palace.directionName(primary.bearing)} · ${primary.pinyin}",
                faceNote = FortuneEngine.wealthDirectionLabel(s),
                bestHourRange = best.range,
                bestHourLabel = "${best.branch.pinyin} hour · ${best.officer.en}"
            )
        }
    }
}
