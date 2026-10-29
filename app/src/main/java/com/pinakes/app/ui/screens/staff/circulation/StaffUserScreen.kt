package com.pinakes.app.ui.screens.staff.circulation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pinakes.app.R
import com.pinakes.app.data.model.StaffUserDetail
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.repository.StaffCirculationRepository
import com.pinakes.app.data.repository.isForbidden
import com.pinakes.app.ui.common.AppViewModel
import com.pinakes.app.ui.common.DateFormat
import com.pinakes.app.ui.components.ErrorState
import com.pinakes.app.ui.components.LoadingState
import com.pinakes.app.ui.components.PinakesTopBar
import com.pinakes.app.ui.components.PrimaryButton
import com.pinakes.app.ui.navigation.Routes
import com.pinakes.app.ui.screens.staff.ForbiddenExit
import com.pinakes.app.ui.screens.staff.FormSectionTitle
import com.pinakes.app.ui.screens.staff.InfoCard
import com.pinakes.app.ui.screens.staff.InfoLine
import com.pinakes.app.ui.screens.staff.LeaveWhen
import com.pinakes.app.ui.screens.staff.StaffMessage
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

data class StaffUserUiState(
    val user: StaffUserDetail? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: StaffMessage? = null,
    val forbidden: Boolean = false,
)

@HiltViewModel
class StaffUserViewModel @Inject constructor(
    private val circulation: StaffCirculationRepository,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val userId: Int = savedState.get<Int>(Routes.ARG_USER_ID) ?: 0
    private val _state = MutableStateFlow(StaffUserUiState())
    val state: StateFlow<StaffUserUiState> = _state.asStateFlow()

    fun load() {
        _state.update { it.copy(loading = it.user == null && !it.refreshing, error = null) }
        viewModelScope.launch {
            val res = circulation.user(userId)
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(user = res.data, loading = false, refreshing = false)
                    is ApiResult.Failure -> it.copy(loading = false, refreshing = false, forbidden = res.isForbidden(), error = res.toStaffMessage(R.string.staff_error_load))
                }
            }
        }
    }

    fun refresh() {
        _state.update { it.copy(refreshing = true) }
        load()
    }
}

internal fun userStatusLabel(status: String?): Int? = when (status) {
    "attivo" -> R.string.staff_user_status_active
    "sospeso" -> R.string.staff_user_status_suspended
    "scaduto" -> R.string.staff_user_status_expired
    else -> null
}

/** A borrower at the desk: card, status, open loans and active reservations. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffUserScreen(onNavigateUp: () -> Unit, onOpenLoan: (Int) -> Unit, onNewLoan: (Int) -> Unit) {
    val vm: StaffUserViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val app: AppViewModel = hiltViewModel()
    val areas by app.staffAreas.collectAsStateWithLifecycle()
    ForbiddenExit(state.forbidden, onNavigateUp)
    LeaveWhen(!areas.circulation, onNavigateUp)
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) vm.load() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    Scaffold(topBar = { PinakesTopBar(title = state.user?.name ?: stringResource(R.string.staff_pick_user), onNavigateUp = onNavigateUp) }) { padding ->
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize().padding(padding)) {
            val user = state.user
            when {
                state.loading -> LoadingState()
                user == null -> ErrorState(message = state.error?.resolve() ?: stringResource(R.string.staff_error_load), onRetry = vm::refresh)
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = 48.dp)) {
                    item(key = "card") {
                        InfoCard(Modifier.padding(top = Spacing.sm).testTag("user-card")) {
                            Text(user.name, style = MaterialTheme.typography.titleLarge)
                            InfoLine(stringResource(R.string.staff_user_card), user.cardNumber)
                            InfoLine(stringResource(R.string.staff_field_email), user.email)
                            InfoLine(stringResource(R.string.staff_user_status), userStatusLabel(user.status)?.let { stringResource(it) } ?: user.status)
                            InfoLine(stringResource(R.string.staff_user_card_expires), user.cardExpiresAt?.let(DateFormat::date))
                            InfoLine(stringResource(R.string.staff_loans_active), user.activeLoans.toString())
                            InfoLine(stringResource(R.string.staff_reservations), user.activeReservations.toString())
                            if (!user.eligible) {
                                Text(
                                    // A known reason (debt block, expired card, suspension) in our words.
                                    com.pinakes.app.data.staff.StaffErrors.eligibilityRes(user.eligibilityReason)?.let { stringResource(it) }
                                        ?: stringResource(R.string.staff_user_not_eligible, user.eligibilityReason ?: user.status.orEmpty()),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                    item(key = "new") {
                        PrimaryButton(stringResource(R.string.staff_loan_new), onClick = { onNewLoan(user.id) }, enabled = user.eligible,
                            modifier = Modifier.fillMaxWidth().padding(top = Spacing.md))
                    }
                    item(key = "loans-title") { FormSectionTitle(stringResource(R.string.staff_user_open_loans)) }
                    if (user.loans.isEmpty()) item(key = "loans-empty") {
                        Text(stringResource(R.string.staff_loans_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(user.loans, key = { "l${it.id}" }) { loan -> StaffLoanRow(loan, onClick = { onOpenLoan(loan.id) }, showUser = false) }
                    item(key = "res-title") { FormSectionTitle(stringResource(R.string.staff_reservations)) }
                    if (user.reservations.isEmpty()) item(key = "res-empty") {
                        Text(stringResource(R.string.staff_reservations_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(user.reservations, key = { "r${it.id}" }) { r -> StaffReservationRow(r, onClick = {}) }
                }
            }
        }
    }
}
