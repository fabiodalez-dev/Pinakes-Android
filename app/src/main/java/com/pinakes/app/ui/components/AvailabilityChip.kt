package com.pinakes.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pinakes.app.R
import com.pinakes.app.ui.theme.LocalPinakesColors

enum class AvailabilityStatus {
    Available, // green dot
    Unavailable, // neutral dot
    LoanActive, // green dot
    DueSoon, // amber dot
    Overdue, // error dot
    ReservedReady, // theme accent dot
    Scheduled, // neutral dot
    Returned, // neutral dot
    Digital, // theme accent dot
}

@Composable
fun AvailabilityChip(
    status: AvailabilityStatus,
    label: String? = null,
    modifier: Modifier = Modifier,
    // A pill in a row of pills keeps its inset; a status that heads a block
    // (the book's availability) passes no horizontal inset, so its dot starts
    // where the text and buttons under it start.
    contentPadding: PaddingValues = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
) {
    val colors = LocalPinakesColors.current
    val dot = when (status) {
        AvailabilityStatus.Available, AvailabilityStatus.LoanActive -> Color(0xFF16A34A)
        AvailabilityStatus.DueSoon -> Color(0xFFF59E0B)
        AvailabilityStatus.Overdue -> MaterialTheme.colorScheme.error
        AvailabilityStatus.ReservedReady, AvailabilityStatus.Digital -> colors.accentText
        else -> Color(0xFF9CA3AF)
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
        color = colors.surface,
        contentColor = colors.ink,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(contentPadding),
        ) {
            Spacer(
                modifier = Modifier
                    .testTag("availability-dot")
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dot),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = chipLabel,
                style = MaterialTheme.typography.labelMedium,
                color = colors.ink,
            )
        }
    }
}
