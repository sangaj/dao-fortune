package com.laki.daofortune.core

import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Dumps real readings to docs/store/reading_sample.json so the store-graphics
 * generator can render screenshots from the actual engine output instead of
 * invented text.
 *
 * Three things are dumped:
 *  * the plate of today (with the hour), which is what the home screens show;
 *  * the plate of a day ahead, which is what a chosen date shows;
 *  * the forward scan, which is what the "favourable days" list shows.
 */
class SampleReadingDump {

    @Test
    fun dumpSampleReading() {
        val zone = ZoneId.of("Asia/Shanghai")
        val moment = LocalDateTime.of(2026, 10, 31, 12, 0)
        val today = LocalDate.of(2026, 10, 31)
        val ahead = LocalDate.of(2026, 11, 12)

        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"date\": \"").append(moment.format(dateFormat)).append("\",\n")
        sb.append("  \"time\": \"").append(moment.format(timeFormat)).append("\",\n")
        sb.append("  \"dayPlateDate\": \"")
            .append(ahead.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH)))
            .append("\",\n")
        sb.append("  \"dayPlateDateShort\": \"")
            .append(ahead.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)))
            .append("\",\n")
        sb.append("  \"plate\": ").append(plateJson(LuopanEngine.snapshot(moment, zone), true, 2)).append(",\n")
        sb.append("  \"dayPlate\": ").append(plateJson(LuopanEngine.dayPlate(ahead, zone), false, 2)).append(",\n")
        sb.append("  \"favourableDays\": [\n")

        val favourable = FavourableDates.scan(today, zone, null)
        favourable.forEachIndexed { index, day ->
            sb.append("    {\n")
            sb.append("      \"date\": \"")
                .append(day.date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)))
                .append("\",\n")
            sb.append("      \"fullDate\": \"").append(day.date.format(dateFormat)).append("\",\n")
            sb.append("      \"score\": ").append(day.score).append(",\n")
            sb.append("      \"verdict\": \"").append(day.verdict.label).append("\",\n")
            sb.append("      \"headline\": \"").append(esc(day.headline)).append("\",\n")
            sb.append("      \"module\": \"").append(day.module.short).append("\",\n")
            sb.append("      \"dayPillar\": \"").append(day.dayPillar).append("\",\n")
            sb.append("      \"term\": \"").append(esc(day.term)).append("\",\n")
            sb.append("      \"isToday\": ").append(day.isToday).append("\n")
            sb.append("    }").append(if (index == favourable.lastIndex) "" else ",").append("\n")
        }
        sb.append("  ],\n")
        sb.append("  \"dream\": ").append(dreamJson(zone, moment)).append(",\n")
        sb.append("  \"modules\": [\n")

        val readings = FortuneModule.entries.map { FortuneEngine.reading(LuopanEngine.snapshot(moment, zone), it) }
        readings.forEachIndexed { index, reading ->
            sb.append("    {\n")
            sb.append("      \"title\": \"").append(reading.module.title).append("\",\n")
            sb.append("      \"blurb\": \"").append(reading.module.blurb).append("\",\n")
            sb.append("      \"score\": ").append(reading.score).append(",\n")
            sb.append("      \"verdict\": \"").append(reading.verdict.label).append("\",\n")
            sb.append("      \"headline\": \"").append(esc(reading.headline)).append("\",\n")
            sb.append("      \"summary\": \"").append(esc(reading.summary)).append("\",\n")
            sb.append("      \"insights\": [")
            reading.insights.forEachIndexed { i, line ->
                if (i > 0) sb.append(", ")
                sb.append("\"").append(esc(line)).append("\"")
            }
            sb.append("],\n")
            sb.append("      \"advice\": [")
            reading.advice.forEachIndexed { i, line ->
                if (i > 0) sb.append(", ")
                sb.append("\"").append(esc(line)).append("\"")
            }
            sb.append("],\n")
            sb.append("      \"hours\": [")
            reading.bestHours.forEachIndexed { i, hour ->
                if (i > 0) sb.append(", ")
                sb.append("{\"range\": \"").append(hour.range).append("\", \"branch\": \"")
                    .append(hour.branch.pinyin).append("\", \"officer\": \"").append(hour.officer.en).append("\"}")
            }
            sb.append("]\n")
            sb.append("    }").append(if (index == readings.lastIndex) "" else ",").append("\n")
        }
        sb.append("  ]\n")
        sb.append("}\n")

        val root = findProjectRoot()
        val out = File(root, "docs/store/reading_sample.json")
        out.parentFile.mkdirs()
        out.writeText(sb.toString(), Charsets.UTF_8)
        println("Wrote ${out.absolutePath}")
    }

    /**
     * A real dream reading, so the dream screenshot is rendered from engine
     * output rather than from invented text.
     *
     * The dream belongs to the night of 31 October: the reader woke at 05:30,
     * and a dream woken after three in the morning is read against the day it
     * woke in.
     */
    private fun dreamJson(zone: ZoneId, moment: LocalDateTime): String {
        val text = "Money on a table, a tiger in the north."
        val wokeAt = LocalTime.of(5, 30)
        val plate = LuopanEngine.dayPlate(moment.toLocalDate(), zone)
        val image = DreamEngine.extract(text, wokeAt)
        val natal = Branch.ZI
        val reading = DreamEngine.reading(plate, image, natal)

        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("    \"text\": \"").append(esc(text)).append("\",\n")
        sb.append("    \"wokeAt\": \"")
            .append(wokeAt.format(DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)))
            .append("\",\n")
        sb.append("    \"contextLabel\": \"").append(esc(reading.contextLabel)).append("\",\n")
        sb.append("    \"day\": \"").append(plate.day).append("\",\n")
        sb.append("    \"seed\": ").append(image.seed).append(",\n")
        sb.append("    \"score\": ").append(reading.score).append(",\n")
        sb.append("    \"verdict\": \"").append(reading.verdict.label).append("\",\n")
        sb.append("    \"headline\": \"").append(esc(reading.headline)).append("\",\n")
        sb.append("    \"summary\": \"").append(esc(reading.summary)).append("\",\n")
        sb.append("    \"sunPalace\": \"").append(plate.sunPalace.pinyin).append("\",\n")
        sb.append("    \"wealthElement\": \"").append(plate.wealthElement.en).append("\",\n")
        sb.append("    \"natalPalace\": \"").append(Branch.palaceOf(natal).pinyin).append("\",\n")
        sb.append("    \"insights\": [")
        reading.insights.forEachIndexed { i, line ->
            if (i > 0) sb.append(", ")
            sb.append("\"").append(esc(line)).append("\"")
        }
        sb.append("],\n")
        sb.append("    \"advice\": [")
        reading.advice.forEachIndexed { i, line ->
            if (i > 0) sb.append(", ")
            sb.append("\"").append(esc(line)).append("\"")
        }
        sb.append("],\n")
        sb.append("    \"factors\": [")
        reading.factors.forEachIndexed { i, factor ->
            if (i > 0) sb.append(", ")
            sb.append("{\"label\": \"").append(esc(factor.label))
                .append("\", \"delta\": ").append(factor.delta).append("}")
        }
        sb.append("],\n")
        sb.append("    \"tokens\": [\n")
        image.tokens.forEachIndexed { index, token ->
            sb.append("      {\n")
            sb.append("        \"term\": \"").append(esc(token.term)).append("\",\n")
            sb.append("        \"chinese\": \"").append(token.chinese).append("\",\n")
            sb.append("        \"kind\": \"").append(token.kind.name).append("\",\n")
            sb.append("        \"palace\": \"").append(token.palace?.pinyin ?: "").append("\",\n")
            sb.append("        \"bearing\": ").append(token.palace?.bearing ?: 0).append(",\n")
            sb.append("        \"principal\": ").append(token == image.principal).append("\n")
            sb.append("      }").append(if (index == image.tokens.lastIndex) "" else ",").append("\n")
        }
        sb.append("    ]\n")
        sb.append("  }")
        return sb.toString()
    }

    /** One plate as a JSON object, with its own module readings. */
    private fun plateJson(snapshot: LuopanSnapshot, withHour: Boolean, indent: Int): String {
        val pad = " ".repeat(indent)
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append(pad).append("  \"term\": \"").append(snapshot.term.term.en).append("\",\n")
        sb.append(pad).append("  \"termChinese\": \"").append(snapshot.term.term.chinese).append("\",\n")
        sb.append(pad).append("  \"officer\": \"").append(snapshot.officer.en).append("\",\n")
        sb.append(pad).append("  \"officerDeity\": \"").append(snapshot.officer.deity).append("\",\n")
        sb.append(pad).append("  \"year\": \"").append(snapshot.year).append("\",\n")
        sb.append(pad).append("  \"yearNumber\": ").append(snapshot.yearNumber).append(",\n")
        sb.append(pad).append("  \"yearAnimal\": \"").append(snapshot.year.branch.animal).append("\",\n")
        sb.append(pad).append("  \"month\": \"").append(snapshot.month).append("\",\n")
        sb.append(pad).append("  \"day\": \"").append(snapshot.day).append("\",\n")
        sb.append(pad).append("  \"hour\": \"").append(snapshot.hour).append("\",\n")
        sb.append(pad).append("  \"sunPalace\": \"").append(snapshot.sunPalace.pinyin).append("\",\n")
        sb.append(pad).append("  \"sunElement\": \"").append(snapshot.sunPalace.element.en).append("\",\n")
        sb.append(pad).append("  \"trigram\": \"").append(snapshot.sunPalace.trigram).append("\",\n")
        sb.append(pad).append("  \"wealthElement\": \"").append(snapshot.wealthElement.en).append("\",\n")
        sb.append(pad).append("  \"bestDirection\": \"").append(FortuneEngine.wealthDirectionLabel(snapshot)).append("\",\n")
        sb.append(pad).append("  \"avoidDirection\": \"").append(FortuneEngine.avoidDirectionLabel(snapshot)).append("\",\n")
        sb.append(pad).append("  \"strength\": \"").append(snapshot.strength.en).append("\",\n")
        sb.append(pad).append("  \"withHour\": ").append(withHour).append(",\n")
        // The glance row the home screen shows: the same object the app draws,
        // so the screenshot cannot show a glance the app never produces.
        val glance = DayGlance.of(snapshot)
        sb.append(pad).append("  \"glance\": {\n")
        sb.append(pad).append("    \"colour\": \"").append(glance.colour).append("\",\n")
        sb.append(pad).append("    \"colourNote\": \"").append(esc(glance.colourNote)).append("\",\n")
        sb.append(pad).append("    \"direction\": \"").append(esc(glance.direction)).append("\",\n")
        sb.append(pad).append("    \"directionNote\": \"").append(esc(glance.directionNote)).append("\",\n")
        sb.append(pad).append("    \"bestHourRange\": \"").append(glance.bestHourRange).append("\",\n")
        sb.append(pad).append("    \"bestHourLabel\": \"").append(esc(glance.bestHourLabel)).append("\"\n")
        sb.append(pad).append("  },\n")
        sb.append(pad).append("  \"modules\": [\n")
        val readings = FortuneModule.entries.map { FortuneEngine.reading(snapshot, it) }
        readings.forEachIndexed { index, reading ->
            sb.append(pad).append("    {\n")
            sb.append(pad).append("      \"title\": \"").append(reading.module.title).append("\",\n")
            sb.append(pad).append("      \"blurb\": \"").append(reading.module.blurb).append("\",\n")
            sb.append(pad).append("      \"score\": ").append(reading.score).append(",\n")
            sb.append(pad).append("      \"verdict\": \"").append(reading.verdict.label).append("\",\n")
            sb.append(pad).append("      \"headline\": \"").append(esc(reading.headline)).append("\",\n")
            sb.append(pad).append("      \"summary\": \"").append(esc(reading.summary)).append("\",\n")
            sb.append(pad).append("      \"insights\": [")
            reading.insights.forEachIndexed { i, line ->
                if (i > 0) sb.append(", ")
                sb.append("\"").append(esc(line)).append("\"")
            }
            sb.append("],\n")
            sb.append(pad).append("      \"advice\": [")
            reading.advice.forEachIndexed { i, line ->
                if (i > 0) sb.append(", ")
                sb.append("\"").append(esc(line)).append("\"")
            }
            sb.append("],\n")
            sb.append(pad).append("      \"hours\": [")
            reading.bestHours.forEachIndexed { i, hour ->
                if (i > 0) sb.append(", ")
                sb.append("{\"range\": \"").append(hour.range).append("\", \"branch\": \"")
                    .append(hour.branch.pinyin).append("\", \"officer\": \"").append(hour.officer.en).append("\"}")
            }
            sb.append("]\n")
            sb.append(pad).append("    }").append(if (index == readings.lastIndex) "" else ",").append("\n")
        }
        sb.append(pad).append("  ]\n")
        sb.append(pad).append("}")
        return sb.toString()
    }

    private fun findProjectRoot(): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        while (dir.parentFile != null) {
            if (File(dir, "docs").isDirectory && File(dir, "app").isDirectory) return dir
            dir = dir.parentFile
        }
        return File(System.getProperty("user.dir")).absoluteFile
    }

    private fun esc(text: String): String = text
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", " ")

    private companion object {
        val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH)
        val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
    }
}
