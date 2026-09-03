package com.pinakes.app.ui.screens.periodicals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pinakes.app.R
import com.pinakes.app.data.model.PeriodicalDetail
import com.pinakes.app.data.model.PeriodicalYear
import com.pinakes.app.ui.common.UiState
import com.pinakes.app.ui.common.resolvedMessage
import com.pinakes.app.ui.components.ErrorState
import com.pinakes.app.ui.components.LoadingState
import com.pinakes.app.ui.components.PinakesTopBar
import com.pinakes.app.ui.theme.Spacing

/** Masthead detail: header (logo, ISSN, publisher, coverage, holdings) + the years list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodicalDetailScreen(
    onNavigateUp: () -> Unit,
    onOpenYear: (yearId: Int, year: Int) -> Unit,
) {
    val vm: PeriodicalDetailViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()

    val title = (state.content as? UiState.Success)?.data?.title
        ?: stringResource(R.string.periodicals_title)

    Scaffold(
        topBar = { PinakesTopBar(title = title, onNavigateUp = onNavigateUp) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = vm::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when (val content = state.content) {
                is UiState.Loading -> LoadingState(label = stringResource(R.string.periodicals_detail_loading))
                is UiState.Error -> ErrorState(message = content.resolvedMessage(), onRetry = vm::refresh)
                is UiState.Success -> {
                    val detail = content.data
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        item { PeriodicalHeader(detail) }
                        if (detail.years.isNotEmpty()) {
                            item {
                                Text(
                                    stringResource(R.string.periodicals_years_section),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(top = Spacing.sm),
                                )
                            }
                            items(detail.years, key = { it.id }) { year ->
                                YearRow(year = year, onClick = { onOpenYear(year.id, year.year) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodicalHeader(detail: PeriodicalDetail) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PeriodicalLogo(
                    url = detail.logoUrl,
                    contentDescription = detail.title,
                    modifier = Modifier
                        .size(width = 64.dp, height = 84.dp)
                        .clip(MaterialTheme.shapes.medium),
                )
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        detail.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    detail.subtitle?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    val meta = listOfNotNull(
                        stringResource(periodicalTypeLabelRes(detail.type)),
                        periodicalFrequencyLabelRes(detail.frequency)?.let { stringResource(it) },
                    ).joinToString(" · ")
                    Text(
                        meta,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(Spacing.md))

            detail.publisher?.let { InfoRow(stringResource(R.string.periodicals_label_publisher), it.name) }
            detail.issn?.takeIf { it.isNotBlank() }?.let { InfoRow("ISSN", it) }
            val place = listOfNotNull(
                detail.place?.takeIf { it.isNotBlank() },
                detail.language?.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (place.isNotBlank()) InfoRow(stringResource(R.string.periodicals_label_place), place)
            coverageLabel(detail.yearStart, detail.yearEnd)?.let {
                InfoRow(stringResource(R.string.periodicals_label_years), it)
            }
            detail.holdings?.takeIf { it.isNotBlank() }?.let {
                InfoRow(stringResource(R.string.periodicals_label_holdings), it)
            }

            detail.description?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** "1946–1998", "Since 1946", or null when the server has no coverage data. */
@Composable
private fun coverageLabel(start: Int?, end: Int?): String? = when {
    start != null && end != null -> "$start–$end"
    start != null -> stringResource(R.string.periodicals_year_since, start)
    else -> null
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xxs)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun YearRow(year: PeriodicalYear, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val heading = listOfNotNull(
                    year.year.toString(),
                    year.volume?.takeIf { it.isNotBlank() }
                        ?.let { stringResource(R.string.periodicals_year_volume, it) },
                ).joinToString(" · ")
                Text(
                    heading,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val subtitle = listOfNotNull(
                    stringResource(R.string.periodicals_year_issues_owned, year.ownedCount, year.issuesCount),
                    if (year.bound) stringResource(R.string.periodicals_year_bound) else null,
                ).joinToString(" · ")
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
