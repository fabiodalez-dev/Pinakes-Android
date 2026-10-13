package com.pinakes.app.ui.screens.profile

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pinakes.app.R
import com.pinakes.app.data.calendar.CalendarLinks
import com.pinakes.app.data.model.CalendarStatus
import com.pinakes.app.ui.common.DateFormat
import com.pinakes.app.ui.common.UiState
import com.pinakes.app.ui.common.resolvedMessage
import com.pinakes.app.ui.components.ConfirmDialog
import com.pinakes.app.ui.components.ErrorState
import com.pinakes.app.ui.components.LoadingState
import com.pinakes.app.ui.components.PinakesTextButton
import com.pinakes.app.ui.components.PrimaryButton
import com.pinakes.app.ui.components.SecondaryButton
import com.pinakes.app.ui.screens.bookclub.openWeb
import com.pinakes.app.ui.theme.Spacing

/**
 * Subscribe to "my loans" in a calendar app. Copying the link leads: Google Calendar on
 * Android cannot subscribe from the device (only from its website, "From URL"), while
 * calendar apps that understand `webcal://` subscribe directly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CalendarSubscribeSheet(
    calendar: UiState<CalendarStatus>,
    busy: Boolean,
    onEnable: () -> Unit,
    onRegenerate: () -> Unit,
    onDisable: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var confirmRegenerate by remember { mutableStateOf(false) }
    var confirmDisable by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(stringResource(R.string.calendar_subscribe_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.calendar_subscribe_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.xs))
            when (calendar) {
                is UiState.Loading -> LoadingState(modifier = Modifier.heightIn(max = 160.dp))
                is UiState.Error -> ErrorState(message = calendar.resolvedMessage(), onRetry = onRetry, modifier = Modifier.heightIn(max = 320.dp))
                is UiState.Success -> {
                    val status = calendar.data
                    val feedUrl = status.feedUrl?.takeIf { status.enabled && it.isNotBlank() }
                    if (feedUrl == null) {
                        Text(stringResource(R.string.calendar_status_disabled), style = MaterialTheme.typography.bodyMedium)
                        PrimaryButton(
                            stringResource(R.string.calendar_enable), onClick = onEnable, loading = busy,
                            leadingIcon = Icons.Outlined.CalendarMonth, modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                            SelectionContainer {
                                Text(feedUrl, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(Spacing.md))
                            }
                        }
                        (status.rotatedAt ?: status.createdAt)?.takeIf { it.isNotBlank() }?.let {
                            Text(stringResource(R.string.calendar_created_on, DateFormat.dateTime(it)), style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        PrimaryButton(
                            stringResource(R.string.calendar_copy_link), leadingIcon = Icons.Outlined.ContentCopy,
                            onClick = { copyLink(context, feedUrl) }, modifier = Modifier.fillMaxWidth(),
                        )
                        SecondaryButton(
                            stringResource(R.string.calendar_open_app), leadingIcon = Icons.AutoMirrored.Outlined.OpenInNew,
                            onClick = { openInCalendarApp(context, feedUrl, status.webcalUrl) }, modifier = Modifier.fillMaxWidth(),
                        )
                        Text(stringResource(R.string.calendar_hint_google), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(Spacing.xs))
                        PinakesTextButton(stringResource(R.string.calendar_regenerate), onClick = { confirmRegenerate = true }, enabled = !busy)
                        PinakesTextButton(stringResource(R.string.calendar_disable), onClick = { confirmDisable = true }, enabled = !busy)
                    }
                }
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }

    if (confirmRegenerate) {
        ConfirmDialog(
            title = stringResource(R.string.calendar_regenerate),
            body = stringResource(R.string.calendar_regenerate_confirm),
            confirmLabel = stringResource(R.string.calendar_regenerate),
            onConfirm = { confirmRegenerate = false; onRegenerate() },
            onDismiss = { confirmRegenerate = false },
        )
    }
    if (confirmDisable) {
        ConfirmDialog(
            title = stringResource(R.string.calendar_disable),
            body = stringResource(R.string.calendar_disable_confirm),
            confirmLabel = stringResource(R.string.calendar_disable),
            onConfirm = { confirmDisable = false; onDisable() },
            onDismiss = { confirmDisable = false },
        )
    }
}

private fun copyLink(context: Context, url: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.calendar_subscribe_title), url))
    Toast.makeText(context, context.getString(R.string.calendar_link_copied), Toast.LENGTH_SHORT).show()
}

/** `webcal://` first; without a calendar app that handles it, open the HTTPS link instead. */
private fun openInCalendarApp(context: Context, feedUrl: String, webcalUrl: String?) {
    val webcal = CalendarLinks.subscribeUrl(feedUrl, webcalUrl) ?: return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webcal)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        openWeb(context, feedUrl)
    } catch (_: SecurityException) {
        openWeb(context, feedUrl)
    }
}
