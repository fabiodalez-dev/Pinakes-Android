package com.pinakes.app.ui.screens.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pinakes.app.R
import com.pinakes.app.data.model.GenreNode
import com.pinakes.app.ui.components.PinakesTextField
import com.pinakes.app.ui.components.PrimaryButton
import com.pinakes.app.ui.theme.Spacing

/**
 * Material 3 modal bottom sheet exposing every catalog facet: availability, genre, author,
 * publisher and language. Edits are staged on the [SearchUiState] as drafts; the search only
 * runs when the user taps "Applica" (or "Azzera" to reset). Labels are Italian.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchFilterSheet(
    state: SearchUiState,
    sheetState: SheetState,
    onAvailableChange: (Boolean) -> Unit,
    onGenreChange: (Int?) -> Unit,
    onAuthorChange: (String) -> Unit,
    onPublisherChange: (String) -> Unit,
    onLanguageChange: (String?) -> Unit,
    onApply: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.filters_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (state.hasActiveFilters) {
                    TextButton(onClick = onClear) { Text(stringResource(R.string.filters_clear)) }
                }
            }

            Spacer(Modifier.height(Spacing.md))

            // Magenta selection for every chip (DESIGN.md: selected chips = primaryContainer).
            val chipColors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            // --- Availability ---
            SectionLabel(stringResource(R.string.filters_section_availability))
            FilterChip(
                selected = state.availableOnly,
                onClick = { onAvailableChange(!state.availableOnly) },
                label = { Text(stringResource(R.string.filters_available_now)) },
                leadingIcon = if (state.availableOnly) {
                    { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.height(18.dp)) }
                } else null,
                colors = chipColors,
            )

            Spacer(Modifier.height(Spacing.lg))

            // --- Genre (cascade from /catalog/genres) ---
            // The backend `genre` filter matches at ANY level, so whatever node the user
            // selects — a top category or a sub-category several levels deep — its id is
            // sent as `genre`. Selecting a top category still means "everything under it".
            if (state.genres.isNotEmpty()) {
                SectionLabel(stringResource(R.string.filters_section_genre))
                // Root→selected node path; drives which sub-category rows are revealed and
                // which chip is highlighted at each level (a breadcrumb of the chosen branch).
                val path: List<GenreNode> =
                    state.selectedGenreId?.let { genrePath(state.genres, it) } ?: emptyList()

                // Level 0: "All" + top categories.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    FilterChip(
                        selected = state.selectedGenreId == null,
                        onClick = { onGenreChange(null) },
                        label = { Text(stringResource(R.string.filters_all_genres)) },
                        colors = chipColors,
                    )
                    state.genres.forEach { g: GenreNode ->
                        FilterChip(
                            selected = path.getOrNull(0)?.id == g.id,
                            onClick = { onGenreChange(g.id) },
                            label = { Text(g.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            colors = chipColors,
                        )
                    }
                }

                // Deeper levels: for each selected node that has children, drill into its
                // sub-categories. Tapping a sub-category narrows the filter to that id.
                path.forEachIndexed { level, node ->
                    if (node.children.isNotEmpty()) {
                        Spacer(Modifier.height(Spacing.md))
                        SectionLabel(stringResource(R.string.filters_section_subgenre))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            node.children.forEach { child: GenreNode ->
                                FilterChip(
                                    selected = path.getOrNull(level + 1)?.id == child.id,
                                    onClick = { onGenreChange(child.id) },
                                    label = { Text(child.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    colors = chipColors,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.lg))
            }

            // --- Author ---
            SectionLabel(stringResource(R.string.filters_section_author))
            PinakesTextField(
                value = state.author,
                onValueChange = onAuthorChange,
                label = stringResource(R.string.filters_section_author),
                placeholder = stringResource(R.string.filters_author_placeholder),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )

            Spacer(Modifier.height(Spacing.md))

            // --- Publisher ---
            SectionLabel(stringResource(R.string.filters_section_publisher))
            PinakesTextField(
                value = state.publisher,
                onValueChange = onPublisherChange,
                label = stringResource(R.string.filters_section_publisher),
                placeholder = stringResource(R.string.filters_publisher_placeholder),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onApply() }),
            )

            Spacer(Modifier.height(Spacing.lg))

            // --- Language ---
            SectionLabel(stringResource(R.string.filters_section_language))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FilterChip(
                    selected = state.language == null,
                    onClick = { onLanguageChange(null) },
                    label = { Text(stringResource(R.string.filters_all_languages)) },
                    colors = chipColors,
                )
                SearchLanguageOptions.forEach { opt ->
                    val selected = state.language == opt.code
                    FilterChip(
                        selected = selected,
                        onClick = { onLanguageChange(if (selected) null else opt.code) },
                        label = { Text(stringResource(opt.labelRes)) },
                        leadingIcon = if (selected) {
                            { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.height(18.dp)) }
                        } else null,
                        colors = chipColors,
                    )
                }
            }

            Spacer(Modifier.height(Spacing.xl))

            PrimaryButton(
                label = if (state.hasActiveFilters)
                    stringResource(R.string.filters_apply_count, state.activeFilterCount)
                else stringResource(R.string.filters_apply),
                onClick = onApply,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

/**
 * Depth-first search for [id] in the genre tree, returning the root→node path (inclusive)
 * or an empty list if not found. Used to reveal the selected branch's sub-category rows.
 */
private fun genrePath(nodes: List<GenreNode>, id: Int): List<GenreNode> {
    for (node in nodes) {
        if (node.id == id) return listOf(node)
        val sub = genrePath(node.children, id)
        if (sub.isNotEmpty()) return listOf(node) + sub
    }
    return emptyList()
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = Spacing.sm),
    )
}
