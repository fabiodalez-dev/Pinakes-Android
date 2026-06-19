package com.pinakes.app.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pinakes.app.R
import com.pinakes.app.data.model.LoanItem
import com.pinakes.app.data.model.ReservationItem
import com.pinakes.app.ui.common.DateFormat
import com.pinakes.app.ui.common.LocalServices
import com.pinakes.app.ui.common.StatusMapping
import com.pinakes.app.ui.common.UiState
import com.pinakes.app.ui.common.resolvedMessage
import com.pinakes.app.ui.components.ConfirmDialog
import com.pinakes.app.ui.components.EmptyState
import com.pinakes.app.ui.components.ErrorState
import com.pinakes.app.ui.components.LoadingState
import com.pinakes.app.ui.components.MediaRow
import com.pinakes.app.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(onBookClick: (Int) -> Unit) {
    val services = LocalServices.current
    val vm: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory(services.libraryRepository))
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var confirmCancelId by remember { mutableStateOf<Int?>(null) }

    val snackbarHost = remember { androidx.compose.material3.SnackbarHostState() }
    val snackbarMessage = state.snackbar ?: state.snackbarRes?.let { stringResource(it) }
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { snackbarHost.showSnackbar(it); vm.consumeSnackbar() }
    }

    androidx.compose.material3.Scaffold(
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHost) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val tabs = listOf(
                stringResource(R.string.library_tab_active),
                stringResource(R.string.library_tab_history),
                stringResource(R.string.library_tab_reservations),
            )
            TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface) {
                tabs.forEachIndexed { i, label ->
                    Tab(
                        selected = tab == i,
                        onClick = { tab = i },
                        text = { Text(label, style = MaterialTheme.typography.labelLarge) },
                    )
                }
            }

            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = vm::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when (val content = state.content) {
                    is UiState.Loading -> LoadingState(label = stringResource(R.string.library_loading))
                    is UiState.Error -> ErrorState(message = content.resolvedMessage(), onRetry = vm::refresh)
                    is UiState.Success -> {
                        val data = content.data
                        when (tab) {
                            0 -> ActiveLoanList(
                                loans = data.loans.active + data.loans.pending,
                                emptyTitle = stringResource(R.string.library_empty_active_title),
                                emptySubtitle = stringResource(R.string.library_empty_active_subtitle),
                                onBookClick = onBookClick,
                            )
                            1 -> LoanList(
                                loans = data.loans.history,
                                emptyTitle = stringResource(R.string.library_empty_history_title),
                                emptySubtitle = stringResource(R.string.library_empty_history_subtitle),
                                onBookClick = onBookClick,
                            )
                            else -> ReservationList(
                                reservations = data.reservations,
                                cancelingId = state.cancelingId,
                                onBookClick = onBookClick,
                                onCancel = { confirmCancelId = it },
                            )
                        }
                    }
                }
            }
        }
    }

    confirmCancelId?.let { id ->
        ConfirmDialog(
            title = stringResource(R.string.library_confirm_cancel_title),
            body = stringResource(R.string.library_confirm_cancel_body),
            confirmLabel = stringResource(R.string.library_confirm_cancel_confirm),
            dismissLabel = stringResource(R.string.library_confirm_cancel_keep),
            onConfirm = { vm.cancelReservation(id); confirmCancelId = null },
            onDismiss = { confirmCancelId = null },
        )
    }
}

/**
 * "Active" tab: shows the user's current loan situation in urgency order with small section
 * headers — OVERDUE (red) first, then ON LOAN (with due date), then READY/SCHEDULED, then
 * PENDING requests. Each row carries a clear due-date line and the localized status chip.
 */
@Composable
private fun ActiveLoanList(
    loans: List<LoanItem>,
    emptyTitle: String,
    emptySubtitle: String,
    onBookClick: (Int) -> Unit,
) {
    if (loans.isEmpty()) {
        EmptyState(title = emptyTitle, subtitle = emptySubtitle, icon = Icons.AutoMirrored.Outlined.LibraryBooks)
        return
    }
    // Group by urgency bucket, preserving the bucket order (Overdue → … → Pending).
    val grouped = loans
        .groupBy { StatusMapping.loanGroup(it.status) }
        .toSortedMap(compareBy { it.order })

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        grouped.forEach { (group, groupLoans) ->
            val headerRes = when (group) {
                StatusMapping.LoanGroup.Overdue -> R.string.library_section_overdue
                StatusMapping.LoanGroup.OnLoan -> R.string.library_section_on_loan
                StatusMapping.LoanGroup.ReadyScheduled -> R.string.library_section_upcoming
                StatusMapping.LoanGroup.Pending -> R.string.library_section_pending
            }
            val isOverdue = group == StatusMapping.LoanGroup.Overdue
            item(key = "header_${group.name}") {
                Text(
                    text = stringResource(headerRes),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isOverdue) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.xs),
                )
            }
            items(groupLoans, key = { it.id }) { loan ->
                LoanRow(loan = loan, onBookClick = onBookClick)
            }
        }
    }
}

@Composable
private fun LoanList(
    loans: List<LoanItem>,
    emptyTitle: String,
    emptySubtitle: String,
    onBookClick: (Int) -> Unit,
) {
    if (loans.isEmpty()) {
        EmptyState(title = emptyTitle, subtitle = emptySubtitle, icon = Icons.AutoMirrored.Outlined.LibraryBooks)
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        items(loans, key = { it.id }) { loan ->
            LoanRow(loan = loan, onBookClick = onBookClick)
        }
    }
}

/**
 * A single loan row. The metadata line is the clearest signal of the loan's timing:
 * an overdue loan shows "Overdue" in red; an active loan shows "Due <date>"; a returned
 * loan shows "Returned <date>"; otherwise the borrow date. The status chip carries the
 * localized, colour-coded state.
 */
@Composable
private fun androidx.compose.foundation.lazy.LazyItemScope.LoanRow(
    loan: LoanItem,
    onBookClick: (Int) -> Unit,
) {
    val (status, statusLabel) = StatusMapping.loan(loan.status)
    val label = statusLabel.resId?.let { stringResource(it) } ?: statusLabel.fallback
    val overdue = StatusMapping.loanGroup(loan.status) == StatusMapping.LoanGroup.Overdue
    val dateLine = when {
        overdue && loan.dueAt != null ->
            stringResource(R.string.library_overdue_since, DateFormat.date(loan.dueAt))
        overdue -> stringResource(R.string.library_overdue_label)
        loan.returnedAt != null -> stringResource(R.string.library_returned_on, DateFormat.date(loan.returnedAt))
        loan.dueAt != null -> stringResource(R.string.library_due_label, DateFormat.date(loan.dueAt))
        loan.loanedAt != null -> stringResource(R.string.library_borrowed_on, DateFormat.date(loan.loanedAt))
        else -> null
    }
    MediaRow(
        modifier = Modifier.animateItem(),
        title = loan.title,
        coverUrl = loan.coverUrl,
        line1 = dateLine,
        line1Color = if (overdue) MaterialTheme.colorScheme.error else null,
        status = status,
        statusLabel = label,
        onClick = { onBookClick(loan.bookId) },
    )
}

@Composable
private fun ReservationList(
    reservations: List<ReservationItem>,
    cancelingId: Int?,
    onBookClick: (Int) -> Unit,
    onCancel: (Int) -> Unit,
) {
    if (reservations.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.library_empty_reservations_title),
            subtitle = stringResource(R.string.library_empty_reservations_subtitle),
            icon = Icons.AutoMirrored.Outlined.LibraryBooks,
        )
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        items(reservations, key = { it.id }) { r ->
            val (status, statusLabel) = StatusMapping.reservation(r.status)
            val label = statusLabel.resId?.let { stringResource(it) } ?: statusLabel.fallback
            val queueText = r.queuePosition?.let { stringResource(R.string.library_queue_position, it) }
            val line = buildString {
                if (r.requestedFrom != null) append(DateFormat.date(r.requestedFrom))
                if (r.requestedTo != null) append(" – ${DateFormat.date(r.requestedTo)}")
                if (queueText != null) {
                    if (isNotEmpty()) append(" · ")
                    append(queueText)
                }
            }.ifBlank { null }
            MediaRow(
                modifier = Modifier.animateItem(),
                title = r.title,
                coverUrl = r.coverUrl,
                line1 = line,
                status = status,
                statusLabel = label,
                onClick = { onBookClick(r.bookId) },
                trailing = if (StatusMapping.isReservationCancellable(r.status)) {
                    {
                        TextButton(
                            onClick = { onCancel(r.id) },
                            enabled = cancelingId != r.id,
                        ) { Text(stringResource(R.string.library_cancel)) }
                    }
                } else null,
            )
        }
    }
}
