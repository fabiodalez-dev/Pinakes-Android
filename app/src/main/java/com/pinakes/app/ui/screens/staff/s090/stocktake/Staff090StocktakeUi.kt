package com.pinakes.app.ui.screens.staff.s090.stocktake

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pinakes.app.R
import com.pinakes.app.data.model.Staff090StocktakeColor
import com.pinakes.app.data.model.Staff090StocktakeOptions
import com.pinakes.app.data.model.Staff090StocktakeScan
import com.pinakes.app.data.model.Staff090StocktakeSummary
import com.pinakes.app.ui.screens.staff.PickItem
import com.pinakes.app.ui.screens.staff.SearchPickerSheet
import com.pinakes.app.ui.screens.staff.SearchState
import com.pinakes.app.ui.theme.Spacing

// Building blocks shared by the stocktake screens: the outcome badge in the web's colours, the
// counter tiles, a scan row and a picker over a local list. Same shapes as the staff screens.

/** The server's colour pair; the theme's neutral pair when the server sent none. */
@Composable
internal fun Staff090StocktakeColor?.pair(): Pair<Color, Color> {
    val bg = Staff090StocktakeColors.argb(this?.background)
    val fg = Staff090StocktakeColors.argb(this?.foreground)
    return if (bg != null && fg != null) Color(bg) to Color(fg)
    else MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
}

/** A fully round badge: the outcome (or status) label on its own colour, as on the web page. */
@Composable
internal fun StocktakeBadge(label: String, color: Staff090StocktakeColor?, modifier: Modifier = Modifier) {
    val (bg, fg) = color.pair()
    Surface(shape = RoundedCornerShape(50), color = bg, contentColor = fg, modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xxs))
    }
}

/** The live counters as tiles; an outcome counter carries its colour as a dot. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StocktakeCounters(summary: Staff090StocktakeSummary, options: Staff090StocktakeOptions?) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier.fillMaxWidth().testTag("stocktake-counters"),
    ) {
        Staff090StocktakeCounters.of(summary).forEach { counter ->
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.widthIn(min = 96.dp)) {
                Column(Modifier.semantics(mergeDescendants = true) {}.padding(horizontal = Spacing.md, vertical = Spacing.sm).testTag("count-${counter.key}")) {
                    Text(counter.value.toString(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        counter.outcome?.let { code ->
                            val (_, fg) = Staff090StocktakeColors.forOutcome(code, null, options).pair()
                            Box(Modifier.size(8.dp).background(fg, CircleShape))
                            Spacer(Modifier.width(Spacing.xs))
                        }
                        Text(stringResource(counter.label), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** One scan in a list: badge, code, title and the operator. */
@Composable
internal fun StocktakeScanRow(scan: Staff090StocktakeScan, options: Staff090StocktakeOptions?, onOpenBook: ((Int) -> Unit)?) {
    val bookId = scan.bookId?.takeIf { it > 0 }
    Surface(
        onClick = { if (bookId != null) onOpenBook?.invoke(bookId) },
        enabled = bookId != null && onOpenBook != null,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs).testTag("scan-${scan.id}"),
    ) {
        Column(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StocktakeBadge(scan.outcomeLabel.ifBlank { scan.outcome }, Staff090StocktakeColors.forOutcome(scan.outcome, scan.color, options))
                Text(scan.code, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
            scan.title?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            val meta = listOfNotNull(
                scan.expectedShelf?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.staff090_stocktake_belongs_on, it) },
                scan.operator?.name?.takeIf { it.isNotBlank() },
                stocktakeDateTime(scan.scannedAt).takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** [SearchPickerSheet] over a list already in memory (shelves, genres): filtered as the user types. */
@Composable
internal fun LocalPickerSheet(
    title: String,
    placeholder: String,
    items: List<PickItem>,
    query: String,
    onQuery: (String) -> Unit,
    onPick: (PickItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val results = remember(items, query) {
        val q = query.trim()
        if (q.isEmpty()) items else items.filter { it.title.contains(q, ignoreCase = true) || it.subtitle?.contains(q, ignoreCase = true) == true }
    }
    SearchPickerSheet(
        title = title, placeholder = placeholder,
        state = SearchState(query = query, results = results, searched = true),
        onQuery = onQuery, onPick = onPick, onDismiss = onDismiss,
    )
}
