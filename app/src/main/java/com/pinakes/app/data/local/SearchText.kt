package com.pinakes.app.data.local

import java.text.Normalizer
import java.util.Locale

/**
 * Text folding for the offline search (SQLite LIKE only folds ASCII case). Pure, unit-tested.
 *
 * Rows store a [key]: title, subtitle, author, publisher and identifiers, lower-cased with
 * accents removed (NFD + drop combining marks), identifiers without dashes or spaces. A query
 * goes through [queryPattern], folded the same way and escaped for `LIKE … ESCAPE '\'`.
 */
object SearchText {
    private val COMBINING = Regex("\\p{M}+")
    private val ID_SEPARATORS = Regex("[\\s-]")
    private val IDENTIFIER_LIKE = Regex("^[0-9Xx\\s-]+$")
    private const val QUOTES = "\"\u201C\u201D\u201E\u00AB\u00BB"
    private val SPACES = Regex("\\s+")

    /** Lower-case, accent-free form of [text] ("Città" → "citta", "Ærø" keeps æ/ø, ß stays). */
    fun fold(text: String?): String =
        Normalizer.normalize(text.orEmpty(), Normalizer.Form.NFD).replace(COMBINING, "").lowercase(Locale.ROOT)

    /** The stored search key of one catalogue row. */
    fun key(
        title: String?,
        subtitle: String?,
        author: String?,
        publisher: String?,
        isbn13: String?,
        isbn10: String?,
        ean: String?,
    ): String = listOf(
        fold(title), fold(subtitle), fold(author), fold(publisher),
        identifier(isbn13), identifier(isbn10), identifier(ean),
    ).filter { it.isNotEmpty() }.joinToString("\u001F")

    /**
     * The folded, LIKE-escaped query: `\`, `%` and `_` match themselves. A query that looks
     * like an identifier (digits, X, dashes, spaces, at least 4 digits) loses its separators,
     * so "978-88-04" finds 9788804….
     */
    fun queryPattern(query: String?): String {
        val trimmed = withoutSearchQuotes(query?.trim().orEmpty())
        val compact = if (IDENTIFIER_LIKE.matches(trimmed) && trimmed.count { it.isDigit() } >= 4) {
            trimmed.replace(ID_SEPARATORS, "")
        } else trimmed
        return escapeLike(fold(compact))
    }

    /**
     * [query] without the quotes that mark an exact spelling online (Pinakes #501): straight and
     * typographic quotes, as the server reads them. The offline key folds accents, so it cannot
     * tell "Häffner" from "Haffner"; without this the quotes themselves were searched for and a
     * quoted query found nothing offline. An inch mark (a straight or closing quote right after
     * a digit, outside a quoted part, as in `12"`) stays, as it does on the server.
     */
    fun withoutSearchQuotes(query: String): String {
        if (query.none { it in QUOTES }) return query
        val out = StringBuilder(query.length)
        var open = false
        var prev = ' '
        for (c in query) {
            if (c in QUOTES) {
                if (!open && (c == '"' || c == '\u201D') && prev.isDigit()) out.append(c)
                else { open = !open; out.append(' ') }
            } else out.append(c)
            prev = c
        }
        return out.toString().replace(SPACES, " ").trim()
    }

    /**
     * The LIKE pattern of the author or publisher filter. Those are quoted for an exact spelling
     * too (#501); offline the quotes go, as in [queryPattern]. Case and accents are left to the
     * DAO, as before.
     */
    fun filterPattern(value: String?): String = escapeLike(withoutSearchQuotes(value?.trim().orEmpty()))

    /** Escape the LIKE metacharacters for `ESCAPE '\'`. */
    fun escapeLike(value: String): String =
        value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    private fun identifier(value: String?): String = value.orEmpty().replace(ID_SEPARATORS, "").lowercase(Locale.ROOT)
}
