package com.pinakes.app.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import com.pinakes.app.ui.theme.LocalPinakesColors
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.pinakes.app.ui.theme.Spacing


/**
 * Primary filled button (12dp radius, 48dp minimum height).
 *
 * Shows a spinner and disables interaction while [loading] is true.
 */
@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    dark: Boolean = false,
) {
    val tokens = LocalPinakesColors.current
    val fill = if (dark) tokens.dark else tokens.button
    val text = if (dark) androidx.compose.ui.graphics.Color.White else tokens.buttonText
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        enabled = enabled && !loading,
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = 12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = fill,
            contentColor = text,
            disabledContainerColor = if (loading) fill else MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = if (loading) text else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = LocalContentColor.current,
                strokeWidth = 2.dp,
            )
        } else {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(Spacing.sm))
            }
            Text(text = label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Secondary outlined button. */
@Composable
fun SecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = 12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(Spacing.sm))
        }
        Text(text = label, style = MaterialTheme.typography.labelLarge)
    }
}

/** Low-emphasis text button (cancel, "Forgot password", etc.). */
@Composable
fun PinakesTextButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
    }
}
