package com.pinakes.app.staff

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Staff texts never put a counted noun right after a number ("1 books", "1 Bücher"): the i18n
 * pipeline generates plain strings, so counts are written as "Books: 1" in every language.
 */
class StaffStringsPluralTest {

    private val nouns = listOf(
        "books", "loans", "days", "rows", "units", "records", "copies", "reservations",
        "libri", "prestiti", "giorni", "righe", "unità", "schede", "copie",
        "livres", "prêts", "jours", "lignes", "unités", "notices", "exemplaires",
        "Bücher", "Ausleihen", "Tage", "Zeilen", "Einheiten", "Einträge", "Exemplare",
    )
    private val pattern = Regex("%\\d\\${'$'}d\\s+(" + nouns.joinToString("|") + ")\\b")

    private fun i18nDir(): File = listOf(File("../i18n"), File("i18n")).first { it.isDirectory }

    @Test fun noCountedNounAfterANumberInStaffStrings() {
        val offenders = mutableListOf<String>()
        for (lang in listOf("en", "it", "fr", "de")) {
            val text = File(i18nDir(), "$lang.json").readText()
            Regex(""""(staff_[a-z0-9_]+)":\s*"((?:[^"\\]|\\.)*)"""").findAll(text).forEach { m ->
                if (pattern.containsMatchIn(m.groupValues[2])) offenders += "$lang ${m.groupValues[1]}: ${m.groupValues[2]}"
            }
        }
        assertTrue(offenders.joinToString("\n"), offenders.isEmpty())
    }
}
