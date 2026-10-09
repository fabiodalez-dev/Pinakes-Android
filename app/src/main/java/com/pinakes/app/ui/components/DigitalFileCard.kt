package com.pinakes.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pinakes.app.ui.theme.LocalPinakesColors

@Composable
fun DigitalFileCard(type: String, name: String, kind: String, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalPinakesColors.current
    Surface(shape = RoundedCornerShape(16.dp), color = colors.surface, border = BorderStroke(1.dp, colors.line),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(6.dp), color = colors.accentSoft, modifier = Modifier.width(44.dp).height(48.dp)) {
                    Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                        Text(type, style = MaterialTheme.typography.labelSmall, color = colors.accentStrong)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(kind, style = MaterialTheme.typography.bodySmall, color = colors.muted)
                }
            }
            content()
        }
    }
}
