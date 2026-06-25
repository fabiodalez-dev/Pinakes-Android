package com.pinakes.app.ui.screens.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pinakes.app.R
import com.pinakes.app.data.network.ApiResult
import com.pinakes.app.data.repository.AuthRepository
import com.pinakes.app.ui.common.LocalServices
import com.pinakes.app.ui.components.PinakesTextButton
import com.pinakes.app.ui.components.PinakesTextField
import com.pinakes.app.ui.components.PrimaryButton
import com.pinakes.app.ui.theme.Spacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ForgotPasswordUiState(
    val email: String = "",
    val loading: Boolean = false,
    val sent: Boolean = false,
    val error: String? = null,
    val errorRes: Int? = null,
)

class ForgotPasswordViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(ForgotPasswordUiState())
    val state: StateFlow<ForgotPasswordUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) {
        _state.update { it.copy(email = value, error = null, errorRes = null) }
    }

    fun submit() {
        val email = _state.value.email.trim()
        if (email.isBlank()) {
            _state.update { it.copy(error = null, errorRes = R.string.auth_email_required) }
            return
        }
        _state.update { it.copy(loading = true, error = null, errorRes = null) }
        viewModelScope.launch {
            when (val res = auth.forgotPassword(email)) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, sent = true) }
                is ApiResult.Failure -> _state.update {
                    it.copy(
                        loading = false,
                        error = res.message.ifBlank { null },
                        errorRes = if (res.message.isBlank()) R.string.forgot_password_error else null,
                    )
                }
            }
        }
    }

    class Factory(private val auth: AuthRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ForgotPasswordViewModel(auth) as T
    }
}

@Composable
fun ForgotPasswordScreen(onBackToLogin: () -> Unit) {
    val services = LocalServices.current
    val vm: ForgotPasswordViewModel = viewModel(factory = ForgotPasswordViewModel.Factory(services.authRepository))
    val state by vm.state.collectAsStateWithLifecycle()
    val form = Modifier.fillMaxWidth().widthIn(max = 420.dp)
    val errorMessage = state.error ?: state.errorRes?.let { stringResource(it) }

    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.forgot_password_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = stringResource(R.string.forgot_password_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = form,
            )

            Spacer(Modifier.height(Spacing.xxl))

            if (state.sent) {
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer, modifier = form) {
                    Text(
                        text = stringResource(R.string.forgot_password_sent),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(Spacing.md),
                    )
                }
                Spacer(Modifier.height(Spacing.xl))
                PrimaryButton(
                    label = stringResource(R.string.auth_back_to_login),
                    onClick = onBackToLogin,
                    modifier = form,
                )
            } else {
                PinakesTextField(
                    value = state.email,
                    onValueChange = vm::onEmailChange,
                    label = stringResource(R.string.login_email_label),
                    modifier = form,
                    isError = errorMessage != null,
                    errorText = errorMessage.orEmpty(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { vm.submit() }),
                )
                Spacer(Modifier.height(Spacing.xl))
                PrimaryButton(
                    label = stringResource(R.string.forgot_password_action),
                    onClick = vm::submit,
                    modifier = form,
                    loading = state.loading,
                )
                Spacer(Modifier.height(Spacing.sm))
                PinakesTextButton(
                    label = stringResource(R.string.auth_back_to_login),
                    onClick = onBackToLogin,
                )
            }
        }
    }
}
