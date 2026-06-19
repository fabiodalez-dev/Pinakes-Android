package com.pinakes.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pinakes.app.R
import com.pinakes.app.ui.theme.AvailableContainerDark
import com.pinakes.app.ui.theme.AvailableContainerLight
import com.pinakes.app.ui.theme.AvailableOnContainerDark
import com.pinakes.app.ui.theme.AvailableOnContainerLight
import com.pinakes.app.ui.theme.DueSoonContainerDark
import com.pinakes.app.ui.theme.DueSoonContainerLight
import com.pinakes.app.ui.theme.DueSoonOnContainerDark
import com.pinakes.app.ui.theme.DueSoonOnContainerLight

enum class AvailabilityStatus {
    Available,     // available-green tint (also on-loan-on-time)
    Unavailable,   // uses error color role
    LoanActive,    // uses secondary color role
    DueSoon,       // amber tint (pending approval / damaged)
    Overdue,       // uses error color role (RED) — overdue / lost
    ReservedReady, // uses tertiary color role (magenta family) — ready for pickup
    Scheduled,     // neutral mauve-grey (secondaryContainer) — scheduled / future reservation
    Returned,      // neutral grey (surfaceVariant) — returned / expired / cancelled
    Digital,       // uses primary color role
}

data class ChipColors(val container: Color, val onContainer: Color)

@Composable
fun AvailabilityChip(
    status: AvailabilityStatus,
    label: String? = null,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val colorScheme = MaterialTheme.colorScheme

    val (container, onContainer) = when (status) {
        AvailabilityStatus.Available ->
            if (dark) ChipColors(AvailableContainerDark, AvailableOnContainerDark)
            else      ChipColors(AvailableContainerLight, AvailableOnContainerLight)
        AvailabilityStatus.Unavailable, AvailabilityStatus.Overdue ->
            ChipColors(colorScheme.errorContainer, colorScheme.onErrorContainer)
        AvailabilityStatus.LoanActive ->
            ChipColors(colorScheme.secondaryContainer, colorScheme.onSecondaryContainer)
        AvailabilityStatus.DueSoon ->
            if (dark) ChipColors(DueSoonContainerDark, DueSoonOnContainerDark)
            else      ChipColors(DueSoonContainerLight, DueSoonOnContainerLight)
        AvailabilityStatus.ReservedReady ->
            ChipColors(colorScheme.tertiaryContainer, colorScheme.onTertiaryContainer)
        AvailabilityStatus.Scheduled ->
            ChipColors(colorScheme.secondaryContainer, colorScheme.onSecondaryContainer)
        AvailabilityStatus.Returned ->
            ChipColors(colorScheme.surfaceVariant, colorScheme.onSurfaceVariant)
        AvailabilityStatus.Digital ->
            ChipColors(colorScheme.primaryContainer, colorScheme.onPrimaryContainer)
    }

    val chipLabel = label ?: when (status) {
        AvailabilityStatus.Available     -> stringResource(R.string.availability_available)
        AvailabilityStatus.Unavailable   -> stringResource(R.string.availability_on_loan)
        AvailabilityStatus.LoanActive    -> stringResource(R.string.availability_active_loan)
        AvailabilityStatus.DueSoon       -> stringResource(R.string.availability_due_soon)
        AvailabilityStatus.Overdue       -> stringResource(R.string.availability_overdue)
        AvailabilityStatus.ReservedReady -> stringResource(R.string.availability_ready)
        AvailabilityStatus.Scheduled     -> stringResource(R.string.loan_status_scheduled)
        AvailabilityStatus.Returned      -> stringResource(R.string.loan_status_returned)
        AvailabilityStatus.Digital       -> stringResource(R.string.availability_digital)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),   // fully rounded pill
        color = container,
        contentColor = onContainer,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Spacer(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(onContainer),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = chipLabel,
                style = MaterialTheme.typography.labelMedium,
                color = onContainer,
            )
        }
    }
}
