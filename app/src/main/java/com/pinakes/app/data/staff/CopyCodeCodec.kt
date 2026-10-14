package com.pinakes.app.data.staff

/**
 * Copy inventory codes (the CODE 128 / CODE 39 labels the web prints, or a typed code).
 * The server accepts at most 100 characters and strips control characters; the app does the
 * same before sending, so what the operator sees is what the server stores. Pure.
 */
object CopyCodeCodec {
    const val MAX_LENGTH = 100

    /** Trimmed, control characters removed, at most [MAX_LENGTH] characters. */
    fun normalize(raw: String): String =
        raw.filterNot { it.isISOControl() || it == ' ' || it == ' ' }.trim().take(MAX_LENGTH)

    /** A usable code: something left after [normalize]. */
    fun isValid(raw: String): Boolean = normalize(raw).isNotEmpty()

    /**
     * Whether a scanned value looks like a book's own ISBN/EAN rather than a copy label: the
     * copy scanner still accepts it (some libraries use the EAN as inventory code), but the
     * sheet says so when the server finds no copy.
     */
    fun looksLikeBookIdentifier(raw: String): Boolean {
        val digits = normalize(raw)
        return digits.length == 13 && digits.all { it.isDigit() } && (digits.startsWith("978") || digits.startsWith("979"))
    }
}
