package com.pinakes.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun BookQuickFacts(facts: List<Pair<String, String>>) {
    if (facts.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        facts.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { (label, value) ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(label.uppercase(Locale.getDefault()), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
