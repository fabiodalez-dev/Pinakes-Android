package com.pinakes.app.ui.screens.staff.s090.patrons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pinakes.app.R
import com.pinakes.app.data.model.StaffLoanLateFine
import com.pinakes.app.data.staff.LateFineRules
import com.pinakes.app.ui.components.PinakesTextField
import com.pinakes.app.ui.screens.staff.SwitchRow
import com.pinakes.app.ui.theme.Spacing

/**
 * The late fine block of the return form (Mobile API 1.10.0): the amount a return today
 * charges and the waiver, whose reason is mandatory. Inert (nothing drawn, nothing sent) when
 * the loan accrues no fine or the server predates the field.
 */
class ReturnFineState internal constructor(
    val fine: StaffLoanLateFine?,
    private val waiveState: MutableState<Boolean>,
    private val reasonState: MutableState<String>,
) {
    var waive by waiveState
    var reason by reasonState
    val shows: Boolean get() = LateFineRules.shows(fine)
    val canConfirm: Boolean get() = !shows || LateFineRules.canConfirm(waive, reason)

    /** The reason to send with the return, or null when the fine is charged. */
    fun waiveReason(): String? = LateFineRules.waiveReason(fine, waive, reason)

    @Composable
    fun Content() {
        val fine = fine ?: return
        if (!shows) return
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth().testTag("return-fine")) {
            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    stringResource(R.string.staff090_patrons_return_fine, money(fine.accruingCents, fine.currency), fine.accruingDays),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (waive) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
                SwitchRow(stringResource(R.string.staff090_patrons_return_waive), waive, { waive = it })
                if (waive) {
                    PinakesTextField(
                        value = reason, onValueChange = { reason = it }, label = stringResource(R.string.staff090_patrons_waive_reason),
                        singleLine = false, maxLength = 255, isError = reason.isBlank(), errorText = stringResource(R.string.staff090_patrons_reason_required),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).testTag("return-waive-reason"),
                    )
                }
            }
        }
    }
}

@Composable
fun rememberReturnFineState(fine: StaffLoanLateFine?): ReturnFineState {
    val waive = rememberSaveable { mutableStateOf(false) }
    val reason = rememberSaveable { mutableStateOf("") }
    return ReturnFineState(fine, waive, reason)
}
