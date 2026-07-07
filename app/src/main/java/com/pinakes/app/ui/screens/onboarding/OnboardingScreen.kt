package com.pinakes.app.ui.screens.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pinakes.app.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import com.pinakes.app.data.repository.HealthDiscovery
import com.pinakes.app.ui.components.PrimaryButton
import com.pinakes.app.ui.components.UrlField
import com.pinakes.app.ui.theme.AvailableOnContainerDark
import com.pinakes.app.ui.theme.AvailableOnContainerLight
import com.pinakes.app.ui.theme.Spacing

/**
 * Minimal onboarding: centered column on a plain surface, bare logo, a readable URL field, and
 * the discovery result as a clean card. Solid magenta Continue. No gradient panel. (DESIGN.md.)
 */
@Composable
fun OnboardingScreen(onContinue: () -> Unit) {
    val vm: OnboardingViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()

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
                modifier = Modifier.height(52.dp),
            )

            Spacer(Modifier.height(Spacing.xl))

            Text(
                text = stringResource(R.string.onboarding_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = stringResource(R.string.onboarding_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(Spacing.xxl))

            val errorMessage = state.error ?: state.errorRes?.let { stringResource(it) }
            UrlField(
                value = state.url,
                onValueChange = vm::onUrlChange,
                label = stringResource(R.string.onboarding_url_label),
                modifier = form,
                isError = errorMessage != null,
                errorText = errorMessage.orEmpty(),
                onDone = vm::discover,
            )

            Spacer(Modifier.height(Spacing.lg))

            if (state.discovery == null) {
                Row(
                    // Toggle from the whole row (not just the switch): a screen reader
                    // announces the label together with the on/off state, and the larger
                    // touch target is easier to hit. onCheckedChange = null makes the Switch
                    // a passive indicator of the row's toggle state.
                    modifier = form.toggleable(
                        value = state.allowInsecureHttp,
                        role = Role.Switch,
                        onValueChange = vm::onAllowInsecureChange,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.onboarding_allow_http_label),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            stringResource(R.string.onboarding_allow_http_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(Spacing.md))
                    Switch(
                        checked = state.allowInsecureHttp,
                        onCheckedChange = null,
                    )
                }

                Spacer(Modifier.height(Spacing.lg))

                PrimaryButton(
                    label = stringResource(R.string.action_discover_library),
                    onClick = vm::discover,
                    modifier = form,
                    loading = state.checking,
                )
            }

            state.discovery?.let { d ->
                Spacer(Modifier.height(Spacing.sm))
                DiscoveryCard(d, modifier = form)
                Spacer(Modifier.height(Spacing.lg))
                val canContinue = d.health.appAccessEnabled && d.transportAllowed
                PrimaryButton(
                    label = stringResource(R.string.action_continue),
                    onClick = { if (vm.confirm()) onContinue() },
                    modifier = form,
                    enabled = canContinue,
                )
                if (!d.transportAllowed) {
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        stringResource(R.string.onboarding_http_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.xxxl))
        }
    }
}

@Composable
private fun DiscoveryCard(d: HealthDiscovery, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier,
    ) {
        Column(Modifier.padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(48.dp),
                ) {
                    if (d.health.logo != null) {
                        SubcomposeAsyncImage(
                            model = d.health.logo,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.clip(CircleShape),
                        )
                    } else {
                        Column(
                            Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                d.health.name.take(1).uppercase(),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        d.health.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        d.origin,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (d.health.version.isNotBlank()) {
                        Text(
                            stringResource(R.string.onboarding_version_prefix, d.health.version),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.md))

            StatusLine(
                ok = !d.insecureTransport,
                okText = stringResource(R.string.onboarding_status_secure_ok),
                warnText = stringResource(R.string.onboarding_status_secure_warn),
            )
            StatusLine(
                ok = d.health.appAccessEnabled,
                okText = stringResource(R.string.onboarding_status_app_access_ok),
                warnText = stringResource(R.string.onboarding_status_app_access_warn),
            )
        }
    }
}

@Composable
private fun StatusLine(ok: Boolean, okText: String, warnText: String) {
    Row(
        modifier = Modifier.padding(vertical = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // "secure / app-access OK" is a positive status → availability-green token (the only
        // extra semantic colour allowed). Warnings use the error role.
        val okTint = if (isSystemInDarkTheme()) AvailableOnContainerDark else AvailableOnContainerLight
        val tint = if (ok) okTint else MaterialTheme.colorScheme.error
        Icon(
            imageVector = if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.Warning,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = if (ok) okText else warnText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
