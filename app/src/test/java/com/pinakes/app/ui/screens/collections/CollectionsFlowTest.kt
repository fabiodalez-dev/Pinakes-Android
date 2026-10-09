package com.pinakes.app.ui.screens.collections

import androidx.lifecycle.SavedStateHandle
import com.pinakes.app.data.model.*
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.repository.*
import com.pinakes.app.ui.navigation.Routes
import com.pinakes.app.ui.screens.search.genrePath
import com.pinakes.app.ui.screens.search.CollectionSearchViewModel
import com.pinakes.app.data.store.InstanceFeatures
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionsFlowTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }

    private class Source : CollectionsSource {
        val offers = mutableListOf<DonationOfferRequest>()
        var receipt: ApiResult<DonationReceipt> = ApiResult.Success(DonationReceipt(4, "submitted"))
        var status: ApiResult<DonationReceipt> = ApiResult.Failure("not_found", "", 404)
        override suspend fun offer(request: DonationOfferRequest): ApiResult<DonationReceipt> { offers += request; return receipt }
        override suspend fun offerStatus(submission: String) = status
        override suspend fun wantedBook(id: Int) = ApiResult.Success(WantedBook(id, "Requested title"))
        override suspend fun wanted(query: String, cursor: String?) = ApiResult.Success(CollectionPage<WantedBook>(emptyList()))
        override suspend fun archive(id: Int) = ApiResult.Success(ArchiveRecord(id))
        override suspend fun archives(filters: ArchiveFilters, cursor: String?) = ApiResult.Success(CollectionPage<ArchiveRecord>(emptyList()))
        override suspend fun archivesHealth() = ApiResult.Success(CollectionHealth())
        override suspend fun desiderataHealth() = ApiResult.Success(CollectionHealth())
    }

    @Test fun uncertainDonationSurvivesProcessDeathAndRecoversWithoutResending() = runTest {
        val source = Source().apply { receipt = ApiResult.Failure("network", "timeout") }
        val saved = SavedStateHandle()
        val vm = DonationViewModel(source, saved)
        vm.edit("title", "A wanted book"); vm.consent(true); vm.submit(); advanceUntilIdle()
        assertTrue(vm.state.value.uncertain)
        val submission = source.offers.single().submissionId
        vm.edit("title", "Another book"); vm.consent(false); vm.proposeFreely()
        assertEquals("A wanted book", vm.state.value.title)
        assertTrue(vm.state.value.consent)
        source.status = ApiResult.Success(DonationReceipt(4, "submitted"))
        val restored = DonationViewModel(source, saved)
        restored.submit(); advanceUntilIdle()
        assertTrue(restored.state.value.submitted)
        assertEquals(submission, source.offers.single().submissionId)
    }

    @Test fun unknownOutcomeRetriesTheIdenticalPayloadAndUuid() = runTest {
        val source = Source().apply { receipt = ApiResult.Failure("internal_error", "", 500) }
        val vm = DonationViewModel(source, SavedStateHandle())
        vm.edit("title", "Book"); vm.consent(true); vm.submit(); advanceUntilIdle()
        source.receipt = ApiResult.Success(DonationReceipt(5, "submitted"))
        vm.submit(); advanceUntilIdle()
        assertEquals(source.offers.first(), source.offers.last())
        assertTrue(vm.state.value.submitted)
    }

    @Test fun uncertainLookupFailureKeepsDraftFrozenAndDoesNotPostAgain() = runTest {
        val source = Source().apply { receipt = ApiResult.Failure("network", "") }
        val vm = DonationViewModel(source, SavedStateHandle())
        vm.edit("title", "Book"); vm.consent(true); vm.submit(); advanceUntilIdle()
        source.status = ApiResult.Failure("network", "")
        vm.submit(); advanceUntilIdle()
        assertEquals(1, source.offers.size)
        assertTrue(vm.state.value.uncertain)
    }

    @Test fun selectedBookCanBecomeFreeProposalAndStaysFreeAfterRecreation() = runTest {
        val source = Source()
        val saved = SavedStateHandle(mapOf(Routes.ARG_WANTED_ID to 9))
        val vm = DonationViewModel(source, saved); advanceUntilIdle()
        assertEquals("Requested title", vm.state.value.title)
        vm.proposeFreely()
        val restored = DonationViewModel(source, saved)
        restored.consent(true); restored.submit(); advanceUntilIdle()
        assertNull(source.offers.single().bookId)
        assertTrue(restored.state.value.submitted)
    }

    @Test fun missingConsentPreventsAnyRequest() = runTest {
        val source = Source(); val vm = DonationViewModel(source, SavedStateHandle())
        vm.edit("title", "Book"); vm.submit(); advanceUntilIdle()
        assertTrue(source.offers.isEmpty())
    }

    @Test fun lateCursorPageCannotOverwriteTheNewQueryEvenIfTransportIgnoresCancellation() = runTest {
        val pending = CompletableDeferred<ApiResult<CollectionPage<WantedBook>>>()
        val pager = CollectionPager<String, WantedBook>(this, { query, cursor ->
            if (cursor != null) withContext(NonCancellable) { pending.await() }
            else ApiResult.Success(CollectionPage(listOf(WantedBook(if (query == "old") 1 else 9)), "2"))
        }, WantedBook::id)
        pager.reload("old"); runCurrent(); pager.more(); runCurrent()
        pager.reload("new", debounce = true)
        pending.complete(ApiResult.Success(CollectionPage(listOf(WantedBook(2)))))
        runCurrent(); assertTrue(pager.state.value.items.isEmpty())
        advanceUntilIdle(); assertEquals(listOf(9), pager.state.value.items.map { it.id })
    }

    @Test fun paginationDeduplicatesRecordsAndStopsRepeatingCursor() = runTest {
        val pager = CollectionPager<String, WantedBook>(this, { _, cursor ->
            ApiResult.Success(CollectionPage(if (cursor == null) listOf(WantedBook(1)) else listOf(WantedBook(1), WantedBook(2)), "2"))
        }, WantedBook::id)
        pager.reload(""); runCurrent(); pager.more(); runCurrent()
        assertEquals(listOf(1, 2), pager.state.value.items.map { it.id }); assertNull(pager.state.value.nextCursor)
    }

    @Test fun deepGenreSelectionKeepsAllAncestors() {
        var tree = GenreNode(id = 20, name = "leaf")
        for (id in 19 downTo 1) tree = GenreNode(id, "$id", listOf(tree))
        assertEquals((1..20).toList(), genrePath(listOf(tree), 20).map { it.id })
    }

    @Test fun catalogBrowseSkipsArticlesButSharedAuthorAndQueriesFindThem() = runTest {
        val requests = mutableListOf<ArticleFilters>()
        val articles = object : StandaloneArticlesSource {
            override suspend fun standaloneArticlesSupported() = true
            override suspend fun articles(query: String?, mastheadId: Int?, cursor: String?) = ApiResult.Success(StandaloneArticlesPage(emptyList()))
            override suspend fun searchArticles(filters: ArticleFilters, cursor: String?): ApiResult<StandaloneArticlesPage> {
                requests += filters
                return ApiResult.Success(StandaloneArticlesPage(emptyList()))
            }
            override suspend fun article(id: Int) = ApiResult.Success(StandaloneArticle(id = id))
            override suspend fun confirmGone() = false
        }
        val vm = CollectionSearchViewModel(Source(), articles)
        val features = InstanceFeatures(periodicalsAvailable = true, desiderataAvailable = true, archivesAvailable = true)
        vm.search(SearchFilters(), features); advanceUntilIdle()
        assertTrue(requests.isEmpty()); assertFalse(vm.showArticles.value)
        vm.search(SearchFilters(authorId = 42), features); advanceUntilIdle()
        assertEquals(42, requests.single().authorId); assertTrue(vm.showArticles.value)
        assertFalse(vm.showWanted.value); assertFalse(vm.showArchives.value)
        vm.search(SearchFilters(query = "library"), features); advanceUntilIdle()
        assertEquals("library", requests.last().query)
        assertTrue(vm.showWanted.value); assertTrue(vm.showArchives.value)
        vm.clear()
        assertFalse(vm.showArticles.value); assertFalse(vm.showWanted.value); assertFalse(vm.showArchives.value)
    }

    @Test fun currentServerWireFormatCarriesAllFilesAndCitationStyles() {
        val json = Json { ignoreUnknownKeys = true }
        val book = json.decodeFromString<BookDetail>("""{"id":1,"digital_attachments":[{"url":"https://library.example/book.pdf","kind":"ebook"},{"url":"https://library.example/book.epub","kind":"ebook"},{"url":"https://library.example/review.pdf","kind":"supplement"},{"url":"https://library.example/audio.mp3","kind":"audio"}],"citations":[{"key":"oxford","label":"Oxford","text":"Reference"}],"genre_path":[{"id":1,"name":"Root"},{"id":20,"name":"Leaf"}]}""")
        assertEquals(4, book.digitalFiles.size); assertEquals("oxford", book.citations.single().key)
        assertEquals("Leaf", book.genrePath.last().name)
        val archive = json.decodeFromString<ArchiveRecord>("""{"id":2,"fields":{},"ancestors":[],"documents":[{"url":"https://library.example/image.jpg","mime":"image/jpeg"},{"url":"https://library.example/audio.wav","mime":"audio/wav"}]}""")
        assertEquals(2, archive.documents.size)
        assertEquals("-50–20", ArchiveRecord(dateStart = -50, dateEnd = 20).datesLabel)
    }
}
