package com.pinakes.app.ui.screens.staff.s090.stocktake

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pinakes.app.R
import com.pinakes.app.data.model.Staff090Statistics
import com.pinakes.app.data.model.Staff090StatisticsFinesReport
import com.pinakes.app.data.model.Staff090StatisticsMoney
import com.pinakes.app.data.model.Staff090StatisticsSection
import com.pinakes.app.data.model.Staff090StatisticsVisitors
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.repository.Staff090StatisticsRepository
import com.pinakes.app.data.repository.StaffFilesRepository
import com.pinakes.app.data.repository.isForbidden
import com.pinakes.app.ui.common.AppLocale
import com.pinakes.app.ui.common.AppViewModel
import com.pinakes.app.ui.components.BookCover
import com.pinakes.app.ui.components.ErrorState
import com.pinakes.app.ui.components.LoadingState
import com.pinakes.app.ui.components.PinakesTopBar
import com.pinakes.app.ui.components.SecondaryButton
import com.pinakes.app.ui.screens.movements.MoneyFormat
import com.pinakes.app.ui.screens.staff.DatePickerField
import com.pinakes.app.ui.screens.staff.ExportFileDialog
import com.pinakes.app.ui.screens.staff.ForbiddenExit
import com.pinakes.app.ui.screens.staff.FormSectionTitle
import com.pinakes.app.ui.screens.staff.InfoCard
import com.pinakes.app.ui.screens.staff.InfoLine
import com.pinakes.app.ui.screens.staff.LeaveWhen
import com.pinakes.app.ui.screens.staff.StaffMessage
import com.pinakes.app.ui.screens.staff.StaffSnackbarEffect
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

data class StatisticsUiState(
    val data: Staff090Statistics? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: StaffMessage? = null,
    val forbidden: Boolean = false,
    /** The fines report period (YYYY-MM-DD); null until the server's default arrives. */
    val finesFrom: String? = null,
    val finesTo: String? = null,
    val export: java.io.File? = null,
    val message: StaffMessage? = null,
)

/** The statistics page; the fines period is the server's default until the user picks dates. */
@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val repo: Staff090StatisticsRepository,
    private val files: StaffFilesRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(StatisticsUiState())
    val state: StateFlow<StatisticsUiState> = _state.asStateFlow()
    private var generation = 0

    init {
        reload()
    }

    fun reload() {
        val gen = ++generation
        _state.update { it.copy(loading = it.data == null && !it.refreshing, error = null) }
        viewModelScope.launch {
            val s = _state.value
            val res = repo.statistics(s.finesFrom, s.finesTo)
            if (gen != generation) return@launch
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(
                        data = res.data, loading = false, refreshing = false,
                        // The period the server used (its default, or the range swapped when reversed).
                        finesFrom = res.data.finesReport?.from ?: it.finesFrom,
                        finesTo = res.data.finesReport?.to ?: it.finesTo,
                    )
                    is ApiResult.Failure -> it.copy(
                        loading = false, refreshing = false, forbidden = res.isForbidden(),
                        error = if (it.data == null) res.toStaffMessage(R.string.staff_error_load) else null,
                        message = if (it.data != null) res.toStaffMessage(R.string.staff_error_load) else null,
                    )
                }
            }
        }
    }

    fun refresh() {
        _state.update { it.copy(refreshing = true) }
        reload()
    }

    fun setFinesFrom(value: String?) {
        _state.update { it.copy(finesFrom = value) }
        reload()
    }

    fun setFinesTo(value: String?) {
        _state.update { it.copy(finesTo = value) }
        reload()
    }

    fun downloadFines() {
        val s = _state.value
        viewModelScope.launch {
            val name = "multe_${s.finesFrom.orEmpty()}_${s.finesTo.orEmpty()}.csv"
            val res = files.download(Staff090StatisticsRepository.finesCsvPath(s.finesFrom, s.finesTo), name)
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(export = res.data)
                    is ApiResult.Failure -> it.copy(forbidden = res.isForbidden(), message = res.toStaffMessage(R.string.staff_download_failed))
                }
            }
        }
    }

    fun closeExport() = _state.update { it.copy(export = null) }
    fun consumeMessage() = _state.update { it.copy(message = null) }
}

/** One label + bar + value line of a simple horizontal bar chart. */
private data class Bar(val key: String, val label: String, val value: Int)

/**
 * The web statistics page on one scrolling screen: overview, loans by month / state / material
 * as plain bars, the most borrowed books (a tap opens the staff book) and the most active
 * readers, then each 0.9.0 section the server sent (patron types, fines, stocktake), visitors
 * and the fines report with its period and CSV. What the server leaves out is not shown.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun Staff090StatisticsScreen(onNavigateUp: () -> Unit, onOpenBook: (Int) -> Unit) {
    val vm: StatisticsViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val app: AppViewModel = hiltViewModel()
    val areas by app.staffAreas.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    ForbiddenExit(state.forbidden, onNavigateUp)
    LeaveWhen(!areas.s090.statistics, onNavigateUp)
    StaffSnackbarEffect(state.message, snackbar, vm::consumeMessage)
    Scaffold(
        topBar = { PinakesTopBar(title = stringResource(R.string.staff090_statistics), onNavigateUp = onNavigateUp) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val data = state.data
        when {
            state.loading && data == null -> LoadingState(modifier = Modifier.padding(padding))
            data == null -> ErrorState(
                message = state.error?.resolve().orEmpty().ifBlank { stringResource(R.string.staff_error_load) },
                onRetry = vm::reload, modifier = Modifier.padding(padding),
            )
            else -> PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize().padding(padding)) {
                StatisticsBody(data, state.finesFrom, state.finesTo, onOpenBook, vm::setFinesFrom, vm::setFinesTo, vm::downloadFines)
            }
        }
    }

    state.export?.let { file ->
        ExportFileDialog(file = file, mime = "text/csv", title = stringResource(R.string.staff090_statistics_fines_report), onDismiss = vm::closeExport)
    }
}

/** The scrolling content, apart from the ViewModel so it renders from a captured body in tests. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StatisticsBody(
    data: Staff090Statistics,
    finesFrom: String?,
    finesTo: String?,
    onOpenBook: (Int) -> Unit,
    onFinesFrom: (String?) -> Unit,
    onFinesTo: (String?) -> Unit,
    onDownloadFines: () -> Unit,
) {
    val locale = AppLocale.current()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = 48.dp)) {
        item(key = "overview") {
            Column {
                FormSectionTitle(stringResource(R.string.staff090_statistics_overview))
                val o = data.overview
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Tile(R.string.staff090_statistics_books_available, o.booksAvailable, "stat-books-available")
                    Tile(R.string.staff090_statistics_books_on_loan, o.booksOnLoan, "stat-books-on-loan")
                    Tile(R.string.staff090_statistics_active_loans, o.activeLoans, "stat-active")
                    Tile(R.string.staff090_statistics_overdue_loans, o.overdueLoans, "stat-overdue")
                    Tile(R.string.staff090_statistics_completed_loans, o.completedLoans, "stat-completed")
                    Tile(R.string.staff090_statistics_borrowers, o.borrowers, "stat-borrowers")
                }
            }
        }
        item(key = "by-month") {
            BarChart(
                stringResource(R.string.staff090_statistics_by_month),
                data.loansByMonth.map { Bar("m-${it.month}", Staff090StatisticsBars.monthLabel(it.month, locale), it.total) },
            )
        }
        item(key = "by-status") { BarChart(stringResource(R.string.staff090_statistics_by_status), data.loansByStatus.map { Bar("s-${it.status}", it.label.ifBlank { it.status }, it.total) }) }
        item(key = "by-media") { BarChart(stringResource(R.string.staff090_statistics_by_media), data.loansByMediaType.map { Bar("t-${it.mediaType}", it.label.ifBlank { it.mediaType }, it.total) }) }
        if (data.topBooks.isNotEmpty()) {
            item(key = "top-books-title") { FormSectionTitle(stringResource(R.string.staff090_statistics_top_books)) }
            items(data.topBooks, key = { "book-${it.id}" }) { book ->
                Surface(
                    onClick = { onOpenBook(book.id) },
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs).testTag("top-book-${book.id}"),
                ) {
                    Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        BookCover(book.title, book.coverUrl, Modifier.width(44.dp).aspectRatio(2f / 3f), compact = true)
                        Column(Modifier.weight(1f)) {
                            Text(book.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                stringResource(R.string.staff090_statistics_loan_counts, book.totalLoans, book.activeLoans, book.overdueLoans),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        if (data.topReaders.isNotEmpty()) {
            item(key = "top-readers-title") { FormSectionTitle(stringResource(R.string.staff090_statistics_top_readers)) }
            items(data.topReaders, key = { "reader-${it.id}" }) { reader ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
                ) {
                    Column(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
                        Text(reader.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        reader.email?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(
                            stringResource(R.string.staff090_statistics_loan_counts, reader.totalLoans, reader.activeLoans, reader.overdueLoans),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        items(data.sections, key = { "section-${it.id}" }) { section -> Section(section) }
        data.visitors?.let { visitors -> item(key = "visitors") { Visitors(visitors) } }
        data.finesReport?.let { report ->
            item(key = "fines") {
                FinesReport(report, finesFrom, finesTo, locale, onFinesFrom, onFinesTo, onDownloadFines)
            }
        }
    }
}

@Composable
private fun Tile(label: Int, value: Int, tag: String) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.widthIn(min = 150.dp).semantics(mergeDescendants = true) {}.testTag(tag)) {
        Column(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
            Text(value.toString(), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(label), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A titled list of horizontal bars, each scaled to the largest; nothing when there is nothing to show. */
@Composable
private fun BarChart(title: String, bars: List<Bar>) {
    if (bars.isEmpty()) return
    val max = bars.maxOf { it.value }
    Column {
        FormSectionTitle(title)
        InfoCard {
            bars.forEach { bar ->
                Column(Modifier.fillMaxWidth().padding(vertical = Spacing.xs).testTag("bar-${bar.key}")) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(bar.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(bar.value.toString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(Modifier.height(Spacing.xxs))
                    LinearProgressIndicator(
                        progress = { Staff090StatisticsBars.fraction(bar.value, max) },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        drawStopIndicator = {},
                    )
                }
            }
        }
    }
}

@Composable
private fun Section(section: Staff090StatisticsSection) {
    Column(Modifier.testTag("section-${section.id}")) {
        FormSectionTitle(section.title)
        InfoCard {
            section.rows.forEach { InfoLine(it.label, it.display) }
            section.note?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(Spacing.xs))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Visitors(visitors: Staff090StatisticsVisitors) {
    Column(Modifier.testTag("visitors")) {
        FormSectionTitle(stringResource(R.string.staff090_statistics_visitors, visitors.days))
        InfoCard {
            InfoLine(stringResource(R.string.staff090_statistics_visitors_members), visitors.members.toString())
            InfoLine(stringResource(R.string.staff090_statistics_visitors_guests), visitors.guests.toString())
            InfoLine(stringResource(R.string.staff090_statistics_visitors_total), visitors.total.toString())
        }
        val days = visitors.daily.filter { it.total > 0 }
        BarChart(stringResource(R.string.staff090_statistics_visitors_daily), days.map { Bar("v-${it.date}", stocktakeDate(it.date), it.total) })
    }
}

@Composable
private fun FinesReport(
    report: Staff090StatisticsFinesReport,
    from: String?,
    to: String?,
    locale: java.util.Locale,
    onFrom: (String?) -> Unit,
    onTo: (String?) -> Unit,
    onDownload: () -> Unit,
) {
    val money = { cents: Int -> MoneyFormat.format(cents, report.currency, locale) }
    Column(Modifier.testTag("fines-report"), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        FormSectionTitle(stringResource(R.string.staff090_statistics_fines_report))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Column(Modifier.weight(1f)) { DatePickerField(stringResource(R.string.staff090_statistics_from), from, onFrom) }
            Column(Modifier.weight(1f)) { DatePickerField(stringResource(R.string.staff090_statistics_to), to, onTo) }
        }
        if (report.months.isEmpty()) {
            Text(stringResource(R.string.staff090_statistics_fines_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        report.months.forEach { month ->
            InfoCard(Modifier.testTag("fines-${month.month}")) {
                Text(Staff090StatisticsBars.monthLabel(month.month, locale), style = MaterialTheme.typography.titleSmall)
                MoneyLines(month.money, money)
            }
        }
        InfoCard(Modifier.testTag("fines-totals")) {
            Text(stringResource(R.string.staff090_statistics_fines_totals), style = MaterialTheme.typography.titleSmall)
            MoneyLines(report.totals, money)
        }
        SecondaryButton(
            stringResource(R.string.staff090_statistics_fines_csv), onClick = onDownload, leadingIcon = Icons.Outlined.Download,
            modifier = Modifier.fillMaxWidth().testTag("fines-csv"),
        )
    }
}

@Composable
private fun MoneyLines(m: Staff090StatisticsMoney, money: (Int) -> String) {
    InfoLine(stringResource(R.string.staff090_statistics_charged), money(m.chargedCents))
    InfoLine(stringResource(R.string.staff090_statistics_collected), money(m.collectedCents))
    InfoLine(stringResource(R.string.staff090_statistics_waived), money(m.waivedCents))
    InfoLine(stringResource(R.string.staff090_statistics_reversed), money(m.reversedCents))
}

/** A visitor-log day (YYYY-MM-DD) in the app's date style. */
private fun stocktakeDate(raw: String): String = com.pinakes.app.ui.common.DateFormat.date(raw)
