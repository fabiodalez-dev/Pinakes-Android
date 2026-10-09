package com.pinakes.app.ui.screens.collections

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pinakes.app.data.model.DonationOfferRequest
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.repository.CollectionsSource
import com.pinakes.app.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class DonationState(
    val bookId: Int? = null, val title: String = "", val author: String = "", val publisher: String = "",
    val isbn: String = "", val notes: String = "", val consent: Boolean = false,
    val loadingBook: Boolean = false, val sending: Boolean = false, val submitted: Boolean = false,
    val uncertain: Boolean = false,
    val error: ApiResult.Failure? = null,
) {
    val canSubmit get() = title.isNotBlank() && consent && !sending && !loadingBook && !submitted
}

@HiltViewModel
class DonationViewModel @Inject constructor(private val source: CollectionsSource, private val saved: SavedStateHandle) : ViewModel() {
    private var submissionId: String = saved.get<String>("submissionId") ?: UUID.randomUUID().toString().also { saved["submissionId"] = it }
    private val initialId = saved.get<Int>(Routes.ARG_WANTED_ID)?.takeIf { it > 0 }
    private val mutableState = MutableStateFlow(DonationState(
        bookId = (saved.get<Int>("draftBookId") ?: initialId)?.takeIf { it > 0 },
        title = saved["title"] ?: "", author = saved["author"] ?: "", publisher = saved["publisher"] ?: "",
        isbn = saved["isbn"] ?: "", notes = saved["notes"] ?: "", consent = saved["consent"] ?: false,
        submitted = saved["submitted"] ?: false,
        uncertain = saved["uncertain"] ?: false,
    ))
    val state = mutableState.asStateFlow()
    init { if (mutableState.value.bookId != null && mutableState.value.title.isBlank()) loadBook() }

    fun loadBook() {
        val id = mutableState.value.bookId ?: return
        mutableState.update { it.copy(loadingBook = true, error = null) }
        viewModelScope.launch {
            when (val result = source.wantedBook(id)) {
                is ApiResult.Success -> {
                    val book = result.data
                    saved["title"] = book.title; saved["author"] = book.author.orEmpty(); saved["publisher"] = book.publisher.orEmpty(); saved["isbn"] = book.isbn.orEmpty()
                    mutableState.update { it.copy(title = book.title, author = book.author.orEmpty(), publisher = book.publisher.orEmpty(), isbn = book.isbn.orEmpty(), loadingBook = false) }
                }
                is ApiResult.Failure -> mutableState.update { it.copy(loadingBook = false, error = result) }
            }
        }
    }

    fun edit(field: String, value: String) {
        if (mutableState.value.sending || mutableState.value.submitted || mutableState.value.uncertain || mutableState.value.loadingBook) return
        // Editing is allowed only once an earlier send is known to have failed.
        saved[field] = value
        mutableState.update { when (field) {
            "title" -> it.copy(title = value, error = null)
            "author" -> it.copy(author = value, error = null)
            "publisher" -> it.copy(publisher = value, error = null)
            "isbn" -> it.copy(isbn = value, error = null)
            "notes" -> it.copy(notes = value, error = null)
            else -> it
        } }
    }
    fun consent(value: Boolean) { if (!mutableState.value.sending && !mutableState.value.uncertain) { saved["consent"] = value; mutableState.update { it.copy(consent = value) } } }

    fun proposeFreely() {
        if (mutableState.value.sending || mutableState.value.uncertain) return
        saved["draftBookId"] = 0
        submissionId = UUID.randomUUID().toString(); saved["submissionId"] = submissionId
        mutableState.update { it.copy(bookId = null, error = null) }
    }

    fun submit() {
        val current = mutableState.value
        if (!current.canSubmit) return
        saved["uncertain"] = true
        mutableState.update { it.copy(sending = true, error = null) }
        val request = DonationOfferRequest(submissionId, current.bookId, current.title.trim(), current.author.trim(), current.publisher.trim(), current.isbn.trim(), current.notes.trim(), current.consent)
        viewModelScope.launch {
            if (current.uncertain) {
                when (val status = source.offerStatus(submissionId)) {
                    is ApiResult.Success -> { saved["submitted"] = true; saved["uncertain"] = false; mutableState.update { it.copy(sending = false, submitted = true, uncertain = false) }; return@launch }
                    is ApiResult.Failure -> if (status.httpStatus != 404) { mutableState.update { it.copy(sending = false, error = status) }; return@launch }
                }
            }
            when (val result = source.offer(request)) {
                is ApiResult.Success -> { saved["submitted"] = true; saved["uncertain"] = false; mutableState.update { it.copy(sending = false, submitted = true, uncertain = false) } }
                is ApiResult.Failure -> {
                    val uncertain = result.httpStatus == 0 || result.httpStatus >= 500
                    saved["uncertain"] = uncertain
                    mutableState.update { it.copy(sending = false, error = result, uncertain = uncertain) }
                }
            }
        }
    }
}
