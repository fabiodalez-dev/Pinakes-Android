package com.pinakes.app.ui.screens.staff.s090.library

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.GridOn
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pinakes.app.R
import com.pinakes.app.data.model.Staff090LibraryPatronType
import com.pinakes.app.data.model.Staff090LibraryTypes
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.repository.Staff090LibraryRepository
import com.pinakes.app.data.staff.SubmissionKeys
import com.pinakes.app.ui.common.AppLocale
import com.pinakes.app.ui.common.AppViewModel
import com.pinakes.app.ui.components.AvailabilityChip
import com.pinakes.app.ui.components.AvailabilityStatus
import com.pinakes.app.ui.components.ConfirmDialog
import com.pinakes.app.ui.components.InlineErrorBanner
import com.pinakes.app.ui.components.PinakesTextField
import com.pinakes.app.ui.components.PinakesTopBar
import com.pinakes.app.ui.components.PrimaryButton
import com.pinakes.app.ui.screens.staff.FormSectionTitle
import com.pinakes.app.ui.screens.staff.InfoCard
import com.pinakes.app.ui.screens.staff.LeaveWhen
import com.pinakes.app.ui.screens.staff.StaffActionRow
import com.pinakes.app.ui.screens.staff.StaffFab
import com.pinakes.app.ui.screens.staff.StaffLoadBody
import com.pinakes.app.ui.screens.staff.StaffMessage
import com.pinakes.app.ui.screens.staff.StaffSnackbarEffect
import com.pinakes.app.ui.screens.staff.SwitchRow
import com.pinakes.app.ui.screens.staff.resolve
import com.pinakes.app.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PatronTypesUiState(
    val data: Staff090LibraryTypes? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: StaffMessage? = null,
    val exit: StaffMessage? = null,
    /** The sheet: a new type (id null) or an edit, with the values it started from. */
    val form: TypeForm? = null,
    val original: TypeForm? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
    val formError: StaffMessage? = null,
    val saving: Boolean = false,
    val busy: Boolean = false,
    val confirmDelete: Staff090LibraryPatronType? = null,
    val message: StaffMessage? = null,
) {
    val editing: Staff090LibraryPatronType? get() = form?.id?.let { id -> data?.types?.firstOrNull { it.id == id } }
    val invalid: Set<String> get() = form?.let { TypeForms.invalid(it, data?.limits.orEmpty()) }.orEmpty()
    val canSave: Boolean get() = form != null && invalid.isEmpty() && (form.id == null || original?.let { TypeForms.updateBody(it, form) } != null)
}

@HiltViewModel
class PatronTypesViewModel @Inject constructor(
    private val repo: Staff090LibraryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PatronTypesUiState())
    val state: StateFlow<PatronTypesUiState> = _state.asStateFlow()
    private val keys = SubmissionKeys()

    init { load() }

    fun load() {
        _state.update { it.copy(loading = it.data == null && !it.refreshing, error = null) }
        viewModelScope.launch {
            val res = repo.types()
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(data = res.data, loading = false, refreshing = false)
                    is ApiResult.Failure -> it.copy(loading = false, refreshing = false, exit = res.exitMessage(), error = res.toLibraryMessage(R.string.staff_error_load))
                }
            }
        }
    }

    fun refresh() {
        _state.update { it.copy(refreshing = true) }
        load()
    }

    fun startCreate() = _state.update { it.copy(form = TypeForm(), original = null, fieldErrors = emptyMap(), formError = null) }

    fun startEdit(t: Staff090LibraryPatronType) {
        val form = TypeForm.from(t, AppLocale.current())
        _state.update { it.copy(form = form, original = form, fieldErrors = emptyMap(), formError = null) }
    }

    fun edit(transform: (TypeForm) -> TypeForm) = _state.update { s -> s.copy(form = s.form?.let(transform), formError = null) }
    fun closeForm() = _state.update { it.copy(form = null, original = null, fieldErrors = emptyMap()) }

    fun save() {
        val s = _state.value
        val form = s.form ?: return
        if (s.saving || !s.canSave) return
        _state.update { it.copy(saving = true, formError = null, fieldErrors = emptyMap()) }
        viewModelScope.launch {
            val saved = if (form.id == null) {
                val body = TypeForms.createBody(form)
                repo.createType(body, keys.keyFor(body)).also { r ->
                    if (r.result is ApiResult.Success || (r.result as? ApiResult.Failure)?.code == SubmissionKeys.CONFLICT) keys.reset()
                }
            } else {
                repo.updateType(form.id, TypeForms.updateBody(s.original ?: form, form) ?: return@launch)
            }
            when (val res = saved.result) {
                is ApiResult.Success -> {
                    _state.update { it.copy(saving = false, form = null, original = null, message = StaffMessage(R.string.staff090_library_saved)) }
                    // A new default or a changed name affects the whole list (badges, order): reload it.
                    load()
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(saving = false, fieldErrors = saved.fields, formError = res.toLibraryMessage(R.string.staff_error_save))
                }
            }
        }
    }

    fun askDelete(t: Staff090LibraryPatronType) = _state.update { it.copy(confirmDelete = t) }
    fun dismissDelete() = _state.update { it.copy(confirmDelete = null) }

    fun delete() {
        val t = _state.value.confirmDelete ?: return
        _state.update { it.copy(confirmDelete = null, busy = true) }
        viewModelScope.launch {
            val res = repo.deleteType(t.id)
            when (res) {
                is ApiResult.Success -> {
                    _state.update { it.copy(busy = false, message = StaffMessage(R.string.staff090_library_type_deleted, args = listOf(t.name, res.data.usersReassigned))) }
                    load()
                }
                // The default type: the server's own explanation (it names what to do first).
                is ApiResult.Failure -> _state.update { it.copy(busy = false, message = res.toLibraryMessage(R.string.staff_error_save)) }
            }
        }
    }

    fun setDefault(t: Staff090LibraryPatronType) = action { repo.setDefaultType(t.id).let { r -> if (r is ApiResult.Success) ApiResult.Success(Unit) else r as ApiResult.Failure } }

    fun move(t: Staff090LibraryPatronType, up: Boolean) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val res = repo.moveType(t.id, up)
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(busy = false, data = res.data)
                    is ApiResult.Failure -> it.copy(busy = false, message = res.toLibraryMessage(R.string.staff_error_save))
                }
            }
        }
    }

    private fun action(call: suspend () -> ApiResult<Unit>) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val res = call()
            when (res) {
                is ApiResult.Success -> {
                    _state.update { it.copy(busy = false, message = StaffMessage(R.string.staff090_library_saved)) }
                    load()
                }
                is ApiResult.Failure -> _state.update { it.copy(busy = false, message = res.toLibraryMessage(R.string.staff_error_save)) }
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}

/** Settings → Circulation: patron types (list, sheet, default, order) and the door to the rules matrix. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatronTypesScreen(onNavigateUp: () -> Unit, onOpenMatrix: () -> Unit) {
    val vm: PatronTypesViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val app: AppViewModel = hiltViewModel()
    val areas by app.staffAreas.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LeaveWhen(!areas.s090.circulationRules, onNavigateUp)
    LibraryExit(state.exit, onNavigateUp)
    StaffSnackbarEffect(state.message, snackbar, vm::consumeMessage)
    val data = state.data

    Scaffold(
        topBar = { PinakesTopBar(title = stringResource(R.string.staff090_circulation_rules), onNavigateUp = onNavigateUp) },
        floatingActionButton = { if (data?.canConfigure == true) StaffFab(Icons.Outlined.Add, stringResource(R.string.staff090_library_type_new), vm::startCreate) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize().padding(padding)) {
            StaffLoadBody(
                loading = state.loading, error = state.error, isEmpty = false,
                emptyTitle = "", emptyIcon = Icons.Outlined.Badge, onRetry = vm::refresh,
            ) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = 96.dp)) {
                    if (data != null && !data.moduleEnabled) {
                        item(key = "off") {
                            InfoCard(Modifier.padding(top = Spacing.sm)) {
                                Text(stringResource(R.string.staff090_library_rules_module_off), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    item(key = "matrix") {
                        StaffActionRow(Icons.Outlined.GridOn, stringResource(R.string.staff090_library_matrix), stringResource(R.string.staff090_library_matrix_subtitle), onOpenMatrix)
                    }
                    item(key = "types-title") { FormSectionTitle(stringResource(R.string.staff090_library_types)) }
                    val types = data?.types.orEmpty()
                    items(types, key = { it.id }) { t ->
                        TypeRow(
                            t, first = t == types.first(), last = t == types.last(), busy = state.busy, canConfigure = data?.canConfigure == true,
                            onOpen = { vm.startEdit(t) }, onDefault = { vm.setDefault(t) }, onMove = { up -> vm.move(t, up) }, onDelete = { vm.askDelete(t) },
                        )
                    }
                }
            }
        }
    }

    if (data != null) {
        state.form?.let { form -> TypeSheet(form, state, data, vm) }
    }
    state.confirmDelete?.let { t ->
        ConfirmDialog(
            title = stringResource(R.string.staff090_library_type_delete),
            body = stringResource(R.string.staff090_library_type_delete_body, t.name, t.userCount),
            confirmLabel = stringResource(R.string.staff090_library_delete),
            onConfirm = vm::delete, onDismiss = vm::dismissDelete,
        )
    }
}

@Composable
private fun TypeRow(
    t: Staff090LibraryPatronType,
    first: Boolean,
    last: Boolean,
    busy: Boolean,
    canConfigure: Boolean,
    onOpen: () -> Unit,
    onDefault: () -> Unit,
    onMove: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Surface(
        onClick = onOpen, enabled = canConfigure,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs).testTag("type-${t.id}"),
    ) {
        Row(Modifier.padding(start = Spacing.lg, top = Spacing.sm, bottom = Spacing.sm, end = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(t.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(t.code, stringResource(R.string.staff090_library_type_users, t.userCount)).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (t.isDefault || !t.active) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        if (t.isDefault) AvailabilityChip(AvailabilityStatus.ReservedReady, stringResource(R.string.staff090_library_type_default))
                        if (!t.active) AvailabilityChip(AvailabilityStatus.Unavailable, stringResource(R.string.staff090_library_type_inactive))
                    }
                }
            }
            if (canConfigure) {
                IconButton(onClick = { menu = true }, enabled = !busy, modifier = Modifier.testTag("type-menu-${t.id}")) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.staff_more_actions))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.staff090_library_type_edit)) }, onClick = { menu = false; onOpen() })
                    if (!t.isDefault) DropdownMenuItem(text = { Text(stringResource(R.string.staff090_library_type_make_default)) }, onClick = { menu = false; onDefault() })
                    if (!first) DropdownMenuItem(text = { Text(stringResource(R.string.staff090_library_move_up)) }, onClick = { menu = false; onMove(true) })
                    if (!last) DropdownMenuItem(text = { Text(stringResource(R.string.staff090_library_move_down)) }, onClick = { menu = false; onMove(false) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.staff090_library_delete)) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeSheet(form: TypeForm, state: PatronTypesUiState, data: Staff090LibraryTypes, vm: PatronTypesViewModel) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val invalid = state.invalid
    val errors = state.fieldErrors
    val type = state.editing
    ModalBottomSheet(onDismissRequest = { if (!state.saving) vm.closeForm() }, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(stringResource(if (form.id == null) R.string.staff090_library_type_new else R.string.staff090_library_type_edit), style = MaterialTheme.typography.headlineSmall)
            state.formError?.let { InlineErrorBanner(message = it.resolve()) }
            PinakesTextField(
                value = form.name, onValueChange = { v -> vm.edit { it.copy(name = v) } }, label = stringResource(R.string.staff_field_name),
                maxLength = 100, isError = "name" in errors, errorText = errors["name"].orEmpty(), modifier = Modifier.fillMaxWidth().testTag("type-name"),
            )
            PinakesTextField(
                value = form.code, onValueChange = { v -> vm.edit { it.copy(code = v.lowercase()) } }, label = stringResource(R.string.staff090_library_type_code),
                maxLength = 40, isError = "code" in invalid || "code" in errors,
                errorText = errors["code"] ?: stringResource(R.string.staff090_library_type_code_hint),
                supportingText = stringResource(R.string.staff090_library_type_code_hint), modifier = Modifier.fillMaxWidth(),
            )
            PinakesTextField(
                value = form.description, onValueChange = { v -> vm.edit { it.copy(description = v) } }, label = stringResource(R.string.staff_field_description),
                singleLine = false, maxLength = 2000, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
            )
            SwitchRow(stringResource(R.string.staff090_library_type_active), form.active, { v -> vm.edit { it.copy(active = v) } },
                subtitle = stringResource(R.string.staff090_library_type_active_hint))
            if (form.id == null || !(state.original?.isDefault ?: false)) {
                SwitchRow(stringResource(R.string.staff090_library_type_default), form.isDefault, { v -> vm.edit { it.copy(isDefault = v) } },
                    subtitle = stringResource(R.string.staff090_library_type_default_hint))
            }
            Text(stringResource(R.string.staff090_library_inherit_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            FormSectionTitle(stringResource(R.string.staff090_library_loans_section))
            TypeNumberField(TypeNumber.MAX_LOANS, form, type, data, invalid, errors, vm)
            TypeNumberField(TypeNumber.LOAN_DURATION, form, type, data, invalid, errors, vm)
            TypeNumberField(TypeNumber.MAX_REQUEST_DURATION, form, type, data, invalid, errors, vm)
            TypeNumberField(TypeNumber.MAX_RENEWALS, form, type, data, invalid, errors, vm)
            TypeNumberField(TypeNumber.RENEWAL_DAYS, form, type, data, invalid, errors, vm)
            FormSectionTitle(stringResource(R.string.staff090_library_reservations_section))
            SwitchRow(stringResource(R.string.staff090_library_type_reservations), form.reservationsEnabled, { v -> vm.edit { it.copy(reservationsEnabled = v) } })
            TypeNumberField(TypeNumber.MAX_RESERVATIONS, form, type, data, invalid, errors, vm)
            FormSectionTitle(stringResource(R.string.staff090_library_card_section))
            TypeNumberField(TypeNumber.CARD_DURATION, form, type, data, invalid, errors, vm)
            FormSectionTitle(stringResource(R.string.staff090_library_loans_fines))
            TypeNumberField(TypeNumber.GRACE_DAYS, form, type, data, invalid, errors, vm)
            TypeNumberField(TypeNumber.DAILY_FINE, form, type, data, invalid, errors, vm)
            TypeNumberField(TypeNumber.MAX_FINE, form, type, data, invalid, errors, vm)
            TypeNumberField(TypeNumber.DEBT_BLOCK, form, type, data, invalid, errors, vm)

            PrimaryButton(stringResource(R.string.action_save), onClick = vm::save, loading = state.saving, enabled = state.canSave,
                modifier = Modifier.fillMaxWidth().testTag("type-save"))
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

@Composable
private fun TypeNumberField(
    n: TypeNumber,
    form: TypeForm,
    type: Staff090LibraryPatronType?,
    data: Staff090LibraryTypes,
    invalid: Set<String>,
    errors: Map<String, String>,
    vm: PatronTypesViewModel,
) {
    val label = stringResource(typeNumberLabel(n))
    val inherited = TypeForms.inherited(n, type, data.globals)
    val shown = if (n.money) money(inherited, data.currency) else inherited?.toString() ?: "—"
    val hint = errors[n.key] ?: stringResource(R.string.staff090_library_inherits, shown)
    val modifier = Modifier.fillMaxWidth().testTag("type-${n.key}")
    if (n.money) {
        MoneyField(label, form.text(n), data.currency, { v -> vm.edit { it.copy(numbers = it.numbers + (n to v)) } }, invalid = n.key in invalid || n.key in errors, hint = hint, modifier = modifier)
    } else {
        NumberField(label, form.text(n), { v -> vm.edit { it.copy(numbers = it.numbers + (n to v)) } }, invalid = n.key in invalid || n.key in errors, hint = hint, modifier = modifier)
    }
}

internal fun typeNumberLabel(n: TypeNumber) = when (n) {
    TypeNumber.MAX_LOANS -> R.string.staff090_library_num_max_loans
    TypeNumber.LOAN_DURATION -> R.string.staff090_library_num_loan_duration
    TypeNumber.MAX_REQUEST_DURATION -> R.string.staff090_library_num_max_request
    TypeNumber.MAX_RENEWALS -> R.string.staff090_library_num_max_renewals
    TypeNumber.RENEWAL_DAYS -> R.string.staff090_library_num_renewal_days
    TypeNumber.MAX_RESERVATIONS -> R.string.staff090_library_num_max_reservations
    TypeNumber.CARD_DURATION -> R.string.staff090_library_num_card_duration
    TypeNumber.GRACE_DAYS -> R.string.staff090_library_num_grace_days
    TypeNumber.DAILY_FINE -> R.string.staff090_library_num_daily_fine
    TypeNumber.MAX_FINE -> R.string.staff090_library_num_max_fine
    TypeNumber.DEBT_BLOCK -> R.string.staff090_library_num_debt_block
}
