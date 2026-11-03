package com.laki.daofortune.core

/**
 * The year officer (tai sui) card: how the reader's year sign stands against
 * the year itself.
 *
 * The daily plate answers "how is today"; this card answers the question
 * every reader of the tradition asks about the year — fan taisui and its
 * softer cousins. The relations are the same classical ones the day readings
 * use ([Natal.DayRelation]), measured between the reader's year branch and
 * the branch of the current year.
 *
 * The card is free and always visible once a sign is set: it is the lightest
 * personal hook the app has, and it is why a reader who has not set their
 * sign is shown the teaser instead. The copy stays in the app's register —
 * a cultural reading, never a prediction of events.
 */
data class YearReading(
    val relation: Natal.DayRelation,
    val animal: String,
    val yearAnimal: String,
    val headline: String,
    val body: String
) {
    companion object {

        /** Reads the year against the reader's sign. */
        fun of(sign: YearSign, yearBranch: Branch): YearReading {
            val relation = Natal.DayRelation.of(sign.branch, yearBranch)
            val animal = sign.animal
            val yearAnimal = yearBranch.animal
            val (headline, body) = when (relation) {
                Natal.DayRelation.HARMONY ->
                    "The year carries you" to
                        "Your $animal harmonises with the year of the $yearAnimal. " +
                        "Things line up with less pushing this year — the walking is still yours to do."

                Natal.DayRelation.TRIAD ->
                    "You join the year's frame" to
                        "Your $animal shares a frame with the year of the $yearAnimal. " +
                        "Allies are easier to find this year; ask for the help."

                Natal.DayRelation.CLASH ->
                    "Your sign clashes the year" to
                        "Your $animal stands opposite the year of the $yearAnimal — " +
                        "a fan taisui year. Hold steady, think twice before big moves, " +
                        "and let the year pass around you."

                Natal.DayRelation.HARM ->
                    "A year of small frictions" to
                        "Your $animal quietly harms the year of the $yearAnimal. " +
                        "Nothing dramatic — mind the niggling things, because they add up."

                Natal.DayRelation.NEUTRAL ->
                    "The year is yours to shape" to
                        "Your $animal stands apart from the year of the $yearAnimal — " +
                        "neither carried nor opposed. What this year becomes is largely your own making."
            }
            return YearReading(relation, animal, yearAnimal, headline, body)
        }

        /** The invitation shown when no sign has been chosen yet. */
        fun teaser(yearBranch: Branch): String =
            "The year of the ${yearBranch.animal} is here. Set your sign and " +
                "the plate will read the year against you."
    }
}
