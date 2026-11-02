package com.pinakes.app.staffe2e

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.pinakes.app.data.model.BookDetail
import com.pinakes.app.data.model.DigitalAttachment
import com.pinakes.app.data.model.Envelope
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.network.ErrorCodes
import com.pinakes.app.ui.common.UiState
import com.pinakes.app.ui.navigation.Routes
import com.pinakes.app.ui.screens.detail.BookDetailViewModel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** Real repository/ViewModel, HTTP cache and access decisions on a disposable emulator. */
class DigitalLinkRefreshFlowTest : AppFlowTest() {
    private val old = DigitalAttachment("https://library.example/a.epub?pk_exp=1000&pk_uid=3&pk_sig=old", access = "granted")
    private val fresh = old.copy(url = old.url.replace("1000", "4102444800").replace("old", "new"))
    private val book = BookDetail(id = 1, title = "Digital", digitalAttachments = listOf(old))
    private val store = ViewModelStore()

    private fun FakePinakes.answer(data: BookDetail = book) = body("GET catalog/books/1", 200,
        Json.encodeToString(Envelope(data = data)), mapOf("ETag" to "\"digital-v1\"", "Cache-Control" to "private, max-age=3600"))

    private fun viewModel(): BookDetailViewModel {
        lateinit var vm: BookDetailViewModel
        instrumentation.runOnMainSync {
            vm = BookDetailViewModel(SavedStateHandle(mapOf(Routes.ARG_BOOK_ID to 1)),
                deps.catalogRepository(), deps.libraryRepository(), deps.wishlistRepository(), deps.connectivity(), deps.featureStore())
            store.put("digital", vm)
        }
        compose.waitUntil(10_000) { vm.state.value.content is UiState.Success }
        return vm
    }

    @After fun clearViewModels() { instrumentation.runOnMainSync { store.clear() } }

    @Test fun forcedRenewalBypassesTheHttpCacheAndGetsANewSignature() {
        launch { answer() }
        val repository = deps.catalogRepository()
        assertTrue(runBlocking { repository.book(1) } is ApiResult.Success)
        api.answer(book.copy(digitalAttachments = listOf(fresh)))
        val response = runBlocking { repository.book(1, forceRefresh = true) }
        assertEquals(fresh.url, (response as ApiResult.Success).data.digitalFiles.single().url)
        val request = api.recorded("GET", "catalog/books/1").last()
        assertNull(request.getHeader("If-None-Match"))
        assertEquals("no-cache, no-store", request.getHeader("Cache-Control"))
        assertEquals(2, api.recorded("GET", "catalog/books/1").size)
    }

    @Test fun aServerFailureDuringRenewalCannotReturnTheCachedSignedLink() {
        launch { answer() }
        val repository = deps.catalogRepository()
        assertTrue(runBlocking { repository.book(1) } is ApiResult.Success)
        api.body("GET catalog/books/1", 503, """{"error":{"code":"server_error","message":"Unavailable"}}""")
        val response = runBlocking { repository.book(1, forceRefresh = true) }
        assertTrue(response is ApiResult.Failure)
        assertEquals(503, (response as ApiResult.Failure).httpStatus)
    }

    @Test fun anUnexpected304CannotPretendTheExpiredPayloadWasRenewed() {
        launch { answer() }
        val repository = deps.catalogRepository()
        assertTrue(runBlocking { repository.book(1) } is ApiResult.Success)
        api.body("GET catalog/books/1", 304, "")
        assertTrue(runBlocking { repository.book(1, forceRefresh = true) } is ApiResult.Failure)
    }

    @Test fun renewalFailurePreservesTheLoadedPageAndASubsequentRetrySucceeds() {
        launch { answer() }
        val vm = viewModel()
        val before = vm.state.value.content
        api.body("GET catalog/books/1", 500, """{"error":{"code":"server_error","message":"Unavailable"}}""")
        assertTrue(runBlocking { vm.refreshDigitalFile(old) } is ApiResult.Failure)
        assertSame(before, vm.state.value.content)
        api.answer(book.copy(digitalAttachments = listOf(fresh)))
        val result = runBlocking { vm.refreshDigitalFile(old) }
        assertEquals(fresh.url, (result as ApiResult.Success).data.url)
        assertEquals(fresh.url, (vm.state.value.content as UiState.Success).data.digitalFiles.single().url)
    }

    @Test fun renewalSelectsTheSameFileEvenWhenTheServerReordersAndRenamesEntries() {
        launch { answer() }
        val vm = viewModel()
        val other = fresh.copy(url = fresh.url.replace("a.epub", "b.epub"))
        api.answer(book.copy(digitalAttachments = listOf(other, fresh.copy(label = "Renamed"))))
        val result = runBlocking { vm.refreshDigitalFile(old) }
        assertEquals(fresh.url, (result as ApiResult.Success).data.url)
    }

    @Test fun revokedFileAccessUpdatesTheCardAndReturnsNoOpenableAddress() {
        launch { answer() }
        val vm = viewModel()
        api.answer(book.copy(digitalAttachments = listOf(old.copy(url = "", access = "loan_required"))))
        val result = runBlocking { vm.refreshDigitalFile(old) }
        assertEquals(ErrorCodes.FORBIDDEN, (result as ApiResult.Failure).code)
        val file = (vm.state.value.content as UiState.Success).data.digitalFiles.single()
        assertTrue(file.locked)
        assertNull(file.safeUrl)
    }
}
