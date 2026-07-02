package com.pinakes.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pinakes.app.R
import com.pinakes.app.ui.theme.Spacing

// ---------------------------------------------------------------------------
// SectionHeader
// ---------------------------------------------------------------------------

@Composable
fun SectionHeader(
    title: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (actionLabel != null) {
            PinakesTextButton(label = actionLabel, onClick = onAction)
        }
    }
}

// ---------------------------------------------------------------------------
// MetadataRow
// ---------------------------------------------------------------------------

/**
 * A single label + value row for the book-detail metadata block. Label sits in a fixed-width
 * column (medium weight, onSurfaceVariant) so the values align into a clean key/value list;
 * the value reads in onSurface. Comfortable vertical rhythm — not a cramped grey blob. (DESIGN.md.)
 */
@Composable
fun MetadataRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(116.dp),
        )
        Text(
            text = value,
            // High-contrast value: the primary onSurface colour + a slightly
            // larger, medium-weight body so metadata reads clearly at a glance.
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

// ---------------------------------------------------------------------------
// FilterChipRow
// ---------------------------------------------------------------------------

data class FilterOption<T>(val id: T, val label: String)

@Composable
fun <T> FilterChipRow(
    options: List<FilterOption<T>>,
    selectedIds: Set<T>,
    onToggle: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(options) { option ->
            FilterChip(
                selected = option.id in selectedIds,
                onClick = { onToggle(option.id) },
                label = {
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// ConfirmDialog
// ---------------------------------------------------------------------------

@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String? = null,
    dismissLabel: String? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
        },
        text = {
            Text(text = body, style = MaterialTheme.typography.bodyMedium)
        },
        confirmButton = {
            PrimaryButton(label = confirmLabel ?: stringResource(R.string.action_confirm), onClick = onConfirm)
        },
        dismissButton = {
            PinakesTextButton(label = dismissLabel ?: stringResource(R.string.action_cancel), onClick = onDismiss)
        },
    )
}
