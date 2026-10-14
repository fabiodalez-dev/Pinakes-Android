package com.pinakes.app.ui.screens.staff.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pinakes.app.R
import com.pinakes.app.data.model.LookupOption
import com.pinakes.app.data.staff.Identifier
import com.pinakes.app.data.staff.StaffBookForm
import com.pinakes.app.ui.components.BookCover
import com.pinakes.app.ui.components.EmptyState
import com.pinakes.app.ui.components.MediaRow
import com.pinakes.app.ui.components.PinakesTextButton
import com.pinakes.app.ui.components.PinakesTextField
import com.pinakes.app.ui.components.PinakesTopBar
import com.pinakes.app.ui.components.PrimaryButton
import com.pinakes.app.ui.components.SecondaryButton
import com.pinakes.app.ui.screens.staff.ForbiddenExit
import com.pinakes.app.ui.screens.staff.FormSectionTitle
import com.pinakes.app.ui.screens.staff.LookupField
import com.pinakes.app.ui.screens.staff.QuantityStepper
import com.pinakes.app.ui.screens.staff.findActivity
import com.pinakes.app.ui.theme.LocalPinakesColors
import com.pinakes.app.ui.theme.Spacing

private enum class CameraAccess { Unknown, Granted, Denied, PermanentlyDenied }

/**
 * ISBN/EAN scanner with batch mode. The manual entry field is always visible: it is the path
 * on emulators, devices without a camera and when the permission is denied.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffScanScreen(
    onNavigateUp: () -> Unit,
    onEditNew: (String) -> Unit,
    onOpenBook: (Int) -> Unit,
) {
    val vm: StaffScanViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val snackbarHost = remember { SnackbarHostState() }
    var torchOn by rememberSaveable { mutableStateOf(false) }
    var hasTorch by remember { mutableStateOf(false) }
    var cameraBroken by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var askedOnce by rememberSaveable { mutableStateOf(false) }
    ForbiddenExit(state.forbidden, onNavigateUp)

    fun currentAccess(): CameraAccess {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (granted) return CameraAccess.Granted
        if (!askedOnce) return CameraAccess.Unknown
        val rationale = context.findActivity()?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) ?: true
        return if (rationale) CameraAccess.Denied else CameraAccess.PermanentlyDenied
    }
    var access by remember { mutableStateOf(currentAccess()) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        askedOnce = true
        access = currentAccess()
    }
    // First visit: ask straight away. Coming back from the system settings: re-check.
    LaunchedEffect(Unit) { if (access == CameraAccess.Unknown) permission.launch(Manifest.permission.CAMERA) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) access = currentAccess() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.acceptedTick) { if (state.acceptedTick > 0) haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
    val message = state.message ?: state.messageRes?.let { stringResource(it) }
    LaunchedEffect(message) { message?.let { snackbarHost.showSnackbar(it); vm.consumeMessage() } }

    Scaffold(
        topBar = {
            PinakesTopBar(
                title = stringResource(R.string.staff_scan),
                onNavigateUp = onNavigateUp,
                actions = {
                    if (state.sessionCount > 0) {
                        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) {
                            Text(
                                stringResource(R.string.staff_scan_session_count, state.sessionCount),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                            )
                        }
                    }
                    if (access == CameraAccess.Granted && hasTorch) {
                        IconButton(onClick = { torchOn = !torchOn }) {
                            Icon(
                                if (torchOn) Icons.Outlined.FlashlightOff else Icons.Outlined.FlashlightOn,
                                contentDescription = stringResource(if (torchOn) R.string.staff_scan_torch_off else R.string.staff_scan_torch_on),
                            )
                        }
                    }
                    IconButton(onClick = { settingsOpen = true }) {
                        Icon(Icons.Outlined.Tune, contentDescription = stringResource(R.string.staff_scan_settings))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        if (state.phase is ScanPhase.Offline) {
            EmptyState(
                title = stringResource(R.string.staff_scan_offline_title),
                subtitle = stringResource(R.string.staff_scan_offline_subtitle),
                icon = Icons.Outlined.CloudOff,
                actionLabel = stringResource(R.string.action_retry),
                onAction = vm::rescan,
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item(key = "camera") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(LocalPinakesColors.current.coverBlank),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        access == CameraAccess.Granted && !cameraBroken -> CameraPreview(
                            onCode = vm::onDecoded,
                            torchOn = torchOn,
                            onHasTorch = { hasTorch = it },
                            onUnavailable = { cameraBroken = true },
                            accent = LocalPinakesColors.current.accent,
                            modifier = Modifier.fillMaxSize(),
                        )
                        access == CameraAccess.Granted -> CameraMessage(stringResource(R.string.staff_camera_unavailable))
                        access == CameraAccess.PermanentlyDenied -> CameraMessage(
                            stringResource(R.string.staff_scan_permission_denied),
                            actionLabel = stringResource(R.string.staff_scan_permission_settings),
                            onAction = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            },
                        )
                        else -> CameraMessage(
                            stringResource(R.string.staff_scan_permission_rationale),
                            actionLabel = stringResource(R.string.staff_scan_permission_allow),
                            onAction = { permission.launch(Manifest.permission.CAMERA) },
                        )
                    }
                    if (state.phase is ScanPhase.Looking) {
                        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = androidx.compose.ui.graphics.Color.White, strokeWidth = 3.dp)
                                Spacer(Modifier.height(Spacing.sm))
                                Text(stringResource(R.string.staff_scan_looking), color = androidx.compose.ui.graphics.Color.White,
                                    style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            item(key = "hint") {
                Text(stringResource(R.string.staff_scan_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item(key = "manual") {
                var manual by rememberSaveable { mutableStateOf("") }
                val submit = { if (vm.submitManual(manual)) manual = "" }
                PinakesTextField(
                    value = manual,
                    onValueChange = { manual = it; vm.clearManualError() },
                    label = stringResource(R.string.staff_scan_manual_label),
                    placeholder = "978-88-04-66823-7",
                    isError = state.manualError,
                    errorText = stringResource(R.string.staff_identifier_invalid),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submit() }),
                    trailingIcon = {
                        IconButton(onClick = submit, enabled = state.phase == ScanPhase.Scanning) {
                            Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.staff_scan_lookup))
                        }
                    },
                    maxLength = 20,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item(key = "batch") { BatchSummary(state, onOpen = { settingsOpen = true }) }
            if (state.added.isNotEmpty()) {
                item(key = "added-title") { FormSectionTitle(stringResource(R.string.staff_scan_added_list)) }
                itemsIndexed(state.added, key = { i, _ -> "added-${state.added.size - i}" }) { _, entry ->
                    MediaRow(
                        title = entry.title,
                        coverUrl = entry.coverUrl,
                        line1 = if (entry.newRecord) stringResource(R.string.staff_scan_added_new, entry.copies)
                        else stringResource(R.string.staff_scan_added_copies, entry.copies),
                        onClick = { onOpenBook(entry.bookId) },
                    )
                }
            }
            item(key = "bottom") { Spacer(Modifier.height(Spacing.xl)) }
        }
    }

    if (settingsOpen) BatchSettingsSheet(state, vm, onDismiss = { settingsOpen = false })
    if (state.sheetOpen) {
        ScanResultSheet(
            state = state,
            vm = vm,
            onEdit = { code -> vm.leaveForEdit(); onEditNew(code) },
            onOpenExisting = { id -> vm.leaveForEdit(); onOpenBook(id) },
        )
    }
}

@Composable
private fun CameraMessage(text: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.padding(Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Outlined.CameraAlt, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(Spacing.sm))
        Text(text, color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (actionLabel != null) {
            Spacer(Modifier.height(Spacing.md))
            PrimaryButton(actionLabel, onClick = onAction)
        }
    }
}

@Composable
private fun BatchSummary(state: StaffScanUiState, onOpen: () -> Unit) {
    val shelf = state.shelves.firstOrNull { it.id == state.shelfId }
    val level = shelf?.levels?.firstOrNull { it.id == state.levelId }
    val location = if (shelf != null && level != null) "${shelf.display} · ${stringResource(R.string.staff_level_number, level.number)}"
    else stringResource(R.string.staff_scan_no_location)
    Surface(onClick = onOpen, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.staff_scan_batch_title), style = MaterialTheme.typography.bodyLarge)
                Text(
                    listOf(
                        stringResource(R.string.staff_scan_batch_copies, state.copies),
                        location,
                        stringResource(if (state.autoConfirm) R.string.staff_scan_auto_on else R.string.staff_scan_auto_off),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BatchSettingsSheet(state: StaffScanUiState, vm: StaffScanViewModel, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(stringResource(R.string.staff_scan_settings), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.staff_scan_settings_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            QuantityStepper(stringResource(R.string.staff_copies), state.copies, vm::setCopies, range = StaffBookForm.COPIES_RANGE)
            LookupField(
                label = stringResource(R.string.staff_shelf),
                value = state.shelfId?.toString().orEmpty(),
                displayValue = state.shelves.firstOrNull { it.id == state.shelfId }?.display.orEmpty(),
                options = state.shelves.map { LookupOption(it.id.toString(), it.display) },
                editable = false,
                onValueChange = { vm.setShelf(it.toIntOrNull()) },
                modifier = Modifier.fillMaxWidth(),
            )
            val levels = state.shelves.firstOrNull { it.id == state.shelfId }?.levels.orEmpty()
            if (state.shelfId != null) {
                val options = levels.map { l ->
                    LookupOption(l.id.toString(), listOfNotNull(stringResource(R.string.staff_level_number, l.number), l.description?.takeIf { it.isNotBlank() }).joinToString(" · "))
                }
                LookupField(
                    label = stringResource(R.string.staff_level),
                    value = state.levelId?.toString().orEmpty(),
                    displayValue = options.firstOrNull { it.value == state.levelId?.toString() }?.label.orEmpty(),
                    options = options,
                    editable = false,
                    onValueChange = { vm.setLevel(it.toIntOrNull()) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.staff_position_auto), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.staff_scan_auto_confirm), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.staff_scan_auto_confirm_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = state.autoConfirm, onCheckedChange = vm::setAutoConfirm)
            }
            PrimaryButton(stringResource(R.string.action_done), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanResultSheet(
    state: StaffScanUiState,
    vm: StaffScanViewModel,
    onEdit: (String) -> Unit,
    onOpenExisting: (Int) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { if (!state.saving) vm.skip() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            when (val phase = state.phase) {
                is ScanPhase.Preview -> {
                    val p = phase.preview
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                        BookCover(p.title, p.coverUrl, Modifier.width(88.dp).aspectRatio(2f / 3f), compact = true)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Text(p.title, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            p.subtitle?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            p.authorNames.takeIf { it.isNotEmpty() }?.let { Text(it.joinToString(", "), style = MaterialTheme.typography.bodyMedium) }
                            val meta = listOfNotNull(p.publisher?.takeIf { it.isNotBlank() }, p.year?.takeIf { it.isNotBlank() }).joinToString(" · ")
                            if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(phase.identifier.lookupCode, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    QuantityStepper(stringResource(R.string.staff_copies), state.copies, vm::setCopies, range = StaffBookForm.COPIES_RANGE, enabled = !state.saving)
                    PrimaryButton(stringResource(R.string.staff_scan_confirm), onClick = vm::confirm, loading = state.saving, modifier = Modifier.fillMaxWidth())
                    SecondaryButton(stringResource(R.string.staff_scan_edit_details), onClick = { onEdit(phase.identifier.lookupCode) },
                        enabled = !state.saving, modifier = Modifier.fillMaxWidth())
                    PinakesTextButton(stringResource(R.string.staff_scan_skip), onClick = vm::skip, enabled = !state.saving,
                        modifier = Modifier.align(Alignment.CenterHorizontally))
                }
                is ScanPhase.Duplicate -> {
                    var quantity by remember { mutableIntStateOf(1) }
                    Text(stringResource(R.string.staff_scan_duplicate), style = MaterialTheme.typography.titleMedium)
                    Text(phase.existing.title, style = MaterialTheme.typography.titleLarge)
                    val details = listOfNotNull(
                        stringResource(R.string.staff_copies_total, phase.existing.copiesTotal),
                        phase.existing.locationLabel,
                    ).joinToString(" · ")
                    Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    QuantityStepper(stringResource(R.string.staff_copies_to_add), quantity, { quantity = it }, enabled = !state.saving)
                    PrimaryButton(stringResource(R.string.staff_scan_add_copy), onClick = { vm.addCopies(quantity) }, loading = state.saving,
                        modifier = Modifier.fillMaxWidth())
                    SecondaryButton(stringResource(R.string.staff_scan_open_existing), onClick = { onOpenExisting(phase.existing.id) },
                        enabled = !state.saving && phase.existing.id > 0, modifier = Modifier.fillMaxWidth())
                    PinakesTextButton(stringResource(R.string.staff_scan_skip), onClick = vm::skip, enabled = !state.saving,
                        modifier = Modifier.align(Alignment.CenterHorizontally))
                }
                is ScanPhase.NotFound -> {
                    Text(stringResource(R.string.staff_scan_not_found), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(
                            if (phase.identifier is Identifier.Ean) R.string.staff_scan_not_found_ean else R.string.staff_scan_not_found_body,
                            phase.identifier.lookupCode,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PrimaryButton(stringResource(R.string.staff_scan_create_manual), onClick = { onEdit(phase.identifier.lookupCode) },
                        modifier = Modifier.fillMaxWidth())
                    SecondaryButton(stringResource(R.string.staff_scan_rescan), onClick = vm::rescan, modifier = Modifier.fillMaxWidth())
                }
                else -> Unit
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}
