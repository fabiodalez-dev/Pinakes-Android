package com.pinakes.app.ui.screens.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pinakes.app.R
import com.pinakes.app.ui.common.LocalServices
import com.pinakes.app.ui.components.PasswordField
import com.pinakes.app.ui.components.PinakesTextButton
import com.pinakes.app.ui.components.PinakesTextField
import com.pinakes.app.ui.components.PrimaryButton
import com.pinakes.app.ui.theme.Spacing

/**
 * Minimal login: a vertically centered column on a plain surface. Bare magenta logo, a small
 * title, two clearly readable fields, a solid magenta sign-in button, and a quiet "use a
 * different library" text button. No gradient, no colored panel. (DESIGN.md.)
 */
@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    onChangeLibrary: () -> Unit,
    onRegister: () -> Unit,
    onForgotPassword: () -> Unit,
) {
    val services = LocalServices.current
    val vm: LoginViewModel = viewModel(
        factory = LoginViewModel.Factory(services.authRepository, services.session)
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val features by services.features.features.collectAsStateWithLifecycle()

    val errorMessage = state.error ?: state.errorRes?.let { res ->
        if (state.errorArg != null) stringResource(res, state.errorArg!!) else stringResource(res)
    }

    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val form = Modifier.fillMaxWidth().widthIn(max = 420.dp)

            Spacer(Modifier.height(Spacing.xxxl))

            // Bare logo — no circle, ring, or card.
            Image(
                painter = painterResource(R.drawable.brand_logo),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.height(48.dp),
            )

            Spacer(Modifier.height(Spacing.xxl))

            Text(
                text = state.libraryName.ifBlank { stringResource(R.string.login_welcome_back) },
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            if (state.instanceOrigin.isNotBlank()) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = state.instanceOrigin,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(Spacing.xxl))

            PinakesTextField(
                value = state.email,
                onValueChange = vm::onEmailChange,
                label = stringResource(R.string.login_email_label),
                modifier = form,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
            )
            Spacer(Modifier.height(Spacing.md))
            PasswordField(
                value = state.password,
                onValueChange = vm::onPasswordChange,
                modifier = form,
                isError = errorMessage != null,
                imeAction = ImeAction.Done,
                keyboardActions = KeyboardActions(onDone = { vm.login(onLoggedIn) }),
            )

            if (errorMessage != null) {
                Spacer(Modifier.height(Spacing.md))
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = form,
                ) {
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
                label = stringResource(R.string.login_action_sign_in),
                onClick = { vm.login(onLoggedIn) },
                modifier = form,
                loading = state.loading,
            )
            Spacer(Modifier.height(Spacing.sm))
            Box(modifier = form, contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PinakesTextButton(
                        label = stringResource(R.string.login_forgot_password),
                        onClick = onForgotPassword,
                    )
                    if (features.registrationEnabled) {
                        PinakesTextButton(
                            label = stringResource(R.string.login_create_account),
                            onClick = onRegister,
                        )
                    }
                }
            }
            Spacer(Modifier.height(Spacing.sm))
            Box(modifier = form, contentAlignment = Alignment.Center) {
                PinakesTextButton(
                    label = stringResource(R.string.login_use_different_library),
                    onClick = { vm.changeLibrary(onChangeLibrary) },
                )
            }
            Spacer(Modifier.height(Spacing.xxxl))
        }
    }
}
