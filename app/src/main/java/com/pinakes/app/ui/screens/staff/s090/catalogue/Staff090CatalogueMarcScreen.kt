package com.pinakes.app.ui.screens.staff.s090.catalogue

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import com.pinakes.app.ui.common.DateFormat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pinakes.app.R
import com.pinakes.app.data.model.MarcImportJob
import com.pinakes.app.data.model.MarcOptions
import com.pinakes.app.data.model.ValueLabel
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.repository.MarcUploadOptions
import com.pinakes.app.data.repository.Staff090CatalogueRepository
import com.pinakes.app.data.repository.Staff090CataloguePaths
import com.pinakes.app.data.repository.StaffFilesRepository
import com.pinakes.app.data.repository.StaffRepository
import com.pinakes.app.data.repository.isForbidden
import com.pinakes.app.data.staff.ChunkStep
import com.pinakes.app.data.staff.ImportProgress
import com.pinakes.app.data.staff.MarcImportRules
import com.pinakes.app.data.staff.PickedEntity
import com.pinakes.app.ui.common.AppViewModel
import com.pinakes.app.ui.components.InlineErrorBanner
import com.pinakes.app.ui.components.LoadingState
import com.pinakes.app.ui.components.ErrorState
import com.pinakes.app.ui.components.PinakesTopBar
import com.pinakes.app.ui.components.PrimaryButton
import com.pinakes.app.ui.components.SecondaryButton
import com.pinakes.app.ui.screens.staff.ExportFileDialog
import com.pinakes.app.ui.screens.staff.ForbiddenExit
import com.pinakes.app.ui.screens.staff.FormSectionTitle
import com.pinakes.app.ui.screens.staff.InfoCard
import com.pinakes.app.ui.screens.staff.InfoLine
import com.pinakes.app.ui.screens.staff.LeaveWhen
import com.pinakes.app.ui.screens.staff.LookupField
import com.pinakes.app.ui.screens.staff.PickItem
import com.pinakes.app.ui.screens.staff.PickedChips
import com.pinakes.app.ui.screens.staff.PickerRow
import com.pinakes.app.ui.screens.staff.ProgressLine
import com.pinakes.app.ui.screens.staff.SearchController
import com.pinakes.app.ui.screens.staff.SearchPickerSheet
import com.pinakes.app.ui.screens.staff.StaffMessage
import com.pinakes.app.ui.screens.staff.StaffSnackbarEffect
import com.pinakes.app.ui.screens.staff.SwitchRow
import com.pinakes.app.ui.screens.staff.content.PickedFile
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

enum class MarcTab(val label: Int) { IMPORT(R.string.staff090_catalogue_marc_import), EXPORT(R.string.staff090_catalogue_marc_export) }

/** The export formats of the server, used until /options answers. */
private val DEFAULT_EXPORT_FORMATS = listOf(ValueLabel("marc21", "MARC 21"), ValueLabel("marcxml", "MARCXML"), ValueLabel("unimarc", "UNIMARC"))

data class MarcUiState(
    val tab: MarcTab = MarcTab.IMPORT,
    val options: MarcOptions? = null,
    val optionsLoading: Boolean = true,
    val optionsError: StaffMessage? = null,
    // Import form
    val file: PickedFile? = null,
    val flavour: String = "auto",
    val encoding: String = "auto",
    val duplicates: String = "skip",
    val supplierId: Int? = null,
    val downloadCovers: Boolean = false,
    val uploading: Boolean = false,
    val importError: StaffMessage? = null,
    // The job: preview, then chunks
    val job: MarcImportJob? = null,
    /** The caller's MARC jobs from the server (`GET /staff/marc/imports`), any device. */
    val jobs: List<MarcImportJob> = emptyList(),
    val jobsCursor: String? = null,
    val cancellingId: String? = null,
    val running: Boolean = false,
    /** The server answered 410 `gone`: the job ended elsewhere. */
    val ended: Boolean = false,
    // Export
    val format: String = "marc21",
    val wholeCatalogue: Boolean = true,
    val chosen: List<PickItem> = emptyList(),
    val picking: Boolean = false,
    val exporting: Boolean = false,
    val export: File? = null,
    val exportMime: String = "application/marc",
    val forbidden: Boolean = false,
    val message: StaffMessage? = null,
) {
    /** The upload answered and no record was imported yet: show the preview and the go button. */
    val previewing: Boolean get() = job != null && !running && job.processed == 0 && job.status == "ready" && !ended
    val finished: Boolean get() = job?.let { ImportProgress.isFinished(it.asImportJob()) } == true || ended

    /** Unfinished jobs other than the one on screen: resumable here whoever started them. */
    val openJobs: List<MarcImportJob> get() = MarcImportRules.openJobs(jobs, job?.id)
}

/**
 * MARC import and export (module `marc`). The import is the CSV import's contract: the upload
 * stages the file and answers a preview; then the app drives chunks from the server's
 * `processed` with [ImportProgress] (out-of-order recovery, busy retry, 410 gone), resumable
 * after process death through the saved job id. The export downloads the whole catalogue or the
 * chosen books (at most 1000), and the chosen books' catalogue cards (at most 200).
 */
@HiltViewModel
class Staff090CatalogueMarcViewModel @Inject constructor(
    private val repo: Staff090CatalogueRepository,
    private val staff: StaffRepository,
    private val files: StaffFilesRepository,
    @ApplicationContext private val context: Context,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(MarcUiState())
    val state: StateFlow<MarcUiState> = _state.asStateFlow()
    private var loop: Job? = null

    val books = SearchController(viewModelScope, minLength = 2) { q ->
        when (val res = staff.books(q, cursor = null, limit = 30)) {
            is ApiResult.Success -> ApiResult.Success(res.data.items.map { PickItem(it.id, it.title, listOfNotNull(it.author, it.year?.toString()).joinToString(" · ")) })
            is ApiResult.Failure -> res
        }
    }.also { c -> c.onFailure = { f -> if (f.isForbidden()) _state.update { it.copy(forbidden = true) } } }

    init {
        loadOptions()
        savedState.get<String>(KEY_JOB)?.let(::restore)
    }

    /**
     * The caller's jobs on the server: an import started on another device, or whose id this
     * app lost, can be resumed or cancelled from here. [more] reads the next page.
     */
    fun loadJobs(more: Boolean = false) {
        val cursor = if (more) _state.value.jobsCursor ?: return else null
        viewModelScope.launch {
            when (val res = repo.marcImports(cursor)) {
                is ApiResult.Success -> _state.update {
                    it.copy(jobs = if (more) (it.jobs + res.data.items).distinctBy { j -> j.id } else res.data.items, jobsCursor = res.data.nextCursor)
                }
                // The list is a convenience: a failure leaves the form usable.
                is ApiResult.Failure -> _state.update { it.copy(forbidden = res.isForbidden()) }
            }
        }
    }

    /** Take [job] on screen and continue its chunks from the server's position. */
    fun resumeJob(job: MarcImportJob) {
        if (_state.value.running) return
        loop?.cancel()
        savedState[KEY_JOB] = job.id
        _state.update { it.copy(job = job, ended = false, importError = null) }
        resume()
    }

    /** Cancel a listed job (not the one on screen) and drop it from the list. */
    fun cancelListed(job: MarcImportJob) {
        if (_state.value.cancellingId != null) return
        _state.update { it.copy(cancellingId = job.id) }
        viewModelScope.launch {
            when (val res = repo.cancelMarcImport(job.id)) {
                is ApiResult.Success -> _state.update { s ->
                    s.copy(cancellingId = null, jobs = s.jobs.filterNot { it.id == job.id }, message = StaffMessage(R.string.staff_import_status_cancelled))
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(cancellingId = null, forbidden = res.isForbidden(), message = res.toCatalogueMessage(R.string.staff_error_save))
                }
            }
        }
    }

    fun loadOptions() {
        _state.update { it.copy(optionsLoading = true, optionsError = null) }
        viewModelScope.launch {
            when (val res = repo.marcOptions()) {
                is ApiResult.Success -> _state.update { s ->
                    s.copy(
                        options = res.data, optionsLoading = false,
                        format = res.data.exportFormats.firstOrNull()?.value ?: s.format,
                        // A supplier that disappeared (or a module switched off) is not offered.
                        supplierId = s.supplierId?.takeIf { id -> res.data.suppliers?.any { it.id == id } == true },
                    )
                }
                is ApiResult.Failure -> _state.update { it.copy(optionsLoading = false, forbidden = res.isForbidden(), optionsError = res.toCatalogueMessage(R.string.staff_error_load)) }
            }
        }
    }

    private fun restore(id: String) {
        viewModelScope.launch {
            when (val res = repo.marcImport(id)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(job = res.data) }
                    if (res.data.processed > 0 && !ImportProgress.isFinished(res.data.asImportJob())) resume()
                }
                // The job expired or was purged: start over.
                is ApiResult.Failure -> {
                    savedState.remove<String>(KEY_JOB)
                    _state.update { it.copy(forbidden = res.isForbidden()) }
                }
            }
        }
    }

    fun selectTab(tab: MarcTab) = _state.update { it.copy(tab = tab) }

    // ---- Import form ----

    fun setFlavour(v: String) = _state.update { it.copy(flavour = v) }
    fun setEncoding(v: String) = _state.update { it.copy(encoding = v) }
    fun setDuplicates(v: String) = _state.update { it.copy(duplicates = v) }
    fun setSupplier(v: String) = _state.update { it.copy(supplierId = v.toIntOrNull()?.takeIf { id -> id > 0 }) }
    fun setDownloadCovers(v: Boolean) = _state.update { it.copy(downloadCovers = v) }

    /** Read the picked document; refuse what the server refuses anyway (extension, size). */
    fun onPicked(uri: Uri?) {
        uri ?: return
        val options = _state.value.options
        val max = options?.maxFileBytes ?: ImportProgress.MAX_BYTES
        viewModelScope.launch {
            val read = withContext(Dispatchers.IO) { runCatching { read(uri, max) }.getOrNull() }
            _state.update {
                when {
                    read == null -> it.copy(importError = StaffMessage(R.string.staff_import_read_failed), file = null)
                    read.file == null -> it.copy(importError = StaffMessage(R.string.staff_import_too_big), file = null)
                    !MarcImportRules.acceptsFileName(read.file.name, options?.extensions.orEmpty()) ->
                        it.copy(importError = StaffMessage(R.string.staff090_catalogue_marc_wrong_ext), file = null)
                    else -> it.copy(file = read.file, importError = null)
                }
            }
        }
    }

    private class Read(val file: PickedFile?)

    private fun read(uri: Uri, max: Long): Read? {
        val resolver = context.contentResolver
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "import.mrc"
        var size = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { i -> c.getString(i)?.let { name = it } }
                c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { i -> if (!c.isNull(i)) size = c.getLong(i) }
            }
        }
        if (size > max) return Read(null)
        val out = java.io.ByteArrayOutputStream()
        resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                if (total > max) return Read(null)
                out.write(buffer, 0, n)
            }
        } ?: return null
        return Read(PickedFile(name, resolver.getType(uri), out.toByteArray()))
    }

    fun upload() {
        val s = _state.value
        val file = s.file ?: return
        if (s.uploading) return
        _state.update { it.copy(uploading = true, importError = null) }
        viewModelScope.launch {
            val options = MarcUploadOptions(
                flavour = s.flavour, encoding = if (MarcImportRules.encodingApplies(file.name)) s.encoding else "auto",
                duplicates = s.duplicates, downloadCovers = s.downloadCovers,
                supplierId = s.supplierId.takeIf { s.options?.suppliers != null },
            )
            when (val res = repo.createMarcImport(file.name, file.bytes, options)) {
                is ApiResult.Success -> {
                    savedState[KEY_JOB] = res.data.id
                    _state.update { it.copy(uploading = false, file = null, job = res.data, ended = false) }
                }
                is ApiResult.Failure -> _state.update { it.copy(uploading = false, forbidden = res.isForbidden(), importError = res.toCatalogueMessage(R.string.staff_import_failed)) }
            }
        }
    }

    // ---- Chunk driver (the CSV import's, on the MARC routes) ----

    fun resume() {
        val job = _state.value.job ?: return
        if (loop?.isActive == true || ImportProgress.isFinished(job.asImportJob()) || _state.value.ended) return
        _state.update { it.copy(running = true, importError = null) }
        loop = viewModelScope.launch {
            var step: ChunkStep = ImportProgress.next(job.asImportJob())
            var busy = 0
            while (isActive) {
                val start = when (val s = step) {
                    is ChunkStep.Next -> s.start
                    is ChunkStep.Wait -> { delay(3_000); s.start }
                    ChunkStep.Done -> break
                    is ChunkStep.Stop -> {
                        _state.update { it.copy(forbidden = s.failure.isForbidden(), importError = s.failure.toCatalogueMessage(R.string.staff_import_failed)) }
                        if (s.failure.code == "gone" || s.failure.httpStatus == 410 || s.failure.code == "job_cancelled") refreshAfterGone(job.id)
                        break
                    }
                }
                when (val res = repo.marcImportChunk(job.id, start)) {
                    is ApiResult.Success -> {
                        busy = 0
                        _state.update { it.copy(job = res.data) }
                        step = ImportProgress.next(res.data.asImportJob())
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

    private suspend fun refreshAfterGone(id: String) {
        _state.update { it.copy(ended = true) }
        (repo.marcImport(id) as? ApiResult.Success)?.let { res -> _state.update { it.copy(job = res.data) } }
    }

    fun pause() {
        loop?.cancel()
        _state.update { it.copy(running = false) }
    }

    /** Cancel the job and drop its staged records (also "discard" on the preview). */
    fun cancelJob() {
        val id = _state.value.job?.id ?: return
        pause()
        viewModelScope.launch {
            when (val res = repo.cancelMarcImport(id)) {
                is ApiResult.Success -> {
                    savedState.remove<String>(KEY_JOB)
                    _state.update { s -> s.copy(job = null, ended = false, jobs = s.jobs.filterNot { it.id == id }, message = StaffMessage(R.string.staff_import_status_cancelled)) }
                }
                is ApiResult.Failure -> _state.update { it.copy(forbidden = res.isForbidden(), importError = res.toCatalogueMessage(R.string.staff_error_save)) }
            }
        }
    }

    /** A finished job: back to an empty form. */
    fun newImport() {
        pause()
        savedState.remove<String>(KEY_JOB)
        _state.update { it.copy(job = null, ended = false, importError = null) }
        loadJobs()
    }

    // ---- Export ----

    fun setFormat(v: String) = _state.update { it.copy(format = v) }
    fun setWholeCatalogue(v: Boolean) = _state.update { it.copy(wholeCatalogue = v) }

    fun openPicker() {
        books.clear()
        _state.update { it.copy(picking = true) }
    }

    fun closePicker() = _state.update { it.copy(picking = false) }

    fun toggleBook(item: PickItem) = _state.update { s ->
        val chosen = if (s.chosen.any { it.id == item.id }) s.chosen.filterNot { it.id == item.id }
        else if (s.chosen.size >= Staff090CataloguePaths.MAX_EXPORT_IDS) s.chosen else s.chosen + item
        s.copy(chosen = chosen)
    }

    fun removeBook(id: Int) = _state.update { s -> s.copy(chosen = s.chosen.filterNot { it.id == id }) }

    fun export() {
        val s = _state.value
        val path = Staff090CataloguePaths.marcExport(s.format, if (s.wholeCatalogue) null else s.chosen.map { it.id }) ?: return
        download(path, Staff090CataloguePaths.exportFallbackName(s.format), Staff090CataloguePaths.exportMime(s.format))
    }

    fun printCards() {
        val path = Staff090CataloguePaths.catalogueCards(_state.value.chosen.map { it.id }) ?: return
        download(path, "schede_catalografiche.pdf", "application/pdf")
    }

    private fun download(path: String, name: String, mime: String) {
        if (_state.value.exporting) return
        _state.update { it.copy(exporting = true) }
        viewModelScope.launch {
            val res = files.download(path, name)
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(exporting = false, export = res.data, exportMime = mime)
                    is ApiResult.Failure -> it.copy(exporting = false, forbidden = res.isForbidden(), message = res.toCatalogueMessage(R.string.staff_download_failed))
                }
            }
        }
    }

    fun closeExport() = _state.update { it.copy(export = null) }
    fun consumeMessage() = _state.update { it.copy(message = null) }

    companion object {
        const val KEY_JOB = "marcJobId"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Staff090CatalogueMarcScreen(onNavigateUp: () -> Unit) {
    val vm: Staff090CatalogueMarcViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val app: AppViewModel = hiltViewModel()
    val areas by app.staffAreas.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    ForbiddenExit(state.forbidden, onNavigateUp)
    LeaveWhen(!areas.s090.marc, onNavigateUp)
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) vm.loadJobs() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    StaffSnackbarEffect(state.message, snackbar, vm::consumeMessage)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), vm::onPicked)

    Scaffold(
        topBar = { PinakesTopBar(title = stringResource(R.string.staff090_marc), onNavigateUp = onNavigateUp) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = state.tab.ordinal) {
                MarcTab.entries.forEach { tab ->
                    Tab(selected = tab == state.tab, onClick = { vm.selectTab(tab) }, text = { Text(stringResource(tab.label)) }, modifier = Modifier.testTag("marc-tab-${tab.name.lowercase()}"))
                }
            }
            when {
                state.optionsLoading && state.options == null -> LoadingState()
                state.options == null -> ErrorState(message = state.optionsError?.resolve() ?: stringResource(R.string.staff_error_load), onRetry = vm::loadOptions)
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = 48.dp)) {
                    if (state.tab == MarcTab.IMPORT) importTab(state, vm, onPick = { picker.launch(MarcImportRules.PICKER_MIME_TYPES) })
                    else exportTab(state, vm, cards = areas.s090.catalogueCards)
                }
            }
        }
    }

    if (state.picking) {
        val s by vm.books.state.collectAsStateWithLifecycle()
        SearchPickerSheet(
            title = stringResource(R.string.staff090_catalogue_marc_pick_books), placeholder = stringResource(R.string.staff_books_search),
            state = s, onQuery = vm.books::onQuery, onPick = vm::toggleBook, onDismiss = vm::closePicker,
            selected = state.chosen.map { it.id }.toSet(), onConfirm = vm::closePicker, hint = stringResource(R.string.staff_search_min_chars),
        )
    }
    state.export?.let { ExportFileDialog(it, state.exportMime, stringResource(R.string.staff090_marc), onDismiss = vm::closeExport) }
}

private fun LazyListScope.importTab(state: MarcUiState, vm: Staff090CatalogueMarcViewModel, onPick: () -> Unit) {
    val options = state.options ?: return
    val job = state.job
    if (job == null) {
        // The list keeps its first visible item in place when items are inserted before it:
        // the open jobs arrive after the form is drawn, so a fixed first row keeps them in view
        // instead of above the top of the screen.
        item(key = "import-hint") {
            Text(
                stringResource(R.string.staff090_catalogue_marc_import_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.md),
            )
        }
        openJobs(state, vm)
        item(key = "form") { ImportForm(state, options, vm, onPick) }
        return
    }
    item(key = "job") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.md)) {
            state.importError?.let { InlineErrorBanner(message = it.resolve()) }
            if (state.previewing) {
                Text(stringResource(R.string.staff090_catalogue_marc_preview), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("marc-status"))
                InfoCard {
                    InfoLine(stringResource(R.string.staff_import_file), job.fileName)
                    InfoLine(stringResource(R.string.staff090_catalogue_marc_records), job.totalRows.toString())
                    InfoLine(stringResource(R.string.staff090_catalogue_marc_container), stringResource(if (job.container == "xml") R.string.staff090_catalogue_marc_container_xml else R.string.staff090_catalogue_marc_container_iso))
                    InfoLine(stringResource(R.string.staff090_catalogue_marc_unreadable), job.unreadable.toString().takeIf { job.unreadable > 0 })
                    InfoLine(stringResource(R.string.staff090_catalogue_marc_duplicates), options.duplicateModes.asOptions().labelOf(job.duplicates))
                    InfoLine(stringResource(R.string.staff090_catalogue_supplier), options.suppliers?.firstOrNull { it.id == job.supplierId }?.name)
                }
                job.truncated?.let { InlineErrorBanner(message = it) }
                PrimaryButton(
                    stringResource(R.string.staff090_catalogue_marc_run, job.totalRows), onClick = vm::resume,
                    leadingIcon = Icons.Outlined.UploadFile, modifier = Modifier.fillMaxWidth().testTag("marc-run"),
                )
                SecondaryButton(stringResource(R.string.staff090_catalogue_marc_discard), onClick = vm::cancelJob, modifier = Modifier.fillMaxWidth())
                if (job.preview.isNotEmpty()) FormSectionTitle(stringResource(R.string.staff090_catalogue_marc_first_records))
            } else {
                val finished = state.finished
                Text(
                    stringResource(
                        when {
                            job.status == "cancelled" -> R.string.staff_import_status_cancelled
                            job.status == "failed" -> R.string.staff_import_status_failed
                            state.ended && !ImportProgress.isFinished(job.asImportJob()) -> R.string.staff_import_status_gone
                            finished -> R.string.staff_import_status_done
                            state.running -> R.string.staff_import_status_running
                            else -> R.string.staff_import_status_paused
                        },
                    ),
                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("marc-status"),
                )
                ProgressLine(job.processed, job.totalRows, stringResource(R.string.staff090_catalogue_marc_progress, job.processed, job.totalRows))
                InfoCard {
                    InfoLine(stringResource(R.string.staff_import_imported), job.imported.toString())
                    InfoLine(stringResource(R.string.staff_import_updated), job.updated.toString())
                    InfoLine(stringResource(R.string.staff090_catalogue_marc_skipped), job.skipped.toString())
                    InfoLine(stringResource(R.string.staff090_catalogue_marc_covers), job.covers.toString().takeIf { job.downloadCovers })
                    InfoLine(stringResource(R.string.staff_import_authors_created), job.authorsCreated.toString())
                    InfoLine(stringResource(R.string.staff_import_publishers_created), job.publishersCreated.toString())
                    InfoLine(stringResource(R.string.staff_import_errors), job.failed.toString())
                }
                if (!finished) {
                    if (state.running) SecondaryButton(stringResource(R.string.staff_import_pause), onClick = vm::pause, modifier = Modifier.fillMaxWidth())
                    else PrimaryButton(stringResource(R.string.staff_import_resume), onClick = vm::resume, modifier = Modifier.fillMaxWidth())
                    SecondaryButton(stringResource(R.string.staff_import_cancel), onClick = vm::cancelJob, modifier = Modifier.fillMaxWidth())
                } else {
                    PrimaryButton(stringResource(R.string.staff090_catalogue_marc_new), onClick = vm::newImport, leadingIcon = Icons.Outlined.Add, modifier = Modifier.fillMaxWidth().testTag("marc-new"))
                }
                if (job.errorLines.isNotEmpty()) FormSectionTitle(stringResource(R.string.staff_import_errors))
            }
        }
    }
    if (state.previewing) {
        items(job.preview, key = { "p${it.position}" }) { row ->
            InfoCard(Modifier.padding(vertical = Spacing.xs)) {
                if (row.error != null) {
                    Text(stringResource(R.string.staff090_catalogue_marc_unreadable_row, row.position), style = MaterialTheme.typography.titleSmall)
                    Text(row.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                } else {
                    Text(row.title, style = MaterialTheme.typography.titleSmall)
                    row.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Text(
                        listOfNotNull(row.authors, row.publisher, row.year, row.isbn).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (row.existingId != null) {
                        Text(stringResource(R.string.staff090_catalogue_in_catalogue), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    } else {
        items(job.errorLines.take(200)) { line ->
            Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = Spacing.xxs))
        }
    }
    item(key = "bottom") { Spacer(Modifier.height(Spacing.xxl)) }
}

/** Unfinished imports on the server (this device or another one), each with Resume and Cancel. */
private fun LazyListScope.openJobs(state: MarcUiState, vm: Staff090CatalogueMarcViewModel) {
    val open = state.openJobs
    if (open.isEmpty()) return
    item(key = "open-title") { FormSectionTitle(stringResource(R.string.staff_import_open_jobs)) }
    items(open, key = { "open-${it.id}" }) { job ->
        InfoCard(Modifier.padding(vertical = Spacing.xs).testTag("marc-open-${job.id}")) {
            Text(job.fileName, style = MaterialTheme.typography.titleSmall)
            InfoLine(stringResource(R.string.staff_import_result), stringResource(R.string.staff090_catalogue_marc_progress, job.processed, job.totalRows))
            InfoLine(stringResource(R.string.staff_import_when), job.createdAt?.let(DateFormat::dateTime))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.xs)) {
                PrimaryButton(
                    stringResource(R.string.staff_import_resume), onClick = { vm.resumeJob(job) }, enabled = !state.running,
                    modifier = Modifier.weight(1f).testTag("marc-open-resume-${job.id}"),
                )
                SecondaryButton(
                    stringResource(R.string.staff_import_cancel), onClick = { vm.cancelListed(job) }, enabled = state.cancellingId == null,
                    modifier = Modifier.weight(1f).testTag("marc-open-cancel-${job.id}"),
                )
            }
        }
    }
    if (state.jobsCursor != null) {
        item(key = "open-more") { SecondaryButton(stringResource(R.string.action_load_more), onClick = { vm.loadJobs(more = true) }, modifier = Modifier.fillMaxWidth()) }
    }
}

@Composable
private fun ImportForm(state: MarcUiState, options: MarcOptions, vm: Staff090CatalogueMarcViewModel, onPick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.sm)) {
        state.importError?.let { InlineErrorBanner(message = it.resolve()) }
        PickerRow(
            label = stringResource(R.string.staff_import_file), value = state.file?.name,
            placeholder = stringResource(R.string.staff_import_choose_file), onClick = onPick,
        )
        val flavours = options.flavours.asOptions()
        LookupField(
            label = stringResource(R.string.staff090_catalogue_marc_flavour), value = state.flavour, displayValue = flavours.labelOf(state.flavour),
            options = flavours, editable = false, allowClear = false, onValueChange = vm::setFlavour, modifier = Modifier.fillMaxWidth(),
        )
        if (MarcImportRules.encodingApplies(state.file?.name)) {
            val encodings = options.encodings.asOptions()
            LookupField(
                label = stringResource(R.string.staff090_catalogue_marc_encoding), value = state.encoding, displayValue = encodings.labelOf(state.encoding),
                options = encodings, editable = false, allowClear = false, onValueChange = vm::setEncoding, modifier = Modifier.fillMaxWidth(),
            )
        }
        val modes = options.duplicateModes.asOptions()
        LookupField(
            label = stringResource(R.string.staff090_catalogue_marc_duplicates), value = state.duplicates, displayValue = modes.labelOf(state.duplicates),
            options = modes, editable = false, allowClear = false, onValueChange = vm::setDuplicates, modifier = Modifier.fillMaxWidth(),
        )
        options.duplicateModes.firstOrNull { it.value == state.duplicates }?.description?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        options.suppliers?.let { list ->
            val suppliers = list.asOptions()
            LookupField(
                label = stringResource(R.string.staff090_catalogue_supplier), value = state.supplierId?.toString().orEmpty(),
                displayValue = suppliers.labelOf(state.supplierId?.toString()), options = suppliers, editable = false,
                onValueChange = vm::setSupplier, modifier = Modifier.fillMaxWidth().testTag("marc-supplier"),
            )
        }
        SwitchRow(
            stringResource(R.string.staff090_catalogue_marc_covers_switch), state.downloadCovers, vm::setDownloadCovers,
            subtitle = stringResource(R.string.staff090_catalogue_marc_covers_hint),
        )
        PrimaryButton(
            stringResource(R.string.staff090_catalogue_marc_upload), onClick = vm::upload, loading = state.uploading, enabled = state.file != null,
            leadingIcon = Icons.Outlined.UploadFile, modifier = Modifier.fillMaxWidth().testTag("marc-upload"),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.exportTab(state: MarcUiState, vm: Staff090CatalogueMarcViewModel, cards: Boolean) {
    val options = state.options ?: return
    item(key = "export") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.md)) {
            FormSectionTitle(stringResource(R.string.staff090_catalogue_marc_format))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                options.exportFormats.ifEmpty { DEFAULT_EXPORT_FORMATS }.forEach { f ->
                    FilterChip(
                        selected = state.format == f.value, onClick = { vm.setFormat(f.value) }, label = { Text(f.label) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer, selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer),
                    )
                }
            }
            FormSectionTitle(stringResource(R.string.staff090_catalogue_marc_scope))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                listOf(true to R.string.staff090_catalogue_marc_scope_all, false to R.string.staff090_catalogue_marc_scope_chosen).forEach { (whole, label) ->
                    FilterChip(
                        selected = state.wholeCatalogue == whole, onClick = { vm.setWholeCatalogue(whole) }, label = { Text(stringResource(label)) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer, selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer),
                        modifier = Modifier.testTag(if (whole) "marc-scope-all" else "marc-scope-chosen"),
                    )
                }
            }
            if (state.wholeCatalogue) {
                Text(stringResource(R.string.staff090_catalogue_marc_scope_all_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(
                    stringResource(R.string.staff090_catalogue_marc_chosen_count, state.chosen.size, options.maxExportIds),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PickedChips(state.chosen.map { PickedEntity(it.id, it.title) }) { vm.removeBook(it.id) }
                SecondaryButton(stringResource(R.string.staff090_catalogue_marc_add_books), onClick = vm::openPicker, leadingIcon = Icons.Outlined.Add, modifier = Modifier.fillMaxWidth().testTag("marc-add-books"))
            }
            PrimaryButton(
                stringResource(R.string.staff090_catalogue_marc_export_run), onClick = vm::export, loading = state.exporting,
                enabled = state.wholeCatalogue || state.chosen.isNotEmpty(), leadingIcon = Icons.Outlined.Download,
                modifier = Modifier.fillMaxWidth().testTag("marc-export"),
            )
            if (cards && !state.wholeCatalogue) {
                SecondaryButton(
                    stringResource(R.string.staff090_catalogue_cards_print, minOf(state.chosen.size, Staff090CataloguePaths.MAX_CARDS)), onClick = vm::printCards,
                    enabled = state.chosen.isNotEmpty() && !state.exporting, leadingIcon = Icons.Outlined.Print, modifier = Modifier.fillMaxWidth().testTag("marc-cards"),
                )
                if (state.chosen.size > Staff090CataloguePaths.MAX_CARDS) {
                    Text(stringResource(R.string.staff090_catalogue_cards_limit, Staff090CataloguePaths.MAX_CARDS), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
