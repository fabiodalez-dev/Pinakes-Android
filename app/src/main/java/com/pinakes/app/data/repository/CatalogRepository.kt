package com.pinakes.app.data.repository

import com.pinakes.app.data.local.CatalogDao
import com.pinakes.app.data.local.toCached
import com.pinakes.app.data.local.toSummary
import com.pinakes.app.data.model.AvailabilityCalendar
import com.pinakes.app.data.model.BookDetail
import com.pinakes.app.data.model.BookSummary
import com.pinakes.app.data.model.GenreNode
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.network.ErrorCodes
import com.pinakes.app.data.network.NetworkModule
import com.pinakes.app.data.network.apiCall
import com.pinakes.app.data.network.apiResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Filters for a catalog search; nulls are omitted from the query. */
data class SearchFilters(
    val query: String? = null,
    val author: String? = null,
    val publisher: String? = null,
    val genreId: Int? = null,
    val language: String? = null,
    val availableOnly: Boolean? = null,
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
) {

    // Small in-memory ETag cache for book detail: id -> (etag, payload).
    private val detailCache = HashMap<Int, Pair<String?, BookDetail>>()

    /**
     * Reactive cached catalog snapshot (offline-first). Emits whatever Room holds —
     * including offline — so the UI can render instantly without a network round-trip
     * for the list or, via Coil's disk cache, its covers. Empty until the first refresh.
     */
    fun observeCachedCatalog(): Flow<List<BookSummary>> =
        catalogDao.observeAll().map { rows -> rows.map { it.toSummary() } }

    /** True once a catalog snapshot has been cached at least once. */
    suspend fun hasCachedCatalog(): Boolean = catalogDao.count() > 0

    /**
     * Refresh the cached catalog from the network (first page, unfiltered) and replace
     * the Room snapshot atomically. On network failure the existing cache is kept, so a
     * refresh-on-open that fails never wipes the offline catalog.
     */
    suspend fun refreshCatalog(limit: Int = 40): ApiResult<Unit> =
        when (val res = search(SearchFilters(), limit = limit)) {
            is ApiResult.Success -> {
                catalogDao.replaceAll(res.data.items.mapIndexed { i, b -> b.toCached(i) })
                ApiResult.Success(Unit, res.meta)
            }
            is ApiResult.Failure -> res
        }

    suspend fun search(
        filters: SearchFilters,
        cursor: String? = null,
        limit: Int = 20,
    ): ApiResult<SearchPage> {
        val api = network.api()
        return when (
            val res = apiCall {
                api.search(
                    q = filters.query?.takeIf { it.isNotBlank() },
                    author = filters.author?.takeIf { it.isNotBlank() },
                    publisher = filters.publisher?.takeIf { it.isNotBlank() },
                    genre = filters.genreId,
                    language = filters.language?.takeIf { it.isNotBlank() },
                    available = filters.availableOnly,
                    cursor = cursor,
                    limit = limit.coerceIn(1, 50),
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

    suspend fun book(id: Int): ApiResult<BookDetail> {
        val api = network.api()
        val cached = detailCache[id]
        val (result, response) = apiResponse { api.book(id, etag = cached?.first) }

        // 304 Not Modified → reuse the cached payload.
        if (response?.code() == 304 && cached != null) {
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
                if (cached != null && result.code != ErrorCodes.NETWORK) {
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
     * Per-day availability for [id] (~180 days from today) used to drive the colored
     * loan-request calendar. Mirrors the website's availability view.
     */
    suspend fun bookAvailability(id: Int): ApiResult<AvailabilityCalendar> {
        val api = network.api()
        return apiCall { api.bookAvailability(id) }
    }
}
