package com.pinakes.app.ui.screens.staff.circulation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pinakes.app.R
import com.pinakes.app.data.model.StaffLoan
import com.pinakes.app.data.model.StaffReservation
import com.pinakes.app.data.staff.LoanActions
import com.pinakes.app.data.staff.PenaltyInput
import com.pinakes.app.data.staff.ReturnOutcome
import com.pinakes.app.ui.common.DateFormat
import com.pinakes.app.ui.common.StatusMapping
import com.pinakes.app.ui.components.AvailabilityStatus
import com.pinakes.app.ui.components.FilterChipRow
import com.pinakes.app.ui.components.FilterOption
import com.pinakes.app.ui.components.MediaRow
import com.pinakes.app.ui.components.PinakesTextField
import com.pinakes.app.ui.components.PrimaryButton
import com.pinakes.app.ui.theme.Spacing

/** Chip status + localized label for a loan state (`status_label` for states the app does not know). */
@Composable
internal fun loanChip(loan: StaffLoan): Pair<AvailabilityStatus, String?> {
    val (status, label) = StatusMapping.loan(loan.status, loan.statusLabel)
    return status to (label.resId?.let { stringResource(it) } ?: label.fallback)
}

@Composable
internal fun reservationChip(reservation: StaffReservation): Pair<AvailabilityStatus, String?> {
    val (status, label) = StatusMapping.reservation(reservation.status)
    return status to (label.resId?.let { stringResource(it) } ?: label.fallback ?: reservation.statusLabel)
}

/** The date that matters for a desk row: overdue days, due date, pickup deadline, request date. */
@Composable
internal fun loanDateLine(loan: StaffLoan): String? = when {
    loan.status == "in_ritardo" && loan.daysOverdue > 0 -> stringResource(R.string.staff_loan_days_overdue, loan.daysOverdue)
    loan.status == "da_ritirare" && loan.pickupDeadline != null -> stringResource(R.string.staff_loan_pickup_by, DateFormat.date(loan.pickupDeadline))
    loan.returnedAt != null -> stringResource(R.string.library_returned_on, DateFormat.date(loan.returnedAt))
    loan.status == "pendente" && loan.requestedAt != null -> stringResource(R.string.library_requested_on, DateFormat.date(loan.requestedAt))
    loan.status == "prenotato" && loan.loanedAt != null -> stringResource(R.string.staff_loan_starts_on, DateFormat.date(loan.loanedAt))
    loan.dueAt != null -> stringResource(R.string.library_due_label, DateFormat.date(loan.dueAt))
    else -> null
}

/** One loan at the desk: cover, title, borrower and the date that matters. */
@Composable
internal fun StaffLoanRow(
    loan: StaffLoan,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean? = null,
    showUser: Boolean = true,
) {
    val (status, label) = loanChip(loan)
    val user = listOfNotNull(loan.user.name.takeIf { it.isNotBlank() }, loan.user.cardNumber?.takeIf { it.isNotBlank() }).joinToString(" · ")
    MediaRow(
        modifier = modifier.padding(vertical = Spacing.xs).testTag("loan-${loan.id}"),
        title = loan.book.title,
        coverUrl = loan.book.coverUrl,
        line1 = if (showUser) user.ifBlank { null } else loan.copy?.inventoryNumber,
        line2 = loanDateLine(loan),
        status = status,
        statusLabel = label,
        onClick = onClick,
        trailing = selected?.let { isSelected ->
            {
                Icon(
                    if (isSelected) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                    contentDescription = stringResource(if (isSelected) R.string.staff_selected else R.string.staff_not_selected),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
    )
}

/** One reservation: position in the queue, borrower, requested window. */
@Composable
internal fun StaffReservationRow(
    reservation: StaffReservation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showBook: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
) {
    val (status, label) = reservationChip(reservation)
    val window = listOfNotNull(reservation.requestedFrom, reservation.requestedTo).joinToString(" → ") { DateFormat.date(it) }
    val position = reservation.queuePosition?.let { stringResource(R.string.staff_queue_position, it) }
    MediaRow(
        modifier = modifier.padding(vertical = Spacing.xs).testTag("reservation-${reservation.id}"),
        title = if (showBook) reservation.book.title else reservation.user.name,
        coverUrl = if (showBook) reservation.book.coverUrl else null,
        line1 = listOfNotNull(position, if (showBook) reservation.user.name.takeIf { it.isNotBlank() } else reservation.user.cardNumber).joinToString(" · ").ifBlank { null },
        line2 = window.ifBlank { reservation.expiresAt?.let { stringResource(R.string.staff_reservation_expires, DateFormat.date(it)) } },
        status = status,
        statusLabel = label,
        onClick = onClick,
        trailing = trailing,
    )
}

/**
 * The return form: the copy's outcome, notes, and a penalty for lost or damaged copies. The
 * penalty is validated before sending ([LoanActions.parsePenalty]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReturnSheet(
    title: String,
    saving: Boolean,
    onConfirm: (outcome: ReturnOutcome, notes: String, penalty: Double?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var outcome by rememberSaveable { mutableStateOf(ReturnOutcome.RETURNED) }
    var notes by rememberSaveable { mutableStateOf("") }
    var penalty by rememberSaveable { mutableStateOf("") }
    val parsed = LoanActions.parsePenalty(penalty, outcome)
    ModalBottomSheet(onDismissRequest = { if (!saving) onDismiss() }, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.staff_return_outcome), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilterChipRow(
                options = ReturnOutcome.entries.map { FilterOption(it, stringResource(returnOutcomeLabel(it))) },
                selectedIds = setOf(outcome),
                onToggle = { outcome = it; if (!it.allowsPenalty) penalty = "" },
            )
            PinakesTextField(
                value = notes, onValueChange = { notes = it }, label = stringResource(R.string.staff_field_notes), singleLine = false,
                maxLength = 1000, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
            )
            if (outcome.allowsPenalty) {
                PinakesTextField(
                    value = penalty, onValueChange = { penalty = it }, label = stringResource(R.string.staff_penalty),
                    isError = parsed is PenaltyInput.Invalid, errorText = stringResource(R.string.staff_penalty_invalid),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), maxLength = 12,
                    modifier = Modifier.fillMaxWidth().testTag("penalty-field"),
                )
            }
            PrimaryButton(
                stringResource(R.string.staff_loan_action_return), loading = saving, enabled = parsed !is PenaltyInput.Invalid,
                onClick = { onConfirm(outcome, notes, (parsed as? PenaltyInput.Amount)?.value) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

internal fun returnOutcomeLabel(outcome: ReturnOutcome): Int = when (outcome) {
    ReturnOutcome.RETURNED -> R.string.staff_return_outcome_returned
    ReturnOutcome.MAINTENANCE -> R.string.staff_return_outcome_maintenance
    ReturnOutcome.RESTORATION -> R.string.staff_return_outcome_restoration
    ReturnOutcome.LOST -> R.string.staff_return_outcome_lost
    ReturnOutcome.DAMAGED -> R.string.staff_return_outcome_damaged
}
