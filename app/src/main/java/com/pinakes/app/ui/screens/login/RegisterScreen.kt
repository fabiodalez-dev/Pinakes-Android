package com.pinakes.app.ui.screens.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Checkbox
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
import com.pinakes.app.ui.components.PasswordField
import com.pinakes.app.ui.components.PinakesTextButton
import com.pinakes.app.ui.components.PinakesTextField
import com.pinakes.app.ui.components.PrimaryButton
import com.pinakes.app.ui.theme.Spacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RegisterUiState(
    val nome: String = "",
    val cognome: String = "",
    val email: String = "",
    val telefono: String = "",
    val indirizzo: String = "",
    val password: String = "",
    val passwordConfirm: String = "",
    val privacyAccepted: Boolean = false,
    val loading: Boolean = false,
    val sent: Boolean = false,
    val error: String? = null,
    val errorRes: Int? = null,
)

class RegisterViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(RegisterUiState())
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    fun onNomeChange(value: String) = update { it.copy(nome = value) }
    fun onCognomeChange(value: String) = update { it.copy(cognome = value) }
    fun onEmailChange(value: String) = update { it.copy(email = value) }
    fun onTelefonoChange(value: String) = update { it.copy(telefono = value) }
    fun onIndirizzoChange(value: String) = update { it.copy(indirizzo = value) }
    fun onPasswordChange(value: String) = update { it.copy(password = value) }
    fun onPasswordConfirmChange(value: String) = update { it.copy(passwordConfirm = value) }
    fun onPrivacyChange(value: Boolean) = update { it.copy(privacyAccepted = value) }

    private fun update(block: (RegisterUiState) -> RegisterUiState) {
        _state.update { block(it).copy(error = null, errorRes = null) }
    }

    fun submit() {
        val s = _state.value
        val validation = when {
            s.nome.isBlank() || s.cognome.isBlank() || s.email.isBlank() ||
                s.telefono.isBlank() || s.indirizzo.isBlank() -> R.string.register_error_required
            // Mirror the backend (AuthController) rules so the user gets a clear
            // client-side error instead of a server 422: 8-72 chars, plus at least
            // one uppercase, one lowercase and one digit.
            s.password.length < 8 || s.password.length > 72 -> R.string.register_error_password_length
            !(s.password.any { it.isUpperCase() } && s.password.any { it.isLowerCase() } && s.password.any { it.isDigit() }) ->
                R.string.register_error_password_weak
            s.password != s.passwordConfirm -> R.string.profile_passwords_mismatch
            !s.privacyAccepted -> R.string.register_error_privacy
            else -> null
        }
        if (validation != null) {
            _state.update { it.copy(error = null, errorRes = validation) }
            return
        }

        _state.update { it.copy(loading = true, error = null, errorRes = null) }
        viewModelScope.launch {
            when (val res = auth.register(
                nome = s.nome,
                cognome = s.cognome,
                email = s.email,
                telefono = s.telefono,
                indirizzo = s.indirizzo,
                password = s.password,
                passwordConfirm = s.passwordConfirm,
                privacyAccepted = s.privacyAccepted,
            )) {
                is ApiResult.Success -> _state.update { it.copy(loading = false, sent = true) }
                is ApiResult.Failure -> _state.update {
                    it.copy(
                        loading = false,
                        error = res.message.ifBlank { null },
                        errorRes = if (res.message.isBlank()) R.string.register_error_generic else null,
                    )
                }
            }
        }
    }

    class Factory(private val auth: AuthRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            RegisterViewModel(auth) as T
    }
}

@Composable
fun RegisterScreen(onBackToLogin: () -> Unit) {
    val services = LocalServices.current
    val features by services.features.features.collectAsStateWithLifecycle()
    val vm: RegisterViewModel = viewModel(factory = RegisterViewModel.Factory(services.authRepository))
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
                text = stringResource(R.string.register_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = stringResource(R.string.register_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = form,
            )

            Spacer(Modifier.height(Spacing.xxl))

            if (!features.registrationEnabled) {
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = form) {
                    Text(
                        text = stringResource(R.string.register_disabled),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Spacing.md),
                    )
                }
                Spacer(Modifier.height(Spacing.xl))
                PrimaryButton(label = stringResource(R.string.auth_back_to_login), onClick = onBackToLogin, modifier = form)
                return@Column
            }

            if (state.sent) {
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer, modifier = form) {
                    Text(
                        text = stringResource(R.string.register_sent),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(Spacing.md),
                    )
                }
                Spacer(Modifier.height(Spacing.xl))
                PrimaryButton(label = stringResource(R.string.auth_back_to_login), onClick = onBackToLogin, modifier = form)
                return@Column
            }

            PinakesTextField(
                value = state.nome,
                onValueChange = vm::onNomeChange,
                label = stringResource(R.string.profile_first_name),
                modifier = form,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            Spacer(Modifier.height(Spacing.md))
            PinakesTextField(
                value = state.cognome,
                onValueChange = vm::onCognomeChange,
                label = stringResource(R.string.profile_last_name),
                modifier = form,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            Spacer(Modifier.height(Spacing.md))
            PinakesTextField(
                value = state.email,
                onValueChange = vm::onEmailChange,
                label = stringResource(R.string.login_email_label),
                modifier = form,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            )
            Spacer(Modifier.height(Spacing.md))
            PinakesTextField(
                value = state.telefono,
                onValueChange = vm::onTelefonoChange,
                label = stringResource(R.string.register_phone),
                modifier = form,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
            )
            Spacer(Modifier.height(Spacing.md))
            PinakesTextField(
                value = state.indirizzo,
                onValueChange = vm::onIndirizzoChange,
                label = stringResource(R.string.register_address),
                modifier = form,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            Spacer(Modifier.height(Spacing.md))
            PasswordField(
                value = state.password,
                onValueChange = vm::onPasswordChange,
                modifier = form,
                imeAction = ImeAction.Next,
            )
            Spacer(Modifier.height(Spacing.md))
            PasswordField(
                value = state.passwordConfirm,
                onValueChange = vm::onPasswordConfirmChange,
                label = stringResource(R.string.register_confirm_password),
                modifier = form,
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = { vm.submit() }),
            )
            Spacer(Modifier.height(Spacing.md))
            Row(modifier = form, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = state.privacyAccepted, onCheckedChange = vm::onPrivacyChange)
                Text(
                    text = stringResource(R.string.register_privacy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (errorMessage != null) {
                Spacer(Modifier.height(Spacing.md))
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.errorContainer, modifier = form) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(Spacing.md),
                    )
                }
            }

            Spacer(Modifier.height(Spacing.xl))
            PrimaryButton(
                label = stringResource(R.string.register_action),
                onClick = vm::submit,
                modifier = form,
                loading = state.loading,
            )
            Spacer(Modifier.height(Spacing.sm))
            PinakesTextButton(label = stringResource(R.string.auth_back_to_login), onClick = onBackToLogin)
        }
    }
}
