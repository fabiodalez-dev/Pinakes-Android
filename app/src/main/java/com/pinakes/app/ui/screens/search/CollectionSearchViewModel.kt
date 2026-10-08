package com.pinakes.app.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pinakes.app.data.model.*
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.repository.*
import com.pinakes.app.data.store.InstanceFeatures
import com.pinakes.app.ui.screens.collections.CollectionPager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Every collection owns its cursor: loading more books cannot discard article/archive matches. */
@HiltViewModel
class CollectionSearchViewModel @Inject constructor(private val collections: CollectionsSource, private val articles: StandaloneArticlesSource) : ViewModel() {
    private val articlePager = CollectionPager<ArticleFilters, StandaloneArticle>(viewModelScope, { filters, cursor ->
        when (val result = articles.searchArticles(filters, cursor)) {
            is ApiResult.Success -> ApiResult.Success(CollectionPage(result.data.items, result.data.nextCursor))
            is ApiResult.Failure -> result
        }
    }, StandaloneArticle::id)
    private val wantedPager = CollectionPager(viewModelScope, collections::wanted, WantedBook::id)
    private val archivePager = CollectionPager(viewModelScope, collections::archives, ArchiveRecord::id)
    val articleState = articlePager.state
    val wantedState = wantedPager.state
    val archiveState = archivePager.state
    val showArticles = MutableStateFlow(false)
    val showWanted = MutableStateFlow(false)
    val showArchives = MutableStateFlow(false)
    private var generation = 0

    fun search(filters: SearchFilters, features: InstanceFeatures) {
        val request = ++generation
        showArticles.value = false; showWanted.value = false; showArchives.value = false
        val query = filters.query.orEmpty()
        // Inventory-only facets cannot truthfully be applied to wanted books or archival units.
        val onlyQuery = filters.author.isNullOrBlank() && filters.publisher.isNullOrBlank() && filters.genreId == null && filters.language == null && filters.availableOnly != true
        if (query.isNotBlank() && onlyQuery && features.desiderataAvailable) { showWanted.value = true; wantedPager.reload(query) }
        if (query.isNotBlank() && onlyQuery && features.archivesAvailable) { showArchives.value = true; archivePager.reload(ArchiveFilters(query)) }
        if (features.periodicalsAvailable && filters.availableOnly != true) viewModelScope.launch {
            val supported = articles.standaloneArticlesSupported()
            if (request != generation || supported != true) return@launch
            showArticles.value = true
            articlePager.reload(ArticleFilters(query.takeIf(String::isNotBlank), genreId = filters.genreId, language = filters.language,
                author = filters.author?.takeIf { filters.authorId == null }, authorId = filters.authorId, publisher = filters.publisher))
        }
    }
    fun moreArticles() = articlePager.more()
    fun moreWanted() = wantedPager.more()
    fun moreArchives() = archivePager.more()
}
