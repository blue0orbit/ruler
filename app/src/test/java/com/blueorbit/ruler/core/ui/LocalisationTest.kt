package com.blueorbit.ruler.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The German translation is a real translation, not a copy, and it has to stay complete. A partial
 * translation shows half the UI in English on a German device, and a placeholder that changes shape
 * crashes the format call at runtime.
 */
class LocalisationTest {

    private fun names(path: String): Set<String> {
        val file = File(path)
        assertTrue("missing resource file: ${file.path}", file.exists())
        return Regex("name=\"([^\"]+)\"").findAll(file.readText()).map { it.groupValues[1] }.toSet()
    }

    /**
     * Every `strings*.xml` in a values folder, read as one text. Strings are split across files per
     * feature (`strings.xml`, `strings_tools.xml`, …), and a translation check that read only
     * `strings.xml` would pass while a whole feature shipped in English.
     */
    private fun stringsText(folder: String): String {
        val files = File(folder).listFiles { f -> f.name.startsWith("strings") && f.name.endsWith(".xml") }
            .orEmpty().sortedBy { it.name }
        assertTrue("no strings files in $folder", files.isNotEmpty())
        return files.joinToString("\n") { it.readText() }
    }

    private fun stringNames(folder: String): Set<String> =
        Regex("name=\"([^\"]+)\"").findAll(stringsText(folder)).map { it.groupValues[1] }.toSet()

    @Test
    fun everyGermanStringsFileHasAnEnglishTwin(): Unit {
        val english = File("src/main/res/values").list().orEmpty().filter { it.startsWith("strings") }.toSet()
        val german = File("src/main/res/values-de").list().orEmpty().filter { it.startsWith("strings") }.toSet()
        assertEquals("strings files present in only one language", english, german)
    }

    @Test
    fun everyStringHasAGermanTranslation(): Unit {
        val english = stringNames("src/main/res/values")
        val german = stringNames("src/main/res/values-de")
        assertEquals(
            "strings without a German translation",
            emptySet<String>(),
            english.filter { !german.contains(it) }.toSet()
        )
    }

    @Test
    fun everyPluralHasAGermanTranslation(): Unit {
        val english = names("src/main/res/values/plurals.xml")
        val german = names("src/main/res/values-de/plurals.xml")
        assertEquals(
            "plurals without a German translation",
            emptySet<String>(),
            english.filter { !german.contains(it) }.toSet()
        )
    }

    @Test
    fun formatPlaceholdersMatchAcrossTheTranslation(): Unit {
        val pattern = Regex("name=\"([^\"]+)\"[^>]*>([^<]*)<")

        val english = pattern.findAll(stringsText("src/main/res/values"))
            .associate { it.groupValues[1] to it.groupValues[2] }
        val german = pattern.findAll(stringsText("src/main/res/values-de"))
            .associate { it.groupValues[1] to it.groupValues[2] }

        val mismatched = english.keys.filter { name ->
            Regex("%(\\d+)\\$[sd]").findAll(english.getValue(name)).map { it.groupValues[1] }.toList() !=
                Regex("%(\\d+)\\$[sd]").findAll(german.getValue(name)).map { it.groupValues[1] }.toList()
        }

        assertEquals("placeholder positions differ from the translation", emptySet<String>(), mismatched.toSet())
    }

    @Test
    fun theTranslationIsNotJustACopyOfTheEnglish(): Unit {
        val pattern = Regex("name=\"([^\"]+)\"[^>]*>([^<]*)<")
        val english = pattern.findAll(stringsText("src/main/res/values"))
            .associate { it.groupValues[1] to it.groupValues[2] }
        val german = pattern.findAll(stringsText("src/main/res/values-de"))
            .associate { it.groupValues[1] to it.groupValues[2] }

        val identical = english.keys.filter { english.getValue(it) == german.getValue(it) }
        // Unit symbols, format-only strings, and words that are the same in both languages.
        val allowed = setOf(
            "unit_mm",
            "unit_cm",
            "log_row_format",
            "coin_label_format",
            // M11V (DESIGN.md §5.4): the readout is the caption plus the value, so the readout string
            // itself is only value + unit, which is the same in both languages.
            "ruler_readout_format",
            "theme_system",
            "theme_sepia",
            // "Pro" is the product name and is the same word in both languages.
            "settings_pro_title",
            // M15 (rule 13): the field used to be "Label" / "Bezeichnung". The plain word for it is
            // "Name" in English and in German, so the copy is identical on purpose.
            "log_label",
            // M21: the two things a ring is measured against are the same plain words in both
            // languages, and two of the four size systems are named by abbreviations that are the
            // same in both. Translating them would name something else.
            "ring_method_ring",
            "ring_method_finger",
            "ring_system_eu",
            "ring_system_jp"
        )
        assertEquals("copied strings", emptySet<String>(), identical.filter { !allowed.contains(it) }.toSet())
    }
}
