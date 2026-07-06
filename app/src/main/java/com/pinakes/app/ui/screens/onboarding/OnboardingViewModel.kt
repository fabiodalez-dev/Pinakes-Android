package com.pinakes.app.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.repository.AuthRepository
import com.pinakes.app.data.repository.HealthDiscovery
import com.pinakes.app.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val url: String = "",
    val checking: Boolean = false,
    val discovery: HealthDiscovery? = null,
    val error: String? = null,
    val errorRes: Int? = null,
    /** User opt-in: allow a plain-HTTP instance (off by default, they own the risk). */
    val allowInsecureHttp: Boolean = false,
)

/**
 * Onboarding: the user types an instance URL, we call `/health` and surface the library
 * identity + transport/app-access warnings before they continue.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(private val auth: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun onUrlChange(value: String) {
        _state.update { it.copy(url = value, error = null, errorRes = null, discovery = null) }
    }

    /** Toggle the "allow insecure HTTP" opt-in; clears any stale discovery so the next
     *  probe re-derives the scheme (http vs https) from the new choice. */
    fun onAllowInsecureChange(value: Boolean) {
        _state.update { it.copy(allowInsecureHttp = value, error = null, errorRes = null, discovery = null) }
    }

    fun discover() {
        val url = _state.value.url.trim()
        if (url.isBlank()) {
            _state.update { it.copy(error = null, errorRes = R.string.onboarding_error_empty) }
            return
        }
        _state.update { it.copy(checking = true, error = null, errorRes = null, discovery = null) }
        viewModelScope.launch {
            when (val res = auth.discover(url, _state.value.allowInsecureHttp)) {
                is ApiResult.Success ->
                    _state.update { it.copy(checking = false, discovery = res.data, error = null, errorRes = null) }
                is ApiResult.Failure ->
                    _state.update { applyError(it.copy(checking = false, discovery = null), res) }
            }
        }
    }

    /** Persist the instance so the app advances to login. Returns false if not connectable. */
    fun confirm(): Boolean {
        val d = _state.value.discovery ?: return false
        if (!d.health.appAccessEnabled || !d.transportAllowed) return false
        auth.commitInstance(d)
        return true
    }

    private fun applyError(state: OnboardingUiState, failure: ApiResult.Failure): OnboardingUiState = when (failure.code) {
        "network" -> state.copy(error = null, errorRes = R.string.onboarding_error_network)
        "not_found" -> state.copy(error = null, errorRes = R.string.onboarding_error_not_found)
        else ->
            if (failure.message.isNotBlank()) state.copy(error = failure.message, errorRes = null)
            else state.copy(error = null, errorRes = R.string.onboarding_error_generic)
    }
}
