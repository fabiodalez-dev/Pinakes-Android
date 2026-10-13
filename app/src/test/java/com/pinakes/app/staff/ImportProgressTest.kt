package com.pinakes.app.staff

import com.pinakes.app.data.model.ImportJob
import com.pinakes.app.data.model.Meta
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.staff.ChunkStep
import com.pinakes.app.data.staff.ImportProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The resumable import: chunks start where the server is, out-of-order and busy answers recover. */
class ImportProgressTest {

    private fun failure(code: String, processed: Int? = null) =
        ApiResult.Failure(code, "x", 409, meta = Meta(processed = processed), fromEnvelope = true)

    @Test fun chunksStartAtTheServersProcessedCount() {
        assertEquals(ChunkStep.Next(0), ImportProgress.next(ImportJob(totalRows = 30)))
        assertEquals(ChunkStep.Next(20), ImportProgress.next(ImportJob(totalRows = 30, processed = 20, status = "running")))
        assertEquals(ChunkStep.Done, ImportProgress.next(ImportJob(totalRows = 30, processed = 30, complete = true)))
        listOf("completed", "failed", "cancelled").forEach { assertEquals(ChunkStep.Done, ImportProgress.next(ImportJob(status = it))) }
    }

    @Test fun outOfOrderContinuesFromTheServer() {
        assertEquals(ChunkStep.Next(40), ImportProgress.afterFailure(failure("out_of_order", 40), start = 30, busyAttempts = 0))
        assertTrue(ImportProgress.afterFailure(failure("out_of_order"), 30, 0) is ChunkStep.Stop)
    }

    @Test fun busyWaitsAFewTimesThenStops() {
        assertEquals(ChunkStep.Wait(10), ImportProgress.afterFailure(failure("job_busy"), 10, busyAttempts = 1))
        assertTrue(ImportProgress.afterFailure(failure("job_busy"), 10, busyAttempts = ImportProgress.BUSY_RETRIES) is ChunkStep.Stop)
    }

    @Test fun goneOfflineOrRefusedStops() {
        listOf("gone", "network", "forbidden", "not_found").forEach {
            assertTrue(it, ImportProgress.afterFailure(failure(it), 0, 0) is ChunkStep.Stop)
        }
    }

    @Test fun percentIsClamped() {
        assertEquals(0, ImportProgress.percent(ImportJob(totalRows = 0)))
        assertEquals(100, ImportProgress.percent(ImportJob(totalRows = 0, complete = true)))
        assertEquals(33, ImportProgress.percent(ImportJob(totalRows = 30, processed = 10)))
        assertEquals(100, ImportProgress.percent(ImportJob(totalRows = 30, processed = 45)))
    }

    @Test fun fileExtensions() {
        assertTrue(ImportProgress.acceptsFileName("libri.CSV", librarything = false))
        assertFalse(ImportProgress.acceptsFileName("libri.tsv", librarything = false))
        assertTrue(ImportProgress.acceptsFileName("export.tsv", librarything = true))
        assertTrue(ImportProgress.acceptsFileName("export.txt", librarything = true))
        assertFalse(ImportProgress.acceptsFileName("export.xlsx", librarything = true))
    }
}
