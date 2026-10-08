package com.pinakes.app.data.repository

import com.pinakes.app.data.model.*
import com.pinakes.app.data.network.*

data class CollectionPage<T>(val items: List<T>, val nextCursor: String? = null, val total: Int? = null)
data class ArchiveFilters(val query: String = "", val level: String? = null, val from: Int? = null, val to: Int? = null, val parentId: Int? = null)

/** Injectable contract lets paging, donation retries and server capability changes be tested. */
interface CollectionsSource {
    suspend fun archivesHealth(): ApiResult<CollectionHealth>
    suspend fun desiderataHealth(): ApiResult<CollectionHealth>
    suspend fun archives(filters: ArchiveFilters = ArchiveFilters(), cursor: String? = null): ApiResult<CollectionPage<ArchiveRecord>>
    suspend fun archive(id: Int): ApiResult<ArchiveRecord>
    suspend fun wanted(query: String = "", cursor: String? = null): ApiResult<CollectionPage<WantedBook>>
    suspend fun wantedBook(id: Int): ApiResult<WantedBook>
    suspend fun offer(request: DonationOfferRequest): ApiResult<DonationReceipt>
    suspend fun offerStatus(submission: String): ApiResult<DonationReceipt>
}

class CollectionsRepository(private val network: NetworkModule) : CollectionsSource {
    override suspend fun archivesHealth() = apiCall { network.collectionsApi().archivesHealth() }
    override suspend fun desiderataHealth() = apiCall { network.collectionsApi().desiderataHealth() }
    override suspend fun archive(id: Int) = apiCall { network.collectionsApi().archive(id) }
    override suspend fun wantedBook(id: Int) = apiCall { network.collectionsApi().wantedBook(id) }
    override suspend fun offer(request: DonationOfferRequest) = apiCall { network.collectionsApi().offer(request) }
    override suspend fun offerStatus(submission: String) = apiCall { network.collectionsApi().offerStatus(submission) }
    override suspend fun archives(filters: ArchiveFilters, cursor: String?) = page {
        network.collectionsApi().archives(filters.query.takeIf { it.isNotBlank() }, filters.level, filters.from, filters.to, filters.parentId, cursor)
    }
    override suspend fun wanted(query: String, cursor: String?) = page {
        network.collectionsApi().wanted(query.takeIf { it.isNotBlank() }, cursor)
    }

    private suspend fun <T> page(call: suspend () -> Envelope<List<T>>): ApiResult<CollectionPage<T>> = when (val result = apiCall(call)) {
        is ApiResult.Success -> ApiResult.Success(CollectionPage(result.data, result.meta?.nextCursor, result.meta?.totalCount))
        is ApiResult.Failure -> result
    }
}
