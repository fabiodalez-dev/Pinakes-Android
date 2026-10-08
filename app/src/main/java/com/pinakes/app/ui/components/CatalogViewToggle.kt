package com.pinakes.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.pinakes.app.R

@Composable
fun CatalogViewToggle(grid: Boolean, onChange: (Boolean) -> Unit) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row {
            listOf(true, false).forEach { value ->
                IconButton(onClick = { onChange(value) }, modifier = Modifier.semantics { selected = grid == value },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (grid == value) MaterialTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onSurface)) {
                    Icon(if (value) Icons.Outlined.GridView else Icons.AutoMirrored.Outlined.ViewList,
                        contentDescription = stringResource(if (value) R.string.restyle_grid else R.string.restyle_list))
                }
            }
        }
    }
}
