package com.pinakes.app.ui.screens.staff.tier2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pinakes.app.R
import com.pinakes.app.data.model.LookupOption
import com.pinakes.app.data.model.StaffWork
import com.pinakes.app.data.model.WorkExpression
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.repository.StaffRepository
import com.pinakes.app.data.repository.StaffTier2Repository
import com.pinakes.app.data.repository.isForbidden
import com.pinakes.app.data.staff.ExpressionForm
import com.pinakes.app.data.staff.WorkForm
import com.pinakes.app.ui.common.AppViewModel
import com.pinakes.app.ui.components.ConfirmDialog
import com.pinakes.app.ui.components.ErrorState
import com.pinakes.app.ui.components.LoadingState
import com.pinakes.app.ui.components.PinakesTextField
import com.pinakes.app.ui.components.PinakesTopBar
import com.pinakes.app.ui.components.SearchField
import com.pinakes.app.ui.components.SecondaryButton
import com.pinakes.app.ui.navigation.Routes
import com.pinakes.app.ui.screens.staff.ForbiddenExit
import com.pinakes.app.ui.screens.staff.FormSectionTitle
import com.pinakes.app.ui.screens.staff.InfoCard
import com.pinakes.app.ui.screens.staff.InfoLine
import com.pinakes.app.ui.screens.staff.LeaveWhen
import com.pinakes.app.ui.screens.staff.LookupField
import com.pinakes.app.ui.screens.staff.PickItem
import com.pinakes.app.ui.screens.staff.PickedField
import com.pinakes.app.ui.screens.staff.SearchController
import com.pinakes.app.ui.screens.staff.SearchPickerSheet
import com.pinakes.app.ui.screens.staff.StaffActionRow
import com.pinakes.app.ui.screens.staff.StaffFab
import com.pinakes.app.ui.screens.staff.StaffLoadBody
import com.pinakes.app.ui.screens.staff.StaffMessage
import com.pinakes.app.ui.screens.staff.StaffSnackbarEffect
import com.pinakes.app.ui.screens.staff.resolve
import com.pinakes.app.ui.screens.staff.toStaffMessage
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

internal fun expressionTypeLabel(type: String?): Int = when (type) {
    "traduzione" -> R.string.staff_expression_translation
    "revisione" -> R.string.staff_expression_revision
    "adattamento" -> R.string.staff_expression_adaptation
    "edizione_critica" -> R.string.staff_expression_critical
    "audio" -> R.string.staff_expression_audio
    "altro" -> R.string.staff_expression_other
    else -> R.string.staff_expression_text
}

/** Work form fields shared by create (list) and edit (detail). */
@Composable
private fun WorkFormSheet(title: String, form: WorkForm, error: StaffMessage?, saving: Boolean, onEdit: ((WorkForm) -> WorkForm) -> Unit,
                          onPickAuthor: () -> Unit, onSave: () -> Unit, onDismiss: () -> Unit) {
    FormSheet(title, error, saving, enabled = form.valid, onSave = onSave, onDismiss = onDismiss) {
        PinakesTextField(form.uniformTitle, { v -> onEdit { it.copy(uniformTitle = v) } }, stringResource(R.string.staff_work_uniform_title), maxLength = 255,
            modifier = Modifier.fillMaxWidth().testTag("work-title"))
        PinakesTextField(form.originalTitle, { v -> onEdit { it.copy(originalTitle = v) } }, stringResource(R.string.staff_work_original_title), modifier = Modifier.fillMaxWidth())
        PickedField(stringResource(R.string.staff_work_main_author), form.mainAuthorName.ifBlank { null }, stringResource(R.string.staff_search_authors), onClick = onPickAuthor,
            onClear = { onEdit { it.copy(mainAuthorId = null, mainAuthorName = "") } })
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            PinakesTextField(form.createdFrom, { v -> onEdit { it.copy(createdFrom = v.filter { c -> c.isDigit() || c == '-' }.take(5)) } }, stringResource(R.string.staff_work_created_from),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
            PinakesTextField(form.createdTo, { v -> onEdit { it.copy(createdTo = v.filter { c -> c.isDigit() || c == '-' }.take(5)) } }, stringResource(R.string.staff_work_created_to),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
        }
        PinakesTextField(form.originalLanguage, { v -> onEdit { it.copy(originalLanguage = v) } }, stringResource(R.string.staff_work_language), modifier = Modifier.fillMaxWidth())
        PinakesTextField(form.viafWorkId, { v -> onEdit { it.copy(viafWorkId = v) } }, "VIAF", modifier = Modifier.fillMaxWidth())
        PinakesTextField(form.wikidataId, { v -> onEdit { it.copy(wikidataId = v) } }, "Wikidata", modifier = Modifier.fillMaxWidth())
        PinakesTextField(form.notes, { v -> onEdit { it.copy(notes = v) } }, stringResource(R.string.staff_field_notes), singleLine = false, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp))
    }
}

// =============================================================================================
// List
// =============================================================================================

data class WorksUiState(
    val query: String = "",
    val works: List<StaffWork> = emptyList(),
    val loading: Boolean = true,
    val error: StaffMessage? = null,
    val creating: WorkForm? = null,
    val pickingAuthor: Boolean = false,
    val saving: Boolean = false,
    val formError: StaffMessage? = null,
    val created: Int? = null,
    val forbidden: Boolean = false,
)

@HiltViewModel
class StaffWorksViewModel @Inject constructor(private val tier2: StaffTier2Repository, private val staff: StaffRepository) : ViewModel() {
    private val _state = MutableStateFlow(WorksUiState())
    val state: StateFlow<WorksUiState> = _state.asStateFlow()
    private var job: Job? = null

    val authors = SearchController(viewModelScope, minLength = 2) { q ->
        when (val res = staff.authors(q, 20)) {
            is ApiResult.Success -> ApiResult.Success(res.data.map { PickItem(it.id, it.display) })
            is ApiResult.Failure -> res
        }
    }

    fun reload() {
        job?.cancel()
        job = viewModelScope.launch {
            if (_state.value.works.isNotEmpty()) delay(300)
            val res = tier2.works(_state.value.query)
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(works = res.data, loading = false, error = null)
                    is ApiResult.Failure -> it.copy(loading = false, forbidden = res.isForbidden(), error = res.toStaffMessage(R.string.staff_error_load))
                }
            }
        }
    }

    fun onQuery(value: String) { _state.update { it.copy(query = value) }; reload() }
    fun startCreate() = _state.update { it.copy(creating = WorkForm(uniformTitle = it.query.trim()), formError = null) }
    fun edit(transform: (WorkForm) -> WorkForm) = _state.update { s -> s.copy(creating = s.creating?.let(transform), formError = null) }
    fun pickAuthor(open: Boolean) { _state.update { it.copy(pickingAuthor = open) }; if (!open) authors.clear() }
    fun setAuthor(item: PickItem) { edit { it.copy(mainAuthorId = item.id, mainAuthorName = item.title) }; pickAuthor(false) }
    fun close() = _state.update { it.copy(creating = null) }

    fun create() {
        val form = _state.value.creating ?: return
        if (!form.valid || _state.value.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val res = tier2.saveWork(null, form.toJson())
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(saving = false, creating = null, created = res.data.id)
                    is ApiResult.Failure -> it.copy(saving = false, forbidden = res.isForbidden(), formError = res.toStaffMessage(R.string.staff_error_save))
                }
            }
        }
    }

    fun consumeCreated() = _state.update { it.copy(created = null) }
}

/** FRBR works (the abstract work behind several editions), searchable, with a create sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffWorksScreen(onNavigateUp: () -> Unit, onOpen: (Int) -> Unit) {
    val vm: StaffWorksViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val app: AppViewModel = hiltViewModel()
    val areas by app.staffAreas.collectAsStateWithLifecycle()
    ForbiddenExit(state.forbidden, onNavigateUp)
    LeaveWhen(!areas.works, onNavigateUp)
    LaunchedEffect(Unit) { vm.reload() }
    LaunchedEffect(state.created) { state.created?.let { vm.consumeCreated(); onOpen(it) } }

    Scaffold(
        topBar = { PinakesTopBar(title = stringResource(R.string.staff_works), onNavigateUp = onNavigateUp) },
        floatingActionButton = { StaffFab(Icons.Outlined.Add, stringResource(R.string.staff_work_new), vm::startCreate) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SearchField(query = state.query, onQueryChange = vm::onQuery, placeholder = stringResource(R.string.staff_works_search),
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm))
            StaffLoadBody(
                loading = state.loading, error = state.error, isEmpty = state.works.isEmpty(),
                emptyTitle = stringResource(R.string.staff_works_empty), emptyIcon = Icons.Outlined.AccountTree, onRetry = vm::reload,
            ) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = 96.dp)) {
                    items(state.works, key = { it.id }) { w ->
                        StaffActionRow(Icons.Outlined.AccountTree, w.uniformTitle.orEmpty(),
                            listOfNotNull(w.mainAuthorName, w.editionsCount?.let { stringResource(R.string.staff_work_editions_count, it) }).joinToString(" · ").ifBlank { null }) { onOpen(w.id) }
                    }
                }
            }
        }
    }
    state.creating?.let { form ->
        WorkFormSheet(stringResource(R.string.staff_work_new), form, state.formError, state.saving, vm::edit, { vm.pickAuthor(true) }, vm::create, vm::close)
    }
    if (state.pickingAuthor) {
        val s by vm.authors.state.collectAsStateWithLifecycle()
        SearchPickerSheet(stringResource(R.string.staff_work_main_author), stringResource(R.string.staff_search_authors), s, vm.authors::onQuery, vm::setAuthor, { vm.pickAuthor(false) })
    }
}

// =============================================================================================
// Detail
// =============================================================================================

enum class WorkPicker { AUTHOR, BOOK }

data class WorkUiState(
    val work: StaffWork? = null,
    val loading: Boolean = true,
    val loadError: StaffMessage? = null,
    val editing: WorkForm? = null,
    val expression: ExpressionForm? = null,
    val picker: WorkPicker? = null,
    val confirmDelete: Boolean = false,
    val busy: Boolean = false,
    val formError: StaffMessage? = null,
    val message: StaffMessage? = null,
    val deleted: Boolean = false,
    val forbidden: Boolean = false,
)

@HiltViewModel
class StaffWorkViewModel @Inject constructor(
    private val tier2: StaffTier2Repository,
    private val staff: StaffRepository,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val workId: Int = savedState.get<Int>(Routes.ARG_WORK_ID) ?: 0
    private val _state = MutableStateFlow(WorkUiState())
    val state: StateFlow<WorkUiState> = _state.asStateFlow()

    val search = SearchController(viewModelScope, minLength = 2) { q ->
        if (_state.value.picker == WorkPicker.BOOK) {
            when (val res = staff.books(q, cursor = null, limit = 20)) {
                is ApiResult.Success -> ApiResult.Success(res.data.items.map { PickItem(it.id, it.title, it.author) })
                is ApiResult.Failure -> res
            }
        } else {
            when (val res = staff.authors(q, 20)) {
                is ApiResult.Success -> ApiResult.Success(res.data.map { PickItem(it.id, it.display) })
                is ApiResult.Failure -> res
            }
        }
    }.also { c -> c.onFailure = { f -> if (f.isForbidden()) _state.update { it.copy(forbidden = true) } } }

    init { load() }

    fun load() {
        viewModelScope.launch {
            val res = tier2.work(workId)
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(work = res.data, loading = false, loadError = null)
                    is ApiResult.Failure -> it.copy(loading = false, forbidden = res.isForbidden(), loadError = res.toStaffMessage(R.string.staff_error_load))
                }
            }
        }
    }

    fun startEdit() = _state.update { s -> s.copy(editing = s.work?.let(WorkForm::from), formError = null) }
    fun editForm(transform: (WorkForm) -> WorkForm) = _state.update { s -> s.copy(editing = s.editing?.let(transform), formError = null) }
    fun startExpression(e: WorkExpression?) = _state.update { it.copy(expression = e?.let(ExpressionForm::from) ?: ExpressionForm(), formError = null) }
    fun editExpression(transform: (ExpressionForm) -> ExpressionForm) = _state.update { s -> s.copy(expression = s.expression?.let(transform), formError = null) }
    fun openPicker(kind: WorkPicker?) { _state.update { it.copy(picker = kind) }; if (kind == null) search.clear() }
    fun closeForms() = _state.update { it.copy(editing = null, expression = null, formError = null) }

    fun picked(item: PickItem) {
        when (_state.value.picker) {
            WorkPicker.AUTHOR -> editForm { it.copy(mainAuthorId = item.id, mainAuthorName = item.title) }
            WorkPicker.BOOK -> attach(item.id, workId)
            null -> Unit
        }
        openPicker(null)
    }

    fun saveWork() {
        val form = _state.value.editing ?: return
        if (!form.valid) return
        run(R.string.staff_work_saved) {
            when (val res = tier2.saveWork(workId, form.toJson())) {
                is ApiResult.Success -> { _state.update { it.copy(work = res.data, editing = null) }; null }
                is ApiResult.Failure -> res
            }
        }
    }

    fun saveExpression() {
        val form = _state.value.expression ?: return
        if (!form.valid) return
        run(R.string.staff_expression_saved) {
            when (val res = tier2.saveExpression(workId, form.id, form.toJson())) {
                is ApiResult.Success -> { _state.update { it.copy(expression = null) }; reloadWork(); null }
                is ApiResult.Failure -> res
            }
        }
    }

    fun deleteExpression(e: WorkExpression) = run(R.string.staff_expression_deleted) {
        when (val res = tier2.deleteExpression(e.id)) {
            is ApiResult.Success -> { reloadWork(); null }
            is ApiResult.Failure -> res
        }
    }

    /** Link a book to this work, or unlink it (null). */
    fun attach(bookId: Int, target: Int?) = run(if (target == null) R.string.staff_work_book_detached else R.string.staff_work_book_attached) {
        when (val res = tier2.attachBook(bookId, target)) {
            is ApiResult.Success -> { reloadWork(); null }
            is ApiResult.Failure -> res
        }
    }

    private suspend fun reloadWork() {
        (tier2.work(workId) as? ApiResult.Success)?.data?.let { w -> _state.update { it.copy(work = w) } }
    }

    private fun run(success: Int, block: suspend () -> ApiResult.Failure?) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, formError = null) }
        viewModelScope.launch {
            val failure = block()
            _state.update {
                if (failure == null) it.copy(busy = false, message = StaffMessage(success))
                else it.copy(busy = false, forbidden = failure.isForbidden(), formError = failure.toStaffMessage(R.string.staff_error_save),
                    message = if (it.editing == null && it.expression == null) failure.toStaffMessage(R.string.staff_error_save) else null)
            }
        }
    }

    fun askDelete() = _state.update { it.copy(confirmDelete = true) }
    fun dismissDelete() = _state.update { it.copy(confirmDelete = false) }

    fun delete() {
        _state.update { it.copy(confirmDelete = false) }
        viewModelScope.launch {
            when (val res = tier2.deleteWork(workId)) {
                is ApiResult.Success -> _state.update { it.copy(deleted = true) }
                is ApiResult.Failure -> _state.update { it.copy(forbidden = res.isForbidden(), message = res.toStaffMessage(R.string.staff_error_save)) }
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}

/** One work: its data, its expressions (translations, editions…) and the books linked to it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffWorkScreen(onNavigateUp: () -> Unit, onOpenBook: (Int) -> Unit) {
    val vm: StaffWorkViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val app: AppViewModel = hiltViewModel()
    val areas by app.staffAreas.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    ForbiddenExit(state.forbidden, onNavigateUp)
    LeaveWhen(!areas.works || state.deleted, onNavigateUp)
    StaffSnackbarEffect(state.message, snackbar, vm::consumeMessage)

    Scaffold(
        topBar = {
            PinakesTopBar(
                title = state.work?.uniformTitle ?: stringResource(R.string.staff_works), onNavigateUp = onNavigateUp,
                actions = { if (state.work != null) IconButton(onClick = vm::askDelete) { Icon(Icons.Outlined.DeleteOutline, contentDescription = stringResource(R.string.staff_work_delete)) } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val w = state.work
        when {
            state.loading -> LoadingState(modifier = Modifier.padding(padding))
            w == null -> ErrorState(message = state.loadError?.resolve() ?: stringResource(R.string.staff_error_load), onRetry = vm::load, modifier = Modifier.padding(padding))
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = 48.dp)) {
                item(key = "facts") {
                    InfoCard(Modifier.padding(top = Spacing.sm)) {
                        InfoLine(stringResource(R.string.staff_work_original_title), w.originalTitle)
                        InfoLine(stringResource(R.string.staff_work_main_author), w.mainAuthorName)
                        InfoLine(stringResource(R.string.staff_work_created), listOfNotNull(w.createdFrom, w.createdTo).distinct().joinToString("–").ifBlank { null })
                        InfoLine(stringResource(R.string.staff_work_language), w.originalLanguage)
                        InfoLine("VIAF", w.viafWorkId)
                        InfoLine("Wikidata", w.wikidataId)
                        InfoLine(stringResource(R.string.staff_field_notes), w.notes)
                    }
                    SecondaryButton(stringResource(R.string.staff_work_edit), onClick = vm::startEdit, modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm))
                }
                item(key = "expr-title") { FormSectionTitle(stringResource(R.string.staff_work_expressions)) }
                items(w.expressions, key = { "e${it.id}" }) { e ->
                    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xs).testTag("expression-${e.id}"), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(e.title?.takeIf { it.isNotBlank() } ?: stringResource(expressionTypeLabel(e.type)), style = MaterialTheme.typography.bodyLarge)
                            Text(listOfNotNull(stringResource(expressionTypeLabel(e.type)), e.language, e.year?.toString()).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { vm.startExpression(e) }) { Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = stringResource(R.string.staff_expression_edit)) }
                        IconButton(onClick = { vm.deleteExpression(e) }, enabled = !state.busy) { Icon(Icons.Outlined.DeleteOutline, contentDescription = stringResource(R.string.staff_expression_delete)) }
                    }
                }
                item(key = "expr-add") { SecondaryButton(stringResource(R.string.staff_expression_add), onClick = { vm.startExpression(null) }, leadingIcon = Icons.Outlined.Add, modifier = Modifier.fillMaxWidth()) }
                item(key = "books-title") { FormSectionTitle(stringResource(R.string.staff_work_books)) }
                items(w.editions, key = { "b${it.id}" }) { b ->
                    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xs).testTag("edition-${b.id}"), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(b.title, style = MaterialTheme.typography.bodyLarge)
                            Text(listOfNotNull(b.year?.toString(), b.isbn13).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onOpenBook(b.id) }) { Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = stringResource(R.string.staff_open_book)) }
                        IconButton(onClick = { vm.attach(b.id, null) }, enabled = !state.busy) { Icon(Icons.Outlined.LinkOff, contentDescription = stringResource(R.string.staff_work_detach)) }
                    }
                }
                item(key = "attach") {
                    SecondaryButton(stringResource(R.string.staff_work_attach), onClick = { vm.openPicker(WorkPicker.BOOK) }, leadingIcon = Icons.Outlined.Add, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

    state.editing?.let { form ->
        WorkFormSheet(stringResource(R.string.staff_work_edit), form, state.formError, state.busy, vm::editForm, { vm.openPicker(WorkPicker.AUTHOR) }, vm::saveWork, vm::closeForms)
    }
    state.expression?.let { form ->
        FormSheet(stringResource(if (form.id > 0) R.string.staff_expression_edit else R.string.staff_expression_add), state.formError, state.busy,
            enabled = form.valid, onSave = vm::saveExpression, onDismiss = vm::closeForms) {
            LookupField(
                label = stringResource(R.string.staff_expression_type), value = form.type, displayValue = stringResource(expressionTypeLabel(form.type)),
                options = ExpressionForm.TYPES.map { LookupOption(it, stringResource(expressionTypeLabel(it))) }, editable = false, allowClear = false,
                onValueChange = { v -> vm.editExpression { it.copy(type = v) } }, modifier = Modifier.fillMaxWidth(),
            )
            PinakesTextField(form.title, { v -> vm.editExpression { it.copy(title = v) } }, stringResource(R.string.staff_field_title_plain), modifier = Modifier.fillMaxWidth().testTag("expression-title"))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                PinakesTextField(form.language, { v -> vm.editExpression { it.copy(language = v) } }, stringResource(R.string.staff_work_language), modifier = Modifier.weight(2f))
                PinakesTextField(form.year, { v -> vm.editExpression { it.copy(year = v.filter(Char::isDigit).take(4)) } }, stringResource(R.string.staff_field_year),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
            }
            PinakesTextField(form.notes, { v -> vm.editExpression { it.copy(notes = v) } }, stringResource(R.string.staff_field_notes), singleLine = false, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp))
        }
    }
    state.picker?.let { kind ->
        val s by vm.search.state.collectAsStateWithLifecycle()
        SearchPickerSheet(
            title = stringResource(if (kind == WorkPicker.BOOK) R.string.staff_work_attach else R.string.staff_work_main_author),
            placeholder = stringResource(if (kind == WorkPicker.BOOK) R.string.staff_books_search else R.string.staff_search_authors),
            state = s, onQuery = vm.search::onQuery, onPick = vm::picked, onDismiss = { vm.openPicker(null) }, hint = stringResource(R.string.staff_search_min_chars),
        )
    }
    if (state.confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.staff_work_delete), body = stringResource(R.string.staff_work_delete_body, state.work?.uniformTitle.orEmpty()),
            confirmLabel = stringResource(R.string.staff_work_delete), onConfirm = vm::delete, onDismiss = vm::dismissDelete,
        )
    }
}
