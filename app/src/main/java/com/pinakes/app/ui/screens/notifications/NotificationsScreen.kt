package com.pinakes.app.ui.screens.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pinakes.app.R
import com.pinakes.app.data.model.NotificationItem
import com.pinakes.app.ui.common.DateFormat
import com.pinakes.app.ui.common.LocalServices
import com.pinakes.app.ui.common.UiState
import com.pinakes.app.ui.common.resolvedMessage
import com.pinakes.app.ui.components.EmptyState
import com.pinakes.app.ui.components.ErrorState
import com.pinakes.app.ui.components.LoadingState
import com.pinakes.app.ui.components.PinakesTopBar
import com.pinakes.app.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(onNavigateUp: () -> Unit) {
    val services = LocalServices.current
    val features by services.features.features.collectAsStateWithLifecycle()
    if (!features.notifications) {
        Scaffold(
            topBar = { PinakesTopBar(title = stringResource(R.string.title_notifications), onNavigateUp = onNavigateUp) },
        ) { padding ->
            EmptyState(
                title = stringResource(R.string.notifications_disabled_title),
                subtitle = stringResource(R.string.notifications_disabled_subtitle),
                icon = Icons.Outlined.NotificationsNone,
                modifier = Modifier.padding(padding),
            )
        }
        return
    }

    val vm: NotificationsViewModel = viewModel(factory = NotificationsViewModel.Factory(services.notificationsRepository))
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { PinakesTopBar(title = stringResource(R.string.title_notifications), onNavigateUp = onNavigateUp) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = vm::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when (val content = state.content) {
                is UiState.Loading -> LoadingState(label = stringResource(R.string.notifications_loading))
                is UiState.Error -> ErrorState(message = content.resolvedMessage(), onRetry = vm::refresh)
                is UiState.Success -> {
                    if (content.data.isEmpty()) {
                        EmptyState(
                            title = stringResource(R.string.notifications_empty_title),
                            subtitle = stringResource(R.string.notifications_empty_subtitle),
                            icon = Icons.Outlined.NotificationsNone,
                        )
                    } else {
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(Spacing.lg),
                            verticalArrangement = Arrangement.spacedBy(Spacing.md),
                        ) {
                            items(content.data, key = { it.id }) { n -> NotificationRow(n) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(n: NotificationItem) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (n.read) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(Spacing.md)) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = iconFor(n.type),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(n.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                if (n.message.isNotBlank()) {
                    Text(n.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                n.date?.let { date ->
                    Spacer(Modifier.size(Spacing.xs))
                    Text(DateFormat.dateTime(date), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun iconFor(type: String): ImageVector = when (type) {
    "loan_due" -> Icons.Outlined.EventAvailable
    "loan_overdue" -> Icons.Outlined.WarningAmber
    "reservation_ready" -> Icons.Outlined.CheckCircle
    "new_message" -> Icons.Outlined.MailOutline
    "book_available" -> Icons.AutoMirrored.Outlined.MenuBook
    else -> Icons.Outlined.NotificationsNone
}
