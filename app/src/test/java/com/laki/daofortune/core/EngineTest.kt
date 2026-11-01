package com.laki.daofortune.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The engine is checked against golden values produced by an independently
 * written reference implementation (tools/reference/luopan_reference.py).
 * If the two ever disagree, one of them has drifted.
 */
class EngineTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    private fun goldenRows(): List<Map<String, String>> {
        val stream = javaClass.getResourceAsStream("/golden_pillars.tsv")
            ?: error("golden_pillars.tsv missing from test resources")
        val all = stream.bufferedReader().use { it.readLines() }.filter { it.isNotBlank() }
        val header = all.first().split("\t")
        return all.drop(1).map { line ->
            val cells = line.split("\t")
            header.zip(cells).toMap()
        }
    }

    @Test
    fun `golden file is not empty`() {
        assertTrue(goldenRows().size > 700)
    }

    private fun natalRows(): List<Map<String, String>> {
        val stream = javaClass.getResourceAsStream("/golden_natal.tsv")
            ?: error("golden_natal.tsv missing from test resources")
        val all = stream.bufferedReader().use { it.readLines() }.filter { it.isNotBlank() }
        val header = all.first().split("\t")
        return all.drop(1).map { line ->
            val cells = line.split("\t")
            header.zip(cells).toMap()
        }
    }

    @Test
    fun `pillars match the reference implementation on every sample`() {
        val rows = goldenRows()
        var checked = 0
        rows.forEach { row ->
            val moment = LocalDateTime.parse("${row["date"]}T${row["time"]}")
            val day = CalendarEngine.dayGanZhi(moment)
            val month = CalendarEngine.monthGanZhi(moment, zone)
            val year = CalendarEngine.yearGanZhi(moment, zone)
            val hour = CalendarEngine.hourGanZhi(moment)
            val context = "${row["date"]} ${row["time"]}"
            assertEquals("$context day pillar", row["day"], day.toString())
            assertEquals("$context month pillar", row["month"], month.toString())
            assertEquals("$context year pillar", row["year"], year.toString())
            assertEquals("$context hour pillar", row["hourPillar"], hour.toString())
            assertEquals("$context day officer", row["officer"], CalendarEngine.dayOfficer(day, month).name)
            assertEquals(
                "$context hour officer",
                row["hourOfficer"],
                CalendarEngine.hourOfficer(day, hour.branch).name
            )
            checked++
        }
        assertTrue(checked > 300)
    }

    @Test
    fun `solar term and sun palace match the reference implementation`() {
        goldenRows().forEach { row ->
            val moment = LocalDateTime.parse("${row["date"]}T${row["time"]}")
            val term = SolarTerms.currentTerm(moment, zone).term
            assertEquals("${row["date"]} solar term", row["term"], term.pinyin)

            val snapshot = LuopanEngine.snapshot(moment, zone)
            assertEquals("${row["date"]} sun palace", row["sunPalace"], snapshot.sunPalace.pinyin)
            assertEquals(
                "${row["date"]} sun longitude",
                row["sunLongitude"]!!.toDouble(),
                snapshot.sunLongitude,
                0.02
            )
            assertEquals(
                "${row["date"]} void branches",
                row["void"],
                snapshot.voidBranches.sortedBy { it.ordinal }.joinToString("|") {
                    it.pinyin
                }
            )
        }
    }

    @Test
    fun `known calendar anchors`() {
        assertEquals("JiaZi", CalendarEngine.dayGanZhi(LocalDateTime.of(1949, 10, 1, 12, 0)).toString())
        assertEquals("WuWu", CalendarEngine.dayGanZhi(LocalDateTime.of(2000, 1, 1, 12, 0)).toString())
        assertEquals("BingYin", CalendarEngine.dayGanZhi(LocalDateTime.of(1984, 2, 2, 12, 0)).toString())
        assertEquals("JiaChen", CalendarEngine.dayGanZhi(LocalDateTime.of(2024, 2, 10, 12, 0)).toString())
    }

    /**
     * A birth day can be anywhere in a reader's life, far outside the two
     * years the daily golden file covers. These samples walk 1930–2020 in
     * 97-day steps; the day pillar of every one must match the reference.
     */
    @Test
    fun `birthday pillars match the reference implementation`() {
        val stream = javaClass.getResourceAsStream("/golden_birthdays.tsv")
            ?: error("golden_birthdays.tsv missing from test resources")
        val all = stream.bufferedReader().use { it.readLines() }.filter { it.isNotBlank() }
        val rows = all.drop(1)
        assertTrue(rows.size > 300)
        rows.forEach { line ->
            val (date, day) = line.split("\t")
            val moment = LocalDateTime.parse("${date}T12:00")
            assertEquals("$date birth-day pillar", day, CalendarEngine.dayGanZhi(moment).toString())
        }
    }

    @Test
    fun `the day turns over at 23 00`() {
        val justBefore = LocalDateTime.of(2026, 5, 5, 22, 59)
        val justAfter = LocalDateTime.of(2026, 5, 5, 23, 0)
        assertTrue(CalendarEngine.dayGanZhi(justBefore) != CalendarEngine.dayGanZhi(justAfter))
    }

    @Test
    fun `the year turns over at Li Chun and not on New Year`() {
        val beforeLiChun = CalendarEngine.yearGanZhi(LocalDateTime.of(2026, 1, 20, 12, 0), zone)
        val afterLiChun = CalendarEngine.yearGanZhi(LocalDateTime.of(2026, 3, 1, 12, 0), zone)
        assertEquals("YiSi", beforeLiChun.toString())
        assertEquals("BingWu", afterLiChun.toString())
    }

    @Test
    fun `hour officers start at the zi hour`() {
        val jiaDay = (0L..90L)
            .map { LocalDateTime.of(2026, 10, 1, 12, 0).plusDays(it) }
            .first { CalendarEngine.dayGanZhi(it).stem == Stem.JIA }
        val day = CalendarEngine.dayGanZhi(jiaDay)
        assertEquals("Jia", day.stem.pinyin)
        assertEquals(HourOfficer.QING_LONG, CalendarEngine.hourOfficer(day, Branch.ZI))
        assertEquals(HourOfficer.MING_TANG, CalendarEngine.hourOfficer(day, Branch.CHOU))
    }

    @Test
    fun `the sun sits in the expected mountain at the equinoxes and solstices`() {
        assertEquals("Mao", LuopanEngine.snapshot(LocalDateTime.of(2026, 3, 20, 12, 0), zone).sunPalace.pinyin)
        assertEquals("Wu", LuopanEngine.snapshot(LocalDateTime.of(2026, 6, 21, 12, 0), zone).sunPalace.pinyin)
        assertEquals("You", LuopanEngine.snapshot(LocalDateTime.of(2026, 9, 23, 12, 0), zone).sunPalace.pinyin)
        assertEquals("Zi", LuopanEngine.snapshot(LocalDateTime.of(2026, 12, 21, 12, 0), zone).sunPalace.pinyin)
    }

    @Test
    fun `twenty four mountains cover the dial once each`() {
        val bearings = Palace.entries.map { it.bearing }.sorted()
        assertEquals(24, bearings.size)
        bearings.forEachIndexed { index, bearing ->
            assertEquals(index * 15, bearing)
        }
    }

    @Test
    fun `the wealth sector is four mountains, either a quadrant or the four corners`() {
        // The dial draws one fifteen-degree arc per mountain of the wealth
        // element. That only works because the four mountains of one element
        // either sit together (water on the north side) or take the four
        // corners (earth). A set that spread over the dial would be drawn as a
        // band that wraps the whole circle - which is what the dial used to do.
        WuXing.entries.forEach { element ->
            val palaces = LuopanEngine.sectorFor(element)
            assertEquals("four mountains for $element", 4, palaces.size)
            val bearings = palaces.map { it.bearing }.toSet()
            assertEquals("four distinct mountains for $element", 4, bearings.size)

            val consecutive = (0 until 360 step 15).any { base ->
                bearings == setOf(base, (base + 15) % 360, (base + 30) % 360, (base + 45) % 360)
            }
            val corners = bearings == setOf(30, 120, 210, 300)
            assertTrue(
                "the mountains of $element must be a quadrant or the four corners, not $bearings",
                consecutive || corners
            )
        }
    }

    @Test
    fun `every module produces a bounded score and a verdict`() {
        val snapshot = LuopanEngine.snapshot(LocalDateTime.of(2026, 10, 31, 12, 0), zone)
        FortuneModule.entries.forEach { module ->
            val reading = FortuneEngine.reading(snapshot, module)
            assertTrue("${module.name} score in range", reading.score in 0..100)
            assertEquals(Verdict.of(reading.score), reading.verdict)
            assertTrue("${module.name} has a headline", reading.headline.isNotBlank())
            assertTrue("${module.name} has insights", reading.insights.isNotEmpty())
            assertTrue("${module.name} has advice", reading.advice.isNotEmpty())
            assertEquals(2, reading.bestHours.size)
        }
    }

    @Test
    fun `every module produces a usable preview headline`() {
        // The headline is the only thing a locked module shows for free, so it
        // has to stand on its own: one line, a sentence, and short enough to
        // sit on a home card without wrapping into a paragraph.
        val moments = listOf(
            LocalDateTime.of(2026, 10, 31, 12, 0),
            LocalDateTime.of(2026, 2, 4, 7, 30),
            LocalDateTime.of(2026, 6, 21, 23, 0),
            LocalDateTime.of(2027, 1, 1, 0, 0)
        )
        moments.forEach { moment ->
            val snapshot = LuopanEngine.snapshot(moment, zone)
            FortuneModule.entries.forEach { module ->
                val reading = FortuneEngine.reading(snapshot, module)
                val headline = reading.headline
                val where = "${module.name} @ $moment"
                assertTrue("$where preview is not blank", headline.isNotBlank())
                assertTrue("$where preview is a single line", !headline.contains('\n') && !headline.contains('\r'))
                assertTrue("$where preview is a sentence", headline.endsWith("."))
                assertTrue("$where preview is card-sized", headline.length <= 90)
                assertTrue("$where preview hides the score", !headline.contains(reading.score.toString()))
                assertTrue("$where preview is not the full reading", headline.length < reading.summary.length)
            }
        }
    }

    @Test
    fun `readings are deterministic for the same moment`() {
        val moment = LocalDateTime.of(2026, 6, 21, 9, 0)
        val first = FortuneEngine.reading(LuopanEngine.snapshot(moment, zone), FortuneModule.WINDFALL)
        val second = FortuneEngine.reading(LuopanEngine.snapshot(moment, zone), FortuneModule.WINDFALL)
        assertEquals(first.score, second.score)
        assertEquals(first.headline, second.headline)
    }

    @Test
    fun `the avoid direction is opposite the day branch`() {
        val snapshot = LuopanEngine.snapshot(LocalDateTime.of(2026, 10, 31, 12, 0), zone)
        val clash = Branch.clashOf(snapshot.day.branch)
        assertEquals(clash.ordinal * 30, snapshot.clashPalace.bearing)
        // The 寅 day of 2026 clashes 申, which sits on the south-west mountain.
        assertEquals("Shen", snapshot.clashPalace.pinyin)
    }

    @Test
    fun `branches sit every thirty degrees and the clash is the opposite mountain`() {
        Branch.entries.forEach { branch ->
            val bearing = Branch.bearingOf(branch)
            assertEquals(branch.ordinal * 30, bearing)
            val palace = Branch.palaceOf(branch)
            assertEquals(palace.bearing, bearing)
            val opposite = Branch.palaceOf(Branch.clashOf(branch))
            assertEquals(180, Math.abs(opposite.bearing - bearing))
        }
    }

    // ------------------------------------------------------------------ day plates

    @Test
    fun `a day plate carries the pillars of the day it names`() {
        val dates = listOf(
            LocalDate.of(2026, 11, 3),
            LocalDate.of(2026, 12, 21),
            LocalDate.of(2027, 2, 4),
            LocalDate.of(2027, 7, 15),
            LocalDate.of(2028, 2, 29)
        )
        dates.forEach { date ->
            val plate = LuopanEngine.dayPlate(date, zone)
            assertTrue("${date} is a day plate", plate.dayOnly)

            // The day pillar belongs to the calendar day that was asked for:
            // the day turns at 23:00, so midnight and noon agree.
            assertEquals(
                "${date} day pillar",
                CalendarEngine.dayGanZhi(date.atStartOfDay()).toString(),
                plate.day.toString()
            )

            // The month and year pillars turn at a solar term, so they must be
            // read at the plate's own reference moment — a term that falls
            // between midnight and noon legitimately splits the two.
            val reference = date.atTime(12, 0)
            assertEquals("${date} year pillar", CalendarEngine.yearGanZhi(reference, zone).toString(), plate.year.toString())
            assertEquals("${date} month pillar", CalendarEngine.monthGanZhi(reference, zone).toString(), plate.month.toString())
        }
    }

    @Test
    fun `the year pillar turns at Li Chun in the middle of the day`() {
        // 2027-02-04 is Li Chun: the year changes in the middle of that day.
        // Before the term the year is still 2026 (Bing Wu); after it, 2027 (Ding Wei).
        val before = CalendarEngine.yearGanZhi(LocalDateTime.of(2027, 2, 4, 0, 0), zone)
        val after = CalendarEngine.yearGanZhi(LocalDateTime.of(2027, 2, 4, 12, 0), zone)
        assertEquals("BingWu", before.toString())
        assertEquals("DingWei", after.toString())
    }

    @Test
    fun `a day plate never lets the hour colour the reading`() {
        // These are the factors that only make sense for "right now".
        val hourLabels = listOf(
            "This hour", "Hour branch falls in the void", "Day and hour combine",
            "Hour joins your day frame", "Hour clashes the day",
            "Day and hour branches combine", "Hour harms the day",
            "Star of Motion — money is on the move", "Star of Motion — posts, travel, change"
        )
        val plate = LuopanEngine.dayPlate(LocalDate.of(2026, 11, 3), zone)
        FortuneModule.entries.forEach { module ->
            val reading = FortuneEngine.reading(plate, module)
            val offending = reading.factors.map { it.label }.filter { label ->
                hourLabels.any { label.startsWith(it) }
            }
            assertTrue("${module.name} has no hour factor: $offending", offending.isEmpty())
        }
    }

    @Test
    fun `a day plate and the same moment in the day agree on the day itself`() {
        val date = LocalDate.of(2026, 11, 3)
        val plate = LuopanEngine.dayPlate(date, zone)
        val moment = LuopanEngine.snapshot(date.atTime(12, 0), zone)
        assertEquals(plate.day.toString(), moment.day.toString())
        assertEquals(plate.month.toString(), moment.month.toString())
        assertEquals(plate.year.toString(), moment.year.toString())
        assertEquals(plate.sunPalace.pinyin, moment.sunPalace.pinyin)
        assertEquals(plate.officer, moment.officer)
    }

    @Test
    fun `the twelve double hours belong to the day, not to the moment`() {
        val plate = LuopanEngine.dayPlate(LocalDate.of(2026, 11, 3), zone)
        assertEquals(12, plate.hourWindows.size)
        assertTrue(plate.hourWindows.any { it.score > 0 })
        assertTrue(plate.hourWindows.any { it.score < 0 })
    }

    // ------------------------------------------------------------------ year sign

    @Test
    fun `the year sign layer matches the reference implementation`() {
        val rows = natalRows()
        assertTrue(rows.size > 300)
        rows.forEach { row ->
            val natal = Branch.entries.first { it.pinyin == row["natal"] }
            val moment = LocalDateTime.parse("${row["date"]}T12:00")
            val day = CalendarEngine.dayGanZhi(moment)
            assertEquals("${row["date"]} day pillar", row["day"], day.toString())
            val relation = Natal.DayRelation.of(natal, day.branch)
            assertEquals("${row["natal"]} vs ${row["date"]}", row["relation"], relation.name)
            val tone = Natal.elementTone(natal.element, day.element)
            assertEquals("${row["natal"]} vs ${row["date"]} element", row["elementTone"]!!.toInt(), tone)
        }
    }

    @Test
    fun `every year sign maps to exactly one branch and back`() {
        Branch.entries.forEach { branch ->
            val sign = YearSign.of(branch)
            assertEquals(branch, sign.branch)
            assertEquals(branch, YearSign.ofOrdinal(sign.ordinal).branch)
        }
        assertEquals(12, YearSign.entries.size)
    }

    @Test
    fun `the year sign changes the reading and says so`() {
        val plate = LuopanEngine.dayPlate(LocalDate.of(2026, 11, 3), zone)
        val plain = FortuneEngine.reading(plate, FortuneModule.OVERALL)
        assertTrue(
            "no year-sign factor without a year sign",
            plain.factors.none { it.label.contains("year sign") }
        )

        val withSign = FortuneEngine.reading(plate, FortuneModule.OVERALL, Branch.ZI)
        val natalFactors = withSign.factors.filter { it.label.contains("year sign") }
        assertTrue("the year sign contributes factors", natalFactors.isNotEmpty())
        assertTrue(
            "the year sign notes reach the prose",
            withSign.insights.any { it.contains("year sign") }
        )

        val otherSign = FortuneEngine.reading(plate, FortuneModule.OVERALL, Branch.WU)
        assertTrue(
            "different year signs read differently",
            withSign.factors.filter { it.label.contains("year sign") } !=
                otherSign.factors.filter { it.label.contains("year sign") }
        )
    }

    @Test
    fun `the year sign reaches the windfall reading too`() {
        val plate = LuopanEngine.dayPlate(LocalDate.of(2026, 11, 3), zone)
        val reading = FortuneEngine.reading(plate, FortuneModule.WINDFALL, Branch.SHEN)
        assertTrue(
            "the windfall reading mentions the year sign",
            reading.insights.any { it.contains("year sign") }
        )
    }

    // ------------------------------------------------------------ favourable dates

    @Test
    fun `the forward scan is ranked and deterministic`() {
        val start = LocalDate.of(2026, 10, 31)
        val first = FavourableDates.scan(start, zone, null)
        val second = FavourableDates.scan(start, zone, null)
        assertEquals(first.map { it.date }, second.map { it.date })
        assertEquals(first.map { it.score }, second.map { it.score })
        assertEquals(FavourableDates.SHOWN, first.size)

        val scores = first.map { it.score }
        assertEquals("ranked best first", scores.sortedDescending(), scores)
        assertTrue("every day is inside the horizon", first.all { !it.date.isBefore(start) })
        assertTrue("no day is beyond the horizon", first.all { it.date.isBefore(start.plusDays(FavourableDates.HORIZON_DAYS.toLong())) })
        first.forEach { day ->
            assertTrue("${day.date} has a headline", day.headline.isNotBlank())
            assertTrue("${day.date} has a day pillar", day.dayPillar.isNotBlank())
            assertTrue("${day.date} has a term", day.term.isNotBlank())
        }
        // Today is inside the horizon, but it need not make the short list.
        val whole = FavourableDates.scan(start, zone, null, horizonDays = 60, shown = 60)
        assertTrue("the scan marks today", whole.any { it.isToday })
        assertEquals("today is the first day scanned", start, whole.first { it.isToday }.date)
    }

    @Test
    fun `the forward scan uses day plates, not the current hour`() {
        val start = LocalDate.of(2026, 10, 31)
        val days = FavourableDates.scan(start, zone, null, horizonDays = 3, shown = 3)
        assertEquals(3, days.size)
        days.forEach { day ->
            val plate = LuopanEngine.dayPlate(day.date, zone)
            val expected = FortuneEngine.reading(plate, FortuneModule.WINDFALL, null)
            assertEquals("${day.date} score", expected.score, day.score)
            assertEquals("${day.date} headline", expected.headline, day.headline)
        }
    }
}
