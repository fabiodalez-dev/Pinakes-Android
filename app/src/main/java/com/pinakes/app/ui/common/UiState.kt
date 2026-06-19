package com.pinakes.app.ui.common

import androidx.annotation.StringRes

/**
 * A small, exhaustive UI state for a single screen's primary content. Screens render a
 * loading spinner, error state, empty state or the data based on which case this is in.
 *
 * [Error.message] carries a server-provided message when available; [Error.messageRes] is the
 * localizable app fallback used by the screen when [message] is blank.
 */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Error(
        val message: String,
        val code: String = "",
        @param:StringRes val messageRes: Int? = null,
    ) : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
}

/** True when the [UiState.Success] payload is an empty collection. */
fun <T> UiState<List<T>>.isEmptyList(): Boolean = this is UiState.Success && data.isEmpty()

/** Resolves an error message: prefers the server [message], falls back to the localized [messageRes]. */
@androidx.compose.runtime.Composable
fun UiState.Error.resolvedMessage(): String = when {
    message.isNotBlank() -> message
    messageRes != null -> androidx.compose.ui.res.stringResource(messageRes)
    else -> ""
}
