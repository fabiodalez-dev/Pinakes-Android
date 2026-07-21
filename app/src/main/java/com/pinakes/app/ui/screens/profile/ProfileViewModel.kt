package com.pinakes.app.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pinakes.app.data.model.BuiltinFieldRule
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
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profile: ProfileRepository,
    private val auth: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init { load(); loadDevices(); loadSchema() }

    /** Fetch the registration schema so telefono/indirizzo required-ness matches the instance. */
    fun loadSchema() {
        viewModelScope.launch {
            when (val res = auth.registrationFields()) {
                is ApiResult.Success -> _state.update { it.copy(builtinFields = res.data.builtinFields) }
                is ApiResult.Failure -> { /* keep defaults */ }
            }
        }
    }

    fun builtinRequired(key: String): Boolean = _state.value.builtinFields[key]?.required ?: true

    fun load() {
        _state.update { it.copy(profile = UiState.Loading) }
        viewModelScope.launch {
            when (val res = profile.me()) {
                is ApiResult.Success -> _state.update { it.copy(profile = UiState.Success(res.data)) }
                is ApiResult.Failure -> _state.update {
                    it.copy(profile = UiState.Error(res.message, res.code, R.string.profile_error_load))
                }
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
    fun onEditNome(v: String) = _state.update { it.copy(editNome = v) }
    fun onEditCognome(v: String) = _state.update { it.copy(editCognome = v) }
    fun onEditTelefono(v: String) = _state.update { it.copy(editTelefono = v, editErrorRes = null) }
    fun onEditIndirizzo(v: String) = _state.update { it.copy(editIndirizzo = v, editErrorRes = null) }
    fun onEditDataNascita(v: String) = _state.update { it.copy(editDataNascita = v) }
    fun onEditCodFiscale(v: String) = _state.update { it.copy(editCodFiscale = v) }
    fun onEditSesso(v: String) = _state.update { it.copy(editSesso = v) }
    fun onEditCustomField(id: Int, v: String) = _state.update { it.copy(editCustomValues = it.editCustomValues + (id to v), editErrorRes = null) }

    fun saveEdit() {
        val s = _state.value
        val original = (s.profile as? UiState.Success)?.data

        // Client-side required-field validation, mirroring the register flow, so
        // clearing a required field gives an immediate, clear message instead of
        // an opaque server 422 round-trip. telefono/indirizzo are required only
        // when the instance requires them; required custom fields must be filled.
        val missingBuiltin =
            (builtinRequired("telefono") && s.editTelefono.isBlank()) ||
                (builtinRequired("indirizzo") && s.editIndirizzo.isBlank())
        val missingCustom = original?.customFields?.any { def ->
            val v = s.editCustomValues[def.id].orEmpty()
            val normalized = if (def.type == "checkbox") (if (v == "1") "1" else "") else v.trim()
            def.required && normalized.isEmpty()
        } ?: false
        if (missingBuiltin || missingCustom) {
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
            codFiscale = changed(s.editCodFiscale, original?.codFiscale),
            sesso = changed(s.editSesso, original?.sesso),
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
}
