package com.laki.daofortune.core

/**
 * The day at a glance: colour, direction and hour, in one row.
 *
 * The full readings carry the reasoning; this card is the ten-second version
 * for the reader who opens the app with their morning tea. Everything on it
 * is derived from the same plate the readings use — the colour and the
 * direction come from the same [FortuneEngine] helpers the readings use, so
 * the glance can never disagree with the detail.
 *
 * The colour is the classical five-phase colour of the day's **wealth
 * element** — the element the day master controls, which is the element the
 * luopan points at when it points at wealth.
 */
data class DayGlance(
    val colour: String,
    val colourNote: String,
    val direction: String,
    val directionNote: String,
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
            val direction = FortuneEngine.wealthDirection(s)
            return DayGlance(
                colour = colourOf(s.wealthElement),
                colourNote = "the ${s.wealthElement.en} phase of your wealth",
                direction = direction.short,
                directionNote = direction.role,
                bestHourRange = best.range,
                bestHourLabel = "${best.branch.pinyin} hour · ${best.officer.en}"
            )
        }
    }
}
