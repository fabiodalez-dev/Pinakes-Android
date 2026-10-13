package com.pinakes.app.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pinakes.app.data.model.BuiltinFieldRule
import com.pinakes.app.data.model.ProfileFieldRule
import com.pinakes.app.data.model.CalendarStatus
import com.pinakes.app.data.repository.CalendarRepository
import com.pinakes.app.data.model.DeviceItem
import com.pinakes.app.data.model.UpdateProfileRequest
import com.pinakes.app.data.model.UserProfile
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.network.ErrorCodes
import com.pinakes.app.data.repository.AuthRepository
import com.pinakes.app.data.repository.ProfileRepository
import com.pinakes.app.R
import com.pinakes.app.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val profile: UiState<UserProfile> = UiState.Loading,
    val devices: List<DeviceItem> = emptyList(),
    val devicesLoading: Boolean = false,
    // Edit dialog
    val editing: Boolean = false,
    val editNome: String = "",
    val editCognome: String = "",
    val editTelefono: String = "",
    val editIndirizzo: String = "",
    val editDataNascita: String = "",
    val editCodFiscale: String = "",
    val editSesso: String = "",
    // Custom-field edit values keyed by field id (checkbox → "1"/"").
    val editCustomValues: Map<Int, String> = emptyMap(),
    // Required-ness for telefono/indirizzo comes from the registration schema.
    val builtinFields: Map<String, BuiltinFieldRule> = emptyMap(),
    // Profile-only field visibility from the registration schema (gender, tax code).
    val profileFields: Map<String, ProfileFieldRule> = emptyMap(),
    // Inline validation error shown in the edit dialog (e.g. a required field
    // left blank) — surfaced client-side instead of relying on a server 422.
    val editErrorRes: Int? = null,
    val savingProfile: Boolean = false,
    // Password dialog
    val changingPassword: Boolean = false,
    val pwCurrent: String = "",
    val pwNew: String = "",
    val pwConfirm: String = "",
    val pwError: String? = null,
    val pwErrorRes: Int? = null,
    val savingPassword: Boolean = false,
    val loggingOut: Boolean = false,
    val snackbar: String? = null,
    val snackbarRes: Int? = null,
    // Personal loans calendar feed sheet (Mobile API 1.6.0).
    val calendarOpen: Boolean = false,
    val calendar: UiState<CalendarStatus> = UiState.Loading,
    val calendarBusy: Boolean = false,
) {
    private val hiddenFields: List<String> get() = (profile as? UiState.Success)?.data?.hiddenFields.orEmpty()

    /** Gender is shown unless the library hides it (`hidden_fields` or the schema). */
    val showGender: Boolean get() = ProfileFieldVisibility.isVisible(ProfileFieldVisibility.GENDER, hiddenFields, profileFields)

    /** Tax code is shown unless the library hides it. */
    val showTaxCode: Boolean get() = ProfileFieldVisibility.isVisible(ProfileFieldVisibility.TAX_CODE, hiddenFields, profileFields)

    /** Profile-side required-ness defaults to false until discovery succeeds. */
    fun builtinRequired(key: String): Boolean = builtinFields[key]?.required ?: false

    /**
     * Only built-in fields are required during profile editing. The server
     * deliberately does not enforce custom-field required flags on PATCH /me:
     * a field made mandatory after signup must not block an existing member
     * from editing unrelated profile data.
     */
    fun hasBlankRequiredProfileField(): Boolean =
        editNome.isBlank() ||
            (builtinRequired("cognome") && editCognome.isBlank()) ||
            (builtinRequired("telefono") && editTelefono.isBlank()) ||
            (builtinRequired("indirizzo") && editIndirizzo.isBlank())
}

internal val PROFILE_GENDER_VALUES = setOf("", "M", "F", "Altro")

internal fun isCanonicalProfileGender(value: String): Boolean = value in PROFILE_GENDER_VALUES

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profile: ProfileRepository,
    private val auth: AuthRepository,
    private val calendarRepo: CalendarRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init { load(); loadDevices(); loadSchema() }

    /** Fetch the registration schema so telefono/indirizzo required-ness matches the instance. */
    fun loadSchema() {
        viewModelScope.launch {
            when (val res = auth.registrationFields()) {
                is ApiResult.Success -> _state.update {
                    it.copy(builtinFields = res.data.builtinFields, profileFields = res.data.profileFields)
                }
                is ApiResult.Failure -> { /* keep defaults */ }
            }
        }
    }

    /**
     * Profile-side required-ness. Unlike registration (which defaults to
     * required-when-absent, the conservative signup choice), profile editing
     * defaults to NOT required when the registration schema hasn't loaded (or
     * failed): the user already has an account, so an unloaded schema must not
     * make an optional telefono/indirizzo suddenly block saving — the server
     * stays authoritative. Once the schema loads, the real flags apply.
     */
    fun builtinRequired(key: String): Boolean = _state.value.builtinRequired(key)

    fun load() {
        _state.update { it.copy(profile = UiState.Loading) }
        viewModelScope.launch {
            when (val res = profile.me()) {
                is ApiResult.Success -> {
                    // GET /me is authoritative for the role: keep the staff area in step.
                    auth.rememberRole(res.data.tipoUtente)
                    _state.update { it.copy(profile = UiState.Success(res.data)) }
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(profile = UiState.Error(res.message, res.code, R.string.profile_error_load))
                }
            }
        }
    }

    /** Re-read the profile without the loading state (on resume); failures keep what is shown. */
    fun refreshQuietly() {
        // The field-visibility settings may have changed on the server too.
        loadSchema()
        viewModelScope.launch {
            when (val res = profile.me()) {
                is ApiResult.Success -> {
                    auth.rememberRole(res.data.tipoUtente)
                    _state.update { it.copy(profile = UiState.Success(res.data)) }
                }
                is ApiResult.Failure -> Unit
            }
        }
    }

    fun loadDevices() {
        _state.update { it.copy(devicesLoading = true) }
        viewModelScope.launch {
            when (val res = profile.devices()) {
                is ApiResult.Success -> _state.update { it.copy(devices = res.data, devicesLoading = false) }
                is ApiResult.Failure -> _state.update { it.copy(devicesLoading = false) }
            }
        }
    }

    // ---- Edit profile ----
    fun startEdit() {
        val p = (_state.value.profile as? UiState.Success)?.data ?: return
        _state.update {
            it.copy(
                editing = true,
                editErrorRes = null,
                editNome = p.nome,
                editCognome = p.cognome,
                editTelefono = p.telefono.orEmpty(),
                editIndirizzo = p.indirizzo.orEmpty(),
                editDataNascita = p.dataNascita.orEmpty(),
                editCodFiscale = p.codFiscale.orEmpty(),
                editSesso = p.sesso.orEmpty(),
                editCustomValues = p.customFields.associate { f -> f.id to f.value },
            )
        }
    }
    fun cancelEdit() = _state.update { it.copy(editing = false) }
    fun onEditNome(v: String) = _state.update { it.copy(editNome = v, editErrorRes = null) }
    fun onEditCognome(v: String) = _state.update { it.copy(editCognome = v, editErrorRes = null) }
    fun onEditTelefono(v: String) = _state.update { it.copy(editTelefono = v, editErrorRes = null) }
    fun onEditIndirizzo(v: String) = _state.update { it.copy(editIndirizzo = v, editErrorRes = null) }
    fun onEditDataNascita(v: String) = _state.update { it.copy(editDataNascita = v) }
    fun onEditCodFiscale(v: String) = _state.update { it.copy(editCodFiscale = v) }
    fun onEditSesso(v: String) {
        if (!isCanonicalProfileGender(v)) return
        _state.update { it.copy(editSesso = v) }
    }
    fun onEditCustomField(id: Int, v: String) = _state.update { it.copy(editCustomValues = it.editCustomValues + (id to v), editErrorRes = null) }

    fun saveEdit() {
        val s = _state.value
        val original = (s.profile as? UiState.Success)?.data

        // Built-in fields follow the instance schema. Custom-field required
        // flags apply at signup only: the server intentionally permits blank
        // values on PATCH /me for existing users.
        if (s.hasBlankRequiredProfileField()) {
            _state.update { it.copy(editErrorRes = R.string.register_error_required) }
            return
        }

        // Build a partial PATCH: only include a built-in when it actually changed (null → omitted).
        fun changed(new: String, old: String?): String? = new.trim().takeIf { it != old.orEmpty() }
        // Custom fields: only the ids whose value differs from the loaded one.
        val customPatch: Map<String, String> = original?.customFields
            ?.mapNotNull { def ->
                val edited = s.editCustomValues[def.id].orEmpty()
                val normalized = if (def.type == "checkbox") (if (edited == "1") "1" else "") else edited.trim()
                if (normalized != def.value) def.id.toString() to normalized else null
            }
            ?.toMap()
            .orEmpty()

        val request = UpdateProfileRequest(
            nome = changed(s.editNome, original?.nome),
            cognome = changed(s.editCognome, original?.cognome),
            telefono = changed(s.editTelefono, original?.telefono),
            indirizzo = changed(s.editIndirizzo, original?.indirizzo),
            dataNascita = changed(s.editDataNascita, original?.dataNascita),
            // A field the library hides is never sent (the server would ignore it anyway).
            codFiscale = if (s.showTaxCode) changed(s.editCodFiscale, original?.codFiscale) else null,
            sesso = if (s.showGender) changed(s.editSesso, original?.sesso) else null,
            customFields = customPatch.takeIf { it.isNotEmpty() },
        )

        _state.update { it.copy(savingProfile = true) }
        viewModelScope.launch {
            when (val res = profile.updateProfile(request)) {
                is ApiResult.Success -> _state.update {
                    it.copy(profile = UiState.Success(res.data), savingProfile = false, editing = false, snackbar = null, snackbarRes = R.string.profile_updated)
                }
                is ApiResult.Failure -> _state.update {
                    if (res.message.isNotBlank()) it.copy(savingProfile = false, snackbar = res.message, snackbarRes = null)
                    else it.copy(savingProfile = false, snackbar = null, snackbarRes = R.string.profile_save_error)
                }
            }
        }
    }

    // ---- Change password ----
    fun startChangePassword() = _state.update {
        it.copy(changingPassword = true, pwCurrent = "", pwNew = "", pwConfirm = "", pwError = null, pwErrorRes = null)
    }
    fun cancelChangePassword() = _state.update { it.copy(changingPassword = false) }
    fun onPwCurrent(v: String) = _state.update { it.copy(pwCurrent = v, pwError = null, pwErrorRes = null) }
    fun onPwNew(v: String) = _state.update { it.copy(pwNew = v, pwError = null, pwErrorRes = null) }
    fun onPwConfirm(v: String) = _state.update { it.copy(pwConfirm = v, pwError = null, pwErrorRes = null) }

    fun savePassword() {
        val s = _state.value
        when {
            s.pwNew.length < 8 -> { _state.update { it.copy(pwError = null, pwErrorRes = R.string.profile_password_too_short) }; return }
            s.pwNew != s.pwConfirm -> { _state.update { it.copy(pwError = null, pwErrorRes = R.string.profile_passwords_mismatch) }; return }
        }
        _state.update { it.copy(savingPassword = true, pwError = null, pwErrorRes = null) }
        viewModelScope.launch {
            when (val res = profile.changePassword(s.pwCurrent, s.pwNew, s.pwConfirm)) {
                is ApiResult.Success -> _state.update {
                    it.copy(savingPassword = false, changingPassword = false, snackbar = null, snackbarRes = R.string.profile_password_changed)
                }
                is ApiResult.Failure -> {
                    when {
                        res.code == ErrorCodes.INVALID_CREDENTIALS || res.code == ErrorCodes.VALIDATION ->
                            _state.update { it.copy(savingPassword = false, pwError = null, pwErrorRes = R.string.profile_current_password_incorrect) }
                        res.message.isNotBlank() ->
                            _state.update { it.copy(savingPassword = false, pwError = res.message, pwErrorRes = null) }
                        else ->
                            _state.update { it.copy(savingPassword = false, pwError = null, pwErrorRes = R.string.profile_change_password_error) }
                    }
                }
            }
        }
    }

    fun revokeDevice(id: Int) {
        viewModelScope.launch {
            when (profile.revokeDevice(id)) {
                is ApiResult.Success -> { _state.update { it.copy(snackbar = null, snackbarRes = R.string.profile_device_signed_out) }; loadDevices() }
                is ApiResult.Failure -> _state.update { it.copy(snackbar = null, snackbarRes = R.string.profile_device_sign_out_error) }
            }
        }
    }

    fun logout(onDone: () -> Unit) {
        _state.update { it.copy(loggingOut = true) }
        viewModelScope.launch {
            auth.logout() // clears token regardless of result
            _state.update { it.copy(loggingOut = false) }
            onDone()
        }
    }

    fun consumeSnackbar() = _state.update { it.copy(snackbar = null, snackbarRes = null) }

    // ---- Calendar subscription ----

    fun openCalendar() {
        _state.update { it.copy(calendarOpen = true, calendar = UiState.Loading) }
        viewModelScope.launch {
            val res = calendarRepo.status()
            _state.update { it.copy(calendar = res.toCalendarState()) }
        }
    }

    fun closeCalendar() = _state.update { it.copy(calendarOpen = false) }

    /** Create the feed, or rotate it: the old URL stops working immediately. */
    fun rotateCalendar() = calendarAction(R.string.calendar_link_ready) { calendarRepo.rotate() }

    fun disableCalendar() = calendarAction(R.string.calendar_disabled) { calendarRepo.revoke() }

    private fun calendarAction(successRes: Int, call: suspend () -> ApiResult<CalendarStatus>) {
        if (_state.value.calendarBusy) return
        _state.update { it.copy(calendarBusy = true) }
        viewModelScope.launch {
            when (val res = call()) {
                is ApiResult.Success -> _state.update {
                    it.copy(calendarBusy = false, calendar = UiState.Success(res.data), snackbarRes = successRes, snackbar = null)
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(calendarBusy = false, snackbar = res.message.ifBlank { null }, snackbarRes = if (res.message.isBlank()) R.string.calendar_error else null)
                }
            }
        }
    }

    private fun ApiResult<CalendarStatus>.toCalendarState(): UiState<CalendarStatus> = when (this) {
        is ApiResult.Success -> UiState.Success(data)
        is ApiResult.Failure -> UiState.Error(message, code, R.string.calendar_error)
    }
}
