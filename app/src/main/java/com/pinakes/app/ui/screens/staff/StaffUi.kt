package com.pinakes.app.ui.screens.staff

import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pinakes.app.R
import com.pinakes.app.data.model.LookupOption
import com.pinakes.app.data.staff.PickedEntity
import com.pinakes.app.ui.components.PinakesTextField
import com.pinakes.app.ui.theme.LocalPinakesColors
import com.pinakes.app.ui.theme.Spacing

/** A navigation row of the staff area: same shape as the Profile action rows. */
@Composable
internal fun StaffActionRow(icon: ImageVector, title: String, subtitle: String? = null, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
    ) {
        Row(Modifier.padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Floating action: the theme `button` fill always carries its paired `buttonText` colour. */
@Composable
internal fun StaffFab(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    val tokens = LocalPinakesColors.current
    FloatingActionButton(onClick = onClick, containerColor = tokens.button, contentColor = tokens.buttonText) {
        Icon(icon, contentDescription = contentDescription)
    }
}

/** A labelled "−  n  +" stepper for copies. */
@Composable
internal fun QuantityStepper(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    range: IntRange = 1..100,
    enabled: Boolean = true,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange((value - 1).coerceIn(range)) }, enabled = enabled && value > range.first) {
            Icon(Icons.Outlined.Remove, contentDescription = stringResource(R.string.staff_cd_decrease))
        }
        Text(value.toString(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        IconButton(onClick = { onChange((value + 1).coerceIn(range)) }, enabled = enabled && value < range.last) {
            Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.staff_cd_increase))
        }
    }
}

/**
 * A field backed by a lookup list. [editable] keeps free text (language, format, acquisition:
 * free-text columns on the server) and offers the list as suggestions; otherwise the value
 * can only be picked (media type, shelf, level).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LookupField(
    label: String,
    value: String,
    options: List<LookupOption>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    editable: Boolean = true,
    displayValue: String = value,
    allowClear: Boolean = true,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val visible = if (editable && value.isNotBlank()) {
        options.filter { it.label.contains(value, ignoreCase = true) || it.value.contains(value, ignoreCase = true) }
    } else options
    ExposedDropdownMenuBox(
        expanded = expanded && enabled && (visible.isNotEmpty() || (allowClear && !editable)),
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier,
    ) {
        PinakesTextField(
            value = if (editable) value else displayValue,
            onValueChange = { if (editable) { onValueChange(it); expanded = true } },
            label = label,
            readOnly = !editable,
            enabled = enabled,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(if (editable) MenuAnchorType.PrimaryEditable else MenuAnchorType.PrimaryNotEditable, enabled),
        )
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            if (allowClear && !editable && value.isNotBlank()) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.staff_option_none), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    onClick = { onValueChange(""); expanded = false },
                )
            }
            visible.take(50).forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = { onValueChange(if (editable) option.label else option.value); expanded = false },
                )
            }
        }
    }
}

/** Chips for the picked authors/publishers, each removable. New names are marked. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PickedChips(items: List<PickedEntity>, onRemove: (PickedEntity) -> Unit) {
    if (items.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        items.forEach { item ->
            InputChip(
                selected = false,
                onClick = { onRemove(item) },
                label = {
                    Text(
                        if (item.isNew) stringResource(R.string.staff_entity_new_chip, item.name) else item.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                trailingIcon = {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.cd_remove),
                        modifier = Modifier.size(InputChipDefaults.IconSize),
                    )
                },
            )
        }
    }
}

/** A read-only field that opens a picker when tapped (genre, subgenre). */
@Composable
internal fun PickerRow(label: String, value: String?, placeholder: String, onClick: () -> Unit, onClear: (() -> Unit)? = null) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    value?.takeIf { it.isNotBlank() } ?: placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (value.isNullOrBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onClear != null && !value.isNullOrBlank()) {
                IconButton(onClick = onClear) { Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.cd_clear)) }
            } else {
                Icon(Icons.Outlined.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Small uppercase section label inside long forms (eyebrow style). */
@Composable
internal fun FormSectionTitle(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.sm),
    )
}

/**
 * Leaves a staff screen when the server refused the call (role revoked): a toast explains
 * why, since the screen that would host a snackbar is going away.
 */
@Composable
internal fun ForbiddenExit(forbidden: Boolean, onNavigateUp: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(forbidden) {
        if (forbidden) {
            Toast.makeText(context, context.getString(R.string.staff_forbidden), Toast.LENGTH_LONG).show()
            onNavigateUp()
        }
    }
}

internal tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
