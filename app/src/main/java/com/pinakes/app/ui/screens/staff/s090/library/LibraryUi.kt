package com.pinakes.app.ui.screens.staff.s090.library

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import com.pinakes.app.R
import com.pinakes.app.data.model.LookupOption
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.staff.Staff090LibraryErrors
import com.pinakes.app.ui.components.PinakesTextField
import com.pinakes.app.ui.screens.movements.MoneyFormat
import com.pinakes.app.ui.screens.staff.LookupField
import com.pinakes.app.ui.screens.staff.StaffMessage
import com.pinakes.app.ui.screens.staff.toStaffMessage
import com.pinakes.app.ui.common.AppLocale
import com.pinakes.app.ui.common.serverMessage
import com.pinakes.app.ui.theme.Spacing

// Small pieces shared by the library settings screens; everything else is the staff kit.

/** Our wording for the codes of these areas, else the usual staff mapping (server text included). */
internal fun ApiResult.Failure.toLibraryMessage(fallback: Int): StaffMessage = when {
    Staff090LibraryErrors.isNotPermitted(this) -> serverMessage()?.let { StaffMessage(text = it) } ?: StaffMessage(R.string.staff090_library_admin_only)
    else -> Staff090LibraryErrors.messageRes(code)?.let { StaffMessage(it) } ?: toStaffMessage(fallback)
}

/**
 * Why the screen closes after its first load failed (area refused to this account, module off
 * for Staff): the server's own message, else ours. Null while the screen may stay.
 */
internal fun ApiResult.Failure.exitMessage(): StaffMessage? =
    if (!Staff090LibraryErrors.closesScreen(this)) null
    else serverMessage()?.let { StaffMessage(text = it) } ?: StaffMessage(R.string.staff090_library_admin_only)

/** Leaves the screen with [message] in a toast (the screen that would host a snackbar is going away). */
@Composable
internal fun LibraryExit(message: StaffMessage?, onNavigateUp: () -> Unit) {
    val context = LocalContext.current
    val text = message?.let {
        when {
            !it.text.isNullOrBlank() -> it.text
            it.res != null -> context.getString(it.res, *it.args.toTypedArray())
            else -> null
        }
    }
    LaunchedEffect(message) {
        if (message != null) {
            if (!text.isNullOrBlank()) Toast.makeText(context, text, Toast.LENGTH_LONG).show()
            onNavigateUp()
        }
    }
}

/** A setting shown without a control (no permission to change it): title, help, value. */
@Composable
internal fun ReadOnlySetting(title: String, value: String, subtitle: String? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = Spacing.md))
    }
}

/** A whole-number field; [hint] says what an empty value means or the allowed range. */
@Composable
internal fun NumberField(label: String, value: String, onChange: (String) -> Unit, invalid: Boolean, modifier: Modifier = Modifier, hint: String = "", signed: Boolean = false) {
    PinakesTextField(
        value = value,
        onValueChange = { v -> onChange(v.filterIndexed { i, c -> c.isDigit() || (signed && c == '-' && i == 0) }) },
        label = label, isError = invalid, errorText = stringResource(R.string.staff_field_invalid), supportingText = hint,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), maxLength = 9, modifier = Modifier.fillMaxWidth().then(modifier),
    )
}

/** An amount field in [currency]; the text is parsed to cents exactly ([LibraryMoney]). */
@Composable
internal fun MoneyField(label: String, value: String, currency: String?, onChange: (String) -> Unit, invalid: Boolean, modifier: Modifier = Modifier, hint: String = "") {
    PinakesTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || it == ',' || it == '.' }) },
        label = if (currency.isNullOrBlank()) label else stringResource(R.string.staff090_library_money_label, label, currency),
        isError = invalid, errorText = stringResource(R.string.staff090_library_money_invalid), supportingText = hint,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), maxLength = 12, modifier = Modifier.fillMaxWidth().then(modifier),
    )
}

/** A pick-one field over fixed options (value → label). */
@Composable
internal fun ChoiceField(label: String, value: String, options: List<LookupOption>, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    LookupField(
        label = label, value = value, displayValue = options.firstOrNull { it.value == value }?.label ?: value,
        options = options, editable = false, allowClear = false, onValueChange = onChange, modifier = Modifier.fillMaxWidth().then(modifier),
    )
}

/** [cents] in the server currency, in the app language; "—" for no amount. */
internal fun money(cents: Int?, currency: String?): String =
    cents?.let { MoneyFormat.format(it, currency, AppLocale.current()) } ?: "—"
