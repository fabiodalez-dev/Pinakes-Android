package com.pinakes.app.data.staff

/**
 * Turns a noisy stream of barcode decodes into deliberate scans.
 *
 * - A code is accepted only after [requiredReads] consecutive identical decodes, so a single
 *   misread while the camera focuses never triggers a lookup.
 * - Once accepted, the same code is suppressed for [suppressMs] milliseconds, so holding the
 *   book under the camera does not add it twice.
 * - [pause] ignores every decode (a sheet is open); [resume] starts counting afresh and
 *   restarts the suppression window, so the book still under the camera after its sheet
 *   closes is not accepted again straight away.
 *
 * Pure and clock-injected ([offer] takes `nowMs`) so it is unit-testable. Not thread-safe:
 * the analyzer delivers decodes on one executor and the caller hops to the main thread.
 */
class ScanDebouncer(
    private val requiredReads: Int = 2,
    private val suppressMs: Long = 3_000,
) {
    private var candidate: String? = null
    private var candidateCount = 0
    private var lastAccepted: String? = null
    private var lastAcceptedAt = Long.MIN_VALUE
    var paused: Boolean = false
        private set

    /** Feed one decode; returns the code when it should be acted on, else null. */
    fun offer(code: String, nowMs: Long): String? {
        if (paused) return null
        if (code == lastAccepted && nowMs - lastAcceptedAt < suppressMs) {
            // Keep the streak empty so the code needs fresh confirmation after the window.
            candidate = null
            candidateCount = 0
            return null
        }
        if (code == candidate) {
            candidateCount++
        } else {
            candidate = code
            candidateCount = 1
        }
        if (candidateCount < requiredReads) return null
        candidate = null
        candidateCount = 0
        lastAccepted = code
        lastAcceptedAt = nowMs
        return code
    }

    fun pause() {
        paused = true
        candidate = null
        candidateCount = 0
    }

    /** Accept decodes again. [nowMs] restarts the suppression window of the last accepted code. */
    fun resume(nowMs: Long? = null) {
        paused = false
        candidate = null
        candidateCount = 0
        if (nowMs != null && lastAccepted != null) lastAcceptedAt = nowMs
    }

    /** Forget the last accepted code, so it can be scanned again immediately (e.g. "Rescan"). */
    fun reset() {
        resume()
        lastAccepted = null
        lastAcceptedAt = Long.MIN_VALUE
    }
}
