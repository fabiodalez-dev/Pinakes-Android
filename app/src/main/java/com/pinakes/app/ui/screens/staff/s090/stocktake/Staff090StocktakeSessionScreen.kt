package com.pinakes.app.ui.screens.staff.s090.stocktake

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
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
import com.pinakes.app.data.model.Staff090StocktakeOptions
import com.pinakes.app.data.model.Staff090StocktakeScan
import com.pinakes.app.data.model.Staff090StocktakeScanRequest
import com.pinakes.app.data.model.Staff090StocktakeSession
import com.pinakes.app.data.model.Staff090StocktakeTone
import com.pinakes.app.data.model.Staff090StocktakeUpload
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.network.ErrorCodes
import com.pinakes.app.data.repository.Staff090StocktakeRepository
import com.pinakes.app.data.repository.isForbidden
import com.pinakes.app.data.staff.Staff090StocktakeErrors
import com.pinakes.app.data.staff.SubmissionKeys
import com.pinakes.app.ui.common.AppViewModel
import com.pinakes.app.ui.components.ConfirmDialog
import com.pinakes.app.ui.components.ErrorState
import com.pinakes.app.ui.components.InlineErrorBanner
import com.pinakes.app.ui.components.LoadingState
import com.pinakes.app.ui.components.PinakesTextButton
import com.pinakes.app.ui.components.PinakesTextField
import com.pinakes.app.ui.components.PinakesTopBar
import com.pinakes.app.ui.components.PrimaryButton
import com.pinakes.app.ui.components.SecondaryButton
import com.pinakes.app.ui.screens.staff.ForbiddenExit
import com.pinakes.app.ui.screens.staff.FormSectionTitle
import com.pinakes.app.ui.screens.staff.InfoCard
import com.pinakes.app.ui.screens.staff.InfoLine
import com.pinakes.app.ui.screens.staff.LeaveWhen
import com.pinakes.app.ui.screens.staff.PickItem
import com.pinakes.app.ui.screens.staff.PickerRow
import com.pinakes.app.ui.screens.staff.StaffMessage
import com.pinakes.app.ui.screens.staff.StaffSnackbarEffect
import com.pinakes.app.ui.screens.staff.SwitchRow
import com.pinakes.app.ui.screens.staff.resolve
import com.pinakes.app.ui.screens.staff.scan.BarcodeAnalyzer
import com.pinakes.app.ui.screens.staff.scan.ScannerBox
import com.pinakes.app.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One scan answer to play and feel: [id] grows with every answer, so the same tone twice plays twice. */
data class StocktakeFeedback(val id: Int, val tone: Staff090StocktakeTone?)

data class StocktakeSessionUiState(
    val session: Staff090StocktakeSession? = null,
    val options: Staff090StocktakeOptions? = null,
    val loading: Boolean = true,
    val error: StaffMessage? = null,
    val forbidden: Boolean = false,
    /** The module went off on the server (404 `feature_disabled`): the screen leaves. */
    val leave: Boolean = false,
    /** The shelf the operator stands at: every scan carries it, so a copy of another shelf reads as misplaced. */
    val shelfId: Int? = null,
    val shelfPicker: Boolean = false,
    val pickerQuery: String = "",
    val mine: Boolean = false,
    /** Recent scans of one outcome only (null = the live list of the latest scans). */
    val outcomeFilter: String? = null,
    val filtered: List<Staff090StocktakeScan> = emptyList(),
    val filteredCursor: String? = null,
    val filteredLoading: Boolean = false,
    val lastResult: Staff090StocktakeScan? = null,
    /** Codes read and not answered yet. */
    val pending: Int = 0,
    val feedback: StocktakeFeedback? = null,
    val manualError: Boolean = false,
    val uploading: Boolean = false,
    val uploadResult: Staff090StocktakeUpload? = null,
    val closeDialog: Boolean = false,
    val closeAction: String = Staff090StocktakeClose.NONE,
    val closing: Boolean = false,
    val cancelDialog: Boolean = false,
    val message: StaffMessage? = null,
) {
    val recent: List<Staff090StocktakeScan> get() = if (outcomeFilter == null) session?.recentScans.orEmpty() else filtered
    val scanning: Boolean get() = session?.canScan == true
}

/**
 * One stocktake session on the phone: a continuous scanner (camera, keyboard-wedge reader or
 * typed code) sending every code with the current shelf, the answer at once in the outcome's
 * colour with its tone, live counters polled every few seconds so several operators see each
 * other's progress, the latest scans, a code list upload, close and cancel.
 *
 * Offline the screen leaves (the area is hidden offline): codes are never queued for later.
 */
@HiltViewModel
class StocktakeSessionViewModel @Inject constructor(
    private val repo: Staff090StocktakeRepository,
    @ApplicationContext private val context: Context,
    savedState: SavedStateHandle,
) : ViewModel() {
    val sessionId: Int = savedState.get<Int>(STOCKTAKE_ARG_SESSION) ?: 0
    private val _state = MutableStateFlow(StocktakeSessionUiState())
    val state: StateFlow<StocktakeSessionUiState> = _state.asStateFlow()
    private val gate = Staff090StocktakeScanGate()
    private val keys = SubmissionKeys()
    private val queue = Channel<Staff090StocktakeScanRequest>(Channel.UNLIMITED)
    private var polling: Job? = null
    private var feedbackId = 0
    private var shelfChosen = false

    init {
        viewModelScope.launch { for (request in queue) send(request) }
        viewModelScope.launch {
            (repo.options() as? ApiResult.Success)?.data?.let { options -> _state.update { it.copy(options = options) } }
        }
    }

    fun reload() {
        _state.update { it.copy(loading = it.session == null, error = null) }
        viewModelScope.launch {
            val res = repo.session(sessionId, _state.value.mine)
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(
                        session = res.data, loading = false,
                        // The session's own shelf is where the operator starts, until they pick another.
                        shelfId = if (shelfChosen) it.shelfId else res.data.filter.shelfId,
                    )
                    is ApiResult.Failure -> it.copy(
                        loading = false, forbidden = res.isForbidden(), leave = Staff090StocktakeErrors.moduleOff(res),
                        error = if (it.session == null) res.toStocktakeMessage(R.string.staff_error_load) else null,
                        message = if (it.session != null) res.toStocktakeMessage(R.string.staff_error_load) else null,
                    )
                }
            }
        }
    }

    // ---------- Visibility: poll and scan only while the screen is on top ----------

    fun onVisible() {
        reload()
        gate.resume()
        polling?.cancel()
        polling = viewModelScope.launch {
            while (isActive) {
                delay(POLL_MS)
                if (Staff090StocktakeMerge.shouldPoll(_state.value.session)) poll()
            }
        }
    }

    fun onHidden() {
        polling?.cancel()
        polling = null
        gate.pause()
    }

    private suspend fun poll() {
        val res = repo.state(sessionId, _state.value.mine)
        if (res is ApiResult.Failure) {
            // A missed poll is retried by the next one; only a refusal or the module going away ends the screen.
            if (res.isForbidden() || Staff090StocktakeErrors.moduleOff(res)) _state.update { it.copy(forbidden = res.isForbidden(), leave = Staff090StocktakeErrors.moduleOff(res)) }
            return
        }
        val data = (res as ApiResult.Success).data
        val before = _state.value.session ?: return
        _state.update { s ->
            val session = s.session ?: return@update s
            s.copy(session = Staff090StocktakeMerge.withStatus(session.copy(summary = data.summary, recentScans = data.recentScans), data.status))
        }
        // Another operator closed or cancelled it: read the whole session (who, when, what next).
        if (data.status != before.status) reload()
        if (_state.value.outcomeFilter != null) refreshFiltered()
    }

    // ---------- Scanning ----------

    /** A camera decode: sent when the gate says it is a new read (see [Staff090StocktakeScanGate]). */
    fun onDecoded(raw: String) {
        if (!_state.value.scanning || _state.value.shelfPicker) return
        val code = normalizeStocktakeCode(raw).takeIf { it.isNotEmpty() } ?: return
        val accepted = gate.offer(code, SystemClock.elapsedRealtime()) ?: return
        enqueue(accepted)
    }

    fun clearManualError() = _state.update { if (it.manualError) it.copy(manualError = false) else it }

    /** The typed (or keyboard-wedge scanned) code; the text stays in the screen. True when it was taken. */
    fun submitManual(input: String): Boolean {
        val code = normalizeStocktakeCode(input)
        if (code.isEmpty()) {
            _state.update { it.copy(manualError = true) }
            return false
        }
        if (!_state.value.scanning) return false
        gate.markSent(code, SystemClock.elapsedRealtime())
        enqueue(code)
        return true
    }

    private fun enqueue(code: String) {
        _state.update { it.copy(pending = it.pending + 1) }
        queue.trySend(Staff090StocktakeScanRequest(code, _state.value.shelfId))
    }

    /**
     * Send one code. One Idempotency-Key per read: the automatic retry after a dropped
     * connection reuses it (the server answers the first scan, never a second one), a new
     * read of the same label gets a new key and becomes the web's "already scanned".
     */
    private suspend fun send(request: Staff090StocktakeScanRequest) {
        if (!_state.value.scanning) {
            _state.update { it.copy(pending = (it.pending - 1).coerceAtLeast(0)) }
            return
        }
        val key = keys.keyFor(request)
        var res = repo.scan(sessionId, request, key)
        if (res is ApiResult.Failure && (res.code == ErrorCodes.NETWORK || res.code == "concurrent_retry")) res = repo.scan(sessionId, request, key)
        if (res is ApiResult.Success || (res as? ApiResult.Failure)?.code == SubmissionKeys.CONFLICT) keys.reset()
        when (val result = res) {
            is ApiResult.Success -> {
                val answer = result.data
                val tone = Staff090StocktakeSound.toneFor(answer.scan, answer.tone, _state.value.options)
                _state.update { s ->
                    // The operator's own scan belongs to "only mine" as well: it always goes on top.
                    val session = s.session?.let { current ->
                        Staff090StocktakeMerge.withStatus(
                            current.copy(summary = answer.summary, recentScans = Staff090StocktakeMerge.prepend(current.recentScans, answer.scan)),
                            answer.sessionStatus,
                        )
                    }
                    s.copy(
                        session = session, lastResult = answer.scan, pending = (s.pending - 1).coerceAtLeast(0),
                        feedback = StocktakeFeedback(++feedbackId, tone),
                        filtered = if (s.outcomeFilter == answer.scan.outcome) Staff090StocktakeMerge.prepend(s.filtered, answer.scan, Int.MAX_VALUE) else s.filtered,
                    )
                }
            }
            is ApiResult.Failure -> {
                val offline = result.code == ErrorCodes.NETWORK
                // Never keep codes for later: what was read while the connection was gone is dropped, and said so.
                if (offline) drainQueue()
                _state.update {
                    it.copy(
                        pending = if (offline) 0 else (it.pending - 1).coerceAtLeast(0),
                        forbidden = result.isForbidden(), leave = Staff090StocktakeErrors.moduleOff(result),
                        message = if (offline) StaffMessage(R.string.staff090_stocktake_not_recorded, args = listOf(request.code)) else result.toStocktakeMessage(R.string.staff_error_save),
                    )
                }
                if (Staff090StocktakeErrors.sessionEnded(result)) {
                    drainQueue()
                    reload()
                }
            }
        }
    }

    private fun drainQueue() {
        while (queue.tryReceive().isSuccess) Unit
        _state.update { it.copy(pending = 0) }
    }

    // ---------- Shelf, filters ----------

    fun openShelfPicker() {
        gate.pause()
        _state.update { it.copy(shelfPicker = true, pickerQuery = "") }
    }

    fun closeShelfPicker() {
        gate.resume()
        _state.update { it.copy(shelfPicker = false, pickerQuery = "") }
    }

    fun setPickerQuery(value: String) = _state.update { it.copy(pickerQuery = value) }

    fun pickShelf(item: PickItem) {
        shelfChosen = true
        _state.update { it.copy(shelfId = item.id) }
        closeShelfPicker()
    }

    fun clearShelf() {
        shelfChosen = true
        _state.update { it.copy(shelfId = null) }
    }

    fun setMine(value: Boolean) {
        _state.update { it.copy(mine = value) }
        viewModelScope.launch { poll() }
        if (_state.value.outcomeFilter != null) refreshFiltered()
    }

    fun setOutcomeFilter(code: String?) {
        _state.update { it.copy(outcomeFilter = code, filtered = emptyList(), filteredCursor = null) }
        if (code != null) refreshFiltered()
    }

    private fun refreshFiltered() {
        val s = _state.value
        val outcome = s.outcomeFilter ?: return
        // Keep a list the operator paged through: the poll refreshes only the first page.
        if (s.filteredCursor != null && s.filtered.size > Staff090StocktakeRepository.PAGE) return
        _state.update { it.copy(filteredLoading = it.filtered.isEmpty()) }
        viewModelScope.launch {
            val res = repo.scans(sessionId, outcome, s.mine)
            if (_state.value.outcomeFilter != outcome) return@launch
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(filtered = res.data.items, filteredCursor = res.data.nextCursor, filteredLoading = false)
                    is ApiResult.Failure -> it.copy(filteredLoading = false, forbidden = res.isForbidden(), message = res.toStocktakeMessage(R.string.staff_error_load))
                }
            }
        }
    }

    fun loadMoreFiltered() {
        val s = _state.value
        val outcome = s.outcomeFilter ?: return
        val cursor = s.filteredCursor ?: return
        if (s.filteredLoading) return
        _state.update { it.copy(filteredLoading = true) }
        viewModelScope.launch {
            val res = repo.scans(sessionId, outcome, s.mine, cursor)
            if (_state.value.outcomeFilter != outcome) return@launch
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(filtered = (it.filtered + res.data.items).distinctBy(Staff090StocktakeScan::id), filteredCursor = res.data.nextCursor, filteredLoading = false)
                    is ApiResult.Failure -> it.copy(filteredLoading = false, forbidden = res.isForbidden(), message = res.toStocktakeMessage(R.string.staff_error_load))
                }
            }
        }
    }

    // ---------- Upload ----------

    /** A picked code list: refused here when the server would refuse it anyway (extension, size). */
    fun onPicked(uri: Uri?) {
        uri ?: return
        val limits = Staff090StocktakeUploadRules.Limits.of(_state.value.options?.limits ?: com.pinakes.app.data.model.Staff090StocktakeLimits())
        viewModelScope.launch {
            val read = withContext(Dispatchers.IO) { runCatching { read(uri, limits.maxBytes) }.getOrNull() }
            when {
                read == null -> _state.update { it.copy(message = StaffMessage(R.string.staff_import_read_failed)) }
                !Staff090StocktakeUploadRules.acceptsName(read.name, limits) || !Staff090StocktakeUploadRules.acceptsSize(read.bytes.size.toLong(), limits) ->
                    _state.update { it.copy(message = StaffMessage(R.string.staff090_stocktake_upload_wrong_file)) }
                else -> upload(read)
            }
        }
    }

    private class Picked(val name: String, val mime: String?, val bytes: ByteArray)

    private fun read(uri: Uri, maxBytes: Long): Picked? {
        val resolver = context.contentResolver
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "codes.txt"
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { i -> c.getString(i)?.let { name = it } }
        }
        val out = java.io.ByteArrayOutputStream()
        resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                // One byte over the limit is enough to refuse it: stop reading there.
                if (total > maxBytes) return Picked(name, null, ByteArray((maxBytes + 1).toInt()))
                out.write(buffer, 0, n)
            }
        } ?: return null
        return Picked(name, resolver.getType(uri), out.toByteArray())
    }

    private fun upload(file: Picked) {
        if (_state.value.uploading) return
        _state.update { it.copy(uploading = true) }
        viewModelScope.launch {
            val res = repo.upload(sessionId, file.name, file.bytes, file.mime, _state.value.shelfId)
            _state.update { s ->
                when (res) {
                    is ApiResult.Success -> s.copy(
                        uploading = false, uploadResult = res.data,
                        session = s.session?.copy(summary = res.data.summary),
                    )
                    is ApiResult.Failure -> s.copy(uploading = false, forbidden = res.isForbidden(), message = res.toStocktakeMessage(R.string.staff_error_save))
                }
            }
            if (res is ApiResult.Success) poll()
            if (res is ApiResult.Failure && Staff090StocktakeErrors.sessionEnded(res)) reload()
        }
    }

    fun closeUploadResult() = _state.update { it.copy(uploadResult = null) }

    // ---------- Close / cancel ----------

    fun openClose() {
        val session = _state.value.session ?: return
        val actions = Staff090StocktakeClose.actions(session)
        if (actions.isEmpty()) return
        gate.pause()
        _state.update { it.copy(closeDialog = true, closeAction = if (Staff090StocktakeClose.canResume(session)) Staff090StocktakeClose.LOST else actions.first()) }
    }

    fun setCloseAction(value: String) = _state.update { it.copy(closeAction = value) }

    fun dismissClose() {
        gate.resume()
        _state.update { it.copy(closeDialog = false) }
    }

    /** `lost` refused to this account (403 `not_permitted`): the server's message, the role stays. */
    fun close() {
        val action = _state.value.closeAction
        if (_state.value.closing) return
        _state.update { it.copy(closing = true) }
        viewModelScope.launch {
            val res = repo.close(sessionId, action)
            _state.update { s ->
                when (res) {
                    is ApiResult.Success -> s.copy(
                        closing = false, closeDialog = false, session = res.data,
                        message = res.data.closeResult?.message?.takeIf { it.isNotBlank() }?.let { StaffMessage(text = it) } ?: StaffMessage(R.string.staff090_stocktake_closed),
                    )
                    is ApiResult.Failure -> s.copy(closing = false, closeDialog = false, forbidden = res.isForbidden(), message = res.toStocktakeMessage(R.string.staff_error_save))
                }
            }
            gate.resume()
            if (res is ApiResult.Failure && Staff090StocktakeErrors.sessionEnded(res)) reload()
        }
    }

    fun openCancel() {
        gate.pause()
        _state.update { it.copy(cancelDialog = true) }
    }

    fun dismissCancel() {
        gate.resume()
        _state.update { it.copy(cancelDialog = false) }
    }

    fun cancel() {
        _state.update { it.copy(cancelDialog = false, closing = true) }
        viewModelScope.launch {
            val res = repo.cancel(sessionId)
            _state.update { s ->
                when (res) {
                    is ApiResult.Success -> s.copy(
                        closing = false, session = res.data,
                        message = res.data.message?.takeIf { it.isNotBlank() }?.let { StaffMessage(text = it) } ?: StaffMessage(R.string.staff090_stocktake_cancelled),
                    )
                    is ApiResult.Failure -> s.copy(closing = false, forbidden = res.isForbidden(), message = res.toStocktakeMessage(R.string.staff_error_save))
                }
            }
            gate.resume()
            if (res is ApiResult.Failure && Staff090StocktakeErrors.sessionEnded(res)) reload()
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    companion object {
        /** The web scan page polls every few seconds; so does the app while the screen is visible. */
        const val POLL_MS = 4_000L
    }
}

private val UPLOAD_MIME_TYPES = arrayOf("text/plain", "text/csv", "text/comma-separated-values", "application/vnd.ms-excel")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun Staff090StocktakeSessionScreen(onNavigateUp: () -> Unit, onOpenReport: (Int) -> Unit, onOpenBook: (Int) -> Unit) {
    val vm: StocktakeSessionViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val app: AppViewModel = hiltViewModel()
    val areas by app.staffAreas.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val player = remember { Staff090StocktakeTonePlayer(context.applicationContext) }
    ForbiddenExit(state.forbidden, onNavigateUp)
    LeaveWhen(!areas.s090.stocktake || state.leave, onNavigateUp)
    StaffSnackbarEffect(state.message, snackbar, vm::consumeMessage)
    LaunchedEffect(state.feedback?.id) {
        val feedback = state.feedback ?: return@LaunchedEffect
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        player.play(feedback.tone)
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> vm.onVisible()
                Lifecycle.Event.ON_PAUSE -> vm.onHidden()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            vm.onHidden()
            player.stop()
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), vm::onPicked)
    val session = state.session
    val open = { book: Int -> vm.onHidden(); onOpenBook(book) }

    Scaffold(
        topBar = {
            PinakesTopBar(
                title = session?.name ?: stringResource(R.string.staff090_stocktake),
                onNavigateUp = onNavigateUp,
                actions = {
                    if (session != null) {
                        IconButton(onClick = { onOpenReport(session.id) }) {
                            Icon(Icons.Outlined.Assessment, contentDescription = stringResource(R.string.staff090_stocktake_report))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when {
            state.loading && session == null -> LoadingState(modifier = Modifier.padding(padding))
            session == null -> ErrorState(
                message = state.error?.resolve().orEmpty().ifBlank { stringResource(R.string.staff_error_load) },
                onRetry = vm::reload, modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding).imePadding(),
                contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                if (session.canScan) {
                    item(key = "scanner") {
                        ScannerBox(
                            formats = BarcodeAnalyzer.COPY_FORMATS, onCode = vm::onDecoded, looking = false,
                            permissionText = stringResource(R.string.staff090_stocktake_camera_permission),
                        )
                    }
                    item(key = "shelf") {
                        val shelf = state.options?.shelves?.firstOrNull { it.id == state.shelfId }
                        PickerRow(
                            label = stringResource(R.string.staff090_stocktake_current_shelf),
                            value = shelf?.label?.ifBlank { shelf.name } ?: state.shelfId?.let { "#$it" },
                            placeholder = stringResource(R.string.staff090_stocktake_no_shelf), onClick = vm::openShelfPicker, onClear = vm::clearShelf,
                        )
                    }
                    item(key = "last") { LastResultCard(state.lastResult, state.pending, state.options, onOpenBook = open) }
                    item(key = "manual") {
                        var manual by rememberSaveable { mutableStateOf("") }
                        val submit = { if (vm.submitManual(manual)) manual = "" }
                        PinakesTextField(
                            value = manual,
                            onValueChange = { manual = it.take(state.options?.limits?.maxCodeLength ?: 100); vm.clearManualError() },
                            label = stringResource(R.string.staff090_stocktake_manual_code),
                            isError = state.manualError, errorText = stringResource(R.string.staff_copy_code_required),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { submit() }),
                            trailingIcon = {
                                IconButton(onClick = submit) { Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = stringResource(R.string.staff090_stocktake_send_code)) }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("stocktake-code-input"),
                        )
                    }
                    if (session.canUpload) {
                        item(key = "upload") {
                            SecondaryButton(
                                stringResource(R.string.staff090_stocktake_upload), onClick = { picker.launch(UPLOAD_MIME_TYPES) },
                                enabled = !state.uploading, leadingIcon = Icons.Outlined.UploadFile, modifier = Modifier.fillMaxWidth().testTag("stocktake-upload"),
                            )
                        }
                    }
                } else {
                    item(key = "status") { StatusBanner(session) }
                }
                item(key = "counters") {
                    Column {
                        FormSectionTitle(stringResource(R.string.staff090_stocktake_progress))
                        StocktakeCounters(session.summary, state.options)
                    }
                }
                item(key = "actions") {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.sm)) {
                        SecondaryButton(stringResource(R.string.staff090_stocktake_report), onClick = { onOpenReport(session.id) },
                            leadingIcon = Icons.Outlined.Assessment, modifier = Modifier.fillMaxWidth())
                        if (session.canClose) {
                            PrimaryButton(stringResource(R.string.staff090_stocktake_close), onClick = vm::openClose, loading = state.closing,
                                leadingIcon = Icons.AutoMirrored.Outlined.FactCheck, modifier = Modifier.fillMaxWidth().testTag("stocktake-close"))
                        } else if (Staff090StocktakeClose.canResume(session)) {
                            PrimaryButton(stringResource(R.string.staff090_stocktake_close_resume), onClick = vm::openClose, loading = state.closing,
                                modifier = Modifier.fillMaxWidth().testTag("stocktake-resume"))
                        }
                        if (session.canCancel) {
                            PinakesTextButton(stringResource(R.string.staff090_stocktake_cancel), onClick = vm::openCancel, enabled = !state.closing,
                                modifier = Modifier.align(Alignment.CenterHorizontally).testTag("stocktake-cancel"))
                        }
                    }
                }
                item(key = "recent-title") {
                    Column {
                        FormSectionTitle(stringResource(R.string.staff090_stocktake_recent))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            val chipColors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer, selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            FilterChip(selected = state.outcomeFilter == null, onClick = { vm.setOutcomeFilter(null) },
                                label = { Text(stringResource(R.string.staff090_stocktake_filter_latest)) }, colors = chipColors)
                            state.options?.outcomes?.filter { it.scanResult }?.forEach { outcome ->
                                FilterChip(selected = state.outcomeFilter == outcome.code, onClick = { vm.setOutcomeFilter(outcome.code) },
                                    label = { Text(outcome.label) }, colors = chipColors, modifier = Modifier.testTag("filter-${outcome.code}"))
                            }
                        }
                        SwitchRow(stringResource(R.string.staff090_stocktake_only_mine), state.mine, vm::setMine)
                    }
                }
                if (state.recent.isEmpty() && !state.filteredLoading) {
                    item(key = "recent-empty") {
                        Text(stringResource(R.string.staff090_stocktake_no_scans), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(state.recent, key = { "scan-${it.id}" }) { scan -> StocktakeScanRow(scan, state.options, onOpenBook = open) }
                if (state.outcomeFilter != null && state.filteredCursor != null) {
                    item(key = "recent-more") {
                        PinakesTextButton(stringResource(R.string.staff090_stocktake_more), onClick = vm::loadMoreFiltered, enabled = !state.filteredLoading,
                            modifier = Modifier.fillMaxWidth())
                    }
                }
                item(key = "info") { SessionInfo(session) }
            }
        }
    }

    if (state.shelfPicker) {
        LocalPickerSheet(
            title = stringResource(R.string.staff090_stocktake_current_shelf), placeholder = stringResource(R.string.staff090_stocktake_search_shelf),
            items = state.options?.shelves.orEmpty().map { PickItem(it.id, it.label.ifBlank { it.name }) },
            query = state.pickerQuery, onQuery = vm::setPickerQuery, onPick = vm::pickShelf, onDismiss = vm::closeShelfPicker,
        )
    }
    if (state.closeDialog && session != null) CloseDialog(session, state.closeAction, state.closing, vm::setCloseAction, vm::close, vm::dismissClose)
    if (state.cancelDialog) {
        ConfirmDialog(
            title = stringResource(R.string.staff090_stocktake_cancel_title),
            body = stringResource(R.string.staff090_stocktake_cancel_body),
            confirmLabel = stringResource(R.string.staff090_stocktake_cancel),
            dismissLabel = stringResource(R.string.staff090_stocktake_keep),
            onConfirm = vm::cancel, onDismiss = vm::dismissCancel,
        )
    }
    state.uploadResult?.let { result ->
        AlertDialog(
            onDismissRequest = vm::closeUploadResult,
            shape = MaterialTheme.shapes.large,
            title = { Text(stringResource(R.string.staff090_stocktake_upload_done), style = MaterialTheme.typography.titleMedium) },
            text = {
                Column {
                    if (result.message.isNotBlank()) Text(result.message, style = MaterialTheme.typography.bodyMedium)
                    result.outcomes.filter { it.count > 0 }.forEach { InfoLine(it.label, it.count.toString()) }
                }
            },
            confirmButton = { PrimaryButton(stringResource(R.string.action_confirm), onClick = vm::closeUploadResult) },
        )
    }
}

/** The answer to the latest code, in its outcome's colour: what it is and what to do with it. */
@Composable
internal fun LastResultCard(scan: Staff090StocktakeScan?, pending: Int, options: Staff090StocktakeOptions?, onOpenBook: (Int) -> Unit) {
    if (scan == null) {
        Text(
            if (pending > 0) stringResource(R.string.staff090_stocktake_sending, pending) else stringResource(R.string.staff090_stocktake_scan_hint),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val (bg, fg) = Staff090StocktakeColors.forOutcome(scan.outcome, scan.color, options).pair()
    Surface(
        onClick = { scan.bookId?.takeIf { it > 0 }?.let(onOpenBook) },
        enabled = (scan.bookId ?: 0) > 0,
        shape = MaterialTheme.shapes.large, color = bg, contentColor = fg,
        modifier = Modifier.fillMaxWidth().testTag("stocktake-last-result"),
    ) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(scan.outcomeLabel.ifBlank { scan.outcome }, style = MaterialTheme.typography.titleLarge, color = fg)
            Text(scan.code, style = MaterialTheme.typography.labelLarge, color = fg)
            scan.title?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = fg, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            scan.expectedShelf?.takeIf { it.isNotBlank() }?.let { Text(stringResource(R.string.staff090_stocktake_belongs_on, it), style = MaterialTheme.typography.bodyMedium, color = fg) }
            if (scan.offShelf) scan.copyStatusLabel?.let { Text(stringResource(R.string.staff090_stocktake_recorded_as, it), style = MaterialTheme.typography.bodyMedium, color = fg) }
            if (pending > 0) Text(stringResource(R.string.staff090_stocktake_sending, pending), style = MaterialTheme.typography.bodySmall, color = fg)
        }
    }
}

/** A session that takes no scans: why (closed, cancelled) and what the close did. */
@Composable
private fun StatusBanner(session: Staff090StocktakeSession) {
    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StocktakeBadge(session.statusLabel.ifBlank { session.status }, session.statusColor)
            Text(stringResource(R.string.staff090_stocktake_no_more_scans), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
        session.closeResult?.message?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(Spacing.sm))
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
        if (session.pendingLost > 0) {
            Spacer(Modifier.height(Spacing.sm))
            InlineErrorBanner(message = stringResource(R.string.staff090_stocktake_pending_lost, session.pendingLost))
        }
    }
}

@Composable
private fun SessionInfo(session: Staff090StocktakeSession) {
    Column {
        FormSectionTitle(stringResource(R.string.staff090_stocktake_details))
        InfoCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(session.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                StocktakeBadge(session.statusLabel.ifBlank { session.status }, session.statusColor)
            }
            InfoLine(stringResource(R.string.staff090_stocktake_field_branch), session.branch?.name)
            InfoLine(stringResource(R.string.staff090_stocktake_scope), session.filterLabels.joinToString("\n").ifBlank { stringResource(R.string.staff090_stocktake_whole_library) })
            InfoLine(stringResource(R.string.staff090_stocktake_field_note), session.note)
            InfoLine(stringResource(R.string.staff090_stocktake_opened_label), listOfNotNull(stocktakeDateTime(session.openedAt).ifBlank { null }, session.openedBy?.name?.ifBlank { null }).joinToString(" · "))
            InfoLine(stringResource(R.string.staff090_stocktake_closed_label), listOfNotNull(stocktakeDateTime(session.closedAt).ifBlank { null }, session.closedBy?.name?.ifBlank { null }).joinToString(" · "))
        }
        if (session.history.isNotEmpty()) {
            FormSectionTitle(stringResource(R.string.staff090_stocktake_history))
            InfoCard {
                session.history.forEach { event ->
                    InfoLine(stocktakeDateTime(event.at), listOfNotNull(event.label.ifBlank { event.event }, event.operatorName).joinToString(" · "))
                }
            }
        }
    }
}

/** The missing copies' fate, stated with their count; `lost` appears only when the server allows it. */
@Composable
internal fun CloseDialog(
    session: Staff090StocktakeSession,
    action: String,
    closing: Boolean,
    onAction: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val actions = Staff090StocktakeClose.actions(session)
    val resume = Staff090StocktakeClose.canResume(session)
    AlertDialog(
        onDismissRequest = { if (!closing) onDismiss() },
        shape = MaterialTheme.shapes.large,
        title = { Text(stringResource(if (resume) R.string.staff090_stocktake_close_resume else R.string.staff090_stocktake_close), style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (!resume && actions.size > 1) {
                    actions.forEach { code ->
                        val label = when (code) {
                            Staff090StocktakeClose.LOST -> stringResource(R.string.staff090_stocktake_action_lost)
                            else -> stringResource(R.string.staff090_stocktake_action_none)
                        }
                        Row(
                            Modifier.fillMaxWidth().selectable(selected = action == code, role = Role.RadioButton, onClick = { onAction(code) }).testTag("close-$code"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = action == code, onClick = null)
                            Spacer(Modifier.padding(start = Spacing.sm))
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                Text(
                    stringResource(Staff090StocktakeClose.confirmText(session, action), Staff090StocktakeClose.affected(session)),
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("close-confirm-text"),
                )
            }
        },
        confirmButton = {
            PrimaryButton(stringResource(if (resume) R.string.staff090_stocktake_close_resume else R.string.staff090_stocktake_close), onClick = onConfirm,
                loading = closing, modifier = Modifier.testTag("close-confirm"))
        },
        dismissButton = { PinakesTextButton(stringResource(R.string.action_cancel), onClick = onDismiss, enabled = !closing) },
    )
}
