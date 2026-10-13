package com.pinakes.app.staff

import com.pinakes.app.data.model.SyncBook
import com.pinakes.app.data.model.SyncItem
import com.pinakes.app.data.model.SyncPage
import com.pinakes.app.data.sync.SyncContinuation
import com.pinakes.app.data.sync.SyncMerge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMergeTest {

    private fun up(id: Int, title: String = "T$id") = SyncItem(id = id, change = "upsert", book = SyncBook(id = id, title = title))
    private fun del(id: Int) = SyncItem(id = id, change = "delete")
    private fun page(vararg items: SyncItem, time: String? = "2026-10-13 10:00:00", full: Boolean = false) =
        SyncPage(items = items.toList(), serverTime = time, fullResync = full)

    private val day = 24L * 60 * 60 * 1000

    @Test fun deltaCollectsUpsertsAndDeletes() {
        val r = SyncMerge.merge(listOf(page(up(1), del(2), up(3))), requestedFull = false)
        assertFalse(r.fullResync)
        assertEquals(listOf(1, 3), r.upserts.map { it.id })
        assertEquals(listOf(2), r.deleteIds)
        assertEquals("2026-10-13 10:00:00", r.serverTime)
    }

    @Test fun lastChangeForAnIdWinsAcrossPages() {
        val r = SyncMerge.merge(
            listOf(page(up(1, "old"), up(2)), page(del(1), up(2, "new"), up(4)), page(up(4, "back"))),
            requestedFull = false,
        )
        assertEquals(listOf(1), r.deleteIds)
        assertEquals(mapOf(2 to "new", 4 to "back"), r.upserts.associate { it.id to it.title })
    }

    @Test fun deleteThenUpsertIsAnUpsert() {
        val r = SyncMerge.merge(listOf(page(del(5)), page(up(5))), requestedFull = false)
        assertTrue(r.deleteIds.isEmpty())
        assertEquals(listOf(5), r.upserts.map { it.id })
    }

    @Test fun serverDemandedFullResyncDropsDeletes() {
        val r = SyncMerge.merge(listOf(page(up(1), del(2), full = true)), requestedFull = false)
        assertTrue(r.fullResync)
        assertTrue(r.deleteIds.isEmpty())
        assertEquals(listOf(1), r.upserts.map { it.id })
    }

    @Test fun requestedFullIsFullEvenIfPagesDoNotSaySo() {
        assertTrue(SyncMerge.merge(listOf(page(up(1))), requestedFull = true).fullResync)
    }

    @Test fun upsertWithoutBookIsIgnoredAndInvalidIdsSkipped() {
        val r = SyncMerge.merge(listOf(page(SyncItem(id = 3, change = "upsert", book = null), up(0), del(-1))), requestedFull = false)
        assertTrue(r.upserts.isEmpty())
        assertTrue(r.deleteIds.isEmpty())
    }

    @Test fun bookIdFollowsTheItemId() {
        val r = SyncMerge.merge(listOf(page(SyncItem(id = 8, book = SyncBook(id = 0, title = "x")))), requestedFull = false)
        assertEquals(8, r.upserts.single().id)
    }

    @Test fun watermarkComesFromThePagesAndIsNullWithoutOne() {
        assertEquals("2026-10-13 10:00:00", SyncMerge.merge(listOf(page(time = null), page(time = "2026-10-13 10:00:00")), false).serverTime)
        assertNull(SyncMerge.merge(listOf(page(time = null)), false).serverTime)
        assertNull(SyncMerge.merge(emptyList(), false).serverTime)
    }

    @Test fun forcedFullWithoutWatermarkOrHistory() {
        assertTrue(SyncMerge.shouldForceFull(since = null, lastFullAtMs = 0, nowMs = 1))
        assertTrue(SyncMerge.shouldForceFull(since = "2026-10-13 10:00:00", lastFullAtMs = null, nowMs = 1))
    }

    @Test fun weeklyFullResync() {
        val now = 100 * day
        assertFalse(SyncMerge.shouldForceFull("t", lastFullAtMs = now - 6 * day, nowMs = now))
        assertTrue(SyncMerge.shouldForceFull("t", lastFullAtMs = now - 7 * day, nowMs = now))
        assertTrue(SyncMerge.shouldForceFull("t", lastFullAtMs = now - 30 * day, nowMs = now))
    }

    @Test fun clockMovedBackwardsForcesFull() {
        assertTrue(SyncMerge.shouldForceFull("t", lastFullAtMs = 10 * day, nowMs = 9 * day))
    }

    @Test fun continuationFollowsTheCursor() {
        assertEquals(SyncContinuation.Next("abc"), SyncMerge.continuation(hasMore = true, nextCursor = "abc"))
        assertEquals(SyncContinuation.Done, SyncMerge.continuation(hasMore = false, nextCursor = "abc"))
        assertEquals(SyncContinuation.Done, SyncMerge.continuation(hasMore = false, nextCursor = null))
    }

    @Test fun hasMoreWithoutCursorIsBroken() {
        assertEquals(SyncContinuation.Broken, SyncMerge.continuation(hasMore = true, nextCursor = null))
        assertEquals(SyncContinuation.Broken, SyncMerge.continuation(hasMore = true, nextCursor = "  "))
    }

    @Test fun serversWithoutHasMorePageByCursor() {
        assertEquals(SyncContinuation.Next("c"), SyncMerge.continuation(hasMore = null, nextCursor = "c"))
        assertEquals(SyncContinuation.Done, SyncMerge.continuation(hasMore = null, nextCursor = ""))
    }
}
