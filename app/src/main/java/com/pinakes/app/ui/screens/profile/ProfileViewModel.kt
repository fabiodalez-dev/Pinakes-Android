package com.pinakes.app.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pinakes.app.data.model.DeviceItem
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

    init { load(); loadDevices() }

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
        _state.update { it.copy(editing = true, editNome = p.nome, editCognome = p.cognome) }
    }
    fun cancelEdit() = _state.update { it.copy(editing = false) }
    fun onEditNome(v: String) = _state.update { it.copy(editNome = v) }
    fun onEditCognome(v: String) = _state.update { it.copy(editCognome = v) }

    fun saveEdit() {
        val s = _state.value
        _state.update { it.copy(savingProfile = true) }
        viewModelScope.launch {
            when (val res = profile.updateProfile(s.editNome.trim(), s.editCognome.trim())) {
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
