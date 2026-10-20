package com.pinakes.app.ui.screens.staff.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pinakes.app.R
import com.pinakes.app.data.model.EnrichOverview
import com.pinakes.app.data.model.MissingCover
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.network.ErrorCodes
import com.pinakes.app.data.repository.StaffContentRepository
import com.pinakes.app.data.repository.isForbidden
import com.pinakes.app.ui.common.AppViewModel
import com.pinakes.app.ui.components.ConfirmDialog
import com.pinakes.app.ui.components.PinakesTopBar
import com.pinakes.app.ui.components.PrimaryButton
import com.pinakes.app.ui.components.SecondaryButton
import com.pinakes.app.ui.screens.staff.ForbiddenExit
import com.pinakes.app.ui.screens.staff.FormSectionTitle
import com.pinakes.app.ui.screens.staff.InfoCard
import com.pinakes.app.ui.screens.staff.InfoLine
import com.pinakes.app.ui.screens.staff.LeaveWhen
import com.pinakes.app.ui.screens.staff.ProgressLine
import com.pinakes.app.ui.screens.staff.StaffMessage
import com.pinakes.app.ui.screens.staff.StaffSnackbarEffect
import com.pinakes.app.ui.screens.staff.SwitchRow
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** The per-book cover loop: how far it got and what it found. */
data class CoverLoop(val total: Int = 0, val done: Int = 0, val fetched: Int = 0, val notFound: Int = 0, val waiting: Boolean = false)

data class CoversUiState(
    val missing: Int? = null,
    val enrich: EnrichOverview? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val loop: CoverLoop? = null,
    val looping: Boolean = false,
    val syncing: Boolean = false,
    val enriching: Boolean = false,
    val message: StaffMessage? = null,
    val forbidden: Boolean = false,
)

/**
 * Covers and bulk enrichment. The default action is the per-book loop over the missing covers,
 * with progress, cancel and the server's rate limit honoured; the server-side sync and an
 * enrichment batch run synchronously for minutes, so they sit behind a confirmation and use the
 * long-timeout client.
 */
@HiltViewModel
class StaffCoversViewModel @Inject constructor(private val content: StaffContentRepository) : ViewModel() {
    private val _state = MutableStateFlow(CoversUiState())
    val state: StateFlow<CoversUiState> = _state.asStateFlow()
    private var loopJob: Job? = null

    init { reload() }

    fun reload() {
        viewModelScope.launch {
            val missing = content.missingCovers(limit = 1)
            val enrich = content.enrich()
            _state.update {
                it.copy(
                    loading = false, refreshing = false,
                    missing = (missing as? ApiResult.Success)?.let { m -> m.data.totalCount ?: m.data.items.size } ?: it.missing,
                    enrich = (enrich as? ApiResult.Success)?.data ?: it.enrich,
                    forbidden = (missing as? ApiResult.Failure)?.isForbidden() == true || (enrich as? ApiResult.Failure)?.isForbidden() == true,
                    message = (missing as? ApiResult.Failure)?.toStaffMessage(R.string.staff_error_load),
                )
            }
        }
    }

    fun refresh() {
        _state.update { it.copy(refreshing = true) }
        reload()
    }

    fun startLoop() {
        if (loopJob?.isActive == true) return
        _state.update { it.copy(looping = true, loop = CoverLoop()) }
        loopJob = viewModelScope.launch {
            val books = ArrayList<MissingCover>()
            var cursor: String? = null
            do {
                when (val page = content.missingCovers(cursor, 50)) {
                    is ApiResult.Success -> { books += page.data.items; cursor = page.data.nextCursor }
                    is ApiResult.Failure -> {
                        _state.update { it.copy(looping = false, forbidden = page.isForbidden(), message = page.toStaffMessage(R.string.staff_error_load)) }
                        return@launch
                    }
                }
            } while (cursor != null && isActive && books.size < 5_000)
            _state.update { it.copy(loop = CoverLoop(total = books.size)) }
            for (book in books) {
                if (!isActive) break
                var res = content.fetchCover(book.id)
                // 30 fetches a minute: wait as the server says, then retry the same book.
                while (res is ApiResult.Failure && res.code == ErrorCodes.RATE_LIMITED && isActive) {
                    _state.update { it.copy(loop = it.loop?.copy(waiting = true)) }
                    delay(((res.meta?.retryAfter?.toLong() ?: res.retryAfterSeconds ?: 30L).coerceIn(1, 120)) * 1_000)
                    _state.update { it.copy(loop = it.loop?.copy(waiting = false)) }
                    res = content.fetchCover(book.id)
                }
                when (val outcome = res) {
                    is ApiResult.Success -> _state.update { s ->
                        val l = s.loop ?: CoverLoop()
                        val got = outcome.data.fetched
                        s.copy(loop = l.copy(done = l.done + 1, fetched = l.fetched + if (got) 1 else 0, notFound = l.notFound + if (got) 0 else 1))
                    }
                    is ApiResult.Failure -> {
                        if (outcome.isForbidden() || outcome.code == ErrorCodes.NETWORK) {
                            _state.update { it.copy(forbidden = outcome.isForbidden(), message = outcome.toStaffMessage(R.string.staff_error_load)) }
                            break
                        }
                        _state.update { s -> val l = s.loop ?: CoverLoop(); s.copy(loop = l.copy(done = l.done + 1, notFound = l.notFound + 1)) }
                    }
                }
            }
            _state.update { it.copy(looping = false) }
            reload()
        }
    }

    fun cancelLoop() {
        loopJob?.cancel()
        _state.update { it.copy(looping = false) }
        reload()
    }

    fun sync() {
        if (_state.value.syncing) return
        _state.update { it.copy(syncing = true) }
        viewModelScope.launch {
            val res = content.syncCovers()
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(syncing = false, message = StaffMessage(R.string.staff_covers_sync_done, args = listOf(res.data.synced, res.data.total)))
                    is ApiResult.Failure -> it.copy(syncing = false, forbidden = res.isForbidden(), message = res.toStaffMessage(R.string.staff_error_save))
                }
            }
            reload()
        }
    }

    fun enrichBatch() {
        if (_state.value.enriching) return
        _state.update { it.copy(enriching = true) }
        viewModelScope.launch {
            val res = content.enrichBatch()
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(enriching = false, message = StaffMessage(R.string.staff_enrich_done, args = listOf(res.data.enriched, res.data.processed)))
                    is ApiResult.Failure -> it.copy(enriching = false, forbidden = res.isForbidden(), message = res.toStaffMessage(R.string.staff_error_save))
                }
            }
            reload()
        }
    }

    fun setCron(enabled: Boolean) {
        viewModelScope.launch {
            val res = content.setEnrichCron(enabled)
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(enrich = it.enrich?.copy(cronEnabled = res.data.enabled))
                    is ApiResult.Failure -> it.copy(forbidden = res.isForbidden(), message = res.toStaffMessage(R.string.staff_error_save))
                }
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffCoversScreen(onNavigateUp: () -> Unit) {
    val vm: StaffCoversViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val app: AppViewModel = hiltViewModel()
    val areas by app.staffAreas.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirm by rememberSaveable { mutableStateOf<String?>(null) }
    ForbiddenExit(state.forbidden, onNavigateUp)
    LeaveWhen(!areas.covers, onNavigateUp)
    StaffSnackbarEffect(state.message, snackbar, vm::consumeMessage)

    Scaffold(
        topBar = { PinakesTopBar(title = stringResource(R.string.staff_covers), onNavigateUp = onNavigateUp) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FormSectionTitle(stringResource(R.string.staff_covers_missing_title))
                Text(
                    state.missing?.let { stringResource(R.string.staff_covers_missing_count, it) } ?: stringResource(R.string.staff_covers_missing_unknown),
                    style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("missing-count"),
                )
                state.loop?.let { loop ->
                    ProgressLine(loop.done, loop.total, stringResource(R.string.staff_covers_loop_progress, loop.done, loop.total, loop.fetched, loop.notFound))
                    if (loop.waiting) Text(stringResource(R.string.staff_covers_rate_wait), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (state.looping) {
                    SecondaryButton(stringResource(R.string.staff_covers_stop), onClick = vm::cancelLoop, modifier = Modifier.fillMaxWidth())
                } else {
                    PrimaryButton(stringResource(R.string.staff_covers_fetch_missing), onClick = vm::startLoop, enabled = state.missing != 0,
                        modifier = Modifier.fillMaxWidth().testTag("covers-loop"))
                }
                SecondaryButton(stringResource(R.string.staff_covers_server_sync), onClick = { confirm = "sync" }, enabled = !state.syncing && !state.looping,
                    modifier = Modifier.fillMaxWidth())
                if (state.syncing) Text(stringResource(R.string.staff_long_job_running), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                FormSectionTitle(stringResource(R.string.staff_enrich_title))
                state.enrich?.let { e ->
                    InfoCard {
                        InfoLine(stringResource(R.string.staff_enrich_with_isbn), e.stats.totalWithIsbn.toString())
                        InfoLine(stringResource(R.string.staff_enrich_missing_cover), e.stats.missingCover.toString())
                        InfoLine(stringResource(R.string.staff_enrich_missing_description), e.stats.missingDescription.toString())
                        InfoLine(stringResource(R.string.staff_enrich_pending), e.stats.pending.toString())
                    }
                    SwitchRow(stringResource(R.string.staff_enrich_cron), e.cronEnabled, vm::setCron, subtitle = stringResource(R.string.staff_enrich_cron_hint))
                }
                SecondaryButton(stringResource(R.string.staff_enrich_run), onClick = { confirm = "enrich" }, enabled = !state.enriching, modifier = Modifier.fillMaxWidth())
                if (state.enriching) Text(stringResource(R.string.staff_long_job_running), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(Spacing.xxl))
            }
        }
    }

    when (confirm) {
        "sync" -> ConfirmDialog(
            title = stringResource(R.string.staff_covers_server_sync), body = stringResource(R.string.staff_covers_server_sync_body),
            onConfirm = { confirm = null; vm.sync() }, onDismiss = { confirm = null },
        )
        "enrich" -> ConfirmDialog(
            title = stringResource(R.string.staff_enrich_run), body = stringResource(R.string.staff_enrich_run_body),
            onConfirm = { confirm = null; vm.enrichBatch() }, onDismiss = { confirm = null },
        )
    }
}
