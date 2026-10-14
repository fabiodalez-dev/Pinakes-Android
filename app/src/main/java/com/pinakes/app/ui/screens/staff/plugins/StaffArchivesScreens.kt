package com.pinakes.app.ui.screens.staff.plugins

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pinakes.app.R
import com.pinakes.app.data.model.ArchiveLookups
import com.pinakes.app.data.model.AuthorityRow
import com.pinakes.app.data.model.LookupOption
import com.pinakes.app.data.model.StaffArchiveDetail
import com.pinakes.app.data.model.StaffArchiveRow
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.repository.StaffPluginsRepository
import com.pinakes.app.data.repository.isDuplicate
import com.pinakes.app.data.repository.isForbidden
import com.pinakes.app.data.staff.ArchiveField
import com.pinakes.app.data.staff.ArchiveForm
import com.pinakes.app.data.staff.AuthorityForm
import com.pinakes.app.data.staff.CoverImageEncoder
import com.pinakes.app.ui.common.AppViewModel
import com.pinakes.app.ui.components.ConfirmDialog
import com.pinakes.app.ui.components.ErrorState
import com.pinakes.app.ui.components.InlineErrorBanner
import com.pinakes.app.ui.components.LoadingState
import com.pinakes.app.ui.components.MediaRow
import com.pinakes.app.ui.components.PinakesTextField
import com.pinakes.app.ui.components.PinakesTopBar
import com.pinakes.app.ui.components.PrimaryButton
import com.pinakes.app.ui.components.SearchField
import com.pinakes.app.ui.components.SecondaryButton
import com.pinakes.app.ui.navigation.Routes
import com.pinakes.app.ui.screens.staff.ForbiddenExit
import com.pinakes.app.ui.screens.staff.FormSectionTitle
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
import com.pinakes.app.ui.screens.staff.resolve
import com.pinakes.app.ui.screens.staff.toStaffMessage
import com.pinakes.app.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Mime types the Archives plugin accepts as documents (`uploadDocumentAction`). */
private val DOCUMENT_MIMES = arrayOf(
    "application/pdf", "image/jpeg", "image/png", "image/tiff", "image/webp", "audio/mpeg", "audio/wav", "audio/x-wav",
    "video/mp4", "text/plain", "application/xml", "text/xml",
)

/** The plugin's maximum document size (200 MB): larger files are refused before uploading. */
private const val MAX_DOCUMENT_BYTES = 200L * 1024 * 1024

// =============================================================================================
// Archive list
// =============================================================================================

enum class PublishedFilter(val wire: String, val label: Int) {
    ALL("all", R.string.staff_archives_filter_all), PUBLISHED("1", R.string.staff_archives_filter_published), DRAFT("0", R.string.staff_archives_filter_draft),
}

data class ArchivesUiState(
    val query: String = "",
    val filter: PublishedFilter = PublishedFilter.ALL,
    val items: List<StaffArchiveRow> = emptyList(),
    val nextCursor: String? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: StaffMessage? = null,
    val forbidden: Boolean = false,
)

@HiltViewModel
class StaffArchivesViewModel @Inject constructor(private val plugins: StaffPluginsRepository) : ViewModel() {
    private val _state = MutableStateFlow(ArchivesUiState())
    val state: StateFlow<ArchivesUiState> = _state.asStateFlow()
    private var searchJob: Job? = null
    private var generation = 0

    fun reload() {
        val gen = ++generation
        _state.update { it.copy(loading = it.items.isEmpty() && !it.refreshing, error = null) }
        viewModelScope.launch {
            val s = _state.value
            val res = plugins.archives(s.query, s.filter.wire, null)
            if (gen != generation) return@launch
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(items = res.data.items, nextCursor = res.data.nextCursor, loading = false, refreshing = false)
                    is ApiResult.Failure -> it.copy(loading = false, refreshing = false, forbidden = res.isForbidden(), error = res.toStaffMessage(R.string.staff_error_load))
                }
            }
        }
    }

    fun loadMore() {
        val cursor = _state.value.nextCursor ?: return
        val gen = generation
        viewModelScope.launch {
            val s = _state.value
            val res = plugins.archives(s.query, s.filter.wire, cursor)
            if (gen != generation) return@launch
            if (res is ApiResult.Success) _state.update { it.copy(items = (it.items + res.data.items).distinctBy(StaffArchiveRow::id), nextCursor = res.data.nextCursor) }
        }
    }

    fun refresh() { _state.update { it.copy(refreshing = true) }; reload() }
    fun setFilter(f: PublishedFilter) { _state.update { it.copy(filter = f, items = emptyList()) }; reload() }

    fun onQuery(value: String) {
        _state.update { it.copy(query = value) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch { delay(350); reload() }
    }
}

/** Every archival unit, published or not, with search and a publish filter. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StaffArchivesScreen(onNavigateUp: () -> Unit, onOpen: (Int) -> Unit, onNew: () -> Unit) {
    val vm: StaffArchivesViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val app: AppViewModel = hiltViewModel()
    val areas by app.staffAreas.collectAsStateWithLifecycle()
    ForbiddenExit(state.forbidden, onNavigateUp)
    LeaveWhen(!areas.archives, onNavigateUp)
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) vm.reload() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = { PinakesTopBar(title = stringResource(R.string.staff_archives), onNavigateUp = onNavigateUp) },
        floatingActionButton = { StaffFab(Icons.Outlined.Add, stringResource(R.string.staff_archive_new), onNew) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SearchField(query = state.query, onQueryChange = vm::onQuery, placeholder = stringResource(R.string.staff_archives_search),
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm))
            FlowRow(Modifier.padding(horizontal = Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                PublishedFilter.entries.forEach { f ->
                    FilterChip(selected = state.filter == f, onClick = { vm.setFilter(f) }, label = { Text(stringResource(f.label)) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer))
                }
            }
            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
                StaffLoadBody(
                    loading = state.loading, error = state.error, isEmpty = state.items.isEmpty(),
                    emptyTitle = stringResource(R.string.staff_archives_empty), emptyIcon = Icons.Outlined.Archive, onRetry = vm::refresh,
                ) {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = 96.dp)) {
                        items(state.items, key = { it.id }) { row ->
                            MediaRow(
                                title = row.title, coverUrl = row.coverUrl,
                                line1 = listOfNotNull(row.referenceCode.takeIf { it.isNotBlank() }, row.level.takeIf { it.isNotBlank() }, row.datesLabel).joinToString(" · "),
                                line2 = stringResource(if (row.published) R.string.staff_archive_published else R.string.staff_archive_draft),
                                onClick = { onOpen(row.id) },
                                modifier = Modifier.padding(vertical = Spacing.xs).testTag("archive-${row.id}"),
                            )
                        }
                        if (state.nextCursor != null) item(key = "more") {
                            LaunchedEffect(state.items.size) { vm.loadMore() }
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================================
// Archive editor
// =============================================================================================

data class ArchiveEditUiState(
    val id: Int = 0,
    val detail: StaffArchiveDetail? = null,
    val form: ArchiveForm = ArchiveForm.new(),
    val lookups: ArchiveLookups = ArchiveLookups(),
    val loading: Boolean = true,
    val loadError: StaffMessage? = null,
    val saving: Boolean = false,
    val showErrors: Boolean = false,
    val serverField: ArchiveField? = null,
    val formError: StaffMessage? = null,
    val uploading: Boolean = false,
    val attaching: Boolean = false,
    val attachRole: String = "creator",
    val confirmDelete: Boolean = false,
    val message: StaffMessage? = null,
    val forbidden: Boolean = false,
    val deleted: Boolean = false,
) {
    val errors: Set<ArchiveField> get() = (if (showErrors) form.errors() else emptySet()) + setOfNotNull(serverField)
}

@HiltViewModel
class StaffArchiveEditViewModel @Inject constructor(
    private val plugins: StaffPluginsRepository,
    @ApplicationContext private val context: Context,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(ArchiveEditUiState(id = savedState.get<Int>(Routes.ARG_ARCHIVE_ID) ?: 0))
    val state: StateFlow<ArchiveEditUiState> = _state.asStateFlow()

    val authorities = SearchController(viewModelScope, minLength = 1) { q ->
        when (val res = plugins.authorities(q)) {
            is ApiResult.Success -> ApiResult.Success(res.data.items.map { PickItem(it.id, it.name, listOfNotNull(it.type, it.dates).joinToString(" · ")) })
            is ApiResult.Failure -> res
        }
    }.also { c -> c.onFailure = { f -> if (f.isForbidden()) _state.update { it.copy(forbidden = true) } } }

    init {
        viewModelScope.launch { (plugins.archiveLookups() as? ApiResult.Success)?.data?.let { l -> _state.update { it.copy(lookups = l) } } }
        load()
    }

    fun load() {
        val id = _state.value.id
        if (id <= 0) {
            _state.update { it.copy(loading = false) }
            return
        }
        viewModelScope.launch {
            val res = plugins.archive(id)
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(detail = res.data, form = ArchiveForm.from(res.data), loading = false, loadError = null)
                    is ApiResult.Failure -> it.copy(loading = false, forbidden = res.isForbidden(), loadError = res.toStaffMessage(R.string.staff_error_load))
                }
            }
        }
    }

    fun edit(key: String, value: String) = _state.update {
        it.copy(form = it.form.with(key, value), formError = null, serverField = it.serverField?.takeIf { f -> f.wire != key })
    }

    fun setPublished(value: Boolean) = _state.update { it.copy(form = it.form.copy(published = value)) }

    fun save() {
        val s = _state.value
        if (s.saving) return
        if (s.form.errors().isNotEmpty()) {
            _state.update { it.copy(showErrors = true, formError = StaffMessage(R.string.staff_form_fix_errors)) }
            return
        }
        _state.update { it.copy(saving = true, showErrors = true, formError = null, serverField = null) }
        viewModelScope.launch {
            val res = plugins.saveArchive(s.id, s.form.toJson())
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(
                        saving = false, id = res.data.id, detail = res.data, form = ArchiveForm.from(res.data),
                        message = StaffMessage(if (s.id <= 0) R.string.staff_archive_created else R.string.staff_archive_saved),
                    )
                    is ApiResult.Failure -> it.copy(
                        saving = false, forbidden = res.isForbidden(), serverField = ArchiveField.fromWire(res.meta?.field)
                            ?: if (res.isDuplicate()) ArchiveField.REFERENCE_CODE else null,
                        formError = if (res.isDuplicate()) StaffMessage(R.string.staff_archive_duplicate) else res.toStaffMessage(R.string.staff_error_save),
                    )
                }
            }
        }
    }

    private fun upload(call: suspend () -> ApiResult<StaffArchiveDetail>?, success: Int) {
        if (_state.value.uploading) return
        _state.update { it.copy(uploading = true) }
        viewModelScope.launch {
            val res = call()
            _state.update {
                when (res) {
                    null -> it.copy(uploading = false, message = StaffMessage(R.string.staff_import_read_failed))
                    is ApiResult.Success -> it.copy(uploading = false, detail = res.data, message = StaffMessage(success))
                    is ApiResult.Failure -> it.copy(uploading = false, forbidden = res.isForbidden(), message = res.toStaffMessage(R.string.staff_error_save))
                }
            }
        }
    }

    fun uploadCover(uri: Uri?) {
        val id = _state.value.id
        if (uri == null || id <= 0) return
        upload({
            val bytes = withContext(Dispatchers.IO) { CoverImageEncoder.encode(context, uri) }
            bytes?.let { plugins.uploadArchiveCover(id, "cover.jpg", it, "image/jpeg") }
        }, R.string.staff_archive_cover_uploaded)
    }

    fun removeCover() {
        val id = _state.value.id
        if (id > 0) upload({ plugins.deleteArchiveCover(id) }, R.string.staff_archive_cover_removed)
    }

    fun uploadDocument(uri: Uri?) {
        val id = _state.value.id
        if (uri == null || id <= 0) return
        upload({
            withContext(Dispatchers.IO) { readDocument(uri) }?.let { (name, mime, bytes) -> plugins.uploadArchiveDocument(id, name, bytes, mime) }
        }, R.string.staff_archive_document_uploaded)
    }

    private fun readDocument(uri: Uri): Triple<String, String?, ByteArray>? = runCatching {
        val resolver = context.contentResolver
        var name = "document"
        var size = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { i -> c.getString(i)?.let { name = it } }
                c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { i -> if (!c.isNull(i)) size = c.getLong(i) }
            }
        }
        if (size > MAX_DOCUMENT_BYTES) return null
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        Triple(name, resolver.getType(uri), bytes)
    }.getOrNull()

    fun removeDocument(fileId: Int) {
        val id = _state.value.id
        if (id > 0) upload({ plugins.deleteArchiveDocument(id, fileId) }, R.string.staff_archive_document_removed)
    }

    fun openAttach() = _state.update { it.copy(attaching = true) }
    fun closeAttach() { _state.update { it.copy(attaching = false) }; authorities.clear() }
    fun setRole(role: String) = _state.update { it.copy(attachRole = role) }

    fun attach(item: PickItem) {
        val id = _state.value.id
        val role = _state.value.attachRole
        closeAttach()
        if (id > 0) upload({ plugins.attachAuthority(id, item.id, role) }, R.string.staff_archive_authority_attached)
    }

    fun detach(authorityId: Int) {
        val id = _state.value.id
        if (id > 0) upload({ plugins.detachAuthority(id, authorityId) }, R.string.staff_archive_authority_detached)
    }

    fun askDelete() = _state.update { it.copy(confirmDelete = true) }
    fun dismissDelete() = _state.update { it.copy(confirmDelete = false) }

    fun delete() {
        val id = _state.value.id
        _state.update { it.copy(confirmDelete = false) }
        if (id <= 0) return
        viewModelScope.launch {
            when (val res = plugins.deleteArchive(id)) {
                is ApiResult.Success -> _state.update { it.copy(deleted = true) }
                is ApiResult.Failure -> _state.update { it.copy(forbidden = res.isForbidden(), message = res.toStaffMessage(R.string.staff_error_save)) }
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}

/**
 * Create or edit an archival unit (ISAD(G) identity, dates, description, material, links),
 * publish it, attach authorities with a role, and upload a cover or documents. Uploads and
 * authorities need a saved unit, so they appear after the first save.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffArchiveEditScreen(onNavigateUp: () -> Unit) {
    val vm: StaffArchiveEditViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val app: AppViewModel = hiltViewModel()
    val areas by app.staffAreas.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    ForbiddenExit(state.forbidden, onNavigateUp)
    LeaveWhen(!areas.archives || state.deleted, onNavigateUp)
    StaffSnackbarEffect(state.message, snackbar, vm::consumeMessage)
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia(), vm::uploadCover)
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), vm::uploadDocument)

    Scaffold(
        topBar = {
            PinakesTopBar(
                title = stringResource(if (state.id > 0) R.string.staff_archive_edit else R.string.staff_archive_new),
                onNavigateUp = onNavigateUp,
                actions = {
                    if (state.id > 0) IconButton(onClick = vm::askDelete) { Icon(Icons.Outlined.DeleteOutline, contentDescription = stringResource(R.string.staff_archive_delete)) }
                },
            )
        },
        bottomBar = {
            if (!state.loading && state.loadError == null) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    PrimaryButton(
                        stringResource(R.string.action_save), onClick = vm::save, loading = state.saving,
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = Spacing.lg, vertical = Spacing.sm).testTag("archive-save"),
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when {
            state.loading -> LoadingState(modifier = Modifier.padding(padding))
            state.loadError != null -> ErrorState(message = state.loadError?.resolve().orEmpty(), onRetry = vm::load, modifier = Modifier.padding(padding))
            else -> ArchiveForm(state, vm, Modifier.padding(padding), onPickCover = {
                coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }, onPickDocument = { documentPicker.launch(DOCUMENT_MIMES) })
        }
    }

    if (state.attaching) {
        val s by vm.authorities.state.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) { vm.authorities.start("") }
        SearchPickerSheet(
            title = stringResource(R.string.staff_archive_attach_authority), placeholder = stringResource(R.string.staff_authorities_search),
            state = s, onQuery = vm.authorities::onQuery, onPick = vm::attach, onDismiss = vm::closeAttach,
            hint = stringResource(R.string.staff_archive_attach_role_hint, state.lookups.authorityRoleOptions.firstOrNull { it.value == state.attachRole }?.label ?: state.attachRole),
        )
    }
    if (state.confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.staff_archive_delete), body = stringResource(R.string.staff_archive_delete_body, state.form["constructed_title"]),
            confirmLabel = stringResource(R.string.staff_archive_delete), onConfirm = vm::delete, onDismiss = vm::dismissDelete,
        )
    }
}

@Composable
private fun ArchiveForm(
    state: ArchiveEditUiState,
    vm: StaffArchiveEditViewModel,
    modifier: Modifier,
    onPickCover: () -> Unit,
    onPickDocument: () -> Unit,
) {
    val f = state.form
    val errors = state.errors
    @Composable
    fun field(key: String, label: Int, error: ArchiveField? = null, number: Boolean = false, multiline: Boolean = false) {
        PinakesTextField(
            value = f[key], onValueChange = { vm.edit(key, it) }, label = stringResource(label),
            isError = error != null && error in errors, errorText = stringResource(R.string.staff_field_invalid),
            singleLine = !multiline, keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Text),
            modifier = Modifier.fillMaxWidth().then(if (multiline) Modifier.heightIn(min = 96.dp) else Modifier).testTag("archive-$key"),
        )
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        state.formError?.let { InlineErrorBanner(message = it.resolve(), modifier = Modifier.padding(top = Spacing.sm)) }
        SwitchRow(stringResource(R.string.staff_archive_published), f.published, vm::setPublished, subtitle = stringResource(R.string.staff_archive_published_hint))

        FormSectionTitle(stringResource(R.string.staff_archive_section_identity))
        field("reference_code", R.string.staff_archive_reference_code, ArchiveField.REFERENCE_CODE)
        field("institution_code", R.string.staff_archive_institution_code)
        LookupField(
            label = stringResource(R.string.staff_archive_level), value = f["level"],
            displayValue = state.lookups.levelOptions.firstOrNull { it.value == f["level"] }?.label ?: f["level"],
            options = state.lookups.levelOptions.ifEmpty { listOf("fonds", "series", "file", "item").map { LookupOption(it, it) } },
            editable = false, allowClear = false, onValueChange = { vm.edit("level", it) }, modifier = Modifier.fillMaxWidth(),
        )
        field("constructed_title", R.string.staff_archive_constructed_title, ArchiveField.CONSTRUCTED_TITLE)
        field("formal_title", R.string.staff_archive_formal_title)
        field("parent_id", R.string.staff_archive_parent_id, ArchiveField.PARENT_ID, number = true)

        FormSectionTitle(stringResource(R.string.staff_archive_section_dates))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Column(Modifier.weight(1f)) { field("date_start", R.string.staff_archive_date_start, ArchiveField.DATE_START, number = true) }
            Column(Modifier.weight(1f)) { field("date_end", R.string.staff_archive_date_end, ArchiveField.DATE_END, number = true) }
        }

        FormSectionTitle(stringResource(R.string.staff_archive_section_description))
        field("extent", R.string.staff_archive_extent)
        field("scope_content", R.string.staff_archive_scope_content, multiline = true)
        field("language_codes", R.string.staff_archive_language_codes)

        FormSectionTitle(stringResource(R.string.staff_archive_section_material))
        LookupField(
            label = stringResource(R.string.staff_archive_material), value = f["specific_material"],
            displayValue = state.lookups.materialOptions.firstOrNull { it.value == f["specific_material"] }?.label ?: f["specific_material"],
            options = state.lookups.materialOptions, editable = false, allowClear = false,
            onValueChange = { vm.edit("specific_material", it) }, modifier = Modifier.fillMaxWidth(),
        )
        LookupField(
            label = stringResource(R.string.staff_archive_color_mode), value = f["color_mode"],
            displayValue = state.lookups.colorModeOptions.firstOrNull { it.value == f["color_mode"] }?.label ?: f["color_mode"],
            options = state.lookups.colorModeOptions, editable = false, onValueChange = { vm.edit("color_mode", it) }, modifier = Modifier.fillMaxWidth(),
        )
        field("dimensions", R.string.staff_archive_dimensions)
        field("photographer", R.string.staff_archive_photographer)
        field("publisher", R.string.staff_archive_publisher)
        field("collection_name", R.string.staff_archive_collection_name)
        field("local_classification", R.string.staff_archive_local_classification)

        FormSectionTitle(stringResource(R.string.staff_archive_section_links))
        field("iiif_manifest_url", R.string.staff_archive_iiif)
        field("rights_statement_url", R.string.staff_archive_rights)
        field("ark_identifier", R.string.staff_archive_ark)
        field("version_note", R.string.staff_archive_version_note)

        val detail = state.detail
        if (state.id > 0 && detail != null) {
            FormSectionTitle(stringResource(R.string.staff_archive_section_authorities))
            detail.authorities.forEach { a ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(a.name, style = MaterialTheme.typography.bodyLarge)
                        Text(state.lookups.authorityRoleOptions.firstOrNull { it.value == a.role }?.label ?: a.role.orEmpty(),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { vm.detach(a.id) }, enabled = !state.uploading) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.staff_archive_detach, a.name))
                    }
                }
            }
            LookupField(
                label = stringResource(R.string.staff_archive_role), value = state.attachRole,
                displayValue = state.lookups.authorityRoleOptions.firstOrNull { it.value == state.attachRole }?.label ?: state.attachRole,
                options = state.lookups.authorityRoleOptions, editable = false, allowClear = false, onValueChange = vm::setRole, modifier = Modifier.fillMaxWidth(),
            )
            SecondaryButton(stringResource(R.string.staff_archive_attach_authority), onClick = vm::openAttach, leadingIcon = Icons.Outlined.Person,
                enabled = !state.uploading, modifier = Modifier.fillMaxWidth())

            FormSectionTitle(stringResource(R.string.staff_archive_section_files))
            if (state.uploading) LoadingState(modifier = Modifier.heightIn(max = 64.dp))
            Text(
                stringResource(if (detail.coverUrl.isNullOrBlank()) R.string.staff_archive_no_cover else R.string.staff_archive_has_cover),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SecondaryButton(stringResource(R.string.staff_archive_upload_cover), onClick = onPickCover, leadingIcon = Icons.Outlined.Image, enabled = !state.uploading)
                if (!detail.coverUrl.isNullOrBlank()) SecondaryButton(stringResource(R.string.staff_archive_remove_cover), onClick = vm::removeCover, enabled = !state.uploading)
            }
            detail.files.forEach { file ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(file.label?.takeIf { it.isNotBlank() } ?: file.url.substringAfterLast('/'), style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    IconButton(onClick = { vm.removeDocument(file.id) }, enabled = !state.uploading) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = stringResource(R.string.staff_archive_remove_document))
                    }
                }
            }
            SecondaryButton(stringResource(R.string.staff_archive_upload_document), onClick = onPickDocument, leadingIcon = Icons.Outlined.AttachFile,
                enabled = !state.uploading, modifier = Modifier.fillMaxWidth())
        } else {
            Text(stringResource(R.string.staff_archive_save_first), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(Spacing.xxl))
    }
}

// =============================================================================================
// Authorities
// =============================================================================================

data class AuthoritiesUiState(
    val query: String = "",
    val items: List<AuthorityRow> = emptyList(),
    val loading: Boolean = true,
    val error: StaffMessage? = null,
    val forbidden: Boolean = false,
)

@HiltViewModel
class StaffAuthoritiesViewModel @Inject constructor(private val plugins: StaffPluginsRepository) : ViewModel() {
    private val _state = MutableStateFlow(AuthoritiesUiState())
    val state: StateFlow<AuthoritiesUiState> = _state.asStateFlow()
    private var job: Job? = null

    fun reload() {
        job?.cancel()
        job = viewModelScope.launch {
            delay(if (_state.value.items.isEmpty()) 0 else 300)
            val res = plugins.authorities(_state.value.query)
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(items = res.data.items, loading = false, error = null)
                    is ApiResult.Failure -> it.copy(loading = false, forbidden = res.isForbidden(), error = res.toStaffMessage(R.string.staff_error_load))
                }
            }
        }
    }

    fun onQuery(value: String) { _state.update { it.copy(query = value) }; reload() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffAuthoritiesScreen(onNavigateUp: () -> Unit, onOpen: (Int) -> Unit) {
    val vm: StaffAuthoritiesViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val app: AppViewModel = hiltViewModel()
    val areas by app.staffAreas.collectAsStateWithLifecycle()
    ForbiddenExit(state.forbidden, onNavigateUp)
    LeaveWhen(!areas.archives, onNavigateUp)
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) vm.reload() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    Scaffold(
        topBar = { PinakesTopBar(title = stringResource(R.string.staff_authorities), onNavigateUp = onNavigateUp) },
        floatingActionButton = { StaffFab(Icons.Outlined.Add, stringResource(R.string.staff_authority_new)) { onOpen(0) } },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SearchField(query = state.query, onQueryChange = vm::onQuery, placeholder = stringResource(R.string.staff_authorities_search),
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm))
            StaffLoadBody(
                loading = state.loading, error = state.error, isEmpty = state.items.isEmpty(),
                emptyTitle = stringResource(R.string.staff_authorities_empty), emptyIcon = Icons.Outlined.Person, onRetry = vm::reload,
            ) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = 96.dp)) {
                    items(state.items, key = { it.id }) { a ->
                        StaffActionRow(Icons.Outlined.Person, a.name,
                            listOfNotNull(a.type, a.dates, stringResource(R.string.staff_authority_units, a.unitsCount)).joinToString(" · ")) { onOpen(a.id) }
                    }
                }
            }
        }
    }
}

data class AuthorityEditUiState(
    val id: Int = 0,
    val form: AuthorityForm = AuthorityForm(),
    val units: List<com.pinakes.app.data.model.AuthorityUnitRef> = emptyList(),
    val lookups: ArchiveLookups = ArchiveLookups(),
    val loading: Boolean = true,
    val saving: Boolean = false,
    val showErrors: Boolean = false,
    val error: StaffMessage? = null,
    val confirmDelete: Boolean = false,
    val done: Boolean = false,
    val forbidden: Boolean = false,
)

@HiltViewModel
class StaffAuthorityEditViewModel @Inject constructor(
    private val plugins: StaffPluginsRepository,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(AuthorityEditUiState(id = savedState.get<Int>(Routes.ARG_AUTHORITY_ID) ?: 0))
    val state: StateFlow<AuthorityEditUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { (plugins.archiveLookups() as? ApiResult.Success)?.data?.let { l -> _state.update { it.copy(lookups = l) } } }
        val id = _state.value.id
        if (id <= 0) _state.update { it.copy(loading = false) } else viewModelScope.launch {
            val res = plugins.authority(id)
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(form = AuthorityForm.from(res.data), units = res.data.units, loading = false)
                    is ApiResult.Failure -> it.copy(loading = false, forbidden = res.isForbidden(), error = res.toStaffMessage(R.string.staff_error_load))
                }
            }
        }
    }

    fun edit(key: String, value: String) = _state.update { it.copy(form = it.form.with(key, value), error = null) }

    fun save() {
        val s = _state.value
        if (!s.form.valid) { _state.update { it.copy(showErrors = true) }; return }
        if (s.saving) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val res = plugins.saveAuthority(s.id, s.form.toJson())
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(saving = false, done = true)
                    is ApiResult.Failure -> it.copy(saving = false, forbidden = res.isForbidden(), error = res.toStaffMessage(R.string.staff_error_save))
                }
            }
        }
    }

    fun askDelete() = _state.update { it.copy(confirmDelete = true) }
    fun dismissDelete() = _state.update { it.copy(confirmDelete = false) }

    fun delete() {
        _state.update { it.copy(confirmDelete = false) }
        viewModelScope.launch {
            when (val res = plugins.deleteAuthority(_state.value.id)) {
                is ApiResult.Success -> _state.update { it.copy(done = true) }
                is ApiResult.Failure -> _state.update { it.copy(forbidden = res.isForbidden(), error = res.toStaffMessage(R.string.staff_error_save)) }
            }
        }
    }
}

/** An ISAAR(CPF) authority record: type, authorised form and the descriptive areas. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffAuthorityEditScreen(onNavigateUp: () -> Unit) {
    val vm: StaffAuthorityEditViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val app: AppViewModel = hiltViewModel()
    val areas by app.staffAreas.collectAsStateWithLifecycle()
    ForbiddenExit(state.forbidden, onNavigateUp)
    LeaveWhen(!areas.archives || state.done, onNavigateUp)

    Scaffold(
        topBar = {
            PinakesTopBar(
                title = stringResource(if (state.id > 0) R.string.staff_authority_edit else R.string.staff_authority_new), onNavigateUp = onNavigateUp,
                actions = { if (state.id > 0) IconButton(onClick = vm::askDelete) { Icon(Icons.Outlined.DeleteOutline, contentDescription = stringResource(R.string.staff_authority_delete)) } },
            )
        },
    ) { padding ->
        if (state.loading) { LoadingState(modifier = Modifier.padding(padding)); return@Scaffold }
        val f = state.form
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg).imePadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            state.error?.let { InlineErrorBanner(message = it.resolve(), modifier = Modifier.padding(top = Spacing.sm)) }
            LookupField(
                label = stringResource(R.string.staff_authority_type), value = f["type"],
                displayValue = state.lookups.authorityTypeOptions.firstOrNull { it.value == f["type"] }?.label ?: f["type"],
                options = state.lookups.authorityTypeOptions.ifEmpty { listOf("person", "corporate", "family").map { LookupOption(it, it) } },
                editable = false, allowClear = false, onValueChange = { vm.edit("type", it) }, modifier = Modifier.fillMaxWidth(),
            )
            val labels = listOf(
                "authorised_form" to R.string.staff_authority_authorised_form, "parallel_forms" to R.string.staff_authority_parallel_forms,
                "other_forms" to R.string.staff_authority_other_forms, "identifiers" to R.string.staff_authority_identifiers,
                "dates_of_existence" to R.string.staff_authority_dates, "history" to R.string.staff_authority_history,
                "places" to R.string.staff_authority_places, "legal_status" to R.string.staff_authority_legal_status,
                "functions" to R.string.staff_authority_functions, "mandates" to R.string.staff_authority_mandates,
                "internal_structure" to R.string.staff_authority_internal_structure, "general_context" to R.string.staff_authority_general_context,
            )
            labels.forEach { (key, label) ->
                val long = key in setOf("history", "functions", "mandates", "internal_structure", "general_context")
                PinakesTextField(
                    value = f[key], onValueChange = { vm.edit(key, it) }, label = stringResource(label), singleLine = !long,
                    isError = key == "authorised_form" && state.showErrors && f[key].isBlank(), errorText = stringResource(R.string.staff_field_required),
                    modifier = Modifier.fillMaxWidth().then(if (long) Modifier.heightIn(min = 96.dp) else Modifier).testTag("authority-$key"),
                )
            }
            if (state.units.isNotEmpty()) {
                FormSectionTitle(stringResource(R.string.staff_authority_linked_units))
                state.units.forEach { u -> Text(listOfNotNull(u.title, u.role).joinToString(" · "), style = MaterialTheme.typography.bodyMedium) }
            }
            PrimaryButton(stringResource(R.string.action_save), onClick = vm::save, loading = state.saving, modifier = Modifier.fillMaxWidth().testTag("authority-save"))
            Spacer(Modifier.height(Spacing.xxl))
        }
    }
    if (state.confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.staff_authority_delete), body = stringResource(R.string.staff_authority_delete_body, state.form["authorised_form"]),
            confirmLabel = stringResource(R.string.staff_authority_delete), onConfirm = vm::delete, onDismiss = vm::dismissDelete,
        )
    }
}
