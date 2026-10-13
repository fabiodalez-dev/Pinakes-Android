package com.pinakes.app.ui.screens.staff

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pinakes.app.R
import com.pinakes.app.data.model.ExistingBook
import com.pinakes.app.data.model.FlatGenre
import com.pinakes.app.data.model.IdName
import com.pinakes.app.data.model.NextPosition
import com.pinakes.app.data.model.Shelf
import com.pinakes.app.data.model.StaffBook
import com.pinakes.app.data.model.StaffGenreNode
import com.pinakes.app.data.model.StaffLookups
import com.pinakes.app.data.model.flatten
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.ui.common.isOffline
import com.pinakes.app.ui.common.serverMessage
import com.pinakes.app.data.network.ErrorCodes
import com.pinakes.app.data.repository.StaffRepository
import com.pinakes.app.data.repository.isDuplicate
import com.pinakes.app.data.repository.isForbidden
import com.pinakes.app.data.staff.CoverImageEncoder
import com.pinakes.app.data.staff.FormField
import com.pinakes.app.data.staff.IsbnCodec
import com.pinakes.app.data.staff.PickedEntity
import com.pinakes.app.data.staff.StaffBookForm
import com.pinakes.app.data.staff.StaffBookWrite
import com.pinakes.app.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A cover change waiting for the save: a remote URL or a local photo/picked image. */
sealed interface PendingCover {
    data class FromUrl(val url: String) : PendingCover
    data class FromImage(val uri: Uri) : PendingCover
}

enum class PickerKind { AUTHORS, PUBLISHERS, GENRE, SUBGENRE }

/** One row in the picker sheet: an existing record or the typed query as a new name. */
data class PickerItem(val id: Int, val label: String, val detail: String? = null)

/** One-shot outcomes the screen reacts to by navigating. */
sealed interface StaffEditEvent {
    data class Saved(val bookId: Int, val created: Boolean) : StaffEditEvent
    data object Deleted : StaffEditEvent
    data class OpenExisting(val bookId: Int) : StaffEditEvent
}

data class StaffBookEditUiState(
    val bookId: Int = 0,
    val loading: Boolean = true,
    val loadError: String? = null,
    val loadOffline: Boolean = false,
    val original: StaffBook? = null,
    val form: StaffBookForm = StaffBookForm(),
    /** Set after the first save attempt so untouched empty fields do not start red. */
    val showErrors: Boolean = false,
    val lookups: StaffLookups = StaffLookups(),
    val genres: List<FlatGenre> = emptyList(),
    val shelves: List<Shelf> = emptyList(),
    val nextPosition: NextPosition? = null,
    val pendingCover: PendingCover? = null,
    val saving: Boolean = false,
    val error: String? = null,
    val errorRes: Int? = null,
    /** 409 duplicate: the record that already carries this identifier. */
    val duplicate: ExistingBook? = null,
    /** Identifier lookup for a prefilled new record found nothing online. */
    val lookupNoteRes: Int? = null,
    val addCopies: Int = 1,
    val addingCopies: Boolean = false,
    val deleting: Boolean = false,
    val picker: PickerKind? = null,
    val pickerQuery: String = "",
    val pickerResults: List<PickerItem> = emptyList(),
    val pickerLoading: Boolean = false,
    val snackbar: String? = null,
    val snackbarRes: Int? = null,
    val event: StaffEditEvent? = null,
    val forbidden: Boolean = false,
) {
    val isEdit: Boolean get() = bookId > 0
    val errors: Set<FormField> get() = if (showErrors) form.validate(isEdit) else emptySet()
    val levels get() = shelves.firstOrNull { it.id == form.shelfId }?.levels.orEmpty()

    /**
     * Copies "Add copy" on the duplicate dialog adds to the existing record: the copies the
     * user asked for when creating, but exactly one when editing (the form's count is then the
     * edited record's own total, which must never be added to another book).
     */
    val duplicateAddQuantity: Int get() = if (isEdit) 1 else form.copies.coerceIn(StaffBookForm.COPIES_RANGE)
}

/**
 * Create and edit share this ViewModel. Nav args: `bookId` (edit) or `isbn` (create, optional:
 * the identifier is looked up and the form prefilled from the scraper).
 */
@HiltViewModel
class StaffBookEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val staff: StaffRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    /** 0 while creating; becomes the new id when a create succeeds but the cover upload fails. */
    private var bookId: Int = savedStateHandle.get<Int>(Routes.ARG_BOOK_ID) ?: 0
    private val isbnArg: String = savedStateHandle.get<String>(Routes.ARG_ISBN).orEmpty()

    private val _state = MutableStateFlow(StaffBookEditUiState(bookId = bookId))
    val state: StateFlow<StaffBookEditUiState> = _state.asStateFlow()

    private var pickerJob: Job? = null
    private var positionJob: Job? = null
    private var genreTree: List<StaffGenreNode> = emptyList()

    init { load() }

    fun load() {
        _state.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            val lookups = async { staff.lookups() }
            val genres = async { staff.genres() }
            val shelves = async { staff.shelves() }
            if (bookId > 0) {
                when (val res = staff.book(bookId)) {
                    is ApiResult.Success -> _state.update {
                        it.copy(original = res.data, form = StaffBookForm.from(res.data), loading = false)
                    }
                    is ApiResult.Failure -> {
                        _state.update {
                            it.copy(loading = false, forbidden = res.isForbidden(), loadError = res.serverMessage() ?: "", loadOffline = res.isOffline)
                        }
                        return@launch
                    }
                }
            } else {
                prefillFromIdentifier()
            }
            (lookups.await() as? ApiResult.Success)?.let { r -> _state.update { it.copy(lookups = r.data) } }
            (genres.await() as? ApiResult.Success)?.let { r ->
                genreTree = r.data
                _state.update { it.copy(genres = r.data.flatten()) }
            }
            (shelves.await() as? ApiResult.Success)?.let { r -> _state.update { it.copy(shelves = r.data) } }
            refreshNextPosition()
        }
    }

    /** New record: look the identifier up (when given) and prefill, or start blank. */
    private suspend fun prefillFromIdentifier() {
        val identifier = IsbnCodec.fromManual(isbnArg)
        if (identifier == null) {
            _state.update { it.copy(form = StaffBookForm(), loading = false) }
            return
        }
        when (val res = staff.isbnLookup(identifier.lookupCode)) {
            is ApiResult.Success -> _state.update {
                it.copy(
                    form = StaffBookForm.fromPreview(res.data.preview, identifier),
                    duplicate = res.data.existing,
                    lookupNoteRes = if (res.data.preview == null) R.string.staff_scan_not_found_manual else null,
                    loading = false,
                )
            }
            is ApiResult.Failure -> _state.update {
                it.copy(
                    form = StaffBookForm.fromIdentifier(identifier),
                    loading = false,
                    forbidden = res.isForbidden(),
                    lookupNoteRes = R.string.staff_lookup_failed_manual,
                )
            }
        }
    }

    fun edit(transform: (StaffBookForm) -> StaffBookForm) {
        val before = _state.value.form
        val after = transform(before)
        _state.update { it.copy(form = after, error = null, errorRes = null) }
        if (after.shelfId != before.shelfId || after.levelId != before.levelId) refreshNextPosition()
    }

    fun selectShelf(id: Int?) = edit { it.copy(shelfId = id, levelId = null) }

    fun selectLevel(id: Int?) = edit { it.copy(levelId = id) }

    private fun refreshNextPosition() {
        val f = _state.value.form
        positionJob?.cancel()
        val shelf = f.shelfId
        val level = f.levelId
        if (shelf == null || level == null) {
            _state.update { it.copy(nextPosition = null) }
            return
        }
        positionJob = viewModelScope.launch {
            val res = staff.nextPosition(shelf, level, bookId.takeIf { it > 0 })
            _state.update { it.copy(nextPosition = (res as? ApiResult.Success)?.data) }
        }
    }

    // ---- Pickers ----

    fun openPicker(kind: PickerKind) {
        _state.update { it.copy(picker = kind, pickerQuery = "", pickerResults = emptyList()) }
        searchPicker("")
    }

    fun closePicker() {
        pickerJob?.cancel()
        _state.update { it.copy(picker = null, pickerLoading = false) }
    }

    fun onPickerQuery(value: String) {
        _state.update { it.copy(pickerQuery = value) }
        searchPicker(value)
    }

    private fun searchPicker(query: String) {
        val kind = _state.value.picker ?: return
        pickerJob?.cancel()
        when (kind) {
            PickerKind.GENRE, PickerKind.SUBGENRE -> {
                val parent = _state.value.form.genre?.id
                val pool = if (kind == PickerKind.GENRE) _state.value.genres
                else childrenOf(parent)
                val q = query.trim()
                _state.update {
                    it.copy(pickerResults = pool.filter { g -> q.isEmpty() || g.path.contains(q, ignoreCase = true) }
                        .take(200).map { g -> PickerItem(g.id, g.name, g.path.takeIf { p -> p != g.name }) })
                }
            }
            PickerKind.AUTHORS, PickerKind.PUBLISHERS -> {
                pickerJob = viewModelScope.launch {
                    if (query.isNotBlank()) delay(300)
                    _state.update { it.copy(pickerLoading = true) }
                    val items = if (kind == PickerKind.AUTHORS) {
                        when (val res = staff.authors(query)) {
                            is ApiResult.Success -> res.data.map { a ->
                                PickerItem(a.id, a.name, listOfNotNull(a.pseudonym, a.born?.let { b -> listOfNotNull(b, a.died).joinToString("–") })
                                    .joinToString(" · ").ifBlank { null })
                            }
                            is ApiResult.Failure -> emptyList()
                        }
                    } else {
                        when (val res = staff.publishers(query)) {
                            is ApiResult.Success -> res.data.map { p -> PickerItem(p.id, p.name) }
                            is ApiResult.Failure -> emptyList()
                        }
                    }
                    _state.update { it.copy(pickerResults = items, pickerLoading = false) }
                }
            }
        }
    }

    /** Descendants of the chosen genre (any depth), for the subgenre picker. */
    private fun childrenOf(parentId: Int?): List<FlatGenre> {
        if (parentId == null) return emptyList()
        fun find(nodes: List<StaffGenreNode>): StaffGenreNode? {
            for (n in nodes) {
                if (n.id == parentId) return n
                find(n.children)?.let { return it }
            }
            return null
        }
        return find(genreTree)?.children.orEmpty().flatten()
    }

    /** [item] null = add the typed query as a new name (authors/publishers only). */
    fun pick(item: PickerItem?) {
        val kind = _state.value.picker ?: return
        val query = _state.value.pickerQuery.trim()
        when (kind) {
            PickerKind.AUTHORS -> {
                val entity = item?.let { PickedEntity(it.id, it.label) } ?: PickedEntity(0, query)
                if (entity.name.isNotBlank()) edit { f -> f.copy(authors = (f.authors + entity).distinctBy { e -> if (e.isNew) "n:${e.name.lowercase()}" else "i:${e.id}" }) }
            }
            PickerKind.PUBLISHERS -> {
                val entity = item?.let { PickedEntity(it.id, it.label) } ?: PickedEntity(0, query)
                if (entity.name.isNotBlank()) edit { f -> f.copy(publishers = (f.publishers + entity).distinctBy { e -> if (e.isNew) "n:${e.name.lowercase()}" else "i:${e.id}" }) }
            }
            PickerKind.GENRE -> item?.let { g -> edit { f -> f.copy(genre = IdName(g.id, g.label), subgenre = null) } }
            PickerKind.SUBGENRE -> item?.let { g -> edit { f -> f.copy(subgenre = IdName(g.id, g.label)) } }
        }
        closePicker()
    }

    fun removeAuthor(e: PickedEntity) = edit { f -> f.copy(authors = f.authors - e) }
    fun removePublisher(e: PickedEntity) = edit { f -> f.copy(publishers = f.publishers - e) }

    // ---- Cover ----

    fun setPendingCover(cover: PendingCover?) = _state.update { it.copy(pendingCover = cover) }

    // ---- Save ----

    fun save() {
        val s = _state.value
        if (s.saving) return
        if (!s.form.canSave(s.isEdit)) {
            _state.update { it.copy(showErrors = true, errorRes = R.string.staff_form_fix_errors, error = null) }
            return
        }
        _state.update { it.copy(saving = true, showErrors = true, error = null, errorRes = null) }
        viewModelScope.launch {
            val pending = s.pendingCover
            // A typed cover URL rides along with the create (the server downloads it).
            val createForm = if (!s.isEdit && pending is PendingCover.FromUrl) s.form.copy(coverUrl = pending.url) else s.form
            val result = if (s.isEdit) {
                val original = s.original ?: run {
                    _state.update { it.copy(saving = false) }
                    return@launch
                }
                val patch = StaffBookWrite.update(original, s.form)
                if (patch.isEmpty()) ApiResult.Success(original) else staff.updateBook(bookId, patch)
            } else {
                staff.createBook(StaffBookWrite.create(createForm))
            }
            when (result) {
                is ApiResult.Success -> {
                    val id = result.data.id.takeIf { it > 0 } ?: bookId
                    val coverOk = applyCover(id, pending, alreadySent = !s.isEdit && pending is PendingCover.FromUrl)
                    if (coverOk) {
                        _state.update { it.copy(saving = false, pendingCover = null, event = StaffEditEvent.Saved(id, created = !s.isEdit)) }
                    } else {
                        // The record is saved but the cover is not: stay here, now editing the
                        // saved record, keep the pending cover so the next Save retries the
                        // upload, and say so (navigating away would swallow the message).
                        bookId = id
                        val saved = result.data.takeIf { it.id > 0 } ?: s.original
                        _state.update {
                            it.copy(
                                saving = false,
                                bookId = id,
                                original = saved ?: it.original,
                                form = saved?.let { b -> StaffBookForm.from(b) } ?: it.form,
                                snackbarRes = R.string.staff_cover_failed,
                            )
                        }
                    }
                }
                is ApiResult.Failure -> onSaveFailure(result)
            }
        }
    }

    /** Upload or download the pending cover once the record exists. False when it failed. */
    private suspend fun applyCover(id: Int, pending: PendingCover?, alreadySent: Boolean): Boolean {
        if (pending == null || alreadySent || id <= 0) return true
        return when (pending) {
            is PendingCover.FromUrl -> staff.coverFromUrl(id, pending.url) is ApiResult.Success
            is PendingCover.FromImage -> {
                val bytes = withContext(Dispatchers.IO) { CoverImageEncoder.encode(context, pending.uri) } ?: return false
                staff.uploadCover(id, bytes, "image/jpeg") is ApiResult.Success
            }
        }
    }

    private fun onSaveFailure(res: ApiResult.Failure) {
        _state.update {
            when {
                res.isForbidden() -> it.copy(saving = false, forbidden = true)
                res.isDuplicate() -> it.copy(
                    saving = false,
                    duplicate = staff.existingBook(res) ?: ExistingBook(title = it.form.title),
                )
                res.code == ErrorCodes.LOCK_ERROR -> it.copy(saving = false, errorRes = R.string.staff_error_busy)
                res.code == ErrorCodes.NETWORK -> it.copy(saving = false, errorRes = R.string.staff_error_offline)
                else -> it.copy(saving = false, error = res.serverMessage(), errorRes = R.string.staff_error_save)
            }
        }
    }

    fun dismissDuplicate() = _state.update { it.copy(duplicate = null) }

    fun openExisting() {
        val id = _state.value.duplicate?.id ?: return
        _state.update { it.copy(duplicate = null, event = StaffEditEvent.OpenExisting(id)) }
    }

    /** "Add copy" on the duplicate dialog: [StaffBookEditUiState.duplicateAddQuantity] copies of the existing record. */
    fun addCopyToExisting() {
        val existing = _state.value.duplicate ?: return
        if (existing.id <= 0) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            when (val res = staff.addCopies(existing.id, _state.value.duplicateAddQuantity)) {
                is ApiResult.Success -> _state.update {
                    it.copy(saving = false, duplicate = null, event = StaffEditEvent.Saved(existing.id, created = false))
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(saving = false, forbidden = res.isForbidden(), error = res.serverMessage(), errorRes = R.string.staff_error_save)
                }
            }
        }
    }

    // ---- Edit-only actions ----

    fun setAddCopies(n: Int) = _state.update { it.copy(addCopies = n.coerceIn(1, 100)) }

    fun addCopies() {
        if (!_state.value.isEdit || _state.value.addingCopies) return
        _state.update { it.copy(addingCopies = true) }
        viewModelScope.launch {
            when (val res = staff.addCopies(bookId, _state.value.addCopies)) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        addingCopies = false,
                        addCopies = 1,
                        original = it.original?.copy(copiesTotal = res.data.copiesTotal, copiesAvailable = res.data.copiesAvailable),
                        snackbarRes = R.string.staff_copies_added,
                    )
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(addingCopies = false, forbidden = res.isForbidden(), snackbar = res.serverMessage(), snackbarRes = R.string.staff_error_save)
                }
            }
        }
    }

    /** The copies section re-read the record: take its copies and counters, never the form. */
    fun applyCopies(book: com.pinakes.app.data.model.StaffBook) = _state.update {
        it.copy(original = it.original?.copy(copies = book.copies, copiesTotal = book.copiesTotal, copiesAvailable = book.copiesAvailable))
    }

    fun showMessage(text: String) = _state.update { it.copy(snackbar = text) }

    fun markForbidden() = _state.update { it.copy(forbidden = true) }

    fun delete() {
        if (!_state.value.isEdit || _state.value.deleting) return
        _state.update { it.copy(deleting = true) }
        viewModelScope.launch {
            when (val res = staff.deleteBook(bookId)) {
                is ApiResult.Success -> _state.update { it.copy(deleting = false, event = StaffEditEvent.Deleted) }
                is ApiResult.Failure -> _state.update {
                    when {
                        res.isForbidden() -> it.copy(deleting = false, forbidden = true)
                        res.httpStatus == 409 || res.code == ErrorCodes.CONFLICT ->
                            it.copy(deleting = false, snackbarRes = R.string.staff_book_delete_active_circulation)
                        else -> it.copy(deleting = false, snackbar = res.serverMessage(), snackbarRes = R.string.staff_error_save)
                    }
                }
            }
        }
    }

    fun consumeEvent() = _state.update { it.copy(event = null) }
    fun consumeSnackbar() = _state.update { it.copy(snackbar = null, snackbarRes = null) }
}
