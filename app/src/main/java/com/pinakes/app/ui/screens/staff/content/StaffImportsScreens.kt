package com.pinakes.app.ui.screens.staff.content

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
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
import com.pinakes.app.data.model.ImportHistoryRow
import com.pinakes.app.data.model.ImportJob
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.repository.ImportKind
import com.pinakes.app.data.repository.StaffContentRepository
import com.pinakes.app.data.repository.StaffFilesRepository
import com.pinakes.app.data.repository.isForbidden
import com.pinakes.app.data.staff.ChunkStep
import com.pinakes.app.data.staff.ImportProgress
import com.pinakes.app.ui.common.AppViewModel
import com.pinakes.app.ui.common.DateFormat
import com.pinakes.app.ui.components.ErrorState
import com.pinakes.app.ui.components.InlineErrorBanner
import com.pinakes.app.ui.components.LoadingState
import com.pinakes.app.ui.components.PinakesTopBar
import com.pinakes.app.ui.components.PrimaryButton
import com.pinakes.app.ui.components.SecondaryButton
import com.pinakes.app.ui.navigation.Routes
import com.pinakes.app.ui.screens.staff.ExportFileDialog
import com.pinakes.app.ui.screens.staff.ForbiddenExit
import com.pinakes.app.ui.screens.staff.FormSectionTitle
import com.pinakes.app.ui.screens.staff.InfoCard
import com.pinakes.app.ui.screens.staff.InfoLine
import com.pinakes.app.ui.screens.staff.LeaveWhen
import com.pinakes.app.ui.screens.staff.PickerRow
import com.pinakes.app.ui.screens.staff.ProgressLine
import com.pinakes.app.ui.screens.staff.StaffActionRow
import com.pinakes.app.ui.screens.staff.StaffMessage
import com.pinakes.app.ui.screens.staff.StaffSnackbarEffect
import com.pinakes.app.ui.screens.staff.SwitchRow
import com.pinakes.app.ui.screens.staff.resolve
import com.pinakes.app.ui.screens.staff.toStaffMessage
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

/** The field families a CSV import may overwrite on existing records (`CsvImportController::UPDATE_FIELD_FAMILIES`). */
internal val UPDATE_FAMILIES = listOf(
    "authors" to R.string.staff_import_field_authors, "contributors" to R.string.staff_import_field_contributors,
    "publisher" to R.string.staff_import_field_publisher, "genre" to R.string.staff_import_field_genre,
    "keywords" to R.string.staff_import_field_keywords, "description" to R.string.staff_import_field_description,
    "isbn" to R.string.staff_import_field_isbn, "ean" to R.string.staff_import_field_ean,
    "titolo" to R.string.staff_import_field_title, "sottotitolo" to R.string.staff_import_field_subtitle,
    "anno" to R.string.staff_import_field_year, "lingua" to R.string.staff_import_field_language,
    "edizione" to R.string.staff_import_field_edition, "pagine" to R.string.staff_import_field_pages,
    "formato" to R.string.staff_import_field_format, "prezzo" to R.string.staff_import_field_price,
    "collana" to R.string.staff_import_field_series, "dewey" to R.string.staff_import_field_dewey,
)

/** A picked document, read into memory (at most 20 MB). */
data class PickedFile(val name: String, val mime: String?, val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean = other is PickedFile && other.name == name && other.bytes.contentEquals(bytes)
    override fun hashCode(): Int = name.hashCode() * 31 + bytes.contentHashCode()
}

data class ImportsUiState(
    val kind: ImportKind = ImportKind.CSV,
    val file: PickedFile? = null,
    val scraping: Boolean = false,
    val families: Set<String> = UPDATE_FAMILIES.map { it.first }.toSet(),
    val jobs: List<ImportJob> = emptyList(),
    val history: List<ImportHistoryRow> = emptyList(),
    val loading: Boolean = true,
    val uploading: Boolean = false,
    val error: StaffMessage? = null,
    val message: StaffMessage? = null,
    val forbidden: Boolean = false,
    val startedJob: String? = null,
    val export: File? = null,
    val exportMime: String = "text/csv",
)

@HiltViewModel
class StaffImportsViewModel @Inject constructor(
    private val content: StaffContentRepository,
    private val files: StaffFilesRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _state = MutableStateFlow(ImportsUiState())
    val state: StateFlow<ImportsUiState> = _state.asStateFlow()

    fun reload() {
        viewModelScope.launch {
            val res = content.imports()
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(jobs = res.data.jobs.filterNot(ImportProgress::isFinished), history = res.data.history, loading = false)
                    is ApiResult.Failure -> it.copy(loading = false, forbidden = res.isForbidden(), message = res.toStaffMessage(R.string.staff_error_load))
                }
            }
        }
    }

    fun setKind(kind: ImportKind) = _state.update { it.copy(kind = kind, error = null) }
    fun setScraping(value: Boolean) = _state.update { it.copy(scraping = value) }
    fun toggleFamily(family: String) = _state.update { s -> s.copy(families = if (family in s.families) s.families - family else s.families + family) }

    /** Read the picked document; refuse what the server would refuse anyway (size, extension). */
    fun onPicked(uri: Uri?) {
        uri ?: return
        viewModelScope.launch {
            val read = withContext(Dispatchers.IO) { runCatching { read(uri) }.getOrNull() }
            _state.update {
                when {
                    read == null -> it.copy(error = StaffMessage(R.string.staff_import_read_failed), file = null)
                    read.tooBig -> it.copy(error = StaffMessage(R.string.staff_import_too_big), file = null)
                    else -> it.copy(file = read.file, error = null)
                }
            }
        }
    }

    private class Read(val file: PickedFile?, val tooBig: Boolean)

    private fun read(uri: Uri): Read? {
        val resolver = context.contentResolver
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "import.csv"
        var size = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { i -> c.getString(i)?.let { name = it } }
                c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { i -> if (!c.isNull(i)) size = c.getLong(i) }
            }
        }
        if (size > ImportProgress.MAX_BYTES) return Read(null, tooBig = true)
        val out = java.io.ByteArrayOutputStream()
        resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                // The provider may not report a size: stop reading as soon as the limit is passed.
                if (total > ImportProgress.MAX_BYTES) return Read(null, tooBig = true)
                out.write(buffer, 0, n)
            }
        } ?: return null
        return Read(PickedFile(name, resolver.getType(uri), out.toByteArray()), tooBig = false)
    }

    fun start() {
        val s = _state.value
        val file = s.file ?: return
        if (s.uploading) return
        if (!ImportProgress.acceptsFileName(file.name, s.kind == ImportKind.LIBRARYTHING)) {
            _state.update { it.copy(error = StaffMessage(if (s.kind == ImportKind.CSV) R.string.staff_import_wrong_ext_csv else R.string.staff_import_wrong_ext_lt)) }
            return
        }
        _state.update { it.copy(uploading = true, error = null) }
        viewModelScope.launch {
            val families = s.families.takeIf { it.size < UPDATE_FAMILIES.size }?.let { set -> UPDATE_FAMILIES.map { it.first }.filter { it in set } }
            val res = content.createImport(s.kind, file.name, file.bytes, file.mime, s.scraping, families)
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(uploading = false, file = null, startedJob = res.data.id)
                    is ApiResult.Failure -> it.copy(uploading = false, forbidden = res.isForbidden(), error = res.toStaffMessage(R.string.staff_import_failed))
                }
            }
        }
    }

    fun consumeStarted() = _state.update { it.copy(startedJob = null) }

    fun downloadExample() = download("staff/imports/example.csv", "esempio_import.csv")

    /** The whole catalogue as a LibraryThing TSV (core export, 15 a minute). */
    fun exportLibraryThing() = download("staff/exports/librarything.tsv", "librarything.tsv", "text/tab-separated-values")

    fun downloadErrors(row: ImportHistoryRow) = download("staff/imports/history/${row.importId}/errors.csv", "errori_${row.importId}.csv")

    private fun download(path: String, name: String, mime: String = "text/csv") {
        viewModelScope.launch {
            val res = files.download(path, name)
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(export = res.data, exportMime = mime)
                    is ApiResult.Failure -> it.copy(forbidden = res.isForbidden(), message = res.toStaffMessage(R.string.staff_download_failed))
                }
            }
        }
    }

    fun closeExport() = _state.update { it.copy(export = null) }
    fun consumeMessage() = _state.update { it.copy(message = null) }
}

/** Upload a CSV (or LibraryThing export) and follow it; open jobs resume; past imports with their error file. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StaffImportsScreen(onNavigateUp: () -> Unit, onOpenJob: (String) -> Unit) {
    val vm: StaffImportsViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val app: AppViewModel = hiltViewModel()
    val areas by app.staffAreas.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    ForbiddenExit(state.forbidden, onNavigateUp)
    LeaveWhen(!areas.imports, onNavigateUp)
    StaffSnackbarEffect(state.message, snackbar, vm::consumeMessage)
    LaunchedEffect(state.startedJob) { state.startedJob?.let { vm.consumeStarted(); onOpenJob(it) } }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) vm.reload() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), vm::onPicked)

    Scaffold(
        topBar = { PinakesTopBar(title = stringResource(R.string.staff_imports), onNavigateUp = onNavigateUp) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = 48.dp)) {
            item(key = "form") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.sm)) {
                    if (areas.libraryThing) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            listOf(ImportKind.CSV to R.string.staff_import_kind_csv, ImportKind.LIBRARYTHING to R.string.staff_import_kind_lt).forEach { (kind, label) ->
                                FilterChip(selected = state.kind == kind, onClick = { vm.setKind(kind) }, label = { Text(stringResource(label)) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer))
                            }
                        }
                    }
                    state.error?.let { InlineErrorBanner(message = it.resolve()) }
                    PickerRow(
                        label = stringResource(R.string.staff_import_file), value = state.file?.name, placeholder = stringResource(R.string.staff_import_choose_file),
                        onClick = { picker.launch(MIME_TYPES) },
                    )
                    SwitchRow(stringResource(R.string.staff_import_scraping), state.scraping, vm::setScraping, subtitle = stringResource(R.string.staff_import_scraping_hint))
                    if (state.kind == ImportKind.CSV) {
                        Text(stringResource(R.string.staff_import_update_fields), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            UPDATE_FAMILIES.forEach { (family, label) ->
                                FilterChip(selected = family in state.families, onClick = { vm.toggleFamily(family) }, label = { Text(stringResource(label)) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer))
                            }
                        }
                    }
                    PrimaryButton(
                        stringResource(R.string.staff_import_start), onClick = vm::start, loading = state.uploading, enabled = state.file != null,
                        leadingIcon = Icons.Outlined.UploadFile, modifier = Modifier.fillMaxWidth().testTag("import-start"),
                    )
                    SecondaryButton(stringResource(R.string.staff_import_example), onClick = vm::downloadExample, leadingIcon = Icons.Outlined.Download, modifier = Modifier.fillMaxWidth())
                    if (areas.libraryThing) {
                        SecondaryButton(stringResource(R.string.staff_export_librarything), onClick = vm::exportLibraryThing, leadingIcon = Icons.Outlined.Download, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            if (state.jobs.isNotEmpty()) {
                item(key = "jobs-title") { FormSectionTitle(stringResource(R.string.staff_import_open_jobs)) }
                items(state.jobs, key = { "j${it.id}" }) { job ->
                    StaffActionRow(Icons.Outlined.UploadFile, job.fileName, stringResource(R.string.staff_import_progress, job.processed, job.totalRows)) { onOpenJob(job.id) }
                }
            }
            if (state.history.isNotEmpty()) {
                item(key = "history-title") { FormSectionTitle(stringResource(R.string.staff_import_history)) }
                items(state.history, key = { "h${it.importId}" }) { row ->
                    InfoCard(Modifier.padding(vertical = Spacing.xs)) {
                        Text(row.fileName ?: row.importId, style = MaterialTheme.typography.titleSmall)
                        InfoLine(stringResource(R.string.staff_import_when), row.startedAt?.let(DateFormat::dateTime))
                        InfoLine(stringResource(R.string.staff_import_by), row.user?.name)
                        InfoLine(stringResource(R.string.staff_import_result), stringResource(R.string.staff_import_counts, row.imported, row.updated, row.failed))
                        if (row.failed > 0) {
                            SecondaryButton(stringResource(R.string.staff_import_errors_csv), onClick = { vm.downloadErrors(row) }, leadingIcon = Icons.Outlined.Download)
                        }
                    }
                }
            }
        }
    }
    state.export?.let { ExportFileDialog(it, state.exportMime, stringResource(R.string.staff_imports), onDismiss = vm::closeExport) }
}

private val MIME_TYPES = arrayOf("text/csv", "text/comma-separated-values", "text/plain", "text/tab-separated-values", "application/vnd.ms-excel")

// =============================================================================================
// One job: the chunk driver
// =============================================================================================

data class ImportJobUiState(
    val job: ImportJob? = null,
    val loading: Boolean = true,
    val running: Boolean = false,
    val error: StaffMessage? = null,
    val forbidden: Boolean = false,
    val cancelled: Boolean = false,
    /** The server answered 410 `gone`: the job ended elsewhere (finished, cancelled, expired). */
    val ended: Boolean = false,
)

/**
 * Drives one import chunk by chunk, always from the server's `processed`. The job id comes from
 * the route (kept in the saved state), so the job survives process death and resumes where the
 * server is. Pause stops after the chunk in flight; the server's `job_busy` keeps two loops
 * (two devices) from running one job at once.
 */
@HiltViewModel
class StaffImportJobViewModel @Inject constructor(
    private val content: StaffContentRepository,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val jobId: String = savedState.get<String>(Routes.ARG_JOB_ID).orEmpty()
    private val _state = MutableStateFlow(ImportJobUiState())
    val state: StateFlow<ImportJobUiState> = _state.asStateFlow()
    private var loop: Job? = null

    init {
        viewModelScope.launch {
            when (val res = content.importJob(jobId)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(job = res.data, loading = false) }
                    if (!ImportProgress.isFinished(res.data)) resume()
                }
                is ApiResult.Failure -> _state.update { it.copy(loading = false, forbidden = res.isForbidden(), error = res.toStaffMessage(R.string.staff_error_load)) }
            }
        }
    }

    fun resume() {
        val job = _state.value.job ?: return
        if (loop?.isActive == true || ImportProgress.isFinished(job) || _state.value.ended) return
        _state.update { it.copy(running = true, error = null) }
        loop = viewModelScope.launch {
            var step: ChunkStep = ImportProgress.next(job)
            var busy = 0
            while (isActive) {
                val start = when (val s = step) {
                    is ChunkStep.Next -> s.start
                    is ChunkStep.Wait -> { delay(3_000); s.start }
                    ChunkStep.Done -> break
                    is ChunkStep.Stop -> {
                        _state.update { it.copy(forbidden = s.failure.isForbidden(), error = s.failure.toStaffMessage(R.string.staff_import_failed)) }
                        // The job ended elsewhere (410 gone: finished, cancelled or expired on another
                        // device): read its real final state, so Resume/Cancel go away.
                        if (s.failure.code == "gone" || s.failure.httpStatus == 410) refreshAfterGone()
                        break
                    }
                }
                val scraping = _state.value.job?.enableScraping == true
                when (val res = content.importChunk(jobId, start, scraping)) {
                    is ApiResult.Success -> {
                        busy = 0
                        _state.update { it.copy(job = res.data) }
                        step = ImportProgress.next(res.data)
                    }
                    is ApiResult.Failure -> {
                        if (res.code == "job_busy") busy++
                        step = ImportProgress.afterFailure(res, start, busy)
                        res.meta?.processed?.let { p -> _state.update { s -> s.copy(job = s.job?.copy(processed = p)) } }
                    }
                }
            }
            _state.update { it.copy(running = false) }
        }
    }

    private suspend fun refreshAfterGone() {
        // Whatever the server still says about it, a gone job is never resumable here.
        _state.update { it.copy(ended = true) }
        (content.importJob(jobId) as? ApiResult.Success)?.let { res -> _state.update { it.copy(job = res.data) } }
    }

    fun pause() {
        loop?.cancel()
        _state.update { it.copy(running = false) }
    }

    fun cancelJob() {
        pause()
        viewModelScope.launch {
            when (val res = content.cancelImport(jobId)) {
                is ApiResult.Success -> _state.update { it.copy(cancelled = true) }
                is ApiResult.Failure -> _state.update { it.copy(forbidden = res.isForbidden(), error = res.toStaffMessage(R.string.staff_error_save)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffImportJobScreen(onNavigateUp: () -> Unit) {
    val vm: StaffImportJobViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val app: AppViewModel = hiltViewModel()
    val areas by app.staffAreas.collectAsStateWithLifecycle()
    ForbiddenExit(state.forbidden, onNavigateUp)
    LeaveWhen(!areas.imports || state.cancelled, onNavigateUp)

    Scaffold(topBar = { PinakesTopBar(title = state.job?.fileName ?: stringResource(R.string.staff_imports), onNavigateUp = onNavigateUp) }) { padding ->
        val job = state.job
        when {
            state.loading -> LoadingState(modifier = Modifier.padding(padding))
            job == null -> ErrorState(message = state.error?.resolve() ?: stringResource(R.string.staff_error_load), modifier = Modifier.padding(padding))
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(Spacing.lg)) {
                item(key = "progress") {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        val finished = ImportProgress.isFinished(job) || state.ended
                        Text(
                            stringResource(
                                when {
                                    job.status == "cancelled" -> R.string.staff_import_status_cancelled
                                    job.status == "failed" -> R.string.staff_import_status_failed
                                    state.ended && !ImportProgress.isFinished(job) -> R.string.staff_import_status_gone
                                    finished -> R.string.staff_import_status_done
                                    state.running -> R.string.staff_import_status_running
                                    else -> R.string.staff_import_status_paused
                                },
                            ),
                            style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("import-status"),
                        )
                        ProgressLine(job.processed, job.totalRows, stringResource(R.string.staff_import_progress, job.processed, job.totalRows))
                        state.error?.let { InlineErrorBanner(message = it.resolve()) }
                        InfoCard {
                            InfoLine(stringResource(R.string.staff_import_imported), job.imported.toString())
                            InfoLine(stringResource(R.string.staff_import_updated), job.updated.toString())
                            InfoLine(stringResource(R.string.staff_import_scraped), job.scraped.toString().takeIf { job.enableScraping })
                            InfoLine(stringResource(R.string.staff_import_authors_created), job.authorsCreated.toString())
                            InfoLine(stringResource(R.string.staff_import_publishers_created), job.publishersCreated.toString())
                            InfoLine(stringResource(R.string.staff_import_errors), job.errors.toString())
                        }
                        if (!finished) {
                            if (state.running) SecondaryButton(stringResource(R.string.staff_import_pause), onClick = vm::pause, modifier = Modifier.fillMaxWidth())
                            else PrimaryButton(stringResource(R.string.staff_import_resume), onClick = vm::resume, modifier = Modifier.fillMaxWidth())
                            SecondaryButton(stringResource(R.string.staff_import_cancel), onClick = vm::cancelJob, modifier = Modifier.fillMaxWidth())
                        }
                        if (job.errorLines.isNotEmpty()) FormSectionTitle(stringResource(R.string.staff_import_errors))
                    }
                }
                items(job.errorLines.take(200)) { line ->
                    Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = Spacing.xxs))
                }
                item(key = "bottom") { Spacer(Modifier.height(Spacing.xxl)) }
            }
        }
    }
}
