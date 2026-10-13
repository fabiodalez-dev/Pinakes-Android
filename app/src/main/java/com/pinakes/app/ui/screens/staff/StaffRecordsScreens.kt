package com.pinakes.app.ui.screens.staff

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pinakes.app.R
import com.pinakes.app.data.model.IdName
import com.pinakes.app.data.model.StaffAuthorWrite
import com.pinakes.app.data.model.StaffPublisherWrite
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.ui.common.isOffline
import com.pinakes.app.ui.common.serverMessage
import com.pinakes.app.data.repository.StaffRepository
import com.pinakes.app.data.repository.isDuplicate
import com.pinakes.app.data.repository.isForbidden
import com.pinakes.app.ui.components.ConfirmDialog
import com.pinakes.app.ui.components.EmptyState
import com.pinakes.app.ui.components.ErrorState
import com.pinakes.app.ui.components.InlineErrorBanner
import com.pinakes.app.ui.components.LoadingState
import com.pinakes.app.ui.components.PinakesTextField
import com.pinakes.app.ui.components.PinakesTopBar
import com.pinakes.app.ui.components.PrimaryButton
import com.pinakes.app.ui.components.SearchField
import com.pinakes.app.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Authors and publishers share one list + sheet editor; [RecordKind] selects the fields. */
enum class RecordKind { AUTHOR, PUBLISHER }

/** One row of the record list. */
data class RecordRow(val id: Int, val name: String, val detail: String?)

/**
 * The editor sheet's fields. Authors use name, pseudonym, born, died, nationality, website,
 * biography, GND id; publishers use name, website, address, phone, email.
 */
data class RecordForm(
    val id: Int = 0,
    val name: String = "",
    val pseudonym: String = "",
    val born: String = "",
    val died: String = "",
    val nationality: String = "",
    val website: String = "",
    val biography: String = "",
    val gndId: String = "",
    val address: String = "",
    val phone: String = "",
    val email: String = "",
    /**
     * False while an existing record has not been read from the server (loading, or the load
     * failed). Such a form is a placeholder: saving it would send blank fields and wipe the
     * stored record, so it is never editable or savable.
     */
    val loaded: Boolean = true,
) {
    /** A new record, or an existing one that was loaded; and a name. */
    val canSubmit: Boolean get() = loaded && name.isNotBlank()
}

data class RecordsUiState(
    val kind: RecordKind = RecordKind.AUTHOR,
    val query: String = "",
    val items: List<RecordRow> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val errorOffline: Boolean = false,
    val editing: RecordForm? = null,
    val editLoading: Boolean = false,
    val saving: Boolean = false,
    val formError: String? = null,
    val formErrorRes: Int? = null,
    val duplicate: IdName? = null,
    val snackbarRes: Int? = null,
    val forbidden: Boolean = false,
    /** Bumped after every successful save, so a detail page can reload its record. */
    val savedCount: Int = 0,
)

private fun String.orNullIfBlank(): String? = trim().takeIf { it.isNotEmpty() }

/**
 * Every editable field is always sent, trimmed, and a cleared field as `""`: the server keeps
 * the stored value for an absent key (null keys are dropped by `explicitNulls = false`) and
 * clears it for `""` (dates and the GND id become NULL). Without this a PUT could never empty
 * a field.
 */
internal fun RecordForm.toAuthorWrite(): StaffAuthorWrite = StaffAuthorWrite(
    name = name.trim(), pseudonym = pseudonym.trim(), born = born.trim(), died = died.trim(),
    nationality = nationality.trim(), biography = biography.trim(), website = website.trim(), gndId = gndId.trim(),
)

/** Same contract as [toAuthorWrite]: cleared fields are sent as `""`. */
internal fun RecordForm.toPublisherWrite(): StaffPublisherWrite = StaffPublisherWrite(
    name = name.trim(), website = website.trim(), address = address.trim(), phone = phone.trim(), email = email.trim(),
)

/** Shared logic for the author and publisher screens. */
abstract class RecordsViewModel(protected val staff: StaffRepository, kind: RecordKind) : ViewModel() {
    protected val _state = MutableStateFlow(RecordsUiState(kind = kind))
    val state: StateFlow<RecordsUiState> = _state.asStateFlow()
    private var searchJob: Job? = null
    private var generation = 0

    protected abstract suspend fun search(query: String): ApiResult<List<RecordRow>>
    protected abstract suspend fun fetch(id: Int): ApiResult<RecordForm>
    protected abstract suspend fun save(form: RecordForm): ApiResult<Unit>

    init { viewModelScope.launch { reload() } }

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch { delay(300); reload() }
    }

    fun refresh() {
        _state.update { it.copy(refreshing = true) }
        viewModelScope.launch { reload() }
    }

    protected suspend fun reload() {
        val gen = ++generation
        _state.update { it.copy(loading = it.items.isEmpty() && !it.refreshing, error = null) }
        val res = search(_state.value.query)
        if (gen != generation) return
        _state.update {
            when (res) {
                is ApiResult.Success -> it.copy(items = res.data, loading = false, refreshing = false)
                is ApiResult.Failure -> it.copy(
                    loading = false, refreshing = false, forbidden = res.isForbidden(),
                    error = res.serverMessage() ?: "", errorOffline = res.isOffline,
                )
            }
        }
    }

    fun startNew() = _state.update {
        it.copy(editing = RecordForm(name = it.query.trim()), formError = null, formErrorRes = null, duplicate = null)
    }

    fun startEdit(id: Int) {
        _state.update { it.copy(editing = RecordForm(id = id, loaded = false), editLoading = true, formError = null, formErrorRes = null) }
        viewModelScope.launch {
            when (val res = fetch(id)) {
                is ApiResult.Success -> _state.update {
                    // Ignore a late answer for an editor that was closed or switched meanwhile.
                    if (it.editing?.id == id) it.copy(editing = res.data.copy(loaded = true), editLoading = false) else it
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(editLoading = false, forbidden = res.isForbidden(), formError = res.serverMessage(), formErrorRes = R.string.staff_error_load)
                }
            }
        }
    }

    /** Edits apply only to a loaded form; the placeholder of a failed load stays untouched. */
    fun edit(transform: (RecordForm) -> RecordForm) = _state.update { s ->
        val form = s.editing
        if (form == null || !form.loaded) s else s.copy(editing = transform(form), formError = null, formErrorRes = null)
    }

    fun closeEditor() = _state.update { it.copy(editing = null, duplicate = null) }

    fun submit() {
        val form = _state.value.editing ?: return
        // Never save a form that was not read from the server (it would wipe the record).
        if (!form.loaded) return
        if (form.name.isBlank()) {
            _state.update { it.copy(formErrorRes = R.string.staff_field_required) }
            return
        }
        if (_state.value.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            when (val res = save(form)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(saving = false, editing = null, snackbarRes = R.string.staff_record_saved, savedCount = it.savedCount + 1) }
                    reload()
                }
                is ApiResult.Failure -> _state.update {
                    when {
                        res.isForbidden() -> it.copy(saving = false, forbidden = true)
                        res.isDuplicate() -> it.copy(saving = false, duplicate = staff.existingEntity(res) ?: IdName(0, form.name))
                        else -> it.copy(saving = false, formError = res.serverMessage(), formErrorRes = R.string.staff_error_save)
                    }
                }
            }
        }
    }

    /** "Use existing" on the duplicate dialog: edit the record that already has this name. */
    fun useExisting() {
        val existing = _state.value.duplicate ?: return
        _state.update { it.copy(duplicate = null) }
        if (existing.id > 0) startEdit(existing.id) else closeEditor()
    }

    fun dismissDuplicate() = _state.update { it.copy(duplicate = null) }
    fun consumeSnackbar() = _state.update { it.copy(snackbarRes = null) }
}

@HiltViewModel
class StaffAuthorsViewModel @Inject constructor(staff: StaffRepository) : RecordsViewModel(staff, RecordKind.AUTHOR) {
    override suspend fun search(query: String): ApiResult<List<RecordRow>> = when (val res = staff.authors(query, limit = 50)) {
        is ApiResult.Success -> ApiResult.Success(res.data.map { a ->
            val dates = listOfNotNull(a.born?.orNullIfBlank(), a.died?.orNullIfBlank()).joinToString("–")
            RecordRow(a.id, a.name, listOfNotNull(a.pseudonym?.orNullIfBlank(), dates.orNullIfBlank()).joinToString(" · ").orNullIfBlank())
        })
        is ApiResult.Failure -> res
    }

    override suspend fun fetch(id: Int): ApiResult<RecordForm> = when (val res = staff.author(id)) {
        is ApiResult.Success -> ApiResult.Success(res.data.let { a ->
            RecordForm(
                id = a.id, name = a.name, pseudonym = a.pseudonym.orEmpty(), born = a.born.orEmpty(), died = a.died.orEmpty(),
                nationality = a.nationality.orEmpty(), website = a.website.orEmpty(), biography = a.biography.orEmpty(), gndId = a.gndId.orEmpty(),
            )
        })
        is ApiResult.Failure -> res
    }

    override suspend fun save(form: RecordForm): ApiResult<Unit> {
        return when (val res = staff.saveAuthor(form.id, form.toAuthorWrite())) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Failure -> res
        }
    }
}

@HiltViewModel
class StaffPublishersViewModel @Inject constructor(staff: StaffRepository) : RecordsViewModel(staff, RecordKind.PUBLISHER) {
    override suspend fun search(query: String): ApiResult<List<RecordRow>> = when (val res = staff.publishers(query, limit = 50)) {
        is ApiResult.Success -> ApiResult.Success(res.data.map { p -> RecordRow(p.id, p.name, p.website?.orNullIfBlank()) })
        is ApiResult.Failure -> res
    }

    override suspend fun fetch(id: Int): ApiResult<RecordForm> = when (val res = staff.publisher(id)) {
        is ApiResult.Success -> ApiResult.Success(res.data.let { p ->
            RecordForm(id = p.id, name = p.name, website = p.website.orEmpty(), address = p.address.orEmpty(), phone = p.phone.orEmpty(), email = p.email.orEmpty())
        })
        is ApiResult.Failure -> res
    }

    override suspend fun save(form: RecordForm): ApiResult<Unit> {
        return when (val res = staff.savePublisher(form.id, form.toPublisherWrite())) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Failure -> res
        }
    }
}

@Composable
fun StaffAuthorsScreen(onNavigateUp: () -> Unit, onOpen: (Int) -> Unit) {
    val vm: StaffAuthorsViewModel = hiltViewModel()
    RecordsScreen(vm, stringResource(R.string.staff_authors), Icons.Outlined.Person, onNavigateUp, onOpen)
}

@Composable
fun StaffPublishersScreen(onNavigateUp: () -> Unit, onOpen: (Int) -> Unit) {
    val vm: StaffPublishersViewModel = hiltViewModel()
    RecordsScreen(vm, stringResource(R.string.staff_publishers), Icons.Outlined.Business, onNavigateUp, onOpen)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordsScreen(vm: RecordsViewModel, title: String, icon: ImageVector, onNavigateUp: () -> Unit, onOpen: (Int) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    ForbiddenExit(state.forbidden, onNavigateUp)
    val message = state.snackbarRes?.let { stringResource(it) }
    LaunchedEffect(message) { message?.let { snackbarHost.showSnackbar(it); vm.consumeSnackbar() } }

    Scaffold(
        topBar = { PinakesTopBar(title = title, onNavigateUp = onNavigateUp) },
        floatingActionButton = { StaffFab(Icons.Outlined.Add, stringResource(R.string.staff_record_new), vm::startNew) },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SearchField(
                query = state.query, onQueryChange = vm::onQueryChange,
                placeholder = stringResource(if (state.kind == RecordKind.AUTHOR) R.string.staff_search_authors else R.string.staff_search_publishers),
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            )
            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
                when {
                    state.loading -> LoadingState()
                    state.error != null -> ErrorState(
                        message = if (state.errorOffline) stringResource(R.string.offline_generic)
                        else state.error?.takeIf { it.isNotBlank() } ?: stringResource(R.string.staff_error_load),
                        onRetry = vm::refresh,
                    )
                    state.items.isEmpty() -> EmptyState(
                        title = stringResource(R.string.staff_records_empty), icon = icon,
                        actionLabel = stringResource(R.string.staff_record_new), onAction = vm::startNew,
                    )
                    else -> LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = 96.dp),
                    ) {
                        items(state.items, key = { it.id }) { row ->
                            // Opens the detail page (info card + the record's books); edit is there.
                            StaffActionRow(icon, row.name, row.detail) { onOpen(row.id) }
                        }
                    }
                }
            }
        }
    }

    RecordDialogs(state, vm)
}

/** The editor sheet and the duplicate-name dialog, shared by the list and the detail page. */
@Composable
internal fun RecordDialogs(state: RecordsUiState, vm: RecordsViewModel) {
    if (state.editing != null) RecordEditorSheet(state, vm)
    state.duplicate?.let { existing ->
        ConfirmDialog(
            title = stringResource(R.string.staff_record_duplicate_title),
            body = stringResource(R.string.staff_record_duplicate_body, existing.name),
            confirmLabel = stringResource(R.string.staff_entity_use_existing),
            dismissLabel = stringResource(R.string.action_cancel),
            onConfirm = vm::useExisting,
            onDismiss = vm::dismissDuplicate,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordEditorSheet(state: RecordsUiState, vm: RecordsViewModel) {
    val form = state.editing ?: return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = vm::closeEditor, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                stringResource(if (form.id > 0) R.string.staff_record_edit else R.string.staff_record_new),
                style = MaterialTheme.typography.headlineSmall,
            )
            if (state.editLoading) {
                LoadingState(modifier = Modifier.heightIn(max = 160.dp))
                return@Column
            }
            if (!form.loaded) {
                // The record could not be read: retry only, no fields and no Save.
                InlineErrorBanner(
                    message = state.formError ?: stringResource(state.formErrorRes ?: R.string.staff_error_load),
                    onRetry = { vm.startEdit(form.id) },
                )
                Spacer(Modifier.height(Spacing.lg))
                return@Column
            }
            val errorText = state.formError ?: state.formErrorRes?.let { stringResource(it) }
            if (errorText != null) InlineErrorBanner(message = errorText)
            Field(stringResource(R.string.staff_field_name), form.name) { v -> vm.edit { it.copy(name = v) } }
            if (state.kind == RecordKind.AUTHOR) {
                Field(stringResource(R.string.staff_field_pseudonym), form.pseudonym) { v -> vm.edit { it.copy(pseudonym = v) } }
                Field(stringResource(R.string.staff_field_born), form.born, placeholder = "1882-01-25") { v -> vm.edit { it.copy(born = v) } }
                Field(stringResource(R.string.staff_field_died), form.died, placeholder = "1941-03-28") { v -> vm.edit { it.copy(died = v) } }
                Field(stringResource(R.string.staff_field_nationality), form.nationality) { v -> vm.edit { it.copy(nationality = v) } }
                Field(stringResource(R.string.staff_field_website), form.website, KeyboardType.Uri) { v -> vm.edit { it.copy(website = v) } }
                Field(stringResource(R.string.staff_field_gnd), form.gndId) { v -> vm.edit { it.copy(gndId = v) } }
                PinakesTextField(
                    value = form.biography, onValueChange = { v -> vm.edit { it.copy(biography = v) } },
                    label = stringResource(R.string.staff_field_biography), singleLine = false,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                )
            } else {
                Field(stringResource(R.string.staff_field_website), form.website, KeyboardType.Uri) { v -> vm.edit { it.copy(website = v) } }
                Field(stringResource(R.string.staff_field_address), form.address) { v -> vm.edit { it.copy(address = v) } }
                Field(stringResource(R.string.staff_field_phone), form.phone, KeyboardType.Phone) { v -> vm.edit { it.copy(phone = v) } }
                Field(stringResource(R.string.staff_field_email), form.email, KeyboardType.Email) { v -> vm.edit { it.copy(email = v) } }
            }
            Spacer(Modifier.height(Spacing.sm))
            PrimaryButton(
                label = stringResource(R.string.action_save), onClick = vm::submit, loading = state.saving,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    placeholder: String = "",
    onChange: (String) -> Unit,
) {
    PinakesTextField(
        value = value, onValueChange = onChange, label = label, placeholder = placeholder,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType), modifier = Modifier.fillMaxWidth(),
    )
}
