package com.pinakes.app.ui.screens.staff.scan

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pinakes.app.R
import com.pinakes.app.data.model.CopyLookup
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.repository.StaffCirculationRepository
import com.pinakes.app.data.repository.isForbidden
import com.pinakes.app.data.staff.CopyCodeCodec
import com.pinakes.app.data.staff.ReturnOutcome
import com.pinakes.app.data.staff.ScanDebouncer
import com.pinakes.app.ui.common.AppViewModel
import com.pinakes.app.ui.common.DateFormat
import com.pinakes.app.ui.components.AvailabilityChip
import com.pinakes.app.ui.components.AvailabilityStatus
import com.pinakes.app.ui.components.BookCover
import com.pinakes.app.ui.components.PinakesTextButton
import com.pinakes.app.ui.components.PinakesTextField
import com.pinakes.app.ui.components.PinakesTopBar
import com.pinakes.app.ui.components.PrimaryButton
import com.pinakes.app.ui.components.SecondaryButton
import com.pinakes.app.ui.screens.staff.ForbiddenExit
import com.pinakes.app.ui.screens.staff.InfoLine
import com.pinakes.app.ui.screens.staff.LeaveWhen
import com.pinakes.app.ui.screens.staff.StaffMessage
import com.pinakes.app.ui.screens.staff.StaffSnackbarEffect
import com.pinakes.app.ui.screens.staff.circulation.ReturnSheet
import com.pinakes.app.ui.screens.staff.circulation.loanChip
import com.pinakes.app.ui.screens.staff.resolve
import com.pinakes.app.ui.screens.staff.toStaffMessage
import com.pinakes.app.ui.theme.LocalPinakesColors
import com.pinakes.app.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface CopyScanPhase {
    data object Scanning : CopyScanPhase
    data class Looking(val code: String) : CopyScanPhase
    data class Found(val code: String, val lookup: CopyLookup) : CopyScanPhase
    data class NotFound(val code: String) : CopyScanPhase
}

data class CopyScanUiState(
    val phase: CopyScanPhase = CopyScanPhase.Scanning,
    val manualInput: String = "",
    val manualError: Boolean = false,
    val returning: Boolean = false,
    val saving: Boolean = false,
    val returnedCount: Int = 0,
    val acceptedTick: Int = 0,
    val message: StaffMessage? = null,
    val forbidden: Boolean = false,
) {
    val sheetOpen: Boolean get() = phase is CopyScanPhase.Found || phase is CopyScanPhase.NotFound
}

/**
 * The desk scanner: read a copy's inventory label (CODE 128 / CODE 39, or EAN-13), see the copy
 * and its open loan, then return it with an outcome, lend it, or open the book.
 */
@HiltViewModel
class StaffCopyScanViewModel @Inject constructor(private val circulation: StaffCirculationRepository) : ViewModel() {
    private val _state = MutableStateFlow(CopyScanUiState())
    val state: StateFlow<CopyScanUiState> = _state.asStateFlow()
    private val debouncer = ScanDebouncer()

    fun onDecoded(raw: String) {
        if (_state.value.phase != CopyScanPhase.Scanning) return
        val code = CopyCodeCodec.normalize(raw).takeIf { it.isNotEmpty() } ?: return
        val accepted = debouncer.offer(code, SystemClock.elapsedRealtime()) ?: return
        _state.update { it.copy(acceptedTick = it.acceptedTick + 1) }
        lookup(accepted)
    }

    fun onManualInput(value: String) = _state.update { it.copy(manualInput = value.take(CopyCodeCodec.MAX_LENGTH), manualError = false) }

    fun submitManual() {
        val code = CopyCodeCodec.normalize(_state.value.manualInput)
        if (code.isEmpty()) {
            _state.update { it.copy(manualError = true) }
            return
        }
        if (_state.value.phase != CopyScanPhase.Scanning) return
        _state.update { it.copy(manualInput = "") }
        lookup(code)
    }

    private fun lookup(code: String) {
        debouncer.pause()
        _state.update { it.copy(phase = CopyScanPhase.Looking(code)) }
        viewModelScope.launch {
            val res = circulation.copyByCode(code)
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(
                        phase = if (res.data.found && res.data.copy != null) CopyScanPhase.Found(code, res.data) else CopyScanPhase.NotFound(code),
                    )
                    is ApiResult.Failure -> it.copy(phase = CopyScanPhase.Scanning, forbidden = res.isForbidden(), message = res.toStaffMessage(R.string.staff_error_load))
                }
            }
            if (_state.value.phase == CopyScanPhase.Scanning) debouncer.resume(SystemClock.elapsedRealtime())
        }
    }

    fun openReturn() = _state.update { it.copy(returning = true) }
    fun closeReturn() = _state.update { it.copy(returning = false) }

    /** Return by code: the same canonical path as the web's quick return. */
    fun returnCopy(outcome: ReturnOutcome, notes: String, penalty: Double?) {
        val phase = _state.value.phase as? CopyScanPhase.Found ?: return
        if (_state.value.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            var res = circulation.returnByCode(phase.code, outcome.wire, notes, penalty)
            if (res is ApiResult.Failure && res.code == "concurrent_retry") res = circulation.returnByCode(phase.code, outcome.wire, notes, penalty)
            val result = res
            _state.update {
                when (result) {
                    is ApiResult.Success -> it.copy(
                        saving = false, returning = false, phase = CopyScanPhase.Scanning, returnedCount = it.returnedCount + 1,
                        message = StaffMessage(R.string.staff_copy_returned, args = listOf(result.data.book.title)),
                    )
                    is ApiResult.Failure -> it.copy(saving = false, forbidden = result.isForbidden(), message = result.toStaffMessage(R.string.staff_error_save))
                }
            }
            if (result is ApiResult.Success) debouncer.resume(SystemClock.elapsedRealtime())
        }
    }

    /** Close the sheet and scan the next copy. */
    fun dismiss() {
        _state.update { it.copy(phase = CopyScanPhase.Scanning, returning = false) }
        debouncer.resume(SystemClock.elapsedRealtime())
    }

    /** Leaving for another screen: keep the scanner paused until we are back. */
    fun leave() {
        _state.update { it.copy(phase = CopyScanPhase.Scanning, returning = false) }
        debouncer.resume(SystemClock.elapsedRealtime())
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffCopyScanScreen(
    onNavigateUp: () -> Unit,
    onNewLoan: (bookId: Int, copyCode: String) -> Unit,
    onOpenBook: (Int) -> Unit,
    onOpenLoan: (Int) -> Unit,
) {
    val vm: StaffCopyScanViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val app: AppViewModel = hiltViewModel()
    val areas by app.staffAreas.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val snackbar = remember { SnackbarHostState() }
    ForbiddenExit(state.forbidden, onNavigateUp)
    LeaveWhen(!areas.circulation, onNavigateUp)
    StaffSnackbarEffect(state.message, snackbar, vm::consumeMessage)
    LaunchedEffect(state.acceptedTick) { if (state.acceptedTick > 0) haptics.performHapticFeedback(HapticFeedbackType.LongPress) }

    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var asked by rememberSaveable { mutableStateOf(false) }
    var cameraBroken by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; asked = true }
    LaunchedEffect(Unit) { if (!granted && !asked) permission.launch(Manifest.permission.CAMERA) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            PinakesTopBar(
                title = stringResource(R.string.staff_scan_copy),
                onNavigateUp = onNavigateUp,
                actions = {
                    if (state.returnedCount > 0) {
                        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) {
                            Text(
                                stringResource(R.string.staff_copy_returned_count, state.returnedCount),
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(16.dp)).background(LocalPinakesColors.current.coverBlank),
                contentAlignment = Alignment.Center,
            ) {
                if (granted && !cameraBroken) {
                    CameraPreview(
                        onCode = vm::onDecoded, torchOn = false, onHasTorch = {}, onUnavailable = { cameraBroken = true },
                        accent = LocalPinakesColors.current.accent, modifier = Modifier.fillMaxSize(), formats = BarcodeAnalyzer.COPY_FORMATS,
                    )
                } else {
                    Column(Modifier.padding(Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(if (cameraBroken) R.string.staff_camera_unavailable else R.string.staff_scan_copy_permission),
                            color = Color.White, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                        )
                        if (!granted && !cameraBroken) {
                            Spacer(Modifier.height(Spacing.md))
                            PrimaryButton(stringResource(R.string.staff_scan_permission_allow), onClick = { permission.launch(Manifest.permission.CAMERA) })
                        }
                    }
                }
                if (state.phase is CopyScanPhase.Looking) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
                    }
                }
            }
            Text(stringResource(R.string.staff_scan_copy_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PinakesTextField(
                value = state.manualInput, onValueChange = vm::onManualInput, label = stringResource(R.string.staff_copy_code),
                isError = state.manualError, errorText = stringResource(R.string.staff_copy_code_required),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.submitManual() }),
                trailingIcon = {
                    IconButton(onClick = vm::submitManual, enabled = state.phase == CopyScanPhase.Scanning) {
                        Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.staff_scan_lookup))
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("copy-code-input"),
            )
            Spacer(Modifier.height(Spacing.xl))
        }
    }

    when (val phase = state.phase) {
        is CopyScanPhase.Found -> if (state.returning) {
            ReturnSheet(
                title = stringResource(R.string.staff_loan_action_return), saving = state.saving,
                onConfirm = vm::returnCopy, onDismiss = vm::closeReturn,
            )
        } else {
            CopyResultSheet(
                phase = phase,
                onReturn = vm::openReturn,
                onNewLoan = { bookId -> vm.leave(); onNewLoan(bookId, phase.code) },
                onOpenBook = { id -> vm.leave(); onOpenBook(id) },
                onOpenLoan = { id -> vm.leave(); onOpenLoan(id) },
                onDismiss = vm::dismiss,
            )
        }
        is CopyScanPhase.NotFound -> {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(onDismissRequest = vm::dismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(stringResource(R.string.staff_copy_not_found), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(
                            if (CopyCodeCodec.looksLikeBookIdentifier(phase.code)) R.string.staff_copy_not_found_isbn else R.string.staff_copy_not_found_body,
                            phase.code,
                        ),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SecondaryButton(stringResource(R.string.staff_scan_rescan), onClick = vm::dismiss, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(Spacing.lg))
                }
            }
        }
        else -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CopyResultSheet(
    phase: CopyScanPhase.Found,
    onReturn: () -> Unit,
    onNewLoan: (Int) -> Unit,
    onOpenBook: (Int) -> Unit,
    onOpenLoan: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val copy = phase.lookup.copy ?: return
    val loan = copy.loan
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).navigationBarsPadding().testTag("copy-sheet"),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                BookCover(copy.book?.title.orEmpty(), copy.book?.coverUrl, Modifier.width(72.dp).aspectRatio(2f / 3f), compact = true)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(copy.book?.title.orEmpty(), style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(
                        stringResource(R.string.staff_copy_code_value, copy.inventoryNumber ?: phase.code),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (loan != null) {
                        val (status, label) = loanChip(loan)
                        AvailabilityChip(status = status, label = label)
                    } else {
                        AvailabilityChip(
                            status = if (copy.available == true) AvailabilityStatus.Available else AvailabilityStatus.Unavailable,
                            label = copy.statusLabel ?: copy.status,
                        )
                    }
                }
            }
            if (loan != null) {
                InfoLine(stringResource(R.string.staff_loan_borrower), listOfNotNull(loan.user.name, loan.user.cardNumber).joinToString(" · "))
                InfoLine(stringResource(R.string.staff_loan_due), loan.dueAt?.let(DateFormat::date))
                val returnable = loan.status == "in_corso" || loan.status == "in_ritardo" || "return" in loan.actions
                if (returnable) PrimaryButton(stringResource(R.string.staff_loan_action_return), onClick = onReturn, modifier = Modifier.fillMaxWidth())
                SecondaryButton(stringResource(R.string.staff_loan_open), onClick = { onOpenLoan(loan.id) }, modifier = Modifier.fillMaxWidth())
            }
            if (copy.available == true) {
                PrimaryButton(stringResource(R.string.staff_loan_new_with_copy), onClick = { onNewLoan(copy.book?.id ?: 0) },
                    enabled = (copy.book?.id ?: 0) > 0, modifier = Modifier.fillMaxWidth())
            }
            SecondaryButton(stringResource(R.string.staff_open_book), onClick = { onOpenBook(copy.book?.id ?: 0) },
                enabled = (copy.book?.id ?: 0) > 0, modifier = Modifier.fillMaxWidth())
            PinakesTextButton(stringResource(R.string.staff_scan_next), onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}
