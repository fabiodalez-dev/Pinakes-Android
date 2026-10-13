package com.pinakes.app.ui.screens.staff.scan

import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pinakes.app.R
import com.pinakes.app.data.model.ExistingBook
import com.pinakes.app.data.model.IsbnPreview
import com.pinakes.app.data.model.Shelf
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.ui.common.isOffline
import com.pinakes.app.ui.common.serverMessage
import com.pinakes.app.data.network.ErrorCodes
import com.pinakes.app.data.repository.StaffRepository
import com.pinakes.app.data.repository.isDuplicate
import com.pinakes.app.data.repository.isForbidden
import com.pinakes.app.data.staff.Identifier
import com.pinakes.app.data.staff.IsbnCodec
import com.pinakes.app.data.staff.ScanDebouncer
import com.pinakes.app.data.staff.StaffBookForm
import com.pinakes.app.data.staff.StaffBookWrite
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where one scan is in its lifecycle. Sheets are shown for Preview, Duplicate and NotFound. */
sealed interface ScanPhase {
    data object Scanning : ScanPhase
    data class Looking(val code: String) : ScanPhase
    data class Preview(val identifier: Identifier, val preview: IsbnPreview) : ScanPhase
    data class Duplicate(val identifier: Identifier, val existing: ExistingBook) : ScanPhase
    data class NotFound(val identifier: Identifier) : ScanPhase
    /** The server could not be reached: scanning needs a connection. */
    data object Offline : ScanPhase
}

/** A title handled in this batch session (shown in the "added" list). */
data class ScanAdded(val bookId: Int, val title: String, val coverUrl: String?, val copies: Int, val newRecord: Boolean)

data class StaffScanUiState(
    val phase: ScanPhase = ScanPhase.Scanning,
    /** Batch settings, remembered across scans. */
    val copies: Int = 1,
    val shelfId: Int? = null,
    val levelId: Int? = null,
    val autoConfirm: Boolean = false,
    val shelves: List<Shelf> = emptyList(),
    val saving: Boolean = false,
    val added: List<ScanAdded> = emptyList(),
    val manualInput: String = "",
    val manualError: Boolean = false,
    val message: String? = null,
    val messageRes: Int? = null,
    /** Bumped whenever a code is accepted: the screen plays a haptic tick. */
    val acceptedTick: Int = 0,
    val forbidden: Boolean = false,
) {
    val sessionCount: Int get() = added.size
    val sheetOpen: Boolean get() = phase is ScanPhase.Preview || phase is ScanPhase.Duplicate || phase is ScanPhase.NotFound
}

/**
 * Batch "scan-to-add". `Scanning → Looking → Preview | Duplicate | NotFound → (save) → Scanning`.
 * Decodes pass checksum validation ([IsbnCodec.fromScan]) and the [ScanDebouncer] (two
 * identical reads, 3 s suppression, paused while a sheet is open). Nothing is created without
 * a confirm unless the user enables auto-confirm.
 */
@HiltViewModel
class StaffScanViewModel @Inject constructor(
    private val staff: StaffRepository,
    private val saved: SavedStateHandle,
) : ViewModel() {

    private val debouncer = ScanDebouncer()

    private val _state = MutableStateFlow(
        StaffScanUiState(
            copies = saved.get<Int>(KEY_COPIES) ?: 1,
            shelfId = saved.get<Int>(KEY_SHELF)?.takeIf { it > 0 },
            levelId = saved.get<Int>(KEY_LEVEL)?.takeIf { it > 0 },
            autoConfirm = saved.get<Boolean>(KEY_AUTO) ?: false,
        )
    )
    val state: StateFlow<StaffScanUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            when (val res = staff.shelves()) {
                is ApiResult.Success -> _state.update { it.copy(shelves = res.data) }
                is ApiResult.Failure -> if (res.isForbidden()) _state.update { it.copy(forbidden = true) }
            }
        }
    }

    /** A raw decode from the analyzer (main thread). */
    fun onDecoded(raw: String) {
        if (_state.value.phase != ScanPhase.Scanning || _state.value.saving) return
        val identifier = IsbnCodec.fromScan(raw) ?: return
        val accepted = debouncer.offer(identifier.lookupCode, SystemClock.elapsedRealtime()) ?: return
        _state.update { it.copy(acceptedTick = it.acceptedTick + 1) }
        lookup(identifier, accepted)
    }

    fun onManualInput(value: String) = _state.update { it.copy(manualInput = value, manualError = false) }

    /** The always-visible manual entry (emulators, denied permission, damaged barcodes). */
    fun submitManual() {
        val identifier = IsbnCodec.fromManual(_state.value.manualInput)
        if (identifier == null) {
            _state.update { it.copy(manualError = true) }
            return
        }
        if (_state.value.phase is ScanPhase.Looking || _state.value.sheetOpen) return
        _state.update { it.copy(manualInput = "", manualError = false) }
        lookup(identifier, identifier.lookupCode)
    }

    private fun lookup(identifier: Identifier, code: String) {
        debouncer.pause()
        _state.update { it.copy(phase = ScanPhase.Looking(code), message = null, messageRes = null) }
        viewModelScope.launch {
            when (val res = staff.isbnLookup(code)) {
                is ApiResult.Success -> {
                    val data = res.data
                    val existing = data.existing
                    val preview = data.preview
                    when {
                        existing != null && existing.id > 0 -> _state.update { it.copy(phase = ScanPhase.Duplicate(identifier, existing)) }
                        preview != null && preview.title.isNotBlank() -> {
                            _state.update { it.copy(phase = ScanPhase.Preview(identifier, preview)) }
                            if (_state.value.autoConfirm) confirm()
                        }
                        else -> _state.update { it.copy(phase = ScanPhase.NotFound(identifier)) }
                    }
                }
                is ApiResult.Failure -> when {
                    res.isForbidden() -> _state.update { it.copy(phase = ScanPhase.Scanning, forbidden = true) }
                    res.code == ErrorCodes.NETWORK -> _state.update { it.copy(phase = ScanPhase.Offline) }
                    res.httpStatus == 422 || res.code == ErrorCodes.VALIDATION -> {
                        _state.update { it.copy(phase = ScanPhase.Scanning, messageRes = R.string.staff_identifier_invalid) }
                        debouncer.resume()
                    }
                    // Scrapers unreachable or failing: the record can still be created by hand.
                    else -> _state.update {
                        it.copy(
                            phase = ScanPhase.NotFound(identifier),
                            messageRes = if (res.code == ErrorCodes.SCRAPE_UNAVAILABLE) R.string.staff_scrape_unavailable else null,
                        )
                    }
                }
            }
        }
    }

    // ---- Batch settings ----

    fun setCopies(n: Int) {
        val v = n.coerceIn(StaffBookForm.COPIES_RANGE)
        saved[KEY_COPIES] = v
        _state.update { it.copy(copies = v) }
    }

    fun setShelf(id: Int?) {
        saved[KEY_SHELF] = id ?: 0
        saved[KEY_LEVEL] = 0
        _state.update { it.copy(shelfId = id, levelId = null) }
    }

    fun setLevel(id: Int?) {
        saved[KEY_LEVEL] = id ?: 0
        _state.update { it.copy(levelId = id) }
    }

    fun setAutoConfirm(on: Boolean) {
        saved[KEY_AUTO] = on
        _state.update { it.copy(autoConfirm = on) }
    }

    // ---- Sheet actions ----

    /** Preview sheet "Confirm": create the record with the batch copies and location. */
    fun confirm() {
        val s = _state.value
        val phase = s.phase as? ScanPhase.Preview ?: return
        if (s.saving) return
        _state.update { it.copy(saving = true) }
        val form = StaffBookForm.fromPreview(phase.preview, phase.identifier).copy(
            copies = s.copies,
            shelfId = s.shelfId.takeIf { s.levelId != null },
            levelId = s.levelId.takeIf { s.shelfId != null },
        )
        viewModelScope.launch {
            when (val res = staff.createBook(StaffBookWrite.create(form))) {
                is ApiResult.Success -> added(ScanAdded(res.data.id, res.data.title.ifBlank { form.title }, res.data.coverUrl ?: form.coverUrl, s.copies, true))
                is ApiResult.Failure -> when {
                    res.isForbidden() -> _state.update { it.copy(saving = false, forbidden = true) }
                    // Someone added it meanwhile (race with the lookup): offer the duplicate actions.
                    res.isDuplicate() -> _state.update {
                        it.copy(saving = false, phase = ScanPhase.Duplicate(phase.identifier, staff.existingBook(res) ?: ExistingBook(title = form.title)))
                    }
                    else -> _state.update {
                        it.copy(saving = false, message = res.serverMessage(), messageRes = R.string.staff_error_save)
                    }
                }
            }
        }
    }

    /** Duplicate sheet "Add copy": [quantity] more copies of the existing record. */
    fun addCopies(quantity: Int) {
        val s = _state.value
        val phase = s.phase as? ScanPhase.Duplicate ?: return
        if (s.saving || phase.existing.id <= 0) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            when (val res = staff.addCopies(phase.existing.id, quantity)) {
                is ApiResult.Success -> added(ScanAdded(phase.existing.id, phase.existing.title, null, quantity, false))
                is ApiResult.Failure -> _state.update {
                    if (res.isForbidden()) it.copy(saving = false, forbidden = true)
                    else it.copy(saving = false, message = res.serverMessage(), messageRes = R.string.staff_error_save)
                }
            }
        }
    }

    private fun added(entry: ScanAdded) {
        _state.update {
            it.copy(
                saving = false,
                phase = ScanPhase.Scanning,
                added = listOf(entry) + it.added,
                messageRes = R.string.staff_scan_added,
                message = null,
                acceptedTick = it.acceptedTick + 1,
            )
        }
        debouncer.resume()
    }

    /** Close the sheet without changes and keep scanning ("Skip"). */
    fun skip() {
        _state.update { it.copy(phase = ScanPhase.Scanning, saving = false) }
        debouncer.resume()
    }

    /** Scan the same code again right away ("Rescan", or retry after going offline). */
    fun rescan() {
        _state.update { it.copy(phase = ScanPhase.Scanning, saving = false) }
        debouncer.reset()
    }

    /** Pause decoding while the screen hands off to the edit form. */
    fun leaveForEdit() {
        _state.update { it.copy(phase = ScanPhase.Scanning, saving = false) }
        debouncer.resume()
    }

    fun consumeMessage() = _state.update { it.copy(message = null, messageRes = null) }

    private companion object {
        const val KEY_COPIES = "scan_copies"
        const val KEY_SHELF = "scan_shelf"
        const val KEY_LEVEL = "scan_level"
        const val KEY_AUTO = "scan_auto_confirm"
    }
}
