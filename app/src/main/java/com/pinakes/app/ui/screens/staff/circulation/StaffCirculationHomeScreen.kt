package com.pinakes.app.ui.screens.staff.circulation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.AssignmentReturn
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.PostAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pinakes.app.R
import com.pinakes.app.data.model.CirculationSummary
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.repository.LoanBucket
import com.pinakes.app.data.repository.StaffCirculationRepository
import com.pinakes.app.data.repository.isForbidden
import com.pinakes.app.ui.common.AppViewModel
import com.pinakes.app.ui.common.DateFormat
import com.pinakes.app.ui.components.ErrorState
import com.pinakes.app.ui.components.LoadingState
import com.pinakes.app.ui.components.PinakesTopBar
import com.pinakes.app.ui.screens.staff.ForbiddenExit
import com.pinakes.app.ui.screens.staff.FormSectionTitle
import com.pinakes.app.ui.screens.staff.LeaveWhen
import com.pinakes.app.ui.screens.staff.StaffActionRow
import com.pinakes.app.ui.screens.staff.StaffMessage
import com.pinakes.app.ui.screens.staff.resolve
import com.pinakes.app.ui.screens.staff.toStaffMessage
import com.pinakes.app.ui.theme.Fraunces
import com.pinakes.app.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CirculationHomeUiState(
    val summary: CirculationSummary? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: StaffMessage? = null,
    val forbidden: Boolean = false,
)

@HiltViewModel
class StaffCirculationHomeViewModel @Inject constructor(
    private val circulation: StaffCirculationRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(CirculationHomeUiState())
    val state: StateFlow<CirculationHomeUiState> = _state.asStateFlow()

    /** The counters change with every desk action: reload them each time the screen shows. */
    fun reload() {
        _state.update { it.copy(loading = it.summary == null && !it.refreshing, error = null) }
        viewModelScope.launch {
            val res = circulation.summary()
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(summary = res.data, loading = false, refreshing = false)
                    is ApiResult.Failure -> it.copy(
                        loading = false, refreshing = false, forbidden = res.isForbidden(),
                        error = if (it.summary == null) res.toStaffMessage(R.string.staff_error_load) else null,
                    )
                }
            }
        }
    }

    fun refresh() {
        _state.update { it.copy(refreshing = true) }
        reload()
    }
}

/** The desk: today's counters (each opens its list) and the quick actions. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StaffCirculationHomeScreen(
    onNavigateUp: () -> Unit,
    onOpenLoans: (String) -> Unit,
    onScanCopy: () -> Unit,
    onNewLoan: () -> Unit,
    onReservations: () -> Unit,
) {
    val vm: StaffCirculationHomeViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val app: AppViewModel = hiltViewModel()
    val areas by app.staffAreas.collectAsStateWithLifecycle()
    ForbiddenExit(state.forbidden, onNavigateUp)
    LeaveWhen(!areas.circulation, onNavigateUp)
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) vm.reload() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    Scaffold(topBar = { PinakesTopBar(title = stringResource(R.string.staff_circulation_desk), onNavigateUp = onNavigateUp) }) { padding ->
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize().padding(padding)) {
            val summary = state.summary
            when {
                state.loading -> LoadingState()
                summary == null -> ErrorState(message = state.error?.resolve() ?: stringResource(R.string.staff_error_load), onRetry = vm::refresh)
                else -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                ) {
                    summary.today?.let {
                        Text(DateFormat.date(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FlowRow(
                        Modifier.fillMaxWidth().padding(top = Spacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        maxItemsInEachRow = 2,
                    ) {
                        val cell = Modifier.weight(1f)
                        CounterCard(stringResource(R.string.staff_loans_pending), summary.pending, cell, "counter-pending") { onOpenLoans(LoanBucket.PENDING.wire) }
                        CounterCard(stringResource(R.string.staff_loans_pickup), summary.pickupReady, cell, "counter-pickup") { onOpenLoans(LoanBucket.PICKUP.wire) }
                        CounterCard(stringResource(R.string.staff_loans_active), summary.active, cell, "counter-active") { onOpenLoans(LoanBucket.ACTIVE.wire) }
                        CounterCard(stringResource(R.string.staff_loans_overdue), summary.overdue, cell, "counter-overdue", alert = summary.overdue > 0) {
                            onOpenLoans(LoanBucket.OVERDUE.wire)
                        }
                        CounterCard(stringResource(R.string.staff_loans_scheduled), summary.scheduled, cell, "counter-scheduled") { onOpenLoans(LoanBucket.SCHEDULED.wire) }
                        CounterCard(stringResource(R.string.staff_reservations), summary.reservations, cell, "counter-reservations", onClick = onReservations)
                    }
                    Text(
                        stringResource(R.string.staff_circulation_today, summary.dueToday, summary.returnedToday),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.md),
                    )
                    FormSectionTitle(stringResource(R.string.staff_circulation_actions))
                    StaffActionRow(Icons.AutoMirrored.Outlined.AssignmentReturn, stringResource(R.string.staff_scan_copy), stringResource(R.string.staff_scan_copy_subtitle), onScanCopy)
                    StaffActionRow(Icons.Outlined.PostAdd, stringResource(R.string.staff_loan_new), onClick = onNewLoan)
                    StaffActionRow(Icons.Outlined.Event, stringResource(R.string.staff_reservations), onClick = onReservations)
                    StaffActionRow(Icons.AutoMirrored.Outlined.List, stringResource(R.string.staff_loans_all), onClick = { onOpenLoans(LoanBucket.ALL.wire) })
                }
            }
        }
    }
}

/** A counter tile: big Fraunces number over a muted label. */
@Composable
private fun CounterCard(label: String, value: Int, modifier: Modifier, tag: String, alert: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.testTag(tag),
    ) {
        Column(Modifier.padding(Spacing.lg)) {
            Text(
                value.toString(),
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Fraunces),
                color = if (alert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
