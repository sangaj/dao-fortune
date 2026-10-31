package com.laki.daofortune.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * The dream layer: the vocabulary, the extraction and the reading.
 *
 * The golden file comes from the independent Python implementation, so if the
 * two engines ever disagree about what a dream contains or what it scores,
 * one of them has drifted.
 */
class DreamTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    private fun dreamRows(): List<Map<String, String>> {
        val stream = javaClass.getResourceAsStream("/golden_dream.tsv")
            ?: error("golden_dream.tsv missing from test resources")
        val all = stream.bufferedReader().use { it.readLines() }.filter { it.isNotBlank() }
        val header = all.first().split("\t")
        return all.drop(1).map { line ->
            val cells = line.split("\t")
            header.zip(cells).toMap()
        }
    }

    @Test
    fun `the vocabulary finds the animal and the water`() {
        val image = DreamEngine.extract("A black snake by a river")
        assertEquals(Branch.SI, image.principal?.branch)
        assertEquals(DreamSpirit.TENG_SHE, image.principal?.spirit)
        assertTrue(image.tokens.any { it.kind == DreamKind.NATURE && it.element == WuXing.WATER })
    }

    @Test
    fun `a word is never found inside another word`() {
        val image = DreamEngine.extract("I was grateful for the day")
        assertTrue("no images should be found, got ${image.tokens}", image.tokens.isEmpty())
    }

    @Test
    fun `a compound direction is read as one direction`() {
        assertEquals(Branch.CHOU, DreamEngine.extract("I walked north-east").principal?.branch)
        assertEquals(Branch.WU, DreamEngine.extract("I walked south").principal?.branch)
        assertEquals(Branch.ZI, DreamEngine.extract("I walked north").principal?.branch)
    }

    @Test
    fun `eleven is not read as one`() {
        assertEquals(Branch.XU, DreamEngine.extract("eleven birds").principal?.branch)
    }

    @Test
    fun `a bare number is a number and not an hour`() {
        val image = DreamEngine.extract("three cats")
        assertEquals(DreamKind.NUMBER, image.tokens.first().kind)
        assertNull("a bare number is not an hour", image.hourBranch)
    }

    @Test
    fun `a clock time is read as the double hour`() {
        assertEquals(Branch.YIN, DreamEngine.extract("woke at 3:30").hourBranch)
        assertEquals(Branch.ZI, DreamEngine.extract("woke at midnight").hourBranch)
    }

    @Test
    fun `the given hour of waking wins over the text`() {
        val image = DreamEngine.extract("I dreamt of a tiger", java.time.LocalTime.of(7, 30))
        assertEquals(Branch.CHEN, image.hourBranch)
    }

    @Test
    fun `a dream with no image has no omen`() {
        val image = DreamEngine.extract("nothing happened at all")
        assertNull(image.principal)
        assertTrue(image.tokens.isEmpty())
    }

    @Test
    fun `the same dream always draws the same plate`() {
        val first = DreamEngine.extract("A black snake by a river")
        val second = DreamEngine.extract("A black snake by a river")
        assertEquals(first.seed, second.seed)
    }

    @Test
    fun `money in a dream is the sign of wealth`() {
        val image = DreamEngine.extract("I found a pile of money")
        assertTrue(image.tokens.any { it.wealth })
    }

    @Test
    fun `the reading is bounded and explains itself`() {
        val plate = LuopanEngine.dayPlate(LocalDate.of(2026, 1, 5), zone)
        val image = DreamEngine.extract("A black snake by a river in the south, at dawn.")
        val reading = DreamEngine.reading(plate, image)
        assertTrue("score ${reading.score}", reading.score in 0..100)
        assertTrue("the reading must show its factors", reading.factors.isNotEmpty())
        assertTrue("the headline must not be blank", reading.headline.isNotBlank())
        assertTrue("the summary must not be blank", reading.summary.isNotBlank())
    }

    @Test
    fun `the python reference and the kotlin engine agree on every dream`() {
        val rows = dreamRows()
        assertTrue("the golden file must not be empty", rows.isNotEmpty())
        var checked = 0
        for (row in rows) {
            val date = LocalDate.parse(row.getValue("date"))
            val plate = LuopanEngine.dayPlate(date, zone)
            val image = DreamEngine.extract(row.getValue("text"))
            val reading = DreamEngine.reading(plate, image)

            val tokens = image.tokens.joinToString("|") {
                "${it.kind}:${it.term}:${it.branch?.pinyin ?: ""}"
            }
            assertEquals("tokens for '${row.getValue("text")}' on ${row.getValue("date")}",
                row.getValue("tokens"), tokens)
            assertEquals("omen for '${row.getValue("text")}'",
                row.getValue("omen"), image.principal?.term ?: "")
            assertEquals("omen branch for '${row.getValue("text")}'",
                row.getValue("omenBranch"), image.principal?.branch?.pinyin ?: "")
            assertEquals("hour for '${row.getValue("text")}'",
                row.getValue("hour"), image.hourBranch?.pinyin ?: "")
            assertEquals("score for '${row.getValue("text")}' on ${row.getValue("date")}",
                row.getValue("score"), reading.score.toString())
            assertEquals("deltas for '${row.getValue("text")}' on ${row.getValue("date")}",
                row.getValue("deltas"),
                reading.factors.joinToString("|") { it.delta.toString() })
            checked++
        }
        assertTrue("checked $checked dreams", checked >= 90)
    }
}
