package com.pinakes.app.ui.screens.staff.s090.patrons

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pinakes.app.R
import com.pinakes.app.data.model.PatronTypeList
import com.pinakes.app.data.model.StaffUserDetail
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.repository.Staff090PatronsRepository
import com.pinakes.app.data.repository.StaffFilesRepository
import com.pinakes.app.data.repository.isForbidden
import com.pinakes.app.data.staff.CoverImageEncoder
import com.pinakes.app.data.staff.PatronTools
import com.pinakes.app.data.staff.Staff090Areas
import com.pinakes.app.data.staff.SubmissionKeys
import com.pinakes.app.ui.common.DateFormat
import com.pinakes.app.ui.components.ConfirmDialog
import com.pinakes.app.ui.components.InlineErrorBanner
import com.pinakes.app.ui.components.LoadingState
import com.pinakes.app.ui.components.PrimaryButton
import com.pinakes.app.ui.components.SecondaryButton
import com.pinakes.app.ui.screens.staff.ExportFileDialog
import com.pinakes.app.ui.screens.staff.FormSectionTitle
import com.pinakes.app.ui.screens.staff.InfoCard
import com.pinakes.app.ui.screens.staff.InfoLine
import com.pinakes.app.ui.screens.staff.StaffActionRow
import com.pinakes.app.ui.screens.staff.StaffMessage
import com.pinakes.app.ui.screens.staff.SwitchRow
import com.pinakes.app.ui.screens.staff.resolve
import com.pinakes.app.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the patron tools are doing; one at a time. */
enum class PatronBusy { RENEW, TYPE, PHOTO, PRINT }

data class PatronSectionUiState(
    val userId: Int = 0,
    val photo: ByteArray? = null,
    /** The `photo_url` (versioned) the bytes belong to: a new photo has a new one. */
    val photoUrl: String? = null,
    val busy: PatronBusy? = null,
    val types: PatronTypeList? = null,
    val typesLoading: Boolean = false,
    val typeSheet: Boolean = false,
    val selectedType: Int? = null,
    val recompute: Boolean = false,
    val confirmRenew: Boolean = false,
    val confirmRemovePhoto: Boolean = false,
    val export: File? = null,
    val message: StaffMessage? = null,
    val error: StaffMessage? = null,
    /** Bumped after a write: the record screen reloads the user. */
    val changed: Int = 0,
    val forbidden: Boolean = false,
)

/**
 * The 0.9.0 tools on a patron record: photo, patron type, card renewal and printout, and the
 * way into the ledger. Every write answers the reloaded user, and the record screen reloads.
 */
@HiltViewModel
class Staff090PatronSectionViewModel @Inject constructor(
    private val patrons: Staff090PatronsRepository,
    private val files: StaffFilesRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _state = MutableStateFlow(PatronSectionUiState())
    val state: StateFlow<PatronSectionUiState> = _state.asStateFlow()
    private val renewKeys = SubmissionKeys()

    /** The user on screen: loads its photo when it has one (or a new one). */
    fun bind(user: StaffUserDetail, photos: Boolean) {
        val url = user.photoUrl?.takeIf { photos && user.hasPhoto }
        _state.update { if (it.userId != user.id) PatronSectionUiState(userId = user.id) else it }
        if (url == null) {
            _state.update { it.copy(photo = null, photoUrl = null) }
            return
        }
        if (url == _state.value.photoUrl) return
        _state.update { it.copy(photoUrl = url) }
        viewModelScope.launch {
            when (val res = patrons.photo(user.id)) {
                is ApiResult.Success -> _state.update { if (it.photoUrl == url) it.copy(photo = res.data) else it }
                is ApiResult.Failure -> _state.update { it.copy(forbidden = res.isForbidden()) }
            }
        }
    }

    // ---- Patron type ----

    fun openTypes(current: Int?) {
        _state.update { it.copy(typeSheet = true, selectedType = current, recompute = false, error = null) }
        if (_state.value.types != null) return
        _state.update { it.copy(typesLoading = true) }
        viewModelScope.launch {
            val res = patrons.patronTypes()
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(types = res.data, typesLoading = false)
                    is ApiResult.Failure -> it.copy(typesLoading = false, forbidden = res.isForbidden(), error = res.toPatronsMessage(R.string.staff_error_load))
                }
            }
        }
    }

    fun selectType(id: Int) = _state.update { it.copy(selectedType = id) }
    fun setRecompute(value: Boolean) = _state.update { it.copy(recompute = value) }
    fun closeTypes() = _state.update { it.copy(typeSheet = false) }

    fun saveType() {
        val s = _state.value
        if (s.busy != null) return
        // The default type is sent as "none of its own", as the web form's empty choice.
        val chosen = s.selectedType?.takeIf { it != s.types?.defaultId }
        _state.update { it.copy(busy = PatronBusy.TYPE, error = null) }
        viewModelScope.launch {
            val res = patrons.setPatronType(s.userId, chosen, s.recompute)
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(
                        busy = null, typeSheet = false, changed = it.changed + 1,
                        message = StaffMessage(if (res.data.cardExpiryRecomputed) R.string.staff090_patrons_type_saved_expiry else R.string.staff090_patrons_type_saved),
                    )
                    is ApiResult.Failure -> it.copy(busy = null, forbidden = res.isForbidden(), error = res.toPatronsMessage(R.string.staff_error_save))
                }
            }
        }
    }

    // ---- Card ----

    fun askRenew() = _state.update { it.copy(confirmRenew = true) }
    fun dismissRenew() = _state.update { it.copy(confirmRenew = false) }

    fun renew() {
        val s = _state.value
        if (s.busy != null) return
        // One key per renewal: a retry after a lost answer never adds the duration twice.
        val key = renewKeys.keyFor(s.userId to s.changed)
        _state.update { it.copy(confirmRenew = false, busy = PatronBusy.RENEW, error = null) }
        viewModelScope.launch {
            val res = patrons.renewCard(s.userId, key)
            _state.update {
                when (res) {
                    is ApiResult.Success -> {
                        renewKeys.reset()
                        it.copy(
                            busy = null, changed = it.changed + 1,
                            message = StaffMessage(R.string.staff090_patrons_card_renewed, args = listOf(DateFormat.date(res.data.cardExpiresAt))),
                        )
                    }
                    is ApiResult.Failure -> {
                        if (res.code == SubmissionKeys.CONFLICT) renewKeys.reset()
                        it.copy(busy = null, forbidden = res.isForbidden(), error = res.toPatronsMessage(R.string.staff_error_save))
                    }
                }
            }
        }
    }

    fun printCard() {
        val s = _state.value
        if (s.busy != null) return
        _state.update { it.copy(busy = PatronBusy.PRINT, error = null) }
        viewModelScope.launch {
            val res = files.download("staff/users/${s.userId}/card.pdf", "tessera_${s.userId}.pdf")
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(busy = null, export = res.data)
                    is ApiResult.Failure -> it.copy(busy = null, forbidden = res.isForbidden(), error = res.toPatronsMessage(R.string.staff_download_failed))
                }
            }
        }
    }

    fun closeExport() = _state.update { it.copy(export = null) }

    // ---- Photo ----

    /** A photo taken or picked: re-encoded (EXIF rotation, metadata dropped), then uploaded. */
    fun uploadPhoto(uri: Uri, scratch: File?) {
        val s = _state.value
        if (s.busy != null) return
        _state.update { it.copy(busy = PatronBusy.PHOTO, error = null) }
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                CoverImageEncoder.encode(context, uri).also { scratch?.delete() }
            }
            if (bytes == null) {
                _state.update { it.copy(busy = null, error = StaffMessage(R.string.staff090_patrons_photo_unreadable)) }
                return@launch
            }
            val res = patrons.uploadPhoto(s.userId, bytes)
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(
                        busy = null, photo = bytes, photoUrl = res.data.photoUrl, changed = it.changed + 1,
                        message = StaffMessage(R.string.staff090_patrons_photo_saved),
                    )
                    is ApiResult.Failure -> it.copy(busy = null, forbidden = res.isForbidden(), error = res.toPatronsMessage(R.string.staff_error_save))
                }
            }
        }
    }

    fun askRemovePhoto() = _state.update { it.copy(confirmRemovePhoto = true) }
    fun dismissRemovePhoto() = _state.update { it.copy(confirmRemovePhoto = false) }

    fun removePhoto() {
        val s = _state.value
        if (s.busy != null) return
        _state.update { it.copy(confirmRemovePhoto = false, busy = PatronBusy.PHOTO, error = null) }
        viewModelScope.launch {
            val res = patrons.deletePhoto(s.userId)
            _state.update {
                when (res) {
                    is ApiResult.Success -> it.copy(busy = null, photo = null, photoUrl = null, changed = it.changed + 1, message = StaffMessage(R.string.staff090_patrons_photo_removed))
                    is ApiResult.Failure -> it.copy(busy = null, forbidden = res.isForbidden(), error = res.toPatronsMessage(R.string.staff_error_save))
                }
            }
        }
    }

    fun photoUnavailable() = _state.update { it.copy(error = StaffMessage(R.string.staff_camera_unavailable)) }
    fun consumeMessage() = _state.update { it.copy(message = null) }
}

/** A fresh scratch file for the camera app (cache/covers is shared through the FileProvider). */
internal fun newPatronPhotoFile(context: Context): File {
    val dir = File(context.cacheDir, "covers").apply { mkdirs() }
    return File(dir, "patron_${System.currentTimeMillis()}.jpg")
}

/**
 * The patron section of the staff user screen. Shown only when the server offers at least one
 * of its tools ([PatronTools]); [onChanged] reloads the record after a write.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Staff090PatronSection(
    user: StaffUserDetail,
    areas: Staff090Areas,
    onChanged: () -> Unit,
    onOpenLedger: (Int) -> Unit,
    onForbidden: () -> Unit,
) {
    val tools = PatronTools.of(areas, user)
    if (!tools.section) return
    val vm: Staff090PatronSectionViewModel = hiltViewModel(key = "patron-section-${user.id}")
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(user.id, user.photoUrl, user.hasPhoto, tools.photo) { vm.bind(user, tools.photo) }
    LaunchedEffect(state.changed) { if (state.changed > 0) onChanged() }
    LaunchedEffect(state.forbidden) { if (state.forbidden) onForbidden() }
    val message = state.message?.resolve()
    LaunchedEffect(state.message) {
        if (!message.isNullOrBlank()) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            vm.consumeMessage()
        }
    }

    var scratchPath by rememberSaveable { mutableStateOf<String?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = scratchPath?.let(::File)
        if (ok && file != null) vm.uploadPhoto(Uri.fromFile(file), file) else file?.delete()
        scratchPath = null
    }
    fun launchCamera() {
        val file = newPatronPhotoFile(context)
        scratchPath = file.absolutePath
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        runCatching { takePicture.launch(uri) }.onFailure { vm.photoUnavailable() }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else Toast.makeText(context, context.getString(R.string.staff_scan_permission_rationale), Toast.LENGTH_LONG).show()
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.uploadPhoto(uri, null) }

    Column(Modifier.testTag("patron-section")) {
        FormSectionTitle(stringResource(R.string.staff090_patrons_section))
        InfoCard {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg), verticalAlignment = Alignment.Top) {
                if (tools.photo) {
                    if (state.busy == PatronBusy.PHOTO) LoadingState(modifier = Modifier.heightIn(max = 88.dp).padding(end = Spacing.sm))
                    else PatronPhoto(state.photo, stringResource(R.string.staff090_patrons_photo), modifier = Modifier.testTag("patron-photo"))
                }
                Column(Modifier.weight(1f)) {
                    user.patronType?.let { type ->
                        InfoLine(
                            stringResource(R.string.staff090_patrons_type),
                            if (type.assigned) type.name else stringResource(R.string.staff090_patrons_type_default, type.name),
                        )
                    }
                    if (user.patronType == null) {
                        Text(stringResource(R.string.staff090_patrons_tools_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            state.error?.let { InlineErrorBanner(message = it.resolve(), modifier = Modifier.padding(top = Spacing.sm)) }
            Spacer(Modifier.height(Spacing.sm))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (tools.renewCard) SecondaryButton(
                    stringResource(R.string.staff090_patrons_renew_card), onClick = vm::askRenew, enabled = state.busy == null,
                    leadingIcon = Icons.Outlined.Refresh, modifier = Modifier.testTag("patron-renew"),
                )
                if (tools.printCard) SecondaryButton(
                    stringResource(R.string.staff090_patrons_print_card), onClick = vm::printCard, enabled = state.busy == null,
                    leadingIcon = Icons.Outlined.Print, modifier = Modifier.testTag("patron-print"),
                )
                if (tools.patronTypeEdit) SecondaryButton(
                    stringResource(R.string.staff090_patrons_change_type), onClick = { vm.openTypes(user.patronType?.id) }, enabled = state.busy == null,
                    modifier = Modifier.testTag("patron-type"),
                )
                if (tools.photoEdit) {
                    SecondaryButton(stringResource(if (state.photo != null) R.string.staff090_patrons_photo_replace else R.string.staff090_patrons_photo_take),
                        leadingIcon = Icons.Outlined.CameraAlt, enabled = state.busy == null, onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCamera()
                            else permission.launch(Manifest.permission.CAMERA)
                        })
                    SecondaryButton(stringResource(R.string.staff090_patrons_photo_pick), leadingIcon = Icons.Outlined.Image, enabled = state.busy == null, onClick = {
                        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    })
                    if (user.hasPhoto || state.photo != null) SecondaryButton(
                        stringResource(R.string.staff090_patrons_photo_remove), onClick = vm::askRemovePhoto, enabled = state.busy == null,
                        leadingIcon = Icons.Outlined.Delete,
                    )
                }
            }
        }
        val ledger = user.ledger
        if (tools.ledger && ledger != null) {
            StaffActionRow(
                Icons.Outlined.AccountBalanceWallet,
                stringResource(R.string.staff090_ledger),
                listOfNotNull(
                    stringResource(R.string.staff090_patrons_balance_line, money(ledger.balanceCents, ledger.currency)),
                    stringResource(R.string.staff090_patrons_debt_blocked).takeIf { ledger.debtBlocked },
                ).joinToString(" · "),
            ) { onOpenLedger(user.id) }
        }
    }

    if (state.confirmRenew) ConfirmDialog(
        title = stringResource(R.string.staff090_patrons_renew_card),
        body = stringResource(R.string.staff090_patrons_renew_body),
        confirmLabel = stringResource(R.string.staff090_patrons_renew_card),
        onConfirm = vm::renew, onDismiss = vm::dismissRenew,
    )
    if (state.confirmRemovePhoto) ConfirmDialog(
        title = stringResource(R.string.staff090_patrons_photo_remove),
        body = stringResource(R.string.staff090_patrons_photo_remove_body),
        confirmLabel = stringResource(R.string.staff090_patrons_photo_remove),
        onConfirm = vm::removePhoto, onDismiss = vm::dismissRemovePhoto,
    )
    if (state.typeSheet) PatronTypeSheet(state, vm)
    state.export?.let { ExportFileDialog(it, "application/pdf", stringResource(R.string.staff090_patrons_card_ready), onDismiss = vm::closeExport) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PatronTypeSheet(state: PatronSectionUiState, vm: Staff090PatronSectionViewModel) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = { if (state.busy == null) vm.closeTypes() }, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(stringResource(R.string.staff090_patrons_change_type), style = MaterialTheme.typography.headlineSmall)
            val types = state.types
            when {
                state.typesLoading -> LoadingState(modifier = Modifier.heightIn(max = 120.dp))
                types == null -> state.error?.let { InlineErrorBanner(message = it.resolve()) }
                else -> {
                    // Inactive types stay listed only when the account already has one.
                    val choices = types.types.filter { it.active || it.id == state.selectedType }
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                        items(choices, key = { it.id }) { type ->
                            val selected = type.id == state.selectedType
                            Surface(
                                onClick = { vm.selectType(type.id) },
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs).testTag("type-${type.id}"),
                            ) {
                                Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                                        contentDescription = stringResource(if (selected) R.string.staff_selected else R.string.staff_not_selected),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Column(Modifier.weight(1f).padding(start = Spacing.md)) {
                                        Text(
                                            if (type.isDefault) stringResource(R.string.staff090_patrons_type_default, type.name) else type.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                        val details = listOfNotNull(
                                            type.description?.takeIf { it.isNotBlank() },
                                            type.cardDurationDays?.let { stringResource(R.string.staff090_patrons_type_card_days, it) },
                                        ).joinToString(" · ")
                                        if (details.isNotBlank()) Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                    SwitchRow(
                        stringResource(R.string.staff090_patrons_recompute_expiry), state.recompute, vm::setRecompute,
                        subtitle = stringResource(R.string.staff090_patrons_recompute_expiry_hint),
                    )
                    state.error?.let { InlineErrorBanner(message = it.resolve()) }
                    PrimaryButton(
                        stringResource(R.string.action_save), onClick = vm::saveType, loading = state.busy == PatronBusy.TYPE,
                        enabled = types.canAssign && state.selectedType != null, modifier = Modifier.fillMaxWidth().testTag("type-save"),
                    )
                }
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}
