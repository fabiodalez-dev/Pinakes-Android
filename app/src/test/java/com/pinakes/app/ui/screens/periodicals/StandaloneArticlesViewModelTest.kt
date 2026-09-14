package com.pinakes.app.ui.screens.periodicals

import androidx.lifecycle.SavedStateHandle
import com.pinakes.app.data.model.StandaloneArticle
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.network.ErrorCodes
import com.pinakes.app.data.repository.StandaloneArticlesPage
import com.pinakes.app.data.repository.StandaloneArticlesSource
import com.pinakes.app.ui.common.UiState
import com.pinakes.app.ui.navigation.Routes
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StandaloneArticlesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }

    private class Source : StandaloneArticlesSource {
        var supported: Boolean? = true
        var calls = mutableListOf<Triple<String?, Int?, String?>>()
        var list: suspend (String?, String?) -> ApiResult<StandaloneArticlesPage> = { _, _ ->
            ApiResult.Success(StandaloneArticlesPage(listOf(StandaloneArticle(id = 1)), "1"))
        }
        var detail: ApiResult<StandaloneArticle> = ApiResult.Success(StandaloneArticle(id = 1, hasPublicPdf = true, pdfUrl = "https://example.org/pdf"))
        override suspend fun standaloneArticlesSupported() = supported
        override suspend fun articles(query: String?, mastheadId: Int?, cursor: String?): ApiResult<StandaloneArticlesPage> {
            calls += Triple(query, mastheadId, cursor)
            return list(query, cursor)
        }
        override suspend fun article(id: Int) = detail
        override suspend fun confirmGone() = false
    }

    @Test fun oldServerKeepsArticlesUnavailableWithoutCallingMissingEndpoint() = runTest {
        val source = Source().apply { supported = false }
        val vm = StandaloneArticlesViewModel(source, SavedStateHandle())
        advanceUntilIdle()
        assertTrue(vm.state.value.unavailable)
        assertTrue(source.calls.isEmpty())
    }

    @Test fun mastheadFilterAndSearchArePassedToEveryCursorPage() = runTest {
        val source = Source()
        val vm = StandaloneArticlesViewModel(source, SavedStateHandle(mapOf(Routes.ARG_PERIODICAL_ID to 7)))
        advanceUntilIdle()
        vm.onQueryChange("Tyll")
        advanceUntilIdle()
        source.list = { _, _ -> ApiResult.Success(StandaloneArticlesPage(listOf(StandaloneArticle(id = 1), StandaloneArticle(id = 2)))) }
        vm.loadMore()
        advanceUntilIdle()
        assertEquals(Triple("Tyll", 7, "1"), source.calls.last())
        assertEquals(listOf(1, 2), vm.state.value.items.map { it.id })
        assertNull(vm.state.value.nextCursor)
    }

    @Test fun aLatePageCannotAppendAfterTheQueryChanges() = runTest {
        val source = Source()
        val vm = StandaloneArticlesViewModel(source, SavedStateHandle())
        advanceUntilIdle()
        val pending = CompletableDeferred<ApiResult<StandaloneArticlesPage>>()
        source.list = { _, cursor ->
            if (cursor != null) withContext(NonCancellable) { pending.await() }
            else ApiResult.Success(StandaloneArticlesPage(listOf(StandaloneArticle(id = 9))))
        }
        vm.loadMore(); runCurrent()
        vm.onQueryChange("new")
        pending.complete(ApiResult.Success(StandaloneArticlesPage(listOf(StandaloneArticle(id = 2)))))
        runCurrent()
        assertTrue(vm.state.value.items.isEmpty())
        advanceUntilIdle()
        assertEquals(listOf(9), vm.state.value.items.map { it.id })
    }

    @Test fun paginationFailureKeepsRowsAndSupportsRetry() = runTest {
        val source = Source()
        val vm = StandaloneArticlesViewModel(source, SavedStateHandle())
        advanceUntilIdle()
        source.list = { _, _ -> ApiResult.Failure(ErrorCodes.NETWORK, "offline") }
        vm.loadMore(); advanceUntilIdle()
        assertTrue(vm.state.value.moreError)
        assertEquals(listOf(1), vm.state.value.items.map { it.id })
        source.list = { _, _ -> ApiResult.Success(StandaloneArticlesPage(listOf(StandaloneArticle(id = 2)))) }
        vm.loadMore(); advanceUntilIdle()
        assertFalse(vm.state.value.moreError)
        assertEquals(listOf(1, 2), vm.state.value.items.map { it.id })
    }

    @Test fun privateOrDeletedArticleClearsCachedPdfOnRefresh() = runTest {
        val source = Source()
        val vm = StandaloneArticleViewModel(source, SavedStateHandle(mapOf(Routes.ARG_ARTICLE_ID to 1)))
        advanceUntilIdle()
        assertTrue(vm.state.value is UiState.Success)
        source.detail = ApiResult.Failure(ErrorCodes.NOT_FOUND, "Not found", 404)
        vm.refresh(); advanceUntilIdle()
        assertTrue(vm.state.value is UiState.Error)
    }
}
