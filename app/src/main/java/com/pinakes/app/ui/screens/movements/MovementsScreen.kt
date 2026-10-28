package com.pinakes.app.ui.screens.movements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pinakes.app.R
import com.pinakes.app.data.model.AccruedFine
import com.pinakes.app.data.model.Movement
import com.pinakes.app.data.model.MovementsPayload
import com.pinakes.app.ui.common.AppLocale
import com.pinakes.app.ui.common.AppViewModel
import com.pinakes.app.ui.common.DateFormat
import com.pinakes.app.ui.common.UiState
import com.pinakes.app.ui.common.resolvedMessage
import com.pinakes.app.ui.components.EmptyState
import com.pinakes.app.ui.components.ErrorState
import com.pinakes.app.ui.components.LoadingState
import com.pinakes.app.ui.components.PinakesTopBar
import com.pinakes.app.ui.components.SectionHeader
import com.pinakes.app.ui.theme.Spacing

/**
 * The reader's balance and movements (fines ledger, Pinakes 0.9.0). Reached from the profile
 * only while `/health` advertises `features.fines`; if the library turns fines off meanwhile
 * the server answers 404 `feature_disabled` and the page simply closes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovementsScreen(
    onNavigateUp: () -> Unit,
) {
    val vm: MovementsViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val app: AppViewModel = hiltViewModel()
    val features by app.features.collectAsStateWithLifecycle()
    LaunchedEffect(state.disabled, features.showMovements) {
        if (state.disabled || !features.showMovements) onNavigateUp()
    }
    // A failed refresh keeps the last balance on screen, but says it may be out of date.
    val snackbar = remember { SnackbarHostState() }
    val refreshFailedText = stringResource(R.string.movements_refresh_failed)
    LaunchedEffect(state.refreshFailed) {
        if (state.refreshFailed) {
            snackbar.showSnackbar(refreshFailedText)
            vm.consumeRefreshFailed()
        }
    }

    Scaffold(
        topBar = { PinakesTopBar(title = stringResource(R.string.title_movements), onNavigateUp = onNavigateUp) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = vm::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when (val content = state.content) {
                is UiState.Loading -> LoadingState(label = stringResource(R.string.movements_loading))
                is UiState.Error -> ErrorState(message = content.resolvedMessage(), onRetry = vm::load)
                is UiState.Success -> MovementsContent(content.data)
            }
        }
    }
}

@Composable
private fun MovementsContent(data: MovementsPayload) {
    val locale = AppLocale.current()
    val money = { cents: Int -> MoneyFormat.format(cents, data.currency, locale) }
    LazyColumn(
        Modifier.fillMaxSize().testTag("movements-list"),
        contentPadding = PaddingValues(vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item(key = "balance") { BalanceCard(data, money) }
        // The total shows even when the server sends it without the per-loan rows.
        if (data.accruedCents != 0 || data.accrued.isNotEmpty()) {
            item(key = "accrued-title") {
                SectionHeader(title = stringResource(R.string.movements_accrued_title, money(data.accruedCents)))
            }
            // Keys fall back to the position: a row without its id must not collide with another.
            itemsIndexed(data.accrued, key = { i, a -> MoneyFormat.rowKey("accrued", i, a.loanId) }) { _, a -> AccruedRow(a, money) }
        }
        item(key = "items-title") { SectionHeader(title = stringResource(R.string.movements_history_title)) }
        if (data.items.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    title = stringResource(R.string.movements_empty_title),
                    subtitle = stringResource(R.string.movements_empty_subtitle),
                    icon = Icons.Outlined.AccountBalanceWallet,
                    modifier = Modifier.testTag("movements-empty"),
                )
            }
        } else {
            itemsIndexed(data.items, key = { i, m -> MoneyFormat.rowKey("movement", i, m.id) }) { _, m -> MovementRow(m, money) }
        }
    }
}

@Composable
private fun BalanceCard(data: MovementsPayload, money: (Int) -> String) {
    val kind = MoneyFormat.balanceKind(data.balanceCents)
    Column(Modifier.padding(horizontal = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth().testTag("movements-balance"),
        ) {
            Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(stringResource(R.string.movements_balance), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    money(kotlin.math.abs(data.balanceCents)),
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (kind == BalanceKind.Owed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    stringResource(
                        when (kind) {
                            BalanceKind.Owed -> R.string.movements_balance_owed
                            BalanceKind.Credit -> R.string.movements_balance_credit
                            BalanceKind.Settled -> R.string.movements_balance_settled
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (data.debtBlocked) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth().testTag("movements-debt-blocked"),
            ) {
                Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(stringResource(R.string.movements_debt_blocked_title), style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer)
                    Text(
                        data.debtBlockThresholdCents?.let { stringResource(R.string.movements_debt_blocked_body, money(it)) }
                            ?: stringResource(R.string.movements_debt_blocked_body_plain),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun AccruedRow(fine: AccruedFine, money: (Int) -> String) {
    LedgerRow(
        title = fine.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.movements_untitled),
        subtitle = stringResource(R.string.movements_accrued_row, DateFormat.date(fine.dueAt), fine.daysLate),
        amount = money(fine.amountCents),
        owed = true,
    )
}

@Composable
private fun MovementRow(m: Movement, money: (Int) -> String) {
    val title = m.typeLabel?.takeIf { it.isNotBlank() } ?: m.type
    val detail = listOfNotNull(
        m.bookTitle?.takeIf { it.isNotBlank() },
        m.description?.takeIf { it.isNotBlank() && it != title },
        m.paymentMethod?.takeIf { it.isNotBlank() },
        m.createdAt?.let { DateFormat.date(it) },
    ).joinToString(" · ")
    LedgerRow(title = title, subtitle = detail, amount = money(m.amountCents), owed = m.amountCents > 0)
}

@Composable
private fun LedgerRow(title: String, subtitle: String, amount: String, owed: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
    ) {
        Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
            Text(
                amount,
                style = MaterialTheme.typography.titleSmall,
                color = if (owed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = Spacing.md),
            )
        }
    }
}
