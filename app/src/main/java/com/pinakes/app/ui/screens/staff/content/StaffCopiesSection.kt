package com.pinakes.app.ui.screens.staff.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.remember
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
import com.pinakes.app.data.model.StaffBook
import com.pinakes.app.data.model.StaffCopy
import com.pinakes.app.data.model.StaffLookups
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.repository.StaffContentRepository
import com.pinakes.app.data.repository.StaffFilesRepository
import com.pinakes.app.data.repository.StaffRepository
import com.pinakes.app.data.repository.isDuplicate
import com.pinakes.app.data.repository.isForbidden
import com.pinakes.app.data.staff.CopyCodeCodec
import com.pinakes.app.ui.common.AppViewModel
import com.pinakes.app.ui.components.ConfirmDialog
import com.pinakes.app.ui.components.InlineErrorBanner
import com.pinakes.app.ui.components.PinakesTextButton
import com.pinakes.app.ui.components.PinakesTextField
import com.pinakes.app.ui.components.PrimaryButton
import com.pinakes.app.ui.components.SecondaryButton
import com.pinakes.app.ui.navigation.Routes
import com.pinakes.app.ui.screens.staff.ExportFileDialog
import com.pinakes.app.ui.screens.staff.LookupField
import com.pinakes.app.ui.screens.staff.StaffMessage
import com.pinakes.app.ui.screens.staff.resolve
import com.pinakes.app.ui.screens.staff.toStaffMessage
import com.pinakes.app.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The copy being edited (sheet) or created (id 0). */
data class CopyDraft(val id: Int = 0, val code: String = "", val status: String = "", val notes: String = "", val originalStatus: String = "")

data class CopiesUiState(
    val copies: List<StaffCopy> = emptyList(),
    val editing: CopyDraft? = null,
    val deleting: StaffCopy? = null,
    val saving: Boolean = false,
    val formError: StaffMessage? = null,
    val message: StaffMessage? = null,
    val downloading: Boolean = false,
    val export: File? = null,
    val forbidden: Boolean = false,
    /** The reloaded record after a copy change, for the editor's counters. */
    val refreshed: StaffBook? = null,
)

/**
 * Per-copy management inside the book editor (Mobile API 1.7.0): edit status and notes, delete,
 * add one copy with an explicit inventory code, and the label PDFs. After every change the book
 * is read again, so the copy list and the availability counters always come from the server.
 */
@HiltViewModel
class StaffCopiesViewModel @Inject constructor(
    private val staff: StaffRepository,
    private val content: StaffContentRepository,
    private val files: StaffFilesRepository,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val bookId: Int = savedState.get<Int>(Routes.ARG_BOOK_ID) ?: 0
    private val _state = MutableStateFlow(CopiesUiState())
    val state: StateFlow<CopiesUiState> = _state.asStateFlow()

    /** The editor already loaded the record: start from its copies. */
    fun seed(copies: List<StaffCopy>) = _state.update { it.copy(copies = copies) }

    fun reload() {
        if (bookId <= 0) return
        viewModelScope.launch {
            when (val res = staff.book(bookId)) {
                is ApiResult.Success -> _state.update { it.copy(copies = res.data.copies, refreshed = res.data) }
                is ApiResult.Failure -> _state.update { it.copy(forbidden = res.isForbidden()) }
            }
        }
    }

    fun consumeRefreshed() = _state.update { it.copy(refreshed = null) }

    fun startAdd() = _state.update { it.copy(editing = CopyDraft(status = "disponibile"), formError = null) }

    fun startEdit(copy: StaffCopy) = _state.update {
        it.copy(editing = CopyDraft(copy.id, copy.inventoryNumber.orEmpty(), copy.status.orEmpty(), copy.notes.orEmpty(), copy.status.orEmpty()), formError = null)
    }

    fun edit(transform: (CopyDraft) -> CopyDraft) = _state.update { s -> s.copy(editing = s.editing?.let(transform), formError = null) }
    fun closeEditor() = _state.update { it.copy(editing = null, formError = null) }

    fun save() {
        val draft = _state.value.editing ?: return
        if (_state.value.saving) return
        _state.update { it.copy(saving = true, formError = null) }
        viewModelScope.launch {
            val failure: ApiResult.Failure? = if (draft.id == 0) {
                when (val res = staff.addCopy(bookId, CopyCodeCodec.normalize(draft.code), draft.status, draft.notes)) {
                    is ApiResult.Success -> null
                    is ApiResult.Failure -> res
                }
            } else {
                // Send the status only when it changed: an unchanged circulation state is not a request.
                val status = draft.status.takeIf { it != draft.originalStatus }
                when (val res = content.updateCopy(draft.id, status, draft.notes)) {
                    is ApiResult.Success -> null
                    is ApiResult.Failure -> res
                }
            }
            if (failure == null) {
                _state.update {
                    it.copy(saving = false, editing = null, message = StaffMessage(if (draft.id == 0) R.string.staff_copy_added else R.string.staff_copy_saved))
                }
                reload()
            } else {
                _state.update {
                    it.copy(
                        saving = false, forbidden = failure.isForbidden(),
                        formError = if (failure.isDuplicate()) StaffMessage(R.string.staff_copy_code_taken, args = listOf(draft.code.trim()))
                        else failure.toStaffMessage(R.string.staff_error_save),
                    )
                }
            }
        }
    }

    fun askDelete(copy: StaffCopy) = _state.update { it.copy(deleting = copy, editing = null) }
    fun dismissDelete() = _state.update { it.copy(deleting = null) }

    fun confirmDelete() {
        val copy = _state.value.deleting ?: return
        _state.update { it.copy(deleting = null) }
        viewModelScope.launch {
            when (val res = content.deleteCopy(copy.id)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(message = StaffMessage(R.string.staff_copy_deleted)) }
                    reload()
                }
                is ApiResult.Failure -> _state.update { it.copy(forbidden = res.isForbidden(), message = res.toStaffMessage(R.string.staff_error_save)) }
            }
        }
    }

    /** `book.pdf` (the record's spine label) or `copies.pdf` (all copies, or one with [copyId]). */
    fun downloadLabels(copies: Boolean, copyId: Int? = null) {
        if (_state.value.downloading) return
        _state.update { it.copy(downloading = true) }
        val path = if (copies) "staff/books/$bookId/labels/copies.pdf" + (copyId?.let { "?copy_id=$it" } ?: "") else "staff/books/$bookId/labels/book.pdf"
        val name = if (copies) "etichette_copie_$bookId.pdf" else "etichetta_$bookId.pdf"
        viewModelScope.launch {
            val res = files.download(path, name)
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(downloading = false, export = res.data)
                    is ApiResult.Failure -> it.copy(downloading = false, forbidden = res.isForbidden(), message = res.toStaffMessage(R.string.staff_download_failed))
                }
            }
        }
    }

    fun closeExport() = _state.update { it.copy(export = null) }
    fun consumeMessage() = _state.update { it.copy(message = null) }
}

/** Label for a copy state: the server's `status_label`, else the lookup label, else the raw value. */
internal fun copyStateLabel(copy: StaffCopy, lookups: StaffLookups): String =
    copy.statusLabel?.takeIf { it.isNotBlank() }
        ?: lookups.copyStateOptions.firstOrNull { it.value == copy.status }?.label
        ?: copy.status.orEmpty()

/**
 * The per-copy list of the book editor. Shown only when the server offers per-copy management
 * (`staff_copies`); label buttons only with `staff_labels`. Messages go to [onMessage] (the
 * editor's snackbar); counter changes to [onBookRefreshed].
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun StaffCopiesSection(
    copies: List<StaffCopy>,
    copiesTotal: Int,
    lookups: StaffLookups,
    onBookRefreshed: (StaffBook) -> Unit,
    onMessage: (String) -> Unit,
    onForbidden: () -> Unit,
    openCopyId: Int = 0,
    onCopyOpened: () -> Unit = {},
) {
    val app: AppViewModel = hiltViewModel()
    val areas by app.staffAreas.collectAsStateWithLifecycle()
    if (!areas.copies && !areas.labels) return
    val vm: StaffCopiesViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.seed(copies) }
    // The editor's "Add copies" changed the total: read the copies again.
    LaunchedEffect(copiesTotal) { if (copiesTotal != state.copies.size) vm.reload() }
    LaunchedEffect(state.refreshed) { state.refreshed?.let { onBookRefreshed(it); vm.consumeRefreshed() } }
    // Arriving from a receipt: scroll to the copies and open the new copy's editor at once.
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(openCopyId, state.copies) {
        val copy = state.copies.firstOrNull { it.id == openCopyId } ?: return@LaunchedEffect
        onCopyOpened()
        bringIntoView.bringIntoView()
        // Labels alone show the section but not the per-copy editor: open it only with copies.
        if (areas.copies) vm.startEdit(copy)
    }
    LaunchedEffect(state.forbidden) { if (state.forbidden) onForbidden() }
    val message = state.message?.resolve()
    LaunchedEffect(state.message) { if (!message.isNullOrBlank()) { onMessage(message); vm.consumeMessage() } }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.bringIntoViewRequester(bringIntoView).testTag("copies-section")) {
        if (areas.copies) {
            state.copies.forEach { copy ->
                Surface(
                    onClick = { vm.startEdit(copy) },
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth().testTag("copy-${copy.id}"),
                ) {
                    Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(copy.inventoryNumber ?: stringResource(R.string.staff_copy_no_code), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                listOfNotNull(copyStateLabel(copy, lookups).takeIf { it.isNotBlank() }, copy.notes?.takeIf { it.isNotBlank() }).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.staff_copy_edit), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            SecondaryButton(stringResource(R.string.staff_copy_add_with_code), onClick = vm::startAdd, leadingIcon = Icons.Outlined.Add, modifier = Modifier.fillMaxWidth())
        }
        if (areas.labels) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                SecondaryButton(stringResource(R.string.staff_labels_book_pdf), onClick = { vm.downloadLabels(copies = false) },
                    leadingIcon = Icons.AutoMirrored.Outlined.Label, enabled = !state.downloading)
                SecondaryButton(stringResource(R.string.staff_labels_copies_pdf), onClick = { vm.downloadLabels(copies = true) },
                    leadingIcon = Icons.AutoMirrored.Outlined.Label, enabled = !state.downloading)
            }
        }
    }

    state.editing?.let { CopyEditorSheet(it, state, lookups, vm, labels = areas.labels) }
    state.deleting?.let { copy ->
        ConfirmDialog(
            title = stringResource(R.string.staff_copy_delete),
            body = stringResource(R.string.staff_copy_delete_body, copy.inventoryNumber ?: copy.id.toString()),
            confirmLabel = stringResource(R.string.staff_copy_delete),
            onConfirm = vm::confirmDelete, onDismiss = vm::dismissDelete,
        )
    }
    state.export?.let { ExportFileDialog(it, "application/pdf", stringResource(R.string.staff_labels_ready), onDismiss = vm::closeExport) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CopyEditorSheet(draft: CopyDraft, state: CopiesUiState, lookups: StaffLookups, vm: StaffCopiesViewModel, labels: Boolean) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val editable = lookups.editableCopyStateOptions
    // A circulation state (on loan, held) can be shown but not picked.
    val options = if (draft.originalStatus.isNotBlank() && editable.none { it.value == draft.originalStatus }) {
        listOf(LookupOption(draft.originalStatus, lookups.copyStateOptions.firstOrNull { it.value == draft.originalStatus }?.label ?: draft.originalStatus)) + editable
    } else editable
    ModalBottomSheet(onDismissRequest = { if (!state.saving) vm.closeEditor() }, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(stringResource(if (draft.id == 0) R.string.staff_copy_add_with_code else R.string.staff_copy_edit), style = MaterialTheme.typography.headlineSmall)
            state.formError?.let { InlineErrorBanner(message = it.resolve()) }
            if (draft.id == 0) {
                PinakesTextField(
                    value = draft.code, onValueChange = { v -> vm.edit { it.copy(code = v.take(CopyCodeCodec.MAX_LENGTH)) } },
                    label = stringResource(R.string.staff_copy_code), supportingText = stringResource(R.string.staff_copy_code_new_hint),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii), modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(stringResource(R.string.staff_copy_code_value, draft.code.ifBlank { draft.id.toString() }), style = MaterialTheme.typography.titleMedium)
            }
            LookupField(
                label = stringResource(R.string.staff_copy_status), value = draft.status,
                displayValue = options.firstOrNull { it.value == draft.status }?.label ?: draft.status,
                options = options.filter { it.value in editable.map(LookupOption::value) }, editable = false, allowClear = false,
                onValueChange = { v -> vm.edit { it.copy(status = v) } }, modifier = Modifier.fillMaxWidth(),
            )
            PinakesTextField(
                value = draft.notes, onValueChange = { v -> vm.edit { it.copy(notes = v) } }, label = stringResource(R.string.staff_field_notes),
                singleLine = false, maxLength = 1000, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).testTag("copy-notes"),
            )
            PrimaryButton(stringResource(R.string.action_save), onClick = vm::save, loading = state.saving, modifier = Modifier.fillMaxWidth().testTag("copy-save"))
            if (draft.id > 0) {
                if (labels) {
                    SecondaryButton(stringResource(R.string.staff_labels_this_copy), onClick = { vm.downloadLabels(copies = true, copyId = draft.id) },
                        enabled = !state.downloading, modifier = Modifier.fillMaxWidth())
                }
                PinakesTextButton(stringResource(R.string.staff_copy_delete), onClick = {
                    vm.askDelete(StaffCopy(id = draft.id, inventoryNumber = draft.code))
                }, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}
