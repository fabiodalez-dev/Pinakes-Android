package com.pinakes.app.staff

import com.pinakes.app.data.model.LoanCreateRequest
import com.pinakes.app.data.staff.StaffErrors
import com.pinakes.app.data.staff.SubmissionKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** One Idempotency-Key per submission: stable across retries, new for a new payload or after a reset. */
class SubmissionKeysTest {

    private var counter = 0
    private val keys = SubmissionKeys { "k${++counter}" }
    private val loan = LoanCreateRequest(userId = 3, bookId = 5, immediate = true)

    @Test fun retriesOfTheSameSubmissionReuseTheKey() {
        val first = keys.keyFor(loan)
        assertEquals(first, keys.keyFor(loan.copy()))
        assertEquals(first, keys.keyFor(LoanCreateRequest(userId = 3, bookId = 5, immediate = true)))
    }

    @Test fun aChangedPayloadIsANewSubmission() {
        val first = keys.keyFor(loan)
        val second = keys.keyFor(loan.copy(dueAt = "2026-11-30"))
        assertNotEquals(first, second)
        // Going back to the first payload is again a new submission (the server would see a new body).
        assertNotEquals(first, keys.keyFor(loan))
    }

    @Test fun resetAfterSuccessOrConflictGivesAFreshKey() {
        val first = keys.keyFor(loan)
        keys.reset()
        assertNotEquals(first, keys.keyFor(loan))
    }

    @Test fun defaultKeysAreUuids() {
        val key = SubmissionKeys().keyFor(loan)
        assertEquals(36, key.length)
        assertNotNull(java.util.UUID.fromString(key))
    }

    @Test fun conflictHasItsOwnMessage() {
        assertNotNull(StaffErrors.messageRes(SubmissionKeys.CONFLICT))
    }
}
