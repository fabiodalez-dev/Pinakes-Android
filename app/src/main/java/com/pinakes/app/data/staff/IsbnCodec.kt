package com.pinakes.app.data.staff

/**
 * A validated book or product identifier, ready for `GET /staff/isbn/{code}`.
 *
 * [lookupCode] is what the app sends: the ISBN-13 for books (the server derives every
 * variant), the EAN-13 for other media.
 */
sealed interface Identifier {
    val lookupCode: String

    /** A Bookland EAN (978/979) or a converted ISBN-10. [isbn10] exists only for 978 codes. */
    data class Isbn13(val isbn13: String, val isbn10: String?) : Identifier {
        override val lookupCode: String get() = isbn13
    }

    /** A non-book EAN-13 (CD, DVD, games). Still looked up: plugins such as Discogs accept them. */
    data class Ean(val ean: String) : Identifier {
        override val lookupCode: String get() = ean
    }
}

/**
 * Pure ISBN/EAN validation and conversion used by the scanner and the manual entry field.
 * No Android dependencies, fully unit-tested.
 */
object IsbnCodec {

    /**
     * A raw barcode decode. Only 13-digit codes with a valid EAN checksum pass, which filters
     * out the partial reads a camera produces while it focuses.
     */
    fun fromScan(raw: String?): Identifier? {
        val code = raw?.trim().orEmpty()
        if (code.length != 13 || !code.all { it.isDigit() }) return null
        if (!checkEan13(code)) return null
        return classify13(code)
    }

    /**
     * Manually typed input: dashes and spaces are ignored, ISBN-10 (with a trailing X) is
     * converted to ISBN-13 and both forms are kept, a 13-digit code must pass the EAN checksum.
     */
    fun fromManual(input: String?): Identifier? {
        val cleaned = input.orEmpty().replace(SEPARATORS, "").uppercase()
        return when {
            cleaned.length == 10 && checkIsbn10(cleaned) ->
                Identifier.Isbn13(isbn13 = isbn10To13(cleaned), isbn10 = cleaned)
            cleaned.length == 13 && cleaned.all { it.isDigit() } && checkEan13(cleaned) ->
                classify13(cleaned)
            else -> null
        }
    }

    /** True when [code] is a 13-digit string whose last digit is the EAN-13 check digit. */
    fun checkEan13(code: String): Boolean {
        if (code.length != 13 || !code.all { it.isDigit() }) return false
        return ean13CheckDigit(code.substring(0, 12)) == code[12].digitToInt()
    }

    /** True when [code] is a valid ISBN-10 (mod 11; `X` allowed only as the check digit). */
    fun checkIsbn10(code: String): Boolean {
        val c = code.uppercase()
        if (c.length != 10) return false
        if (!c.substring(0, 9).all { it.isDigit() }) return false
        val last = c[9]
        if (!last.isDigit() && last != 'X') return false
        var sum = 0
        for (i in 0 until 9) sum += (10 - i) * c[i].digitToInt()
        sum += if (last == 'X') 10 else last.digitToInt()
        return sum % 11 == 0
    }

    /** ISBN-10 → ISBN-13 (978 prefix, recomputed check digit). Expects a valid ISBN-10. */
    fun isbn10To13(isbn10: String): String {
        val core = "978" + isbn10.substring(0, 9)
        return core + ean13CheckDigit(core)
    }

    /** ISBN-13 (978 only) → ISBN-10, or null for 979 codes, which have no ISBN-10. */
    fun isbn13To10(isbn13: String): String? {
        if (isbn13.length != 13 || !isbn13.startsWith("978")) return null
        val core = isbn13.substring(3, 12)
        var sum = 0
        for (i in 0 until 9) sum += (10 - i) * core[i].digitToInt()
        val check = (11 - sum % 11) % 11
        return core + if (check == 10) "X" else check.toString()
    }

    /** Light validation for the edit form: blank is fine, anything typed must be a real code. */
    fun isValidIsbn13OrBlank(value: String): Boolean {
        val c = value.replace(SEPARATORS, "")
        return c.isEmpty() || (c.length == 13 && checkEan13(c) && (c.startsWith("978") || c.startsWith("979")))
    }

    fun isValidIsbn10OrBlank(value: String): Boolean {
        val c = value.replace(SEPARATORS, "")
        return c.isEmpty() || checkIsbn10(c)
    }

    fun isValidEanOrBlank(value: String): Boolean {
        val c = value.replace(SEPARATORS, "")
        return c.isEmpty() || checkEan13(c)
    }

    /** ISSN: 8 characters, mod-11 check digit, `X` allowed last; the hyphen is optional. */
    fun isValidIssnOrBlank(value: String): Boolean {
        val c = value.replace(SEPARATORS, "").uppercase()
        if (c.isEmpty()) return true
        if (c.length != 8 || !c.substring(0, 7).all { it.isDigit() }) return false
        var sum = 0
        for (i in 0 until 7) sum += (8 - i) * c[i].digitToInt()
        val check = (11 - sum % 11) % 11
        val expected = if (check == 10) 'X' else ('0' + check)
        return c[7] == expected
    }

    /** Strip the separators people type or paste into identifier fields. */
    fun normalize(value: String): String = value.replace(SEPARATORS, "").uppercase()

    private fun classify13(code: String): Identifier =
        if (code.startsWith("978") || code.startsWith("979")) {
            Identifier.Isbn13(isbn13 = code, isbn10 = isbn13To10(code))
        } else {
            Identifier.Ean(code)
        }

    private fun ean13CheckDigit(first12: String): Int {
        var sum = 0
        for (i in first12.indices) {
            val d = first12[i].digitToInt()
            sum += if (i % 2 == 0) d else d * 3
        }
        return (10 - sum % 10) % 10
    }

    private val SEPARATORS = Regex("[\\s-]")
}
