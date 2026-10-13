package com.pinakes.app.data.calendar

/**
 * URL helpers for the personal loans calendar feed. Pure, unit-tested.
 *
 * Calendar apps subscribe to `webcal://` URLs (the same resource over HTTPS); Google Calendar
 * on Android cannot subscribe from the device at all, so the sheet leads with copying the
 * HTTPS link.
 */
object CalendarLinks {

    /** `https://host/x.ics` → `webcal://host/x.ics`; `http://` (loopback dev) likewise. Other input is returned unchanged. */
    fun toWebcal(url: String): String {
        val trimmed = url.trim()
        return when {
            trimmed.startsWith("https://", ignoreCase = true) -> "webcal://" + trimmed.substring("https://".length)
            trimmed.startsWith("http://", ignoreCase = true) -> "webcal://" + trimmed.substring("http://".length)
            else -> trimmed
        }
    }

    /** `webcal://` → `https://`, for the browser fallback. Other input is returned unchanged. */
    fun toHttps(url: String): String {
        val trimmed = url.trim()
        return if (trimmed.startsWith("webcal://", ignoreCase = true)) "https://" + trimmed.substring("webcal://".length) else trimmed
    }

    /**
     * The URL to open in a calendar app: the server's `webcal_url` when present, else the
     * feed URL with its scheme swapped. Null when the feed is disabled.
     */
    fun subscribeUrl(feedUrl: String?, webcalUrl: String?): String? = when {
        !webcalUrl.isNullOrBlank() -> webcalUrl.trim()
        !feedUrl.isNullOrBlank() -> toWebcal(feedUrl)
        else -> null
    }

    /**
     * The feed URL embeds a secret token (anyone holding it reads the user's loans), so the
     * sheet shows only `host/…` plus the last [visible] characters of the path.
     */
    fun mask(url: String, visible: Int = 6): String {
        val trimmed = url.trim()
        val afterScheme = trimmed.substringAfter("://", trimmed)
        val host = afterScheme.substringBefore('/')
        val path = afterScheme.substringAfter('/', "")
        if (path.isEmpty()) return host
        val tail = path.removeSuffix(".ics").takeLast(visible)
        val ext = if (path.endsWith(".ics")) ".ics" else ""
        return "$host/…$tail$ext"
    }
}
