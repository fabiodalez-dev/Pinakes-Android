package com.pinakes.app.ui.screens.staff.s090.patrons

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pinakes.app.R
import com.pinakes.app.data.model.UserImportJob
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.repository.Staff090PatronsRepository
import com.pinakes.app.data.repository.StaffFilesRepository
import com.pinakes.app.data.repository.isForbidden
import com.pinakes.app.data.staff.ChunkStep
import com.pinakes.app.data.staff.UserImportRules
import com.pinakes.app.ui.common.AppViewModel
import com.pinakes.app.ui.components.InlineErrorBanner
import com.pinakes.app.ui.components.PinakesTopBar
import com.pinakes.app.ui.components.PrimaryButton
import com.pinakes.app.ui.components.SecondaryButton
import com.pinakes.app.ui.screens.staff.ExportFileDialog
import com.pinakes.app.ui.screens.staff.ForbiddenExit
import com.pinakes.app.ui.screens.staff.FormSectionTitle
import com.pinakes.app.ui.screens.staff.InfoCard
import com.pinakes.app.ui.screens.staff.InfoLine
import com.pinakes.app.ui.screens.staff.LeaveWhen
import com.pinakes.app.ui.screens.staff.PickerRow
import com.pinakes.app.ui.screens.staff.ProgressLine
import com.pinakes.app.ui.screens.staff.StaffMessage
import com.pinakes.app.ui.screens.staff.StaffSnackbarEffect
import com.pinakes.app.ui.screens.staff.SwitchRow
import com.pinakes.app.ui.screens.staff.resolve
import com.pinakes.app.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A picked CSV, read into memory (at most 5 MB). */
class PickedCsv(val name: String, val mime: String?, val bytes: ByteArray)

data class UserImportUiState(
    val file: PickedCsv? = null,
    val delimiter: String = "",
    val job: UserImportJob? = null,
    /** The mapping being edited: one field key per header ("" = ignored). */
    val mapping: List<String> = emptyList(),
    val sendInvites: Boolean = false,
    val uploading: Boolean = false,
    val starting: Boolean = false,
    val running: Boolean = false,
    val error: StaffMessage? = null,
    /** The job ended elsewhere (410 gone) or here (cancel): never resumed again. */
    val ended: Boolean = false,
    val export: File? = null,
    val message: StaffMessage? = null,
    val forbidden: Boolean = false,
)

/**
 * The patron CSV import: upload → preview and suggested mapping → start → chunks of 10 rows,
 * always from the server's `processed` (out_of_order resumes there, job_busy waits, gone
 * stops) → counters and the error report. The job id is kept in the saved state, so the run
 * resumes after process death where the server is.
 */
@HiltViewModel
class Staff090UserImportViewModel @Inject constructor(
    private val patrons: Staff090PatronsRepository,
    private val files: StaffFilesRepository,
    @ApplicationContext private val context: Context,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(UserImportUiState())
    val state: StateFlow<UserImportUiState> = _state.asStateFlow()
    private var loop: Job? = null

    init {
        savedState.get<String>(KEY_JOB)?.let { id ->
            viewModelScope.launch {
                when (val res = patrons.userImport(id)) {
                    is ApiResult.Success -> {
                        setJob(res.data)
                        if (res.data.started && !UserImportRules.isFinished(res.data)) resume()
                    }
                    is ApiResult.Failure -> savedState.remove<String>(KEY_JOB)
                }
            }
        }
    }

    private fun setJob(job: UserImportJob) {
        savedState[KEY_JOB] = job.id
        _state.update { it.copy(job = job, mapping = if (it.job?.id == job.id && !job.started) it.mapping.ifEmpty { UserImportRules.aligned(job) } else UserImportRules.aligned(job)) }
    }

    fun setDelimiter(value: String) = _state.update { it.copy(delimiter = value) }

    fun onPicked(uri: Uri?) {
        uri ?: return
        viewModelScope.launch {
            val read = withContext(Dispatchers.IO) { runCatching { read(uri) }.getOrNull() }
            _state.update {
                when {
                    read == null -> it.copy(error = StaffMessage(R.string.staff_import_read_failed), file = null)
                    read.bytes.isEmpty() && read.name.isEmpty() -> it.copy(error = StaffMessage(R.string.staff090_patrons_import_too_big), file = null)
                    !UserImportRules.acceptsFileName(read.name) -> it.copy(error = StaffMessage(R.string.staff090_patrons_import_wrong_ext), file = null)
                    else -> it.copy(file = read, error = null)
                }
            }
        }
    }

    /** The document's bytes; an empty name and body say "too big" (the server takes 5 MB). */
    private fun read(uri: Uri): PickedCsv? {
        val resolver = context.contentResolver
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "patrons.csv"
        var size = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { i -> c.getString(i)?.let { name = it } }
                c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { i -> if (!c.isNull(i)) size = c.getLong(i) }
            }
        }
        if (size > UserImportRules.MAX_BYTES) return PickedCsv("", null, ByteArray(0))
        val out = java.io.ByteArrayOutputStream()
        resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                if (total > UserImportRules.MAX_BYTES) return PickedCsv("", null, ByteArray(0))
                out.write(buffer, 0, n)
            }
        } ?: return null
        return PickedCsv(name, resolver.getType(uri), out.toByteArray())
    }

    fun upload() {
        val s = _state.value
        val file = s.file ?: return
        if (s.uploading) return
        _state.update { it.copy(uploading = true, error = null) }
        viewModelScope.launch {
            val res = patrons.createUserImport(file.name, file.bytes, file.mime, s.delimiter)
            when (res) {
                is ApiResult.Success -> {
                    _state.update { it.copy(uploading = false, file = null, ended = false, sendInvites = false) }
                    setJob(res.data)
                }
                is ApiResult.Failure -> _state.update { it.copy(uploading = false, forbidden = res.isForbidden(), error = res.toPatronsMessage(R.string.staff_import_failed)) }
            }
        }
    }

    fun assign(column: Int, key: String) = _state.update { it.copy(mapping = UserImportRules.assign(it.mapping, column, key)) }
    fun setSendInvites(value: Boolean) = _state.update { it.copy(sendInvites = value) }

    fun start() {
        val s = _state.value
        val job = s.job ?: return
        if (s.starting || !UserImportRules.canStart(job, s.mapping)) return
        _state.update { it.copy(starting = true, error = null) }
        viewModelScope.launch {
            val res = patrons.startUserImport(job.id, s.mapping, s.sendInvites)
            when (res) {
                is ApiResult.Success -> {
                    _state.update { it.copy(starting = false) }
                    setJob(res.data)
                    resume()
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(starting = false, forbidden = res.isForbidden(), error = res.toPatronsMessage(R.string.staff_import_failed)) }
                    // Started already (another device, or a lost answer): drive it from where it is.
                    if (res.code == "already_started") refresh(thenResume = true)
                }
            }
        }
    }

    private suspend fun refreshNow(): UserImportJob? {
        val id = _state.value.job?.id ?: return null
        return (patrons.userImport(id) as? ApiResult.Success)?.data?.also { setJob(it) }
    }

    private fun refresh(thenResume: Boolean) {
        viewModelScope.launch {
            val job = refreshNow()
            if (thenResume && job != null && job.started && !UserImportRules.isFinished(job)) resume()
        }
    }

    fun resume() {
        val job = _state.value.job ?: return
        if (loop?.isActive == true || !job.started || UserImportRules.isFinished(job) || _state.value.ended) return
        _state.update { it.copy(running = true, error = null) }
        loop = viewModelScope.launch {
            var step: ChunkStep = UserImportRules.next(job)
            var busy = 0
            while (isActive) {
                val start = when (val s = step) {
                    is ChunkStep.Next -> s.start
                    is ChunkStep.Wait -> { delay(3_000); s.start }
                    ChunkStep.Done -> break
                    is ChunkStep.Stop -> {
                        _state.update { it.copy(forbidden = s.failure.isForbidden(), error = s.failure.toPatronsMessage(R.string.staff_import_failed)) }
                        // Ended elsewhere, cancelled meanwhile, or its file is gone: read the final state.
                        if (s.failure.code in setOf("gone", "job_cancelled", "file_missing") || s.failure.httpStatus == 410) {
                            _state.update { it.copy(ended = true) }
                            refreshNow()
                        }
                        break
                    }
                }
                when (val res = patrons.userImportChunk(job.id, start)) {
                    is ApiResult.Success -> {
                        busy = 0
                        setJob(res.data)
                        step = UserImportRules.next(res.data)
                    }
                    is ApiResult.Failure -> {
                        if (res.code == "job_busy") busy++
                        step = UserImportRules.afterFailure(res, start, busy)
                        res.meta?.processed?.let { p -> _state.update { s -> s.copy(job = s.job?.copy(processed = p)) } }
                    }
                }
            }
            _state.update { it.copy(running = false) }
        }
    }

    fun pause() {
        loop?.cancel()
        _state.update { it.copy(running = false) }
    }

    fun cancel() {
        val job = _state.value.job ?: return
        pause()
        viewModelScope.launch {
            when (val res = patrons.cancelUserImport(job.id)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(ended = true, message = StaffMessage(R.string.staff090_patrons_import_cancelled)) }
                    refreshNow()
                }
                is ApiResult.Failure -> _state.update { it.copy(forbidden = res.isForbidden(), error = res.toPatronsMessage(R.string.staff_error_save)) }
            }
        }
    }

    /** Back to the file picker (the finished job stays in the server's history). */
    fun newImport() {
        if (_state.value.running) return
        savedState.remove<String>(KEY_JOB)
        _state.update { UserImportUiState(delimiter = it.delimiter) }
    }

    fun downloadExample() = download("staff/user-imports/example.csv", "esempio_import_utenti.csv")

    fun downloadErrors() {
        val job = _state.value.job ?: return
        download("staff/user-imports/${job.id}/errors.csv", "errori_import_utenti.csv")
    }

    private fun download(path: String, name: String) {
        viewModelScope.launch {
            val res = files.download(path, name)
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(export = res.data)
                    is ApiResult.Failure -> it.copy(forbidden = res.isForbidden(), message = res.toPatronsMessage(R.string.staff_download_failed))
                }
            }
        }
    }

    fun closeExport() = _state.update { it.copy(export = null) }
    fun consumeMessage() = _state.update { it.copy(message = null) }

    private companion object {
        const val KEY_JOB = "userImportJob"
    }
}

internal fun delimiterLabel(value: String): Int = when (value) {
    ";" -> R.string.staff090_patrons_delimiter_semicolon
    "," -> R.string.staff090_patrons_delimiter_comma
    "tab" -> R.string.staff090_patrons_delimiter_tab
    else -> R.string.staff090_patrons_delimiter_auto
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun Staff090UserImportScreen(onNavigateUp: () -> Unit) {
    val vm: Staff090UserImportViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val app: AppViewModel = hiltViewModel()
    val areas by app.staffAreas.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    ForbiddenExit(state.forbidden, onNavigateUp)
    LeaveWhen(!areas.s090.userImport, onNavigateUp)
    StaffSnackbarEffect(state.message, snackbar, vm::consumeMessage)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), vm::onPicked)

    Scaffold(
        topBar = { PinakesTopBar(title = stringResource(R.string.staff090_user_import), onNavigateUp = onNavigateUp) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val job = state.job
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = 48.dp)) {
            if (job == null) {
                item(key = "form") {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.sm)) {
                        Text(stringResource(R.string.staff090_patrons_import_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        state.error?.let { InlineErrorBanner(message = it.resolve()) }
                        PickerRow(
                            label = stringResource(R.string.staff_import_file), value = state.file?.name, placeholder = stringResource(R.string.staff_import_choose_file),
                            onClick = { picker.launch(MIME_TYPES) },
                        )
                        Text(stringResource(R.string.staff090_patrons_delimiter), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            UserImportRules.DELIMITERS.forEach { d ->
                                FilterChip(
                                    selected = state.delimiter == d, onClick = { vm.setDelimiter(d) }, label = { Text(stringResource(delimiterLabel(d))) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    ),
                                )
                            }
                        }
                        PrimaryButton(
                            stringResource(R.string.staff090_patrons_import_upload), onClick = vm::upload, loading = state.uploading, enabled = state.file != null,
                            leadingIcon = Icons.Outlined.UploadFile, modifier = Modifier.fillMaxWidth().testTag("user-import-upload"),
                        )
                        SecondaryButton(
                            stringResource(R.string.staff_import_example), onClick = vm::downloadExample, leadingIcon = Icons.Outlined.Download,
                            modifier = Modifier.fillMaxWidth().testTag("user-import-example"),
                        )
                    }
                }
            } else {
                item(key = "job") { JobHeader(job, state, vm) }
                if (!job.started && !UserImportRules.isFinished(job)) {
                    item(key = "preview-title") { FormSectionTitle(stringResource(R.string.staff090_patrons_import_preview)) }
                    item(key = "preview") { PreviewTable(job) }
                    item(key = "mapping-title") { FormSectionTitle(stringResource(R.string.staff090_patrons_import_mapping)) }
                    itemsIndexed(job.headers, key = { i, _ -> "col-$i" }) { i, header -> MappingRow(i, header, job, state.mapping, vm) }
                    item(key = "start") {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.sm)) {
                            val missing = UserImportRules.missingRequired(job.fields, state.mapping)
                            if (missing.isNotEmpty()) {
                                Text(
                                    stringResource(R.string.staff090_patrons_import_missing, missing.joinToString(", ") { it.label }),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                                )
                            }
                            SwitchRow(
                                stringResource(R.string.staff090_patrons_import_invites), state.sendInvites, vm::setSendInvites,
                                subtitle = stringResource(R.string.staff090_patrons_import_invites_hint),
                            )
                            PrimaryButton(
                                stringResource(R.string.staff090_patrons_import_start), onClick = vm::start, loading = state.starting,
                                enabled = UserImportRules.canStart(job, state.mapping), modifier = Modifier.fillMaxWidth().testTag("user-import-start"),
                            )
                            SecondaryButton(stringResource(R.string.staff_import_cancel), onClick = vm::cancel, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
                if (job.errors.isNotEmpty()) {
                    item(key = "errors-title") { FormSectionTitle(stringResource(R.string.staff_import_errors)) }
                    itemsIndexed(job.errors, key = { i, _ -> "err-$i" }) { _, e ->
                        Text(
                            stringResource(R.string.staff090_patrons_import_error_line, e.line, listOf(e.title, e.message).filter { it.isNotBlank() }.joinToString(": ")),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (e.type == "warning") MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(vertical = Spacing.xxs),
                        )
                    }
                }
            }
        }
    }
    state.export?.let { ExportFileDialog(it, "text/csv", stringResource(R.string.staff090_user_import), onDismiss = vm::closeExport) }
}

private val MIME_TYPES = arrayOf("text/csv", "text/comma-separated-values", "text/plain", "text/tab-separated-values", "application/vnd.ms-excel")

@Composable
private fun JobHeader(job: UserImportJob, state: UserImportUiState, vm: Staff090UserImportViewModel) {
    val finished = UserImportRules.isFinished(job) || state.ended
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.sm)) {
        Text(job.fileName, style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(
                when {
                    job.status == "cancelled" -> R.string.staff_import_status_cancelled
                    job.status == "failed" -> R.string.staff_import_status_failed
                    state.ended && !UserImportRules.isFinished(job) -> R.string.staff_import_status_gone
                    finished -> R.string.staff_import_status_done
                    !job.started -> R.string.staff090_patrons_import_ready
                    state.running -> R.string.staff_import_status_running
                    else -> R.string.staff_import_status_paused
                },
            ),
            style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("user-import-status"),
        )
        job.notice?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        state.error?.let { InlineErrorBanner(message = it.resolve()) }
        if (job.started) {
            ProgressLine(job.processed, job.totalRows, stringResource(R.string.staff_import_progress, job.processed, job.totalRows))
            InfoCard(Modifier.testTag("user-import-counters")) {
                InfoLine(stringResource(R.string.staff090_patrons_import_created), job.created.toString())
                InfoLine(stringResource(R.string.staff090_patrons_import_duplicates), job.duplicates.toString())
                InfoLine(stringResource(R.string.staff090_patrons_import_failed), job.failed.toString())
                if (job.sendInvites == true) {
                    InfoLine(stringResource(R.string.staff090_patrons_import_invited), job.invited.toString())
                    InfoLine(stringResource(R.string.staff090_patrons_import_invite_failed), job.inviteFailed.toString())
                }
            }
            job.logError?.takeIf { it.isNotBlank() }?.let { InlineErrorBanner(message = it) }
            if (!finished) {
                if (state.running) SecondaryButton(stringResource(R.string.staff_import_pause), onClick = vm::pause, modifier = Modifier.fillMaxWidth())
                else PrimaryButton(stringResource(R.string.staff_import_resume), onClick = vm::resume, modifier = Modifier.fillMaxWidth())
                SecondaryButton(stringResource(R.string.staff_import_cancel), onClick = vm::cancel, modifier = Modifier.fillMaxWidth())
            }
        } else {
            InfoLine(stringResource(R.string.staff090_patrons_import_rows), job.totalRows.toString())
        }
        if (job.reportAvailable) {
            SecondaryButton(
                stringResource(R.string.staff_import_errors_csv), onClick = vm::downloadErrors, leadingIcon = Icons.Outlined.Download,
                modifier = Modifier.fillMaxWidth().testTag("user-import-errors"),
            )
        }
        if (finished && !state.running) {
            PrimaryButton(stringResource(R.string.staff090_patrons_import_new), onClick = vm::newImport, modifier = Modifier.fillMaxWidth().testTag("user-import-new"))
        }
    }
}

/** The first rows as the server read them, scrolling sideways on a narrow screen. */
@Composable
private fun PreviewTable(job: UserImportJob) {
    InfoCard(Modifier.testTag("user-import-preview")) {
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            val rows = listOf(job.headers) + job.preview
            rows.forEachIndexed { r, row ->
                Row(Modifier.padding(vertical = Spacing.xxs)) {
                    job.headers.indices.forEach { c ->
                        Text(
                            row.getOrNull(c).orEmpty(), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            fontWeight = if (r == 0) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.width(132.dp).padding(end = Spacing.sm),
                        )
                    }
                }
            }
        }
    }
}

/** One CSV column and the field it fills; required fields carry an asterisk. */
@Composable
private fun MappingRow(index: Int, header: String, job: UserImportJob, mapping: List<String>, vm: Staff090UserImportViewModel) {
    var open by remember { mutableStateOf(false) }
    val current = mapping.getOrNull(index).orEmpty()
    val label = { key: String -> job.fields.firstOrNull { it.key == key }?.let { f -> if (f.required) "${f.label} *" else f.label } }
    val ignore = stringResource(R.string.staff090_patrons_import_ignore)
    Box(Modifier.padding(vertical = Spacing.xs).testTag("mapping-$index")) {
        PickerRow(label = header.ifBlank { stringResource(R.string.staff090_patrons_import_column, index + 1) }, value = label(current) ?: ignore, placeholder = ignore, onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(ignore) }, onClick = { vm.assign(index, ""); open = false })
            job.fields.forEach { f ->
                DropdownMenuItem(
                    text = { Text(if (f.required) "${f.label} *" else f.label) },
                    onClick = { vm.assign(index, f.key); open = false },
                    modifier = Modifier.testTag("field-$index-${f.key}"),
                )
            }
        }
    }
}
