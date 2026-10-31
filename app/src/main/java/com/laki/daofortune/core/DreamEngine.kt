package com.laki.daofortune.core

import java.time.LocalTime
import java.util.Locale

/**
 * Reads a dream through the plate.
 *
 * The tradition this follows is **梦占**: a dream is not read on its own, it is
 * read against the day. The dream supplies the image (象), the day's own
 * pillar supplies the field (局), and the reader's year branch supplies the
 * person (人). Everything here is computed from those three, with the same
 * rules the rest of the app uses — there is no random element and no model.
 *
 * The dream text is read **on the device** and is never written anywhere.
 */
object DreamEngine {

    /**
     * Takes the images out of the dream text.
     *
     * @param text what the reader wrote. It is not stored.
     * @param wokeAt when the reader woke up, when it is known — the double-hour
     *   is one of the classical handles on a dream.
     */
    fun extract(text: String, wokeAt: LocalTime? = null): DreamImage {
        val tokens = mutableListOf<DreamToken>()
        val used = mutableListOf<IntRange>()

        fun free(range: IntRange): Boolean =
            used.none { range.first <= it.last && it.first <= range.last }

        // The hour is taken out of the text first, so that "3:30" is not also
        // read as the number three.
        val hourHit = findHour(text)
        val masked = if (hourHit != null) {
            val sb = StringBuilder(text)
            for (i in hourHit.second) sb.setCharAt(i, ' ')
            sb.toString()
        } else {
            text
        }
        if (hourHit != null) used += hourHit.second

        // Then the vocabulary, in the order it is declared. A word that has
        // already been claimed is not read twice.
        for (matcher in DreamLexicon.matchers) {
            if (matcher.lexeme.kind == DreamKind.NUMBER) continue // handled below
            var claimed = false
            for (pattern in matcher.patterns) {
                val hit = pattern.find(masked) ?: continue
                if (!free(hit.range)) continue
                used += hit.range
                claimed = true
                tokens += tokenOf(matcher.lexeme, hit.value)
                break
            }
            if (claimed) continue
        }

        // Numbers last: "eleven" must not be read as "one".
        for (matcher in DreamLexicon.numberMatchers) {
            var claimed = false
            for (pattern in matcher.patterns) {
                val hit = pattern.find(masked) ?: continue
                if (!free(hit.range)) continue
                used += hit.range
                claimed = true
                tokens += tokenOf(matcher.lexeme, hit.value)
                break
            }
            if (claimed) continue
        }

        val hourBranch = wokeAt?.let { branchOfHour(it.hour) } ?: hourHit?.first?.let { branchOfHour(it) }

        val principal = pickPrincipal(tokens)
        val element = principal?.element
            ?: tokens.mapNotNull { it.element }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

        return DreamImage(
            tokens = tokens,
            principal = principal,
            hourBranch = hourBranch,
            element = element,
            seed = text.hashCode()
        )
    }

    /**
     * Reads the dream against the day's plate.
     *
     * @param s the plate of the day the dream belongs to. It is a **day
     *   plate** — the dream is a past event, so it has no hour of its own;
     *   the hour the reader woke in is used separately.
     */
    fun reading(
        s: LuopanSnapshot,
        image: DreamImage,
        natal: Branch? = null
    ): DreamReading {
        val factors = mutableListOf<Factor>()
        var score = 50

        fun add(label: String, delta: Int, note: String = "") {
            if (delta != 0) {
                factors += Factor(label, delta, note)
                score += delta
            }
        }

        val omen = image.principal

        // 1. The omen against the day's own branch. This is the core of the
        //    reading: does the image answer the day, or argue with it?
        if (omen?.branch != null) {
            val relation = Natal.DayRelation.of(omen.branch, s.day.branch)
            if (relation.tone != 0) {
                add(
                    when (relation) {
                        Natal.DayRelation.HARMONY -> "The omen and the day's branch combine"
                        Natal.DayRelation.TRIAD -> "The omen joins the day's own frame"
                        Natal.DayRelation.CLASH -> "The omen clashes the day's branch"
                        Natal.DayRelation.HARM -> "The omen harms the day's branch"
                        Natal.DayRelation.NEUTRAL -> "The omen stands apart from the day's branch"
                    },
                    relation.tone,
                    "Omen branch ${omen.branch.pinyin} against day branch ${s.day.branch.pinyin}."
                )
            }
            // The triad layer: an omen in the day's own frame answers it; an
            // omen in the opposing frame argues with it.
            val dayTriad = Branch.triadOf(s.day.branch)
            val opposite = Branch.triadOf(Branch.clashOf(s.day.branch))
            if (omen.branch in dayTriad && omen.branch != s.day.branch) {
                add("The omen carries the day's frame", 5, "${omen.branch.pinyin} is one of the day's own three.")
            } else if (omen.branch in opposite) {
                add("The omen carries the opposing frame", -4, "${omen.branch.pinyin} belongs to the frame that opposes the day.")
            }
        }

        // 2. The omen's element against the day's element.
        if (omen?.element != null) {
            val tone = Natal.elementTone(omen.element, s.day.element)
            if (tone != 0) {
                add(
                    "The omen ${Natal.elementPhrase(omen.element, s.day.element)} the day",
                    tone,
                    "Omen element ${omen.element.en}; day element ${s.day.element.en}."
                )
            }
        }

        // 3. A direction in the dream, measured against the day's own axis.
        val direction = image.tokens.firstOrNull { it.kind == DreamKind.DIRECTION }
        if (direction?.palace != null) {
            val palace = direction.palace
            if (palace == s.sunPalace) {
                add("The dream points where the sun stands", 5, "The sun rests on the ${palace.pinyin} mountain.")
            } else if (palace == s.clashPalace) {
                add("The dream points at the direction to avoid", -6, "The ${palace.pinyin} mountain clashes the day branch.")
            }
            val diff = FortuneEngine.angularDistance(palace.bearing, FortuneEngine.primaryBearing(s.wealthElement))
            when {
                diff == 0 -> add("The dream points at the wealth direction", 4, "The ${palace.pinyin} mountain faces where gain waits.")
                diff == 180 -> add("The dream points away from the wealth direction", -3)
            }
        }

        // 4. The double-hour the dreamer woke in.
        val hour = image.hourBranch
        if (hour != null) {
            when {
                hour == s.day.branch -> add("The hour of waking is the day's own branch", 3, "${hour.pinyin} hour.")
                Branch.harmonyOf(hour) == s.day.branch -> add("The hour of waking combines with the day", 5)
                hour == Branch.clashOf(s.day.branch) -> add("The hour of waking clashes the day", -4)
            }
            val officer = CalendarEngine.hourOfficer(s.day, hour)
            add("The hour is under ${officer.en}", if (officer.auspicious) 3 else -3)
        }

        // 5. The reader's own year branch against the omen.
        if (natal != null && omen?.branch != null) {
            val relation = Natal.DayRelation.of(natal, omen.branch)
            if (relation.tone != 0) {
                add(
                    "Your year sign ${relation.en} the omen",
                    relation.tone,
                    "Year branch ${natal.pinyin} against omen branch ${omen.branch.pinyin}."
                )
            }
        }

        // 6. The traditional sign of money.
        if (image.tokens.any { it.wealth }) {
            add("The dream shows the sign of money", 6, "Money in a dream is the one omen that is never ambiguous.")
        }

        // 7. How clearly the dream spoke. A short, concrete dream is a sharper
        //    sign than a crowded one; a dream with no image in it is no sign.
        when {
            image.tokens.isEmpty() -> add("The dream held no image the plate could use", -12)
            image.tokens.size <= 2 -> add("A short, concrete dream", 4, "One or two images — the message is plain.")
            image.tokens.size > 5 -> add("A crowded dream", -4, "So many images that the message scatters.")
        }

        val clamped = score.coerceIn(0, 100)
        val verdict = Verdict.of(clamped)
        val text = Narrative.dream(s, image, clamped, verdict, factors)

        return DreamReading(
            score = clamped,
            verdict = verdict,
            headline = text.headline,
            summary = text.summary,
            insights = text.insights,
            advice = text.advice,
            factors = factors,
            image = image,
            contextLabel = contextLabel(s, image)
        )
    }

    /**
     * Reads the wake-up time the reader typed.
     *
     * The field is free text, so anything that is not a time falls back to the
     * moment the reader is reading the dream at.
     */
    fun parseWokeAt(text: String, fallback: LocalTime): LocalTime {
        val cleaned = text.trim().lowercase(Locale.ENGLISH)
        if (cleaned.isEmpty()) return fallback
        val match = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?").find(cleaned) ?: return fallback
        var hour = match.groupValues[1].toIntOrNull() ?: return fallback
        val minute = match.groupValues[2].toIntOrNull() ?: 0
        val meridiem = match.groupValues[3]
        if (meridiem == "pm" && hour < 12) hour += 12
        if (meridiem == "am" && hour == 12) hour = 0
        if (hour !in 0..23) return fallback
        return LocalTime.of(hour, minute.coerceIn(0, 59))
    }

    /** The branch that holds a double-hour. The day turns at 23:00. */
    fun branchOfHour(hour: Int): Branch = when (hour) {
        23, 0 -> Branch.ZI
        1, 2 -> Branch.CHOU
        3, 4 -> Branch.YIN
        5, 6 -> Branch.MAO
        7, 8 -> Branch.CHEN
        9, 10 -> Branch.SI
        11, 12 -> Branch.WU
        13, 14 -> Branch.WEI
        15, 16 -> Branch.SHEN
        17, 18 -> Branch.YOU
        19, 20 -> Branch.XU
        else -> Branch.HAI
    }

    /** The main omen: the most concrete image in the dream. */
    private fun pickPrincipal(tokens: List<DreamToken>): DreamToken? {
        val order = listOf(
            DreamKind.CREATURE,
            DreamKind.WEALTH,
            DreamKind.DIRECTION,
            DreamKind.NUMBER,
            DreamKind.HOUR,
            DreamKind.COLOUR,
            DreamKind.NATURE
        )
        for (kind in order) {
            tokens.firstOrNull { it.kind == kind }?.let { return it }
        }
        return null
    }

    /** Builds a token from the lexicon entry and the word that actually matched. */
    private fun tokenOf(lexeme: DreamLexicon.Lexeme, matched: String): DreamToken = DreamToken(
        kind = lexeme.kind,
        term = matched.trim().replaceFirstChar { it.uppercase() }.ifEmpty { lexeme.term },
        chinese = lexeme.chinese,
        branch = lexeme.branch,
        element = lexeme.element,
        palace = lexeme.palace,
        spirit = lexeme.spirit,
        wealth = lexeme.wealth
    )

    /**
     * Finds the hour in the text, if the text names one.
     *
     * A bare number is **not** read as an hour — in a dream, "three cats" is an
     * omen about three, not a statement about the time of day.
     */
    private fun findHour(text: String): Pair<Int, IntRange>? {
        val clock = Regex("\\b(\\d{1,2}):(\\d{2})\\b").find(text)
        if (clock != null) {
            val hour = clock.groupValues[1].toInt()
            return hour.coerceIn(0, 23) to clock.range
        }
        val meridiem = Regex("\\b(\\d{1,2})\\s*(am|pm)\\b", RegexOption.IGNORE_CASE).find(text)
        if (meridiem != null) {
            var hour = meridiem.groupValues[1].toInt() % 24
            if (meridiem.groupValues[2].equals("pm", ignoreCase = true) && hour < 12) hour += 12
            if (meridiem.groupValues[2].equals("am", ignoreCase = true) && hour == 12) hour = 0
            return hour to meridiem.range
        }
        val lowered = text.lowercase(Locale.ENGLISH)
        for ((word, hour) in hourWords) {
            val index = lowered.indexOf(word)
            if (index >= 0) return hour to (index until index + word.length)
        }
        return null
    }

    /** Words that name a double-hour. */
    private val hourWords: List<Pair<String, Int>> = listOf(
        "midnight" to 0, "子时" to 0,
        "dawn" to 5, "sunrise" to 5, "daybreak" to 5, "early morning" to 5, "寅时" to 3,
        "morning" to 7, "辰时" to 7,
        "noon" to 11, "midday" to 11, "午时" to 11,
        "afternoon" to 13, "未时" to 13,
        "evening" to 19, "dusk" to 17, "sunset" to 17, "酉时" to 17,
        "night" to 21, "tonight" to 21, "亥时" to 21
    )

    /** Which night the reading belongs to. */
    private fun contextLabel(s: LuopanSnapshot, image: DreamImage): String {
        val night = s.moment.format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH))
        val hour = image.hourBranch
        return if (hour != null) "$night · ${hour.pinyin} hour" else "$night · dream"
    }
}
