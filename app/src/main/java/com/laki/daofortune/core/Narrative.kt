package com.laki.daofortune.core

/**
 * Turns the scored factors into English prose. The wording stays deliberately
 * grounded in what the plate actually shows: each line names the classical
 * device it came from, so a reader can follow the reasoning.
 */
object Narrative {

    data class Text(
        val headline: String,
        val summary: String,
        val insights: List<String>,
        val advice: List<String>
    )

    fun compose(
        s: LuopanSnapshot,
        module: FortuneModule,
        score: Int,
        verdict: Verdict,
        factors: List<Factor>,
        natal: Branch? = null
    ): Text {
        val base = when (module) {
            FortuneModule.OVERALL -> overall(s, score, verdict, factors)
            FortuneModule.WINDFALL -> windfall(s, score, verdict, factors)
            FortuneModule.LOVE -> love(s, score, verdict, factors)
            FortuneModule.CAREER -> career(s, score, verdict, factors)
            FortuneModule.HEALTH -> health(s, score, verdict, factors)
        }
        if (natal == null) return base
        return base.copy(insights = base.insights + natalNotes(s, natal))
    }

    /**
     * How the querent's own year branch stands against the day.
     *
     * These are added to every module: the year branch is the reader's position
     * in the day's field, so it belongs under every reading, not under one.
     */
    private fun natalNotes(s: LuopanSnapshot, natal: Branch): List<String> {
        val relation = Natal.DayRelation.of(natal, s.day.branch)
        val headline = when (relation) {
            Natal.DayRelation.HARMONY ->
                "Your year sign (${natal.animal}, ${natal.pinyin}) harmonises with this day's branch — the day meets you halfway."
            Natal.DayRelation.TRIAD ->
                "Your year sign (${natal.animal}, ${natal.pinyin}) joins this day's three-harmony frame — the day carries your own frame with it."
            Natal.DayRelation.CLASH ->
                "Your year sign (${natal.animal}, ${natal.pinyin}) stands opposite this day's branch — the day works against your grain."
            Natal.DayRelation.HARM ->
                "Your year sign (${natal.animal}, ${natal.pinyin}) harms this day's branch — a quiet friction rather than an open row."
            Natal.DayRelation.NEUTRAL ->
                "Your year sign (${natal.animal}, ${natal.pinyin}) stands apart from this day's branch — the day leaves you neutral."
        }
        val element = when (Natal.elementTone(natal.element, s.day.element)) {
            5 -> "the day feeds your year element"
            4 -> "your year element and the day's are the same"
            3 -> "your year element carries the day"
            -5 -> "the day cuts across your year element"
            else -> "your year element and the day's stand apart"
        }
        return listOf(
            headline,
            "Elementally, $element — your branch is ${natal.element.en}, the day's is ${s.day.element.en}.",
            "Your own branch sits on the ${Branch.palaceOf(natal).pinyin} mountain: that is your position on the plate."
        )
    }

    // ------------------------------------------------------------------ shared

    private fun pillars(s: LuopanSnapshot) =
        "Day ${s.day}, month ${s.month}, year ${s.year}, hour ${s.hour}"

    /** "today" for a reading about now, "that day" for a plate of a chosen date. */
    private fun dayWord(s: LuopanSnapshot) = if (s.dayOnly) "that day" else "today"

    /** The possessive form, for "today's gains" / "that day's gains". */
    private fun dayPossessive(s: LuopanSnapshot) = if (s.dayOnly) "that day's" else "today's"

    private fun strengthNote(s: LuopanSnapshot) = when (s.strength) {
        CalendarEngine.DailyStrength.WANG ->
            "Your day element ${s.day.element.en} rules the month — you are at full strength and can carry a load."
        CalendarEngine.DailyStrength.XIANG ->
            "Your ${s.day.element.en} is nourished by the month's ${s.month.branch.element.en} — you are supported rather than strained."
        CalendarEngine.DailyStrength.XIU ->
            "The month's ${s.month.branch.element.en} draws on your ${s.day.element.en}; you are resting rather than running."
        CalendarEngine.DailyStrength.QIU ->
            "Your ${s.day.element.en} is trapped by the month's ${s.month.branch.element.en} — effort is possible but constrained."
        CalendarEngine.DailyStrength.SI ->
            "Your ${s.day.element.en} is spent against the month's ${s.month.branch.element.en}; reserve strength is low."
    }

    private fun officerNote(s: LuopanSnapshot) =
        "The day's officer is ${s.officer.en} (${s.officer.pinyin}), watched over by the ${s.officer.deity} — " +
            if (s.officer.auspicious) "a configuration that supports beginnings."
            else "a configuration that warns against forcing outcomes."

    private fun hoursNote(s: LuopanSnapshot): String {
        val best = s.bestHours.firstOrNull() ?: return ""
        val second = s.bestHours.getOrNull(1)
        val secondText = second?.let { "; ${it.range} (${it.branch.pinyin}) also holds" } ?: ""
        return "The strongest window is ${best.range} (${best.branch.pinyin} hour, ${best.officer.en})$secondText."
    }

    private fun voidNote(s: LuopanSnapshot) =
        if (s.day.branch in s.voidBranches)
            "The day branch ${s.day.branch.pinyin} falls in the void of its ten-day frame — what is started in it tends to evaporate."
        else "Nothing of consequence falls into the void."

    // ------------------------------------------------------------------ overall

    private fun overall(s: LuopanSnapshot, score: Int, verdict: Verdict, factors: List<Factor>): Text {
        val headline = when (verdict) {
            Verdict.EXCEPTIONAL -> "The plate is wide open."
            Verdict.FAVORABLE -> "A day that leans your way."
            Verdict.MILDLY_GOOD -> "Quietly workable, quietly good."
            Verdict.MIXED -> "Half the sky is clear."
            Verdict.CAUTIOUS -> "Do not force the day."
            Verdict.UNFAVORABLE -> "Hold the line, and spend the day carefully."
        }
        val summary = buildString {
            append(strengthNote(s))
            append(" ")
            append(officerNote(s))
            append(" ")
            append("The sun sits in the ${s.sunPalace.pinyin} mountain (${s.sunPalace.element.en}), which sets the day's axis. ")
            append(hoursNote(s))
        }
        val insights = buildList {
            add("Solar term: ${s.term.term.en} (${s.term.term.chinese}), which governs the month pillar ${s.month}.")
            add("Your element is ${s.day.element.en}; the month's is ${s.month.branch.element.en}. ${strengthNote(s)}")
            add(officerNote(s))
            if (s.day.branch == Branch.clashOf(s.month.branch)) {
                add("The day branch ${s.day.branch.pinyin} stands opposite the month branch ${s.month.branch.pinyin} — this is the 'clashing the month' configuration, and it lands on friction with rules, superiors or fixed arrangements.")
            } else if (Branch.harmonyOf(s.day.branch) == s.month.branch) {
                add("The day branch ${s.day.branch.pinyin} combines with the month branch ${s.month.branch.pinyin} — cooperation is easier ${dayWord(s)} than usual.")
            } else {
                add("Nothing in the day directly clashes the month branch ${s.month.branch.pinyin}, so external friction stays low.")
            }
            if (s.day.branch in s.noblemen) add("Your day branch ${s.day.branch.pinyin} is one of the Nobleman branches (${s.noblemen.joinToString { it.pinyin }}) — help is likelier to arrive from the side.")
            add(voidNote(s))
        }
        val advice = buildList {
            add(if (s.auspiciousHourCount >= 7)
                "Seven or more double-hours are favourable. Put your heaviest thinking in the first favourable window after 09:00."
            else
                "Only ${s.auspiciousHourCount} of 12 double-hours are favourable — pick one window and protect it.")
            add(if (s.strength == CalendarEngine.DailyStrength.SI || s.strength == CalendarEngine.DailyStrength.QIU)
                "Your element is low ${dayWord(s)}. Keep the schedule light: fewer commitments, longer gaps."
            else
                "You have room to spend. Make the important move early, while the day is still strengthening.")
            add("The one direction to leave alone is ${Palace.directionName(s.clashPalace.bearing)} (${s.clashPalace.pinyin}) — it is opposite your day branch and it is where the day rubs against itself.")
            add("Keep records of anything you sign or send ${dayWord(s)}; a void-day signature is the classic thing people regret.")
        }
        return Text(headline, summary, insights, advice)
    }

    // ----------------------------------------------------------------- windfall

    private fun windfall(s: LuopanSnapshot, score: Int, verdict: Verdict, factors: List<Factor>): Text {
        val headline = when (verdict) {
            Verdict.EXCEPTIONAL -> "Money has a way in."
            Verdict.FAVORABLE -> "The door to gain is ajar."
            Verdict.MILDLY_GOOD -> "Small gains are on offer."
            Verdict.MIXED -> "The trail is cold for money."
            Verdict.CAUTIOUS -> "What arrives will cost you more than it gives."
            Verdict.UNFAVORABLE -> "Keep your hands off the purse."
        }
        val summary = buildString {
            append("Your wealth element is ${s.wealthElement.en}, and the sun rests in the ${s.sunPalace.pinyin} mountain (${s.sunPalace.element.en}). ")
            append(
                when {
                    s.sunPalace.element == s.wealthElement ->
                        "Because the two match, the classic configuration for an unexpected gain is present — the day's own axis and your wealth axis point the same way."
                    s.sunPalace.element.generates() == s.wealthElement ->
                        "The day's mountain nourishes your wealth element, so opportunity is fed rather than forced. Gains come gently, and they compound if you take them."
                    s.sunPalace.element.controls() == s.wealthElement ->
                        "The day's mountain cuts across your wealth element: money will be visible and contested. Approach with care and do not fight for what is being trimmed."
                    s.wealthElement.controls() == s.sunPalace.element ->
                        "Your wealth element overpowers the day's mountain. You are the one pressing the advantage — take the initiative rather than waiting to be chosen."
                    else ->
                        "Your wealth element feeds the day's mountain, which means energy spent on money is spent. The reward is in giving, not in gathering."
                }
            )
            append(" ")
            append(officerNote(s))
        }
        val insights = buildList {
            add("The sun's mountain is ${s.sunPalace.pinyin} (${s.sunPalace.element.en}, ${s.sunPalace.trigram} trigram, ${s.sunPalace.yuan} yuan). It fixes where the day is pointing.")
            add("Wealth direction: ${FortuneEngine.wealthDirectionLabel(s)}. Anything you want to receive, meet or accept from, belongs here.")
            add("Unexpected wealth (pian cai) lives in ${s.unexpectedWealthBranches.joinToString { it.pinyin }} — the 'side' branch of your wealth element, which is exactly what windfall money is.")
            if (s.unexpectedWealthBranches.any { it in s.voidBranches })
                add("That branch falls into the void of the day, so even if the offer arrives it may dissolve before it lands. Treat it as a hint, not a plan.")
            if (s.strength == CalendarEngine.DailyStrength.WANG || s.strength == CalendarEngine.DailyStrength.XIANG)
                add("You are ${s.strength.en.lowercase()} enough to hold money. A strong holder is not dragged by it — the classic advice here is to take the larger position.")
            else
                add("Your element is ${s.strength.en.lowercase()} ${dayWord(s)}. 'Wealth beyond the body's strength' is the warning in the classics: heavy money on a weak day becomes a burden, so prefer the smaller, quicker gain.")
            add("The officer '${s.officer.en}' rules the day. " + if (s.officer == DayOfficer.MAN)
                "'Full' is the collector's officer — it is one of the best signs for gathering."
            else if (s.officer == DayOfficer.PO)
                "'Break' is the opposite sign: it shows money leaving, and is the classic warning against guarantees, advances and lending."
            else
                "'${s.officer.en}' is neutral for money — no strong push either way, so your own discipline decides the outcome.")
            add(hoursNote(s))
        }
        val advice = buildList {
            add("Face ${Palace.directionName(FortuneEngine.primaryBearing(s.wealthElement))} when you make the ask, the call or the hand-off. Dealings turned towards the wealth direction run cleaner.")
            add("Keep away from ${Palace.directionName(s.clashPalace.bearing)} (${s.clashPalace.pinyin}) — that is where ${dayPossessive(s)} gains get cancelled.")
            add(if (s.auspiciousHourCount >= 7)
                "There are ${s.auspiciousHourCount} favourable double-hours. Send the request in one of them, not in the first hour that feels convenient."
            else
                "Only ${s.auspiciousHourCount} double-hours favour money ${dayWord(s)}. Pick the single best window and let the rest go quiet.")
            add(if (verdict == Verdict.UNFAVORABLE || verdict == Verdict.CAUTIOUS)
                "Decline advances, guarantees and loans ${dayWord(s)}. 'Break' and weak day-elements are exactly when those come back doubled."
            else
                "Windfall favours speed over size: a small, clean gain taken ${dayWord(s)} is worth more than a larger one negotiated over a long delay.")
        }
        return Text(headline, summary, insights, advice)
    }

    // --------------------------------------------------------------------- love

    private fun love(s: LuopanSnapshot, score: Int, verdict: Verdict, factors: List<Factor>): Text {
        val headline = when (verdict) {
            Verdict.EXCEPTIONAL -> "Warmth is easy to find."
            Verdict.FAVORABLE -> "An open door between people."
            Verdict.MILDLY_GOOD -> "Gentle, receptive energy."
            Verdict.MIXED -> "Half-expressed, half-guarded."
            Verdict.CAUTIOUS -> "Say it carefully, or not at all."
            Verdict.UNFAVORABLE -> "Old grievances are close to the surface."
        }
        val summary = buildString {
            if (s.dayOnly) {
                append("Your day branch ${s.day.branch.pinyin} (${s.day.branch.element.en}) stands at the centre of this day. ")
                append(
                    when {
                        s.day.branch in Branch.triadOf(s.month.branch) ->
                            "It joins the month's three-harmony frame, so the day moves with the season rather than against it."
                        Branch.harmonyOf(s.day.branch) == s.month.branch ->
                            "It pairs with the month branch by six-harmony — the old texts call this the configuration in which people come together easily."
                        s.day.branch == Branch.clashOf(s.month.branch) ->
                            "It stands opposite the month branch, so the day rubs against the season's fixed arrangements."
                        else ->
                            "It has no strong pull toward the month's frame; the day leaves the feeling fairly neutral."
                    }
                )
            } else {
                append("Your day branch ${s.day.branch.pinyin} (${s.day.branch.element.en}) meets the hour branch ${s.hour.branch.pinyin}. ")
                append(
                    when {
                        Branch.harmonyOf(s.day.branch) == s.hour.branch ->
                            "These two branches are a six-harmony pair — the old texts call this the configuration in which people come together easily and are glad to do so."
                        s.hour.branch in Branch.triadOf(s.day.branch) ->
                            "These branches share a three-harmony frame, which gives conversations a shared direction and easy agreement."
                        s.hour.branch == Branch.clashOf(s.day.branch) ->
                            "These two branches clash. In relationships this reads as misread messages, old scores, and the urge to say the sharp thing first."
                        s.hour.branch == Branch.harmOf(s.day.branch) ->
                            "These two branches harm each other — a quiet, niggling friction rather than an open row."
                        else ->
                            "Neither a strong pull nor a direct push: the day leaves feeling fairly neutral, which puts the outcome in your hands."
                    }
                )
            }
            append(" ")
            append(officerNote(s))
        }
        val insights = buildList {
            add("Peach Blossom for your day branch ${s.day.branch.pinyin} falls on ${s.peachBlossom.pinyin} — this is the branch that draws attention and interest your way.")
            add("Red Phoenix sits at ${s.redPhoenix.pinyin}, Heaven's Blessing at ${s.heavenBlessing.pinyin}. They land on ${if (s.day.branch == s.redPhoenix || (s.hour.branch == s.redPhoenix && !s.dayOnly)) "your day or hour" else if (s.dayOnly) "a neighbouring day, not this one" else "a neighbouring day, not today"}.")
            if (s.day.stem.element == s.day.branch.element) {
                add("Your stem ${s.day.stem.pinyin} and branch ${s.day.branch.pinyin} share the element ${s.day.element.en}: you will be read clearly ${dayWord(s)} — what you feel shows on your face.")
            } else {
                add("Your stem ${s.day.stem.pinyin} (${s.day.stem.element.en}) and branch ${s.day.branch.pinyin} (${s.day.branch.element.en}) pull in different directions: say what you mean rather than implying it.")
            }
            if (s.day.branch in s.noblemen) add("Your day branch is a Nobleman branch — the people who help you are the people you least expect to.")
            if (!s.dayOnly) {
                if (s.hour.branch == s.peachBlossom) add("Peach Blossom is lit in this hour as well.")
                if (s.hour.branch == s.redPhoenix) add("Red Phoenix is lit in this hour.")
                if (s.hour.branch == s.heavenBlessing) add("Heaven's Blessing is lit in this hour.")
                add("This hour is ${CalendarEngine.hourOfficer(s.day, s.hour.branch).en}, which sets the tone for anything said right now.")
            } else {
                add("Of the twelve double-hours on this day, ${s.auspiciousHourCount} carry an auspicious officer — the rest are better left to rest.")
            }
        }
        val advice = buildList {
            add("If there is someone you have been meaning to reach, the ${s.bestHours.firstOrNull()?.range ?: "best double-hour"} window is the one the plate favours for a first move.")
            add(if (s.hour.branch == Branch.clashOf(s.day.branch))
                "Do not open a disagreement in the next hour. Write the message, wait for the favourable window, then send."
            else
                if (s.dayOnly)
                    "Use the favourable window for the honest version — the day favours plainness rather than diplomacy."
                else
                    "Use the favourable window for the honest version — today the hour supports plainness rather than diplomacy.")
            add("Make it easy for the other person to say yes: a question beats a statement.")
        }
        return Text(headline, summary, insights, advice)
    }

    // ------------------------------------------------------------------- career

    private fun career(s: LuopanSnapshot, score: Int, verdict: Verdict, factors: List<Factor>): Text {
        val headline = when (verdict) {
            Verdict.EXCEPTIONAL -> "Move. This is the day to move."
            Verdict.FAVORABLE -> "Push while the door is open."
            Verdict.MILDLY_GOOD -> "A workable day for steady progress."
            Verdict.MIXED -> "Progress, but at your own pace."
            Verdict.CAUTIOUS -> "Hold the position you have."
            Verdict.UNFAVORABLE -> "Sign nothing. Change nothing."
        }
        val summary = buildString {
            append("The day's officer is '${s.officer.en}'. ")
            append(
                when (s.officer) {
                    DayOfficer.CHENG -> "'Succeed' is one of the strongest officers for business: it is the day things finish and confirm."
                    DayOfficer.KAI -> "'Open' is the officer of openings — launches, first meetings, and documents that need a beginning."
                    DayOfficer.DING -> "'Stabilise' favours consolidation: locking in what works rather than chasing what is new."
                    DayOfficer.ZHI -> "'Hold' favours retention and steady control — keep what is working and do not over-extend."
                    DayOfficer.PO -> "'Break' breaks things open, and in business it breaks them apart: contracts, plans and partnerships are the exposed tissue."
                    DayOfficer.BI -> "'Close' favours endings. Complete rather than begin; close the file, end the debate."
                    else -> "'${s.officer.en}' is a middling officer for business: nothing decisive, nothing dangerous."
                }
            )
            append(" ")
            append(strengthNote(s))
        }
        val insights = buildList {
            add("You are ${s.strength.en.lowercase()} in a ${s.month.branch.element.en} month. ${if (s.strength.tone >= 0) "You can carry more than you think." else "You can carry less than you think — negotiate accordingly."}")
            if (s.day.branch in s.noblemen || s.hour.branch in s.noblemen) add("A Nobleman branch (${s.noblemen.joinToString { it.pinyin }}) is active — the introduction or the endorsement you need is available.")
            if (!s.dayOnly && s.hour.branch == s.travelStar) add("The Star of Motion falls in this hour (${s.travelStar.pinyin}): postings, travel, relocation and new territory are favoured.")
            add("Opportunity waits in the ${s.wealthElement.en} direction — ${FortuneEngine.wealthDirectionLabel(s)}. Face it when you pitch or ask.")
            if (s.day.branch == Branch.clashOf(s.month.branch)) add("The day clashes the month branch, so institutional friction (rules, budgets, superiors) is the live risk ${dayWord(s)}.")
            add(voidNote(s))
        }
        val advice = buildList {
            add("Put the decisive conversation in ${s.bestHours.firstOrNull()?.range ?: "the first favourable hour"}. " +
                if (s.officer == DayOfficer.CHENG || s.officer == DayOfficer.KAI)
                    "That is the window where the day's officer is on your side."
                else
                    "That is the window where the day actually helps, even if the officer does not.")
            add(if (s.officer == DayOfficer.PO || s.officer == DayOfficer.BI)
                "Delay irreversible steps. Review contracts, then let the day pass."
            else
                "Sign the thing, send the thing, put the date on it. '${s.officer.en}' is wasting daylight if you only prepare.")
            add("Do the outward-facing work facing ${Palace.directionName(FortuneEngine.primaryBearing(s.wealthElement))}; keep the internal work anywhere else.")
        }
        return Text(headline, summary, insights, advice)
    }

    // ------------------------------------------------------------------- health

    private fun health(s: LuopanSnapshot, score: Int, verdict: Verdict, factors: List<Factor>): Text {
        val headline = when (verdict) {
            Verdict.EXCEPTIONAL -> "The body is running clear."
            Verdict.FAVORABLE -> "A good day to build on."
            Verdict.MILDLY_GOOD -> "Mostly settled."
            Verdict.MIXED -> "One system is doing the work."
            Verdict.CAUTIOUS -> "Rest is the treatment."
            Verdict.UNFAVORABLE -> "Your reserves are low. Take it gently."
        }
        val dominant = s.dominantElement
        val thin = s.thinnestElement
        val summary = buildString {
            append("The plate ${dayWord(s)} is dominated by ${dominant.en}, and ${thin.en} is the thinnest presence. ")
            append(elementNote(dominant))
            append(" ")
            append(strengthNote(s))
        }
        val insights = buildList {
            add(elementNote(dominant))
            add("Thinnest element: ${thin.en}. ${elementNote(thin)}")
            if (!s.dayOnly && s.hour.branch == Branch.clashOf(s.day.branch)) add("The hour clashes the day branch — in the body this is the pull of doing too much in too short a time.")
            if (!s.dayOnly && s.hour.branch == Branch.harmOf(s.day.branch)) add("The hour harms the day branch — a slow, niggling fatigue rather than a sharp symptom.")
            add("Your constitution is ${s.strength.en.lowercase()}. " + if (s.strength.tone >= 0)
                "Reserves are good, so training and travel are well supported."
            else
                "Reserves are thin; the classic advice is to borrow strength from the hours that support you.")
            add("Useful hours: ${s.hourWindows.filter { it.score >= 8 }.joinToString { it.branch.pinyin }.ifEmpty { "none strongly — keep the day gentle" }}.")
        }
        val advice = buildList {
            add(when (dominant) {
                WuXing.WOOD -> "Wood rules liver, gallbladder and eyes. Less screen glare, a walk, and something sour or green if it appeals."
                WuXing.FIRE -> "Fire rules heart and small intestine. Protect sleep, keep the mind cool, avoid over-stimulation."
                WuXing.EARTH -> "Earth rules spleen and stomach. Warm, regular meals; skip cold drinks and long gaps between food."
                WuXing.METAL -> "Metal rules lungs and large intestine. Air, humidity and slow breathing matter more than training."
                WuXing.WATER -> "Water rules kidneys and bladder. Hydrate early, and do not push through fatigue."
            })
            add("Support the thin ${thin.en} element through the ${thinHours(s, thin)} hours, and keep the day simple.")
            if (s.strength.tone < 0) add("Your reserves are low — one demanding thing ${dayWord(s)}, not five.")
            else add("Your reserves are good — this is a good day for the effort you have been postponing.")
        }
        return Text(headline, summary, insights, advice)
    }

    private fun thinHours(s: LuopanSnapshot, element: WuXing): String {
        val branches = Branch.entries.filter { it.element == element }
        return branches.joinToString(" / ") { "${it.pinyin} (${LuopanEngine.hourRange(it)})" }
    }

    /** Traditional five-phase care notes, written as everyday sense. */
    private fun elementNote(element: WuXing): String = when (element) {
        WuXing.WOOD -> "Wood governs liver, gallbladder and eyes: this is the element of growth, and while it is loudest it wants movement, green things and unhurried digestion."
        WuXing.FIRE -> "Fire governs heart and small intestine: this is the element of warmth and clarity, and when it is loudest it wants rest, light and calm."
        WuXing.EARTH -> "Earth governs spleen and stomach: this is the element of holding and transformation, and when it is loudest it wants regularity and warmth."
        WuXing.METAL -> "Metal governs lungs and large intestine: this is the element of refinement and release, and when it is loudest it wants air, breathing space and less clutter."
        WuXing.WATER -> "Water governs kidneys and bladder: this is the element of depth and reserve, and when it is loudest it wants rest, warmth and quiet."
    }
}
