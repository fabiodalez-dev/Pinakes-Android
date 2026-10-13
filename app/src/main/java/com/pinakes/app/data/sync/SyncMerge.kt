package com.pinakes.app.data.sync

import com.pinakes.app.data.model.SyncBook
import com.pinakes.app.data.model.SyncPage

/** What one completed sync run does to the local catalogue. */
data class SyncResult(
    /** True: replace the whole cache with [upserts]; false: apply [upserts] and [deleteIds]. */
    val fullResync: Boolean,
    val upserts: List<SyncBook>,
    val deleteIds: List<Int>,
    /** The watermark to store as the next `since`, or null when no page carried one. */
    val serverTime: String?,
)

/**
 * Pure delta-sync bookkeeping (no Room, no network), unit-tested in isolation.
 *
 * The repository fetches every page of `GET /catalog/sync`, then calls [merge] once, applies
 * the result in one transaction and only then stores [SyncResult.serverTime]. A run that
 * fails half-way therefore leaves the old watermark in place and the next run repeats it;
 * repeated upserts are harmless.
 */
object SyncMerge {

    /** The client forces an unfiltered pass at least this often (link-only edits on the server). */
    const val FULL_RESYNC_INTERVAL_MS: Long = 7L * 24 * 60 * 60 * 1000

    /**
     * Whether the next run must ask for everything (`since` omitted): no watermark yet, no
     * recorded full pass, or the last full pass is older than [FULL_RESYNC_INTERVAL_MS].
     * A clock that moved backwards also forces a full pass rather than trusting the gap.
     */
    fun shouldForceFull(since: String?, lastFullAtMs: Long?, nowMs: Long): Boolean {
        if (since.isNullOrBlank() || lastFullAtMs == null) return true
        val age = nowMs - lastFullAtMs
        return age < 0 || age >= FULL_RESYNC_INTERVAL_MS
    }

    /**
     * Fold the pages of one run, in order, into a single result. The last change for an id
     * wins (an upsert followed by a delete on a later page is a delete, and the reverse).
     * [requestedFull] is true when the run omitted `since`; the server can also demand a
     * full resync (`full_resync: true`, e.g. a watermark older than 30 days), in which case
     * deletes are irrelevant: the cache becomes exactly the upserted set.
     */
    fun merge(pages: List<SyncPage>, requestedFull: Boolean): SyncResult {
        val full = requestedFull || pages.any { it.fullResync }
        val latest = LinkedHashMap<Int, SyncBook?>() // null = delete
        for (page in pages) {
            for (item in page.items) {
                if (item.id <= 0) continue
                when (item.change.lowercase()) {
                    "delete" -> {
                        latest.remove(item.id)
                        latest[item.id] = null
                    }
                    else -> {
                        val book = item.book ?: continue
                        latest.remove(item.id)
                        latest[item.id] = if (book.id == item.id) book else book.copy(id = item.id)
                    }
                }
            }
        }
        val upserts = latest.values.filterNotNull()
        val deletes = if (full) emptyList() else latest.filterValues { it == null }.keys.toList()
        val serverTime = pages.firstNotNullOfOrNull { it.serverTime?.takeIf { t -> t.isNotBlank() } }
        return SyncResult(fullResync = full, upserts = upserts, deleteIds = deletes, serverTime = serverTime)
    }
}
