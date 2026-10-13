package com.pinakes.app.staff

import com.pinakes.app.data.staff.ScanDebouncer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanDebouncerTest {

    private val a = "9788804668237"
    private val b = "9780140186475"

    @Test fun needsTwoConsecutiveIdenticalReads() {
        val d = ScanDebouncer()
        assertNull(d.offer(a, 0))
        assertEquals(a, d.offer(a, 50))
    }

    @Test fun aDifferentCodeInBetweenRestartsTheStreak() {
        val d = ScanDebouncer()
        assertNull(d.offer(a, 0))
        assertNull(d.offer(b, 10))
        assertNull(d.offer(a, 20))
        assertEquals(a, d.offer(a, 30))
    }

    @Test fun sameCodeIsSuppressedForThreeSeconds() {
        val d = ScanDebouncer()
        d.offer(a, 0); assertEquals(a, d.offer(a, 10))
        assertNull(d.offer(a, 500))
        assertNull(d.offer(a, 2_999))
        // After the window it needs two fresh reads again.
        assertNull(d.offer(a, 3_100))
        assertEquals(a, d.offer(a, 3_200))
    }

    @Test fun anotherCodeIsNotSuppressed() {
        val d = ScanDebouncer()
        d.offer(a, 0); d.offer(a, 10)
        assertNull(d.offer(b, 100))
        assertEquals(b, d.offer(b, 150))
    }

    @Test fun pauseIgnoresEverythingAndResumeStartsFresh() {
        val d = ScanDebouncer()
        d.pause()
        assertNull(d.offer(a, 0))
        assertNull(d.offer(a, 10))
        d.resume()
        assertNull(d.offer(a, 20))   // the paused reads did not count
        assertEquals(a, d.offer(a, 30))
    }

    @Test fun resetAllowsTheSameCodeImmediately() {
        val d = ScanDebouncer()
        d.offer(a, 0); d.offer(a, 10)
        d.reset()
        assertNull(d.offer(a, 20))
        assertEquals(a, d.offer(a, 30))
    }

    @Test fun resumeWithTimeRestartsTheSuppressionWindow() {
        val d = ScanDebouncer()
        d.offer(a, 0); assertEquals(a, d.offer(a, 10))
        d.pause()
        // The sheet stayed open 10 s; the book is still under the camera when it closes.
        d.resume(nowMs = 10_000)
        assertNull(d.offer(a, 10_100))
        assertNull(d.offer(a, 10_200))
        assertNull(d.offer(a, 12_900))
        // After the restarted window it can be scanned again (two fresh reads).
        assertNull(d.offer(a, 13_100))
        assertEquals(a, d.offer(a, 13_200))
        // Other codes are never held back.
        d.pause(); d.resume(nowMs = 20_000)
        assertNull(d.offer(b, 20_050))
        assertEquals(b, d.offer(b, 20_100))
    }
}
