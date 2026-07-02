package com.pinakes.app.ui.screens.contact

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pinakes.app.R
import com.pinakes.app.ui.common.AppViewModel
import com.pinakes.app.ui.components.EmptyState
import com.pinakes.app.ui.components.PinakesTextField
import com.pinakes.app.ui.components.PinakesTopBar
import com.pinakes.app.ui.components.PrimaryButton
import com.pinakes.app.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactScreen(onNavigateUp: () -> Unit) {
    val app: AppViewModel = hiltViewModel()
    val features by app.features.collectAsStateWithLifecycle()
    if (!features.messages) {
        Scaffold(
            topBar = { PinakesTopBar(title = stringResource(R.string.title_message_library), onNavigateUp = onNavigateUp) },
        ) { padding ->
            EmptyState(
                title = stringResource(R.string.contact_disabled_title),
                subtitle = stringResource(R.string.contact_disabled_subtitle),
                icon = Icons.Outlined.MarkEmailRead,
                modifier = Modifier.padding(padding),
            )
        }
        return
    }

    val vm: ContactViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { PinakesTopBar(title = stringResource(R.string.title_message_library), onNavigateUp = onNavigateUp) },
    ) { padding ->
        if (state.sent) {
            EmptyState(
                title = stringResource(R.string.contact_sent_title),
                subtitle = stringResource(R.string.contact_sent_subtitle),
                icon = Icons.Outlined.MarkEmailRead,
                actionLabel = stringResource(R.string.action_done),
                onAction = onNavigateUp,
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
        ) {
            Text(
                stringResource(R.string.contact_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.lg))
            PinakesTextField(
                value = state.subject,
                onValueChange = vm::onSubject,
                label = stringResource(R.string.contact_subject_label),
                modifier = Modifier.fillMaxWidth(),
                maxLength = 255,
            )
            Spacer(Modifier.height(Spacing.md))
            PinakesTextField(
                value = state.body,
                onValueChange = vm::onBody,
                label = stringResource(R.string.contact_message_label),
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                singleLine = false,
                maxLength = 5000,
            )

            val errorMessage = state.error ?: state.errorRes?.let { stringResource(it) }
            if (errorMessage != null) {
                Spacer(Modifier.height(Spacing.md))
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(Spacing.md),
                    )
                }
            }

            Spacer(Modifier.height(Spacing.xl))
            PrimaryButton(
                label = stringResource(R.string.contact_send),
                onClick = vm::send,
                modifier = Modifier.fillMaxWidth(),
                loading = state.sending,
                enabled = state.canSend,
            )
        }
    }
}
