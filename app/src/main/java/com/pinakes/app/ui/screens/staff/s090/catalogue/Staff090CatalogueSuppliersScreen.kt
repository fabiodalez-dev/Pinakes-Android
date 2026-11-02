package com.pinakes.app.ui.screens.staff.s090.catalogue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pinakes.app.R
import com.pinakes.app.data.model.Supplier
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.repository.Staff090CatalogueRepository
import com.pinakes.app.data.repository.isForbidden
import com.pinakes.app.data.staff.SubmissionKeys
import com.pinakes.app.data.staff.SupplierControls
import com.pinakes.app.data.staff.SupplierField
import com.pinakes.app.data.staff.SupplierForm
import com.pinakes.app.data.staff.SupplierProblem
import com.pinakes.app.ui.common.AppViewModel
import com.pinakes.app.ui.components.ConfirmDialog
import com.pinakes.app.ui.components.InlineErrorBanner
import com.pinakes.app.ui.components.PinakesTextField
import com.pinakes.app.ui.components.PinakesTopBar
import com.pinakes.app.ui.components.PrimaryButton
import com.pinakes.app.ui.components.SearchField
import com.pinakes.app.ui.screens.staff.ForbiddenExit
import com.pinakes.app.ui.screens.staff.LeaveWhen
import com.pinakes.app.ui.screens.staff.StaffFab
import com.pinakes.app.ui.screens.staff.StaffLoadBody
import com.pinakes.app.ui.screens.staff.StaffMessage
import com.pinakes.app.ui.screens.staff.StaffSnackbarEffect
import com.pinakes.app.ui.screens.staff.resolve
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

/** The open create/edit sheet: the record being edited (null = new) and the form. */
data class SupplierSheet(
    val original: Supplier? = null,
    val form: SupplierForm = SupplierForm(),
    /** The server's per-field messages of the last 422 (`meta.fields`). */
    val serverErrors: Map<SupplierField, String> = emptyMap(),
    /** Set after the first save attempt, so untouched empty fields do not start red. */
    val showErrors: Boolean = false,
) {
    val readOnly: Boolean get() = original != null && !original.canEdit
}

data class SuppliersUiState(
    val items: List<Supplier> = emptyList(),
    val canCreate: Boolean = false,
    val query: String = "",
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: StaffMessage? = null,
    val forbidden: Boolean = false,
    val sheet: SupplierSheet? = null,
    val saving: Boolean = false,
    val formError: StaffMessage? = null,
    val confirmDelete: Supplier? = null,
    val deletingId: Int? = null,
    val message: StaffMessage? = null,
)

/**
 * Suppliers (`/staff/suppliers`, module `suppliers`): the list with its book counts, a sheet
 * to create or edit one (the server's field errors under each field) and delete. Create carries
 * an Idempotency-Key kept across retries of the same form.
 */
@HiltViewModel
class Staff090CatalogueSuppliersViewModel @Inject constructor(
    private val repo: Staff090CatalogueRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SuppliersUiState())
    val state: StateFlow<SuppliersUiState> = _state.asStateFlow()
    private val keys = SubmissionKeys()
    private var searchJob: Job? = null
    private var generation = 0

    init { reload() }

    fun reload(debounce: Boolean = false) {
        searchJob?.cancel()
        val gen = ++generation
        val query = _state.value.query
        searchJob = viewModelScope.launch {
            if (debounce) delay(300)
            _state.update { it.copy(loading = it.items.isEmpty() && !it.refreshing, error = null) }
            val res = repo.suppliers(query)
            if (gen != generation) return@launch
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(items = res.data.items, canCreate = res.data.canCreate, loading = false, refreshing = false)
                    is ApiResult.Failure -> it.copy(loading = false, refreshing = false, forbidden = res.isForbidden(), error = res.toCatalogueMessage(R.string.staff_error_load))
                }
            }
        }
    }

    fun refresh() {
        _state.update { it.copy(refreshing = true) }
        reload()
    }

    fun onQuery(value: String) {
        _state.update { it.copy(query = value) }
        reload(debounce = true)
    }

    fun startCreate() = _state.update { it.copy(sheet = SupplierSheet(form = SupplierForm(name = it.query.trim())), formError = null) }

    fun open(supplier: Supplier) = _state.update { it.copy(sheet = SupplierSheet(original = supplier, form = SupplierForm.from(supplier)), formError = null) }

    fun edit(field: SupplierField, value: String) = _state.update { s ->
        s.copy(sheet = s.sheet?.let { it.copy(form = it.form.with(field, value), serverErrors = it.serverErrors - field) }, formError = null)
    }

    fun closeSheet() {
        if (!_state.value.saving) _state.update { it.copy(sheet = null, formError = null) }
    }

    fun save() {
        val sheet = _state.value.sheet ?: return
        if (_state.value.saving || sheet.readOnly) return
        if (!sheet.form.valid) {
            _state.update { it.copy(sheet = sheet.copy(showErrors = true)) }
            return
        }
        val original = sheet.original
        val body = if (original == null) sheet.form.createJson() else sheet.form.patchJson(original)
        if (original != null && body.isEmpty()) {
            _state.update { it.copy(sheet = null) }
            return
        }
        _state.update { it.copy(saving = true, sheet = sheet.copy(showErrors = true), formError = null) }
        viewModelScope.launch {
            val key = keys.keyFor(sheet.form)
            val saved = repo.saveSupplier(original?.id, body, key)
            when (val res = saved.result) {
                is ApiResult.Success -> {
                    keys.reset()
                    _state.update { s ->
                        // A replayed create answers the supplier already listed: replace, never duplicate.
                        val items = (s.items.filterNot { it.id == res.data.id } + res.data).sortedBy { it.name.lowercase() }
                        s.copy(saving = false, sheet = null, items = items, message = StaffMessage(R.string.staff090_catalogue_supplier_saved))
                    }
                }
                is ApiResult.Failure -> {
                    if (res.code == SubmissionKeys.CONFLICT) keys.reset()
                    val fields = saved.fields.mapNotNull { (k, v) -> SupplierForm.fieldOf(k)?.let { it to v } }.toMap()
                    _state.update {
                        it.copy(
                            saving = false,
                            forbidden = res.isForbidden(),
                            sheet = it.sheet?.copy(serverErrors = fields),
                            // A field error is shown under its field; anything else on top.
                            formError = if (fields.isEmpty()) res.toCatalogueMessage(R.string.staff_error_save) else null,
                        )
                    }
                }
            }
        }
    }

    fun askDelete(supplier: Supplier) = _state.update { it.copy(confirmDelete = supplier) }
    fun dismissDelete() = _state.update { it.copy(confirmDelete = null) }

    fun delete() {
        val supplier = _state.value.confirmDelete ?: return
        _state.update { it.copy(confirmDelete = null, deletingId = supplier.id) }
        viewModelScope.launch {
            when (val res = repo.deleteSupplier(supplier.id)) {
                is ApiResult.Success -> _state.update { s ->
                    s.copy(
                        deletingId = null, items = s.items.filterNot { it.id == supplier.id },
                        sheet = s.sheet?.takeIf { it.original?.id != supplier.id },
                        message = StaffMessage(R.string.staff090_catalogue_supplier_deleted, args = listOf(res.data.booksUnlinked)),
                    )
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(deletingId = null, forbidden = res.isForbidden(), message = res.toCatalogueMessage(R.string.staff_error_save))
                }
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Staff090CatalogueSuppliersScreen(onNavigateUp: () -> Unit) {
    val vm: Staff090CatalogueSuppliersViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val app: AppViewModel = hiltViewModel()
    val areas by app.staffAreas.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    ForbiddenExit(state.forbidden, onNavigateUp)
    LeaveWhen(!areas.s090.suppliers, onNavigateUp)
    StaffSnackbarEffect(state.message, snackbar, vm::consumeMessage)

    Scaffold(
        topBar = { PinakesTopBar(title = stringResource(R.string.staff090_suppliers), onNavigateUp = onNavigateUp) },
        floatingActionButton = {
            if (SupplierControls.of(state.canCreate, null).create) {
                StaffFab(Icons.Outlined.Add, stringResource(R.string.staff090_catalogue_supplier_new), vm::startCreate)
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SearchField(
                query = state.query, onQueryChange = vm::onQuery, placeholder = stringResource(R.string.staff090_catalogue_supplier_search),
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm).testTag("supplier-search"),
            )
            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
                StaffLoadBody(
                    loading = state.loading, error = state.error, isEmpty = state.items.isEmpty(),
                    emptyTitle = stringResource(R.string.staff090_catalogue_suppliers_empty), emptyIcon = Icons.Outlined.LocalShipping,
                    onRetry = vm::refresh, emptySubtitle = stringResource(R.string.staff090_suppliers_subtitle),
                ) {
                    val listState = rememberLazyListState()
                    // A supplier created before the first row would land above the top (the list
                    // keeps its first visible row in place): stay at the top while the user is there.
                    val first = state.items.firstOrNull()?.id
                    LaunchedEffect(first) { if (listState.firstVisibleItemIndex <= 1) listState.scrollToItem(0) }
                    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = 96.dp)) {
                        items(state.items, key = { it.id }) { supplier ->
                            SupplierRow(
                                supplier = supplier, deleting = state.deletingId == supplier.id,
                                onOpen = { vm.open(supplier) }, onDelete = { vm.askDelete(supplier) },
                            )
                        }
                    }
                }
            }
        }
    }

    state.sheet?.let { sheet ->
        SupplierSheetContent(
            sheet = sheet, saving = state.saving, error = state.formError,
            onEdit = vm::edit, onSave = vm::save, onDismiss = vm::closeSheet,
        )
    }
    state.confirmDelete?.let { supplier ->
        ConfirmDialog(
            title = stringResource(R.string.staff090_catalogue_supplier_delete),
            body = if (supplier.bookCount > 0) stringResource(R.string.staff090_catalogue_supplier_delete_books, supplier.name, supplier.bookCount)
            else stringResource(R.string.staff090_catalogue_supplier_delete_confirm, supplier.name),
            confirmLabel = stringResource(R.string.staff090_catalogue_supplier_delete),
            onConfirm = vm::delete,
            onDismiss = vm::dismissDelete,
        )
    }
}

@Composable
internal fun SupplierRow(supplier: Supplier, deleting: Boolean, onOpen: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val controls = SupplierControls.of(canCreate = false, supplier = supplier)
    Surface(
        onClick = onOpen,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs).testTag("supplier-${supplier.id}"),
    ) {
        Row(Modifier.padding(start = Spacing.lg, top = Spacing.sm, bottom = Spacing.sm, end = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(supplier.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        stringResource(R.string.staff090_catalogue_supplier_books, supplier.bookCount),
                        supplier.contactPerson, supplier.email ?: supplier.phone,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            if (controls.edit || controls.delete) {
                Box {
                    IconButton(onClick = { menu = true }, enabled = !deleting) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.cd_more_options))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (controls.edit) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.staff090_catalogue_supplier_edit)) }, onClick = { menu = false; onOpen() })
                        }
                        if (controls.delete) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.staff090_catalogue_supplier_delete), color = MaterialTheme.colorScheme.error) },
                                onClick = { menu = false; onDelete() },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SupplierSheetContent(
    sheet: SupplierSheet,
    saving: Boolean,
    error: StaffMessage?,
    onEdit: (SupplierField, String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val problems = if (sheet.showErrors) sheet.form.validate() else emptyMap()
    ModalBottomSheet(onDismissRequest = { if (!saving) onDismiss() }, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                stringResource(
                    when {
                        sheet.original == null -> R.string.staff090_catalogue_supplier_new
                        sheet.readOnly -> R.string.staff090_catalogue_supplier
                        else -> R.string.staff090_catalogue_supplier_edit
                    },
                ),
                style = MaterialTheme.typography.headlineSmall,
            )
            sheet.original?.let { Text(stringResource(R.string.staff090_catalogue_supplier_books, it.bookCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            error?.let { InlineErrorBanner(message = it.resolve()) }
            SupplierField.entries.forEach { field ->
                val problem = problems[field]
                val server = sheet.serverErrors[field]
                PinakesTextField(
                    value = sheet.form.value(field), onValueChange = { onEdit(field, it) },
                    label = stringResource(fieldLabel(field)),
                    isError = problem != null || server != null,
                    errorText = server ?: stringResource(if (problem == SupplierProblem.REQUIRED) R.string.staff_field_required else R.string.staff_field_invalid),
                    maxLength = field.maxLength, enabled = !sheet.readOnly,
                    singleLine = field != SupplierField.ADDRESS && field != SupplierField.NOTES,
                    keyboardOptions = when (field) {
                        SupplierField.EMAIL -> KeyboardOptions(keyboardType = KeyboardType.Email)
                        SupplierField.PHONE -> KeyboardOptions(keyboardType = KeyboardType.Phone)
                        SupplierField.WEBSITE -> KeyboardOptions(keyboardType = KeyboardType.Uri)
                        else -> KeyboardOptions.Default
                    },
                    modifier = Modifier.fillMaxWidth().testTag("supplier-field-${field.key}")
                        .then(if (field == SupplierField.NOTES || field == SupplierField.ADDRESS) Modifier.heightIn(min = 96.dp) else Modifier),
                )
            }
            if (!sheet.readOnly) {
                PrimaryButton(stringResource(R.string.action_save), onClick = onSave, loading = saving, modifier = Modifier.fillMaxWidth().testTag("supplier-save"))
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

private fun fieldLabel(field: SupplierField): Int = when (field) {
    SupplierField.NAME -> R.string.staff_field_name
    SupplierField.CONTACT_PERSON -> R.string.staff090_catalogue_supplier_contact
    SupplierField.EMAIL -> R.string.staff_field_email
    SupplierField.PHONE -> R.string.staff_field_phone
    SupplierField.WEBSITE -> R.string.staff_field_website
    SupplierField.ADDRESS -> R.string.staff_field_address
    SupplierField.VAT_NUMBER -> R.string.staff090_catalogue_supplier_vat
    SupplierField.NOTES -> R.string.staff_field_notes
}
