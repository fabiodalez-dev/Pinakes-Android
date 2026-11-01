package com.pinakes.app.ui.screens.staff.s090.stocktake

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pinakes.app.R
import com.pinakes.app.data.model.Staff090StocktakeOptions
import com.pinakes.app.data.model.Staff090StocktakeReportRow
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.repository.Staff090StocktakeRepository
import com.pinakes.app.data.repository.StaffFilesRepository
import com.pinakes.app.data.repository.isForbidden
import com.pinakes.app.ui.common.AppViewModel
import com.pinakes.app.ui.components.PinakesTopBar
import com.pinakes.app.ui.screens.staff.ExportFileDialog
import com.pinakes.app.ui.screens.staff.ForbiddenExit
import com.pinakes.app.ui.screens.staff.LeaveWhen
import com.pinakes.app.ui.screens.staff.StaffLoadBody
import com.pinakes.app.ui.screens.staff.StaffMessage
import com.pinakes.app.ui.screens.staff.StaffSnackbarEffect
import com.pinakes.app.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StocktakeReportUiState(
    /** Report category (an outcome code); null = every row. */
    val category: String? = null,
    val items: List<Staff090StocktakeReportRow> = emptyList(),
    val nextCursor: String? = null,
    val total: Int? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val error: StaffMessage? = null,
    val forbidden: Boolean = false,
    val options: Staff090StocktakeOptions? = null,
    val downloading: Boolean = false,
    val export: java.io.File? = null,
    val exportMime: String = "text/csv",
    val message: StaffMessage? = null,
)

/** The report of a session by category, as the web final report lists it, with the CSV and PDF downloads. */
@HiltViewModel
class StocktakeReportViewModel @Inject constructor(
    private val repo: Staff090StocktakeRepository,
    private val files: StaffFilesRepository,
    savedState: SavedStateHandle,
) : ViewModel() {
    val sessionId: Int = savedState.get<Int>(STOCKTAKE_ARG_SESSION) ?: 0
    private val _state = MutableStateFlow(StocktakeReportUiState())
    val state: StateFlow<StocktakeReportUiState> = _state.asStateFlow()
    private var generation = 0

    init {
        viewModelScope.launch { (repo.options() as? ApiResult.Success)?.data?.let { o -> _state.update { it.copy(options = o) } } }
        reload()
    }

    fun reload() {
        val gen = ++generation
        _state.update { it.copy(loading = it.items.isEmpty() && !it.refreshing, error = null) }
        viewModelScope.launch {
            val res = repo.report(sessionId, _state.value.category)
            if (gen != generation) return@launch
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(items = res.data.items, nextCursor = res.data.nextCursor, total = res.data.totalCount, loading = false, refreshing = false)
                    is ApiResult.Failure -> it.copy(loading = false, refreshing = false, forbidden = res.isForbidden(), error = res.toStocktakeMessage(R.string.staff_error_load))
                }
            }
        }
    }

    fun refresh() {
        _state.update { it.copy(refreshing = true) }
        reload()
    }

    fun loadMore() {
        val s = _state.value
        val cursor = s.nextCursor ?: return
        if (s.loadingMore || s.loading) return
        val gen = generation
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            val res = repo.report(sessionId, s.category, cursor)
            if (gen != generation) {
                _state.update { it.copy(loadingMore = false) }
                return@launch
            }
            _state.update {
                when (res) {
                    // Report rows have no id of their own: pages are appended as they come.
                    is ApiResult.Success -> it.copy(items = it.items + res.data.items, nextCursor = res.data.nextCursor, loadingMore = false)
                    is ApiResult.Failure -> it.copy(loadingMore = false, forbidden = res.isForbidden(), message = res.toStocktakeMessage(R.string.staff_error_load))
                }
            }
        }
    }

    fun selectCategory(category: String?) {
        if (category == _state.value.category) return
        _state.update { it.copy(category = category, items = emptyList(), nextCursor = null, total = null) }
        reload()
    }

    fun downloadCsv() = download(Staff090StocktakeRepository.reportCsvPath(sessionId), "revisione-$sessionId.csv", "text/csv")

    /** A report with too many rows is refused as PDF (422 `report_too_large`): the CSV holds any size. */
    fun downloadPdf() = download(Staff090StocktakeRepository.reportPdfPath(sessionId), "revisione-$sessionId.pdf", "application/pdf")

    private fun download(path: String, name: String, mime: String) {
        if (_state.value.downloading) return
        _state.update { it.copy(downloading = true) }
        viewModelScope.launch {
            val res = files.download(path, name)
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(downloading = false, export = res.data, exportMime = mime)
                    is ApiResult.Failure -> it.copy(downloading = false, forbidden = res.isForbidden(), message = res.toStocktakeMessage(R.string.staff_download_failed))
                }
            }
        }
    }

    fun closeExport() = _state.update { it.copy(export = null) }
    fun consumeMessage() = _state.update { it.copy(message = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Staff090StocktakeReportScreen(onNavigateUp: () -> Unit) {
    val vm: StocktakeReportViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val app: AppViewModel = hiltViewModel()
    val areas by app.staffAreas.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    ForbiddenExit(state.forbidden, onNavigateUp)
    LeaveWhen(!areas.s090.stocktake, onNavigateUp)
    StaffSnackbarEffect(state.message, snackbar, vm::consumeMessage)
    val categories = listOf<Pair<String?, String>>(null to stringResource(R.string.staff090_stocktake_filter_all)) +
        state.options?.outcomes.orEmpty().map { it.code to it.label }

    Scaffold(
        topBar = {
            PinakesTopBar(
                title = stringResource(R.string.staff090_stocktake_report),
                onNavigateUp = onNavigateUp,
                actions = {
                    IconButton(onClick = vm::downloadCsv, enabled = !state.downloading, modifier = Modifier.testTag("report-csv")) {
                        Icon(Icons.Outlined.TableChart, contentDescription = stringResource(R.string.staff090_stocktake_download_csv))
                    }
                    IconButton(onClick = vm::downloadPdf, enabled = !state.downloading, modifier = Modifier.testTag("report-pdf")) {
                        Icon(Icons.Outlined.PictureAsPdf, contentDescription = stringResource(R.string.staff090_stocktake_download_pdf))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryScrollableTabRow(selectedTabIndex = categories.indexOfFirst { it.first == state.category }.coerceAtLeast(0), edgePadding = Spacing.lg) {
                categories.forEach { (code, label) ->
                    Tab(selected = code == state.category, onClick = { vm.selectCategory(code) }, text = { Text(label) })
                }
            }
            state.total?.let {
                Text(stringResource(R.string.staff090_stocktake_report_rows, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm))
            }
            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
                StaffLoadBody(
                    loading = state.loading, error = state.error, isEmpty = state.items.isEmpty(),
                    emptyTitle = stringResource(R.string.staff090_stocktake_report_empty), emptyIcon = Icons.Outlined.Assessment, onRetry = vm::refresh,
                ) {
                    val listState = rememberLazyListState()
                    val nearEnd by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= state.items.size - 5 } == true } }
                    LaunchedEffect(nearEnd, state.nextCursor) { if (nearEnd) vm.loadMore() }
                    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = 48.dp)) {
                        itemsIndexed(state.items, key = { index, row -> "$index-${row.category}-${row.code}" }) { _, row -> ReportRow(row, state.options) }
                    }
                }
            }
        }
    }

    state.export?.let { file ->
        ExportFileDialog(file = file, mime = state.exportMime, title = stringResource(R.string.staff090_stocktake_report), onDismiss = vm::closeExport)
    }
}

@Composable
private fun ReportRow(row: Staff090StocktakeReportRow, options: Staff090StocktakeOptions?) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
    ) {
        Column(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StocktakeBadge(row.categoryLabel.ifBlank { row.category }, Staff090StocktakeColors.forOutcome(row.category, row.color, options))
                Text(row.code, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
            row.title?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            val meta = listOfNotNull(
                row.collocation?.takeIf { it.isNotBlank() },
                row.expectedShelf?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.staff090_stocktake_belongs_on, it) },
                row.copyStatusLabel?.takeIf { it.isNotBlank() },
                row.operatorName?.takeIf { it.isNotBlank() },
                stocktakeDateTime(row.scannedAt).takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
