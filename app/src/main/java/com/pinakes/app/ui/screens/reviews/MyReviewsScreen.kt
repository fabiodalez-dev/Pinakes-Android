package com.pinakes.app.ui.screens.reviews

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import com.pinakes.app.R
import com.pinakes.app.data.model.MyReview
import com.pinakes.app.ui.common.DateFormat
import com.pinakes.app.ui.common.LocalServices
import com.pinakes.app.ui.common.UiState
import com.pinakes.app.ui.common.resolvedMessage
import com.pinakes.app.ui.components.EmptyState
import com.pinakes.app.ui.components.ErrorState
import com.pinakes.app.ui.components.LoadingState
import com.pinakes.app.ui.components.PinakesTopBar
import com.pinakes.app.ui.components.SecondaryButton
import com.pinakes.app.ui.components.StarRating
import com.pinakes.app.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyReviewsScreen(
    onNavigateUp: () -> Unit,
    onOpenBook: (Int) -> Unit,
) {
    val services = LocalServices.current
    val vm: MyReviewsViewModel = viewModel(factory = MyReviewsViewModel.Factory(services.reviewsRepository))
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            PinakesTopBar(
                title = stringResource(R.string.title_my_reviews),
                onNavigateUp = onNavigateUp,
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = vm::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when (val content = state.content) {
                is UiState.Loading -> LoadingState(label = stringResource(R.string.reviews_loading))
                is UiState.Error -> ErrorState(message = content.resolvedMessage(), onRetry = vm::load)
                is UiState.Success -> {
                    if (content.data.isEmpty()) {
                        EmptyState(
                            title = stringResource(R.string.my_reviews_empty_title),
                            subtitle = stringResource(R.string.my_reviews_empty_subtitle),
                            icon = Icons.Outlined.RateReview,
                        )
                    } else {
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(Spacing.lg),
                            verticalArrangement = Arrangement.spacedBy(Spacing.md),
                        ) {
                            items(content.data, key = { it.id }) { review ->
                                MyReviewRow(
                                    review = review,
                                    onClick = { onOpenBook(review.bookId) },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                            if (state.hasMore) {
                                item(key = "load_more") {
                                    Box(Modifier.fillMaxWidth().padding(Spacing.md), contentAlignment = Alignment.Center) {
                                        if (state.loadingMore) {
                                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                        } else {
                                            SecondaryButton(
                                                label = stringResource(R.string.action_load_more),
                                                onClick = vm::loadMore,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MyReviewRow(
    review: MyReview,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            Modifier.clickable { onClick() }.padding(Spacing.md),
        ) {
            Box(
                Modifier
                    .width(48.dp)
                    .height(72.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (review.coverUrl != null) {
                    SubcomposeAsyncImage(
                        model = review.coverUrl,
                        contentDescription = review.bookTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(width = 48.dp, height = 72.dp),
                        error = {
                            Icon(
                                Icons.AutoMirrored.Outlined.MenuBook,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                    )
                } else {
                    Icon(
                        Icons.AutoMirrored.Outlined.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    review.bookTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!review.bookAuthor.isNullOrBlank()) {
                    Text(
                        review.bookAuthor,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(Spacing.xs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StarRating(rating = review.rating.toDouble(), starSize = 16.dp)
                    val date = review.updatedAt ?: review.createdAt
                    if (date != null) {
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            DateFormat.date(date),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (!review.text.isNullOrBlank()) {
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        review.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
