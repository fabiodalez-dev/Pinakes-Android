package com.pinakes.app.ui.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RateReview
import com.pinakes.app.R
import com.pinakes.app.data.model.BookReviews
import com.pinakes.app.data.model.Review
import com.pinakes.app.data.network.ErrorCodes
import com.pinakes.app.ui.common.DateFormat
import com.pinakes.app.ui.common.UiState
import com.pinakes.app.ui.common.resolvedMessage
import com.pinakes.app.ui.components.InlineErrorBanner
import com.pinakes.app.ui.components.PinakesTextButton
import com.pinakes.app.ui.components.PinakesTextField
import com.pinakes.app.ui.components.PrimaryButton
import com.pinakes.app.ui.components.SecondaryButton
import com.pinakes.app.ui.components.StarRating
import com.pinakes.app.ui.components.StarRatingInput
import com.pinakes.app.ui.theme.Spacing
import java.util.Locale

private const val REVIEW_TEXT_MAX = 2000

/**
 * Book-detail reviews block: aggregate rating, the user's own review with an inline composer
 * (create / edit / delete), and the list of other users' reviews. Hosts its own
 * [BookReviewsViewModel] so book detail stays focused on catalog data.
 *
 * [canReviewFallback] comes from personal history (has the user borrowed this title) and gates the
 * composer optimistically until the server's authoritative `can_review` is loaded.
 */
@Composable
fun BookReviewsSection(
    bookId: Int,
    canReviewFallback: Boolean,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Hilt scopes this to the book-detail nav entry and feeds bookId through
    // SavedStateHandle (same ARG_BOOK_ID as BookDetailViewModel), so no key/
    // factory is needed — navigating to a different book gets a fresh entry.
    val vm: BookReviewsViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()

    val message = state.snackbar ?: state.snackbarRes?.let { stringResource(it) }
    LaunchedEffect(message) {
        message?.let { onShowMessage(it); vm.consumeSnackbar() }
    }

    // Graceful degradation: if this instance's server doesn't expose the reviews
    // endpoint (older build → 404 / not_found), collapse the whole section rather
    // than showing an error banner on a book the user can't do anything about.
    val content = state.content
    if (content is UiState.Error && content.code == ErrorCodes.NOT_FOUND) return

    Column(modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.reviews_section_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Spacing.sm))

        when (content) {
            is UiState.Loading -> Row(
                Modifier.fillMaxWidth().padding(vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(Spacing.md))
                Text(
                    stringResource(R.string.reviews_loading),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            is UiState.Error -> InlineErrorBanner(message = content.resolvedMessage(), onRetry = vm::load)

            is UiState.Success -> ReviewsBody(
                data = content.data,
                canReviewFallback = canReviewFallback,
                state = state,
                vm = vm,
            )
        }
    }
}

@Composable
private fun ReviewsBody(
    data: BookReviews,
    canReviewFallback: Boolean,
    state: BookReviewsUiState,
    vm: BookReviewsViewModel,
) {
    // Aggregate header
    if (data.count > 0) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                String.format(Locale.getDefault(), "%.1f", data.average),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(Spacing.md))
            Column {
                StarRating(rating = data.average)
                Text(
                    stringResource(R.string.reviews_count, data.count),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        Text(
            stringResource(R.string.reviews_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    val canReview = data.canReview || canReviewFallback

    // Composer / own review
    if (state.editorOpen) {
        Spacer(Modifier.height(Spacing.lg))
        ReviewEditor(
            rating = state.draftRating,
            text = state.draftText,
            submitting = state.submitting,
            deleting = state.deleting,
            canDelete = data.mine != null,
            onRating = vm::onDraftRating,
            onText = vm::onDraftText,
            onSubmit = vm::submit,
            onDelete = vm::delete,
            onCancel = vm::dismissEditor,
        )
    } else if (data.mine != null) {
        Spacer(Modifier.height(Spacing.lg))
        OwnReviewCard(review = data.mine, onEdit = vm::openEditor)
    } else if (canReview) {
        Spacer(Modifier.height(Spacing.lg))
        SecondaryButton(
            label = stringResource(R.string.reviews_write),
            onClick = vm::openEditor,
            leadingIcon = Icons.Outlined.RateReview,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    // Other users' reviews
    if (data.items.isNotEmpty()) {
        Spacer(Modifier.height(Spacing.lg))
        Text(
            stringResource(R.string.reviews_others_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.sm))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            data.items.forEach { review -> ReviewCard(review) }
        }
    }
}

/** The user's own saved review, with an Edit affordance. */
@Composable
private fun OwnReviewCard(review: Review, onEdit: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.reviews_your_review),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                PinakesTextButton(label = stringResource(R.string.reviews_edit), onClick = onEdit)
            }
            Spacer(Modifier.height(Spacing.xs))
            StarRating(rating = review.rating.toDouble())
            if (!review.text.isNullOrBlank()) {
                Spacer(Modifier.height(Spacing.sm))
                Text(review.text, style = MaterialTheme.typography.bodyMedium)
            }
            review.updatedAt?.let {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    DateFormat.date(it),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
            }
        }
    }
}

/** Inline star + text composer. */
@Composable
private fun ReviewEditor(
    rating: Int,
    text: String,
    submitting: Boolean,
    deleting: Boolean,
    canDelete: Boolean,
    onRating: (Int) -> Unit,
    onText: (String) -> Unit,
    onSubmit: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    // Deleting a review is destructive and can't be undone — confirm first
    // instead of firing on the raw tap.
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.reviews_delete_confirm_title)) },
            text = { Text(stringResource(R.string.reviews_delete_confirm_message)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text(stringResource(R.string.reviews_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.lg)) {
            Text(
                stringResource(R.string.reviews_your_rating),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Spacing.sm))
            StarRatingInput(rating = rating, onRatingChange = onRating, enabled = !submitting && !deleting)
            Spacer(Modifier.height(Spacing.md))
            PinakesTextField(
                value = text,
                onValueChange = onText,
                label = stringResource(R.string.reviews_text_label),
                placeholder = stringResource(R.string.reviews_text_placeholder),
                modifier = Modifier.fillMaxWidth().height(120.dp),
                singleLine = false,
                maxLength = REVIEW_TEXT_MAX,
            )
            Spacer(Modifier.height(Spacing.md))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (canDelete) {
                    PinakesTextButton(
                        label = stringResource(R.string.reviews_delete),
                        onClick = { confirmDelete = true },
                        enabled = !submitting && !deleting,
                    )
                }
                Spacer(Modifier.weight(1f))
                PinakesTextButton(
                    label = stringResource(R.string.action_cancel),
                    onClick = onCancel,
                    enabled = !submitting && !deleting,
                )
                PrimaryButton(
                    label = stringResource(R.string.action_save),
                    onClick = onSubmit,
                    loading = submitting,
                    enabled = rating in 1..5 && !deleting,
                )
            }
        }
    }
}

/** A single other-user review row. */
@Composable
fun ReviewCard(review: Review, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    review.userName.ifBlank { stringResource(R.string.reviews_anonymous) },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                review.createdAt?.let {
                    Text(
                        DateFormat.date(it),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.xs))
            StarRating(rating = review.rating.toDouble(), starSize = 16.dp)
            if (!review.text.isNullOrBlank()) {
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    review.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
