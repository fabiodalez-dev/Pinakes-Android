package com.pinakes.app.data.repository

import com.pinakes.app.data.local.CatalogDao
import com.pinakes.app.data.local.SearchText
import com.pinakes.app.data.local.SyncMeta
import com.pinakes.app.data.local.SyncMetaDao
import com.pinakes.app.data.local.toCached
import com.pinakes.app.data.local.toOfflineDetail
import com.pinakes.app.data.local.toSummary
import com.pinakes.app.data.model.AvailabilityCalendar
import com.pinakes.app.data.model.BookDetail
import com.pinakes.app.data.model.BookSummary
import com.pinakes.app.data.model.CatalogLanguage
import com.pinakes.app.data.model.GenreNode
import com.pinakes.app.data.model.SyncPage
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.network.ErrorCodes
import com.pinakes.app.data.network.NetworkModule
import com.pinakes.app.data.network.apiCall
import com.pinakes.app.data.network.apiResponse
import com.pinakes.app.data.sync.SyncMerge
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.pinakes.app.data.sync.SyncContinuation
import java.util.concurrent.ConcurrentHashMap

/** Filters for a catalog search; nulls are omitted from the query. */
data class SearchFilters(
    val query: String? = null,
    val author: String? = null,
    val publisher: String? = null,
    val genreId: Int? = null,
    val language: String? = null,
    val availableOnly: Boolean? = null,
    val authorId: Int? = null,
)

/** One page of search results plus the cursor needed to fetch the next page. */
data class SearchPage(
    val items: List<BookSummary>,
    val nextCursor: String?,
    val totalCount: Int?,
) {
    val hasMore: Boolean get() = nextCursor != null
}

/**
 * Catalog browsing: cursor-paginated search, book detail (with ETag/304 caching) and the
 * genre tree. The ETag cache lets a re-fetch of the same book reuse the last payload on 304.
 */
class CatalogRepository(
    private val network: NetworkModule,
    private val catalogDao: CatalogDao,
    private val syncMeta: SyncMetaDao,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {

    /** Serialises sync runs: the foreground observer, Home and the worker may all ask at once. */
    private val syncMutex = Mutex()

    /**
     * Guards every cache write and [clearCache]. A sync fetches without it, then applies its
     * result under it only if [cacheGeneration] is unchanged, so a run that was in flight when
     * the instance was forgotten can never write the old library's rows or watermark back.
     */
    private val writeMutex = Mutex()

    @Volatile
    private var cacheGeneration = 0

    // Small in-memory ETag cache for book detail: id -> (etag, payload). Concurrent: detail
    // screens, the sync and clearCache() touch it from different coroutines.
    private val detailCache = ConcurrentHashMap<Int, Pair<String?, BookDetail>>()

    /**
     * Reactive cached catalog snapshot (offline-first). Emits whatever Room holds —
     * including offline — so the UI can render instantly without a network round-trip
     * for the list or, via Coil's disk cache, its covers. Empty until the first refresh.
     */
    fun observeCachedCatalog(limit: Int = HOME_SHELF_SIZE): Flow<List<BookSummary>> =
        catalogDao.observeNewest(limit).map { rows -> rows.map { it.toSummary() } }

    /** True once a catalog snapshot has been cached at least once. */
    suspend fun hasCachedCatalog(): Boolean = catalogDao.count() > 0

    /**
     * Drop every per-instance catalog artifact: the Room snapshot and the in-memory ETag
     * cache. Called when the instance is forgotten so library A's titles, covers and 304
     * payloads can never surface under library B.
     */
    suspend fun clearCache() {
        writeMutex.withLock {
            cacheGeneration++
            detailCache.clear()
            catalogDao.clear()
            syncMeta.clear()
        }
    }

    /** Transitional alias: every caller now gets the delta sync. */
    suspend fun refreshCatalog(): ApiResult<Unit> = syncCatalog()

    /**
     * Bring the offline catalogue up to date with `GET /catalog/sync`.
     *
     * Uses the stored watermark as `since`; omits it (full pass) when there is none or the
     * last full pass is older than seven days ([SyncMerge.shouldForceFull]). Every page is
     * fetched first, then merged and applied in one transaction, and only then is the new
     * watermark stored. Any failure keeps the cache and the old watermark untouched.
     * A server without the endpoint (404) falls back to the legacy first-page snapshot.
     * Concurrent calls collapse: a run already in progress makes the second call a no-op.
     */
    suspend fun syncCatalog(): ApiResult<Unit> {
        if (!syncMutex.tryLock()) return ApiResult.Success(Unit)
        try {
            val generation = cacheGeneration
            val since = syncMeta.get(SyncMeta.CATALOG_SERVER_TIME)
            val lastFullAt = syncMeta.get(SyncMeta.CATALOG_LAST_FULL_AT)?.toLongOrNull()
            val requestFull = SyncMerge.shouldForceFull(since, lastFullAt, nowMs())
            val api = network.api()
            val pages = ArrayList<SyncPage>()
            var cursor: String? = null
            var guard = 0
            do {
                val res = apiCall {
                    api.catalogSync(
                        since = if (requestFull || cursor != null) null else since,
                        cursor = cursor,
                        limit = SYNC_PAGE_SIZE,
                    )
                }
                when (res) {
                    is ApiResult.Success -> {
                        pages += res.data
                        cursor = when (val c = SyncMerge.continuation(res.meta?.hasMore, res.meta?.nextCursor)) {
                            is SyncContinuation.Next -> c.cursor
                            SyncContinuation.Done -> null
                            // Never apply a partial run nor advance the watermark past it.
                            SyncContinuation.Broken ->
                                return ApiResult.Failure(ErrorCodes.SERVER_ERROR, "Catalogue sync page without a cursor", 0)
                        }
                    }
                    is ApiResult.Failure -> {
                        return if (res.httpStatus == 404 && pages.isEmpty()) legacySnapshot(generation) else res
                    }
                }
            } while (cursor != null && ++guard < MAX_SYNC_PAGES)
            if (cursor != null) {
                // The server kept paging past the safety cap: do not trust a partial run.
                return ApiResult.Failure(ErrorCodes.SERVER_ERROR, "Catalogue sync did not finish", 0)
            }

            val result = SyncMerge.merge(pages, requestedFull = requestFull)
            val rows = result.upserts.map { it.toCached() }
            writeMutex.withLock {
                // The instance was forgotten (cache cleared) while this run was fetching.
                if (generation != cacheGeneration) return ApiResult.Success(Unit)
                if (result.fullResync) {
                    catalogDao.replaceAll(rows)
                    syncMeta.put(SyncMeta(SyncMeta.CATALOG_LAST_FULL_AT, nowMs().toString()))
                } else {
                    catalogDao.applyDelta(rows, result.deleteIds)
                }
                result.serverTime?.let { syncMeta.put(SyncMeta(SyncMeta.CATALOG_SERVER_TIME, it)) }
            }
            // Book pages served from memory may now be stale.
            result.upserts.forEach { detailCache.remove(it.id) }
            result.deleteIds.forEach { detailCache.remove(it) }
            return ApiResult.Success(Unit)
        } finally {
            syncMutex.unlock()
        }
    }

    /** Servers before Mobile API 1.6.0: cache the first unfiltered page, as 1.6.x did. */
    private suspend fun legacySnapshot(generation: Int): ApiResult<Unit> =
        when (val res = search(SearchFilters(), limit = HOME_SHELF_SIZE)) {
            is ApiResult.Success -> {
                writeMutex.withLock {
                    if (generation == cacheGeneration) catalogDao.replaceAll(res.data.items.map { it.toCached() })
                }
                ApiResult.Success(Unit, res.meta)
            }
            is ApiResult.Failure -> res
        }

    /**
     * Search the offline catalogue (used when the server is unreachable). Filters mirror the
     * online ones by value: genre and language match the cached labels. [sort] accepts
     * `title_asc`, `title_desc` or `newest` (anything else falls back to title).
     */
    suspend fun searchOffline(
        filters: SearchFilters,
        genreName: String?,
        sort: String?,
        offset: Int,
        limit: Int = 20,
    ): List<BookSummary> = catalogDao.search(
        q = SearchText.queryPattern(filters.query),
        author = SearchText.escapeLike(filters.author?.trim().orEmpty()),
        publisher = SearchText.escapeLike(filters.publisher?.trim().orEmpty()),
        language = filters.language?.trim().orEmpty(),
        genre = genreName?.trim().orEmpty(),
        availableOnly = filters.availableOnly == true,
        sort = when (sort) {
            "title_desc" -> "title_desc"
            "newest" -> "newest"
            else -> "title_asc"
        },
        limit = limit,
        offset = offset,
    ).map { it.toSummary() }

    /** Server time of the last completed sync (`Y-m-d H:i:s`), or null when never synced. */
    suspend fun lastSyncedAt(): String? = syncMeta.get(SyncMeta.CATALOG_SERVER_TIME)

    /** A summary-level detail page from the cache, or null when the title is not cached. */
    suspend fun bookOffline(id: Int): BookDetail? = catalogDao.byId(id)?.toOfflineDetail()

    suspend fun search(
        filters: SearchFilters,
        cursor: String? = null,
        limit: Int = 20,
        // relevance | newest | oldest | title_asc | title_desc; null → server default
        // (relevance for text queries, newest for facet-only browsing).
        sort: String? = null,
    ): ApiResult<SearchPage> {
        val api = network.api()
        return when (
            val res = apiCall {
                api.search(
                    q = filters.query?.takeIf { it.isNotBlank() },
                    authorId = filters.authorId,
                    author = filters.author?.takeIf { it.isNotBlank() && filters.authorId == null },
                    publisher = filters.publisher?.takeIf { it.isNotBlank() },
                    genre = filters.genreId,
                    language = filters.language?.takeIf { it.isNotBlank() },
                    available = filters.availableOnly,
                    cursor = cursor,
                    limit = limit.coerceIn(1, 50),
                    sort = sort?.takeIf { it.isNotBlank() },
                )
            }
        ) {
            is ApiResult.Success -> ApiResult.Success(
                SearchPage(
                    items = res.data,
                    nextCursor = res.meta?.nextCursor,
                    totalCount = res.meta?.totalCount,
                ),
                res.meta,
            )
            is ApiResult.Failure -> res
        }
    }

    /** A signed-file renewal must reach the server and must never fall back to a stale payload. */
    suspend fun book(id: Int, forceRefresh: Boolean = false): ApiResult<BookDetail> {
        val api = network.api()
        val cached = detailCache[id]
        val (result, response) = apiResponse { api.book(id, etag = if (forceRefresh) null else cached?.first,
            cacheControl = if (forceRefresh) "no-cache, no-store" else null) }

        // 304 Not Modified → reuse the cached payload.
        if (!forceRefresh && response?.code() == 304 && cached != null) {
            return ApiResult.Success(cached.second)
        }
        return when (result) {
            is ApiResult.Success -> {
                val etag = response?.headers()?.get("ETag")
                detailCache[id] = etag to result.data
                result
            }
            is ApiResult.Failure -> {
                // If the conditional request errored but we have a cached copy, serve it.
                if (!forceRefresh && cached != null && result.code != ErrorCodes.NETWORK) {
                    ApiResult.Success(cached.second)
                } else {
                    result
                }
            }
        }
    }

    suspend fun genres(): ApiResult<List<GenreNode>> {
        val api = network.api()
        return apiCall { api.genres() }
    }

    /**
     * Distinct language values present in the catalogue (with counts) from
     * GET /catalog/languages, used to populate the search language filter with
     * the collection's REAL values instead of a hardcoded ISO list (issue #282).
     */
    suspend fun languages(): ApiResult<List<CatalogLanguage>> {
        val api = network.api()
        return apiCall { api.languages() }
    }

    /**
     * Per-day availability for [id] (~180 days from today) used to drive the colored
     * loan-request calendar. Mirrors the website's availability view.
     */
    suspend fun bookAvailability(id: Int): ApiResult<AvailabilityCalendar> {
        val api = network.api()
        return apiCall { api.bookAvailability(id) }
    }

    private companion object {
        /** Home shelf and legacy snapshot size (the server page cap is 50). */
        const val HOME_SHELF_SIZE = 40
        const val SYNC_PAGE_SIZE = 200

        /** 200 × 2 000 = 400 000 titles: far beyond any target catalogue, still finite. */
        const val MAX_SYNC_PAGES = 2_000
    }
}
