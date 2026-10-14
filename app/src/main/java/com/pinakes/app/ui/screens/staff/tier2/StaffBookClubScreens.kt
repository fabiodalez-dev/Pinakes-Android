package com.pinakes.app.ui.screens.staff.tier2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.pinakes.app.data.model.ClubMember
import com.pinakes.app.data.model.LookupOption
import com.pinakes.app.data.model.StaffClub
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.repository.StaffCirculationRepository
import com.pinakes.app.data.repository.StaffTier2Repository
import com.pinakes.app.data.repository.isForbidden
import com.pinakes.app.data.staff.ClubForm
import com.pinakes.app.data.staff.WorkflowDraft
import com.pinakes.app.ui.common.AppViewModel
import com.pinakes.app.ui.components.ConfirmDialog
import com.pinakes.app.ui.components.ErrorState
import com.pinakes.app.ui.components.LoadingState
import com.pinakes.app.ui.components.PinakesTextField
import com.pinakes.app.ui.components.PinakesTopBar
import com.pinakes.app.ui.components.SecondaryButton
import com.pinakes.app.ui.navigation.Routes
import com.pinakes.app.ui.screens.staff.ForbiddenExit
import com.pinakes.app.ui.screens.staff.FormSectionTitle
import com.pinakes.app.ui.screens.staff.InfoCard
import com.pinakes.app.ui.screens.staff.InfoLine
import com.pinakes.app.ui.screens.staff.LeaveWhen
import com.pinakes.app.ui.screens.staff.LookupField
import com.pinakes.app.ui.screens.staff.PickItem
import com.pinakes.app.ui.screens.staff.SearchController
import com.pinakes.app.ui.screens.staff.SearchPickerSheet
import com.pinakes.app.ui.screens.staff.StaffActionRow
import com.pinakes.app.ui.screens.staff.StaffFab
import com.pinakes.app.ui.screens.staff.StaffLoadBody
import com.pinakes.app.ui.screens.staff.StaffMessage
import com.pinakes.app.ui.screens.staff.StaffSnackbarEffect
import com.pinakes.app.ui.screens.staff.SwitchRow
import com.pinakes.app.ui.screens.staff.circulation.toPickItem
import com.pinakes.app.ui.screens.staff.resolve
import com.pinakes.app.ui.screens.staff.toStaffMessage
import com.pinakes.app.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal fun privacyLabel(value: String): Int = when (value) {
    "private" -> R.string.staff_club_privacy_private
    "invite" -> R.string.staff_club_privacy_invite
    "hidden" -> R.string.staff_club_privacy_hidden
    else -> R.string.staff_club_privacy_public
}

internal fun roleLabel(value: String): Int = when (value) {
    "owner" -> R.string.staff_club_role_owner
    "moderator" -> R.string.staff_club_role_moderator
    "guest" -> R.string.staff_club_role_guest
    else -> R.string.staff_club_role_member
}

internal fun memberStatusLabel(value: String): Int = when (value) {
    "pending" -> R.string.staff_club_member_pending
    "suspended" -> R.string.staff_club_member_suspended
    "left" -> R.string.staff_club_member_left
    "banned" -> R.string.staff_club_member_banned
    else -> R.string.staff_club_member_active
}

/** The settings sheet shared by create and edit. */
@Composable
private fun ClubFormSheet(title: String, form: ClubForm, error: StaffMessage?, saving: Boolean, onEdit: ((ClubForm) -> ClubForm) -> Unit, onSave: () -> Unit, onDismiss: () -> Unit) {
    FormSheet(title, error, saving, enabled = form.valid, onSave = onSave, onDismiss = onDismiss) {
        PinakesTextField(form.name, { v -> onEdit { it.copy(name = v) } }, stringResource(R.string.staff_field_name), maxLength = 190, modifier = Modifier.fillMaxWidth().testTag("club-name"))
        PinakesTextField(form.description, { v -> onEdit { it.copy(description = v) } }, stringResource(R.string.staff_field_description), singleLine = false,
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp))
        PinakesTextField(form.rules, { v -> onEdit { it.copy(rules = v) } }, stringResource(R.string.staff_club_rules), singleLine = false, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp))
        LookupField(
            label = stringResource(R.string.staff_club_privacy), value = form.privacy, displayValue = stringResource(privacyLabel(form.privacy)),
            options = ClubForm.PRIVACIES.map { LookupOption(it, stringResource(privacyLabel(it))) }, editable = false, allowClear = false,
            onValueChange = { v -> onEdit { it.copy(privacy = v) } }, modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            PinakesTextField(form.color, { v -> onEdit { it.copy(color = v.take(7)) } }, stringResource(R.string.staff_club_color), isError = !form.colorValid,
                errorText = stringResource(R.string.staff_field_invalid), modifier = Modifier.weight(1f))
            PinakesTextField(form.maxMembers, { v -> onEdit { it.copy(maxMembers = v.filter(Char::isDigit)) } }, stringResource(R.string.staff_club_max_members),
                isError = !form.maxMembersValid, errorText = stringResource(R.string.staff_field_invalid),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
        }
        SwitchRow(stringResource(R.string.staff_club_active), form.active, { v -> onEdit { it.copy(active = v) } })
        SwitchRow(stringResource(R.string.staff_club_moderate), form.moderateProposals, { v -> onEdit { it.copy(moderateProposals = v) } })
    }
}

// =============================================================================================
// List
// =============================================================================================

data class ClubsUiState(
    val clubs: List<StaffClub> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: StaffMessage? = null,
    val creating: ClubForm? = null,
    val saving: Boolean = false,
    val formError: StaffMessage? = null,
    val created: Int? = null,
    val forbidden: Boolean = false,
)

@HiltViewModel
class StaffClubsViewModel @Inject constructor(private val tier2: StaffTier2Repository) : ViewModel() {
    private val _state = MutableStateFlow(ClubsUiState())
    val state: StateFlow<ClubsUiState> = _state.asStateFlow()

    fun reload() {
        viewModelScope.launch {
            val res = tier2.clubs()
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(clubs = res.data, loading = false, refreshing = false, error = null)
                    is ApiResult.Failure -> it.copy(loading = false, refreshing = false, forbidden = res.isForbidden(), error = res.toStaffMessage(R.string.staff_error_load))
                }
            }
        }
    }

    fun refresh() { _state.update { it.copy(refreshing = true) }; reload() }
    fun startCreate() = _state.update { it.copy(creating = ClubForm(), formError = null) }
    fun edit(transform: (ClubForm) -> ClubForm) = _state.update { s -> s.copy(creating = s.creating?.let(transform), formError = null) }
    fun close() = _state.update { it.copy(creating = null) }

    fun create() {
        val form = _state.value.creating ?: return
        if (!form.valid || _state.value.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val res = tier2.saveClub(null, form.toJson())
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

/** Book clubs: every club, active or not, with a create sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffClubsScreen(onNavigateUp: () -> Unit, onOpen: (Int) -> Unit) {
    val vm: StaffClubsViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val app: AppViewModel = hiltViewModel()
    val areas by app.staffAreas.collectAsStateWithLifecycle()
    ForbiddenExit(state.forbidden, onNavigateUp)
    LeaveWhen(!areas.bookClub, onNavigateUp)
    LaunchedEffect(Unit) { vm.reload() }
    LaunchedEffect(state.created) { state.created?.let { vm.consumeCreated(); onOpen(it) } }

    Scaffold(
        topBar = { PinakesTopBar(title = stringResource(R.string.staff_clubs), onNavigateUp = onNavigateUp) },
        floatingActionButton = { StaffFab(Icons.Outlined.Add, stringResource(R.string.staff_club_new), vm::startCreate) },
    ) { padding ->
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize().padding(padding)) {
            StaffLoadBody(
                loading = state.loading, error = state.error, isEmpty = state.clubs.isEmpty(),
                emptyTitle = stringResource(R.string.staff_clubs_empty), emptyIcon = Icons.Outlined.Groups, onRetry = vm::refresh,
            ) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = 96.dp)) {
                    items(state.clubs, key = { it.id }) { c ->
                        StaffActionRow(
                            Icons.Outlined.Groups, c.name,
                            listOfNotNull(
                                stringResource(privacyLabel(c.privacy)),
                                c.memberCount?.let { stringResource(R.string.staff_club_members_count, it) },
                                if (c.active) null else stringResource(R.string.staff_club_inactive),
                            ).joinToString(" · "),
                        ) { onOpen(c.id) }
                    }
                }
            }
        }
    }

    state.creating?.let { form ->
        ClubFormSheet(stringResource(R.string.staff_club_new), form, state.formError, state.saving, vm::edit, vm::create, vm::close)
    }
}

// =============================================================================================
// Detail
// =============================================================================================

data class ClubUiState(
    val club: StaffClub? = null,
    val loading: Boolean = true,
    val loadError: StaffMessage? = null,
    val editing: ClubForm? = null,
    val workflow: WorkflowDraft? = null,
    val member: ClubMember? = null,
    val addingMember: Boolean = false,
    val addRole: String = "member",
    val confirmDelete: Boolean = false,
    val busy: Boolean = false,
    val formError: StaffMessage? = null,
    val message: StaffMessage? = null,
    val deleted: Boolean = false,
    val forbidden: Boolean = false,
)

@HiltViewModel
class StaffClubViewModel @Inject constructor(
    private val tier2: StaffTier2Repository,
    private val circulation: StaffCirculationRepository,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val clubId: Int = savedState.get<Int>(Routes.ARG_CLUB_ID) ?: 0
    private val _state = MutableStateFlow(ClubUiState())
    val state: StateFlow<ClubUiState> = _state.asStateFlow()

    val users = SearchController(viewModelScope, minLength = 2) { q ->
        when (val res = circulation.users(q)) {
            is ApiResult.Success -> ApiResult.Success(res.data.map { it.toPickItem().copy(note = null) })
            is ApiResult.Failure -> res
        }
    }.also { c -> c.onFailure = { f -> if (f.isForbidden()) _state.update { it.copy(forbidden = true) } } }

    init { load() }

    fun load() {
        viewModelScope.launch {
            val res = tier2.club(clubId)
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(club = res.data, loading = false, loadError = null)
                    is ApiResult.Failure -> it.copy(loading = false, forbidden = res.isForbidden(), loadError = res.toStaffMessage(R.string.staff_error_load))
                }
            }
        }
    }

    fun startEdit() = _state.update { s -> s.copy(editing = s.club?.let(ClubForm::from), formError = null) }
    fun editForm(transform: (ClubForm) -> ClubForm) = _state.update { s -> s.copy(editing = s.editing?.let(transform), formError = null) }
    fun startWorkflow() = _state.update { s -> s.copy(workflow = s.club?.let { WorkflowDraft.from(it.workflow) }, formError = null) }
    fun editWorkflow(transform: (WorkflowDraft) -> WorkflowDraft) = _state.update { s -> s.copy(workflow = s.workflow?.let(transform), formError = null) }
    fun closeForms() { _state.update { it.copy(editing = null, workflow = null, member = null, addingMember = false, formError = null) }; users.clear() }

    fun saveSettings() {
        val form = _state.value.editing ?: return
        if (!form.valid) return
        mutate(R.string.staff_club_saved) { tier2.saveClub(clubId, form.toJson()) }
    }

    fun saveWorkflow() {
        val draft = _state.value.workflow ?: return
        if (!draft.valid) return
        mutate(R.string.staff_club_workflow_saved) { tier2.saveWorkflow(clubId, draft.toJson()) }
    }

    private fun mutate(success: Int, call: suspend () -> ApiResult<StaffClub>) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, formError = null) }
        viewModelScope.launch {
            val res = call()
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(busy = false, club = res.data, editing = null, workflow = null, message = StaffMessage(success))
                    is ApiResult.Failure -> it.copy(busy = false, forbidden = res.isForbidden(), formError = res.toStaffMessage(R.string.staff_error_save))
                }
            }
        }
    }

    fun openMember(m: ClubMember?) = _state.update { it.copy(member = m) }

    fun updateMember(m: ClubMember, status: String? = null, role: String? = null) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val res = tier2.updateMember(clubId, m.id, status, role)
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(busy = false, member = null, message = StaffMessage(R.string.staff_club_member_saved),
                        club = it.club?.copy(members = it.club.members.map { x -> if (x.id == res.data.id) res.data else x }))
                    is ApiResult.Failure -> it.copy(busy = false, forbidden = res.isForbidden(), message = res.toStaffMessage(R.string.staff_error_save))
                }
            }
        }
    }

    fun startAddMember() = _state.update { it.copy(addingMember = true, addRole = "member") }
    fun setAddRole(role: String) = _state.update { it.copy(addRole = role) }

    fun addMember(user: PickItem) {
        val role = _state.value.addRole
        closeForms()
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val res = tier2.addMember(clubId, user.id, role)
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(busy = false, message = StaffMessage(R.string.staff_club_member_added, args = listOf(res.data.name)),
                        club = it.club?.copy(members = it.club.members.filterNot { x -> x.id == res.data.id } + res.data))
                    is ApiResult.Failure -> it.copy(busy = false, forbidden = res.isForbidden(), message = res.toStaffMessage(R.string.staff_error_save))
                }
            }
        }
    }

    fun askDelete() = _state.update { it.copy(confirmDelete = true) }
    fun dismissDelete() = _state.update { it.copy(confirmDelete = false) }

    fun delete() {
        _state.update { it.copy(confirmDelete = false) }
        viewModelScope.launch {
            when (val res = tier2.deleteClub(clubId)) {
                is ApiResult.Success -> _state.update { it.copy(deleted = true) }
                is ApiResult.Failure -> _state.update { it.copy(forbidden = res.isForbidden(), message = res.toStaffMessage(R.string.staff_error_save)) }
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}

/** One club: settings, the reading workflow and the members (role, status, add). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StaffClubScreen(onNavigateUp: () -> Unit) {
    val vm: StaffClubViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val app: AppViewModel = hiltViewModel()
    val areas by app.staffAreas.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    ForbiddenExit(state.forbidden, onNavigateUp)
    LeaveWhen(!areas.bookClub || state.deleted, onNavigateUp)
    StaffSnackbarEffect(state.message, snackbar, vm::consumeMessage)

    Scaffold(
        topBar = {
            PinakesTopBar(
                title = state.club?.name ?: stringResource(R.string.staff_clubs), onNavigateUp = onNavigateUp,
                actions = { if (state.club != null) IconButton(onClick = vm::askDelete) { Icon(Icons.Outlined.DeleteOutline, contentDescription = stringResource(R.string.staff_club_delete)) } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val club = state.club
        when {
            state.loading -> LoadingState(modifier = Modifier.padding(padding))
            club == null -> ErrorState(message = state.loadError?.resolve() ?: stringResource(R.string.staff_error_load), onRetry = vm::load, modifier = Modifier.padding(padding))
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = 48.dp)) {
                item(key = "facts") {
                    InfoCard(Modifier.padding(top = Spacing.sm)) {
                        InfoLine(stringResource(R.string.staff_club_privacy), stringResource(privacyLabel(club.privacy)))
                        InfoLine(stringResource(R.string.staff_club_max_members), club.maxMembers?.toString())
                        InfoLine(stringResource(R.string.staff_club_active), stringResource(if (club.active) R.string.staff_yes else R.string.staff_club_inactive))
                        if (club.description.isNotBlank()) Text(club.description, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Spacing.sm))
                    }
                    SecondaryButton(stringResource(R.string.staff_club_edit), onClick = vm::startEdit, modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm))
                }
                item(key = "wf-title") { FormSectionTitle(stringResource(R.string.staff_club_workflow)) }
                item(key = "wf") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        club.workflow.forEach { s -> Text(s.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = Spacing.sm)) }
                    }
                    SecondaryButton(stringResource(R.string.staff_club_workflow_edit), onClick = vm::startWorkflow, modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm))
                }
                item(key = "members-title") { FormSectionTitle(stringResource(R.string.staff_club_members)) }
                items(club.members, key = { "m${it.id}" }) { m ->
                    StaffActionRow(Icons.Outlined.Person, m.name.ifBlank { m.email },
                        listOf(stringResource(roleLabel(m.role)), stringResource(memberStatusLabel(m.status))).joinToString(" · ")) { vm.openMember(m) }
                }
                item(key = "add-member") {
                    SecondaryButton(stringResource(R.string.staff_club_member_add), onClick = vm::startAddMember, leadingIcon = Icons.Outlined.Add, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

    state.editing?.let { form -> ClubFormSheet(stringResource(R.string.staff_club_edit), form, state.formError, state.busy, vm::editForm, vm::saveSettings, vm::closeForms) }
    state.workflow?.let { draft ->
        FormSheet(stringResource(R.string.staff_club_workflow_edit), state.formError, state.busy, enabled = draft.valid, onSave = vm::saveWorkflow, onDismiss = vm::closeForms) {
            Text(stringResource(R.string.staff_club_workflow_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            draft.states.forEachIndexed { i, s ->
                Column(Modifier.fillMaxWidth().padding(vertical = Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        PinakesTextField(s.label, { v -> vm.editWorkflow { d -> d.update(i) { it.copy(label = v) } } }, stringResource(R.string.staff_club_state_label),
                            modifier = Modifier.weight(2f).testTag("state-label-$i"))
                        PinakesTextField(s.color, { v -> vm.editWorkflow { d -> d.update(i) { it.copy(color = v.take(7)) } } }, stringResource(R.string.staff_club_color), modifier = Modifier.weight(1f))
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        listOf(
                            Triple(R.string.staff_club_flag_voting, s.voting) { v: Boolean -> vm.editWorkflow { d -> d.update(i) { it.copy(voting = v) } } },
                            Triple(R.string.staff_club_flag_current, s.current) { v: Boolean -> vm.editWorkflow { d -> d.update(i) { it.copy(current = v) } } },
                            Triple(R.string.staff_club_flag_archived, s.archived) { v: Boolean -> vm.editWorkflow { d -> d.update(i) { it.copy(archived = v) } } },
                        ).forEach { (label, on, set) ->
                            FilterChip(selected = on, onClick = { set(!on) }, label = { Text(stringResource(label)) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer))
                        }
                        IconButton(onClick = { vm.editWorkflow { it.move(i, -1) } }, enabled = i > 0) { Icon(Icons.Outlined.ArrowUpward, contentDescription = stringResource(R.string.staff_move_up)) }
                        IconButton(onClick = { vm.editWorkflow { it.move(i, 1) } }, enabled = i < draft.states.size - 1) { Icon(Icons.Outlined.ArrowDownward, contentDescription = stringResource(R.string.staff_move_down)) }
                        IconButton(onClick = { vm.editWorkflow { it.remove(i) } }, enabled = draft.states.size > 2) { Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.cd_remove)) }
                    }
                }
            }
            val newLabel = stringResource(R.string.staff_club_state_new)
            SecondaryButton(stringResource(R.string.staff_club_state_add), onClick = { vm.editWorkflow { it.add(newLabel) } }, leadingIcon = Icons.Outlined.Add, modifier = Modifier.fillMaxWidth())
        }
    }
    state.member?.let { m ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { vm.openMember(null) }, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(m.name.ifBlank { m.email }, style = MaterialTheme.typography.headlineSmall)
                FormSectionTitle(stringResource(R.string.staff_club_role))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    ClubForm.ROLES.forEach { role ->
                        FilterChip(selected = m.role == role, onClick = { if (m.role != role) vm.updateMember(m, role = role) }, enabled = !state.busy,
                            label = { Text(stringResource(roleLabel(role))) }, modifier = Modifier.testTag("role-$role"))
                    }
                }
                FormSectionTitle(stringResource(R.string.staff_club_member_status))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    ClubForm.MEMBER_STATUSES.forEach { status ->
                        FilterChip(selected = m.status == status, onClick = { if (m.status != status) vm.updateMember(m, status = status) }, enabled = !state.busy,
                            label = { Text(stringResource(memberStatusLabel(status))) }, modifier = Modifier.testTag("status-$status"))
                    }
                }
                Spacer(Modifier.height(Spacing.lg))
            }
        }
    }
    if (state.addingMember) {
        val s by vm.users.state.collectAsStateWithLifecycle()
        SearchPickerSheet(
            title = stringResource(R.string.staff_club_member_add), placeholder = stringResource(R.string.staff_search_users), state = s,
            onQuery = vm.users::onQuery, onPick = vm::addMember, onDismiss = vm::closeForms,
            hint = stringResource(R.string.staff_club_member_add_hint, stringResource(roleLabel(state.addRole))),
        )
    }
    if (state.confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.staff_club_delete), body = stringResource(R.string.staff_club_delete_body, state.club?.name.orEmpty()),
            confirmLabel = stringResource(R.string.staff_club_delete), onConfirm = vm::delete, onDismiss = vm::dismissDelete,
        )
    }
}
