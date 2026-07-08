package com.pinakes.app.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.network.ErrorCodes
import com.pinakes.app.data.repository.AuthRepository
import com.pinakes.app.data.store.SessionStore
import com.pinakes.app.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val errorRes: Int? = null,
    val errorArg: Long? = null,
    val libraryName: String = "Pinakes",
    val instanceOrigin: String = "",
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val auth: AuthRepository,
    session: SessionStore,
) : ViewModel() {

    private val _state = MutableStateFlow(
        LoginUiState(
            libraryName = session.libraryName ?: "Pinakes",
            instanceOrigin = session.instanceOrigin.orEmpty(),
        )
    )
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { auth.refreshHealth() }
    }

    fun onEmailChange(v: String) = _state.update { it.copy(email = v, error = null, errorRes = null, errorArg = null) }
    fun onPasswordChange(v: String) = _state.update { it.copy(password = v, error = null, errorRes = null, errorArg = null) }

    fun login(onSuccess: () -> Unit) {
        val s = _state.value
        if (s.email.isBlank() || s.password.isBlank()) {
            _state.update { it.copy(error = null, errorRes = R.string.login_error_empty, errorArg = null) }
            return
        }
        _state.update { it.copy(loading = true, error = null, errorRes = null, errorArg = null) }
        viewModelScope.launch {
            when (val res = auth.login(s.email, s.password)) {
                is ApiResult.Success -> {
                    // Pick up the latest feature flags / catalogue mode for this session.
                    auth.refreshHealth()
                    _state.update { it.copy(loading = false) }
                    onSuccess()
                }
                is ApiResult.Failure ->
                    _state.update { applyError(it.copy(loading = false), res) }
            }
        }
    }

    /** Forget the instance — back to onboarding (e.g. "wrong library"). */
    fun changeLibrary(onDone: () -> Unit) {
        viewModelScope.launch {
            auth.forgetInstance()
            onDone()
        }
    }

    private fun applyError(state: LoginUiState, failure: ApiResult.Failure): LoginUiState = when (failure.code) {
        ErrorCodes.INVALID_CREDENTIALS -> state.copy(error = null, errorRes = R.string.login_error_invalid_credentials, errorArg = null)
        ErrorCodes.APP_ACCESS_DISABLED -> state.copy(error = null, errorRes = R.string.login_error_app_disabled, errorArg = null)
        ErrorCodes.RATE_LIMITED -> {
            val s = failure.retryAfterSeconds
            if (s != null) state.copy(error = null, errorRes = R.string.login_error_rate_limited_seconds, errorArg = s)
            else state.copy(error = null, errorRes = R.string.login_error_rate_limited, errorArg = null)
        }
        ErrorCodes.NETWORK -> state.copy(error = null, errorRes = R.string.login_error_network, errorArg = null)
        ErrorCodes.TLS -> state.copy(error = null, errorRes = R.string.login_error_tls, errorArg = null)
        ErrorCodes.FORBIDDEN -> state.copy(error = null, errorRes = R.string.login_error_forbidden, errorArg = null)
        else ->
            if (failure.message.isNotBlank()) state.copy(error = failure.message, errorRes = null, errorArg = null)
            else state.copy(error = null, errorRes = R.string.login_error_generic, errorArg = null)
    }
}
