package com.pinakes.app.data.repository

import com.pinakes.app.data.model.StandaloneArticle
import com.pinakes.app.data.network.ApiResult

data class StandaloneArticlesPage(
    val items: List<StandaloneArticle>,
    val nextCursor: String? = null,
)

/** Read-only article source, separately injectable for lifecycle/pagination tests. */
interface StandaloneArticlesSource {
    suspend fun standaloneArticlesSupported(): Boolean?
    suspend fun articles(query: String? = null, mastheadId: Int? = null, cursor: String? = null): ApiResult<StandaloneArticlesPage>
    suspend fun article(id: Int): ApiResult<StandaloneArticle>
    suspend fun confirmGone(): Boolean
}
