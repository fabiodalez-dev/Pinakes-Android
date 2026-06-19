package com.pinakes.app.ui.screens.wishlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pinakes.app.R
import com.pinakes.app.ui.common.LocalServices
import com.pinakes.app.ui.common.UiState
import com.pinakes.app.ui.common.resolvedMessage
import com.pinakes.app.ui.components.AvailabilityStatus
import com.pinakes.app.ui.components.EmptyState
import com.pinakes.app.ui.components.ErrorState
import com.pinakes.app.ui.components.LoadingState
import com.pinakes.app.ui.components.MediaRow
import com.pinakes.app.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistScreen(onBookClick: (Int) -> Unit) {
    val services = LocalServices.current
    val vm: WishlistViewModel = viewModel(factory = WishlistViewModel.Factory(services.wishlistRepository))
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }

    val snackbarMessage = state.snackbar ?: state.snackbarRes?.let { stringResource(it) }
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { snackbarHost.showSnackbar(it); vm.consumeSnackbar() }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHost) }) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = vm::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when (val content = state.content) {
                is UiState.Loading -> LoadingState(label = stringResource(R.string.wishlist_loading))
                is UiState.Error -> ErrorState(message = content.resolvedMessage(), onRetry = vm::refresh)
                is UiState.Success -> {
                    if (content.data.isEmpty()) {
                        EmptyState(
                            title = stringResource(R.string.wishlist_empty_title),
                            subtitle = stringResource(R.string.wishlist_empty_subtitle),
                            icon = Icons.Outlined.FavoriteBorder,
                        )
                    } else {
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(Spacing.lg),
                            verticalArrangement = Arrangement.spacedBy(Spacing.md),
                        ) {
                            items(content.data, key = { it.bookId }) { item ->
                                MediaRow(
                                    modifier = Modifier.animateItem(),
                                    title = item.title,
                                    coverUrl = item.coverUrl,
                                    line1 = item.author,
                                    status = if (item.available) AvailabilityStatus.Available else AvailabilityStatus.Unavailable,
                                    onClick = { onBookClick(item.bookId) },
                                    trailing = {
                                        IconButton(
                                            onClick = { vm.remove(item.bookId) },
                                            enabled = state.removingId != item.bookId,
                                        ) {
                                            Icon(
                                                Icons.Outlined.DeleteOutline,
                                                contentDescription = stringResource(R.string.cd_remove),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
