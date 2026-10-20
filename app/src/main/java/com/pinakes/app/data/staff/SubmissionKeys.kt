package com.pinakes.app.data.staff

import java.util.UUID

/**
 * Idempotency keys for staff creates (`POST /staff/loans`, `POST /staff/reservations`). One key
 * per user-initiated submission: retrying the same payload (automatically after a dropped
 * connection, or by tapping again) reuses it, so the server answers the original resource
 * (`meta.replayed`) instead of creating a second one; a changed payload is a new submission and
 * gets a new key. After a success, or a 422 `idempotency_conflict`, the key is discarded. Pure.
 */
class SubmissionKeys(private val newKey: () -> String = { UUID.randomUUID().toString() }) {
    private var key: String? = null
    private var payload: Any? = null

    /** The key for [submission] (compared by value: use a data class). */
    fun keyFor(submission: Any): String {
        val current = key
        if (current != null && payload == submission) return current
        return newKey().also {
            key = it
            payload = submission
        }
    }

    /** Forget the key: the next submission, even with the same payload, is a new one. */
    fun reset() {
        key = null
        payload = null
    }

    companion object {
        /** 422: the key was already used for a different payload. */
        const val CONFLICT = "idempotency_conflict"
    }
}
