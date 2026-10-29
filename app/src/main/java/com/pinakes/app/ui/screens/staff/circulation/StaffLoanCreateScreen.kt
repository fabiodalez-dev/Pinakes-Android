package com.pinakes.app.ui.screens.staff.circulation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pinakes.app.R
import com.pinakes.app.data.model.AvailabilityCalendar
import com.pinakes.app.data.model.LoanCreateRequest
import com.pinakes.app.data.model.ReservationWrite
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.repository.StaffCirculationRepository
import com.pinakes.app.data.repository.StaffRepository
import com.pinakes.app.data.repository.isForbidden
import com.pinakes.app.data.network.ErrorCodes
import com.pinakes.app.data.staff.CopyCodeCodec
import com.pinakes.app.data.staff.SubmissionKeys
import com.pinakes.app.ui.common.AppViewModel
import com.pinakes.app.ui.common.DateFormat
import com.pinakes.app.ui.components.InlineErrorBanner
import com.pinakes.app.ui.components.PinakesTextField
import com.pinakes.app.ui.components.PinakesTopBar
import com.pinakes.app.ui.components.PrimaryButton
import com.pinakes.app.ui.navigation.Routes
import com.pinakes.app.ui.screens.detail.LoanCalendar
import com.pinakes.app.ui.screens.staff.DatePickerField
import com.pinakes.app.ui.screens.staff.ForbiddenExit
import com.pinakes.app.ui.screens.staff.FormSectionTitle
import com.pinakes.app.ui.screens.staff.LeaveWhen
import com.pinakes.app.ui.screens.staff.PickItem
import com.pinakes.app.ui.screens.staff.PickedField
import com.pinakes.app.ui.screens.staff.SearchController
import com.pinakes.app.ui.screens.staff.SearchPickerSheet
import com.pinakes.app.ui.screens.staff.StaffMessage
import com.pinakes.app.ui.screens.staff.SwitchRow
import com.pinakes.app.ui.screens.staff.resolve
import com.pinakes.app.ui.screens.staff.toStaffMessage
import com.pinakes.app.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DeskPicker { USER, BOOK }

data class LoanCreateUiState(
    val reservation: Boolean = false,
    val user: PickItem? = null,
    val book: PickItem? = null,
    val copyCode: String = "",
    val start: String? = null,
    val end: String? = null,
    val handOverNow: Boolean = true,
    val notes: String = "",
    val calendar: AvailabilityCalendar? = null,
    val calendarFailed: Boolean = false,
    val picker: DeskPicker? = null,
    val saving: Boolean = false,
    val error: StaffMessage? = null,
    val forbidden: Boolean = false,
    /** Loan id after a successful create (the screen opens it); -1 for a reservation. */
    val createdLoanId: Int? = null,
) {
    val today: String get() = LocalDate.now().toString()

    /** "Hand over now" only makes sense for a loan starting today. */
    val canHandOver: Boolean get() = !reservation && (start == null || start == today)

    val canSubmit: Boolean get() = user != null && book != null && !saving

    /** Closed days of the library (closures calendar) from the availability payload. */
    val closedDates: Set<String> get() = calendar?.closedDates?.toSet().orEmpty()

    /** The chosen start or end when it falls on a day the library is closed, else null. */
    val closedDateChosen: String? get() = listOfNotNull(start, end).firstOrNull { it in closedDates }
}

/**
 * New loan (or, with `reservation=true`, a new reservation) for a borrower: pick the user
 * (eligibility shown), the book (or arrive with a scanned copy code), the dates on the
 * per-user availability calendar, and whether the copy is handed over now.
 */
@HiltViewModel
class StaffLoanCreateViewModel @Inject constructor(
    private val circulation: StaffCirculationRepository,
    private val staff: StaffRepository,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(
        LoanCreateUiState(
            reservation = savedState.get<Boolean>(Routes.ARG_RESERVATION_MODE) ?: false,
            copyCode = savedState.get<String>(Routes.ARG_COPY_CODE).orEmpty(),
        ),
    )
    val state: StateFlow<LoanCreateUiState> = _state.asStateFlow()
    private val keys = SubmissionKeys()

    val users = SearchController(viewModelScope, minLength = 2) { q ->
        when (val res = circulation.users(q)) {
            is ApiResult.Success -> ApiResult.Success(res.data.map { it.toPickItem() })
            is ApiResult.Failure -> res
        }
    }.also { it.onFailure = ::onSearchFailure }

    val books = SearchController(viewModelScope, minLength = 2) { q ->
        when (val res = staff.books(q, cursor = null, limit = 20)) {
            is ApiResult.Success -> ApiResult.Success(res.data.items.map { b ->
                PickItem(b.id, b.title, listOfNotNull(b.author?.takeIf { it.isNotBlank() }, b.isbn13).joinToString(" · "),
                    note = null)
            })
            is ApiResult.Failure -> res
        }
    }.also { it.onFailure = ::onSearchFailure }

    init {
        val userId = savedState.get<Int>(Routes.ARG_USER_ID) ?: 0
        val bookId = savedState.get<Int>(Routes.ARG_BOOK_ID) ?: 0
        viewModelScope.launch {
            if (userId > 0) (circulation.user(userId) as? ApiResult.Success)?.data?.let { u ->
                _state.update { it.copy(user = PickItem(u.id, u.name, u.cardNumber, note = if (!u.eligible) u.eligibilityReason ?: u.status else null)) }
            }
            if (bookId > 0) (staff.book(bookId) as? ApiResult.Success)?.data?.let { b ->
                _state.update { it.copy(book = PickItem(b.id, b.title)) }
            }
            loadCalendar()
        }
    }

    private fun onSearchFailure(failure: ApiResult.Failure) {
        if (failure.isForbidden()) _state.update { it.copy(forbidden = true) }
    }

    fun openPicker(kind: DeskPicker) = _state.update { it.copy(picker = kind) }

    fun closePicker() {
        _state.update { it.copy(picker = null) }
        users.clear()
        books.clear()
    }

    fun pick(item: PickItem) {
        when (_state.value.picker) {
            DeskPicker.USER -> _state.update { it.copy(user = item, error = null) }
            DeskPicker.BOOK -> _state.update { it.copy(book = item, error = null, copyCode = "") }
            null -> Unit
        }
        closePicker()
        viewModelScope.launch { loadCalendar() }
    }

    /** Availability for the chosen book, from this user's point of view (their own holds count as free). */
    private suspend fun loadCalendar() {
        val book = _state.value.book ?: return
        when (val res = circulation.availability(book.id, _state.value.user?.id)) {
            is ApiResult.Success -> _state.update { it.copy(calendar = res.data, calendarFailed = false) }
            is ApiResult.Failure -> _state.update { it.copy(calendar = null, calendarFailed = true, forbidden = res.isForbidden()) }
        }
    }

    fun setCopyCode(value: String) = _state.update { it.copy(copyCode = value.take(CopyCodeCodec.MAX_LENGTH), error = null) }
    fun setStart(value: String?) = _state.update { it.copy(start = value, handOverNow = it.handOverNow && (value == null || value == it.today)) }
    fun setEnd(value: String?) = _state.update { it.copy(end = value) }
    fun setHandOver(value: Boolean) = _state.update { it.copy(handOverNow = value && it.canHandOver) }
    fun setNotes(value: String) = _state.update { it.copy(notes = value) }

    fun submit() {
        val s = _state.value
        val user = s.user ?: return
        val book = s.book ?: return
        if (s.saving) return
        if (s.start != null && s.end != null && s.end < s.start) {
            _state.update { it.copy(error = StaffMessage(R.string.staff_loan_dates_order)) }
            return
        }
        // Neither a start nor a return on a day the library is closed (closures calendar).
        s.closedDateChosen?.let { day ->
            _state.update { it.copy(error = StaffMessage(R.string.staff_loan_closed_day, args = listOf(DateFormat.date(day)))) }
            return
        }
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            if (s.reservation) {
                val body = ReservationWrite(userId = user.id, bookId = book.id, requestedFrom = s.start, requestedTo = s.end)
                val res = withIdempotency(body) { key -> circulation.createReservation(body, key) }
                _state.update {
                    when (res) {
                        is ApiResult.Success -> it.copy(saving = false, createdLoanId = -1)
                        is ApiResult.Failure -> it.copy(saving = false, forbidden = res.isForbidden(), error = res.toStaffMessage(R.string.staff_error_save))
                    }
                }
                return@launch
            }
            val request = LoanCreateRequest(
                userId = user.id, bookId = book.id,
                copyCode = s.copyCode.takeIf { CopyCodeCodec.isValid(it) },
                loanedAt = s.start, dueAt = s.end, notes = s.notes,
                immediate = s.handOverNow && s.canHandOver,
            )
            val res = withIdempotency(request) { key -> circulation.createLoan(request, key) }
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(saving = false, createdLoanId = res.data.id)
                    is ApiResult.Failure -> it.copy(saving = false, forbidden = res.isForbidden(), error = res.toStaffMessage(R.string.staff_error_save))
                }
            }
        }
    }

    /**
     * Send [payload] under its submission key: one automatic retry with the same key when the
     * connection dropped or the server asked for a retry (it may already have created it: the
     * replay returns it, 200 with `meta.replayed`, handled like a 201), a
     * fresh key after a success or a key conflict.
     */
    private suspend fun <T> withIdempotency(payload: Any, call: suspend (String) -> ApiResult<T>): ApiResult<T> {
        val key = keys.keyFor(payload)
        var res = call(key)
        // A dropped connection, or the server's 409 concurrent_retry (its lock was busy): one retry, same key.
        if (res is ApiResult.Failure && (res.code == ErrorCodes.NETWORK || res.code == "concurrent_retry")) res = call(key)
        if (res is ApiResult.Success || (res as? ApiResult.Failure)?.code == SubmissionKeys.CONFLICT) keys.reset()
        return res
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffLoanCreateScreen(onNavigateUp: () -> Unit, onCreated: (loanId: Int) -> Unit, onReservationCreated: () -> Unit) {
    val vm: StaffLoanCreateViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val app: AppViewModel = hiltViewModel()
    val areas by app.staffAreas.collectAsStateWithLifecycle()
    ForbiddenExit(state.forbidden, onNavigateUp)
    LeaveWhen(!areas.circulation, onNavigateUp)
    LaunchedEffect(state.createdLoanId) {
        when (val id = state.createdLoanId) {
            null -> Unit
            -1 -> onReservationCreated()
            else -> onCreated(id)
        }
    }

    Scaffold(
        topBar = {
            PinakesTopBar(
                title = stringResource(if (state.reservation) R.string.staff_reservation_new else R.string.staff_loan_new),
                onNavigateUp = onNavigateUp,
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            state.error?.let { InlineErrorBanner(message = it.resolve()) }
            FormSectionTitle(stringResource(R.string.staff_pick_user))
            PickedField(
                stringResource(R.string.staff_pick_user), state.user?.let { u -> listOfNotNull(u.title, u.subtitle).joinToString(" · ") },
                stringResource(R.string.staff_pick_user_placeholder), onClick = { vm.openPicker(DeskPicker.USER) },
            )
            state.user?.note?.let {
                // The note is the server's eligibility_reason: a known one in our words.
                val text = com.pinakes.app.data.staff.StaffErrors.eligibilityRes(it)?.let { res -> stringResource(res) }
                    ?: stringResource(R.string.staff_user_not_eligible, it)
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            FormSectionTitle(stringResource(R.string.staff_pick_book))
            PickedField(stringResource(R.string.staff_pick_book), state.book?.title, stringResource(R.string.staff_pick_book_placeholder),
                onClick = { vm.openPicker(DeskPicker.BOOK) })
            if (!state.reservation) {
                PinakesTextField(
                    value = state.copyCode, onValueChange = vm::setCopyCode, label = stringResource(R.string.staff_copy_code),
                    supportingText = stringResource(R.string.staff_copy_code_hint),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii), modifier = Modifier.fillMaxWidth().testTag("copy-code"),
                )
            }
            FormSectionTitle(stringResource(R.string.staff_loan_dates))
            DatePickerField(stringResource(if (state.reservation) R.string.staff_reservation_from else R.string.staff_loan_loaned), state.start, vm::setStart, allowClear = true, disabledDates = state.closedDates)
            DatePickerField(stringResource(if (state.reservation) R.string.staff_reservation_to else R.string.staff_loan_due), state.end, vm::setEnd, allowClear = true, disabledDates = state.closedDates)
            Text(
                stringResource(if (state.reservation) R.string.staff_reservation_dates_hint else R.string.staff_loan_dates_hint),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val calendar = state.calendar
            if (state.book != null && (calendar != null || state.calendarFailed)) {
                LoanCalendar(
                    calendar = calendar ?: AvailabilityCalendar(),
                    selected = state.start,
                    onSelect = { vm.setStart(it) },
                    anyFutureDay = state.calendarFailed,
                    modifier = Modifier.testTag("loan-calendar"),
                )
            }
            if (!state.reservation) {
                SwitchRow(
                    stringResource(R.string.staff_hand_over_now), state.handOverNow && state.canHandOver, vm::setHandOver,
                    subtitle = stringResource(if (state.canHandOver) R.string.staff_hand_over_now_hint else R.string.staff_hand_over_future),
                    enabled = state.canHandOver,
                )
                PinakesTextField(
                    value = state.notes, onValueChange = vm::setNotes, label = stringResource(R.string.staff_field_notes), singleLine = false,
                    maxLength = 1000, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                )
            }
            state.start?.let {
                Text(stringResource(R.string.staff_loan_summary_start, DateFormat.date(it)), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PrimaryButton(
                stringResource(if (state.reservation) R.string.staff_reservation_create else R.string.staff_loan_create),
                onClick = vm::submit, loading = state.saving, enabled = state.canSubmit, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.xxl))
        }
    }

    when (state.picker) {
        DeskPicker.USER -> {
            val s by vm.users.state.collectAsStateWithLifecycle()
            SearchPickerSheet(
                title = stringResource(R.string.staff_pick_user), placeholder = stringResource(R.string.staff_search_users), state = s,
                onQuery = vm.users::onQuery, onPick = vm::pick, onDismiss = vm::closePicker, hint = stringResource(R.string.staff_search_min_chars),
            )
        }
        DeskPicker.BOOK -> {
            val s by vm.books.state.collectAsStateWithLifecycle()
            SearchPickerSheet(
                title = stringResource(R.string.staff_pick_book), placeholder = stringResource(R.string.staff_books_search), state = s,
                onQuery = vm.books::onQuery, onPick = vm::pick, onDismiss = vm::closePicker, hint = stringResource(R.string.staff_search_min_chars),
            )
        }
        null -> Unit
    }
}
