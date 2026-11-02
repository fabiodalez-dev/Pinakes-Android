package com.pinakes.app.ui.screens.staff

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pinakes.app.R
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.staff.StaffErrors
import com.pinakes.app.ui.common.DateFormat
import com.pinakes.app.ui.common.isOffline
import com.pinakes.app.ui.common.serverMessage
import com.pinakes.app.ui.components.EmptyState
import com.pinakes.app.ui.components.ErrorState
import com.pinakes.app.ui.components.LoadingState
import com.pinakes.app.ui.components.PinakesTextButton
import com.pinakes.app.ui.components.PinakesTextField
import com.pinakes.app.ui.components.PrimaryButton
import com.pinakes.app.ui.components.SearchField
import com.pinakes.app.ui.theme.Spacing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// Shared building blocks of the Phase 2 staff screens. They follow the Phase 1 staff screens
// (StaffUi.kt): the same rows, sheets, fields and states, so a new screen reads like the old.

/** A user-facing message: a string resource (with arguments) or a server text. */
data class StaffMessage(@param:StringRes val res: Int? = null, val text: String? = null, val args: List<Any> = emptyList())

@Composable
fun StaffMessage.resolve(): String = when {
    !text.isNullOrBlank() -> text
    res != null -> stringResource(res, *args.toTypedArray())
    else -> ""
}

/**
 * The message for a failed staff call: our wording for a known code, else the server text
 * (409 `conflict` carries the only precise explanation), else offline wording or [fallback].
 */
fun ApiResult.Failure.toStaffMessage(@StringRes fallback: Int): StaffMessage {
    StaffErrors.messageRes(this)?.let { return StaffMessage(it) }
    if (isOffline) return StaffMessage(R.string.offline_generic)
    return serverMessage()?.let { StaffMessage(text = it) } ?: StaffMessage(fallback)
}

/** Show [message] once in [host], then report it consumed. */
@Composable
internal fun StaffSnackbarEffect(message: StaffMessage?, host: SnackbarHostState, onConsumed: () -> Unit) {
    val text = message?.resolve()
    LaunchedEffect(message) {
        if (message != null && !text.isNullOrBlank()) {
            host.showSnackbar(text)
            onConsumed()
        }
    }
}

/**
 * Leaves the screen when its area is no longer offered: the circulation desk while the device
 * goes offline (owner rule: hidden, never disabled), or a flag the server turned off.
 */
@Composable
internal fun LeaveWhen(condition: Boolean, onNavigateUp: () -> Unit) {
    LaunchedEffect(condition) { if (condition) onNavigateUp() }
}

/** Loading → error → empty → content, the order every staff list uses. */
@Composable
internal fun StaffLoadBody(
    loading: Boolean,
    error: StaffMessage?,
    isEmpty: Boolean,
    emptyTitle: String,
    emptyIcon: ImageVector,
    onRetry: () -> Unit,
    emptySubtitle: String = "",
    content: @Composable () -> Unit,
) {
    when {
        loading -> LoadingState()
        error != null -> ErrorState(message = error.resolve().ifBlank { stringResource(R.string.staff_error_load) }, onRetry = onRetry)
        isEmpty -> EmptyState(title = emptyTitle, subtitle = emptySubtitle, icon = emptyIcon)
        else -> content()
    }
}

/** A labelled switch row (forms and settings sheets). */
@Composable
internal fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, subtitle: String? = null, enabled: Boolean = true) {
    // The whole row toggles (one touch target, one accessibility node with the switch role).
    Row(
        Modifier.fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = onChange)
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** A key/value line inside a staff info card. */
@Composable
internal fun InfoLine(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xs), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.42f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(0.58f))
    }
}

/** The white rounded box grouping a record's facts (DESIGN.md "Other screens"). */
@Composable
internal fun InfoCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.lg)) { content() }
    }
}

/** A dialog asking for an optional free-text reason (reject, cancel pickup, cancel reservation). */
@Composable
internal fun ReasonDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    label: String = stringResource(R.string.staff_reason_optional),
    required: Boolean = false,
) {
    var value by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (body.isNotBlank()) Text(body, style = MaterialTheme.typography.bodyMedium)
                PinakesTextField(
                    value = value, onValueChange = { value = it }, label = label, singleLine = false, maxLength = 500,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).testTag("reason-field"),
                )
            }
        },
        confirmButton = { PrimaryButton(confirmLabel, onClick = { onConfirm(value) }, enabled = !required || value.isNotBlank()) },
        dismissButton = { PinakesTextButton(stringResource(R.string.action_cancel), onClick = onDismiss) },
    )
}

/**
 * A read-only field showing a date (`yyyy-MM-dd`, rendered in the app language) that opens the
 * Material date picker. [onChange] receives `yyyy-MM-dd`, or null when cleared.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DatePickerField(
    label: String,
    value: String?,
    onChange: (String?) -> Unit,
    allowClear: Boolean = false,
    enabled: Boolean = true,
    /** Days the dialog refuses (yyyy-MM-dd), e.g. the library's closures; empty = none. */
    disabledDates: Set<String> = emptySet(),
) {
    var open by remember { mutableStateOf(false) }
    PickerRow(
        label = label,
        value = value?.takeIf { it.isNotBlank() }?.let { DateFormat.date(it) },
        placeholder = stringResource(R.string.staff_date_pick),
        onClick = { if (enabled) open = true },
        onClear = if (allowClear) ({ onChange(null) }) else null,
    )
    if (open) {
        val initial = value?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val selectable = remember(disabledDates) {
            object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().toString() !in disabledDates
            }
        }
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = initial?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            selectableDates = selectable,
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                PinakesTextButton(stringResource(R.string.action_confirm), onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        onChange(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString())
                    }
                    open = false
                })
            },
            dismissButton = { PinakesTextButton(stringResource(R.string.action_cancel), onClick = { open = false }) },
        ) { DatePicker(state = pickerState) }
    }
}

/** A thin progress line with "n of m" under it (imports, cover loops). */
@Composable
internal fun ProgressLine(done: Int, total: Int, label: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        LinearProgressIndicator(
            progress = { if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().testTag("progress"),
        )
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------------------------------------
// Search pickers (users, books, series, authorities)
// ---------------------------------------------------------------------------------------------

/** One row a picker can show. */
data class PickItem(val id: Int, val title: String, val subtitle: String? = null, val enabled: Boolean = true, val note: String? = null)

data class SearchState(
    val query: String = "",
    val results: List<PickItem> = emptyList(),
    val loading: Boolean = false,
    val error: StaffMessage? = null,
    val searched: Boolean = false,
)

/**
 * Debounced search for a picker, owned by a ViewModel. Late answers for an older query are
 * dropped; a query shorter than [minLength] clears the results without calling the server.
 */
class SearchController(
    private val scope: CoroutineScope,
    private val minLength: Int = 1,
    private val search: suspend (String) -> ApiResult<List<PickItem>>,
) {
    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()
    private var job: Job? = null
    private var generation = 0
    /** Called on a refused (forbidden) search so the screen can leave. */
    var onFailure: (ApiResult.Failure) -> Unit = {}

    fun onQuery(value: String) {
        _state.update { it.copy(query = value) }
        job?.cancel()
        val gen = ++generation
        if (value.trim().length < minLength) {
            _state.update { it.copy(results = emptyList(), loading = false, error = null, searched = false) }
            return
        }
        job = scope.launch {
            delay(300)
            _state.update { it.copy(loading = true, error = null) }
            val res = search(value.trim())
            if (gen != generation) return@launch
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(results = res.data, loading = false, searched = true)
                    is ApiResult.Failure -> {
                        onFailure(res)
                        it.copy(loading = false, error = res.toStaffMessage(R.string.staff_error_load), searched = true)
                    }
                }
            }
        }
    }

    /** Run the current query again (or start with [initial]). */
    fun start(initial: String = _state.value.query) = onQuery(initial)

    fun clear() {
        job?.cancel()
        generation++
        _state.value = SearchState()
    }
}

/**
 * A bottom sheet with a search field and the matching rows. Single pick calls [onPick];
 * with [selected] non-null the rows toggle and [onConfirm] closes with the selection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchPickerSheet(
    title: String,
    placeholder: String,
    state: SearchState,
    onQuery: (String) -> Unit,
    onPick: (PickItem) -> Unit,
    onDismiss: () -> Unit,
    selected: Set<Int>? = null,
    onConfirm: (() -> Unit)? = null,
    hint: String? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            SearchField(query = state.query, onQueryChange = onQuery, placeholder = placeholder, modifier = Modifier.fillMaxWidth().testTag("picker-search"))
            if (!hint.isNullOrBlank() && state.results.isEmpty() && !state.loading) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when {
                state.loading -> LoadingState(modifier = Modifier.heightIn(max = 120.dp))
                state.error != null -> Text(state.error.resolve(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                state.searched && state.results.isEmpty() -> Text(
                    stringResource(R.string.staff_picker_empty), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp), contentPadding = PaddingValues(bottom = Spacing.sm)) {
                items(state.results, key = { it.id }) { item ->
                    val isSelected = selected?.contains(item.id) == true
                    Surface(
                        onClick = { if (item.enabled) onPick(item) },
                        enabled = item.enabled,
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs).testTag("pick-${item.id}"),
                    ) {
                        Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                            if (selected != null) {
                                Icon(
                                    if (isSelected) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                                    contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.padding(start = Spacing.sm))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(item.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    color = if (item.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                                item.subtitle?.takeIf { it.isNotBlank() }?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                item.note?.takeIf { it.isNotBlank() }?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
            if (selected != null && onConfirm != null) {
                PrimaryButton(
                    stringResource(R.string.staff_picker_confirm, selected.size), onClick = onConfirm,
                    enabled = selected.isNotEmpty(), modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

/** A tappable field that opens a picker and shows the pick (user, book). */
@Composable
internal fun PickedField(label: String, value: String?, placeholder: String, onClick: () -> Unit, onClear: (() -> Unit)? = null) =
    PickerRow(label = label, value = value, placeholder = placeholder, onClick = onClick, onClear = onClear)

/** A trailing icon button with a content description (row actions). */
@Composable
internal fun RowIconButton(icon: ImageVector, description: String, onClick: () -> Unit, enabled: Boolean = true) {
    IconButton(onClick = onClick, enabled = enabled) { Icon(icon, contentDescription = description) }
}

/**
 * A downloaded staff export (receipt, labels, error CSV): a PDF opens in the in-app reader,
 * anything else in a viewer app; both can be shared through the FileProvider (print, mail).
 */
@Composable
internal fun ExportFileDialog(file: java.io.File, mime: String, title: String, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var reading by rememberSaveable { mutableStateOf(false) }
    if (reading) {
        com.pinakes.app.ui.screens.detail.PdfReaderDialog(file = file, onDismiss = { reading = false })
        return
    }
    val shareTitle = stringResource(R.string.staff_share_file)
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = { Text(file.name, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            PrimaryButton(stringResource(R.string.staff_open_file), onClick = {
                if (mime == "application/pdf") reading = true
                else if (!com.pinakes.app.ui.common.ShareFile.open(context, file, mime)) com.pinakes.app.ui.common.ShareFile.share(context, file, mime, shareTitle)
            })
        },
        dismissButton = {
            com.pinakes.app.ui.components.SecondaryButton(shareTitle, leadingIcon = androidx.compose.material.icons.Icons.Outlined.Share, onClick = {
                com.pinakes.app.ui.common.ShareFile.share(context, file, mime, shareTitle)
                onDismiss()
            })
        },
    )
}
