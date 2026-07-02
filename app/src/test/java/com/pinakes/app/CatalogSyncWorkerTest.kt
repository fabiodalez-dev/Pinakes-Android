package com.pinakes.app

import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.network.ErrorCodes
import com.pinakes.app.data.sync.CatalogSyncWorker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The periodic catalog-sync worker (WorkManager #11, Hilt-injected #14).
 *
 * The worker itself resolves its dependencies via Hilt EntryPointAccessors, so
 * it is not unit-testable in isolation without a Hilt harness. Its ONE piece of
 * real branching — retry only transient failures, give up on permanent ones so
 * the periodic chain isn't stuck retrying an unrecoverable condition — is a pure
 * function, and that is what we assert here.
 */
class CatalogSyncWorkerTest {

    private fun failure(code: String, status: Int = 0) =
        ApiResult.Failure(code = code, message = "x", httpStatus = status)

    @Test fun permanentFailures_giveUp() {
        // Auth/authorization: a background worker can't re-login.
        assertTrue(CatalogSyncWorker.isPermanentFailure(failure(ErrorCodes.UNAUTHORIZED, 401)))
        assertTrue(CatalogSyncWorker.isPermanentFailure(failure(ErrorCodes.FORBIDDEN, 403)))
        assertTrue(CatalogSyncWorker.isPermanentFailure(failure(ErrorCodes.APP_DISABLED, 403)))
        // A bare 401/403 status with no matching body code still counts.
        assertTrue(CatalogSyncWorker.isPermanentFailure(failure(ErrorCodes.UNKNOWN, 401)))
        assertTrue(CatalogSyncWorker.isPermanentFailure(failure(ErrorCodes.UNKNOWN, 403)))
        // Won't change on retry.
        assertTrue(CatalogSyncWorker.isPermanentFailure(failure(ErrorCodes.VALIDATION, 422)))
        assertTrue(CatalogSyncWorker.isPermanentFailure(failure(ErrorCodes.NOT_FOUND, 404)))
    }

    @Test fun transientFailures_retry() {
        assertFalse(CatalogSyncWorker.isPermanentFailure(failure(ErrorCodes.NETWORK)))
        assertFalse(CatalogSyncWorker.isPermanentFailure(failure(ErrorCodes.SERVER_ERROR, 500)))
        assertFalse(CatalogSyncWorker.isPermanentFailure(failure(ErrorCodes.RATE_LIMITED, 429)))
        assertFalse(CatalogSyncWorker.isPermanentFailure(failure(ErrorCodes.CONFLICT, 409)))
        assertFalse(CatalogSyncWorker.isPermanentFailure(failure(ErrorCodes.UNKNOWN, 0)))
    }
}
