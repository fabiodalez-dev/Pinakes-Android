package com.pinakes.app.data.repository

import com.pinakes.app.data.model.StandaloneArticle
import com.pinakes.app.data.network.ApiResult

data class StandaloneArticlesPage(
    val items: List<StandaloneArticle>,
    val nextCursor: String? = null,
)

data class ArticleFilters(val query: String? = null, val mastheadId: Int? = null, val issueId: Int? = null,
    val genreId: Int? = null, val language: String? = null, val author: String? = null,
    val publisher: String? = null, val container: String? = null, val keyword: String? = null, val authorId: Int? = null)

/** Read-only article source, separately injectable for lifecycle/pagination tests. */
interface StandaloneArticlesSource {
    suspend fun standaloneArticlesSupported(): Boolean?
    suspend fun articles(query: String? = null, mastheadId: Int? = null, cursor: String? = null): ApiResult<StandaloneArticlesPage>
    suspend fun searchArticles(filters: ArticleFilters, cursor: String? = null): ApiResult<StandaloneArticlesPage> = articles(filters.query, filters.mastheadId, cursor)
    suspend fun article(id: Int): ApiResult<StandaloneArticle>
    suspend fun confirmGone(): Boolean
    fun articleWebUrl(id: Int): String? = null
}
