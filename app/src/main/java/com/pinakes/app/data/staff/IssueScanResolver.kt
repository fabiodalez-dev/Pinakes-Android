package com.pinakes.app.data.staff

/**
 * Camera reads on the periodicals desk, where an issue is the masthead EAN-13 (977…) plus a
 * 2/5-digit add-on. ZXing treats the add-on as optional on every frame: a blurred frame still
 * decodes the bare 13 digits, so two bare reads could confirm the masthead before the read that
 * carries the issue wins, and the desk would show the masthead instead of the issue.
 *
 * - A bare masthead read that follows a read with its add-on counts as that longer code.
 * - A bare masthead the [debouncer] accepts is held for [holdMs]; a read with its add-on in
 *   the meantime is taken at once. A masthead with no add-on at all is still looked up, by
 *   [flush] once the hold has passed.
 * - Every other code (an ISSN-less EAN, a code with its add-on) behaves as the debouncer says.
 * - The issue the debouncer still suppresses (just received, claimed or dismissed, with the
 *   magazine still under the camera) is not looked up again by either path, nor its masthead.
 *
 * Pure and clock-injected, like [ScanDebouncer]. Not thread-safe: the caller is on one thread.
 */
class IssueScanResolver(
    val debouncer: ScanDebouncer = ScanDebouncer(),
    val holdMs: Long = 800,
) {
    private var lastWithAddOn: String? = null
    private var lastWithAddOnAt = Long.MIN_VALUE
    private var pending: String? = null
    private var pendingSince = 0L

    /** A bare masthead is waiting for its add-on; call [flush] after [holdMs]. */
    val holding: Boolean get() = pending != null

    /** Feed one camera read (digits only); returns the code to look up now, else null. */
    fun offer(code: String, nowMs: Long): String? {
        if (debouncer.paused) return null
        val suppressed = debouncer.suppressedAt(nowMs)
        if (suppressed != null && suppressed.startsWith(code)) {
            // The issue just handled (or its bare masthead) is still under the camera.
            pending = null
            return null
        }
        if (code.length > MASTHEAD_LENGTH) {
            lastWithAddOn = code
            lastWithAddOnAt = nowMs
            val held = pending
            if (held != null && code.startsWith(held)) {
                // The masthead was already confirmed: its add-on read settles which issue it is.
                pending = null
                return code
            }
        }
        val effective = if (isMasthead(code) && lastWithAddOn?.startsWith(code) == true && nowMs - lastWithAddOnAt <= holdMs) {
            lastWithAddOn!!
        } else {
            code
        }
        val accepted = debouncer.offer(effective, nowMs) ?: return null
        if (isMasthead(accepted)) {
            pending = accepted
            pendingSince = nowMs
            return null
        }
        pending = null
        return accepted
    }

    /** The held masthead once [holdMs] passed with no add-on read, else null. */
    fun flush(nowMs: Long): String? {
        val held = pending ?: return null
        if (nowMs - pendingSince < holdMs) return null
        pending = null
        return held
    }

    /** Drop a held masthead (a lookup started another way, or the scan was paused). */
    fun clear() {
        pending = null
    }

    private fun isMasthead(code: String) = code.length == MASTHEAD_LENGTH && code.startsWith("977")

    private companion object {
        const val MASTHEAD_LENGTH = 13
    }
}
